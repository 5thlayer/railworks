// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import static io.github._5thlayer.railworks.gametest.LocomotiveTests.STAND;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.CarRecords;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.HatchMode;
import io.github._5thlayer.railworks.LevelCarRecords;
import io.github._5thlayer.railworks.LevelHatches;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Train;
import io.github._5thlayer.railworks.rail.Logistics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A car's record in the level's saved data (ADR 0008, amended in #6): a Cargo car's cargo and a
 * Locomotive's fuel slots kept there by the car's id, its entity only a view onto it. They survive
 * the level's save with their components, outlive the view leaving, answer the level's goods with
 * no view, follow a car through uncoupling, drop once when the car breaks, and take in what an
 * entity saved before the record held. All of these tests share the {@code record_} prefix, and
 * stand on {@link LocomotiveTests}' Span.
 */
final class CarRecordTests {

    private CarRecordTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("record_cargo_and_fuel_survive_the_levels_save", 20, CarRecordTests::survivesSave);
        tests.test("record_cargo_outlives_its_view_and_comes_back_with_it", 20, CarRecordTests::outlivesView);
        tests.test("record_goods_answer_for_cars_with_no_view", 20, CarRecordTests::goodsWithNoView);
        tests.test("record_uncoupling_keeps_each_cars_contents", 20, CarRecordTests::uncouplingKeeps);
        tests.test("record_breaking_a_car_drops_its_record_once", 20, CarRecordTests::breakingDropsOnce);
        tests.test("record_a_cargo_car_saved_with_its_cargo_hands_it_over_once", 20, CarRecordTests::legacyCargo);
        tests.test("record_a_locomotive_saved_with_its_cab_hands_it_over_once", 20, CarRecordTests::legacyCab);
        tests.test("record_data_merge_sets_what_a_car_holds", 20, CarRecordTests::dataMerge);
        tests.test("far_a_train_saved_with_each_of_its_cars_takes_in_each_cars_cab_once", 20, CarRecordTests::legacyTrain);
        tests.test("record_goes_with_a_car_copied_into_another_level", 20, CarRecordTests::otherLevel);
        tests.test("record_a_locomotive_out_of_view_burns_its_slots_fuel", 20, CarRecordTests::burnsOutOfView);
        tests.test("record_a_locomotive_out_of_view_leaves_a_lava_bucket_for_its_view", 20, CarRecordTests::lavaWaits);
    }

    private static void survivesSave(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = CargoCarTests.placeCargoBeside(helper, locomotive, -1);
        ItemStack ore = named(new ItemStack(Items.IRON_ORE, 12), "Ore");
        cargo.cargo().setItem(3, ore.copy());
        LocomotiveTests.fill(helper, locomotive, named(new ItemStack(Items.COAL, 5), "Anthracite"));
        CarRecords records = LevelCarRecords.of(helper.getLevel());
        var saved = LevelCarRecords.TYPE.codec().encodeStart(NbtOps.INSTANCE,
                helper.getLevel().getDataStorage().computeIfAbsent(LevelCarRecords.TYPE)).getOrThrow();
        CarRecords loaded = LevelCarRecords.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow().records();
        expectSame(helper, records, loaded, cargo.carId());
        expectSame(helper, records, loaded, locomotive.carId());
        if (!ItemStack.matches(loaded.of(cargo.carId()).orElseThrow().getItem(3), ore)) {
            helper.fail("the named ore came back as " + loaded.of(cargo.carId()).orElseThrow().getItem(3), STAND);
        }
        helper.succeed();
    }

    private static void outlivesView(GameTestHelper helper) {
        CargoCarEntity cargo = CargoCarTests.lone(helper);
        ItemStack ore = named(new ItemStack(Items.IRON_ORE, 12), "Ore");
        cargo.cargo().setItem(3, ore.copy());
        CompoundTag tag = saved(cargo);
        if (tag.contains("cargo")) {
            helper.fail("the Cargo car's entity still saves its cargo: " + tag.get("cargo"), STAND);
        }
        // Its chunk unloads: the view goes, the car and its record stay.
        cargo.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        Optional<CarRecords.Contents> record = LevelCarRecords.of(helper.getLevel()).of(cargo.carId());
        if (record.isEmpty() || !ItemStack.matches(record.get().getItem(3), ore)) {
            helper.fail("the record lost the cargo as its view unloaded: " + record, STAND);
        }
        CargoCarEntity back = load(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), tag);
        if (!ItemStack.matches(back.cargo().getItem(3), ore)) {
            helper.fail("the view came back holding " + back.cargo().getItem(3) + " in slot 3", STAND);
        }
        helper.succeed();
    }

    private static void goodsWithNoView(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = CargoCarTests.placeCargoBeside(helper, locomotive, -1);
        cargo.cargo().setItem(0, new ItemStack(Items.IRON_ORE, 12));
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 5));
        CargoCar cargoCar = (CargoCar) cargo.car();
        Locomotive locomotiveCar = locomotive.locomotive();
        cargo.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        locomotive.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        Goods<Item> goods = LevelRails.of(helper.getLevel()).goods(helper.getLevel());
        if (goods.held(cargoCar, Items.IRON_ORE) != 12 || goods.empty(cargoCar)
                || !goods.contents(cargoCar).equals(java.util.Map.of(Items.IRON_ORE, 12L))) {
            helper.fail("a Cargo car out of view holds " + goods.contents(cargoCar) + ", empty " + goods.empty(cargoCar), STAND);
        }
        if (!goods.slotsFuelled(locomotiveCar)) {
            helper.fail("a Locomotive out of view has no fuel in its slots", STAND);
        }
        // A provider's record beside them, loading the car and fuelling the Locomotive with no view.
        BlockPos at = helper.absolutePos(STAND.west(3));
        var hatch = LevelHatches.of(helper.getLevel()).put(at, HatchMode.PROVIDER);
        try (var transaction = Transaction.openRoot()) {
            ResourceHandlerUtil.insertStacking(hatch.contents(), ItemResource.of(Items.IRON_ORE), 20, transaction);
            ResourceHandlerUtil.insertStacking(hatch.contents(), ItemResource.of(Items.COAL), 10, transaction);
            transaction.commit();
        }
        var from = new Logistics.Hatch<Item>(at.getX(), at.getY(), at.getZ(), null, 0, true, Goods.Kind.ITEMS, Optional.empty());
        long loaded = goods.load(from, cargoCar, Items.IRON_ORE, 20);
        long room = goods.fuelRoom(from, locomotiveCar, Items.COAL);
        long fuelled = goods.refuel(from, locomotiveCar, Items.COAL, 10);
        if (loaded != 20 || goods.held(cargoCar, Items.IRON_ORE) != 32) {
            helper.fail("a provider loaded " + loaded + " into a Cargo car out of view", STAND);
        }
        if (room <= 0 || fuelled != 10) {
            helper.fail("a provider found room for " + room + " coal and fuelled " + fuelled + " into a Locomotive out of view", STAND);
        }
        var slots = LevelCarRecords.of(helper.getLevel()).of(locomotiveCar.id()).orElseThrow();
        if (!slots.getItem(0).is(Items.COAL) || slots.getItem(0).getCount() != 15) {
            helper.fail("the fuel slots out of view hold " + slots.getItem(0), STAND);
        }
        helper.succeed();
    }

    private static void uncouplingKeeps(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = CargoCarTests.placeCargoBeside(helper, locomotive, -1);
        cargo.cargo().setItem(7, new ItemStack(Items.IRON_ORE, 12));
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 5));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        cargo.interact(player, InteractionHand.MAIN_HAND, new Vec3(0.4, 1.0, 0.0));
        if (locomotive.train() == cargo.train()) {
            helper.fail("shears did not part the Train", STAND);
            return;
        }
        if (!ItemStack.matches(cargo.cargo().getItem(7), new ItemStack(Items.IRON_ORE, 12))
                || !ItemStack.matches(locomotive.fuelSlot(0), new ItemStack(Items.COAL, 5))) {
            helper.fail("uncoupling left the car " + cargo.cargo().getItem(7) + " and the Locomotive " + locomotive.fuelSlot(0), STAND);
        }
        helper.succeed();
    }

    private static void breakingDropsOnce(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = CargoCarTests.placeCargoBeside(helper, locomotive, -1);
        cargo.cargo().setItem(0, new ItemStack(Items.IRON_ORE, 8));
        cargo.cargo().setItem(9, new ItemStack(Items.IRON_ORE, 3));
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 5));
        UUID cargoId = cargo.carId();
        UUID locomotiveId = locomotive.carId();
        cargo.kill(helper.getLevel());
        locomotive.kill(helper.getLevel());
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, LocomotiveTests.searchBox(helper));
        int ore = drops.stream().filter(d -> d.getItem().is(Items.IRON_ORE)).mapToInt(d -> d.getItem().getCount()).sum();
        int coal = drops.stream().filter(d -> d.getItem().is(Items.COAL)).mapToInt(d -> d.getItem().getCount()).sum();
        if (ore != 11 || coal != 5) {
            helper.fail("the broken cars dropped " + ore + " ore and " + coal + " coal, not 11 and 5", STAND);
        }
        CarRecords records = LevelCarRecords.of(helper.getLevel());
        if (records.of(cargoId).isPresent() || records.of(locomotiveId).isPresent()) {
            helper.fail("a broken car's record is still in the level", STAND);
        }
        helper.succeed();
    }

    private static void legacyCargo(GameTestHelper helper) {
        CargoCarEntity cargo = CargoCarTests.lone(helper);
        ItemStack ore = named(new ItemStack(Items.IRON_ORE, 12), "Ore");
        NonNullList<ItemStack> held = NonNullList.withSize(CargoCarEntity.SLOTS, ItemStack.EMPTY);
        held.set(3, ore.copy());
        held.set(20, new ItemStack(Items.COAL, 5));
        // The entity as a world saved before the record held it: its cargo on it, and no record.
        CompoundTag tag = saved(cargo);
        var old = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        ContainerHelper.saveAllItems(old.child("cargo"), held);
        tag.put("cargo", old.buildResult().get("cargo"));
        CarRecords records = LevelCarRecords.of(helper.getLevel());
        records.remove(cargo.carId());
        cargo.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        CargoCarEntity first = load(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), tag);
        expectCargo(helper, records, cargo.carId(), held);
        if (saved(first).contains("cargo")) {
            helper.fail("a migrated Cargo car still saves its cargo on its entity", STAND);
        }
        // The same old entity loaded again, as a chunk saved before its level's record would be: the record keeps its own.
        records.of(cargo.carId()).orElseThrow().setItem(20, ItemStack.EMPTY);
        held.set(20, ItemStack.EMPTY);
        load(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), tag);
        expectCargo(helper, records, cargo.carId(), held);
        helper.succeed();
    }

    private static void legacyCab(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        List<ItemStack> cab = List.of(ItemStack.EMPTY, new ItemStack(Items.COAL, 10), new ItemStack(Items.CHARCOAL, 5),
                named(new ItemStack(Items.COAL, 20), "Anthracite"));
        CompoundTag tag = saved(locomotive);
        var old = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        var list = old.list("cab", ItemStack.OPTIONAL_CODEC);
        cab.forEach(list::add);
        tag.put("cab", old.buildResult().get("cab"));
        CarRecords records = LevelCarRecords.of(helper.getLevel());
        records.remove(locomotive.carId());
        locomotive.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LocomotiveEntity first = load(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), tag);
        LocomotiveTests.expectSlots(helper, first, cab);
        if (saved(first).contains("cab")) {
            helper.fail("a migrated Locomotive still saves its cab on its entity", STAND);
        }
        LocomotiveEntity again = load(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), tag);
        LocomotiveTests.expectSlots(helper, again, cab);
        helper.succeed();
    }

    /**
     * Two Locomotives of one Train, each saved, as a world from before the level held Trains saved
     * them, with the whole Train on it, its own place in it and its cab (#6): the first loaded takes the
     * Train in and is its car's view; the second hands its cab to its car's record and goes, the level
     * showing its car by a view of its own. Neither drops anything, and each car has one view.
     */
    private static void legacyTrain(GameTestHelper helper) {
        Pair pair = pair(helper);
        Train train = pair.ahead().train();
        List<LocomotiveEntity> cars = List.of(pair.ahead(), pair.behind());
        List<ItemStack> cabs = List.of(new ItemStack(Items.COAL, 7), new ItemStack(Items.CHARCOAL, 3));
        List<CompoundTag> tags = new java.util.ArrayList<>();
        for (int i = 0; i < cars.size(); i++) {
            CompoundTag tag = saved(cars.get(i));
            tag.remove("car_id");
            tag.put("train", Train.CODEC.encodeStart(NbtOps.INSTANCE, train).getOrThrow());
            tag.putInt("car", train.cars().indexOf(cars.get(i).car()));
            var old = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
            old.list("cab", ItemStack.OPTIONAL_CODEC).add(cabs.get(i));
            tag.put("cab", old.buildResult().get("cab"));
            tags.add(tag);
        }
        List<UUID> ids = cars.stream().map(CarEntity::carId).toList();
        // As that world loads: no Train in the level, no records and no views.
        LevelRails rails = LevelRails.of(helper.getLevel());
        CarRecords records = LevelCarRecords.of(helper.getLevel());
        rails.trains().remove(train);
        for (LocomotiveEntity car : cars) {
            records.remove(car.carId());
            car.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }
        tags.forEach(tag -> helper.getLevel().addFreshEntity(load(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), tag)));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    Train back = rails.trains().of(ids.get(0));
                    if (back == null || rails.trains().of(ids.get(1)) != back) {
                        helper.fail("the two Locomotives came back in " + back + " and " + rails.trains().of(ids.get(1)), STAND);
                    }
                    for (int i = 0; i < ids.size(); i++) {
                        LocomotiveEntity view = (LocomotiveEntity) rails.view(back.car(ids.get(i)));
                        if (view == null) {
                            helper.fail("the Locomotive " + i + " has no view", STAND);
                            return;
                        }
                        LocomotiveTests.expectSlots(helper, view, List.of(cabs.get(i), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
                        UUID id = ids.get(i);
                        long entities = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class, LocomotiveTests.searchBox(helper))
                                .stream().filter(car -> id.equals(car.carId())).count();
                        if (entities != 1) {
                            helper.fail("the Locomotive " + i + " has " + entities + " entities", STAND);
                        }
                    }
                    List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, LocomotiveTests.searchBox(helper));
                    if (!drops.isEmpty()) {
                        helper.fail("loading the old Train dropped " + drops.stream().map(ItemEntity::getItem).toList(), STAND);
                    }
                })
                .thenSucceed();
    }

    private static void dataMerge(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = CargoCarTests.placeCargoBeside(helper, locomotive, -1);
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.CHARCOAL, 5));
        cargo.cargo().setItem(0, new ItemStack(Items.IRON_ORE, 12));
        // As the Gallery pack fills its cars: the named slots are what each holds now.
        command(helper, "data merge entity " + locomotive.getStringUUID() + " {cab:[{id:\"minecraft:coal\",count:32}]}");
        command(helper, "data merge entity " + cargo.getStringUUID()
                + " {cargo:{Items:[{Slot:4b,id:\"minecraft:iron_ingot\",count:20}]}}");
        LocomotiveTests.expectSlots(helper, locomotive, List.of(new ItemStack(Items.COAL, 32), ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY));
        if (!cargo.cargo().getItem(0).isEmpty() || !ItemStack.matches(cargo.cargo().getItem(4), new ItemStack(Items.IRON_INGOT, 20))) {
            helper.fail("data merge left the Cargo car holding " + cargo.cargo().getItem(0) + " and " + cargo.cargo().getItem(4), STAND);
        }
        helper.succeed();
    }

    private static void otherLevel(GameTestHelper helper) {
        CargoCarEntity cargo = CargoCarTests.lone(helper);
        cargo.cargo().setItem(2, new ItemStack(Items.IRON_ORE, 12));
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) {
            helper.fail("the game test server has no Nether", STAND);
            return;
        }
        // As a portal or /tp copies it: the copy takes the record with it.
        CargoCarEntity copy = RailworksRegistries.CARGO_CAR_ENTITY.get().create(nether, EntitySpawnReason.DIMENSION_TRAVEL);
        copy.restoreFrom(cargo);
        var there = LevelCarRecords.of(nether).of(cargo.carId());
        if (LevelCarRecords.of(helper.getLevel()).of(cargo.carId()).isPresent()
                || there.isEmpty() || !ItemStack.matches(there.get().getItem(2), new ItemStack(Items.IRON_ORE, 12))) {
            helper.fail("the record did not go with the car into the Nether: " + there, STAND);
        }
        // Nor does the car stay in its Train here, where the level would show it again, holding nothing.
        if (LevelRails.of(helper.getLevel()).trains().of(cargo.carId()) != null) {
            helper.fail("the car stayed in its Train in the level it left", STAND);
        }
        LevelCarRecords.of(nether).remove(cargo.carId());
        helper.succeed();
    }

    private static void burnsOutOfView(GameTestHelper helper) {
        Pair pair = pair(helper);
        LocomotiveTests.fill(helper, pair.behind(), new ItemStack(Items.COAL, 3));
        Locomotive behind = pair.behind().locomotive();
        pair.behind().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        // Driven from the one in view, which has no fuel: only the one out of view can pull.
        ServerPlayer driver = LocomotiveTests.board(helper, pair.ahead());
        LocomotiveTests.press(driver, true, false);
        double start = pair.ahead().getX();
        LocomotiveTests.run(pair.ahead(), 5);
        var slots = LevelCarRecords.of(helper.getLevel()).of(behind.id()).orElseThrow();
        if (!ItemStack.matches(slots.getItem(0), new ItemStack(Items.COAL, 2)) || behind.fuel().storedJoules() <= 0) {
            helper.fail("the Locomotive out of view burnt nothing: its slot holds " + slots.getItem(0)
                    + ", its buffer " + behind.fuel().storedJoules() + " J", STAND);
        }
        if (pair.ahead().getX() - start < 0.01) {
            helper.fail("the Train did not move on the fuel of its Locomotive out of view", STAND);
        }
        helper.succeed();
    }

    private static void lavaWaits(GameTestHelper helper) {
        Pair pair = pair(helper);
        LocomotiveTests.fill(helper, pair.behind(), new ItemStack(Items.LAVA_BUCKET));
        LocomotiveTests.fill(helper, pair.behind(), new ItemStack(Items.COAL, 3));
        Locomotive behind = pair.behind().locomotive();
        pair.behind().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        ServerPlayer driver = LocomotiveTests.board(helper, pair.ahead());
        LocomotiveTests.press(driver, true, false);
        LocomotiveTests.run(pair.ahead(), 5);
        // The lava is burnt first, and its bucket would have nowhere to go: nothing is burnt.
        var slots = LevelCarRecords.of(helper.getLevel()).of(behind.id()).orElseThrow();
        if (!slots.getItem(0).is(Items.LAVA_BUCKET) || !ItemStack.matches(slots.getItem(1), new ItemStack(Items.COAL, 3))
                || behind.fuel().storedJoules() != 0) {
            helper.fail("the Locomotive out of view burnt from its slots: " + slots.getItem(0) + ", " + slots.getItem(1)
                    + ", its buffer " + behind.fuel().storedJoules() + " J", STAND);
        }
        if (helper.getLevel().getEntitiesOfClass(ItemEntity.class, LocomotiveTests.searchBox(helper))
                .stream().anyMatch(drop -> drop.getItem().is(Items.BUCKET))) {
            helper.fail("a bucket was dropped for a Locomotive out of view", STAND);
        }
        helper.succeed();
    }

    // ---- setup ----

    /** Two Locomotives coupled, both facing east, the one ahead with nothing in its fuel slots. */
    private record Pair(LocomotiveEntity ahead, LocomotiveEntity behind) {
    }

    private static Pair pair(GameTestHelper helper) {
        LocomotiveEntity ahead = LocomotiveTests.placed(helper);
        ServerPlayer player = CargoCarTests.cargoPlayer(helper, ahead, -1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.LOCOMOTIVE.get()));
        LocomotiveTests.use(helper, player);
        LocomotiveEntity behind = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class, LocomotiveTests.searchBox(helper))
                .stream().filter(car -> car != ahead).findFirst().orElseThrow();
        if (behind.train() != ahead.train() || behind.train().reversed(behind.car())) {
            helper.fail("the second Locomotive did not couple behind facing east: " + behind.train(), STAND);
        }
        return new Pair(ahead, behind);
    }

    private static void command(GameTestHelper helper, String command) {
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(helper.getLevel())
                .withSuppressedOutput(), command);
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static CompoundTag saved(CarEntity car) {
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        car.saveWithoutId(output);
        return output.buildResult();
    }

    private static <T extends CarEntity> T load(GameTestHelper helper, EntityType<T> type, CompoundTag tag) {
        T car = type.create(helper.getLevel(), EntitySpawnReason.LOAD);
        car.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        return car;
    }

    private static void expectSame(GameTestHelper helper, CarRecords was, CarRecords now, UUID car) {
        var before = was.of(car).orElseThrow();
        var after = now.of(car).orElse(null);
        if (after == null || after.getContainerSize() != before.getContainerSize()) {
            helper.fail("car " + car + "'s record came back as " + after, STAND);
            return;
        }
        for (int slot = 0; slot < before.getContainerSize(); slot++) {
            if (!ItemStack.matches(before.getItem(slot), after.getItem(slot))) {
                helper.fail("slot " + slot + " came back as " + after.getItem(slot) + ", not " + before.getItem(slot), STAND);
            }
        }
    }

    private static void expectCargo(GameTestHelper helper, CarRecords records, UUID car, List<ItemStack> expected) {
        var record = records.of(car).orElse(null);
        if (record == null) {
            helper.fail("the Cargo car's cargo did not reach the record", STAND);
            return;
        }
        for (int slot = 0; slot < expected.size(); slot++) {
            if (!ItemStack.matches(record.getItem(slot), expected.get(slot))) {
                helper.fail("record slot " + slot + " holds " + record.getItem(slot) + ", not " + expected.get(slot), STAND);
            }
        }
    }
}
