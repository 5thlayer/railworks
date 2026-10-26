// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Station block's name, saved with its chunk and sent to the clients watching it, which is what
 * its menu and Jade show. The level's {@link LevelStations} follows it.
 */
public final class StationBlockEntity extends BlockEntity {

    private String name = Station.DEFAULT_NAME;

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(RailworksRegistries.STATION_ENTITY.get(), pos, state);
    }

    public String name() {
        return name;
    }

    /** The model's view of this Station, for asking which Span it serves. */
    public Station station() {
        return new Station(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), name);
    }

    /**
     * {@code player} typed {@code typed} in the menu: the Station takes it as {@link Station#name}
     * cleans it, if they are in reach of it and anything is left. Returns whether it was taken.
     */
    public boolean rename(Player player, String typed) {
        if (isRemoved() || !player.isWithinBlockInteractionRange(worldPosition, 1.0) || !player.mayBuild()) {
            return false;
        }
        var cleaned = Station.name(typed);
        if (cleaned.isEmpty()) {
            return false;
        }
        name = cleaned.get();
        setChanged();
        register();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** How many hatches joined it, asked afresh of the level's hatches, on a server; 0 on a client. */
    public int hatches() {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        return PlatformsNear.joinedTo(server, station()).size();
    }

    /** Puts itself in the level's Stations under its name, on a server. */
    void register() {
        if (level instanceof ServerLevel server) {
            LevelStations.of(server).put(worldPosition, name);
        }
    }

    /**
     * Registers itself as its chunk loads, which heals a copy that drifted from the chunk. NeoForge
     * calls this a tick after a block is placed, so {@link StationBlock#setPlacedBy} registers it too.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        register();
        if (level instanceof ServerLevel server) {
            // Not while its chunk loads: a tick on, the block turns its arrow if it must, and again every so often.
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    /**
     * Turns the block's arrow to the way it serves now, on a server: a Span strung or cut since, or
     * the drive side changed, may have turned it. Jade asks afresh each time, so it is never behind.
     */
    void face() {
        if (level instanceof ServerLevel server && getBlockState().hasProperty(StationBlock.FACING)) {
            StationBlock.serving(LevelRails.of(server).network(), worldPosition)
                    .filter(way -> getBlockState().getValue(StationBlock.FACING) != way)
                    .ifPresent(way -> server.setBlock(worldPosition,
                            getBlockState().setValue(StationBlock.FACING, way), Block.UPDATE_CLIENTS));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("name", name);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        name = input.getStringOr("name", Station.DEFAULT_NAME);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
