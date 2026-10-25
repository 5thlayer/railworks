// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The Rail between two Arms: an unordered pair, held with the first end sorting first by position,
 * and the Rail it cost, which cutting it pays back whatever the Pylons did since.
 *
 * <p>Pure: no Minecraft types.
 *
 * @param cost the Rail spent stringing it
 */
public record Span(Arm a, Arm b, int cost) {

    public static final Codec<Span> CODEC = RecordCodecBuilder.create(i -> i.group(
            Arm.CODEC.fieldOf("a").forGetter(Span::a),
            Arm.CODEC.fieldOf("b").forGetter(Span::b),
            Codec.INT.fieldOf("cost").forGetter(Span::cost)
    ).apply(i, Span::of));

    /** The Span between these Arms, in either order. */
    public static Span of(Arm one, Arm other, int cost) {
        return one.compareTo(other) <= 0 ? new Span(one, other, cost) : new Span(other, one, cost);
    }

    public boolean touches(Arm arm) {
        return a.equals(arm) || b.equals(arm);
    }

    /** The Arm at the other end from {@code arm}, which must be one of the two. */
    public Arm otherEnd(Arm arm) {
        return a.equals(arm) ? b : a;
    }

    /** Whether an end of this Span stands in chunk ({@code chunkX}, {@code chunkZ}). */
    public boolean inChunk(int chunkX, int chunkZ) {
        return in(a, chunkX, chunkZ) || in(b, chunkX, chunkZ);
    }

    private static boolean in(Arm arm, int chunkX, int chunkZ) {
        return arm.chunkX() == chunkX && arm.chunkZ() == chunkZ;
    }
}
