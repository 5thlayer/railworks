// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import io.github._5thlayer.railworks.client.ClientRails;
import io.github._5thlayer.railworks.rail.RailNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive in the hand: using it hangs the car from the Span the player's line of sight
 * is on, facing the way they look along it. {@link LocomotivePlacement} is the one rule, so the
 * Placement Preview, drawn through Groundworks, and the click cannot disagree.
 *
 * <p>The Rail is not a block, so the click is {@code use}, which also fires on a click in the air
 * and, when the aimed block's own use passes, on one at a block.
 */
public final class LocomotiveItem extends Item implements PlansPlacement {

    public LocomotiveItem(Item.Properties properties) {
        super(properties);
    }

    private static RailNetwork networkOf(Level level) {
        return level instanceof ServerLevel server ? LevelRails.of(server).network() : ClientRails.network();
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        var result = LocomotivePlacement.plan(context.getLevel(), networkOf(context.getLevel()), context.getPlayer(),
                Placements.aimedPos(context));
        return result == null ? null : result.plan();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var result = LocomotivePlacement.plan(level, networkOf(level), player, player.blockPosition());
        if (result == null || result.locomotive() == null) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            ItemStack stack = player.getItemInHand(hand);
            LocomotiveEntity car = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(server, EntitySpawnReason.SPAWN_ITEM_USE);
            if (car == null) {
                return InteractionResult.FAIL;
            }
            car.setLocomotive(result.locomotive());
            server.addFreshEntity(car);
            server.gameEvent(player, GameEvent.ENTITY_PLACE, car.position());
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
