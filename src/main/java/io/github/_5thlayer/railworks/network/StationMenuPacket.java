// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.network;

import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.client.ClientStations;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: open the menu of the Station at {@code pos}, which is called {@code name}. */
public record StationMenuPacket(BlockPos pos, String name) implements CustomPacketPayload {

    public static final Type<StationMenuPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "station_menu"));

    public static final StreamCodec<ByteBuf, StationMenuPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StationMenuPacket::pos,
            ByteBufCodecs.STRING_UTF8, StationMenuPacket::name,
            StationMenuPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StationMenuPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStations.openMenu(packet.pos(), packet.name()));
    }
}
