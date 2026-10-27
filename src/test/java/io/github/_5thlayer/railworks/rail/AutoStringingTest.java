// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static io.github._5thlayer.railworks.rail.Clearance.Blocks.OPEN;
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
        SpanRules.configureGrade(SpanRules.DEFAULT_GRADE_LIMIT);
        SpanRules.configureBend(SpanRules.DEFAULT_BEND_LIMIT);
    }

    @Test
    void choosesTheNearestArmAndItsCost() {
        var choice = AutoStringing.choose(network, OPEN, PLACED, List.of(FAR, NEAR), 100).orElseThrow();
        assertEquals(NEAR, choice.target());
        assertEquals(6, choice.cost());
    }

    @Test
    void nothingInReachStringsNothing() {
        assertTrue(AutoStringing.choose(network, OPEN, PLACED, List.of(), 100).isEmpty());
        assertTrue(AutoStringing.choose(network, OPEN, PLACED, List.of(new Arm(33, 5, 0)), 100).isEmpty());
    }

    @Test
    void theLimitItselfIsInReach() {
        var edge = new Arm((int) SpanRules.DEFAULT_LENGTH_LIMIT, 5, 0);
        assertEquals(edge, AutoStringing.choose(network, OPEN, PLACED, List.of(edge), 100).orElseThrow().target());
    }

    @Test
    void anArmWithTwoSpansIsPassedOverForTheNextNearest() {
        network.add(Span.of(NEAR, new Arm(6, 5, 20), 20));
        network.add(Span.of(NEAR, new Arm(6, 5, -20), 20));
        var choice = AutoStringing.choose(network, OPEN, PLACED, List.of(NEAR, FAR), 100).orElseThrow();
        assertEquals(FAR, choice.target());
    }

    @Test
    void anArmWithOneSpanIsStillFree() {
        // Running on east, so the line through NEAR runs straight on
        network.add(Span.of(NEAR, new Arm(26, 5, 0), 20));
        assertEquals(NEAR, AutoStringing.choose(network, OPEN, PLACED, List.of(NEAR, FAR), 100).orElseThrow().target());
    }

    @Test
    void tooLittleRailStringsNothingEvenWhereAFartherArmCostsMore() {
        assertTrue(AutoStringing.choose(network, OPEN, PLACED, List.of(NEAR, FAR), 5).isEmpty());
        assertEquals(NEAR, AutoStringing.choose(network, OPEN, PLACED, List.of(NEAR, FAR), 6).orElseThrow().target());
    }

    @Test
    void heightCountsTowardNearnessAndCost() {
        // Both 8 across; the climb makes the higher one farther, sqrt(68) = 8.25
        var higher = new Arm(8, 7, 0);
        var level = new Arm(0, 5, 8);
        assertEquals(level, AutoStringing.choose(network, OPEN, PLACED, List.of(higher, level), 100).orElseThrow().target());
        var choice = AutoStringing.choose(network, OPEN, PLACED, List.of(FAR, higher), 100).orElseThrow();
        assertEquals(higher, choice.target());
        assertEquals(9, choice.cost());
    }

    @Test
    void equalDistancesBreakTheTieByPosition() {
        var west = new Arm(-6, 5, 0);
        assertEquals(west, AutoStringing.choose(network, OPEN, PLACED, List.of(NEAR, west), 100).orElseThrow().target());
        assertEquals(west, AutoStringing.choose(network, OPEN, PLACED, List.of(west, NEAR), 100).orElseThrow().target());
    }

    @Test
    void itNeverChoosesItself() {
        assertTrue(AutoStringing.choose(network, OPEN, PLACED, List.of(PLACED), 100).isEmpty());
    }

    @Test
    void aConfiguredLimitMovesTheReach() {
        SpanRules.configure(8.0);
        assertTrue(AutoStringing.choose(network, OPEN, PLACED, List.of(FAR), 100).isEmpty());
        assertEquals(NEAR, AutoStringing.choose(network, OPEN, PLACED, List.of(NEAR, FAR), 100).orElseThrow().target());
    }

    @Test
    void aTooSteepArmIsPassedOverForTheNextNearest() {
        var above = new Arm(2, 12, 0);
        assertEquals(FAR, AutoStringing.choose(network, OPEN, PLACED, List.of(above, FAR), 100).orElseThrow().target());
    }

    @Test
    void anArmTheLineWouldBendTooSharplyThroughIsPassedOver() {
        // NEAR's Span runs on east; back west from it to PLACED would double back
        network.add(Span.of(NEAR, FAR, 6));
        var placed = new Arm(6, 5, 6);
        var choice = AutoStringing.choose(network, OPEN, placed, List.of(NEAR, new Arm(-2, 5, 6)), 100).orElseThrow();
        assertEquals(new Arm(-2, 5, 6), choice.target());
    }

    @Test
    void aSpanRefusedForGradeOrBendIsWhatThePreviewShowsWhenNothingStrings() {
        var above = new Arm(2, 12, 0);
        assertTrue(AutoStringing.choose(network, OPEN, PLACED, List.of(above), 100).isEmpty());
        assertEquals(above, AutoStringing.refused(network, OPEN, PLACED, List.of(above), 100).orElseThrow());
        network.add(Span.of(NEAR, FAR, 6));
        var placed = new Arm(6, 5, 6);
        assertEquals(NEAR, AutoStringing.refused(network, OPEN, placed, List.of(NEAR), 100).orElseThrow());
    }

    @Test
    void nothingIsShownRefusedWhereASpanStringsOrTheRefusalIsAnotherKind() {
        var above = new Arm(2, 12, 0);
        assertTrue(AutoStringing.refused(network, OPEN, PLACED, List.of(above, FAR), 100).isEmpty());
        assertTrue(AutoStringing.refused(network, OPEN, PLACED, List.of(new Arm(33, 5, 0)), 100).isEmpty());
        assertTrue(AutoStringing.refused(network, OPEN, PLACED, List.of(NEAR), 0).isEmpty());
    }

    @Test
    void passesOverAnArmWhoseSpanWouldBeBlockedForTheNextNearest() {
        Clearance.Blocks inTheWay = (x, y, z) -> x == 3 && y == 7 && z == 0;
        Arm north = new Arm(0, 5, 8);
        assertEquals(north, AutoStringing.choose(network, inTheWay, PLACED, List.of(NEAR, north), 100).orElseThrow().target());
        assertTrue(AutoStringing.choose(network, inTheWay, PLACED, List.of(NEAR), 100).isEmpty());
        assertEquals(NEAR, AutoStringing.refused(network, inTheWay, PLACED, List.of(NEAR), 100).orElseThrow());
    }
}
