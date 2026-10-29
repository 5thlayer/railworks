// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * The space a car straddling the Rail passes through, above a Span and down both sides of its web
 * (ADR 0006), as the blocks it reaches into: a car's box at each spot along the Span's curve,
 * {@link CarBody#WIDTH} square and {@link CarBody#HEIGHT} up from the flange's top. Centred on the
 * Rail, a two-wide box reaches halfway into the blocks either side, so a straight Span's Clearance
 * is three blocks across (ADR 0010), and a block past either Arm. A block that only touches the box
 * is outside it, so the row two off a Straight leg at a Cargo car's chests' height, where Stations,
 * hatches and Chargers stand, is clear.
 * Another Span's Rail is no block, so one line passes over or under another at any height
 * (ADR 0002).
 *
 * <p>Pure: no Minecraft types. The world is asked only which blocks are filled, through
 * {@link Blocks}.
 */
public final class Clearance {

    /** How finely a Span's curve is swept, in blocks along it: well under a car's width, so the boxes overlap. */
    static final double SWEEP_STEP = 0.25;
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

    /** The blocks the car's box at {@code p}, a spot on a curve in Arm coordinates, reaches into. */
    public static SequencedSet<Cell> car(Place.Point p) {
        SequencedSet<Cell> cells = new LinkedHashSet<>();
        addCar(cells, p);
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
        for (int i = 0; i <= steps; i++) {
            addCar(cells, curve.at(curve.length() * i / steps).point());
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

    private static void addCar(SequencedSet<Cell> cells, Place.Point p) {
        double half = CarBody.WIDTH / 2.0;
        double x = p.x() + RailGeometry.ATTACH_X;
        double y = p.y() + RailGeometry.RIDE_Y;
        double z = p.z() + RailGeometry.ATTACH_Z;
        int minX = floor(x - half + TOUCH);
        int maxX = floor(x + half - TOUCH);
        int minY = floor(y + TOUCH);
        int maxY = floor(y + CarBody.HEIGHT - TOUCH);
        int minZ = floor(z - half + TOUCH);
        int maxZ = floor(z + half - TOUCH);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cy = minY; cy <= maxY; cy++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    cells.add(new Cell(cx, cy, cz));
                }
            }
        }
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
