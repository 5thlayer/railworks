// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The Stations and platforms near a block, on either side: a server asks the level's Stations, and a
 * client, which has only the blocks it was sent, the Station blocks loaded near it. What a Station or a
 * hatch in hand, a hatch's block and Jade ask, so each asks one rule.
 */
public final class PlatformsNear {

    /** Half the gap between two coupled cars' bodies, by which a drawn car spot falls short of each of its ends. */
    private static final double HALF_GAP = (CarBody.PITCH - CarBody.LENGTH) / 2.0;

    private PlatformsNear() {
    }

    /**
     * The Stations whose platform a block at {@code pos} could be beside, or a Station there overlap:
     * the level's, on a server, and on a client the Station blocks in the chunks loaded within two
     * platforms' reach.
     */
    static Iterable<Station> stations(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            return LevelStations.of(server).stations().all();
        }
        int reach = (int) Math.ceil(2 * Platform.REACH);
        List<Station> near = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(pos.getX() - reach); cx <= SectionPos.blockToSectionCoord(pos.getX() + reach); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(pos.getZ() - reach); cz <= SectionPos.blockToSectionCoord(pos.getZ() + reach); cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    chunk.getBlockEntities().values().forEach(entity -> {
                        if (entity instanceof StationBlockEntity station) {
                            near.add(station.station());
                        }
                    });
                }
            }
        }
        return near;
    }

    /** Which Station a hatch at {@code pos} joins or would join, and at which car spot; empty beside no car spot. */
    public static Optional<Platform.Joined> joined(Level level, BlockPos pos) {
        return Platform.joined(StationItem.networkOf(level), stations(level, pos), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Where a hatch could go near {@code pos}, as a held hatch draws it, car spot by car spot: the
     * free blocks beside a spot of the platform of a Station near, empty enough to place in, the two
     * on one side of the Rail as one box, or the one of them still free, each falling half a car gap
     * short of its spot's ends, so the spots read as the cars that will stand there.
     */
    public static List<AABB> freeSpots(Level level, BlockPos pos) {
        RailNetwork network = StationItem.networkOf(level);
        List<AABB> boxes = new ArrayList<>();
        for (Station station : stations(level, pos)) {
            Platform.of(network, station).ifPresent(platform -> {
                for (Platform.Side side : platform.sides(network)) {
                    AABB spot = null;
                    for (Platform.Beside beside : side.blocks()) {
                        AABB box = new AABB(block(beside));
                        spot = spot == null ? box : spot.minmax(box);
                    }
                    // Along the Line, half a car gap short of each end of the spot.
                    AABB drawn = side.alongX() ? spot.deflate(HALF_GAP, 0, 0) : spot.deflate(0, 0, HALF_GAP);
                    List<Platform.Beside> free = side.blocks().stream()
                            .filter(beside -> level.getBlockState(block(beside)).canBeReplaced())
                            .toList();
                    if (free.size() == side.blocks().size()) {
                        boxes.add(drawn);
                    } else {
                        free.forEach(beside -> boxes.add(new AABB(block(beside)).intersect(drawn)));
                    }
                }
            });
        }
        return boxes;
    }

    private static BlockPos block(Platform.Beside beside) {
        return new BlockPos(beside.x(), beside.y(), beside.z());
    }
}
