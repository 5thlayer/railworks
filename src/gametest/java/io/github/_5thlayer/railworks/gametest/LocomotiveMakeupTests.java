// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import static io.github._5thlayer.railworks.gametest.LocomotiveTests.STAND;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Locomotives stand only at a Train's ends: a car whose coupling would leave a Locomotive between
 * other cars is refused, and nothing is placed. All of these tests share the {@code loco_makeup_}
 * prefix, and stand on {@link LocomotiveTests}' Span, run on east past its B.
 */
final class LocomotiveMakeupTests {

    /** Five blocks east of {@link LocomotiveTests#B}, a Span to it running the line on straight. */
    private static final BlockPos EAST = LocomotiveTests.B.east(5);

    private LocomotiveMakeupTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        // Three five-block cars take fifteen blocks of line, past the plot beside it: on its own.
        tests.testOnItsOwn("loco_makeup_a_cargo_car_that_would_leave_a_locomotive_between_two_is_refused", 20,
                LocomotiveMakeupTests::cargoAheadOfLocomotiveAndCargo);
    }

    private static void cargoAheadOfLocomotiveAndCargo(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        // The line run on east, so a car fits on either side of the Locomotive.
        helper.setBlock(EAST, RailworksRegistries.PYLON.get().defaultBlockState());
        Arm b = PylonColumn.arm(helper.absolutePos(LocomotiveTests.B));
        Arm east = PylonColumn.arm(helper.absolutePos(EAST));
        LevelRails.of(helper.getLevel()).network().add(Span.of(b, east, 5));
        CargoCarEntity behind = CargoCarTests.placeCargoBeside(helper, locomotive, -1);

        // Ahead of the Locomotive, the Train would be C-L-C.
        ServerPlayer player = CargoCarTests.cargoPlayer(helper, locomotive, 1);
        PlacementPlan plan = LocomotiveTests.plan(helper, player);
        if (plan == null || plan.refusal() != RailworksRefusal.LOCOMOTIVE_BETWEEN_CARS) {
            helper.fail("a Cargo car aimed ahead of an L-C Train planned " + plan, STAND);
            return;
        }
        LocomotiveTests.use(helper, player);
        int cargoCars = helper.getLevel().getEntitiesOfClass(CargoCarEntity.class, LocomotiveTests.searchBox(helper),
                car -> !car.isRemoved()).size();
        Train train = locomotive.train();
        if (cargoCars != 1 || train == null || train.cars().size() != 2 || train != behind.train()) {
            helper.fail("the refused Cargo car was placed: " + cargoCars + " Cargo cars, Train " + train, STAND);
            return;
        }
        if (!player.getMainHandItem().is(RailworksRegistries.CARGO_CAR.get())) {
            helper.fail("the refused Cargo car was taken from the hand", STAND);
            return;
        }
        helper.succeed();
    }
}
