// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpanAimTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(0, 5, 10);

    private final RailNetwork network = new RailNetwork();

    @Test
    void aRayAtTheMiddleOfASpanAimsAtItThere() {
        network.add(Span.of(A, B, 20));
        // From below the middle, looking straight up.
        var aim = SpanAim.nearest(network, 10, 0, 0, 0, 1, 0, 8, 0.5).orElseThrow();
        assertEquals(Span.of(A, B, 20), aim.span());
        assertEquals(10.0, aim.fromA(), 1e-9);
        assertTrue(aim.fromA() >= 0);
    }

    @Test
    void theRayBeyondAnEndClampsToTheEnd() {
        network.add(Span.of(A, B, 20));
        var aim = SpanAim.nearest(network, 20.3, 0, 0, 0, 1, 0, 8, 0.5).orElseThrow();
        assertEquals(20.0, aim.fromA(), 1e-9);
    }

    @Test
    void aRayThatPassesWideOfEverySpanAimsAtNone() {
        network.add(Span.of(A, B, 20));
        assertTrue(SpanAim.nearest(network, 10, 0, 3, 0, 1, 0, 8, 0.5).isEmpty());
    }

    @Test
    void aSpanBeyondReachIsNotAimedAt() {
        network.add(Span.of(A, B, 20));
        assertTrue(SpanAim.nearest(network, 10, -10, 0, 0, 1, 0, 8, 0.5).isEmpty());
    }

    @Test
    void theNearerOfTwoSpansWins() {
        network.add(Span.of(A, B, 20));
        network.add(Span.of(A, C, 10));
        var aim = SpanAim.nearest(network, 0, 0, 5, 0, 1, 0, 8, 0.5).orElseThrow();
        assertEquals(Span.of(A, C, 10), aim.span());
        assertEquals(5.0, aim.fromA(), 1e-9);
    }
}
