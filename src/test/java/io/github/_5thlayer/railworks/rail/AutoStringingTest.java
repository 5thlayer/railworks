// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AutoStringingTest {

    private static final Arm PLACED = new Arm(0, 5, 0);
    private static final Arm NEAR = new Arm(6, 5, 0);
    private static final Arm FAR = new Arm(12, 5, 0);

    private final RailNetwork network = new RailNetwork();

    @AfterEach
    void resetLimit() {
        SpanRules.configure(SpanRules.DEFAULT_LENGTH_LIMIT);
    }

    @Test
    void choosesTheNearestArmAndItsCost() {
        var choice = AutoStringing.choose(network, PLACED, List.of(FAR, NEAR), 100).orElseThrow();
        assertEquals(NEAR, choice.target());
        assertEquals(6, choice.cost());
    }

    @Test
    void nothingInReachStringsNothing() {
        assertTrue(AutoStringing.choose(network, PLACED, List.of(), 100).isEmpty());
        assertTrue(AutoStringing.choose(network, PLACED, List.of(new Arm(33, 5, 0)), 100).isEmpty());
    }

    @Test
    void theLimitItselfIsInReach() {
        var edge = new Arm((int) SpanRules.DEFAULT_LENGTH_LIMIT, 5, 0);
        assertEquals(edge, AutoStringing.choose(network, PLACED, List.of(edge), 100).orElseThrow().target());
    }

    @Test
    void anArmWithTwoSpansIsPassedOverForTheNextNearest() {
        network.add(Span.of(NEAR, new Arm(6, 5, 20), 20));
        network.add(Span.of(NEAR, new Arm(6, 5, -20), 20));
        var choice = AutoStringing.choose(network, PLACED, List.of(NEAR, FAR), 100).orElseThrow();
        assertEquals(FAR, choice.target());
    }

    @Test
    void anArmWithOneSpanIsStillFree() {
        network.add(Span.of(NEAR, new Arm(6, 5, 20), 20));
        assertEquals(NEAR, AutoStringing.choose(network, PLACED, List.of(NEAR, FAR), 100).orElseThrow().target());
    }

    @Test
    void tooLittleRailStringsNothingEvenWhereAFartherArmCostsMore() {
        assertTrue(AutoStringing.choose(network, PLACED, List.of(NEAR, FAR), 5).isEmpty());
        assertEquals(NEAR, AutoStringing.choose(network, PLACED, List.of(NEAR, FAR), 6).orElseThrow().target());
    }

    @Test
    void heightCountsTowardNearnessAndCost() {
        var above = new Arm(0, 12, 0);
        var choice = AutoStringing.choose(network, PLACED, List.of(FAR, above), 100).orElseThrow();
        assertEquals(above, choice.target());
        assertEquals(7, choice.cost());
    }

    @Test
    void equalDistancesBreakTheTieByPosition() {
        var west = new Arm(-6, 5, 0);
        assertEquals(west, AutoStringing.choose(network, PLACED, List.of(NEAR, west), 100).orElseThrow().target());
        assertEquals(west, AutoStringing.choose(network, PLACED, List.of(west, NEAR), 100).orElseThrow().target());
    }

    @Test
    void itNeverChoosesItself() {
        assertTrue(AutoStringing.choose(network, PLACED, List.of(PLACED), 100).isEmpty());
    }

    @Test
    void aConfiguredLimitMovesTheReach() {
        SpanRules.configure(8.0);
        assertTrue(AutoStringing.choose(network, PLACED, List.of(FAR), 100).isEmpty());
        assertEquals(NEAR, AutoStringing.choose(network, PLACED, List.of(NEAR, FAR), 100).orElseThrow().target());
    }
}
