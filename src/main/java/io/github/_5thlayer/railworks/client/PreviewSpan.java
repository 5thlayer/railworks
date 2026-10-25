// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.client.PlacementPreviewEvent;
import io.github._5thlayer.railworks.PylonBlock;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.PlacedColumn;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The Span a held Pylon would string, drawn with its Placement Preview: the server's own
 * {@link PlacedColumn#autoString} asked of the previewed spot, answered entirely on the client from its
 * block entities, {@link ClientRails} and the player's inventory, so the preview cannot promise a Span
 * the placement will not make. Faded, like the Rail it promises.
 */
final class PreviewSpan {

    private static final float FADE = 0.5F;

    private PreviewSpan() {
    }

    static void onOverlay(PlacementPreviewEvent.Overlay event) {
        PlacementPlan plan = event.getPlan();
        // A refused plan puts nothing down, and a replace keeps the column it has.
        if (plan.isRefused() || plan.isReplace() || plan.blocks().size() != 1
                || !(plan.blocks().getFirst().state().getBlock() instanceof PylonBlock)) {
            return;
        }
        BlockPos pos = plan.blocks().getFirst().pos();
        ClientLevel level = event.getLevel();
        LocalPlayer player = event.getPlayer();
        var choice = PlacedColumn.autoString(level, pos, player, ClientRails.network());
        if (choice.isEmpty()) {
            return;
        }
        SubmitCustomGeometryEvent geometry = event.getGeometry();
        Vec3 camera = geometry.getLevelRenderState().cameraRenderState.pos;
        Vec3 start = SpanRenderer.attachPoint(pos).subtract(camera);
        Vec3 end = SpanRenderer.attachPoint(PylonColumn.block(choice.get().target())).subtract(camera);
        int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
        geometry.getSubmitNodeCollector().submitCustomGeometry(geometry.getPoseStack(), RenderTypes.leash(),
                (pose, buffer) -> SpanRenderer.drawSpan(pose.pose(), buffer, start, end, light, FADE,
                        SpanRenderer.R, SpanRenderer.G, SpanRenderer.B));
    }
}
