// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import com.mojang.serialization.Codec;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * A level's Stations, saved with it, so a Train can find each Station of a name with their chunks
 * unloaded. Each Station block keeps its own name too, which is what its menu and Jade read; this
 * copy follows it, every Station block putting itself here as it loads.
 */
public final class LevelStations extends SavedData {

    private static final Codec<LevelStations> CODEC = Stations.CODEC
            .xmap(LevelStations::new, data -> data.stations)
            .fieldOf("stations")
            .codec();

    public static final SavedDataType<LevelStations> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "stations"),
            LevelStations::new, CODEC);

    private final Stations stations;

    public LevelStations() {
        this(new Stations());
    }

    private LevelStations(Stations stations) {
        this.stations = stations;
    }

    public static LevelStations of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Stations stations() {
        return stations;
    }

    /** The Station at {@code pos} is called {@code name}: added, renamed, or already so. */
    void put(BlockPos pos, String name) {
        Station station = new Station(pos.getX(), pos.getY(), pos.getZ(), name);
        if (!station.equals(stations.get(pos.getX(), pos.getY(), pos.getZ()))) {
            stations.put(station);
            setDirty();
        }
    }

    void remove(BlockPos pos) {
        if (stations.remove(pos.getX(), pos.getY(), pos.getZ()) != null) {
            setDirty();
        }
    }
}
