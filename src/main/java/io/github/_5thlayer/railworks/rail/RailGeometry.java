// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * The Rail's cross-section and where it rests on an Arm, as offsets from the Arm's block position,
 * so that what draws the Rail and what rides it agree. The Rail is an inverted T: a flange along the
 * bottom, its underside resting on the top of the Pylon, and a web standing on it. A car straddles
 * the web and sits on the flange's top. Placeholder sizes.
 *
 * <p>Pure: no Minecraft types.
 */
public final class RailGeometry {

    public static final double ATTACH_X = 0.5;
    /** The flange's underside, which rests on the top of the Pylon's top segment. */
    public static final double ATTACH_Y = 1.0;
    public static final double ATTACH_Z = 0.5;

    public static final double FLANGE_WIDTH = 0.9;
    public static final double FLANGE_HEIGHT = 0.15;
    public static final double WEB_WIDTH = 0.3;
    public static final double WEB_HEIGHT = 0.5;

    /** Where a car's floor rests: the flange's top. */
    public static final double RIDE_Y = ATTACH_Y + FLANGE_HEIGHT;

    /** The middle of the web, which a player aims at. */
    public static final double AIM_Y = RIDE_Y + WEB_HEIGHT / 2.0;

    private RailGeometry() {
    }
}
