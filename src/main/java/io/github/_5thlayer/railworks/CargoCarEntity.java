// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.List;
import java.util.stream.IntStream;

import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.HopperSpots;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.StraightLeg;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jspecify.annotations.Nullable;

/**
 * A Cargo car in the world: the view of its {@link CargoCar}, holding a chest's worth of items,
 * which its menu, vanilla's three-row chest, shows when the car is used. It is a {@link WorldlyContainer},
 * as a chest boat is a {@link Container}, so hoppers and Jade find it as they find one, but its sides
 * open only while it {@link #loading() loads}: its Train stopped at a Station and the car on a
 * Straight leg ({@link StraightLeg}). It gives mods' feeders and pipes the same cargo through
 * {@link #automation()} on the same rule, and a client sees the rule's bit to draw its
 * {@link #hopperSpots()}. Nobody rides it; coupling and the rest are {@link CarEntity}'s.
 */
public final class CargoCarEntity extends CarEntity implements WorldlyContainer {

    /** A chest's worth, three rows of nine. */
    public static final int SLOTS = 27;

    /** How far from the car the player may stand with its menu open, in blocks. */
    private static final double REACH = 8.0;

    private final SimpleContainer cargo = new SimpleContainer(SLOTS) {
        @Override
        public boolean stillValid(Player player) {
            return isAlive() && player.distanceToSqr(CargoCarEntity.this) < REACH * REACH;
        }
    };

    /** Whether it loads where it stands: at a Station, on a straight leg. The overlay draws by it. */
    private static final EntityDataAccessor<Boolean> DATA_LOADING =
            SynchedEntityData.defineId(CargoCarEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int[] ALL_SLOTS = IntStream.range(0, SLOTS).toArray();
    private static final int[] NO_SLOTS = new int[0];

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
     * Its cargo to hoppers, feeders and pipes, NeoForge's item capability for automation: while its
     * Train is stopped at a Station, wherever along the Train it stands, so long as it stands on a
     * straight leg; null while the Train moves or stands away from every Station, on a bend or a
     * diagonal, or on a client.
     */
    public @Nullable ResourceHandler<ItemResource> automation() {
        return loading() ? handler : null;
    }


    /** Whether it stands wholly on a straight leg square to the blocks, on the server: where it loads. */
    private boolean onStraightLeg() {
        Train train = train();
        return train != null && level() instanceof ServerLevel server
                && StraightLeg.under(LevelRails.of(server).network(), train, train.car(carId()));
    }

    /** The Stations its Train is stopped at, on the server; none on a client or for a car no Train holds. */
    private List<Station> stoppedAt() {
        Train train = train();
        if (train == null || !(level() instanceof ServerLevel server)) {
            return List.of();
        }
        return LevelStations.of(server).stations().stoppedAt(LevelRails.of(server).network(), train);
    }

    /**
     * Whether it loads where it stands, as the server worked it out on its last tick: the one bit the
     * capability, the hoppers' sides and the overlay all go by.
     */
    public boolean loading() {
        return entityData.get(DATA_LOADING);
    }

    /** Where a hopper loads it and where one unloads it, from its box where it stands and its heading. */
    public HopperSpots hopperSpots() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return HopperSpots.of(getX(), getBoundingBox().minY, getZ(), getBbWidth(), getBbHeight(), -Mth.sin(yaw), Mth.cos(yaw));
    }

    /** Nobody rides a Cargo car: using it opens its cargo, with or without sneaking. */
    @Override
    protected boolean rideable() {
        return false;
    }

    /** Sneak-using a hopper at it sets the hopper beside it, pointing in; anything else opens its cargo. */
    @Override
    protected InteractionResult useHeld(Player player, InteractionHand hand, ItemStack held) {
        if (!held.is(Items.HOPPER) || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (level() instanceof ServerLevel) {
            placeHopper(player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Sets a hopper in the filling spot on the player's side, pointing at the car, while its Train is
     * stopped at a Station and the car stands on a Straight leg: the spots are where it stands, so
     * a hopper set while it passes, or where it does not load, would load nothing. It stands in the air, however high the Train is, and is refused where the spot is taken.
     */
    private void placeHopper(Player player, InteractionHand hand) {
        if (stoppedAt().isEmpty()) {
            refuse(player, "message.railworks.hopper_not_at_station");
            return;
        }
        if (!onStraightLeg()) {
            refuse(player, "message.railworks.hopper_not_straight");
            return;
        }
        HopperSpots.Filling filling = hopperSpots().fillingNearest(player.getX(), player.getZ());
        BlockPos pos = new BlockPos(filling.spot().x(), filling.spot().y(), filling.spot().z());
        // A click on a block that cannot be replaced would place the hopper beyond it instead.
        if (!level().getBlockState(pos).canBeReplaced()) {
            refuse(player, "message.railworks.hopper_spot_taken");
            return;
        }
        // Placed as vanilla places a hopper clicked against the spot's face toward the car, which
        // points it at the car: its checks, sound, item data and NeoForge's place event included.
        Direction toward = Direction.getApproximateNearest(filling.towardX(), 0, filling.towardZ());
        BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(pos), toward.getOpposite(), pos, false);
        if (!player.getItemInHand(hand).useOn(new UseOnContext(player, hand, click)).consumesAction()) {
            refuse(player, "message.railworks.hopper_spot_taken");
        }
    }

    private static void refuse(Player player, String message) {
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(Component.translatable(message), true);
        }
    }

    @Override
    protected void syncCar(Car car) {
        entityData.set(DATA_LOADING, !stoppedAt().isEmpty() && onStraightLeg());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_LOADING, false);
    }

    // ---- a Container, open to hoppers only while it loads ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return loading() ? ALL_SLOTS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return loading();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return loading();
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return cargo.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return cargo.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return cargo.removeItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return cargo.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        cargo.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        cargo.setChanged();
    }

    /** As a chest boat's: while it is whole and the player near it. */
    @Override
    public boolean stillValid(Player player) {
        return cargo.stillValid(player);
    }

    @Override
    public void clearContent() {
        cargo.clearContent();
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
