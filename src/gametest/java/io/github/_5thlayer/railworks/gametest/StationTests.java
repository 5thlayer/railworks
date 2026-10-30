// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.StationItem;
import io.github._5thlayer.railworks.network.NameStationPacket;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Stations through the item, the block and a real player: placing one in the row beside the Rail,
 * two blocks off it at a Cargo car's crates' height (ADR 0010), refused off it, in the old row one
 * off it, in its Clearance and by a bend or a diagonal, with the line it says why, naming it, and the
 * level's Stations following the blocks. Which stretch of a Span a Station serves is
 * {@code StationTest}'s, and where Rail is a Straight leg {@code StraightLegTest}'s; what only a
 * server shows is that the click asks the level's Spans and that the name survives a save. All of these tests share the {@code station_}
 * prefix.
 */
final class StationTests {

    /** Eight apart, one segment tall, with a Span strung between the tops at y 1. */
    private static final BlockPos A = new BlockPos(1, 1, 3);
    private static final BlockPos B = new BlockPos(9, 1, 3);
    /** In the row beside the Rail, two blocks off it at the height of a Cargo car's crates: where a Station goes. */
    private static final BlockPos BESIDE = new BlockPos(5, 3, 1);
    /** Under the Rail, between the Pylons, where a Station is refused, but a block can be clicked. */
    private static final BlockPos UNDER = new BlockPos(5, 1, 3);
    /** In the row on the Rail's other side, far enough along for its platform to clear {@link #BESIDE}'s. */
    private static final BlockPos ACROSS = new BlockPos(2, 3, 5);
    /** In the Rail's own block, where a car passes. */
    private static final BlockPos IN_CLEARANCE = new BlockPos(5, 2, 3);
    /** Three blocks off the Rail, at the row's height. */
    private static final BlockPos OFF = new BlockPos(5, 3, 6);
    /** Where a player stands, clear of the row's blocks and the supports under them. */
    private static final BlockPos STAND = new BlockPos(3, 1, 0);

