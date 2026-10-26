// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailCurve;
import io.github._5thlayer.railworks.rail.RailGeometry;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A car on the curved Rail: that the entity stands where {@code RailCurve} says and faces and
 * pitches the way it runs, that driving through an Arm turns it without a jump, and that the rider's
 * seat stands square to a climbing floor. The curve's own geometry is {@code RailCurveTest}'s. All of
 * these tests share the {@code curve_} prefix.
 */
final class CurveTests {

    /** A line bending 45 degrees at {@link #B}: east from A, then north-east to C. */
    static final BlockPos A = new BlockPos(1, 1, 2);
    static final BlockPos B = new BlockPos(8, 1, 2);
    static final BlockPos C = new BlockPos(13, 1, 7);
    /** Three segments tall, so the Span from A climbs a quarter: two up over eight across. */
    static final BlockPos TOP = new BlockPos(9, 1, 2);
    static final BlockPos STAND = new BlockPos(5, 1, 5);

    private CurveTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("curve_car_stands_faces_and_pitches_along_the_curve", 20, CurveTests::followsCurve);
        tests.test("curve_car_driven_through_an_arm_turns_without_a_jump", 100, CurveTests::throughArm);
        tests.test("curve_rider_seat_stands_square_to_a_climbing_floor", 20, CurveTests::seatOnClimb);
    }

    private static void followsCurve(GameTestHelper helper) {
        RailNetwork network = bend(helper);
        Span span = network.get(arm(helper, A), arm(helper, B));
        Place place = new Place(span, arm(helper, B), 2.0);
        LocomotiveEntity car = spawn(helper, place);
        RailCurve.Pose pose = place.pose(network);
        Vec3 expected = new Vec3(pose.x() + RailGeometry.ATTACH_X, pose.y() + RailGeometry.RIDE_Y, pose.z() + RailGeometry.ATTACH_Z);
        if (car.position().distanceTo(expected) > 1e-6) {
            helper.fail("the car is at " + car.position() + ", not on the curve at " + expected, STAND);
        }
        float yaw = (float) (Mth.atan2(-pose.dx(), pose.dz()) * Mth.RAD_TO_DEG);
        if (Math.abs(Mth.wrapDegrees(car.getYRot() - yaw)) > 0.01F) {
            helper.fail("the car faces " + car.getYRot() + ", not along the curve at " + yaw, STAND);
        }
        // Two blocks short of the bend's Arm the curve has already begun to turn off the straight east.
        if (Math.abs(Mth.wrapDegrees(car.getYRot() - -90.0F)) < 1.0F) {
            helper.fail("the car faces straight east two blocks short of a bend", STAND);
        }
        helper.succeed();
    }

    private static void throughArm(GameTestHelper helper) {
        RailNetwork network = bend(helper);
        Span span = network.get(arm(helper, A), arm(helper, B));
        LocomotiveEntity car = spawn(helper, new Place(span, arm(helper, B), 1.5));
        LocomotiveTests.fill(helper, car, new ItemStack(Items.COAL, 4));
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        LocomotiveTests.press(driver, true, false);
        Span beyond = network.get(arm(helper, B), arm(helper, C));
        float yaw = car.getYRot();
        Vec3 at = car.position();
        boolean crossed = false;
        for (int tick = 0; tick < 80 && !crossed; tick++) {
            car.tick();
            float turned = Math.abs(Mth.wrapDegrees(car.getYRot() - yaw));
            double moved = car.position().distanceTo(at);
            // A tick moves a car well under a block, and the bend turns it a few degrees a block.
            if (turned > 5.0F || moved > 1.0) {
                helper.fail("on tick " + tick + " the car turned " + turned + " degrees and moved " + moved, STAND);
                return;
            }
            yaw = car.getYRot();
            at = car.position();
            crossed = car.holds(beyond) && !car.holds(span);
        }
        if (!crossed) {
            helper.fail("the car never ran through the Arm onto the next Span", STAND);
        }
        helper.succeed();
    }

    private static void seatOnClimb(GameTestHelper helper) {
        clearRoof(helper);
        for (int i = 0; i < 3; i++) {
            helper.setBlock(TOP.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
        }
        helper.setBlock(A, RailworksRegistries.PYLON.get().defaultBlockState());
        RailNetwork network = LevelRails.of(helper.getLevel()).network();
        Arm low = arm(helper, A);
        Arm high = arm(helper, TOP.above(2));
        network.add(Span.of(low, high, SpanRules.cost(low, high)));
        LocomotiveEntity car = spawn(helper, new Place(network.get(low, high), high, 4.0));
        ServerPlayer rider = LocomotiveTests.board(helper, car);
        rider.rideTick();
        if (car.getXRot() > -10.0F) {
            helper.fail("a car climbing a quarter is pitched " + car.getXRot(), STAND);
        }
        // Square to the floor, the seat leans back down the climb: west of the car, which faces east.
        Vec3 seat = rider.position().subtract(car.position());
        double height = car.getDimensions(car.getPose()).attachments().get(EntityAttachment.PASSENGER, 0, 0.0F).y;
        double lean = Math.sin(Math.toRadians(-car.getXRot())) * height;
        if (Math.abs(seat.x + lean) > 0.01 || Math.abs(seat.z) > 0.01) {
            helper.fail("the rider sits " + seat + " from a car pitched " + car.getXRot(), STAND);
        }
        helper.succeed();
    }

    /** The bent line, strung in the level's network, with the roof cleared over it. */
    private static RailNetwork bend(GameTestHelper helper) {
        clearRoof(helper);
        for (BlockPos column : new BlockPos[] {A, B, C}) {
            helper.setBlock(column, RailworksRegistries.PYLON.get().defaultBlockState());
        }
        RailNetwork network = LevelRails.of(helper.getLevel()).network();
        string(network, arm(helper, A), arm(helper, B));
        string(network, arm(helper, B), arm(helper, C));
        return network;
    }

    /** The structure's roof is a barrier layer the car would ride up into. */
    private static void clearRoof(GameTestHelper helper) {
        for (int x = 0; x <= 15; x++) {
            for (int z = 0; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
            }
        }
    }

    private static void string(RailNetwork network, Arm one, Arm other) {
        network.add(Span.of(one, other, SpanRules.cost(one, other)));
    }

    private static Arm arm(GameTestHelper helper, BlockPos column) {
        return PylonColumn.arm(helper.absolutePos(column));
    }

    /** A one-car Train with a Burner Locomotive at {@code place}, and its entity. */
    private static LocomotiveEntity spawn(GameTestHelper helper, Place place) {
        Locomotive locomotive = Locomotive.burner();
        Train train = Train.of(place, locomotive);
        LevelRails.of(helper.getLevel()).add(train);
        LocomotiveEntity car = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        car.setCar(train, locomotive);
        helper.getLevel().addFreshEntity(car);
        return car;
    }
}
