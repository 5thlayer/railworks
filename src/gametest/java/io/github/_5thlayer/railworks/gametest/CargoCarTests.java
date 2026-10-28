// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import static io.github._5thlayer.railworks.gametest.LocomotiveTests.A;
import static io.github._5thlayer.railworks.gametest.LocomotiveTests.B;
import static io.github._5thlayer.railworks.gametest.LocomotiveTests.HEIGHT;
import static io.github._5thlayer.railworks.gametest.LocomotiveTests.STAND;

import java.util.List;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The Cargo car, and Trains of several cars, through the items, the entities and a real player:
 * the recipe, coupling a car by placing it beside an end of a Train, uncoupling it with shears,
 * the menu, leading a mob in and out of a Locomotive, and a Train of cars moving, saving and breaking
 * as one. Weight's numbers are {@code TrainTest}'s. All of these tests share the {@code cargo_}
 * prefix, and stand on {@link LocomotiveTests}' Span.
 */
final class CargoCarTests {

    private CargoCarTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("cargo_car_crafts_from_iron_and_a_chest", 20, CargoCarTests::crafts);
        tests.test("cargo_car_placed_beside_a_locomotive_couples_into_its_train", 20, CargoCarTests::couplesBehind);
        tests.test("cargo_car_placed_ahead_of_a_train_couples_and_leads_it", 20, CargoCarTests::couplesAhead);
        tests.test("cargo_car_plan_matches_the_coupling", 20, CargoCarTests::planMatchesCoupling);
        tests.test("cargo_car_placed_alone_starts_a_train_of_its_own", 20, CargoCarTests::placesAlone);
        tests.test("cargo_car_plan_is_refused_on_a_span_a_train_holds_away_from_its_ends", 20, CargoCarTests::refusedMidTrain);
        tests.test("cargo_locomotive_placed_facing_away_couples_reversed", 20, CargoCarTests::locomotiveFacingAway);
        tests.test("cargo_shears_uncouple_a_train_in_two", 20, CargoCarTests::shearsUncouple);
        tests.test("cargo_a_moving_train_does_not_uncouple", 20, CargoCarTests::movingDoesNotUncouple);
        tests.test("cargo_car_sneaking_at_it_opens_its_menu", 20, CargoCarTests::sneakOpensMenu);
        tests.test("cargo_car_holds_items_put_in_through_its_menu", 20, CargoCarTests::holdsItems);
        tests.test("cargo_a_led_mob_is_pulled_into_a_locomotive", 20, CargoCarTests::pullsMobIn);
        tests.test("cargo_a_lead_leads_a_mob_out_of_a_locomotive", 20, CargoCarTests::leadsMobOut);
        tests.test("cargo_a_train_of_cars_moves_as_one", 20, CargoCarTests::movesAsOne);
        tests.test("cargo_a_train_of_cars_is_saved_with_the_level", 20, CargoCarTests::savedWithLevel);
        tests.test("cargo_car_saves_what_it_holds", 20, CargoCarTests::savesCargo);
        tests.test("cargo_breaking_a_car_closes_its_train_up_and_drops_its_cargo", 20, CargoCarTests::breaking);
        tests.test("cargo_a_derailed_train_drops_every_car", 20, CargoCarTests::derails);
    }

    // ---- crafting ----

    private static void crafts(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack air = ItemStack.EMPTY;
        var input = CraftingInput.of(3, 3, List.of(iron, air, iron, iron, new ItemStack(Items.CHEST), iron, iron, iron, iron));
        var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        if (recipe.isEmpty() || !recipe.get().value().assemble(input).is(RailworksRegistries.CARGO_CAR.get())) {
            helper.fail("iron round a chest did not craft a Cargo car", STAND);
        }
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("railworks", "cargo_car"));
        if (recipe.isPresent() && !recipe.get().id().equals(key)) {
            helper.fail("iron round a chest crafted " + recipe.get().id() + ", not the Cargo car's recipe", STAND);
        }
        helper.succeed();
    }

    // ---- coupling ----

    private static void couplesBehind(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = placeCargoBeside(helper, locomotive, -1);
        Train train = locomotive.train();
        if (train == null || train != cargo.train() || train.cars().size() != 2
                || train.cars().getFirst() != locomotive.car() || train.cars().getLast() != cargo.car()) {
            helper.fail("the Cargo car did not couple behind the Locomotive: " + train + " and " + cargo.train(), STAND);
            return;
        }
        // Five blocks apart, middle to middle: half a five-block Locomotive and half a five-block Cargo car.
        double gap = locomotive.getX() - cargo.getX();
        if (Math.abs(gap - 5.0) > 1e-6) {
            helper.fail("the coupled car stands " + gap + " behind the Locomotive", STAND);
        }
        helper.succeed();
    }

    private static void couplesAhead(GameTestHelper helper) {
        // Five blocks further from the line's end than the others, so a whole car fits ahead of it.
        ServerPlayer placer = LocomotiveTests.player(helper, -45.0F);
        placer.snapTo(placer.getX() - 5.0, placer.getY(), placer.getZ(), -45.0F, 5.0F);
        LocomotiveTests.use(helper, placer);
        LocomotiveEntity locomotive = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class,
                LocomotiveTests.searchBox(helper)).getFirst();
        CargoCarEntity cargo = placeCargoBeside(helper, locomotive, 1);
        Train train = locomotive.train();
        if (train == null || train.cars().getFirst() != cargo.car() || train.cars().getLast() != locomotive.car()) {
            helper.fail("the Cargo car did not couple ahead of the Locomotive: " + train, STAND);
            return;
        }
        if (Math.abs(cargo.getX() - locomotive.getX() - 5.0) > 1e-6) {
            helper.fail("the car coupled ahead stands " + (cargo.getX() - locomotive.getX()) + " ahead of the Locomotive, not five", STAND);
        }
        helper.succeed();
    }

    private static void planMatchesCoupling(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        ServerPlayer player = cargoPlayer(helper, locomotive, -1);
        PlacementPlan plan = LocomotiveTests.plan(helper, player);
        if (plan == null || plan.isRefused()) {
            helper.fail("a Cargo car aimed behind a Locomotive planned " + plan, STAND);
            return;
        }
        LocomotiveTests.use(helper, player);
        CargoCarEntity cargo = onlyCargo(helper);
        BlockPos centre = BlockPos.containing(cargo.getBoundingBox().getCenter());
        if (!plan.blocks().getFirst().pos().equals(centre)) {
            helper.fail("the plan drew the car at " + plan.blocks().getFirst().pos() + " but it coupled at " + centre, STAND);
        }
        helper.succeed();
    }

    private static void placesAlone(GameTestHelper helper) {
        ServerPlayer player = LocomotiveTests.player(helper, -45.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.CARGO_CAR.get()));
        LocomotiveTests.use(helper, player);
        CargoCarEntity cargo = onlyCargo(helper);
        Train train = cargo.train();
        if (train == null || train.cars().size() != 1 || !(train.cars().getFirst() instanceof CargoCar)
                || !cargo.holds(LocomotiveTests.span(helper))) {
            helper.fail("a Cargo car placed alone is not a Train of its own on the Span: " + train, STAND);
        }
        if (!player.getMainHandItem().isEmpty()) {
            helper.fail("placing the only Cargo car left one in hand", STAND);
        }
        // Five blocks long, coupler to coupler, as every car is.
        if (cargo.car().length() != 5.0) {
            helper.fail("the placed Cargo car is " + cargo.car().length() + " blocks long, not five", STAND);
        }
        helper.succeed();
    }

    private static void refusedMidTrain(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        // Aimed at the Locomotive itself, nowhere near where a car would couple.
        ServerPlayer player = cargoPlayer(helper, locomotive, 0);
        PlacementPlan plan = LocomotiveTests.plan(helper, player);
        if (plan == null || plan.refusal() != RailworksRefusal.SPAN_TAKEN) {
            helper.fail("a Cargo car aimed at a Locomotive's own place planned " + plan, STAND);
        }
        helper.succeed();
    }

    private static void locomotiveFacingAway(GameTestHelper helper) {
        LocomotiveEntity first = LocomotiveTests.placed(helper);
        // Behind the first Locomotive, looking back down the Line: it faces the other way.
        ServerPlayer player = cargoPlayer(helper, first, -1, 45.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.LOCOMOTIVE.get()));
        LocomotiveTests.use(helper, player);
        List<LocomotiveEntity> locomotives = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class,
                LocomotiveTests.searchBox(helper), car -> car != first);
        if (locomotives.size() != 1) {
            helper.fail("the second Locomotive was not placed, found " + locomotives.size(), STAND);
            return;
        }
        LocomotiveEntity second = locomotives.getFirst();
        Train train = first.train();
        if (train == null || train != second.train() || !train.reversed(second.car()) || train.reversed(first.car())) {
            helper.fail("the second Locomotive did not couple facing the other way: " + train, STAND);
            return;
        }
        if (Math.abs(Math.abs(second.getYRot() - first.getYRot()) - 180.0F) > 0.5F) {
            helper.fail("the two Locomotives face " + first.getYRot() + " and " + second.getYRot(), STAND);
        }
        helper.succeed();
    }

    private static void shearsUncouple(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = placeCargoBeside(helper, locomotive, -1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        // The Cargo car's front, toward the Locomotive: east, since the car faces east.
        cargo.interact(player, InteractionHand.MAIN_HAND, new Vec3(0.0, 1.0, 0.0).add(0.4, 0.0, 0.0));
        Train front = locomotive.train();
        Train back = cargo.train();
        if (front == null || back == null || front == back || front.cars().size() != 1 || back.cars().size() != 1) {
            helper.fail("shears did not part the Train in two: " + front + " and " + back, STAND);
            return;
        }
        var trains = LevelRails.of(helper.getLevel()).trains();
        if (!trains.all().contains(front) || !trains.all().contains(back)) {
            helper.fail("the level does not hold the two Trains uncoupling left", STAND);
        }
        if (player.getMainHandItem().getDamageValue() != 1) {
            helper.fail("the shears took no wear", STAND);
        }
        // The Locomotive drives off and leaves the car behind.
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 2));
        ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
        LocomotiveTests.press(driver, true, false);
        double cargoAt = cargo.getX();
        for (int i = 0; i < 10; i++) {
            LocomotiveTests.step(locomotive);
            LocomotiveTests.step(cargo);
        }
        if (cargo.getX() != cargoAt || locomotive.train().speed() <= 0.0) {
            helper.fail("the uncoupled car moved with the Locomotive, or the Locomotive did not move", STAND);
        }
        helper.succeed();
    }

    private static void movingDoesNotUncouple(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = placeCargoBeside(helper, locomotive, -1);
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 2));
        ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
        LocomotiveTests.press(driver, true, false);
        LocomotiveTests.run(locomotive, 3);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        cargo.interact(player, InteractionHand.MAIN_HAND, new Vec3(0.4, 1.0, 0.0));
        if (locomotive.train() != cargo.train() || locomotive.train().cars().size() != 2) {
            helper.fail("a moving Train parted under the shears", STAND);
        }
        helper.succeed();
    }

    // ---- the menu, leading mobs ----

    private static void sneakOpensMenu(GameTestHelper helper) {
        CargoCarEntity cargo = lone(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (!(player.containerMenu instanceof ChestMenu menu) || menu.getContainer() != cargo.cargo()) {
            helper.fail("sneaking at the Cargo car did not open its cargo, the player has " + player.containerMenu, STAND);
            return;
        }
        if (player.getVehicle() != null) {
            helper.fail("sneaking at the Cargo car boarded it", STAND);
        }
        helper.succeed();
    }

    private static void holdsItems(GameTestHelper helper) {
        CargoCarEntity cargo = lone(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        cargo.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        ChestMenu menu = (ChestMenu) player.containerMenu;
        menu.setCarried(new ItemStack(Items.IRON_ORE, 40));
        menu.clicked(5, 0, ContainerInput.PICKUP, player);
        if (!cargo.cargo().getItem(5).is(Items.IRON_ORE) || cargo.cargo().getItem(5).getCount() != 40) {
            helper.fail("the cargo did not take the ore, slot 5 holds " + cargo.cargo().getItem(5), STAND);
        }
        if (cargo.cargo().getContainerSize() != 54 || menu.getRowCount() != 6) {
            helper.fail("the cargo is not a double chest's six rows", STAND);
        }
        player.setPos(player.getX() + 20.0, player.getY(), player.getZ());
        if (menu.stillValid(player)) {
            helper.fail("the cargo stays open for a player far from the car", STAND);
        }
        helper.succeed();
    }

    private static void pullsMobIn(GameTestHelper helper) {
        // A Locomotive: nobody rides a Cargo car.
        LocomotiveEntity car = LocomotiveTests.placed(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Pig pig = helper.spawn(EntityType.PIG, STAND.east(2));
        pig.setLeashedTo(player, true);
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (pig.getVehicle() != car) {
            helper.fail("the led pig was not pulled into the car", STAND);
        }
        if (player.getVehicle() != null) {
            helper.fail("the player boarded instead of the pig", STAND);
        }
        if (pig.isLeashed() || player.getInventory().countItem(Items.LEAD) != 1) {
            helper.fail("the lead was not handed back, leashed " + pig.isLeashed(), STAND);
        }
        helper.succeed();
    }

    private static void leadsMobOut(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.placed(helper);
        Pig pig = helper.spawn(EntityType.PIG, STAND.east(2));
        pig.startRiding(car);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        car.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        if (pig.getVehicle() != null || pig.getLeashHolder() != player || !player.getMainHandItem().isEmpty()) {
            helper.fail("the lead did not lead the pig out, riding " + pig.getVehicle() + ", held by " + pig.getLeashHolder(), STAND);
        }
        helper.succeed();
    }

    // ---- a Train of cars ----

    private static void movesAsOne(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = placeCargoBeside(helper, locomotive, -1);
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 2));
        ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
        LocomotiveTests.press(driver, true, false);
        double spacing = (Locomotive.LENGTH + CargoCar.LENGTH) / 2.0;
        double start = cargo.getX();
        // The Train ticks once, and moves the car behind with it.
        for (int i = 0; i < 15; i++) {
            LocomotiveTests.step(locomotive);
            if (Math.abs(locomotive.getX() - cargo.getX() - spacing) > 1e-6) {
                helper.fail("on tick " + i + " the car behind is " + (locomotive.getX() - cargo.getX()) + " back", STAND);
            }
        }
        if (cargo.getX() - start < 0.05) {
            helper.fail("the car behind did not move", STAND);
        }
        helper.succeed();
    }

    private static void savedWithLevel(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = placeCargoBeside(helper, locomotive, -1);
        LevelRails rails = LevelRails.of(helper.getLevel());
        var saved = LevelRails.TYPE.codec().encodeStart(NbtOps.INSTANCE, rails).getOrThrow();
        LevelRails loaded = LevelRails.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        Train was = locomotive.train();
        Train now = loaded.trains().of(cargo.carId());
        if (now == null || now != loaded.trains().of(locomotive.carId()) || now.cars().size() != 2
                || !now.head().equals(was.head())
                || !now.spans(loaded.network()).equals(was.spans(rails.network()))) {
            helper.fail("a Train of two cars came back from the level's save as " + now, STAND);
        }
        // The entities keep only their car's id, so a reloaded car finds the same Train.
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(
                net.minecraft.util.ProblemReporter.DISCARDING);
        cargo.saveWithoutId(output);
        var reloaded = RailworksRegistries.CARGO_CAR_ENTITY.get().create(helper.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.LOAD);
        reloaded.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        if (reloaded.train() != was || reloaded.car() != cargo.car()) {
            helper.fail("a reloaded Cargo car did not find its Train", STAND);
        }
        helper.succeed();
    }

    private static void savesCargo(GameTestHelper helper) {
        CargoCarEntity cargo = lone(helper);
        cargo.cargo().setItem(3, new ItemStack(Items.IRON_ORE, 12));
        cargo.cargo().setItem(20, new ItemStack(Items.COAL, 5));
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(
                net.minecraft.util.ProblemReporter.DISCARDING);
        cargo.saveWithoutId(output);
        var reloaded = RailworksRegistries.CARGO_CAR_ENTITY.get().create(helper.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.LOAD);
        reloaded.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        for (int slot = 0; slot < CargoCarEntity.SLOTS; slot++) {
            if (!ItemStack.matches(cargo.cargo().getItem(slot), reloaded.cargo().getItem(slot))) {
                helper.fail("slot " + slot + " came back as " + reloaded.cargo().getItem(slot) + ", not " + cargo.cargo().getItem(slot), STAND);
            }
        }
        helper.succeed();
    }

    private static void breaking(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = placeCargoBeside(helper, locomotive, -1);
        cargo.cargo().setItem(0, new ItemStack(Items.IRON_ORE, 8));
        cargo.kill(helper.getLevel());
        Train left = locomotive.train();
        if (left == null || left.cars().size() != 1 || left.cars().getFirst() != locomotive.car()) {
            helper.fail("breaking the Cargo car left " + left, STAND);
        }
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, LocomotiveTests.searchBox(helper));
        if (drops.stream().noneMatch(drop -> drop.getItem().is(Items.IRON_ORE))) {
            helper.fail("a broken Cargo car did not drop its cargo, dropped " + drops, STAND);
        }
        helper.succeed();
    }

    private static void derails(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = placeCargoBeside(helper, locomotive, -1);
        var network = LevelRails.of(helper.getLevel()).network();
        network.remove(PylonColumn.arm(helper.absolutePos(A.above(HEIGHT - 1))), PylonColumn.arm(helper.absolutePos(B.above(HEIGHT - 1))));
        LocomotiveTests.step(cargo);
        if (!locomotive.isRemoved() || !cargo.isRemoved()) {
            helper.fail("a Train whose Span was cut left a car in the world", STAND);
        }
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, LocomotiveTests.searchBox(helper));
        if (drops.stream().noneMatch(d -> d.getItem().is(RailworksRegistries.CARGO_CAR))
                || drops.stream().noneMatch(d -> d.getItem().is(RailworksRegistries.LOCOMOTIVE))) {
            helper.fail("a derailed Train should drop every car, dropped " + drops, STAND);
        }
        var trains = LevelRails.of(helper.getLevel()).trains();
        if (trains.of(locomotive.carId()) != null || trains.of(cargo.carId()) != null) {
            helper.fail("a derailed Train is still in the level", STAND);
        }
        helper.succeed();
    }

    // ---- setup ----

    /**
     * A survival player holding a Cargo car, aimed {@code side} car lengths along the Line from
     * {@code car}: +1 where a car would couple ahead of it, -1 behind, 0 at the car itself.
     */
    static ServerPlayer cargoPlayer(GameTestHelper helper, CarEntity car, int side) {
        return cargoPlayer(helper, car, side, -45.0F);
    }

    /** As {@link #cargoPlayer(GameTestHelper, CarEntity, int)}, looking east along the Line at -45 degrees or west at 45. */
    private static ServerPlayer cargoPlayer(GameTestHelper helper, CarEntity car, int side, float yaw) {
        ServerPlayer player = LocomotiveTests.player(helper, yaw);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.CARGO_CAR.get()));
        // Looking 45 degrees off south from STAND's row, the line of sight meets the Rail as far
        // along the Line from the player as the Rail is south of them: aim at the car's spot, then
        // stand that far back from it.
        BlockPos stand = helper.absolutePos(STAND);
        double reach = car.getZ() - (stand.getZ() + 0.5);
        double target = car.getX() + side * (Locomotive.LENGTH + CargoCar.LENGTH) / 2.0;
        double x = yaw < 0.0F ? target - reach : target + reach;
        player.snapTo(x, stand.getY(), stand.getZ() + 0.5, yaw, 5.0F);
        return player;
    }

    static CargoCarEntity placeCargoBeside(GameTestHelper helper, CarEntity car, int side) {
        ServerPlayer player = cargoPlayer(helper, car, side);
        PlacementPlan plan = LocomotiveTests.plan(helper, player);
        if (plan == null || plan.isRefused()) {
            helper.fail("a Cargo car aimed " + side + " cars from " + car.position() + " by a player at " + player.position()
                    + " planned " + plan, STAND);
        }
        LocomotiveTests.use(helper, player);
        return onlyCargo(helper);
    }

    /** A Cargo car alone on the Span. */
    static CargoCarEntity lone(GameTestHelper helper) {
        ServerPlayer player = LocomotiveTests.player(helper, -45.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.CARGO_CAR.get()));
        LocomotiveTests.use(helper, player);
        return onlyCargo(helper);
    }

    private static CargoCarEntity onlyCargo(GameTestHelper helper) {
        List<CargoCarEntity> cars = helper.getLevel().getEntitiesOfClass(CargoCarEntity.class, LocomotiveTests.searchBox(helper),
                car -> !car.isRemoved());
        if (cars.size() != 1) {
            helper.fail("expected one Cargo car, found " + cars.size(), STAND);
        }
        return cars.getFirst();
    }
}
