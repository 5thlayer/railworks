// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One segment of a Pylon column, as tall as the player stacks it with no cap below the build limit.
 * Each segment costs one Pylon, and the Arm is always on the top segment.
 *
 * <p>Using a Pylon on any segment stacks one on top, so a player on the ground raises a column past
 * their reach. Breaking a segment breaks and drops every segment above it, and the Spans on the
 * column's Arm go with it, their Rail dropped ({@link LevelRails#armLost}).
 */
public final class PylonBlock extends Block implements EntityBlock {

    public static final String BLOCK_NAME = "pylon";

    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    public PylonBlock(BlockBehaviour.Properties props) {
        super(props.strength(2.0F).sound(SoundType.METAL).noOcclusion());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PylonBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof PylonItem item)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // The rule is asked for, not restated: this executes the plan the Placement Preview draws.
        PlacementPlan plan = Placements.planFor(item, new BlockPlaceContext(level, player, hand, stack, hit));
        if (plan != null && plan.refusal() == RailworksRefusal.RAISES_SPAN) {
            PylonItem.tellWhy(level, player, plan.blocks().getFirst().pos());
        }
        if (plan == null || plan.isRefused() || plan.blocks().size() != 1 || !player.mayBuild()) {
            return InteractionResult.CONSUME;
        }
        PlacementPlan.Placed segment = plan.blocks().getFirst();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        level.setBlockAndUpdate(segment.pos(), segment.state());
        level.playSound(null, segment.pos(), getSoundType(state, level, segment.pos(), player).getPlaceSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !(oldState.getBlock() instanceof PylonBlock)) {
            LevelRails.of(server).placed(server, pos);
        }
    }

    /** A Pylon placed by a player starts a column that strings itself ({@link LevelRails#autoString}). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer instanceof Player player) {
            LevelRails.of(server).autoString(server, pos, player);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
                                               boolean movedByPiston) {
        // 26.1 calls this whenever the block changes: a Pylon still there is a replacement, not a break.
        if (level.getBlockState(pos).getBlock() instanceof PylonBlock) {
            return;
        }
        BlockPos above = pos.above();
        boolean segmentsAbove = PylonColumn.isPylon(level, above);
        // The Arm the column lost: its top segment, which is this one when nothing stood above.
        BlockPos top = segmentsAbove ? PylonColumn.topOf(level, above) : pos;
        LevelRails.of(level).armLost(level, top);
        if (segmentsAbove) {
            // With loot, one Pylon per segment: that is the refund, each having cost one.
            level.destroyBlock(above, true);
        }
    }
}
