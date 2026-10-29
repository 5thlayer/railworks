// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import io.github._5thlayer.railworks.rail.Goods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * A hatch's block entity, which holds nothing of its own: what the hatch holds is its record in the
 * level's {@link LevelHatches}, saved with the level, so a Train loads and unloads it with no chunk
 * loaded. A hatch of items' item handler is that record's slots, and a Fluid hatch's fluid handler its
 * tank, the same on every side; neither has the other. A hatch placed with no player, as by a command,
 * puts itself there, holding nothing, as it loads, as its block's kind and mode say.
 *
 * <p>Its watchers draw one item ({@link #shown}) on a hatch of items' sides or a Fluid hatch's top: a
 * requester's request, a fluid drawn as its filled bucket, or the first stack a provider of items
 * holds. A Fluid hatch's watchers also see its tank, the fluid, its amount and the tank's capacity,
 * in the Sight glass on each side ({@link #tankSeen}). The server works both out from the record
 * each tick and sends them to the watchers as they change; the record stays the one truth.
 */
public final class HatchBlockEntity extends BlockEntity {

    /**
     * A watcher's item handler, with no slots: it knows nothing of what the hatch holds, but a mod
     * there that looks for one before asking the server what a block holds, as Jade does, finds one.
     */
    private static final ResourceHandler<ItemResource> EMPTY = new ItemStacksResourceHandler(0);
    /** A watcher's fluid handler, with no tank, for the same reason. */
    private static final ResourceHandler<FluidResource> NO_TANK = new FluidStacksResourceHandler(0, 0);

    private static final String SHOWN_KEY = "shown";
    private static final String TANK_KEY = "tank";
    private static final String TANK_CAPACITY_KEY = "tank_capacity";

    private ItemStack shown = ItemStack.EMPTY;
    private FluidStack tankSeen = FluidStack.EMPTY;
    private int tankCapacitySeen;

    public HatchBlockEntity(BlockPos pos, BlockState state) {
        super(RailworksRegistries.HATCH_ENTITY.get(), pos, state);
    }

    /** Its record in the level's {@link LevelHatches}, put there if it has none; empty for a watcher, which has none. */
    public Optional<Hatches.Hatch> hatch() {
        if (!(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        Hatches hatches = LevelHatches.of(server);
        return Optional.of(hatches.at(worldPosition)
                .orElseGet(() -> hatches.put(worldPosition, holds(), getBlockState().getValue(HatchBlock.MODE))));
    }

    /** Whether its block is a hatch of items or a Fluid hatch. */
    private Goods.Kind holds() {
        return getBlockState().getBlock() instanceof HatchBlock block ? block.holds() : Goods.Kind.ITEMS;
    }

    /**
     * What a hatch of items holds, on every side: hoppers and pipes fill and empty it. For a watcher, a
     * handler holding nothing; none for a Fluid hatch.
     */
    public @Nullable ResourceHandler<ItemResource> items() {
        if (holds() != Goods.Kind.ITEMS) {
            return null;
        }
        return hatch().<ResourceHandler<ItemResource>>map(Hatches.Hatch::contents).orElse(EMPTY);
    }

    /**
     * What a Fluid hatch holds, on every side: any mod's pipes fill and empty its tank. For a watcher, a
     * handler with no tank; none for a hatch of items.
     */
    public @Nullable ResourceHandler<FluidResource> fluids() {
        if (holds() != Goods.Kind.FLUID) {
            return null;
        }
        return hatch().<ResourceHandler<FluidResource>>map(Hatches.Hatch::tank).orElse(NO_TANK);
    }

    /**
     * The item drawn on a hatch of items' sides or a requester Fluid hatch's top, one of it: a
     * requester's request, or the first stack a provider of items holds; empty for a requester asking
     * for nothing, a provider holding nothing, and a provider Fluid hatch, whose Sight glasses show
     * its fluid.
     */
    public ItemStack shown() {
        return shown;
    }

    /** What a Fluid hatch's tank holds, as the server last sent it to its watchers: a copy, empty for none. */
    public FluidStack tankSeen() {
        return tankSeen.copy();
    }

    /** How many millibuckets a Fluid hatch's tank holds when full, as the server last sent it; 0 for a hatch of items. */
    public int tankCapacitySeen() {
        return tankCapacitySeen;
    }

    /**
     * Sends its watchers the item drawn on its sides and its tank when either changes. Worked out from
     * its record each tick, they follow whatever changed the record: a hopper, a pipe, a Train, a
     * player or a command, and a change made while its chunk was unloaded once the chunk loads again.
     */
    void serverTick(ServerLevel level) {
        var hatch = hatch();
        ItemResource now = hatch.map(Hatches.Hatch::shown).orElse(ItemResource.EMPTY);
        FluidStack tank = hatch.map(record -> record.tank().held()).orElse(FluidStack.EMPTY);
        int capacity = hatch.map(record -> record.tank().capacity()).orElse(0);
        // A stack equals only itself, so they are matched, not compared.
        if (!now.matches(shown) || !FluidStack.matches(tank, tankSeen) || capacity != tankCapacitySeen) {
            shown = now.toStack();
            tankSeen = tank;
            tankCapacitySeen = capacity;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Only what its watchers draw, the item on its sides and its tank, never saved, which it works out again each tick. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        if (!shown.isEmpty()) {
            output.store(SHOWN_KEY, ItemStack.CODEC, shown);
        }
        if (!tankSeen.isEmpty()) {
            output.store(TANK_KEY, FluidStack.CODEC, tankSeen);
        }
        if (tankCapacitySeen > 0) {
            output.putInt(TANK_CAPACITY_KEY, tankCapacitySeen);
        }
        return output.buildResult();
    }

    /** Sent as {@link #shown} or the tank changes, carrying {@link #getUpdateTag}. */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as the chunk arrives. */
    @Override
    public void handleUpdateTag(ValueInput input) {
        readSeen(input);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as it changes. */
    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readSeen(input);
    }

    private void readSeen(ValueInput input) {
        shown = input.read(SHOWN_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        tankSeen = input.read(TANK_KEY, FluidStack.CODEC).orElse(FluidStack.EMPTY);
        tankCapacitySeen = input.getIntOr(TANK_CAPACITY_KEY, 0);
    }

    /**
     * Puts itself in the level's hatches as it loads, if it is not there yet, as after a command placed
     * it. A record there of the other mode or kind is a stale one, left by a hatch a command removed with
     * no side effects: what it held is dropped, and a fresh record of this block's takes its place.
     * One of the same mode cannot be told from its own, and this block takes it over as it is.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Hatches hatches = LevelHatches.of(server);
            HatchMode mode = getBlockState().getValue(HatchBlock.MODE);
            hatches.at(worldPosition).filter(stale -> stale.mode() != mode || stale.holds() != holds()).ifPresent(stale -> {
                hatches.remove(worldPosition);
                stale.drop(server);
            });
        }
        hatch();
    }

    /** Broken, it leaves the level's hatches and drops what it held. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            LevelHatches.of(server).remove(pos).ifPresent(hatch -> hatch.drop(server));
        }
    }
}
