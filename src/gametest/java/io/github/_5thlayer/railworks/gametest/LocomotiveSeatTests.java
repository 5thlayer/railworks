// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import static io.github._5thlayer.railworks.gametest.LocomotiveTests.STAND;

import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.rail.CarBody;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A Locomotive's two seats: the cab, where only a player sits and drives, and the bay behind it,
 * where one mob or a second player stands on the floor. A player boarding takes the cab if it is
 * free, else the bay; a led mob goes in the bay only; nobody moves up a seat; each gets out beside
 * their own seat. All of these tests share the {@code loco_seats_} prefix and stand on
 * {@link LocomotiveTests}' Span, the car facing east; the one out of view is {@code FarAwayTests}'.
 */
final class LocomotiveSeatTests {

    /**
     * The cab's seat, {@link CarBody#CAB_SEAT}: in the cab, a seated player's feet 0.6 below it, 0.6 over the floor's underside.
     */
    private static final Vec3 CAB = new Vec3(CarBody.CAB_SEAT.x(), CarBody.CAB_SEAT.y() - 0.6, CarBody.CAB_SEAT.z());
    /**
     * Standing in the bay, {@link CarBody#BAY_SPOT}: mid-bay behind the Engine room, on the floor's top 0.7 over its underside.
     */
    private static final Vec3 BAY = new Vec3(CarBody.BAY_SPOT.x(), CarBody.BAY_SPOT.y(), CarBody.BAY_SPOT.z());

