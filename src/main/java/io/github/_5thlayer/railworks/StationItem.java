// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A Station in the hand: placed as a block is, but only beside the Rail, by a Straight leg, out of
 * its Clearance and clear of other Stations' platforms ({@link StationPlacement}), so the Placement
 * Preview and the click ask one rule. A refused click tells the player why on the action bar, naming
 * the other Station for an overlap. The server asks the level's Stations; the client, which has
 * only the blocks it was sent, the Station blocks loaded near the one it would place.
 */
public final class StationItem extends BlockItem implements PlansPlacement {

    public StationItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    private static Optional<StationPlacement.Refused> refusal(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return StationPlacement.refusal(PlatformsNear.network(level), PlatformsNear.stations(level, pos), pos);
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        var refusal = refusal(context);
        if (refusal.isPresent()) {
            return PlacementPlan.refused(context.getClickedPos(), getBlock().defaultBlockState(), refusal.get().reason());
        }
        return Placements.vanillaPlan(this, context);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var refusal = refusal(context);
        if (refusal.isEmpty()) {
            return super.place(context);
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(message(refusal.get()), true);
        }
        return InteractionResult.FAIL;
    }

    /** The action-bar line for a refused Station, naming the other Station for an overlap. */
    public static Component message(StationPlacement.Refused refused) {
        return refused.other()
                .map(other -> Component.translatable(messageKey(refused.reason()), other.name()))
                .orElseGet(() -> Component.translatable(messageKey(refused.reason())));
    }

    /** The action-bar line for why a Station was refused. */
    public static String messageKey(RailworksRefusal refusal) {
        return switch (refusal) {
            case STATION_OFF_RAIL -> "message.railworks.station_off_rail";
            case STATION_UNDER_RAIL -> "message.railworks.station_under_rail";
            case STATION_OVERLAPS -> "message.railworks.station_overlaps";
            case STATION_IN_CLEARANCE -> "message.railworks.station_in_clearance";
            case STATION_NOT_STRAIGHT -> "message.railworks.station_not_straight";
            default -> throw new IllegalArgumentException(refusal + " does not refuse a Station");
        };
    }
}
