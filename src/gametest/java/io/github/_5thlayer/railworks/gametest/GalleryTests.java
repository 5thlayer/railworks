// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Layout;
import io.github._5thlayer.libworks.testkit.GalleryCheck.PlotSize;
import io.github._5thlayer.libworks.testkit.GalleryCheck.Scene;
import io.github._5thlayer.railworks.LevelChargers;
import io.github._5thlayer.railworks.LevelHatches;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.PlatformsNear;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Clearance;
import io.github._5thlayer.railworks.rail.Logistics;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.Station;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Gallery pack (libworks ADR 0010), through libworks' {@link GalleryCheck}: offered and off, its
 * build running every command without failing, and each scene's key block where it stands, the
 * Stations scene's Stations in the level's Stations under their names, the flyover's two lines
 * strung and not joined, and every scene's Clearance inside what {@code gallery/clear} clears, which
 * reaches past a plot's ends as far as its lines' Clearance does. Beyond the build, the coupling
 * scene's sign, clicked, builds it again, leaving one Train and nothing loose, and the two logistics
 * scenes are built working: each requester's chest gets what it asks for, as the refuel scene's does
 * once its dry Train is fuelled, the charge scene's once its Train is charged, by a Creative Charger
 * and by a Charger its Burner Generator feeds, the far-away scene's Train fetches ore from its far
 * end out of view, and the Fluid car scene's carries its lava across. How a Train plans and runs its
 * jobs is the mechanics' own game tests' to check, never the Gallery's. Each test turns the pack on,
 * which reloads the server's data, and off again, passing or failing, so each runs in a batch of its
 * own.
 */
final class GalleryTests {

    /**
     * One scene a row, each row 16 blocks north of the last, as {@code gallery/build} lays them, on
     * plots 40 wide from {@link GalleryCheck#FIRST_PLOT_EAST} east of where each is run and 15 deep.
     * {@code gallery/clear} clears a block past a plot's west end and two past its east end too: the
     * Clearance of a line ending at an Arm on the plot's second or last block reaches two blocks past
     * that Arm.
     */
    private static final Layout ROWS = new Layout(1, 16, new PlotSize(40, 15, 1, 2));

    /** Where the gallery is run from: the platform's south-west corner, the plots spilling north and east of it. */
    private static final BlockPos ORIGIN = new BlockPos(0, 1, 8);
    /** Where a scene's sign stands, from where the scene is run. */
    private static final BlockPos SIGN = new BlockPos(21, 0, 0);
    /** The Stations scene's two Stations, from where it is run, and what it names them. */
    private static final BlockPos QUARRY = new BlockPos(4, 3, -8);
    private static final BlockPos SMELTER = new BlockPos(41, 3, -7);

    /**
     * The logistics scenes' first provider hatch, beside the first car spot of the Station on the
     * shuttle line's south leg, and the chests under the requesters beside the first and second car
     * spots of the one on its north leg, from where each is run.
     */
    private static final BlockPos PROVIDER = new BlockPos(11, 3, 0);
    private static final BlockPos FIRST_SPOT_CHEST = new BlockPos(11, 1, -10);
    private static final BlockPos SECOND_SPOT_CHEST = new BlockPos(16, 1, -10);
    /** The Fluid car scene's requester Fluid hatch beside Smelter's first car spot, from where it is run. */
    private static final BlockPos FLUID_REQUESTER = new BlockPos(11, 3, -10);
    /** The buckets of lava the Fluid car scene's requester asks for, half what its provider holds once built. */
    private static final int FLUID_BUCKETS = 16;
    private static final int BUCKET = (int) Logistics.MILLIBUCKETS_PER_BUCKET;
    /** The refuel scene's provider of coal beside Mine's leading car's place, from where it is run. */
    private static final BlockPos LEADING_FUEL = new BlockPos(6, 3, 0);
    /** The charge scene's Creative Charger beside Mine's leading car's place, and the Charger by Smelter fed by its Burner Generator, from where it is run. */
    private static final BlockPos CREATIVE_CHARGER = new BlockPos(6, 3, 0);
    private static final BlockPos SMELTER_CHARGER = new BlockPos(6, 3, -10);
    /** The far-away scene's Mine, at its line's far end, and Smelter's requester by its plot and the chest under it, from where it is run. */
    private static final BlockPos FAR_MINE = new BlockPos(260, 3, -4);
    private static final BlockPos NEAR_REQUESTER = new BlockPos(11, 3, -4);
    private static final BlockPos NEAR_CHEST = new BlockPos(11, 1, -4);
    /** The ends of the strip its line runs along, out past its plot, from where it is run. */
    private static final BlockPos FAR_WEST = new BlockPos(22, 0, -8);
    private static final BlockPos FAR_EAST = new BlockPos(262, 0, -4);
    /** How high above the strip's foot the scene builds, its Station and hatch on their pillars, as {@code far_away/laid} clears it. */
    private static final int FAR_HEIGHT = 6;
    /** The flyover's Arms, from where it is run: the low line's, west to east, and the high line's, south to north. */
    private static final List<BlockPos> LOW_LINE = List.of(
            new BlockPos(3, 1, -6), new BlockPos(11, 1, -6), new BlockPos(19, 1, -6), new BlockPos(27, 1, -6),
            new BlockPos(35, 1, -6), new BlockPos(41, 1, -6));
    private static final List<BlockPos> HIGH_LINE = List.of(new BlockPos(23, 6, -2), new BlockPos(23, 6, -12));

