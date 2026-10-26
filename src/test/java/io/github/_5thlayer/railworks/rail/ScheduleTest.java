// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A Schedule driving its Train unattended: on a Line of two 20-block Spans with a Station near each
 * end, and round a loop of eight Arms, with round numbers for the drive.
 */
class ScheduleTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(40, 5, 0);
    /** Beside the Rail five blocks from A, and five short of C. */
    private static final Station MINE = new Station(5, 6, 1, "Mine");
    private static final Station DEPOT = new Station(35, 6, 1, "Depot");
    /** Far enough for any run here. */
    private static final int BUDGET = 2_000;

    private final RailNetwork network = new RailNetwork();
    private final Stations stations = new Stations();
    private final Map<CargoCar, Schedule.Load> loads = new HashMap<>();
    private final List<Locomotive> slotFuelled = new ArrayList<>();
    private final Schedule.Cars cars = new Schedule.Cars() {
        @Override
        public boolean hasFuel(Locomotive locomotive) {
            return slotFuelled.contains(locomotive);
        }

        @Override
        public Schedule.Load load(CargoCar car) {
            return loads.getOrDefault(car, Schedule.Load.PARTLY);
        }
    };

    @BeforeEach
    void spec() {
        // 1 block/tick at most, +0.1 a tick up, -0.25 a tick braking, -0.01 a tick coasting.
        LocomotiveSpec.configure(20.0, 40.0, 100.0, 4.0);
    }

    @AfterEach
    void resetSpec() {
        LocomotiveSpec.configure(LocomotiveSpec.DEFAULT_MAX_SPEED, LocomotiveSpec.DEFAULT_ACCELERATION,
                LocomotiveSpec.DEFAULT_BRAKING, LocomotiveSpec.DEFAULT_FRICTION);
    }

    // ---- the line ----

    private void line() {
        network.add(Span.of(A, B, 20));
        network.add(Span.of(B, C, 20));
        stations.put(MINE);
        stations.put(DEPOT);
    }

    /** A Locomotive facing C, a Cargo car, and a Locomotive facing A, the first car {@code fromA} blocks from A. */
    private Train shuttle(double fromA) {
        Span span = network.get(A, B);
        Train train = Train.of(new Place(span, B, span.length() - fromA), List.of(fuelled(), CargoCar.create()));
        return train.coupled(network, Train.End.BACK, fuelled(), true);
    }

    /** One Locomotive facing C, its middle {@code fromA} blocks from A. */
    private Train lone(double fromA) {
        Span span = fromA <= 20.0 ? network.get(A, B) : network.get(B, C);
        double toFront = fromA <= 20.0 ? 20.0 - fromA : 40.0 - fromA;
        return Train.of(new Place(span, fromA <= 20.0 ? B : C, toFront), fuelled());
    }

    private static Locomotive fuelled() {
        Locomotive locomotive = Locomotive.burner();
        locomotive.fuel().light(1_000_000_000L);
        return locomotive;
    }

    private static void go(Train train, Schedule.Entry... entries) {
        train.schedule().set(List.of(entries), true);
    }

    private static Schedule.Entry seconds(String station, int seconds) {
        return new Schedule.Entry(station, Schedule.Wait.TIME, seconds);
    }

    private void tick(Train train) {
        train.tick(network, stations, locomotive -> 0L, cars, span -> false);
    }

    /** Ticks until {@code done}, failing past the budget; how many ticks it took. */
    private int runUntil(Train train, BooleanSupplier done) {
        for (int ticks = 0; ticks < BUDGET; ticks++) {
            if (done.getAsBoolean()) {
                return ticks;
            }
            tick(train);
        }
        throw new AssertionError("never happened: " + train + " " + train.schedule());
    }

    private void runFor(Train train, int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick(train);
        }
    }

    private static BooleanSupplier waitingAt(Train train, String name) {
        return () -> name.equals(train.schedule().waitingAt());
    }

    /** How far along A to C the middle of {@code car} is. */
    private double alongLine(Train train, Car car) {
        Place place = train.placeOf(network, car);
        double fromSpanA = place.front().equals(place.span().a()) ? place.toFront() : place.span().length() - place.toFront();
        return place.span().a().x() + fromSpanA;
    }

    @Test
    void setGoingAtItsFirstStationItWaitsThereFirstThenLeaves() {
        line();
        Train train = shuttle(5.0);
        go(train, seconds("Mine", 1), seconds("Depot", 1));
        tick(train);
        assertEquals("Mine", train.schedule().waitingAt());
        assertEquals("Depot", train.schedule().after());
        runFor(train, 20);
        assertEquals("Depot", train.schedule().heading());
        tick(train);
        assertTrue(train.speed() > 0.0, "it should pull away toward the Depot: " + train);
    }

    @Test
    void itStopsWithItsLeadingCarAtTheNextStation() {
        line();
        Train train = shuttle(5.0);
        go(train, seconds("Mine", 1), seconds("Depot", 1));
        runUntil(train, waitingAt(train, "Depot"));
        assertTrue(train.atRest());
        assertEquals(35.0, alongLine(train, train.cars().getFirst()), Schedule.ARRIVE);
        assertEquals(List.of(DEPOT), stations.stoppedAt(network, train));
    }

    @Test
    void withALocomotiveFacingEachWayItRunsBackAndForthAlongTheLine() {
        line();
        Train train = shuttle(5.0);
        go(train, seconds("Mine", 1), seconds("Depot", 1));
        for (int trip = 0; trip < 2; trip++) {
            runUntil(train, waitingAt(train, "Depot"));
            runUntil(train, () -> train.speed() < 0.0);
            runUntil(train, waitingAt(train, "Mine"));
            // Back at the Mine with the last car, the Locomotive facing A, leading.
            assertEquals(5.0, alongLine(train, train.cars().getLast()), Schedule.ARRIVE);
            assertEquals(List.of(MINE), stations.stoppedAt(network, train));
        }
    }

    @Test
    void aTrainWithOneLocomotiveNeverBacksToAStation() {
        line();
        Train train = lone(35.0);
        go(train, seconds("Depot", 1), seconds("Mine", 1));
        runFor(train, 21);
        runFor(train, 200);
        assertTrue(train.atRest());
        assertFalse(train.schedule().waiting());
        assertEquals("Mine", train.schedule().heading());
        assertEquals(35.0, alongLine(train, train.cars().getFirst()), 1e-9);
    }

    @Test
    void aLocomotiveWithFuelOnlyInItsSlotStillPullsItThere() {
        line();
        Locomotive locomotive = Locomotive.burner();
        Span span = network.get(A, B);
        Train train = Train.of(new Place(span, B, 15.0), locomotive);
        slotFuelled.add(locomotive);
        go(train, seconds("Depot", 1));
        int lit = 0;
        for (int i = 0; i < BUDGET && train.schedule().waitingAt() == null; i++) {
            train.tick(network, stations, l -> {
                return 1_000_000_000L;
            }, cars, s -> false);
            lit++;
        }
        assertEquals("Depot", train.schedule().waitingAt(), "after " + lit + " ticks: " + train);
    }

    @Test
    void itGoesToTheNearestStationOfTheName() {
        line();
        Station nearer = new Station(15, 6, 1, "Depot");
        stations.put(nearer);
        Train train = lone(5.0);
        go(train, seconds("Depot", 1));
        runUntil(train, waitingAt(train, "Depot"));
        assertEquals(List.of(nearer), stations.stoppedAt(network, train));
    }

    @Test
    void withNoStationOfTheNameItHoldsWhereItIs() {
        line();
        Train train = lone(10.0);
        go(train, seconds("Nowhere", 1));
        runFor(train, 50);
        assertTrue(train.atRest());
        assertEquals(10.0, alongLine(train, train.cars().getFirst()), 1e-9);
    }

    // ---- when to leave ----

    @Test
    void aTimedWaitLastsItsSeconds() {
        line();
        Train train = shuttle(5.0);
        go(train, seconds("Mine", 3), seconds("Depot", 1));
        tick(train);
        runFor(train, 3 * LocomotiveSpec.TICKS_PER_SECOND - 1);
        assertEquals("Mine", train.schedule().waitingAt());
        tick(train);
        assertEquals("Depot", train.schedule().heading());
    }

    @Test
    void aCargoFullWaitLastsUntilEveryCargoCarIsFull() {
        line();
        Train train = shuttle(5.0);
        CargoCar cargo = (CargoCar) train.cars().get(1);
        go(train, Schedule.Entry.of("Mine", Schedule.Wait.CARGO_FULL), seconds("Depot", 1));
        runFor(train, 100);
        assertEquals("Mine", train.schedule().waitingAt());
        loads.put(cargo, Schedule.Load.FULL);
        tick(train);
        assertEquals("Depot", train.schedule().heading());
    }

    @Test
    void aCargoEmptyWaitLastsUntilEveryCargoCarIsEmpty() {
        line();
        Span span = network.get(A, B);
        CargoCar first = CargoCar.create();
        CargoCar second = CargoCar.create();
        Train train = Train.of(new Place(span, B, 15.0), List.of(fuelled(), first, second));
        go(train, Schedule.Entry.of("Mine", Schedule.Wait.CARGO_EMPTY), seconds("Depot", 1));
        loads.put(first, Schedule.Load.EMPTY);
        runFor(train, 100);
        assertEquals("Mine", train.schedule().waitingAt());
        loads.put(second, Schedule.Load.EMPTY);
        tick(train);
        assertEquals("Depot", train.schedule().heading());
    }

    // ---- the loop ----

    /** Eight Arms round a square of 30 with its corners cut, so each bends 45 degrees; a Station on the south and north sides. */
    private void loop() {
        Arm[] arms = {new Arm(10, 5, 0), new Arm(20, 5, 0), new Arm(30, 5, 10), new Arm(30, 5, 20),
                new Arm(20, 5, 30), new Arm(10, 5, 30), new Arm(0, 5, 20), new Arm(0, 5, 10)};
        for (int i = 0; i < arms.length; i++) {
            Arm one = arms[i];
            Arm other = arms[(i + 1) % arms.length];
            network.add(Span.of(one, other, SpanRules.cost(one, other)));
        }
        stations.put(new Station(15, 6, 1, "South"));
        stations.put(new Station(15, 6, 29, "North"));
    }

    /** One Locomotive on the south side at x {@code x}, heading east. */
    private Train eastbound(double x) {
        Span span = network.get(new Arm(10, 5, 0), new Arm(20, 5, 0));
        return Train.of(new Place(span, new Arm(20, 5, 0), 20.0 - x), fuelled());
    }

    @Test
    void itRunsRoundALoopFromStationToStationTheWayItFaces() {
        loop();
        Train train = eastbound(15.0);
        go(train, seconds("South", 1), seconds("North", 1));
        for (int lap = 0; lap < 2; lap++) {
            runUntil(train, waitingAt(train, "North"));
            runUntil(train, waitingAt(train, "South"));
        }
        assertEquals(List.of(stations.named("South").getFirst()), stations.stoppedAt(network, train));
    }

    @Test
    void aStationJustBehindIsReachedTheLongWayRound() {
        loop();
        Train train = eastbound(17.0);
        go(train, seconds("South", 1));
        tick(train);
        assertTrue(train.speed() > 0.0, "it should set off forward: " + train);
        int ticks = runUntil(train, waitingAt(train, "South"));
        // Round the loop's 90-odd blocks, never faster than a block a tick.
        assertTrue(ticks > 80, "it arrived in " + ticks + " ticks");
    }

    // ---- the Schedule itself ----

    @Test
    void anEmptyScheduleNeverDrives() {
        Schedule schedule = new Schedule();
        schedule.set(List.of(), true);
        assertFalse(schedule.automatic());
        assertNull(schedule.heading());
    }

    @Test
    void itKeepsAtMostItsMaximumOfEntries() {
        Schedule schedule = new Schedule();
        List<Schedule.Entry> many = new ArrayList<>();
        for (int i = 0; i < Schedule.MAX_ENTRIES + 3; i++) {
            many.add(seconds("S" + i, 1));
        }
        schedule.set(many, true);
        assertEquals(Schedule.MAX_ENTRIES, schedule.entries().size());
    }

    @Test
    void aTimedWaitIsKeptWithinItsLimits() {
        assertEquals(1, seconds("Mine", 0).seconds());
        assertEquals(Schedule.MAX_SECONDS, seconds("Mine", Schedule.MAX_SECONDS + 1).seconds());
    }

    @Test
    void aDriverTakingOverStopsItDriving() {
        line();
        Train train = shuttle(5.0);
        go(train, seconds("Mine", 1), seconds("Depot", 1));
        tick(train);
        train.schedule().manual();
        assertFalse(train.schedule().automatic());
        assertNull(train.schedule().waitingAt());
        assertEquals(2, train.schedule().entries().size());
    }

    @Test
    void itIsSavedWithItsTrainPartWayThrough() {
        line();
        Train train = shuttle(5.0);
        go(train, seconds("Mine", 1), Schedule.Entry.of("Depot", Schedule.Wait.CARGO_EMPTY));
        runUntil(train, () -> train.speed() > 0.5);
        var saved = Train.CODEC.encodeStart(JsonOps.INSTANCE, train).getOrThrow();
        Train loaded = Train.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        Schedule schedule = loaded.schedule();
        assertEquals(train.schedule().entries(), schedule.entries());
        assertTrue(schedule.automatic());
        assertEquals(1, schedule.index());
        assertEquals("Depot", schedule.heading());
        // And it runs on to the Depot from where it was.
        runUntil(loaded, waitingAt(loaded, "Depot"));
    }

    @Test
    void aTrainSavedBeforeSchedulesLoadsWithAnEmptyOne() {
        line();
        Train train = lone(10.0);
        var saved = Train.CODEC.encodeStart(JsonOps.INSTANCE, train).getOrThrow().getAsJsonObject();
        saved.remove("schedule");
        Train loaded = Train.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertTrue(loaded.schedule().entries().isEmpty());
        assertFalse(loaded.schedule().automatic());
    }

    @Test
    void anUnknownWaitDoesNotLoad() {
        var json = new com.google.gson.JsonPrimitive("forever");
        assertTrue(Schedule.Wait.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void couplingKeepsTheTrainsScheduleAndPartingCopiesItWithoutDriving() {
        line();
        Train train = lone(10.0);
        go(train, seconds("Mine", 1), seconds("Depot", 1));
        Schedule schedule = train.schedule();
        Train coupled = train.coupled(network, Train.End.BACK, CargoCar.create(), false);
        assertSame(schedule, coupled.schedule());
        List<Train> parts = coupled.uncoupled(network, 1);
        for (Train part : parts) {
            assertNotSame(schedule, part.schedule());
            assertEquals(schedule.entries(), part.schedule().entries());
            assertFalse(part.schedule().automatic());
        }
    }
}
