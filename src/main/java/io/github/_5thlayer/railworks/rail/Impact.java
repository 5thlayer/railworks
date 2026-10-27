// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * What a moving car does to a player or mob in its path: hurts it by how fast the car runs, nothing
 * at rest, and pushes it aside, off the Rail to the side it stands on and carried on at the car's
 * speed, so the car runs past it rather than through it again.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Impact {

    /**
     * Health taken for each block a second the car runs: a Train at the Locomotive's default top
     * speed of 25 blocks a second takes 20, an unarmoured player's whole health, as a Factorio train
     * at speed kills one.
     */
    static final double DAMAGE_PER_BLOCK_PER_SECOND = 0.8;
    /** How hard what is struck is pushed off the Rail, in blocks a tick: past a car's half width in a tick or two. */
    public static final double ASIDE = 0.6;
    /** How far up it is thrown, in blocks a tick, so it clears the ground it is pushed along. */
    static final double LIFT = 0.25;

    /** The push given to what is struck, in blocks a tick. */
    public record Push(double dx, double dy, double dz) {
    }

    private Impact() {
    }

    /** The health a car running {@code blocksPerSecond}, either way, takes from what it strikes. */
    public static double damage(double blocksPerSecond) {
        return Math.abs(blocksPerSecond) * DAMAGE_PER_BLOCK_PER_SECOND;
    }

    /**
     * The push a car moving at ({@code vx}, {@code vy}, {@code vz}) blocks a tick gives what it strikes
     * standing ({@code ox}, {@code oz}) from the car's middle: its own velocity, {@link #ASIDE} square
     * to the way it runs toward the side the struck stands on, and {@link #LIFT} up. One right on the
     * line goes to the car's right.
     */
    public static Push push(double vx, double vy, double vz, double ox, double oz) {
        double run = Math.sqrt(vx * vx + vz * vz);
        // Square to the run, to the car's right; along x for a car running straight up or down.
        double sx = run < 1e-9 ? 1.0 : -vz / run;
        double sz = run < 1e-9 ? 0.0 : vx / run;
        if (sx * ox + sz * oz < 0.0) {
            sx = -sx;
            sz = -sz;
        }
        return new Push(vx + sx * ASIDE, Math.max(vy, 0.0) + LIFT, vz + sz * ASIDE);
    }
}
