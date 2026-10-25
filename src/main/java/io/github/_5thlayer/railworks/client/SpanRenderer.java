// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * The Rail between Arms, drawn as a straight beam: a square tube from the Arm of this Pylon to the
 * other end of each Span it is the first end of, so exactly one end draws a Span. Only the top
 * segment of a column has an Arm. Placeholder art; the curved Rail is a later ticket.
 */
public final class SpanRenderer implements BlockEntityRenderer<BlockEntity, SpanRenderer.State> {

    /** Where on the top segment the Rail hangs from. */
    private static final double ATTACH_HEIGHT = 0.8;
    private static final float HALF_WIDTH = 0.12F;
    /** Rust red, with the four sides shaded apart. */
    private static final float[] SHADE = {1.0F, 0.75F, 0.55F, 0.85F};
    private static final float R = 0.62F;
    private static final float G = 0.27F;
    private static final float B = 0.18F;

    public static final class State extends BlockEntityRenderState {
        /** Index-aligned: where each Rail ends, relative to this block's origin, and its light. */
        final List<Vec3> ends = new ArrayList<>();
        final List<Integer> lights = new ArrayList<>();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BlockEntity pylon, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pylon, state, partialTicks, cameraPosition, breakProgress);
        state.ends.clear();
        state.lights.clear();
        Level level = pylon.getLevel();
        BlockPos at = pylon.getBlockPos();
        if (level == null || !PylonColumn.isArm(level, at)) {
            return;
        }
        Arm self = PylonColumn.arm(at);
        int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, at), level.getBrightness(LightLayer.SKY, at));
        for (Span span : ClientRails.network().all()) {
            // A Span's first end sorts first by position, so exactly one end draws it.
            if (!span.a().equals(self)) {
                continue;
            }
            BlockPos to = PylonColumn.block(span.b());
            if (!level.isLoaded(to) || !PylonColumn.isArm(level, to)) {
                continue;
            }
            state.ends.add(new Vec3(to.getX() - at.getX() + 0.5, to.getY() - at.getY() + ATTACH_HEIGHT,
                    to.getZ() - at.getZ() + 0.5));
            state.lights.add(light);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.ends.size(); i++) {
            Vec3 end = state.ends.get(i);
            int light = state.lights.get(i);
            collector.submitCustomGeometry(poseStack, RenderTypes.leash(),
                    (pose, buffer) -> drawSpan(pose.pose(), buffer, end, light));
        }
    }

    /**
     * A square tube from this Arm to {@code end}, as one triangle strip around its four sides.
     *
     * <p>The pipeline draws every Span of a frame into one buffer, so consecutive Spans join into one
     * strip, and the triangles between one Span's end and the next one's start would fill the space
     * between them. The strip therefore repeats its first and last vertex twice, as Wireworks'
     * {@code WireStrip} does: every triangle that spans two Spans is zero-area, and the even count
     * keeps the winding.
     */
    private static void drawSpan(Matrix4f pose, VertexConsumer buffer, Vec3 end, int light) {
        Vec3 start = new Vec3(0.5, ATTACH_HEIGHT, 0.5);
        Vec3 along = end.subtract(start).normalize();
        Vec3 up = Math.abs(along.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = along.cross(up).normalize().scale(HALF_WIDTH);
        Vec3 lift = side.cross(along).normalize().scale(HALF_WIDTH);
        Vec3[] corners = {
                side.add(lift), side.subtract(lift), side.reverse().subtract(lift), side.reverse().add(lift)
        };
        vertex(pose, buffer, start.add(corners[0]), SHADE[0], light);
        vertex(pose, buffer, start.add(corners[0]), SHADE[0], light);
        for (int i = 0; i <= corners.length; i++) {
            Vec3 corner = corners[i % corners.length];
            float shade = SHADE[i % SHADE.length];
            vertex(pose, buffer, start.add(corner), shade, light);
            vertex(pose, buffer, end.add(corner), shade, light);
        }
        vertex(pose, buffer, end.add(corners[0]), SHADE[0], light);
        vertex(pose, buffer, end.add(corners[0]), SHADE[0], light);
    }

    private static void vertex(Matrix4f pose, VertexConsumer buffer, Vec3 at, float shade, int light) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                .setColor(R * shade, G * shade, B * shade, 1.0F)
                .setLight(light);
    }

    /** A Rail leaves the frustum long after the Pylon that draws it does. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return BlockEntityRenderer.super.getViewDistance() + (int) Math.ceil(SpanRules.lengthLimit());
    }

    @Override
    public AABB getRenderBoundingBox(BlockEntity pylon) {
        return new AABB(pylon.getBlockPos()).inflate(SpanRules.lengthLimit());
    }
}
