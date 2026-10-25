// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.HopperSpots;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * A Cargo car in the world: the view of its {@link CargoCar}, holding a chest's worth of items,
 * which its menu, vanilla's three-row chest, shows. It is not a {@link Container} itself, so a hopper
 * cannot reach into it wherever it is: only while it is stopped at a Station does it give hoppers,
 * feeders and pipes its cargo, through {@link #automation()}, and a client sees that it is, to draw
 * its {@link #hopperSpots()}. Boarding and the rest are
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
            return isAlive() && (hasPassenger(player) || player.distanceToSqr(CargoCarEntity.this) < REACH * REACH);
        }
    };

    /** Whether it is stopped at a Station, which a client sees to draw where hoppers go. */
    private static final EntityDataAccessor<Boolean> DATA_AT_STATION =
            SynchedEntityData.defineId(CargoCarEntity.class, EntityDataSerializers.BOOLEAN);

    /** Its cargo as NeoForge's transfer sees it. */
    private final ResourceHandler<ItemResource> handler = VanillaContainerWrapper.of(cargo);

    public CargoCarEntity(EntityType<? extends CargoCarEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public CarKind carKind() {
        return CarKind.CARGO;
    }

    /** What it carries: the server's, for Jade and the menu. */
    public Container cargo() {
        return cargo;
    }

    /**
     * Its cargo to hoppers, feeders and pipes, NeoForge's item capability for automation: while it is
     * stopped at a Station, and null while it moves, stands away from every Station, or is a client's.
     */
    public @Nullable ResourceHandler<ItemResource> automation() {
        Train train = train();
        if (train == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Car car = train.car(carId());
        boolean stopped = !LevelStations.of(server).stations()
                .stoppedAt(LevelRails.of(server).network(), train, car).isEmpty();
        return stopped ? handler : null;
    }

    /** Whether it is stopped at a Station, as the server last synced it: what the overlay draws by. */
    public boolean atStation() {
        return entityData.get(DATA_AT_STATION);
    }

    /** Where a hopper loads it and where one unloads it, from its box where it stands. */
    public HopperSpots hopperSpots() {
        return HopperSpots.of(getX(), getBoundingBox().minY, getZ(), getBbHeight());
    }

    @Override
    protected void syncCar(Car car) {
        entityData.set(DATA_AT_STATION, automation() != null);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_AT_STATION, false);
    }

    /** The menu, from the inventory key while riding and from sneaking at the car. */
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
