// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchBlockEntity;
import io.github._5thlayer.railworks.HatchMode;
import io.github._5thlayer.railworks.Hatches;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LevelStations;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.LocomotiveMenu;
import io.github._5thlayer.railworks.PlatformsNear;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.StationPlacement;
import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Automatic Trains in a real level (#37, ADR 0008): the Locomotive's menu switches its Train to
 * automatic, the level saves the mode, a driver takes it over, the status line follows it, and one
 * Train runs unattended from a provider Station to a requester Station through the level's tick, the
 * Stations and hatches real blocks and the fuel burnt from the slot, and what it loaded for a requester
 * broken on the way is held until a requester asks for it again (#38); and a provider beside the
 * leading car's place fills an automatic Train's Locomotive with fuel that leaves nothing behind (#53),
 * those tests sharing the {@code logistics_refuel_} prefix. How jobs are planned, stacks
 * moved, claims broken and fuel moved is {@code LogisticsTest}'s. All of these tests share the {@code logistics_} prefix.
 */
final class LogisticsTests {

    /**
     * A line in the plot and the gap beside it: west along z 3, a 45-degree jog south-east, and on east
     * along z 5, so each Straight leg ends a platform after one car spot and the two never overlap. A
     * Span bends all along when either Arm does, so a short Span each side of the jog takes the bend.
     */
    private static final BlockPos[] LINE = {new BlockPos(-1, 1, 3), new BlockPos(4, 1, 3), new BlockPos(5, 1, 3),
            new BlockPos(7, 1, 5), new BlockPos(8, 1, 5), new BlockPos(12, 1, 5)};
    /** North of the west leg, serving Trains heading west: its first car spot beside x 2 and 3. */
    private static final BlockPos MINE = new BlockPos(0, 1, 2);
    /** South of the east leg at the line's end, serving Trains heading east: its first car spot beside x 9 and 10. */
    private static final BlockPos DEPOT = new BlockPos(12, 1, 6);
    private static final BlockPos PROVIDER = new BlockPos(3, 2, 2);
    private static final BlockPos REQUESTER = new BlockPos(9, 2, 6);

    private LogisticsTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("logistics_the_menus_toggle_switches_the_train_and_its_status_line_follows", 20, LogisticsTests::toggles);
        tests.test("logistics_a_driver_pressing_forward_switches_the_train_to_manual", 20, LogisticsTests::driverTakesOver);
        tests.test("logistics_the_mode_is_kept_across_a_save_and_reload", 20, LogisticsTests::saved);
        tests.test("logistics_runs_a_train_unattended_from_a_provider_to_a_requester", 1_200, LogisticsTests::unattended);
        // Its line runs out past the structure, as the unattended run's does, so the two would cross side by side.
        tests.testOnItsOwn("logistics_a_broken_requester_leaves_a_leftover_delivered_once_one_asks_again", 1_600,
                LogisticsTests::brokenRequester);
        // Its line, too, runs out past the structure.
        tests.testOnItsOwn("logistics_refuel_a_provider_by_the_leading_car_fills_the_locomotive_with_clean_fuel", 400,
                LogisticsTests::refuels);
        tests.testOnItsOwn("logistics_refuel_a_manual_train_is_left_alone", 200, LogisticsTests::refuelsNoManualTrain);
    }

    // ---- the mode ----

    private static void toggles(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.fuelled(helper);
        ServerPlayer player = near(helper);
        locomotive.openCustomInventoryScreen(player);
        if (!(player.containerMenu instanceof LocomotiveMenu menu)) {
            helper.fail("the Locomotive's menu did not open: " + player.containerMenu, LocomotiveTests.STAND);
            return;
        }
        if (!menu.clickMenuButton(player, LocomotiveMenu.TOGGLE_AUTOMATIC) || !locomotive.train().automatic()
                || !locomotive.automaticSeen()) {
            helper.fail("the toggle did not make the Train automatic: " + locomotive.train(), LocomotiveTests.STAND);
        }
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> expectStatus(helper, locomotive, "status.railworks.train.waiting"))
                .thenExecute(() -> menu.clickMenuButton(player, LocomotiveMenu.TOGGLE_AUTOMATIC))
                .thenIdle(1)
                .thenExecute(() -> {
                    if (locomotive.train().automatic()) {
                        helper.fail("the toggle did not switch the Train back to manual", LocomotiveTests.STAND);
                    }
                    expectStatus(helper, locomotive, "status.railworks.train.manual");
                })
                .thenSucceed();
    }

    private static void driverTakesOver(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.fuelled(helper);
        locomotive.train().setAutomatic(true);
        ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
        LocomotiveTests.press(driver, true, false);
        LocomotiveTests.run(locomotive, 2);
        Train train = locomotive.train();
        if (train.automatic() || train.speed() <= 0.0) {
            helper.fail("a driver's forward key left the Train automatic, or did not drive it: " + train, LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    private static void saved(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.fuelled(helper);
        locomotive.train().setAutomatic(true);
        LevelRails rails = LevelRails.of(helper.getLevel());
        var saved = LevelRails.TYPE.codec().encodeStart(NbtOps.INSTANCE, rails).getOrThrow();
        LevelRails loaded = LevelRails.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        if (!loaded.trains().of(locomotive.carId()).automatic()) {
            helper.fail("the Train came back from the level's save manual", LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    // ---- running unattended ----

    /** The two Locomotives and the Cargo car between them of the Train {@link #running} sets going. */
    private record Shuttle(LocomotiveEntity leading, CargoCarEntity cargo, LocomotiveEntity trailing) {
    }

    /**
     * The Mine with 100 iron in its provider and the Depot's requester asking for a stack, and between
     * them, heading east, the first car on the east leg, a fuelled Train of a Locomotive facing each way,
     * a Cargo car between them, switched to automatic by its menu.
     */
    private static Shuttle running(GameTestHelper helper) {
        Span last = line(helper);
        ServerPlayer player = near(helper);
        station(helper, MINE);
        station(helper, DEPOT);
        helper.getBlockEntity(MINE, StationBlockEntity.class).rename(near(helper, MINE), "Mine");
        helper.getBlockEntity(DEPOT, StationBlockEntity.class).rename(near(helper, DEPOT), "Depot");
        helper.setBlock(PROVIDER, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, HatchMode.PROVIDER));
        helper.setBlock(REQUESTER, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, HatchMode.REQUESTER));
        expectJoined(helper, PROVIDER, MINE);
        expectJoined(helper, REQUESTER, DEPOT);
        insert(record(helper, PROVIDER), new ItemStack(Items.IRON_INGOT, 100));
        record(helper, REQUESTER).ask(Items.IRON_INGOT);
        Locomotive east = Locomotive.burner();
        CargoCar wagon = CargoCar.create();
        Locomotive west = Locomotive.burner();
        Train train = Train.of(new Place(last, last.b(), 2.0), List.of(east, wagon))
                .coupled(LevelRails.of(helper.getLevel()).network(), Train.End.BACK, west, true);
        LocomotiveEntity leading = spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, east);
        CargoCarEntity cargo = spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, wagon);
        LocomotiveEntity trailing = spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, west);
        LocomotiveTests.fill(helper, leading, new ItemStack(Items.COAL, 4));
        LocomotiveTests.fill(helper, trailing, new ItemStack(Items.COAL, 4));
        leading.openCustomInventoryScreen(player);
        ((LocomotiveMenu) player.containerMenu).clickMenuButton(player, LocomotiveMenu.TOGGLE_AUTOMATIC);
        return new Shuttle(leading, cargo, trailing);
    }

    private static void unattended(GameTestHelper helper) {
        Shuttle shuttle = running(helper);
        LocomotiveEntity leading = shuttle.leading();
        CargoCarEntity cargo = shuttle.cargo();
        LocomotiveEntity trailing = shuttle.trailing();
        // Loading a single stack takes a tick, too short to wait on: the Train is seen leaving the Mine loaded.
        helper.startSequence()
                .thenWaitUntil(() -> expectStatus(helper, leading, "status.railworks.train.to_unload"))
                .thenExecute(() -> {
                    if (cargo.cargo().countItem(Items.IRON_INGOT) != 64) {
                        helper.fail("the Cargo car left the Mine with " + cargo.cargo().countItem(Items.IRON_INGOT)
                                + " iron, not the stack the Depot asks for", MINE);
                    }
                })
                .thenWaitUntil(() -> expectStatus(helper, leading, "status.railworks.train.waiting"))
                .thenExecute(() -> {
                    long delivered = held(record(helper, REQUESTER));
                    long left = held(record(helper, PROVIDER));
                    if (delivered != 64 || left != 36 || !cargo.cargo().isEmpty()) {
                        helper.fail("the run left the Depot with " + delivered + " iron and the Mine with " + left
                                + ", the car holding " + cargo.cargo().countItem(Items.IRON_INGOT), DEPOT);
                    }
                    if (leading.fuelItem().getCount() >= 4 && trailing.fuelItem().getCount() >= 4) {
                        helper.fail("the run burnt no coal from either Locomotive's slot", DEPOT);
                    }
                    // Unloaded, it waits by the Depot, its cars on the car spots.
                    if (Math.abs(cargo.getX() - (helper.absolutePos(REQUESTER).getX() + 1.0)) > 1.0) {
                        helper.fail("the Cargo car stands at x " + cargo.getX() + ", not by the requester", REQUESTER);
                    }
                })
                .thenExecute(() -> forceLoad(helper, false))
                .thenSucceed();
    }

    private static void brokenRequester(GameTestHelper helper) {
        Shuttle shuttle = running(helper);
        LocomotiveEntity leading = shuttle.leading();
        CargoCarEntity cargo = shuttle.cargo();
        helper.startSequence()
                .thenWaitUntil(() -> expectStatus(helper, leading, "status.railworks.train.to_unload"))
                .thenExecute(() -> helper.destroyBlock(REQUESTER))
                .thenWaitUntil(() -> expectStatus(helper, leading, "status.railworks.train.holding"))
                .thenExecute(() -> {
                    if (cargo.cargo().countItem(Items.IRON_INGOT) != 64) {
                        helper.fail("the Cargo car holds " + cargo.cargo().countItem(Items.IRON_INGOT)
                                + " iron, not the stack loaded for the broken requester", REQUESTER);
                    }
                })
                // Holding, it waits: nothing more is fetched from the Mine.
                .thenIdle(3 * 20)
                .thenExecute(() -> {
                    expectStatus(helper, leading, "status.railworks.train.holding");
                    if (held(record(helper, PROVIDER)) != 36) {
                        helper.fail("the Mine holds " + held(record(helper, PROVIDER)) + " iron, not the 36 left", PROVIDER);
                    }
                    helper.setBlock(REQUESTER, RailworksRegistries.HATCH.get().defaultBlockState()
                            .setValue(HatchBlock.MODE, HatchMode.REQUESTER));
                    record(helper, REQUESTER).ask(Items.IRON_INGOT);
                })
                .thenWaitUntil(() -> expectStatus(helper, leading, "status.railworks.train.waiting"))
                .thenExecute(() -> {
                    if (held(record(helper, REQUESTER)) != 64 || !cargo.cargo().isEmpty()
                            || held(record(helper, PROVIDER)) != 36) {
                        helper.fail("the leftover was not delivered: the requester holds " + held(record(helper, REQUESTER))
                                + ", the car " + cargo.cargo().countItem(Items.IRON_INGOT) + " and the Mine "
                                + held(record(helper, PROVIDER)), REQUESTER);
                    }
                })
                .thenExecute(() -> forceLoad(helper, false))
                .thenSucceed();
    }

    // ---- refuelling ----

    /**
     * The Depot alone, a provider beside its leading car's place holding a lava bucket and 300 coal,
     * and a Locomotive heading east, a Cargo car behind it, standing at the Depot with nothing in its
     * fuel slots. The Locomotive.
     */
    private static LocomotiveEntity atTheDepot(GameTestHelper helper) {
        Span last = line(helper);
        station(helper, DEPOT);
        BlockPos fuel = besideTheLeadingCar(helper, DEPOT);
        helper.setBlock(fuel, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, HatchMode.PROVIDER));
        insert(record(helper, fuel), new ItemStack(Items.LAVA_BUCKET));
        insert(record(helper, fuel), new ItemStack(Items.COAL, 300));
        Locomotive east = Locomotive.burner();
        CargoCar wagon = CargoCar.create();
        // Its middle half a block short of the line's end, where the Depot's leading car stands.
        Train train = Train.of(new Place(last, last.b(), 0.5), List.of(east, wagon));
        LocomotiveEntity locomotive = spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, east);
        spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, wagon);
        return locomotive;
    }

    private static void refuels(GameTestHelper helper) {
        LocomotiveEntity locomotive = atTheDepot(helper);
        BlockPos fuel = besideTheLeadingCar(helper, DEPOT);
        locomotive.train().setAutomatic(true);
        ItemStack full = new ItemStack(Items.COAL, 64);
        helper.startSequence()
                .thenWaitUntil(() -> LocomotiveTests.expectSlots(helper, locomotive, List.of(full, full, full, full)))
                .thenExecute(() -> {
                    long coal = record(helper, fuel).contents().getResource(0).is(Items.LAVA_BUCKET)
                            ? held(record(helper, fuel)) - 1 : -1;
                    if (coal != 300 - 4 * 64) {
                        helper.fail("the provider was left with " + coal + " coal beside its lava bucket, not 44", fuel);
                    }
                })
                .thenExecute(() -> forceLoad(helper, false))
                .thenSucceed();
    }

    private static void refuelsNoManualTrain(GameTestHelper helper) {
        LocomotiveEntity locomotive = atTheDepot(helper);
        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> LocomotiveTests.expectSlots(helper, locomotive,
                        List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)))
                .thenExecute(() -> forceLoad(helper, false))
                .thenSucceed();
    }

    /** A block beside the leading car's place of the Station at {@code station}, clear of the Station's own. */
    private static BlockPos besideTheLeadingCar(GameTestHelper helper, BlockPos station) {
        var network = LevelRails.of(helper.getLevel()).network();
        BlockPos at = helper.absolutePos(station);
        return LevelStations.of(helper.getLevel()).stations().all().stream()
                .filter(s -> s.x() == at.getX() && s.y() == at.getY() && s.z() == at.getZ())
                .flatMap(s -> Platform.of(network, s).stream())
                .flatMap(p -> p.besideBlocks(network).stream())
                .filter(b -> b.spot() == Platform.LEADING)
                // Back to the test's own positions: from its origin, as absolutePos counts them.
                .map(b -> new BlockPos(b.x(), b.y(), b.z()).subtract(helper.absolutePos(BlockPos.ZERO)))
                .filter(pos -> !pos.equals(station))
                .findFirst()
                .orElseThrow(() -> helper.assertionException(station, Component.literal("nothing beside its leading car's place")));
    }

    // ---- setup ----

    /**
     * The {@link #LINE}, the structure's roof cleared over it, its chunks loaded for the Train's entities
     * to tick: only the structure's are, and the line runs out past it. Its last Span, the east leg.
     */
    private static Span line(GameTestHelper helper) {
        forceLoad(helper, true);
        for (BlockPos pylon : LINE) {
            helper.setBlock(pylon, RailworksRegistries.PYLON.get().defaultBlockState());
        }
        for (int x = -1; x <= 13; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
            }
        }
        Span span = null;
        for (int i = 0; i + 1 < LINE.length; i++) {
            var one = PylonColumn.arm(helper.absolutePos(LINE[i]));
            var other = PylonColumn.arm(helper.absolutePos(LINE[i + 1]));
            span = Span.of(one, other, SpanRules.cost(one, other));
            LevelRails.of(helper.getLevel()).network().add(span);
        }
        return span;
    }

    /** Forces the chunks under the {@link #LINE} loaded, or lets them go. */
    private static void forceLoad(GameTestHelper helper, boolean forced) {
        BlockPos west = helper.absolutePos(new BlockPos(-1, 1, 2));
        BlockPos east = helper.absolutePos(new BlockPos(13, 1, 6));
        for (int cx = Math.min(west.getX(), east.getX()) >> 4; cx <= Math.max(west.getX(), east.getX()) >> 4; cx++) {
            for (int cz = Math.min(west.getZ(), east.getZ()) >> 4; cz <= Math.max(west.getZ(), east.getZ()) >> 4; cz++) {
                helper.getLevel().setChunkForced(cx, cz, forced);
            }
        }
    }

    /** Places a Station at {@code pos}, which must go, by a player beside it. */
    private static void station(GameTestHelper helper, BlockPos pos) {
        // Past the structure's floor there is nothing to click on.
        helper.setBlock(pos.below(), Blocks.STONE);
        ServerPlayer player = near(helper, pos.north(2));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailworksRegistries.STATION_ITEM.get()));
        if (!StationTests.place(helper, player, pos).consumesAction()) {
            var refusal = StationPlacement.refusal(LevelRails.of(helper.getLevel()).network(),
                    LevelStations.of(helper.getLevel()).stations().all(), helper.absolutePos(pos));
            throw helper.assertionException(pos, Component.literal("the Station was refused: " + refusal));
        }
    }

    /** A survival player beside {@link LocomotiveTests}' Span, within the menu's reach of a car on it. */
    private static ServerPlayer near(GameTestHelper helper) {
        return near(helper, LocomotiveTests.STAND);
    }

    private static ServerPlayer near(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(pos);
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        return player;
    }

    /** {@code train} on the Rail, if it is not yet, and an entity of {@code type} viewing {@code car} of it. */
    private static <T extends CarEntity> T spawn(GameTestHelper helper, EntityType<T> type, Train train, Car car) {
        LevelRails rails = LevelRails.of(helper.getLevel());
        if (rails.trains().of(car.id()) == null) {
            rails.add(train);
        }
        T view = type.create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        view.setCar(train, car);
        helper.getLevel().addFreshEntity(view);
        return view;
    }

    /** The hatch's record in the level's hatches, which its block puts there if it has not yet as it loads. */
    private static Hatches.Hatch record(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, HatchBlockEntity.class).hatch()
                .orElseThrow(() -> helper.assertionException(pos, Component.literal("no hatch in the level's hatches")));
    }

    private static void insert(Hatches.Hatch hatch, ItemStack stack) {
        try (var transaction = Transaction.openRoot()) {
            ResourceHandlerUtil.insertStacking(hatch.contents(), ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
        }
    }

    private static long held(Hatches.Hatch hatch) {
        long held = 0;
        for (int slot = 0; slot < hatch.contents().size(); slot++) {
            held += hatch.contents().getAmountAsLong(slot);
        }
        return held;
    }

    /** The hatch at {@code pos} joins the Station at {@code station}, at its first car spot. */
    private static void expectJoined(GameTestHelper helper, BlockPos pos, BlockPos station) {
        Optional<Platform.Joined> joined = PlatformsNear.joined(helper.getLevel(), helper.absolutePos(pos));
        BlockPos at = helper.absolutePos(station);
        if (joined.isEmpty() || joined.get().spot() != 0 || joined.get().station().x() != at.getX()
                || joined.get().station().z() != at.getZ()) {
            var network = LevelRails.of(helper.getLevel()).network();
            var beside = LevelStations.of(helper.getLevel()).stations().all().stream()
                    .filter(s -> s.x() == at.getX() && s.z() == at.getZ())
                    .flatMap(s -> Platform.of(network, s).stream())
                    .map(p -> p.besideBlocks(network).stream().map(b -> helper.relativePos(new BlockPos(b.x(), b.y(), b.z()))).toList())
                    .toList();
            helper.fail("the hatch joins " + joined + ", not the Station at " + at + " at its first car spot, beside " + beside, pos);
        }
    }

    /** Fails unless the Locomotive's status line, as a client reads it, is the line {@code key} says. */
    private static void expectStatus(GameTestHelper helper, LocomotiveEntity locomotive, String key) {
        Component status = locomotive.statusSeen();
        String said = status.getContents() instanceof TranslatableContents contents ? contents.getKey() : status.getString();
        if (!said.equals(key)) {
            helper.fail("the status line says " + said + ", not " + key + ": " + locomotive.train(), LocomotiveTests.STAND);
        }
    }
}
