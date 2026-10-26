// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.rail.HopperSpots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

/**
 * Where hoppers go by a Cargo car stopped at a Station, drawn in the world as Factorio draws where an
 * inserter reaches: a yellow box on either side of the car at its floor, where a hopper sneak-used
 * at the car fills it, and an amber one under the Rail where a hopper empties it. They show for the
 * one car under the crosshair, whatever the player holds, which is the car a sneak-used hopper would
 * go beside, and they go when the Train leaves or the player looks away. The spots are
 * {@link HopperSpots}', at the car's synced box; one a block already fills, a Pylon or a Station
 * under the Rail, is not drawn, since no hopper can go there.
 */
final class HopperSpotsOverlay {

    private static final ContextKey<List<Box>> BOXES =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "hopper_spots"));

    /** Where a hopper fills the car: yellow. */
    private static final int FILL = 0xFFE000;
    /** Where a hopper empties it: amber. */
    private static final int EMPTY = 0xFF9000;
    private static final int FACE_ALPHA = 0x50;
    private static final int EDGE_ALPHA = 0xFF;
    /** Out past a placed hopper's faces, so the box does not fight them. */
    private static final double GROW = 0.01;
    private static final float LINE_WIDTH = 2.0F;

    private record Box(AABB box, int colour) {
    }

    private HopperSpotsOverlay() {
    }

    /** Takes the boxes of the Cargo car under the crosshair, while it loads. */
    static void onExtract(ExtractLevelRenderStateEvent event) {
        List<Box> boxes = new ArrayList<>();
        if (Minecraft.getInstance().hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof CargoCarEntity car
                && car.level() == event.getLevel() && car.loadingSeen()) {
            HopperSpots spots = car.hopperSpots();
            for (HopperSpots.Filling filling : spots.filling()) {
                add(boxes, event.getLevel(), filling.spot(), FILL);
            }
            add(boxes, event.getLevel(), spots.emptying(), EMPTY);
        }
        event.getRenderState().setRenderData(BOXES, boxes.isEmpty() ? null : List.copyOf(boxes));
    }

    private static void add(List<Box> boxes, Level level, HopperSpots.Spot spot, int colour) {
        BlockState state = level.getBlockState(new BlockPos(spot.x(), spot.y(), spot.z()));
        if (state.canBeReplaced() || state.is(Blocks.HOPPER)) {
            boxes.add(new Box(block(spot), colour));
        }
    }

    /** Draws the boxes taken: translucent faces, and their edges over them. */
    static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Box> boxes = event.getLevelRenderState().getRenderData(BOXES);
        if (boxes == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
            for (Box box : boxes) {
                faces(pose, buffer, box.box().move(camera.reverse()), FACE_ALPHA << 24 | box.colour());
            }
        });
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            for (Box box : boxes) {
                edges(pose, buffer, box.box().move(camera.reverse()), EDGE_ALPHA << 24 | box.colour());
            }
        });
    }

    private static AABB block(HopperSpots.Spot spot) {
        return new AABB(spot.x(), spot.y(), spot.z(), spot.x() + 1, spot.y() + 1, spot.z() + 1).inflate(GROW);
    }

    /** The six faces, each wound both ways so it shows from inside the box too. */
    private static void faces(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int argb) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        float[][] quads = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
        };
        for (float[] q : quads) {
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(pose, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(argb);
            }
            for (int i = 3; i >= 0; i--) {
                buffer.addVertex(pose, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(argb);
            }
        }
    }

    /** The twelve edges, as vanilla's {@code ShapeRenderer} draws a shape's. */
    private static void edges(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int argb) {
        Shapes.create(b).forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1)).normalize();
            buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(argb).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
            buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(argb).setNormal(pose, normal).setLineWidth(LINE_WIDTH);
        });
    }
}
