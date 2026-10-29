// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Clearance;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Stringing;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A five-block car on a bend at the bend limit (#75): its rigid body's ends swing out of the curve
 * and its middle cuts in, so a bent Span's Clearance reaches blocks a straight one leaves clear, and a
 * Span is refused, and a block placed, where it would. {@code ClearanceTest} measures the body
 * against the Clearance at every step; these show the world's blocks meet it. Each runs on its own:
 * a line's Clearance reaches two blocks past its end Arms, out past the structure.
 */
final class BendTests {

    /** A line bending 45 degrees, the limit, at {@link #B}: east from A, then south-east to C. */
    static final BlockPos A = new BlockPos(1, 1, 2);
    static final BlockPos B = new BlockPos(8, 1, 2);
    static final BlockPos C = new BlockPos(13, 1, 7);
    /**
     * Two off the Rail on the bend's outside, a block short of B, at a car's floor: outside the
     * straight Span from A to B's Clearance, and inside it once the line bends at B, where a car's
     * back end swings out as it turns.
     */
    static final BlockPos SWUNG_INTO = new BlockPos(7, 2, 0);
    /** The ground a test lays, cleared first: the line's Clearance and the blocks around it. */
    private static final BlockPos FROM = new BlockPos(-3, 1, -2);
    private static final BlockPos TO = new BlockPos(17, 5, 11);

