// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.libworks.WorksTab;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Every item of the Module, in the suite's Works tab and no other (FactoryWorks ADR-0128). One
 * listener, in the order the tab shows them, which {@code CreativeTabTests} lists too.
 */
final class WorksTabContents {

    private WorksTabContents() {
    }

    static void add(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != WorksTab.KEY) {
            return;
        }
        event.accept(RailworksRegistries.PYLON_ITEM.get());
        event.accept(RailworksRegistries.RAIL.get());
        event.accept(RailworksRegistries.LOCOMOTIVE.get());
        event.accept(RailworksRegistries.ELECTRIC_LOCOMOTIVE.get());
        event.accept(RailworksRegistries.CARGO_CAR.get());
        event.accept(RailworksRegistries.STATION_ITEM.get());
        event.accept(RailworksRegistries.HATCH_ITEM.get());
        event.accept(RailworksRegistries.REQUESTER_HATCH_ITEM.get());
        event.accept(RailworksRegistries.CHARGER_ITEM.get());
        // No recipe makes it: the Works tab, in creative, is the one way to get it.
        event.accept(RailworksRegistries.CREATIVE_CHARGER_ITEM.get());
        event.accept(RailworksRegistries.BURNER_GENERATOR_ITEM.get());
    }
}
