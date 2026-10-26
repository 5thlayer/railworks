// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

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

    /** How far a Train settling onto a Station's mark moves in a tick, in blocks: a block a second. */
    public static final double SETTLE_STEP = 0.05;
    /** How near its mark a Train is settled, in blocks: closer than the floating point can tell apart. */
    private static final double SETTLED = 1e-9;
    /** How far from a Station block its leading car may stand and still be within its stretch, along x or z, in blocks. */
    private static final double NEAR = Station.STRETCH + Station.REACH + 1.0;

    /**
     * One tick of {@code train} settling at a Station it is stopped at: while it is at rest, its
     * leading car eases up to {@link #SETTLE_STEP} toward the nearest {@link Platform#leadMark} it is
     * within a Station's stretch of, either end of a Train leading as {@link #stoppedAt} says, so a
     * Train brought to rest by hand ends with its cars on the car spots, as the hatches beside them
     * expect. Settled at one Station it moves no more, though another is near. It moves nowhere
     * {@code taken} says another Train holds, nor past a line's end. Returns whether it moved.
     */
    public boolean settle(RailNetwork network, Train train, Predicate<Span> taken) {
        if (!train.atRest()) {
            return false;
        }
        List<Integer> ways = train.cars().size() == 1
                ? List.of(train.reversed(train.cars().getFirst()) ? -1 : 1)
                : List.of(1, -1);
        int bestWay = 0;
        double best = Double.NaN;
        for (int way : ways) {
            Place lead = train.lead(network, way);
            Place.Point at = lead.point(network);
            for (Station station : stations.values()) {
                if (Math.abs(station.x() - at.x()) > NEAR || Math.abs(station.z() - at.z()) > NEAR) {
                    continue;
                }
                Optional<Platform> platform = Platform.of(network, station);
                if (platform.isEmpty()) {
                    continue;
                }
                double ahead = platform.get().toLeadMark(network, lead, Station.STRETCH);
                if (Math.abs(ahead) <= SETTLED) {
                    return false;
                }
                if (Math.abs(ahead) <= Station.STRETCH && !(Math.abs(ahead) >= Math.abs(best))) {
                    best = ahead;
                    bestWay = way;
                }
            }
        }
        if (bestWay == 0) {
            return false;
        }
        double step = Math.max(-SETTLE_STEP, Math.min(SETTLE_STEP, best));
        return train.nudge(network, bestWay * step, taken);
    }

    public Collection<Station> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(stations.values()));
    }

    private static List<Integer> key(Station station) {
        return List.of(station.x(), station.y(), station.z());
    }
}
