// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.FluidCarEntity;
import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchMode;
import io.github._5thlayer.railworks.Hatches;
import io.github._5thlayer.railworks.LevelCarRecords;
import io.github._5thlayer.railworks.LevelHatches;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.FluidCar;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Logistics;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Stations;
import io.github._5thlayer.railworks.rail.Throttle;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Trains far away (#6, ADR 0004 amended): every Train ticks from the level's tick, in view or not,
 * and the level shows a car only where its place ticks entities. A line runs from the structure east
 * past the chunks the runner loads, out to a Station 240 blocks off, which the tests bring into view
 * and out of it with a chunk ticket of their own ({@link RailworksGameTests#IN_VIEW}). An automatic
 * Train runs its job out there as an abstract Train and its views come back where it stands; a
 * rider is carried out of view seated, a mob riding a car whose view goes rides on in its record, a
 * Train whose Span is cut out of view comes off the Rail as its chunk loads, and a Train coasting out
 * of view stops where it would have in view. How the level decides what to show is {@code ViewsTest}'s.
 * These tests share the {@code far_} prefix, but for a Fluid car's (#10), whose names share
 * {@code fluid_} with {@code FluidTests}: a Train carrying fluid out of view, and a Fluid car's tank
 * kept while its view is gone; and for a mob Rider seated again in the bay beside a Driver, which
 * shares {@code loco_seats_} with {@code LocomotiveSeatTests}.
 */
final class FarAwayTests {

    /** The line runs along z 3 from x -1, Span by Span of {@link #SPAN} blocks, to x {@link #FAR}. */
    private static final int WEST = -1;
    private static final int SPAN = 30;
    private static final int FAR = 239;
    private static final int Z = 3;
    /** North of the line's west end, serving Trains heading west: its first car spot beside x 5 to 9. */
    private static final BlockPos MINE = new BlockPos(0, 3, 1);
    /** South of the line's far end, serving Trains heading east: its first car spot beside x 230 to 234. */
    private static final BlockPos DEPOT = new BlockPos(FAR, 3, 5);
    private static final BlockPos PROVIDER = new BlockPos(7, 3, 1);
    private static final BlockPos REQUESTER = new BlockPos(FAR - 7, 3, 5);
    /** How far east of the structure the line's near stretch runs, which the shuttle stands on as it starts. */
    private static final int NEAR = 22;
    /** On the far stretch, out of every chunk the runner loads: where a Train stands still for a test. */
    private static final BlockPos YARD = new BlockPos(164, 2, Z);
    /** How far around a chunk a ticket of {@link RailworksGameTests#IN_VIEW} simulates: that chunk and those beside it. */
    private static final int AROUND = 3;
    /** A bucket, in millibuckets. */
    private static final int BUCKET = (int) Logistics.MILLIBUCKETS_PER_BUCKET;

    /** The cars whose entity was found in a saved chunk as it loaded, which no car's view ever should be. */
    private static final Set<UUID> FROM_CHUNKS = ConcurrentHashMap.newKeySet();

    private FarAwayTests() {
    }

    /** Starts listening: called when game tests register, which a production game never does. */
    static void listen() {
        NeoForge.EVENT_BUS.addListener(FarAwayTests::joined);
    }

    private static void joined(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity() instanceof CarEntity car && car.carId() != null) {
            FROM_CHUNKS.add(car.carId());
        }
    }

    static void register(RailworksGameTests.Registrar tests) {
        // Each runs out past its structure, along the same stretch as the others.
        tests.testOnItsOwn("far_an_automatic_train_runs_its_job_out_of_view_and_is_seen_again_where_it_stands", 3_000,
                FarAwayTests::runsItsJob);
        tests.testOnItsOwn("far_a_rider_is_carried_out_of_view_seated", 3_000, FarAwayTests::carriesRider);
        tests.testOnItsOwn("far_a_mob_riding_a_car_whose_view_goes_comes_back_seated", 1_200, FarAwayTests::mobRidesOn);
        tests.testOnItsOwn("loco_seats_a_mob_rider_whose_view_goes_comes_back_in_the_bay", 1_200,
                FarAwayTests::mobBackInTheBay);
        tests.testOnItsOwn("far_a_train_whose_span_is_cut_out_of_view_comes_off_the_rail_as_its_chunk_loads", 1_200,
                FarAwayTests::cutOutOfView);
        tests.testOnItsOwn("far_a_manual_train_coasts_out_of_view_and_stops_where_it_would_in_view", 2_000,
                FarAwayTests::coasts);
        tests.testOnItsOwn("fluid_an_abstract_train_carries_fluid_out_of_view_and_is_seen_again_where_it_stands", 3_000,
                FarAwayTests::carriesFluid);
        tests.testOnItsOwn("fluid_a_fluid_cars_tank_is_kept_while_its_view_is_gone", 1_200, FarAwayTests::tankKept);
    }

    // ---- running a job ----

    /** The shuttle's Train: a Locomotive facing west first, a Cargo car, and a Locomotive facing east. */
    private record Shuttle(Train train, LocomotiveEntity west, CargoCarEntity wagon, LocomotiveEntity east) {
    }

    /**
     * The Mine's provider holding 100 iron and the Depot's requester asking for a stack, both Stations
     * on the line, and by the Mine, heading west, a fuelled automatic Train of a Locomotive facing each
     * way and a Cargo car between them.
     */
    private static Shuttle shuttle(GameTestHelper helper) {
        RailNetwork network = line(helper);
        LogisticsTests.station(helper, MINE);
        LogisticsTests.station(helper, DEPOT);
        helper.setBlock(PROVIDER, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, HatchMode.PROVIDER));
        helper.setBlock(REQUESTER, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, HatchMode.REQUESTER));
        LogisticsTests.expectJoined(helper, PROVIDER, MINE);
        LogisticsTests.expectJoined(helper, REQUESTER, DEPOT);
        LogisticsTests.insert(LogisticsTests.record(helper, PROVIDER), new ItemStack(Items.IRON_INGOT, 100));
        LogisticsTests.record(helper, REQUESTER).ask(Items.IRON_INGOT);
        Span first = network.get(arm(helper, WEST), arm(helper, WEST + SPAN));
        Locomotive west = Locomotive.burner();
        CargoCar wagon = CargoCar.create();
        Locomotive east = Locomotive.burner();
        Train train = Train.of(new Place(first, first.a(), 10.0), List.of(west, wagon))
                .coupled(network, Train.End.BACK, east, true);
        LocomotiveEntity westView = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, west);
        CargoCarEntity wagonView = LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, wagon);
        LocomotiveEntity eastView = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, east);
        LocomotiveTests.fill(helper, westView, new ItemStack(Items.COAL, 4));
        LocomotiveTests.fill(helper, eastView, new ItemStack(Items.COAL, 4));
        train.setAutomatic(true);
        return new Shuttle(train, westView, wagonView, eastView);
    }

    /**
     * The shuttle loads at the Mine, runs out of view to the Depot, unloads there with no chunk of it
     * loaded and waits: every tick the Train moves once, each car is shown where its place ticks and
     * only there, at most once. The Depot's chunks brought into view, its three cars are seen where it
     * stands; let go of, unloaded and loaded again, they are seen again, and no car was saved with them.
     */
    private static void runsItsJob(GameTestHelper helper) {
        Shuttle shuttle = shuttle(helper);
        Train train = shuttle.train();
        Watch watch = watch(helper, train, null);
        UUID wagon = shuttle.wagon().carId();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    long delivered = LogisticsTests.held(hatch(helper, REQUESTER));
                    helper.assertTrue(delivered == 64 && train.atRest(), "the Depot holds " + delivered + " iron, the Train "
                            + LevelRails.of(helper.getLevel()).status(helper.getLevel(), train) + " " + train);
                })
                .thenExecute(() -> {
                    helper.assertTrue(watch.abstracted && watch.split,
                            "the Train was never wholly out of view, or never half in it: abstract " + watch.abstracted + ", split " + watch.split);
                    long left = LogisticsTests.held(hatch(helper, PROVIDER));
                    int carried = LevelCarRecords.of(helper.getLevel()).of(wagon).map(record -> record.countItem(Items.IRON_INGOT)).orElse(-1);
                    helper.assertTrue(left == 36 && carried == 0,
                            "the run out of view left the Mine with " + left + " iron and the car with " + carried);
                    expectOutOfView(helper, train);
                    inView(helper, DEPOT, true);
                })
                .thenWaitUntil(() -> expectInView(helper, train))
                .thenExecute(() -> inView(helper, DEPOT, false))
                .thenWaitUntil(() -> {
                    expectOutOfView(helper, train);
                    ChunkPos depot = ChunkPos.containing(helper.absolutePos(DEPOT));
                    helper.assertFalse(helper.getLevel().getChunkSource().hasChunk(depot.x(), depot.z()), "the Depot's chunk is still loaded");
                })
                .thenExecute(() -> inView(helper, DEPOT, true))
                .thenWaitUntil(() -> expectInView(helper, train))
                .thenExecute(() -> {
                    for (Car car : train.cars()) {
                        helper.assertFalse(FROM_CHUNKS.contains(car.id()), "a car's entity was saved with its chunk: " + car);
                    }
                    inView(helper, DEPOT, false);
                    clearAway(helper, train);
                })
                .thenSucceed();
    }

    // ---- fluid ----

    /**
     * The shuttle with a Fluid car in the Cargo car's place, between Fluid hatches: the Mine's provider
     * holding 20 buckets of water and the Depot's requester asking for 8. It loads at the Mine and runs
     * out of view to the Depot, unloads there with no chunk of it loaded, and its cars are seen again
     * where it stands once the Depot comes into view.
     */
    private static void carriesFluid(GameTestHelper helper) {
        RailNetwork network = line(helper);
        LogisticsTests.station(helper, MINE);
        LogisticsTests.station(helper, DEPOT);
        helper.setBlock(PROVIDER, FluidTests.fluidHatch(HatchMode.PROVIDER));
        helper.setBlock(REQUESTER, FluidTests.fluidHatch(HatchMode.REQUESTER));
        LogisticsTests.expectJoined(helper, PROVIDER, MINE);
        LogisticsTests.expectJoined(helper, REQUESTER, DEPOT);
        try (var transaction = Transaction.openRoot()) {
            LogisticsTests.record(helper, PROVIDER).tank().insert(FluidResource.of(Fluids.WATER), 20 * BUCKET, transaction);
            transaction.commit();
        }
        LogisticsTests.record(helper, REQUESTER).ask(Fluids.WATER);
        Span first = network.get(arm(helper, WEST), arm(helper, WEST + SPAN));
        Locomotive west = Locomotive.burner();
        FluidCar fluidCar = FluidCar.create();
        Locomotive east = Locomotive.burner();
        Train train = Train.of(new Place(first, first.a(), 10.0), List.of(west, fluidCar))
                .coupled(network, Train.End.BACK, east, true);
        LocomotiveEntity westView = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, west);
        LogisticsTests.spawn(helper, RailworksRegistries.FLUID_CAR_ENTITY.get(), train, fluidCar);
        LocomotiveEntity eastView = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, east);
        LocomotiveTests.fill(helper, westView, new ItemStack(Items.COAL, 4));
        LocomotiveTests.fill(helper, eastView, new ItemStack(Items.COAL, 4));
        train.setAutomatic(true);
        Watch watch = watch(helper, train, null);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    int delivered = hatch(helper, REQUESTER).tank().amount();
                    helper.assertTrue(delivered == 8 * BUCKET && train.atRest(), "the Depot holds " + delivered + " of water, the Train "
                            + LevelRails.of(helper.getLevel()).status(helper.getLevel(), train) + " " + train);
                })
                .thenExecute(() -> {
                    helper.assertTrue(watch.abstracted, "the Train was never wholly out of view");
                    int left = hatch(helper, PROVIDER).tank().amount();
                    int carried = LevelCarRecords.of(helper.getLevel()).of(fluidCar.id()).map(record -> record.tank().amount()).orElse(-1);
                    helper.assertTrue(left == 12 * BUCKET && carried == 0,
                            "the run out of view left the Mine with " + left + " of water and the car with " + carried);
                    expectOutOfView(helper, train);
                    inView(helper, DEPOT, true);
                })
                .thenWaitUntil(() -> expectInView(helper, train))
                .thenExecute(() -> {
                    inView(helper, DEPOT, false);
                    clearAway(helper, train);
                })
                .thenSucceed();
    }

    /**
     * A Locomotive and a Fluid car stand on the far stretch; brought into view, the car is filled with
     * five buckets of lava; let go of, its view goes and its record keeps the lava; brought back, its
     * view shows the same tank.
     */
    private static void tankKept(GameTestHelper helper) {
        RailNetwork network = line(helper);
        Locomotive locomotive = Locomotive.burner();
        FluidCar fluidCar = FluidCar.create();
        Train train = standing(helper, network, locomotive, fluidCar);
        inView(helper, YARD, true);
        helper.startSequence()
                .thenWaitUntil(() -> expectInView(helper, train))
                .thenExecute(() -> {
                    FluidCarEntity view = (FluidCarEntity) LevelRails.of(helper.getLevel()).view(fluidCar);
                    try (var transaction = Transaction.openRoot()) {
                        view.tank().insert(FluidResource.of(Fluids.LAVA), 5 * BUCKET, transaction);
                        transaction.commit();
                    }
                    inView(helper, YARD, false);
                })
                .thenWaitUntil(() -> {
                    expectOutOfView(helper, train);
                    int kept = LevelCarRecords.of(helper.getLevel()).of(fluidCar.id()).map(record -> record.tank().amount()).orElse(-1);
                    helper.assertTrue(kept == 5 * BUCKET, "the car's record keeps " + kept + " of lava out of view");
                })
                .thenExecute(() -> inView(helper, YARD, true))
                .thenWaitUntil(() -> {
                    expectInView(helper, train);
                    var tank = ((FluidCarEntity) LevelRails.of(helper.getLevel()).view(fluidCar)).tank();
                    helper.assertTrue(tank != null && tank.fluid().is(Fluids.LAVA) && tank.amount() == 5 * BUCKET,
                            "the car's view came back holding " + (tank == null ? "nothing" : tank.held().getAmount() + " of " + tank.fluid()));
                })
                .thenExecute(() -> {
                    inView(helper, YARD, false);
                    clearAway(helper, train);
                })
                .thenSucceed();
    }

    // ---- riders ----

    /**
     * A player rides the shuttle's first car on its run out to the Depot: its view is never taken away,
     * though its place stops ticking, and the rider stays in it, moved with it, until they get off there,
     * when it goes.
     */
    private static void carriesRider(GameTestHelper helper) {
        Shuttle shuttle = shuttle(helper);
        Train train = shuttle.train();
        LocomotiveEntity seat = shuttle.west();
        ServerPlayer rider = LocomotiveTests.board(helper, seat);
        Watch watch = watch(helper, train, seat);
        helper.onEachTick(() -> {
            if (rider.getVehicle() == null) {
                return;
            }
            helper.assertTrue(!seat.isRemoved() && rider.getVehicle() == seat, "the rider's car went from under them");
            // Seated in the cab, at the car's front: within half its length of its middle.
            helper.assertTrue(rider.position().distanceTo(seat.position()) < Locomotive.LENGTH / 2.0,
                    "the rider is at " + rider.position() + ", left behind by their car at " + seat.position());
        });
        helper.startSequence()
                .thenWaitUntil(() -> {
                    long delivered = LogisticsTests.held(hatch(helper, REQUESTER));
                    helper.assertTrue(delivered == 64 && train.atRest(), "the Depot holds " + delivered + " iron, the Train "
                            + LevelRails.of(helper.getLevel()).status(helper.getLevel(), train) + " " + train);
                })
                .thenExecute(() -> {
                    helper.assertTrue(watch.riddenOut, "the ridden car never left the chunks that tick");
                    rider.stopRiding();
                })
                .thenWaitUntil(() -> helper.assertTrue(seat.isRemoved() && LevelRails.of(helper.getLevel()).view(train.cars().getFirst()) == null,
                        "the car's view stayed out of view once its rider got off"))
                .thenExecute(() -> {
                    helper.getLevel().getServer().getPlayerList().remove(rider);
                    clearAway(helper, train);
                })
                .thenSucceed();
    }

    /**
     * A pig riding a Locomotive standing on the far stretch, its chunk in view: let go of, the view goes
     * and the pig with it, kept in the car's record; brought back, the view comes back with the same pig
     * seated in it.
     */
    private static void mobRidesOn(GameTestHelper helper) {
        RailNetwork network = line(helper);
        Locomotive car = Locomotive.burner();
        Train train = standing(helper, network, car);
        inView(helper, YARD, true);
        UUID[] pig = new UUID[1];
        helper.startSequence()
                // Never placed, it is shown once its place ticks.
                .thenWaitUntil(() -> expectInView(helper, train))
                .thenExecute(() -> {
                    CarEntity view = LevelRails.of(helper.getLevel()).view(car);
                    Pig mob = EntityType.PIG.create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
                    mob.snapTo(view.position());
                    helper.getLevel().addFreshEntity(mob);
                    helper.assertTrue(mob.startRiding(view, true, false), "the pig could not get in");
                    pig[0] = mob.getUUID();
                    // Kept from the moment it gets in, so a server stopping without a save brings it back once.
                    helper.assertTrue(riderKept(helper, car), "the car's record does not keep the pig that got in");
                    inView(helper, YARD, false);
                })
                .thenWaitUntil(() -> {
                    expectOutOfView(helper, train);
                    helper.assertTrue(helper.getLevel().getEntity(pig[0]) == null, "the pig stayed in the world without its car");
                    helper.assertTrue(riderKept(helper, car), "the car's record keeps no rider");
                })
                .thenExecute(() -> inView(helper, YARD, true))
                .thenWaitUntil(() -> {
                    CarEntity view = LevelRails.of(helper.getLevel()).view(car);
                    helper.assertTrue(view != null && view.getFirstPassenger() instanceof Pig back && back.getUUID().equals(pig[0]),
                            "the pig is not seated in the car's view: " + (view == null ? "no view" : view.getPassengers()));
                    helper.assertTrue(riderKept(helper, car), "the car's record lost the pig seated again");
                })
                .thenExecute(() -> {
                    LevelRails.of(helper.getLevel()).view(car).getFirstPassenger().discard();
                    helper.assertFalse(riderKept(helper, car), "the car's record still keeps the pig gone");
                    inView(helper, YARD, false);
                    clearAway(helper, train);
                })
                .thenSucceed();
    }

    /**
     * A Locomotive standing on the far stretch, its chunk in view, carries a Driver in its cab and a pig
     * that got in after them in its bay. The Driver gets off before the view goes, since a view a
     * player rides is never taken away; the pig is kept in the car's record and, brought back, stands
     * in the bay again, and a player boarding takes the cab.
     */
    private static void mobBackInTheBay(GameTestHelper helper) {
        RailNetwork network = line(helper);
        Locomotive car = Locomotive.burner();
        Train train = standing(helper, network, car);
        inView(helper, YARD, true);
        UUID[] pig = new UUID[1];
        ServerPlayer[] players = new ServerPlayer[2];
        helper.startSequence()
                .thenWaitUntil(() -> expectInView(helper, train))
                .thenExecute(() -> {
                    LocomotiveEntity view = (LocomotiveEntity) LevelRails.of(helper.getLevel()).view(car);
                    players[0] = LocomotiveTests.board(helper, view);
                    Pig mob = EntityType.PIG.create(helper.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
                    mob.snapTo(view.position());
                    helper.getLevel().addFreshEntity(mob);
                    helper.assertTrue(mob.startRiding(view), "the pig could not get in behind the Driver");
                    pig[0] = mob.getUUID();
                    expectInBay(helper, view, mob);
                    players[0].setShiftKeyDown(true);
                    players[0].rideTick();
                    helper.assertTrue(players[0].getVehicle() == null && mob.getVehicle() == view, "the Driver did not get off alone");
                    inView(helper, YARD, false);
                })
                .thenWaitUntil(() -> {
                    expectOutOfView(helper, train);
                    helper.assertTrue(helper.getLevel().getEntity(pig[0]) == null, "the pig stayed in the world without its car");
                    helper.assertTrue(riderKept(helper, car), "the car's record keeps no rider");
                })
                .thenExecute(() -> inView(helper, YARD, true))
                .thenWaitUntil(() -> {
                    CarEntity view = LevelRails.of(helper.getLevel()).view(car);
                    helper.assertTrue(view != null && view.getPassengers().size() == 1
                                    && view.getFirstPassenger() instanceof Pig back && back.getUUID().equals(pig[0]),
                            "the pig is not seated in the car's view: " + (view == null ? "no view" : view.getPassengers()));
                })
                .thenExecute(() -> {
                    LocomotiveEntity view = (LocomotiveEntity) LevelRails.of(helper.getLevel()).view(car);
                    Entity back = view.getFirstPassenger();
                    expectInBay(helper, view, back);
                    players[1] = LocomotiveTests.board(helper, view);
                    players[1].rideTick();
                    // A seated player's feet 0.6 over the floor's underside, at the cab's seat along the car.
                    Vec3 cab = view.position().add(new Vec3(0.0, 0.6, CarBody.CAB_SEAT.z()).yRot(-view.getYRot() * Mth.DEG_TO_RAD));
                    helper.assertTrue(players[1].position().distanceTo(cab) < 1e-3,
                            "the player boarding after the pig came back is at " + players[1].position().subtract(view.position())
                                    + " from the car's middle, not in the cab");
                    players[1].stopRiding();
                    back.discard();
                    inView(helper, YARD, false);
                    clearAway(helper, train);
                })
                .thenSucceed();
    }

    /** {@code mob} stands in {@code view}'s bay, {@link CarBody#BAY_SPOT} from its middle, facing the car's way. */
    private static void expectInBay(GameTestHelper helper, CarEntity view, Entity mob) {
        view.positionRider(mob);
        Vec3 spot = new Vec3(CarBody.BAY_SPOT.x(), CarBody.BAY_SPOT.y(), CarBody.BAY_SPOT.z());
        Vec3 bay = view.position().add(spot.yRot(-view.getYRot() * Mth.DEG_TO_RAD));
        helper.assertTrue(mob.position().distanceTo(bay) < 1e-3 && Math.abs(Mth.wrapDegrees(mob.getYRot() - view.getYRot())) < 1e-3,
                "the pig is at " + mob.position().subtract(view.position()) + " from the car's middle facing " + mob.getYRot()
                        + ", not in the bay facing the car's " + view.getYRot());
    }

    /** Whether {@code car}'s record keeps a mob riding it. */
    private static boolean riderKept(GameTestHelper helper, Car car) {
        return LevelCarRecords.of(helper.getLevel()).of(car.id()).map(record -> record.rider() != null).orElse(false);
    }

    // ---- off the Rail ----

    /**
     * A Locomotive and a Cargo car holding iron stand on the far stretch out of view when their Span is
     * cut: their Train is gone, and they stand where they were, dropping nothing, until their chunk is
     * brought into view, when each comes off the Rail there, dropping itself and the iron.
     */
    private static void cutOutOfView(GameTestHelper helper) {
        RailNetwork network = line(helper);
        Locomotive locomotive = Locomotive.burner();
        CargoCar wagon = CargoCar.create();
        Train train = standing(helper, network, locomotive, wagon);
        inView(helper, YARD, true);
        Vec3[] at = new Vec3[1];
        helper.startSequence()
                .thenWaitUntil(() -> expectInView(helper, train))
                .thenExecute(() -> {
                    ((CargoCarEntity) LevelRails.of(helper.getLevel()).view(wagon)).cargo().setItem(0, new ItemStack(Items.IRON_INGOT, 10));
                    inView(helper, YARD, false);
                })
                .thenWaitUntil(() -> expectOutOfView(helper, train))
                .thenExecute(() -> {
                    at[0] = LevelRails.of(helper.getLevel()).standing(wagon.id());
                    Span under = train.head().span();
                    network.remove(under.a(), under.b());
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    LevelRails rails = LevelRails.of(helper.getLevel());
                    helper.assertTrue(rails.trains().of(wagon.id()) == null, "the Train stayed on the cut Span");
                    helper.assertTrue(rails.fallen().containsAll(List.of(locomotive.id(), wagon.id())),
                            "the cars out of view are not standing off the Rail: " + rails.fallen());
                    helper.assertTrue(LevelCarRecords.of(helper.getLevel()).of(wagon.id()).isPresent(), "the Cargo car's record went before it came off");
                    inView(helper, YARD, true);
                })
                .thenWaitUntil(() -> {
                    List<UUID> fallen = LevelRails.of(helper.getLevel()).fallen();
                    helper.assertTrue(!fallen.contains(locomotive.id()) && !fallen.contains(wagon.id()),
                            "the cars did not come off the Rail as their chunk loaded");
                })
                .thenExecute(() -> {
                    List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[0]).inflate(8.0));
                    List<ItemStack> dropped = drops.stream().map(ItemEntity::getItem).toList();
                    helper.assertTrue(dropped.stream().anyMatch(s -> s.is(RailworksRegistries.LOCOMOTIVE.get()))
                                    && dropped.stream().anyMatch(s -> s.is(RailworksRegistries.CARGO_CAR.get()))
                                    && dropped.stream().filter(s -> s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum() == 10,
                            "the cars coming off the Rail dropped " + dropped);
                    helper.assertTrue(LevelCarRecords.of(helper.getLevel()).of(wagon.id()).isEmpty(), "the Cargo car's record stayed once it came off");
                    drops.forEach(Entity::discard);
                    inView(helper, YARD, false);
                    clearAway(helper);
                })
                .thenSucceed();
    }

    // ---- driving by hand ----

    /**
     * A driver runs a Locomotive east from the structure and gets off: nobody at its keys, it coasts on
     * out of view and stops exactly where a copy of it, coasting on the bare Rail, stops.
     */
    private static void coasts(GameTestHelper helper) {
        RailNetwork network = line(helper);
        Span first = network.get(arm(helper, WEST), arm(helper, WEST + SPAN));
        Locomotive car = Locomotive.burner();
        Train train = Train.of(new Place(first, first.b(), 26.0), car);
        LocomotiveEntity view = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, car);
        LocomotiveTests.fill(helper, view, new ItemStack(Items.COAL, 4));
        ServerPlayer driver = LocomotiveTests.board(helper, view);
        LocomotiveTests.press(driver, true, false);
        Train[] copy = new Train[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(train.speed() >= 0.3, "the Locomotive runs at only " + train.speed()))
                .thenExecute(() -> {
                    LocomotiveTests.press(driver, false, false);
                    driver.stopRiding();
                    copy[0] = copied(train);
                    for (int tick = 0; tick < 100_000 && !copy[0].atRest(); tick++) {
                        copy[0].tick(network, Throttle.NONE, locomotive -> 0L);
                    }
                    helper.getLevel().getServer().getPlayerList().remove(driver);
                })
                .thenWaitUntil(() -> helper.assertTrue(train.atRest(), "the Locomotive still coasts at " + train.speed()))
                .thenExecute(() -> {
                    expectOutOfView(helper, train);
                    helper.assertTrue(train.head().span().equals(copy[0].head().span()) && train.head().front().equals(copy[0].head().front())
                                    && Math.abs(train.head().toFront() - copy[0].head().toFront()) < 1e-9,
                            "out of view it stopped at " + train.head() + ", not where it would have in view, " + copy[0].head());
                    clearAway(helper, train);
                })
                .thenSucceed();
    }

    /** {@code train} as its save would bring it back. */
    private static Train copied(Train train) {
        Codec<Train> codec = Train.CODEC;
        return codec.parse(NbtOps.INSTANCE, codec.encodeStart(NbtOps.INSTANCE, train).getOrThrow()).getOrThrow();
    }

    // ---- what each tick shows ----

    /** What {@link #watch} saw of a Train over the ticks. */
    private static final class Watch {
        /** Whether it was ever wholly out of view, and ever part in and part out. */
        boolean abstracted;
        boolean split;
        /** Whether the ridden car was ever where its place did not tick. */
        boolean riddenOut;
        @Nullable Vec3 headWas;
    }

    /**
     * Checks {@code train} after every tick: each car shown where its place ticks, but {@code ridden}
     * wherever, and nowhere else, at most one entity a car, each view where the Train puts its car, and
     * the Train moved at most once, as far as its speed, or a settling step at rest.
     */
    private static Watch watch(GameTestHelper helper, Train train, @Nullable CarEntity ridden) {
        Watch watch = new Watch();
        helper.onEachTick(() -> {
            ServerLevel level = helper.getLevel();
            LevelRails rails = LevelRails.of(level);
            if (rails.trains().of(train.cars().getFirst().id()) != train) {
                return;
            }
            int shown = 0;
            for (Car car : train.cars()) {
                Vec3 at = rails.standing(car.id());
                if (at == null) {
                    return;
                }
                CarEntity view = rails.view(car);
                long entities = entitiesOf(level, car.id());
                helper.assertTrue(entities == (view == null ? 0 : 1), "the car " + car + " has " + entities + " entities, its view " + view);
                boolean isRidden = ridden != null && car.id().equals(ridden.carId()) && !ridden.getPassengers().isEmpty();
                if (view != null) {
                    shown++;
                    helper.assertTrue(view.position().distanceTo(at) < 1e-6, "the view is at " + view.position() + ", its car at " + at);
                    helper.assertTrue(ticks(level, at) || isRidden, "a view stands where its place does not tick, at " + at);
                    if (isRidden && !ticks(level, at)) {
                        watch.riddenOut = true;
                    }
                } else {
                    helper.assertFalse(ticks(level, at), "no view stands where the car's place ticks, at " + at);
                }
            }
            if (shown == 0) {
                watch.abstracted = true;
            } else if (shown < train.cars().size()) {
                watch.split = true;
            }
            Vec3 head = rails.standing(train.cars().getFirst().id());
            if (watch.headWas != null) {
                double moved = head.distanceTo(watch.headWas);
                helper.assertTrue(moved <= Math.abs(train.speed()) + Stations.SETTLE_STEP + 1e-6,
                        "the Train moved " + moved + " in a tick at a speed of " + train.speed());
            }
            watch.headWas = head;
        });
        return watch;
    }

    /** Fails unless every car of {@code train} is shown, once, where the Train puts it. */
    private static void expectInView(GameTestHelper helper, Train train) {
        LevelRails rails = LevelRails.of(helper.getLevel());
        for (Car car : train.cars()) {
            CarEntity view = rails.view(car);
            Vec3 at = rails.standing(car.id());
            helper.assertTrue(view != null && at != null && view.position().distanceTo(at) < 1e-6,
                    "the car " + car + " is not shown where it stands, " + at + ", its place ticking " + (at != null && ticks(helper.getLevel(), at))
                            + ": " + view);
            helper.assertTrue(entitiesOf(helper.getLevel(), car.id()) == 1, "the car " + car + " is shown more than once");
        }
    }

    /** Fails unless no car of {@code train} is shown, by any entity, though the Train holds them all. */
    private static void expectOutOfView(GameTestHelper helper, Train train) {
        LevelRails rails = LevelRails.of(helper.getLevel());
        for (Car car : train.cars()) {
            helper.assertTrue(rails.trains().of(car.id()) == train, "the Train no longer holds " + car);
            helper.assertTrue(entitiesOf(helper.getLevel(), car.id()) == 0, "the car " + car + " is still shown, out of view");
        }
    }

    private static long entitiesOf(ServerLevel level, UUID car) {
        long count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof CarEntity view && !view.isRemoved() && car.equals(view.carId())) {
                count++;
            }
        }
        return count;
    }

    /** Whether {@code at} ticks entities, its chunk's saved ones loaded: where the level shows a car. */
    private static boolean ticks(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        return level.isPositionEntityTicking(pos) && level.areEntitiesActuallyLoadedAndTicking(ChunkPos.containing(pos));
    }

    // ---- setup ----

    /**
     * The line, its Spans strung with no Pylons out past the structure, where nothing loads, and its
     * near stretch loaded: failing if the far stretch is in view before any test brings it there.
     */
    private static RailNetwork line(GameTestHelper helper) {
        forceLoad(helper, true);
        RailNetwork network = LevelRails.of(helper.getLevel()).network();
        for (int x = WEST; x < FAR; x += SPAN) {
            Arm one = arm(helper, x);
            Arm other = arm(helper, x + SPAN);
            network.add(Span.of(one, other, SpanRules.cost(one, other)));
        }
        for (int x = WEST; x <= NEAR; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 4, z), net.minecraft.world.level.block.Blocks.AIR);
            }
        }
        for (BlockPos far : List.of(YARD, DEPOT)) {
            if (ticks(helper.getLevel(), Vec3.atCenterOf(helper.absolutePos(far)))) {
                throw helper.assertionException(far, Component.literal("the far stretch is in view before the test brings it there"));
            }
            // Generated now, as the far stretch of a world played in would be: the runner ticks faster
            // than a chunk generates off the server thread, and a ticket would wait on it for ever.
            ChunkPos chunk = ChunkPos.containing(helper.absolutePos(far));
            for (int cx = chunk.x() - AROUND - 1; cx <= chunk.x() + AROUND + 1; cx++) {
                for (int cz = chunk.z() - AROUND - 1; cz <= chunk.z() + AROUND + 1; cz++) {
                    helper.getLevel().getChunk(cx, cz);
                }
            }
        }
        return network;
    }

    /** A Train of {@code cars} standing heading east on the far stretch, the first at {@link #YARD}, out of view and never shown. */
    private static Train standing(GameTestHelper helper, RailNetwork network, Car... cars) {
        int x = YARD.getX();
        int from = WEST + (x - WEST) / SPAN * SPAN;
        Span span = network.get(arm(helper, from), arm(helper, from + SPAN));
        Train train = Train.of(new Place(span, span.b(), from + SPAN - x), List.of(cars));
        LevelRails.of(helper.getLevel()).add(train);
        return train;
    }

    /** The hatch at {@code pos} as the level's hatches hold it, read without loading its chunk. */
    private static Hatches.Hatch hatch(GameTestHelper helper, BlockPos pos) {
        return LevelHatches.of(helper.getLevel()).at(helper.absolutePos(pos))
                .orElseThrow(() -> helper.assertionException(pos, Component.literal("no hatch in the level's hatches")));
    }

    private static Arm arm(GameTestHelper helper, int x) {
        return PylonColumn.arm(helper.absolutePos(new BlockPos(x, 1, Z)));
    }

    /** Brings the chunks around {@code pos} into view, or lets them go, by the tests' own ticket. */
    private static void inView(GameTestHelper helper, BlockPos pos, boolean in) {
        ChunkPos chunk = ChunkPos.containing(helper.absolutePos(pos));
        var chunks = helper.getLevel().getChunkSource();
        if (in) {
            chunks.addTicketWithRadius(RailworksGameTests.IN_VIEW.get(), chunk, AROUND);
            // Loaded now, as the ticket would load them in a while: the runner ticks faster than a
            // chunk loads off the server thread.
            for (int cx = chunk.x() - AROUND; cx <= chunk.x() + AROUND; cx++) {
                for (int cz = chunk.z() - AROUND; cz <= chunk.z() + AROUND; cz++) {
                    helper.getLevel().getChunk(cx, cz);
                }
            }
        } else {
            chunks.removeTicketWithRadius(RailworksGameTests.IN_VIEW.get(), chunk, AROUND);
        }
    }

    /**
     * Takes the test's Trains and what they held out of the level, its line's Spans, and the Station and
     * hatch it set out past the structure, so the next test, along the same stretch, finds it clear; and
     * lets the near stretch go.
     */
    private static void clearAway(GameTestHelper helper, Train... trains) {
        LevelRails rails = LevelRails.of(helper.getLevel());
        for (Train train : trains) {
            rails.trains().remove(train);
            // Its views, no Train holding their cars, go at the level's next tick.
            train.cars().forEach(car -> LevelCarRecords.of(helper.getLevel()).remove(car.id()));
        }
        // Each batch's structure stands beside the last one's, so the next test's line runs along this one.
        for (int x = WEST; x < FAR; x += SPAN) {
            rails.network().remove(arm(helper, x), arm(helper, x + SPAN));
        }
        for (BlockPos pos : List.of(REQUESTER, DEPOT)) {
            if (!helper.getBlockState(pos).isAir()) {
                helper.destroyBlock(pos);
            }
        }
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(REQUESTER)).inflate(8.0))
                .forEach(Entity::discard);
        forceLoad(helper, false);
    }

    /** Forces the chunks under the line's near stretch, by the structure, loaded, or lets them go. */
    private static void forceLoad(GameTestHelper helper, boolean forced) {
        BlockPos west = helper.absolutePos(new BlockPos(WEST, 1, 1));
        BlockPos east = helper.absolutePos(new BlockPos(NEAR, 1, 5));
        for (int cx = west.getX() >> 4; cx <= east.getX() >> 4; cx++) {
            for (int cz = Math.min(west.getZ(), east.getZ()) >> 4; cz <= Math.max(west.getZ(), east.getZ()) >> 4; cz++) {
                helper.getLevel().setChunkForced(cx, cz, forced);
            }
        }
    }
}
