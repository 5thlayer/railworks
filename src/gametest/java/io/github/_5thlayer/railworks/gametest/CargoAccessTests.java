// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.RailworksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Who reaches a Cargo car's cargo: a player through its menu, and nothing else. It gives no item
 * capability and is no container, so hoppers beside it or under it move nothing, even with its Train
 * stopped at a Station, and sneak-using a hopper at it opens its cargo as using it with anything
 * else does (ADR 0008: a Station's hatches load a car). All of these tests share the
 * {@code logistics_cargo_} prefix, as the logistics work's (#26) game tests do, and stand on
 * {@link LocomotiveTests}' Span, the Locomotive placed at x 7.5 and a Cargo car coupled behind it at
 * x 2.5.
 */
final class CargoAccessTests {

    /**
     * In the row beside the Rail, two blocks off it at the car's crates' height, at the front end of
     * the coupled Cargo car, which leads a Train
     * heading west: it stands with its middle where a leading Locomotive's would, two blocks back
     * from the Station, its front at the Station block's front edge, in the block {@link #CAR}.
     */
    static final BlockPos BY_CARGO = new BlockPos(0, 3, 1);
    /** The block the coupled Cargo car's floor is in at its middle, and those beside it on the near side and under it. */
    private static final BlockPos CAR = new BlockPos(2, 2, 3);
    private static final BlockPos BESIDE = CAR.north();
    private static final BlockPos UNDER = CAR.below();
    /** The row beside the Span a player stands in, on the near side of the Rail. */
    private static final int SIDE_Z = 1;

    private CargoAccessTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("logistics_cargo_a_car_has_no_item_capability_at_a_station_or_away", 20, CargoAccessTests::noCapability);
        tests.test("logistics_cargo_hoppers_by_a_car_stopped_at_a_station_move_nothing", 60, CargoAccessTests::hoppersIdle);
        tests.test("logistics_cargo_sneak_using_a_hopper_at_a_car_opens_its_cargo", 20, CargoAccessTests::hopperOpensCargo);
        tests.test("logistics_cargo_using_a_car_opens_its_cargo_sneaking_or_not", 20, CargoAccessTests::useOpensCargo);
        tests.test("logistics_cargo_a_car_is_not_boarded_by_a_player_or_a_led_mob", 20, CargoAccessTests::notBoarded);
    }

    private static void noCapability(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        helper.startSequence()
                .thenExecute(() -> noCapability(helper, cargo, "away from every Station"))
                .thenExecute(() -> station(helper, BY_CARGO))
                .thenWaitUntil(() -> stoppedAtStation(helper, cargo))
                .thenExecute(() -> noCapability(helper, cargo, "stopped at a Station"))
                .thenSucceed();
    }

    private static void noCapability(GameTestHelper helper, CargoCarEntity cargo, String where) {
        if (cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, null) != null) {
            helper.fail("a Cargo car " + where + " has an item capability from no side in particular", BY_CARGO);
        }
        for (Direction side : Direction.values()) {
            if (cargo.getCapability(Capabilities.Item.ENTITY_AUTOMATION, side) != null) {
                helper.fail("a Cargo car " + where + " has an item capability on its " + side, BY_CARGO);
            }
        }
        if ((Object) cargo instanceof Container) {
            helper.fail("a Cargo car is a Container, which hoppers reach", BY_CARGO);
        }
    }

    /**
     * A hopper beside the car at its floor pointing in, where #25 set one, and one under it pulling,
     * with ore in the first and coal in the car: with the Train stopped at a Station, neither finds
     * the car, and after long enough for several moves nothing moved.
     */
    private static void hoppersIdle(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        station(helper, BY_CARGO);
        BlockPos beside = helper.absolutePos(BESIDE);
        BlockPos under = helper.absolutePos(UNDER);
        helper.startSequence()
                .thenWaitUntil(() -> stoppedAtStation(helper, cargo))
                .thenExecute(() -> {
                    helper.getLevel().setBlockAndUpdate(beside, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
                    helper.getLevel().setBlockAndUpdate(under, Blocks.HOPPER.defaultBlockState());
                    hopper(helper, beside).setItem(0, new ItemStack(Items.IRON_ORE, 4));
                    cargo.cargo().setItem(0, new ItemStack(Items.COAL, 4));
                    var into = HopperBlockEntity.getContainerOrHandlerAt(helper.getLevel(), beside.south(), Direction.NORTH);
                    var from = HopperBlockEntity.getContainerOrHandlerAt(helper.getLevel(), under.above(), Direction.DOWN);
                    if (into.container() != null || into.itemHandler() != null || from.container() != null || from.itemHandler() != null) {
                        helper.fail("a hopper by the Cargo car finds it: " + into + " beside, " + from + " under", BY_CARGO);
                    }
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    if (count(cargo.cargo()) != 4 || !cargo.cargo().getItem(0).is(Items.COAL)
                            || count(hopper(helper, beside)) != 4 || !hopper(helper, under).isEmpty()) {
                        helper.fail("hoppers moved items with the Cargo car: the car holds " + count(cargo.cargo())
                                + ", the hopper beside it " + count(hopper(helper, beside)) + ", the one under it "
                                + count(hopper(helper, under)), BY_CARGO);
                    }
                })
                .thenSucceed();
    }

    private static void hopperOpensCargo(GameTestHelper helper) {
        CargoCarEntity cargo = cargoBehindLocomotive(helper);
        station(helper, BY_CARGO);
        helper.startSequence()
                .thenWaitUntil(() -> stoppedAtStation(helper, cargo))
                .thenExecute(() -> {
                    ServerPlayer player = helper.makeMockServerPlayerInLevel();
                    player.setGameMode(GameType.SURVIVAL);
                    BlockPos stand = helper.absolutePos(new BlockPos(CAR.getX(), 1, SIDE_Z));
                    player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                    player.setShiftKeyDown(true);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HOPPER, 4));
                    cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
                    if (!(player.containerMenu instanceof ChestMenu menu) || menu.getContainer() != cargo.cargo()) {
                        helper.fail("sneak-using a hopper at the Cargo car did not open its cargo", BY_CARGO);
                    }
                    if (player.getMainHandItem().getCount() != 4 || !helper.getLevel().getBlockState(helper.absolutePos(BESIDE)).isAir()) {
                        helper.fail("sneak-using a hopper at the Cargo car set a hopper beside it", BY_CARGO);
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
                helper.fail("using the Cargo car " + (sneaking ? "sneaking" : "standing") + " did not open its cargo", BY_CARGO);
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
            helper.fail("a player boarded the Cargo car", BY_CARGO);
        }
        ServerPlayer leader = helper.makeMockServerPlayerInLevel();
        leader.setGameMode(GameType.SURVIVAL);
        Pig pig = helper.spawn(EntityType.PIG, LocomotiveTests.STAND.east(2));
        pig.setLeashedTo(leader, true);
        cargo.interact(leader, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (pig.getVehicle() != null || pig.getLeashHolder() != leader) {
            helper.fail("a led pig was pulled into the Cargo car", BY_CARGO);
        }
        helper.succeed();
    }

    // ---- setup ----

    /** The Cargo car of a Locomotive on the Span with it coupled behind. */
    static CargoCarEntity cargoBehindLocomotive(GameTestHelper helper) {
        return CargoCarTests.placeCargoBeside(helper, LocomotiveTests.placed(helper), -1);
    }

    /** A Station block, which puts itself in the level's Stations a tick after it is set. */
    static void station(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RailworksRegistries.STATION.get().defaultBlockState());
    }

    /** Fails until {@code cargo}'s Train is stopped at a Station, for a sequence to wait on. */
    private static void stoppedAtStation(GameTestHelper helper, CargoCarEntity cargo) {
        var network = LevelRails.of(helper.getLevel()).network();
        if (LevelStations.of(helper.getLevel()).stations().stoppedAt(network, cargo.train()).isEmpty()) {
            helper.fail("the Cargo car's Train is not stopped at a Station yet", BY_CARGO);
        }
    }

    private static HopperBlockEntity hopper(GameTestHelper helper, BlockPos absolute) {
        return (HopperBlockEntity) helper.getLevel().getBlockEntity(absolute);
    }

    private static int count(Container container) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            count += container.getItem(slot).getCount();
        }
        return count;
    }
}
