// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A held Pylon's Placement Plan against what the click does: an accepted plan must put its block
 * down in the state it named, and a refused one must change nothing. The plan and the click ask one
 * rule ({@code PylonItem}), and these tests are where a disagreement would show.
 */
final class PylonPlanTests {

    private static final BlockPos FLOOR = new BlockPos(3, 0, 3);
    private static final BlockPos BASE = new BlockPos(3, 1, 3);

    private PylonPlanTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("plan_matches_placement_for_a_pylon", 20, PylonPlanTests::matchesPlacement);
        tests.test("plan_stacks_a_pylon_onto_the_top_of_a_column", 20, PylonPlanTests::stacksOnTop);
        tests.test("plan_refuses_a_blocked_pylon_top", 20, PylonPlanTests::blockedTop);
    }

    private static void matchesPlacement(GameTestHelper helper) {
        PlacementPlan plan = check(helper, FLOOR, Direction.UP, false);
        if (!plan.blocks().getFirst().pos().equals(helper.absolutePos(BASE))) {
            helper.fail("a Pylon aimed at the floor planned somewhere other than on it", BASE);
        }
        helper.succeed();
    }

    private static void stacksOnTop(GameTestHelper helper) {
        column(helper, 3);
        // Aimed at the base's side, the plan is still the next segment of the column.
        PlacementPlan plan = check(helper, BASE, Direction.NORTH, false);
        if (plan.blocks().getFirst().pos().getY() != helper.absolutePos(BASE).getY() + 3) {
            helper.fail("a Pylon aimed at a column's base planned a segment somewhere other than its top", BASE);
        }
        helper.succeed();
    }

    private static void blockedTop(GameTestHelper helper) {
        column(helper, 1);
        helper.setBlock(BASE.above(), Blocks.STONE);
        PlacementPlan plan = check(helper, BASE, Direction.NORTH, true);
        if (plan.refusal() != RailworksRefusal.BLOCKED_TOP) {
            helper.fail("expected BLOCKED_TOP but the plan gave " + plan.refusal(), BASE);
        }
        helper.succeed();
    }

    private static PlacementPlan check(GameTestHelper helper, BlockPos target, Direction face, boolean expectRefused) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(RailworksRegistries.PYLON_ITEM.get(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(target);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false);

        PlacementPlan plan = Placements.planFor(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit);
        if (plan == null) {
            helper.fail("no plan at all where one was expected", target);
            throw new IllegalStateException("unreachable");
        }
        if (plan.isRefused() != expectRefused) {
            helper.fail("the plan " + (plan.isRefused() ? "refused" : "accepted") + " where the opposite was expected", target);
        }
        BlockState before = helper.getLevel().getBlockState(plan.blocks().getFirst().pos());

        helper.useBlock(target, player, hit);

        PlacementPlan.Placed placed = plan.blocks().getFirst();
        BlockState now = helper.getLevel().getBlockState(placed.pos());
        BlockPos relative = helper.relativePos(placed.pos());
        if (plan.isRefused()) {
            if (!now.equals(before)) {
                helper.fail("a refused plan changed the world at its position", relative);
            }
            if (stack.getCount() != 4) {
                helper.fail("a refused plan spent a Pylon", relative);
            }
        } else if (!now.equals(placed.state())) {
            helper.fail("the plan promised " + placed.state() + " but placing left " + now, relative);
        }
        return plan;
    }

    private static void column(GameTestHelper helper, int segments) {
        for (int i = 0; i < segments; i++) {
            helper.setBlock(BASE.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
        }
    }
}
