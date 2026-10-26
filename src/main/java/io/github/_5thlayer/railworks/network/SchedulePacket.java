// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.network;

import java.util.List;

import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.rail.Schedule;
import io.github._5thlayer.railworks.rail.Station;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the player edited the Schedule of the Train of the Locomotive with entity id
 * {@code car} in its menu. The server checks the player can reach the Locomotive and cleans each
 * Station's name ({@link LocomotiveEntity#editSchedule}).
 */
public record SchedulePacket(int car, List<Schedule.Entry> entries, boolean automatic) implements CustomPacketPayload {

    public static final Type<SchedulePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "schedule"));

    /** A wait by its place in the list, an unknown one read as the first. */
    private static final StreamCodec<ByteBuf, Schedule.Wait> WAIT_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> Schedule.Wait.values()[Math.floorMod(i, Schedule.Wait.values().length)], Schedule.Wait::ordinal);

    /** Room for a Station's name of the limit's length with formatting codes in it, which the server takes out. */
    public static final StreamCodec<ByteBuf, Schedule.Entry> ENTRY_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(Station.NAME_LIMIT * 4), Schedule.Entry::station,
            WAIT_STREAM_CODEC, Schedule.Entry::until,
            ByteBufCodecs.VAR_INT, Schedule.Entry::seconds,
            Schedule.Entry::new);

    public static final StreamCodec<ByteBuf, List<Schedule.Entry>> ENTRIES_STREAM_CODEC =
            ENTRY_STREAM_CODEC.apply(ByteBufCodecs.list(Schedule.MAX_ENTRIES));

    public static final StreamCodec<ByteBuf, SchedulePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SchedulePacket::car,
            ENTRIES_STREAM_CODEC, SchedulePacket::entries,
            ByteBufCodecs.BOOL, SchedulePacket::automatic,
            SchedulePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SchedulePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> packet.apply(context.player()));
    }

    /** What the server does with it from {@code player}: sets the Schedule, if that Locomotive is there and in reach. */
    public boolean apply(Player player) {
        return player.level().getEntity(car) instanceof LocomotiveEntity locomotive
                && locomotive.editSchedule(player, entries, automatic);
    }
}
