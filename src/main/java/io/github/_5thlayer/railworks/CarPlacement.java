// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailCurve;
import io.github._5thlayer.railworks.rail.RailGeometry;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanAim;
import io.github._5thlayer.railworks.rail.Train;
import io.github._5thlayer.railworks.rail.Trains;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a car in the hand would sit: on the Span the player's line of sight is on, facing the way
 * the player looks along it, or, aimed at where it would couple to the end of a Train at rest,
 * there, coupled into that Train. The Placement Preview draws this and the click sets the car, so
 * both ask this one rule. On the server it reads the level's {@link Trains}; a client knows no
 * Trains, so it reads the cars it sees instead.
 */
public final class CarPlacement {

    /** How far the line of sight may pass from the Rail and still be aimed at it. */
    static final double AIM_TOLERANCE = 0.75;

    /**
     * What a plan is built from: the car, and either the new one-car Train it would start or the
     * Train end it would couple to, facing that Train's way or, {@code reversed}, against it; or
     * neither, and why it would not place.
     */
    public record Result(@Nullable Car car, @Nullable Train train, Trains.@Nullable Slot slot, boolean reversed,
                         PlacementPlan plan) {
    }

    private CarPlacement() {
    }

    /** The space the car takes above the Rail point: the Clearance the placement checks. */
    static AABB carBox(Vec3 rail) {
        double half = CarEntity.WIDTH / 2.0;
        return new AABB(rail.x - half, rail.y, rail.z - half, rail.x + half, rail.y + CarEntity.HEIGHT, rail.z + half);
    }

    /** The world point on the flange's top at {@code place}, on its Span's curve: where a car's floor rests. */
    static Vec3 railPoint(RailNetwork network, Place place) {
        return railPoint(place.point(network));
    }

    /** The world point on the flange's top over {@code p}, a point in Arm coordinates. */
    static Vec3 railPoint(Place.Point p) {
        return new Vec3(p.x() + RailGeometry.ATTACH_X, p.y() + RailGeometry.RIDE_Y, p.z() + RailGeometry.ATTACH_Z);
    }

    /**
     * A line of sight a car is aimed along: from {@code eye}, along the unit {@code look}, for
     * {@code reach} blocks. A player's, or a command's.
     */
    public record Sight(Vec3 eye, Vec3 look, double reach) {

        public static Sight of(Player player) {
            return new Sight(player.getEyePosition(), player.getLookAngle(), player.blockInteractionRange());
        }

        /**
         * The sight a command places a car with: level along {@code yaw}, through the middle of the
         * web over the block {@code at}, the block a car riding the Rail there stands in.
         */
        public static Sight over(BlockPos at, float yaw) {
            Vec3 eye = new Vec3(at.getX() + RailGeometry.ATTACH_X, at.getY() - RailGeometry.ATTACH_Y + RailGeometry.AIM_Y,
                    at.getZ() + RailGeometry.ATTACH_Z);
            return new Sight(eye, Vec3.directionFromRotation(0.0F, yaw), 1.0);
        }
    }

    /** The plan for {@code player} aiming {@code kind} at {@code aimedBlock}'s neighbourhood, or null with no player. */
    public static @Nullable Result plan(Level level, RailNetwork network, @Nullable Player player, BlockPos aimedBlock,
                                        CarKind kind) {
        return player == null ? null : plan(level, network, Sight.of(player), aimedBlock, kind);
    }

