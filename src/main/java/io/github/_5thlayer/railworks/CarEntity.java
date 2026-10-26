// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailCurve;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jspecify.annotations.Nullable;

/**
 * A car in the world: the view of its {@link Car}, coupled into a {@link Train} that
 * {@link LevelRails} owns. The model holds the place, speed and heading (the Train's) and which way
 * the car faces in it; this entity rides the Rail where the model puts its car and mirrors what a
 * client needs (the renderer's heading, Jade, the Placement Preview's coupling) into synched data.
 * The view of a Train's first car runs the Train's tick and moves the other cars' views with it.
 *
 * <p>Using a car boards it. Sneaking at it opens its menu, as does the inventory key while riding;
 * sneaking gets off. A kind of car nobody rides, the Cargo car, opens its menu when used instead. Shears uncouple it from the car on the side used, parting a Train at rest in
 * two. A player leading a mob puts the mob in instead, and a lead used on a car carrying a mob leads
 * it out. A car comes off the Rail, dropping itself and what it holds, when a Span its Train stands
 * on is cut.
 */
public abstract class CarEntity extends VehicleEntity implements HasCustomInventoryScreen {

    public static final float WIDTH = 1.0F;
    public static final float HEIGHT = 2.75F;

    /** Its own synched {@link #DATA_LINKS} bits. */
    private static final byte FIRST = 1;
    private static final byte LAST = 2;
    private static final byte REVERSED = 4;
    private static final byte AT_REST = 8;

