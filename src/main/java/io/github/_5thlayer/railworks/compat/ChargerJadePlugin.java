// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import io.github._5thlayer.libworks.machine.jade.MachineReport;
import io.github._5thlayer.railworks.ChargerBlock;
import io.github._5thlayer.railworks.ChargerBlockEntity;
import io.github._5thlayer.railworks.ChargerReport;
import io.github._5thlayer.railworks.Railworks;
import net.minecraft.resources.Identifier;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * A Charger in Jade: the Station and car spot it joins, and whether it stands beside the Engine room
 * of a Locomotive standing there, the one block it charges from, or that it joins none, read on the
 * server as a {@link ChargerReport}. What it holds Jade shows itself, from its energy handler. Found by
 * Jade's annotation scan and named nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public class ChargerJadePlugin implements IWailaPlugin {

    private static final MachineReport<ChargerBlockEntity, ChargerReport> CHARGER = new MachineReport<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "charger"), ChargerBlockEntity.class, ChargerReport::of,
            ChargerReport.STREAM_CODEC);

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CHARGER, ChargerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new MachineReport.Client<>(CHARGER,
                (report, tooltip) -> report.lines().forEach(tooltip::add)), ChargerBlock.class);
    }
}
