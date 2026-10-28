// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;
import java.util.Set;

import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Layout;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import io.github._5thlayer.railworks.LevelHatches;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Gallery pack (libworks ADR 0010), through libworks' {@link GalleryCheck}: offered and off, its
 * build running every command without failing, and each scene's key block where it stands, the
 * Stations scene's Stations in the level's Stations under their names, and the flyover's two lines
 * strung and not joined. Beyond the build, the coupling scene's sign, clicked, builds it again,
 * leaving one Train and nothing loose, and the two logistics scenes are built working: each
 * requester's chest gets what it asks for, as the refuel scene's does once its dry Train is fuelled. How a Train plans and runs its jobs is the mechanics' own
 * game tests' to check, never the Gallery's. Each test turns the pack on, which reloads the server's
 * data, and off again, passing or failing, so each runs in a batch of its own.
 */
final class GalleryTests {

    /** Three scenes a row, each row 16 blocks north of the last, as {@code gallery/build} lays them. */
    private static final Layout ROWS = new Layout(3, 16);

    /** Where the gallery is run from: the platform's south-west corner, the plots spilling north and east of it. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    /** Where a scene's sign stands, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(14, 0, 0);
    /** The Stations scene's two Stations, from where it is run, and what it names them. */
    private static final BlockPos QUARRY = new BlockPos(4, 2, -7);
    private static final BlockPos SMELTER = new BlockPos(24, 2, -5);

    /** The logistics scenes' first provider hatch, and the chests under the requesters beside their first and second car spots, from where each is run. */
    private static final BlockPos PROVIDER = new BlockPos(7, 2, -5);
    private static final BlockPos FIRST_SPOT_CHEST = new BlockPos(22, 0, -7);
    private static final BlockPos SECOND_SPOT_CHEST = new BlockPos(20, 0, -7);
    /** The refuel scene's provider of coal beside Mine's leading car's place, from where it is run. */
    private static final BlockPos LEADING_FUEL = new BlockPos(5, 2, -5);
    /** The flyover's Arms, from where it is run: the low line's, west to east, and the high line's, south to north. */
    private static final List<BlockPos> LOW_LINE = List.of(
            new BlockPos(3, 1, -6), new BlockPos(11, 1, -6), new BlockPos(19, 1, -6), new BlockPos(25, 1, -6));
    private static final List<BlockPos> HIGH_LINE = List.of(new BlockPos(15, 6, -1), new BlockPos(15, 6, -12));

    /** The stacks the logistics scene's provider holds once built. */
    private static final int PROVIDED_STACKS = 8;

    /** A test of one scene, run on its own from where the gallery is run, loads only the first plot. */
    private static final int ONE_SCENE = 1;

    private GalleryTests() {
    }

    /** The scenes in the order {@code build} lays them, each with a key block and where its function places it. */
    private static List<Scene> scenes() {
        var pylon = RailworksRegistries.PYLON.get();
        return List.of(
                new Scene("pylons", new BlockPos(23, 3, -6), pylon),
                new Scene("limits", new BlockPos(14, 3, -2), pylon),
                new Scene("curves", new BlockPos(14, 1, -4), pylon),
                new Scene("locomotive", new BlockPos(11, 1, -6), pylon),
                new Scene("coupling", new BlockPos(10, 0, -2), Blocks.CHEST),
                new Scene("stations", QUARRY, RailworksRegistries.STATION.get()),
                new Scene("logistics", PROVIDER, RailworksRegistries.HATCH.get()),
                new Scene("two_loads", PROVIDER, RailworksRegistries.HATCH.get()),
                new Scene("flyover", HIGH_LINE.getLast(), pylon),
                new Scene("refuel", LEADING_FUEL, RailworksRegistries.HATCH.get()));
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.testOnItsOwn("gallery_pack_is_offered_and_off_and_its_build_lays_every_scene_with_no_failing_command",
                GalleryCheck.TICKS, GalleryTests::builds);
        tests.testOnItsOwn("gallery_coupling_sign_clicked_builds_the_scene_again_with_one_train",
                GalleryCheck.TICKS, GalleryTests::signReplays);
        tests.testOnItsOwn("gallery_logistics_scenes_deliver_what_each_requester_asks_for",
                GalleryCheck.TICKS, GalleryTests::logisticsDeliver);
        tests.testOnItsOwn("refuel_the_gallery_scene_fuels_its_dry_train_which_then_delivers",
                GalleryCheck.TICKS, GalleryTests::refuelDelivers);
    }

