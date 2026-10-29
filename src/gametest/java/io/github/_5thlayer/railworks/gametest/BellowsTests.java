// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.CarKind;
import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchMode;
import io.github._5thlayer.railworks.LevelCarRecords;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PlatformsNear;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.FluidCar;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * A hatch's Bellows and a Fluid hatch's Loading arm (#76) reach across to a car of their kind at rest
 * at their spot, as a client draws them from the cars it sees ({@link PlatformsNear#facingCar}),
 * asked here of the same synched cars on the server: the car a hatch trades with, in a Train stopped
 * at the Station. On {@code HatchTests}' line: a Span south along
 * x 5 from z -3 to z 12, a Station east of its north end serving Trains heading north, the leading
 * car's place beside z -3 to 1, the first car spot beside z 2 to 6 and the second beside z 7 to 11,
 * the row two off the Rail at x 3 and x 7, y 3. Which car stands at which spot is
 * {@code PlatformTest}'s. All share the {@code bellows_} prefix.
 */
final class BellowsTests {

    private static final BlockPos NORTH_END = new BlockPos(5, 1, -3);
    private static final BlockPos SOUTH_END = new BlockPos(5, 1, 12);
    private static final BlockPos STATION = new BlockPos(7, 3, -3);
    /** Beside the leading car's place, where the Locomotive stands. */
    private static final BlockPos LEADER = new BlockPos(7, 3, -1);
    /** Beside the first car spot, west of the Rail and east of it. */
    private static final BlockPos FIRST_WEST = new BlockPos(3, 3, 4);
    private static final BlockPos FIRST_EAST = new BlockPos(7, 3, 2);
    /** Beside the second car spot, west of the Rail and east of it. */
    private static final BlockPos SECOND_WEST = new BlockPos(3, 3, 7);
    private static final BlockPos SECOND_EAST = new BlockPos(7, 3, 9);
    /**
     * How far the leading car's middle stands from the line's north end at rest: half a car, less the
     * half block the Station's edge lies past it.
     */
    private static final double LEAD_MARK = 2.0;

    private BellowsTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("bellows_and_the_loading_arm_reach_a_car_of_their_kind_at_rest_at_their_spot_and_no_other", 20,
                BellowsTests::reach);
        tests.test("bellows_go_as_the_car_moves_off_and_stay_gone_as_it_comes_to_rest_off_the_mark", 300, BellowsTests::leaves);
        tests.test("bellows_reach_no_car_of_a_train_at_rest_off_the_stations_mark", 20, BellowsTests::offTheMark);
        tests.test("bellows_reach_a_cargo_car_leading_at_the_station", 20, BellowsTests::cargoFirst);
    }

    private static void reach(GameTestHelper helper) {
        Span span = line(helper);
        hatch(helper, LEADER, RailworksRegistries.HATCH.get());
        hatch(helper, FIRST_WEST, RailworksRegistries.HATCH.get());
        hatch(helper, FIRST_EAST, RailworksRegistries.FLUID_HATCH.get());
        hatch(helper, SECOND_WEST, RailworksRegistries.HATCH.get());
        hatch(helper, SECOND_EAST, RailworksRegistries.FLUID_HATCH.get());
        Locomotive locomotive = Locomotive.burner();
        CargoCar cargo = CargoCar.create();
        FluidCar fluid = FluidCar.create();
        Train train = Train.of(new Place(span, span.a(), LEAD_MARK), List.of(locomotive, cargo, fluid));
        LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, locomotive);
        LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, cargo);
        LogisticsTests.spawn(helper, RailworksRegistries.FLUID_CAR_ENTITY.get(), train, fluid);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    expectFacing(helper, FIRST_WEST, Optional.of(Direction.EAST), "the Bellows to the Cargo car");
                    expectFacing(helper, SECOND_EAST, Optional.of(Direction.WEST), "the Loading arm to the Fluid car");
                    expectFacing(helper, LEADER, Optional.empty(), "Bellows to the Locomotive");
                    expectFacing(helper, FIRST_EAST, Optional.empty(), "a Loading arm to the Cargo car");
                    expectFacing(helper, SECOND_WEST, Optional.empty(), "Bellows to the Fluid car");
                })
                .thenSucceed();
    }

    private static void leaves(GameTestHelper helper) {
        Span span = line(helper);
        hatch(helper, FIRST_WEST, RailworksRegistries.HATCH.get());
        hatch(helper, SECOND_WEST, RailworksRegistries.HATCH.get());
        Locomotive locomotive = Locomotive.electric();
        CargoCar cargo = CargoCar.create();
        Train train = Train.of(new Place(span, span.a(), LEAD_MARK), List.of(locomotive, cargo));
        LocomotiveEntity view = LogisticsTests.spawn(helper, RailworksRegistries.ELECTRIC_LOCOMOTIVE_ENTITY.get(), train, locomotive);
        LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, cargo);
        LevelCarRecords.of(helper.getLevel()).of(locomotive.id()).orElseThrow().setCharge(100_000L);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    expectFacing(helper, FIRST_WEST, Optional.of(Direction.EAST), "the Bellows to the Cargo car at rest");
                    expectFacing(helper, SECOND_WEST, Optional.empty(), "Bellows to the second spot, with no car");
                    // Its Driver backs it off south, away from the line's end ahead.
                    ServerPlayer driver = LocomotiveTests.board(helper, view);
                    LocomotiveTests.press(driver, false, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(train.speed() != 0.0, "the Train did not move off"))
                .thenExecuteAfter(1, () ->
                        expectFacing(helper, FIRST_WEST, Optional.empty(), "the Bellows to the Cargo car moving off"))
                // On against the line's south end, where it comes to rest off the Station's mark.
                .thenWaitUntil(() -> helper.assertTrue(train.atRest(), "the Train still runs at " + train.speed()))
                .thenExecuteAfter(1, () -> {
                    expectFacing(helper, FIRST_WEST, Optional.empty(), "Bellows to the Cargo car at rest off the mark");
                    expectFacing(helper, SECOND_WEST, Optional.empty(), "Bellows to the Cargo car at rest off the mark");
                })
                .thenSucceed();
    }

    private static void offTheMark(GameTestHelper helper) {
        Span span = line(helper);
        hatch(helper, SECOND_WEST, RailworksRegistries.HATCH.get());
        Locomotive locomotive = Locomotive.burner();
        CargoCar cargo = CargoCar.create();
        // A car back from the mark: its Locomotive on the first spot and its Cargo car on the second,
        // lined up but not stopped there.
        Train train = Train.of(new Place(span, span.a(), LEAD_MARK + CargoCar.LENGTH), List.of(locomotive, cargo));
        LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, locomotive);
        LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, cargo);
        helper.startSequence()
                .thenExecuteAfter(1, () -> expectFacing(helper, SECOND_WEST, Optional.empty(),
                        "Bellows to a Cargo car of a Train not stopped at the Station"))
                .thenSucceed();
    }

    private static void cargoFirst(GameTestHelper helper) {
        Span span = line(helper);
        hatch(helper, LEADER, RailworksRegistries.HATCH.get());
        CargoCar cargo = CargoCar.create();
        Train train = Train.of(new Place(span, span.a(), LEAD_MARK), cargo);
        LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, cargo);
        helper.startSequence()
                .thenExecuteAfter(1, () -> expectFacing(helper, LEADER, Optional.of(Direction.WEST),
                        "the Bellows to the Cargo car leading"))
                .thenSucceed();
    }

    // ---- setup ----

    /** The Span with the Station east of its north end: the Span. */
    private static Span line(GameTestHelper helper) {
        for (BlockPos end : List.of(NORTH_END, SOUTH_END)) {
            helper.setBlock(end, RailworksRegistries.PYLON.get().defaultBlockState());
        }
        var one = PylonColumn.arm(helper.absolutePos(NORTH_END));
        var other = PylonColumn.arm(helper.absolutePos(SOUTH_END));
        Span span = Span.of(one, other, SpanRules.cost(one, other));
        LevelRails.of(helper.getLevel()).network().add(span);
        LogisticsTests.station(helper, STATION);
        return span;
    }

    /** A hatch of {@code block}'s kind, a provider, set at {@code pos} as a command sets one. */
    private static void hatch(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, block.defaultBlockState().setValue(HatchBlock.MODE, HatchMode.PROVIDER));
    }

    /** That the hatch at {@code pos} reaches out of {@code side} to a car, or to none. */
    private static void expectFacing(GameTestHelper helper, BlockPos pos, Optional<Direction> side, String what) {
        HatchBlock block = (HatchBlock) helper.getBlockState(pos).getBlock();
        Optional<Direction> facing = PlatformsNear.facingCar(helper.getLevel(), helper.absolutePos(pos),
                CarKind.carrying(block.holds()));
        if (!facing.equals(side)) {
            throw helper.assertionException(pos, Component.literal((side.isPresent() ? "no " : "") + what
                    + ": it reaches " + facing.map(Direction::getName).orElse("nothing")));
        }
    }
}
