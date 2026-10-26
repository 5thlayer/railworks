// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

/**
 * Every Station in a level, one to a block, so a Train can be driven to one and a car
 * can ask which it is at without the Stations' chunks being loaded. Several may share a name.
 *
 * <p>Pure: DataFixerUpper's codec, no Minecraft types.
 */
public final class Stations {

    public static final Codec<Stations> CODEC = Station.CODEC.listOf().xmap(
            list -> {
                Stations stations = new Stations();
                list.forEach(stations::put);
                return stations;
            },
            stations -> List.copyOf(stations.stations.values()));

    private final Map<List<Integer>, Station> stations = new LinkedHashMap<>();

    /** Adds a Station, or renames the one at its block. */
    public void put(Station station) {
        stations.put(key(station), station);
    }

    /** Removes the Station at this block and returns it, or null if there was none. */
    public @Nullable Station remove(int x, int y, int z) {
        return stations.remove(List.of(x, y, z));
    }

    /** The Station at this block, or null if there is none. */
    public @Nullable Station get(int x, int y, int z) {
        return stations.get(List.of(x, y, z));
    }

    /** Every Station called {@code name}, in the order they were placed. */
    public List<Station> named(String name) {
        return stations.values().stream().filter(s -> s.name().equals(name)).toList();
    }

    /** The Stations a car at {@code place} is at: those serving its Span within their stretch. */
    public List<Station> at(RailNetwork network, Place place) {
        return stations.values().stream().filter(s -> s.serves(network, place)).toList();
    }

    /**
     * The Stations {@code train} is stopped at: while it is at rest, those its leading car is at
     * arriving their serving way, within a Station's {@link Station#STRETCH} of its
     * {@link Platform#leadMark}, the rest of it back along the platform; none while it moves. Either end
     * of a Train may lead, but a Train of one car leads only the way it faces, so a Locomotive that
     * came in the other way is not stopped at a Station it stands by.
     */
    public List<Station> stoppedAt(RailNetwork network, Train train) {
        if (!train.atRest()) {
            return List.of();
        }
        List<Place> leads = train.cars().size() == 1
                ? List.of(train.facing(network, train.cars().getFirst()))
                : List.of(train.lead(network, 1), train.lead(network, -1));
        return stations.values().stream()
                .filter(station -> Platform.of(network, station)
                        .map(platform -> leads.stream().anyMatch(lead ->
                                Math.abs(platform.toLeadMark(network, lead, Station.STRETCH)) <= Station.STRETCH))
                        .orElse(false))
                .toList();
    }

    public Collection<Station> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(stations.values()));
    }

    private static List<Integer> key(Station station) {
        return List.of(station.x(), station.y(), station.z());
    }
}
