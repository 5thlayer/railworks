// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** How far along the Line a point is from a Place, the walk a Schedule measures to a Station by. */
class PlaceTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(40, 5, 0);
    private static final double EPS = 1e-9;

    private final RailNetwork network = new RailNetwork();

    private RailNetwork line() {
        network.add(Span.of(A, B, 20));
        network.add(Span.of(B, C, 20));
        return network;
    }

    /** Five blocks from A, heading for {@code front}. */
    private Place fiveFromA(Arm front) {
        Span span = network.get(A, B);
        return new Place(span, front, front.equals(B) ? 15.0 : 5.0);
    }

    @Test
    void aPointAheadOnItsOwnSpanIsThatFar() {
        line();
        assertEquals(7.0, fiveFromA(B).distanceTo(network, network.get(A, B), 12.0), EPS);
        assertEquals(3.0, fiveFromA(A).distanceTo(network, network.get(A, B), 2.0), EPS);
    }

    @Test
    void aPointOnASpanBeyondAnArmCountsThroughIt() {
        line();
        assertEquals(15.0 + 10.0, fiveFromA(B).distanceTo(network, network.get(B, C), 10.0), EPS);
    }

    @Test
    void aPointBehindOnALineIsOutOfReach() {
        line();
        assertEquals(-1.0, fiveFromA(B).distanceTo(network, network.get(A, B), 2.0), EPS);
        assertEquals(-1.0, fiveFromA(A).distanceTo(network, network.get(B, C), 10.0), EPS);
    }

    @Test
    void aPointBehindOnALoopIsReachedTheWayRound() {
        // A triangle: A to B to D and back to A, 20 + 20 + 20√2 round.
        line();
        network.remove(B, C);
        Arm d = new Arm(20, 5, 20);
        network.add(Span.of(B, d, 20));
        network.add(Span.of(d, A, 29));
        double round = 40.0 + Math.sqrt(800.0);
        assertEquals(round - 3.0, fiveFromA(B).distanceTo(network, network.get(A, B), 2.0), EPS);
    }
}
