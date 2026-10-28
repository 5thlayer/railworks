// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.LocomotiveMenu;
import io.github._5thlayer.railworks.StatusLine;
import io.github._5thlayer.railworks.rail.Locomotive;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import java.text.NumberFormat;
import java.util.Locale;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive's menu as a plain container: vanilla's one-row chest panel with the row's
 * other slots painted over, leaving the fuel slots where the menu puts them, an Automatic toggle for
 * its Train beside them, and the Train's status line under the row, the items it names drawn after
 * its words as icons with their counts. The mode and the status line are the Locomotive entity's
 * synched data, so they follow the server while the menu is open.
 *
 * <p>An Electric Locomotive's menu draws a charge gauge across the fuel slots' columns instead, the
 * share of its capacity its charge holds, and names both in FE when hovered.
 * Placeholder.
 */
public final class LocomotiveScreen extends AbstractContainerScreen<LocomotiveMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    /** The panel's own grey, to paint the chest row's unused slots out. */
    private static final int PANEL = 0xFFC6C6C6;
    /** How many slots the chest row has. */
    private static final int ROW_SLOTS = 9;
    /** The chest row's columns the fuel slots take, from the first. */
    private static final int FUEL_COLUMNS = Locomotive.FUEL_SLOTS;
    /** The status line's grey, a label's. */
    private static final int LABEL = 0xFF404040;
    /** The strip under the row the status line takes, as tall as an item icon and its margin. */
    private static final int STATUS_HEIGHT = 18;
    /** The status line's top, under the row. */
    private static final int STATUS_TOP = 17 + 18;
    /** The charge gauge's frame, its empty part and its charge. */
    private static final int GAUGE_FRAME = 0xFF373737;
    private static final int GAUGE_EMPTY = 0xFF8B8B8B;
    private static final int GAUGE_CHARGE = 0xFFD8913A;

    private Button automatic;

    public LocomotiveScreen(LocomotiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + 18 + STATUS_HEIGHT);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // Over the painted-out slots right of the fuel slots, a column clear of them.
        automatic = addRenderableWidget(Button.builder(label(), button -> toggle())
                .bounds(leftPos + 7 + (FUEL_COLUMNS + 1) * 18 + 2, topPos + 17, (ROW_SLOTS - 1 - FUEL_COLUMNS) * 18 - 4, 18).build());
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
        if (car == null) {
            return;
        }
        if (menu.electric()) {
            int percent = (int) Math.floor(100.0 * share(car));
            graphics.centeredText(font, percent + "%", 7 + FUEL_COLUMNS * 9, 17 + (18 - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
        }
        Component status = car.statusSeen();
        int textY = STATUS_TOP + (STATUS_HEIGHT - font.lineHeight) / 2 + 1;
        graphics.text(font, status, 8, textY, LABEL, false);
        int x = 8 + font.width(status) + 2;
        for (ItemStack icon : car.statusIconsSeen()) {
            graphics.item(icon, x, STATUS_TOP + 1);
            graphics.itemDecorations(font, icon, x, STATUS_TOP + 1, StatusLine.amount(icon.getCount()));
            x += 18;
        }
        if (car.statusMoreSeen() > 0) {
            graphics.text(font, StatusLine.more(car.statusMoreSeen()), x, textY, LABEL, false);
        }
    }

    /** What share of its capacity an Electric Locomotive's charge holds, 0 to 1. */
    private static double share(LocomotiveEntity car) {
        long capacity = car.chargeCapacitySeen();
        return capacity <= 0L ? 0.0 : Math.clamp((double) car.chargeSeen() / capacity, 0.0, 1.0);
    }

    /** Hovering an Electric Locomotive's gauge names its charge and capacity in FE. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        LocomotiveEntity car = car();
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (menu.electric() && car != null && x >= 7 && x < 7 + FUEL_COLUMNS * 18 && y >= 17 && y < 17 + 18) {
            NumberFormat fe = NumberFormat.getIntegerInstance(Locale.ROOT);
            graphics.setTooltipForNextFrame(Component.translatable("menu.railworks.locomotive.charge",
                    fe.format(car.chargeSeen()), fe.format(car.chargeCapacitySeen())), mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, this.imageWidth, 18 + 17, 256, 256);
        // A strip of the panel's own grey under the row, for the status line, from the title's
        // plain stretch, which is shorter than the strip, so twice.
        int half = STATUS_HEIGHT / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + STATUS_TOP, 0.0F, 4.0F, this.imageWidth, half, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + STATUS_TOP + half, 0.0F, 4.0F, this.imageWidth, STATUS_HEIGHT - half, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + STATUS_TOP + STATUS_HEIGHT, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
        for (int column = 0; column < ROW_SLOTS; column++) {
            if (column >= FUEL_COLUMNS || menu.electric()) {
                graphics.fill(left + 7 + column * 18, top + 17, left + 7 + (column + 1) * 18, top + 17 + 18, PANEL);
            }
        }
        LocomotiveEntity car = car();
        if (menu.electric() && car != null) {
            int x0 = left + 7;
            int y0 = top + 17;
            int x1 = x0 + FUEL_COLUMNS * 18;
            int y1 = y0 + 18;
            graphics.fill(x0, y0, x1, y1, GAUGE_FRAME);
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GAUGE_EMPTY);
            int filled = (int) Math.round((x1 - x0 - 2) * share(car));
            graphics.fill(x0 + 1, y0 + 1, x0 + 1 + filled, y1 - 1, GAUGE_CHARGE);
        }
    }
}
