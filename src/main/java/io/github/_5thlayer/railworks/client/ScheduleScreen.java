// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.railworks.LocomotiveMenu;
import io.github._5thlayer.railworks.network.SchedulePacket;
import io.github._5thlayer.railworks.rail.Schedule;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A Train's Schedule, opened from its Locomotive's menu: a row per Station, its name typed in a
 * box and when to leave it cycled by a button, with seconds for a timed wait; a row added or
 * removed; and whether the Train runs on it. Done sends it all to the server, which cleans the names
 * and checks the player can reach the Locomotive. The level's Station names are listed to copy from.
 * Placeholder.
 */
public final class ScheduleScreen extends Screen {

    private static final Component STATIONS_LABEL = Component.translatable("menu.railworks.schedule.stations");
    private static final int ROW = 22;
    private static final int WIDTH = 300;

    private final int car;
    private final List<String> stations;
    /** The rows as edited, kept across a resize, which rebuilds the widgets. */
    private final List<Row> rows = new ArrayList<>();
    private boolean automatic;

    /** One row as it is being edited. */
    private static final class Row {
        String station;
        Schedule.Wait until;
        String seconds;

        Row(Schedule.Entry entry) {
            this.station = entry.station();
            this.until = entry.until();
            this.seconds = Integer.toString(entry.seconds());
        }

        Schedule.Entry entry() {
            int parsed;
            try {
                parsed = Integer.parseInt(seconds.strip());
            } catch (NumberFormatException e) {
                parsed = Schedule.DEFAULT_SECONDS;
            }
            return new Schedule.Entry(station, until, parsed);
        }
    }

    ScheduleScreen(LocomotiveMenu.Opened opened) {
        super(Component.translatable("menu.railworks.schedule"));
        this.car = opened.car();
        this.stations = opened.stations();
        this.automatic = opened.automatic();
        opened.entries().forEach(entry -> rows.add(new Row(entry)));
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = 30;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = top + i * ROW;
            EditBox name = new EditBox(font, left, y, 150, 20, Component.translatable("menu.railworks.schedule.station"));
            name.setMaxLength(Station.NAME_LIMIT);
            name.setValue(row.station);
            name.setResponder(value -> row.station = value);
            addRenderableWidget(name);
            addRenderableWidget(Button.builder(waitLabel(row.until), button -> {
                row.until = row.until.next();
                rebuildWidgets();
            }).bounds(left + 154, y, 80, 20).build());
            if (row.until == Schedule.Wait.TIME) {
                EditBox seconds = new EditBox(font, left + 238, y, 36, 20, Component.translatable("menu.railworks.schedule.seconds"));
                seconds.setMaxLength(4);
                seconds.setFilter(text -> text.chars().allMatch(Character::isDigit));
                seconds.setValue(row.seconds);
                seconds.setResponder(value -> row.seconds = value);
                addRenderableWidget(seconds);
            }
            addRenderableWidget(Button.builder(Component.literal("x"), button -> {
                rows.remove(row);
                rebuildWidgets();
            }).bounds(left + WIDTH - 20, y, 20, 20).build());
        }
        int below = top + Math.max(rows.size(), 1) * ROW + 4;
        Button add = addRenderableWidget(Button.builder(Component.translatable("menu.railworks.schedule.add"), button -> {
            rows.add(new Row(Schedule.Entry.of(stations.isEmpty() ? Station.DEFAULT_NAME : stations.getFirst(),
                    Schedule.Wait.TIME)));
            rebuildWidgets();
        }).bounds(left, below, 98, 20).build());
        add.active = rows.size() < Schedule.MAX_ENTRIES;
        addRenderableWidget(Button.builder(automaticLabel(), button -> {
            automatic = !automatic;
            button.setMessage(automaticLabel());
        }).bounds(left + 102, below, 196, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onDone())
                .bounds(width / 2 - 100, below + 48, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(width / 2 + 2, below + 48, 98, 20).build());
    }

    private static Component waitLabel(Schedule.Wait until) {
        return Component.translatable("menu.railworks.schedule.wait." + until.serializedName());
    }

    private Component automaticLabel() {
        return Component.translatable(automatic ? "menu.railworks.schedule.automatic" : "menu.railworks.schedule.manual");
    }

    private void onDone() {
        ClientPacketDistributor.sendToServer(new SchedulePacket(car, rows.stream().map(Row::entry).toList(), automatic));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(font, title, width / 2, 12, -1);
        int left = (width - WIDTH) / 2;
        int below = 30 + Math.max(rows.size(), 1) * ROW + 4;
        if (rows.isEmpty()) {
            graphics.text(font, Component.translatable("menu.railworks.schedule.empty"), left + 1, 36, -6250336);
        }
        // The names to copy from, as many as fit on a line.
        String names = stations.isEmpty() ? "-" : String.join(", ", stations);
        graphics.text(font, STATIONS_LABEL.copy().append(" ").append(font.plainSubstrByWidth(names,
                WIDTH - font.width(STATIONS_LABEL) - 6)), left + 1, below + 30, -6250336);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
