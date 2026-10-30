// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Getting off a Locomotive on a Span far above the ground (#17): whoever gets off, sneaking, led out
 * or put off by the car breaking, lands on the first floor beside their seat two off the Rail, else
 * below, at any height, on water's top and never in lava, and stays aboard when there is nowhere. Each
 * stands on a Span strung {@link #HEIGHT} blocks up, past the 24 blocks getting off once looked down,
 * the car facing east; the ground is the platform's stone floor. All share the {@code get_off_} prefix.
 */
final class GettingOffTests {

    private static final BlockPos A = new BlockPos(-1, 1, 3);
    private static final BlockPos B = new BlockPos(11, 1, 3);
    /** The columns' height in segments, the Span on their tops: well past 24 blocks over the floor. */
    private static final int HEIGHT = 30;
    /** On the floor, where each test is reported. */
    private static final BlockPos FLOOR = new BlockPos(4, 1, 4);
    /** The rows two off the Rail, which runs along z 3: south and north of it. */
    private static final int SOUTH_ROW = 5;
    private static final int NORTH_ROW = 1;

    private GettingOffTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("get_off_a_driver_off_a_high_span_lands_on_the_ground", 20, GettingOffTests::stopped);
        tests.test("get_off_a_driver_off_a_moving_train_lands_on_the_ground", 20, GettingOffTests::moving);
        tests.test("get_off_a_walkway_two_off_the_rail_on_the_side_looked_to", 20, helper -> walkway(helper, true));
        tests.test("get_off_a_walkway_two_off_the_rail_on_the_other_side", 20, helper -> walkway(helper, false));
        tests.test("get_off_a_rider_gets_off_beside_the_bay", 20, GettingOffTests::riderBesideBay);
        tests.test("get_off_a_mob_led_out_lands_unhurt_on_the_lead", 20, GettingOffTests::ledOut);
        tests.test("get_off_onto_the_top_of_water", 20, GettingOffTests::water);
        tests.test("get_off_never_into_lava", 20, GettingOffTests::lava);
        tests.test("get_off_never_under_a_wall_beside_the_car", 20, GettingOffTests::wall);
        // On its own: it waits out the level's ticks, and a test beside it clearing its neighbours' Pylons,
        // as AutoStringTests' does, would cut the columns and break the car under the driver meanwhile.
        tests.testOnItsOwn("get_off_nowhere_to_land_keeps_them_aboard", 20, GettingOffTests::nowhere);
        tests.test("get_off_a_span_cut_under_the_train_puts_the_driver_down", 20, GettingOffTests::spanCut);
        tests.test("get_off_a_broken_car_puts_the_driver_down", 20, GettingOffTests::carBroken);
    }

    private static void stopped(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        lookSouth(driver);
        getOff(driver);
        expectLanded(helper, driver, car, CarBody.CAB_SEAT, 1, floorY(helper), "the driver");
        helper.succeed();
    }

    private static void moving(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        LocomotiveTests.fill(helper, car, new ItemStack(Items.COAL, 4));
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        LocomotiveTests.press(driver, true, false);
        LocomotiveTests.run(car, 10);
        if (car.train().speed() <= 0.0) {
            helper.fail("the Train did not get going", FLOOR);
        }
        lookSouth(driver);
        getOff(driver);
        expectLanded(helper, driver, car, CarBody.CAB_SEAT, 1, floorY(helper), "the driver off a moving Train");
        helper.succeed();
    }

    /** A walkway level with the car's floor two off the Rail, on the side looked to or only on the other. */
    private static void walkway(GameTestHelper helper, boolean lookedTo) {
        LocomotiveEntity car = raised(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        int row = lookedTo ? SOUTH_ROW : NORTH_ROW;
        // Its top at or just under the car's floor.
        int y = (int) Math.floor(car.getY() + CarBody.FLOOR_TOP) - 1;
        for (int x = 0; x <= 8; x++) {
            BlockPos at = helper.absolutePos(new BlockPos(x, 0, row));
            helper.getLevel().setBlockAndUpdate(new BlockPos(at.getX(), y, at.getZ()), Blocks.STONE.defaultBlockState());
        }
        lookSouth(driver);
        getOff(driver);
        expectLanded(helper, driver, car, CarBody.CAB_SEAT, lookedTo ? 1 : -1, y + 1.0, "the driver by the walkway");
        helper.succeed();
    }

    private static void riderBesideBay(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        LocomotiveTests.board(helper, car);
        ServerPlayer rider = LocomotiveTests.board(helper, car);
        lookSouth(rider);
        getOff(rider);
        expectLanded(helper, rider, car, CarBody.BAY_SPOT, 1, floorY(helper), "the rider");
        helper.succeed();
    }

    private static void ledOut(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        Pig pig = helper.spawn(EntityType.PIG, FLOOR);
        if (!pig.startRiding(car)) {
            helper.fail("the pig could not be put in the bay", FLOOR);
        }
        ServerPlayer leader = player(helper);
        leader.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        car.interact(leader, InteractionHand.MAIN_HAND, Vec3.ZERO);
        // A mob faces the car's way and takes its right first: south, for a car facing east.
        expectLanded(helper, pig, car, CarBody.BAY_SPOT, 1, floorY(helper), "the pig");
        if (pig.getLeashHolder() != leader) {
            helper.fail("the pig led out is held by " + pig.getLeashHolder() + ", not its leader", FLOOR);
        }
        helper.succeed();
    }

    private static void water(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        fillStrip(helper, SOUTH_ROW - 1, SOUTH_ROW + 1, Blocks.WATER);
        lookSouth(driver);
        getOff(driver);
        // Not on the bed, at the block's bottom, but at the water's top.
        BlockPos water = helper.absolutePos(new BlockPos(6, 1, SOUTH_ROW));
        double top = floorY(helper) + helper.getLevel().getFluidState(water).getHeight(helper.getLevel(), water);
        expectLanded(helper, driver, car, CarBody.CAB_SEAT, 1, top, "the driver over water");
        helper.succeed();
    }

    private static void lava(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        fillStrip(helper, SOUTH_ROW - 1, SOUTH_ROW + 1, Blocks.LAVA);
        lookSouth(driver);
        getOff(driver);
        expectLanded(helper, driver, car, CarBody.CAB_SEAT, -1, floorY(helper), "the driver over lava");
        helper.succeed();
    }

    /** A wall two off the Rail from the floor to over the car: no getting off into it, or under it, but on the other side. */
    private static void wall(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        int top = (int) Math.ceil(car.getY() + CarBody.HEIGHT);
        for (int x = 0; x <= 8; x++) {
            BlockPos at = helper.absolutePos(new BlockPos(x, 1, SOUTH_ROW));
            for (int y = at.getY() + 2; y <= top; y++) {
                helper.getLevel().setBlockAndUpdate(new BlockPos(at.getX(), y, at.getZ()), Blocks.STONE.defaultBlockState());
            }
        }
        lookSouth(driver);
        getOff(driver);
        expectLanded(helper, driver, car, CarBody.CAB_SEAT, -1, floorY(helper), "the driver by a wall");
        helper.succeed();
    }

    /**
     * Lava under every spot: a player sneaking stays seated, over the level's own ticks, the car's and
     * then theirs; and a mob led out stays in the bay with the lead in hand.
     */
    private static void nowhere(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        Pig pig = helper.spawn(EntityType.PIG, FLOOR);
        pig.startRiding(car);
        fillStrip(helper, 0, 8, Blocks.LAVA);
        ServerPlayer leader = player(helper);
        leader.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        car.interact(leader, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (pig.getVehicle() != car) {
            helper.fail("a pig with nowhere to land was led out, to " + pig.position(), FLOOR);
        }
        if (!leader.getMainHandItem().is(Items.LEAD) || leader.getMainHandItem().getCount() != 1) {
            helper.fail("the refused lead was used up: " + leader.getMainHandItem(), FLOOR);
        }
        driver.setShiftKeyDown(true);
        helper.runAfterDelay(3, () -> {
            if (driver.getVehicle() != car) {
                helper.fail("a driver with nowhere to land got off, to " + driver.position(), FLOOR);
            }
            if (driver.isShiftKeyDown()) {
                helper.fail("the driver's sneak was not let go", FLOOR);
            }
            helper.succeed();
        });
    }

    private static void spanCut(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        lookSouth(driver);
        Vec3 at = car.position();
        LevelRails.of(helper.getLevel()).network().remove(arm(helper, A), arm(helper, B));
        LocomotiveTests.step(car);
        if (!car.isRemoved()) {
            helper.fail("the car whose Span was cut stayed in the world", FLOOR);
        }
        expectLanded(helper, driver, at, CarBody.CAB_SEAT, 1, floorY(helper), "the driver of a derailed car");
        helper.succeed();
    }

    private static void carBroken(GameTestHelper helper) {
        LocomotiveEntity car = raised(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        lookSouth(driver);
        Vec3 at = car.position();
        car.kill(helper.getLevel());
        expectLanded(helper, driver, at, CarBody.CAB_SEAT, 1, floorY(helper), "the driver of a broken car");
        helper.succeed();
    }

    // ---- setup ----

    /** Two columns {@link #HEIGHT} tall, a Span between their tops, and a Burner Locomotive at its middle facing east. */
    private static LocomotiveEntity raised(GameTestHelper helper) {
        for (int i = 0; i < HEIGHT; i++) {
            helper.setBlock(A.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
            helper.setBlock(B.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
        }
        // The structure's roof is a barrier layer, which would catch whoever gets off: clear it.
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
            }
        }
        Arm a = arm(helper, A);
        Arm b = arm(helper, B);
        Span span = Span.of(a, b, 12);
        LevelRails rails = LevelRails.of(helper.getLevel());
        rails.network().add(span);
        Locomotive locomotive = Locomotive.burner();
        Train train = Train.of(new Place(span, b, span.length() / 2.0), locomotive);
        rails.add(train);
        LocomotiveEntity view = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        view.setCar(train, locomotive);
        helper.getLevel().addFreshEntity(view);
        return view;
    }

    private static Arm arm(GameTestHelper helper, BlockPos column) {
        return PylonColumn.arm(helper.absolutePos(column.above(HEIGHT - 1)));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** {@code block} over the platform's floor, along the whole plot, from row {@code from} to {@code to}. */
    private static void fillStrip(GameTestHelper helper, int from, int to, Block block) {
        for (int x = 0; x <= 8; x++) {
            for (int z = from; z <= to; z++) {
                helper.setBlock(new BlockPos(x, 1, z), block);
            }
        }
    }

    /** South, a little ahead of square to the car facing east. */
    private static void lookSouth(ServerPlayer player) {
        player.setYRot(-10.0F);
    }

    private static void getOff(ServerPlayer player) {
        player.setShiftKeyDown(true);
        player.rideTick();
    }

    private static double floorY(GameTestHelper helper) {
        return helper.absolutePos(FLOOR).getY();
    }

    private static void expectLanded(GameTestHelper helper, LivingEntity who, LocomotiveEntity car, CarBody.Point seat,
                                     int south, double y, String name) {
        expectLanded(helper, who, car.position(), seat, south, y, name);
    }

    /**
     * {@code who} got off, unhurt, beside {@code seat} of a car that stood at {@code car} facing east, on
     * its {@code south} side (1, or -1 north), two off the Rail, on a floor at {@code y}.
     */
    private static void expectLanded(GameTestHelper helper, LivingEntity who, Vec3 car, CarBody.Point seat,
                                     int south, double y, String name) {
        if (who.getVehicle() != null) {
            helper.fail(name + " did not get off", FLOOR);
        }
        double across = (who.getZ() - car.z) * south;
        if (Math.abs(who.getX() - (car.x + seat.z())) > 1e-3 || across < Station.ROW_OFF - 0.5 + who.getBbWidth() / 2.0 || across > Station.ROW_OFF + 0.5
                || Math.abs(who.getY() - y) > 1e-3) {
            helper.fail(name + " got off at " + who.position().subtract(car) + " from the car's middle, not two off the Rail beside "
                    + "the seat at x " + seat.z() + " on the " + (south > 0 ? "south" : "north") + " side at y " + (y - car.y), FLOOR);
        }
        if (who.getHealth() < who.getMaxHealth() || who.fallDistance > 0.0) {
            helper.fail(name + " was hurt getting off: health " + who.getHealth() + ", falling " + who.fallDistance, FLOOR);
        }
    }
}
