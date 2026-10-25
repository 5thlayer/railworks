// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.railworks.LocomotiveMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * A Burner Locomotive's menu as a plain container: vanilla's one-row chest panel with the row's
 * other slots painted over, leaving the fuel slot where the menu puts it. Placeholder; the
 * Schedule (#3) adds its widgets here.
 */
public final class LocomotiveScreen extends AbstractContainerScreen<LocomotiveMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    /** The panel's own grey, to paint the chest row's unused slots out. */
    private static final int PANEL = 0xFFC6C6C6;
    private static final int FUEL_COLUMN = 4;

    public LocomotiveScreen(LocomotiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, this.imageWidth, 18 + 17, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
        for (int column = 0; column < 9; column++) {
            if (column != FUEL_COLUMN) {
                graphics.fill(left + 7 + column * 18, top + 17, left + 7 + (column + 1) * 18, top + 17 + 18, PANEL);
            }
        }
    }
}
