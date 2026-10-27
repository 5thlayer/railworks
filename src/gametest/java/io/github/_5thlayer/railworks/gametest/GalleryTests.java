// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Layout;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * The Gallery pack (libworks ADR 0010), through libworks' {@link GalleryCheck}: offered and off, its
 * build running every command without failing, and each scene's key block where it stands, the
 * Stations scene's Stations in the level's Stations under their names. Beyond the build, the
 * coupling scene's sign, clicked, builds it again, leaving one Train and nothing loose. What a
 * scene's Trains do is the mechanics' own game tests' to check, never the Gallery's. Each test turns
 * the pack on, which reloads the server's data, and off again, passing or failing, so each runs in a
 * batch of its own.
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
                new Scene("stations", QUARRY, RailworksRegistries.STATION.get()));
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.testOnItsOwn("gallery_pack_is_offered_and_off_and_its_build_lays_every_scene_with_no_failing_command",
                GalleryCheck.TICKS, GalleryTests::builds);
        tests.testOnItsOwn("gallery_coupling_sign_clicked_builds_the_scene_again_with_one_train",
                GalleryCheck.TICKS, GalleryTests::signReplays);
    }

    /** libworks' build check, written out to add what no key block shows: the Stations scene's Stations, registered by name. */
    private static void builds(GameTestHelper helper) {
        var scenes = scenes();
        var at = helper.absolutePos(ORIGIN);
        var functions = helper.getLevel().getServer().getFunctions();
        var stations = ROWS.sceneOrigin(at, scenes.size() - 1);
        GalleryCheck.withPack(helper, Railworks.MOD_ID, ORIGIN, ROWS, scenes.size(), (source, steps) -> steps
                .thenExecute(() -> {
                    GalleryCheck.assertLoads(helper, source, Railworks.MOD_ID);
                    for (var scene : scenes) {
                        helper.assertTrue(functions.get(GalleryPack.scene(Railworks.MOD_ID, scene.name())).isPresent(),
                                "there is no scene function " + scene.name());
                    }
                    GalleryCheck.assertRuns(helper, source, GalleryPack.build(Railworks.MOD_ID));
                    GalleryCheck.assertKeyBlocks(helper, at, ROWS, scenes);
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
