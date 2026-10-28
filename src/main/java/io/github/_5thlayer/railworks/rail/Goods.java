// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Map;

/**
 * What only Minecraft knows of the goods {@link Logistics} moves: what hatches, Cargo cars and Fluid
 * cars hold, and moving it between them, what a Locomotive's fuel slots hold and take, and the FE of
 * an Electric Locomotive's charge and a Charger's buffer. Goods are of two {@link Kind kinds}: items,
 * counted one by one, of one kind each, their components ignored; and fluid, counted in millibuckets
 * ({@link Logistics#MILLIBUCKETS_PER_BUCKET} to a bucket). A hatch of items and a Cargo car hold
 * items, a Fluid hatch and a Fluid car fluid. Charge is counted in FE.
 *
 * <p>Pure: no Minecraft types. {@code I} is the goods' kind, the game's {@code Item} or {@code Fluid}.
 */
public interface Goods<I> {

    /** The two kinds of goods, each moved only between a hatch and a car of its own kind. */
    enum Kind {
        /** Items, held by a hatch of items and a Cargo car. */
        ITEMS,
        /** Fluid, held by a Fluid hatch and a Fluid car. */
        FLUID
    }

    /** How many of {@code item} make a stack. */
    int stackSize(I item);

    /** How much of {@code item} {@code hatch} holds. */
    long held(Logistics.Hatch<I> hatch, I item);

    /** How much of {@code item} {@code car} holds. */
    long held(GoodsCar car, I item);

    /** What {@code car} holds, item by item in the order of its slots or its one fluid; nothing for a car whose goods are not known. */
    Map<I, Long> contents(GoodsCar car);

    /** Whether {@code car} holds nothing at all. */
    boolean empty(GoodsCar car);

    /** How many slots {@code car} has, each a stack of one kind. */
    int slots(CargoCar car);

    /** How many millibuckets of one fluid {@code car}'s tank holds when full. */
    long tank(FluidCar car);

    /** Moves up to {@code most} of {@code item} from {@code from} into {@code into}, as far as it holds them and the car has room; how much moved. */
    long load(Logistics.Hatch<I> from, GoodsCar into, I item, long most);

    /** Moves up to {@code most} of {@code item} from {@code from} into {@code into}, as far as it holds them and the hatch has room; how much moved. */
    long unload(GoodsCar from, Logistics.Hatch<I> into, I item, long most);

    /** What {@code hatch} holds, item by item in the order of its slots or its one fluid. */
    Map<I, Long> contents(Logistics.Hatch<I> hatch);

    /**
     * How many of {@code item} {@code locomotive}'s fuel slots take from {@code from}, as
     * {@link #refuel} would move it: none for an item that is not fuel, or that leaves something
     * behind when burnt, or for a Locomotive whose fuel slots are not known.
     */
    long fuelRoom(Logistics.Hatch<I> from, Locomotive locomotive, I item);

    /** Moves up to {@code most} of {@code item} from {@code from} into {@code into}'s fuel slots, as far as it holds them and they take them; how many moved. */
    long refuel(Logistics.Hatch<I> from, Locomotive into, I item, long most);

    /** Whether fuel waits in {@code locomotive}'s slots, so it can pull before its buffer is lit. */
    boolean slotsFuelled(Locomotive locomotive);

    /** How many FE {@code locomotive}'s charge holds: none for a Burner, or for one whose record is not known. */
    long charge(Locomotive locomotive);

    /** Spends {@code fe} of {@code locomotive}'s charge, all or nothing; whether it did. */
    boolean spendCharge(Locomotive locomotive, long fe);

    /**
     * Moves up to {@code most} FE from {@code from}'s buffer into {@code into}'s charge, as far as the
     * buffer holds it, a Creative Charger's never running out, and the charge has room; how many moved.
     */
    long giveCharge(Logistics.Charger from, Locomotive into, long most);
}
