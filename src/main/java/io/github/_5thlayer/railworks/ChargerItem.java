// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A Charger, or a Creative Charger, in hand. It places where a hatch does, beside a car spot or the
 * leading car's place of a Station's platform ({@link PlatformsNear#joined}), so the Placement
 * Preview and the click ask one rule; a refused click says why on the action bar. While one is held,
 * the free blocks beside them on the platforms near are drawn, as for a hatch.
 */
public final class ChargerItem extends BlockItem implements PlansPlacement {

    public ChargerItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        PlacementPlan plan = Placements.vanillaPlan(this, context);
        if (plan == null || plan.isRefused() || PlatformsNear.joined(context.getLevel(), context.getClickedPos()).isPresent()) {
            return plan;
        }
        return PlacementPlan.refused(context.getClickedPos(), getBlock().defaultBlockState(), RailworksRefusal.CHARGER_OFF_PLATFORM);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (PlatformsNear.joined(context.getLevel(), context.getClickedPos()).isPresent()) {
            return super.place(context);
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.railworks.charger_off_platform"), true);
        }
        return InteractionResult.FAIL;
    }
}
