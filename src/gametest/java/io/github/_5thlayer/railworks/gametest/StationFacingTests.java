// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.StationBlock;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.StationItem;
import io.github._5thlayer.railworks.StationPlacement;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * A Station faces the way it serves (ADR 0008): placed by a real player beside {@link LocomotiveTests}'
 * Span, which runs east from x -1 to x 11 along z 3, its arrow points the way the Trains it serves
 * head, the Trains with it on their drive side, and a Train standing at it is stopped there only
 * when it came in that way. Run driving on the right and, in a batch of their own, on the left. A
 * Station under the Rail, or one whose platform would overlap another's, is refused with the reason
 * on the action bar. Where the platform runs and what overlaps is {@code PlatformTest}'s; what only a
 * server shows is the placed block, the level's Stations and real cars. All of these tests share the
 * {@code logistics_station_} prefix, as the logistics work's (#26) game tests do.
 */
final class StationFacingTests {

    /** Beside the Rail a block down, north of it, at the front end of the Cargo car coupled behind the Locomotive. */
    private static final BlockPos NORTH = CargoAccessTests.BY_CARGO;
    /** The same, south of the Rail. */
    private static final BlockPos SOUTH = new BlockPos(0, 1, 4);
    /** Under the Rail. */
    private static final BlockPos UNDER = new BlockPos(5, 1, 3);

