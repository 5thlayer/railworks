// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailGeometry;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanAim;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a Locomotive in the hand would sit: the Span the player's line of sight is on, the car
 * riding on it facing the way the player looks along it. The Placement Preview draws this and the click spawns
 * it, so both ask this one rule. Asked of the client's copy of the Spans on the client and the
 * level's on the server.
 */
public final class LocomotivePlacement {

    /** How far the line of sight may pass from the Rail and still be aimed at it. */
    static final double AIM_TOLERANCE = 0.75;

    /** What a plan is built from: the one-car Train it would set on the Rail, or why it would not. */
    public record Result(@Nullable Train train, @Nullable Locomotive locomotive, PlacementPlan plan) {
    }

    private LocomotivePlacement() {
    }

    /** The space the car takes above the Rail point: the Clearance the placement checks. */
    static AABB carBox(Vec3 rail) {
        double half = LocomotiveEntity.WIDTH / 2.0;
        return new AABB(rail.x - half, rail.y, rail.z - half, rail.x + half, rail.y + LocomotiveEntity.HEIGHT, rail.z + half);
    }

    /** The world point on the flange's top at {@code place}: where a car's floor rests. */
    static Vec3 railPoint(Place place) {
        Place.Point p = place.point();
        return new Vec3(p.x() + RailGeometry.ATTACH_X, p.y() + RailGeometry.RIDE_Y, p.z() + RailGeometry.ATTACH_Z);
    }

    /** The plan for {@code player} aiming at {@code aimedBlock}'s neighbourhood, or null with no player. */
    public static @Nullable Result plan(Level level, RailNetwork network, @Nullable Player player, BlockPos aimedBlock) {
        if (player == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        var aim = SpanAim.nearest(network,
                eye.x - RailGeometry.ATTACH_X, eye.y - RailGeometry.AIM_Y, eye.z - RailGeometry.ATTACH_Z,
                look.x, look.y, look.z, player.blockInteractionRange(), AIM_TOLERANCE);
        if (aim.isEmpty()) {
            return new Result(null, null, PlacementPlan.refused(aimedBlock, Blocks.FURNACE.defaultBlockState(), RailworksRefusal.OFF_SPAN));
        }
        Span span = aim.get().span();
        // The whole look vector, not its horizontal part: a steep Span has almost no horizontal run.
        boolean facesB = (span.b().x() - span.a().x()) * look.x + (span.b().y() - span.a().y()) * look.y
                + (span.b().z() - span.a().z()) * look.z >= 0.0;
        Arm front = facesB ? span.b() : span.a();
        double toFront = facesB ? span.length() - aim.get().fromA() : aim.get().fromA();
        Place place = new Place(span, front, toFront);
        Vec3 rail = railPoint(place);
        AABB box = carBox(rail);
        BlockPos at = BlockPos.containing(box.getCenter());
        var state = Blocks.FURNACE.defaultBlockState();
        if (LocomotiveEntity.holding(level, span)) {
            return new Result(null, null, PlacementPlan.refused(at, state, RailworksRefusal.SPAN_TAKEN));
        }
        if (!level.noCollision(box)) {
            return new Result(null, null, PlacementPlan.refused(at, state, RailworksRefusal.BLOCKED_CAR));
        }
        Locomotive locomotive = Locomotive.burner();
        return new Result(Train.of(place, locomotive), locomotive, PlacementPlan.accepted(at, state));
    }
}
