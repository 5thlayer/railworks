// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** A level's {@link Chargers}, saved with it beside its {@link LevelHatches}. */
public final class LevelChargers extends SavedData {

    private static final Codec<LevelChargers> CODEC = Chargers.CODEC
            .xmap(LevelChargers::new, data -> data.chargers)
            .fieldOf("chargers")
            .codec();

    private static final SavedDataType<LevelChargers> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "chargers"),
            () -> new LevelChargers(new Chargers()), CODEC);

    private final Chargers chargers;

    private LevelChargers(Chargers chargers) {
        this.chargers = chargers;
        chargers.onChange(this::setDirty);
    }

    /** The Chargers of {@code level}. */
    public static Chargers of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE).chargers;
    }
}
