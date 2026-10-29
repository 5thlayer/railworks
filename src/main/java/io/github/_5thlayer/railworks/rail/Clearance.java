// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * The space a car straddling the Rail passes through, above a Span and down both sides of its web
 * (ADR 0006), as the blocks it reaches into: a car's body at each spot along the Span's curve, a
 * rigid box {@link #BODY_LENGTH} long, {@link CarBody#WIDTH} across and {@link CarBody#HEIGHT} up from
 * the flange's top, its middle on the curve and turned and pitched the way the curve runs there, as
 * a car rides it. Centred on the Rail, a two-wide body reaches halfway into the blocks either side,
 * so a straight Span's Clearance is three blocks across (ADR 0010), and two blocks past either Arm,
 * where a car's middle may stand. On a bend the body's ends swing out of the curve and its middle
 * cuts in, so a bent Span's Clearance is wider than three blocks there. A block that only touches the
 * body is outside it, so the row two off a Straight leg at a Cargo car's chests' height, where Stations,
 * hatches and Chargers stand, is clear.
 * Another Span's Rail is no block, so one line passes over or under another at any height
 * (ADR 0002).
 *
 * <p>Pure: no Minecraft types. The world is asked only which blocks are filled, through
 * {@link Blocks}.
 */
public final class Clearance {

    /**
     * How finely a Span's curve is swept, in blocks along it: well under a car's width, so the bodies
     * overlap. Each is widened by how far its ends swing to the next, so none is missed between.
     */
    static final double SWEEP_STEP = 0.25;
    /** A car's body: every car is a Locomotive's length (ADR 0010), less {@link CarBody#GAP}. */
    public static final double BODY_LENGTH = Locomotive.LENGTH - CarBody.GAP;
    /**
     * How far a Span's Clearance may reach past the box of its two Arms, any way: the curve swinging
     * out of it on a bend, a car's body, whose corner stands half its length and half its width from
     * its middle, and a car's height on top. A cheap test before the sweep.
     */
    public static final int REACH = 4 + (int) Math.ceil(Math.hypot(BODY_LENGTH / 2.0, CarBody.WIDTH / 2.0))
            + (int) Math.ceil(CarBody.HEIGHT);
    /**
     * The shortest bent Span a Straight leg may run on into, in line, with its row two off the Rail
     * at a Cargo car's chests' height, where hatches, Stations and Chargers stand, left clear: on a
     * shorter one, bending at the bend limit at its far Arm, a car's body swings into that row beside
     * the leg's last blocks. Measured by {@code ClearanceTest} at the default bend limit, 45 degrees:
     * a bent Span 3 long reaches one row block, 2 long two, 1 long three each side, and from 4 on
     * none, whichever way it bends and however long the Span beyond.
     */
    public static final int SWING_CLEAR_LENGTH = 4;
    /** Slack for a block that only touches the car's box, so the floating point never tips a touch into an overlap. */
    private static final double TOUCH = 1e-6;

    /** A block position. */
    public record Cell(int x, int y, int z) {
    }

    /**
     * The seam to the world: whether the block at a position is filled, so a car would hit it. The
     * world says which blocks those are; a block a car passes through, such as a torch, is not.
     */
    @FunctionalInterface
    public interface Blocks {

        /** A world with nothing in the way. */
        Blocks OPEN = (x, y, z) -> false;

        boolean filled(int x, int y, int z);
    }

    private Clearance() {
    }

    /** The blocks the car's body at {@code pose}, a spot on a curve in Arm coordinates, reaches into. */
    public static SequencedSet<Cell> car(RailCurve.Pose pose) {
        SequencedSet<Cell> cells = new LinkedHashSet<>();
        Body.at(pose, 0.0, 0.0).addCells(cells);
        return cells;
    }

    /**
     * The blocks a car riding {@code curve} from end to end passes through, nearest its first Arm
     * first: a Span's Clearance. Its own two Pylon columns are not in it, the Arms and the segments
     * under them, which a steep Span's car would otherwise clip as it leaves an Arm; the block on top
     * of an Arm is.
     */
    public static SequencedSet<Cell> along(RailCurve curve) {
        SequencedSet<Cell> cells = new LinkedHashSet<>();
        int steps = Math.max(1, (int) Math.ceil(curve.length() / SWEEP_STEP));
        RailCurve.Pose[] poses = new RailCurve.Pose[steps + 1];
        for (int i = 0; i <= steps; i++) {
            poses[i] = curve.at(curve.length() * i / steps);
        }
        for (int i = 0; i <= steps; i++) {
            // How far its ends swing, across and up, on their way to the step either side.
            double yaw = 0.0;
            double pitch = 0.0;
            for (int j = i - 1; j <= i + 1; j += 2) {
                if (j >= 0 && j <= steps) {
                    yaw = Math.max(yaw, Math.abs(yaw(poses[j]) - yaw(poses[i])));
                    pitch = Math.max(pitch, Math.abs(pitch(poses[j]) - pitch(poses[i])));
                }
            }
            // An end stands half the body from its middle, which moves a step on: how far it swings is
            // that by the angle turned, the yaw the short way round across its wrap at a half turn.
            double endFromMiddle = BODY_LENGTH / 2.0 + curve.length() / steps;
            Body.at(poses[i], endFromMiddle * Math.min(yaw, 2 * Math.PI - yaw), endFromMiddle * pitch).addCells(cells);
        }
        cells.removeIf(cell -> underArm(cell, curve.a()) || underArm(cell, curve.b()));
        return cells;
    }

    /** The first of {@code cells} that {@code blocks} says is filled, or empty when they are all clear. */
    public static Optional<Cell> firstFilled(Collection<Cell> cells, Blocks blocks) {
        return cells.stream().filter(cell -> blocks.filled(cell.x(), cell.y(), cell.z())).findFirst();
    }

    private static boolean underArm(Cell cell, Arm arm) {
        return cell.x() == arm.x() && cell.z() == arm.z() && cell.y() <= arm.y();
    }

    /** Which way {@code pose} heads, level, in radians. */
    private static double yaw(RailCurve.Pose pose) {
        return Math.atan2(pose.dz(), pose.dx());
    }

    /** How steeply {@code pose} climbs, in radians. */
    private static double pitch(RailCurve.Pose pose) {
        return Math.atan2(pose.dy(), Math.sqrt(pose.dx() * pose.dx() + pose.dz() * pose.dz()));
    }

    /**
     * A car's body at a spot on the curve, as a box in the world: its middle, its own unit axes,
     * along the curve, across it level and up from the floor, and how far it reaches along each.
     */
    record Body(double[] middle, double[][] axes, double[] halves) {

        /**
         * The body at {@code pose}: {@link #BODY_LENGTH} long and {@link CarBody#WIDTH} across,
         * centred on the flange's top, turned the way the curve runs there and pitched with it, and
         * {@link CarBody#HEIGHT} up from the floor; {@code swingAcross} wider each side and
         * {@code swingUp} taller each way, to cover a swing between steps.
         */
        static Body at(RailCurve.Pose pose, double swingAcross, double swingUp) {
            double[][] axes = axes(pose);
            double[] up = axes[2];
            double[] middle = new double[3];
            double[] rail = {pose.x() + RailGeometry.ATTACH_X, pose.y() + RailGeometry.RIDE_Y, pose.z() + RailGeometry.ATTACH_Z};
            // Half its height up its own up from the floor.
            for (int i = 0; i < 3; i++) {
                middle[i] = rail[i] + up[i] * CarBody.HEIGHT / 2.0;
            }
            return new Body(middle, axes,
                    new double[] {BODY_LENGTH / 2.0, CarBody.WIDTH / 2.0 + swingAcross, CarBody.HEIGHT / 2.0 + swingUp});
        }

        /** The body's own axes at {@code pose}, unit: along the curve, across it level, and up from the floor. */
        static double[][] axes(RailCurve.Pose pose) {
            double[] along = {pose.dx(), pose.dy(), pose.dz()};
            double horizontal = Math.sqrt(pose.dx() * pose.dx() + pose.dz() * pose.dz());
            double[] across = horizontal < 1e-9 ? new double[] {1.0, 0.0, 0.0}
                    : new double[] {-pose.dz() / horizontal, 0.0, pose.dx() / horizontal};
            double[] up = cross(along, across);
            if (up[1] < 0) {
                up = new double[] {-up[0], -up[1], -up[2]};
            }
            return new double[][] {along, across, up};
        }

        /** Adds the blocks it reaches into: each within its bounds that it {@link #overlaps}. */
        void addCells(SequencedSet<Cell> cells) {
            int[] min = new int[3];
            int[] max = new int[3];
            for (int i = 0; i < 3; i++) {
                double extent = 0.0;
                for (int a = 0; a < 3; a++) {
                    extent += Math.abs(axes[a][i]) * halves[a];
                }
                min[i] = floor(middle[i] - extent + TOUCH);
                max[i] = floor(middle[i] + extent - TOUCH);
            }
            for (int cx = min[0]; cx <= max[0]; cx++) {
                for (int cy = min[1]; cy <= max[1]; cy++) {
                    for (int cz = min[2]; cz <= max[2]; cz++) {
                        if (overlaps(cx, cy, cz)) {
                            cells.add(new Cell(cx, cy, cz));
                        }
                    }
                }
            }
        }

        /**
         * Whether it overlaps the block at ({@code x}, {@code y}, {@code z}) by more than
         * {@link #TOUCH}: the separating axis test, over its three axes, the block's three and the
         * nine crossings of the two.
         */
        boolean overlaps(int x, int y, int z) {
            double[] apart = {x + 0.5 - middle[0], y + 0.5 - middle[1], z + 0.5 - middle[2]};
            double[][] world = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
            for (double[] axis : axes) {
                if (separates(axis, apart)) {
                    return false;
                }
            }
            for (double[] axis : world) {
                if (separates(axis, apart)) {
                    return false;
                }
            }
            for (double[] own : axes) {
                for (double[] block : world) {
                    double[] axis = cross(own, block);
                    double norm = Math.sqrt(dot(axis, axis));
                    if (norm > 1e-9 && separates(new double[] {axis[0] / norm, axis[1] / norm, axis[2] / norm}, apart)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Whether it and the block, {@code apart} middle to middle, lie apart along the unit {@code axis}, but for a touch. */
        private boolean separates(double[] axis, double[] apart) {
            double box = 0.0;
            for (int a = 0; a < 3; a++) {
                box += Math.abs(dot(axes[a], axis)) * halves[a];
            }
            double block = 0.5 * (Math.abs(axis[0]) + Math.abs(axis[1]) + Math.abs(axis[2]));
            return Math.abs(dot(apart, axis)) >= box + block - TOUCH;
        }
    }

    private static double dot(double[] one, double[] other) {
        return one[0] * other[0] + one[1] * other[1] + one[2] * other[2];
    }

    private static double[] cross(double[] one, double[] other) {
        return new double[] {
                one[1] * other[2] - one[2] * other[1],
                one[2] * other[0] - one[0] * other[2],
                one[0] * other[1] - one[1] * other[0]
        };
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
