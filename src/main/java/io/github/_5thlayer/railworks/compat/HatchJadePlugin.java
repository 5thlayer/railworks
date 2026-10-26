// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import io.github._5thlayer.libworks.machine.jade.MachineReport;
import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchBlockEntity;
import io.github._5thlayer.railworks.HatchReport;
import io.github._5thlayer.railworks.Railworks;
import net.minecraft.resources.Identifier;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * A hatch in Jade: whether it is a provider or a requester, what a requester asks for, and the
 * Station and car spot it joins, or that it joins none, read on the server as a {@link HatchReport}.
 * What it holds Jade lists itself, from the hatch's item handler. Found by Jade's annotation scan and
 * named nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public class HatchJadePlugin implements IWailaPlugin {

    private static final MachineReport<HatchBlockEntity, HatchReport> HATCH = new MachineReport<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "hatch"), HatchBlockEntity.class, HatchReport::of,
            HatchReport.STREAM_CODEC);

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HATCH, HatchBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new MachineReport.Client<>(HATCH,
                (report, tooltip) -> report.lines().forEach(tooltip::add)), HatchBlock.class);
    }
}
