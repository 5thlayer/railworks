// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpanRulesTest {

    private static final Arm ORIGIN = new Arm(0, 0, 0);

    @Test
    void aStraightRunCostsItsLength() {
        assertEquals(10, SpanRules.cost(ORIGIN, new Arm(10, 0, 0)));
    }

    @Test
    void aWholeDiagonalCostsItsLength() {
        assertEquals(5, SpanRules.cost(ORIGIN, new Arm(3, 4, 0)));
    }

    @Test
    void aFractionalLengthRoundsUp() {
        // sqrt(2) = 1.41
        assertEquals(2, SpanRules.cost(ORIGIN, new Arm(1, 1, 0)));
        // sqrt(26) = 5.09
        assertEquals(6, SpanRules.cost(ORIGIN, new Arm(5, 1, 0)));
    }

    @Test
    void heightCountsLikeDistance() {
        assertEquals(7, SpanRules.cost(ORIGIN, new Arm(0, 7, 0)));
        assertEquals(3, SpanRules.cost(ORIGIN, new Arm(1, 2, 2)));
    }

    @Test
    void costIsTheSameEitherWay() {
        Arm far = new Arm(-13, 4, 27);
        assertEquals(SpanRules.cost(ORIGIN, far), SpanRules.cost(far, ORIGIN));
    }

    @Test
    void theLimitItselfIsAllowedAndOneMoreIsNot() {
        assertFalse(SpanRules.tooLong(ORIGIN, new Arm(SpanRules.LENGTH_LIMIT, 0, 0)));
        assertTrue(SpanRules.tooLong(ORIGIN, new Arm(SpanRules.LENGTH_LIMIT + 1, 0, 0)));
    }

    @Test
    void aLengthJustPastTheLimitRoundsUpToRefused() {
        // sqrt(64^2 + 1) is a hair over the limit and costs 65
        assertTrue(SpanRules.tooLong(ORIGIN, new Arm(SpanRules.LENGTH_LIMIT, 1, 0)));
    }

    @Test
    void farArmsDoNotOverflow() {
        assertTrue(SpanRules.tooLong(ORIGIN, new Arm(30_000_000, 300, 30_000_000)));
    }
}
