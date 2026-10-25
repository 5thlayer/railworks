// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.function.Consumer;

import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailPurse;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Pylon placed by a player strings itself to the nearest free Arm, for Rail from the placer's
 * inventory. {@code AutoStringingTest} holds the choice; these hold what the server does with it.
 * All of them share the {@code autostring_} prefix.
 */
final class AutoStringTests {

    /** Eight apart, so the Span costs 8. */
    private static final BlockPos PLACED = new BlockPos(1, 1, 3);
    private static final BlockPos NEIGHBOUR = new BlockPos(9, 1, 3);

    private AutoStringTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("autostring_a_placed_column_strings_to_the_nearest_arm", 20, within(9.0, AutoStringTests::strings));
        tests.test("autostring_picks_the_nearest_of_two", 20, within(14.0, AutoStringTests::nearest));
        tests.test("autostring_charges_a_creative_player_too", 20, within(9.0, AutoStringTests::creativePays));
        tests.test("autostring_with_too_little_rail_places_the_pylon_alone", 20, within(9.0, AutoStringTests::shortOfRail));
        tests.test("autostring_never_strings_past_the_grade_limit", 20, within(9.0, AutoStringTests::tooSteep));
        tests.test("autostring_never_strings_past_the_bend_limit", 20, within(9.0, AutoStringTests::tooSharp));
        tests.test("autostring_strings_nothing_when_sneaking", 20, within(9.0, AutoStringTests::sneaking));
        tests.test("autostring_strings_nothing_with_no_arm_in_reach", 20, within(9.0, AutoStringTests::nothingInReach));
        tests.test("autostring_never_strings_when_stacking_onto_a_column", 20, within(9.0, AutoStringTests::stackingNever));
    }

    /**
     * Runs a test with the length limit cut to {@code limit} and every Pylon the other tests left
     * standing within reach cleared: the tests share one world, a few blocks apart.
     */
    private static Consumer<GameTestHelper> within(double limit, Consumer<GameTestHelper> body) {
        return helper -> {
            SpanRules.configure(limit);
            try {
                clearNeighbours(helper);
                body.accept(helper);
            } finally {
                SpanRules.configure(SpanRules.DEFAULT_LENGTH_LIMIT);
            }
        };
    }

    private static void strings(GameTestHelper helper) {
        helper.setBlock(NEIGHBOUR, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = player(helper, 20, GameType.SURVIVAL);
        place(helper, player, PLACED);
        expectPylon(helper, PLACED);
        expectStrung(helper, PLACED, NEIGHBOUR, true, "placing a column 8 from a Pylon");
        expectRail(helper, player, 12, "an 8 block auto Span");
        helper.succeed();
    }

    private static void nearest(GameTestHelper helper) {
        BlockPos farther = new BlockPos(14, 1, 3);
        helper.setBlock(farther, RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(NEIGHBOUR, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = player(helper, 40, GameType.SURVIVAL);
        place(helper, player, PLACED);
        expectStrung(helper, PLACED, NEIGHBOUR, true, "placing between two Pylons' reach");
        expectStrung(helper, PLACED, farther, false, "placing: only one Span is strung");
        helper.succeed();
    }

    private static void creativePays(GameTestHelper helper) {
        helper.setBlock(NEIGHBOUR, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = player(helper, 20, GameType.CREATIVE);
        place(helper, player, PLACED);
        expectStrung(helper, PLACED, NEIGHBOUR, true, "a creative player placing a column");
        expectRail(helper, player, 12, "a creative player's auto Span");
        helper.succeed();
    }

    private static void shortOfRail(GameTestHelper helper) {
        helper.setBlock(NEIGHBOUR, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = player(helper, 7, GameType.SURVIVAL);
        place(helper, player, PLACED);
        expectPylon(helper, PLACED);
        expectStrung(helper, PLACED, NEIGHBOUR, false, "placing with 7 Rail for an 8 Rail Span");
        expectRail(helper, player, 7, "a Span that was not strung");
        helper.succeed();
    }

    private static void tooSteep(GameTestHelper helper) {
        // The neighbour's Arm four segments up: 3 of rise over 8, past the 1 in 4 limit.
        for (int i = 0; i < 4; i++) {
            helper.setBlock(NEIGHBOUR.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
        }
        ServerPlayer player = player(helper, 20, GameType.SURVIVAL);
        place(helper, player, PLACED);
        expectPylon(helper, PLACED);
        expectStrung(helper, PLACED, NEIGHBOUR.above(3), false, "placing a column 3 below and 8 from an Arm");
        expectRail(helper, player, 20, "a Span past the grade limit");
        helper.succeed();
    }

    private static void tooSharp(GameTestHelper helper) {
        // The neighbour's Span runs north, out of reach; on to the placed column would turn a right angle.
        helper.setBlock(NEIGHBOUR, RailworksRegistries.PYLON.get().defaultBlockState());
        BlockPos north = NEIGHBOUR.offset(0, 0, 8);
        helper.setBlock(north, RailworksRegistries.PYLON.get().defaultBlockState());
        LevelRails rails = LevelRails.of(helper.getLevel());
        rails.network().add(Span.of(PylonColumn.arm(helper.absolutePos(NEIGHBOUR)),
                PylonColumn.arm(helper.absolutePos(north)), 8));
        try {
            ServerPlayer player = player(helper, 20, GameType.SURVIVAL);
            place(helper, player, PLACED);
            expectPylon(helper, PLACED);
            expectStrung(helper, PLACED, NEIGHBOUR, false, "placing a column a right angle off a line");
            expectRail(helper, player, 20, "a Span past the bend limit");
        } finally {
            rails.network().removeAllAt(PylonColumn.arm(helper.absolutePos(NEIGHBOUR)));
        }
        helper.succeed();
    }

    private static void sneaking(GameTestHelper helper) {
        helper.setBlock(NEIGHBOUR, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = player(helper, 20, GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        place(helper, player, PLACED);
        expectPylon(helper, PLACED);
        expectStrung(helper, PLACED, NEIGHBOUR, false, "placing while sneaking");
        expectRail(helper, player, 20, "a sneaking placement");
        helper.succeed();
    }

    private static void nothingInReach(GameTestHelper helper) {
        ServerPlayer player = player(helper, 20, GameType.SURVIVAL);
        place(helper, player, PLACED);
        expectPylon(helper, PLACED);
        if (!LevelRails.of(helper.getLevel()).network().spansAt(PylonColumn.arm(helper.absolutePos(PLACED))).isEmpty()) {
            helper.fail("a Span was strung with no Pylon in reach", PLACED);
        }
        expectRail(helper, player, 20, "a placement with nothing in reach");
        helper.succeed();
    }

    private static void stackingNever(GameTestHelper helper) {
        helper.setBlock(PLACED, RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(NEIGHBOUR, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = player(helper, 20, GameType.SURVIVAL);
        // Using the Pylon on the column stacks a segment on it.
        BlockPos absolute = helper.absolutePos(PLACED);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false));
        if (!helper.getBlockState(PLACED.above()).is(RailworksRegistries.PYLON.get())) {
            helper.fail("the segment was not stacked", PLACED);
        }
        expectStrung(helper, PLACED.above(), NEIGHBOUR, false, "stacking a segment");
        expectRail(helper, player, 20, "stacking a segment");
        helper.succeed();
    }

    private static void clearNeighbours(GameTestHelper helper) {
        BlockPos centre = helper.absolutePos(PLACED);
        for (Arm arm : PylonColumn.armsNear(helper.getLevel(), centre, 48)) {
            BlockPos pos = PylonColumn.block(arm);
            while (PylonColumn.isPylon(helper.getLevel(), pos)) {
                helper.getLevel().removeBlock(pos, false);
                pos = pos.below();
            }
        }
    }

    /** A player with a Pylon in hand and Rail in the inventory. */
    private static ServerPlayer player(GameTestHelper helper, int rail, GameType mode) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        BlockPos stand = helper.absolutePos(new BlockPos(5, 1, 1));
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.PYLON_ITEM.get(), 4));
        player.getInventory().setItem(9, new ItemStack(RailworksRegistries.RAIL.get(), rail));
        return player;
    }

    /** Places the held Pylon on the floor under {@code at}. */
    private static void place(GameTestHelper helper, ServerPlayer player, BlockPos at) {
        BlockPos floor = helper.absolutePos(at.below());
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
    }

    private static void expectPylon(GameTestHelper helper, BlockPos at) {
        if (!helper.getBlockState(at).is(RailworksRegistries.PYLON.get())) {
            helper.fail("the Pylon was not placed", at);
        }
    }

    private static void expectStrung(GameTestHelper helper, BlockPos a, BlockPos b, boolean strung, String after) {
        if (LevelRails.of(helper.getLevel()).contains(helper.absolutePos(a), helper.absolutePos(b)) != strung) {
            helper.fail("the Span is " + (strung ? "not " : "") + "strung after " + after, a);
        }
    }

    private static void expectRail(GameTestHelper helper, ServerPlayer player, int expected, String after) {
        if (RailPurse.count(player) != expected) {
            helper.fail("the player holds " + RailPurse.count(player) + " Rail, expected " + expected + " after " + after, PLACED);
        }
    }
}
