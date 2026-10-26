// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.StraightLeg;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Where a Station block may go: by the Rail, as {@link Station#foot} says, beside it rather than
 * under it, so it has a side and serves one way ({@link Station#servingEnd}), by a Straight leg, as
 * {@link StraightLeg#at} says, not in the Clearance of the Span it would serve, where a car passing
 * would run into it, and with its block and platform clear of every other Station's
 * ({@link Platform#overlapping}). The Placement Preview and the click both ask this, of the client's
 * Spans and Stations and the level's.
 */
public final class StationPlacement {

    private StationPlacement() {
    }

    /** How finely the Clearance is checked along the stretch, in blocks. */
    private static final double SWEEP_STEP = 0.25;

    /** Why a Station was refused, and for an overlap, the other Station. */
    public record Refused(RailworksRefusal reason, Optional<Station> other) {

        static Refused because(RailworksRefusal reason) {
            return new Refused(reason, Optional.empty());
        }
    }

    /**
     * Why a Station at {@code pos} would be refused, among {@code stations}, or empty where it may go.
     * The Clearance is checked along the whole stretch, since on a slope a car short of the foot can
     * sit lower than at it, and before the side, so a Station in the Rail's own block is refused as
     * in the way rather than as under it.
     */
    public static Optional<Refused> refusal(RailNetwork network, Iterable<Station> stations, BlockPos pos) {
        Station station = new Station(pos.getX(), pos.getY(), pos.getZ(), Station.DEFAULT_NAME);
        var foot = station.foot(network);
        if (foot.isEmpty()) {
            return Optional.of(Refused.because(RailworksRefusal.STATION_OFF_RAIL));
        }
        if (!StraightLeg.at(network, station)) {
            return Optional.of(Refused.because(RailworksRefusal.STATION_NOT_STRAIGHT));
        }
        AABB block = new AABB(pos);
        Place at = foot.get().place();
        for (double by = -Station.STRETCH; by <= Station.STRETCH + 1e-9; by += SWEEP_STEP) {
            Place car = at.move(network, by).place();
            if (block.intersects(CarPlacement.carBox(CarPlacement.railPoint(network, car)))) {
                return Optional.of(Refused.because(RailworksRefusal.STATION_IN_CLEARANCE));
            }
        }
        if (station.servingEnd(network).isEmpty()) {
            return Optional.of(Refused.because(RailworksRefusal.STATION_UNDER_RAIL));
        }
        return Platform.overlapping(network, station, stations)
                .map(other -> new Refused(RailworksRefusal.STATION_OVERLAPS, Optional.of(other)));
    }
}