    /**
     * libworks' build check, written out to add what no key block shows: the Stations scene's
     * Stations, registered by name, and the flyover's two lines, each strung and neither joined to
     * the other.
     */
    private static void builds(GameTestHelper helper) {
        var scenes = scenes();
        var at = helper.absolutePos(ORIGIN);
        var functions = helper.getLevel().getServer().getFunctions();
        var stations = ROWS.sceneOrigin(at, indexOf(scenes, "stations"));
        var flyover = ROWS.sceneOrigin(at, indexOf(scenes, "flyover"));
        GalleryCheck.withPack(helper, Railworks.MOD_ID, ORIGIN, ROWS, scenes.size(), (source, steps) -> steps
                .thenExecute(() -> {
                    GalleryCheck.assertLoads(helper, source, Railworks.MOD_ID);
                    for (var scene : scenes) {
                        helper.assertTrue(functions.get(GalleryPack.scene(Railworks.MOD_ID, scene.name())).isPresent(),
                                "there is no scene function " + scene.name());
                    }
                    GalleryCheck.assertRuns(helper, source, GalleryPack.build(Railworks.MOD_ID));
                    GalleryCheck.assertKeyBlocks(helper, at, ROWS, scenes);
                    var low = arms(flyover, LOW_LINE);
                    var high = arms(flyover, HIGH_LINE);
                    strung(helper, low);
                    strung(helper, high);
                    notJoined(helper, low, high);
                })
                // A Station set by a command registers as its block entity loads, a tick on.
                .thenWaitUntil(() -> {
                    var quarry = registered(helper, stations.offset(QUARRY), "Quarry");
                    var smelter = registered(helper, stations.offset(SMELTER), "Smelter");
                    // Set by setblock, which no placement rule asks: the scene shows only what a player could place.
                    var network = LevelRails.of(helper.getLevel()).network();
                    helper.assertTrue(Platform.of(network, quarry).isPresent() && Platform.of(network, smelter).isPresent(),
                            "a Station of the Stations scene has no platform");
                    helper.assertTrue(Platform.overlapping(network, quarry, List.of(smelter)).isEmpty(),
                            "the Stations scene's two platforms overlap");
                })).thenSucceed();
    }

