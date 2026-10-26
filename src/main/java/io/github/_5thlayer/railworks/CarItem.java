// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import io.github._5thlayer.railworks.client.ClientRails;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * A car in the hand, a Burner Locomotive or a Cargo car: using it sets the car on the Span the
 * player's line of sight is on, facing the way they look along it, or couples it to the end of a
 * Train at rest it is aimed beside. {@link CarPlacement} is the one rule, so the Placement Preview,
 * drawn through Groundworks, and the click cannot disagree.
 *
 * <p>The Rail is not a block, so the click is {@code use}, which also fires on a click in the air
 * and, when the aimed block's own use passes, on one at a block.
 */
public final class CarItem extends Item implements PlansPlacement {

    /** An iron block's place sound pitched down, as vanilla's {@code BlockItem} pitches a block's, for a heavy thing set on the Rail. */
    private static final float PLACE_PITCH = 0.8F;

    private final CarKind kind;

    public CarItem(CarKind kind, Item.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    private static RailNetwork networkOf(Level level) {
        return level instanceof ServerLevel server ? LevelRails.of(server).network() : ClientRails.network();
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        var result = CarPlacement.plan(context.getLevel(), networkOf(context.getLevel()), context.getPlayer(),
                Placements.aimedPos(context), kind);
        return result == null ? null : result.plan();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var result = CarPlacement.plan(level, networkOf(level), player, player.blockPosition(), kind);
        if (result == null || result.car() == null || (result.train() == null && result.slot() == null)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            ItemStack stack = player.getItemInHand(hand);
            CarEntity car = kind.entityType().create(server, EntitySpawnReason.SPAWN_ITEM_USE);
            if (car == null) {
                return InteractionResult.FAIL;
            }
            LevelRails rails = LevelRails.of(server);
            Train train = result.train();
            if (train != null) {
                rails.add(train);
            } else {
                train = rails.couple(result.slot(), result.car(), result.reversed());
                if (train == null) {
                    return InteractionResult.FAIL;
                }
            }
            car.setCar(train, result.car());
            server.addFreshEntity(car);
            server.gameEvent(player, GameEvent.ENTITY_PLACE, car.position());
            // To everyone, the placer too: the client predicts no car, so it plays no sound of its own.
            server.playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.METAL_PLACE, SoundSource.BLOCKS,
                    1.0F, PLACE_PITCH);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
