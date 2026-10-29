// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.HatchReport.StationSpot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Charger shows a player looking at it: the Station it joins with the car spot, numbered from
 * 1 at the Station for the player, and whether it stands beside the spot's Engine room, the one block
 * from which it charges a Locomotive, or that it joins none. Read on the server and sent to the
 * client, which words it with {@link #lines}.
 *
 * @param joined the Station it joins and the car spot it stands beside; empty for none
 */
public record ChargerReport(Optional<StationSpot> joined) {

    public static final StreamCodec<RegistryFriendlyByteBuf, ChargerReport> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(HatchReport.JOINED_CODEC), ChargerReport::joined,
            ChargerReport::new);

    /** What {@code entity}'s Charger shows, on the server. */
    public static ChargerReport of(ChargerBlockEntity entity) {
        return new ChargerReport(PlatformsNear.joined(entity.getLevel(), entity.getBlockPos()).map(StationSpot::of));
    }

    /** Its lines in Jade: the Station it joins and whether it is beside an Engine room, or that it joins none. */
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (joined.isEmpty()) {
            lines.add(Component.translatable("tooltip.railworks.charger.jade.no_station"));
            return lines;
        }
        lines.add(joined.get().line("tooltip.railworks.charger.jade."));
        lines.add(Component.translatable(joined.get().engineRoom()
                ? "tooltip.railworks.charger.jade.engine_room" : "tooltip.railworks.charger.jade.not_engine_room"));
        return lines;
    }
}
