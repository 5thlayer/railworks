// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Automatic Trains running jobs from providers to requesters (ADR 0008), through {@link Logistics}'
 * tick and drive with a fake {@link Goods}: on a Line of two 20-block Spans from A to C, and round a
 * loop of eight Arms, with round numbers for the drive. Item kinds are strings; coal stacks to 16 and
 * everything else to 64.
 */
class LogisticsTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(40, 5, 0);
    /** North of the line, serving Trains heading for A: the leading car at 5.5, its spots back east from 7.5. */
    private static final Station MINE = new Station(5, 6, -1, "Mine");
    /** South of the line, serving Trains heading for C: the leading car at 34.5, its spots back west from 32.5. */
    private static final Station DEPOT = new Station(35, 6, 1, "Depot");
    /** South of the line, serving Trains heading for C: the leading car at 8.5, its spots back west from 6.5. */
    private static final Station QUARRY = new Station(9, 6, 1, "Quarry");
    /** North of the line, serving Trains heading for A, nearer the Depot than the Mine is. */
    private static final Station PIT = new Station(17, 6, -1, "Pit");
    /** Far enough for any run here. */
    private static final int BUDGET = 3_000;

    private final RailNetwork network = new RailNetwork();
    private final Trains trains = new Trains();
    private final List<Logistics.Hatch<String>> hatches = new ArrayList<>();
    private final FakeGoods goods = new FakeGoods();
    private final Logistics<String> logistics = new Logistics<>();
    private int nextHatch;

    @BeforeEach
    void spec() {
        // 1 block/tick at most, +0.1 a tick up, -0.25 a tick braking, -0.01 a tick coasting.
        LocomotiveSpec.configure(20.0, 40.0, 100.0, 4.0);
    }

    @AfterEach
    void reset() {
        LocomotiveSpec.configure(LocomotiveSpec.DEFAULT_MAX_SPEED, LocomotiveSpec.DEFAULT_ACCELERATION,
                LocomotiveSpec.DEFAULT_BRAKING, LocomotiveSpec.DEFAULT_FRICTION);
        Logistics.configure(Logistics.DEFAULT_HATCH_TICKS_PER_STACK, Logistics.DEFAULT_STALL_SECONDS);
    }

    // ---- the network ----

    private void line() {
        network.add(Span.of(A, B, 20));
        network.add(Span.of(B, C, 20));
    }

    /** A Locomotive facing C, a Cargo car, and a Locomotive facing A, the first car's middle {@code x} blocks from A. */
    private Train shuttle(double x) {
        Train train = Train.of(onLine(x), List.of(fuelled(), CargoCar.create()));
        return automatic(train.coupled(network, Train.End.BACK, fuelled(), true));
    }

    /** A Locomotive facing C with {@code cargo} Cargo cars behind it, its middle {@code x} blocks from A. */
    private Train eastbound(double x, int cargo) {
        List<Car> cars = new ArrayList<>(List.of(fuelled()));
        for (int i = 0; i < cargo; i++) {
            cars.add(CargoCar.create());
        }
        return automatic(Train.of(onLine(x), cars));
    }

    private Place onLine(double x) {
        Span span = x <= 20.0 ? network.get(A, B) : network.get(B, C);
        return new Place(span, x <= 20.0 ? B : C, (x <= 20.0 ? 20.0 : 40.0) - x);
    }

    private Train automatic(Train train) {
        train.setAutomatic(true);
        trains.add(train);
        return train;
    }

    private static Locomotive fuelled() {
        Locomotive locomotive = Locomotive.burner();
        locomotive.fuel().light(1_000_000_000L);
        return locomotive;
    }

    // ---- the hatches ----

    private Logistics.Hatch<String> provider(Station station, int spot, String item, long count) {
        Logistics.Hatch<String> hatch = new Logistics.Hatch<>(nextHatch++, 0, 0, station, spot, true, Optional.empty());
        hatches.add(hatch);
        goods.put(hatch, item, count);
        return hatch;
    }

    private Logistics.Hatch<String> requester(Station station, int spot, String item, int stacks) {
        Logistics.Hatch<String> hatch = new Logistics.Hatch<>(nextHatch++, 0, 0, station, spot, false,
                Optional.of(new HatchRequest<>(item, stacks)));
        hatches.add(hatch);
        return hatch;
    }

    // ---- running it ----

    /** One server tick: the logistics, then each automatic Train driven. */
    private void step() {
        logistics.tick(network, trains, hatches, goods);
        for (Train train : trains.all()) {
            if (train.automatic()) {
                logistics.drive(network, train, goods, goods::ignite, trains.takenFrom(network, train));
            }
        }
    }

    private void runFor(int ticks) {
        for (int i = 0; i < ticks; i++) {
            step();
        }
    }

    /** Steps until {@code done}, failing past the budget; how many steps it took. */
    private int runUntil(BooleanSupplier done, String what) {
        for (int ticks = 0; ticks < BUDGET; ticks++) {
            if (done.getAsBoolean()) {
                return ticks;
            }
            step();
        }
        throw new AssertionError("never " + what + ": " + trains.all());
    }

    private Logistics.Kind kind(Train train) {
        return logistics.status(train, goods).kind();
    }

    private CargoCar cargo(Train train, int index) {
        return (CargoCar) train.cars().get(index);
    }

    // ---- planning ----

    @Test
    void anAutomaticTrainCarriesWhatARequesterAsksForFromAProviderAtAnotherStation() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 100);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> goods.held(depot, "iron") == 64 && kind(train) == Logistics.Kind.WAITING, "delivered");
        assertEquals(36, goods.held(mine, "iron"));
        assertTrue(goods.empty(cargo(train, 1)));
        assertEquals(List.of(DEPOT), stationsAt(train));
    }

    @Test
    void itsStatusFollowsTheJobFromStationToStation() {
        line();
        provider(MINE, 0, "iron", 100);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 2);
        Train train = shuttle(20.0);
        assertEquals(Logistics.Kind.WAITING, kind(train));
        runUntil(() -> kind(train) == Logistics.Kind.TO_LOAD, "set off to load");
        assertEquals("Mine", logistics.status(train, goods).station());
        runUntil(() -> kind(train) == Logistics.Kind.LOADING, "loaded");
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "set off to unload");
        Logistics.Status<String> heading = logistics.status(train, goods);
        assertEquals("Depot", heading.station());
        // All the Mine held, a stack and a part one.
        assertEquals(List.of(new Logistics.Cargo<>("iron", 2)), heading.cargo());
        runUntil(() -> kind(train) == Logistics.Kind.UNLOADING, "unloading");
        runUntil(() -> kind(train) == Logistics.Kind.WAITING, "done");
        assertEquals(100, goods.held(depot, "iron"));
    }

    @Test
    void aManualTrainIsSaidToBeManual() {
        line();
        Train train = shuttle(20.0);
        train.setAutomatic(false);
        assertEquals(Logistics.Kind.MANUAL, kind(train));
    }

    @Test
    void theNearestProviderStationOfferingTheItemServesTheRequester() {
        line();
        Logistics.Hatch<String> far = provider(MINE, 0, "iron", 64);
        Logistics.Hatch<String> near = provider(PIT, 0, "iron", 64);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(25.0);
        runUntil(() -> goods.held(depot, "iron") == 64, "delivered");
        assertEquals(64, goods.held(far, "iron"));
        assertEquals(0, goods.held(near, "iron"));
    }

    @Test
    void aProviderAndARequesterAtOneStationNeverPair() {
        line();
        Logistics.Hatch<String> offered = provider(DEPOT, 0, "iron", 64);
        Logistics.Hatch<String> asked = requester(DEPOT, 1, "iron", 1);
        Train train = eastbound(20.0, 2);
        runFor(200);
        assertEquals(Logistics.Kind.WAITING, kind(train));
        assertEquals(64, goods.held(offered, "iron"));
        assertEquals(0, goods.held(asked, "iron"));
    }

    @Test
    void noJobIsPlannedForATrainThatCannotReachTheProvider() {
        line();
        // Its one Locomotive faces C, and the Mine serves only Trains heading for A.
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 64);
        requester(DEPOT, 0, "iron", 1);
        Train train = eastbound(20.0, 1);
        Place was = train.head();
        runFor(200);
        assertEquals(Logistics.Kind.WAITING, kind(train));
        assertEquals(was, train.head());
        assertEquals(64, goods.held(mine, "iron"));
    }

    @Test
    void aRequesterWithAllItAsksForIsLeftAlone() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 64);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        goods.put(depot, "iron", 64);
        Train train = shuttle(20.0);
        runFor(200);
        assertEquals(Logistics.Kind.WAITING, kind(train));
        assertEquals(64, goods.held(mine, "iron"));
    }

    @Test
    void eachLoadIsTheLeastOfTheNeedTheProvidersStockAndTheCarsRoom() {
        line();
        goods.slots = 2;
        // Needs 118, two stacks less the 10 it holds.
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 500);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 2);
        goods.put(depot, "iron", 10);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(118, goods.held(cargo(train, 1), "iron"));

        // The car's two slots hold 128: a request for five stacks gets that much a trip.
        Logistics.Hatch<String> more = requester(DEPOT, 0, "copper", 5);
        provider(MINE, 0, "copper", 500);
        runUntil(() -> goods.held(more, "copper") > 0 && kind(train) == Logistics.Kind.WAITING, "delivered copper");
        assertEquals(128, goods.held(more, "copper"));
        assertEquals(500 - 118, goods.held(mine, "iron"));
    }

    @Test
    void aLoadNeverPromisesMoreThanTheProviderHolds() {
        line();
        provider(MINE, 0, "iron", 40);
        requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(40, goods.held(cargo(train, 1), "iron"));
    }

    // ---- several Trains and claims ----

    /** Eight Arms round a square of 30 with its corners cut, so each bends 45 degrees. */
    private void loop() {
        for (int i = 0; i < LOOP.length; i++) {
            Arm one = LOOP[i];
            Arm other = LOOP[(i + 1) % LOOP.length];
            network.add(Span.of(one, other, SpanRules.cost(one, other)));
        }
    }

    private static final Arm[] LOOP = {new Arm(10, 5, 0), new Arm(20, 5, 0), new Arm(30, 5, 10), new Arm(30, 5, 20),
            new Arm(20, 5, 30), new Arm(10, 5, 30), new Arm(0, 5, 20), new Arm(0, 5, 10)};
    /** Inside the loop on its south side, serving Trains heading east; one car spot. */
    private static final Station SOUTH = new Station(15, 6, 1, "South");
    /** Inside the loop on its north side, serving Trains heading west; one car spot. */
    private static final Station NORTH = new Station(15, 6, 29, "North");

    /** A Locomotive and a Cargo car round the loop, the Locomotive on the Span from {@code from} to {@code to}, {@code toFront} short of {@code to}. */
    private Train onLoop(Arm from, Arm to, double toFront) {
        Span span = network.get(from, to);
        return automatic(Train.of(new Place(span, to, toFront), List.of(fuelled(), CargoCar.create())));
    }

    @Test
    void theNearestFreeTrainTakesTheJob() {
        loop();
        provider(NORTH, 0, "iron", 64);
        requester(SOUTH, 0, "iron", 1);
        // Just past the South Station, most of the way round from the North.
        Train far = onLoop(LOOP[0], LOOP[1], 2.0);
        // Coming up the east side, a short way from the North.
        Train near = onLoop(LOOP[2], LOOP[3], 4.0);
        runFor(Logistics.PLAN_TICKS + 1);
        assertEquals(Logistics.Kind.WAITING, kind(far));
        assertEquals(Logistics.Kind.TO_LOAD, kind(near));
    }

    @Test
    void aLoadOnItsWayCountsAgainstTheRequestSoNoSecondTrainIsSent() {
        loop();
        provider(NORTH, 0, "iron", 640);
        requester(SOUTH, 0, "iron", 1);
        Train one = onLoop(LOOP[2], LOOP[3], 4.0);
        Train other = onLoop(LOOP[6], LOOP[7], 4.0);
        runUntil(() -> kind(one) == Logistics.Kind.TO_UNLOAD, "loaded");
        // On its way the load counts against the request, plan after plan.
        for (int i = 0; i < 10 * Logistics.PLAN_TICKS && kind(one) == Logistics.Kind.TO_UNLOAD; i++) {
            step();
            assertEquals(Logistics.Kind.WAITING, kind(other));
        }
    }

    @Test
    void stockClaimedForOneRequesterIsNotPromisedToAnother() {
        loop();
        Logistics.Hatch<String> north = provider(NORTH, 0, "iron", 64);
        requester(SOUTH, 0, "iron", 1);
        Station east = new Station(29, 6, 15, "East");
        requester(east, 0, "iron", 1);
        Train one = onLoop(LOOP[2], LOOP[3], 4.0);
        Train other = onLoop(LOOP[6], LOOP[7], 4.0);
        runFor(Logistics.PLAN_TICKS + 1);
        // One job takes all the North holds; the other Train is left with nothing to fetch.
        assertEquals(1, List.of(one, other).stream().filter(t -> kind(t) == Logistics.Kind.WAITING).count());
        assertEquals(64, goods.held(north, "iron"));
    }

    @Test
    void requestersAreServedInTheOrderTheyWerePlaced() {
        loop();
        provider(NORTH, 0, "iron", 64);
        Station east = new Station(29, 6, 15, "East");
        Logistics.Hatch<String> first = requester(SOUTH, 0, "iron", 1);
        Logistics.Hatch<String> second = requester(east, 0, "iron", 1);
        onLoop(LOOP[2], LOOP[3], 4.0);
        runUntil(() -> goods.held(first, "iron") == 64, "delivered to the first");
        assertEquals(0, goods.held(second, "iron"));
    }

    // ---- mixed Trains ----

    @Test
    void oneJobCarriesALoadForEveryCarSpotWhereTheRequesterAsksForWhatTheProviderOffers() {
        line();
        provider(QUARRY, 0, "iron", 64);
        provider(QUARRY, 1, "coal", 50);
        Logistics.Hatch<String> iron = requester(DEPOT, 0, "iron", 1);
        Logistics.Hatch<String> coal = requester(DEPOT, 1, "coal", 1);
        // Its Locomotive leading, the first Cargo car at spot 0 and the second at spot 1.
        Train train = eastbound(5.0, 2);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
        assertEquals(16, goods.held(cargo(train, 2), "coal"));
        assertEquals(List.of(new Logistics.Cargo<>("iron", 1), new Logistics.Cargo<>("coal", 1)),
                logistics.status(train, goods).cargo());
        runUntil(() -> kind(train) == Logistics.Kind.WAITING, "unloaded");
        assertEquals(64, goods.held(iron, "iron"));
        assertEquals(16, goods.held(coal, "coal"));
    }

    @Test
    void oneCarCarriesTwoItemsWhereTwoHatchPairsShareItsSpot() {
        line();
        provider(QUARRY, 0, "iron", 64);
        provider(QUARRY, 0, "coal", 16);
        Logistics.Hatch<String> iron = requester(DEPOT, 0, "iron", 1);
        Logistics.Hatch<String> coal = requester(DEPOT, 0, "coal", 1);
        Train train = eastbound(5.0, 1);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
        assertEquals(16, goods.held(cargo(train, 1), "coal"));
        runUntil(() -> kind(train) == Logistics.Kind.WAITING, "unloaded");
        assertEquals(64, goods.held(iron, "iron"));
        assertEquals(16, goods.held(coal, "coal"));
    }

    @Test
    void aCargoCarLeadingItsTrainTradesWithTheHatchesBesideTheLeadingCarsPlace() {
        line();
        Logistics.Hatch<String> quarry = provider(QUARRY, Platform.LEADING, "iron", 64);
        Logistics.Hatch<String> depot = requester(DEPOT, Platform.LEADING, "iron", 1);
        // A Cargo car leading, its Locomotive behind it facing the same way.
        Train train = automatic(Train.of(onLine(5.0), List.of(CargoCar.create(), fuelled())));
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(64, goods.held(cargo(train, 0), "iron"));
        runUntil(() -> kind(train) == Logistics.Kind.WAITING, "unloaded");
        assertEquals(0, goods.held(quarry, "iron"));
        assertEquals(64, goods.held(depot, "iron"));
    }

    // ---- at the Station ----

    @Test
    void eachHatchMovesOneStackEveryHatchTicksPerStack() {
        line();
        provider(MINE, 0, "iron", 640);
        requester(DEPOT, 0, "iron", 3);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.LOADING, "arrived");
        step();
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
        runFor(Logistics.DEFAULT_HATCH_TICKS_PER_STACK - 1);
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
        step();
        assertEquals(128, goods.held(cargo(train, 1), "iron"));
    }

    @Test
    void theServerSetsHowManyTicksAStackTakes() {
        line();
        Logistics.configure(5, Logistics.DEFAULT_STALL_SECONDS);
        provider(MINE, 0, "iron", 640);
        requester(DEPOT, 0, "iron", 3);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.LOADING, "arrived");
        runFor(1 + 5);
        assertEquals(128, goods.held(cargo(train, 1), "iron"));
    }

    @Test
    void hatchesSlowerThanTheStallStillMoveEveryStack() {
        line();
        Logistics.configure(60, 1.0);
        provider(MINE, 0, "iron", 640);
        requester(DEPOT, 0, "iron", 3);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(192, goods.held(cargo(train, 1), "iron"));
    }

    @Test
    void itLeavesAsSoonAsEveryLoadIsMoved() {
        line();
        provider(MINE, 0, "iron", 640);
        requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.LOADING, "arrived");
        int loading = runUntil(() -> kind(train) != Logistics.Kind.LOADING, "left");
        assertTrue(loading < 5, "it loaded for " + loading + " ticks");
        assertEquals(Logistics.Kind.TO_UNLOAD, kind(train));
    }

    @Test
    void withNothingMovingItLeavesAfterTheStallWithWhatItTookAndDeliversIt() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 100);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 3);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_LOAD, "set off");
        // Some is taken by hand after the job claimed all 100, so it finds only 70.
        goods.put(mine, "iron", 70);
        runUntil(() -> kind(train) == Logistics.Kind.LOADING, "arrived");
        int loading = runUntil(() -> kind(train) != Logistics.Kind.LOADING, "left");
        int stall = (int) (Logistics.DEFAULT_STALL_SECONDS * LocomotiveSpec.TICKS_PER_SECOND);
        assertTrue(loading >= stall && loading <= stall + Logistics.DEFAULT_HATCH_TICKS_PER_STACK + 2,
                "it loaded for " + loading + " ticks");
        assertEquals(List.of(new Logistics.Cargo<>("iron", 2)), logistics.status(train, goods).cargo());
        runUntil(() -> kind(train) == Logistics.Kind.WAITING && goods.empty(cargo(train, 1)), "delivered");
        assertEquals(70, goods.held(depot, "iron"));
    }

    @Test
    void atAFullRequesterItLeavesAfterTheStall() {
        line();
        Logistics.configure(Logistics.DEFAULT_HATCH_TICKS_PER_STACK, 1.0);
        provider(MINE, 0, "iron", 128);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 2);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        goods.hatchRoom = 64;
        runUntil(() -> kind(train) == Logistics.Kind.UNLOADING, "arrived");
        int unloading = runUntil(() -> kind(train) != Logistics.Kind.UNLOADING, "left");
        assertTrue(unloading >= LocomotiveSpec.TICKS_PER_SECOND, "it unloaded for " + unloading + " ticks");
        assertEquals(64, goods.held(depot, "iron"));
    }

    @Test
    void aTrainThatLoadedNothingDropsItsJobAndWaitsAtTheProvider() {
        line();
        Logistics.configure(Logistics.DEFAULT_HATCH_TICKS_PER_STACK, 1.0);
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 64);
        requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_LOAD, "set off");
        goods.put(mine, "iron", 0);
        runUntil(() -> kind(train) == Logistics.Kind.LOADING, "arrived");
        runUntil(() -> kind(train) == Logistics.Kind.WAITING, "dropped its job");
        Place at = train.head();
        runFor(100);
        assertEquals(at, train.head());
        assertEquals(List.of(MINE), stationsAt(train));
    }

    @Test
    void withNoJobItWaitsWhereItLastUnloaded() {
        line();
        provider(MINE, 0, "iron", 64);
        requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.WAITING && goods.empty(cargo(train, 1))
                && !stationsAt(train).isEmpty(), "delivered");
        Place at = train.head();
        runFor(200);
        assertEquals(at, train.head());
        assertEquals(List.of(DEPOT), stationsAt(train));
    }

    @Test
    void aManualTrainStoppedAtAStationTradesWithNoHatch() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 64);
        requester(DEPOT, 0, "iron", 1);
        // The Locomotive facing A leading at the Mine, its Cargo car at the first spot.
        Train train = shuttle(9.5);
        train.setAutomatic(false);
        assertTrue(stationsAt(train).contains(MINE));
        runFor(200);
        assertEquals(64, goods.held(mine, "iron"));
        assertTrue(goods.empty(cargo(train, 1)));
    }

    @Test
    void aTrainCutOffFromItsStationCannotReachIt() {
        line();
        provider(MINE, 0, "iron", 64);
        requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(25.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_LOAD, "set off");
        network.remove(A, B);
        runFor(20);
        Logistics.Status<String> status = logistics.status(train, goods);
        assertEquals(Logistics.Kind.CANNOT_REACH, status.kind());
        assertEquals("Mine", status.station());
    }

    // ---- fuel ----

    @Test
    void aProviderFuelsTheLocomotiveBesideItAStackATurnUntilItsFourStacksAreFull() {
        line();
        Logistics.Hatch<String> quarry = provider(QUARRY, Platform.LEADING, "coal", 100);
        // Waiting with no job, its Locomotive at the Quarry's leading car's place.
        Train train = eastbound(8.5, 1);
        Locomotive locomotive = (Locomotive) train.cars().getFirst();
        step();
        assertEquals(16, goods.fuel(locomotive, "coal"), "the first stack on the first tick");
        runFor(Logistics.hatchTicksPerStack() - 1);
        assertEquals(16, goods.fuel(locomotive, "coal"));
        step();
        assertEquals(32, goods.fuel(locomotive, "coal"));
        runFor(200);
        assertEquals(64, goods.fuel(locomotive, "coal"), "four stacks of coal");
        assertEquals(36, goods.held(quarry, "coal"));
    }

    @Test
    void aManualTrainIsNeverFuelled() {
        line();
        provider(QUARRY, Platform.LEADING, "coal", 100);
        Train train = eastbound(8.5, 1);
        train.setAutomatic(false);
        runFor(100);
        assertEquals(0, goods.fuel((Locomotive) train.cars().getFirst(), "coal"));
    }

    @Test
    void onlyAProviderBesideALocomotiveFuelsIt() {
        line();
        Logistics.Hatch<String> asking = requester(QUARRY, Platform.LEADING, "coal", 1);
        goods.put(asking, "coal", 16);
        Logistics.Hatch<String> besideTheCar = provider(QUARRY, 0, "coal", 16);
        Train train = eastbound(8.5, 1);
        runFor(100);
        assertEquals(0, goods.fuel((Locomotive) train.cars().getFirst(), "coal"));
        assertEquals(16, goods.held(asking, "coal"));
        assertEquals(16, goods.held(besideTheCar, "coal"));
        assertTrue(goods.empty(cargo(train, 1)));
    }

    @Test
    void onlyFuelThatBurnsLeavingNothingGoesIn() {
        line();
        Logistics.Hatch<String> quarry = provider(QUARRY, Platform.LEADING, "lava_bucket", 1);
        goods.put(quarry, "wood", 10);
        Train train = eastbound(8.5, 1);
        runFor(100);
        Locomotive locomotive = (Locomotive) train.cars().getFirst();
        assertEquals(0, goods.fuel(locomotive, "lava_bucket"));
        assertEquals(10, goods.fuel(locomotive, "wood"));
    }

    @Test
    void aLocomotiveTakesOnlyTheFuelNoJobHasClaimed() {
        line();
        provider(QUARRY, Platform.LEADING, "coal", 20);
        Logistics.Hatch<String> depot = requester(DEPOT, Platform.LEADING, "coal", 1);
        // Its Locomotive at the Quarry's leading car's place, manual until a job of the other has claimed a stack.
        Train standing = eastbound(8.5, 0);
        standing.setAutomatic(false);
        // A Cargo car leading, behind it.
        Train other = automatic(Train.of(onLine(3.0), List.of(CargoCar.create(), fuelled())));
        runUntil(() -> kind(other) == Logistics.Kind.TO_LOAD, "planned");
        standing.setAutomatic(true);
        runUntil(() -> goods.held(depot, "coal") == 16, "delivered the claimed stack");
        assertEquals(4, goods.fuel((Locomotive) standing.cars().getFirst(), "coal"), "the four no job claims");
    }

    @Test
    void aTrainOnAJobWaitsAtTheStationWhileFuelGoesIn() {
        line();
        provider(QUARRY, 0, "iron", 64);
        provider(QUARRY, Platform.LEADING, "coal", 64);
        requester(DEPOT, 0, "iron", 1);
        Train train = eastbound(3.0, 1);
        runUntil(() -> kind(train) == Logistics.Kind.LOADING, "at the Quarry");
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(64, goods.held(cargo(train, 1), "iron"), "a stack on the first turn");
        assertEquals(64, goods.fuel((Locomotive) train.cars().getFirst(), "coal"), "four stacks over four turns");
    }

    @Test
    void aTrainThatRanDryWaitingIsFuelledAndTakesJobsAgain() {
        line();
        provider(QUARRY, Platform.LEADING, "coal", 64);
        provider(QUARRY, 0, "iron", 64);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        // Nothing in its buffer or its slots: it cannot pull, so no job is planned for it.
        Train train = automatic(Train.of(onLine(8.5), List.of(Locomotive.burner(), CargoCar.create())));
        runUntil(() -> goods.held(depot, "iron") == 64, "delivered");
    }

    @Test
    void aTrainOnItsWayHeldAtAnotherStationTakesNoFuelThere() {
        line();
        // South of the line, serving Trains heading for C: the leading car at 18.5, a block short of B.
        Station siding = new Station(19, 6, 1, "Siding");
        provider(siding, Platform.LEADING, "coal", 64);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        Train train = eastbound(5.0, 1);
        Locomotive locomotive = (Locomotive) train.cars().getFirst();
        goods.put(cargo(train, 1), "iron", 10);
        // A Train on the Span from B, so it is held at B on its way to deliver, by the Siding.
        trains.add(Train.of(onLine(30.0), fuelled()));
        runUntil(() -> train.atRest() && train.head().toFront() == 1.0, "held at B");
        runFor(100);
        assertEquals(Logistics.Kind.TO_UNLOAD, kind(train));
        assertEquals(0, goods.fuel(locomotive, "coal"), "on its way, not at its job's Station");
        // Its requester gone, it waits there holding, and is fuelled.
        hatches.remove(depot);
        runUntil(() -> goods.fuel(locomotive, "coal") > 0, "fuelled waiting");
    }

    // ---- claims break ----

    @Test
    void aTrainSwitchedToManualReleasesItsClaimsAndKeepsItsCargo() {
        loop();
        provider(NORTH, 0, "iron", 640);
        requester(SOUTH, 0, "iron", 1);
        Train one = onLoop(LOOP[2], LOOP[3], 4.0);
        Train other = onLoop(LOOP[6], LOOP[7], 4.0);
        runUntil(() -> kind(one) == Logistics.Kind.TO_UNLOAD, "loaded");
        one.setAutomatic(false);
        runUntil(() -> kind(other) == Logistics.Kind.TO_LOAD, "sent the other Train for the released request");
        assertEquals(Logistics.Kind.MANUAL, kind(one));
        assertEquals(64, goods.held(cargo(one, 1), "iron"));
    }

    @Test
    void aTrainBrokenUpReleasesItsClaimsAndItsCarsKeepTheirCargo() {
        loop();
        provider(NORTH, 0, "iron", 640);
        requester(SOUTH, 0, "iron", 3);
        Train one = onLoop(LOOP[2], LOOP[3], 4.0);
        Train other = onLoop(LOOP[6], LOOP[7], 4.0);
        runUntil(() -> kind(one) == Logistics.Kind.LOADING && goods.held(cargo(one, 1), "iron") > 0, "loading");
        CargoCar wagon = cargo(one, 1);
        // Stopped at the provider, as shears part a Train only while it stands still.
        if (trains.uncouple(network, one, 1) == null) {
            throw new AssertionError("could not part " + one);
        }
        runUntil(() -> kind(other) == Logistics.Kind.TO_LOAD, "sent the other Train for the released request");
        assertEquals(64, goods.held(wagon, "iron"));
    }

    @Test
    void aTrainCoupledToReleasesItsClaims() {
        loop();
        provider(NORTH, 0, "iron", 640);
        requester(SOUTH, 0, "iron", 3);
        Train one = onLoop(LOOP[2], LOOP[3], 4.0);
        Train other = onLoop(LOOP[6], LOOP[7], 4.0);
        runUntil(() -> kind(one) == Logistics.Kind.LOADING && goods.held(cargo(one, 1), "iron") > 0, "loading");
        Place behind = one.slot(network, Train.End.BACK, CargoCar.LENGTH);
        if (trains.couple(network, new Trains.Slot(one, Train.End.BACK, behind), CargoCar.create(), false) == null) {
            throw new AssertionError("could not couple a car to " + one);
        }
        runUntil(() -> kind(other) == Logistics.Kind.TO_LOAD, "sent the other Train for the released request");
    }

    @Test
    void aTrainGoneFromTheRailReleasesItsClaims() {
        loop();
        provider(NORTH, 0, "iron", 640);
        requester(SOUTH, 0, "iron", 1);
        Train one = onLoop(LOOP[2], LOOP[3], 4.0);
        Train other = onLoop(LOOP[6], LOOP[7], 4.0);
        runUntil(() -> kind(one) == Logistics.Kind.TO_UNLOAD, "loaded");
        // Derailed by a cut Span, or its last car broken.
        trains.remove(one);
        runUntil(() -> kind(other) == Logistics.Kind.TO_LOAD, "sent the other Train for the released request");
    }

    @Test
    void aRequestCutBeforeLoadingTakesOnlyWhatItNowWants() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 640);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 3);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_LOAD, "set off");
        ask(depot, "iron", 1);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
        assertEquals(640 - 64, goods.held(mine, "iron"));
    }

    @Test
    void aBrokenProviderDropsItsLoadsAndTheEmptyJobIsCancelled() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 64);
        requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_LOAD, "set off");
        hatches.remove(mine);
        step();
        assertEquals(Logistics.Kind.WAITING, kind(train));
        runFor(200);
        assertEquals(Logistics.Kind.WAITING, kind(train));
        assertTrue(goods.empty(cargo(train, 1)));
    }

    @Test
    void aProviderSwitchedToARequesterDropsItsLoads() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 64);
        requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_LOAD, "set off");
        replace(mine, new Logistics.Hatch<>(mine.x(), mine.y(), mine.z(), MINE, 0, false, Optional.empty()));
        step();
        assertEquals(Logistics.Kind.WAITING, kind(train));
    }

    @Test
    void whatWasLoadedForABrokenRequesterIsALeftoverWithNowhereToDeliver() {
        line();
        provider(MINE, 0, "iron", 640);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        hatches.remove(depot);
        step();
        Logistics.Status<String> status = logistics.status(train, goods);
        assertEquals(Logistics.Kind.HOLDING, status.kind());
        assertEquals(List.of(new Logistics.Cargo<>("iron", 1)), status.cargo());
        runUntil(train::atRest, "stopped");
        Place at = train.head();
        runFor(200);
        assertEquals(at, train.head());
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
        assertEquals(Logistics.Kind.HOLDING, kind(train));
    }

    @Test
    void aRequesterSwitchedToAProviderLeavesALeftover() {
        line();
        provider(MINE, 0, "iron", 640);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        replace(depot, new Logistics.Hatch<>(depot.x(), depot.y(), depot.z(), DEPOT, 0, true, Optional.empty()));
        step();
        assertEquals(Logistics.Kind.HOLDING, kind(train));
    }

    @Test
    void aJobKeepsTheLoadsWhoseHatchesStand() {
        line();
        provider(QUARRY, 0, "iron", 64);
        provider(QUARRY, 1, "coal", 16);
        Logistics.Hatch<String> iron = requester(DEPOT, 0, "iron", 1);
        Logistics.Hatch<String> coal = requester(DEPOT, 1, "coal", 1);
        Train train = eastbound(5.0, 2);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        hatches.remove(coal);
        step();
        assertEquals(List.of(new Logistics.Cargo<>("iron", 1)), logistics.status(train, goods).cargo());
        runUntil(() -> kind(train) == Logistics.Kind.HOLDING, "unloaded what still has somewhere to go");
        assertEquals(64, goods.held(iron, "iron"));
        assertEquals(16, goods.held(cargo(train, 2), "coal"));
    }

    @Test
    void aRequestCutOnTheWayCapsUnloadingAndTheRestIsALeftover() {
        line();
        provider(MINE, 0, "iron", 640);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 2);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(128, goods.held(cargo(train, 1), "iron"));
        Logistics.Hatch<String> cut = ask(depot, "iron", 1);
        runUntil(() -> kind(train) == Logistics.Kind.UNLOADING, "arrived");
        int unloading = runUntil(() -> kind(train) != Logistics.Kind.UNLOADING, "left");
        assertTrue(unloading < 5, "it unloaded for " + unloading + " ticks");
        assertEquals(64, goods.held(cut, "iron"));
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
        assertEquals(Logistics.Kind.HOLDING, kind(train));
    }

    @Test
    void aRequestForAnotherItemTakesNothingOfTheOldOne() {
        line();
        provider(MINE, 0, "iron", 640);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        ask(depot, "copper", 1);
        runUntil(() -> kind(train) == Logistics.Kind.HOLDING, "gave up on the Depot");
        assertEquals(0, goods.held(depot, "iron"));
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
    }

    // ---- leftovers ----

    @Test
    void aLeftoverIsDeliveredOnceARequesterAsksForIt() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 640);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        hatches.remove(depot);
        runFor(Logistics.PLAN_TICKS * 3);
        assertEquals(Logistics.Kind.HOLDING, kind(train));
        Logistics.Hatch<String> again = requester(DEPOT, 0, "iron", 1);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "sent to deliver the leftover");
        assertEquals("Depot", logistics.status(train, goods).station());
        runUntil(() -> kind(train) == Logistics.Kind.WAITING, "delivered");
        assertEquals(64, goods.held(again, "iron"));
        // A delivery job has no provider: nothing more was taken from the Mine.
        assertEquals(640 - 64, goods.held(mine, "iron"));
    }

    @Test
    void aHandLoadedTrainSetToAutomaticDeliversToTheNearestRequesterAskingForWhatEachCarHolds() {
        line();
        Logistics.Hatch<String> far = requester(DEPOT, 0, "iron", 1);
        Logistics.Hatch<String> near = requester(QUARRY, 0, "iron", 1);
        // At the nearest Station of all, but asking for another item.
        Logistics.Hatch<String> other = requester(new Station(7, 6, 1, "Siding"), 0, "copper", 1);
        Train train = eastbound(3.0, 1);
        train.setAutomatic(false);
        goods.put(cargo(train, 1), "iron", 40);
        train.setAutomatic(true);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "sent to deliver");
        assertEquals("Quarry", logistics.status(train, goods).station());
        runUntil(() -> kind(train) == Logistics.Kind.WAITING, "delivered");
        assertEquals(40, goods.held(near, "iron"));
        assertEquals(0, goods.held(far, "iron"));
        assertEquals(0, goods.held(other, "copper"));
    }

    @Test
    void aDeliveryCutOffFromItsRequesterIsGivenUpAndTheTrainHolds() {
        line();
        requester(DEPOT, 0, "iron", 1);
        Train train = eastbound(5.0, 1);
        goods.put(cargo(train, 1), "iron", 40);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "sent to deliver");
        network.remove(B, C);
        runUntil(() -> kind(train) == Logistics.Kind.HOLDING, "gave the delivery up");
        assertEquals(40, goods.held(cargo(train, 1), "iron"));
    }

    @Test
    void aJobCutOffFromItsRequesterIsGivenUpAndWhatItCarriesALeftover() {
        line();
        provider(QUARRY, 0, "iron", 64);
        requester(DEPOT, 0, "iron", 1);
        Train train = eastbound(5.0, 1);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        network.remove(B, C);
        runUntil(() -> kind(train) == Logistics.Kind.HOLDING, "gave the job up");
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
    }

    @Test
    void twoLoadsToOneRequesterTakeNoMoreBetweenThemThanItNowWants() {
        line();
        provider(QUARRY, 0, "iron", 64);
        provider(QUARRY, 0, "iron", 64);
        Logistics.Hatch<String> depot = requester(DEPOT, 0, "iron", 2);
        Train train = eastbound(5.0, 1);
        runUntil(() -> kind(train) == Logistics.Kind.TO_LOAD, "set off");
        ask(depot, "iron", 1);
        runUntil(() -> kind(train) == Logistics.Kind.TO_UNLOAD, "loaded");
        assertEquals(64, goods.held(cargo(train, 1), "iron"));
    }

    @Test
    void aTrainHoldingAnythingTakesNoJobFromAProvider() {
        line();
        Logistics.Hatch<String> mine = provider(MINE, 0, "iron", 640);
        requester(DEPOT, 0, "iron", 1);
        Train train = shuttle(20.0);
        goods.put(cargo(train, 1), "coal", 5);
        runFor(200);
        assertEquals(Logistics.Kind.HOLDING, kind(train));
        assertEquals(640, goods.held(mine, "iron"));
    }

    private void replace(Logistics.Hatch<String> hatch, Logistics.Hatch<String> with) {
        hatches.set(hatches.indexOf(hatch), with);
    }

    /** {@code hatch}, a requester, asking for {@code stacks} of {@code item} now, which is the hatch it becomes. */
    private Logistics.Hatch<String> ask(Logistics.Hatch<String> hatch, String item, int stacks) {
        Logistics.Hatch<String> asking = new Logistics.Hatch<>(hatch.x(), hatch.y(), hatch.z(), hatch.station(),
                hatch.spot(), false, Optional.of(new HatchRequest<>(item, stacks)));
        replace(hatch, asking);
        return asking;
    }

    private List<Station> stationsAt(Train train) {
        Stations stations = new Stations();
        List.of(MINE, DEPOT, QUARRY).forEach(stations::put);
        return stations.stoppedAt(network, train);
    }

    // ---- the fake ----

    /** What hatches and cars hold, by item kind; a car has {@link #slots} slots, and a hatch takes up to {@link #hatchRoom} items. */
    private static final class FakeGoods implements Goods<String> {

        private final Map<Integer, Map<String, Long>> inHatches = new HashMap<>();
        private final Map<UUID, Map<String, Long>> inCars = new HashMap<>();
        private final Map<UUID, Map<String, Long>> inLocomotives = new HashMap<>();
        int slots = 27;
        long hatchRoom = Long.MAX_VALUE / 4;

        void put(Logistics.Hatch<String> hatch, String item, long count) {
            inHatches.computeIfAbsent(hatch.x(), x -> new HashMap<>()).put(item, count);
        }

        void put(CargoCar car, String item, long count) {
            inCars.computeIfAbsent(car.id(), id -> new HashMap<>()).put(item, count);
        }

        @Override
        public int stackSize(String item) {
            return item.equals("coal") ? 16 : 64;
        }

        @Override
        public long held(Logistics.Hatch<String> hatch, String item) {
            return inHatches.getOrDefault(hatch.x(), Map.of()).getOrDefault(item, 0L);
        }

        @Override
        public long held(CargoCar car, String item) {
            return inCars.getOrDefault(car.id(), Map.of()).getOrDefault(item, 0L);
        }

        @Override
        public Map<String, Long> contents(CargoCar car) {
            Map<String, Long> contents = new LinkedHashMap<>();
            inCars.getOrDefault(car.id(), Map.of()).forEach((item, count) -> {
                if (count > 0) {
                    contents.put(item, count);
                }
            });
            return contents;
        }

        @Override
        public boolean empty(CargoCar car) {
            return inCars.getOrDefault(car.id(), Map.of()).values().stream().allMatch(count -> count == 0L);
        }

        @Override
        public int slots(CargoCar car) {
            return slots;
        }

        @Override
        public long load(Logistics.Hatch<String> from, CargoCar into, String item, long most) {
            Map<String, Long> car = inCars.computeIfAbsent(into.id(), id -> new HashMap<>());
            long used = 0;
            for (var held : car.entrySet()) {
                used += (held.getValue() + stackSize(held.getKey()) - 1) / stackSize(held.getKey());
            }
            long have = car.getOrDefault(item, 0L);
            long slack = (stackSize(item) - have % stackSize(item)) % stackSize(item);
            long room = (slots - used) * stackSize(item) + slack;
            long moved = Math.min(most, Math.min(held(from, item), room));
            put(from, item, held(from, item) - moved);
            car.merge(item, moved, Long::sum);
            return moved;
        }

        @Override
        public long unload(CargoCar from, Logistics.Hatch<String> into, String item, long most) {
            long holds = inHatches.getOrDefault(into.x(), Map.of()).values().stream().mapToLong(Long::longValue).sum();
            long moved = Math.min(most, Math.min(held(from, item), hatchRoom - holds));
            inCars.computeIfAbsent(from.id(), id -> new HashMap<>()).merge(item, -moved, Long::sum);
            inHatches.computeIfAbsent(into.x(), x -> new HashMap<>()).merge(item, moved, Long::sum);
            return moved;
        }

        @Override
        public Map<String, Long> contents(Logistics.Hatch<String> hatch) {
            Map<String, Long> contents = new LinkedHashMap<>();
            inHatches.getOrDefault(hatch.x(), Map.of()).forEach((item, count) -> {
                if (count > 0) {
                    contents.put(item, count);
                }
            });
            return contents;
        }

        /** How many of {@code item} {@code locomotive}'s fuel slots hold. */
        long fuel(Locomotive locomotive, String item) {
            return inLocomotives.getOrDefault(locomotive.id(), Map.of()).getOrDefault(item, 0L);
        }

        /** Coal and wood burn and leave nothing; a lava bucket leaves its bucket, and iron does not burn. */
        @Override
        public long fuelRoom(Logistics.Hatch<String> from, Locomotive locomotive, String item) {
            if (!item.equals("coal") && !item.equals("wood")) {
                return 0;
            }
            Map<String, Long> slots = inLocomotives.getOrDefault(locomotive.id(), Map.of());
            long used = 0;
            for (var held : slots.entrySet()) {
                used += (held.getValue() + stackSize(held.getKey()) - 1) / stackSize(held.getKey());
            }
            long have = slots.getOrDefault(item, 0L);
            long slack = (stackSize(item) - have % stackSize(item)) % stackSize(item);
            return (Locomotive.FUEL_SLOTS - used) * stackSize(item) + slack;
        }

        @Override
        public long refuel(Logistics.Hatch<String> from, Locomotive into, String item, long most) {
            long moved = Math.min(most, Math.min(held(from, item), fuelRoom(from, into, item)));
            put(from, item, held(from, item) - moved);
            inLocomotives.computeIfAbsent(into.id(), id -> new HashMap<>()).merge(item, moved, Long::sum);
            return moved;
        }

        /** Burns one item from {@code locomotive}'s first fuel slot holding any, coal's 4 MJ whatever it is; 0 for none. */
        long ignite(Locomotive locomotive) {
            for (var held : inLocomotives.getOrDefault(locomotive.id(), Map.of()).entrySet()) {
                if (held.getValue() > 0) {
                    held.setValue(held.getValue() - 1);
                    return 4_000_000L;
                }
            }
            return 0L;
        }

        @Override
        public boolean slotsFuelled(Locomotive locomotive) {
            return inLocomotives.getOrDefault(locomotive.id(), Map.of()).values().stream().anyMatch(count -> count > 0);
        }
    }
}
