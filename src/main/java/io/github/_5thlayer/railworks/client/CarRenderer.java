// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.FluidCarEntity;
import io.github._5thlayer.railworks.rail.CarBody;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * A car, drawn from its {@link CarBody} as placeholder boxes: a boxy car straddling the Rail, in the
 * Rail's rust red, on a Cargo car a vanilla chest set into each side, and on a Fluid car a tank. Every
 * face is its own triangle strip, padded at both ends as {@link SpanRenderer#drawSpan} pads its tube,
 * so the strips the pipeline joins in one buffer leave no triangles between them.
 *
 * <p>A Fluid car's Sight glasses are filled, on their panes' outer faces, with its tank's fluid as
 * the server last synced it ({@link SightGlassFluid}), tiled a block to a tile from the pane's bottom,
 * so the surface only crops the top tile.
 */
public final class CarRenderer<T extends CarEntity> extends EntityRenderer<T, CarRenderer.State> {

    public static final class State extends EntityRenderState {
        float yRot;
        float xRot;
        /** A Fluid car's fluid; null for none, or another car. */
        @Nullable SightGlassFluid fluid;
        /** How full the tank is: its amount and capacity, in millibuckets. */
        int amount;
        int capacity;
    }

    /** The six faces shaded apart: top, bottom, then the four sides. */
    private static final float[] SHADE = {1.0F, 0.5F, 0.8F, 0.8F, 0.65F, 0.65F};
    private final CarBody body;
    private final ChestModel chest;
    private final SpriteGetter sprites;

    private CarRenderer(EntityRendererProvider.Context context, CarBody body) {
        super(context);
        this.body = body;
        this.chest = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
        this.sprites = context.getSprites();
        this.shadowRadius = 0.0F;
    }

    public static <T extends CarEntity> CarRenderer<T> locomotive(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, CarBody.LOCOMOTIVE);
    }

    public static <T extends CarEntity> CarRenderer<T> electric(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, CarBody.ELECTRIC);
    }

    public static <T extends CarEntity> CarRenderer<T> cargo(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, CarBody.CARGO);
    }

    public static <T extends CarEntity> CarRenderer<T> fluid(EntityRendererProvider.Context context) {
        return new CarRenderer<>(context, CarBody.FLUID);
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
        state.fluid = null;
        if (car instanceof FluidCarEntity fluidCar) {
            FluidStack tank = fluidCar.tankSeen();
            state.fluid = SightGlassFluid.of(tank);
            state.amount = tank.getAmount();
            state.capacity = fluidCar.tankCapacitySeen();
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // The body is built facing +z. The car's origin is the middle of its floor, on the flange's
        // top, and it pitches about that point so a climb keeps the floor on the Rail.
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.leash(), (pose, buffer) -> {
            for (CarBody.Part part : body.parts()) {
                CarBody.Box b = part.box();
                CarBody.Paint paint = part.paint();
                box(pose.pose(), buffer, (float) b.minX(), (float) b.minY(), (float) b.minZ(),
                        (float) b.maxX(), (float) b.maxY(), (float) b.maxZ(), paint.red, paint.green, paint.blue, light);
            }
        });
        for (CarBody.Box chest : body.chests()) {
            submitChest(chest, poseStack, collector, light);
        }
        SightGlassFluid fluid = state.fluid;
        if (fluid != null) {
            int fluidLight = fluid.light(light);
            collector.submitCustomGeometry(poseStack, fluid.renderType(), (pose, buffer) -> {
                for (CarBody.SightGlass glass : body.sightGlasses()) {
                    fill(pose, buffer, glass, (float) glass.surface(state.amount, state.capacity), fluid, fluidLight);
                }
            });
        }
        poseStack.popPose();
    }

    /**
     * The fluid in {@code glass} up to {@code surface}, on its pane's outer face: one quad per tile of
     * a block, from the pane's bottom and its back end, the last row cropped at the surface and the
     * last column at the pane's front end, each showing as much of the sprite as it covers.
     */
    private static void fill(PoseStack.Pose pose, VertexConsumer buffer, CarBody.SightGlass glass, float surface,
                             SightGlassFluid fluid, int light) {
        CarBody.Box pane = glass.pane();
        float side = (float) glass.side();
        float x = side > 0 ? (float) pane.maxX() + SightGlassFluid.PROUD : (float) pane.minX() - SightGlassFluid.PROUD;
        float bottom = (float) pane.minY();
        float back = (float) pane.minZ();
        for (float y0 = bottom; y0 < surface; y0 += 1.0F) {
            float y1 = Math.min(y0 + 1.0F, surface);
            for (float z0 = back; z0 < pane.maxZ(); z0 += 1.0F) {
                float z1 = Math.min(z0 + 1.0F, (float) pane.maxZ());
                // The sprite runs along z from the tile's back end on both sides, so a cropped tile
                // still meets the next at the sprite's edge.
                float uBack = 0.0F;
                float uFront = z1 - z0;
                // The sprite's top is v 0: a cropped row shows its lower part, as a block's fluid shows its top cut off.
                float v1 = 1.0F;
                float v0 = 1.0F - (y1 - y0);
                // Counter-clockwise as seen from the side it faces, from the bottom left, which is
                // the tile's front end on the +x side and its back end on the -x side.
                float left = side > 0 ? z1 : z0;
                float right = side > 0 ? z0 : z1;
                float uLeft = side > 0 ? uFront : uBack;
                float uRight = side > 0 ? uBack : uFront;
                fluid.vertex(pose, buffer, x, y0, left, uLeft, v1, light, side, 0.0F, 0.0F);
                fluid.vertex(pose, buffer, x, y0, right, uRight, v1, light, side, 0.0F, 0.0F);
                fluid.vertex(pose, buffer, x, y1, right, uRight, v0, light, side, 0.0F, 0.0F);
                fluid.vertex(pose, buffer, x, y1, left, uLeft, v0, light, side, 0.0F, 0.0F);
            }
        }
    }

    /** A closed chest filling the cube as it would a block, its front out of the car's nearer side. */
    private void submitChest(CarBody.Box cube, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        float size = (float) (cube.maxY() - cube.minY());
        boolean right = cube.minX() + cube.maxX() > 0.0;
        poseStack.pushPose();
        poseStack.translate((cube.minX() + cube.maxX()) / 2.0, cube.minY(), (cube.minZ() + cube.maxZ()) / 2.0);
        // The chest's model faces +z; turn it to face out of its side.
        poseStack.mulPose(Axis.YP.rotationDegrees(right ? 90.0F : -90.0F));
        poseStack.scale(size, size, size);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        collector.submitModel(chest, 0.0F, poseStack, light, OverlayTexture.NO_OVERLAY, -1,
                Sheets.CHEST_REGULAR.select(ChestType.SINGLE), sprites, 0, null);
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
