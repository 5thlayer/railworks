// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Optional;

/**
 * What a requester hatch asks for: one item's kind, so many stacks of it, or for a Fluid hatch one
 * fluid, so many buckets of it (ADR 0008). Used with an item, a requester asks for a stack more of
 * that kind, up to as many stacks as it has slots; with a fluid, a Fluid requester asks for
 * {@link #BUCKETS_PER_ASK} buckets more of it, up to its tank. Another kind replaces the request
 * with one stack, or {@link #BUCKETS_PER_ASK} buckets, of it.
 *
 * <p>Pure: no Minecraft types. {@code I} is the goods' kind, the game's {@code Item} or {@code Fluid}.
 *
 * @param goods the goods it asks for: an item's kind or a fluid
 * @param count how many of it: stacks of an item, each full by the item's stack size, or buckets of a fluid
 */
public record HatchRequest<I>(I goods, int count) {

    /** How many buckets more a Fluid requester asks for each time it is used with a fluid. */
    public static final int BUCKETS_PER_ASK = 8;

    public HatchRequest {
        if (count < 1) {
            throw new IllegalArgumentException("a request for " + count);
        }
    }

    /** What a requester asking for {@code current}, with {@code slots} slots, asks for once used with {@code item}. */
    public static <I> HatchRequest<I> ask(Optional<HatchRequest<I>> current, I item, int slots) {
        return asked(current, item, 1, slots);
    }

    /** What a Fluid requester asking for {@code current}, its tank {@code buckets} buckets, asks for once used with {@code fluid}. */
    public static <I> HatchRequest<I> askFluid(Optional<HatchRequest<I>> current, I fluid, int buckets) {
        return asked(current, fluid, BUCKETS_PER_ASK, buckets);
    }

    private static <I> HatchRequest<I> asked(Optional<HatchRequest<I>> current, I item, int more, int most) {
        int count = current.filter(asked -> asked.goods().equals(item))
                .map(asked -> asked.count() + more)
                .orElse(more);
        return new HatchRequest<>(item, Math.min(count, most));
    }
}
