// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * Where the Rail hangs from an Arm, as an offset from the Arm's block position, so that what draws
 * the Rail and what rides it agree.
 *
 * <p>Pure: no Minecraft types.
 */
public final class RailGeometry {

    public static final double ATTACH_X = 0.5;
    /** Where on the top segment the Rail hangs from. */
    public static final double ATTACH_Y = 0.8;
    public static final double ATTACH_Z = 0.5;

    private RailGeometry() {
    }
}