    private BendTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.testOnItsOwn("bend_a_span_bent_to_the_limit_through_a_block_a_car_swings_into_is_refused", 20, BendTests::spanRefused);
        tests.testOnItsOwn("bend_a_block_placed_where_a_car_swings_out_on_a_bend_is_refused", 20, BendTests::blockRefused);
        tests.testOnItsOwn("bend_a_train_runs_a_bend_at_the_limit_beside_blocks_just_outside_its_clearance", 20,
                BendTests::runsBeside);
    }

    private static void spanRefused(GameTestHelper helper) {
        ground(helper);
        // Outside the straight Span's Clearance: it strings past the stone.
        helper.setBlock(SWUNG_INTO, Blocks.STONE);
        string(helper, A, B);
        var outcome = LevelRails.of(helper.getLevel()).string(helper.getLevel(), helper.absolutePos(B), helper.absolutePos(C));
        if (outcome.result() != Stringing.Result.BLOCKED) {
            helper.fail("the Span bending the line at B past stone its cars swing into was " + outcome.result(), SWUNG_INTO);
        }
        BlockPos blocked = helper.absolutePos(SWUNG_INTO);
        if (!new Clearance.Cell(blocked.getX(), blocked.getY(), blocked.getZ()).equals(outcome.blocked())) {
            helper.fail("the Span was refused for " + outcome.blocked() + ", not the stone a car swings into", SWUNG_INTO);
        }
        helper.succeed();
    }

    private static void blockRefused(GameTestHelper helper) {
        ground(helper);
        string(helper, A, B);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(new BlockPos(4, 1, -1));
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        BlockPos under = SWUNG_INTO.below();
        helper.setBlock(under, Blocks.STONE);
        // Beside the straight line the block stays; once the line bends at B it is refused.
        placeOnTop(helper, player, under);
        if (!helper.getBlockState(SWUNG_INTO).is(Blocks.STONE)) {
            helper.fail("a block two off the straight line was refused", SWUNG_INTO);
        }
        helper.setBlock(SWUNG_INTO, Blocks.AIR);
        string(helper, B, C);
        placeOnTop(helper, player, under);
        if (!helper.getBlockState(SWUNG_INTO).isAir()) {
            helper.fail("a block went where a car swings out on the bend", SWUNG_INTO);
        }
        if (player.getMainHandItem().getCount() != 3) {
            helper.fail("a refused block was spent", SWUNG_INTO);
        }
        helper.succeed();
    }

    private static void placeOnTop(GameTestHelper helper, ServerPlayer player, BlockPos on) {
        BlockPos absolute = helper.absolutePos(on);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute, false));
    }

    /**
     * Every block around the line that its Clearance leaves clear is stone, and a Locomotive driven
     * from near A through the bend to the line's end at C never has its body, as the entity stands
     * and faces each tick, in any of them.
     */
    private static void runsBeside(GameTestHelper helper) {
        RailNetwork network = ground(helper);
        string(helper, A, B);
        string(helper, B, C);
        Set<BlockPos> clear = new HashSet<>();
        for (Span span : List.of(network.get(arm(helper, A), arm(helper, B)), network.get(arm(helper, B), arm(helper, C)))) {
            for (Clearance.Cell cell : Clearance.along(network.curve(span))) {
                clear.add(new BlockPos(cell.x(), cell.y(), cell.z()));
            }
        }
        Set<BlockPos> stone = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(FROM, TO)) {
            BlockPos at = pos.immutable();
            if (!clear.contains(helper.absolutePos(at)) && !List.of(A, B, C).contains(at)) {
                helper.setBlock(at, Blocks.STONE);
                stone.add(helper.absolutePos(at));
            }
        }
        Span first = network.get(arm(helper, A), arm(helper, B));
        Span beyond = network.get(arm(helper, B), arm(helper, C));
        Locomotive locomotive = Locomotive.burner();
        Train train = Train.of(new Place(first, arm(helper, B), first.length() - 2.5), locomotive);
        LevelRails.of(helper.getLevel()).add(train);
        LocomotiveEntity car = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        car.setCar(train, locomotive);
        helper.getLevel().addFreshEntity(car);
        LocomotiveTests.fill(helper, car, new ItemStack(Items.COAL, 4));
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        LocomotiveTests.press(driver, true, false);
        boolean ranBeyond = false;
        for (int tick = 0; tick < 2000; tick++) {
            LocomotiveTests.step(car);
            BlockPos hit = struck(car, stone);
            if (hit != null) {
                helper.fail("on tick " + tick + " the car's body reached into the stone at " + hit + ", outside the Clearance", B);
                return;
            }
            ranBeyond |= car.holds(beyond) && !car.holds(first);
            if (ranBeyond && car.train().speed() == 0.0) {
                break;
            }
        }
        if (!ranBeyond || car.train().speed() != 0.0) {
            helper.fail("the car never ran through the bend to the line's end", C);
        }
        helper.succeed();
    }

    /**
     * The first of {@code filled} that {@code car}'s body reaches into: a box 4.8 long, two wide and
     * 2.75 up from where the entity stands, turned and pitched as it faces, by points spread through
     * it a hair in from its faces. Null when it reaches into none.
     */
    private static BlockPos struck(LocomotiveEntity car, Set<BlockPos> filled) {
        Vec3 along = Vec3.directionFromRotation(car.getXRot(), car.getYRot());
        Vec3 across = new Vec3(-along.z, 0.0, along.x).normalize();
        Vec3 up = along.cross(across);
        if (up.y < 0) {
            up = up.reverse();
        }
        Vec3 at = car.position();
        double in = 1e-3;
        for (double u = -2.4 + in; u <= 2.4 - in + 1e-9; u += (4.8 - 2 * in) / 48) {
            for (double v = -1.0 + in; v <= 1.0 - in + 1e-9; v += (2.0 - 2 * in) / 20) {
                for (double w = in; w <= 2.75 - in + 1e-9; w += (2.75 - 2 * in) / 4) {
                    BlockPos pos = BlockPos.containing(at.add(along.scale(u)).add(across.scale(v)).add(up.scale(w)));
                    if (filled.contains(pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Clears the test's ground, taking up any Span an earlier test left on it, and stands the three
     * Pylons: an on-its-own test lands on ground others ran on.
     */
    private static RailNetwork ground(GameTestHelper helper) {
        RailNetwork network = LevelRails.of(helper.getLevel()).network();
        BlockPos from = helper.absolutePos(FROM.offset(-4, -4, -4));
        BlockPos to = helper.absolutePos(TO.offset(4, 4, 4));
        for (Span left : List.copyOf(network.all())) {
            if (within(left.a(), from, to) || within(left.b(), from, to)) {
                network.remove(left.a(), left.b());
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(FROM, TO)) {
            helper.setBlock(pos, Blocks.AIR);
        }
        for (BlockPos column : List.of(A, B, C)) {
            helper.setBlock(column, RailworksRegistries.PYLON.get().defaultBlockState());
        }
        return network;
    }

    private static boolean within(Arm arm, BlockPos from, BlockPos to) {
        return arm.x() >= Math.min(from.getX(), to.getX()) && arm.x() <= Math.max(from.getX(), to.getX())
                && arm.y() >= Math.min(from.getY(), to.getY()) && arm.y() <= Math.max(from.getY(), to.getY())
                && arm.z() >= Math.min(from.getZ(), to.getZ()) && arm.z() <= Math.max(from.getZ(), to.getZ());
    }

    /** The Span between the columns at {@code one} and {@code other}, strung by the command's rule. */
    private static void string(GameTestHelper helper, BlockPos one, BlockPos other) {
        var outcome = LevelRails.of(helper.getLevel()).string(helper.getLevel(), helper.absolutePos(one), helper.absolutePos(other));
        if (outcome.result() != Stringing.Result.STRUNG) {
            helper.fail("the Span from " + one + " to " + other + " was " + outcome.result(), one);
        }
    }

    private static Arm arm(GameTestHelper helper, BlockPos column) {
        return PylonColumn.arm(helper.absolutePos(column));
    }
}
