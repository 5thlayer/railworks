// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.railworks.LocomotiveMenu;
import net.minecraft.client.Minecraft;

/** Where the common packet hands a Locomotive's menu its Schedule, so it names no client class but this. Client only. */
public final class ClientSchedules {

    private ClientSchedules() {
    }

    /** The menu with {@code containerId}, if it is still the one open, takes what the server sent. */
    public static void opened(int containerId, LocomotiveMenu.Opened opened) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof LocomotiveMenu menu && menu.containerId == containerId) {
            menu.opened(opened);
        }
    }
}
