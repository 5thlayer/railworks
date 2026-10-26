// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.CargoCar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Cargo car in the world: the view of its {@link CargoCar}, holding a chest's worth of items,
 * which its menu, vanilla's three-row chest, shows when the car is used. It is no {@link Container}
 * and gives no item capability, so no hopper, feeder or pipe reaches its cargo: unattended, items
 * go in and out through a Station's hatches (ADR 0008). Nobody rides it; coupling and the rest are
 * {@link CarEntity}'s.
 */
public final class CargoCarEntity extends CarEntity {

    /** A chest's worth, three rows of nine. */
    public static final int SLOTS = 27;

    /** How far from the car the player may stand with its menu open, in blocks. */
    private static final double REACH = 8.0;

    private final SimpleContainer cargo = new SimpleContainer(SLOTS) {
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
    };

    /** How many have its menu open, on the server: a chest sounds for the first to open it and the last to close it. */
    private int openers;

    public CargoCarEntity(EntityType<? extends CargoCarEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public CarKind carKind() {
        return CarKind.CARGO;
    }

    /** What it carries: the server's, for its menu. */
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
        open(player, new SimpleMenuProvider((id, inventory, opener) -> ChestMenu.threeRows(id, inventory, cargo),
                Component.translatable("entity.railworks.cargo_car")));
    }

    @Override
    protected void dropContents(ServerLevel level) {
        for (int slot = 0; slot < cargo.getContainerSize(); slot++) {
            if (!cargo.getItem(slot).isEmpty()) {
                spawnAtLocation(level, cargo.removeItemNoUpdate(slot));
            }
        }
    }

    @Override
    protected Item getDropItem() {
        return RailworksRegistries.CARGO_CAR.get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        // Slot by slot, so what was put where stays there.
        ContainerHelper.saveAllItems(output.child("cargo"), cargo.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.child("cargo").ifPresent(saved -> ContainerHelper.loadAllItems(saved, cargo.getItems()));
    }
}
