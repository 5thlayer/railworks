// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.function.LongSupplier;

/**
 * A Burner Generator's burning: a {@link FuelBuffer} of joules, filled by lighting fuel as a Burner
 * Locomotive's is, burnt at {@link ChargeSpec#burnerGeneratorJoulesPerTick()} into a small buffer of FE,
 * {@link ChargeSpec#burnerGeneratorBuffer()} at most. It burns a tick only while that buffer has room for
 * the tick's FE, so no joule is lost, and keeps the joules short of a whole FE for the next tick.
 *
 * <p>Pure: no Minecraft types.
 */
public final class BurnerGenerator {

    private final FuelBuffer fuel = new FuelBuffer();
    /** FE made and not yet given away. */
    private long stored;
    /** Joules burnt and not yet a whole FE. */
    private long joules;

    public FuelBuffer fuel() {
        return fuel;
    }

    /** FE it holds, to give away. */
    public long stored() {
        return stored;
    }

    /** Joules it burnt short of a whole FE, kept for the next tick. */
    public long joules() {
        return joules;
    }

    /** FE it holds at most. */
    public static long capacity() {
        return ChargeSpec.burnerGeneratorBuffer();
    }

    /**
     * One tick: with room for the tick's FE, it burns a tick's joules from its fuel buffer, lighting
     * an item through {@code ignite} (the item's joules, or 0 for none) when the buffer cannot cover
     * it, and banks them as FE. Whether it burnt.
     */
    public boolean tick(LongSupplier ignite) {
        long perTick = ChargeSpec.burnerGeneratorJoulesPerTick();
        long perFe = ChargeSpec.joulesPerFe();
        if (stored + (joules + perTick) / perFe > capacity()) {
            return false;
        }
        if (!fuel.drawTick(perTick)) {
            long lit = ignite.getAsLong();
            if (lit <= 0L) {
                return false;
            }
            fuel.light(lit);
            if (!fuel.drawTick(perTick)) {
                return false;
            }
        }
        joules += perTick;
        stored += joules / perFe;
        joules %= perFe;
        return true;
    }

    /** Gives away up to {@code most} FE; how many it gave. */
    public long extract(long most) {
        long given = Math.max(0L, Math.min(most, stored));
        stored -= given;
        return given;
    }

    public void load(long stored, long joules, long fuel, long lastLit) {
        this.stored = Math.max(0L, stored);
        this.joules = Math.max(0L, joules);
        this.fuel.load(fuel, lastLit);
    }
}
