// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** A level's {@link Hatches}, saved with it beside its {@link LevelStations}. */
public final class LevelHatches extends SavedData {

    private static final Codec<LevelHatches> CODEC = Hatches.CODEC
            .xmap(LevelHatches::new, data -> data.hatches)
            .fieldOf("hatches")
            .codec();

    private static final SavedDataType<LevelHatches> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "hatches"),
            () -> new LevelHatches(new Hatches()), CODEC);

    private final Hatches hatches;

    private LevelHatches(Hatches hatches) {
        this.hatches = hatches;
        hatches.onChange(this::setDirty);
    }

    /** The hatches of {@code level}. */
    public static Hatches of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE).hatches;
    }
}