    /** The stacks the logistics scene's provider holds once built. */
    private static final int PROVIDED_STACKS = 8;

    /** A test of one scene, run on its own from where the gallery is run, loads only the first plot. */
    private static final int ONE_SCENE = 1;

    private GalleryTests() {
    }

    /**
     * The scenes in the order {@code build} lays them, each with a key block and where its function
     * places it, inside its plot, which {@link GalleryCheck#assertKeyBlocks} asks of it.
     */
    private static List<Scene> scenes() {
        var pylon = RailworksRegistries.PYLON.get();
        return List.of(
                new Scene("pylons", new BlockPos(24, 2, -6), pylon),
                new Scene("limits", new BlockPos(21, 3, -2), pylon),
                new Scene("curves", new BlockPos(21, 1, -3), pylon),
                new Scene("locomotive", new BlockPos(11, 1, -6), pylon),
                new Scene("coupling", new BlockPos(16, 0, -2), Blocks.CHEST),
                new Scene("stations", QUARRY, RailworksRegistries.STATION.get()),
                new Scene("logistics", PROVIDER, RailworksRegistries.HATCH.get()),
                new Scene("two_loads", PROVIDER, RailworksRegistries.HATCH.get()),
                new Scene("flyover", HIGH_LINE.getFirst(), pylon),
                new Scene("refuel", LEADING_FUEL, RailworksRegistries.HATCH.get()),
                new Scene("charge", CREATIVE_CHARGER, RailworksRegistries.CREATIVE_CHARGER.get()),
                new Scene("far_away", NEAR_REQUESTER, RailworksRegistries.HATCH.get()),
                new Scene("fluid", PROVIDER, RailworksRegistries.FLUID_HATCH.get()));
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.testOnItsOwn("gallery_pack_is_offered_and_off_and_its_build_lays_every_scene_with_no_failing_command",
                GalleryCheck.TICKS, GalleryTests::builds);
        tests.testOnItsOwn("gallery_clear_clears_its_plot_and_its_lines_reach_past_its_ends",
                GalleryCheck.TICKS, helper -> GalleryCheck.clears(helper, Railworks.MOD_ID, ORIGIN, ROWS));
        tests.testOnItsOwn("gallery_coupling_sign_clicked_builds_the_scene_again_with_one_train",
                GalleryCheck.TICKS, GalleryTests::signReplays);
        tests.testOnItsOwn("gallery_logistics_scenes_deliver_what_each_requester_asks_for",
                GalleryCheck.TICKS, GalleryTests::logisticsDeliver);
        tests.testOnItsOwn("refuel_the_gallery_scene_fuels_its_dry_train_which_then_delivers",
                GalleryCheck.TICKS, GalleryTests::refuelDelivers);
        tests.testOnItsOwn("electric_the_gallery_charge_scene_charges_its_train_at_both_stations_and_it_delivers",
                GalleryCheck.TICKS, GalleryTests::chargeDelivers);
        tests.testOnItsOwn("fluid_the_gallery_scene_carries_its_lava_to_the_requester",
                GalleryCheck.TICKS, GalleryTests::fluidDelivers);
        // Its Train runs out 240 blocks and back, after the scene is built twice.
        tests.testOnItsOwn("far_the_gallery_scene_brings_ore_back_from_its_far_end_out_of_view",
                3 * GalleryCheck.TICKS, GalleryTests::farAwayDelivers);
    }

