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
        var p = train.head().point();
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
}
