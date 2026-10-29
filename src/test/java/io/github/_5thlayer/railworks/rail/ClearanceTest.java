// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClearanceTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(4, 5, 0);

    private final RailNetwork network = new RailNetwork();

    @AfterEach
    void resetLimits() {
        SpanRules.configureGrade(SpanRules.DEFAULT_GRADE_LIMIT);
    }

    @Test
    void aStraightSpansClearanceIsThreeBlocksAcrossAndThreeUpFromArmToArmAndABlockPast() {
        // A car two wide centred on the Rail reaches halfway into the blocks either side (ADR 0010),
        // and its box at either Arm a block past it.
        assertEquals(cells(-1, 5, 6, 8, -1, 1), new HashSet<>(Clearance.along(RailCurve.between(network, A, B))));
    }

    @Test
    void aDiagonalSpanReachesTwoBlocksEitherSideOfItsLineButNoFarther() {
        var cells = Clearance.along(RailCurve.between(network, A, new Arm(4, 5, 4)));
        assertTrue(cells.contains(new Clearance.Cell(2, 6, 0)));
        assertTrue(cells.contains(new Clearance.Cell(0, 6, 2)));
        assertFalse(cells.contains(new Clearance.Cell(3, 6, 0)));
        assertFalse(cells.contains(new Clearance.Cell(0, 6, 3)));
    }

    @Test
    void aSpansOwnPylonsAreNotInItsClearanceHoweverSteepItClimbs() {
        SpanRules.configureGrade(1.0);
        Arm top = new Arm(3, 8, 0);
        var cells = Clearance.along(RailCurve.between(network, A, top));
        for (Arm arm : new Arm[] {A, top}) {
            for (int y = arm.y() - 10; y <= arm.y(); y++) {
                assertFalse(cells.contains(new Clearance.Cell(arm.x(), y, arm.z())), "column of " + arm + " at " + y);
            }
        }
        assertTrue(cells.contains(new Clearance.Cell(3, 9, 0)), "the block on top of the Arm is in the way");
    }

    @Test
    void aSpanBendingIntoTheNextFollowsItsCurveOffTheStraightLine() {
        Arm b = new Arm(20, 5, 0);
        network.add(Span.of(b, new Arm(30, 5, 10), 15));
        var cells = Clearance.along(RailCurve.between(network, A, b));
        assertTrue(cells.stream().anyMatch(cell -> cell.z() < -1), "the curve swings out before turning");
        assertTrue(Clearance.along(RailCurve.between(new RailNetwork(), A, b)).stream().allMatch(cell -> Math.abs(cell.z()) <= 1));
    }

    @Test
    void theRowTwoOffAStraightLegAtTheChestsHeightIsClear() {
        // Where Stations, hatches and Chargers stand (ADR 0010): two off the line, the second block above the Rail.
        var cells = Clearance.along(RailCurve.between(network, A, B));
        for (int x = 0; x <= 4; x++) {
            assertTrue(cells.contains(new Clearance.Cell(x, 7, 1)), "the block between the row and the Rail, south at " + x);
            assertTrue(cells.contains(new Clearance.Cell(x, 7, -1)), "the block between the row and the Rail, north at " + x);
            assertFalse(cells.contains(new Clearance.Cell(x, 7, 2)), "south at " + x);
            assertFalse(cells.contains(new Clearance.Cell(x, 7, -2)), "north at " + x);
        }
    }

    @Test
    void aCarFillsTheThreeBlocksAcrossAndUpAroundTheRailWhereItStands() {
        assertEquals(cells(1, 3, 6, 8, -1, 1), Set.copyOf(Clearance.car(new Place.Point(2.0, 5.0, 0.0))));
    }

    @Test
    void theFirstFilledBlockIsTheOneNearestTheFirstArm() {
        var cells = Clearance.along(RailCurve.between(network, A, B));
        Clearance.Blocks filled = (x, y, z) -> y == 7 && z == 0 && (x == 3 || x == 1);
        assertEquals(new Clearance.Cell(1, 7, 0), Clearance.firstFilled(cells, filled).orElseThrow());
        assertTrue(Clearance.firstFilled(cells, Clearance.Blocks.OPEN).isEmpty());
    }

    /** Every block from x0 to x1, y0 to y1 and z0 to z1, the ends included. */
    private static Set<Clearance.Cell> cells(int x0, int x1, int y0, int y1, int z0, int z1) {
        Set<Clearance.Cell> cells = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    cells.add(new Clearance.Cell(x, y, z));
                }
            }
        }
        return cells;
    }
}
