// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.function.Predicate;

import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.RailworksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Pylon columns and the Rail gesture, through the right-click event and the player tick, the way a
 * survival player reaches them. {@code SpanRulesTest} and {@code StringingTest} hold the rules; what
 * they cannot see is that the click moves items, that breaking a segment unwinds the column, and that
 * the Spans live in the level. All of these tests share the {@code span_} prefix.
 */
final class SpanTests {

    /** Eight apart on the floor, so a Span between the bases (both columns one segment) costs 8. */
    private static final BlockPos A = new BlockPos(1, 1, 3);
    private static final BlockPos B = new BlockPos(9, 1, 3);
    private static final BlockPos C = new BlockPos(17, 1, 3);
    private static final BlockPos D = new BlockPos(25, 1, 3);

    private SpanTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("span_pylons_stack_one_pylon_per_segment", 20, SpanTests::pylonsStack);
        tests.test("span_breaking_a_segment_drops_and_refunds_the_column_above", 20, SpanTests::breakingUnwindsColumn);
        tests.test("span_the_rail_gesture_strings_and_spends_the_cost", 20, SpanTests::stringsAndSpends);
        tests.test("span_a_short_purse_strings_nothing", 20, SpanTests::shortPurse);
        tests.test("span_a_span_past_the_length_limit_is_refused", 20, SpanTests::tooLong);
        tests.test("span_a_span_past_the_grade_limit_is_refused", 20, SpanTests::tooSteep);
        tests.test("span_a_span_bending_past_the_bend_limit_is_refused", 20, SpanTests::tooSharp);
        tests.testOnItsOwn("span_a_third_span_on_one_arm_is_refused", 20, SpanTests::thirdRefused);
        tests.test("span_using_the_same_arm_again_cancels", 20, SpanTests::sameArmCancels);
        tests.test("span_switching_items_cancels", 20, SpanTests::switchingCancels);
        tests.test("span_walking_away_cancels", 20, SpanTests::walkingAwayCancels);
        tests.test("span_repeating_the_gesture_cuts_and_refunds", 20, SpanTests::cutRefunds);
        tests.testOnItsOwn("span_breaking_a_pylon_cuts_its_spans_and_refunds_them", 20, SpanTests::breakingRefunds);
        tests.test("span_stacking_a_segment_keeps_the_spans", 20, SpanTests::stackingKeepsSpans);
        tests.test("span_joining_two_columns_cuts_the_lower_arms_spans", 20, SpanTests::joiningCutsLowerArm);
        tests.test("span_the_arm_is_the_top_segment", 20, SpanTests::armIsTheTop);
    }

    private static void pylonsStack(GameTestHelper helper) {
        ServerPlayer player = player(helper, new ItemStack(RailworksRegistries.PYLON_ITEM.get(), 3));
        helper.setBlock(A, RailworksRegistries.PYLON.get().defaultBlockState());
        use(helper, player, A);
        use(helper, player, A);
        for (int y = 1; y <= 3; y++) {
            if (!helper.getBlockState(A.above(y - 1)).is(RailworksRegistries.PYLON.get())) {
                helper.fail("no segment at height " + y, A.above(y - 1));
            }
        }
        // The placed one plus two stacked on it spent two; the third Pylon is left in hand.
        if (player.getMainHandItem().getCount() != 1) {
            helper.fail("two stacked segments should leave 1 Pylon of 3, left " + player.getMainHandItem().getCount(), A);
        }
        helper.succeed();
    }

    private static void breakingUnwindsColumn(GameTestHelper helper) {
        column(helper, A, 4);
        helper.getLevel().destroyBlock(helper.absolutePos(A.above()), true);
        if (!helper.getBlockState(A).is(RailworksRegistries.PYLON.get())) {
            helper.fail("the segment below the break was lost", A);
        }
        for (int y = 1; y <= 3; y++) {
            if (!helper.getBlockState(A.above(y)).isAir()) {
                helper.fail("a segment above the break stayed standing", A.above(y));
            }
        }
        int dropped = dropped(helper, A, RailworksRegistries.PYLON_ITEM.get());
        if (dropped != 3) {
            helper.fail("breaking the second of four segments should drop 3 Pylons, dropped " + dropped, A);
        }
        helper.succeed();
    }

    private static void stringsAndSpends(GameTestHelper helper) {
        ServerPlayer player = railPlayer(helper, 20);
        use(helper, player, A);
        use(helper, player, B);
        if (!LevelRails.of(helper.getLevel()).contains(helper.absolutePos(A), helper.absolutePos(B))) {
            helper.fail("clicking A then B with Rail strung nothing", A);
        }
        expectRail(helper, player, 12, "stringing an 8 block Span");
        if (player.getMainHandItem().has(RailworksRegistries.PENDING_SPAN.get())) {
            helper.fail("the Rail still holds an end after the Span was strung", B);
        }
        helper.succeed();
    }

    private static void shortPurse(GameTestHelper helper) {
        ServerPlayer player = railPlayer(helper, 7);
        use(helper, player, A);
        use(helper, player, B);
        expectStrung(helper, A, B, false, "clicking with 7 Rail for an 8 Rail Span");
        expectRail(helper, player, 7, "a refused Span");
        helper.succeed();
    }

    private static void tooLong(GameTestHelper helper) {
        // 70 blocks apart on x, past the 32 limit; the columns stand where the test never looks.
        BlockPos far = A.offset(70, 0, 0);
        helper.setBlock(far, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = railPlayer(helper, 64);
        // A far column in an unloaded chunk would be air, and the click would pass for a refusal.
        if (!helper.getBlockState(far).is(RailworksRegistries.PYLON.get())) {
            helper.fail("the far Pylon did not stand", far);
        }
        use(helper, player, A);
        use(helper, player, far);
        expectStrung(helper, A, far, false, "a 70 block Span");
        expectRail(helper, player, 64, "a refused Span");
        helper.succeed();
    }

    private static void tooSteep(GameTestHelper helper) {
        // B's Arm four segments up: 3 blocks of rise over 8 across, past the 1 in 4 limit.
        column(helper, B, 4);
        ServerPlayer player = railPlayer(helper, 20);
        use(helper, player, A);
        use(helper, player, B);
        expectStrung(helper, A, B.above(3), false, "a Span climbing 3 over 8");
        expectRail(helper, player, 20, "a refused Span");
        helper.succeed();
    }

    private static void tooSharp(GameTestHelper helper) {
        // A to B runs east; turning north at B is a right angle, past the 45 degree limit.
        BlockPos corner = B.offset(0, 0, 8);
        helper.setBlock(corner, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = railPlayer(helper, 20);
        use(helper, player, A);
        use(helper, player, B);
        use(helper, player, B);
        use(helper, player, corner);
        expectStrung(helper, B, corner, false, "a right angle through B");
        expectRail(helper, player, 12, "a refused bend");
        helper.succeed();
    }

    private static void thirdRefused(GameTestHelper helper) {
        helper.setBlock(C, RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(D, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = railPlayer(helper, 100);
        use(helper, player, B);
        use(helper, player, A);
        use(helper, player, B);
        use(helper, player, C);
        expectStrung(helper, A, B, true, "the first Span on B");
        expectStrung(helper, B, C, true, "the second Span on B");
        int rail = rail(player);
        use(helper, player, B);
        use(helper, player, D);
        expectStrung(helper, B, D, false, "a third Span on B");
        expectRail(helper, player, rail, "a third Span refused");
        helper.succeed();
    }

    private static void sameArmCancels(GameTestHelper helper) {
        ServerPlayer player = railPlayer(helper, 20);
        use(helper, player, A);
        if (!player.getMainHandItem().has(RailworksRegistries.PENDING_SPAN.get())) {
            helper.fail("the first use held no end", A);
        }
        use(helper, player, A);
        if (player.getMainHandItem().has(RailworksRegistries.PENDING_SPAN.get())) {
            helper.fail("using the same Arm again left the end held", A);
        }
        use(helper, player, B);
        expectStrung(helper, A, B, false, "a cancelled Span, then one use on B");
        expectRail(helper, player, 20, "a cancelled Span");
        helper.succeed();
    }

    private static void switchingCancels(GameTestHelper helper) {
        ServerPlayer player = railPlayer(helper, 20);
        ItemStack rail = player.getMainHandItem();
        use(helper, player, A);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        player.getInventory().setItem(9, rail);
        tick(player);
        if (rail.has(RailworksRegistries.PENDING_SPAN.get())) {
            helper.fail("the end stayed held after the Rail left the main hand", A);
        }
        player.getInventory().setItem(9, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, rail);
        use(helper, player, B);
        expectStrung(helper, A, B, false, "switching items, then one use on B");
        expectRail(helper, player, 20, "a Span cancelled by switching items");
        helper.succeed();
    }

    private static void walkingAwayCancels(GameTestHelper helper) {
        ServerPlayer player = railPlayer(helper, 20);
        ItemStack rail = player.getMainHandItem();
        use(helper, player, A);
        BlockPos far = helper.absolutePos(A).offset(300, 0, 0);
        player.snapTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        tick(player);
        if (rail.has(RailworksRegistries.PENDING_SPAN.get())) {
            helper.fail("the end stayed held after the player walked away", A);
        }
        expectStrung(helper, A, B, false, "walking away");
        expectRail(helper, player, 20, "a Span cancelled by walking away");
        helper.succeed();
    }

    private static void cutRefunds(GameTestHelper helper) {
        ServerPlayer player = railPlayer(helper, 20);
        use(helper, player, A);
        use(helper, player, B);
        use(helper, player, B);
        use(helper, player, A);
        expectStrung(helper, A, B, false, "repeating the gesture on a strung pair");
        expectRail(helper, player, 20, "cutting a Span");
        helper.succeed();
    }

    private static void breakingRefunds(GameTestHelper helper) {
        helper.setBlock(C, RailworksRegistries.PYLON.get().defaultBlockState());
        ServerPlayer player = railPlayer(helper, 40);
        use(helper, player, B);
        use(helper, player, A);
        use(helper, player, B);
        use(helper, player, C);
        expectRail(helper, player, 24, "stringing two 8 block Spans");
        helper.getLevel().destroyBlock(helper.absolutePos(B), false);
        expectStrung(helper, A, B, false, "breaking B");
        expectStrung(helper, B, C, false, "breaking B");
        int refunded = dropped(helper, B, RailworksRegistries.RAIL.get());
        if (refunded != 16) {
            helper.fail("breaking B should drop its two Spans' 16 Rail, dropped " + refunded, B);
        }
        helper.succeed();
    }

    private static void stackingKeepsSpans(GameTestHelper helper) {
        ServerPlayer player = railPlayer(helper, 20);
        use(helper, player, A);
        use(helper, player, B);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.PYLON_ITEM.get(), 2));
        use(helper, player, A);
        if (!LevelRails.of(helper.getLevel()).contains(helper.absolutePos(A.above()), helper.absolutePos(B))) {
            helper.fail("the Span did not move up to the stacked column's new Arm", A.above());
        }
        expectStrung(helper, A, B, false, "the old top segment");
        helper.succeed();
    }

    private static void joiningCutsLowerArm(GameTestHelper helper) {
        ServerPlayer player = railPlayer(helper, 20);
        use(helper, player, A);
        use(helper, player, B);
        // A second column two above A's, then a segment placed in the gap joins them.
        helper.setBlock(A.above(2), RailworksRegistries.PYLON.get().defaultBlockState());
        helper.setBlock(A.above(), RailworksRegistries.PYLON.get().defaultBlockState());
        expectStrung(helper, A, B, false, "joining A's column to another above it");
        int refunded = dropped(helper, A, RailworksRegistries.RAIL.get());
        if (refunded != 8) {
            helper.fail("the lower Arm's Span should drop its 8 Rail, dropped " + refunded, A);
        }
        helper.succeed();
    }

    private static void armIsTheTop(GameTestHelper helper) {
        column(helper, A, 3);
        ServerPlayer player = railPlayer(helper, 40);
        // Using the base holds the column's Arm, its top segment.
        use(helper, player, A);
        var held = player.getMainHandItem().get(RailworksRegistries.PENDING_SPAN.get());
        if (held == null || !held.pos().equals(helper.absolutePos(A.above(2)))) {
            helper.fail("using the base should hold the top segment, held " + held, A);
        }
        helper.succeed();
    }

    /** A survival player standing on the floor between the pylons, with the stack in hand. */
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

    private static ServerPlayer railPlayer(GameTestHelper helper, int rail) {
        return player(helper, new ItemStack(RailworksRegistries.RAIL.get(), rail));
    }

    private static void column(GameTestHelper helper, BlockPos base, int height) {
        for (int i = 0; i < height; i++) {
            helper.setBlock(base.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
        }
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.NORTH, 0.5), Direction.NORTH, absolute, false));
    }

    private static void tick(ServerPlayer player) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }

    private static int rail(ServerPlayer player) {
        return count(player, stack -> stack.is(RailworksRegistries.RAIL));
    }

    private static int count(ServerPlayer player, Predicate<ItemStack> match) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (match.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void expectRail(GameTestHelper helper, ServerPlayer player, int expected, String after) {
        if (rail(player) != expected) {
            helper.fail("the player holds " + rail(player) + " Rail, expected " + expected + " after " + after, A);
        }
    }

    private static void expectStrung(GameTestHelper helper, BlockPos a, BlockPos b, boolean strung, String after) {
        if (LevelRails.of(helper.getLevel()).contains(helper.absolutePos(a), helper.absolutePos(b)) != strung) {
            helper.fail("the Span is " + (strung ? "not " : "") + "strung after " + after, a);
        }
    }

    /** How many of {@code item} lie dropped within a few blocks of {@code near}. */
    private static int dropped(GameTestHelper helper, BlockPos near, Item item) {
        AABB area = new AABB(helper.absolutePos(near)).inflate(6);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(item)).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
    }
}
