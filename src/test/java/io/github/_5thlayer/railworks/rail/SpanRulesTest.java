// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SpanRulesTest {

    private static final Arm ORIGIN = new Arm(0, 0, 0);
    private static final int LIMIT = (int) SpanRules.DEFAULT_LENGTH_LIMIT;

    @AfterEach
    void resetLimits() {
        SpanRules.configureGrade(SpanRules.DEFAULT_GRADE_LIMIT);
        SpanRules.configureBend(SpanRules.DEFAULT_BEND_LIMIT);
    }

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
        assertFalse(SpanRules.tooLong(ORIGIN, new Arm(LIMIT, 0, 0)));
        assertTrue(SpanRules.tooLong(ORIGIN, new Arm(LIMIT + 1, 0, 0)));
    }

    @Test
    void aLengthJustPastTheLimitRoundsUpToRefused() {
        // sqrt(32^2 + 1) is a hair over the limit and costs 33
        assertTrue(SpanRules.tooLong(ORIGIN, new Arm(LIMIT, 1, 0)));
    }

    @Test
    void theDefaultLimitIsAWireworksTransmissionPolesReach() {
        assertEquals(32, LIMIT);
    }

    @Test
    void farArmsDoNotOverflow() {
        assertTrue(SpanRules.tooLong(ORIGIN, new Arm(30_000_000, 300, 30_000_000)));
    }

    @Test
    void aLevelSpanIsNotSteep() {
        assertFalse(SpanRules.tooSteep(ORIGIN, new Arm(20, 0, 7)));
    }

    @Test
    void theGradeLimitItselfIsAllowedAndABlockMoreIsNot() {
        // A block up for every four across, the default
        assertFalse(SpanRules.tooSteep(ORIGIN, new Arm(8, 2, 0)));
        assertTrue(SpanRules.tooSteep(ORIGIN, new Arm(8, 3, 0)));
    }

    @Test
    void theGradeIsMeasuredAcrossTheGroundNotAlongOneAxis() {
        // 3 blocks up over a run of 5 diagonally, sqrt(3^2 + 4^2)
        SpanRules.configureGrade(0.6);
        assertFalse(SpanRules.tooSteep(ORIGIN, new Arm(3, 3, 4)));
        assertTrue(SpanRules.tooSteep(ORIGIN, new Arm(3, 4, 4)));
    }

    @Test
    void aSpanStraightUpIsTooSteep() {
        assertTrue(SpanRules.tooSteep(ORIGIN, new Arm(0, 1, 0)));
    }

    @Test
    void theGradeIsTheSameUpOrDown() {
        Arm high = new Arm(4, 2, 0);
        assertEquals(SpanRules.tooSteep(ORIGIN, high), SpanRules.tooSteep(high, ORIGIN));
        assertTrue(SpanRules.tooSteep(ORIGIN, new Arm(4, -2, 0)));
    }

    @Test
    void straightOnIsNoBend() {
        assertEquals(0.0, SpanRules.bend(new Arm(-10, 0, 0), ORIGIN, new Arm(7, 0, 0)), 1e-9);
    }

    @Test
    void aCornerIsARightAngleAndDoublingBackIsHalfACircle() {
        assertEquals(90.0, SpanRules.bend(new Arm(-10, 0, 0), ORIGIN, new Arm(0, 0, 5)), 1e-9);
        assertEquals(180.0, SpanRules.bend(new Arm(-10, 0, 0), ORIGIN, new Arm(-3, 0, 0)), 1e-9);
    }

    @Test
    void theBendLimitItselfIsAllowedAndAnyMoreIsNot() {
        Arm from = new Arm(-10, 0, 0);
        assertFalse(SpanRules.tooSharp(from, ORIGIN, new Arm(6, 0, 6)));
        assertTrue(SpanRules.tooSharp(from, ORIGIN, new Arm(6, 0, 7)));
        assertFalse(SpanRules.tooSharp(from, ORIGIN, new Arm(7, 0, 6)));
    }

    @Test
    void aBendIsJudgedTheSameEitherWayThrough() {
        Arm one = new Arm(-10, 0, 3);
        Arm other = new Arm(5, 0, 9);
        assertEquals(SpanRules.bend(one, ORIGIN, other), SpanRules.bend(other, ORIGIN, one), 1e-9);
        assertEquals(SpanRules.tooSharp(one, ORIGIN, other), SpanRules.tooSharp(other, ORIGIN, one));
    }

    @Test
    void aClimbLevellingOffBendsToo() {
        // Up at a 1 in 4, then level: atan(1/4) = 14.0 degrees
        assertEquals(Math.toDegrees(Math.atan(0.25)), SpanRules.bend(new Arm(-8, -2, 0), ORIGIN, new Arm(8, 0, 0)), 1e-9);
        SpanRules.configureBend(10.0);
        assertTrue(SpanRules.tooSharp(new Arm(-8, -2, 0), ORIGIN, new Arm(8, 0, 0)));
    }

    @Test
    void aConfiguredBendLimitMovesTheRefusal() {
        Arm from = new Arm(-10, 0, 0);
        Arm corner = new Arm(0, 0, 5);
        assertTrue(SpanRules.tooSharp(from, ORIGIN, corner));
        SpanRules.configureBend(90.0);
        assertFalse(SpanRules.tooSharp(from, ORIGIN, corner));
    }

    @Test
    void theDefaultLimitsAreAQuarterGradeAndAnEighthOfATurn() {
        assertEquals(0.25, SpanRules.DEFAULT_GRADE_LIMIT);
        assertEquals(45.0, SpanRules.DEFAULT_BEND_LIMIT);
    }
}
