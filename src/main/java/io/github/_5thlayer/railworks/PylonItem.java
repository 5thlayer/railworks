// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A Pylon in the hand: ordinary placement, except where the aim lands on a Pylon, where the column
 * rule takes over and the new segment goes on top of the column.
 *
 * <p>{@link PylonBlock#useItemOn} executes this plan, so the Placement Preview and the click ask one
 * rule. A sneak places beside, since a sneaking player's click never reaches the block's
 * {@code useItemOn}.
 */
public final class PylonItem extends BlockItem implements PlansPlacement {

    public PylonItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos aimed = Placements.aimedPos(context);
        if (!PylonColumn.isPylon(level, aimed) || context.isSecondaryUseActive()) {
            PlacementPlan plan = Placements.vanillaPlan(this, context);
            if (plan == null || plan.isRefused() || plan.blocks().size() != 1) {
                return plan;
            }
            PlacementPlan.Placed placed = plan.blocks().getFirst();
            return raisesPastARule(level, placed.pos())
                    ? PlacementPlan.refused(placed.pos(), placed.state(), RailworksRefusal.RAISES_SPAN) : plan;
        }
        BlockPos next = PylonColumn.topOf(level, aimed).above();
        BlockState segment = getBlock().defaultBlockState();
        if (level.isOutsideBuildHeight(next) || !level.getBlockState(next).canBeReplaced()) {
            return PlacementPlan.refused(next, segment, RailworksRefusal.BLOCKED_TOP);
        }
        if (raisesPastARule(level, next)) {
            return PlacementPlan.refused(next, segment, RailworksRefusal.RAISES_SPAN);
        }
        return PlacementPlan.accepted(next, segment);
    }

    /** A sneaking player's segment on top of a column places as vanilla's does, and is refused as the plan is. */
    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (!super.canPlace(context, state)) {
            return false;
        }
        if (raisesPastARule(context.getLevel(), context.getClickedPos())) {
            tellWhy(context.getLevel(), context.getPlayer(), context.getClickedPos());
            return false;
        }
        return true;
    }

    /** Tells {@code player}, on the server, which rule a segment at {@code pos} would break, on the action bar. */
    static void tellWhy(Level level, @Nullable Player player, BlockPos pos) {
        if (player instanceof ServerPlayer serverPlayer) {
            LevelClearance.stacking(level, pos).ifPresent(reason -> serverPlayer.sendSystemMessage(reason, true));
        }
    }

    /**
     * Whether a segment at {@code pos} would go on top of a column's Arm, raising it, and break a rule
     * there ({@link LevelClearance#stacking}). A segment joining two columns cuts the lower one's
     * Spans instead, as breaking its Arm does, and is no raise.
     */
    private static boolean raisesPastARule(Level level, BlockPos pos) {
        return PylonColumn.raisesArm(level, pos) && LevelClearance.stacking(level, pos).isPresent();
    }
}
