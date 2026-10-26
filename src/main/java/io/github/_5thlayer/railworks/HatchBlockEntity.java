// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A hatch's block entity, which holds nothing of its own: what the hatch holds is its record in the
 * level's {@link LevelHatches}, saved with the level, so a Train loads and unloads it with no chunk
 * loaded. Its item handler is that record's slots, the same on every side. A hatch placed with no
 * player, as by a command, puts itself there, holding nothing, as it loads, as its block's mode says.
 */
public final class HatchBlockEntity extends BlockEntity {

    /**
     * A watcher's item handler, with no slots: it knows nothing of what the hatch holds, but a mod
     * there that looks for one before asking the server what a block holds, as Jade does, finds one.
     */
    private static final ResourceHandler<ItemResource> EMPTY = new ItemStacksResourceHandler(0);

    public HatchBlockEntity(BlockPos pos, BlockState state) {
        super(RailworksRegistries.HATCH_ENTITY.get(), pos, state);
    }

    /** Its record in the level's {@link LevelHatches}, put there if it has none; empty for a watcher, which has none. */
    public Optional<LevelHatches.Hatch> hatch() {
        if (!(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        LevelHatches hatches = LevelHatches.of(server);
        return Optional.of(hatches.at(worldPosition)
                .orElseGet(() -> hatches.put(worldPosition, getBlockState().getValue(HatchBlock.MODE))));
    }

    /** What it holds, on every side: hoppers and pipes fill and empty it. For a watcher, a handler holding nothing. */
    public ResourceHandler<ItemResource> items() {
        return hatch().<ResourceHandler<ItemResource>>map(LevelHatches.Hatch::contents).orElse(EMPTY);
    }

    /** Puts itself in the level's hatches as it loads, if it is not there yet, as after a command placed it. */
    @Override
    public void onLoad() {
        super.onLoad();
        hatch();
    }

    /** Broken, it leaves the level's hatches and drops what it held. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            LevelHatches.of(server).remove(pos).ifPresent(hatch -> LevelHatches.drop(server, hatch));
        }
    }
}
