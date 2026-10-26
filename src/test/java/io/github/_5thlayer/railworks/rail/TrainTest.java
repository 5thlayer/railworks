// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A Train of one Locomotive on a Line of three Arms, 20 blocks a Span, with round numbers for its drive. */
class TrainTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(40, 5, 0);
    private static final double EPS = 1e-9;
    private static final long FULL_BURN = LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER;

    private final RailNetwork network = new RailNetwork();
    private int lit;

    @BeforeEach
    void line() {
        // 1 block/tick at most, +0.1 a tick up, -0.25 a tick braking, -0.01 a tick coasting.
        LocomotiveSpec.configure(20.0, 40.0, 100.0, 4.0);
        network.add(Span.of(A, B, 20));
        network.add(Span.of(B, C, 20));
    }

    @AfterEach
    void resetSpec() {
        LocomotiveSpec.configure(LocomotiveSpec.DEFAULT_MAX_SPEED, LocomotiveSpec.DEFAULT_ACCELERATION,
                LocomotiveSpec.DEFAULT_BRAKING, LocomotiveSpec.DEFAULT_FRICTION);
    }

    /** A Train of one Burner Locomotive at rest on {@code span}, facing {@code front}. */
    private static Train train(Span span, Arm front, double toFront) {
        return Train.of(new Place(span, front, toFront), Locomotive.burner());
    }

    private static FuelBuffer fuel(Train train) {
        return ((Locomotive) train.cars().getFirst()).fuel();
    }

    /** At the A end of the first Span, facing C, with plenty of fuel. */
    private Train atStart() {
        Train train = train(network.get(A, B), B, 20.0);
        fuel(train).light(1_000_000_000L);
        return train;
    }

    private void tick(Train train, Throttle throttle, int times) {
        for (int i = 0; i < times; i++) {
            train.tick(network, throttle, car -> {
                lit++;
                return 0L;
            });
        }
    }

    @Test
    void forwardAcceleratesTheWayItFacesAndMovesIt() {
        Train train = atStart();
        tick(train, Throttle.FORWARD, 1);
        assertEquals(0.1, train.speed(), EPS);
        tick(train, Throttle.FORWARD, 1);
        assertEquals(0.2, train.speed(), EPS);
        // 0.1 + 0.2 blocks travelled, toward B.
        assertEquals(20.0 - 0.3, train.head().toFront(), EPS);
        assertEquals(B, train.head().front());
    }

    @Test
    void speedIsCappedAtTheMaximum() {
        Train train = atStart();
        tick(train, Throttle.FORWARD, 30);
        assertEquals(1.0, train.speed(), EPS);
    }

    @Test
    void drivingBurnsFuelAtTheFullPowerRateAndCoastingBurnsNone() {
        Train train = atStart();
        long before = fuel(train).storedJoules();
        tick(train, Throttle.FORWARD, 3);
        assertEquals(before - 3 * FULL_BURN, fuel(train).storedJoules());
        tick(train, Throttle.NONE, 3);
        assertEquals(before - 3 * FULL_BURN, fuel(train).storedJoules());
    }

    @Test
    void anEmptyBufferLightsAnItemThroughTheCallbackAndBanksIt() {
        Train train = train(network.get(A, B), B, 20.0);
        train.tick(network, Throttle.FORWARD, car -> 4 * FULL_BURN);
        assertEquals(3 * FULL_BURN, fuel(train).storedJoules());
        assertEquals(0.1, train.speed(), EPS);
    }

    @Test
    void withNoFuelItCannotAccelerate() {
        Train train = train(network.get(A, B), B, 20.0);
        tick(train, Throttle.FORWARD, 5);
        assertEquals(0.0, train.speed(), EPS);
        assertEquals(20.0, train.head().toFront(), EPS);
        assertEquals(5, lit);
    }

    @Test
    void whenTheFuelRunsOutItBrakesToAStop() {
        Train train = train(network.get(A, B), B, 20.0);
        fuel(train).light(5 * FULL_BURN);
        tick(train, Throttle.FORWARD, 5);
        assertEquals(0.5, train.speed(), EPS);
        tick(train, Throttle.FORWARD, 1);
        assertEquals(0.5 - 0.25, train.speed(), EPS, "the dead engine's brakes take it down");
        tick(train, Throttle.FORWARD, 3);
        assertEquals(0.0, train.speed(), EPS);
        assertFalse(fuel(train).drawTick(FULL_BURN));
    }

    @Test
    void releasingTheThrottleCoastsToAStopOnFriction() {
        Train train = atStart();
        tick(train, Throttle.FORWARD, 2);
        tick(train, Throttle.NONE, 1);
        assertEquals(0.19, train.speed(), EPS);
        tick(train, Throttle.NONE, 50);
        assertEquals(0.0, train.speed(), EPS);
    }

    @Test
    void backBrakesAndThenReverses() {
        Train train = atStart();
        tick(train, Throttle.FORWARD, 4);
        tick(train, Throttle.BACK, 1);
        assertEquals(0.4 - 0.25, train.speed(), EPS);
        tick(train, Throttle.BACK, 1);
        assertEquals(0.0, train.speed(), EPS);
        tick(train, Throttle.BACK, 1);
        assertEquals(-0.1, train.speed(), EPS);
        assertEquals(B, train.head().front(), "reversing does not turn it round");
    }

    @Test
    void brakingBurnsNoFuel() {
        Train train = atStart();
        tick(train, Throttle.FORWARD, 4);
        long before = fuel(train).storedJoules();
        tick(train, Throttle.BACK, 2);
        assertEquals(before, fuel(train).storedJoules());
    }

    @Test
    void reversingIsCappedAtTheMaximumToo() {
        Train train = train(network.get(B, C), C, 10.0);
        fuel(train).light(1_000_000_000L);
        tick(train, Throttle.BACK, 15);
        assertEquals(-1.0, train.speed(), EPS);
    }

    @Test
    void itRunsOnIntoTheNextSpanKeepingItsHeading() {
        Train train = train(network.get(A, B), B, 0.05);
        fuel(train).light(1_000_000_000L);
        tick(train, Throttle.FORWARD, 2);
        // 0.1 then 0.2 blocks: 0.3 travelled, 0.05 to the Arm and 0.25 into the next Span.
        assertEquals(C, train.head().front());
        assertEquals(network.get(B, C), train.head().span());
        assertEquals(20.0 - 0.25, train.head().toFront(), EPS);
    }

    @Test
    void itStopsAtALinesEnd() {
        Train train = train(network.get(B, C), C, 1.0);
        fuel(train).light(1_000_000_000L);
        tick(train, Throttle.FORWARD, 30);
        assertEquals(0.0, train.speed(), EPS);
        assertEquals(0.0, train.head().toFront(), EPS);
        assertEquals(C, train.head().front());
        tick(train, Throttle.FORWARD, 5);
        assertEquals(0.0, train.head().toFront(), EPS, "it stays at the end under power");
        assertEquals(0.0, train.speed(), EPS);
    }

    @Test
    void reversingItStopsAtTheLineEndBehindIt() {
        Train train = train(network.get(A, B), B, 19.0);
        fuel(train).light(1_000_000_000L);
        tick(train, Throttle.BACK, 30);
        assertEquals(0.0, train.speed(), EPS);
        assertEquals(20.0, train.head().toFront(), EPS, "all the way back on the first Span");
        assertEquals(B, train.head().front());
    }

    @Test
    void reversingRunsBackAcrossAnArmOntoThePreviousSpan() {
        Train train = train(network.get(B, C), C, 19.95);
        fuel(train).light(1_000_000_000L);
        tick(train, Throttle.BACK, 2);
        // It still faces B, the Arm it backed through, now the front of the Span behind.
        assertEquals(network.get(A, B), train.head().span());
        assertEquals(B, train.head().front());
        assertEquals(0.25, train.head().toFront(), EPS);
    }

    @Test
    void aTrainLeftOnACutSpanIsOffTheRail() {
        Train train = atStart();
        assertTrue(train.onRail(network));
        network.remove(A, B);
        assertFalse(train.onRail(network));
    }

    @Test
    void itsPointIsOnTheSpanBetweenTheArms() {
        Train train = train(network.get(A, B), B, 5.0);
        var p = train.head().point(network);
        assertEquals(15.0, p.x(), EPS);
        assertEquals(5.0, p.y(), EPS);
        assertEquals(0.0, p.z(), EPS);
    }

    @Test
    void itRoundTripsThroughItsCodec() {
        Train train = atStart();
        tick(train, Throttle.FORWARD, 3);
        var json = Train.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, train).getOrThrow();
        Train back = Train.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(train.head().span(), back.head().span());
        assertEquals(train.head().front(), back.head().front());
        assertEquals(train.head().toFront(), back.head().toFront(), EPS);
        assertEquals(train.speed(), back.speed(), EPS);
        assertEquals(fuel(train).storedJoules(), fuel(back).storedJoules());
    }

    @Test
    void aCarStaysOnItsSpanWhenTheArmItFacesMovesUp() {
        Train train = train(network.get(A, B), B, 5.0);
        Arm higher = new Arm(B.x(), B.y() + 1, B.z());
        network.rekey(B, higher);
        train.moveArm(network, B, higher);
        assertEquals(network.get(A, higher), train.head().span());
        assertEquals(higher, train.head().front());
        // A quarter of the way from the front, as it was.
        assertEquals(0.25 * network.get(A, higher).length(), train.head().toFront(), EPS);
        assertTrue(train.onRail(network));
    }

    @Test
    void aCarStaysOnItsSpanWhenTheArmBehindItMovesUp() {
        Train train = train(network.get(A, B), A, 5.0);
        Arm higher = new Arm(B.x(), B.y() + 1, B.z());
        network.rekey(B, higher);
        train.moveArm(network, B, higher);
        assertEquals(A, train.head().front(), "it still faces the Arm that did not move");
        assertEquals(network.get(A, higher), train.head().span());
        assertEquals(0.25 * network.get(A, higher).length(), train.head().toFront(), EPS);
    }

    @Test
    void anArmMovingElsewhereLeavesTheCarAlone() {
        Train train = train(network.get(A, B), B, 5.0);
        Arm higher = new Arm(C.x(), C.y() + 1, C.z());
        network.rekey(C, higher);
        train.moveArm(network, C, higher);
        assertEquals(network.get(A, B), train.head().span());
        assertEquals(5.0, train.head().toFront(), EPS);
    }

    // ---- a Train of cars ----

    /** Two Burner Locomotives, the first with plenty of fuel, the second with none. */
    private static Train pair(Span span, Arm front, double toFront) {
        Locomotive first = Locomotive.burner();
        first.fuel().light(1_000_000_000L);
        return Train.of(new Place(span, front, toFront), List.of(first, Locomotive.burner()));
    }

    @Test
    void aTrainWeighsWhatItsCarsWeigh() {
        assertEquals(LocomotiveSpec.MASS_KG, atStart().mass(), EPS);
        assertEquals(2 * LocomotiveSpec.MASS_KG, pair(network.get(A, B), B, 5.0).mass(), EPS);
    }

    @Test
    void itPullsWithItsFuelledLocomotivesOnly() {
        Train train = pair(network.get(A, B), B, 5.0);
        Locomotive first = (Locomotive) train.cars().get(0);
        Locomotive second = (Locomotive) train.cars().get(1);
        assertEquals(first.pull(), train.pull(), EPS);
        second.fuel().light(1_000_000_000L);
        assertEquals(first.pull() + second.pull(), train.pull(), EPS);
        assertEquals(0.0, train(network.get(A, B), B, 5.0).pull(), EPS, "an empty buffer pulls nothing");
    }

    @Test
    void oneFuelledLocomotiveAcceleratesTwoAtHalfItsOwnRate() {
        Train train = pair(network.get(A, B), B, 15.0);
        tick(train, Throttle.FORWARD, 1);
        assertEquals(0.05, train.speed(), EPS);
        assertEquals(1, lit, "the dry Locomotive asked to light, and was given nothing");
    }

    @Test
    void theDryLocomotiveIsAskedForFuelForItself() {
        Train train = pair(network.get(A, B), B, 15.0);
        Locomotive second = (Locomotive) train.cars().get(1);
        train.tick(network, Throttle.FORWARD, car -> car == second ? 4 * FULL_BURN : 0L);
        assertEquals(3 * FULL_BURN, second.fuel().storedJoules());
        assertEquals(0.1, train.speed(), EPS, "both pulling move both at a Locomotive's own rate");
    }

    @Test
    void itsCarsAreSpacedBehindTheHeadAlongTheLine() {
        Train train = pair(network.get(B, C), C, 19.5);
        Car second = train.cars().get(1);
        assertEquals(0.0, train.offset(train.cars().getFirst()), EPS);
        assertEquals(Locomotive.LENGTH, train.offset(second), EPS);
        assertEquals(Locomotive.LENGTH, train.length(), EPS);
        Place behind = train.placeOf(network, second);
        // 0.5 back to B, then on into the Span behind, still facing C's way through B.
        assertEquals(network.get(A, B), behind.span());
        assertEquals(B, behind.front());
        assertEquals(Locomotive.LENGTH - 0.5, behind.toFront(), EPS);
    }

    @Test
    void itHoldsEverySpanItsCarsStandOn() {
        assertEquals(List.of(network.get(A, B)), atStart().spans(network));
        Train across = pair(network.get(B, C), C, 19.5);
        assertEquals(List.of(network.get(B, C), network.get(A, B)), across.spans(network));
        network.remove(A, B);
        assertFalse(across.onRail(network), "cutting the Span its last car is on derails it");
    }

    @Test
    void backingItStopsWhenItsLastCarMeetsTheLineEnd() {
        Train train = pair(network.get(A, B), B, 15.0);
        tick(train, Throttle.BACK, 60);
        assertEquals(0.0, train.speed(), EPS);
        assertEquals(20.0, train.placeOf(network, train.cars().getLast()).toFront(), EPS);
        assertEquals(20.0 - Locomotive.LENGTH, train.head().toFront(), EPS);
    }

    @Test
    void aTrainSavedWithAScheduleLoadsWithoutIt() {
        Train train = train(network.get(A, B), B, 10.0);
        var saved = Train.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, train).getOrThrow().getAsJsonObject();
        // What #26's Schedule saved with a Train, mid-way to its second entry and driving it.
        saved.add("schedule", com.google.gson.JsonParser.parseString("""
                {"entries": [{"station": "Mine", "until": "time", "seconds": 5},
                             {"station": "Depot", "until": "cargo_full"}],
                 "automatic": true, "index": 1, "waiting": false, "waited": 0,
                 "target": {"x": 35, "y": 6, "z": 1, "way": 1}}"""));
        Train back = Train.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(train.head(), back.head());
        assertEquals(train.cars().getFirst().id(), back.cars().getFirst().id());
        assertFalse(Train.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, back).getOrThrow()
                .getAsJsonObject().has("schedule"));
    }

    @Test
    void aLocomotiveSavedBeforeTrainsHadCarsLoadsAsATrainOfIt() {
        var json = com.google.gson.JsonParser.parseString("""
                {"span": {"a": [0, 5, 0], "b": [20, 5, 0], "cost": 20}, "front": [20, 5, 0],
                 "to_front": 7.5, "speed": 0.25, "fuel": 90000, "last_lit": 4000000}""");
        Train back = Train.LEGACY_LOCOMOTIVE_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(network.get(A, B), back.head().span());
        assertEquals(B, back.head().front());
        assertEquals(7.5, back.head().toFront(), EPS);
        assertEquals(0.25, back.speed(), EPS);
        assertEquals(1, back.cars().size());
        assertEquals(90_000L, fuel(back).storedJoules());
        assertEquals(4_000_000L, fuel(back).lastLitJoules());
    }

    // ---- weight ----

    /** A fuelled Burner Locomotive with a Cargo car behind it. */
    private static Train withCargo(Span span, Arm front, double toFront) {
        Locomotive locomotive = Locomotive.burner();
        locomotive.fuel().light(1_000_000_000L);
        return Train.of(new Place(span, front, toFront), List.of(locomotive, CargoCar.create()));
    }

    @Test
    void everyCarAddsWeight() {
        assertEquals(LocomotiveSpec.MASS_KG + CargoCar.MASS_KG, withCargo(network.get(A, B), B, 5.0).mass(), EPS);
    }

    @Test
    void aHeavierTrainAcceleratesMoreSlowly() {
        Train light = atStart();
        Train heavy = withCargo(network.get(A, B), B, 15.0);
        tick(light, Throttle.FORWARD, 1);
        tick(heavy, Throttle.FORWARD, 1);
        // The Locomotive's 2,000 kg pulls 3,000 kg: two thirds of its own acceleration.
        assertEquals(0.1, light.speed(), EPS);
        assertEquals(0.1 * 2.0 / 3.0, heavy.speed(), EPS);
    }

    @Test
    void aHeavierTrainBrakesMoreSlowly() {
        Train light = atStart();
        Train heavy = withCargo(network.get(A, B), B, 15.0);
        tick(light, Throttle.FORWARD, 6);
        tick(heavy, Throttle.FORWARD, 9);
        assertEquals(light.speed(), heavy.speed(), EPS, "both at 0.6 blocks a tick");
        tick(light, Throttle.BACK, 1);
        tick(heavy, Throttle.BACK, 1);
        // Only the Locomotive brakes, so a Cargo car's weight stretches the stop.
        assertEquals(0.6 - 0.25, light.speed(), EPS);
        assertEquals(0.6 - 0.25 * 2.0 / 3.0, heavy.speed(), EPS);
    }

    @Test
    void aHeavierTrainRunningDryBrakesMoreSlowlyToo() {
        Locomotive locomotive = Locomotive.burner();
        locomotive.fuel().light(5 * FULL_BURN);
        Train train = Train.of(new Place(network.get(A, B), B, 15.0), List.of(locomotive, CargoCar.create()));
        tick(train, Throttle.FORWARD, 5);
        double before = train.speed();
        tick(train, Throttle.FORWARD, 1);
        assertEquals(before - 0.25 * 2.0 / 3.0, train.speed(), EPS);
    }

    // ---- facing ----

    @Test
    void aReversedLocomotiveDrivesTheTrainItsOwnForward() {
        Locomotive front = Locomotive.burner();
        Locomotive back = Locomotive.burner();
        front.fuel().light(1_000_000_000L);
        back.fuel().light(1_000_000_000L);
        Train train = Train.of(new Place(network.get(A, B), B, 10.0), List.of(front, CargoCar.create()));
        train = train.coupled(network, Train.End.BACK, back, true);
        assertTrue(train.reversed(back));
        long frontFuel = front.fuel().storedJoules();
        train.tick(network, Throttle.FORWARD, back, car -> 0L, span -> false);
        assertTrue(train.speed() < 0.0, "forward from the back cab runs the Train backward");
        assertEquals(frontFuel, front.fuel().storedJoules(), "the Locomotive facing away does not pull");
        // One Locomotive's 2,000 kg pulls 5,000 kg.
        assertEquals(-0.1 * 2.0 / 5.0, train.speed(), EPS);
    }

    @Test
    void aReversedCarFacesTheOtherWayAtItsPlace() {
        Train train = withCargo(network.get(A, B), B, 10.0);
        Car cargo = train.cars().get(1);
        Train turned = Train.of(train.head(), train.cars().getFirst())
                .coupled(network, Train.End.BACK, CargoCar.create(), true);
        Car reversed = turned.cars().get(1);
        assertEquals(B, turned.placeOf(network, reversed).front());
        assertEquals(A, turned.facing(network, reversed).front());
        assertEquals(B, train.facing(network, cargo).front());
    }

    // ---- coupling ----

    @Test
    void aCarCouplesBehindTheLastCar() {
        Train train = atStart().coupled(network, Train.End.BACK, CargoCar.create(), false);
        assertTrue(train == null, "at a line's end there is no room behind");
        Train mid = train(network.get(A, B), B, 10.0);
        CargoCar cargo = CargoCar.create();
        Train coupled = mid.coupled(network, Train.End.BACK, cargo, false);
        assertEquals(List.of(mid.cars().getFirst(), cargo), coupled.cars());
        assertEquals(mid.head(), coupled.head());
        assertEquals(10.0 + (Locomotive.LENGTH + CargoCar.LENGTH) / 2.0, coupled.placeOf(network, cargo).toFront(), EPS);
    }

    @Test
    void aCarCouplesAheadOfTheFirstCarAndLeadsTheTrain() {
        Train mid = train(network.get(A, B), B, 10.0);
        CargoCar cargo = CargoCar.create();
        Train coupled = mid.coupled(network, Train.End.FRONT, cargo, false);
        assertEquals(cargo, coupled.cars().getFirst());
        assertEquals(10.0 - 2.0, coupled.head().toFront(), EPS);
        assertEquals(10.0, coupled.placeOf(network, mid.cars().getFirst()).toFront(), EPS, "the old first car stays put");
    }

    @Test
    void aMovingTrainTakesNoCar() {
        Train train = atStart();
        tick(train, Throttle.FORWARD, 2);
        assertEquals(null, train.coupled(network, Train.End.FRONT, CargoCar.create(), false));
        assertEquals(null, withMoving().uncoupled(network, 1));
    }

    private Train withMoving() {
        Train train = withCargo(network.get(A, B), B, 15.0);
        tick(train, Throttle.FORWARD, 2);
        return train;
    }

    @Test
    void uncouplingPartsTheTrainWhereItsCarsStood() {
        Train train = withCargo(network.get(B, C), C, 19.5);
        Car first = train.cars().get(0);
        Car second = train.cars().get(1);
        Place behind = train.placeOf(network, second);
        List<Train> parts = train.uncoupled(network, 1);
        assertEquals(List.of(first), parts.get(0).cars());
        assertEquals(List.of(second), parts.get(1).cars());
        assertEquals(train.head(), parts.get(0).head());
        assertEquals(behind, parts.get(1).head());
        assertEquals(null, train.uncoupled(network, 0), "no coupler ahead of the first car");
    }

    @Test
    void uncouplingKeepsEachCarFacingItsWay() {
        Locomotive back = Locomotive.burner();
        Train train = withCargo(network.get(A, B), B, 5.0).coupled(network, Train.End.BACK, back, true);
        Train last = train.uncoupled(network, 2).get(1);
        assertTrue(last.reversed(back));
        assertEquals(A, last.facing(network, back).front());
    }

    @Test
    void theCouplerUsedIsOnTheSideOfTheCarUsedOrElseTheOther() {
        Train train = withCargo(network.get(A, B), B, 5.0).coupled(network, Train.End.BACK, CargoCar.create(), true);
        Car first = train.cars().get(0);
        Car middle = train.cars().get(1);
        Car last = train.cars().get(2);
        assertEquals(1, train.coupler(middle, 1), "ahead of the middle car");
        assertEquals(2, train.coupler(middle, -1), "behind it");
        assertEquals(1, train.coupler(first, 1), "nothing ahead of the first car, so the one behind it");
        // The last car faces back, so its own ahead is the Train's end.
        assertEquals(2, train.coupler(last, 1));
        assertEquals(2, train.coupler(last, -1));
        Train alone = atStart();
        assertEquals(-1, alone.coupler(alone.cars().getFirst(), 1));
    }

    @Test
    void aCarGoneFromTheMiddleLeavesTwoTrains() {
        Train train = withCargo(network.get(A, B), B, 5.0).coupled(network, Train.End.BACK, CargoCar.create(), false);
        List<Train> left = train.without(network, train.cars().get(1));
        assertEquals(2, left.size());
        assertEquals(train.placeOf(network, train.cars().get(2)), left.get(1).head());
        assertEquals(1, train.without(network, train.cars().getFirst()).size());
        Train alone = atStart();
        assertTrue(alone.without(network, alone.cars().getFirst()).isEmpty());
    }

    // ---- the Spans it holds ----

    @Test
    void itHoldsTheSpansItsCarsBodiesReachInto() {
        // Its middle 0.5 from B, its front half a block long: over B and into the Span beyond.
        Train train = train(network.get(A, B), B, 0.5);
        assertEquals(List.of(network.get(B, C), network.get(A, B)), train.spans(network));
    }

    @Test
    void itStopsAtTheArmOfASpanAnotherTrainHolds() {
        Train train = train(network.get(A, B), B, 5.0);
        fuel(train).light(1_000_000_000L);
        Span ahead = network.get(B, C);
        for (int i = 0; i < 40; i++) {
            train.tick(network, Throttle.FORWARD, train.cars().getFirst(), car -> 0L, ahead::equals);
        }
        assertEquals(0.0, train.speed(), EPS);
        assertEquals(Locomotive.LENGTH / 2.0, train.head().toFront(), EPS, "its front at B");
        assertEquals(List.of(network.get(A, B)), train.spans(network));
    }

    @Test
    void aTrainOfCarsRoundTripsThroughItsCodecWithItsCarsAndTheirFacing() {
        Train train = withCargo(network.get(A, B), B, 5.0).coupled(network, Train.End.BACK, Locomotive.burner(), true);
        var json = Train.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, train).getOrThrow();
        Train back = Train.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(train.cars().stream().map(Car::id).toList(), back.cars().stream().map(Car::id).toList());
        assertEquals(train.cars().stream().map(Car::kind).toList(), back.cars().stream().map(Car::kind).toList());
        assertTrue(back.reversed(back.cars().get(2)));
        assertFalse(back.reversed(back.cars().get(1)));
        assertEquals(train.spans(network), back.spans(network));
    }

    @Test
    void whenTheLocomotivesFacingTheWayItRunsAreDryTheOthersPull() {
        Locomotive front = Locomotive.burner();
        Locomotive back = Locomotive.burner();
        back.fuel().light(1_000_000_000L);
        Train train = Train.of(new Place(network.get(A, B), B, 10.0), front)
                .coupled(network, Train.End.BACK, back, true);
        train.tick(network, Throttle.FORWARD, front, car -> 0L, span -> false);
        // The dry front Locomotive pulls nothing, so the fuelled one facing back pulls 4,000 kg.
        assertEquals(0.1 * 2.0 / 4.0, train.speed(), EPS);
    }

    @Test
    void aCarCouplesOnlyWhereItsWholeBodyIsOnTheLine() {
        // Its back 2 + 1 from A: a 2-block car fits behind it, a hair more does not.
        Train train = train(network.get(A, B), B, 20.0 - 3.0);
        assertTrue(train.slot(network, Train.End.BACK, 2.0) != null);
        assertEquals(null, train.slot(network, Train.End.BACK, 2.01));
    }
}
