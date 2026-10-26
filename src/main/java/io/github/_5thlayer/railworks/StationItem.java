// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import io.github._5thlayer.railworks.client.ClientRails;
import io.github._5thlayer.railworks.rail.RailNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A Station in the hand: placed as a block is, but only by the Rail, by a Straight leg and out of
 * its Clearance ({@link StationPlacement}), so the Placement Preview and the click ask one rule. A refused click
 * tells the player why on the action bar.
 */
public final class StationItem extends BlockItem implements PlansPlacement {

    public StationItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    private static RailNetwork networkOf(Level level) {
        return level instanceof ServerLevel server ? LevelRails.of(server).network() : ClientRails.network();
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        var refusal = StationPlacement.refusal(networkOf(context.getLevel()), context.getClickedPos());
        if (refusal.isPresent()) {
            return PlacementPlan.refused(context.getClickedPos(), getBlock().defaultBlockState(), refusal.get());
        }
        return Placements.vanillaPlan(this, context);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var refusal = StationPlacement.refusal(networkOf(context.getLevel()), context.getClickedPos());
        if (refusal.isEmpty()) {
            return super.place(context);
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(message(refusal.get()), true);
        }
        return InteractionResult.FAIL;
    }

    private static Component message(RailworksRefusal refusal) {
        return Component.translatable(messageKey(refusal));
    }

    /** The action-bar line for why a Station was refused. */
    public static String messageKey(RailworksRefusal refusal) {
        return switch (refusal) {
            case STATION_OFF_RAIL -> "message.railworks.station_off_rail";
            case STATION_IN_CLEARANCE -> "message.railworks.station_in_clearance";
            case STATION_NOT_STRAIGHT -> "message.railworks.station_not_straight";
            default -> throw new IllegalArgumentException(refusal + " does not refuse a Station");
        };
    }
}
