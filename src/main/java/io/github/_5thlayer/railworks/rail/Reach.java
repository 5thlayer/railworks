// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * How far a hatch's Bellows or Loading arm is reached out to the car at its spot, as a client sees
 * it, tick by tick: from 0, folded into the hatch, to 1, on the car. It reaches out over
 * {@link #OUT_TICKS} once a car is there, and folds back over {@link #BACK_TICKS} once it is gone,
 * either from wherever the other had got to, so it never jumps. Both run along one curve, eased out,
 * so the reach slows as it lands and the fold starts slow. What is there at its first look it takes
 * as it is, reached or not, with nothing started: a client cannot tell a car just come from one that
 * stood there all along. Only drawn: the hatch trades the same whatever it shows.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Reach {

    /** How many ticks it takes to reach out from folded. */
    public static final int OUT_TICKS = 10;
    /** How many ticks it takes to fold back from reached. */
    public static final int BACK_TICKS = 4;

    /** What a tick started, for the sound it makes. */
    public enum Change {
        NONE,
        /** It began to reach out. */
        OUT,
        /** It reached the car, having reached out. */
        LANDED,
        /** It began to fold back. */
        BACK
    }

    /**
     * How far along it is, evenly in time, before the eased curve: from 0 to {@link #FULL}, in steps
     * that are whole for both ways, so it lands on its ends exactly.
     */
    private static final int FULL = OUT_TICKS * BACK_TICKS;

    private int along;
    /** {@link #along} as the last tick began, which {@link #out} eases from. */
    private int before;
    /** Whether a car is there, as the last tick saw. */
    private boolean car;
    private boolean looked;

    /** One tick on, with a car there or not: what it started. */
    public Change tick(boolean car) {
        before = along;
        if (!looked) {
            looked = true;
            this.car = car;
            along = car ? FULL : 0;
            before = along;
            return Change.NONE;
        }
        Change change = Change.NONE;
        if (car != this.car) {
            this.car = car;
            change = car ? Change.OUT : Change.BACK;
        }
        if (car) {
            along = Math.min(FULL, along + FULL / OUT_TICKS);
            if (change == Change.NONE && before < FULL && along == FULL) {
                change = Change.LANDED;
            }
        } else {
            along = Math.max(0, along - FULL / BACK_TICKS);
        }
        return change;
    }

    /** How far out it is, from 0 to 1, {@code partialTick} of the way through the last tick. */
    public double out(float partialTick) {
        double t = (before + (along - before) * (double) partialTick) / FULL;
        return 1.0 - (1.0 - t) * (1.0 - t);
    }
}
