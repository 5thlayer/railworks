// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import java.util.Locale;

import io.github._5thlayer.railworks.PylonBlock;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailPurse;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.SpanGesture;
import io.github._5thlayer.railworks.client.ClientRails;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * A Pylon in Jade: the length of each Span at its column's Arm and, while the player's Rail holds a
 * pending Span, why the Span to this Arm would be refused. A Span is neither a block nor an entity,
 * so the Pylon carries it. Read from the client's Spans and asked of {@link Stringing#decide}, the
 * click's own rule, so no server data provider is needed. Found by Jade's annotation scan and named
 * nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public class PylonJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "pylon");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new Provider(), PylonBlock.class);
    }

    private static final class Provider implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            Arm arm = PylonColumn.arm(PylonColumn.topOf(accessor.getLevel(), accessor.getPosition()));
            for (Span span : ClientRails.network().spansAt(arm)) {
                tooltip.add(Component.translatable("tooltip.railworks.pylon.jade.span",
                        String.format(Locale.ROOT, "%.1f", span.length())));
            }
            Player player = accessor.getPlayer();
            GlobalPos pending = player.getMainHandItem().get(RailworksRegistries.PENDING_SPAN.get());
            if (pending == null || !pending.dimension().equals(accessor.getLevel().dimension())) {
                return;
            }
            Arm anchor = PylonColumn.arm(pending.pos());
            Stringing.Result result = Stringing.decide(ClientRails.network(), anchor, arm, RailPurse.count(player)).result();
            if (Stringing.refused(result)) {
                tooltip.add(Component.translatable("tooltip.railworks.pylon.jade.refused",
                        SpanGesture.reason(result, anchor, arm)));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
