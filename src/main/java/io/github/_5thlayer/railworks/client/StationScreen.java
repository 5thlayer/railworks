// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.railworks.network.NameStationPacket;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A Station's menu: its name in a text box, sent to the server on Done or Enter, which cleans it and
 * checks the player can reach the Station. Placeholder; the Schedule (#26) lists Stations by these names.
 */
public final class StationScreen extends Screen {

    private static final Component NAME_LABEL = Component.translatable("menu.railworks.station.name");

    private final BlockPos pos;
    private final String name;
    private EditBox nameEdit;
    private Button done;

    StationScreen(BlockPos pos, String name) {
        super(Component.translatable("block.railworks.station"));
        this.pos = pos;
        this.name = name;
    }

    @Override
    protected void init() {
        String typed = nameEdit == null ? name : nameEdit.getValue();
        nameEdit = new EditBox(font, width / 2 - 100, 80, 200, 20, NAME_LABEL);
        nameEdit.setMaxLength(Station.NAME_LIMIT);
        nameEdit.setValue(typed);
        nameEdit.setResponder(value -> updateDone());
        addRenderableWidget(nameEdit);
        done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onDone())
                .bounds(width / 2 - 100, 112, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 2, 112, 98, 20).build());
        updateDone();
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(nameEdit);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (done.active && getFocused() == nameEdit && event.isConfirmation()) {
            onDone();
            return true;
        }
        return super.keyPressed(event);
    }

    private void updateDone() {
        done.active = Station.name(nameEdit.getValue()).isPresent();
    }

    private void onDone() {
        ClientPacketDistributor.sendToServer(new NameStationPacket(pos, nameEdit.getValue()));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, 40, -1);
        graphics.text(font, NAME_LABEL, width / 2 - 100 + 1, 68, -6250336);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
