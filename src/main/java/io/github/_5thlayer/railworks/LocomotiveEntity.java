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
 * keys and the fuel slots, and mirrors the fuel, its Train's mode and its status line that a client
 * shows (its menu and Jade) into synched data. Its {@link Locomotive#FUEL_SLOTS} fuel slots each hold
 * a stack of one fuel, the fuels free to differ, and burn from the first that holds any.
 *
 * <p>Fuel in hand fills the fuel slots rather than boarding, as a provider beside it at a Station
 * does (ADR 0008, amended in #53); the rest is {@link CarEntity}'s.
 */
public final class LocomotiveEntity extends CarEntity {

    private static final EntityDataAccessor<Long> DATA_FUEL =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_FUEL_CAPACITY =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<ItemStack> DATA_FUEL_ITEM =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_AUTOMATIC =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Component> DATA_STATUS =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.COMPONENT);

    /** What the cab holds, which the menu shows: the fuel slots. */
    private final SimpleContainer cab = new SimpleContainer(LocomotiveMenu.CAB_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (!level().isClientSide()) {
                entityData.set(DATA_FUEL_ITEM, nextFuel());
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

    /** The fuel it burns next, the first fuel slot's that holds any, which a client sees. */
    public ItemStack fuelItem() {
        return entityData.get(DATA_FUEL_ITEM);
    }

    /** Whether its Train is automatic, as the server last synced it. */
    public boolean automaticSeen() {
        return entityData.get(DATA_AUTOMATIC);
    }

    /** Its Train's status line, as the server last synced it. */
    public Component statusSeen() {
        return entityData.get(DATA_STATUS);
    }

    @Override
    protected void syncCar(ServerLevel level, Train train, Car car) {
        if (car instanceof Locomotive locomotive) {
            entityData.set(DATA_FUEL, locomotive.fuel().storedJoules());
            entityData.set(DATA_FUEL_CAPACITY, locomotive.fuel().gaugeCapacity());
        }
        entityData.set(DATA_FUEL_ITEM, nextFuel());
        entityData.set(DATA_AUTOMATIC, train.automatic());
        entityData.set(DATA_STATUS, StatusLine.of(LevelRails.of(level).status(level, train)));
    }

    // ---- the drive ----

    /** Whether a player sits in it to drive it, pressing a key or not. */
    boolean driven() {
        return getFirstPassenger() instanceof Player;
    }

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

    /** On the server, what fuel slot {@code slot} holds, a copy. */
    public ItemStack fuelSlot(int slot) {
        return cab.getItem(LocomotiveMenu.FUEL + slot).copy();
    }

    /** The first of its fuel slots holding fuel; -1 for none. */
    private int firstFuelled() {
        for (int slot = LocomotiveMenu.FUEL; slot < LocomotiveMenu.FUEL + Locomotive.FUEL_SLOTS; slot++) {
            if (fuelValue(level(), cab.getItem(slot)) > 0L) {
                return slot;
            }
        }
        return -1;
    }

    /** A copy of the fuel it burns next; empty for none. */
    private ItemStack nextFuel() {
        int slot = firstFuelled();
        return slot < 0 ? ItemStack.EMPTY : cab.getItem(slot).copy();
    }

    /** Whether its slots hold fuel, which logistics counts as able to pull before its buffer is lit. */
    boolean hasFuel() {
        return firstFuelled() >= 0;
    }

    /** Burns one fuel item whole from the first slot that holds any and says what it was worth; 0 when there is none. */
    long ignite() {
        int slot = firstFuelled();
        if (slot < 0) {
            return 0L;
        }
        ItemStack fuel = cab.getItem(slot);
        long joules = fuelValue(level(), fuel);
        var remainder = fuel.getCraftingRemainder();
        cab.removeItem(slot, 1);
        if (remainder != null && level() instanceof ServerLevel server) {
            spawnAtLocation(server, remainder.create());
        }
        return joules;
    }

    /** What an item is worth to a Locomotive: its vanilla burn time in joules; 0 for a stack that is not fuel. */
    public static long fuelValue(Level level, ItemStack stack) {
        return stack.isEmpty() ? 0L : LocomotiveSpec.joules(stack.getBurnTime(null, level.fuelValues()));
    }

    /**
     * How many of {@code stack} a provider may put in its fuel slots: as many as they take, of a fuel
     * that leaves nothing behind when burnt, so no bucket is dropped under the Rail; none of
     * anything else.
     */
    long fuelRoomFromHatch(ItemStack stack) {
        return fuelValue(level(), stack) > 0L && stack.getCraftingRemainder() == null ? fuelRoomFor(stack) : 0;
    }

    /** Puts what fits of {@code stack} in its fuel slots, as fuel in hand fills them, and says how many went in. */
    int addFuel(ItemStack stack) {
        int before = stack.getCount();
        ItemStack left = stack.copy();
        fillFuel(left);
        return before - left.getCount();
    }

    // ---- using it ----

    /** Fuel in hand fills the slots as a shortcut; fuel they cannot take boards instead. */
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

    /** How many of {@code held} the fuel slots take: each holds the same fuel or nothing. */
    private int fuelRoomFor(ItemStack held) {
        int room = 0;
        for (int slot = LocomotiveMenu.FUEL; slot < LocomotiveMenu.FUEL + Locomotive.FUEL_SLOTS; slot++) {
            ItemStack in = cab.getItem(slot);
            if (in.isEmpty()) {
                room += held.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(in, held)) {
                room += in.getMaxStackSize() - in.getCount();
            }
        }
        return room;
    }

    /** Moves what fits of {@code held} into the fuel slots, those holding the same fuel first, then empty ones, and nowhere else in the cab. */
    private void fillFuel(ItemStack held) {
        for (int slot = LocomotiveMenu.FUEL; slot < LocomotiveMenu.FUEL + Locomotive.FUEL_SLOTS && !held.isEmpty(); slot++) {
            ItemStack in = cab.getItem(slot);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, held)) {
                int moved = Math.min(in.getMaxStackSize() - in.getCount(), held.getCount());
                in.grow(moved);
                held.shrink(moved);
            }
        }
        for (int slot = LocomotiveMenu.FUEL; slot < LocomotiveMenu.FUEL + Locomotive.FUEL_SLOTS && !held.isEmpty(); slot++) {
            if (cab.getItem(slot).isEmpty()) {
                int moved = Math.min(held.getMaxStackSize(), held.getCount());
                cab.setItem(slot, held.copyWithCount(moved));
                held.shrink(moved);
            }
        }
        cab.setChanged();
    }

    /** The menu, from the inventory key while riding and from sneaking at the car. */
    @Override
    public void openCustomInventoryScreen(Player player) {
        open(player, new SimpleMenuProvider((id, inventory, opener) -> new LocomotiveMenu(id, inventory, cab, this),
                Component.translatable("entity.railworks.burner_locomotive")));
    }

    /** Switches its Train between automatic and manual, as the menu's toggle does; false for a car no Train holds. */
    boolean toggleAutomatic() {
        Train train = train();
        if (train == null || !(level() instanceof ServerLevel level)) {
            return false;
        }
        train.setAutomatic(!train.automatic());
        LevelRails.of(level).setDirty();
        syncFromModel(train);
        return true;
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
        entityData.define(DATA_AUTOMATIC, false);
        entityData.define(DATA_STATUS, Component.empty());
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
