// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.CarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.LocomotiveMenu;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.StationBlockEntity;
import io.github._5thlayer.railworks.network.SchedulePacket;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Schedule;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * A Train on its Schedule in a real level: the Locomotive's menu edits it through the packet the
 * Schedule screen sends, the level saves it, a driver takes the Train off it, Jade's line follows it,
 * and it runs a Train unattended round a loop and back and forth along a line, the Stations real
 * blocks and the fuel burnt from the slot. How it drives is {@code ScheduleTest}'s. All of these
 * tests share the {@code schedule_} prefix.
 */
final class ScheduleTests {

    /**
     * Eight Pylons round a square with its corners cut, so the Rail bends 45 degrees through each,
     * within the test's own plot: a test whose cars stray into the next plot loses them when that
     * test is cleared away.
     */
    private static final BlockPos[] LOOP = {new BlockPos(2, 1, 0), new BlockPos(6, 1, 0), new BlockPos(8, 1, 2),
            new BlockPos(8, 1, 6), new BlockPos(6, 1, 8), new BlockPos(2, 1, 8), new BlockPos(0, 1, 6),
            new BlockPos(0, 1, 2)};
    /** Inside the loop, beside its south, north and west sides. */
    private static final BlockPos SOUTH = new BlockPos(4, 1, 1);
    private static final BlockPos NORTH = new BlockPos(4, 1, 7);
    private static final BlockPos WEST_SIDE = new BlockPos(1, 1, 4);
    /** Beside {@link LocomotiveTests}' Span, a block in from each end. */
    private static final BlockPos WEST = new BlockPos(2, 1, 2);
    private static final BlockPos EAST = new BlockPos(8, 1, 2);

