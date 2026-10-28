// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import io.github._5thlayer.railworks.rail.Logistics;
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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive in the world: the view of its {@link Locomotive}, a car in a {@link Train}.
 * The model runs the drive and holds the joule fuel (the car's); this entity feeds it the driver's
 * keys and the fuel slots, and mirrors the fuel, its Train's mode and its status line that a client
 * shows (its menu and Jade) into synched data. Its {@link Locomotive#FUEL_SLOTS} fuel slots each hold
 * a stack of one fuel, the fuels free to differ, and burn from the first that holds any; they are
 * its car's record in the level's {@link CarRecords} (ADR 0008), which this entity is a view onto.
 *
 * <p>Fuel in hand fills the fuel slots rather than boarding, as a provider beside it at a Station
 * does (ADR 0008); the rest is {@link CarEntity}'s.
 */
public final class LocomotiveEntity extends CarEntity {

    private static final EntityDataAccessor<Long> DATA_FUEL =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_FUEL_CAPACITY =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_AUTOMATIC =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Component> DATA_STATUS =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.COMPONENT);
    /** The status line's icons, one an item, an empty stack past the last. */
    private static final List<EntityDataAccessor<ItemStack>> DATA_STATUS_ICONS = IntStream.range(0, StatusLine.ICONS)
            .mapToObj(icon -> SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.ITEM_STACK))
            .toList();
    private static final EntityDataAccessor<Integer> DATA_STATUS_MORE =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.INT);

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

    /** Whether its Train is automatic, as the server last synced it. */
    public boolean automaticSeen() {
        return entityData.get(DATA_AUTOMATIC);
    }

    /** Its Train's status line, as the server last synced it. */
    public Component statusSeen() {
        return entityData.get(DATA_STATUS);
    }

    /** The items its status line shows as icons, each a stack of its count, as the server last synced them. */
    public List<ItemStack> statusIconsSeen() {
        return DATA_STATUS_ICONS.stream().map(entityData::get).filter(stack -> !stack.isEmpty()).toList();
    }

    /** How many items its status line names beyond its icons, as the server last synced it. */
    public int statusMoreSeen() {
        return entityData.get(DATA_STATUS_MORE);
    }

    @Override
    protected void syncCar(ServerLevel level, Train train, Car car) {
        if (car instanceof Locomotive locomotive) {
            entityData.set(DATA_FUEL, locomotive.fuel().storedJoules());
            entityData.set(DATA_FUEL_CAPACITY, locomotive.fuel().gaugeCapacity());
        }
        entityData.set(DATA_AUTOMATIC, train.automatic());
        Logistics.Status<Item> status = LevelRails.of(level).status(level, train);
        entityData.set(DATA_STATUS, StatusLine.of(status));
        List<ItemStack> icons = StatusLine.icons(status);
        for (int icon = 0; icon < StatusLine.ICONS; icon++) {
            ItemStack shown = icon < icons.size() ? icons.get(icon) : ItemStack.EMPTY;
            // A stack equals only itself, so the same icon set again would sync every tick.
            if (!ItemStack.matches(entityData.get(DATA_STATUS_ICONS.get(icon)), shown)) {
                entityData.set(DATA_STATUS_ICONS.get(icon), shown);
            }
        }
        entityData.set(DATA_STATUS_MORE, StatusLine.beyondIcons(status));
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

    /** On the server, the fuel it burns next, the first fuel slot's that holds any, a copy. */
    public ItemStack fuelItem() {
        return fuel().nextFuel();
    }

    /** On the server, what fuel slot {@code slot} holds, a copy. */
    public ItemStack fuelSlot(int slot) {
        return fuel().slot(slot);
    }

    /** Its fuel slots, on the server: its record in the level's {@link CarRecords}. */
    FuelSlots fuel() {
        return new FuelSlots(level(), record());
    }

    /** Whether its slots hold fuel, which logistics counts as able to pull before its buffer is lit. */
    boolean hasFuel() {
        return fuel().hasFuel();
    }

    /**
     * Burns one fuel item whole from the first slot that holds any and says what it was worth; 0 when
     * there is none. What it leaves behind, a bucket from lava, drops where the car is.
     */
    long ignite() {
        return fuel().ignite(remainder -> {
            if (level() instanceof ServerLevel server) {
                spawnAtLocation(server, remainder);
            }
        });
    }

    /** What an item is worth to a Locomotive: its vanilla burn time in joules; 0 for a stack that is not fuel. */
    public static long fuelValue(Level level, ItemStack stack) {
        return stack.isEmpty() ? 0L : LocomotiveSpec.joules(stack.getBurnTime(null, level.fuelValues()));
    }

    // ---- using it ----

    /** Fuel in hand fills the slots as a shortcut; fuel they cannot take boards instead. */
    @Override
    protected InteractionResult useHeld(Player player, InteractionHand hand, ItemStack held) {
        if (fuelValue(level(), held) > 0L && fuel().roomFor(held) > 0) {
            if (!level().isClientSide()) {
                held.shrink(fuel().add(held));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** The menu, from the inventory key while riding and from sneaking at the car. */
    @Override
    public void openCustomInventoryScreen(Player player) {
        open(player, new SimpleMenuProvider((id, inventory, opener) -> new LocomotiveMenu(id, inventory, record(), this),
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
    protected Item getDropItem() {
        return RailworksRegistries.LOCOMOTIVE.get();
    }

    // ---- saving ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_FUEL, 0L);
        entityData.define(DATA_FUEL_CAPACITY, 0L);
        entityData.define(DATA_AUTOMATIC, false);
        entityData.define(DATA_STATUS, Component.empty());
        DATA_STATUS_ICONS.forEach(icon -> entityData.define(icon, ItemStack.EMPTY));
        entityData.define(DATA_STATUS_MORE, 0);
    }

    @Override
    protected int recordSlots() {
        return LocomotiveMenu.CAB_SLOTS;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // A cab named on the entity, saved before the level kept its record or written by a command:
        // slot by slot, one saved with one fuel slot filling the first.
        Optional<List<ItemStack>> cab = input.list("cab", ItemStack.OPTIONAL_CODEC).map(saved -> saved.stream().toList());
        if (carId() == null) {
            // A Locomotive saved with its own Train, before the level held Trains, or before Trains
            // had cars, when the Locomotive was the whole Train.
            List<ItemStack> held = cab.orElse(List.of());
            input.read("train", Train.CODEC).ifPresentOrElse(
                    train -> adoptLegacy(train, input.getIntOr("car", 0), held),
                    () -> input.read("locomotive", Train.LEGACY_LOCOMOTIVE_CODEC).ifPresent(train -> adoptLegacy(train, 0, held)));
        } else {
            cab.ifPresent(this::takeSaved);
        }
    }
}
