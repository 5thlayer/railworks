// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import io.github._5thlayer.railworks.rail.Span;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Burner Locomotive through the item, the entity and a real player: placing it hanging from a
 * Span the player looks along, the fuel slot, boarding and getting off, and driving. The drive's
 * numbers are {@code LocomotiveTest}'s; what only a server can show is that the keys, the fuel slot
 * and the Rail reach the model and that the entity ends up where the model says. All of these tests
 * share the {@code locomotive_} prefix.
 */
final class LocomotiveTests {

    /** Eight apart, four segments tall so the car clears the floor, with a Span strung between the tops. */
    private static final BlockPos A = new BlockPos(1, 1, 3);
    private static final BlockPos B = new BlockPos(9, 1, 3);
    private static final int HEIGHT = 4;
    /** Under the middle of the Span. */
    private static final BlockPos STAND = new BlockPos(5, 1, 3);

    private LocomotiveTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("locomotive_places_hanging_from_the_span_aimed_at", 20, LocomotiveTests::places);
        tests.test("locomotive_faces_the_way_the_player_looks_along_the_span", 20, LocomotiveTests::facesLookDirection);
        tests.test("locomotive_plan_matches_placement", 20, LocomotiveTests::planMatchesPlacement);
        tests.test("locomotive_plan_is_refused_off_a_span", 20, LocomotiveTests::refusedOffSpan);
        tests.test("locomotive_plan_is_refused_where_the_car_would_hang_in_a_block", 20, LocomotiveTests::refusedBlocked);
        tests.test("locomotive_plan_is_refused_on_a_span_another_holds", 20, LocomotiveTests::refusedTaken);
        tests.test("locomotive_fuel_goes_in_its_slot", 20, LocomotiveTests::fuelSlot);
        tests.test("locomotive_is_boarded_by_using_it_and_left_by_sneaking", 20, LocomotiveTests::boardsAndLeaves);
        tests.test("locomotive_is_driven_both_ways_by_forward_and_back", 20, LocomotiveTests::drivesBothWays);
        tests.test("locomotive_stops_at_a_lines_end", 20, LocomotiveTests::stopsAtLinesEnd);
        tests.test("locomotive_stops_when_its_fuel_runs_out", 20, LocomotiveTests::stopsWhenFuelRunsOut);
        tests.test("locomotive_with_no_fuel_does_not_move_under_a_driver", 20, LocomotiveTests::noFuelNoMovement);
        tests.test("locomotive_comes_off_the_rail_when_its_span_is_cut", 20, LocomotiveTests::derails);
        tests.test("locomotive_is_saved_with_its_place_speed_and_fuel", 20, LocomotiveTests::saves);
    }

    // ---- placing ----

    private static void places(GameTestHelper helper) {
        ServerPlayer player = player(helper, 90.0F);
        use(helper, player);
        LocomotiveEntity car = onlyCar(helper);
        if (car.locomotive() == null || !car.holds(span(helper))) {
            helper.fail("the placed car does not hang from the Span the player looked at", STAND);
        }
        // Hanging: its roof just under the Rail, so the car is below the Span and not on the floor.
        double railY = helper.absolutePos(A).getY() + HEIGHT - 1 + 0.8;
        if (Math.abs(car.getBoundingBox().maxY - (railY - LocomotiveEntity.ROOF_GAP)) > 1e-6) {
            helper.fail("the car's roof is at " + car.getBoundingBox().maxY + ", not just under the Rail at " + railY, STAND);
        }
        if (!player.getMainHandItem().isEmpty()) {
            helper.fail("placing the only Locomotive left one in hand", STAND);
        }
        helper.succeed();
    }

    private static void facesLookDirection(GameTestHelper helper) {
        ServerPlayer player = player(helper, -90.0F);
        use(helper, player);
        float east = onlyCar(helper).getYRot();
        onlyCar(helper).discard();
        ServerPlayer other = player(helper, 90.0F);
        use(helper, other);
        float west = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class, searchBox(helper)).stream()
                .filter(car -> !car.isRemoved()).findFirst().orElseThrow().getYRot();
        if (Math.abs(east - -90.0F) > 0.5F || Math.abs(west - 90.0F) > 0.5F) {
            helper.fail("a player looking east then west got cars facing " + east + " then " + west, STAND);
        }
        helper.succeed();
    }

    private static void planMatchesPlacement(GameTestHelper helper) {
        ServerPlayer player = player(helper, 90.0F);
        PlacementPlan plan = plan(helper, player);
        if (plan == null || plan.isRefused()) {
            helper.fail("a Locomotive aimed along a Span planned " + plan, STAND);
            return;
        }
        use(helper, player);
        BlockPos centre = BlockPos.containing(onlyCar(helper).getBoundingBox().getCenter());
        if (!plan.blocks().getFirst().pos().equals(centre)) {
            helper.fail("the plan drew the car at " + plan.blocks().getFirst().pos() + " but it hangs at " + centre, STAND);
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
        ServerPlayer player = player(helper, 90.0F);
        // Under the middle of the Span, where the car would hang.
        helper.setBlock(new BlockPos(5, 3, 3), Blocks.STONE);
        PlacementPlan plan = plan(helper, player);
        if (plan == null || plan.refusal() != RailworksRefusal.BLOCKED_CAR) {
            helper.fail("a car that would hang in stone planned " + plan, STAND);
        }
        helper.succeed();
    }

    private static void refusedTaken(GameTestHelper helper) {
        ServerPlayer first = player(helper, 90.0F);
        use(helper, first);
        ServerPlayer second = player(helper, 90.0F);
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
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 5));
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (car.fuelItem().getCount() != 5 || !player.getMainHandItem().isEmpty()) {
            helper.fail("coal in hand should fill the slot with 5, slot holds " + car.fuelItem(), STAND);
        }
        // Fuel the slot cannot take, another fuel or more than fits, boards the player instead.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 2));
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (car.fuelItem().getCount() != 5 || player.getMainHandItem().getCount() != 2 || player.getVehicle() != car) {
            helper.fail("a different fuel should stay in hand and board, with the slot still holding 5 coal", STAND);
        }
        helper.succeed();
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

    // ---- driving ----

    private static void drivesBothWays(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        ServerPlayer driver = board(helper, car);
        double start = car.getX();
        press(driver, true, false);
        run(car, 10);
        double afterForward = car.getX();
        if (car.locomotive().speed() <= 0.0 || afterForward <= start) {
            helper.fail("forward should run the car the way it faces, but it went " + (afterForward - start), STAND);
        }
        press(driver, false, true);
        run(car, 30);
        if (car.locomotive().speed() >= 0.0) {
            helper.fail("holding back should brake and then reverse, but the speed is " + car.locomotive().speed(), STAND);
        }
        double before = car.getX();
        run(car, 5);
        if (car.getX() >= before) {
            helper.fail("a reversing car did not move back", STAND);
        }
        helper.succeed();
    }

    private static void stopsAtLinesEnd(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        ServerPlayer driver = board(helper, car);
        press(driver, true, false);
        run(car, 120);
        double endX = helper.absolutePos(B).getX() + 0.5;
        if (Math.abs(car.getX() - endX) > 1e-6 || car.locomotive().speed() != 0.0) {
            helper.fail("the car should be stopped at the line's end x=" + endX + " but is at " + car.getX()
                    + " at speed " + car.locomotive().speed(), STAND);
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
        if (car.locomotive().speed() <= 0.0) {
            helper.fail("a fuelled car did not pick up speed", STAND);
        }
        run(car, 300);
        double endX = helper.absolutePos(B).getX() + 0.5;
        if (car.locomotive().speed() != 0.0 || car.getX() >= endX - 0.5 || car.locomotive().fuel().storedJoules() >= LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER) {
            helper.fail("the car should have run dry and stopped short of the end, speed "
                    + car.locomotive().speed() + " at " + car.getX() + ", fuel " + car.locomotive().fuel().storedJoules(), STAND);
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
        car.tick();
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

    private static void saves(GameTestHelper helper) {
        LocomotiveEntity car = fuelled(helper);
        ServerPlayer driver = board(helper, car);
        press(driver, true, false);
        run(car, 20);
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(
                net.minecraft.util.ProblemReporter.DISCARDING);
        car.saveWithoutId(output);
        var reloaded = RailworksRegistries.LOCOMOTIVE_ENTITY.get().create(helper.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.LOAD);
        reloaded.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        var was = car.locomotive();
        var now = reloaded.locomotive();
        if (now == null || !now.span().equals(was.span()) || now.speed() != was.speed()
                || now.toFront() != was.toFront() || now.fuel().storedJoules() != was.fuel().storedJoules()
                || reloaded.fuelItem().getCount() != car.fuelItem().getCount()) {
            helper.fail("a saved and reloaded car came back different: " + now, STAND);
        }
        helper.succeed();
    }

    // ---- setup ----

    /** Two columns, a Span between their tops, and a survival player under the middle holding a Locomotive. */
    private static ServerPlayer player(GameTestHelper helper, float yaw) {
        if (!helper.getBlockState(A).is(RailworksRegistries.PYLON.get())) {
            for (int i = 0; i < HEIGHT; i++) {
                helper.setBlock(A.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
                helper.setBlock(B.above(i), RailworksRegistries.PYLON.get().defaultBlockState());
            }
            LevelRails.of(helper.getLevel()).network().add(span(helper));
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(STAND);
        // Looking up and along the Span, so the line of sight stays in the Span's vertical plane.
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, yaw, -70.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.LOCOMOTIVE.get()));
        return player;
    }

    private static Span span(GameTestHelper helper) {
        return Span.of(PylonColumn.arm(helper.absolutePos(A.above(HEIGHT - 1))), PylonColumn.arm(helper.absolutePos(B.above(HEIGHT - 1))), 8);
    }

    private static AABB searchBox(GameTestHelper helper) {
        return new AABB(helper.absolutePos(new BlockPos(-2, 0, 0)).getCenter(), helper.absolutePos(new BlockPos(14, 8, 8)).getCenter());
    }

    private static void use(GameTestHelper helper, ServerPlayer player) {
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    }

    private static LocomotiveEntity onlyCar(GameTestHelper helper) {
        List<LocomotiveEntity> cars = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class, searchBox(helper));
        if (cars.size() != 1) {
            helper.fail("expected one Locomotive, found " + cars.size(), STAND);
        }
        return cars.getFirst();
    }

    /** A car hung mid-Span, facing the second Pylon. */
    private static LocomotiveEntity placed(GameTestHelper helper) {
        ServerPlayer player = player(helper, -90.0F);
        use(helper, player);
        return onlyCar(helper);
    }

    /** A placed car with a stack of coal in its slot. */
    private static LocomotiveEntity fuelled(GameTestHelper helper) {
        LocomotiveEntity car = placed(helper);
        fill(helper, car, new ItemStack(Items.COAL, 4));
        return car;
    }

    private static void fill(GameTestHelper helper, LocomotiveEntity car, ItemStack fuel) {
        ServerPlayer filler = helper.makeMockServerPlayerInLevel();
        filler.setGameMode(GameType.SURVIVAL);
        filler.setItemInHand(InteractionHand.MAIN_HAND, fuel);
        car.interact(filler, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    private static ServerPlayer board(GameTestHelper helper, LocomotiveEntity car) {
        ServerPlayer driver = helper.makeMockServerPlayerInLevel();
        driver.setGameMode(GameType.SURVIVAL);
        car.interact(driver, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (driver.getVehicle() != car) {
            helper.fail("the driver could not board", STAND);
        }
        return driver;
    }

    private static void press(ServerPlayer driver, boolean forward, boolean back) {
        driver.setLastClientInput(new Input(forward, back, false, false, false, false, false));
    }

    private static void run(LocomotiveEntity car, int ticks) {
        for (int i = 0; i < ticks; i++) {
            car.tick();
        }
    }

    /** The plan a Locomotive in hand gets for a click at the floor under the player, through Groundworks. */
    private static PlacementPlan plan(GameTestHelper helper, ServerPlayer player) {
        BlockPos floor = helper.absolutePos(STAND.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false);
        return Placements.planFor(helper.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
    }
}
