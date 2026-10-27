// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.DriveSide;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import io.github._5thlayer.railworks.rail.Logistics;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.SpanRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code railworks-server.toml}: how long, steep and sharply bending a Span may be, the Burner
 * Locomotive's drive and fuel, the drive side, how many car spots a Station's platform has, how
 * many slots a hatch holds, and how fast hatches trade and how long a Train waits on them. A server config, so the client measures the same limits the server
 * strings by.
 */
public final class RailworksConfig {

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue SPAN_LENGTH_LIMIT;
    private static final ModConfigSpec.DoubleValue SPAN_GRADE_LIMIT;
    private static final ModConfigSpec.DoubleValue SPAN_BEND_LIMIT;
    private static final ModConfigSpec.DoubleValue LOCOMOTIVE_MAX_SPEED;
    private static final ModConfigSpec.DoubleValue LOCOMOTIVE_ACCELERATION;
    private static final ModConfigSpec.DoubleValue LOCOMOTIVE_BRAKING;
    private static final ModConfigSpec.DoubleValue LOCOMOTIVE_FRICTION;
    private static final ModConfigSpec.LongValue LOCOMOTIVE_JOULES_PER_BURN_TICK;
    private static final ModConfigSpec.EnumValue<DriveSide> DRIVE_SIDE;
    private static final ModConfigSpec.IntValue PLATFORM_CARS;
    private static final ModConfigSpec.IntValue HATCH_SLOTS;
    private static final ModConfigSpec.IntValue HATCH_TICKS_PER_STACK;
    private static final ModConfigSpec.DoubleValue STALL_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SPAN_LENGTH_LIMIT = builder.comment("The longest Span, in blocks: the straight distance between its two Arms, which its Rail cost rounds up.")
                .defineInRange("spanLengthLimit", SpanRules.DEFAULT_LENGTH_LIMIT, 1.0, 256.0);
        SPAN_GRADE_LIMIT = builder.comment("The steepest Span, in percent: blocks up for every 100 across. 25 is a block up for every four across.")
                .defineInRange("spanGradeLimitPercent", SpanRules.DEFAULT_GRADE_LIMIT * 100.0, 1.0, 1000.0);
        SPAN_BEND_LIMIT = builder.comment("The sharpest bend a Span may make through an Arm against the Span already there, in degrees off straight on. 45 is Factorio's curved rail pair.")
                .defineInRange("spanBendLimit", SpanRules.DEFAULT_BEND_LIMIT, 1.0, 180.0);
        DRIVE_SIDE = builder.comment("The side of the Rail a Station stands on to serve a Train, seen the way the Train runs: RIGHT, as Factorio's train stops, or LEFT.")
                .defineEnum("driveSide", DriveSide.DEFAULT);
        PLATFORM_CARS = builder.comment("How many car spots of two blocks a Station's platform has at most, behind the leading car's place at the Station along its straight leg. 4 at most.")
                .defineInRange("platformCars", Platform.MAX_CARS, 1, Platform.MAX_CARS);
        HATCH_SLOTS = builder.comment("How many slots a hatch holds, a chest's 27 by default. A hatch keeps the slots it was placed with.")
                .defineInRange("hatchSlots", Hatches.DEFAULT_SLOTS, 1, 54);
        HATCH_TICKS_PER_STACK = builder.comment("How many ticks a hatch takes to move each stack to or from the Cargo car at its car spot. 20 is a stack a second.")
                .defineInRange("hatchTicksPerStack", Logistics.DEFAULT_HATCH_TICKS_PER_STACK, 1, 1200);
        STALL_SECONDS = builder.comment("How long an automatic Train waits at a Station with nothing moving before it leaves, in seconds.")
                .defineInRange("stallSeconds", Logistics.DEFAULT_STALL_SECONDS, 0.0, 600.0);
        builder.push("burner_locomotive");
        LOCOMOTIVE_MAX_SPEED = builder.comment("Top speed either way, in blocks a second. Factorio's locomotive does 72, which outruns chunk loading.")
                .defineInRange("maxSpeed", LocomotiveSpec.DEFAULT_MAX_SPEED, 1.0, 200.0);
        LOCOMOTIVE_ACCELERATION = builder.comment("How fast power adds speed, in blocks a second squared.")
                .defineInRange("acceleration", LocomotiveSpec.DEFAULT_ACCELERATION, 0.1, 100.0);
        LOCOMOTIVE_BRAKING = builder.comment("How fast back brakes a moving Locomotive, in blocks a second squared. 5 is Factorio's locomotive.")
                .defineInRange("braking", LocomotiveSpec.DEFAULT_BRAKING, 0.1, 100.0);
        LOCOMOTIVE_FRICTION = builder.comment("How fast an unpowered Locomotive slows, in blocks a second squared. 0.25 is Factorio's locomotive.")
                .defineInRange("friction", LocomotiveSpec.DEFAULT_FRICTION, 0.0, 10.0);
        LOCOMOTIVE_JOULES_PER_BURN_TICK = builder.comment("What one tick of a fuel's vanilla burn time is worth to the Locomotive, in joules. 2500 makes coal's 1600 ticks Factorio's 4 MJ, as in Wireworks' Boiler.")
                .defineInRange("joules_per_burn_tick", LocomotiveSpec.DEFAULT_JOULES_PER_BURN_TICK, 1L, 1_000_000L);
        builder.pop();
        SPEC = builder.build();
    }

    private RailworksConfig() {
    }

    static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, event -> apply(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> apply(event.getConfig()));
        modBus.addListener(ModConfigEvent.Unloading.class, event -> {
            if (event.getConfig().getSpec() == SPEC) {
                SpanRules.configure(SpanRules.DEFAULT_LENGTH_LIMIT);
                SpanRules.configureGrade(SpanRules.DEFAULT_GRADE_LIMIT);
                SpanRules.configureBend(SpanRules.DEFAULT_BEND_LIMIT);
                LocomotiveSpec.configure(LocomotiveSpec.DEFAULT_MAX_SPEED, LocomotiveSpec.DEFAULT_ACCELERATION,
                        LocomotiveSpec.DEFAULT_BRAKING, LocomotiveSpec.DEFAULT_FRICTION);
                LocomotiveSpec.configureFuel(LocomotiveSpec.DEFAULT_JOULES_PER_BURN_TICK);
                DriveSide.configure(DriveSide.DEFAULT);
                Platform.configure(Platform.MAX_CARS);
                Hatches.configure(Hatches.DEFAULT_SLOTS);
                Logistics.configure(Logistics.DEFAULT_HATCH_TICKS_PER_STACK, Logistics.DEFAULT_STALL_SECONDS);
            }
        });
    }

    private static void apply(ModConfig config) {
        if (config.getSpec() == SPEC) {
            SpanRules.configure(SPAN_LENGTH_LIMIT.get());
            SpanRules.configureGrade(SPAN_GRADE_LIMIT.get() / 100.0);
            SpanRules.configureBend(SPAN_BEND_LIMIT.get());
            LocomotiveSpec.configure(LOCOMOTIVE_MAX_SPEED.get(), LOCOMOTIVE_ACCELERATION.get(),
                    LOCOMOTIVE_BRAKING.get(), LOCOMOTIVE_FRICTION.get());
            LocomotiveSpec.configureFuel(LOCOMOTIVE_JOULES_PER_BURN_TICK.get());
            DriveSide.configure(DRIVE_SIDE.get());
            Platform.configure(PLATFORM_CARS.get());
            Hatches.configure(HATCH_SLOTS.get());
            Logistics.configure(HATCH_TICKS_PER_STACK.get(), STALL_SECONDS.get());
        }
    }
}
