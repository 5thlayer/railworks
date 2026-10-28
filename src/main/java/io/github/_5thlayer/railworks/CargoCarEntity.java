// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.CargoCar;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;

/**
 * A Cargo car in the world: the view of its {@link CargoCar}, which holds a double chest's worth of
 * items in its record in the level's {@link CarRecords} (ADR 0008), shown by its menu, vanilla's
 * six-row chest, when the car is used. It is no {@link Container} and gives no item capability, so
 * no hopper, feeder or pipe reaches its cargo: with no player there, items go in and out through a
 * Station's hatches (ADR 0008). Nobody rides it; coupling and the rest are {@link CarEntity}'s.
 */
public final class CargoCarEntity extends CarEntity {

    /** A double chest's worth, six rows of nine. */
    public static final int SLOTS = 54;

    /** How far from the car the player may stand with its menu open, in blocks. */
    private static final double REACH = 8.0;

    /** Its menu's window onto its record, the same for every opener. */
    private final Container cargo = new Cargo();

    /** How many have its menu open, on the server: a chest sounds for the first to open it and the last to close it. */
    private int openers;

    public CargoCarEntity(EntityType<? extends CargoCarEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public CarKind carKind() {
        return CarKind.CARGO;
    }

    /**
     * What it carries, on the server: a window onto its car's record in the level's
     * {@link CarRecords}, which its menu shows and Jade reads.
     */
    public Container cargo() {
        return cargo;
    }

    /** Nobody rides a Cargo car: using it opens its cargo, with or without sneaking. */
    @Override
    protected boolean rideable() {
        return false;
    }

    /** Someone opened its menu: a chest's open sound for the first, and a chest boat's game event. */
    private void opened(ContainerUser user) {
        if (openers++ == 0) {
            chestSound(SoundEvents.CHEST_OPEN);
        }
        level().gameEvent(GameEvent.CONTAINER_OPEN, position(), GameEvent.Context.of(user.getLivingEntity()));
    }

    /** Someone closed its menu: a chest's close sound for the last, and a chest boat's game event. */
    private void closed(ContainerUser user) {
        if (openers > 0 && --openers == 0) {
            chestSound(SoundEvents.CHEST_CLOSE);
        }
        level().gameEvent(GameEvent.CONTAINER_CLOSE, position(), GameEvent.Context.of(user.getLivingEntity()));
    }

    /** As a chest plays it: a little quiet, its pitch a little varied. */
    private void chestSound(SoundEvent sound) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.1F + 0.9F);
    }

    /** The menu, from using the car. */
    @Override
    public void openCustomInventoryScreen(Player player) {
        open(player, new SimpleMenuProvider((id, inventory, opener) -> ChestMenu.sixRows(id, inventory, cargo),
                Component.translatable("entity.railworks.cargo_car")));
    }

    @Override
    protected Item getDropItem() {
        return carKind().item();
    }

    @Override
    protected int recordSlots() {
        return SLOTS;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // Cargo named on the entity, saved before the level kept its record or written by a command:
        // slot by slot, so what was put where stays there.
        input.child("cargo").ifPresent(saved -> {
            NonNullList<ItemStack> stacks = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(saved, stacks);
            takeSaved(stacks);
        });
    }

    /**
     * Its record's slots, as its menu sees them: closed to a player too far off or once the car is
     * gone, and sounding as a chest's lid does.
     */
    private final class Cargo implements Container {

        @Override
        public int getContainerSize() {
            return record().getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return record().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return record().getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return record().removeItem(slot, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return record().removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            record().setItem(slot, stack);
        }

        @Override
        public void setChanged() {
            record().setChanged();
        }

        @Override
        public void clearContent() {
            record().clearContent();
        }

        @Override
        public boolean stillValid(Player player) {
            return isAlive() && player.distanceToSqr(CargoCarEntity.this) < REACH * REACH;
        }

        @Override
        public void startOpen(ContainerUser user) {
            opened(user);
        }

        @Override
        public void stopOpen(ContainerUser user) {
            closed(user);
        }
    }
}
