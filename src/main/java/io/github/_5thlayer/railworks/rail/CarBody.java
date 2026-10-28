// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.List;

/**
 * The boxes a car is drawn from: a boxy car straddling the Rail, its shell riding on top with
 * skirts down both sides of the web, the chests set into a Cargo car's sides, and a Fluid car's tank. Coordinates are
 * the car's own: x across, y up from the floor's underside on the flange's top, z along the car with
 * +z the end it faces. Placeholder art, so {@code CarRenderer} only paints these and nothing else
 * decides their sizes. It also holds the car's size and where the Locomotive's Driver sits in it and
 * its Rider stands, so the boxes, the seats and the Driver's view out of the cab are checked together. A body is
 * {@link #length()} long, from {@code -length / 2} to {@code length / 2} along z.
 *
 * <p>Where two boxes meet, one stops a little short of the other's faces or reaches into it, so no
 * two faces share a plane and the depth test never has to choose between them.
 *
 * <p>Pure: no Minecraft types.
 */
public record CarBody(double length, List<Part> parts, List<Box> chests) {

    /** A car's box across: a block. */
    public static final double WIDTH = 1.0;
    /** A car's box up from the floor's underside. */
    public static final double HEIGHT = 2.75;
    /**
     * What a car's body falls short of its length, coupler to coupler, half at each end: two coupled
     * bodies stand this far apart. Every car is a whole number of blocks long, so a stopped Train's
     * cars line up with the blocks.
     */
    public static final double GAP = 0.2;

    /** What colour a part is drawn in, red, green and blue. */
    public enum Paint {
        /** The shell, rust red as the Rail is. */
        SHELL(0.62F, 0.27F, 0.18F),
        /** The skirts and the gangway doors, a darker rust than the shell. */
        DARK_RUST(0.40F, 0.17F, 0.12F),
        /** The window bands and the cab's frame, near black. */
        TRIM(0.13F, 0.08F, 0.07F),
        /** The band across the Locomotive's front, deep red. */
        DEEP_RED(0.30F, 0.10F, 0.08F),
        /** A headlight. */
        LAMP(0.95F, 0.90F, 0.62F),
        /** The Electric Locomotive's nose band, copper. */
        COPPER(0.78F, 0.45F, 0.28F),
        /** A Fluid car's tank, a paler rust than the shell. */
        TANK(0.72F, 0.42F, 0.33F);

        public final float red;
        public final float green;
        public final float blue;

        Paint(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    /**
     * An axis-aligned box in the car's coordinates. Among a body's {@link #chests()}, a cube a vanilla
     * chest fills as it would a block, drawn with its own texture, its front out of the car's nearer
     * side.
     */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        /** The same box on the other side of the car. */
        Box mirrored() {
            return new Box(-maxX, minY, minZ, -minX, maxY, maxZ);
        }

        /** The same box at the other end of the car. */
        Box turned() {
            return new Box(minX, minY, -maxZ, maxX, maxY, -minZ);
        }
    }

    /** A painted box of the car's. */
    public record Part(Paint paint, Box box) {

        Part(Paint paint, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this(paint, new Box(minX, minY, minZ, maxX, maxY, maxZ));
        }

        Part mirrored() {
            return new Part(paint, box.mirrored());
        }

        Part turned() {
            return new Part(paint, box.turned());
        }
    }

    /** A point in the car's own coordinates. */
    public record Point(double x, double y, double z) {
    }

    /**
     * A seated player's eye above the seat: their 1.62 eye height less the 0.6 they sit below a
     * seat ({@code Avatar.DEFAULT_VEHICLE_ATTACHMENT}).
     */
    private static final double EYE_ABOVE_SEAT = 1.62 - 0.6;

    /** The window bands, at one height on every car so they line up along a Train. */
    private static final double BAND_BOTTOM = 1.64;
    private static final double BAND_TOP = 2.5;

    /** The Locomotive's open side windows, along the cab inside the band's frame. */
    private static final double WINDOW_SILL = 1.7;
    private static final double WINDOW_HEAD = 2.44;
    private static final double WINDOW_BACK = 0.25;
    private static final double WINDOW_FRONT = 1.24;

    /** The Locomotive's body, its cab's back wall, and the low wall around the open bay behind it. */
    private static final double LOCOMOTIVE_LENGTH = Locomotive.LENGTH - GAP;
    private static final double CAB_BACK = -0.4;
    private static final double BAY_WALL_TOP = 1.3;
    /** The top of every car's floor, over its underside. */
    private static final double FLOOR_TOP = 0.7;

