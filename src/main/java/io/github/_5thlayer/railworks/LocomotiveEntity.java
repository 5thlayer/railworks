// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Throttle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive in the world: the view of a {@link Locomotive}, which is the Train. The model
 * runs the drive and holds the place, speed, heading and joule fuel; this entity feeds it the
 * driver's keys and the fuel slot, rides the Rail where the model says, and mirrors the numbers a
 * client shows (Jade, the renderer's heading) into synched data.
 *
 * <p>Using it with fuel in hand fills the fuel slot; using it otherwise boards it, and sneaking
 * gets off (vanilla's dismount). It comes off the Rail, dropping itself and its fuel, when the Span
 * it rides is cut.
 */
public final class LocomotiveEntity extends VehicleEntity implements HasCustomInventoryScreen {

    public static final float WIDTH = 1.0F;
    public static final float HEIGHT = 2.0F;

    private static final EntityDataAccessor<BlockPos> DATA_SPAN_A =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> DATA_SPAN_B =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_FRONT_B =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> DATA_FUEL =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_FUEL_CAPACITY =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<ItemStack> DATA_FUEL_ITEM =
            SynchedEntityData.defineId(LocomotiveEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Steps the client's car toward each position the server sends, as a boat's does, rather than snapping to it. */
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    private @Nullable Locomotive model;
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
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Sets this car on the Rail as {@code locomotive}; the entity is its view from then on. */
    public void setLocomotive(Locomotive locomotive) {
        this.model = locomotive;
        moveToModel();
        syncFromModel();
    }

    public @Nullable Locomotive locomotive() {
        return model;
    }

    /** Whether some Locomotive in {@code level} rides {@code span}: one Train holds a Span at a time. */
    public static boolean holding(Level level, Span span) {
        AABB around = new AABB(span.a().x(), span.a().y(), span.a().z(), span.b().x(), span.b().y(), span.b().z())
                .inflate(HEIGHT + 1.0);
        return !level.getEntitiesOfClass(LocomotiveEntity.class, around, car -> car.holds(span)).isEmpty();
    }

    /** The Arm at {@code from} moved to {@code to} with its Spans: this car stays on its Span. */
    public void armMoved(RailNetwork network, Arm from, Arm to) {
        if (model != null) {
            model.moveArm(network, from, to);
            moveToModel();
            syncFromModel();
        }
    }

    /** Whether this car rides {@code span}, as the client knows it from synched data. */
    public boolean holds(Span span) {
        BlockPos a = entityData.get(DATA_SPAN_A);
        BlockPos b = entityData.get(DATA_SPAN_B);
        return a.equals(PylonColumn.block(span.a())) && b.equals(PylonColumn.block(span.b()));
    }

    // ---- what a client shows ----

    /** Blocks a second, positive the way it faces. */
    public double speedPerSecond() {
        return LocomotiveSpec.blocksPerSecond(entityData.get(DATA_SPEED));
    }

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

    // ---- the tick ----

    @Override
    public void tick() {
        if (getHurtTime() > 0) {
            setHurtTime(getHurtTime() - 1);
        }
        if (getDamage() > 0.0F) {
            setDamage(getDamage() - 1.0F);
        }
        super.tick();
        interpolation.interpolate();
        if (level() instanceof ServerLevel server) {
            tickServer(server);
        }
    }

    private void tickServer(ServerLevel level) {
        if (model == null) {
            discard();
            return;
        }
        RailNetwork network = LevelRails.of(level).network();
        if (!model.onRail(network)) {
            // The Span it hung from was cut, or its Arm moved: it falls off the Rail as an item.
            destroy(level, damageSources().generic());
            return;
        }
        model.tick(network, throttle(), this::ignite);
        moveToModel();
        syncFromModel();
    }

    private Throttle throttle() {
        if (getFirstPassenger() instanceof ServerPlayer driver) {
            Input input = driver.getLastClientInput();
            if (input.forward() != input.backward()) {
                return input.forward() ? Throttle.FORWARD : Throttle.BACK;
            }
        }
        return Throttle.NONE;
    }

    /** Burns one fuel item from the slot whole and says what it was worth; 0 when there is none. */
    private long ignite() {
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

    private void moveToModel() {
        if (model == null) {
            return;
        }
        Vec3 rail = LocomotivePlacement.railPoint(model);
        double speed = model.speed();
        Arm front = model.front();
        Arm back = model.span().otherEnd(front);
        double dx = front.x() - back.x();
        double dy = front.y() - back.y();
        double dz = front.z() - back.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        setYRot((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG));
        setXRot((float) (-Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG));
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        setDeltaMovement(dx / length * speed, dy / length * speed, dz / length * speed);
        setPos(rail.x, rail.y, rail.z);
    }

    private void syncFromModel() {
        if (model == null) {
            return;
        }
        entityData.set(DATA_SPAN_A, PylonColumn.block(model.span().a()));
        entityData.set(DATA_SPAN_B, PylonColumn.block(model.span().b()));
        entityData.set(DATA_FRONT_B, model.front().equals(model.span().b()));
        entityData.set(DATA_SPEED, (float) model.speed());
        entityData.set(DATA_FUEL, model.fuel().storedJoules());
        entityData.set(DATA_FUEL_CAPACITY, model.fuel().gaugeCapacity());
        entityData.set(DATA_FUEL_ITEM, cab.getItem(LocomotiveMenu.FUEL).copy());
    }

    // ---- using it ----

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        InteractionResult inherited = super.interact(player, hand, location);
        if (inherited != InteractionResult.PASS) {
            return inherited;
        }
        // Sneaking at the car from outside opens its menu.
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                openCustomInventoryScreen(player);
            }
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        // Fuel in hand fills the slot as a shortcut; fuel the slot cannot take boards instead.
        if (fuelValue(level(), held) > 0L && fuelRoomFor(held) > 0) {
            if (!level().isClientSide()) {
                fillFuel(held);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide() && !player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
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
        if (player instanceof ServerPlayer server) {
            server.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new LocomotiveMenu(id, inventory, cab, this),
                    Component.translatable("entity.railworks.burner_locomotive")));
        }
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    // No getControllingPassenger: the server drives the car, and a controlling passenger would make
    // it client-authoritative, so the client would drop the server's positions for it and the rider
    // would not move until they got off.

    /** Off the car onto the first floor below it, since it rides well above the ground. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos.MutableBlockPos probe = BlockPos.containing(getX(), getY(), getZ()).mutable();
        for (int drop = 0; drop < 24 && !level().isOutsideBuildHeight(probe); drop++) {
            double floor = level().getBlockFloorHeight(probe);
            Vec3 spot = new Vec3(getX(), probe.getY() + floor, getZ());
            if (Double.isFinite(floor) && floor < 1.0 && level().noCollision(passenger, passenger.getBoundingBox().move(
                    spot.subtract(passenger.position())))) {
                return spot;
            }
            probe.move(0, -1, 0);
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ---- breaking it ----

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        for (int slot = 0; slot < cab.getContainerSize(); slot++) {
            if (!cab.getItem(slot).isEmpty()) {
                spawnAtLocation(level, cab.removeItemNoUpdate(slot));
            }
        }
        super.destroy(level, source);
    }

    @Override
    protected Item getDropItem() {
        return RailworksRegistries.LOCOMOTIVE.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    // ---- saving ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_SPAN_A, BlockPos.ZERO);
        entityData.define(DATA_SPAN_B, BlockPos.ZERO);
        entityData.define(DATA_FRONT_B, false);
        entityData.define(DATA_SPEED, 0.0F);
        entityData.define(DATA_FUEL, 0L);
        entityData.define(DATA_FUEL_CAPACITY, 0L);
        entityData.define(DATA_FUEL_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (model != null) {
            output.store("locomotive", Locomotive.CODEC, model);
        }
        cab.storeAsItemList(output.list("cab", ItemStack.OPTIONAL_CODEC));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.read("locomotive", Locomotive.CODEC).ifPresent(this::setLocomotive);
        cab.fromItemList(input.listOrEmpty("cab", ItemStack.OPTIONAL_CODEC));
    }

    /** Which end the car faces, for the renderer: true when it is the Span's second end. */
    public boolean facesSecondEnd() {
        return entityData.get(DATA_FRONT_B);
    }
}
