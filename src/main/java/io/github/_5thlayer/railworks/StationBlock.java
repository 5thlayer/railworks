// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import io.github._5thlayer.railworks.network.StationMenuPacket;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A Station: a named block beside the Rail, which a Train arriving its serving way stops at (ADRs
 * 0007, 0008). Its {@link #FACING} is that way, which the arrow on its top shows: set as it is
 * placed and looked at again as it loads and every couple of seconds after, from the Rail by it and
 * the drive side. Using it opens its menu,
 * where it is named. Breaking it takes it out of the level's Stations.
 */
public final class StationBlock extends Block implements EntityBlock {

    public static final String BLOCK_NAME = "station";
    /** The way the Trains it serves head, for the arrow on its top. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StationBlock(BlockBehaviour.Properties props) {
        super(props.strength(2.0F).sound(SoundType.METAL));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return serving(PlatformsNear.network(context.getLevel()), pos)
                .map(way -> defaultBlockState().setValue(FACING, way))
                .orElse(defaultBlockState());
    }

    /**
     * The way a Station at {@code pos} serves, as the way the Trains it serves head: empty by no Rail
     * or under it.
     */
    public static Optional<Direction> serving(RailNetwork network, BlockPos pos) {
        Station station = new Station(pos.getX(), pos.getY(), pos.getZ(), Station.DEFAULT_NAME);
        return station.foot(network).flatMap(foot -> station.servingEnd(network).map(end -> {
            Arm from = foot.span().otherEnd(end);
            int dx = end.x() - from.x();
            int dz = end.z() - from.z();
            if (Math.abs(dx) >= Math.abs(dz)) {
                return dx > 0 ? Direction.EAST : Direction.WEST;
            }
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }));
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

    /** How often a loaded Station looks again at the way it serves, in ticks. */
    private static final int FACE_TICKS = 40;

    /**
     * A tick after its block entity loads, and every {@link #FACE_TICKS} after: turns its arrow to the
     * way it serves now.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof StationBlockEntity station) {
            station.face();
            level.scheduleTick(pos, this, FACE_TICKS);
        }
    }

    /** In the level's Stations at once, rather than a tick later as its block entity loads. */
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
