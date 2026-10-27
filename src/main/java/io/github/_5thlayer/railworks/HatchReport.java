// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.rail.HatchRequest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a hatch shows a player looking at it, beside what it holds: whether it is a provider or a
 * requester, what a requester asks for, and the Station it joins with the car spot, numbered from 1
 * at the Station for the player, or that it joins none. Read on the server, where the hatch's record is, and sent to
 * the client, which words it with {@link #lines}.
 *
 * @param mode    whether it is a provider or a requester
 * @param request what a requester asks for; empty for one asking for nothing, and for a provider
 * @param joined  the Station it joins and the car spot it stands beside; empty for none
 */
public record HatchReport(HatchMode mode, Optional<HatchRequest<Item>> request, Optional<StationSpot> joined) {

    /** The Station a hatch joins, by name, and its car spot's index, 0 the one behind the leading car's. */
    public record StationSpot(String station, int spot) {
    }

    private static final StreamCodec<ByteBuf, StationSpot> JOINED_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, StationSpot::station,
            ByteBufCodecs.VAR_INT, StationSpot::spot,
            StationSpot::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, HatchReport> STREAM_CODEC = StreamCodec.composite(
            HatchMode.STREAM_CODEC, HatchReport::mode,
            ByteBufCodecs.optional(Hatches.REQUEST_STREAM_CODEC), HatchReport::request,
            ByteBufCodecs.optional(JOINED_CODEC), HatchReport::joined,
            HatchReport::new);

    /** What {@code entity}'s hatch shows, on the server. */
    public static HatchReport of(HatchBlockEntity entity) {
        Hatches.Hatch hatch = entity.hatch().orElseThrow();
        Optional<StationSpot> joined = PlatformsNear.joined(entity.getLevel(), entity.getBlockPos())
                .map(at -> new StationSpot(at.station().name(), at.spot()));
        return new HatchReport(hatch.mode(), hatch.request(), joined);
    }

    /** What a requester asking for {@code request} asks for, in words: the action bar's line and Jade's. */
    public static Component requesting(Optional<HatchRequest<Item>> request) {
        return request.map(asked -> Component.translatable("message.railworks.hatch.requesting",
                        asked.stacks(), new ItemStack(asked.item()).getItemName()))
                .orElseGet(() -> Component.translatable("message.railworks.hatch.requesting_nothing"));
    }

    /** Its lines in Jade: provider or requester, a requester's request, and the Station it joins or that it joins none. */
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (mode == HatchMode.PROVIDER) {
            lines.add(Component.translatable("tooltip.railworks.hatch.jade.provider"));
        } else {
            lines.add(Component.translatable("tooltip.railworks.hatch.jade.requester"));
            lines.add(requesting(request));
        }
        lines.add(joined.map(at -> Component.translatable("tooltip.railworks.hatch.jade.station", at.station(), at.spot() + 1))
                .orElseGet(() -> Component.translatable("tooltip.railworks.hatch.jade.no_station")));
        return lines;
    }
}
