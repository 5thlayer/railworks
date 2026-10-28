// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.railworks.BurnerGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * A Burner Generator's menu as a furnace's: vanilla's furnace panel with its input, output and
 * arrow painted over, leaving the fuel slot, and the furnace's flame above it, burning down as the
 * fuel lit does. Placeholder.
 */
public final class BurnerGeneratorScreen extends AbstractContainerScreen<BurnerGeneratorMenu> {

    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier LIT_PROGRESS_SPRITE = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    /** The panel's own grey, to paint the furnace's other slots out. */
    private static final int PANEL = 0xFFC6C6C6;

    public BurnerGeneratorScreen(BurnerGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int xo = leftPos;
        int yo = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, xo, yo, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        // The input slot, the arrow and the output slot.
        graphics.fill(xo + 54, yo + 16, xo + 74, yo + 36, PANEL);
        graphics.fill(xo + 78, yo + 33, xo + 104, yo + 51, PANEL);
        graphics.fill(xo + 110, yo + 29, xo + 138, yo + 57, PANEL);
        float lit = menu.litProgress();
        if (lit > 0.0F) {
            int height = Mth.ceil(lit * 13.0F) + 1;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_PROGRESS_SPRITE, 14, 14, 0, 14 - height,
                    xo + 56, yo + 36 + 14 - height, 14, height);
        }
    }
}
