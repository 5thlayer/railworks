// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive's menu: the cab's slots over the player's inventory, laid out as a vanilla
 * container's. The cab holds the fuel slot only, which takes furnace fuel; the Schedule (#3) adds
 * its own slots or widgets to the cab, so {@link #CAB_SLOTS} is the number of slots the cab
 * holds, not "one".
 */
public final class LocomotiveMenu extends AbstractContainerMenu {

    public static final int FUEL = 0;
    public static final int CAB_SLOTS = 1;

    private static final int FUEL_X = 80;
    private static final int FUEL_Y = 18;
    private static final int INVENTORY_Y = 49;

    private final Container cab;
    private final @Nullable LocomotiveEntity car;

    /** The server's menu, over the car's own cab. */
    public LocomotiveMenu(int containerId, Inventory inventory, Container cab, LocomotiveEntity car) {
        super(RailworksRegistries.LOCOMOTIVE_MENU.get(), containerId);
        this.cab = cab;
        this.car = car;
        addSlots(inventory);
    }

    /** The client's menu, over an empty cab the server fills, for the car whose id the server sent. */
    public LocomotiveMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        super(RailworksRegistries.LOCOMOTIVE_MENU.get(), containerId);
        this.cab = new SimpleContainer(CAB_SLOTS);
        this.car = inventory.player.level().getEntity(data.readVarInt()) instanceof LocomotiveEntity found ? found : null;
        addSlots(inventory);
    }

    private void addSlots(Inventory inventory) {
        checkContainerSize(cab, CAB_SLOTS);
        Level level = inventory.player.level();
        addSlot(new Slot(cab, FUEL, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return LocomotiveEntity.fuelValue(level, stack) > 0L;
            }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    @Override
    public boolean stillValid(Player player) {
        return car != null && car.isAlive() && (car.hasPassenger(player) || player.distanceToSqr(car) < 64.0);
    }

    /** Shift-click: the cab's slots to the inventory, and the inventory's fuel to the fuel slot. */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = slotIndex < CAB_SLOTS
                ? moveItemStackTo(stack, CAB_SLOTS, slots.size(), true)
                : moveItemStackTo(stack, 0, CAB_SLOTS, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }
}
