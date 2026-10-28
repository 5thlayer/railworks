// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Generator (ADR 0005, amended in #9): a block that burns furnace fuel into FE at the
 * Burner Locomotive's 600 kW and gives it to the energy blocks touching it, so a Charger runs in a
 * world with no other energy mod. It powers only what touches it. Its front faces the player who
 * placed it and shows a fire while it burns; using it opens its menu. Broken, it drops its fuel.
 */
public final class BurnerGeneratorBlock extends Block implements EntityBlock {

    public static final String BLOCK_NAME = "burner_generator";

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BurnerGeneratorBlock(BlockBehaviour.Properties props) {
        super(props.strength(3.5F).sound(SoundType.METAL).lightLevel(state -> state.getValue(LIT) ? 13 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BurnerGeneratorBlockEntity(pos, state);
    }

    /** Ticks on the server only, burning and giving its FE away. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != RailworksRegistries.BURNER_GENERATOR_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((BurnerGeneratorBlockEntity) entity).serverTick((ServerLevel) world);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BurnerGeneratorBlockEntity generator) {
            player.openMenu(generator);
        }
        return InteractionResult.SUCCESS;
    }
}
