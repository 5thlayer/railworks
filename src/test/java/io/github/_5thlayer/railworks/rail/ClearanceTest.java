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
        SpanRules.configureBend(SpanRules.DEFAULT_BEND_LIMIT);
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
        // Bent through both Arms at the limit, 45 degrees, from the shortest diagonal to a long one,
        // level and climbing at about the grade limit, a quarter. The Span's own Pylon columns are
        // not in its Clearance, though a climbing body's tail dips into its Arms as it leaves them.
        for (int k = 2; k <= 21; k++) {
            for (int climb : new int[] {0, k / 3}) {
                Arm b = new Arm(k, 5 + climb, k);
                RailNetwork bent = new RailNetwork();
                bent.add(Span.of(new Arm(-k, 5, 0), A, 1));
                bent.add(Span.of(b, new Arm(2 * k, 5 + climb, k), 1));
                RailCurve curve = RailCurve.between(bent, A, b);
                var cells = Clearance.along(curve);
                for (double s = 0.0; s <= curve.length(); s += 0.025) {
                    for (Clearance.Cell cell : body(curve.at(s))) {
                        boolean column = (cell.x() == A.x() && cell.z() == A.z() && cell.y() <= A.y())
                                || (cell.x() == b.x() && cell.z() == b.z() && cell.y() <= b.y());
                        assertTrue(column || cells.contains(cell),
                                "diagonal " + k + " climbing " + climb + ", body " + s + " along, at " + cell);
                    }
                }
            }
        }
    }

    @Test
    void aBentSpanOfTheSwingClearLengthAfterAStraightLegLeavesItsRowClearAndAShorterOneDoesNot() {
        // At the default limit, 45 degrees, bending onto a diagonal of any length: 4 blocks.
        assertEquals(4, Clearance.swingClearLength());
        expectSwingClear(Clearance.swingClearLength(), 1, 1);
    }

    @Test
    void aSharperConfiguredBendLimitNeedsALongerBentSpanAndAGentlerOneNoLonger() {
        SpanRules.configureBend(90.0);
        int right = Clearance.swingClearLength();
        assertTrue(right > 4, "a right angle swings clear only " + right + " on");
        expectSwingClear(right, 0, 1);
        SpanRules.configureBend(Math.toDegrees(Math.atan2(1, 2)));
        int gentle = Clearance.swingClearLength();
        assertTrue(gentle <= 4, "a gentle bend swings clear only " + gentle + " on");
        expectSwingClear(gentle, 2, 1);
    }

    /**
     * A Straight leg east to A, then a Span on in line to B, bending there onto a Span heading
     * ({@code dx}, {@code dz}), at every length up to ten times that: shorter than {@code length} its
     * cars swing into the row two off the leg at a Cargo car's chests' height, where hatches stand, for
     * some length beyond, and from {@code length} to a dozen more blocks they never do.
     */
    private static void expectSwingClear(int length, int dx, int dz) {
        for (int bent = Math.max(1, length - 1); bent <= length + 12; bent++) {
            boolean clash = false;
            for (int k = 1; k <= 10; k++) {
                Arm b = new Arm(bent, 5, 0);
                Arm c = new Arm(bent + dx * k, 5, dz * k);
                RailNetwork line = new RailNetwork();
                line.add(Span.of(new Arm(-20, 5, 0), A, 1));
                line.add(Span.of(A, b, 1));
                line.add(Span.of(b, c, 1));
                Set<Clearance.Cell> cells = new HashSet<>(Clearance.along(line.curve(line.get(A, b))));
                cells.addAll(Clearance.along(line.curve(line.get(b, c))));
                for (int x = -20; x <= 0; x++) {
                    clash |= cells.contains(new Clearance.Cell(x, 7, 2)) || cells.contains(new Clearance.Cell(x, 7, -2));
                }
            }
            assertEquals(bent < length, clash, "a bent Span " + bent + " long, onto (" + dx + ", " + dz + ")");
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
     * The blocks a rigid body {@link Clearance#BODY_LENGTH} long, {@link CarBody#WIDTH} across and
     * {@link CarBody#HEIGHT} up from the flange's top, on the body's own axes at {@code pose}, reaches
     * into: found by points spread through it, a hair in from its faces, so a block it only touches is
     * not one.
     */
    private static Set<Clearance.Cell> body(RailCurve.Pose pose) {
        double[][] axes = Clearance.Body.axes(pose);
        double[] rail = {pose.x() + RailGeometry.ATTACH_X, pose.y() + RailGeometry.RIDE_Y, pose.z() + RailGeometry.ATTACH_Z};
        double halfLength = Clearance.BODY_LENGTH / 2.0;
        double halfWidth = CarBody.WIDTH / 2.0;
        double in = 1e-3;
        Set<Clearance.Cell> cells = new HashSet<>();
        for (double u = -halfLength + in; u <= halfLength - in + 1e-9; u += (2 * halfLength - 2 * in) / 48) {
            for (double v = -halfWidth + in; v <= halfWidth - in + 1e-9; v += (2 * halfWidth - 2 * in) / 20) {
                for (double w = in; w <= CarBody.HEIGHT - in + 1e-9; w += (CarBody.HEIGHT - 2 * in) / 4) {
                    double[] point = new double[3];
                    for (int i = 0; i < 3; i++) {
                        point[i] = rail[i] + u * axes[0][i] + v * axes[1][i] + w * axes[2][i];
                    }
                    cells.add(new Clearance.Cell((int) Math.floor(point[0]), (int) Math.floor(point[1]), (int) Math.floor(point[2])));
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