    /**
     * Where the Locomotive's Driver sits, in the cab, above the floor's underside and forward of the
     * middle. Only a Locomotive is ridden.
     */
    public static final Point CAB_SEAT = new Point(0.0, 1.2, 0.8);
    /**
     * Where the Locomotive's Rider stands, mob or player: on the bay's floor, not raised to a seat,
     * halfway between the cab's back and the car's back end.
     */
    public static final Point BAY_SPOT = new Point(0.0, FLOOR_TOP, (CAB_BACK - LOCOMOTIVE_LENGTH / 2.0) / 2.0);

    public static final CarBody LOCOMOTIVE = locomotive();
    public static final CarBody ELECTRIC = electric();
    public static final CarBody CARGO = cargo();
    public static final CarBody FLUID = fluid();

    /** Where the Locomotive's Driver's eye is. */
    public static Point eye() {
        return new Point(CAB_SEAT.x(), CAB_SEAT.y() + EYE_ABOVE_SEAT, CAB_SEAT.z());
    }

    /** What every car shares: the skirts and the floor, along a body {@code length} long. */
    private static List<Part> shell(double length) {
        double half = length / 2.0;
        List<Part> parts = new ArrayList<>();
        sides(parts, new Part(Paint.DARK_RUST, 0.17, 0.0, -half + 0.01, 0.49, 0.62, half - 0.01));
        parts.add(new Part(Paint.SHELL, -0.5, 0.55, -half, 0.5, FLOOR_TOP, half));
        return parts;
    }

    /** A roof from {@code back} to {@code front}. */
    private static Part roof(double back, double front) {
        return new Part(Paint.SHELL, -0.5, 2.6, back, 0.5, 2.75, front);
    }

    /**
     * The Locomotive: a cab at the front and an open bay behind it. The cab has a closed back with a
     * door into the bay, an open front framed in dark by the roof's edge, two corner pillars and a
     * low dash, with a deep red nose and two headlights under it, and an open window in each side,
     * framed by the band. The bay has no roof, only a low wall with a dark rail along its sides and
     * back.
     */
    private static CarBody locomotive() {
        double half = LOCOMOTIVE_LENGTH / 2.0;
        List<Part> parts = shell(LOCOMOTIVE_LENGTH);
        parts.add(roof(CAB_BACK, half));
        // The cab's side wall below and above the window, then behind and ahead of it; the band runs
        // on behind and ahead of the window and frames it above and below.
        sides(parts, new Part(Paint.SHELL, 0.42, 0.68, CAB_BACK + 0.015, 0.49, WINDOW_SILL - 0.005, half - 0.06));
        sides(parts, new Part(Paint.SHELL, 0.42, WINDOW_HEAD + 0.005, CAB_BACK + 0.015, 0.49, 2.62, half - 0.06));
        sides(parts, new Part(Paint.SHELL, 0.425, BAND_BOTTOM + 0.05, CAB_BACK + 0.02, 0.485, BAND_TOP - 0.05, WINDOW_BACK + 0.005));
        sides(parts, new Part(Paint.SHELL, 0.425, BAND_BOTTOM + 0.05, WINDOW_FRONT - 0.005, 0.485, BAND_TOP - 0.05, half - 0.065));
        sides(parts, new Part(Paint.TRIM, 0.47, BAND_BOTTOM, CAB_BACK, 0.5, BAND_TOP, WINDOW_BACK));
        sides(parts, new Part(Paint.TRIM, 0.47, BAND_BOTTOM, WINDOW_FRONT, 0.5, BAND_TOP, half));
        sides(parts, new Part(Paint.TRIM, 0.472, BAND_BOTTOM + 0.005, WINDOW_BACK - 0.005, 0.498, WINDOW_SILL, WINDOW_FRONT + 0.005));
        sides(parts, new Part(Paint.TRIM, 0.472, WINDOW_HEAD, WINDOW_BACK - 0.005, 0.498, BAND_TOP - 0.005, WINDOW_FRONT + 0.005));
        // The cab's back wall, and its door out into the bay.
        parts.add(new Part(Paint.SHELL, -0.45, 0.67, CAB_BACK + 0.01, 0.45, 2.63, CAB_BACK + 0.08));
        parts.add(new Part(Paint.DARK_RUST, -0.25, 0.75, CAB_BACK, 0.25, 2.35, CAB_BACK + 0.03));
        // The cab's front.
        sides(parts, new Part(Paint.TRIM, 0.3, 0.66, half - 0.14, 0.495, 2.64, half - 0.015));
        parts.add(new Part(Paint.TRIM, -0.49, 2.45, half - 0.05, 0.49, 2.65, half - 0.005));
        parts.add(new Part(Paint.TRIM, -0.31, 0.65, half - 0.12, 0.31, 1.6, half - 0.02));
        parts.add(new Part(Paint.DEEP_RED, -0.5, 0.72, half - 0.04, 0.5, 1.45, half - 0.01));
        sides(parts, new Part(Paint.LAMP, 0.22, 1.1, half - 0.025, 0.36, 1.3, half));
        // The bay's low wall along each side and across the back, reaching into the cab's side walls,
        // and the rail along its top.
        sides(parts, new Part(Paint.SHELL, 0.425, 0.685, -half + 0.015, 0.485, BAY_WALL_TOP, CAB_BACK + 0.02));
        parts.add(new Part(Paint.SHELL, -0.45, 0.69, -half + 0.01, 0.45, BAY_WALL_TOP - 0.01, -half + 0.07));
        sides(parts, new Part(Paint.TRIM, 0.41, BAY_WALL_TOP - 0.005, -half, 0.5, BAY_WALL_TOP + 0.08, CAB_BACK + 0.03));
        parts.add(new Part(Paint.TRIM, -0.495, BAY_WALL_TOP - 0.015, -half + 0.005, 0.495, BAY_WALL_TOP + 0.075, -half + 0.065));
        return new CarBody(LOCOMOTIVE_LENGTH, List.copyOf(parts), List.of());
    }

