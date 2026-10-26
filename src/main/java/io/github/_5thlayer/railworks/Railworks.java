// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.client.RailworksClient;
import io.github._5thlayer.railworks.network.NameStationPacket;
import io.github._5thlayer.railworks.network.ScheduleMenuPacket;
import io.github._5thlayer.railworks.network.SchedulePacket;
import io.github._5thlayer.railworks.network.SpansPacket;
import io.github._5thlayer.railworks.network.StationMenuPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The Module's entry point. It fills its part of the suite's Works tab. The game tests hook
 * themselves in from the {@code gametest} source set, which never ships (libworks ADR 0011).
 */
@Mod(Railworks.MOD_ID)
public final class Railworks {

    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MOD_ID = "railworks";

    private static final String NETWORK_VERSION = "2";

    public Railworks(IEventBus modBus, ModContainer container) {
        RailworksConfig.register(container, modBus);
        RailworksRegistries.register(modBus);
        modBus.addListener(WorksTabContents::add);
        modBus.addListener(Railworks::registerPayloads);
        modBus.addListener(Railworks::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(LevelRails::onChunkSent);
        NeoForge.EVENT_BUS.addListener(SpanGesture::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(SpanGesture::onPlayerTick);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            RailworksClient.register(modBus);
        }
    }

    /** A Cargo car's cargo to hoppers and pipes, only while it is stopped at a Station (ADR 0007). */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.Item.ENTITY_AUTOMATION, RailworksRegistries.CARGO_CAR_ENTITY.get(),
                (car, side) -> car.automation());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION)
                .playToClient(SpansPacket.TYPE, SpansPacket.STREAM_CODEC, SpansPacket::handle)
                .playToClient(StationMenuPacket.TYPE, StationMenuPacket.STREAM_CODEC, StationMenuPacket::handle)
                .playToClient(ScheduleMenuPacket.TYPE, ScheduleMenuPacket.STREAM_CODEC, ScheduleMenuPacket::handle)
                .playToServer(NameStationPacket.TYPE, NameStationPacket.STREAM_CODEC, NameStationPacket::handle)
                .playToServer(SchedulePacket.TYPE, SchedulePacket.STREAM_CODEC, SchedulePacket::handle);
    }
}
