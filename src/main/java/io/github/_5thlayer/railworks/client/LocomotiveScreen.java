// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.LocomotiveMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive's menu as a plain container: vanilla's one-row chest panel with the row's
 * other slots painted over, leaving the fuel slot where the menu puts it, an Automatic toggle for
 * its Train beside it, and the Train's status line under the row. The mode and the status line are
 * the Locomotive entity's synched data, so they follow the server while the menu is open.
 * Placeholder.
 */
public final class LocomotiveScreen extends AbstractContainerScreen<LocomotiveMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    /** The panel's own grey, to paint the chest row's unused slots out. */
    private static final int PANEL = 0xFFC6C6C6;
    private static final int FUEL_COLUMN = 4;
    /** The status line's grey, a label's. */
    private static final int LABEL = 0xFF404040;

    private Button automatic;

    public LocomotiveScreen(LocomotiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + 18 + 12);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // Over the painted-out slots right of the fuel slot.
        automatic = addRenderableWidget(Button.builder(label(), button -> toggle())
                .bounds(leftPos + 7 + (FUEL_COLUMN + 1) * 18 + 2, topPos + 17, 4 * 18 - 4, 18).build());
    }

    /** The Locomotive, as this client sees it; null once it is out of sight. */
    private @Nullable LocomotiveEntity car() {
        return minecraft.level != null && minecraft.level.getEntity(menu.carId()) instanceof LocomotiveEntity car ? car : null;
    }

    private Component label() {
        LocomotiveEntity car = car();
        return Component.translatable(car != null && car.automaticSeen()
                ? "menu.railworks.locomotive.automatic_on" : "menu.railworks.locomotive.automatic_off");
    }

    private void toggle() {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LocomotiveMenu.TOGGLE_AUTOMATIC);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        automatic.setMessage(label());
        automatic.active = car() != null;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        super.extractLabels(graphics, xm, ym);
        LocomotiveEntity car = car();
        if (car != null) {
            graphics.text(font, car.statusSeen(), 8, 17 + 18 + 3, LABEL, false);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, this.imageWidth, 18 + 17, 256, 256);
        // A strip of the panel's own grey under the row, for the status line.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + 18 + 17, 0.0F, 4.0F, this.imageWidth, 12, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + 18 + 17 + 12, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
        for (int column = 0; column < 9; column++) {
            if (column != FUEL_COLUMN) {
                graphics.fill(left + 7 + column * 18, top + 17, left + 7 + (column + 1) * 18, top + 17 + 18, PANEL);
            }
        }
    }
}
