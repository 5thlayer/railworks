// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.SequencedSet;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Clearance;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A strung Span's Clearance kept clear past a player's placed block: a Pylon segment stacked on an
 * Arm is refused where it would raise a Span past the grade limit or into a block, or stand in
 * another Span's Clearance, and one set there anyway cuts the Arm's Spans; a piston pushes no block
 * into a Clearance, nor its head; water reaching lava makes no block in one; and a cactus grows no
 * higher than one. All of these tests share the {@code clearance_kept_} prefix.
 */
final class ClearanceKeptTests {

    /** Eight apart on the floor, one segment each: the Rail rests on top of them, its cars in the three blocks above. */
    private static final BlockPos A = new BlockPos(1, 1, 3);
    private static final BlockPos B = new BlockPos(9, 1, 3);
    /** B's column, three segments high: a Span from A to its top climbs two blocks in eight, right at the grade limit. */
    private static final BlockPos HIGH_B = new BlockPos(9, 3, 3);

    private ClearanceKeptTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("clearance_kept_a_segment_raising_a_span_into_a_block_is_refused", 20, ClearanceKeptTests::raisedIntoABlock);
        tests.test("clearance_kept_a_segment_raising_a_span_past_the_grade_limit_is_refused", 20,
                ClearanceKeptTests::raisedTooSteep);
        tests.test("clearance_kept_a_sneaking_players_segment_past_the_grade_limit_is_refused", 20,
                ClearanceKeptTests::sneakingRaisedTooSteep);
        tests.test("clearance_kept_a_segment_set_past_a_rule_cuts_the_spans_and_drops_their_rail", 20,
                ClearanceKeptTests::setPastARule);
        tests.test("clearance_kept_a_segment_in_another_spans_clearance_is_refused", 20,
                ClearanceKeptTests::segmentInAnotherSpan);
        tests.test("clearance_kept_a_piston_pushes_no_block_into_a_span", 20, ClearanceKeptTests::pistonPush);
        tests.test("clearance_kept_a_pistons_head_stays_out_of_a_span", 20, ClearanceKeptTests::pistonHead);
        tests.test("clearance_kept_water_meeting_lava_makes_no_block_in_a_span", 20, ClearanceKeptTests::waterMeetsLava);
        tests.test("clearance_kept_a_cactus_grows_no_higher_than_a_span", 20, ClearanceKeptTests::cactus);
    }

    private static void raisedIntoABlock(GameTestHelper helper) {
        string(helper, A, B);
        BlockPos stone = raisedOnly(helper);
        helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        ServerPlayer player = player(helper, false);
        expectRefusedPlan(helper, player, A);
        stackOnto(helper, player, A, Direction.NORTH);
        if (!helper.getBlockState(A.above()).isAir()) {
            helper.fail("a segment raised the Span into the stone at " + stone, A.above());
        }
        if (player.getMainHandItem().getCount() != 4) {
            helper.fail("a refused segment was spent", A.above());
        }
        expectStrung(helper, A, B, true, "a refused segment");
        succeed(helper);
    }

    private static void raisedTooSteep(GameTestHelper helper) {
        column(helper, HIGH_B, 3);
        string(helper, A, HIGH_B);
        ServerPlayer player = player(helper, false);
        expectRefusedPlan(helper, player, HIGH_B);
        stackOnto(helper, player, HIGH_B, Direction.NORTH);
        if (!helper.getBlockState(HIGH_B.above()).isAir()) {
            helper.fail("a segment raised the Span past the grade limit", HIGH_B.above());
        }
        expectStrung(helper, A, HIGH_B, true, "a refused segment");
        succeed(helper);
    }

    private static void sneakingRaisedTooSteep(GameTestHelper helper) {
        column(helper, HIGH_B, 3);
        string(helper, A, HIGH_B);
        ServerPlayer player = player(helper, true);
        stackOnto(helper, player, HIGH_B, Direction.UP);
        if (!helper.getBlockState(HIGH_B.above()).isAir()) {
            helper.fail("a sneaking player's segment raised the Span past the grade limit", HIGH_B.above());
        }
        expectStrung(helper, A, HIGH_B, true, "a refused segment");
        succeed(helper);
    }

    private static void setPastARule(GameTestHelper helper) {
        column(helper, HIGH_B, 3);
        string(helper, A, HIGH_B);
        helper.setBlock(HIGH_B.above(), RailworksRegistries.PYLON.get().defaultBlockState());
        expectStrung(helper, A, HIGH_B, false, "a segment set past the grade limit");
        expectStrung(helper, A, HIGH_B.above(), false, "a segment set past the grade limit");
        int rail = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(HIGH_B)).inflate(2))
                .stream().filter(item -> item.getItem().is(RailworksRegistries.RAIL.get()))
                .mapToInt(item -> item.getItem().getCount()).sum();
        if (rail != 9) {
            helper.fail("the cut Span dropped " + rail + " Rail, not the 9 it cost", HIGH_B);
        }
        succeed(helper);
    }

    /** A lone Pylon under the middle of the Span: a segment on it would stand where the Span's cars pass. */
    private static void segmentInAnotherSpan(GameTestHelper helper) {
        string(helper, A, B);
        BlockPos under = new BlockPos(5, 1, 3);
        helper.setBlock(under, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = player(helper, false);
        expectRefusedPlan(helper, player, under);
        stackOnto(helper, player, under, Direction.NORTH);
        if (!helper.getBlockState(under.above()).isAir()) {
            helper.fail("a segment went into the Span's Clearance", under.above());
        }
        succeed(helper);
    }

    /** One piston pushes stone toward the Rail and stays put; one beside it pushes stone along it, clear of the cars. */
    private static void pistonPush(GameTestHelper helper) {
        string(helper, A, B);
        BlockPos toward = new BlockPos(5, 2, 5);
        helper.setBlock(toward.north(), Blocks.STONE);
        piston(helper, toward, Direction.NORTH);
        BlockPos along = new BlockPos(2, 2, 6);
        helper.setBlock(along.east(), Blocks.STONE);
        piston(helper, along, Direction.EAST);
        helper.runAfterDelay(4, () -> {
            if (!helper.getBlockState(toward.north()).is(Blocks.STONE) || !helper.getBlockState(toward.north(2)).isAir()) {
                helper.fail("a piston pushed stone into the Span's Clearance", toward.north(2));
            }
            if (!helper.getBlockState(along.east(2)).is(Blocks.STONE)) {
                helper.fail("a piston clear of the Span did not push", along.east(2));
            }
            succeed(helper);
        });
    }

    private static void pistonHead(GameTestHelper helper) {
        string(helper, A, B);
        BlockPos at = new BlockPos(5, 2, 4);
        piston(helper, at, Direction.NORTH);
        helper.runAfterDelay(4, () -> {
            if (helper.getBlockState(at).getValue(PistonBaseBlock.EXTENDED) || !helper.getBlockState(at.north()).isAir()) {
                helper.fail("a piston put its head into the Span's Clearance", at.north());
            }
            succeed(helper);
        });
    }

    /**
     * Water placed beside lava over the middle of the Span: the lava stays lava, where the same two
     * clear of the Span make obsidian. Checked before the lava's first tick, as lava that flows on
     * beside water turns with no event to refuse it.
     */
    private static void waterMeetsLava(GameTestHelper helper) {
        string(helper, A, B);
        BlockPos over = new BlockPos(5, 2, 3);
        BlockPos clear = new BlockPos(5, 2, 7);
        for (BlockPos lava : new BlockPos[] {over, clear}) {
            helper.setBlock(lava.below(), Blocks.STONE);
            helper.setBlock(lava.south().below(), Blocks.STONE);
            helper.setBlock(lava, Blocks.LAVA);
            helper.setBlock(lava.south(), Blocks.WATER);
        }
        helper.runAfterDelay(5, () -> {
            if (!helper.getBlockState(over).is(Blocks.LAVA)) {
                helper.fail("water beside lava made " + helper.getBlockState(over) + " in the Span's Clearance", over);
            }
            if (!helper.getBlockState(clear).is(Blocks.OBSIDIAN)) {
                helper.fail("water beside lava clear of the Span made no obsidian", clear);
            }
            drain(helper);
            succeed(helper);
        });
    }

    /**
     * Passes, first cutting every Span with both ends in the plot: the level keeps its Spans past the
     * test, and a later test building a line through this ground would find them in its way.
     */
    private static void succeed(GameTestHelper helper) {
        RailNetwork network = LevelRails.of(helper.getLevel()).network();
        for (Span span : network.all()) {
            if (inPlot(helper, span.a()) && inPlot(helper, span.b())) {
                network.remove(span.a(), span.b());
            }
        }
        helper.succeed();
    }

    private static boolean inPlot(GameTestHelper helper, Arm arm) {
        BlockPos relative = helper.relativePos(PylonColumn.block(arm));
        return relative.getX() >= 0 && relative.getX() <= 10 && relative.getY() >= 0 && relative.getY() <= 6
                && relative.getZ() >= 0 && relative.getZ() <= 8;
    }

    /**
     * Every fluid left in the plot set to air, before it can flow out past the plot into the ground a
     * later test builds on.
     */
    private static void drain(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z <= 10; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!helper.getBlockState(pos).getFluidState().isEmpty()) {
                        helper.setBlock(pos, Blocks.AIR);
                    }
                }
            }
        }
    }

    /** A grown cactus under the Rail stays one block high, and one beside it grows. */
    private static void cactus(GameTestHelper helper) {
        string(helper, A, B);
        BlockPos under = new BlockPos(5, 1, 3);
        BlockPos beside = new BlockPos(5, 1, 6);
        for (BlockPos pos : new BlockPos[] {under, beside}) {
            helper.setBlock(pos.below(), Blocks.SAND);
            helper.setBlock(pos, Blocks.CACTUS.defaultBlockState().setValue(CactusBlock.AGE, 15));
            BlockPos absolute = helper.absolutePos(pos);
            helper.getBlockState(pos).randomTick(helper.getLevel(), absolute, helper.getLevel().getRandom());
        }
        if (!helper.getBlockState(under.above()).isAir()) {
            helper.fail("a cactus grew into the Span's Clearance", under.above());
        }
        if (!helper.getBlockState(beside.above()).is(Blocks.CACTUS)) {
            helper.fail("a cactus clear of the Span did not grow", beside.above());
        }
        succeed(helper);
    }

    /**
     * A block a car on the Span would pass through once A is raised a segment, and not before, clear of
     * A's own column, as an absolute position: worked out from the Clearance itself, so the test holds
     * whatever the car's height.
     */
    private static BlockPos raisedOnly(GameTestHelper helper) {
        Arm a = PylonColumn.arm(helper.absolutePos(A));
        Arm b = PylonColumn.arm(helper.absolutePos(B));
        Arm raisedA = PylonColumn.arm(helper.absolutePos(A.above()));
        RailNetwork network = LevelRails.of(helper.getLevel()).network();
        SequencedSet<Clearance.Cell> now = Clearance.along(network.curve(network.get(a, b)));
        RailNetwork raised = new RailNetwork();
        raised.add(Span.of(raisedA, b, 8));
        for (Clearance.Cell cell : Clearance.along(raised.curve(raised.get(raisedA, b)))) {
            if (!now.contains(cell) && cell.x() != a.x()) {
                return new BlockPos(cell.x(), cell.y(), cell.z());
            }
        }
        throw new IllegalStateException("raising A put no block in the Span's way");
    }

    private static void expectRefusedPlan(GameTestHelper helper, ServerPlayer player, BlockPos column) {
        BlockPos absolute = helper.absolutePos(column);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.NORTH, 0.5), Direction.NORTH,
                absolute, false);
        PlacementPlan plan = Placements.planFor(helper.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
        if (plan == null || plan.refusal() != RailworksRefusal.RAISES_SPAN) {
            helper.fail("expected RAISES_SPAN but the plan gave " + (plan == null ? "none" : plan.refusal()), column);
        }
    }

    private static void stackOnto(GameTestHelper helper, ServerPlayer player, BlockPos column, Direction face) {
        BlockPos absolute = helper.absolutePos(column);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false));
    }

    /** A piston at {@code pos} facing {@code facing}, powered from behind by a redstone block. */
    private static void piston(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, facing));
        helper.setBlock(pos.relative(facing.getOpposite()), Blocks.REDSTONE_BLOCK);
    }

    private static void column(GameTestHelper helper, BlockPos top, int segments) {
        for (int i = 0; i < segments; i++) {
            helper.setBlock(top.below(i), RailworksRegistries.PYLON.get().defaultBlockState());
        }
    }

    /** The Span from {@code a} to {@code b}, strung by the command's rule, both Pylons standing. */
    private static void string(GameTestHelper helper, BlockPos a, BlockPos b) {
        helper.setBlock(a, RailworksRegistries.PYLON.get().defaultBlockState());
        if (!PylonColumn.isPylon(helper.getLevel(), helper.absolutePos(b))) {
            helper.setBlock(b, RailworksRegistries.PYLON.get().defaultBlockState());
        }
        var outcome = LevelRails.of(helper.getLevel()).string(helper.getLevel(), helper.absolutePos(a), helper.absolutePos(b));
        if (outcome.result() != Stringing.Result.STRUNG) {
            helper.fail("the Span was " + outcome.result(), a);
        }
    }

    /** A survival player south of the Span, four Pylons in hand. */
    private static ServerPlayer player(GameTestHelper helper, boolean sneaking) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(new BlockPos(5, 1, 1));
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.PYLON_ITEM.get(), 4));
        player.setShiftKeyDown(sneaking);
        return player;
    }

    private static void expectStrung(GameTestHelper helper, BlockPos a, BlockPos b, boolean strung, String after) {
        if (LevelRails.of(helper.getLevel()).contains(helper.absolutePos(a), helper.absolutePos(b)) != strung) {
            helper.fail("the Span is " + (strung ? "not " : "") + "strung after " + after, a);
        }
    }
}
