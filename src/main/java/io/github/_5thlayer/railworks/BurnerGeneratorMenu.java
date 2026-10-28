// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Burner Generator's menu: its one fuel slot where a furnace has its own, over the player's
 * inventory laid out as a furnace's, and a burn flame above the slot from the menu's data. The slot
 * takes only fuel that leaves nothing behind when burnt.
 */
public final class BurnerGeneratorMenu extends AbstractContainerMenu {

    private static final int FUEL_X = 56;
    private static final int FUEL_Y = 53;
    private static final int INVENTORY_Y = 84;

    private final Container generator;
    private final ContainerData data;

    /** The server's menu, over the generator's fuel slot and its data. */
    public BurnerGeneratorMenu(int containerId, Inventory inventory, Container generator, ContainerData data) {
        super(RailworksRegistries.BURNER_GENERATOR_MENU.get(), containerId);
        checkContainerSize(generator, 1);
        checkContainerDataCount(data, BurnerGeneratorBlockEntity.DATA_COUNT);
        this.generator = generator;
        this.data = data;
        Level level = inventory.player.level();
        addSlot(new Slot(generator, BurnerGeneratorBlockEntity.FUEL, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BurnerGeneratorBlockEntity.burns(level, stack);
            }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    /** The client's menu, over an empty slot the server fills. */
    public BurnerGeneratorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1), new SimpleContainerData(BurnerGeneratorBlockEntity.DATA_COUNT));
    }

    /** How far through the fuel lit it is, 1 just lit and 0 burnt out. */
    public float litProgress() {
        return Math.clamp(data.get(BurnerGeneratorBlockEntity.DATA_LIT) / (float) BurnerGeneratorBlockEntity.LIT_STEPS, 0.0F, 1.0F);
    }

    @Override
    public boolean stillValid(Player player) {
        return generator.stillValid(player);
    }

    /** Shift-click: the fuel slot to the inventory, and the inventory's fuel to the fuel slot. */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = slotIndex == 0
                ? moveItemStackTo(stack, 1, slots.size(), true)
                : slots.getFirst().mayPlace(stack) && moveItemStackTo(stack, 0, 1, false);
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
