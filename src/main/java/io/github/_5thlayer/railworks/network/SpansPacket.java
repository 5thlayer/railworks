// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.network;

import java.util.List;

import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.client.ClientRails;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Span;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Every Span with an end in one chunk, server to client. Sent when a player starts watching the
 * chunk and again whenever a Span touching it is strung or cut. The client replaces the chunk's
 * Spans whole, so a Span cut while nobody watched either end cannot survive on a client that comes back.
 */
public record SpansPacket(int chunkX, int chunkZ, List<Span> spans) implements CustomPacketPayload {

    public static final Type<SpansPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "spans"));

    private static final StreamCodec<ByteBuf, Arm> ARM = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Arm::x,
            ByteBufCodecs.VAR_INT, Arm::y,
            ByteBufCodecs.VAR_INT, Arm::z,
            Arm::new);

    private static final StreamCodec<ByteBuf, Span> SPAN = StreamCodec.composite(
            ARM, Span::a,
            ARM, Span::b,
            ByteBufCodecs.VAR_INT, Span::cost,
            Span::new);

    public static final StreamCodec<ByteBuf, SpansPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpansPacket::chunkX,
            ByteBufCodecs.VAR_INT, SpansPacket::chunkZ,
            SPAN.apply(ByteBufCodecs.list()), SpansPacket::spans,
            SpansPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpansPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientRails.accept(packet.chunkX(), packet.chunkZ(), packet.spans()));
    }
}
