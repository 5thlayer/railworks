// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Locale;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * What a hatch does with what it holds (ADR 0008): a provider offers it to requesters at the same car
 * spot of other Stations, and a requester asks for one item. Its block's {@code mode} shows it.
 */
public enum HatchMode implements StringRepresentable {
    PROVIDER, REQUESTER;

    public static final Codec<HatchMode> CODEC = StringRepresentable.fromEnum(HatchMode::values);
    public static final StreamCodec<ByteBuf, HatchMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(ordinal -> values()[ordinal], HatchMode::ordinal);

    /** The other mode, the one switching to it gives. */
    public HatchMode other() {
        return this == PROVIDER ? REQUESTER : PROVIDER;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
