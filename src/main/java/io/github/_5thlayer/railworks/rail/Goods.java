// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Map;

/**
 * What only Minecraft knows of the goods {@link Logistics} moves: what hatches and Cargo cars hold,
 * and moving it between them, and whether fuel waits in a Locomotive's slot. Counts are items, of one
 * kind each, its components ignored.
 *
 * <p>Pure: no Minecraft types. {@code I} is the item's kind, the game's {@code Item}.
 */
public interface Goods<I> {

    /** How many of {@code item} make a stack. */
    int stackSize(I item);

    /** How many of {@code item} {@code hatch} holds. */
    long held(Logistics.Hatch<I> hatch, I item);

    /** How many of {@code item} {@code car} holds. */
    long held(CargoCar car, I item);

    /** What {@code car} holds, item by item in the order of its slots; nothing for a car out of view. */
    Map<I, Long> contents(CargoCar car);

    /** Whether {@code car} holds nothing at all. */
    boolean empty(CargoCar car);

    /** How many slots {@code car} has, each a stack of one kind. */
    int slots(CargoCar car);

    /** Moves up to {@code most} of {@code item} from {@code from} into {@code into}, as far as it holds them and the car has room; how many moved. */
    long load(Logistics.Hatch<I> from, CargoCar into, I item, long most);

    /** Moves up to {@code most} of {@code item} from {@code from} into {@code into}, as far as it holds them and the hatch has room; how many moved. */
    long unload(CargoCar from, Logistics.Hatch<I> into, I item, long most);

    /** Whether fuel waits in {@code locomotive}'s slot, so it can pull before its buffer is lit. */
    boolean slotFuelled(Locomotive locomotive);
}
