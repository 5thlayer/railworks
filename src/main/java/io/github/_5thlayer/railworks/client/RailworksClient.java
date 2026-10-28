// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.client;

import com.google.common.reflect.TypeToken;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.RailworksRegistries;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client half of the Rail: the Rail between Arms, and the item each hatch shows. Called only on the client, from {@code Railworks}. */
public final class RailworksClient {

    private RailworksClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RailworksClient::registerRenderers);
        modBus.addListener(RailworksClient::registerScreens);
        modBus.addListener(RailworksClient::registerRenderStateModifiers);
        NeoForge.EVENT_BUS.addListener(ClientRails::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(PreviewSpan::onOverlay);
        NeoForge.EVENT_BUS.addListener(HatchSpots::onMarker);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(RailworksRegistries.LOCOMOTIVE_MENU.get(), LocomotiveScreen::new);
        event.register(RailworksRegistries.ELECTRIC_LOCOMOTIVE_MENU.get(), LocomotiveScreen::new);
        event.register(RailworksRegistries.BURNER_GENERATOR_MENU.get(), BurnerGeneratorScreen::new);
    }

    /** A player or a humanoid mob in a Locomotive's bay stands there, drawn standing rather than seated. */
    private static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {
        }, (LivingEntity entity, LivingEntityRenderState state) -> {
            if (state instanceof HumanoidRenderState humanoid && CarEntity.standsInBay(entity)) {
                humanoid.isPassenger = false;
            }
        });
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RailworksRegistries.LOCOMOTIVE_ENTITY.get(), CarRenderer::locomotive);
        event.registerEntityRenderer(RailworksRegistries.ELECTRIC_LOCOMOTIVE_ENTITY.get(), CarRenderer::electric);
        event.registerEntityRenderer(RailworksRegistries.CARGO_CAR_ENTITY.get(), CarRenderer::cargo);
        event.registerEntityRenderer(RailworksRegistries.FLUID_CAR_ENTITY.get(), CarRenderer::fluid);
        event.registerBlockEntityRenderer(RailworksRegistries.PYLON_ENTITY.get(), context -> new SpanRenderer());
        event.registerBlockEntityRenderer(RailworksRegistries.HATCH_ENTITY.get(), context -> new HatchRenderer());
    }
}