    private static final EntityDataAccessor<BlockPos> DATA_SPAN_A =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> DATA_SPAN_B =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> DATA_FRONT_B =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_TO_FRONT =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_LINKS =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BYTE);

    /** Steps the client's car toward each position the server sends, as a boat's does, rather than snapping to it. */
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    /** The id of its car, which {@link LevelRails} finds its Train by. */
    private @Nullable UUID carId;

    protected CarEntity(EntityType<? extends CarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** What kind of car it is the view of, which a client knows without the car. */
    public abstract CarKind carKind();

    /** Sets this entity as the view of {@code car}, which {@code train} in its level's {@link LevelRails} holds. */
    public void setCar(Train train, Car car) {
        if (train.car(car.id()) != car) {
            throw new IllegalArgumentException(car + " is not a car of " + train);
        }
        this.carId = car.id();
        if (level() instanceof ServerLevel server) {
            LevelRails.of(server).bind(this);
        }
        moveToModel(train);
        syncFromModel(train);
    }

    public @Nullable UUID carId() {
        return carId;
    }

    /** Its Train, on the server; null on a client, or for a car no Train holds. */
    public @Nullable Train train() {
        return carId != null && level() instanceof ServerLevel server ? LevelRails.of(server).trains().of(carId) : null;
    }

    /** Its car, on the server. */
    public @Nullable Car car() {
        Train train = train();
        return train == null ? null : train.car(carId);
    }

    /** Whether some car in {@code level} rides {@code span}, as a client sees the cars. */
    static boolean seenOn(Level level, Span span) {
        AABB around = new AABB(span.a().x(), span.a().y(), span.a().z(), span.b().x(), span.b().y(), span.b().z())
                .inflate(HEIGHT + 1.0);
        return !level.getEntitiesOfClass(CarEntity.class, around, car -> car.holds(span)).isEmpty();
    }

    /** Whether this car rides {@code span}, as the client knows it from synched data. */
    public boolean holds(Span span) {
        BlockPos a = entityData.get(DATA_SPAN_A);
        BlockPos b = entityData.get(DATA_SPAN_B);
        return a.equals(PylonColumn.block(span.a())) && b.equals(PylonColumn.block(span.b()));
    }

    // ---- what a client sees ----

    /** Blocks a second, positive the way this car faces. */
    public double speedPerSecond() {
        return LocomotiveSpec.blocksPerSecond(entityData.get(DATA_SPEED));
    }

    /** Which end the car faces, for the renderer: true when it is the Span's second end. */
    public boolean facesSecondEnd() {
        return entityData.get(DATA_FRONT_B);
    }

    /**
     * Where a client sees the car, with its Train's heading, on {@code network}'s copy of its Span;
     * null when that Span is not there.
     */
    @Nullable Place seenPlace(RailNetwork network) {
        Arm a = PylonColumn.arm(entityData.get(DATA_SPAN_A));
        Arm b = PylonColumn.arm(entityData.get(DATA_SPAN_B));
        Span span = network.get(a, b);
        if (span == null) {
            return null;
        }
        Place own = new Place(span, facesSecondEnd() ? span.b() : span.a(), entityData.get(DATA_TO_FRONT));
        return (links() & REVERSED) != 0 ? own.turned() : own;
    }

    /** Whether a client sees it as its Train's first car, its last, or both. */
    boolean seenFirst() {
        return (links() & FIRST) != 0;
    }

    boolean seenLast() {
        return (links() & LAST) != 0;
    }

    /** Whether a client sees its Train at rest, so a car may couple to it. */
    boolean seenAtRest() {
        return (links() & AT_REST) != 0;
    }

    private byte links() {
        return entityData.get(DATA_LINKS);
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
        LevelRails rails = LevelRails.of(level);
        Train train = train();
        if (train == null) {
            // No Train holds its car: a world saved between the two, or the car was dropped.
            destroy(level, damageSources().generic());
            return;
        }
        rails.bind(this);
        if (!train.onRail(rails.network())) {
            // A Span it stood on was cut, or its Arm moved: the whole Train falls off the Rail.
            rails.derail(level, train);
            return;
        }
        // The first car's view runs its Train's tick, a Train moving once a tick whatever its length.
        if (train.cars().getFirst().id().equals(carId)) {
            rails.tick(level, train);
        }
        moveToModel(train);
        syncFromModel(train);
    }

    /** Sets the entity where the model says its car is: on the Rail, facing its own way. */
    void moveToModel(Train train) {
        Car car = train.car(carId);
        if (car == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        // On the Span's curve, heading and pitched the way it runs there toward the car's front.
        RailNetwork network = LevelRails.of(server).network();
        RailCurve.Pose pose = train.facing(network, car).pose(network);
        Vec3 rail = CarPlacement.railPoint(pose.point());
        double speed = train.reversed(car) ? -train.speed() : train.speed();
        double horizontal = Math.sqrt(pose.dx() * pose.dx() + pose.dz() * pose.dz());
        setYRot((float) (Mth.atan2(-pose.dx(), pose.dz()) * Mth.RAD_TO_DEG));
        setXRot((float) (-Mth.atan2(pose.dy(), horizontal) * Mth.RAD_TO_DEG));
        setDeltaMovement(pose.dx() * speed, pose.dy() * speed, pose.dz() * speed);
        setPos(rail.x, rail.y, rail.z);
    }

    /** Mirrors what a client needs of its car into synched data. */
    void syncFromModel(Train train) {
        Car car = train.car(carId);
        if (car == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        Place place = train.facing(LevelRails.of(server).network(), car);
        entityData.set(DATA_SPAN_A, PylonColumn.block(place.span().a()));
        entityData.set(DATA_SPAN_B, PylonColumn.block(place.span().b()));
        entityData.set(DATA_FRONT_B, place.front().equals(place.span().b()));
        entityData.set(DATA_TO_FRONT, (float) place.toFront());
        entityData.set(DATA_SPEED, (float) (train.reversed(car) ? -train.speed() : train.speed()));
        byte links = 0;
        if (train.cars().getFirst() == car) {
            links |= FIRST;
        }
        if (train.cars().getLast() == car) {
            links |= LAST;
        }
        if (train.reversed(car)) {
            links |= REVERSED;
        }
        if (train.speed() == 0.0) {
            links |= AT_REST;
        }
        entityData.set(DATA_LINKS, links);
        syncCar(car);
    }

    /** Mirrors what a client needs of this kind of car. */
    protected void syncCar(Car car) {
    }

    // ---- using it ----

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        InteractionResult inherited = super.interact(player, hand, location);
        if (inherited != InteractionResult.PASS) {
            return inherited;
        }
        ItemStack held = player.getItemInHand(hand);
        // Shears cut a coupling, as they cut a lead.
        if (held.canPerformAction(ItemAbilities.SHEARS_HARVEST)) {
            if (level() instanceof ServerLevel server) {
                uncouple(server, player, hand, location);
            }
            return InteractionResult.SUCCESS;
        }
        // A car nobody rides is used for what it holds: its own use of the item in hand, else its menu.
        if (!rideable()) {
            InteractionResult used = useHeld(player, hand, held);
            return used != InteractionResult.PASS ? used : openMenu(player);
        }
        // Sneaking at the car from outside opens its menu.
        if (player.isSecondaryUseActive()) {
            return openMenu(player);
        }
        if (held.is(Items.LEAD) && getFirstPassenger() instanceof Mob mob && mob instanceof Leashable leashable
                && leashable.canBeLeashed()) {
            if (!level().isClientSide()) {
                leadOut(player, held, mob, leashable);
            }
            return InteractionResult.SUCCESS;
        }
        InteractionResult used = useHeld(player, hand, held);
        if (used != InteractionResult.PASS) {
            return used;
        }
        if (canAddPassenger(player)) {
            Mob led = ledMob(player);
            if (led != null) {
                if (!level().isClientSide()) {
                    pullIn(player, led);
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (!level().isClientSide() && !player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult openMenu(Player player) {
        if (!level().isClientSide()) {
            openCustomInventoryScreen(player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Whether players and led mobs board this kind of car: the one switch for riding it. One nobody
     * rides opens its menu when used.
     */
    protected boolean rideable() {
        return true;
    }

    /** What this kind of car does with {@code held} before boarding, or before its menu opens; PASS to go on. */
    protected InteractionResult useHeld(Player player, InteractionHand hand, ItemStack held) {
        return InteractionResult.PASS;
    }

    /**
     * Parts its Train at the coupler on the side of the car the player used, {@code location}
     * being where on the car, or at the other side's when that one has no car coupled. A moving
     * Train will not part.
     */
    private void uncouple(ServerLevel level, Player player, InteractionHand hand, Vec3 location) {
        Train train = train();
        Car car = car();
        if (train == null || car == null) {
            return;
        }
        // Ahead the way this car faces, from its yaw.
        double ahead = -Mth.sin(getYRot() * Mth.DEG_TO_RAD) * location.x + Mth.cos(getYRot() * Mth.DEG_TO_RAD) * location.z;
        int at = train.coupler(car, ahead >= 0.0 ? 1 : -1);
        if (at < 0) {
            return;
        }
        LevelRails rails = LevelRails.of(level);
        if (rails.uncouple(level, train, at) == null) {
            if (player instanceof ServerPlayer server) {
                server.sendSystemMessage(Component.translatable("message.railworks.uncouple_moving"), true);
            }
            return;
        }
        player.getItemInHand(hand).hurtAndBreak(1, player, hand);
        playSound(SoundEvents.CHAIN_BREAK);
    }

    /** The nearest mob {@code player} leads that may ride a car, or null. */
    private @Nullable Mob ledMob(Player player) {
        List<Leashable> led = Leashable.leashableInArea(this, l -> l.getLeashHolder() == player);
        return led.stream()
                .filter(l -> l instanceof Mob mob && mob.isAlive() && !mob.isPassenger() && mob.getPassengers().isEmpty())
                .map(Mob.class::cast)
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    /** Pulls a led mob into the car, handing the lead back, so the lead does not pull it out again. */
    private void pullIn(Player player, Mob mob) {
        ((Leashable) mob).removeLeash();
        if (mob.startRiding(this)) {
            playSound(SoundEvents.LEAD_TIED);
        }
        if (!player.hasInfiniteMaterials()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.LEAD));
        }
    }

    /** Leads the mob riding the car out of it, on the lead in hand. */
    private void leadOut(Player player, ItemStack lead, Mob mob, Leashable leashable) {
        mob.stopRiding();
        leashable.setLeashedTo(player, true);
        lead.consume(1, player);
        playSound(SoundEvents.LEAD_TIED);
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
        return rideable() && getPassengers().isEmpty();
    }

    /**
     * The seat, in the cab forward of the car's middle, stands square to the car's floor, so a rider
     * leans back on a climb and forward on a descent.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        Vec3 seat = new Vec3(0.0, CarBody.SEAT_Y, CarBody.SEAT_Z);
        return seat.xRot(-getXRot() * Mth.DEG_TO_RAD).yRot(-getYRot() * Mth.DEG_TO_RAD);
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
            if (Double.isFinite(floor) && level().noCollision(passenger, passenger.getBoundingBox().move(
                    spot.subtract(passenger.position())))) {
                return spot;
            }
            probe.move(0, -1, 0);
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ---- breaking it ----

    /**
     * Gone for good, broken, dropped or discarded rather than unloaded: what it holds drops, and its
     * Train closes up without it.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server && reason.shouldDestroy()) {
            dropContents(server);
            if (carId != null) {
                LevelRails.of(server).carGone(carId);
            }
        }
        super.remove(reason);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (carId != null && level() instanceof ServerLevel server) {
            LevelRails.of(server).bind(this);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level() instanceof ServerLevel server) {
            LevelRails.of(server).unbind(this);
        }
    }

    /** Breaks off the Rail, dropping itself and what it holds. */
    void breakOff(ServerLevel level) {
        destroy(level, damageSources().generic());
    }

    /** Drops what the car holds, as it leaves the world. */
    protected abstract void dropContents(ServerLevel level);

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
        entityData.define(DATA_TO_FRONT, 0.0F);
        entityData.define(DATA_SPEED, 0.0F);
        entityData.define(DATA_LINKS, (byte) 0);
    }

    /** Its car's id; the Train is saved once, with the level's {@link LevelRails}. */
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (carId != null) {
            output.store("car_id", Car.ID_CODEC, carId);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.read("car_id", Car.ID_CODEC).ifPresent(id -> this.carId = id);
    }

    /** Takes {@code train} into its level's {@link LevelRails} and views its car at {@code index}: a car saved with its own Train. */
    protected void adoptLegacy(Train train, int index) {
        if (index < 0 || index >= train.cars().size() || !train.cars().get(index).kind().equals(carKind().model())
                || !(level() instanceof ServerLevel server)) {
            return;
        }
        LevelRails.of(server).add(train);
        setCar(train, train.cars().get(index));
    }

    /** Opens a menu, if {@code player} is on the server. */
    protected static void open(Player player, MenuProvider menu) {
        if (player instanceof ServerPlayer server) {
            server.openMenu(menu);
        }
    }
}
