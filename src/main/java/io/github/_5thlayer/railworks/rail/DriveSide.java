// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * The side of the Rail a Station stands on to serve a Train, seen the way the Train runs: the right,
 * as a Factorio train stop serves the trains with it on their right, unless {@code
 * railworks-server.toml} sets the left (ADR 0008).
 *
 * <p>Pure: no Minecraft types.
 */
public enum DriveSide {
    RIGHT, LEFT;

    /** The drive side unless {@code railworks-server.toml} says otherwise. */
    public static final DriveSide DEFAULT = RIGHT;

    private static volatile DriveSide current = DEFAULT;

    /** Set by {@code RailworksConfig} when the server config loads or reloads. */
    public static void configure(DriveSide side) {
        current = side;
    }

    /** The drive side the server runs on. */
    public static DriveSide current() {
        return current;
    }
}
