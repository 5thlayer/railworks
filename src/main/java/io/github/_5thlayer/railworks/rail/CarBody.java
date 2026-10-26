// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.List;

/**
 * The boxes a car is drawn from: a boxy car straddling the Rail, its shell riding on top with
 * skirts down both sides of the web, and the chests set into a Cargo car's sides. Coordinates are
 * the car's own: x across, y up from the floor's underside on the flange's top, z along the car with
 * +z the end it faces. Placeholder art, so {@code CarRenderer} only paints these and nothing else
 * decides their sizes. It also holds the car's size and where the Locomotive's rider sits in it, so
 * the boxes, the seat and the rider's view out of the cab are checked together.
 *
 * <p>Where two boxes meet, one stops a little short of the other's faces or reaches into it, so no
 * two faces share a plane and the depth test never has to choose between them.
 *
 * <p>Pure: no Minecraft types.
 */
public record CarBody(List<Part> parts, List<Box> chests) {

    /** A car's box across: a block. */
    public static final double WIDTH = 1.0;
    /** A car's box up from the floor's underside. */
    public static final double HEIGHT = 2.75;
    /** A car's length along the Line. */
    public static final double LENGTH = 1.8;

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
        LAMP(0.95F, 0.90F, 0.62F);

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
     * Where the Locomotive's rider sits, above the floor's underside and forward of the middle. Only
     * a Locomotive is ridden.
     */
    public static final double SEAT_Y = 1.2;
    public static final double SEAT_Z = 0.3;

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
    private static final double WINDOW_BACK = -0.25;
    private static final double WINDOW_FRONT = 0.74;

    public static final CarBody LOCOMOTIVE = locomotive();
    public static final CarBody CARGO = cargo();

    /** Where the Locomotive's rider's eye is. */
    public static Point eye() {
        return new Point(0.0, SEAT_Y + EYE_ABOVE_SEAT, SEAT_Z);
    }

    /** What both cars share: the skirts, the floor and the roof. */
    private static List<Part> shell() {
        List<Part> parts = new ArrayList<>();
        sides(parts, new Part(Paint.DARK_RUST, 0.17, 0.0, -0.89, 0.49, 0.62, 0.89));
        parts.add(new Part(Paint.SHELL, -0.5, 0.55, -0.9, 0.5, 0.7, 0.9));
        parts.add(new Part(Paint.SHELL, -0.5, 2.6, -0.9, 0.5, 2.75, 0.9));
        return parts;
    }

    /**
     * The Locomotive: a closed back, an open cab at the front, framed in dark by the roof's edge, two
     * corner pillars and a low dash, with a deep red nose and two headlights under it, and an open
     * window in each side of the cab, framed by the band.
     */
    private static CarBody locomotive() {
        List<Part> parts = shell();
        // The side wall below and above the window, then behind and ahead of it; the band runs on
        // behind and ahead of the window and frames it above and below.
        sides(parts, new Part(Paint.SHELL, 0.42, 0.68, -0.885, 0.49, WINDOW_SILL - 0.005, 0.84));
        sides(parts, new Part(Paint.SHELL, 0.42, WINDOW_HEAD + 0.005, -0.885, 0.49, 2.62, 0.84));
        sides(parts, new Part(Paint.SHELL, 0.425, BAND_BOTTOM + 0.05, -0.88, 0.485, BAND_TOP - 0.05, WINDOW_BACK + 0.005));
        sides(parts, new Part(Paint.SHELL, 0.425, BAND_BOTTOM + 0.05, WINDOW_FRONT - 0.005, 0.485, BAND_TOP - 0.05, 0.835));
        sides(parts, new Part(Paint.TRIM, 0.47, BAND_BOTTOM, -0.9, 0.5, BAND_TOP, WINDOW_BACK));
        sides(parts, new Part(Paint.TRIM, 0.47, BAND_BOTTOM, WINDOW_FRONT, 0.5, BAND_TOP, 0.9));
        sides(parts, new Part(Paint.TRIM, 0.472, BAND_BOTTOM + 0.005, WINDOW_BACK - 0.005, 0.498, WINDOW_SILL, WINDOW_FRONT + 0.005));
        sides(parts, new Part(Paint.TRIM, 0.472, WINDOW_HEAD, WINDOW_BACK - 0.005, 0.498, BAND_TOP - 0.005, WINDOW_FRONT + 0.005));
        parts.add(new Part(Paint.SHELL, -0.45, 0.67, -0.89, 0.45, 2.63, -0.82));
        parts.add(new Part(Paint.DARK_RUST, -0.25, 0.75, -0.9, 0.25, 2.35, -0.87));
        sides(parts, new Part(Paint.TRIM, 0.3, 0.66, 0.76, 0.495, 2.64, 0.885));
        parts.add(new Part(Paint.TRIM, -0.49, 2.45, 0.85, 0.49, 2.65, 0.895));
        parts.add(new Part(Paint.TRIM, -0.31, 0.65, 0.78, 0.31, 1.6, 0.88));
        parts.add(new Part(Paint.DEEP_RED, -0.5, 0.72, 0.86, 0.5, 1.45, 0.89));
        sides(parts, new Part(Paint.LAMP, 0.22, 1.1, 0.875, 0.36, 1.3, 0.9));
        return new CarBody(List.copyOf(parts), List.of());
    }

    /**
     * The Cargo car: closed, of the Locomotive's height and roof line, a gangway door at both ends,
     * and a chest set into a framed recess in the middle of each side.
     */
    private static CarBody cargo() {
        List<Part> parts = shell();
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
        return new CarBody(List.copyOf(parts), List.of(chest, chest.mirrored()));
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
