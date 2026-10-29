// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.railworks.BurnerGeneratorBlockEntity;
import io.github._5thlayer.railworks.CarRecords;
import io.github._5thlayer.railworks.ChargerBlockEntity;
import io.github._5thlayer.railworks.ChargerReport;
import io.github._5thlayer.railworks.LevelCarRecords;
import io.github._5thlayer.railworks.LevelChargers;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.ChargeSpec;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Electric Locomotive, the Charger and the Burner Generator in a world (#9): their recipes, the
 * energy handlers each shows Jade, a Charger taking FE in and filling a Locomotive beside it, the
 * Creative Charger filling one at once, a Charger placing only in the row two blocks off the Rail
 * at a Cargo car's chests' height (#72), and a Burner Generator fed by a hopper charging a Charger set
 * against it. Which Trains a Charger serves and when a Train waits on it are {@code LogisticsTest}'s,
 * in the model. Prefixed {@code electric_}, but for a Charger beside a Locomotive's cab rather than
 * its Engine room charging nothing (#74), prefixed {@code engine_room_}.
 */
final class ElectricTests {

    /** Beside the cab of the Locomotive standing at the Depot's leading car's place: the block behind the Depot's own. */
    private static final BlockPos CAB = LogisticsTests.DEPOT.west();
    /** Where the Burner Generator stands, a hopper over it and a Charger east of it. */
    private static final BlockPos BURNER_GENERATOR = new BlockPos(2, 2, 2);

    private ElectricTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("electric_recipes_make_the_locomotive_the_charger_and_the_burner_generator_and_none_the_creative_charger", 1,
                ElectricTests::crafts);
        tests.test("electric_charger_takes_fe_in_up_to_its_rate_a_tick_and_gives_none_out", 20, ElectricTests::chargerTakesFe);
        tests.test("electric_charger_row_places_two_blocks_off_the_rail_at_a_cargo_cars_chests_height_and_nowhere_nearer", 20,
                helper -> FluidTests.placesOnlyInTheRow(helper, RailworksRegistries.CHARGER_ITEM.get(),
                        RailworksRegistries.CHARGER.get(), RailworksRefusal.CHARGER_OFF_PLATFORM));
        tests.test("electric_burner_generator_fed_coal_by_a_hopper_makes_300_fe_a_tick_into_a_charger_against_it", 200,
                ElectricTests::burnerGeneratorCharges);
        tests.test("electric_burner_generator_takes_no_fuel_that_leaves_something_behind", 60, ElectricTests::burnerGeneratorRefusesLava);
        // Their lines run out past the structure, as the refuel tests' do.
        tests.testOnItsOwn("electric_locomotive_starts_empty_its_energy_handler_refuses_insert_and_extract_and_breaking_it_loses_its_charge",
                20, ElectricTests::locomotiveHandler);
        tests.testOnItsOwn("electric_charger_charges_a_manual_train_at_rest_beside_it_at_its_rate", 200, ElectricTests::chargesManual);
        tests.testOnItsOwn("electric_creative_charger_fills_a_locomotive_beside_it_in_one_tick", 100, ElectricTests::creativeFills);
        tests.testOnItsOwn("engine_room_a_charger_beside_an_electric_locomotives_cab_charges_nothing_and_jade_says_so", 100,
                ElectricTests::cabCharges);
        tests.testOnItsOwn("electric_locomotive_driven_by_hand_draws_300_fe_a_tick_and_stops_when_empty", 20, ElectricTests::drives);
    }

    // ---- recipes ----

    private static void crafts(GameTestHelper helper) {
        ItemStack copper = new ItemStack(Items.COPPER_INGOT);
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        expectCrafts(helper, List.of(copper, redstone, copper, redstone, new ItemStack(RailworksRegistries.LOCOMOTIVE.get()),
                redstone, copper, redstone, copper), RailworksRegistries.ELECTRIC_LOCOMOTIVE.get(), "electric_locomotive");
        expectCrafts(helper, List.of(copper, redstone, copper, redstone, new ItemStack(Items.IRON_BLOCK), redstone,
                copper, redstone, copper), RailworksRegistries.CHARGER_ITEM.get(), "charger");
        expectCrafts(helper, List.of(iron, copper, iron, copper, new ItemStack(Items.FURNACE), copper, iron, copper, iron),
                RailworksRegistries.BURNER_GENERATOR_ITEM.get(), "burner_generator");
        var creative = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("railworks", "creative_charger"));
        if (helper.getLevel().getServer().getRecipeManager().byKey(creative).isPresent()) {
            helper.fail("the Creative Charger has a recipe");
        }
        helper.succeed();
    }

    private static void expectCrafts(GameTestHelper helper, List<ItemStack> grid, Item made, String id) {
        var input = CraftingInput.of(3, 3, grid);
        var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        var key = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("railworks", id));
        if (recipe.isEmpty() || !recipe.get().id().equals(key) || !recipe.get().value().assemble(input).is(made)) {
            helper.fail(grid + " crafted " + recipe.map(r -> r.id().toString()).orElse("nothing") + ", not " + id);
        }
    }

    // ---- the Charger ----

    private static void chargerTakesFe(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, RailworksRegistries.CHARGER.get());
        EnergyHandler charger = energy(helper, at);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(insert(charger, 25_000) == ChargeSpec.chargerRate(), "a tick's insert took more or less than the rate");
                    helper.assertTrue(insert(charger, 5_000) == 0, "a second insert in the tick went past the rate");
                    helper.assertTrue(extract(charger, 1_000) == 0, "FE came out of a Charger");
                    helper.assertTrue(charger.getAmountAsLong() == ChargeSpec.chargerRate(), "it holds " + charger.getAmountAsLong());
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(insert(charger, 25_000) == ChargeSpec.chargerRate(), "the next tick took no more");
                    helper.assertTrue(LevelChargers.of(helper.getLevel()).at(helper.absolutePos(at)).orElseThrow().held()
                            == 2 * ChargeSpec.chargerRate(), "the level's record does not hold what went in");
                })
                .thenSucceed();
    }

    /**
     * The Depot, a Charger of {@code block} in the row beside its leading car's place, at the Depot's
     * own height, beside the leading Locomotive's Engine room, and a manual Electric Locomotive, a
     * Cargo car behind it, standing there empty. The Locomotive.
     */
    private static LocomotiveEntity atTheDepot(GameTestHelper helper, Block block) {
        return atTheDepot(helper, block, true);
    }

    /** {@link #atTheDepot}, the Charger beside the Locomotive's Engine room, or beside its cab, by the Depot. */
    private static LocomotiveEntity atTheDepot(GameTestHelper helper, Block block, boolean engineRoom) {
        Span last = LogisticsTests.line(helper);
        LogisticsTests.station(helper, LogisticsTests.DEPOT);
        BlockPos charger = engineRoom ? LogisticsTests.besideTheLeadingEngineRoom(helper, LogisticsTests.DEPOT) : CAB;
        if (charger.getY() != LogisticsTests.DEPOT.getY()) {
            throw helper.assertionException(charger, Component.literal("the block beside the leading car is not in the Depot's row"));
        }
        helper.setBlock(charger, block);
        Locomotive east = Locomotive.electric();
        CargoCar wagon = CargoCar.create();
        // Its middle where the Depot's leading Locomotive stands.
        Train train = Train.of(new Place(last, last.b(), LogisticsTests.AT_THE_DEPOT), List.of(east, wagon));
        LocomotiveEntity locomotive = LogisticsTests.spawn(helper, RailworksRegistries.ELECTRIC_LOCOMOTIVE_ENTITY.get(), train, east);
        LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, wagon);
        return locomotive;
    }

    private static void chargesManual(GameTestHelper helper) {
        LocomotiveEntity locomotive = atTheDepot(helper, RailworksRegistries.CHARGER.get());
        EnergyHandler charger = energy(helper, LogisticsTests.besideTheLeadingEngineRoom(helper, LogisticsTests.DEPOT));
        long[] last = {0L};
        helper.onEachTick(() -> {
            // A cable feeding it the Charger's rate each tick.
            insert(charger, (int) ChargeSpec.chargerRate());
            long now = charge(helper, locomotive);
            helper.assertTrue(now - last[0] <= ChargeSpec.chargerRate(), "it charged " + (now - last[0]) + " FE in a tick");
            last[0] = now;
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(charge(helper, locomotive) >= 5 * ChargeSpec.chargerRate(),
                        "the manual Train is charged " + charge(helper, locomotive) + " FE"))
                .thenExecute(() -> helper.assertFalse(locomotive.train().automatic(), "the Train is no longer manual"))
                .thenExecute(() -> LogisticsTests.forceLoad(helper, false))
                .thenSucceed();
    }

    private static void creativeFills(GameTestHelper helper) {
        LocomotiveEntity locomotive = atTheDepot(helper, RailworksRegistries.CREATIVE_CHARGER.get());
        EnergyHandler charger = energy(helper, LogisticsTests.besideTheLeadingEngineRoom(helper, LogisticsTests.DEPOT));
        helper.onEachTick(() -> {
            long now = charge(helper, locomotive);
            helper.assertTrue(now == 0L || now == ChargeSpec.locomotiveCapacity(), "it holds " + now + " FE, neither empty nor full");
        });
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(charger.getAmountAsLong() == charger.getCapacityAsLong(), "the Creative Charger does not show full");
                    helper.assertTrue(insert(charger, 1_000) == 0, "the Creative Charger took FE in");
                    helper.assertTrue(extract(charger, 1_000) == 0, "FE came out of the Creative Charger");
                })
                .thenWaitUntil(() -> helper.assertTrue(charge(helper, locomotive) == ChargeSpec.locomotiveCapacity(), "not yet full"))
                .thenExecute(() -> LogisticsTests.forceLoad(helper, false))
                .thenSucceed();
    }

    /**
     * A Creative Charger beside the cab of the Electric Locomotive standing at the Depot, not its
     * Engine room, gives it nothing, and Jade on it says so; moved beside the Engine room, it fills
     * the Locomotive, and Jade says that too.
     */
    private static void cabCharges(GameTestHelper helper) {
        LocomotiveEntity locomotive = atTheDepot(helper, RailworksRegistries.CREATIVE_CHARGER.get(), false);
        BlockPos engineRoom = LogisticsTests.besideTheLeadingEngineRoom(helper, LogisticsTests.DEPOT);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(charge(helper, locomotive) == 0L, "the Charger beside the cab gave " + charge(helper, locomotive) + " FE");
                    expectJade(helper, CAB, List.of("tooltip.railworks.charger.jade.station_leading",
                            "tooltip.railworks.charger.jade.not_engine_room"));
                    helper.setBlock(engineRoom, RailworksRegistries.CREATIVE_CHARGER.get());
                    expectJade(helper, engineRoom, List.of("tooltip.railworks.charger.jade.station_leading",
                            "tooltip.railworks.charger.jade.engine_room"));
                })
                .thenWaitUntil(() -> helper.assertTrue(charge(helper, locomotive) == ChargeSpec.locomotiveCapacity(),
                        "not yet full from beside the Engine room"))
                .thenExecute(() -> LogisticsTests.forceLoad(helper, false))
                .thenSucceed();
    }

    /** Jade on the Charger at {@code pos} shows lines of {@code keys}, in order. */
    private static void expectJade(GameTestHelper helper, BlockPos pos, List<String> keys) {
        List<String> said = ChargerReport.of(helper.getBlockEntity(pos, ChargerBlockEntity.class)).lines().stream()
                .map(line -> line.getContents() instanceof TranslatableContents words ? words.getKey() : line.getString())
                .toList();
        helper.assertTrue(said.equals(keys), "Jade on the Charger says " + said + ", not " + keys);
    }

    // ---- the Burner Generator ----

    private static void burnerGeneratorCharges(GameTestHelper helper) {
        BlockPos charger = BURNER_GENERATOR.east();
        helper.setBlock(BURNER_GENERATOR, RailworksRegistries.BURNER_GENERATOR.get());
        helper.setBlock(charger, RailworksRegistries.CHARGER.get());
        hopperOver(helper, BURNER_GENERATOR, new ItemStack(Items.COAL, 4));
        EnergyHandler generator = energy(helper, BURNER_GENERATOR);
        long[] last = {-1L};
        int[] steady = {0};
        helper.onEachTick(() -> {
            long now = energy(helper, charger).getAmountAsLong();
            if (last[0] > 0L) {
                helper.assertTrue(now - last[0] == 300L, "the Charger took " + (now - last[0]) + " FE in a tick, not 300");
                steady[0]++;
            }
            last[0] = now;
        });
        helper.startSequence()
                .thenExecute(() -> helper.assertTrue(insert(generator, 1_000) == 0, "FE went into a Burner Generator"))
                .thenWaitUntil(() -> helper.assertTrue(steady[0] >= 40, "not yet steady"))
                .thenSucceed();
    }

    private static void burnerGeneratorRefusesLava(GameTestHelper helper) {
        helper.setBlock(BURNER_GENERATOR, RailworksRegistries.BURNER_GENERATOR.get());
        BlockPos hopper = hopperOver(helper, BURNER_GENERATOR, new ItemStack(Items.LAVA_BUCKET));
        var generator = helper.getBlockEntity(BURNER_GENERATOR, BurnerGeneratorBlockEntity.class);
        helper.assertFalse(generator.canPlaceItem(BurnerGeneratorBlockEntity.FUEL, new ItemStack(Items.LAVA_BUCKET)),
                "the fuel slot takes a lava bucket");
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(generator.getItem(BurnerGeneratorBlockEntity.FUEL).isEmpty(), "the hopper put the lava bucket in");
                    helper.assertTrue(helper.getBlockEntity(hopper, HopperBlockEntity.class).getItem(0).is(Items.LAVA_BUCKET),
                            "the lava bucket left the hopper");
                    helper.assertTrue(generator.stored() == 0L, "it made FE of nothing");
                })
                .thenSucceed();
    }

    /** A hopper over {@code pos}, pointing down into it, holding {@code stack}; where it stands. */
    private static BlockPos hopperOver(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        BlockPos hopper = pos.above();
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, stack);
        return hopper;
    }

    // ---- the Locomotive ----

    private static void locomotiveHandler(GameTestHelper helper) {
        Span last = LogisticsTests.line(helper);
        Locomotive car = Locomotive.electric();
        LocomotiveEntity locomotive = LogisticsTests.spawn(helper, RailworksRegistries.ELECTRIC_LOCOMOTIVE_ENTITY.get(),
                Train.of(new Place(last, last.b(), 0.5), car), car);
        EnergyHandler handler = locomotive.getCapability(Capabilities.Energy.ENTITY, null);
        helper.assertTrue(handler != null, "an Electric Locomotive has no energy handler");
        helper.assertTrue(handler.getAmountAsLong() == 0L, "it starts with " + handler.getAmountAsLong() + " FE");
        helper.assertTrue(handler.getCapacityAsLong() == ChargeSpec.locomotiveCapacity(), "it holds " + handler.getCapacityAsLong() + " at most");
        helper.assertTrue(insert(handler, 1_000) == 0, "FE went into the Locomotive through its handler");
        record(helper, locomotive).setCharge(5_000L);
        helper.assertTrue(handler.getAmountAsLong() == 5_000L, "its handler shows " + handler.getAmountAsLong() + ", not its charge");
        helper.assertTrue(extract(handler, 1_000) == 0, "FE came out of the Locomotive through its handler");
        helper.assertTrue(charge(helper, locomotive) == 5_000L, "its charge changed");
        locomotive.kill(helper.getLevel());
        helper.assertTrue(LevelCarRecords.of(helper.getLevel()).of(car.id()).isEmpty(), "its charge outlived it");
        LogisticsTests.forceLoad(helper, false);
        helper.succeed();
    }

    private static void drives(GameTestHelper helper) {
        Span last = LogisticsTests.line(helper);
        Locomotive car = Locomotive.electric();
        // At the line's east end, facing west along it, room ahead.
        LocomotiveEntity locomotive = LogisticsTests.spawn(helper, RailworksRegistries.ELECTRIC_LOCOMOTIVE_ENTITY.get(),
                Train.of(new Place(last, last.a(), last.length() - 0.5), car), car);
        record(helper, locomotive).setCharge(3_000L);
        ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
        LocomotiveTests.press(driver, true, false);
        LocomotiveTests.run(locomotive, 5);
        helper.assertTrue(charge(helper, locomotive) == 3_000L - 5 * 300L, "five ticks at full power drew "
                + (3_000L - charge(helper, locomotive)) + " FE, not 1,500");
        helper.assertTrue(locomotive.train().speed() > 0.0, "a charged Locomotive under a driver did not move");
        LocomotiveTests.run(locomotive, 30);
        helper.assertTrue(charge(helper, locomotive) == 0L, "it holds " + charge(helper, locomotive) + " FE after running dry");
        helper.assertTrue(locomotive.train().speed() == 0.0, "out of charge it did not brake to a stop");
        LogisticsTests.forceLoad(helper, false);
        helper.succeed();
    }

    // ---- energy ----

    private static EnergyHandler energy(GameTestHelper helper, BlockPos pos) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.UP);
        if (handler == null) {
            throw helper.assertionException(pos, Component.literal("no energy handler"));
        }
        return handler;
    }

    private static int insert(EnergyHandler handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static int extract(EnergyHandler handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    private static CarRecords.Contents record(GameTestHelper helper, LocomotiveEntity locomotive) {
        return LevelCarRecords.of(helper.getLevel()).of(locomotive.locomotive().id())
                .orElseThrow(() -> helper.assertionException(Component.literal("the Locomotive has no record")));
    }

    private static long charge(GameTestHelper helper, LocomotiveEntity locomotive) {
        return record(helper, locomotive).charge();
    }
}
