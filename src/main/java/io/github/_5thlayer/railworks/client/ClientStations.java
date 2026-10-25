// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Where the common packet opens a Station's menu, so it names no screen class itself. Client only. */
public final class ClientStations {

    private ClientStations() {
    }

    public static void openMenu(BlockPos pos, String name) {
        Minecraft.getInstance().setScreen(new StationScreen(pos, name));
    }
}
