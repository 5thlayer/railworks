// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.rail.RailGeometry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * A car, drawn as placeholder boxes in the Rail's rust red: a skirt down both sides of the web and a
 * floor over it. A Burner Locomotive adds a roof, so the driver shows in the open cab, and a darker
 * engine block at the front so the heading reads; a Cargo car adds a low crate, a rider sitting on
 * its lid. Every
 * face is its own triangle strip, padded at both ends as {@link SpanRenderer#drawSpan} pads its
 * tube, so the strips the pipeline joins in one buffer leave no triangles between them.
 */
public final class CarRenderer<T extends CarEntity> extends EntityRenderer<T, CarRenderer.State> {

    public static final class State extends EntityRenderState {
        float yRot;
        float xRot;
    }

    /** Rust red, as the Rail's, with the six faces shaded apart: top, bottom, then the four sides. */
    private static final float[] SHADE = {1.0F, 0.5F, 0.8F, 0.8F, 0.65F, 0.65F};

    /** How far a box that meets another stops short of the other's faces. */
    private static final float INSET = 0.01F;

    private final boolean cargo;

    private CarRenderer(EntityRendererProvider.Context context, boolean cargo) {
        super(context);
        this.cargo = cargo;
        this.shadowRadius = 0.0F;
    }

    public static <T extends CarEntity> CarRenderer<T> locomotive(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, false);
    }

    public static <T extends CarEntity> CarRenderer<T> cargo(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, true);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T car, State state, float partialTicks) {
        super.extractRenderState(car, state, partialTicks);
        state.yRot = Mth.rotLerp(partialTicks, car.yRotO, car.getYRot());
        state.xRot = Mth.lerp(partialTicks, car.xRotO, car.getXRot());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // The body is built facing +z. The car's origin is the middle of its floor, on the flange's
        // top, and it pitches about that point so a climb keeps the floor on the Rail.
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
        int light = state.lightCoords;
        // The skirt down both sides of the web and the floor over it, then the car's own body. Where two
        // boxes meet, one is inset by INSET and reaches into the other, so no two faces share a plane
        // and the depth test never has to choose between them.
        collector.submitCustomGeometry(poseStack, RenderTypes.leash(), (pose, buffer) -> {
            float web = (float) (RailGeometry.WEB_WIDTH / 2.0);
            float skirt = (float) (CarEntity.WIDTH / 2.0);
            float floor = (float) (RailGeometry.WEB_HEIGHT + 0.05);
            float in = skirt - INSET;
            box(pose.pose(), buffer, -in, 0.0F, -0.9F + INSET, -web - 0.02F, floor + 0.2F, 0.9F - INSET,
                    0.40F, 0.17F, 0.12F, light);
            box(pose.pose(), buffer, web + 0.02F, 0.0F, -0.9F + INSET, in, floor + 0.2F, 0.9F - INSET,
                    0.40F, 0.17F, 0.12F, light);
            box(pose.pose(), buffer, -skirt, floor, -0.9F, skirt, floor + 0.15F, 0.9F, 0.62F, 0.27F, 0.18F, light);
            if (cargo) {
                // A crate the length of the car, its lid lighter so the top reads; the crate stands in
                // the floor and its top in the lid.
                box(pose.pose(), buffer, -skirt + 0.05F + INSET, floor + 0.1F, -0.85F + INSET,
                        skirt - 0.05F - INSET, floor + 0.67F, 0.85F - INSET, 0.48F, 0.22F, 0.15F, light);
                box(pose.pose(), buffer, -skirt + 0.05F, floor + 0.65F, -0.85F, skirt - 0.05F, floor + 0.72F, 0.85F,
                        0.62F, 0.27F, 0.18F, light);
            } else {
                box(pose.pose(), buffer, -0.45F, floor + 0.1F, 0.2F, 0.45F, 1.6F, 0.9F - INSET, 0.40F, 0.17F, 0.12F, light);
                box(pose.pose(), buffer, -skirt, 2.6F, -0.9F, skirt, 2.75F, 0.9F, 0.62F, 0.27F, 0.18F, light);
            }
        });
        poseStack.popPose();
    }

    /** An axis-aligned box, six faces each as a padded strip of four. */
    static void box(Matrix4f pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
                    float r, float g, float b, int light) {
        // Each face lists its corners in strip order, wound outward.
        float[][][] faces = {
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z0}, {x1, y1, z1}},
                {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z1}, {x1, y0, z0}},
                {{x0, y0, z1}, {x1, y0, z1}, {x0, y1, z1}, {x1, y1, z1}},
                {{x1, y0, z0}, {x0, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
                {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z1}, {x1, y1, z0}},
                {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z0}, {x0, y1, z1}},
        };
        for (int face = 0; face < faces.length; face++) {
            float[][] corners = faces[face];
            float shade = SHADE[face];
            // First and last vertex twice over, so the strip joins the next as zero-area triangles
            // and keeps an even count for the winding.
            vertex(pose, buffer, corners[0], r, g, b, shade, light);
            vertex(pose, buffer, corners[0], r, g, b, shade, light);
            for (float[] corner : corners) {
                vertex(pose, buffer, corner, r, g, b, shade, light);
            }
            vertex(pose, buffer, corners[3], r, g, b, shade, light);
            vertex(pose, buffer, corners[3], r, g, b, shade, light);
        }
    }

    private static void vertex(Matrix4f pose, VertexConsumer buffer, float[] at, float r, float g, float b,
                               float shade, int light) {
        buffer.addVertex(pose, at[0], at[1], at[2]).setColor(r * shade, g * shade, b * shade, 1.0F).setLight(light);
    }
}
