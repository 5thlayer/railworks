// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import net.minecraft.core.BlockPos;
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
            return Placements.vanillaPlan(this, context);
        }
        BlockPos next = PylonColumn.topOf(level, aimed).above();
        BlockState segment = getBlock().defaultBlockState();
        if (level.isOutsideBuildHeight(next) || !level.getBlockState(next).canBeReplaced()) {
            return PlacementPlan.refused(next, segment, RailworksRefusal.BLOCKED_TOP);
        }
        return PlacementPlan.accepted(next, segment);
    }
}
