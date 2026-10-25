// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.PendingEnd;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Stringing and cutting Spans by hand with Rail.
 *
 * <p>The first use on a Pylon holds its Arm on the Rail; the second is {@link LevelRails#click}.
 * Feedback is a sound per outcome, particles along a Span strung or cut, and an action-bar line for
 * a refusal. The rules are {@link Stringing} and {@link PendingEnd}; this is where they meet a click.
 */
public final class SpanGesture {

    private static final double PARTICLES_PER_BLOCK = 2.0;

    private SpanGesture() {
    }

    /** A main-hand right-click with Rail on a Pylon; any other click is left alone. */
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().is(RailworksRegistries.RAIL)) {
            return;
        }
        Player player = event.getEntity();
        if (!player.mayBuild() || player.isSpectator()) {
            return;
        }
        Level level = event.getLevel();
        if (!PylonColumn.isPylon(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(level instanceof ServerLevel server
                ? click(server, event.getItemStack(), event.getPos(), player)
                : InteractionResult.SUCCESS);
    }

    private static InteractionResult click(ServerLevel server, ItemStack rail, BlockPos clicked, Player player) {
        BlockPos arm = PylonColumn.topOf(server, clicked);
        GlobalPos pending = rail.get(RailworksRegistries.PENDING_SPAN.get());
        if (pending == null || !pending.dimension().equals(server.dimension())) {
            rail.set(RailworksRegistries.PENDING_SPAN.get(), GlobalPos.of(server.dimension(), arm));
            return InteractionResult.SUCCESS_SERVER;
        }
        // Creative players pay and are refunded like anyone: a free Span cut for Rail would be a duplicator.
        Stringing.Outcome outcome = LevelRails.of(server).click(server, pending.pos(), arm, count(player));
        switch (outcome.result()) {
            case STRUNG -> {
                rail.remove(RailworksRegistries.PENDING_SPAN.get());
                spend(player, outcome.rail());
                play(server, arm, SoundEvents.CHAIN_PLACE);
                along(server, pending.pos(), arm, ParticleTypes.ELECTRIC_SPARK);
            }
            case CUT -> {
                rail.remove(RailworksRegistries.PENDING_SPAN.get());
                refund(player, outcome.rail());
                play(server, arm, SoundEvents.CHAIN_BREAK);
                along(server, pending.pos(), arm, ParticleTypes.SMOKE);
            }
            case CANCELLED -> rail.remove(RailworksRegistries.PENDING_SPAN.get());
            // A refusal changes nothing, the held end included.
            case TOO_LONG -> refused(server, player,
                    Component.translatable("message.railworks.too_long", SpanRules.LENGTH_LIMIT));
            case ARM_FULL -> refused(server, player,
                    Component.translatable("message.railworks.arm_full", SpanRules.SPANS_PER_ARM));
            case SHORT_OF_RAIL -> refused(server, player, Component.translatable("message.railworks.short_of_rail",
                    SpanRules.cost(PylonColumn.arm(pending.pos()), PylonColumn.arm(arm))));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static int count(Player player) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(RailworksRegistries.RAIL)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void spend(Player player, int amount) {
        Inventory inventory = player.getInventory();
        int left = amount;
        for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(RailworksRegistries.RAIL)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    /** Into the inventory, and at the player's feet for what does not fit. */
    private static void refund(Player player, int amount) {
        int left = amount;
        while (left > 0) {
            int part = Math.min(left, RailworksRegistries.RAIL.get().getDefaultMaxStackSize());
            player.getInventory().placeItemBackInInventory(new ItemStack(RailworksRegistries.RAIL.get(), part));
            left -= part;
        }
    }

    private static void refused(ServerLevel server, Player player, Component reason) {
        Vec3 at = player.position();
        server.playSound(null, at.x, at.y, at.z, SoundEvents.CRAFTER_FAIL, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(reason, true);
        }
    }

    /** Lets go of every held end the moment {@link PendingEnd} says it is no longer held. */
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getEntity();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.has(RailworksRegistries.PENDING_SPAN.get())) {
                release(stack, level, player, stack == player.getMainHandItem());
            }
        }
    }

    private static void release(ItemStack rail, ServerLevel level, Player player, boolean mainHand) {
        GlobalPos pending = rail.get(RailworksRegistries.PENDING_SPAN.get());
        if (pending == null) {
            return;
        }
        boolean sameDimension = pending.dimension().equals(level.dimension());
        BlockPos anchor = pending.pos();
        boolean standing = sameDimension && level.isLoaded(anchor) && PylonColumn.isArm(level, anchor);
        PendingEnd.Holder held = new PendingEnd.Holder(player.getX(), player.getY(), player.getZ(),
                player.blockInteractionRange(), mainHand, sameDimension);
        if (!PendingEnd.stillHeld(PylonColumn.arm(anchor), standing, held)) {
            rail.remove(RailworksRegistries.PENDING_SPAN.get());
            level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static void play(ServerLevel level, BlockPos at, SoundEvent sound) {
        level.playSound(null, at, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void along(ServerLevel level, BlockPos from, BlockPos to, ParticleOptions particle) {
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 end = Vec3.atCenterOf(to);
        int count = Math.max(2, (int) Math.ceil(start.distanceTo(end) * PARTICLES_PER_BLOCK));
        for (int i = 0; i <= count; i++) {
            Vec3 p = start.lerp(end, (double) i / count);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
