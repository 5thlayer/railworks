// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * A burner's fuel, held as joules rather than as burn ticks, after Wireworks' Boiler (FactoryWorks
 * ADR-0047): lighting an item banks its whole value and a tick of work spends that tick's draw, all
 * or nothing, so the sliver left over from one item is spent before the next is lit.
 *
 * <p>Pure: no Minecraft types.
 */
public final class FuelBuffer {

    private long storedJoules;

    /** What the last item lit was worth: the denominator a gauge is drawn against. */
    private long lastLitJoules;

    public long storedJoules() {
        return storedJoules;
    }

    public long lastLitJoules() {
        return lastLitJoules;
    }

    /** What a gauge fills to: the last item's worth, or the buffer itself if that is larger. */
    public long gaugeCapacity() {
        return Math.max(lastLitJoules, storedJoules);
    }

    /** Banks one fuel item, consumed whole. */
    public void light(long fuelValue) {
        if (fuelValue <= 0L) {
            return;
        }
        storedJoules += fuelValue;
        lastLitJoules = fuelValue;
    }

    /** Pays for one tick of work, all or nothing; false when the buffer cannot cover it. */
    public boolean drawTick(long joulesPerTick) {
        if (storedJoules < joulesPerTick) {
            return false;
        }
        storedJoules -= joulesPerTick;
        return true;
    }

    public void load(long storedJoules, long lastLitJoules) {
        this.storedJoules = Math.max(0L, storedJoules);
        this.lastLitJoules = Math.max(0L, lastLitJoules);
    }
}