    /** The Electric Locomotive: the Burner's body, its nose band copper rather than deep red. Placeholder. */
    private static CarBody electric() {
        List<Part> parts = new ArrayList<>();
        for (Part part : LOCOMOTIVE.parts()) {
            parts.add(part.paint() == Paint.DEEP_RED ? new Part(Paint.COPPER, part.box()) : part);
        }
        return new CarBody(LOCOMOTIVE.length(), List.copyOf(parts), List.of());
    }

    /**
     * The Cargo car: closed, of the Locomotive's height and roof line, a gangway door at both ends,
     * and a chest set into a framed recess in the middle of each side.
     */
    private static CarBody cargo() {
        double length = CargoCar.LENGTH - GAP;
        List<Part> parts = shell(length);
        parts.add(roof(-length / 2.0, length / 2.0));
        sides(parts, new Part(Paint.TRIM, 0.47, BAND_BOTTOM, -0.9, 0.5, BAND_TOP, 0.9));
        ends(parts, new Part(Paint.SHELL, -0.45, 0.67, 0.82, 0.45, 2.63, 0.89));
        ends(parts, new Part(Paint.DARK_RUST, -0.25, 0.75, 0.87, 0.25, 2.35, 0.9));
        // The side's inner wall, then the facing around the recess, which stands proud of the wall
        // by the recess's depth.
        sides(parts, new Part(Paint.SHELL, 0.33, 0.68, -0.885, 0.39, 2.62, 0.885));
        Part facing = new Part(Paint.SHELL, 0.38, 0.665, 0.22, 0.49, 2.635, 0.88);
        sides(parts, facing);
        sides(parts, facing.turned());
        sides(parts, new Part(Paint.SHELL, 0.382, 1.48, -0.23, 0.488, 2.625, 0.23));
        sides(parts, new Part(Paint.SHELL, 0.382, 0.675, -0.23, 0.488, 1.07, 0.23));
        // The chest fills a cube in the recess, its lock just behind the facing.
        Box chest = new Box(0.05, 1.075, -0.195, 0.44, 1.465, 0.195);
        return new CarBody(length, List.copyOf(parts), List.of(chest, chest.mirrored()));
    }

    /**
     * The Fluid car: an open floor carrying a tank along it, rounded by a wide box crossing a tall one,
     * on a saddle at each end, a band round it near each end and a dome on top.
     */
    private static CarBody fluid() {
        double length = FluidCar.LENGTH - GAP;
        List<Part> parts = shell(length);
        parts.add(new Part(Paint.TANK, -0.46, 0.95, -0.83, 0.46, 2.05, 0.83));
        parts.add(new Part(Paint.TANK, -0.33, 0.72, -0.84, 0.33, 2.33, 0.84));
        parts.add(new Part(Paint.TANK, -0.12, 2.32, -0.15, 0.12, 2.5, 0.15));
        ends(parts, new Part(Paint.TRIM, -0.47, 0.94, 0.4, 0.47, 2.06, 0.46));
        ends(parts, new Part(Paint.DARK_RUST, -0.4, 0.69, 0.55, 0.4, 0.8, 0.7));
        return new CarBody(length, List.copyOf(parts), List.of());
    }

    /** The part and its mirror across the car. */
    private static void sides(List<Part> parts, Part right) {
        parts.add(right);
        parts.add(right.mirrored());
    }

    /** The part at the front and its turn at the back. */
    private static void ends(List<Part> parts, Part front) {
        parts.add(front);
        parts.add(front.turned());
    }
}