    private ScheduleTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("schedule_the_locomotives_menu_edits_its_trains_schedule", 20, ScheduleTests::menuEdits);
        tests.test("schedule_is_not_edited_from_out_of_reach", 20, ScheduleTests::notFromAfar);
        tests.test("schedule_is_kept_across_a_save_and_reload", 20, ScheduleTests::saved);
        tests.test("schedule_a_driver_takes_the_train_off_it", 20, ScheduleTests::driverTakesOver);
        tests.test("schedule_a_cargo_car_says_how_full_it_is", 20, ScheduleTests::cargoLoad);
        tests.test("schedule_runs_a_train_unattended_around_a_loop_with_each_wait", 2_000, ScheduleTests::roundALoop);
        tests.test("schedule_runs_a_train_with_a_locomotive_at_each_end_back_and_forth", 1_200, ScheduleTests::backAndForth);
    }

    // ---- the menu ----

    private static void menuEdits(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.fuelled(helper);
        ServerPlayer player = near(helper);
        locomotive.openCustomInventoryScreen(player);
        if (!(player.containerMenu instanceof LocomotiveMenu)) {
            helper.fail("the Locomotive's menu did not open: " + player.containerMenu, LocomotiveTests.STAND);
        }
        boolean applied = new SchedulePacket(locomotive.getId(), List.of(
                new Schedule.Entry("  §cMine ", Schedule.Wait.CARGO_FULL, 5),
                new Schedule.Entry("   ", Schedule.Wait.TIME, 5),
                new Schedule.Entry("Depot", Schedule.Wait.TIME, 12)), true).apply(player);
        Schedule schedule = locomotive.train().schedule();
        List<Schedule.Entry> expected = List.of(new Schedule.Entry("Mine", Schedule.Wait.CARGO_FULL, 5),
                new Schedule.Entry("Depot", Schedule.Wait.TIME, 12));
        if (!applied || !schedule.entries().equals(expected) || !schedule.automatic()) {
            helper.fail("the menu's Schedule came out as " + schedule + ", applied " + applied, LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    private static void notFromAfar(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.fuelled(helper);
        ServerPlayer far = helper.makeMockServerPlayerInLevel();
        far.setGameMode(GameType.SURVIVAL);
        far.snapTo(locomotive.getX() + 20.0, locomotive.getY(), locomotive.getZ());
        boolean applied = new SchedulePacket(locomotive.getId(),
                List.of(Schedule.Entry.of("Mine", Schedule.Wait.TIME)), true).apply(far);
        if (applied || !locomotive.train().schedule().entries().isEmpty()) {
            helper.fail("a player twenty blocks off set the Schedule: " + locomotive.train().schedule(), LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    private static void saved(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.fuelled(helper);
        List<Schedule.Entry> entries = List.of(new Schedule.Entry("Mine", Schedule.Wait.CARGO_EMPTY, 5),
                new Schedule.Entry("Depot", Schedule.Wait.TIME, 30));
        new SchedulePacket(locomotive.getId(), entries, true).apply(near(helper));
        LevelRails rails = LevelRails.of(helper.getLevel());
        var saved = LevelRails.TYPE.codec().encodeStart(NbtOps.INSTANCE, rails).getOrThrow();
        LevelRails loaded = LevelRails.TYPE.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        Schedule schedule = loaded.trains().of(locomotive.carId()).schedule();
        if (!schedule.entries().equals(entries) || !schedule.automatic()) {
            helper.fail("the Schedule came back from the level's save as " + schedule, LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    private static void driverTakesOver(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.fuelled(helper);
        new SchedulePacket(locomotive.getId(), List.of(Schedule.Entry.of("Nowhere", Schedule.Wait.TIME)), true)
                .apply(near(helper));
        ServerPlayer driver = LocomotiveTests.board(helper, locomotive);
        LocomotiveTests.press(driver, true, false);
        LocomotiveTests.run(locomotive, 2);
        Train train = locomotive.train();
        if (train.schedule().automatic() || train.speed() <= 0.0) {
            helper.fail("a driver's forward key left the Train on its Schedule, or did not drive it: " + train
                    + " " + train.schedule(), LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    private static void cargoLoad(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = CargoCarTests.placeCargoBeside(helper, locomotive, -1);
        if (cargo.load() != Schedule.Load.EMPTY) {
            helper.fail("a new Cargo car is " + cargo.load(), LocomotiveTests.STAND);
        }
        for (int slot = 0; slot < CargoCarEntity.SLOTS; slot++) {
            cargo.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        if (cargo.load() != Schedule.Load.FULL) {
            helper.fail("a Cargo car with every slot full is " + cargo.load(), LocomotiveTests.STAND);
        }
        cargo.setItem(3, new ItemStack(Items.COBBLESTONE, 63));
        if (cargo.load() != Schedule.Load.PARTLY) {
            helper.fail("a Cargo car a cobblestone short of full is " + cargo.load(), LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    // ---- running unattended ----

    private static void roundALoop(GameTestHelper helper) {
        clearRoof(helper);
        RailNetwork network = LevelRails.of(helper.getLevel()).network();
        for (int i = 0; i < LOOP.length; i++) {
            helper.setBlock(LOOP[i], RailworksRegistries.PYLON.get().defaultBlockState());
        }
        for (int i = 0; i < LOOP.length; i++) {
            Arm one = arm(helper, LOOP[i]);
            Arm other = arm(helper, LOOP[(i + 1) % LOOP.length]);
            network.add(Span.of(one, other, SpanRules.cost(one, other)));
        }
        CargoLoadingTests.station(helper, SOUTH);
        CargoLoadingTests.station(helper, NORTH);
        CargoLoadingTests.station(helper, WEST_SIDE);
        // East along the south side, a little short of the South Station's foot, a Cargo car behind.
        Span south = network.get(arm(helper, LOOP[0]), arm(helper, LOOP[1]));
        Locomotive model = Locomotive.burner();
        CargoCar wagon = CargoCar.create();
        Train train = Train.of(new Place(south, south.b(), 1.0), List.of(model, wagon));
        LocomotiveEntity locomotive = spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, model);
        CargoCarEntity cargo = spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, wagon);
        LocomotiveTests.fill(helper, locomotive, new ItemStack(Items.COAL, 8));
        helper.startSequence()
                .thenWaitUntil(() -> CargoLoadingTests.registered(helper, SOUTH, NORTH, WEST_SIDE))
                .thenExecute(() -> {
                    name(helper, SOUTH, "South");
                    name(helper, NORTH, "North");
                    name(helper, WEST_SIDE, "West");
                    new SchedulePacket(locomotive.getId(), List.of(
                            new Schedule.Entry("South", Schedule.Wait.CARGO_FULL, 1),
                            new Schedule.Entry("North", Schedule.Wait.CARGO_EMPTY, 1),
                            new Schedule.Entry("West", Schedule.Wait.TIME, 1)), true).apply(near(locomotive, helper));
                })
                .thenWaitUntil(() -> waitingAt(helper, locomotive, "South"))
                // Not full, it waits on, well past a timed wait.
                .thenIdle(40)
                .thenExecute(() -> {
                    waitingAt(helper, locomotive, "South");
                    for (int slot = 0; slot < CargoCarEntity.SLOTS; slot++) {
                        cargo.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                    }
                })
                .thenWaitUntil(() -> heading(helper, locomotive, "North"))
                .thenWaitUntil(() -> waitingAt(helper, locomotive, "North"))
                // Not empty, it waits on.
                .thenIdle(40)
                .thenExecute(() -> {
                    waitingAt(helper, locomotive, "North");
                    cargo.clearContent();
                })
                .thenWaitUntil(() -> heading(helper, locomotive, "West"))
                .thenWaitUntil(() -> waitingAt(helper, locomotive, "West"))
                .thenWaitUntil(() -> heading(helper, locomotive, "South"))
                .thenWaitUntil(() -> waitingAt(helper, locomotive, "South"))
                .thenExecute(() -> {
                    if (locomotive.train().speed() != 0.0 || locomotive.fuelItem().getCount() >= 8) {
                        helper.fail("round the loop the Train should stand at the South having burnt coal from its slot: "
                                + locomotive.train() + ", " + locomotive.fuelItem(), SOUTH);
                    }
                })
                .thenSucceed();
    }

    private static void backAndForth(GameTestHelper helper) {
        LocomotiveTests.player(helper, -45.0F);
        Span span = LocomotiveTests.span(helper);
        // Heading east, the first car 4 blocks along, the second behind it facing west.
        Locomotive east = Locomotive.burner();
        Locomotive west = Locomotive.burner();
        Train train = Train.of(new Place(span, span.b(), span.length() - 4.0), List.of(east))
                .coupled(LevelRails.of(helper.getLevel()).network(), Train.End.BACK, west, true);
        LocomotiveEntity leading = spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, east);
        LocomotiveEntity trailing = spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, west);
        LocomotiveTests.fill(helper, leading, new ItemStack(Items.COAL, 4));
        LocomotiveTests.fill(helper, trailing, new ItemStack(Items.COAL, 4));
        CargoLoadingTests.station(helper, WEST);
        CargoLoadingTests.station(helper, EAST);
        helper.startSequence()
                .thenWaitUntil(() -> CargoLoadingTests.registered(helper, WEST, EAST))
                .thenExecute(() -> {
                    name(helper, WEST, "West");
                    name(helper, EAST, "East");
                    new SchedulePacket(leading.getId(), List.of(new Schedule.Entry("East", Schedule.Wait.TIME, 1),
                            new Schedule.Entry("West", Schedule.Wait.TIME, 1)), true).apply(near(leading, helper));
                })
                .thenWaitUntil(() -> waitingAt(helper, leading, "East"))
                .thenWaitUntil(() -> waitingAt(helper, leading, "West"))
                .thenExecute(() -> {
                    // Back at the West end with the car facing west leading.
                    double eastX = leading.getX();
                    double westX = trailing.getX();
                    if (westX >= eastX || trailing.getX() > helper.absolutePos(WEST).getX() + 1.5) {
                        helper.fail("at the West the car facing west should lead, by the Station: east car at " + eastX
                                + ", west car at " + westX, WEST);
                    }
                })
                .thenWaitUntil(() -> waitingAt(helper, leading, "East"))
                .thenSucceed();
    }

    // ---- setup ----

    /** A survival player beside {@link LocomotiveTests}' Span, within the menu's reach of a car on it. */
    private static ServerPlayer near(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(LocomotiveTests.STAND);
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        return player;
    }

    /** A survival player standing by {@code car}. */
    private static ServerPlayer near(CarEntity car, GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(car.getX(), car.getY() - 1.0, car.getZ());
        return player;
    }

    /** Fails until the Locomotive shows its Train waiting at {@code name}, for a sequence to wait on. */
    private static void waitingAt(GameTestHelper helper, LocomotiveEntity locomotive, String name) {
        if (!locomotive.waitingAtSeen().equals(name)) {
            helper.fail("not yet waiting at " + name + ": " + locomotive.train() + " " + locomotive.train().schedule(),
                    LocomotiveTests.STAND);
        }
    }

    /** Fails until the Locomotive shows its Train running to {@code name}, as Jade reads it. */
    private static void heading(GameTestHelper helper, LocomotiveEntity locomotive, String name) {
        if (!locomotive.headingSeen().equals(name)) {
            helper.fail("not yet heading for " + name + ": " + locomotive.train().schedule(), LocomotiveTests.STAND);
        }
    }

    /** Names the Station at {@code pos}, as a player standing by it does in its menu. */
    private static void name(GameTestHelper helper, BlockPos pos, String name) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(pos);
        player.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (!helper.getBlockEntity(pos, StationBlockEntity.class).rename(player, name)) {
            helper.fail("the Station would not take its name", pos);
        }
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

    private static Arm arm(GameTestHelper helper, BlockPos column) {
        return PylonColumn.arm(helper.absolutePos(column));
    }

    /** The structure's roof is a barrier layer the cars would ride up into. */
    private static void clearRoof(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
            }
        }
    }
}
