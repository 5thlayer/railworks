// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Span's Clearance in a world: a Span through a block is refused by hand and by a placed Pylon,
 * spending nothing, a block placed into a strung Span's Clearance is refused, and one line passes over
 * another. {@code ClearanceTest} holds the shape; these show the world's blocks reach it. All of these
 * tests share the {@code clearance_} prefix.
 */
final class ClearanceTests {

    /** Eight apart on the floor, one segment each: the Rail rests on top of them, its cars in the three blocks above. */
    private static final BlockPos A = new BlockPos(1, 1, 3);
    private static final BlockPos B = new BlockPos(9, 1, 3);
    /** Over the middle of the Span, where a car passes. */
    private static final BlockPos IN_THE_WAY = new BlockPos(5, 3, 3);

    private ClearanceTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("clearance_a_span_through_a_block_is_refused_and_spends_nothing", 20, ClearanceTests::spanRefused);
        tests.test("clearance_blocks_beside_and_under_the_rail_leave_it_clear", 20, ClearanceTests::besideAndUnder);
        tests.testOnItsOwn("clearance_a_placed_pylon_strings_no_span_through_a_block", 20,
                AutoStringTests.within(9.0, ClearanceTests::placedPylonRefused));
        tests.test("clearance_a_block_placed_into_a_spans_clearance_is_refused", 20, ClearanceTests::blockRefused);
        tests.test("clearance_a_block_placed_in_the_row_beside_a_span_stays", 20, ClearanceTests::blockBesideStays);
        tests.test("clearance_a_pylon_stacked_on_an_arm_is_not_in_the_way", 20, ClearanceTests::stackingAllowed);
        tests.test("two_wide_a_span_through_a_block_beside_the_rail_is_refused", 20, ClearanceTests::spanRefusedBeside);
        tests.test("two_wide_a_block_placed_beside_the_rail_is_refused", 20, ClearanceTests::blockRefusedBeside);
        tests.test("clearance_one_line_passes_over_another", 20, ClearanceTests::linesCross);
    }

    private static void spanRefused(GameTestHelper helper) {
        ServerPlayer player = player(helper, new ItemStack(RailworksRegistries.RAIL.get(), 20));
        helper.setBlock(IN_THE_WAY, Blocks.STONE);
        use(helper, player, A);
        use(helper, player, B);
        expectStrung(helper, A, B, false, "stringing through stone");
        if (player.getMainHandItem().getCount() != 20) {
            helper.fail("a refused Span spent Rail: " + player.getMainHandItem().getCount() + " of 20 left", A);
        }
        helper.succeed();
    }

    private static void besideAndUnder(GameTestHelper helper) {
        ServerPlayer player = player(helper, new ItemStack(RailworksRegistries.RAIL.get(), 20));
        // Beside the Rail below a car's floor, in the row two off it at a car's chests, where a hatch
        // stands, and under it.
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 3, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.STONE);
        use(helper, player, A);
        use(helper, player, B);
        expectStrung(helper, A, B, true, "stringing beside and over stone");
        helper.succeed();
    }

    private static void placedPylonRefused(GameTestHelper helper) {
        helper.setBlock(B, RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(IN_THE_WAY, Blocks.STONE);
        ServerPlayer player = player(helper, new ItemStack(RailworksRegistries.PYLON_ITEM.get(), 1));
        player.getInventory().setItem(9, new ItemStack(RailworksRegistries.RAIL.get(), 20));
        helper.setBlock(A, Blocks.AIR);
        BlockPos floor = helper.absolutePos(A.below());
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
        if (!helper.getBlockState(A).is(RailworksRegistries.PYLON.get())) {
            helper.fail("the Pylon was not placed", A);
        }
        expectStrung(helper, A, B, false, "placing a column with stone in the way");
        if (player.getInventory().getItem(9).getCount() != 20) {
            helper.fail("a Span not strung spent Rail", A);
        }
        helper.succeed();
    }

    private static void blockRefused(GameTestHelper helper) {
        string(helper);
        ServerPlayer player = player(helper, new ItemStack(Items.STONE, 4));
        // Under the Rail, so a block placed on top of it goes into the Clearance.
        BlockPos under = new BlockPos(5, 1, 3);
        helper.setBlock(under, Blocks.STONE);
        placeOnTop(helper, player, under);
        if (!helper.getBlockState(under.above()).isAir()) {
            helper.fail("a block went into the Span's Clearance", under.above());
        }
        if (player.getMainHandItem().getCount() != 4) {
            helper.fail("a refused block was spent", under.above());
        }
        helper.succeed();
    }

    /**
     * A block in the row two off the Rail at the height of a Cargo car's crates, where Stations,
     * hatches and Chargers stand (ADR 0010): it is outside the Clearance.
     */
    private static void blockBesideStays(GameTestHelper helper) {
        string(helper);
        ServerPlayer player = player(helper, new ItemStack(Items.STONE, 4));
        BlockPos underRow = new BlockPos(5, 2, 5);
        helper.setBlock(underRow, Blocks.STONE);
        placeOnTop(helper, player, underRow);
        if (!helper.getBlockState(underRow.above()).is(Blocks.STONE)) {
            helper.fail("a block in the row two off the Rail at a car's chests was refused", underRow.above());
        }
        helper.succeed();
    }

    /** A two-wide car reaches halfway into the blocks beside the Rail (ADR 0010): stone one off it is in the way. */
    private static void spanRefusedBeside(GameTestHelper helper) {
        ServerPlayer player = player(helper, new ItemStack(RailworksRegistries.RAIL.get(), 20));
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.STONE);
        use(helper, player, A);
        use(helper, player, B);
        expectStrung(helper, A, B, false, "stringing beside stone at a car's floor");
        helper.succeed();
    }

    /** A block placed one off the Rail at a car's floor goes into the two-wide Clearance, and is refused. */
    private static void blockRefusedBeside(GameTestHelper helper) {
        string(helper);
        ServerPlayer player = player(helper, new ItemStack(Items.STONE, 4));
        BlockPos beside = new BlockPos(5, 1, 4);
        helper.setBlock(beside, Blocks.STONE);
        placeOnTop(helper, player, beside);
        if (!helper.getBlockState(beside.above()).isAir()) {
            helper.fail("a block went into the Span's Clearance beside the Rail", beside.above());
        }
        if (player.getMainHandItem().getCount() != 4) {
            helper.fail("a refused block was spent", beside.above());
        }
        helper.succeed();
    }

    private static void stackingAllowed(GameTestHelper helper) {
        string(helper);
        ServerPlayer player = player(helper, new ItemStack(RailworksRegistries.PYLON_ITEM.get(), 1));
        BlockPos absolute = helper.absolutePos(A);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.NORTH, 0.5), Direction.NORTH, absolute, false));
        if (!helper.getBlockState(A.above()).is(RailworksRegistries.PYLON.get())) {
            helper.fail("a segment stacked on the Arm was refused", A.above());
        }
        expectStrung(helper, A.above(), B, true, "stacking a segment on the Arm");
        helper.succeed();
    }

    /** A line along z, its Arms five segments up, over the middle of one along x on the floor. */
    private static void linesCross(GameTestHelper helper) {
        BlockPos north = new BlockPos(5, 1, 0);
        BlockPos south = new BlockPos(5, 1, 8);
        for (int i = 0; i < 5; i++) {
            helper.setBlock(north.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
            helper.setBlock(south.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
        }
        string(helper);
        LevelRails rails = LevelRails.of(helper.getLevel());
        var over = rails.string(helper.getLevel(), helper.absolutePos(north.above(4)), helper.absolutePos(south.above(4)));
        if (over.result() != Stringing.Result.STRUNG) {
            helper.fail("the line over the other was " + over.result(), north);
        }
        helper.succeed();
    }

    /** The Span from A to B, strung by the command's rule, the Clearance checked as a player's click checks it. */
    private static void string(GameTestHelper helper) {
        helper.setBlock(A, RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(B, RailworksRegistries.PYLON.get().defaultBlockState());
        var outcome = LevelRails.of(helper.getLevel()).string(helper.getLevel(), helper.absolutePos(A), helper.absolutePos(B));
        if (outcome.result() != Stringing.Result.STRUNG) {
            helper.fail("the Span from A to B was " + outcome.result(), A);
        }
    }

    /** A survival player beside the Span, with the stack in hand and both Pylons standing. */
    private static ServerPlayer player(GameTestHelper helper, ItemStack held) {
        helper.setBlock(A, RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(B, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(new BlockPos(5, 1, 1));
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.NORTH, 0.5), Direction.NORTH, absolute, false));
    }

    private static void placeOnTop(GameTestHelper helper, ServerPlayer player, BlockPos on) {
        BlockPos absolute = helper.absolutePos(on);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute, false));
    }

    private static void expectStrung(GameTestHelper helper, BlockPos a, BlockPos b, boolean strung, String after) {
        if (LevelRails.of(helper.getLevel()).contains(helper.absolutePos(a), helper.absolutePos(b)) != strung) {
            helper.fail("the Span is " + (strung ? "not " : "") + "strung after " + after, a);
        }
    }
}
