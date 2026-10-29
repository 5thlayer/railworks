// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.DynamicOps;
import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.CarRecords;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.FluidCarEntity;
import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchBlockEntity;
import io.github._5thlayer.railworks.HatchMode;
import io.github._5thlayer.railworks.Hatches;
import io.github._5thlayer.railworks.LevelCarRecords;
import io.github._5thlayer.railworks.LevelHatches;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PlatformsNear;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.Tank;
import io.github._5thlayer.railworks.Ware;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.FluidCar;
import io.github._5thlayer.railworks.rail.HatchRequest;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import io.github._5thlayer.railworks.rail.Logistics;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Fluid car and the Fluid hatch in a real level (#10): their recipes, the car placed and coupled
 * into a Train and weighing a Cargo car's 1000 kg, buckets by hand on the car and on both kinds of
 * Fluid hatch, a pipe through the hatch's fluid capability, its Sight glasses and a requester's
 * bucket, the tanks kept through a save, and automatic Trains carrying fluid between Fluid
 * hatches: a job, a leftover in a Fluid car, a Train mixing a Cargo car and a Fluid car, and a
 * provider Fluid hatch beside a Locomotive that fuels none. Stations and Fluid hatches stand in the row two blocks off the Rail at a
 * Cargo car's chests' height (#72), and a Fluid hatch places nowhere nearer. How fluid is planned, claimed and moved is {@code LogisticsTest}'s; a
 * Train carrying fluid out of view is {@code FarAwayTests}'. These tests share the {@code fluid_}
 * prefix.
 */
final class FluidTests {

    /**
     * A straight line along z 3, Span by Span from x -11 to 37, its Pylons out past the structure at
     * both ends: the Quarry's platform runs back to three five-block car spots before the line's west
     * end cuts it short, and the Depot's, at the line's east end, four, back to just east of the
     * Quarry's leading car's place, so the two never overlap. No Pylon stands under the Quarry's lead
     * mark, at x 9: a job is never planned for a provider whose lead mark is at an Arm.
     */
    private static final int[] PYLONS = {-11, -1, 8, 19, 29, 37};
    private static final int Z = 3;
    /**
     * South of the line in its row, two blocks off the Rail at a Cargo car's chests' height, serving
     * Trains heading east: its leading car's place beside x 7 to 11, its car spots back west from 2 to 6.
     */
    private static final BlockPos QUARRY = new BlockPos(11, 3, 5);
    /** South of the line at its east end in its row, serving Trains heading east: its leading car's place beside x 33 to 37, its car spots back west from 28 to 32. */
    private static final BlockPos DEPOT = new BlockPos(37, 3, 5);
    /** Two blocks in the row beside each of a Station's first two car spots: each spot's first and last. */
    private static final BlockPos QUARRY_0 = new BlockPos(6, 3, 5);
    private static final BlockPos QUARRY_0_OTHER = new BlockPos(2, 3, 5);
    private static final BlockPos QUARRY_1 = new BlockPos(1, 3, 5);
    private static final BlockPos QUARRY_1_OTHER = new BlockPos(-3, 3, 5);
    private static final BlockPos QUARRY_LEADING = new BlockPos(9, 3, 5);
    private static final BlockPos DEPOT_0 = new BlockPos(32, 3, 5);
    private static final BlockPos DEPOT_0_OTHER = new BlockPos(28, 3, 5);
    private static final BlockPos DEPOT_1 = new BlockPos(27, 3, 5);
    private static final BlockPos DEPOT_1_OTHER = new BlockPos(23, 3, 5);
    /** A hatch inside the structure, joining no Station: only a tank. */
    private static final BlockPos LONE = new BlockPos(4, 2, 6);

    /**
     * The row tests' Rail, a Span along z 3 inside the plot from x -1 to 11, and a Station south of
     * it in its row at its east end, serving Trains heading east: its first car spot beside x 2 to 6.
     */
    private static final BlockPos ROW_WEST = new BlockPos(-1, 1, 3);
    private static final BlockPos ROW_EAST = new BlockPos(11, 1, 3);
    private static final BlockPos ROW_STATION = new BlockPos(11, 3, 5);
    /** Beside the first car spot's first block in the row: two blocks off the Rail at a Cargo car's chests' height. */
    private static final BlockPos IN_ROW = new BlockPos(6, 3, 5);
    /** One block off the Rail at the chests' height, between the row and the Rail. */
    private static final BlockPos ONE_OFF = new BlockPos(6, 3, 4);
    /** The old row (#70): one block off the Rail at a car's floor. */
    private static final BlockPos OLD_ROW = new BlockPos(6, 2, 4);

    private static final int BUCKET = (int) Logistics.MILLIBUCKETS_PER_BUCKET;

    private FluidTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("fluid_car_crafts_from_iron_round_a_bucket", 1, FluidTests::carCrafts);
        tests.test("fluid_hatch_crafts_from_iron_round_a_bucket_and_a_hopper", 1, FluidTests::hatchCrafts);
        tests.test("fluid_car_couples_behind_a_locomotive_and_weighs_a_cargo_cars_1000_kg", 20, FluidTests::couples);
        tests.test("fluid_car_buckets_by_hand_fill_and_empty_it_and_it_opens_no_menu", 20, FluidTests::carByHand);
        tests.test("fluid_hatch_a_filled_bucket_sets_a_requesters_request_and_stays_full", 20, FluidTests::requesterByHand);
        tests.test("fluid_hatch_buckets_by_hand_fill_and_empty_a_provider", 20, FluidTests::providerByHand);
        tests.test("fluid_hatch_a_pipe_fills_and_empties_it_through_its_fluid_capability", 20, FluidTests::pipe);
        tests.test("fluid_hatch_sight_glass_a_provider_draws_no_bucket_and_a_requester_the_bucket_it_asks_for", 40,
                FluidTests::shows);
        tests.test("fluid_tanks_are_kept_through_a_save", 20, FluidTests::saved);
        tests.test("fluid_command_fluid_hatch_fills_a_provider_and_sets_a_requesters_request", 1, FluidTests::command);
        tests.test("fluid_hatch_row_places_two_blocks_off_the_rail_at_a_cargo_cars_chests_height_and_nowhere_nearer", 20,
                helper -> placesOnlyInTheRow(helper, RailworksRegistries.FLUID_HATCH_ITEM.get(),
                        RailworksRegistries.FLUID_HATCH.get(), RailworksRefusal.HATCH_OFF_PLATFORM));
        // Each runs its line out past the structure.
        tests.testOnItsOwn("fluid_a_job_carries_fluid_from_a_provider_fluid_hatch_to_a_requester_one", 1_200, FluidTests::job);
        tests.testOnItsOwn("fluid_a_leftover_poured_in_by_hand_is_delivered_once_a_requester_asks_for_it", 1_200,
                FluidTests::leftover);
        tests.testOnItsOwn("fluid_a_mixed_train_trades_each_car_only_with_a_hatch_of_its_kind", 1_200, FluidTests::mixed);
        tests.testOnItsOwn("fluid_a_provider_fluid_hatch_fuels_no_locomotive", 200, FluidTests::fuelsNone);
        // It sets the server config's tank for every Fluid car made meanwhile.
        tests.testOnItsOwn("fluid_car_tank_holds_the_buckets_the_server_config_sets", 20, FluidTests::configuredTank);
        tests.test("fluid_car_sight_glass_shows_a_bucket_poured_in_and_one_taken_out", 20, FluidTests::sightGlassByHand);
        tests.testOnItsOwn("fluid_car_sight_glass_shows_a_fluid_hatchs_trade_at_a_station", 1_200, FluidTests::sightGlassTrade);
        tests.test("fluid_car_sight_glass_shows_the_tank_its_record_holds_when_its_view_is_made_again", 20,
                FluidTests::sightGlassMadeAgain);
        tests.test("fluid_hatch_sight_glass_its_watchers_see_its_tank_after_a_bucket_a_pipe_and_the_command", 40,
                FluidTests::hatchSightGlass);
        tests.testOnItsOwn("fluid_hatch_sight_glass_its_watchers_see_its_tank_after_a_trade_with_a_fluid_car", 1_200,
                FluidTests::hatchSightGlassTrade);
    }

    // ---- crafting ----

    private static void carCrafts(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        expectCrafts(helper, "fluid_car", RailworksRegistries.FLUID_CAR.get().getDefaultInstance(),
                List.of(iron, ItemStack.EMPTY, iron, iron, new ItemStack(Items.BUCKET), iron, iron, iron, iron));
    }

    private static void hatchCrafts(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        expectCrafts(helper, "fluid_hatch", RailworksRegistries.FLUID_HATCH_ITEM.get().getDefaultInstance(),
                List.of(iron, iron, iron, iron, new ItemStack(Items.BUCKET), iron, iron, new ItemStack(Items.HOPPER), iron));
    }

    private static void expectCrafts(GameTestHelper helper, String name, ItemStack result, List<ItemStack> grid) {
        var input = CraftingInput.of(3, 3, grid);
        var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("railworks", name));
        if (recipe.isEmpty() || !recipe.get().id().equals(key) || !recipe.get().value().assemble(input).is(result.getItem())) {
            helper.fail("the grid crafted " + recipe.map(r -> r.id().toString()).orElse("nothing") + ", not " + name);
        }
        helper.succeed();
    }

    // ---- the car ----

    private static void couples(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        FluidCarEntity fluid = placeBeside(helper, locomotive);
        Train train = locomotive.train();
        if (train == null || train != fluid.train() || train.cars().size() != 2 || !(train.cars().getLast() instanceof FluidCar)) {
            helper.fail("the Fluid car did not couple behind the Locomotive: " + train + " and " + fluid.train(), LocomotiveTests.STAND);
            return;
        }
        double empty = train.mass();
        fill(fluid.tank(), Fluids.WATER, 10 * BUCKET);
        if (Math.abs(empty - (LocomotiveSpec.MASS_KG + 1_000.0)) > 1e-6 || train.mass() != empty) {
            helper.fail("the Train weighs " + empty + " empty and " + train.mass() + " full, not the Locomotive and 1000 kg",
                    LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    private static void carByHand(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        FluidCarEntity fluid = placeBeside(helper, locomotive);
        ServerPlayer player = LogisticsTests.near(helper, LocomotiveTests.STAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        fluid.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        expectTank(helper, fluid.tank(), Fluids.WATER, BUCKET, "a water bucket poured in");
        expectHand(helper, player, Items.BUCKET);
        if (player.containerMenu != player.inventoryMenu || !(player.containerMenu instanceof InventoryMenu)) {
            helper.fail("using the Fluid car opened " + player.containerMenu, LocomotiveTests.STAND);
        }
        // Another fluid does not mix with the one it holds.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        fluid.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        expectTank(helper, fluid.tank(), Fluids.WATER, BUCKET, "a lava bucket used");
        expectHand(helper, player, Items.LAVA_BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        fluid.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        expectTank(helper, fluid.tank(), Fluids.EMPTY, 0, "an empty bucket filled");
        expectHand(helper, player, Items.WATER_BUCKET);
        helper.succeed();
    }

    /** A Fluid car placed by a survival player behind {@code car}, as a Cargo car is. */
    private static FluidCarEntity placeBeside(GameTestHelper helper, LocomotiveEntity car) {
        ServerPlayer player = CargoCarTests.cargoPlayer(helper, car, -1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.FLUID_CAR.get()));
        LocomotiveTests.use(helper, player);
        List<FluidCarEntity> cars = helper.getLevel().getEntitiesOfClass(FluidCarEntity.class, LocomotiveTests.searchBox(helper),
                view -> !view.isRemoved());
        if (cars.size() != 1 || !player.getMainHandItem().isEmpty()) {
            throw helper.assertionException(LocomotiveTests.STAND, Component.literal("placing a Fluid car left " + cars.size()
                    + " of them, and " + player.getMainHandItem() + " in hand"));
        }
        return cars.getFirst();
    }

    // ---- the hatch by hand ----

    private static void requesterByHand(GameTestHelper helper) {
        helper.setBlock(LONE, fluidHatch(HatchMode.REQUESTER));
        ServerPlayer player = LogisticsTests.near(helper, LONE.north());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        use(helper, player, LONE);
        expectRequest(helper, new HatchRequest<>(Ware.of(Fluids.WATER), 8));
        use(helper, player, LONE);
        expectRequest(helper, new HatchRequest<>(Ware.of(Fluids.WATER), 16));
        expectHand(helper, player, Items.WATER_BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        use(helper, player, LONE);
        expectRequest(helper, new HatchRequest<>(Ware.of(Fluids.LAVA), 8));
        // Up to its tank.
        for (int i = 0; i < 20; i++) {
            use(helper, player, LONE);
        }
        expectRequest(helper, new HatchRequest<>(Ware.of(Fluids.LAVA), Hatches.tankBuckets()));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(helper, player, LONE);
        if (record(helper, LONE).request().isPresent()) {
            helper.fail("an empty hand left the request " + record(helper, LONE).request(), LONE);
        }
        // Sneaking switches it, keeping its tank.
        fill(record(helper, LONE).tank(), Fluids.WATER, 3 * BUCKET);
        player.setShiftKeyDown(true);
        use(helper, player, LONE);
        if (record(helper, LONE).mode() != HatchMode.PROVIDER || helper.getBlockState(LONE).getValue(HatchBlock.MODE) != HatchMode.PROVIDER) {
            helper.fail("a sneaking empty hand did not make the Fluid hatch a provider", LONE);
        }
        expectTank(helper, record(helper, LONE).tank(), Fluids.WATER, 3 * BUCKET, "switched to a provider");
        helper.succeed();
    }

    private static void providerByHand(GameTestHelper helper) {
        helper.setBlock(LONE, fluidHatch(HatchMode.PROVIDER));
        ServerPlayer player = LogisticsTests.near(helper, LONE.north());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        use(helper, player, LONE);
        expectTank(helper, record(helper, LONE).tank(), Fluids.WATER, BUCKET, "a water bucket poured in");
        expectHand(helper, player, Items.BUCKET);
        use(helper, player, LONE);
        expectTank(helper, record(helper, LONE).tank(), Fluids.EMPTY, 0, "an empty bucket filled");
        expectHand(helper, player, Items.WATER_BUCKET);
        if (record(helper, LONE).request().isPresent()) {
            helper.fail("a provider asks for " + record(helper, LONE).request(), LONE);
        }
        helper.succeed();
    }

    // ---- the hatch's capability and sides ----

    private static void pipe(GameTestHelper helper) {
        helper.setBlock(LONE, fluidHatch(HatchMode.PROVIDER));
        helper.setBlock(LONE.east(2), RailworksRegistries.HATCH.get().defaultBlockState());
        BlockPos at = helper.absolutePos(LONE);
        var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, at, Direction.WEST);
        if (handler == null || helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.WEST) != null) {
            helper.fail("the Fluid hatch gives a fluid handler " + handler + " and an item handler, or not as it should", LONE);
            return;
        }
        if (helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(LONE.east(2)), Direction.WEST) != null) {
            helper.fail("a hatch of items gives a fluid handler", LONE.east(2));
        }
        int in;
        int out;
        try (var transaction = Transaction.openRoot()) {
            in = handler.insert(FluidResource.of(Fluids.WATER), 5 * BUCKET, transaction);
            out = handler.extract(FluidResource.of(Fluids.WATER), 2 * BUCKET, transaction);
            transaction.commit();
        }
        if (in != 5 * BUCKET || out != 2 * BUCKET) {
            helper.fail("a pipe put in " + in + " and took out " + out, LONE);
        }
        expectTank(helper, record(helper, LONE).tank(), Fluids.WATER, 3 * BUCKET, "a pipe filled and emptied it");
        int lava;
        try (var transaction = Transaction.openRoot()) {
            lava = handler.insert(FluidResource.of(Fluids.LAVA), BUCKET, transaction);
            transaction.commit();
        }
        if (lava != 0) {
            helper.fail("a Fluid hatch holding water took " + lava + " of lava", LONE);
        }
        helper.succeed();
    }

    /**
     * A requester draws the filled bucket of what it asks for in front of its Sight glass, whatever its
     * tank holds; a provider draws none, its Sight glass showing its fluid.
     */
    private static void shows(GameTestHelper helper) {
        helper.setBlock(LONE, fluidHatch(HatchMode.REQUESTER));
        record(helper, LONE).ask(Fluids.WATER);
        fill(record(helper, LONE).tank(), Fluids.WATER, BUCKET);
        helper.setBlock(LONE.east(2), fluidHatch(HatchMode.PROVIDER));
        fill(record(helper, LONE.east(2)).tank(), Fluids.LAVA, BUCKET);
        helper.startSequence()
                .thenWaitUntil(() -> expectWatched(helper, LONE.east(2), Fluids.LAVA, BUCKET, "lava poured into the provider"))
                .thenWaitUntil(() -> {
                    ItemStack asked = helper.getBlockEntity(LONE, HatchBlockEntity.class).shown();
                    helper.assertTrue(asked.is(Items.WATER_BUCKET), "the requester shows " + asked + ", not a water bucket");
                })
                .thenExecute(() -> {
                    ItemStack held = helper.getBlockEntity(LONE.east(2), HatchBlockEntity.class).shown();
                    helper.assertTrue(held.isEmpty(), "the provider shows " + held + ", not nothing");
                })
                .thenSucceed();
    }

    // ---- the command ----

    /**
     * {@code /railworks fluid_hatch} pours buckets into a provider and has a requester ask for so many,
     * eight at a time; past the tank, at a hatch of the other mode, or at a hatch of items, it is refused.
     */
    private static void command(GameTestHelper helper) {
        helper.setBlock(LONE, fluidHatch(HatchMode.PROVIDER));
        helper.setBlock(LONE.east(2), fluidHatch(HatchMode.REQUESTER));
        helper.setBlock(LONE.east(4), RailworksRegistries.HATCH.get().defaultBlockState());
        CommandTests.run(helper, "railworks fluid_hatch %s provider minecraft:lava 5", LONE);
        expectTank(helper, record(helper, LONE).tank(), Fluids.LAVA, 5 * BUCKET, "the command poured lava in");
        CommandTests.run(helper, "railworks fluid_hatch %s requester minecraft:water 12", LONE.east(2));
        var request = record(helper, LONE.east(2)).request();
        if (!request.equals(Optional.of(new HatchRequest<>(Ware.of(Fluids.WATER), 16)))) {
            helper.fail("the command left the requester asking for " + request + ", not 16 buckets of water", LONE.east(2));
        }
        CommandTests.refused(helper, "railworks fluid_hatch %s provider minecraft:lava " + (Hatches.tankBuckets() + 1), LONE);
        CommandTests.refused(helper, "railworks fluid_hatch %s requester minecraft:water " + (Hatches.tankBuckets() + 1),
                LONE.east(2));
        CommandTests.refused(helper, "railworks fluid_hatch %s requester minecraft:water 8", LONE);
        CommandTests.refused(helper, "railworks fluid_hatch %s provider minecraft:water 1", LONE.east(4));
        CommandTests.refused(helper, "railworks hatch %s provider minecraft:iron_ingot 1", LONE);
        helper.succeed();
    }

    // ---- saving ----

    private static void saved(GameTestHelper helper) {
        helper.setBlock(LONE, fluidHatch(HatchMode.REQUESTER));
        record(helper, LONE).ask(Fluids.WATER);
        fill(record(helper, LONE).tank(), Fluids.WATER, 3 * BUCKET);
        DynamicOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Hatches hatches = Hatches.CODEC.parse(ops, Hatches.CODEC.encodeStart(ops, LevelHatches.of(helper.getLevel()))
                .getOrThrow()).getOrThrow();
        Hatches.Hatch back = hatches.at(helper.absolutePos(LONE)).orElseThrow();
        if (back.holds() != record(helper, LONE).holds() || !back.request().equals(record(helper, LONE).request())
                || back.tank().capacity() != Hatches.tankBuckets() * BUCKET) {
            helper.fail("the Fluid hatch came back as " + back.holds() + " asking for " + back.request()
                    + " with a tank of " + back.tank().capacity(), LONE);
        }
        expectTank(helper, back.tank(), Fluids.WATER, 3 * BUCKET, "the hatch saved and loaded");

        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        FluidCarEntity fluid = placeBeside(helper, locomotive);
        fill(fluid.tank(), Fluids.LAVA, 7 * BUCKET);
        CarRecords records = CarRecords.CODEC.parse(ops, CarRecords.CODEC.encodeStart(ops, LevelCarRecords.of(helper.getLevel()))
                .getOrThrow()).getOrThrow();
        Tank tank = records.of(fluid.carId()).orElseThrow().tank();
        if (tank.capacity() != 160 * BUCKET) {
            helper.fail("the Fluid car's tank came back holding " + tank.capacity(), LocomotiveTests.STAND);
        }
        expectTank(helper, tank, Fluids.LAVA, 7 * BUCKET, "the car saved and loaded");
        helper.succeed();
    }

    private static void configuredTank(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        FluidCarEntity fluid;
        FluidCarEntity.configure(12);
        try {
            fluid = placeBeside(helper, locomotive);
        } finally {
            FluidCarEntity.configure(FluidCar.DEFAULT_TANK_BUCKETS);
        }
        if (fluid.tank().capacity() != 12 * BUCKET) {
            helper.fail("a Fluid car made with 12 buckets set holds " + fluid.tank().capacity(), LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    // ---- the sight glass ----

    private static void sightGlassByHand(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        FluidCarEntity fluid = placeBeside(helper, locomotive);
        expectSeen(helper, fluid, Fluids.EMPTY, 0, "a Fluid car placed");
        ServerPlayer player = LogisticsTests.near(helper, LocomotiveTests.STAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        fluid.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        helper.startSequence()
                .thenWaitUntil(() -> expectSeen(helper, fluid, Fluids.WATER, BUCKET, "a water bucket poured in"))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    fluid.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
                })
                .thenWaitUntil(() -> expectSeen(helper, fluid, Fluids.EMPTY, 0, "an empty bucket filled"))
                .thenSucceed();
    }

    /** The {@link #job}'s Fluid car, seen to fill at the Quarry's provider and to empty at the Depot's requester. */
    private static void sightGlassTrade(GameTestHelper helper) {
        line(helper);
        fluidHatch(helper, QUARRY_0, HatchMode.PROVIDER, Fluids.WATER, 20);
        fluidHatch(helper, DEPOT_0, HatchMode.REQUESTER, null, 0);
        record(helper, DEPOT_0).ask(Fluids.WATER);
        Running running = running(helper, FluidCar.create());
        FluidCarEntity fluid = (FluidCarEntity) running.views().get(1);
        helper.startSequence()
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.to_unload"))
                .thenWaitUntil(() -> expectSeen(helper, fluid, Fluids.WATER, 8 * BUCKET, "the Fluid car left the Quarry"))
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.waiting"))
                .thenWaitUntil(() -> expectSeen(helper, fluid, Fluids.EMPTY, 0, "the Fluid car unloaded"))
                .thenExecute(() -> clearAway(helper, running.train()))
                .thenSucceed();
    }

    /**
     * A Fluid car's view goes with its chunk and is made again from its Train, as the level makes the
     * views of a world loaded from a save: the new view shows the lava its record kept.
     */
    private static void sightGlassMadeAgain(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        FluidCarEntity fluid = placeBeside(helper, locomotive);
        fill(fluid.tank(), Fluids.LAVA, 7 * BUCKET);
        Train train = fluid.train();
        Car car = fluid.car();
        fluid.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        FluidCarEntity again = RailworksRegistries.FLUID_CAR_ENTITY.get().create(helper.getLevel(), EntitySpawnReason.LOAD);
        again.setCar(train, car);
        helper.getLevel().addFreshEntity(again);
        expectSeen(helper, again, Fluids.LAVA, 7 * BUCKET, "the view made again");
        helper.succeed();
    }

    /**
     * A provider's Sight glass, as its watchers see it from its update tag: a bucket poured in, a pipe
     * filling and draining it, the command pouring more in, and emptied.
     */
    private static void hatchSightGlass(GameTestHelper helper) {
        helper.setBlock(LONE, fluidHatch(HatchMode.PROVIDER));
        ServerPlayer player = LogisticsTests.near(helper, LONE.north());
        BlockPos at = helper.absolutePos(LONE);
        helper.startSequence()
                .thenWaitUntil(() -> expectWatched(helper, LONE, Fluids.EMPTY, 0, "a Fluid hatch placed"))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
                    use(helper, player, LONE);
                })
                .thenWaitUntil(() -> expectWatched(helper, LONE, Fluids.WATER, BUCKET, "a water bucket poured in"))
                .thenExecute(() -> pourByPipe(helper, at, 4 * BUCKET))
                .thenWaitUntil(() -> expectWatched(helper, LONE, Fluids.WATER, 5 * BUCKET, "a pipe filled it"))
                .thenExecute(() -> pourByPipe(helper, at, -2 * BUCKET))
                .thenWaitUntil(() -> expectWatched(helper, LONE, Fluids.WATER, 3 * BUCKET, "a pipe drained it"))
                .thenExecute(() -> CommandTests.run(helper, "railworks fluid_hatch %s provider minecraft:water 2", LONE))
                .thenWaitUntil(() -> expectWatched(helper, LONE, Fluids.WATER, 5 * BUCKET, "the command poured water in"))
                .thenExecute(() -> pourByPipe(helper, at, -5 * BUCKET))
                .thenWaitUntil(() -> expectWatched(helper, LONE, Fluids.EMPTY, 0, "a pipe emptied it"))
                .thenSucceed();
    }

    /** Water through the Fluid hatch's capability at {@code at}: in for a positive {@code amount}, out for a negative one. */
    private static void pourByPipe(GameTestHelper helper, BlockPos at, int amount) {
        var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, at, Direction.WEST);
        try (var transaction = Transaction.openRoot()) {
            if (amount > 0) {
                handler.insert(FluidResource.of(Fluids.WATER), amount, transaction);
            } else {
                handler.extract(FluidResource.of(Fluids.WATER), -amount, transaction);
            }
            transaction.commit();
        }
    }

    /** The {@link #job}'s Fluid hatches, seen to empty at the Quarry's provider and to fill at the Depot's requester. */
    private static void hatchSightGlassTrade(GameTestHelper helper) {
        line(helper);
        fluidHatch(helper, QUARRY_0, HatchMode.PROVIDER, Fluids.WATER, 20);
        fluidHatch(helper, DEPOT_0, HatchMode.REQUESTER, null, 0);
        record(helper, DEPOT_0).ask(Fluids.WATER);
        Running running = running(helper, FluidCar.create());
        helper.startSequence()
                .thenWaitUntil(() -> expectWatched(helper, QUARRY_0, Fluids.WATER, 20 * BUCKET, "the Quarry's provider filled"))
                .thenWaitUntil(() -> expectWatched(helper, DEPOT_0, Fluids.EMPTY, 0, "the Depot's requester placed"))
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.to_unload"))
                .thenWaitUntil(() -> expectWatched(helper, QUARRY_0, Fluids.WATER, 12 * BUCKET, "the Fluid car loaded at the Quarry"))
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.waiting"))
                .thenWaitUntil(() -> expectWatched(helper, DEPOT_0, Fluids.WATER, 8 * BUCKET, "the Fluid car unloaded at the Depot"))
                .thenExecute(() -> clearAway(helper, running.train()))
                .thenSucceed();
    }

    /**
     * What the Fluid hatch at {@code pos}'s Sight glasses show, as a watcher reads them from its update
     * tag: {@code amount} of {@code fluid}, none for 0, and its record's tank, its capacity too.
     */
    private static void expectWatched(GameTestHelper helper, BlockPos pos, Fluid fluid, int amount, String what) {
        var entity = helper.getBlockEntity(pos, HatchBlockEntity.class);
        var registries = helper.getLevel().registryAccess();
        var watcher = new HatchBlockEntity(entity.getBlockPos(), entity.getBlockState());
        watcher.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, registries, entity.getUpdateTag(registries)));
        FluidStack seen = watcher.tankSeen();
        Tank tank = record(helper, pos).tank();
        boolean matches = (amount == 0 ? seen.isEmpty() : seen.is(fluid) && seen.getAmount() == amount)
                && FluidStack.matches(seen, tank.held()) && watcher.tankCapacitySeen() == tank.capacity();
        if (!matches) {
            throw helper.assertionException(pos, Component.literal(what + ", its watchers see " + seen.getAmount() + " of "
                    + seen.getFluid() + " in " + watcher.tankCapacitySeen() + ", not " + amount + " of " + fluid
                    + ", and the record holds " + tank.held().getAmount() + " in " + tank.capacity()));
        }
    }

    /**
     * What {@code car}'s Sight glasses show, as synced to every player tracking it: {@code amount} of
     * {@code fluid}, none for 0, and its record's tank, its capacity too.
     */
    static void expectSeen(GameTestHelper helper, FluidCarEntity car, Fluid fluid, int amount, String what) {
        FluidStack seen = car.tankSeen();
        Tank tank = car.tank();
        boolean matches = (amount == 0 ? seen.isEmpty() : seen.is(fluid) && seen.getAmount() == amount)
                && tank != null && FluidStack.matches(seen, tank.held()) && car.tankCapacitySeen() == tank.capacity();
        if (!matches) {
            throw helper.assertionException(LONE, Component.literal(what + ", the Sight glasses show " + seen.getAmount() + " of "
                    + seen.getFluid() + " in " + car.tankCapacitySeen() + ", not " + amount + " of " + fluid + ", and the record holds "
                    + (tank == null ? "nothing" : tank.held().getAmount() + " in " + tank.capacity())));
        }
    }

    // ---- the row ----

    /**
     * {@code item}, a hatch's or a Charger's, beside a Station's first car spot: one block off the Rail
     * at a Cargo car's chests' height and in the old row, one off at a car's floor, the Placement
     * Preview's plan is refused {@code refusal} and a click places nothing; in the row, two blocks off
     * at the chests' height, it plans and places {@code block}, joining the first car spot. Shared with
     * {@code HatchTests} and {@code ElectricTests}.
     */
    static void placesOnlyInTheRow(GameTestHelper helper, Item item, Block block, RailworksRefusal refusal) {
        for (BlockPos end : List.of(ROW_WEST, ROW_EAST)) {
            helper.setBlock(end, RailworksRegistries.PYLON.get().defaultBlockState());
        }
        var west = PylonColumn.arm(helper.absolutePos(ROW_WEST));
        var east = PylonColumn.arm(helper.absolutePos(ROW_EAST));
        LevelRails.of(helper.getLevel()).network().add(Span.of(west, east, SpanRules.cost(west, east)));
        LogisticsTests.station(helper, ROW_STATION);
        ServerPlayer player = LogisticsTests.near(helper, new BlockPos(6, 1, 7));
        for (BlockPos wrong : List.of(ONE_OFF, OLD_ROW)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
            PlacementPlan plan = Placements.planFor(item, inTheBlock(helper, player, wrong));
            if (plan == null || !plan.isRefused() || plan.refusal() != refusal) {
                helper.fail(item + " off the row planned " + plan + ", not refused " + refusal, wrong);
                return;
            }
            if (player.getMainHandItem().useOn(inTheBlock(helper, player, wrong)).consumesAction()
                    || !helper.getBlockState(wrong).isAir() || player.getMainHandItem().getCount() != 2) {
                helper.fail(item + " off the row was placed or spent", wrong);
                return;
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        PlacementPlan plan = Placements.planFor(item, inTheBlock(helper, player, IN_ROW));
        if (plan == null || plan.isRefused()) {
            helper.fail(item + " in the row planned " + plan, IN_ROW);
            return;
        }
        if (!player.getMainHandItem().useOn(inTheBlock(helper, player, IN_ROW)).consumesAction()
                || !helper.getBlockState(IN_ROW).is(block)) {
            helper.fail(item + " in the row was refused", IN_ROW);
            return;
        }
        var joined = PlatformsNear.joined(helper.getLevel(), helper.absolutePos(IN_ROW));
        if (joined.isEmpty() || joined.get().spot() != 0) {
            helper.fail("in the row it joins " + joined + ", not the first car spot", IN_ROW);
            return;
        }
        helper.succeed();
    }

    /** A click placing the held item at {@code pos}, standing in the air: the click lands in the block itself. */
    private static BlockPlaceContext inTheBlock(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    // ---- automatic Trains ----

    private static void job(GameTestHelper helper) {
        line(helper);
        fluidHatch(helper, QUARRY_0, HatchMode.PROVIDER, Fluids.WATER, 20);
        fluidHatch(helper, DEPOT_0, HatchMode.REQUESTER, null, 0);
        record(helper, DEPOT_0).ask(Fluids.WATER);
        Running running = running(helper, FluidCar.create());
        FluidCarEntity fluid = (FluidCarEntity) running.views().get(1);
        helper.startSequence()
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.to_unload"))
                .thenExecute(() -> {
                    expectTank(helper, fluid.tank(), Fluids.WATER, 8 * BUCKET, "the Fluid car left the Quarry");
                    LogisticsTests.expectIcons(helper, running.locomotive(), "8 minecraft:water_bucket");
                })
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.waiting"))
                .thenExecute(() -> {
                    expectTank(helper, record(helper, DEPOT_0).tank(), Fluids.WATER, 8 * BUCKET, "the requester was delivered to");
                    expectTank(helper, record(helper, QUARRY_0).tank(), Fluids.WATER, 12 * BUCKET, "the provider gave");
                    expectTank(helper, fluid.tank(), Fluids.EMPTY, 0, "the Fluid car unloaded");
                    LogisticsTests.expectIcons(helper, running.locomotive(), "");
                    clearAway(helper, running.train());
                })
                .thenSucceed();
    }

    private static void leftover(GameTestHelper helper) {
        line(helper);
        fluidHatch(helper, QUARRY_0, HatchMode.PROVIDER, Fluids.WATER, 20);
        Running running = running(helper, FluidCar.create());
        FluidCarEntity fluid = (FluidCarEntity) running.views().get(1);
        ServerPlayer player = LogisticsTests.near(helper, new BlockPos(-3, 2, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        fluid.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        expectTank(helper, fluid.tank(), Fluids.LAVA, BUCKET, "a lava bucket poured in by hand");
        helper.startSequence()
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.holding"))
                .thenExecute(() -> LogisticsTests.expectIcons(helper, running.locomotive(), "1 minecraft:lava_bucket"))
                // Holding, it takes no job from the Quarry.
                .thenIdle(3 * 20)
                .thenExecute(() -> {
                    LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.holding");
                    expectTank(helper, record(helper, QUARRY_0).tank(), Fluids.WATER, 20 * BUCKET, "the Train held a leftover");
                    fluidHatch(helper, DEPOT_0, HatchMode.REQUESTER, null, 0);
                    record(helper, DEPOT_0).ask(Fluids.LAVA);
                })
                .thenWaitUntil(() -> expectTank(helper, record(helper, DEPOT_0).tank(), Fluids.LAVA, BUCKET, "the leftover delivered"))
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.waiting"))
                .thenExecute(() -> {
                    expectTank(helper, fluid.tank(), Fluids.EMPTY, 0, "the leftover delivered");
                    expectTank(helper, record(helper, QUARRY_0).tank(), Fluids.WATER, 20 * BUCKET, "the leftover delivered");
                    clearAway(helper, running.train());
                })
                .thenSucceed();
    }

    /**
     * A Locomotive, a Cargo car at the first car spot and a Fluid car at the second, each spot with a
     * hatch of items and a Fluid hatch beside it at both Stations: the Cargo car carries the iron
     * between the hatches of items beside it and the Fluid car the water between the Fluid hatches
     * beside it, and the hatches of the other kind are left as they were.
     */
    private static void mixed(GameTestHelper helper) {
        line(helper);
        itemHatch(helper, QUARRY_0, HatchMode.PROVIDER, 64);
        fluidHatch(helper, QUARRY_0_OTHER, HatchMode.PROVIDER, Fluids.WATER, 16);
        fluidHatch(helper, QUARRY_1, HatchMode.PROVIDER, Fluids.WATER, 16);
        itemHatch(helper, QUARRY_1_OTHER, HatchMode.PROVIDER, 64);
        itemHatch(helper, DEPOT_0, HatchMode.REQUESTER, 0);
        fluidHatch(helper, DEPOT_0_OTHER, HatchMode.REQUESTER, null, 0);
        fluidHatch(helper, DEPOT_1, HatchMode.REQUESTER, null, 0);
        itemHatch(helper, DEPOT_1_OTHER, HatchMode.REQUESTER, 0);
        record(helper, DEPOT_0).ask(Items.IRON_INGOT);
        record(helper, DEPOT_0_OTHER).ask(Fluids.WATER);
        record(helper, DEPOT_1).ask(Fluids.WATER);
        record(helper, DEPOT_1_OTHER).ask(Items.IRON_INGOT);
        Running running = running(helper, CargoCar.create(), FluidCar.create());
        CargoCarEntity cargo = (CargoCarEntity) running.views().get(1);
        FluidCarEntity fluid = (FluidCarEntity) running.views().get(2);
        helper.startSequence()
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.to_unload"))
                .thenExecute(() -> {
                    helper.assertTrue(cargo.cargo().countItem(Items.IRON_INGOT) == 64,
                            "the Cargo car left the Quarry with " + cargo.cargo().countItem(Items.IRON_INGOT) + " iron");
                    expectTank(helper, fluid.tank(), Fluids.WATER, 8 * BUCKET, "the Fluid car left the Quarry");
                    LogisticsTests.expectIcons(helper, running.locomotive(), "64 minecraft:iron_ingot, 8 minecraft:water_bucket");
                })
                .thenWaitUntil(() -> LogisticsTests.expectStatus(helper, running.locomotive(), "status.railworks.train.waiting"))
                .thenExecute(() -> {
                    helper.assertTrue(LogisticsTests.held(record(helper, DEPOT_0)) == 64
                                    && LogisticsTests.held(record(helper, DEPOT_1_OTHER)) == 0
                                    && LogisticsTests.held(record(helper, QUARRY_0)) == 0
                                    && LogisticsTests.held(record(helper, QUARRY_1_OTHER)) == 64,
                            "the iron went from " + LogisticsTests.held(record(helper, QUARRY_0)) + " and "
                                    + LogisticsTests.held(record(helper, QUARRY_1_OTHER)) + " to "
                                    + LogisticsTests.held(record(helper, DEPOT_0)) + " and " + LogisticsTests.held(record(helper, DEPOT_1_OTHER)));
                    expectTank(helper, record(helper, DEPOT_1).tank(), Fluids.WATER, 8 * BUCKET, "the Fluid car's requester");
                    expectTank(helper, record(helper, DEPOT_0_OTHER).tank(), Fluids.EMPTY, 0, "the Cargo car's Fluid requester");
                    expectTank(helper, record(helper, QUARRY_1).tank(), Fluids.WATER, 8 * BUCKET, "the Fluid car's provider");
                    expectTank(helper, record(helper, QUARRY_0_OTHER).tank(), Fluids.WATER, 16 * BUCKET, "the Cargo car's Fluid provider");
                    clearAway(helper, running.train());
                })
                .thenSucceed();
    }

    /** A Locomotive at rest at the Quarry, automatic, a provider Fluid hatch of lava beside it: its fuel slots stay empty. */
    private static void fuelsNone(GameTestHelper helper) {
        line(helper);
        fluidHatch(helper, QUARRY_LEADING, HatchMode.PROVIDER, Fluids.LAVA, 10);
        Locomotive east = Locomotive.burner();
        Span span = span(helper, 8);
        // Its middle where the Quarry's leading car stands, half a Locomotive behind the Station block's front edge.
        Train train = Train.of(new Place(span, span.b(), 19 - (QUARRY.getX() + 0.5 - Platform.LEAD / 2.0)), List.of(east));
        LocomotiveEntity locomotive = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, east);
        train.setAutomatic(true);
        helper.startSequence()
                .thenIdle(3 * 20)
                .thenExecute(() -> {
                    helper.assertTrue(locomotive.fuelItem().isEmpty(), "a provider Fluid hatch fuelled the Locomotive with "
                            + locomotive.fuelItem());
                    expectTank(helper, record(helper, QUARRY_LEADING).tank(), Fluids.LAVA, 10 * BUCKET, "beside the Locomotive");
                    clearAway(helper, train);
                })
                .thenSucceed();
    }

    /** The views of a Train {@link #running} sets going: its Locomotive first, then its cars. */
    private record Running(Train train, LocomotiveEntity locomotive, List<CarEntity> views) {
    }

    /**
     * Both Stations on the {@link #line}, and west of the Quarry, heading east, a fuelled automatic
     * Train of a Locomotive and {@code cars} behind it, the first car spot's car first.
     */
    private static Running running(GameTestHelper helper, Car... cars) {
        Locomotive east = Locomotive.burner();
        List<Car> all = new ArrayList<>(List.of(east));
        all.addAll(List.of(cars));
        // Its leading car's middle at x 2.5, west of the Quarry, so a Train of three five-block cars ends a block short of the line's west end.
        Span first = span(helper, -1);
        Train train = Train.of(new Place(first, first.b(), PYLONS[2] - 2.5), all);
        List<CarEntity> views = new ArrayList<>();
        LocomotiveEntity locomotive = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, east);
        views.add(locomotive);
        for (var car : cars) {
            views.add(switch (car) {
                case CargoCar cargo -> LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, cargo);
                case FluidCar fluid -> LogisticsTests.spawn(helper, RailworksRegistries.FLUID_CAR_ENTITY.get(), train, fluid);
                default -> throw new IllegalArgumentException("no view for " + car);
            });
        }
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 8));
        train.setAutomatic(true);
        return new Running(train, locomotive, views);
    }

    // ---- setup ----

    /** The line, the structure's roof cleared over it, its chunks loaded, and both its Stations, named. */
    private static void line(GameTestHelper helper) {
        forceLoad(helper, true);
        for (int x = PYLONS[0]; x <= PYLONS[PYLONS.length - 1]; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
            }
        }
        for (int x : PYLONS) {
            helper.setBlock(new BlockPos(x, 1, Z), RailworksRegistries.PYLON.get().defaultBlockState());
        }
        for (int i = 0; i + 1 < PYLONS.length; i++) {
            var one = arm(helper, PYLONS[i]);
            var other = arm(helper, PYLONS[i + 1]);
            LevelRails.of(helper.getLevel()).network().add(Span.of(one, other, SpanRules.cost(one, other)));
        }
        LogisticsTests.station(helper, QUARRY);
        LogisticsTests.station(helper, DEPOT);
    }

    private static Arm arm(GameTestHelper helper, int x) {
        return PylonColumn.arm(helper.absolutePos(new BlockPos(x, 1, Z)));
    }

    /** The line's Span from the Pylon at {@code x}. */
    private static Span span(GameTestHelper helper, int x) {
        for (int i = 0; i + 1 < PYLONS.length; i++) {
            if (PYLONS[i] == x) {
                return LevelRails.of(helper.getLevel()).network().get(arm(helper, x), arm(helper, PYLONS[i + 1]));
            }
        }
        throw new IllegalArgumentException("no Pylon at " + x);
    }

    /**
     * Takes {@code train} and what its cars held out of the level, and the line's Spans, Pylons,
     * Stations and hatches and what they dropped, and lets its chunks go: each batch's structure
     * stands beside the last one's, so the next test's line runs over this one's plot.
     */
    private static void clearAway(GameTestHelper helper, Train train) {
        LevelRails rails = LevelRails.of(helper.getLevel());
        rails.trains().remove(train);
        // Its views, no Train holding their cars, go at the level's next tick.
        train.cars().forEach(car -> LevelCarRecords.of(helper.getLevel()).remove(car.id()));
        for (int i = 0; i + 1 < PYLONS.length; i++) {
            rails.network().remove(arm(helper, PYLONS[i]), arm(helper, PYLONS[i + 1]));
        }
        List<BlockPos> blocks = new ArrayList<>(List.of(QUARRY, DEPOT, QUARRY_0, QUARRY_0_OTHER, QUARRY_1, QUARRY_1_OTHER,
                QUARRY_LEADING, DEPOT_0, DEPOT_0_OTHER, DEPOT_1, DEPOT_1_OTHER));
        for (int x : PYLONS) {
            blocks.add(new BlockPos(x, 1, Z));
        }
        // What each Station was clicked onto.
        blocks.add(QUARRY.below());
        blocks.add(DEPOT.below());
        for (BlockPos pos : blocks) {
            if (!helper.getBlockState(pos).isAir()) {
                helper.destroyBlock(pos);
            }
        }
        BlockPos west = helper.absolutePos(new BlockPos(PYLONS[0], 0, 0));
        BlockPos east = helper.absolutePos(new BlockPos(PYLONS[PYLONS.length - 1], 4, 8));
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(Vec3.atLowerCornerOf(west), Vec3.atLowerCornerOf(east))
                .inflate(2.0)).forEach(Entity::discard);
        forceLoad(helper, false);
    }

    /** Forces the chunks under the {@link #line} loaded, or lets them go. */
    private static void forceLoad(GameTestHelper helper, boolean forced) {
        BlockPos west = helper.absolutePos(new BlockPos(PYLONS[0], 1, 2));
        BlockPos east = helper.absolutePos(new BlockPos(PYLONS[PYLONS.length - 1], 1, 4));
        for (int cx = Math.min(west.getX(), east.getX()) >> 4; cx <= Math.max(west.getX(), east.getX()) >> 4; cx++) {
            for (int cz = Math.min(west.getZ(), east.getZ()) >> 4; cz <= Math.max(west.getZ(), east.getZ()) >> 4; cz++) {
                helper.getLevel().setChunkForced(cx, cz, forced);
            }
        }
    }

    static BlockState fluidHatch(HatchMode mode) {
        return RailworksRegistries.FLUID_HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, mode);
    }

    /** A Fluid hatch at {@code pos}, joining the Station beside it, holding {@code buckets} of {@code fluid}. */
    private static void fluidHatch(GameTestHelper helper, BlockPos pos, HatchMode mode, Fluid fluid, int buckets) {
        helper.setBlock(pos, fluidHatch(mode));
        expectJoins(helper, pos);
        if (buckets > 0) {
            fill(record(helper, pos).tank(), fluid, buckets * BUCKET);
        }
    }

    /** A hatch of items at {@code pos}, joining the Station beside it, holding {@code iron} iron ingots. */
    private static void itemHatch(GameTestHelper helper, BlockPos pos, HatchMode mode, int iron) {
        helper.setBlock(pos, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, mode));
        expectJoins(helper, pos);
        if (iron > 0) {
            LogisticsTests.insert(record(helper, pos), new ItemStack(Items.IRON_INGOT, iron));
        }
    }

    private static void expectJoins(GameTestHelper helper, BlockPos pos) {
        var joined = PlatformsNear.joined(helper.getLevel(), helper.absolutePos(pos));
        if (joined.isEmpty()) {
            throw helper.assertionException(pos, Component.literal("the hatch joins no Station"));
        }
        if (pos.equals(QUARRY_LEADING) && joined.get().spot() != Platform.LEADING) {
            throw helper.assertionException(pos, Component.literal("the hatch is not beside the leading car: " + joined));
        }
    }

    private static Hatches.Hatch record(GameTestHelper helper, BlockPos pos) {
        return LogisticsTests.record(helper, pos);
    }

    private static void fill(Tank tank, Fluid fluid, int amount) {
        try (var transaction = Transaction.openRoot()) {
            tank.insert(FluidResource.of(fluid), amount, transaction);
            transaction.commit();
        }
    }

    /** Uses {@code player}'s main hand on the top of the block at {@code pos}, as a click does. */
    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.upFromBottomCenterOf(at, 1.0), Direction.UP, at, false));
    }

    private static void expectRequest(GameTestHelper helper, HatchRequest<Ware> expected) {
        var request = record(helper, LONE).request();
        if (!request.equals(Optional.of(expected))) {
            throw helper.assertionException(LONE, Component.literal("the Fluid hatch asks for " + request + ", not " + expected));
        }
    }

    private static void expectHand(GameTestHelper helper, ServerPlayer player, Item item) {
        if (!player.getMainHandItem().is(item) || player.getMainHandItem().getCount() != 1) {
            throw helper.assertionException(LONE, Component.literal("the player holds " + player.getMainHandItem() + ", not " + item));
        }
    }

    /** Fails unless {@code tank} holds {@code amount} millibuckets of {@code fluid}, after {@code what}. */
    private static void expectTank(GameTestHelper helper, Tank tank, Fluid fluid, int amount, String what) {
        FluidStack held = tank == null ? FluidStack.EMPTY : tank.held();
        boolean matches = amount == 0 ? held.isEmpty() : held.is(fluid) && held.getAmount() == amount;
        if (!matches) {
            throw helper.assertionException(LONE, Component.literal(what + ", the tank holds " + held.getAmount() + " of "
                    + held.getFluid() + ", not " + amount + " of " + fluid));
        }
    }
}
