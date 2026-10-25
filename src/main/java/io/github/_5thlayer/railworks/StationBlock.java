// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.network.StationMenuPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A Station: a named block beside the Rail or under it, which a Train stopped by it is at (ADR 0007).
 * Using it opens its menu, where it is named. Breaking it takes it out of the level's Stations.
 */
public final class StationBlock extends Block implements EntityBlock {

    public static final String BLOCK_NAME = "station";

    public StationBlock(BlockBehaviour.Properties props) {
        super(props.strength(2.0F).sound(SoundType.METAL));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof StationBlockEntity station) {
            StationMenuPacket packet = new StationMenuPacket(pos, station.name());
            // Only to a connection that negotiated the channel; sending to one that didn't throws.
            if (server.connection.hasChannel(packet)) {
                PacketDistributor.sendToPlayer(server, packet);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** In the level's Stations at once, under the name an anvil gave the item if it had one. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof StationBlockEntity station) {
            station.register();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
                                               boolean movedByPiston) {
        // 26.1 calls this whenever the block changes: a Station still there is a replacement, not a break.
        if (!(level.getBlockState(pos).getBlock() instanceof StationBlock)) {
            LevelStations.of(level).remove(pos);
        }
    }
}