    /**
     * libworks' build check, written out to add what no key block shows: the Stations scene's
     * Stations, registered by name, the flyover's two lines, each strung and neither joined to the
     * other, every scene's Clearance inside what {@code gallery/clear} clears, and every scene's
     * Stations, hatches and Chargers in the row beside the Rail.
     */
    private static void builds(GameTestHelper helper) {
        var scenes = scenes();
        var at = helper.absolutePos(ORIGIN);
        var functions = helper.getLevel().getServer().getFunctions();
        var stations = ROWS.sceneOrigin(at, indexOf(scenes, "stations"));
        var flyover = ROWS.sceneOrigin(at, indexOf(scenes, "flyover"));
        withPack(helper, scenes.size(), (source, steps) -> steps
                .thenExecute(() -> {
                    GalleryCheck.assertLoads(helper, source, Railworks.MOD_ID);
                    for (var scene : scenes) {
                        helper.assertTrue(functions.get(GalleryPack.scene(Railworks.MOD_ID, scene.name())).isPresent(),
                                "there is no scene function " + scene.name());
                    }
                    GalleryCheck.assertRuns(helper, source, GalleryPack.build(Railworks.MOD_ID));
                    GalleryCheck.assertKeyBlocks(helper, at, ROWS, scenes);
                    // On the build's tick, before a neighbouring test strings a line of its own across a plot.
                    for (int index = 0; index < scenes.size(); index++) {
                        reachCleared(helper, scenes.get(index).name(), ROWS.plot(ROWS.sceneOrigin(at, index)));
                    }
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
                    for (int index = 0; index < scenes.size(); index++) {
                        inRow(helper, scenes.get(index).name(), ROWS.plot(ROWS.sceneOrigin(at, index)));
                    }
                })).thenSucceed();
    }

