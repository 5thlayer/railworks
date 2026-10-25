// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Loading a Cargo car at a Station through NeoForge's item capability and vanilla hoppers: the
 * capability is there while the car is stopped at a Station and not while it moves or stands away
 * from one, and a hopper fills the car at one Station and another empties it at the next, the Train
 * driven between them by hand. Which Stations a car is stopped at is {@code StationsTest}'s. All of
 * these tests share the {@code cargo_loading_} prefix, and stand on {@link LocomotiveTests}' Span,
 * the Locomotive placed at x 7.5 and its Cargo car coupled behind it at x 5.7.
 */
final class CargoLoadingTests {

    /** Beside the Rail a block down, by the coupled Cargo car. */
    private static final BlockPos LOADING = new BlockPos(5, 1, 2);
    /** Beside the Rail a block down, by where the Cargo car stops at the line's end. */
    private static final BlockPos UNLOADING = new BlockPos(7, 1, 2);
    /** Beside the car at its floor, on the Loading Station, pointing into the car. */
    private static final BlockPos FILLING_HOPPER = new BlockPos(5, 2, 2);
    /** Under the Rail between the Pylons, pulling from the car above it. */
    private static final BlockPos EMPTYING_HOPPER = new BlockPos(7, 1, 3);
    /** Beside the Rail far from the Cargo car. */
    private static final BlockPos FAR = new BlockPos(2, 1, 2);

    private CargoLoadingTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("cargo_loading_a_car_stopped_at_a_station_has_the_item_capability", 20, CargoLoadingTests::presentAtStation);
        tests.test("cargo_loading_a_car_stopped_away_from_a_station_has_none", 20, CargoLoadingTests::absentAway);
        tests.test("cargo_loading_a_moving_car_at_a_station_has_none", 20, CargoLoadingTests::absentMoving);
        tests.test("cargo_loading_a_hopper_fills_a_car_at_one_station_and_another_empties_it_at_the_next", 400,
                CargoLoadingTests::run);
    }

    private static void presentAtStation(GameTestHelper helper) {
        CargoCarEntity cargo = train(helper);
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
        CargoCarEntity cargo = train(helper);
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
        CargoCarEntity cargo = train(helper);
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

    private static void run(GameTestHelper helper) {
        CargoCarEntity cargo = train(helper);
        LocomotiveEntity locomotive = locomotive(helper, cargo);
        station(helper, LOADING);
        station(helper, UNLOADING);
        helper.setBlock(FILLING_HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
        helper.setBlock(EMPTYING_HOPPER, Blocks.HOPPER.defaultBlockState());
        HopperBlockEntity filling = helper.getBlockEntity(FILLING_HOPPER, HopperBlockEntity.class);
        HopperBlockEntity emptying = helper.getBlockEntity(EMPTYING_HOPPER, HopperBlockEntity.class);
        filling.setItem(0, new ItemStack(Items.IRON_ORE, 4));
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, LOADING, UNLOADING))
                .thenWaitUntil(() -> {
                    if (count(cargo) != 4 || !filling.isEmpty()) {
                        helper.fail("the filling hopper has moved " + count(cargo) + " of 4 iron ore into the car", FILLING_HOPPER);
                    }
                })
                .thenExecute(() -> {
                    if (!emptying.isEmpty()) {
                        helper.fail("the emptying hopper took from the car at the other Station", EMPTYING_HOPPER);
                    }
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
                })
                .thenWaitUntil(() -> {
                    if (count(cargo) != 0 || emptying.getItem(0).getCount() != 4) {
                        helper.fail("the emptying hopper holds " + emptying.getItem(0) + " and the car " + count(cargo), EMPTYING_HOPPER);
                    }
                })
                .thenSucceed();
    }

    // ---- setup ----

    /** A Locomotive on the Span with a Cargo car coupled behind it; the Cargo car. */
    private static CargoCarEntity train(GameTestHelper helper) {
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
        int count = 0;
        for (int slot = 0; slot < cargo.cargo().getContainerSize(); slot++) {
            count += cargo.cargo().getItem(slot).getCount();
        }
        return count;
    }
}
