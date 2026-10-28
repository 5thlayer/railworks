// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** A level's {@link CarRecords}, saved with it beside its {@link LevelHatches}. */
public final class LevelCarRecords extends SavedData {

    private static final Codec<LevelCarRecords> CODEC = CarRecords.CODEC
            .xmap(LevelCarRecords::new, data -> data.records)
            .fieldOf("cars")
            .codec();

    public static final SavedDataType<LevelCarRecords> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "car_records"),
            () -> new LevelCarRecords(new CarRecords()), CODEC);

    private final CarRecords records;

    private LevelCarRecords(CarRecords records) {
        this.records = records;
        records.onChange(this::setDirty);
    }

    /** The car records of {@code level}. */
    public static CarRecords of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE).records;
    }

    /** What this saved data holds. */
    public CarRecords records() {
        return records;
    }
}
