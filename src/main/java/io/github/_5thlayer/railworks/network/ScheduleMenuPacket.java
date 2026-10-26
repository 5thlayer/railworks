// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.network;

import io.github._5thlayer.railworks.LocomotiveMenu;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.client.ClientSchedules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client, right after a Locomotive's menu opens: what its Schedule screen edits, for the
 * menu with {@code containerId}.
 */
public record ScheduleMenuPacket(int containerId, LocomotiveMenu.Opened opened) implements CustomPacketPayload {

    public static final Type<ScheduleMenuPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "schedule_menu"));

    public static final StreamCodec<ByteBuf, ScheduleMenuPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScheduleMenuPacket::containerId,
            LocomotiveMenu.Opened.STREAM_CODEC, ScheduleMenuPacket::opened,
            ScheduleMenuPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScheduleMenuPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientSchedules.opened(packet.containerId(), packet.opened()));
    }
}
