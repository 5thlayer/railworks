// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.HopperSpots;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Loading a Cargo car at a Station through NeoForge's item capability and vanilla hoppers: the
 * capability is there while the car is stopped at a Station and not while it moves or stands away
 * from one, a client is told so, and hoppers placed as a survival player places them at the car's
 * {@link HopperSpots} fill it at one Station and empty it at the next, the Train driven between them
 * by hand. Which Stations a car is stopped at is {@code StationsTest}'s. All of
 * these tests share the {@code cargo_loading_} prefix, and stand on {@link LocomotiveTests}' Span,
 * the Locomotive placed at x 7.5 and its Cargo car coupled behind it at x 5.7.
 */
final class CargoLoadingTests {

    /** Beside the Rail a block down, by the coupled Cargo car. */
    private static final BlockPos LOADING = new BlockPos(5, 1, 2);
    /** Beside the Rail a block down, by where the Cargo car stops at the line's end. */
    private static final BlockPos UNLOADING = new BlockPos(7, 1, 2);
    /** Beside the Rail far from the Cargo car. */
    private static final BlockPos FAR = new BlockPos(2, 1, 2);

    private CargoLoadingTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("cargo_loading_a_car_stopped_at_a_station_has_the_item_capability", 20, CargoLoadingTests::presentAtStation);
        tests.test("cargo_loading_a_car_stopped_away_from_a_station_has_none", 20, CargoLoadingTests::absentAway);
        tests.test("cargo_loading_a_moving_car_at_a_station_has_none", 20, CargoLoadingTests::absentMoving);
        tests.test("cargo_loading_a_car_shows_clients_it_is_at_a_station_until_it_leaves", 40, CargoLoadingTests::syncsAtStation);
        tests.test("cargo_loading_hoppers_at_the_spots_fill_a_car_at_one_station_and_empty_it_at_the_next", 400,
                CargoLoadingTests::fillsAndEmpties);
    }

    private static void presentAtStation(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        station(helper, LOADING);
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, LOADING))
                .thenExecute(() -> {
                    var handler = cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, Direction.NORTH);
                    if (handler == null || handler.size() != CargoCarEntity.SLOTS) {
                        helper.fail("a Cargo car stopped at a Station has no item capability of 27 slots: " + handler, LOADING);
                    }
                    if (cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, null) == null) {
                        helper.fail("the capability is missing from no side in particular", LOADING);
                    }
                })
                .thenSucceed();
    }

    private static void absentAway(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        station(helper, FAR);
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, FAR))
                .thenExecute(() -> {
                    if (cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, Direction.NORTH) != null) {
                        helper.fail("a Cargo car stopped away from every Station has an item capability", FAR);
                    }
                })
                .thenSucceed();
    }

    private static void absentMoving(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        LocomotiveEntity locomotive = locomotive(helper, cargo);
        station(helper, LOADING);
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, LOADING))
                .thenExecute(() -> {
                    LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 4));
                    ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
                    LocomotiveTests.press(driver, true, false);
                    LocomotiveTests.run(locomotive, 2);
                    var network = LevelRails.of(helper.getLevel()).network();
                    var at = LevelStations.of(helper.getLevel()).stations()
                            .at(network, cargo.train().placeOf(network, cargo.car()));
                    if (cargo.train().speed() <= 0.0 || at.isEmpty()) {
                        helper.fail("the Train should be moving with its Cargo car still beside the Station, at speed "
                                + cargo.train().speed() + " by " + at, LOADING);
                    }
                    if (cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, Direction.NORTH) != null) {
                        helper.fail("a moving Cargo car has an item capability", LOADING);
                    }
                })
                .thenSucceed();
    }

    private static void fillsAndEmpties(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        LocomotiveEntity locomotive = locomotive(helper, cargo);
        station(helper, LOADING);
        station(helper, UNLOADING);
        // Where the filling hopper goes, and the chest over it that feeds it.
        BlockPos[] filling = new BlockPos[2];
        BlockState[] was = new BlockState[2];
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, LOADING, UNLOADING))
                .thenWaitUntil(() -> synced(helper, cargo, true))
                .thenExecute(() -> {
                    // A chest of ore over the car's filling spot, and a hopper placed under it as a player
                    // places one, clicking the chest's underside.
                    filling[0] = spot(cargo.hopperSpots().fill());
                    filling[1] = filling[0].above();
                    // Over the structure, where the test's barrier walls it in.
                    was[0] = helper.getLevel().getBlockState(filling[0]);
                    was[1] = helper.getLevel().getBlockState(filling[1]);
                    helper.getLevel().setBlockAndUpdate(filling[0], Blocks.AIR.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(filling[1], Blocks.CHEST.defaultBlockState());
                    chest(helper, filling[1]).setItem(0, new ItemStack(Items.IRON_ORE, 4));
                    placeHopper(helper, filling[1], Direction.DOWN, filling[0]);
                })
                .thenWaitUntil(() -> {
                    if (count(cargo) != 4 || !chest(helper, filling[1]).isEmpty() || !hopper(helper, filling[0]).isEmpty()) {
                        helper.fail("the filling hopper has moved " + count(cargo) + " of 4 iron ore into the car", LOADING);
                    }
                })
                .thenExecute(() -> {
                    // Driven by hand to the line's end, where the Cargo car stops by the next Station.
                    LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 4));
                    ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
                    LocomotiveTests.press(driver, true, false);
                    LocomotiveTests.run(locomotive, 120);
                    LocomotiveTests.press(driver, false, false);
                    if (cargo.train().speed() != 0.0 || count(cargo) != 4) {
                        helper.fail("the Train should be stopped at the line's end with 4 iron ore aboard, at speed "
                                + cargo.train().speed() + " with " + count(cargo), UNLOADING);
                    }
                    var network = LevelRails.of(helper.getLevel()).network();
                    BlockPos next = helper.absolutePos(UNLOADING);
                    var at = LevelStations.of(helper.getLevel()).stations().stoppedAt(network, cargo.train(), cargo.car());
                    if (!at.equals(List.of(new Station(next.getX(), next.getY(), next.getZ(), Station.DEFAULT_NAME)))) {
                        helper.fail("at the line's end the Cargo car is stopped at " + at + ", not only the next Station", UNLOADING);
                    }
                    // A chest under the car's emptying spot, and a hopper placed on it as a player
                    // places one, clicking the chest's top.
                    BlockPos emptying = spot(cargo.hopperSpots().empty());
                    helper.getLevel().setBlockAndUpdate(emptying.below(), Blocks.CHEST.defaultBlockState());
                    placeHopper(helper, emptying.below(), Direction.UP, emptying);
                })
                .thenWaitUntil(() -> {
                    BlockPos below = spot(cargo.hopperSpots().empty()).below();
                    if (count(cargo) != 0 || count(chest(helper, below)) != 4) {
                        helper.fail("the chest under the emptying hopper holds " + count(chest(helper, below))
                                + " and the car " + count(cargo), UNLOADING);
                    }
                })
                .thenExecute(() -> {
                    // The chest and hopper over the car stand above the structure, which its clean-up misses.
                    helper.getLevel().setBlockAndUpdate(filling[1], was[1]);
                    helper.getLevel().setBlockAndUpdate(filling[0], was[0]);
                })
                .thenSucceed();
    }

    private static void syncsAtStation(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        LocomotiveEntity locomotive = locomotive(helper, cargo);
        station(helper, LOADING);
        helper.startSequence()
                .thenExecute(() -> {
                    if (cargo.atStation()) {
                        helper.fail("a Cargo car shows it is at a Station before there is one", LOADING);
                    }
                })
                .thenWaitUntil(() -> registered(helper, LOADING))
                .thenWaitUntil(() -> synced(helper, cargo, true))
                .thenExecute(() -> {
                    LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 4));
                    ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
                    LocomotiveTests.press(driver, true, false);
                    LocomotiveTests.run(locomotive, 2);
                    synced(helper, cargo, false);
                })
                .thenSucceed();
    }

    /** Fails unless the car shows {@code expected} for being at a Station, for a sequence to wait on. */
    private static void synced(GameTestHelper helper, CargoCarEntity cargo, boolean expected) {
        if (cargo.atStation() != expected) {
            helper.fail("the Cargo car shows " + cargo.atStation() + " for being at a Station, with its capability "
                    + cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, null), LOADING);
        }
    }

    /**
     * A survival player's hopper, placed by clicking {@code face} of the block at {@code clicked}: it
     * must land at {@code expected}, pointing down, as vanilla points one placed from a top or underside.
     */
    private static void placeHopper(GameTestHelper helper, BlockPos clicked, Direction face, BlockPos expected) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HOPPER));
        Vec3 hit = Vec3.atCenterOf(clicked).relative(face, 0.5);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
        BlockState placed = helper.getLevel().getBlockState(expected);
        if (!placed.is(Blocks.HOPPER) || placed.getValue(HopperBlock.FACING) != Direction.DOWN) {
            helper.fail("a hopper placed on the " + face + " of " + clicked + " left " + placed + " at " + expected, LOADING);
        }
    }

    private static BlockPos spot(HopperSpots.Spot spot) {
        return new BlockPos(spot.x(), spot.y(), spot.z());
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos absolute) {
        return (ChestBlockEntity) helper.getLevel().getBlockEntity(absolute);
    }

    private static HopperBlockEntity hopper(GameTestHelper helper, BlockPos absolute) {
        return (HopperBlockEntity) helper.getLevel().getBlockEntity(absolute);
    }

    // ---- setup ----

    /** The Cargo car of a Locomotive on the Span with it coupled behind. */
    private static CargoCarEntity cargoBehindLocomotive(GameTestHelper helper) {
        return CargoCarTests.placeCargoBeside(helper, LocomotiveTests.placed(helper), -1);
    }

    private static LocomotiveEntity locomotive(GameTestHelper helper, CargoCarEntity cargo) {
        var view = LevelRails.of(helper.getLevel()).view(cargo.train().cars().getFirst());
        if (!(view instanceof LocomotiveEntity locomotive)) {
            throw new IllegalStateException("the Cargo car's Train is led by " + view);
        }
        return locomotive;
    }

    /** A Station block, which puts itself in the level's Stations a tick after it is set. */
    private static void station(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RailworksRegistries.STATION.get().defaultBlockState());
    }

    /** Fails until each Station is in the level's Stations, for a sequence to wait on. */
    private static void registered(GameTestHelper helper, BlockPos... stations) {
        for (BlockPos pos : stations) {
            BlockPos at = helper.absolutePos(pos);
            if (LevelStations.of(helper.getLevel()).stations().get(at.getX(), at.getY(), at.getZ()) == null) {
                helper.fail("the Station is not in the level's Stations yet", pos);
            }
        }
    }

    private static int count(CargoCarEntity cargo) {
        return count(cargo.cargo());
    }

    private static int count(Container container) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            count += container.getItem(slot).getCount();
        }
        return count;
    }
}
