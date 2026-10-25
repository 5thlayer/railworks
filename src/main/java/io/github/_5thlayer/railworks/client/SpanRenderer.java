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
import io.github._5thlayer.railworks.rail.Stringing;
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
 * The Rail between Arms, drawn straight: a square tube from the Arm of this Pylon to the
 * other end of each Span it is the first end of, so exactly one end draws a Span. Only the top
 * segment of a column has an Arm. Placeholder art; the curved Rail is a later ticket.
 */
public final class SpanRenderer implements BlockEntityRenderer<BlockEntity, SpanRenderer.State> {

    /** Where on the top segment the Rail hangs from. */
    private static final double ATTACH_HEIGHT = 0.8;
    private static final float HALF_WIDTH = 0.12F;
    /** Rust red, with the four sides shaded apart. */
    private static final float[] SHADE = {1.0F, 0.75F, 0.55F, 0.85F};
    static final float R = 0.62F;
    static final float G = 0.27F;
    static final float B = 0.18F;

    /** Where the Rail leaves this block's own Arm, from its origin. */
    private static final Vec3 START = new Vec3(0.5, ATTACH_HEIGHT, 0.5);

    /** Where on the top segment a Rail hangs from, in world coordinates. */
    static Vec3 attachPoint(BlockPos arm) {
        return new Vec3(arm.getX() + 0.5, arm.getY() + ATTACH_HEIGHT, arm.getZ() + 0.5);
    }

    public static final class State extends BlockEntityRenderState {
        /** Index-aligned: where each Rail ends, relative to this block's origin, and its light. */
        final List<Vec3> ends = new ArrayList<>();
        final List<Integer> lights = new ArrayList<>();
        /** The slack from this Arm to the player's hand or the aimed Pylon, relative to this block's origin. */
        @Nullable Vec3 slackEnd;
        Stringing.Tint slackTint = Stringing.Tint.HELD;
        int slackLight;
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
        state.slackEnd = null;
        Level level = pylon.getLevel();
        BlockPos at = pylon.getBlockPos();
        if (level == null || !PylonColumn.isArm(level, at)) {
            return;
        }
        Arm self = PylonColumn.arm(at);
        int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, at), level.getBrightness(LightLayer.SKY, at));
        HeldSpan.Aim aim = HeldSpan.current(level);
        for (Span span : ClientRails.network().all()) {
            // A Span's first end sorts first by position, so exactly one end draws it. One the held
            // Rail would cut is drawn by the orange slack instead.
            if (!span.a().equals(self) || (aim != null && aim.cuts(span.a(), span.b()))) {
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
        if (aim != null && aim.anchor().equals(at)) {
            state.slackEnd = aim.freeEnd(partialTicks).subtract(at.getX(), at.getY(), at.getZ());
            state.slackTint = aim.tint();
            state.slackLight = light;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.ends.size(); i++) {
            Vec3 end = state.ends.get(i);
            int light = state.lights.get(i);
            collector.submitCustomGeometry(poseStack, RenderTypes.leash(),
                    (pose, buffer) -> drawSpan(pose.pose(), buffer, START, end, light, 1.0F, R, G, B));
        }
        Vec3 slack = state.slackEnd;
        if (slack != null) {
            float[] colour = colourOf(state.slackTint);
            int light = state.slackLight;
            collector.submitCustomGeometry(poseStack, RenderTypes.leash(),
                    (pose, buffer) -> drawSpan(pose.pose(), buffer, START, slack, light, 1.0F,
                            colour[0], colour[1], colour[2]));
        }
    }

    /** The slack's colour: leash brown while held, green, orange or red over a Pylon: string, cut or refused. */
    private static float[] colourOf(Stringing.Tint tint) {
        return switch (tint) {
            case HELD -> new float[] {0.5F, 0.4F, 0.3F};
            case STRING -> new float[] {0.2F, 0.8F, 0.2F};
            case CUT -> new float[] {1.0F, 0.55F, 0.0F};
            case REFUSED -> new float[] {0.8F, 0.1F, 0.1F};
        };
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
    static void drawSpan(Matrix4f pose, VertexConsumer buffer, Vec3 start, Vec3 end, int light, float fade,
                         float r, float g, float b) {
        Vec3 along = end.subtract(start).normalize();
        Vec3 up = Math.abs(along.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = along.cross(up).normalize().scale(HALF_WIDTH);
        Vec3 lift = side.cross(along).normalize().scale(HALF_WIDTH);
        Vec3[] corners = {
                side.add(lift), side.subtract(lift), side.reverse().subtract(lift), side.reverse().add(lift)
        };
        vertex(pose, buffer, r, g, b, fade, start.add(corners[0]), SHADE[0], light);
        vertex(pose, buffer, r, g, b, fade, start.add(corners[0]), SHADE[0], light);
        for (int i = 0; i <= corners.length; i++) {
            Vec3 corner = corners[i % corners.length];
            float shade = SHADE[i % SHADE.length];
            vertex(pose, buffer, r, g, b, fade, start.add(corner), shade, light);
            vertex(pose, buffer, r, g, b, fade, end.add(corner), shade, light);
        }
        vertex(pose, buffer, r, g, b, fade, end.add(corners[0]), SHADE[0], light);
        vertex(pose, buffer, r, g, b, fade, end.add(corners[0]), SHADE[0], light);
    }

    private static void vertex(Matrix4f pose, VertexConsumer buffer, float r, float g, float b, float fade, Vec3 at,
                               float shade, int light) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                .setColor(r * shade * fade, g * shade * fade, b * shade * fade, 1.0F)
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
