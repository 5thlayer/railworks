// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import static io.github._5thlayer.railworks.gametest.LocomotiveTests.STAND;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.RailworksRefusal;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Locomotives stand only at a Train's ends: a car whose coupling would leave a Locomotive between
 * other cars is refused, and nothing is placed. All of these tests share the {@code loco_makeup_}
 * prefix, and stand on {@link LocomotiveTests}' Span.
 */
final class LocomotiveMakeupTests {

    private LocomotiveMakeupTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("loco_makeup_a_cargo_car_that_would_leave_a_locomotive_between_two_is_refused", 20,
                LocomotiveMakeupTests::cargoAheadOfLocomotiveAndCargo);
    }

    private static void cargoAheadOfLocomotiveAndCargo(GameTestHelper helper) {
        // Two blocks further from the line's end than the others, so a car fits on either side of it.
        ServerPlayer placer = LocomotiveTests.player(helper, -45.0F);
        placer.snapTo(placer.getX() - 2.0, placer.getY(), placer.getZ(), -45.0F, 5.0F);
        LocomotiveTests.use(helper, placer);
        LocomotiveEntity locomotive = helper.getLevel().getEntitiesOfClass(LocomotiveEntity.class,
                LocomotiveTests.searchBox(helper)).getFirst();
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