    /** The coupling scene's sign, clicked, builds the scene again: the one Train of three cars, and nothing loose. */
    private static void signReplays(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        var sign = at.offset(SIGN);
        var plot = GalleryCheck.plot(at);
        GalleryCheck.withPack(helper, Railworks.MOD_ID, ORIGIN, ROWS, ONE_SCENE, (source, steps) -> steps.thenExecute(() -> {
            GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "coupling"));
            helper.assertTrue(helper.getLevel().getBlockEntity(sign) instanceof SignBlockEntity entity
                            && entity.executeClickCommandsIfPresent(helper.getLevel(), source.getPlayer(), sign, true),
                    "the scene's sign runs no command when clicked");
            var trains = trainsIn(helper, plot);
            helper.assertTrue(trains.size() == 1 && trains.getFirst().cars().size() == 3,
                    "building the scene again left " + trains + ", not one Train of three cars");
            var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, AABB.of(plot));
            helper.assertTrue(loose.isEmpty(), "building the scene again left items loose: " + loose.stream().map(ItemEntity::getItem).toList());
        })).thenSucceed();
    }

    /**
     * The logistics scene, built twice as its sign rebuilds it, and the two-loads scene on the plot
     * east of it: the provider holds only what one build gives, and each requester's chest fills with
     * the item it asks for, carried by the scene's automatic Train.
     */
    private static void logisticsDeliver(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        var second = ROWS.sceneOrigin(at, 1);
        GalleryCheck.withPack(helper, Railworks.MOD_ID, ORIGIN, ROWS, 2, (source, steps) -> steps
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "logistics"));
                    GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "logistics"));
                    GalleryCheck.assertRuns(helper, source.withPosition(Vec3.atBottomCenterOf(second)),
                            GalleryPack.scene(Railworks.MOD_ID, "two_loads"));
                    var provider = LevelHatches.of(helper.getLevel()).at(at.offset(PROVIDER)).orElseThrow();
                    long held = 0;
                    for (int slot = 0; slot < provider.contents().size(); slot++) {
                        held += provider.contents().getAmountAsLong(slot);
                    }
                    helper.assertTrue(held == PROVIDED_STACKS * 64L,
                            "building the logistics scene twice left its provider holding " + held + " ore, not " + PROVIDED_STACKS * 64);
                })
                .thenWaitUntil(() -> {
                    holds(helper, at.offset(FIRST_SPOT_CHEST), Items.IRON_ORE);
                    holds(helper, second.offset(SECOND_SPOT_CHEST), Items.RAW_COPPER);
                    holds(helper, second.offset(FIRST_SPOT_CHEST), Items.RAW_GOLD);
                })).thenSucceed();
    }

    /** The refuel scene: its Train, set down dry, is fuelled at Mine and carries the ore to the Smelter's chest. */
    private static void refuelDelivers(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        GalleryCheck.withPack(helper, Railworks.MOD_ID, ORIGIN, ROWS, ONE_SCENE, (source, steps) -> steps
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "refuel")))
                .thenWaitUntil(() -> holds(helper, at.offset(FIRST_SPOT_CHEST), Items.IRON_ORE))).thenSucceed();
    }

    /** The Arms at {@code line}, from {@code origin}. */
    private static List<Arm> arms(BlockPos origin, List<BlockPos> line) {
        return line.stream().map(pos -> PylonColumn.arm(origin.offset(pos))).toList();
    }

    /** Asserts a Span strings each of the {@code line}'s Arms to the next. */
    private static void strung(GameTestHelper helper, List<Arm> line) {
        var network = LevelRails.of(helper.getLevel()).network();
        for (int next = 1; next < line.size(); next++) {
            helper.assertTrue(network.contains(line.get(next - 1), line.get(next)),
                    "the flyover has no Span from " + line.get(next - 1) + " to " + line.get(next));
        }
    }

    /** Asserts no Span at an Arm of {@code one} reaches an Arm of {@code other}. */
    private static void notJoined(GameTestHelper helper, List<Arm> one, List<Arm> other) {
        var network = LevelRails.of(helper.getLevel()).network();
        for (var arm : one) {
            for (var span : network.spansAt(arm)) {
                helper.assertTrue(!other.contains(span.a()) && !other.contains(span.b()),
                        "the flyover's lines are joined by " + span);
            }
        }
    }

    /** Asserts the chest at {@code pos}, absolute, holds some {@code item}. */
    private static void holds(GameTestHelper helper, BlockPos pos, Item item) {
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.hasAnyOf(Set.of(item)),
                "the chest at " + pos.toShortString() + " holds no " + item);
    }

    private static int indexOf(List<Scene> scenes, String name) {
        for (int index = 0; index < scenes.size(); index++) {
            if (scenes.get(index).name().equals(name)) {
                return index;
            }
        }
        throw new IllegalArgumentException("there is no scene " + name);
    }

    /** The Station at {@code pos}, absolute, asserting the level's Stations hold it under {@code name}. */
    private static Station registered(GameTestHelper helper, BlockPos pos, String name) {
        Station station = LevelStations.of(helper.getLevel()).stations().get(pos.getX(), pos.getY(), pos.getZ());
        helper.assertTrue(station != null && station.name().equals(name),
                "the Station at " + pos.toShortString() + " is in the level's Stations as " + station + ", not " + name);
        return station;
    }

    /** The Trains whose leading car stands on a Span with an end in {@code plot}. */
    private static List<Train> trainsIn(GameTestHelper helper, BoundingBox plot) {
        return LevelRails.of(helper.getLevel()).trains().all().stream()
                .filter(train -> inside(plot, train.head().span().a()) || inside(plot, train.head().span().b()))
                .toList();
    }

    private static boolean inside(BoundingBox box, Arm arm) {
        return box.isInside(arm.x(), arm.y(), arm.z());
    }
}
