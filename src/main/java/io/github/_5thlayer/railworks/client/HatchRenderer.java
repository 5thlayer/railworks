// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github._5thlayer.railworks.HatchBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The item a hatch shows ({@link HatchBlockEntity#shown}), a requester's request or a provider's
 * first stack, flat on each of its four sides, as an item frame holds one, each lit as the block in
 * front of that side is. A hatch has no facing, so it reads from any side of the Rail.
 */
final class HatchRenderer implements BlockEntityRenderer<HatchBlockEntity, HatchRenderer.RenderState> {

    /** How far out from a side its middle stands, as an item frame's stands off its wall. */
    private static final float OFF_FACE = 1 / 16F;
    /** Its size, against an item's own: an item frame's. */
    private static final float SCALE = 0.5F;

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(HatchBlockEntity hatch, RenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(hatch, state, partialTicks, cameraPosition, breakProgress);
        state.item = null;
        var level = hatch.getLevel();
        var shown = hatch.shown();
        if (level == null || shown.isEmpty()) {
            return;
        }
        var item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, shown, ItemDisplayContext.FIXED, level, null, 0);
        state.item = item;
        for (int i = 0; i < SIDES.length; i++) {
            state.light[i] = LevelRenderer.getLightCoords(level, hatch.getBlockPos().relative(SIDES[i]));
        }
    }

    /** On each side, turned as an item frame on that side would turn it, so its own front faces out. */
    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (state.item == null) {
            return;
        }
        for (int i = 0; i < SIDES.length; i++) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(180 - SIDES[i].toYRot()));
            poseStack.translate(0, 0, -(0.5F + OFF_FACE));
            poseStack.scale(SCALE, SCALE, SCALE);
            state.item.submit(poseStack, collector, state.light[i], OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    static final class RenderState extends BlockEntityRenderState {
        private @Nullable ItemStackRenderState item;
        private final int[] light = new int[SIDES.length];
    }
}
