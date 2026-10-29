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
    void aStraightSpansClearanceIsThreeBlocksAcrossAndThreeUpFromArmToArmAndTwoBlocksPast() {
        // A car two wide centred on the Rail reaches halfway into the blocks either side (ADR 0010),
        // and its body, 4.8 long, with its middle at either Arm two blocks past it.
        assertEquals(cells(-2, 6, 6, 8, -1, 1), new HashSet<>(Clearance.along(RailCurve.between(network, A, B))));
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
    void aCarFillsTheFiveBlocksAlongAndThreeAcrossAndUpAroundTheRailWhereItStands() {
        // Its body 4.8 long, 0.1 short of the blocks at either end.
        assertEquals(cells(0, 4, 6, 8, -1, 1), Set.copyOf(Clearance.car(new RailCurve.Pose(2.0, 5.0, 0.0, 1.0, 0.0, 0.0))));
    }

    @Test
    void theFirstFilledBlockIsTheOneNearestTheFirstArm() {
        var cells = Clearance.along(RailCurve.between(network, A, B));
        Clearance.Blocks filled = (x, y, z) -> y == 7 && z == 0 && (x == 3 || x == 1);
        assertEquals(new Clearance.Cell(1, 7, 0), Clearance.firstFilled(cells, filled).orElseThrow());
        assertTrue(Clearance.firstFilled(cells, Clearance.Blocks.OPEN).isEmpty());
    }

    @Test
    void aRigidBodyAtTheBendLimitStaysInsideABentSpansClearanceAtEveryStep() {
        // Bent through both Arms at the limit, 45 degrees, from the shortest diagonal to a long one.
        for (int k = 2; k <= 21; k++) {
            Arm b = new Arm(k, 5, k);
            RailNetwork bent = new RailNetwork();
            bent.add(Span.of(new Arm(-k, 5, 0), A, 1));
            bent.add(Span.of(b, new Arm(2 * k, 5, k), 1));
            RailCurve curve = RailCurve.between(bent, A, b);
            var cells = Clearance.along(curve);
            for (double s = 0.0; s <= curve.length(); s += 0.025) {
                for (Clearance.Cell cell : body(curve.at(s))) {
                    assertTrue(cells.contains(cell), "diagonal " + k + ", body " + s + " along, at " + cell);
                }
            }
        }
    }

    @Test
    void aSpansClearanceStaysWithinItsReachOfItsArmsAtTheLengthAndBendLimits() {
        // The longest Span, bent at the limit through both Arms, either way round and climbing.
        for (int climb : new int[] {0, 8}) {
            for (int turn : new int[] {-1, 1}) {
                Arm b = new Arm(32, 5 + climb, 0);
                RailNetwork bent = new RailNetwork();
                bent.add(Span.of(new Arm(-10, 5, -10), A, 1));
                bent.add(Span.of(b, new Arm(42, 5 + climb, 10 * turn), 1));
                for (Clearance.Cell cell : Clearance.along(RailCurve.between(bent, A, b))) {
                    assertTrue(cell.x() >= -Clearance.REACH && cell.x() <= 32 + Clearance.REACH
                            && cell.y() >= 5 - Clearance.REACH && cell.y() <= 5 + climb + Clearance.REACH
                            && Math.abs(cell.z()) <= Clearance.REACH, "climb " + climb + ", turn " + turn + ", " + cell);
                }
            }
        }
    }

    /**
     * The blocks a rigid body {@code 4.8} long, two wide and {@code 2.75} up from the flange's top,
     * turned and pitched to {@code pose}, reaches into: found by points spread through it, a hair in
     * from its faces, so a block it only touches is not one.
     */
    private static Set<Clearance.Cell> body(RailCurve.Pose pose) {
        double horizontal = Math.hypot(pose.dx(), pose.dz());
        double[] along = {pose.dx(), pose.dy(), pose.dz()};
        double[] across = {-pose.dz() / horizontal, 0.0, pose.dx() / horizontal};
        double[] up = {
                along[1] * across[2] - along[2] * across[1],
                along[2] * across[0] - along[0] * across[2],
                along[0] * across[1] - along[1] * across[0]
        };
        if (up[1] < 0) {
            up = new double[] {-up[0], -up[1], -up[2]};
        }
        double x = pose.x() + RailGeometry.ATTACH_X;
        double y = pose.y() + RailGeometry.RIDE_Y;
        double z = pose.z() + RailGeometry.ATTACH_Z;
        double in = 1e-3;
        Set<Clearance.Cell> cells = new HashSet<>();
        for (double u = -2.4 + in; u <= 2.4 - in + 1e-9; u += (4.8 - 2 * in) / 48) {
            for (double v = -1.0 + in; v <= 1.0 - in + 1e-9; v += (2.0 - 2 * in) / 20) {
                for (double w = in; w <= 2.75 - in + 1e-9; w += (2.75 - 2 * in) / 4) {
                    cells.add(new Clearance.Cell(
                            (int) Math.floor(x + u * along[0] + v * across[0] + w * up[0]),
                            (int) Math.floor(y + u * along[1] + v * across[1] + w * up[1]),
                            (int) Math.floor(z + u * along[2] + v * across[2] + w * up[2])));
                }
            }
        }
        return cells;
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
