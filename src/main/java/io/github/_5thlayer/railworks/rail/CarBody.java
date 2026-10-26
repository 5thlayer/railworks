// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.List;

/**
 * The boxes a car is drawn from: a boxy straddle monorail car, its shell riding on the Rail with
 * skirts down both sides of the web. Coordinates are the car's own: x across, y up from the floor's
 * underside on the flange's top, z along the car with +z the end it faces. Placeholder art, so
 * {@code CarRenderer} only paints these and nothing else decides their sizes.
 *
 * <p>Where two boxes meet, one stops a little short of the other's faces or reaches into it, so no
 * two faces share a plane and the depth test never has to choose between them.
 *
 * <p>Pure: no Minecraft types.
 */
public record CarBody(List<Part> parts) {

    /** What colour a part is drawn in; the renderer holds the colours. */
    public enum Paint {
        /** The rust-red shell, the Rail's colour. */
        SHELL,
        /** The skirts and the gangway doors, darker than the shell. */
        UNDER,
        /** The window bands and the cab's frame, near black. */
        TRIM,
        /** The band across the Locomotive's front, deep red. */
        NOSE,
        /** A headlight. */
        LAMP,
        /**
         * A vanilla chest, drawn with its own texture, in the box as it would fill a block, its front
         * facing out of the car's side. The box is a cube.
         */
        CHEST
    }

    public record Part(Paint paint, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        /** The same part on the other side of the car. */
        Part mirrored() {
            return new Part(paint, -maxX, minY, minZ, -minX, maxY, maxZ);
        }
    }

    /** Where the Locomotive's rider sits, above the floor's underside and forward of the middle. */
    public static final double SEAT_Y = 1.2;
    public static final double SEAT_Z = 0.3;

    /**
     * A seated player's eye above the seat: their 1.62 eye height less the 0.6 they sit below a
     * seat ({@code Avatar.DEFAULT_VEHICLE_ATTACHMENT}).
     */
    private static final double EYE_ABOVE_SEAT = 1.62 - 0.6;

    /** The window bands, running unbroken from car to car. */
    private static final double BAND_Y0 = 1.64;
    private static final double BAND_Y1 = 2.5;

    /** The Locomotive's open side windows, along the cab inside the band's frame. */
    private static final double WINDOW_Y0 = 1.7;
    private static final double WINDOW_Y1 = 2.44;
    private static final double WINDOW_Z0 = -0.25;
    private static final double WINDOW_Z1 = 0.74;

    public static final CarBody LOCOMOTIVE = locomotive();
    public static final CarBody CARGO = cargo();

    /** Where the Locomotive's rider's eye is, as {x, y, z}. */
    public static double[] eye() {
        return new double[] {0.0, SEAT_Y + EYE_ABOVE_SEAT, SEAT_Z};
    }

    /** What both cars share: the skirts, the floor and the roof. */
    private static List<Part> shell() {
        List<Part> parts = new ArrayList<>();
        sides(parts, new Part(Paint.UNDER, 0.17, 0.0, -0.89, 0.49, 0.62, 0.89));
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
        sides(parts, new Part(Paint.SHELL, 0.42, 0.68, -0.885, 0.49, WINDOW_Y0 - 0.005, 0.84));
        sides(parts, new Part(Paint.SHELL, 0.42, WINDOW_Y1 + 0.005, -0.885, 0.49, 2.62, 0.84));
        sides(parts, new Part(Paint.SHELL, 0.425, BAND_Y0 + 0.05, -0.88, 0.485, BAND_Y1 - 0.05, WINDOW_Z0 + 0.005));
        sides(parts, new Part(Paint.SHELL, 0.425, BAND_Y0 + 0.05, WINDOW_Z1 - 0.005, 0.485, BAND_Y1 - 0.05, 0.835));
        sides(parts, new Part(Paint.TRIM, 0.47, BAND_Y0, -0.9, 0.5, BAND_Y1, WINDOW_Z0));
        sides(parts, new Part(Paint.TRIM, 0.47, BAND_Y0, WINDOW_Z1, 0.5, BAND_Y1, 0.9));
        sides(parts, new Part(Paint.TRIM, 0.472, BAND_Y0 + 0.005, WINDOW_Z0 - 0.005, 0.498, WINDOW_Y0, WINDOW_Z1 + 0.005));
        sides(parts, new Part(Paint.TRIM, 0.472, WINDOW_Y1, WINDOW_Z0 - 0.005, 0.498, BAND_Y1 - 0.005, WINDOW_Z1 + 0.005));
        parts.add(new Part(Paint.SHELL, -0.45, 0.67, -0.89, 0.45, 2.63, -0.82));
        parts.add(new Part(Paint.UNDER, -0.25, 0.75, -0.9, 0.25, 2.35, -0.87));
        sides(parts, new Part(Paint.TRIM, 0.3, 0.66, 0.76, 0.495, 2.64, 0.885));
        parts.add(new Part(Paint.TRIM, -0.49, 2.45, 0.85, 0.49, 2.65, 0.895));
        parts.add(new Part(Paint.TRIM, -0.31, 0.65, 0.78, 0.31, 1.6, 0.88));
        parts.add(new Part(Paint.NOSE, -0.5, 0.72, 0.86, 0.5, 1.45, 0.89));
        sides(parts, new Part(Paint.LAMP, 0.22, 1.1, 0.875, 0.36, 1.3, 0.9));
        return new CarBody(List.copyOf(parts));
    }

    /**
     * The Cargo car: a closed boxcar of the Locomotive's height and roof line, a gangway door at
     * both ends, and a chest set into a framed recess in the middle of each side.
     */
    private static CarBody cargo() {
        List<Part> parts = shell();
        sides(parts, new Part(Paint.TRIM, 0.47, BAND_Y0, -0.9, 0.5, BAND_Y1, 0.9));
        for (int end : new int[] {1, -1}) {
            parts.add(along(end, new Part(Paint.SHELL, -0.45, 0.67, 0.82, 0.45, 2.63, 0.89)));
            parts.add(along(end, new Part(Paint.UNDER, -0.25, 0.75, 0.87, 0.25, 2.35, 0.9)));
        }
        // The side's inner wall, then the facing around the recess, which stands proud of the wall
        // by the recess's depth; the chest sits in the recess, its lock just behind the facing.
        sides(parts, new Part(Paint.SHELL, 0.33, 0.68, -0.885, 0.39, 2.62, 0.885));
        for (int end : new int[] {1, -1}) {
            sides(parts, along(end, new Part(Paint.SHELL, 0.38, 0.665, 0.22, 0.49, 2.635, 0.88)));
        }
        sides(parts, new Part(Paint.SHELL, 0.382, 1.48, -0.23, 0.488, 2.625, 0.23));
        sides(parts, new Part(Paint.SHELL, 0.382, 0.675, -0.23, 0.488, 1.07, 0.23));
        sides(parts, new Part(Paint.CHEST, 0.01, 1.075, -0.215, 0.44, 1.505, 0.215));
        return new CarBody(List.copyOf(parts));
    }

    /** The part and its mirror across the car. */
    private static void sides(List<Part> parts, Part right) {
        parts.add(right);
        parts.add(right.mirrored());
    }

    /** The part as given toward the front, or turned end for end toward the back. */
    private static Part along(int end, Part front) {
        return end > 0 ? front
                : new Part(front.paint(), front.minX(), front.minY(), -front.maxZ(), front.maxX(), front.maxY(), -front.minZ());
    }
}
