// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.LocomotiveEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The sounds of a Train's cars, as the server plays them ({@link HeardSounds}): placing a car or
 * coupling one plays an iron block's place sound at it, and a refused placement none; opening a Cargo
 * car's cargo plays a chest's open sound and closing it the close sound, once for however many have
 * it open. Who hears a sound is the client's, so whether the placer hears it is on the dev-client
 * checklist. All of these tests share the {@code car_sound_} prefix, and stand on {@link LocomotiveTests}' Span.
 */
final class CarSoundTests {

    private CarSoundTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("car_sound_placing_a_locomotive_plays_a_place_sound_at_it", 20, CarSoundTests::placingLocomotive);
        tests.test("car_sound_coupling_a_cargo_car_plays_a_place_sound_at_it", 20, CarSoundTests::couplingCargo);
        tests.test("car_sound_a_refused_placement_plays_none", 20, CarSoundTests::refusedSilent);
        tests.test("car_sound_opening_and_closing_the_cargo_plays_a_chests_sounds", 20, CarSoundTests::chestSounds);
    }

    // ---- placing ----

    private static void placingLocomotive(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        assertPlayedAt(helper, SoundEvents.METAL_PLACE, List.of(locomotive), "placing a Locomotive");
        helper.succeed();
    }

    private static void couplingCargo(GameTestHelper helper) {
        LocomotiveEntity locomotive = LocomotiveTests.placed(helper);
        CargoCarEntity cargo = CargoCarTests.placeCargoBeside(helper, locomotive, -1);
        assertPlayedAt(helper, SoundEvents.METAL_PLACE, List.of(locomotive, cargo), "placing a Locomotive and coupling a Cargo car");
        helper.succeed();
    }

    private static void refusedSilent(GameTestHelper helper) {
        ServerPlayer player = LocomotiveTests.player(helper, 0.0F);
        // Looking down at the floor, away from the Rail.
        player.setXRot(80.0F);
        LocomotiveTests.use(helper, player);
        if (!HeardSounds.of(helper, SoundEvents.METAL_PLACE).isEmpty()) {
            helper.fail("a refused placement played a place sound", LocomotiveTests.STAND);
        }
        helper.succeed();
    }

    // ---- the cargo ----

    private static void chestSounds(GameTestHelper helper) {
        CargoCarEntity cargo = CargoAccessTests.cargoBehindLocomotive(helper);
        ServerPlayer first = opener(helper);
        ServerPlayer second = opener(helper);
        cargo.interact(first, InteractionHand.MAIN_HAND, Vec3.ZERO);
        assertPlayedAt(helper, SoundEvents.CHEST_OPEN, List.of(cargo), "opening the cargo");
        cargo.interact(second, InteractionHand.MAIN_HAND, Vec3.ZERO);
        assertPlayedAt(helper, SoundEvents.CHEST_OPEN, List.of(cargo), "opening the cargo open already");
        first.closeContainer();
        assertPlayedAt(helper, SoundEvents.CHEST_CLOSE, List.of(), "closing the cargo another still has open");
        second.closeContainer();
        assertPlayedAt(helper, SoundEvents.CHEST_CLOSE, List.of(cargo), "closing the cargo");
        helper.succeed();
    }

    private static ServerPlayer opener(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** That {@code sound} has played once at each of {@code at}, and nowhere else, in {@code helper}'s structure. */
    private static void assertPlayedAt(GameTestHelper helper, SoundEvent sound, List<? extends Entity> at, String doing) {
        List<Vec3> played = HeardSounds.of(helper, sound);
        List<Vec3> expected = at.stream().map(Entity::position).toList();
        if (!played.equals(expected)) {
            helper.fail(doing + " played " + sound.location() + " at " + played + ", not once at each of " + expected,
                    LocomotiveTests.STAND);
        }
    }
}
