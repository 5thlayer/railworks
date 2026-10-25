// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.List;

import com.mojang.serialization.Codec;

/**
 * Where an Arm is: the block position of the top segment of its Pylon column.
 *
 * <p>Pure: no Minecraft types.
 */
public record Arm(int x, int y, int z) implements Comparable<Arm> {

    public static final Codec<Arm> CODEC = Codec.INT.listOf(3, 3).xmap(
            l -> new Arm(l.get(0), l.get(1), l.get(2)),
            a -> List.of(a.x(), a.y(), a.z()));

    /** The squared straight distance to {@code other}, exact. */
    public long distanceSquaredTo(Arm other) {
        long dx = (long) x - other.x;
        long dy = (long) y - other.y;
        long dz = (long) z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** The chunk column the Arm stands in, x. */
    public int chunkX() {
        return x >> 4;
    }

    /** The chunk column the Arm stands in, z. */
    public int chunkZ() {
        return z >> 4;
    }

    @Override
    public int compareTo(Arm other) {
        if (x != other.x) {
            return Integer.compare(x, other.x);
        }
        if (y != other.y) {
            return Integer.compare(y, other.y);
        }
        return Integer.compare(z, other.z);
    }
}
