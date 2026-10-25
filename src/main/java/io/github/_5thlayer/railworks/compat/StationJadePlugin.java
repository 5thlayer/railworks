// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.StationBlock;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.client.ClientRails;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * A Station in Jade: its name and, when no Span is in reach of it, that it serves no Rail. The name
 * is the block entity's, which the server sends the client, and the Span is asked of the client's
 * Spans, so no server data provider is needed. Found by Jade's annotation scan and named nowhere
 * else, so Jade stays a soft dependency.
 */
@WailaPlugin
public class StationJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "station");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new Provider(), StationBlock.class);
    }

    private static final class Provider implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof StationBlockEntity station)) {
                return;
            }
            tooltip.add(Component.translatable("tooltip.railworks.station.jade.name", station.name()));
            if (station.station().foot(ClientRails.network()).isEmpty()) {
                tooltip.add(Component.translatable("tooltip.railworks.station.jade.off_rail"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
