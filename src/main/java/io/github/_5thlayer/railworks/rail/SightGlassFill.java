// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.List;

/**
 * How far a Sight glass is filled: from its bottom as far as its tank is full, in proportion, not in
 * steps, and dark above. A Fluid car's are panes ({@link CarBody.SightGlass}); a Fluid hatch's is the
 * round window on each of its sides ({@link #roundWindow}).
 *
 * <p>Pure: no Minecraft types.
 */
public final class SightGlassFill {

    /**
     * The round window's rows, bottom up, in a side's pixels: across from its left as seen facing it,
     * up from its bottom, a side being 16 each way. As the Fluid hatch's side textures draw it: the
     * glass six pixels across round the side's middle, inside a dark rim a pixel wide, which stays
     * dark so the fluid is framed apart from the ring.
     */
    private static final List<Rect> WINDOW = List.of(
            new Rect(6, 5, 10, 6),
            new Rect(5, 6, 11, 7),
            new Rect(5, 7, 11, 8),
            new Rect(5, 8, 11, 9),
            new Rect(5, 9, 11, 10),
            new Rect(6, 10, 10, 11));
    private static final double WINDOW_BOTTOM = WINDOW.getFirst().bottom();
    private static final double WINDOW_TOP = WINDOW.getLast().top();

    private SightGlassFill() {
    }

    /** A rectangle of fluid, in a side's pixels, from {@code left} to {@code right} across and {@code bottom} to {@code top} up. */
    public record Rect(double left, double bottom, double right, double top) {
    }

    /** How full a tank holding {@code amount} of {@code capacity} is: 0 empty, 1 full, and no capacity is empty. */
    public static double full(long amount, long capacity) {
        return capacity <= 0 ? 0.0 : Math.clamp((double) amount / capacity, 0.0, 1.0);
    }

    /** How high the fluid stands in a Sight glass from {@code bottom} to {@code top}, for a tank holding {@code amount} of {@code capacity}. */
    public static double surface(double bottom, double top, long amount, long capacity) {
        return bottom + (top - bottom) * full(amount, capacity);
    }

    /**
     * The fluid in a Fluid hatch's round window, for a tank holding {@code amount} of {@code capacity}:
     * each of the window's rows below the surface, the row it stands in cropped at it, so the fill
     * stays inside the rim. None for an empty tank.
     */
    public static List<Rect> roundWindow(long amount, long capacity) {
        double surface = surface(WINDOW_BOTTOM, WINDOW_TOP, amount, capacity);
        List<Rect> filled = new ArrayList<>();
        for (Rect row : WINDOW) {
            if (row.bottom() >= surface) {
                break;
            }
            filled.add(new Rect(row.left(), row.bottom(), row.right(), Math.min(row.top(), surface)));
        }
        return List.copyOf(filled);
    }
}
