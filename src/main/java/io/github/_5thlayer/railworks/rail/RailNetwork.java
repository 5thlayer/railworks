// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

/**
 * Every Span in a level, stored once each as an unordered pair of Arms. It holds the Spans and
 * nothing of the world; {@link Stringing} says what a click does to them.
 *
 * <p>Pure: DataFixerUpper's codec, no Minecraft types.
 */
public final class RailNetwork {

    public static final Codec<RailNetwork> CODEC = Span.CODEC.listOf().xmap(
            list -> {
                RailNetwork network = new RailNetwork();
                list.forEach(network::add);
                return network;
            },
            network -> List.copyOf(network.spans.values()));

    /** Keyed by the pair alone, the first end sorting first. */
    private final Map<List<Arm>, Span> spans = new LinkedHashMap<>();

    /** Adds a Span; a pair already strung keeps the Span it has. */
    public void add(Span span) {
        spans.putIfAbsent(key(span.a(), span.b()), span);
    }

    /** The Span between these Arms, or null if there is none. */
    public Span get(Arm a, Arm b) {
        return spans.get(key(a, b));
    }

    public boolean contains(Arm a, Arm b) {
        return spans.containsKey(key(a, b));
    }

    /** Removes the Span between these Arms and returns it, or null if there was none. */
    public Span remove(Arm a, Arm b) {
        return spans.remove(key(a, b));
    }

    /** The Spans with an end at {@code arm}. */
    public List<Span> spansAt(Arm arm) {
        return spans.values().stream().filter(s -> s.touches(arm)).toList();
    }

    /** Cuts every Span at {@code arm} and returns them, which is what losing the Arm does. */
    public List<Span> removeAllAt(Arm arm) {
        List<Span> cut = spansAt(arm);
        cut.forEach(s -> spans.remove(key(s.a(), s.b())));
        return cut;
    }

    /**
     * Moves every Span at {@code from} to {@code to}, each keeping the Rail it cost: a column that
     * grew keeps its Spans, its Arm now one segment higher.
     */
    public void rekey(Arm from, Arm to) {
        for (Span span : spansAt(from)) {
            spans.remove(key(span.a(), span.b()));
            Arm other = span.otherEnd(from);
            if (!other.equals(to)) {
                add(Span.of(to, other, span.cost()));
            }
        }
    }

    /** Every Span with an end in chunk ({@code chunkX}, {@code chunkZ}): what a client watching it is sent. */
    public List<Span> touching(int chunkX, int chunkZ) {
        return spans.values().stream().filter(s -> s.inChunk(chunkX, chunkZ)).toList();
    }

    /**
     * A client's copy of a chunk's Spans, replaced whole when the chunk is sent, so a Span cut while
     * no end was watched is dropped when either end is watched again.
     */
    public void replaceTouching(int chunkX, int chunkZ, Collection<Span> sent) {
        spans.values().removeIf(s -> s.inChunk(chunkX, chunkZ));
        sent.forEach(this::add);
    }

    public Collection<Span> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(spans.values()));
    }

    private static List<Arm> key(Arm a, Arm b) {
        return a.compareTo(b) <= 0 ? List.of(a, b) : List.of(b, a);
    }
}
