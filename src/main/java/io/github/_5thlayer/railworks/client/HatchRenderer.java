// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github._5thlayer.railworks.CarKind;
import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchBlockEntity;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.RailGeometry;
import io.github._5thlayer.railworks.rail.SightGlassFill;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * The item a hatch of items shows ({@link HatchBlockEntity#shown}), a requester's request or a
 * provider's first stack, flat on each of its four sides, as an item frame holds one, each lit as the
 * block in front of that side is. A hatch has no facing, so it reads from any side of the Rail.
 *
 * <p>Each side of a Fluid hatch is a Sight glass: its round window, dark glass in its texture, is
 * filled from the bottom as far as the tank is full ({@link SightGlassFill#roundWindow}) with the
 * tank's fluid as the server last sent it ({@link SightGlassFluid}), just proud of the face, on all
 * four sides, the one facing a car too, with nothing in front of it. A requester's bucket, the filled
 * bucket of what it asks for, lies flat on its top instead, as an item rests on a shelf, lit as the
 * block above is, unless its Loading arm rises from there.
 *
 * <p>While a car of its kind stands at its spot in a Train stopped at its Station, a hatch of items
 * draws its Bellows, an accordion sleeve, across the half block of air to the Cargo car's side, in
 * place of the item on that side, and a Fluid hatch its Loading arm, a pipe rising from its top, over
 * the car and down into the top of the Fluid car's tank, in place of the bucket there. Neither is a
 * block: each is drawn as far out as the hatch sees it reached ({@link HatchBlockEntity#reachOut}),
 * reaching out as the car comes to rest and folding back as it leaves.
 */
final class HatchRenderer implements BlockEntityRenderer<HatchBlockEntity, HatchRenderer.RenderState> {

    /** How far out from a side its middle stands, as an item frame's stands off its wall. */
    private static final float OFF_FACE = 1 / 16F;
    /**
     * How far above a Fluid hatch's top a requester's bucket lies: a flat item's whole depth at
     * {@link #SCALE}, so it floats just clear of the casing.
     */
    private static final float ON_TOP = 1 / 32F;
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
     * Bellows reach a little past, into the car's facing.
     */
    private static final float ACROSS = (float) (Station.ROW_OFF - 0.5 - CarBody.WIDTH / 2);
    /** How far into the car's side the Bellows reach: through a Cargo car's facing, 0.11 deep. */
    private static final float INTO_FACING = 0.02F;
    /**
     * Where the floor's underside of a car beside a hatch is, up from the hatch's middle: the hatch's
     * block starts a block above the flange's underside, and the floor rests on the flange's top.
     */
    private static final float FLOOR = (float) (RailGeometry.FLANGE_HEIGHT - 1.5);
    /**
     * How high the Loading arm's pipe runs over the car, up from the hatch's middle: far enough over
     * the dome that the pipe drops into it below the elbow, and clear of the car's top.
     */
    private static final float OVER_CAR = FLOOR + (float) CarBody.DOME_TOP + 0.3F;
    /** The Loading arm's pipe's half width. */
    private static final float PIPE = 0.07F;
    /** The Loading arm's elbows' half width. */
    private static final float ELBOW = 0.1F;
    /** The Loading arm's collar's half width, on the tank's top or its dome. */
    private static final float COLLAR = 0.13F;
    /** A side's pixels, as its texture's, across and up. */
    private static final float PIXELS = 16.0F;
    /** How far along the Loading arm's reach its riser has risen, and then its run has reached out over the car. */
    private static final double RISEN = 0.35;
    private static final double RUN_OUT = 0.75;

    /**
     * What colour a part of the Bellows or the Loading arm is drawn in, red, green and blue.
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
        state.fluid = null;
        var level = hatch.getLevel();
        if (level == null) {
            return;
        }
        state.serves = CarKind.carrying(
                hatch.getBlockState().getBlock() instanceof HatchBlock block ? block.holds() : Goods.Kind.ITEMS);
        state.out = (float) hatch.reachOut(partialTicks);
        state.toCar = state.out > 0.0F ? hatch.reachSide() : null;
        state.toDome = hatch.reachesDome();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state.light[side.get2DDataValue()] = LevelRenderer.getLightCoords(level, hatch.getBlockPos().relative(side));
        }
        state.topLight = LevelRenderer.getLightCoords(level, hatch.getBlockPos().above());
        var tank = hatch.tankSeen();
        state.fluid = SightGlassFluid.of(tank);
        state.fill = SightGlassFill.roundWindow(tank.getAmount(), hatch.tankCapacitySeen());
        var shown = hatch.shown();
        if (shown.isEmpty()) {
            return;
        }
        var item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, shown, ItemDisplayContext.FIXED, level, null, 0);
        state.item = item;
    }

    /**
     * On each side, turned as an item frame on that side would turn it, so its own front faces out;
     * on the side facing a car, its Bellows instead; a Fluid hatch's bucket on its top, or its Loading
     * arm there.
     */
    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
        if (state.toCar != null) {
            submitToCar(state, poseStack, collector);
        }
        if (state.fluid != null) {
            submitFill(state, state.fluid, poseStack, collector);
        }
        if (state.item == null) {
            return;
        }
        if (state.serves == CarKind.FLUID) {
            if (state.toCar == null) {
                submitOnTop(state.item, state.topLight, poseStack, collector);
            }
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
     * {@code item} lying flat on the top, its front up, {@link #ON_TOP} above it, the right way up to a
     * player north of the hatch.
     */
    private static void submitOnTop(ItemStackRenderState item, int light, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1.0 + ON_TOP, 0.5);
        // An item faces -z in its frame, as on a north side; turned about x, it faces up.
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        poseStack.scale(SCALE, SCALE, SCALE);
        item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * The fluid in each side's round window, in that side's frame, as {@link #submitToCar}'s: its
     * pixels across from the left as seen facing it, at +x, and up from its bottom, the sprite laid
     * on them as on a block's face.
     */
    private static void submitFill(RenderState state, SightGlassFluid fluid, PoseStack poseStack, SubmitNodeCollector collector) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int light = fluid.light(state.light[side.get2DDataValue()]);
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.mulPose(Axis.YP.rotationDegrees(180 - side.toYRot()));
            collector.submitCustomGeometry(poseStack, fluid.renderType(), (pose, buffer) -> {
                float z = -0.5F - SightGlassFluid.PROUD;
                for (SightGlassFill.Rect rect : state.fill) {
                    float left = 0.5F - (float) rect.left() / PIXELS;
                    float right = 0.5F - (float) rect.right() / PIXELS;
                    float bottom = (float) rect.bottom() / PIXELS - 0.5F;
                    float top = (float) rect.top() / PIXELS - 0.5F;
                    float uLeft = (float) rect.left() / PIXELS;
                    float uRight = (float) rect.right() / PIXELS;
                    // The sprite's top is v 0.
                    float vBottom = 1.0F - (float) rect.bottom() / PIXELS;
                    float vTop = 1.0F - (float) rect.top() / PIXELS;
                    // Counter-clockwise as seen from in front of the side, from the bottom left.
                    fluid.vertex(pose, buffer, left, bottom, z, uLeft, vBottom, light, 0.0F, 0.0F, -1.0F);
                    fluid.vertex(pose, buffer, right, bottom, z, uRight, vBottom, light, 0.0F, 0.0F, -1.0F);
                    fluid.vertex(pose, buffer, right, top, z, uRight, vTop, light, 0.0F, 0.0F, -1.0F);
                    fluid.vertex(pose, buffer, left, top, z, uLeft, vTop, light, 0.0F, 0.0F, -1.0F);
                }
            });
            poseStack.popPose();
        }
    }

    /**
     * The Bellows or the Loading arm, toward the car, in the frame of the side facing it: across the
     * block from -0.5 to 0.5 along x, up it from -0.5 to 0.5 along y, and out from its face at -0.5
     * along -z, to the car's side at {@link #ACROSS} further and its middle at {@link Station#ROW_OFF}.
     */
    private static void submitToCar(RenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        int light = state.light[state.toCar.get2DDataValue()];
        boolean bellows = state.serves == CarKind.CARGO;
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - state.toCar.toYRot()));
        collector.submitCustomGeometry(poseStack, RenderTypes.leash(), (pose, buffer) -> {
            if (bellows) {
                bellows(pose.pose(), buffer, state.out, light);
            } else {
                loadingArm(pose.pose(), buffer, state.out, state.toDome, light);
            }
        });
        poseStack.popPose();
    }

    /**
     * An accordion sleeve over the face, an iron frame at each end and leather pleats between,
     * folding in and out, its middle at the height of a Cargo car's crates: {@code out} of the way
     * to the car, its car's frame sliding out from just past the hatch's and the pleats unfolding
     * behind it.
     */
    private static void bellows(Matrix4f pose, VertexConsumer buffer, float out, int light) {
        float face = -0.5F;
        float frame = 0.05F;
        // Folded, the car's frame stands within the hatch's, a little further out, so no faces meet.
        float car = Mth.lerp(out, face - frame - 0.01F, face - ACROSS - INTO_FACING);
        float y = -0.05F;
        box(pose, buffer, 0.42F, y, face + 0.01F, face - frame, Paint.IRON, light);
        box(pose, buffer, 0.40F, y, car + frame, car, Paint.IRON, light);
        int pleats = 8;
        float from = face - frame;
        float step = (from - (car + frame)) / pleats;
        if (step <= 0.0F) {
            return;
        }
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
     * A pipe up out of a flange on the top, which turns at an elbow over the Fluid car, runs out to
     * above its middle and drops at a second elbow into a collar on the tank's top: on the dome when
     * {@code toDome}, the hatch beside the car's middle block, and on the tank straight across
     * from the hatch otherwise. {@code out} of the way there, in turn: the first elbow rises out of
     * the hatch on its pipe until {@link #RISEN}, the second runs out of it over the car until
     * {@link #RUN_OUT}, and the collar drops from it onto the tank.
     */
    private static void loadingArm(Matrix4f pose, VertexConsumer buffer, float out, boolean toDome, int light) {
        float top = 0.5F;
        float car = (float) -Station.ROW_OFF;
        float landing = FLOOR + (float) (toDome ? CarBody.DOME_TOP : CarBody.TANK_TOP);
        float risen = stage(out, 0.0, RISEN);
        float run = stage(out, RISEN, RUN_OUT);
        float drop = stage(out, RUN_OUT, 1.0);
        // The first elbow starts inside the hatch, under its top; the second, a little smaller, inside the first.
        float rise = Mth.lerp(risen, top - ELBOW - 0.02F, OVER_CAR);
        float secondHalf = ELBOW - 0.01F;
        float secondAt = Mth.lerp(run, 0.0F, car);
        float under = OVER_CAR - secondHalf;
        float end = Mth.lerp(drop, under, landing + 0.07F);
        // The flange on the hatch, the pipe up to the first elbow, out to the second, then down into the
        // collar. Each pipe starts and ends inside what it joins, and no two are as wide, so no two
        // faces share a plane.
        boxY(pose, buffer, 0.15F, 0F, top - 0.01F, top + 0.05F, Paint.IRON, light);
        if (rise > top + 0.04F) {
            boxY(pose, buffer, PIPE, 0F, top + 0.04F, rise, Paint.PIPE, light);
        }
        box(pose, buffer, ELBOW, rise, ELBOW, -ELBOW, Paint.IRON, light);
        if (run <= 0.0F) {
            return;
        }
        if (secondAt + secondHalf - 0.01F < -ELBOW + 0.01F) {
            box(pose, buffer, PIPE + 0.005F, OVER_CAR, -ELBOW + 0.01F, secondAt + secondHalf - 0.01F, Paint.PIPE, light);
        }
        box(pose, buffer, secondHalf, OVER_CAR, secondAt + secondHalf, secondAt - secondHalf, Paint.IRON, light);
        if (drop <= 0.0F) {
            return;
        }
        boxY(pose, buffer, PIPE - 0.005F, car, under + 0.01F, end, Paint.PIPE, light);
        boxY(pose, buffer, COLLAR, car, end + 0.01F, end - 0.08F, Paint.IRON, light);
    }

    /** How far through the stage from {@code from} to {@code to} of the reach {@code out} is, from 0 to 1. */
    private static float stage(float out, double from, double to) {
        return (float) Math.clamp((out - from) / (to - from), 0.0, 1.0);
    }

    /**
     * A box square across x and y, {@code half} each way of x 0 and of {@code y}, from {@code z0} to
     * {@code z1} along z.
     */
    private static void box(Matrix4f pose, VertexConsumer buffer, float half, float y, float z0, float z1,
                            Paint paint, int light) {
        flatBox(pose, buffer, -half, y - half, Math.min(z0, z1), half, y + half, Math.max(z0, z1),
                paint.red, paint.green, paint.blue, light);
    }

    /**
     * A box square across x and z, {@code half} each way of x 0 and of {@code z}, from {@code y0} to
     * {@code y1} up.
     */
    private static void boxY(Matrix4f pose, VertexConsumer buffer, float half, float z, float y0, float y1,
                             Paint paint, int light) {
        flatBox(pose, buffer, -half, Math.min(y0, y1), z - half, half, Math.max(y0, y1), z + half,
                paint.red, paint.green, paint.blue, light);
    }

    /**
     * The block and as far past its sides and its top as an item can reach, or the Bellows or Loading
     * arm to the car beside it, so none is culled while it is still in view.
     */
    @Override
    public AABB getRenderBoundingBox(HatchBlockEntity hatch) {
        // The Loading arm reaches out to the car's middle, past its collar, and up to its elbows.
        double reach = Math.max(REACH, Station.ROW_OFF - 0.5 + COLLAR);
        double above = Math.max(REACH, OVER_CAR + ELBOW - 0.5);
        return new AABB(hatch.getBlockPos()).inflate(reach, 0, reach).expandTowards(0, above, 0);
    }

    static final class RenderState extends BlockEntityRenderState {
        private @Nullable ItemStackRenderState item;
        /** The side facing the car at its spot, which its Bellows or Loading arm reaches out of; null for none. */
        private @Nullable Direction toCar;
        /** How far its Bellows or Loading arm is reached out, from 0 to 1. */
        private float out;
        /** Whether a Loading arm drops onto the car's dome, the hatch beside its middle block. */
        private boolean toDome;
        /** The kind of car the hatch serves, which says whether it draws its Bellows or its Loading arm. */
        private CarKind serves = CarKind.CARGO;
        /** A Fluid hatch's fluid; null for none, or a hatch of items. */
        private @Nullable SightGlassFluid fluid;
        /** How much of each side's round window the fluid fills. */
        private List<SightGlassFill.Rect> fill = List.of();
        /** The light above its top, where a requester Fluid hatch's bucket lies. */
        private int topLight;
        /** Each side's light, by its {@link Direction#get2DDataValue()}. */
        private final int[] light = new int[Direction.Plane.HORIZONTAL.length()];
    }

    /** The six faces of a flat box shaded apart: top, bottom, then the four sides. */
    private static final float[] SHADE = {1.0F, 0.5F, 0.8F, 0.8F, 0.65F, 0.65F};

    /** An axis-aligned box in one colour, six faces each as a padded strip of four, as {@link SpanRenderer#drawSpan} pads its tube. */
    private static void flatBox(Matrix4f pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
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
