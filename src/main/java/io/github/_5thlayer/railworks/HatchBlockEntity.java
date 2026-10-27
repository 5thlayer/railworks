// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * A hatch's block entity, which holds nothing of its own: what the hatch holds is its record in the
 * level's {@link LevelHatches}, saved with the level, so a Train loads and unloads it with no chunk
 * loaded. Its item handler is that record's slots, the same on every side. A hatch placed with no
 * player, as by a command, puts itself there, holding nothing, as it loads, as its block's mode says.
 *
 * <p>Its watchers draw one item on its sides ({@link #shown}): a requester's request, or the first
 * stack a provider holds, which the server works out each tick and sends them as it changes.
 */
public final class HatchBlockEntity extends BlockEntity {

    /**
     * A watcher's item handler, with no slots: it knows nothing of what the hatch holds, but a mod
     * there that looks for one before asking the server what a block holds, as Jade does, finds one.
     */
    private static final ResourceHandler<ItemResource> EMPTY = new ItemStacksResourceHandler(0);

    private static final String SHOWN_KEY = "shown";

    private ItemStack shown = ItemStack.EMPTY;

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
                .orElseGet(() -> hatches.put(worldPosition, getBlockState().getValue(HatchBlock.MODE))));
    }

    /** What it holds, on every side: hoppers and pipes fill and empty it. For a watcher, a handler holding nothing. */
    public ResourceHandler<ItemResource> items() {
        return hatch().<ResourceHandler<ItemResource>>map(Hatches.Hatch::contents).orElse(EMPTY);
    }

    /**
     * The item drawn on its sides, one of it: a requester's request, or the first stack a provider
     * holds; empty for a requester asking for nothing and a provider holding nothing.
     */
    public ItemStack shown() {
        return shown;
    }

    /**
     * Sends its watchers the item drawn on its sides when that changes. Worked out from its record
     * each tick, it follows whatever changed the record: a hopper, a Train, a player or a command, and
     * a change made while its chunk was unloaded once the chunk loads again.
     */
    void serverTick(ServerLevel level) {
        ItemStack now = hatch().map(HatchBlockEntity::toShow).orElse(ItemStack.EMPTY);
        if (!ItemStack.matches(now, shown)) {
            shown = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static ItemStack toShow(Hatches.Hatch hatch) {
        if (hatch.mode() == HatchMode.REQUESTER) {
            return hatch.request().map(asked -> new ItemStack(asked.item())).orElse(ItemStack.EMPTY);
        }
        var contents = hatch.contents();
        for (int slot = 0; slot < contents.size(); slot++) {
            if (!contents.getResource(slot).isEmpty()) {
                return contents.getResource(slot).toStack(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Only what its watchers draw, the item on its sides, never saved, which it works out again each tick. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        if (!shown.isEmpty()) {
            output.store(SHOWN_KEY, ItemStack.CODEC, shown);
        }
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as the chunk arrives. */
    @Override
    public void handleUpdateTag(ValueInput input) {
        readShown(input);
    }

    /** A watcher reads only what {@link #getUpdateTag} sends, as it changes. */
    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readShown(input);
    }

    private void readShown(ValueInput input) {
        shown = input.read(SHOWN_KEY, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    /**
     * Puts itself in the level's hatches as it loads, if it is not there yet, as after a command placed
     * it. A record there of the other mode is a stale one, left by a hatch a command removed with no
     * side effects: what it held is dropped, and a fresh record of this block's mode takes its place.
     * One of the same mode cannot be told from its own, and this block takes it over as it is.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            Hatches hatches = LevelHatches.of(server);
            HatchMode mode = getBlockState().getValue(HatchBlock.MODE);
            hatches.at(worldPosition).filter(stale -> stale.mode() != mode).ifPresent(stale -> {
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
