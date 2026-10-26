// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Optional;

/**
 * What a requester hatch asks for: one item's kind, so many stacks of it (ADR 0008). Used with an
 * item, a requester asks for a stack more of that kind, up to as many stacks as it has slots;
 * another kind replaces the request with one stack of it.
 *
 * <p>Pure: no Minecraft types. {@code I} is the item's kind, the game's {@code Item}.
 *
 * @param item   the kind of item it asks for
 * @param stacks how many stacks of it, each full by the item's stack size
 */
public record HatchRequest<I>(I item, int stacks) {

    public HatchRequest {
        if (stacks < 1) {
            throw new IllegalArgumentException("a request for " + stacks + " stacks");
        }
    }

    /** What a requester asking for {@code current}, with {@code slots} slots, asks for once used with {@code item}. */
    public static <I> HatchRequest<I> ask(Optional<HatchRequest<I>> current, I item, int slots) {
        int stacks = current.filter(asked -> asked.item().equals(item))
                .map(asked -> Math.min(asked.stacks() + 1, slots))
                .orElse(1);
        return new HatchRequest<>(item, stacks);
    }
}
