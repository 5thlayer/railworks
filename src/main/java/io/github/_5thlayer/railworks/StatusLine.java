// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github._5thlayer.railworks.rail.Logistics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A Train's status line as a player reads it, under the Locomotive menu's Automatic toggle and in
 * Jade: "Manual", "Waiting for a job", "To X to load", "Loading at X", "To X to unload", "Unloading
 * at X", "Cannot reach X" or "Nowhere to deliver". Heading to unload or with nowhere to deliver, the
 * items it carries follow the words as icons with their counts, a fluid as its filled bucket with its
 * count in buckets, the first {@link #ICONS} of them and then "+M" for the rest.
 */
public final class StatusLine {

    /** How many items the line shows as icons. */
    public static final int ICONS = 4;

    private StatusLine() {
    }

    /** The line's words. */
    public static Component of(Logistics.Status<Ware> status) {
        String key = "status.railworks.train." + status.kind().name().toLowerCase(Locale.ROOT);
        return status.station().isEmpty() ? Component.translatable(key) : Component.translatable(key, status.station());
    }

    /**
     * The first {@link #ICONS} items and fluids the line names: an item as a stack of its count, however
     * many stacks that is, and a fluid as its filled bucket, so many as its buckets.
     */
    public static List<ItemStack> icons(Logistics.Status<Ware> status) {
        List<ItemStack> icons = new ArrayList<>();
        for (Logistics.Cargo<Ware> load : status.cargo().subList(0, Math.min(ICONS, status.cargo().size()))) {
            icons.add(load.item().icon(load.count()));
        }
        return icons;
    }

    /** How many items the line names beyond its icons. */
    public static int beyondIcons(Logistics.Status<Ware> status) {
        return Math.max(0, status.cargo().size() - ICONS);
    }

    /** "+2", after the icons for the items beyond them. */
    public static Component more(int more) {
        return Component.translatable("status.railworks.cargo.more", more);
    }

    /** An icon's count as it fits on the icon, three figures at most and rounded down: "64", "1.5k", "12M", "2.1G". */
    public static String amount(int count) {
        if (count < 1_000) {
            return Integer.toString(count);
        }
        String[] units = {"k", "M", "G"};
        int unit = count < 1_000_000 ? 0 : count < 1_000_000_000 ? 1 : 2;
        double value = count / Math.pow(1_000, unit + 1);
        return (value < 10 ? String.format(Locale.ROOT, "%.1f", Math.floor(value * 10) / 10).replace(".0", "")
                : Integer.toString((int) value)) + units[unit];
    }
}
