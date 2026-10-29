// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.FluidCar;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jspecify.annotations.Nullable;

/**
 * A Fluid car in the world: the view of its {@link FluidCar}, which holds one fluid in a tank in its
 * record in the level's {@link CarRecords}, as a Cargo car's cargo is (ADR 0008), so an abstract
 * Train keeps and trades it. As a Cargo car gives no item capability, it gives no fluid capability,
 * so no pipe reaches its tank: with no player there, fluid goes in and out
 * through a Station's Fluid hatches. Used with a filled bucket, or any fluid container, it pours one
 * in, and with an empty one it takes one out, the fluid permitting; what goes in by hand is a
 * leftover, as hand-loaded items are. Nobody rides it and it opens no menu; coupling and the rest are
 * {@link CarEntity}'s.
 *
 * <p>It mirrors its tank, the fluid, its amount and the tank's capacity, into synched data, as a
 * Locomotive mirrors its fuel, so every player tracking it sees them in its Sight glasses; the record
 * stays the one truth.
 */
public final class FluidCarEntity extends CarEntity {

    private static final EntityDataAccessor<FluidStack> DATA_TANK =
            SynchedEntityData.defineId(FluidCarEntity.class, RailworksRegistries.FLUID_STACK);
    private static final EntityDataAccessor<Integer> DATA_TANK_CAPACITY =
            SynchedEntityData.defineId(FluidCarEntity.class, EntityDataSerializers.INT);

    private static volatile int tankBuckets = FluidCar.DEFAULT_TANK_BUCKETS;

    public FluidCarEntity(EntityType<? extends FluidCarEntity> type, Level level) {
        super(type, level);
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads: how many buckets a Fluid car made from now on holds. */
    public static void configure(int tankBuckets) {
        if (tankBuckets < 1) {
            throw new IllegalArgumentException("Fluid car tank " + tankBuckets);
        }
        FluidCarEntity.tankBuckets = tankBuckets;
    }

    /** How many millibuckets a Fluid car's tank made now holds. */
    static int tankCapacity() {
        return Tank.capacityOf(tankBuckets);
    }

    @Override
    public CarKind carKind() {
        return CarKind.FLUID;
    }

    /**
     * Its tank, on the server: a window onto its car's record in the level's {@link CarRecords}, which
     * buckets fill and empty and Jade reads; null on a client, which sees it only as {@link #tankSeen()}.
     */
    public @Nullable Tank tank() {
        return level() instanceof ServerLevel && record() instanceof CarRecords.Contents record ? record.tank() : null;
    }

    /** What its tank holds, as the server last synced it: a copy, empty for none. */
    public FluidStack tankSeen() {
        return entityData.get(DATA_TANK).copy();
    }

    /** How many millibuckets its tank holds when full, as the server last synced it. */
    public int tankCapacitySeen() {
        return entityData.get(DATA_TANK_CAPACITY);
    }

    @Override
    protected void syncCar(ServerLevel level, Train train, Car car) {
        Tank tank = tank();
        if (tank == null) {
            return;
        }
        FluidStack held = tank.held();
        // A stack equals only itself, so the same tank set again would sync every tick.
        if (!FluidStack.matches(entityData.get(DATA_TANK), held)) {
            entityData.set(DATA_TANK, held);
        }
        entityData.set(DATA_TANK_CAPACITY, tank.capacity());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_TANK, FluidStack.EMPTY);
        entityData.define(DATA_TANK_CAPACITY, 0);
    }

    /** Nobody rides a Fluid car. */
    @Override
    protected boolean rideable() {
        return false;
    }

    @Override
    protected boolean hasMenu() {
        return false;
    }

    /** It has no menu: the inventory key opens nothing. */
    @Override
    public void openCustomInventoryScreen(Player player) {
    }

    /** A fluid container in hand fills from its tank, or pours into it, a bucket at a time; anything else does nothing. */
    @Override
    protected InteractionResult useHeld(Player player, InteractionHand hand, ItemStack held) {
        if (ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) == null) {
            return InteractionResult.PASS;
        }
        Tank tank = tank();
        if (tank == null) {
            return InteractionResult.SUCCESS;
        }
        return tank.useByHand(player, hand, blockPosition()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected Item getDropItem() {
        return RailworksRegistries.FLUID_CAR.get();
    }

    @Override
    protected int recordSlots() {
        return 0;
    }

    @Override
    protected int recordTank() {
        return tankCapacity();
    }
}
