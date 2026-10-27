// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
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
 * container's, and an Automatic toggle for its Train (ADR 0008), its status line beneath. The cab
 * holds the fuel slot only, which takes furnace fuel, so {@link #CAB_SLOTS} is the number of slots
 * the cab holds, not "one". Vanilla's packets carry all of it: the Locomotive's entity id goes to
 * the client in two of the menu's data slots, each sending a short, so the client's screen reads the
 * Train's mode and status line from the entity's synched data, and the toggle is a menu button.
 */
public final class LocomotiveMenu extends AbstractContainerMenu {

    public static final int FUEL = 0;
    public static final int CAB_SLOTS = 1;
    /** The menu button that switches its Train between automatic and manual. */
    public static final int TOGGLE_AUTOMATIC = 0;

    /** How far from the car the player may stand with its menu open, in blocks. */
    private static final double REACH = 8.0;
    private static final int FUEL_X = 80;
    private static final int FUEL_Y = 18;
    private static final int INVENTORY_Y = 61;

    private final Container cab;
    private final @Nullable LocomotiveEntity car;
    /** The Locomotive's entity id, low half then high half, as the menu's data slots send it. */
    private final ContainerData carId;

    /** The server's menu, over the car's own cab. */
    public LocomotiveMenu(int containerId, Inventory inventory, Container cab, LocomotiveEntity car) {
        super(RailworksRegistries.LOCOMOTIVE_MENU.get(), containerId);
        this.cab = cab;
        this.car = car;
        int id = car.getId();
        this.carId = new SimpleContainerData(2);
        this.carId.set(0, id & 0xFFFF);
        this.carId.set(1, id >>> 16);
        addSlots(inventory);
    }

    /**
     * The client's menu, over an empty cab the server fills, knowing the Locomotive only by its entity
     * id once the server sends it: the server decides when the menu closes.
     */
    public LocomotiveMenu(int containerId, Inventory inventory) {
        super(RailworksRegistries.LOCOMOTIVE_MENU.get(), containerId);
        this.cab = new SimpleContainer(CAB_SLOTS);
        this.car = null;
        this.carId = new SimpleContainerData(2);
        addSlots(inventory);
    }

    /** The Locomotive's entity id, as the server sent it; 0 on a client until it arrives. */
    public int carId() {
        return (carId.get(1) & 0xFFFF) << 16 | carId.get(0) & 0xFFFF;
    }

    /** The Automatic toggle, from a player still in reach: vanilla checks {@link #stillValid} first. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == TOGGLE_AUTOMATIC && car != null && car.toggleAutomatic();
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
        addDataSlots(carId);
    }

    @Override
    public boolean stillValid(Player player) {
        if (car == null) {
            return true;
        }
        return car.isAlive() && (car.hasPassenger(player) || player.distanceToSqr(car) < REACH * REACH);
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
