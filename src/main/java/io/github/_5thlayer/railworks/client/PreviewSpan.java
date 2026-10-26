// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.client.PlacementPreviewEvent;
import io.github._5thlayer.railworks.PylonBlock;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.PlacedColumn;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.RailCurve;
import io.github._5thlayer.railworks.rail.Stringing;
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
 * the placement will not make, along the curve it would take. Faded, like the Rail it promises; where
 * the grade or bend limit refuses the Span it would have strung, that Span in red.
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
        if (choice.isPresent()) {
            draw(event, pos, choice.get().target(), FADE, new float[] {SpanRenderer.R, SpanRenderer.G, SpanRenderer.B});
            return;
        }
        // Nothing would string: the Span the grade or bend limit refused shows red, as the held Rail's slack does.
        PlacedColumn.refusedString(level, pos, player, ClientRails.network())
                .ifPresent(target -> draw(event, pos, target, 1.0F, SpanRenderer.colourOf(Stringing.Tint.REFUSED)));
    }

    private static void draw(PlacementPreviewEvent.Overlay event, BlockPos pos, Arm target, float fade, float[] colour) {
        ClientLevel level = event.getLevel();
        SubmitCustomGeometryEvent geometry = event.getGeometry();
        Vec3 camera = geometry.getLevelRenderState().cameraRenderState.pos;
        // The curve the Span would take, bending into the Span already at its target.
        SpanRenderer.Sweep sweep = SpanRenderer.sweep(RailCurve.between(ClientRails.network(), PylonColumn.arm(pos), target),
                camera);
        int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
        geometry.getSubmitNodeCollector().submitCustomGeometry(geometry.getPoseStack(), RenderTypes.leash(),
                (pose, buffer) -> SpanRenderer.drawCurve(pose.pose(), buffer, sweep, light, fade,
                        colour[0], colour[1], colour[2]));
    }
}
