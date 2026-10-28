// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.function.Consumer;

import io.github._5thlayer.railworks.rail.Locomotive;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive's {@link Locomotive#FUEL_SLOTS} fuel slots, each a stack of one fuel, the
 * fuels free to differ, burning from the first that holds any: the rules for filling and burning
 * them, over the Locomotive's record in {@link CarRecords} (ADR 0008), so they hold the same in
 * view or not. Its view, its menu and a provider beside it all go through these.
 */
record FuelSlots(Level level, Container slots) {

    /** {@code locomotive}'s fuel slots, its record in {@code level}'s {@link CarRecords}; null while it has none. */
    static @Nullable FuelSlots of(ServerLevel level, Locomotive locomotive) {
        return LevelCarRecords.of(level).of(locomotive.id()).map(slots -> new FuelSlots(level, slots)).orElse(null);
    }

    /** What fuel slot {@code slot} holds, a copy. */
    ItemStack slot(int slot) {
        return slots.getItem(LocomotiveMenu.FUEL + slot).copy();
    }

    /** The slot of the first of the fuel slots holding fuel; -1 for none. */
    private int firstFuelled() {
        for (int i = 0; i < Locomotive.FUEL_SLOTS; i++) {
            if (LocomotiveEntity.fuelValue(level, slots.getItem(LocomotiveMenu.FUEL + i)) > 0L) {
                return LocomotiveMenu.FUEL + i;
            }
        }
        return -1;
    }

    /** A copy of the fuel it burns next; empty for none. */
    ItemStack nextFuel() {
        int slot = firstFuelled();
        return slot < 0 ? ItemStack.EMPTY : slots.getItem(slot).copy();
    }

    /** Whether they hold fuel, which logistics counts as able to pull before the buffer is lit. */
    boolean hasFuel() {
        return firstFuelled() >= 0;
    }

    /**
     * Burns one fuel item whole from the first slot that holds any and says what it was worth; 0 when
     * there is none. What it leaves behind, a bucket from lava, goes to {@code remainder}.
     */
    long ignite(Consumer<ItemStack> remainder) {
        int slot = firstFuelled();
        if (slot < 0) {
            return 0L;
        }
        ItemStack fuel = slots.getItem(slot);
        long joules = LocomotiveEntity.fuelValue(level, fuel);
        var left = fuel.getCraftingRemainder();
        slots.removeItem(slot, 1);
        if (left != null) {
            remainder.accept(left.create());
        }
        return joules;
    }

    /**
     * Burns one fuel item as {@link #ignite} does, with nowhere to put what it leaves behind: 0, burning
     * nothing, when the fuel it burns next leaves something, a bucket from lava, so nothing is lost.
     */
    long igniteLeavingNothing() {
        int slot = firstFuelled();
        if (slot < 0 || slots.getItem(slot).getCraftingRemainder() != null) {
            return 0L;
        }
        return ignite(remainder -> {
        });
    }

    /**
     * How many of {@code stack} a provider may put in them: as many as they take, of a fuel that
     * leaves nothing behind when burnt, so no bucket is dropped under the Rail; none of anything else.
     */
    long roomFromHatch(ItemStack stack) {
        return LocomotiveEntity.fuelValue(level, stack) > 0L && stack.getCraftingRemainder() == null ? roomFor(stack) : 0;
    }

    /** How many of {@code held} they take: each holds the same fuel or nothing. */
    int roomFor(ItemStack held) {
        int room = 0;
        for (int share : shares(held, Integer.MAX_VALUE)) {
            room += share;
        }
        return room;
    }

    /**
     * Puts what fits of {@code stack} in them, those holding the same fuel first, then empty ones;
     * how many went in, {@code stack} itself left as it is.
     */
    int add(ItemStack stack) {
        int[] shares = shares(stack, stack.getCount());
        int added = 0;
        for (int i = 0; i < Locomotive.FUEL_SLOTS; i++) {
            int share = shares[i];
            if (share <= 0) {
                continue;
            }
            int slot = LocomotiveMenu.FUEL + i;
            ItemStack in = slots.getItem(slot);
            if (in.isEmpty()) {
                slots.setItem(slot, stack.copyWithCount(share));
            } else {
                in.grow(share);
            }
            added += share;
        }
        if (added > 0) {
            slots.setChanged();
        }
        return added;
    }

    /**
     * How many of up to {@code count} of {@code stack} each fuel slot takes, those holding the same
     * fuel first, then empty ones.
     */
    private int[] shares(ItemStack stack, int count) {
        int[] shares = new int[Locomotive.FUEL_SLOTS];
        int left = count;
        for (int i = 0; i < Locomotive.FUEL_SLOTS && left > 0; i++) {
            ItemStack in = slots.getItem(LocomotiveMenu.FUEL + i);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, stack)) {
                shares[i] = Math.min(in.getMaxStackSize() - in.getCount(), left);
                left -= shares[i];
            }
        }
        for (int i = 0; i < Locomotive.FUEL_SLOTS && left > 0; i++) {
            if (slots.getItem(LocomotiveMenu.FUEL + i).isEmpty()) {
                shares[i] = Math.min(stack.getMaxStackSize(), left);
                left -= shares[i];
            }
        }
        return shares;
    }
}
