// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import io.github._5thlayer.railworks.rail.Throttle;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive in the world: the view of its {@link Locomotive}, a car in a {@link Train}.
 * The model runs the drive and holds the joule fuel (the car's); this entity feeds it the driver's
 * keys and the fuel slot, and mirrors the fuel a client shows (Jade) into synched data.
 *
 * <p>Fuel in hand fills the fuel slot rather than boarding; the rest is {@link CarEntity}'s.
 */
public final class LocomotiveEntity extends CarEntity {

    private static final EntityDataAccessor<Long> DATA_FUEL =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_FUEL_CAPACITY =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<ItemStack> DATA_FUEL_ITEM =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.ITEM_STACK);

    /** What the cab holds, which the menu shows: the fuel slot, with room beside it for what #3 adds. */
    private final SimpleContainer cab = new SimpleContainer(LocomotiveMenu.CAB_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (!level().isClientSide()) {
                entityData.set(DATA_FUEL_ITEM, cab.getItem(LocomotiveMenu.FUEL).copy());
            }
        }
    };

    public LocomotiveEntity(EntityType<? extends LocomotiveEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public CarKind carKind() {
        return CarKind.LOCOMOTIVE;
    }

    /** Its car, on the server. */
    public @Nullable Locomotive locomotive() {
        return car() instanceof Locomotive locomotive ? locomotive : null;
    }

    // ---- what a client shows ----

    public long fuelJoules() {
        return entityData.get(DATA_FUEL);
    }

    public long fuelCapacityJoules() {
        return entityData.get(DATA_FUEL_CAPACITY);
    }

    /** The fuel waiting in the slot, which a client sees. */
    public ItemStack fuelItem() {
        return entityData.get(DATA_FUEL_ITEM);
    }

    @Override
    protected void syncCar(Car car) {
        if (car instanceof Locomotive locomotive) {
            entityData.set(DATA_FUEL, locomotive.fuel().storedJoules());
            entityData.set(DATA_FUEL_CAPACITY, locomotive.fuel().gaugeCapacity());
        }
        entityData.set(DATA_FUEL_ITEM, cab.getItem(LocomotiveMenu.FUEL).copy());
    }

    // ---- the drive ----

    /** Its driver's keys: forward or back the way this car faces. */
    Throttle throttle() {
        if (getFirstPassenger() instanceof ServerPlayer driver) {
            Input input = driver.getLastClientInput();
            if (input.forward() != input.backward()) {
                return input.forward() ? Throttle.FORWARD : Throttle.BACK;
            }
        }
        return Throttle.NONE;
    }

    /** Burns one fuel item from the slot whole and says what it was worth; 0 when there is none. */
    long ignite() {
        ItemStack fuel = cab.getItem(LocomotiveMenu.FUEL);
        long joules = fuelValue(level(), fuel);
        if (joules <= 0L) {
            return 0L;
        }
        var remainder = fuel.getCraftingRemainder();
        cab.removeItem(LocomotiveMenu.FUEL, 1);
        if (remainder != null && level() instanceof ServerLevel server) {
            spawnAtLocation(server, remainder.create());
        }
        return joules;
    }

    /** What an item is worth to a Locomotive: its vanilla burn time in joules; 0 for a stack that is not fuel. */
    public static long fuelValue(Level level, ItemStack stack) {
        return stack.isEmpty() ? 0L : LocomotiveSpec.joules(stack.getBurnTime(null, level.fuelValues()));
    }

    // ---- using it ----

    /** Fuel in hand fills the slot as a shortcut; fuel the slot cannot take boards instead. */
    @Override
    protected InteractionResult useHeld(Player player, InteractionHand hand, ItemStack held) {
        if (fuelValue(level(), held) > 0L && fuelRoomFor(held) > 0) {
            if (!level().isClientSide()) {
                fillFuel(held);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** How many of {@code held} the fuel slot takes: it holds the same fuel or nothing. */
    private int fuelRoomFor(ItemStack held) {
        ItemStack slot = cab.getItem(LocomotiveMenu.FUEL);
        if (slot.isEmpty()) {
            return held.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(slot, held) ? slot.getMaxStackSize() - slot.getCount() : 0;
    }

    /** Moves what fits of {@code held} into the fuel slot, and nowhere else in the cab. */
    private void fillFuel(ItemStack held) {
        int moved = Math.min(fuelRoomFor(held), held.getCount());
        ItemStack slot = cab.getItem(LocomotiveMenu.FUEL);
        if (slot.isEmpty()) {
            cab.setItem(LocomotiveMenu.FUEL, held.copyWithCount(moved));
        } else {
            slot.grow(moved);
            cab.setChanged();
        }
        held.shrink(moved);
    }

    /** The menu, from the inventory key while riding and from sneaking at the car. */
    @Override
    public void openCustomInventoryScreen(Player player) {
        open(player, new SimpleMenuProvider((id, inventory, opener) -> new LocomotiveMenu(id, inventory, cab, this),
                Component.translatable("entity.railworks.burner_locomotive")));
    }

    // ---- breaking it ----

    @Override
    protected void dropContents(ServerLevel level) {
        for (int slot = 0; slot < cab.getContainerSize(); slot++) {
            if (!cab.getItem(slot).isEmpty()) {
                spawnAtLocation(level, cab.removeItemNoUpdate(slot));
            }
        }
    }

    @Override
    protected Item getDropItem() {
        return RailworksRegistries.LOCOMOTIVE.get();
    }

    // ---- saving ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_FUEL, 0L);
        entityData.define(DATA_FUEL_CAPACITY, 0L);
        entityData.define(DATA_FUEL_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        cab.storeAsItemList(output.list("cab", ItemStack.OPTIONAL_CODEC));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        if (carId() == null) {
            // A Locomotive saved with its own Train, before the level held Trains, or before Trains
            // had cars, when the Locomotive was the whole Train.
            input.read("train", Train.CODEC).ifPresentOrElse(
                    train -> adoptLegacy(train, input.getIntOr("car", 0)),
                    () -> input.read("locomotive", Train.LEGACY_LOCOMOTIVE_CODEC).ifPresent(train -> adoptLegacy(train, 0)));
        }
        cab.fromItemList(input.listOrEmpty("cab", ItemStack.OPTIONAL_CODEC));
    }
}
