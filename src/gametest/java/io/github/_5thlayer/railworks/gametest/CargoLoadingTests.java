// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.HopperSpots;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.inventory.ChestMenu;
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
 * capability is there while the car's Train is stopped at a Station, whichever of its cars is by it,
 * and not while it moves or stands away from one; the car flags it for clients; and hoppers placed
 * as a survival player places them at the car's {@link HopperSpots} fill it at one Station and empty
 * it at the next, the Train driven between them by hand. Which Stations a Train is stopped at is
 * {@code StationsTest}'s. All of these tests share the {@code cargo_loading_} prefix, and stand on
 * {@link LocomotiveTests}' Span, the Locomotive placed at x 7.5 and Cargo cars coupled behind it at
 * x 5.7 and 3.9.
 */
final class CargoLoadingTests {

    /** Beside the Rail a block down, by the coupled Cargo car. */
    private static final BlockPos LOADING = new BlockPos(5, 1, 2);
    /**
     * Beside the second Pylon a block down, by where the Locomotive stops at the line's end and by no
     * car of the Train where it starts.
     */
    private static final BlockPos UNLOADING = new BlockPos(9, 1, 2);
    /** Beside the Rail a block down, by the Locomotive and not by the Cargo cars behind it. */
    private static final BlockPos BY_LOCOMOTIVE = new BlockPos(7, 1, 2);
    /** The row beside the Span a player stands in, on the near side and the far side of the Rail. */
    private static final int SIDE_Z = 1;
    private static final int FAR_SIDE_Z = 5;
    /** Beside the Rail far from the Cargo car. */
    private static final BlockPos FAR = new BlockPos(2, 1, 2);