    /** The plan for {@code kind} aimed along {@code sight}, refused at {@code aimedBlock} when it is aimed at no Span. */
    public static Result plan(Level level, RailNetwork network, Sight sight, BlockPos aimedBlock, CarKind kind) {
        BlockState state = kind.preview().defaultBlockState();
        Vec3 eye = sight.eye();
        Vec3 look = sight.look();
        var aim = SpanAim.nearest(network,
                eye.x - RailGeometry.ATTACH_X, eye.y - RailGeometry.AIM_Y, eye.z - RailGeometry.ATTACH_Z,
                look.x, look.y, look.z, sight.reach(), AIM_TOLERANCE);
        if (aim.isEmpty()) {
            return refused(PlacementPlan.refused(aimedBlock, state, RailworksRefusal.OFF_SPAN));
        }
        Span span = aim.get().span();
        Place towardB = new Place(span, span.b(), span.length() - aim.get().fromA());
        Place aimed = looksAlong(network, towardB, look) ? towardB : towardB.turned();
        Car car = kind.create();
        Coupling coupling = level instanceof ServerLevel server
                ? serverCoupling(server, network, aimed, car)
                : clientCoupling(level, network, aimed, kind.length());
        Place place = coupling == null ? aimed : coupling.place();
        Vec3 rail = railPoint(network, place);
        AABB box = carBox(rail);
        BlockPos at = BlockPos.containing(box.getCenter());
        if (coupling == null ? taken(level, network, Train.of(aimed, car)) : coupling.taken()) {
            return refused(PlacementPlan.refused(at, state, RailworksRefusal.SPAN_TAKEN));
        }
        if (!level.noCollision(box)) {
            return refused(PlacementPlan.refused(at, state, RailworksRefusal.BLOCKED_CAR));
        }
        if (coupling == null) {
            return new Result(car, Train.of(aimed, car), null, false, PlacementPlan.accepted(at, state));
        }
        // The car faces the way the player looks along the Rail, which may be against the Train.
        boolean reversed = !looksAlong(network, place, look);
        return new Result(car, null, coupling.slot(), reversed, PlacementPlan.accepted(at, state));
    }

    private static Result refused(PlacementPlan plan) {
        return new Result(null, null, null, false, plan);
    }

    /**
     * Whether {@code look} runs the way the curve at {@code place} heads, toward its front. The whole
     * vector: a steep Span has almost no horizontal run.
     */
    private static boolean looksAlong(RailNetwork network, Place place, Vec3 look) {
        RailCurve.Pose pose = place.pose(network);
        return pose.dx() * look.x + pose.dy() * look.y + pose.dz() * look.z >= 0.0;
    }

    /**
     * Where the car would couple, its middle with the Train's heading, and the slot the server
     * couples it into (null on a client); {@code taken} when the coupled Train would stand on a Span
     * another Train holds.
     */
    private record Coupling(Place place, Trains.@Nullable Slot slot, boolean taken) {
    }

    private static @Nullable Coupling serverCoupling(ServerLevel level, RailNetwork network, Place aimed, Car car) {
        Trains trains = LevelRails.of(level).trains();
        Trains.Slot slot = trains.slotNear(network, aimed.point(network), car.length());
        if (slot == null) {
            return null;
        }
        Train coupled = slot.train().coupled(network, slot.end(), car, false);
        var taken = trains.takenFrom(network, slot.train());
        return new Coupling(slot.place(), slot, coupled == null || coupled.spans(network).stream().anyMatch(taken));
    }

    /** The nearest place a client sees a car would couple: past a first or last car of a Train at rest. */
    private static @Nullable Coupling clientCoupling(Level level, RailNetwork network, Place aimed, double length) {
        Vec3 at = railPoint(network, aimed);
        AABB around = new AABB(at, at).inflate(CarEntity.HEIGHT + Trains.COUPLING_REACH + 2.0);
        Place.Point aim = aimed.point(network);
        Place best = null;
        double bestDistance = Trains.COUPLING_REACH * Trains.COUPLING_REACH;
        for (CarEntity car : level.getEntitiesOfClass(CarEntity.class, around, CarEntity::seenAtRest)) {
            Place seen = car.seenPlace(network);
            if (seen == null) {
                continue;
            }
            for (int way : new int[] {1, -1}) {
                if (way > 0 ? !car.seenFirst() : !car.seenLast()) {
                    continue;
                }
                Place.Moved moved = seen.move(network, way * (car.carKind().length() + length) / 2.0);
                if (moved.stopped()) {
                    continue;
                }
                Place.Point p = moved.place().point(network);
                double dx = p.x() - aim.x();
                double dy = p.y() - aim.y();
                double dz = p.z() - aim.z();
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = moved.place();
                }
            }
        }
        return best == null ? null : new Coupling(best, null, false);
    }

    /** Whether a new {@code train} would stand on a Span some Train holds: one Train holds a Span at a time. */
    private static boolean taken(Level level, RailNetwork network, Train train) {
        if (level instanceof ServerLevel server) {
            Trains trains = LevelRails.of(server).trains();
            return train.spans(network).stream().anyMatch(span -> trains.holding(network, span) != null);
        }
        return train.spans(network).stream().anyMatch(span -> CarEntity.seenOn(level, span));
    }
}