    private LocomotiveSeatTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("loco_seats_a_second_player_rides_in_the_bay_and_does_not_drive", 20, LocomotiveSeatTests::secondPlayer);
        tests.test("loco_seats_a_player_leads_a_mob_in_then_boards_and_drives", 20, LocomotiveSeatTests::mobThenDriver);
        tests.test("loco_seats_a_mob_boarding_first_leaves_the_cab_to_a_player", 20, LocomotiveSeatTests::mobFirst);
        tests.test("loco_seats_nobody_moves_up_and_a_taken_bay_refuses", 20, LocomotiveSeatTests::nobodyMovesUp);
        tests.test("loco_seats_a_rider_stays_in_the_bay_when_the_driver_gets_off", 20, LocomotiveSeatTests::riderStays);
        tests.test("loco_seats_a_lead_takes_the_mob_rider_out_past_the_driver", 20, LocomotiveSeatTests::leadOutPastDriver);
        tests.test("loco_seats_each_gets_out_beside_their_own_seat_on_the_side_they_look", 20,
                LocomotiveSeatTests::outBesideTheirSeat);
    }

    private static void secondPlayer(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.fuelled(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        ServerPlayer rider = LocomotiveTests.board(helper, car);
        expectAt(helper, driver, car, CAB, "the driver");
        expectAt(helper, rider, car, BAY, "the second player");
        // The second player's keys move nothing.
        LocomotiveTests.press(rider, true, false);
        LocomotiveTests.run(car, 10);
        if (car.train().speed() != 0.0) {
            helper.fail("the bay's player drove the Train to speed " + car.train().speed(), STAND);
        }
        if (refused(helper, car)) {
            return;
        }
        LocomotiveTests.press(rider, false, false);
        expectDrives(helper, car, driver);
        helper.succeed();
    }

    private static void mobThenDriver(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.fuelled(helper);
        ServerPlayer player = player(helper);
        Pig pig = helper.spawn(EntityType.PIG, STAND.east(2));
        pig.setLeashedTo(player, true);
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (pig.getVehicle() != car || player.getVehicle() != null) {
            helper.fail("the led pig was not pulled in alone: pig in " + pig.getVehicle() + ", player in " + player.getVehicle(), STAND);
        }
        // The lead handed back would lead the pig out again: put away, the hand is empty.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (player.getVehicle() != car) {
            helper.fail("the player who led the pig in could not board after it", STAND);
        }
        expectAt(helper, pig, car, BAY, "the pig");
        expectAt(helper, player, car, CAB, "the player");
        // The pig faces the way the car does, whichever way it looked as it got in.
        if (Math.abs(Mth.wrapDegrees(pig.getYRot() - car.getYRot())) > 1e-3
                || Math.abs(Mth.wrapDegrees(pig.yBodyRot - car.getYRot())) > 1e-3) {
            helper.fail("the pig faces " + pig.getYRot() + " (body " + pig.yBodyRot + "), not the car's " + car.getYRot(), STAND);
        }
        expectDrives(helper, car, player);
        helper.succeed();
    }

    private static void mobFirst(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.fuelled(helper);
        Pig pig = helper.spawn(EntityType.PIG, STAND.east(2));
        if (!pig.startRiding(car)) {
            helper.fail("the pig could not get into an empty car", STAND);
        }
        ServerPlayer player = LocomotiveTests.board(helper, car);
        expectAt(helper, pig, car, BAY, "the pig");
        expectAt(helper, player, car, CAB, "the player");
        expectDrives(helper, car, player);
        helper.succeed();
    }

    private static void nobodyMovesUp(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.fuelled(helper);
        Pig pig = helper.spawn(EntityType.PIG, STAND.east(2));
        pig.startRiding(car);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        getOff(driver);
        if (driver.getVehicle() != null || pig.getVehicle() != car) {
            helper.fail("the driver did not get off alone: driver in " + driver.getVehicle() + ", pig in " + pig.getVehicle(), STAND);
        }
        expectAt(helper, pig, car, BAY, "the pig left alone");
        ServerPlayer next = LocomotiveTests.board(helper, car);
        expectAt(helper, next, car, CAB, "the next player");
        // The bay taken, a further led mob is refused, and so is its leader.
        ServerPlayer leader = player(helper);
        Cow cow = helper.spawn(EntityType.COW, STAND.east(2));
        cow.setLeashedTo(leader, true);
        car.interact(leader, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (cow.getVehicle() != null || leader.getVehicle() != null || cow.getLeashHolder() != leader) {
            helper.fail("a full car took the cow (" + cow.getVehicle() + ") or its leader (" + leader.getVehicle() + ")", STAND);
        }
        if (refused(helper, car)) {
            return;
        }
        expectDrives(helper, car, next);
        helper.succeed();
    }

    private static void riderStays(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.fuelled(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        ServerPlayer rider = LocomotiveTests.board(helper, car);
        getOff(driver);
        if (driver.getVehicle() != null || rider.getVehicle() != car) {
            helper.fail("the driver did not get off alone", STAND);
        }
        rider.rideTick();
        expectAt(helper, rider, car, BAY, "the rider left alone");
        LocomotiveTests.press(rider, true, false);
        LocomotiveTests.run(car, 10);
        if (car.train().speed() != 0.0) {
            helper.fail("the rider moved up to drive, the Train at speed " + car.train().speed(), STAND);
        }
        LocomotiveTests.press(rider, false, false);
        ServerPlayer next = LocomotiveTests.board(helper, car);
        expectAt(helper, next, car, CAB, "the next player");
        expectDrives(helper, car, next);
        helper.succeed();
    }

    private static void leadOutPastDriver(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.placed(helper);
        // The driver boards first, so the pig is not the car's first passenger.
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        Pig pig = helper.spawn(EntityType.PIG, STAND.east(2));
        if (!pig.startRiding(car)) {
            helper.fail("the pig could not get in behind the driver", STAND);
        }
        ServerPlayer leader = player(helper);
        leader.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        car.interact(leader, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (pig.getVehicle() != null || pig.getLeashHolder() != leader || !leader.getMainHandItem().isEmpty()) {
            helper.fail("the lead did not lead the pig out, riding " + pig.getVehicle() + ", held by " + pig.getLeashHolder(), STAND);
        }
        if (driver.getVehicle() != car || leader.getVehicle() != null) {
            helper.fail("leading the pig out moved the driver (" + driver.getVehicle() + ") or boarded the leader", STAND);
        }
        helper.succeed();
    }

    /**
     * Each gets out beside their own seat, along the car, on the side they look to: the car faces east,
     * so a player looking south gets out on its south side and one looking north on its north side.
     */
    private static void outBesideTheirSeat(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.placed(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        ServerPlayer rider = LocomotiveTests.board(helper, car);
        double floor = helper.absolutePos(STAND).getY();
        // The cab stands past the platform's east edge, at x 9: floor beside it on both sides.
        for (int x = 9; x <= 10; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        // Looking south, a little ahead of square to the car.
        driver.setYRot(-10.0F);
        getOff(driver);
        expectBeside(helper, driver, car, car.getX() + CAB.z, 1, floor, "the driver");
        // Looking north, a little behind.
        rider.setYRot(-170.0F);
        getOff(rider);
        expectBeside(helper, rider, car, car.getX() + BAY.z, -1, floor, "the rider");
        helper.succeed();
    }

    // ---- helpers ----

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void getOff(ServerPlayer player) {
        player.setShiftKeyDown(true);
        player.rideTick();
    }

    /** Whether a further player is let on the full car, failing the test if so. */
    private static boolean refused(GameTestHelper helper, LocomotiveEntity car) {
        ServerPlayer third = player(helper);
        car.interact(third, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (third.getVehicle() != null) {
            helper.fail("a player boarded a car whose cab and bay are taken", STAND);
            return true;
        }
        return false;
    }

    /** {@code entity} stands at {@code seat}, in the car's own coordinates, the car facing east on level Rail. */
    private static void expectAt(GameTestHelper helper, Entity entity, LocomotiveEntity car, Vec3 seat, String who) {
        car.positionRider(entity);
        Vec3 expected = car.position().add(seat.z, seat.y, -seat.x);
        if (entity.position().distanceTo(expected) > 1e-3) {
            helper.fail(who + " is at " + entity.position().subtract(car.position()) + " from the car's middle, not "
                    + expected.subtract(car.position()), STAND);
        }
    }

    /** {@code player} got out at {@code x} along the car, on its {@code south} side (1, or -1 north), on the floor. */
    private static void expectBeside(GameTestHelper helper, Player player, LocomotiveEntity car, double x, int south,
                                     double floor, String who) {
        if (player.getVehicle() != null) {
            helper.fail(who + " did not get off", STAND);
        }
        double across = (player.getZ() - car.getZ()) * south;
        if (Math.abs(player.getX() - x) > 1e-3 || across < 0.5 + player.getBbWidth() / 2.0 || across > 1.5
                || Math.abs(player.getY() - floor) > 1e-3) {
            helper.fail(who + " got out at " + player.position().subtract(car.position()) + " from the car's middle, not beside "
                    + "their seat at x " + (x - car.getX()) + " on the " + (south > 0 ? "south" : "north") + " side, on the floor", STAND);
        }
    }

    /** {@code driver}'s forward key runs the car the way it faces. */
    private static void expectDrives(GameTestHelper helper, LocomotiveEntity car, ServerPlayer driver) {
        double start = car.getX();
        LocomotiveTests.press(driver, true, false);
        LocomotiveTests.run(car, 10);
        if (car.train().speed() <= 0.0 || car.getX() <= start) {
            helper.fail("the cab's player did not drive the Train, which went " + (car.getX() - start), STAND);
        }
    }
}
