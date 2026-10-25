// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.network;

import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.rail.Station;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the player named the Station at {@code pos} in its menu. The server checks the
 * player can reach it and cleans the name ({@link StationBlockEntity#rename}).
 */
public record NameStationPacket(BlockPos pos, String name) implements CustomPacketPayload {

    public static final Type<NameStationPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "name_station"));

    /** Room for a name of the limit's length with formatting codes in it, which the server takes out. */
    public static final StreamCodec<ByteBuf, NameStationPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, NameStationPacket::pos,
            ByteBufCodecs.stringUtf8(Station.NAME_LIMIT * 4), NameStationPacket::name,
            NameStationPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NameStationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.level().isLoaded(packet.pos())
                    && player.level().getBlockEntity(packet.pos()) instanceof StationBlockEntity station) {
                station.rename(player, packet.name());
            }
        });
    }
}
