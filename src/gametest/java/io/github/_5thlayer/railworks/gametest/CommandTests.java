// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * The {@code /railworks} commands a Gallery pack scene builds with: {@code span} strings a Span for
 * no Rail under a player's limits, and {@code car} sets a car as the item does, coupling it beside a
 * Train at rest. A refusal throws, as a vanilla command's does, so a function's trace names it.
 */
final class CommandTests {

    /**
     * Two one-segment columns, eleven blocks apart along x: room for two five-block cars coupled,
     * the one at {@link #ON_RAIL} and one behind it.
     */
    private static final BlockPos WEST = new BlockPos(-1, 1, 3);
    private static final BlockPos EAST = new BlockPos(10, 1, 3);
    /**
     * A column three segments high, three blocks back along x from {@link #EAST}: a Span to it climbs
     * 2 in 3. Back, not on past it, which would stand in the next test's plot.
     */
    private static final BlockPos STEEP = EAST.west(3);
    /**
     * The block a car riding the Span between the Arms stands in, its front half a block short of
     * {@link #EAST}'s end, a car's length and more from {@link #WEST}.
     */
    private static final BlockPos ON_RAIL = new BlockPos(7, 2, 3);

    /** Facing east, as a command's yaw: Minecraft's yaw turns from south through west. */
    private static final float EAST_YAW = -90.0F;