    private StationFacingTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("logistics_station_is_refused_under_the_rail", 20, StationFacingTests::refusedUnder);
        tests.test("logistics_station_is_refused_where_its_platform_overlaps_another", 20, StationFacingTests::refusedOverlapping);
        // Driving on the right, a Train heading west has the north side on its right.
        tests.test("logistics_station_north_of_the_rail_serves_trains_heading_west", 20,
                helper -> serves(helper, NORTH, Direction.WEST, true));
        tests.test("logistics_station_south_of_the_rail_lets_a_train_heading_west_pass", 20,
                helper -> serves(helper, SOUTH, Direction.EAST, false));
        tests.testDrivingLeft("logistics_station_driving_left_north_of_the_rail_lets_a_train_heading_west_pass", 20,
                helper -> serves(helper, NORTH, Direction.EAST, false));
        tests.testDrivingLeft("logistics_station_driving_left_south_of_the_rail_serves_trains_heading_west", 20,
                helper -> serves(helper, SOUTH, Direction.WEST, true));
        tests.test("logistics_station_a_train_at_rest_by_it_settles_onto_its_platform", 80, helper -> settles(helper, false));
        tests.test("logistics_station_a_train_with_its_driver_seated_settles_only_once_they_get_off", 120, helper -> settles(helper, true));
    }

    private static void refusedUnder(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        refused(helper, player, UNDER, RailworksRefusal.STATION_UNDER_RAIL);
        helper.succeed();
    }

    private static void refusedOverlapping(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        StationTests.place(helper, player, NORTH);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(NORTH)) instanceof StationBlockEntity first)) {
            helper.fail("the first Station was not placed", NORTH);
            return;
        }
        first.rename(player, "Iron Mine");
        // Across the Rail from it: serving the other way, its platform runs over the first's.
        refused(helper, player, SOUTH, RailworksRefusal.STATION_OVERLAPS);
        BlockPos at = helper.absolutePos(SOUTH);
        var refusal = StationPlacement.refusal(LevelRails.of(helper.getLevel()).network(),
                LevelStations.of(helper.getLevel()).stations().all(), at).orElseThrow();
        var line = StationItem.message(refusal);
        if (!(line.getContents() instanceof TranslatableContents contents)
                || !contents.getKey().equals("message.railworks.station_overlaps")
                || contents.getArgs().length != 1 || !"Iron Mine".equals(contents.getArgs()[0])) {
            helper.fail("the action bar for an overlap says " + line + ", not naming the Iron Mine", SOUTH);
        }
        helper.succeed();
    }

    /**
     * A Locomotive facing east with a Cargo car behind it, the car leading the way west, and a
     * Station placed at {@code pos}: its arrow points {@code facing}, and the Train is stopped at it
     * when {@code stopped}.
     */
    private static void serves(GameTestHelper helper, BlockPos pos, Direction facing, boolean stopped) {
        CargoCarEntity cargo = CargoAccessTests.cargoBehindLocomotive(helper);
        ServerPlayer player = player(helper);
        if (!StationTests.place(helper, player, pos).consumesAction()) {
            helper.fail("a Station beside the Rail was refused", pos);
            return;
        }
        Direction arrow = helper.getBlockState(pos).getValue(StationBlock.FACING);
        if (arrow != facing) {
            helper.fail("the Station's arrow points " + arrow + ", not " + facing, pos);
        }
        BlockPos at = helper.absolutePos(pos);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    var network = LevelRails.of(helper.getLevel()).network();
                    boolean there = LevelStations.of(helper.getLevel()).stations().stoppedAt(network, cargo.train()).stream()
                            .anyMatch(station -> station.x() == at.getX() && station.y() == at.getY() && station.z() == at.getZ());
                    if (there != stopped) {
                        helper.fail("the Train " + (stopped ? "is not" : "is") + " stopped at the Station", pos);
                    }
                    // Still pointing its way a tick after it loaded and looked again.
                    if (helper.getBlockState(pos).getValue(StationBlock.FACING) != facing) {
                        helper.fail("the Station's arrow turned to " + helper.getBlockState(pos).getValue(StationBlock.FACING), pos);
                    }
                })
                .thenSucceed();
    }

    /**
     * A Train at rest by a Station, off the place its platform puts it: with nobody in its Locomotive
     * it eases onto it and stops there; with a driver seated, pressing nothing, it stays where it was
     * brought to rest until they get off, and then settles. The Locomotive is set half a block east
     * of where {@link LocomotiveTests#placed} sets it, so the Cargo car stands half a block off the
     * Station's mark.
     */
    private static void settles(GameTestHelper helper, boolean seated) {
        ServerPlayer placer = LocomotiveTests.player(helper, -45.0F);
        placer.snapTo(placer.getX() + 0.5, placer.getY(), placer.getZ(), -45.0F, 5.0F);
        LocomotiveTests.use(helper, placer);
        LocomotiveEntity locomotive = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class,
                LocomotiveTests.searchBox(helper)).getFirst();
        CargoCarEntity cargo = CargoCarTests.placeCargoBeside(helper, locomotive, -1);
        var train = cargo.train();
        ServerPlayer driver = helper.makeMockServerPlayerInLevel();
        driver.setGameMode(GameType.SURVIVAL);
        if (seated) {
            locomotive.interact(driver, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (driver.getVehicle() != locomotive) {
                helper.fail("the driver did not board the Locomotive", NORTH);
                return;
            }
        }
        var start = train.head();
        AtomicReference<Place> held = new AtomicReference<>();
        CargoAccessTests.station(helper, NORTH);
        var sequence = helper.startSequence().thenIdle(40);
        if (seated) {
            sequence = sequence.thenExecute(() -> {
                if (!train.head().equals(start)) {
                    helper.fail("a Train with its driver seated moved from " + start + " to " + train.head(), NORTH);
                }
                driver.stopRiding();
            }).thenIdle(40);
        }
        sequence.thenExecute(() -> {
                    if (train.head().equals(start)) {
                        helper.fail("a Train at rest by the Station, nobody aboard, did not settle from " + start, NORTH);
                    }
                    held.set(train.head());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    if (!train.head().equals(held.get())) {
                        helper.fail("a settled Train went on moving, to " + train.head(), NORTH);
                    }
                    // Its Cargo car leads west: its middle on the Station's mark.
                    var network = LevelRails.of(helper.getLevel()).network();
                    BlockPos at = helper.absolutePos(NORTH);
                    var platform = Platform.of(network, LevelStations.of(helper.getLevel()).stations().get(at.getX(), at.getY(), at.getZ()))
                            .orElseThrow();
                    double off = platform.toLeadMark(network, train.placeOf(network, cargo.car()).turned(), Station.STRETCH);
                    // NaN, too, when the mark is out of its reach or it would arrive against the serving way.
                    if (!(Math.abs(off) <= 1e-6)) {
                        helper.fail("the Train settled " + off + " off the Station's mark", NORTH);
                    }
                })
                .thenSucceed();
    }

    // ---- setup ----

    /** {@link LocomotiveTests}' Span, and a survival player beside it holding four Stations. */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = LocomotiveTests.player(helper, -45.0F);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get(), 4));
        return player;
    }

    private static void refused(GameTestHelper helper, ServerPlayer player, BlockPos pos, RailworksRefusal expected) {
        PlacementPlan plan = Placements.planFor(player.getMainHandItem().getItem(), StationTests.context(helper, player, pos));
        if (plan == null || !plan.isRefused() || plan.refusal() != expected) {
            helper.fail("a Station at " + pos + " planned " + plan + ", not refused " + expected, pos);
            return;
        }
        int held = player.getMainHandItem().getCount();
        if (StationTests.place(helper, player, pos).consumesAction() || !helper.getBlockState(pos).isAir()
                || player.getMainHandItem().getCount() != held) {
            helper.fail("a refused Station was placed or spent", pos);
        }
        if (!StationItem.messageKey(expected).equals("message.railworks." + expected.name().toLowerCase(Locale.ROOT))) {
            helper.fail("a Station refused for " + expected + " says " + StationItem.messageKey(expected), pos);
        }
    }
}
