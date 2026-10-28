// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github._5thlayer.groundworks.client.PlacementPreviewEvent;
import io.github._5thlayer.railworks.HatchItem;
import io.github._5thlayer.railworks.PlatformsNear;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

/**
 * While a hatch is held, whatever the aim, an outline round the free blocks beside each car spot
 * of the platforms near the player, and beside each leading car's place in a colour of its own, a
 * spot's two on a side as one box falling half a car gap short of its ends
 * ({@link PlatformsNear#freeSpots}), where a hatch could go, asked of the client's Spans and the
 * Station blocks it has loaded, twice a second or as the player moves.
 */
final class HatchSpots {

    /** The Station's cream, so the outlines read as Railworks' and apart from Groundworks' red. */
    private static final int COLOUR = 0xFFD8C8A0;
    /** A coal fire's amber, for the leading car's place, where a provider fuels its Locomotive. */
    private static final int LEADING_COLOUR = 0xFFF0A040;
    private static final float WIDTH = 2.0F;
    // A little inside the blocks, so vanilla's own outline of an aimed block stays apart.
    private static final double INSET = 0.06;

    /** How often the free blocks are worked out again while the player stands still, in ticks. */
    private static final int REFRESH_TICKS = 10;

    private static List<PlatformsNear.FreeSpot> free = List.of();
    private static long freeAt = Long.MIN_VALUE;
    private static BlockPos freeFrom = BlockPos.ZERO;

    private HatchSpots() {
    }

    /** The free car spots near {@code pos}, worked out again every {@link #REFRESH_TICKS} or when the player moves a block. */
    private static List<PlatformsNear.FreeSpot> free(ClientLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (now - freeAt >= REFRESH_TICKS || now < freeAt || !pos.equals(freeFrom)) {
            free = PlatformsNear.freeSpots(level, pos);
            freeAt = now;
            freeFrom = pos.immutable();
        }
        return free;
    }

    static void onMarker(PlacementPreviewEvent.Marker event) {
        if (!(event.getStack().getItem() instanceof HatchItem)) {
            return;
        }
        SubmitCustomGeometryEvent geometry = event.getGeometry();
        Vec3 camera = geometry.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = geometry.getPoseStack();
        for (PlatformsNear.FreeSpot spot : free(event.getLevel(), event.getPlayer().blockPosition())) {
            AABB box = spot.box();
            int colour = spot.leading() ? LEADING_COLOUR : COLOUR;
            VoxelShape outline = Shapes.create(box.move(-box.minX, -box.minY, -box.minZ).deflate(INSET));
            poseStack.pushPose();
            poseStack.translate(box.minX - camera.x(), box.minY - camera.y(), box.minZ - camera.z());
            geometry.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) ->
                    outline.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
                        Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1)).normalize();
                        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(colour).setNormal(pose, normal).setLineWidth(WIDTH);
                        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(colour).setNormal(pose, normal).setLineWidth(WIDTH);
                    }));
            poseStack.popPose();
        }
    }
}
