// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import io.github._5thlayer.railworks.LocomotiveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A moving Train striking what is in its path, on {@link LocomotiveTests}' line: a Locomotive set
 * mid-Span facing B, driven forward at what stands on a stone under the Rail ahead of it, so it is
 * in the car's way. {@code ImpactTest} holds how hard and which way; these show a car in a world
 * finds what it runs into. All of these tests share the {@code collision_} prefix.
 */
final class CollisionTests {

    /** Under the Rail ahead of the car, at a car's floor: what stands on it is in the car's way. */
    private static final BlockPos STONE = new BlockPos(7, 1, 3);
    /** The middle of the Rail across, as the line runs along x. */
    private static final double LINE_Z = 3.5;

    private CollisionTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        tests.test("collision_a_moving_train_hurts_and_pushes_aside_a_mob_in_its_path", 100, CollisionTests::mobStruck);
        tests.test("collision_a_moving_train_hurts_a_player_in_its_path", 100, CollisionTests::playerStruck);
        tests.test("collision_a_stopped_train_hurts_nothing", 60, CollisionTests::stoppedHurtsNothing);
        tests.test("collision_a_train_never_strikes_its_own_rider", 60, CollisionTests::riderSafe);
    }

    private static void mobStruck(GameTestHelper helper) {
        Pig pig = standingInTheWay(helper);
        drive(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pig was not hurt");
            double off = Math.abs(pig.getZ() - helper.absoluteVec(new Vec3(0, 0, LINE_Z)).z);
            helper.assertTrue(off > 0.75, "the pig is still on the line, " + off + " off it");
        });
    }

    private static void playerStruck(GameTestHelper helper) {
        helper.setBlock(STONE, Blocks.STONE);
        ServerPlayer walker = helper.makeMockServerPlayerInLevel();
        walker.setGameMode(GameType.SURVIVAL);
        hurtable(walker);
        Vec3 at = helper.absoluteVec(new Vec3(STONE.getX() + 0.5, STONE.getY() + 1, LINE_Z + 0.1));
        walker.snapTo(at.x, at.y, at.z);
        drive(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(walker.getHealth() < walker.getMaxHealth(), "the player was not hurt");
            helper.assertTrue(walker.getDeltaMovement().z > 0.3 || walker.getZ() > at.z + 0.5,
                    "the player was not pushed aside to the side they stood on: " + walker.getDeltaMovement());
        });
    }

    private static void stoppedHurtsNothing(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.placed(helper);
        // Under the car itself, in its box: a Train at rest has no path.
        helper.setBlock(new BlockPos((int) Math.floor(helper.relativeVec(car.position()).x), 1, 3), Blocks.STONE);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG,
                new Vec3(Math.floor(helper.relativeVec(car.position()).x) + 0.5, 2, LINE_Z));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(car.train() != null && car.train().speed() == 0.0, "the Train moved");
            helper.assertTrue(unhurt(pig), "a stopped Train hurt the pig");
            helper.succeed();
        });
    }

    private static void riderSafe(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.fuelled(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        hurtable(driver);
        double start = car.getX();
        LocomotiveTests.press(driver, true, false);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(car.getX() > start + 1.0, "the Train did not move");
            helper.assertTrue(unhurt(driver), "the Train struck its own driver");
            helper.succeed();
        });
    }

    /** A pig on the stone, still, in the car's way. */
    private static Pig standingInTheWay(GameTestHelper helper) {
        helper.setBlock(STONE, Blocks.STONE);
        // A shade off the middle, so the side it goes to is the one it stood on.
        return helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(STONE.getX() + 0.5, STONE.getY() + 1, LINE_Z - 0.1));
    }

    /** The Locomotive set mid-Span facing B, fuelled and driven forward. */
    private static void drive(GameTestHelper helper) {
        LocomotiveEntity car = LocomotiveTests.fuelled(helper);
        ServerPlayer driver = LocomotiveTests.board(helper, car);
        LocomotiveTests.press(driver, true, false);
    }

    /** A mock player's client never reports it loaded, which leaves it invulnerable: as if it had. */
    private static void hurtable(ServerPlayer player) {
        player.connection.markClientLoaded();
    }

    /** Whether {@code entity} still has all its health. */
    private static boolean unhurt(LivingEntity entity) {
        return entity.getHealth() == entity.getMaxHealth();
    }
}
