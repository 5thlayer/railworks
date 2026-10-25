// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PendingEndTest {

    private static final Arm ANCHOR = new Arm(0, 5, 0);

    private static PendingEnd.Holder at(double x, boolean rail, boolean sameDimension) {
        return new PendingEnd.Holder(x, 5.5, 0.5, 4.5, rail, sameDimension);
    }

    @Test
    void heldWhileNearWithRailInHand() {
        assertTrue(PendingEnd.stillHeld(ANCHOR, true, at(10, true, true)));
    }

    @Test
    void droppedWhenTheRailLeavesTheMainHand() {
        assertFalse(PendingEnd.stillHeld(ANCHOR, true, at(10, false, true)));
    }

    @Test
    void droppedWhenThePlayerWalksBeyondTheLongestSpanAndTheirReach() {
        assertTrue(PendingEnd.stillHeld(ANCHOR, true, at(SpanRules.LENGTH_LIMIT + 4, true, true)));
        assertFalse(PendingEnd.stillHeld(ANCHOR, true, at(SpanRules.LENGTH_LIMIT + 6, true, true)));
    }

    @Test
    void droppedWhenTheArmIsGoneOrInAnotherDimension() {
        assertFalse(PendingEnd.stillHeld(ANCHOR, false, at(10, true, true)));
        assertFalse(PendingEnd.stillHeld(ANCHOR, true, at(10, true, false)));
    }
}
