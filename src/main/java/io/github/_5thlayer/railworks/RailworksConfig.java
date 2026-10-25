// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.SpanRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code railworks-server.toml}: the longest Span. A server config, so the client measures the same
 * limit the server strings by.
 */
public final class RailworksConfig {

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue SPAN_LENGTH_LIMIT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SPAN_LENGTH_LIMIT = builder.comment("The longest Span, in blocks: the straight distance between its two Arms, which its Rail cost rounds up.")
                .defineInRange("spanLengthLimit", SpanRules.DEFAULT_LENGTH_LIMIT, 1.0, 256.0);
        SPEC = builder.build();
    }

    private RailworksConfig() {
    }

    static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, event -> apply(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> apply(event.getConfig()));
        modBus.addListener(ModConfigEvent.Unloading.class, event -> {
            if (event.getConfig().getSpec() == SPEC) {
                SpanRules.configure(SpanRules.DEFAULT_LENGTH_LIMIT);
            }
        });
    }

    private static void apply(ModConfig config) {
        if (config.getSpec() == SPEC) {
            SpanRules.configure(SPAN_LENGTH_LIMIT.get());
        }
    }
}
