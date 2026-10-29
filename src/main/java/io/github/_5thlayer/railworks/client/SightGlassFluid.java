// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * The fluid a Sight glass shows, a Fluid car's or a Fluid hatch's: the fluid's own still texture and
 * tint, from its fluid model as the world draws it, so any mod's fluid looks right and an animated one
 * animates, and a fluid that gives off light drawn at its own light level.
 *
 * @param sprite its still texture
 * @param tint its tint, ARGB, opaque
 * @param lightLevel the light it gives off, 0 for none
 */
record SightGlassFluid(TextureAtlasSprite sprite, int tint, int lightLevel) {

    /** How far off the glass it is drawn, so it and the dark glass never share a plane. */
    static final float PROUD = 0.002F;

    /** How {@code held} looks, or null for none. */
    static @Nullable SightGlassFluid of(FluidStack held) {
        if (held.isEmpty()) {
            return null;
        }
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(held.getFluid().defaultFluidState());
        FluidTintSource tint = model.fluidTintSource();
        // Opaque, as the world draws a fluid's tint: the sprite's own alpha is its translucency.
        return new SightGlassFluid(model.stillMaterial().sprite(), tint == null ? -1 : 0xFF000000 | tint.colorAsStack(held),
                held.getFluidType().getLightLevel(held));
    }

    /** What it is drawn with. */
    RenderType renderType() {
        return RenderTypes.entityTranslucent(sprite.atlasLocation());
    }

    /** The light it is drawn at where {@code light} falls: at least its own. */
    int light(int light) {
        return LightCoordsUtil.lightCoordsWithEmission(light, lightLevel);
    }

    /**
     * One corner of a quad of it, at {@code u} across and {@code v} down its sprite, each from 0 to 1,
     * facing along the normal.
     */
    void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int light,
                float normalX, float normalY, float normalZ) {
        buffer.addVertex(pose, x, y, z).setColor(tint).setUv(sprite.getU(u), sprite.getV(v)).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, normalX, normalY, normalZ);
    }
}
