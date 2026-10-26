// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import io.github._5thlayer.libworks.machine.jade.MachineReport;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.StationBlock;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.client.ClientRails;
import io.github._5thlayer.railworks.rail.StraightLeg;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * A Station in Jade: its name, the way the Trains it serves head, from the client's Spans and the
 * drive side the server sent, and, when no Span is in reach of it, that it serves no Rail, or when
 * its stretch of Rail is not a straight leg, that a Cargo car standing by it will not load, though cars of the same Train elsewhere may. The name
 * is the block entity's, which the server sends the client, and the Span is asked of the client's
 * Spans, so no server data provider is needed. Found by Jade's annotation scan and named nowhere
 * else, so Jade stays a soft dependency. How many hatches joined it the server counts.
 */
@WailaPlugin
public class StationJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "station");

    /** How many hatches joined it, counted on the server, where the level's hatches are. */
    private static final MachineReport<StationBlockEntity, Integer> HATCHES = new MachineReport<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "station_hatches"), StationBlockEntity.class,
            StationBlockEntity::hatches, ByteBufCodecs.VAR_INT.cast());

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HATCHES, StationBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new Provider(), StationBlock.class);
        registration.registerBlockComponent(new MachineReport.Client<>(HATCHES, (count, tooltip) ->
                tooltip.add(Component.translatable("tooltip.railworks.station.jade.hatches", count))), StationBlock.class);
    }

    private static final class Provider implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof StationBlockEntity station)) {
                return;
            }
            tooltip.add(Component.translatable("tooltip.railworks.station.jade.name", station.name()));
            var network = ClientRails.network();
            var foot = station.station().foot(network);
            if (foot.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.railworks.station.jade.off_rail"));
                return;
            }
            StationBlock.serving(network, accessor.getPosition()).ifPresentOrElse(
                    way -> tooltip.add(Component.translatable("tooltip.railworks.station.jade.serves",
                            Component.translatable("tooltip.railworks.direction." + way.getSerializedName()))),
                    () -> tooltip.add(Component.translatable("tooltip.railworks.station.jade.serves_neither")));
            if (!StraightLeg.at(network, station.station())) {
                tooltip.add(Component.translatable("tooltip.railworks.station.jade.not_straight"));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
