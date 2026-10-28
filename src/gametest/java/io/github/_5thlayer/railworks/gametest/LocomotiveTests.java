// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.railworks.LevelCarRecords;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.LocomotiveMenu;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailGeometry;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Burner Locomotive through the item, the entity and a real player: placing it on a
 * Span the player looks along, the fuel slots, boarding and getting off, and driving. The drive's
 * numbers are {@code TrainTest}'s; what only a server can show is that the keys, the fuel slots
 * and the Rail reach the model and that the entity ends up where the model says. These tests share
 * the {@code locomotive_} prefix, but for the four fuel slots' (#53), which share {@code refuel_}
 * with the refuelling tests, and for a rider logging out (#56), whose names share {@code logout_}
 * with {@code LogisticsTests}' Train on its job.
 */
final class LocomotiveTests {

    /**
     * Twelve apart, one segment tall, with a Span strung between the tops for the car to ride: room
     * for a Locomotive and a Cargo car coupled behind it, each five blocks long, from where
     * {@link #placed} sets the Locomotive.
     */
    static final BlockPos A = new BlockPos(-1, 1, 3);
    static final BlockPos B = new BlockPos(11, 1, 3);
    /** The Rail {@link #span} cost: its length. */
    private static final int COST = 12;
    static final int HEIGHT = 1;
    /** Beside the middle of the Span, looking across it at the web. */
    static final BlockPos STAND = new BlockPos(5, 1, 1);

    private LocomotiveTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("locomotive_places_on_the_span_aimed_at", 20, LocomotiveTests::places);
        tests.test("locomotive_faces_the_way_the_player_looks_along_the_span", 20, LocomotiveTests::facesLookDirection);
        tests.test("locomotive_plan_matches_placement", 20, LocomotiveTests::planMatchesPlacement);
        tests.test("locomotive_plan_is_refused_off_a_span", 20, LocomotiveTests::refusedOffSpan);
        tests.test("locomotive_plan_is_refused_where_the_car_would_sit_in_a_block", 20, LocomotiveTests::refusedBlocked);
        tests.test("locomotive_plan_is_refused_on_a_span_another_holds", 20, LocomotiveTests::refusedTaken);
        tests.test("refuel_fuel_in_hand_goes_in_the_four_slots_the_same_fuels_first", 20, LocomotiveTests::fuelSlot);
        tests.test("locomotive_is_boarded_by_using_it_and_left_by_sneaking", 20, LocomotiveTests::boardsAndLeaves);
        tests.test("locomotive_sneaking_at_the_car_opens_its_menu", 20, LocomotiveTests::sneakOpensMenu);
        tests.test("locomotive_the_inventory_key_opens_the_menu_while_riding", 20, LocomotiveTests::inventoryKeyOpensMenu);
        tests.test("locomotive_menu_slot_takes_fuel_and_gives_it_back", 20, LocomotiveTests::menuSlot);
        tests.test("locomotive_menu_shift_click_moves_fuel_between_slot_and_inventory", 20, LocomotiveTests::menuShiftClick);
        tests.test("locomotive_is_driven_both_ways_by_forward_and_back", 20, LocomotiveTests::drivesBothWays);
        tests.test("locomotive_carries_its_rider_with_it_and_is_driven_by_the_server", 20, LocomotiveTests::carriesRider);
        tests.test("locomotive_stops_at_a_lines_end", 20, LocomotiveTests::stopsAtLinesEnd);
        tests.test("locomotive_stops_when_its_fuel_runs_out", 20, LocomotiveTests::stopsWhenFuelRunsOut);
        tests.test("locomotive_with_no_fuel_does_not_move_under_a_driver", 20, LocomotiveTests::noFuelNoMovement);
        tests.test("locomotive_comes_off_the_rail_when_its_span_is_cut", 20, LocomotiveTests::derails);
        tests.test("locomotive_stays_on_its_span_when_a_segment_is_stacked_on_its_column", 20, LocomotiveTests::staysWhenStacked);
        tests.test("locomotive_is_saved_with_its_place_speed_and_fuel", 20, LocomotiveTests::saves);
        tests.test("refuel_the_fuel_slots_keep_their_order_across_a_save", 20, LocomotiveTests::savesSlotOrder);
        tests.test("refuel_a_cab_saved_with_one_fuel_slot_loads_into_the_first", 20, LocomotiveTests::loadsOneSlotSave);
        tests.test("refuel_the_menu_fills_any_fuel_slot_and_the_first_holding_fuel_burns_first", 20, LocomotiveTests::burnsInOrder);
        tests.test("locomotive_saved_before_trains_had_cars_loads_in_place", 20, LocomotiveTests::loadsLegacySave);
        tests.test("locomotive_cab_seats_its_rider_forward_facing_east", 20, helper -> seatsForward(helper, -45.0F, 1));
        tests.test("locomotive_cab_seats_its_rider_forward_facing_west", 20, helper -> seatsForward(helper, 45.0F, -1));
        tests.test("logout_leaves_a_train_at_rest_where_it_stands", 60, LocomotiveTests::riderLogsOutAtRest);
        tests.test("logout_takes_a_vehicle_that_is_no_car_out_with_its_rider", 20, LocomotiveTests::boaterLogsOut);
    }

    // ---- placing ----

    private static void places(GameTestHelper helper) {
        ServerPlayer player = player(helper, 45.0F);
        use(helper, player);
        LocomotiveEntity car = onlyCar(helper);
        if (car.locomotive() == null || !car.holds(span(helper))) {
            helper.fail("the placed car is not on the Span the player looked at", STAND);
        }
        // Riding: its floor on the flange's top, so the car is above the Span and not on the floor.
        double railTop = helper.absolutePos(A.above(HEIGHT - 1)).getY() + RailGeometry.RIDE_Y;
        if (Math.abs(car.getBoundingBox().minY - railTop) > 1e-6) {
            helper.fail("the car's floor is at " + car.getBoundingBox().minY + ", not on the flange at " + railTop, STAND);
        }
        if (!player.getMainHandItem().isEmpty()) {
            helper.fail("placing the only Locomotive left one in hand", STAND);
        }
        // Five blocks long, coupler to coupler, as every car is.
        if (car.car().length() != 5.0) {
            helper.fail("the placed Locomotive is " + car.car().length() + " blocks long, not five", STAND);
        }
        helper.succeed();
    }

    private static void facesLookDirection(GameTestHelper helper) {
        ServerPlayer player = player(helper, -45.0F);
        use(helper, player);
        float east = onlyCar(helper).getYRot();
        onlyCar(helper).discard();
        ServerPlayer other = player(helper, 45.0F);
        use(helper, other);
        float west = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class, searchBox(helper)).stream()
                .filter(car -> !car.isRemoved()).findFirst().orElseThrow().getYRot();
        if (Math.abs(east - -90.0F) > 0.5F || Math.abs(west - 90.0F) > 0.5F) {
            helper.fail("a player looking east then west got cars facing " + east + " then " + west, STAND);
        }
        helper.succeed();
    }

    private static void planMatchesPlacement(GameTestHelper helper) {
        ServerPlayer player = player(helper, 45.0F);
        PlacementPlan plan = plan(helper, player);
        if (plan == null || plan.isRefused()) {
            helper.fail("a Locomotive aimed along a Span planned " + plan, STAND);
            return;
        }
        use(helper, player);
        BlockPos centre = BlockPos.containing(onlyCar(helper).getBoundingBox().getCenter());
        if (!plan.blocks().getFirst().pos().equals(centre)) {
            helper.fail("the plan drew the car at " + plan.blocks().getFirst().pos() + " but it sits at " + centre, STAND);
        }
        helper.succeed();
    }

    private static void refusedOffSpan(GameTestHelper helper) {
        ServerPlayer player = player(helper, 0.0F);
        // Looking down at the floor, away from the Rail.
        player.setXRot(80.0F);
        PlacementPlan plan = plan(helper, player);
        if (plan == null || plan.refusal() != RailworksRefusal.OFF_SPAN) {
            helper.fail("a Locomotive aimed off any Span planned " + plan, STAND);
        }
        use(helper, player);
        if (!helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class, searchBox(helper)).isEmpty()) {
            helper.fail("a refused click placed a Locomotive", STAND);
        }
        if (player.getMainHandItem().getCount() != 1) {
            helper.fail("a refused click spent the Locomotive", STAND);
        }
        helper.succeed();
    }

    private static void refusedBlocked(GameTestHelper helper) {
        ServerPlayer player = player(helper, 45.0F);
        // Where the car would sit, above the middle of the Span.
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.STONE);
        PlacementPlan plan = plan(helper, player);
        if (plan == null || plan.refusal() != RailworksRefusal.BLOCKED_CAR) {
            helper.fail("a car that would sit in stone planned " + plan, STAND);
        }
        helper.succeed();
    }

    private static void refusedTaken(GameTestHelper helper) {
        ServerPlayer first = player(helper, 45.0F);
        use(helper, first);
        ServerPlayer second = player(helper, 45.0F);
        PlacementPlan plan = plan(helper, second);
        if (plan == null || plan.refusal() != RailworksRefusal.SPAN_TAKEN) {
            helper.fail("a second Locomotive on a held Span planned " + plan, STAND);
        }
        helper.succeed();
    }

    // ---- the fuel slot, boarding ----

    private static void fuelSlot(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        fillByHand(car, player, new ItemStack(Items.COAL, 5));
        fillByHand(car, player, new ItemStack(Items.OAK_PLANKS, 2));
        // Topping up the coal, then the rest into the first empty slot.
        fillByHand(car, player, new ItemStack(Items.COAL, 64));
        expectSlots(helper, car, List.of(new ItemStack(Items.COAL, 64), new ItemStack(Items.OAK_PLANKS, 2),
                new ItemStack(Items.COAL, 5), ItemStack.EMPTY));
        if (!player.getMainHandItem().isEmpty() || !car.fuelItem().is(Items.COAL)) {
            helper.fail("the coal and planks did not all go in, or the first slot's coal is not burnt next: hand "
                    + player.getMainHandItem() + ", next " + car.fuelItem(), STAND);
        }
        fillByHand(car, player, new ItemStack(Items.CHARCOAL, 64));
        // Fuel the slots cannot take, all four holding another, boards the player instead.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_ROD, 2));
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (car.fuelSlot(3).getCount() != 64 || player.getMainHandItem().getCount() != 2 || player.getVehicle() != car) {
            helper.fail("a fuel no slot takes should stay in hand and board, the last slot holding 64 charcoal: "
                    + car.fuelSlot(3), STAND);
        }
        helper.succeed();
    }

    private static void fillByHand(LocomotiveEntity car, ServerPlayer player, ItemStack fuel) {
        player.setItemInHand(InteractionHand.MAIN_HAND, fuel);
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    /** {@code car}'s fuel slots hold {@code expected}, slot by slot. */
    static void expectSlots(GameTestHelper helper, LocomotiveEntity car, List<ItemStack> expected) {
        for (int slot = 0; slot < expected.size(); slot++) {
            if (!ItemStack.matches(car.fuelSlot(slot), expected.get(slot))) {
                helper.fail("fuel slot " + slot + " holds " + car.fuelSlot(slot) + ", not " + expected.get(slot), STAND);
            }
        }
    }

    private static void boardsAndLeaves(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (player.getVehicle() != car) {
            helper.fail("using the empty-handed car did not board it", STAND);
        }
        player.setShiftKeyDown(true);
        player.rideTick();
        if (player.getVehicle() != null) {
            helper.fail("sneaking did not get the driver off", STAND);
        }
        if (player.getY() > helper.absolutePos(STAND).getY() + 1.5) {
            helper.fail("the driver was set down in the air at " + player.getY(), STAND);
        }
        helper.succeed();
    }

    private static void sneakOpensMenu(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        // Coal in hand would fill the slot; sneaking opens the menu all the same.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 2));
        player.setShiftKeyDown(true);
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (!(player.containerMenu instanceof LocomotiveMenu)) {
            helper.fail("sneaking at the car did not open its menu, the player has " + player.containerMenu, STAND);
        }
        if (player.getVehicle() != null || !car.fuelItem().isEmpty()) {
            helper.fail("sneaking at the car boarded it or filled its slot", STAND);
        }
        helper.succeed();
    }

    private static void inventoryKeyOpensMenu(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        ServerPlayer driver = board(helper, car);
        // The inventory key sends OPEN_INVENTORY, which the server answers for a vehicle like this.
        if (!(driver.getVehicle() instanceof HasCustomInventoryScreen vehicle)) {
            helper.fail("the car does not answer the inventory key", STAND);
            return;
        }
        vehicle.openCustomInventoryScreen(driver);
        if (!(driver.containerMenu instanceof LocomotiveMenu menu) || !menu.stillValid(driver)) {
            helper.fail("the inventory key did not open a menu the driver can use", STAND);
        }
        helper.succeed();
    }

    private static void menuSlot(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        LocomotiveMenu menu = (LocomotiveMenu) player.containerMenu;
        // Only furnace fuel goes in.
        menu.setCarried(new ItemStack(Items.APPLE, 3));
        menu.clicked(LocomotiveMenu.FUEL, 0, ContainerInput.PICKUP, player);
        if (!car.fuelItem().isEmpty() || menu.getCarried().getCount() != 3) {
            helper.fail("the fuel slot took an apple", STAND);
        }
        menu.setCarried(new ItemStack(Items.COAL, 3));
        menu.clicked(LocomotiveMenu.FUEL, 0, ContainerInput.PICKUP, player);
        if (car.fuelItem().getCount() != 3 || !car.fuelItem().is(Items.COAL) || !menu.getCarried().isEmpty()) {
            helper.fail("the fuel slot did not take the coal, it holds " + car.fuelItem(), STAND);
        }
        // And it comes back out.
        menu.clicked(LocomotiveMenu.FUEL, 0, ContainerInput.PICKUP, player);
        if (!car.fuelItem().isEmpty() || menu.getCarried().getCount() != 3) {
            helper.fail("the coal did not come back out of the slot", STAND);
        }
        helper.succeed();
    }

    private static void menuShiftClick(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.getInventory().setItem(9, new ItemStack(Items.COAL, 4));
        player.getInventory().setItem(10, new ItemStack(Items.APPLE, 2));
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        LocomotiveMenu menu = (LocomotiveMenu) player.containerMenu;
        int coal = slotOf(menu, player, 9);
        int apple = slotOf(menu, player, 10);
        menu.quickMoveStack(player, apple);
        if (!car.fuelItem().isEmpty() || menu.getSlot(apple).getItem().getCount() != 2) {
            helper.fail("shift-click moved an apple into the fuel slot", STAND);
        }
        menu.quickMoveStack(player, coal);
        if (car.fuelItem().getCount() != 4 || !menu.getSlot(coal).getItem().isEmpty()) {
            helper.fail("shift-click did not move the coal into the slot, it holds " + car.fuelItem(), STAND);
        }
        menu.quickMoveStack(player, LocomotiveMenu.FUEL);
        if (!car.fuelItem().isEmpty() || player.getInventory().countItem(Items.COAL) != 4) {
            helper.fail("shift-click did not move the coal back to the inventory", STAND);
        }
        helper.succeed();
    }

    /** The menu slot showing the player's inventory slot {@code inventorySlot}. */
    private static int slotOf(LocomotiveMenu menu, ServerPlayer player, int inventorySlot) {
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).container == player.getInventory() && menu.getSlot(i).getContainerSlot() == inventorySlot) {
                return i;
            }
        }
        throw new IllegalStateException("no menu slot for inventory slot " + inventorySlot);
    }

    // ---- driving ----

    private static void drivesBothWays(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        ServerPlayer driver = board(helper, car);
        double start = car.getX();
        press(driver, true, false);
        run(car, 10);
        double afterForward = car.getX();
        if (car.train().speed() <= 0.0 || afterForward <= start) {
            helper.fail("forward should run the car the way it faces, but it went " + (afterForward - start), STAND);
        }
        press(driver, false, true);
        run(car, 30);
        if (car.train().speed() >= 0.0) {
            helper.fail("holding back should brake and then reverse, but the speed is " + car.train().speed(), STAND);
        }
        double before = car.getX();
        run(car, 5);
        if (car.getX() >= before) {
            helper.fail("a reversing car did not move back", STAND);
        }
        helper.succeed();
    }

    private static void carriesRider(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        ServerPlayer driver = board(helper, car);
        // A controlling passenger would make the car client-authoritative: the client would then drop
        // the server's positions for it, and the rider would not move until they got off.
        if (car.getControllingPassenger() != null) {
            helper.fail("the car has a controlling passenger, so a client would not follow the server's positions", STAND);
        }
        press(driver, true, false);
        double start = car.getX();
        for (int tick = 0; tick < 25; tick++) {
            step(car);
            driver.rideTick();
            // Seated in the cab, forward of the car's middle; the car faces east.
            if (Math.abs(driver.getX() - (car.getX() + CarBody.CAB_SEAT.z())) > 0.01 || Math.abs(driver.getZ() - car.getZ()) > 0.01) {
                helper.fail("on tick " + tick + " the rider is at " + driver.getX() + ", " + driver.getZ()
                        + " but the car is at " + car.getX() + ", " + car.getZ(), STAND);
            }
        }
        if (car.getX() - start < 0.1) {
            helper.fail("the car did not move, so the rider's following proved nothing", STAND);
        }
        helper.succeed();
    }

    /**
     * A rider sits in the cab, {@link CarBody#CAB_SEAT}'s 1.8 forward of the car's middle the way it
     * faces, their eye at 2.22.
     */
    private static void seatsForward(GameTestHelper helper, float lookYaw, int east) {
        ServerPlayer placer = player(helper, lookYaw);
        use(helper, placer);
        LocomotiveEntity car = onlyCar(helper);
        ServerPlayer driver = board(helper, car);
        driver.rideTick();
        double x = car.getX() + CarBody.CAB_SEAT.z() * east;
        // A seated player's feet are 0.6 below the seat, which is 1.2 over the floor's underside.
        double y = car.getY() + CarBody.CAB_SEAT.y() - 0.6;
        if (Math.abs(driver.getX() - x) > 1e-3 || Math.abs(driver.getY() - y) > 1e-3
                || Math.abs(driver.getZ() - car.getZ()) > 1e-3) {
            helper.fail("the Driver sits at " + driver.position() + ", not in the cab at " + new Vec3(x, y, car.getZ()), STAND);
        }
        // Their eye, 1.62 over their feet, at the height CarBody's sight lines are drawn from.
        if (Math.abs(driver.getEyeY() - (car.getY() + 2.22)) > 1e-3) {
            helper.fail("the rider's eye is " + (driver.getEyeY() - car.getY()) + " over the car, not 2.22", STAND);
        }
        helper.succeed();
    }

    private static void stopsAtLinesEnd(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        ServerPlayer driver = board(helper, car);
        press(driver, true, false);
        run(car, 120);
        double endX = helper.absolutePos(B).getX() + 0.5;
        if (Math.abs(car.getX() - endX) > 1e-6 || car.train().speed() != 0.0) {
            helper.fail("the car should be stopped at the line's end x=" + endX + " but is at " + car.getX()
                    + " at speed " + car.train().speed(), STAND);
        }
        press(driver, false, true);
        run(car, 200);
        if (Math.abs(car.getX() - (helper.absolutePos(A).getX() + 0.5)) > 1e-6) {
            helper.fail("reversing should run to the other end, but the car is at " + car.getX(), STAND);
        }
        helper.succeed();
    }

    private static void stopsWhenFuelRunsOut(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        // A stick is worth 8 whole ticks of full power and a sliver: enough to move, nowhere near an end.
        fill(helper, car, new ItemStack(Items.STICK, 1));
        ServerPlayer driver = board(helper, car);
        press(driver, true, false);
        run(car, 5);
        if (car.train().speed() <= 0.0) {
            helper.fail("a fuelled car did not pick up speed", STAND);
        }
        run(car, 300);
        double endX = helper.absolutePos(B).getX() + 0.5;
        if (car.train().speed() != 0.0 || car.getX() >= endX - 0.5 || car.locomotive().fuel().storedJoules() >= LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER) {
            helper.fail("the car should have run dry and stopped short of the end, speed "
                    + car.train().speed() + " at " + car.getX() + ", fuel " + car.locomotive().fuel().storedJoules(), STAND);
        }
        helper.succeed();
    }

    private static void noFuelNoMovement(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        ServerPlayer driver = board(helper, car);
        double start = car.getX();
        press(driver, true, false);
        run(car, 40);
        if (car.getX() != start) {
            helper.fail("a car with no fuel moved", STAND);
        }
        helper.succeed();
    }

    private static void derails(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        var network = LevelRails.of(helper.getLevel()).network();
        network.remove(PylonColumn.arm(helper.absolutePos(A.above(HEIGHT - 1))), PylonColumn.arm(helper.absolutePos(B.above(HEIGHT - 1))));
        step(car);
        if (!car.isRemoved()) {
            helper.fail("a car whose Span was cut stayed in the world", STAND);
        }
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, searchBox(helper));
        boolean locomotive = drops.stream().anyMatch(drop -> drop.getItem().is(RailworksRegistries.LOCOMOTIVE));
        boolean coal = drops.stream().anyMatch(drop -> drop.getItem().is(Items.COAL));
        if (!locomotive || !coal) {
            helper.fail("a derailed car should drop itself and its fuel, dropped " + drops, STAND);
        }
        helper.succeed();
    }

    private static void staysWhenStacked(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        double distance = car.train().head().toFront() / car.train().head().span().length();
        ServerPlayer builder = helper.makeMockServerPlayerInLevel();
        builder.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(STAND);
        builder.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        builder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.PYLON_ITEM.get()));
        BlockPos base = helper.absolutePos(B);
        // The structure's edge is a barrier cage; clear it for the new segment.
        helper.setBlock(B.above(HEIGHT), Blocks.AIR);
        builder.gameMode.useItemOn(builder, helper.getLevel(), builder.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(base).relative(Direction.NORTH, 0.5), Direction.NORTH, base, false));
        if (!helper.getBlockState(B.above(HEIGHT)).is(RailworksRegistries.PYLON.get())) {
            helper.fail("the segment was not stacked on the column", B);
        }
        Span moved = Span.of(PylonColumn.arm(helper.absolutePos(A.above(HEIGHT - 1))),
                PylonColumn.arm(helper.absolutePos(B.above(HEIGHT))), COST);
        step(car);
        if (car.isRemoved() || !car.holds(moved)) {
            helper.fail("a car on a Span whose Arm moved should stay on it, removed=" + car.isRemoved(), STAND);
        }
        double now = car.train().head().toFront() / car.train().head().span().length();
        if (Math.abs(now - distance) > 1e-6) {
            helper.fail("the car moved along its Span from " + distance + " to " + now + " of the way", STAND);
        }
        helper.succeed();
    }

    private static void saves(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        ServerPlayer driver = board(helper, car);
        press(driver, true, false);
        run(car, 20);
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        car.saveWithoutId(output);
        var reloaded = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                output.buildResult()));
        var was = car.train();
        var now = reloaded.train();
        if (now == null || reloaded.locomotive() == null || !now.head().equals(was.head()) || now.speed() != was.speed()
                || reloaded.locomotive().fuel().storedJoules() != car.locomotive().fuel().storedJoules()
                || reloaded.fuelItem().getCount() != car.fuelItem().getCount()) {
            helper.fail("a saved and reloaded car came back different: " + now, STAND);
        }
        helper.succeed();
    }

    private static void savesSlotOrder(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        Opened opened = menu(helper, car);
        // The first slot left empty, and coal either side of the charcoal, as a menu can lay them.
        putInSlot(opened, 1, new ItemStack(Items.COAL, 10));
        putInSlot(opened, 2, new ItemStack(Items.CHARCOAL, 5));
        putInSlot(opened, 3, new ItemStack(Items.COAL, 20));
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        car.saveWithoutId(output);
        var reloaded = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                output.buildResult()));
        expectSlots(helper, reloaded, List.of(ItemStack.EMPTY, new ItemStack(Items.COAL, 10),
                new ItemStack(Items.CHARCOAL, 5), new ItemStack(Items.COAL, 20)));
        helper.succeed();
    }

    private static void loadsOneSlotSave(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        car.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        // The cab as a build with one fuel slot saved it: vanilla's item list, its stacks only.
        SimpleContainer oneSlot = new SimpleContainer(1);
        oneSlot.setItem(0, new ItemStack(Items.COAL, 7));
        var old = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        oneSlot.storeAsItemList(old.list("cab", ItemStack.OPTIONAL_CODEC));
        tag.put("cab", old.buildResult().get("cab"));
        // From a world saved before the level kept the car's record: it has none.
        LevelCarRecords.of(helper.getLevel()).remove(car.carId());
        var reloaded = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        expectSlots(helper, reloaded, List.of(new ItemStack(Items.COAL, 7), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        helper.succeed();
    }

    private static void burnsInOrder(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        Opened opened = menu(helper, car);
        // A stick is a sliver of a run; the coal after it is not touched until it is gone.
        putInSlot(opened, 2, new ItemStack(Items.STICK, 1));
        putInSlot(opened, 3, new ItemStack(Items.COAL, 3));
        ServerPlayer driver = board(helper, car);
        expectSlots(helper, car, List.of(ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.STICK, 1),
                new ItemStack(Items.COAL, 3)));
        press(driver, true, false);
        run(car, 3);
        expectSlots(helper, car, List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.COAL, 3)));
        helper.succeed();
    }

    /** A survival player's view of {@code car}'s menu, opened by sneaking at it. */
    private record Opened(ServerPlayer player, LocomotiveMenu menu) {
    }

    private static Opened menu(GameTestHelper helper, LocomotiveEntity car) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        player.setShiftKeyDown(false);
        return new Opened(player, (LocomotiveMenu) player.containerMenu);
    }

    /** Puts {@code stack} in fuel slot {@code slot} through the opened menu, as a click with it carried does. */
    private static void putInSlot(Opened opened, int slot, ItemStack stack) {
        opened.menu().setCarried(stack);
        opened.menu().clicked(LocomotiveMenu.FUEL + slot, 0, ContainerInput.PICKUP, opened.player());
    }

    private static void loadsLegacySave(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        ServerPlayer driver = board(helper, car);
        press(driver, true, false);
        run(car, 20);
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        car.saveWithoutId(output);
        // The entity as a world saved before Trains had cars holds it: the Locomotive was the Train,
        // saved with the entity and not with the level.
        var tag = output.buildResult();
        tag.remove("car_id");
        Place head = car.train().head();
        var span = new CompoundTag();
        span.put("a", arm(head.span().a()));
        span.put("b", arm(head.span().b()));
        span.putInt("cost", head.span().cost());
        var locomotive = new CompoundTag();
        locomotive.put("span", span);
        locomotive.put("front", arm(head.front()));
        locomotive.putDouble("to_front", head.toFront());
        locomotive.putDouble("speed", car.train().speed());
        locomotive.putLong("fuel", car.locomotive().fuel().storedJoules());
        locomotive.putLong("last_lit", car.locomotive().fuel().lastLitJoules());
        tag.put("locomotive", locomotive);
        // Its cab saved on it too, which its record takes in.
        var oldCab = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        var cabList = oldCab.list("cab", ItemStack.OPTIONAL_CODEC);
        cabList.add(ItemStack.EMPTY);
        cabList.add(new ItemStack(Items.CHARCOAL, 9));
        tag.put("cab", oldCab.buildResult().get("cab"));
        var reloaded = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        var now = reloaded.train();
        if (now == null || reloaded.locomotive() == null || now.cars().size() != 1
                || !now.head().equals(car.train().head()) || now.speed() != car.train().speed()
                || reloaded.locomotive().fuel().storedJoules() != car.locomotive().fuel().storedJoules()
                || !reloaded.position().equals(car.position()) || !reloaded.holds(span(helper))) {
            helper.fail("a Locomotive saved before Trains had cars came back different: " + now + " at " + reloaded.position(), STAND);
        }
        expectSlots(helper, reloaded, List.of(ItemStack.EMPTY, new ItemStack(Items.CHARCOAL, 9), ItemStack.EMPTY, ItemStack.EMPTY));
        helper.succeed();
    }

    /** An Arm as the old record wrote it: its three coordinates. */
    private static IntArrayTag arm(Arm arm) {
        return new IntArrayTag(new int[] {arm.x(), arm.y(), arm.z()});
    }

    // ---- logging out ----

    /** A rider logging out of a Train at rest, driven by hand, leaves it where it stands. */
    private static void riderLogsOutAtRest(GameTestHelper helper) {
        LocomotiveEntity locomotive = fuelled(helper);
        Train train = locomotive.train();
        var start = train.head();
        ServerPlayer rider = board(helper, locomotive);
        helper.getLevel().getServer().getPlayerList().remove(rider);
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    if (locomotive.isRemoved() || locomotive.isVehicle()) {
                        helper.fail("the rider took their car out of the world, or is still in it", STAND);
                    }
                    if (!train.head().equals(start) || train.speed() != 0.0) {
                        helper.fail("a Train at rest moved from " + start + " to " + train.head() + " as its rider logged out",
                                STAND);
                    }
                })
                .thenSucceed();
    }

    /** A vehicle that is no car goes with its one player as they log out, as vanilla has it. */
    private static void boaterLogsOut(GameTestHelper helper) {
        Boat boat = helper.spawn(EntityType.OAK_BOAT, STAND.above());
        ServerPlayer rider = helper.makeMockServerPlayerInLevel();
        if (!rider.startRiding(boat, true, false)) {
            helper.fail("the player could not board the boat", STAND);
            return;
        }
        helper.getLevel().getServer().getPlayerList().remove(rider);
        if (boat.getRemovalReason() != Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            helper.fail("the boat stayed behind its rider: " + boat.getRemovalReason(), STAND);
        }
        helper.succeed();
    }

    // ---- setup ----

    /** Two columns, a Span between their tops, and a survival player beside the middle holding a Locomotive. */
    static ServerPlayer player(GameTestHelper helper, float yaw) {
        if (!helper.getBlockState(A).is(RailworksRegistries.PYLON.get())) {
            for (int i = 0; i < HEIGHT; i++) {
                helper.setBlock(A.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
                helper.setBlock(B.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
            }
            // The structure's roof is a barrier layer; the car rides up into it, so clear it over the Span.
            for (int x = -2; x <= 12; x++) {
                for (int z = 2; z <= 4; z++) {
                    helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
                }
            }
            LevelRails.of(helper.getLevel()).network().add(span(helper));
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(STAND);
        // Looking across and along the Span, a little down at the web, from two blocks to its side.
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, yaw, 5.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.LOCOMOTIVE.get()));
        return player;
    }

    static Span span(GameTestHelper helper) {
        return Span.of(PylonColumn.arm(helper.absolutePos(A.above(HEIGHT - 1))), PylonColumn.arm(helper.absolutePos(B.above(HEIGHT - 1))), COST);
    }

    static AABB searchBox(GameTestHelper helper) {
        return new AABB(helper.absolutePos(new BlockPos(-2, 0, 0)).getCenter(), helper.absolutePos(new BlockPos(14, 8, 8)).getCenter());
    }

    static void use(GameTestHelper helper, ServerPlayer player) {
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    private static LocomotiveEntity onlyCar(GameTestHelper helper) {
        List<LocomotiveEntity> cars = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class, searchBox(helper));
        if (cars.size() != 1) {
            helper.fail("expected one Locomotive, found " + cars.size(), STAND);
        }
        return cars.getFirst();
    }

    /** A car set on the Span, facing the second Pylon. */
    static LocomotiveEntity placed(GameTestHelper helper) {
        ServerPlayer player = player(helper, -45.0F);
        use(helper, player);
        return onlyCar(helper);
    }

    /** A placed car with a stack of coal in its slot. */
    static LocomotiveEntity fuelled(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        fill(helper, car, new ItemStack(Items.COAL, 4));
        return car;
    }

    static void fill(GameTestHelper helper, LocomotiveEntity car, ItemStack fuel) {
        ServerPlayer filler = helper.makeMockServerPlayerInLevel();
        filler.setGameMode(GameType.SURVIVAL);
        filler.setItemInHand(InteractionHand.MAIN_HAND, fuel);
        car.interact(filler, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    static ServerPlayer board(GameTestHelper helper, LocomotiveEntity car) {
        ServerPlayer driver = helper.makeMockServerPlayerInLevel();
        driver.setGameMode(GameType.SURVIVAL);
        car.interact(driver, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (driver.getVehicle() != car) {
            helper.fail("the driver could not board", STAND);
        }
        return driver;
    }

    static void press(ServerPlayer driver, boolean forward, boolean back) {
        driver.setLastClientInput(new Input(forward, back, false, false, false, false, false));
    }

    static void run(LocomotiveEntity car, int ticks) {
        for (int i = 0; i < ticks; i++) {
            step(car);
        }
    }

    /** One tick of {@code car}'s Train, as the level ticks it, on top of the level's own. */
    static void step(CarEntity car) {
        ServerLevel level = (ServerLevel) car.level();
        Train train = car.train();
        if (train != null) {
            LevelRails.of(level).step(level, train);
        }
    }

    /** The plan a Locomotive in hand gets for a click at the floor under the player, through Groundworks. */
    static PlacementPlan plan(GameTestHelper helper, ServerPlayer player) {
        BlockPos floor = helper.absolutePos(STAND.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false);
        return Placements.planFor(helper.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
    }
}