    private CommandTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("gallery_command_span_strings_a_span_for_no_rail", 1, CommandTests::spanStrings);
        tests.test("gallery_command_span_refuses_a_span_past_the_grade_limit", 1, CommandTests::spanRefusesSteep);
        tests.test("gallery_command_span_refuses_a_span_past_the_length_limit", 1, CommandTests::spanRefusesLong);
        tests.test("gallery_command_span_refuses_a_span_past_the_bend_limit", 1, CommandTests::spanRefusesSharp);
        tests.test("gallery_command_span_refuses_a_block_that_is_no_pylon", 1, CommandTests::spanRefusesNoPylon);
        tests.test("gallery_command_car_sets_a_car_on_the_rail_as_a_train_of_its_own", 1, CommandTests::carSets);
        tests.test("gallery_command_car_beside_a_train_at_rest_couples_into_it", 1, CommandTests::carCouples);
        tests.test("gallery_command_car_refuses_where_the_item_would", 1, CommandTests::carRefused);
    }

    private static void spanStrings(GameTestHelper helper) {
        columns(helper);
        run(helper, "railworks span %s %s", WEST, EAST);
        var network = LevelRails.of(helper.getLevel()).network();
        helper.assertTrue(network.contains(PylonColumn.arm(helper.absolutePos(WEST)), PylonColumn.arm(helper.absolutePos(EAST))),
                "the command strung no Span between the Arms");
        helper.succeed();
    }

    private static void spanRefusesSteep(GameTestHelper helper) {
        columns(helper);
        for (int i = 0; i < 3; i++) {
            helper.setBlock(STEEP.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
        }
        refused(helper, "railworks span %s %s", EAST, STEEP);
        helper.assertFalse(LevelRails.of(helper.getLevel()).network().contains(PylonColumn.arm(helper.absolutePos(EAST)),
                PylonColumn.arm(helper.absolutePos(STEEP.above(2)))), "a refused Span was strung");
        helper.succeed();
    }

    private static void spanRefusesLong(GameTestHelper helper) {
        // 70 blocks apart on x, past the 32 limit; the far column stands where the test never looks.
        BlockPos far = WEST.offset(70, 0, 0);
        helper.setBlock(WEST, RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(far, RailworksRegistries.PYLON.get().defaultBlockState());
        // A far column in an unloaded chunk would be air, and the command's refusal would be for that.
        if (!helper.getBlockState(far).is(RailworksRegistries.PYLON.get())) {
            helper.fail("the far Pylon did not stand", far);
        }
        refused(helper, "railworks span %s %s", WEST, far);
        helper.assertFalse(LevelRails.of(helper.getLevel()).network().contains(PylonColumn.arm(helper.absolutePos(WEST)),
                PylonColumn.arm(helper.absolutePos(far))), "a refused Span was strung");
        helper.succeed();
    }

    private static void spanRefusesSharp(GameTestHelper helper) {
        strung(helper);
        // From the east Arm, back and to the side: a turn of 135 degrees.
        BlockPos back = EAST.offset(-4, 0, 4);
        helper.setBlock(back, RailworksRegistries.PYLON.get().defaultBlockState());
        refused(helper, "railworks span %s %s", EAST, back);
        helper.assertFalse(LevelRails.of(helper.getLevel()).network().contains(PylonColumn.arm(helper.absolutePos(EAST)),
                PylonColumn.arm(helper.absolutePos(back))), "a refused Span was strung");
        helper.succeed();
    }

    private static void spanRefusesNoPylon(GameTestHelper helper) {
        columns(helper);
        helper.setBlock(STEEP, Blocks.STONE);
        refused(helper, "railworks span %s %s", WEST, STEEP);
        helper.succeed();
    }

    private static void carSets(GameTestHelper helper) {
        strung(helper);
        run(helper, "railworks car locomotive %s " + EAST_YAW, ON_RAIL);
        var cars = helper.getLevel().getEntitiesOfClass(CarEntity.class, helper.getBounds());
        helper.assertTrue(cars.size() == 1, "the command set " + cars.size() + " cars, not one");
        Train train = cars.getFirst().train();
        helper.assertTrue(train != null && train.cars().size() == 1, "the car is in no Train of its own: " + train);
        helper.succeed();
    }

    private static void carCouples(GameTestHelper helper) {
        strung(helper);
        run(helper, "railworks car locomotive %s " + EAST_YAW, ON_RAIL);
        // A car's length behind it, where a Cargo car's middle stands coupled.
        run(helper, "railworks car cargo_car %s " + EAST_YAW, ON_RAIL.west(5));
        var cars = helper.getLevel().getEntitiesOfClass(CarEntity.class, helper.getBounds());
        var trains = cars.stream().map(CarEntity::train).distinct().toList();
        helper.assertTrue(trains.size() == 1 && trains.getFirst() != null && trains.getFirst().cars().size() == 2,
                "the Cargo car did not couple into the Locomotive's Train: " + trains);
        double apart = Math.abs(cars.get(0).getX() - cars.get(1).getX());
        helper.assertTrue(Math.abs(apart - 5.0) < 1e-6, "the coupled cars stand " + apart + " apart, not five");
        helper.succeed();
    }

    private static void carRefused(GameTestHelper helper) {
        strung(helper);
        // Off the Rail: a block beside it.
        refused(helper, "railworks car locomotive %s " + EAST_YAW, ON_RAIL.north(3));
        // In a block.
        helper.setBlock(ON_RAIL, Blocks.STONE);
        refused(helper, "railworks car locomotive %s " + EAST_YAW, ON_RAIL);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(CarEntity.class, helper.getBounds()).isEmpty(), "a refused car was set");
        helper.succeed();
    }

    private static void columns(GameTestHelper helper) {
        helper.setBlock(WEST, RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(EAST, RailworksRegistries.PYLON.get().defaultBlockState());
        // The structure's roof is a barrier layer; a car rides up into it, so clear it over the Span.
        for (int x = -2; x <= 11; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
            }
        }
    }

    private static void strung(GameTestHelper helper) {
        columns(helper);
        run(helper, "railworks span %s %s", WEST, EAST);
    }

    static void run(GameTestHelper helper, String command, BlockPos... at) {
        try {
            dispatch(helper, command, at);
        } catch (CommandSyntaxException e) {
            helper.fail("/" + command(helper, command, at) + " failed: " + e.getMessage());
        }
    }

    static void refused(GameTestHelper helper, String command, BlockPos... at) {
        try {
            dispatch(helper, command, at);
        } catch (CommandSyntaxException e) {
            return;
        }
        helper.fail("/" + command(helper, command, at) + " was not refused");
    }

    private static void dispatch(GameTestHelper helper, String command, BlockPos... at) throws CommandSyntaxException {
        CommandSourceStack source = GalleryCheck.operator(helper, helper.absolutePos(BlockPos.ZERO.above()));
        helper.getLevel().getServer().getCommands().getDispatcher().execute(command(helper, command, at), source);
    }

    /** {@code command} with each {@code %s} the next of {@code at}, relative to the test, written absolute. */
    private static String command(GameTestHelper helper, String command, BlockPos... at) {
        Object[] written = new Object[at.length];
        for (int i = 0; i < at.length; i++) {
            BlockPos pos = helper.absolutePos(at[i]);
            written[i] = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }
        return String.format(command, written);
    }
}
