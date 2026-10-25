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

    /** How finely the Clearance is checked along the stretch, in blocks. */
    private static final double SWEEP_STEP = 0.25;

    /**
     * Why a Station at {@code pos} would be refused, or empty where it may go. The Clearance is checked
     * along the whole stretch, since on a slope a car short of the foot can sit lower than at it.
     */
    public static Optional<RailworksRefusal> refusal(RailNetwork network, BlockPos pos) {
        var foot = new Station(pos.getX(), pos.getY(), pos.getZ(), Station.DEFAULT_NAME).foot(network);
        if (foot.isEmpty()) {
            return Optional.of(RailworksRefusal.STATION_OFF_RAIL);
        }
        AABB block = new AABB(pos);
        Place at = foot.get().place();
        for (double by = -Station.STRETCH; by <= Station.STRETCH + 1e-9; by += SWEEP_STEP) {
            Place car = at.move(network, by).place();
            if (block.intersects(CarPlacement.carBox(CarPlacement.railPoint(car)))) {
                return Optional.of(RailworksRefusal.STATION_IN_CLEARANCE);
            }
        }
        return Optional.empty();
    }
}
