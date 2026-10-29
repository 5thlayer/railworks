// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github._5thlayer.railworks.CarKind;
import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchBlockEntity;
import io.github._5thlayer.railworks.PlatformsNear;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * The item a hatch shows ({@link HatchBlockEntity#shown}), a requester's request or a provider's
 * first stack, flat on each of its four sides, as an item frame holds one, each lit as the block in
 * front of that side is. A hatch has no facing, so it reads from any side of the Rail.
 *
 * <p>While a car of its kind stands at its spot in a Train stopped at its Station
 * ({@link PlatformsNear#facingCar}), a hatch of items draws its Bellows, an accordion sleeve,
 * across the half block of air to the Cargo car's side, and a Fluid hatch its Loading arm, a pipe
 * dropping to a nozzle on the Fluid car's tank, in place of the item on that side. Neither is a
 * block: each is worked out afresh from the cars the client sees, every {@link #REFRESH_TICKS},
 * and goes as the car leaves.
 */
final class HatchRenderer implements BlockEntityRenderer<HatchBlockEntity, HatchRenderer.RenderState> {

    /** How far out from a side its middle stands, as an item frame's stands off its wall. */
    private static final float OFF_FACE = 1 / 16F;
    /** Its size, against an item's own: an item frame's. */
    private static final float SCALE = 0.5F;
    /** How big vanilla's block model draws a block item held {@code FIXED}, as in a frame: half a block. */
    private static final float BLOCK_FIXED_SCALE = 0.5F;
    /**
     * How far past a side the item can reach: a block item, the deepest of vanilla's, is {@link #BLOCK_FIXED_SCALE}
     * times {@link #SCALE} of a block deep, its middle {@link #OFF_FACE} out, so it reaches 3/16.
     */
    private static final double REACH = OFF_FACE + BLOCK_FIXED_SCALE * SCALE / 2;

    /**
     * How far a hatch's side is from the car's beside it, in blocks: a hatch stands two off the
     * Rail's line, its side half a block nearer, and a car's side {@link CarBody#WIDTH} / 2 off it. The
     * Bellows and the Loading arm reach a little past, into the car's facing or its tank.
     */
    private static final float ACROSS = (float) (Station.ROW_OFF - 0.5 - CarBody.WIDTH / 2);
    /** How far into the car's side the Bellows reach: through a Cargo car's facing, 0.11 deep. */
    private static final float INTO_FACING = 0.02F;
    /** How far into the Fluid car's tank, which stands 0.05 inside the car's side, the Loading arm reaches. */
    private static final float INTO_TANK = 0.06F;
    /** How often whether a car stands at the hatch's spot is worked out again, in ticks. */
    private static final int REFRESH_TICKS = 10;

    /**
     * What colour a part of the Bellows or the Loading arm is drawn in, red, green and blue, as
     * {@link CarBody.Paint} is a car's part.
     */
    private enum Paint {
        /** The Bellows' leather. */
        LEATHER(0.42F, 0.30F, 0.20F),
        /** The darker folds between the Bellows' pleats. */
        FOLD(0.24F, 0.17F, 0.12F),
        /** The iron of the Bellows' frames and the Loading arm's flanges. */
        IRON(0.36F, 0.36F, 0.38F),
        /** The Loading arm's pipe, a hazard yellow. */
        PIPE(0.82F, 0.62F, 0.16F);

        final float red;
        final float green;
        final float blue;

        Paint(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    /** Each hatch's side facing a car, as last worked out, and when; dropped with the hatch. */
    private final Map<HatchBlockEntity, Facing> facing = new WeakHashMap<>();

    private record Facing(long at, @Nullable Direction side) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(HatchBlockEntity hatch, RenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(hatch, state, partialTicks, cameraPosition, breakProgress);
        state.item = null;
        state.toCar = null;
        var level = hatch.getLevel();
        if (level == null) {
            return;
        }
        state.serves = CarKind.carrying(
                hatch.getBlockState().getBlock() instanceof HatchBlock block ? block.holds() : Goods.Kind.ITEMS);
        state.toCar = facingCar(hatch, level, state.serves);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state.light[side.get2DDataValue()] = LevelRenderer.getLightCoords(level, hatch.getBlockPos().relative(side));
        }
        var shown = hatch.shown();
        if (shown.isEmpty()) {
            return;
        }
        var item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, shown, ItemDisplayContext.FIXED, level, null, 0);
        state.item = item;
    }

    /**
     * The side facing the car of kind {@code serves} at the hatch's spot, worked out again once
     * {@link #REFRESH_TICKS} have passed.
     */
    private @Nullable Direction facingCar(HatchBlockEntity hatch, Level level, CarKind serves) {
        long now = level.getGameTime();
        Facing last = facing.get(hatch);
        if (last == null || now - last.at() >= REFRESH_TICKS || now < last.at()) {
            last = new Facing(now, PlatformsNear.facingCar(level, hatch.getBlockPos(), serves).orElse(null));
            facing.put(hatch, last);
        }
        return last.side();
    }

    /**
     * On each side, turned as an item frame on that side would turn it, so its own front faces out;
     * on the side facing a car, its Bellows or Loading arm instead.
     */
    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (state.toCar != null) {
            submitToCar(state, poseStack, collector);
        }
        if (state.item == null) {
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side == state.toCar) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(180 - side.toYRot()));
            poseStack.translate(0, 0, -(0.5F + OFF_FACE));
            poseStack.scale(SCALE, SCALE, SCALE);
            state.item.submit(poseStack, collector, state.light[side.get2DDataValue()], OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /**
     * The Bellows or the Loading arm, out of the side facing the car, in that side's frame: across
     * the block from -0.5 to 0.5 along x, up it from -0.5 to 0.5 along y, and out from its face at
     * -0.5 along -z, to the car's side at {@link #ACROSS} further.
     */
    private static void submitToCar(RenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        int light = state.light[state.toCar.get2DDataValue()];
        boolean bellows = state.serves == CarKind.CARGO;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - state.toCar.toYRot()));
        collector.submitCustomGeometry(poseStack, RenderTypes.leash(), (pose, buffer) -> {
            if (bellows) {
                bellows(pose.pose(), buffer, light);
            } else {
                loadingArm(pose.pose(), buffer, light);
            }
        });
        poseStack.popPose();
    }

    /**
     * An accordion sleeve over the face, an iron frame at each end and leather pleats between,
     * folding in and out, its middle at the height of a Cargo car's chests.
     */
    private static void bellows(Matrix4f pose, VertexConsumer buffer, int light) {
        float face = -0.5F;
        float car = face - ACROSS - INTO_FACING;
        float frame = 0.05F;
        float y = -0.05F;
        box(pose, buffer, 0.42F, y, face + 0.01F, face - frame, Paint.IRON, light);
        box(pose, buffer, 0.40F, y, car + frame, car, Paint.IRON, light);
        int pleats = 8;
        float from = face - frame;
        float step = (from - (car + frame)) / pleats;
        for (int i = 0; i < pleats; i++) {
            float near = from - i * step;
            // Each reaches a little into what is either side, the folds further, so no two faces
            // share a plane.
            if (i % 2 == 0) {
                box(pose, buffer, 0.37F, y, near + 0.005F, near - step - 0.005F, Paint.LEATHER, light);
            } else {
                box(pose, buffer, 0.32F, y, near + 0.01F, near - step - 0.01F, Paint.FOLD, light);
            }
        }
    }

    /**
     * A pipe out of a flange on the face, which drops at an elbow and turns into a nozzle on the
     * Fluid car's tank, its end in the tank's side.
     */
    private static void loadingArm(Matrix4f pose, VertexConsumer buffer, int light) {
        float face = -0.5F;
        float tank = face - ACROSS - INTO_TANK;
        float high = 0.18F;
        float low = -0.12F;
        float pipe = 0.07F;
        float elbow = face - 0.3F;
        // The flange on the hatch, the pipe out to the elbow, down, then on into the nozzle.
        // Each pipe starts and ends inside what it joins, so no two faces share a plane.
        box(pose, buffer, 0.15F, high, face + 0.01F, face - 0.05F, Paint.IRON, light);
        box(pose, buffer, pipe, high, face - 0.04F, elbow, Paint.PIPE, light);
        box(pose, buffer, 0.1F, high, elbow + 0.1F, elbow - 0.1F, Paint.IRON, light);
        boxY(pose, buffer, pipe - 0.005F, elbow, high, low, Paint.PIPE, light);
        box(pose, buffer, pipe + 0.005F, low, elbow + pipe + 0.01F, tank + 0.07F, Paint.PIPE, light);
        box(pose, buffer, 0.13F, low, tank + 0.08F, tank, Paint.IRON, light);
    }

    /**
     * A box square across x and y, {@code half} each way of x 0 and of {@code y}, from {@code z0} to
     * {@code z1} along z.
     */
    private static void box(Matrix4f pose, VertexConsumer buffer, float half, float y, float z0, float z1,
                            Paint paint, int light) {
        CarRenderer.box(pose, buffer, -half, y - half, Math.min(z0, z1), half, y + half, Math.max(z0, z1),
                paint.red, paint.green, paint.blue, light);
    }

    /**
     * A box square across x and z, {@code half} each way of x 0 and of {@code z}, from {@code y0} to
     * {@code y1} up.
     */
    private static void boxY(Matrix4f pose, VertexConsumer buffer, float half, float z, float y0, float y1,
                             Paint paint, int light) {
        CarRenderer.box(pose, buffer, -half, Math.min(y0, y1), z - half, half, Math.max(y0, y1), z + half,
                paint.red, paint.green, paint.blue, light);
    }

    /**
     * The block and as far past its sides as an item can reach or the Bellows or Loading arm to the
     * car beside it, so none is culled while it is still in view.
     */
    @Override
    public AABB getRenderBoundingBox(HatchBlockEntity hatch) {
        double reach = Math.max(REACH, ACROSS + INTO_TANK);
        return new AABB(hatch.getBlockPos()).inflate(reach, 0, reach);
    }

    static final class RenderState extends BlockEntityRenderState {
        private @Nullable ItemStackRenderState item;
        /** The side facing the car at its spot, which its Bellows or Loading arm reaches out of; null for none. */
        private @Nullable Direction toCar;
        /** The kind of car the hatch serves, which says whether it draws its Bellows or its Loading arm. */
        private CarKind serves = CarKind.CARGO;
        /** Each side's light, by its {@link Direction#get2DDataValue()}. */
        private final int[] light = new int[Direction.Plane.HORIZONTAL.length()];
    }
}
