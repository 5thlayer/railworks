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
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
 * Stations through the item, the block and a real player: placing one by the Rail, refused off it
 * and in its Clearance, naming it, and the level's Stations following the blocks. Which stretch of a
 * Span a Station serves is {@code StationTest}'s; what only a server shows is that the click asks
 * the level's Spans and that the name survives a save. All of these tests share the {@code station_}
 * prefix.
 */
final class StationTests {

    /** Eight apart, one segment tall, with a Span strung between the tops at y 1. */
    private static final BlockPos A = new BlockPos(1, 1, 3);
    private static final BlockPos B = new BlockPos(9, 1, 3);
    /** Beside the Rail, a block down from the car: where a Station goes and leaves the car's side free for a hopper. */
    private static final BlockPos BESIDE = new BlockPos(5, 1, 2);
    /** Under the Rail, between the Pylons. */
    private static final BlockPos UNDER = new BlockPos(5, 1, 3);
    /** In the Rail's own block, where a car passes. */
    private static final BlockPos IN_CLEARANCE = new BlockPos(5, 2, 3);
    /** Three blocks off the Rail. */
    private static final BlockPos OFF = new BlockPos(5, 1, 0);

    private StationTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("station_places_beside_the_rail_and_under_it", 20, StationTests::placesByTheRail);
        tests.test("station_is_refused_off_the_rail", 20, StationTests::refusedOffRail);
        tests.test("station_is_refused_in_the_clearance", 20, StationTests::refusedInClearance);
        tests.test("station_is_named_in_its_menu", 20, StationTests::named);
        tests.test("station_is_not_named_from_out_of_reach", 20, StationTests::notNamedFromAfar);
        tests.test("station_name_is_kept_across_a_save_and_reload", 20, StationTests::nameSaved);
        tests.test("station_names_may_be_shared", 20, StationTests::sharedNames);
        tests.test("station_renamed_item_places_a_station_of_that_name", 20, StationTests::renamedItem);
        tests.test("station_breaking_takes_it_out_of_the_level", 20, StationTests::breaking);
        tests.test("station_serves_the_stretch_of_its_span_beside_it", 20, StationTests::serves);
    }

    private static void placesByTheRail(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        if (!place(helper, player, BESIDE).consumesAction() || !place(helper, player, UNDER).consumesAction()) {
            helper.fail("a Station beside the Rail or under it did not place", BESIDE);
            return;
        }
        for (BlockPos pos : new BlockPos[] {BESIDE, UNDER}) {
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

    private static void refusedInClearance(GameTestHelper helper) {
        // Something under the Rail to click the top of.
        helper.setBlock(UNDER, Blocks.STONE);
        refused(helper, IN_CLEARANCE, RailworksRefusal.STATION_IN_CLEARANCE);
    }

    private static void refused(GameTestHelper helper, BlockPos pos, RailworksRefusal expected) {
        ServerPlayer player = player(helper);
        PlacementPlan plan = Placements.planFor(player.getMainHandItem().getItem(), context(helper, player, pos));
        if (plan == null || !plan.isRefused() || plan.refusal() != expected) {
            helper.fail("a Station at " + pos + " planned " + plan + ", not refused " + expected, pos);
            return;
        }
        if (place(helper, player, pos).consumesAction()
                || !helper.getBlockState(pos).isAir() || player.getMainHandItem().getCount() != 4) {
            helper.fail("a refused Station was placed or spent", pos);
        }
        helper.succeed();
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
        placed(helper, player, UNDER).rename(player, name);
        if (stations(helper).named(name).size() != 2) {
            helper.fail("two Stations named alike are " + stations(helper).named(name), BESIDE);
        }
        helper.succeed();
    }

    private static void renamedItem(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.getMainHandItem().set(DataComponents.CUSTOM_NAME,
                Component.literal("Depot"));
        if (!placed(helper, player, BESIDE).name().equals("Depot")) {
            helper.fail("a Station item named Depot placed a Station not called Depot", BESIDE);
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

    /** The Pylons and their Span, and a survival player beside it holding four Stations. */
    private static ServerPlayer player(GameTestHelper helper) {
        if (!helper.getBlockState(A).is(RailworksRegistries.PYLON.get())) {
            helper.setBlock(A, RailworksRegistries.PYLON.get().defaultBlockState());
            helper.setBlock(B, RailworksRegistries.PYLON.get().defaultBlockState());
            LevelRails.of(helper.getLevel()).network().add(span(helper));
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(new BlockPos(5, 1, 1));
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 30.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get(), 4));
        return player;
    }

    private static Span span(GameTestHelper helper) {
        return Span.of(PylonColumn.arm(helper.absolutePos(A)), PylonColumn.arm(helper.absolutePos(B)), 8);
    }

    private static Stations stations(GameTestHelper helper) {
        return LevelStations.of(helper.getLevel()).stations();
    }

    /** A click on the top of the block under {@code pos}, which places at {@code pos}. */
    private static BlockPlaceContext context(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos below = helper.absolutePos(pos.below());
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.upFromBottomCenterOf(below, 1.0), Direction.UP, below, false));
    }

    private static InteractionResult place(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
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
