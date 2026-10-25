// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import io.github._5thlayer.railworks.RailworksRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client half of the Rail: the Rail between Arms. Called only on the client, from {@code Railworks}. */
public final class RailworksClient {

    private RailworksClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RailworksClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(ClientRails::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(PreviewSpan::onOverlay);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RailworksRegistries.LOCOMOTIVE_ENTITY.get(), LocomotiveRenderer::new);
        event.registerBlockEntityRenderer(RailworksRegistries.PYLON_ENTITY.get(), context -> new SpanRenderer());
    }
}
