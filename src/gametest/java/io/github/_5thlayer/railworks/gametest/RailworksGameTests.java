// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.railworks.Railworks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Module's game tests, run by the {@code gameTestServer} Gradle run: the Minecraft-integration
 * seam, with a real player on a real server. Each test stands on the {@code gametest/platform}
 * structure, a stone floor that {@code scripts/build-gametest-structures.py} writes, and sets up
 * what it needs itself, so the setup is in the diff. It lives in the {@code gametest} source set, which
 * only the Module's own runs load, and hooks itself in, so the {@code @Mod} class names no game-test
 * code and nothing gates it (libworks ADR 0011).
 */
@EventBusSubscriber(modid = Railworks.MOD_ID)
public final class RailworksGameTests {

    private static final Identifier PLATFORM = id("gametest/platform");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Railworks.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition<?>>> ENVIRONMENT_TYPES =
            DeferredRegister.create(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, Railworks.MOD_ID);

    private static final DeferredRegister<TicketType> TICKET_TYPES = DeferredRegister.create(Registries.TICKET_TYPE, Railworks.MOD_ID);

    /**
     * A chunk ticket of the tests' own, which loads and simulates the chunks it is on: a test adds and
     * removes it around a stretch it wants in view and out, never a forced chunk, which the runner
     * lets go of whenever a batch ends.
     */
    static final DeferredHolder<TicketType, TicketType> IN_VIEW = TICKET_TYPES.register("in_view",
            () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
        ENVIRONMENT_TYPES.register("left_drive", () -> LeftDrive.CODEC);
    }

    private RailworksGameTests() {
    }

    @SubscribeEvent
    static void construct(FMLConstructModEvent event) {
        register(event.getContainer().getEventBus());
    }

    public static void register(IEventBus modBus) {
        TEST_TYPES.register(modBus);
        ENVIRONMENT_TYPES.register(modBus);
        TICKET_TYPES.register(modBus);
        // Posted only when game tests are enabled, which a production game never is.
        modBus.addListener(RailworksGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        // Registered rather than borrowed, since the event hands out no lookup for vanilla's.
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var leftDrive = event.registerEnvironment(id("left_drive"), new LeftDrive());
        var tests = new Registrar(event, environment, leftDrive);
        HeardSounds.listen();
        FarAwayTests.listen();
        LoadTests.register(tests);
        GameTestPackTests.register(tests);
        CreativeTabTests.register(tests);
        SpanTests.register(tests);
        PylonPlanTests.register(tests);
        LocomotiveTests.register(tests);
        CargoCarTests.register(tests);
        LocomotiveMakeupTests.register(tests);
        LocomotiveSeatTests.register(tests);
        AutoStringTests.register(tests);
        StationTests.register(tests);
        CurveTests.register(tests);
        CargoAccessTests.register(tests);
        CarRecordTests.register(tests);
        CarSoundTests.register(tests);
        StationFacingTests.register(tests);
        HatchTests.register(tests);
        LogisticsTests.register(tests);
        CommandTests.register(tests);
        GalleryTests.register(tests);
        CollisionTests.register(tests);
        ClearanceTests.register(tests);
        ClearanceKeptTests.register(tests);
        FarAwayTests.register(tests);
        ElectricTests.register(tests);
        EngineRoomTests.register(tests);
        BellowsTests.register(tests);
        FluidTests.register(tests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Railworks.MOD_ID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment,
                     Holder<TestEnvironmentDefinition<?>> leftDrive) {

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            test(environment, name, maxTicks, body);
        }

        /** A test run driving on the left ({@link LeftDrive}), in a batch of its own. */
        void testDrivingLeft(String name, int maxTicks, Consumer<GameTestHelper> body) {
            test(leftDrive, name, maxTicks, body);
        }

        /**
         * A test that runs in a batch of its own, for one that changes the whole server, such as turning
         * a datapack on, which reloads its data under every test running with it, or one that builds out
         * past its structure into the plots beside it.
         */
        void testOnItsOwn(String name, int maxTicks, Consumer<GameTestHelper> body) {
            test(event.registerEnvironment(id("on_its_own/" + name), new TestEnvironmentDefinition.AllOf(List.of())),
                    name, maxTicks, body);
        }

        private void test(Holder<TestEnvironmentDefinition<?>> in, String name, int maxTicks, Consumer<GameTestHelper> body) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(in, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
