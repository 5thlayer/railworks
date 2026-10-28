// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Map;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import io.github._5thlayer.railworks.rail.Goods;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A hatch in hand, of items or a Fluid hatch, a provider's or a requester's, which places the hatch as
 * that and which the hatch drops as what it is. Sneak-used in the air, the whole stack turns into the other. The block's own
 * item, the one its recipe makes, is the provider's. It places only beside a car spot or the leading
 * car's place of a Station's platform ({@link PlatformsNear#joined}), so the Placement Preview and the
 * click ask one rule; a refused click says why on the action bar. While one is held, the free blocks
 * beside them on the platforms near are drawn.
 */
public final class HatchItem extends BlockItem implements PlansPlacement {

    private final HatchMode mode;

    public HatchItem(Block block, HatchMode mode, Item.Properties properties) {
        super(block, properties);
        this.mode = mode;
    }

    /** What a hatch it places is. */
    public HatchMode mode() {
        return mode;
    }

    /** The item a hatch of {@code holds} that is {@code mode} is in hand. */
    public static HatchItem of(Goods.Kind holds, HatchMode mode) {
        if (holds == Goods.Kind.FLUID) {
            return mode == HatchMode.REQUESTER ? RailworksRegistries.REQUESTER_FLUID_HATCH_ITEM.get()
                    : RailworksRegistries.FLUID_HATCH_ITEM.get();
        }
        return mode == HatchMode.REQUESTER ? RailworksRegistries.REQUESTER_HATCH_ITEM.get() : RailworksRegistries.HATCH_ITEM.get();
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        PlacementPlan plan = Placements.vanillaPlan(this, context);
        if (plan == null || plan.isRefused() || PlatformsNear.joined(context.getLevel(), context.getClickedPos()).isPresent()) {
            return plan;
        }
        return PlacementPlan.refused(context.getClickedPos(), getBlock().defaultBlockState(), RailworksRefusal.HATCH_OFF_PLATFORM);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (PlatformsNear.joined(context.getLevel(), context.getClickedPos()).isPresent()) {
            return super.place(context);
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.railworks.hatch_off_platform"), true);
        }
        return InteractionResult.FAIL;
    }

    /** Sneak-used in the air, the whole stack turns into the other mode's, heard as a shulker box opening; otherwise it does what any block in hand does. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), HatchBlock.SWITCH_SOUND, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        HatchItem other = of(((HatchBlock) getBlock()).holds(), mode.other());
        return InteractionResult.SUCCESS.heldItemTransformedTo(player.getItemInHand(hand).transmuteCopy(other));
    }

    /** Only the provider's is the block's own item, the one it is picked and named as when nothing says otherwise. */
    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        if (mode == HatchMode.PROVIDER) {
            super.registerBlocks(map, item);
        }
    }
}
