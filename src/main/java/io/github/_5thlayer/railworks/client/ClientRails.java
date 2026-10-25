// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import java.util.List;

import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The Spans a client has been sent, which is what the girder renderer draws. One network for the one
 * level a client has open, emptied when that level unloads. Holds no Minecraft client types, so the
 * common packet can hand to it.
 */
public final class ClientRails {

    private static RailNetwork network = new RailNetwork();

    private ClientRails() {
    }

    public static void accept(int chunkX, int chunkZ, List<Span> sent) {
        network.replaceTouching(chunkX, chunkZ, sent);
    }

    public static RailNetwork network() {
        return network;
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            network = new RailNetwork();
        }
    }
}