    private CargoLoadingTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("cargo_loading_a_car_stopped_at_a_station_has_the_item_capability", 20, CargoLoadingTests::presentAtStation);
        tests.test("cargo_loading_a_car_stopped_away_from_a_station_has_none", 20, CargoLoadingTests::absentAway);
        tests.test("cargo_loading_a_moving_car_at_a_station_has_none", 20, CargoLoadingTests::absentMoving);
        tests.test("cargo_loading_a_car_flags_it_loads_at_a_station_until_it_leaves", 40, CargoLoadingTests::syncsAtStation);
        tests.test("cargo_loading_hoppers_at_the_spots_fill_a_car_at_one_station_and_empty_it_at_the_next", 400,
                CargoLoadingTests::fillsAndEmpties);
        tests.test("cargo_loading_sneak_using_a_hopper_sets_it_beside_the_car_pointing_in", 40, CargoLoadingTests::setsHopperEitherSide);
        tests.test("cargo_loading_a_hopper_is_refused_where_its_spot_is_taken", 40, CargoLoadingTests::hopperRefusedWhereTaken);
        tests.test("cargo_loading_a_hopper_is_refused_by_a_car_at_no_station", 20, CargoLoadingTests::hopperRefusedAwayFromStation);
        tests.test("cargo_loading_a_car_at_a_station_on_a_bend_neither_loads_nor_takes_a_hopper", 40, CargoLoadingTests::bendRefuses);
        tests.test("cargo_loading_using_a_car_opens_its_cargo_sneaking_or_not", 20, CargoLoadingTests::useOpensCargo);
        tests.test("cargo_loading_a_car_is_not_boarded_by_a_player_or_a_led_mob", 20, CargoLoadingTests::notBoarded);
        tests.test("cargo_loading_a_car_two_back_is_filled_when_the_locomotive_is_by_the_station", 200,
                CargoLoadingTests::fillsTwoBack);
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
        BlockPos[] filling = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, LOADING, UNLOADING))
                .thenWaitUntil(() -> synced(helper, cargo, true))
                .thenExecute(() -> {
                    // A hopper sneak-used at the car from the player's side, and ore dropped in it.
                    filling[0] = sneakHopper(helper, cargo, sneaker(helper, SIDE_Z), Direction.SOUTH);
                    hopper(helper, filling[0]).setItem(0, new ItemStack(Items.IRON_ORE, 4));
                })
                .thenWaitUntil(() -> {
                    if (count(cargo) != 4 || !hopper(helper, filling[0]).isEmpty()) {
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
                    var at = LevelStations.of(helper.getLevel()).stations().stoppedAt(network, cargo.train());
                    if (!at.equals(List.of(new Station(next.getX(), next.getY(), next.getZ(), Station.DEFAULT_NAME)))) {
                        helper.fail("at the line's end the Cargo car is stopped at " + at + ", not only the next Station", UNLOADING);
                    }
                    // A chest under the car's emptying spot, and a hopper placed on it as a player
                    // places one, clicking the chest's top.
                    BlockPos emptying = spot(cargo.hopperSpots().emptying());
                    helper.getLevel().setBlockAndUpdate(emptying.below(), Blocks.CHEST.defaultBlockState());
                    placeHopper(helper, emptying.below(), Direction.UP, emptying);
                })
                .thenWaitUntil(() -> {
                    BlockPos below = spot(cargo.hopperSpots().emptying()).below();
                    if (count(cargo) != 0 || count(chest(helper, below)) != 4) {
                        helper.fail("the chest under the emptying hopper holds " + count(chest(helper, below))
                                + " and the car " + count(cargo), UNLOADING);
                    }
                })
                .thenSucceed();
    }

    private static void fillsTwoBack(GameTestHelper helper) {
        CargoCarEntity first = cargoBehindLocomotive(helper);
        LocomotiveEntity locomotive = locomotive(helper, first);
        CargoCarEntity second = coupledBehind(helper, first);
        // By the Locomotive, two cars ahead of the second Cargo car and too far from it to reach it alone.
        station(helper, BY_LOCOMOTIVE);
        BlockPos[] filling = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, BY_LOCOMOTIVE))
                .thenExecute(() -> {
                    var network = LevelRails.of(helper.getLevel()).network();
                    var train = second.train();
                    var stations = LevelStations.of(helper.getLevel()).stations();
                    if (train != locomotive.train() || train.cars().size() != 3 || train.cars().getLast() != second.car()
                            || stations.at(network, train.placeOf(network, locomotive.car())).isEmpty()
                            || !stations.at(network, train.placeOf(network, first.car())).isEmpty()
                            || !stations.at(network, train.placeOf(network, second.car())).isEmpty()) {
                        helper.fail("expected a Locomotive by the Station and two Cargo cars behind it, neither by it: " + train,
                                BY_LOCOMOTIVE);
                    }
                })
                .thenWaitUntil(() -> {
                    if (!first.loading() || !second.loading()) {
                        helper.fail("the Cargo cars flag " + first.loading() + " and " + second.loading()
                                + " for loading by the Station their Locomotive is by", BY_LOCOMOTIVE);
                    }
                })
                .thenExecute(() -> {
                    filling[0] = sneakHopper(helper, second, sneaker(helper, SIDE_Z), Direction.SOUTH);
                    hopper(helper, filling[0]).setItem(0, new ItemStack(Items.IRON_ORE, 4));
                })
                .thenWaitUntil(() -> {
                    if (count(second) != 4 || count(first) != 0) {
                        helper.fail("the hopper beside the second Cargo car moved " + count(second) + " of 4 iron ore into it, and "
                                + count(first) + " into the first", BY_LOCOMOTIVE);
                    }
                })
                .thenSucceed();
    }

    /** A Cargo car placed and coupled behind {@code car}, which is the last of its Train. */
    private static CargoCarEntity coupledBehind(GameTestHelper helper, CargoCarEntity car) {
        LocomotiveTests.use(helper, CargoCarTests.cargoPlayer(helper, car, -1));
        var train = car.train();
        if (train == null || train.cars().getLast() == car.car()) {
            throw helper.assertionException(BY_LOCOMOTIVE, Component.literal(
                    "a second Cargo car did not couple behind the first: " + train));
        }
        if (!(LevelRails.of(helper.getLevel()).view(train.cars().getLast()) instanceof CargoCarEntity coupled)) {
            throw helper.assertionException(BY_LOCOMOTIVE, Component.literal(
                    "the car coupled behind is not a Cargo car"));
        }
        return coupled;
    }

    // ---- setting a hopper, using the car ----

    private static void setsHopperEitherSide(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        station(helper, LOADING);
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, LOADING))
                .thenWaitUntil(() -> synced(helper, cargo, true))
                .thenExecute(() -> {
                    ServerPlayer near = sneaker(helper, SIDE_Z);
                    BlockPos south = sneakHopper(helper, cargo, near, Direction.SOUTH);
                    if (near.getMainHandItem().getCount() != 3) {
                        helper.fail("setting a hopper left " + near.getMainHandItem().getCount() + " of 4 in hand", LOADING);
                    }
                    ServerPlayer far = sneaker(helper, FAR_SIDE_Z);
                    BlockPos north = sneakHopper(helper, cargo, far, Direction.NORTH);
                    BlockPos car = helper.absolutePos(new BlockPos(5, 2, 3));
                    if (!south.equals(car.north()) || !north.equals(car.south())) {
                        helper.fail("the hoppers stand at " + south + " and " + north + ", not either side of the car's block " + car,
                                LOADING);
                    }
                    ServerPlayer creative = sneaker(helper, SIDE_Z);
                    creative.setGameMode(GameType.CREATIVE);
                    helper.getLevel().removeBlock(south, false);
                    sneakHopper(helper, cargo, creative, Direction.SOUTH);
                    if (creative.getMainHandItem().getCount() != 4) {
                        helper.fail("a creative player's hopper was used up", LOADING);
                    }
                })
                .thenSucceed();
    }

    private static void hopperRefusedWhereTaken(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        station(helper, LOADING);
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, LOADING))
                .thenWaitUntil(() -> synced(helper, cargo, true))
                .thenExecute(() -> {
                    ServerPlayer player = sneaker(helper, SIDE_Z);
                    BlockPos taken = spot(cargo.hopperSpots().fillingNearest(player.getX(), player.getZ()).spot());
                    helper.getLevel().setBlockAndUpdate(taken, Blocks.STONE.defaultBlockState());
                    cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    if (!helper.getLevel().getBlockState(taken).is(Blocks.STONE) || player.getMainHandItem().getCount() != 4) {
                        helper.fail("a hopper was set where stone stood, or used up", LOADING);
                    }
                    if (player.containerMenu instanceof ChestMenu) {
                        helper.fail("a refused hopper opened the cargo instead", LOADING);
                    }
                    // Nor where someone stands, on the far side.
                    ServerPlayer far = sneaker(helper, FAR_SIDE_Z);
                    BlockPos stood = spot(cargo.hopperSpots().fillingNearest(far.getX(), far.getZ()).spot());
                    ServerPlayer standing = helper.makeMockServerPlayerInLevel();
                    standing.snapTo(stood.getX() + 0.5, stood.getY(), stood.getZ() + 0.5);
                    cargo.interact(far, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    if (!helper.getLevel().getBlockState(stood).isAir() || far.getMainHandItem().getCount() != 4) {
                        helper.fail("a hopper was set where a player stood", LOADING);
                    }
                })
                .thenSucceed();
    }

    private static void hopperRefusedAwayFromStation(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        ServerPlayer player = sneaker(helper, SIDE_Z);
        BlockPos spot = spot(cargo.hopperSpots().fillingNearest(player.getX(), player.getZ()).spot());
        cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (!helper.getLevel().getBlockState(spot).isAir() || player.getMainHandItem().getCount() != 4) {
            helper.fail("a hopper was set by a car stopped at no Station", LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    private static void bendRefuses(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        station(helper, LOADING);
        // A Span on from the second Pylon, off line: the Rail now bends through it, all along the Span the car is on.
        var network = LevelRails.of(helper.getLevel()).network();
        network.add(Span.of(PylonColumn.arm(helper.absolutePos(LocomotiveTests.B)),
                PylonColumn.arm(helper.absolutePos(new BlockPos(13, 1, 7))), 6));
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, LOADING))
                .thenExecute(() -> {
                    var stations = LevelStations.of(helper.getLevel()).stations();
                    if (stations.stoppedAt(network, cargo.train()).isEmpty()) {
                        helper.fail("the Train is not stopped at the Station, so the bend proves nothing", LOADING);
                    }
                })
                // A tick for the car to sync what it would flag.
                .thenIdle(2)
                .thenExecute(() -> {
                    if (cargo.loading() || cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, null) != null) {
                        helper.fail("a Cargo car at a Station on a bend loads", LOADING);
                    }
                    ServerPlayer player = sneaker(helper, SIDE_Z);
                    BlockPos spot = spot(cargo.hopperSpots().fillingNearest(player.getX(), player.getZ()).spot());
                    cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    if (!helper.getLevel().getBlockState(spot).isAir() || player.getMainHandItem().getCount() != 4) {
                        helper.fail("a hopper was set by a Cargo car on a bend", LOADING);
                    }
                })
                .thenSucceed();
    }

    private static void useOpensCargo(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        for (boolean sneaking : new boolean[] {false, true}) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.setShiftKeyDown(sneaking);
            cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (!(player.containerMenu instanceof ChestMenu menu) || menu.getContainer() != cargo.cargo()) {
                helper.fail("using the Cargo car " + (sneaking ? "sneaking" : "standing") + " did not open its cargo", LOADING);
            }
        }
        helper.succeed();
    }

    private static void notBoarded(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (player.getVehicle() != null || player.startRiding(cargo)) {
            helper.fail("a player boarded the Cargo car", LOADING);
        }
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.setGameMode(GameType.SURVIVAL);
        Pig pig = helper.spawn(EntityType.PIG, LocomotiveTests.STAND.east(2));
        pig.setLeashedTo(leader, true);
        cargo.interact(leader, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (pig.getVehicle() != null || pig.getLeashHolder() != leader) {
            helper.fail("a led pig was pulled into the Cargo car", LOADING);
        }
        helper.succeed();
    }

    /** A survival player sneaking with four hoppers in hand, beside the Span on the side at {@code z}. */
    private static ServerPlayer sneaker(GameTestHelper helper, int z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(new BlockPos(5, 1, z));
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HOPPER, 4));
        return player;
    }

    /**
     * {@code player} sneak-uses their hopper at {@code cargo}: it must stand in the car's filling spot
     * on their side, pointing {@code facing}, at the car. Its position.
     */
    private static BlockPos sneakHopper(GameTestHelper helper, CargoCarEntity cargo, ServerPlayer player, Direction facing) {
        BlockPos expected = spot(cargo.hopperSpots().fillingNearest(player.getX(), player.getZ()).spot());
        cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        BlockState placed = helper.getLevel().getBlockState(expected);
        if (!placed.is(Blocks.HOPPER) || placed.getValue(HopperBlock.FACING) != facing) {
            helper.fail("a hopper sneak-used at the car left " + placed + " at " + expected + ", not one facing " + facing, LOADING);
        }
        return expected;
    }

    private static void syncsAtStation(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        LocomotiveEntity locomotive = locomotive(helper, cargo);
        station(helper, LOADING);
        helper.startSequence()
                .thenExecute(() -> {
                    if (cargo.loading()) {
                        helper.fail("a Cargo car flags it loads before there is a Station", LOADING);
                    }
                })
                .thenWaitUntil(() -> registered(helper, LOADING))
                .thenWaitUntil(() -> synced(helper, cargo, true))
                .thenExecute(() -> {
                    LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 4));
                    ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
                    LocomotiveTests.press(driver, true, false);
                    LocomotiveTests.run(locomotive, 2);
                })
                .thenWaitUntil(() -> synced(helper, cargo, false))
                .thenSucceed();
    }

    /**
     * Fails unless the car's synced flag says {@code expected} for loading where it stands, for a
     * sequence to wait on. It reads the server's entity data, which vanilla's entity tracking sends on; no
     * client runs here to receive it.
     */
    private static void synced(GameTestHelper helper, CargoCarEntity cargo, boolean expected) {
        if (cargo.loading() != expected) {
            helper.fail("the Cargo car flags " + cargo.loading() + " for loading, with its capability "
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
