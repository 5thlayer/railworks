// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.List;
import java.util.Locale;

import io.github._5thlayer.railworks.rail.Logistics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;

/**
 * A Train's status line as a player reads it, under the Locomotive menu's Automatic toggle and in
 * Jade: "Manual", "Waiting for a job", "To X to load", "Loading at X", "To X to unload: 6 stacks of
 * iron ore, 2 of coal", "Unloading at X" or "Cannot reach X".
 */
public final class StatusLine {

    private StatusLine() {
    }

    public static Component of(Logistics.Status<Item> status) {
        String key = "status.railworks.train." + status.kind().name().toLowerCase(Locale.ROOT);
        if (status.kind() == Logistics.Kind.TO_UNLOAD) {
            return Component.translatable(key, status.station(), cargo(status.cargo()));
        }
        return status.station().isEmpty() ? Component.translatable(key) : Component.translatable(key, status.station());
    }

    /** "6 stacks of iron ore, 2 of coal": the first item with its stacks named, the rest with only their count. */
    private static Component cargo(List<Logistics.Cargo<Item>> cargo) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < cargo.size(); i++) {
            Logistics.Cargo<Item> load = cargo.get(i);
            Component name = load.item().getName(load.item().getDefaultInstance());
            String key = i > 0 ? "status.railworks.cargo.more"
                    : load.stacks() == 1 ? "status.railworks.cargo.stack" : "status.railworks.cargo.stacks";
            if (i > 0) {
                line.append(Component.translatable("status.railworks.cargo.separator"));
            }
            line.append(Component.translatable(key, load.stacks(), name));
        }
        return line;
    }
}
