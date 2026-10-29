// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.StraightLeg;
import net.minecraft.core.BlockPos;

/**
 * Where a Station block may go: by the Rail, in the row beside it, as {@link Station#foot} says,
 * which is clear of the Clearance of the Span it would serve and has a side, so it serves one way
 * ({@link Station#servingEnd}); by a Straight leg, as {@link StraightLeg#at} says; and with its
 * block and platform clear of every other Station's
 * ({@link Platform#overlapping}). The Placement Preview and the click both ask this, of the client's
 * Spans and Stations and the level's.
 */
public final class StationPlacement {

    private StationPlacement() {
    }

    /** Why a Station was refused, and for an overlap, the other Station. */
    public record Refused(RailworksRefusal reason, Optional<Station> other) {

        static Refused because(RailworksRefusal reason) {
            return new Refused(reason, Optional.empty());
        }
    }

    /**
     * Why a Station at {@code pos} would be refused, among {@code stations}, or empty where it may go.
     */
    public static Optional<Refused> refusal(RailNetwork network, Iterable<Station> stations, BlockPos pos) {
        Station station = new Station(pos.getX(), pos.getY(), pos.getZ(), Station.DEFAULT_NAME);
        if (station.foot(network).isEmpty()) {
            return Optional.of(Refused.because(RailworksRefusal.STATION_OFF_RAIL));
        }
        if (!StraightLeg.at(network, station)) {
            return Optional.of(Refused.because(RailworksRefusal.STATION_NOT_STRAIGHT));
        }
        return Platform.overlapping(network, station, stations)
                .map(other -> new Refused(RailworksRefusal.STATION_OVERLAPS, Optional.of(other)));
    }
}
