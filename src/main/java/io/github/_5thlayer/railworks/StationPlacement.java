// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Where a Station block may go: by the Rail, as {@link Station#stop} says, and not in the Clearance
 * of the Span it would serve, where a car passing would run into it. The Placement Preview and the
 * click both ask this, of the client's Spans and the level's.
 */
public final class StationPlacement {

    private StationPlacement() {
    }

    /** Why a Station at {@code pos} would be refused, or empty where it may go. */
    public static Optional<RailworksRefusal> refusal(RailNetwork network, BlockPos pos) {
        var stop = new Station(pos.getX(), pos.getY(), pos.getZ(), Station.DEFAULT_NAME).stop(network);
        if (stop.isEmpty()) {
            return Optional.of(RailworksRefusal.STATION_OFF_RAIL);
        }
        var span = stop.get().span();
        Place foot = new Place(span, span.b(), span.length() - stop.get().fromA());
        AABB car = LocomotivePlacement.carBox(LocomotivePlacement.railPoint(foot));
        if (new AABB(pos).intersects(car)) {
            return Optional.of(RailworksRefusal.STATION_IN_CLEARANCE);
        }
        return Optional.empty();
    }
}
