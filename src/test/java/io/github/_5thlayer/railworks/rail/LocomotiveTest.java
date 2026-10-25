// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A Locomotive on a Line of three Arms, 20 blocks a Span, with round numbers for its drive. */
class LocomotiveTest {

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

    /** At the A end of the first Span, facing C, with plenty of fuel. */
    private Locomotive atStart() {
        Locomotive loco = Locomotive.on(network.get(A, B), B, 20.0);
        loco.fuel().light(1_000_000_000L);
        return loco;
    }

    private void tick(Locomotive loco, Throttle throttle, int times) {
        for (int i = 0; i < times; i++) {
            loco.tick(network, throttle, () -> {
                lit++;
                return 0L;
            });
        }
    }

    @Test
    void forwardAcceleratesTheWayItFacesAndMovesIt() {
        Locomotive loco = atStart();
        tick(loco, Throttle.FORWARD, 1);
        assertEquals(0.1, loco.speed(), EPS);
        tick(loco, Throttle.FORWARD, 1);
        assertEquals(0.2, loco.speed(), EPS);
        // 0.1 + 0.2 blocks travelled, toward B.
        assertEquals(20.0 - 0.3, loco.toFront(), EPS);
        assertEquals(B, loco.front());
    }

    @Test
    void speedIsCappedAtTheMaximum() {
        Locomotive loco = atStart();
        tick(loco, Throttle.FORWARD, 30);
        assertEquals(1.0, loco.speed(), EPS);
    }

    @Test
    void drivingBurnsFuelAtTheFullPowerRateAndCoastingBurnsNone() {
        Locomotive loco = atStart();
        long before = loco.fuel().storedJoules();
        tick(loco, Throttle.FORWARD, 3);
        assertEquals(before - 3 * FULL_BURN, loco.fuel().storedJoules());
        tick(loco, Throttle.NONE, 3);
        assertEquals(before - 3 * FULL_BURN, loco.fuel().storedJoules());
    }

    @Test
    void anEmptyBufferLightsAnItemThroughTheCallbackAndBanksIt() {
        Locomotive loco = Locomotive.on(network.get(A, B), B, 20.0);
        loco.tick(network, Throttle.FORWARD, () -> 4 * FULL_BURN);
        assertEquals(3 * FULL_BURN, loco.fuel().storedJoules());
        assertEquals(0.1, loco.speed(), EPS);
    }

    @Test
    void withNoFuelItCannotAccelerate() {
        Locomotive loco = Locomotive.on(network.get(A, B), B, 20.0);
        tick(loco, Throttle.FORWARD, 5);
        assertEquals(0.0, loco.speed(), EPS);
        assertEquals(20.0, loco.toFront(), EPS);
        assertEquals(5, lit);
    }

    @Test
    void whenTheFuelRunsOutItCoastsToAStop() {
        Locomotive loco = Locomotive.on(network.get(A, B), B, 20.0);
        loco.fuel().light(5 * FULL_BURN);
        tick(loco, Throttle.FORWARD, 5);
        assertEquals(0.5, loco.speed(), EPS);
        tick(loco, Throttle.FORWARD, 1);
        assertTrue(loco.speed() < 0.5, "no fuel, no thrust: it only slows");
        tick(loco, Throttle.FORWARD, 100);
        assertEquals(0.0, loco.speed(), EPS);
        assertFalse(loco.fuel().drawTick(FULL_BURN));
    }

    @Test
    void releasingTheThrottleCoastsToAStopOnFriction() {
        Locomotive loco = atStart();
        tick(loco, Throttle.FORWARD, 2);
        tick(loco, Throttle.NONE, 1);
        assertEquals(0.19, loco.speed(), EPS);
        tick(loco, Throttle.NONE, 50);
        assertEquals(0.0, loco.speed(), EPS);
    }

    @Test
    void backBrakesAndThenReverses() {
        Locomotive loco = atStart();
        tick(loco, Throttle.FORWARD, 4);
        tick(loco, Throttle.BACK, 1);
        assertEquals(0.4 - 0.25, loco.speed(), EPS);
        tick(loco, Throttle.BACK, 1);
        assertEquals(0.0, loco.speed(), EPS);
        tick(loco, Throttle.BACK, 1);
        assertEquals(-0.1, loco.speed(), EPS);
        assertEquals(B, loco.front(), "reversing does not turn it round");
    }

