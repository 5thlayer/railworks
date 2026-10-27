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
    void aStraightSpansClearanceIsTheThreeBlocksAboveTheRailFromArmToArm() {
        Set<Clearance.Cell> expected = new HashSet<>();
        for (int x = 0; x <= 4; x++) {
            for (int y = 6; y <= 8; y++) {
                expected.add(new Clearance.Cell(x, y, 0));
            }
        }
        assertEquals(expected, new HashSet<>(Clearance.along(RailCurve.between(network, A, B))));
    }

    @Test
    void aDiagonalSpanReachesTheBlocksEitherSideOfItsLineButNoFarther() {
        var cells = Clearance.along(RailCurve.between(network, A, new Arm(4, 5, 4)));
        assertTrue(cells.contains(new Clearance.Cell(1, 6, 0)));
        assertTrue(cells.contains(new Clearance.Cell(0, 6, 1)));
        assertFalse(cells.contains(new Clearance.Cell(2, 6, 0)));
        assertFalse(cells.contains(new Clearance.Cell(0, 6, 2)));
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
        assertTrue(cells.stream().anyMatch(cell -> cell.z() < 0), "the curve swings out before turning");
        assertTrue(Clearance.along(RailCurve.between(new RailNetwork(), A, b)).stream().allMatch(cell -> cell.z() == 0));
    }

    @Test
    void aCarFillsTheThreeBlocksAboveTheRailWhereItStands() {
        assertEquals(Set.of(new Clearance.Cell(2, 6, 0), new Clearance.Cell(2, 7, 0), new Clearance.Cell(2, 8, 0)),
                Set.copyOf(Clearance.car(new Place.Point(2.0, 5.0, 0.0))));
    }

    @Test
    void theFirstFilledBlockIsTheOneNearestTheFirstArm() {
        var cells = Clearance.along(RailCurve.between(network, A, B));
        Clearance.Blocks filled = (x, y, z) -> y == 7 && (x == 3 || x == 1);
        assertEquals(new Clearance.Cell(1, 7, 0), Clearance.firstFilled(cells, filled).orElseThrow());
        assertTrue(Clearance.firstFilled(cells, Clearance.Blocks.OPEN).isEmpty());
    }
}
