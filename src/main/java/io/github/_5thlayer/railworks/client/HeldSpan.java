// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.railworks.LevelClearance;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailPurse;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Span the local player's Rail is holding the first end of, and what a click on the Pylon they are
 * looking at would do with it. The tint is {@link Stringing#tintFor}, the model's own rule, asked of
 * the client's Spans and inventory, so the preview cannot promise a Span the click will not make.
 */
final class HeldSpan {

    /** In first person the slack ends just ahead of and below the eye, where it stays in view. */
    private static final double FIRST_PERSON_REACH = 0.8;
    private static final double FIRST_PERSON_DROP = 0.4;

    /**
     * @param anchor the Arm held
     * @param target the Arm looked at, or null when the aim is at no Pylon
     */
    record Aim(BlockPos anchor, @Nullable Arm target, Stringing.Tint tint) {

        /** Whether this aim cuts the Span between {@code a} and {@code b}, which stops drawing. */
        boolean cuts(Arm a, Arm b) {
            if (tint != Stringing.Tint.CUT || target == null) {
                return false;
            }
            Arm held = PylonColumn.arm(anchor);
            return (a.equals(held) && b.equals(target)) || (a.equals(target) && b.equals(held));
        }

        /** Where the free end is: on the looked-at Arm while the click would do something, else in the hand. */
        Vec3 freeEnd(float partialTicks) {
            if (target != null && tint != Stringing.Tint.HELD) {
                return SpanRenderer.attachPoint(PylonColumn.block(target));
            }
            return handPoint(partialTicks);
        }
    }

    private HeldSpan() {
    }

    static @Nullable Aim current(Level level) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // The click is turned away for a spectator or a player who may not build, so nothing is promised.
        if (player == null || !player.mayBuild() || player.isSpectator()) {
            return null;
        }
        GlobalPos pending = player.getMainHandItem().get(RailworksRegistries.PENDING_SPAN.get());
        if (pending == null || !pending.dimension().equals(level.dimension())
                || !PylonColumn.isArm(level, pending.pos())) {
            return null;
        }
        Arm target = null;
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && PylonColumn.isPylon(level, hit.getBlockPos())) {
            target = PylonColumn.arm(PylonColumn.topOf(level, hit.getBlockPos()));
        }
        Stringing.Tint tint = Stringing.tintFor(ClientRails.network(), LevelClearance.blocks(level), PylonColumn.arm(pending.pos()), target,
                RailPurse.count(player));
        return new Aim(pending.pos(), target, tint);
    }

    private static Vec3 handPoint(float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.options.getCameraType().isFirstPerson()) {
            // The rope hold position is at the body, behind the first-person camera.
            return player.getEyePosition(partialTicks).add(player.getViewVector(partialTicks).scale(FIRST_PERSON_REACH))
                    .add(0.0, -FIRST_PERSON_DROP, 0.0);
        }
        return player.getRopeHoldPosition(partialTicks);
    }
}