    /** The coupling scene's sign, clicked, builds the scene again: the one Train of three cars, and nothing loose. */
    private static void signReplays(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        var sign = at.offset(SIGN);
        var plot = ROWS.plot(at);
        withPack(helper, ONE_SCENE, (source, steps) -> steps.thenExecute(() -> {
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
        withPack(helper, 2, (source, steps) -> steps
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
        withPack(helper, ONE_SCENE, (source, steps) -> steps
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "refuel")))
                .thenWaitUntil(() -> holds(helper, at.offset(FIRST_SPOT_CHEST), Items.IRON_ORE))).thenSucceed();
    }

    /** The Fluid car scene: its Train carries the 16 buckets of lava Smelter's requester asks for from Mine's 32. */
    private static void fluidDelivers(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        withPack(helper, ONE_SCENE, (source, steps) -> steps
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "fluid")))
                .thenWaitUntil(() -> {
                    var requester = LevelHatches.of(helper.getLevel()).at(at.offset(FLUID_REQUESTER)).orElseThrow();
                    var provider = LevelHatches.of(helper.getLevel()).at(at.offset(PROVIDER)).orElseThrow();
                    helper.assertTrue(requester.tank().fluid().is(Fluids.LAVA) && requester.tank().amount() == FLUID_BUCKETS * BUCKET
                                    && provider.tank().amount() == FLUID_BUCKETS * BUCKET,
                            "the requester holds " + requester.tank().amount() + " of lava and the provider " + provider.tank().amount());
                })).thenSucceed();
    }

    /**
     * The charge scene: its shuttle, set down with no charge, is filled by Mine's Creative Chargers and
     * carries the ore to Smelter's chest, where the Charger its Burner Generator feeds gives the
     * Locomotive standing by it some of what it holds.
     */
    private static void chargeDelivers(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        boolean[] given = {false};
        long[] most = {0L};
        helper.onEachTick(() -> {
            var charger = LevelChargers.of(helper.getLevel()).at(at.offset(SMELTER_CHARGER));
            charger.ifPresent(c -> {
                long now = c.held();
                // Fed by the Burner Generator, it holds more; charging the Locomotive, less.
                given[0] |= now < most[0];
                most[0] = Math.max(most[0], now);
            });
        });
        withPack(helper, ONE_SCENE, (source, steps) -> steps
                .thenExecute(() -> GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "charge")))
                .thenWaitUntil(() -> {
                    holds(helper, at.offset(FIRST_SPOT_CHEST), Items.IRON_ORE);
                    helper.assertTrue(most[0] > 0L, "the Burner Generator put nothing in Smelter's Charger");
                    helper.assertTrue(given[0], "Smelter's Charger gave the Locomotive by it nothing");
                })).thenSucceed();
    }

    /**
     * The far-away scene: its far part built once the chunks along its line load, and those let go of,
     * its Train runs out of view, every car of it unseen, to load ore at the far Mine with no chunk of
     * it loaded, and brings it back to Smelter's chest by the plot. Built again, as its sign builds it,
     * while its Train is out there, it leaves one Train on the line and nothing loose along it.
     */
    private static void farAwayDelivers(GameTestHelper helper) {
        var at = helper.absolutePos(ORIGIN);
        var plot = ROWS.plot(at);
        var strip = farStrip(at);
        // Generated now, as a world played in would have them: the runner ticks faster than chunks
        // generate off the server thread, and the scene would wait on them for longer than it runs.
        var west = at.offset(FAR_WEST);
        var east = at.offset(FAR_EAST);
        for (int cx = west.getX() >> 4; cx <= east.getX() >> 4; cx++) {
            for (int cz = west.getZ() >> 4; cz <= east.getZ() >> 4; cz++) {
                helper.getLevel().getChunk(cx, cz);
            }
        }
        // Cleared before as after: what an earlier test left on the strip, failing before its own
        // clear, would hold this scene's Spans or stand a second Mine by it.
        clearFarStrip(helper, strip);
        List<Train> train = new java.util.ArrayList<>();
        boolean[] unseen = {false};
        helper.onEachTick(() -> {
            var rails = LevelRails.of(helper.getLevel());
            if (!train.isEmpty() && train.getFirst().cars().stream().allMatch(car -> rails.view(car) == null)
                    && rails.trains().of(train.getFirst().cars().getFirst().id()) == train.getFirst()) {
                unseen[0] = true;
            }
        });
        List<Train> first = new java.util.ArrayList<>();
        withPack(helper, ONE_SCENE, (source, steps) -> steps
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "far_away"));
                    first.addAll(trainsIn(helper, plot));
                    helper.assertTrue(first.size() == 1, "the scene set " + first + ", not one Train");
                    train.addAll(first);
                })
                // Built again, as its sign builds it, once its Train is out of view on the far stretch.
                .thenWaitUntil(() -> helper.assertTrue(unseen[0], "the Train was never wholly out of view"))
                .thenExecute(() -> {
                    GalleryCheck.assertRuns(helper, source, GalleryPack.scene(Railworks.MOD_ID, "far_away"));
                    train.clear();
                    unseen[0] = false;
                    train.addAll(trainsIn(helper, plot));
                    helper.assertTrue(train.size() == 1, "building again set " + train + ", not one Train");
                })
                .thenWaitUntil(() -> {
                    var rails = LevelRails.of(helper.getLevel());
                    var old = first.getFirst();
                    helper.assertTrue(old.cars().stream().allMatch(car -> rails.trains().of(car.id()) == null
                            && !rails.fallen().contains(car.id())), "the first build's Train is still out on the line");
                    holds(helper, at.offset(NEAR_CHEST), Items.IRON_ORE);
                    helper.assertTrue(unseen[0], "the Train built again was never wholly out of view");
                    helper.assertFalse(helper.getLevel().getForceLoadedChunks().contains(net.minecraft.world.level.ChunkPos.containing(at.offset(FAR_MINE)).pack()),
                            "the scene kept its far end's chunk loaded");
                    var loose = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            net.minecraft.world.phys.AABB.encapsulatingFullBlocks(at.offset(FAR_WEST), at.offset(FAR_EAST).above(8)));
                    helper.assertTrue(loose.isEmpty(), "building again left items along the line: " + loose.stream().map(ItemEntity::getItem).toList());
                })
                // Off the line, so cutting it derails nothing out of view.
                .thenWaitUntil(() -> helper.assertTrue(trainsIn(helper, strip).isEmpty(), "the Train is still out on the line"))
                .thenExecute(() -> clearFarStrip(helper, strip))
                .thenExecute(() -> farStripCleared(helper, strip, train.getFirst()))).thenSucceed();
    }

    /**
     * Breaks what stands along the far-away scene's line, {@code strip}, as {@code gallery/clear}
     * breaks a plot, its Pylons cutting their Spans and its Station and hatch taking their records with
     * them, then takes the items loose along it. The plot's own clear stops at the plot, and the test
     * grid lays the next tests along the line, where a second Station named Mine would stop their
     * Trains; run first, it breaks what an earlier test that failed before its own clear left there.
     */
    private static void clearFarStrip(GameTestHelper helper, BoundingBox strip) {
        var level = helper.getLevel();
        for (var pos : BlockPos.betweenClosed(strip.minX(), strip.minY(), strip.minZ(), strip.maxX(), strip.maxY(), strip.maxZ())) {
            if (!level.getBlockState(pos).isAir()) {
                level.destroyBlock(pos, false);
            }
        }
        level.getEntitiesOfClass(ItemEntity.class, AABB.of(strip)).forEach(ItemEntity::discard);
    }

    /** Asserts nothing of the far-away scene is left along its line, {@code strip}: no Station, hatch, Span or Train, nor a car of its {@code train} fallen off it. */
    private static void farStripCleared(GameTestHelper helper, BoundingBox strip, Train train) {
        var level = helper.getLevel();
        var rails = LevelRails.of(level);
        var stations = LevelStations.of(level).stations().all().stream()
                .filter(station -> strip.isInside(station.x(), station.y(), station.z())).toList();
        helper.assertTrue(stations.isEmpty(), "the far-away scene left Stations along its line: " + stations);
        var hatches = LevelHatches.of(level).all().stream().filter(hatch -> strip.isInside(hatch.pos())).toList();
        helper.assertTrue(hatches.isEmpty(), "the far-away scene left hatches along its line at "
                + hatches.stream().map(hatch -> hatch.pos().toShortString()).toList());
        var spans = rails.network().all().stream().filter(span -> inside(strip, span.a()) || inside(strip, span.b())).toList();
        helper.assertTrue(spans.isEmpty(), "the far-away scene left Spans along its line: " + spans);
        helper.assertTrue(trainsIn(helper, strip).isEmpty(), "the far-away scene left a Train along its line");
        helper.assertTrue(train.cars().stream().noneMatch(car -> rails.fallen().contains(car.id())),
                "the far-away scene's Train fell off its line");
    }

    /** libworks' {@link GalleryCheck#withPack} over the first {@code plots} plots, as {@code build} lays them, their reach included. */
    private static GameTestSequence withPack(GameTestHelper helper, int plots, BiConsumer<CommandSourceStack, GameTestSequence> steps) {
        return GalleryCheck.withPack(helper, Railworks.MOD_ID, ORIGIN, ROWS, plots, steps);
    }

    /** The strip the far-away scene run from {@code at} builds its line along, from inside its plot out to Mine, absolute. */
    private static BoundingBox farStrip(BlockPos at) {
        return BoundingBox.fromCorners(at.offset(FAR_WEST), at.offset(FAR_EAST).above(FAR_HEIGHT));
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

    /**
     * Asserts each Station on {@code plot} serves a Span and has a platform, and each hatch, Fluid
     * hatch and Charger on it joins a Station's platform: every one of them stands in the row beside
     * the Rail, two blocks off its line at a Cargo car's crates' height, where nothing else counts.
     */
    private static void inRow(GameTestHelper helper, String scene, BoundingBox plot) {
        var level = helper.getLevel();
        var network = LevelRails.of(level).network();
        var stations = LevelStations.of(level).stations();
        var joining = Set.of(RailworksRegistries.HATCH.get(), RailworksRegistries.FLUID_HATCH.get(),
                RailworksRegistries.CHARGER.get(), RailworksRegistries.CREATIVE_CHARGER.get());
        for (var pos : BlockPos.betweenClosed(plot.minX(), plot.minY(), plot.minZ(), plot.maxX(), plot.maxY(), plot.maxZ())) {
            var block = level.getBlockState(pos).getBlock();
            if (block == RailworksRegistries.STATION.get()) {
                var station = stations.get(pos.getX(), pos.getY(), pos.getZ());
                helper.assertTrue(station != null && station.foot(network).isPresent() && Platform.of(network, station).isPresent(),
                        "the " + scene + " scene's Station at " + pos.toShortString() + " serves no Span");
            } else if (joining.contains(block)) {
                helper.assertTrue(PlatformsNear.joined(level, pos).isPresent(),
                        "the " + scene + " scene's " + block.getName().getString() + " at " + pos.toShortString() + " joins no Station");
            }
        }
    }

    /**
     * Asserts the Clearance of each Span between two Pylons on {@code plot} lies in it, where
     * {@code gallery/clear} clears before a build: a block an earlier visit left past it would refuse
     * the Span. The far-away scene's line, running on out of its plot, clears its own way.
     */
    private static void reachCleared(GameTestHelper helper, String scene, BoundingBox plot) {
        var network = LevelRails.of(helper.getLevel()).network();
        for (var span : network.all()) {
            if (pylonOn(helper, plot, span.a()) && pylonOn(helper, plot, span.b())) {
                for (var cell : Clearance.along(network.curve(span))) {
                    helper.assertTrue(plot.isInside(cell.x(), cell.y(), cell.z()),
                            "the " + scene + " scene's Span " + span + " keeps clear " + cell + ", past what gallery/clear clears");
                }
            }
        }
    }

    /** Whether a Pylon stands at {@code arm} on {@code plot}, not a Span's stale end a neighbouring test left under the floor. */
    private static boolean pylonOn(GameTestHelper helper, BoundingBox plot, Arm arm) {
        return inside(plot, arm) && helper.getLevel().getBlockState(PylonColumn.block(arm)).is(RailworksRegistries.PYLON.get());
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
