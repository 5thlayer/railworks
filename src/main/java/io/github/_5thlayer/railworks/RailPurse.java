// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The Rail a player carries: counted, spent and refunded the same for everyone, creative included. */
public final class RailPurse {

    private RailPurse() {
    }

    public static int count(Player player) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(RailworksRegistries.RAIL)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static void spend(Player player, int amount) {
        Inventory inventory = player.getInventory();
        int left = amount;
        for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(RailworksRegistries.RAIL)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /** Into the inventory, and at the player's feet for what does not fit. */
    public static void refund(Player player, int amount) {
        int left = amount;
        while (left > 0) {
            int part = Math.min(left, RailworksRegistries.RAIL.get().getDefaultMaxStackSize());
            player.getInventory().placeItemBackInInventory(new ItemStack(RailworksRegistries.RAIL.get(), part));
            left -= part;
        }
    }
}