    private StationTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("station_places_beside_the_rail", 20, StationTests::placesByTheRail);
        tests.test("station_is_refused_off_the_rail", 20, StationTests::refusedOffRail);
        tests.test("station_is_refused_in_the_clearance", 20, StationTests::refusedInClearance);
        tests.test("station_is_refused_beside_a_bend", 20, StationTests::refusedBesideBend);
        tests.test("station_is_refused_by_a_diagonal", 20, StationTests::refusedByDiagonal);
        tests.test("station_is_refused_near_a_pylon_where_the_span_beyond_bends", 20, StationTests::refusedNearBendBeyond);
        tests.test("station_places_by_a_straight_climbing_leg", 20, StationTests::placesByClimb);
        tests.test("station_is_named_in_its_menu", 20, StationTests::named);
        tests.test("station_is_not_named_from_out_of_reach", 20, StationTests::notNamedFromAfar);
        tests.test("station_name_is_kept_across_a_save_and_reload", 20, StationTests::nameSaved);
        tests.test("station_names_may_be_shared", 20, StationTests::sharedNames);
        tests.test("station_breaking_takes_it_out_of_the_level", 20, StationTests::breaking);
        tests.test("station_serves_the_stretch_of_its_span_beside_it", 20, StationTests::serves);
        tests.test("station_row_is_two_off_the_rail_at_a_cars_chests_and_nowhere_nearer", 20, StationTests::onlyTheRow);
    }

    private static void placesByTheRail(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        if (!place(helper, player, BESIDE).consumesAction() || !place(helper, player, ACROSS).consumesAction()) {
            helper.fail("a Station beside the Rail on either side did not place", BESIDE);
            return;
        }
        for (BlockPos pos : new BlockPos[] {BESIDE, ACROSS}) {
            if (!helper.getBlockState(pos).is(RailworksRegistries.STATION.get())) {
                helper.fail("no Station block was placed", pos);
            }
            BlockPos at = helper.absolutePos(pos);
            Station station = stations(helper).get(at.getX(), at.getY(), at.getZ());
            if (station == null || !station.name().equals(Station.DEFAULT_NAME)) {
                helper.fail("the level's Stations hold " + station + " for a new Station", pos);
            }
        }
        if (player.getMainHandItem().getCount() != 2) {
            helper.fail("two placed Stations of 4 left " + player.getMainHandItem().getCount(), BESIDE);
        }
        helper.succeed();
    }

    private static void refusedOffRail(GameTestHelper helper) {
        refused(helper, OFF, RailworksRefusal.STATION_OFF_RAIL);
    }

    /**
     * The row is the only place by the Rail (#72): one block off it, at a car's floor where the old
     * row was or at the chests' height, a Station is off the Rail, and two off at the chests' height
     * it places.
     */
    private static void onlyTheRow(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        BlockPos oldRow = new BlockPos(3, 2, 2);
        BlockPos oneOff = new BlockPos(7, 3, 2);
        helper.setBlock(oldRow.below(), Blocks.STONE);
        helper.setBlock(oneOff.below(), Blocks.STONE);
        assertRefused(helper, player, oldRow, RailworksRefusal.STATION_OFF_RAIL);
        assertRefused(helper, player, oneOff, RailworksRefusal.STATION_OFF_RAIL);
        if (!place(helper, player, BESIDE).consumesAction() || !helper.getBlockState(BESIDE).is(RailworksRegistries.STATION.get())) {
            helper.fail("a Station in the row two off the Rail did not place", BESIDE);
        }
        helper.succeed();
    }

    private static void refusedInClearance(GameTestHelper helper) {
        // Something under the Rail to click the top of.
        helper.setBlock(UNDER, Blocks.STONE);
        refused(helper, IN_CLEARANCE, RailworksRefusal.STATION_OFF_RAIL);
    }

    private static void refusedBesideBend(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        // On from B, off line: the Rail bends through B, all along A to B.
        network(helper).add(Span.of(arm(helper, B), arm(helper, new BlockPos(13, 1, 7)), 6));
        refused(helper, player, BESIDE, RailworksRefusal.STATION_NOT_STRAIGHT);
    }

    private static void refusedByDiagonal(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        network(helper).remove(arm(helper, A), arm(helper, B));
        network(helper).add(Span.of(arm(helper, new BlockPos(1, 1, 1)), arm(helper, new BlockPos(7, 1, 7)), 9));
        // No block's centre is two off a diagonal: this one, at the row's height, is about 2.1 off it.
        BlockPos by = new BlockPos(5, 3, 2);
        helper.setBlock(by.below(), Blocks.STONE);
        refused(helper, player, by, RailworksRefusal.STATION_OFF_RAIL);
    }

    private static void refusedNearBendBeyond(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        // B on to C in line, then off line at C: A to B stays straight, B to C bends.
        BlockPos c = new BlockPos(13, 1, 3);
        network(helper).add(Span.of(arm(helper, B), arm(helper, c), 4));
        network(helper).add(Span.of(arm(helper, c), arm(helper, new BlockPos(17, 1, 7)), 6));
        // By B, the stretch reaches over it onto B to C; two blocks short of it, it stays on A to B.
        BlockPos byPylon = new BlockPos(9, 3, 1);
        // Something to click the top of.
        helper.setBlock(byPylon.below(), Blocks.STONE);
        PlacementPlan plan = Placements.planFor(player.getMainHandItem().getItem(), context(helper, player, byPylon));
        if (plan == null || !plan.isRefused() || plan.refusal() != RailworksRefusal.STATION_NOT_STRAIGHT) {
            helper.fail("a Station by B planned " + plan + ", not refused for the bend beyond B", byPylon);
            return;
        }
        BlockPos shortOfPylon = new BlockPos(7, 3, 1);
        helper.setBlock(shortOfPylon.below(), Blocks.STONE);
        if (!place(helper, player, shortOfPylon).consumesAction()
                || !helper.getBlockState(shortOfPylon).is(RailworksRegistries.STATION.get())) {
            helper.fail("a Station two blocks short of B, by straight Rail, was refused", shortOfPylon);
        }
        helper.succeed();
    }

    private static void placesByClimb(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        network(helper).remove(arm(helper, A), arm(helper, B));
        network(helper).add(Span.of(arm(helper, A), arm(helper, B.above()), 9));
        // Three quarters of the way up, the Rail is three quarters of a block higher, so the row is a
        // block higher there than by a level Rail: in the structure's roof, cleared, on something to
        // click the top of.
        BlockPos beside = new BlockPos(7, 4, 1);
        helper.setBlock(beside, Blocks.AIR);
        helper.setBlock(beside.below(), Blocks.STONE);
        if (!place(helper, player, beside).consumesAction() || !helper.getBlockState(beside).is(RailworksRegistries.STATION.get())) {
            helper.fail("a Station beside a straight climbing leg did not place: "
                    + Placements.planFor(player.getMainHandItem().getItem(), context(helper, player, beside)), beside);
        }
        helper.succeed();
    }

    private static void refused(GameTestHelper helper, BlockPos pos, RailworksRefusal expected) {
        refused(helper, player(helper), pos, expected);
    }

    private static void refused(GameTestHelper helper, ServerPlayer player, BlockPos pos, RailworksRefusal expected) {
        assertRefused(helper, player, pos, expected);
        helper.succeed();
    }

    /** That a Station at {@code pos} is refused {@code expected}, with its line, placing and spending nothing. */
    private static void assertRefused(GameTestHelper helper, ServerPlayer player, BlockPos pos, RailworksRefusal expected) {
        int held = player.getMainHandItem().getCount();
        PlacementPlan plan = Placements.planFor(player.getMainHandItem().getItem(), context(helper, player, pos));
        if (plan == null || !plan.isRefused() || plan.refusal() != expected) {
            helper.fail("a Station at " + pos + " planned " + plan + ", not refused " + expected, pos);
            return;
        }
        if (!StationItem.messageKey(expected).equals("message.railworks." + expected.name().toLowerCase(java.util.Locale.ROOT))) {
            helper.fail("a Station refused for " + expected + " says " + StationItem.messageKey(expected), pos);
        }
        if (place(helper, player, pos).consumesAction()
                || !helper.getBlockState(pos).isAir() || player.getMainHandItem().getCount() != held) {
            helper.fail("a refused Station was placed or spent", pos);
        }
    }

    private static void named(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        StationBlockEntity station = placed(helper, player, BESIDE);
        if (!station.rename(player, "  §cIron Mine ") || !station.name().equals("Iron Mine")) {
            helper.fail("naming the Station \"Iron Mine\" left it \"" + station.name() + "\"", BESIDE);
        }
        BlockPos at = helper.absolutePos(BESIDE);
        if (!stations(helper).get(at.getX(), at.getY(), at.getZ()).name().equals("Iron Mine")) {
            helper.fail("the level's Stations did not follow the new name", BESIDE);
        }
        if (station.rename(player, "   ") || !station.name().equals("Iron Mine")) {
            helper.fail("a blank name was taken", BESIDE);
        }
        helper.succeed();
    }

    private static void notNamedFromAfar(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        StationBlockEntity station = placed(helper, player, BESIDE);
        BlockPos far = helper.absolutePos(BESIDE).offset(40, 0, 0);
        player.snapTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        if (station.rename(player, "Far Away") || !station.name().equals(Station.DEFAULT_NAME)) {
            helper.fail("a player out of reach renamed the Station", BESIDE);
        }
        helper.succeed();
    }

    private static void nameSaved(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        StationBlockEntity station = placed(helper, player, BESIDE);
        station.rename(player, "Smelter");
        var registries = helper.getLevel().registryAccess();
        var tag = station.saveWithFullMetadata(registries);
        BlockEntity reloaded = BlockEntity.loadStatic(station.getBlockPos(), station.getBlockState(), tag, registries);
        if (!(reloaded instanceof StationBlockEntity back) || !back.name().equals("Smelter")) {
            helper.fail("a saved and reloaded Station came back as " + reloaded, BESIDE);
        }
        // The level's copy too, through the codec it is saved with.
        var saved = LevelStations.TYPE.codec().encodeStart(NbtOps.INSTANCE, LevelStations.of(helper.getLevel())).getOrThrow();
        Stations read = LevelStations.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow().stations();
        BlockPos at = helper.absolutePos(BESIDE);
        Station kept = read.get(at.getX(), at.getY(), at.getZ());
        if (kept == null || !kept.name().equals("Smelter")) {
            helper.fail("the level's saved Stations came back with " + kept, BESIDE);
        }
        helper.succeed();
    }

    private static void sharedNames(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        String name = "Shared " + helper.absolutePos(BESIDE).toShortString();
        placed(helper, player, BESIDE).rename(player, name);
        placed(helper, player, ACROSS).rename(player, name);
        if (stations(helper).named(name).size() != 2) {
            helper.fail("two Stations named alike are " + stations(helper).named(name), BESIDE);
        }
        helper.succeed();
    }

    private static void breaking(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        placed(helper, player, BESIDE);
        helper.getLevel().destroyBlock(helper.absolutePos(BESIDE), true);
        BlockPos at = helper.absolutePos(BESIDE);
        if (stations(helper).get(at.getX(), at.getY(), at.getZ()) != null) {
            helper.fail("a broken Station is still in the level's Stations", BESIDE);
        }
        helper.succeed();
    }

    private static void serves(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        placed(helper, player, BESIDE).rename(player, "Platform");
        Span span = span(helper);
        var network = LevelRails.of(helper.getLevel()).network();
        // The Station is four blocks from A's Arm; a car there is at it, and one two further on is not.
        Place by = new Place(span, span.b(), span.length() - 4.0);
        Place past = new Place(span, span.b(), span.length() - 6.0);
        var at = stations(helper).at(network, by).stream().map(Station::name).toList();
        if (!at.contains("Platform") || stations(helper).at(network, past).stream().anyMatch(s -> s.name().equals("Platform"))) {
            helper.fail("a car beside the Station is at " + at + ", or one past it is still at it", BESIDE);
        }
        helper.succeed();
    }

    // ---- setup ----

    /**
     * The Pylons and their Span, something to click the top of under {@link #BESIDE}, {@link #ACROSS}
     * and {@link #OFF}, and a survival player beside it holding four Stations.
     */
    private static ServerPlayer player(GameTestHelper helper) {
        if (!helper.getBlockState(A).is(RailworksRegistries.PYLON.get())) {
            helper.setBlock(A, RailworksRegistries.PYLON.get().defaultBlockState());
            helper.setBlock(B, RailworksRegistries.PYLON.get().defaultBlockState());
            LevelRails.of(helper.getLevel()).network().add(span(helper));
            for (BlockPos pos : new BlockPos[] {BESIDE, ACROSS, OFF}) {
                helper.setBlock(pos.below(), Blocks.STONE);
            }
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(STAND);
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 30.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get(), 4));
        return player;
    }

    private static Span span(GameTestHelper helper) {
        return Span.of(PylonColumn.arm(helper.absolutePos(A)), PylonColumn.arm(helper.absolutePos(B)), 8);
    }

    private static RailNetwork network(GameTestHelper helper) {
        return LevelRails.of(helper.getLevel()).network();
    }

    private static Arm arm(GameTestHelper helper, BlockPos pos) {
        return PylonColumn.arm(helper.absolutePos(pos));
    }

    private static Stations stations(GameTestHelper helper) {
        return LevelStations.of(helper.getLevel()).stations();
    }

    /** A click on the top of the block under {@code pos}, which places at {@code pos}. */
    static BlockPlaceContext context(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos below = helper.absolutePos(pos.below());
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.upFromBottomCenterOf(below, 1.0), Direction.UP, below, false));
    }

    static InteractionResult place(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        return player.getMainHandItem().useOn(context(helper, player, pos));
    }

    private static StationBlockEntity placed(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        place(helper, player, pos);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof StationBlockEntity station)) {
            throw helper.assertionException(pos, Component.literal("no Station was placed"));
        }
        return station;
    }
}