    @Test
    void brakingBurnsNoFuel() {
        Locomotive loco = atStart();
        tick(loco, Throttle.FORWARD, 4);
        long before = loco.fuel().storedJoules();
        tick(loco, Throttle.BACK, 2);
        assertEquals(before, loco.fuel().storedJoules());
    }

    @Test
    void reversingIsCappedAtTheMaximumToo() {
        Locomotive loco = Locomotive.on(network.get(B, C), C, 10.0);
        loco.fuel().light(1_000_000_000L);
        tick(loco, Throttle.BACK, 15);
        assertEquals(-1.0, loco.speed(), EPS);
    }

    @Test
    void itRunsOnIntoTheNextSpanKeepingItsHeading() {
        Locomotive loco = Locomotive.on(network.get(A, B), B, 0.05);
        loco.fuel().light(1_000_000_000L);
        tick(loco, Throttle.FORWARD, 2);
        // 0.1 then 0.2 blocks: 0.3 travelled, 0.05 to the Arm and 0.25 into the next Span.
        assertEquals(C, loco.front());
        assertEquals(network.get(B, C), loco.span());
        assertEquals(20.0 - 0.25, loco.toFront(), EPS);
    }

    @Test
    void itStopsAtALinesEnd() {
        Locomotive loco = Locomotive.on(network.get(B, C), C, 1.0);
        loco.fuel().light(1_000_000_000L);
        tick(loco, Throttle.FORWARD, 30);
        assertEquals(0.0, loco.speed(), EPS);
        assertEquals(0.0, loco.toFront(), EPS);
        assertEquals(C, loco.front());
        tick(loco, Throttle.FORWARD, 5);
        assertEquals(0.0, loco.toFront(), EPS, "it stays at the end under power");
        assertEquals(0.0, loco.speed(), EPS);
    }

    @Test
    void reversingItStopsAtTheLineEndBehindIt() {
        Locomotive loco = Locomotive.on(network.get(A, B), B, 19.0);
        loco.fuel().light(1_000_000_000L);
        tick(loco, Throttle.BACK, 30);
        assertEquals(0.0, loco.speed(), EPS);
        assertEquals(20.0, loco.toFront(), EPS, "all the way back on the first Span");
        assertEquals(B, loco.front());
    }

    @Test
    void reversingRunsBackAcrossAnArmOntoThePreviousSpan() {
        Locomotive loco = Locomotive.on(network.get(B, C), C, 19.95);
        loco.fuel().light(1_000_000_000L);
        tick(loco, Throttle.BACK, 2);
        // It still faces B, the Arm it backed through, now the front of the Span behind.
        assertEquals(network.get(A, B), loco.span());
        assertEquals(B, loco.front());
        assertEquals(0.25, loco.toFront(), EPS);
    }

    @Test
    void aLocomotiveLeftOnACutSpanIsOffTheRail() {
        Locomotive loco = atStart();
        assertTrue(loco.onRail(network));
        network.remove(A, B);
        assertFalse(loco.onRail(network));
    }

    @Test
    void itsPointIsOnTheSpanBetweenTheArms() {
        Locomotive loco = Locomotive.on(network.get(A, B), B, 5.0);
        var p = loco.point();
        assertEquals(15.0, p.x(), EPS);
        assertEquals(5.0, p.y(), EPS);
        assertEquals(0.0, p.z(), EPS);
    }

    @Test
    void itRoundTripsThroughItsCodec() {
        Locomotive loco = atStart();
        tick(loco, Throttle.FORWARD, 3);
        var json = Locomotive.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, loco).getOrThrow();
        Locomotive back = Locomotive.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(loco.span(), back.span());
        assertEquals(loco.front(), back.front());
        assertEquals(loco.toFront(), back.toFront(), EPS);
        assertEquals(loco.speed(), back.speed(), EPS);
        assertEquals(loco.fuel().storedJoules(), back.fuel().storedJoules());
    }
}
