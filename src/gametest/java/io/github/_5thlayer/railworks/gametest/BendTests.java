// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PlatformsNear;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Clearance;
import io.github._5thlayer.railworks.rail.FluidCar;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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
    /**
     * A longer line through the same bend at {@link #B}, for a Train of three cars, 15 blocks: 16
     * blocks east to B, then 15.6 south-east to its end.
     */
    static final BlockPos RUN_START = new BlockPos(-8, 1, 2);
    static final BlockPos RUN_END = new BlockPos(19, 1, 13);
    private static final BlockPos RUN_FROM = new BlockPos(-12, 1, -2);
    private static final BlockPos RUN_TO = new BlockPos(23, 5, 17);

    /**
     * A platform's Straight leg south along z, as {@code HatchTests}' is, split at {@link #LEG_MIDDLE}:
     * the Station in the row east of its north end, the leading car's place and two car spots behind
     * it, the second spot's last block beside {@link #LEG_END}. On from it in line, a Span to a bend
     * at the limit, east of south.
     */
    private static final BlockPos LEG_START = new BlockPos(5, 1, -3);
    private static final BlockPos LEG_MIDDLE = new BlockPos(5, 1, 7);
    private static final BlockPos LEG_END = new BlockPos(5, 1, 12);
    private static final BlockPos STATION = new BlockPos(7, 3, -3);
    /** Beside the first car spot's first block, on the Span from the leg's start. */
    private static final BlockPos FIRST = new BlockPos(7, 3, 2);
    /** Beside the second car spot's last block, beside the leg's end, the platform's last. */
    private static final BlockPos SECOND = new BlockPos(7, 3, 11);
    private static final BlockPos LEG_FROM = new BlockPos(0, 1, -6);
    private static final BlockPos LEG_TO = new BlockPos(16, 5, 26);

    private BendTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.testOnItsOwn("bend_a_span_bent_to_the_limit_through_a_block_a_car_swings_into_is_refused", 20, BendTests::spanRefused);
        tests.testOnItsOwn("bend_a_block_placed_where_a_car_swings_out_on_a_bend_is_refused", 20, BendTests::blockRefused);
        tests.testOnItsOwn("bend_a_train_runs_a_bend_at_the_limit_beside_blocks_just_outside_its_clearance", 20,
                BendTests::runsBeside);
        for (int bent = 1; bent < Clearance.SWING_CLEAR_LENGTH; bent++) {
            int length = bent;
            tests.testOnItsOwn("bend_a_platform_ends_short_of_a_bent_span_" + bent + "_long_whose_cars_swing_into_its_row", 20,
                    helper -> platformBeforeBend(helper, length));
        }
        tests.testOnItsOwn("bend_a_platform_runs_on_to_a_bent_span_" + Clearance.SWING_CLEAR_LENGTH + "_long_whose_cars_leave_its_row_clear",
                20, helper -> platformBeforeBend(helper, Clearance.SWING_CLEAR_LENGTH));
    }

    private static void spanRefused(GameTestHelper helper) {
        ground(helper, FROM, TO, List.of(A, B, C));
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
        leave(helper, FROM, TO);
        helper.succeed();
    }

    private static void blockRefused(GameTestHelper helper) {
        ground(helper, FROM, TO, List.of(A, B, C));
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
        leave(helper, FROM, TO);
        helper.succeed();
    }

    private static void placeOnTop(GameTestHelper helper, ServerPlayer player, BlockPos on) {
        BlockPos absolute = helper.absolutePos(on);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute, false));
    }

    /**
     * A platform whose leg runs on into a Span {@code bent} long, bending at the limit at its far end:
     * shorter than {@link Clearance#SWING_CLEAR_LENGTH}, its cars swing into the row beside the leg's
     * end, so the Span there is no Straight leg and the platform ends a spot short of it; at that
     * length the platform runs to the leg's end and a hatch beside its last block goes. Either way
     * every block the held hatch draws as free is outside every Span's Clearance.
     */
    private static void platformBeforeBend(GameTestHelper helper, int bent) {
        BlockPos bend = LEG_END.south(bent);
        BlockPos beyond = bend.offset(5, 0, 5);
        RailNetwork network = ground(helper, LEG_FROM, LEG_TO, List.of(LEG_START, LEG_MIDDLE, LEG_END, bend, beyond));
        string(helper, LEG_START, LEG_MIDDLE);
        string(helper, LEG_MIDDLE, LEG_END);
        string(helper, LEG_END, bend);
        string(helper, bend, beyond);
        helper.setBlock(STATION.below(), Blocks.STONE);
        ServerPlayer player = LogisticsTests.near(helper, new BlockPos(7, 1, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get()));
        if (!StationTests.place(helper, player, STATION).consumesAction()) {
            helper.fail("the Station by the leg's start was refused", STATION);
        }
        Set<BlockPos> clearance = new HashSet<>();
        for (Span span : network.all()) {
            if (near(AABB.encapsulatingFullBlocks(helper.absolutePos(LEG_FROM), helper.absolutePos(LEG_TO)), span.a())) {
                for (Clearance.Cell cell : Clearance.along(network.curve(span))) {
                    clearance.add(new BlockPos(cell.x(), cell.y(), cell.z()));
                }
            }
        }
        // Those of this leg's rows, two off its Rail either side: a server asks every Station of the level.
        Vec3 rail = Vec3.atCenterOf(helper.absolutePos(LEG_START));
        List<AABB> drawn = PlatformsNear.freeSpots(helper.getLevel(), helper.absolutePos(STATION)).stream()
                .map(PlatformsNear.FreeSpot::box)
                .filter(box -> Math.abs(Math.abs(box.getCenter().x - rail.x) - 2.0) < 0.5
                        && box.getCenter().z > rail.z - 1.0 && box.getCenter().z < rail.z + 16.0)
                .toList();
        for (AABB box : drawn) {
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX + 0.01, box.minY + 0.01, box.minZ + 0.01),
                    BlockPos.containing(box.maxX - 0.01, box.maxY - 0.01, box.maxZ - 0.01))) {
                if (clearance.contains(pos)) {
                    helper.fail("a held hatch draws " + pos.immutable() + " free, in a Span's Clearance", STATION);
                }
            }
        }
        boolean clear = bent >= Clearance.SWING_CLEAR_LENGTH;
        if (!drawsFree(helper, drawn, FIRST)) {
            helper.fail("the first car spot is not drawn", FIRST);
        }
        if (drawsFree(helper, drawn, SECOND) != clear) {
            helper.fail("beside a bent Span " + bent + " long, the second car spot is " + (clear ? "not " : "") + "drawn", SECOND);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.HATCH_ITEM.get()));
        BlockPos at = helper.absolutePos(SECOND);
        player.getMainHandItem().useOn(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
        if (helper.getBlockState(SECOND).is(RailworksRegistries.HATCH.get()) != clear) {
            helper.fail("beside a bent Span " + bent + " long, a hatch beside the leg's end " + (clear ? "was refused" : "went"), SECOND);
        }
        leave(helper, LEG_FROM, LEG_TO);
        helper.succeed();
    }

    private static boolean drawsFree(GameTestHelper helper, List<AABB> drawn, BlockPos pos) {
        AABB block = new AABB(helper.absolutePos(pos)).deflate(0.01);
        return drawn.stream().anyMatch(box -> box.intersects(block));
    }

    /**
     * Every block around a longer line through the same bend that its Clearance leaves clear is
     * stone, and a Train of a Locomotive, a Cargo car and a Fluid car, driven from its last car near
     * the line's start through the bend to the line's end, never has a car's body, as each entity
     * stands and faces each tick, in any of them.
     */
    private static void runsBeside(GameTestHelper helper) {
        RailNetwork network = ground(helper, RUN_FROM, RUN_TO, List.of(RUN_START, B, RUN_END));
        string(helper, RUN_START, B);
        string(helper, B, RUN_END);
        Span first = network.get(arm(helper, RUN_START), arm(helper, B));
        Span beyond = network.get(arm(helper, B), arm(helper, RUN_END));
        Set<BlockPos> clear = new HashSet<>();
        for (Span span : List.of(first, beyond)) {
            for (Clearance.Cell cell : Clearance.along(network.curve(span))) {
                clear.add(new BlockPos(cell.x(), cell.y(), cell.z()));
            }
        }
        Set<BlockPos> stone = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(RUN_FROM, RUN_TO)) {
            BlockPos at = pos.immutable();
            if (!clear.contains(helper.absolutePos(at)) && !List.of(RUN_START, B, RUN_END).contains(at)) {
                helper.setBlock(at, Blocks.STONE);
                stone.add(helper.absolutePos(at));
            }
        }
        Locomotive locomotive = Locomotive.burner();
        CargoCar cargo = CargoCar.create();
        FluidCar fluid = FluidCar.create();
        // The last car's middle 2.6 from the line's start, its body's back end just short of it.
        double headFromStart = 2.6 + 2 * CargoCar.LENGTH;
        Train train = Train.of(new Place(first, arm(helper, B), first.length() - headFromStart), List.of(locomotive, cargo, fluid));
        List<CarEntity> cars = List.of(
                LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, locomotive),
                LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, cargo),
                LogisticsTests.spawn(helper, RailworksRegistries.FLUID_CAR_ENTITY.get(), train, fluid));
        LocomotiveEntity front = (LocomotiveEntity) cars.getFirst();
        LocomotiveTests.fill(helper, front, new ItemStack(Items.COAL, 8));
        ServerPlayer driver = LocomotiveTests.board(helper, front);
        LocomotiveTests.press(driver, true, false);
        boolean ranBeyond = false;
        for (int tick = 0; tick < 4000; tick++) {
            LocomotiveTests.step(front);
            for (CarEntity car : cars) {
                BlockPos hit = struck(car, stone);
                if (hit != null) {
                    helper.fail("on tick " + tick + " the " + car.getType().getDescription().getString()
                            + "'s body reached into the stone at " + hit + ", outside the Clearance", B);
                    return;
                }
            }
            ranBeyond |= cars.getLast().holds(beyond) && !cars.getLast().holds(first);
            if (ranBeyond && front.train().speed() == 0.0) {
                break;
            }
        }
        if (!ranBeyond || front.train().speed() != 0.0) {
            helper.fail("the Train never ran through the bend to the line's end", RUN_END);
        }
        leave(helper, RUN_FROM, RUN_TO);
        helper.succeed();
    }

    /**
     * The first of {@code filled} that {@code car}'s body reaches into: a box
     * {@link Clearance#BODY_LENGTH} long, {@link CarBody#WIDTH} across and {@link CarBody#HEIGHT} up
     * from where the entity stands, turned and pitched as it faces, by points spread through it a
     * hair in from its faces. Null when it reaches into none.
     */
    private static BlockPos struck(CarEntity car, Set<BlockPos> filled) {
        Vec3 along = Vec3.directionFromRotation(car.getXRot(), car.getYRot());
        Vec3 across = new Vec3(-along.z, 0.0, along.x).normalize();
        Vec3 up = along.cross(across);
        if (up.y < 0) {
            up = up.reverse();
        }
        Vec3 at = car.position();
        double halfLength = Clearance.BODY_LENGTH / 2.0;
        double halfWidth = CarBody.WIDTH / 2.0;
        double in = 1e-3;
        for (double u = -halfLength + in; u <= halfLength - in + 1e-9; u += (2 * halfLength - 2 * in) / 48) {
            for (double v = -halfWidth + in; v <= halfWidth - in + 1e-9; v += (2 * halfWidth - 2 * in) / 20) {
                for (double w = in; w <= CarBody.HEIGHT - in + 1e-9; w += (CarBody.HEIGHT - 2 * in) / 4) {
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
     * Clears the ground from {@code from} to {@code to}, taking up any Span an earlier test left on
     * it, and stands the Pylons at {@code columns}: an on-its-own test lands on ground others ran on.
     */
    private static RailNetwork ground(GameTestHelper helper, BlockPos from, BlockPos to, List<BlockPos> columns) {
        RailNetwork network = takeUp(helper, from, to);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            helper.setBlock(pos, Blocks.AIR);
        }
        for (BlockPos column : columns) {
            helper.setBlock(column, RailworksRegistries.PYLON.get().defaultBlockState());
        }
        return network;
    }

    /** Leaves the ground as {@link #ground} found it clear: its Spans taken up and its blocks gone. */
    private static void leave(GameTestHelper helper, BlockPos from, BlockPos to) {
        takeUp(helper, from, to);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            helper.setBlock(pos, Blocks.AIR);
        }
    }

    /**
     * Takes up every Span with an Arm on or near the ground from {@code from} to {@code to}: before a
     * test, an earlier one's; after it, its own, so a later test laid on the same ground meets none.
     */
    private static RailNetwork takeUp(GameTestHelper helper, BlockPos from, BlockPos to) {
        RailNetwork network = LevelRails.of(helper.getLevel()).network();
        // LevelClearance.near's box test, which is private to the Module: any Span with an Arm near goes.
        AABB near = AABB.encapsulatingFullBlocks(helper.absolutePos(from), helper.absolutePos(to)).inflate(4);
        for (Span left : List.copyOf(network.all())) {
            if (near(near, left.a()) || near(near, left.b())) {
                network.remove(left.a(), left.b());
            }
        }
        return network;
    }

    private static boolean near(AABB box, Arm arm) {
        return box.contains(arm.x() + 0.5, arm.y() + 0.5, arm.z() + 0.5);
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
