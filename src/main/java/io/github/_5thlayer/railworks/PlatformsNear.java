// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.client.ClientRails;
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
 * What Stations, platforms and hatches ask of the level, on either side: the Rail, the Stations near
 * a block, the Station a hatch joins, the free car spots a held hatch draws, and a Station's hatches.
 * A server asks the level's Spans and Stations, and a client, which has only what it was sent, its
 * Spans and the Station blocks loaded near. A Station or a hatch in hand, a hatch's block and Jade
 * all ask here, so each asks one rule.
 */
public final class PlatformsNear {

    /** Half the gap between two coupled cars' bodies, by which a drawn car spot falls short of each of its ends. */
    private static final double HALF_GAP = CarBody.GAP / 2.0;

    /**
     * What a held hatch draws: the free blocks beside one car spot on one side of the Rail, as a
     * box, and whether the spot is the leading car's place.
     */
    public record FreeSpot(AABB box, boolean leading) {
    }

    private PlatformsNear() {
    }

    /** The Rail on this side: the level's on a server, and on a client the Spans it was sent. */
    static RailNetwork network(Level level) {
        return level instanceof ServerLevel server ? LevelRails.of(server).network() : ClientRails.network();
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
        return Platform.joined(network(level), stations(level, pos), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Where a hatch could go near {@code pos}, as a held hatch draws it, car spot by car spot: the
     * free blocks beside a spot of the platform of a Station near, empty enough to place in, those
     * on one side of the Rail as one box, two for a spot and three for the leading car's place, or
     * each still free on its own when some are taken, falling half a car gap short of its spot's
     * ends, so the spots read as the cars that will stand there. Those beside the leading car's
     * place, where a provider fuels the Locomotive that usually leads, say so.
     */
    public static List<FreeSpot> freeSpots(Level level, BlockPos pos) {
        RailNetwork network = network(level);
        List<FreeSpot> boxes = new ArrayList<>();
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
                    boolean leading = side.spot() == Platform.LEADING;
                    if (free.size() == side.blocks().size()) {
                        boxes.add(new FreeSpot(drawn, leading));
                    } else {
                        free.forEach(beside -> boxes.add(new FreeSpot(new AABB(block(beside)).intersect(drawn), leading)));
                    }
                }
            });
        }
        return boxes;
    }

    /**
     * The hatches of {@code level} that join {@code station}, asked afresh. A record whose block is
     * loaded but no longer a hatch, as after a command replaced it with no side effects, is stale: it
     * is taken out and what it held dropped there, as {@link HatchBlockEntity} drops one it loads over.
     */
    public static List<Hatches.Hatch> joinedTo(ServerLevel level, Station station) {
        Hatches hatches = LevelHatches.of(level);
        List<Hatches.Hatch> joined = new ArrayList<>();
        for (Hatches.Hatch hatch : hatches.all()) {
            BlockPos pos = hatch.pos();
            if (!Platform.near(station, pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }
            if (hatches.dropIfStale(level, pos)) {
                continue;
            }
            if (joined(level, pos).filter(at -> at.station().samePlace(station)).isPresent()) {
                joined.add(hatch);
            }
        }
        return joined;
    }

    private static BlockPos block(Platform.Beside beside) {
        return new BlockPos(beside.x(), beside.y(), beside.z());
    }
}
