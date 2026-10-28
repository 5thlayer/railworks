// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.ChargeSpec;
import io.github._5thlayer.railworks.rail.DriveSide;
import io.github._5thlayer.railworks.rail.FluidCar;
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
 * Locomotive's drive and fuel, the Electric Locomotive's charge with the Charger and the Burner
 * Generator that fill it, the drive side, how many car spots a Station's platform has, how many
 * slots a hatch holds, how many buckets a Fluid hatch and a Fluid car hold, and how fast hatches
 * trade and how long a Train waits on them. A server config, so the client measures the same limits
 * the server strings by.
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
    private static final ModConfigSpec.LongValue JOULES_PER_FE;
    private static final ModConfigSpec.LongValue ELECTRIC_CAPACITY;
    private static final ModConfigSpec.LongValue CHARGER_RATE;
    private static final ModConfigSpec.LongValue CHARGER_BUFFER;
    private static final ModConfigSpec.DoubleValue BURNER_GENERATOR_KILOWATTS;
    private static final ModConfigSpec.EnumValue<DriveSide> DRIVE_SIDE;
    private static final ModConfigSpec.IntValue PLATFORM_CARS;
    private static final ModConfigSpec.IntValue HATCH_SLOTS;
    private static final ModConfigSpec.IntValue FLUID_HATCH_BUCKETS;
    private static final ModConfigSpec.IntValue FLUID_CAR_BUCKETS;
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
        FLUID_HATCH_BUCKETS = builder.comment("How many buckets a Fluid hatch's tank holds. A Fluid hatch keeps the tank it was placed with.")
                .defineInRange("fluidHatchBuckets", Logistics.DEFAULT_FLUID_HATCH_BUCKETS, 1, 1000);
        FLUID_CAR_BUCKETS = builder.comment("How many buckets a Fluid car's tank holds. A Fluid car keeps the tank it was made with.")
                .defineInRange("fluidCarBuckets", FluidCar.DEFAULT_TANK_BUCKETS, 1, 1000);
        HATCH_TICKS_PER_STACK = builder.comment("How many ticks a hatch takes to move each stack to or from the Cargo car at its car spot, and a Fluid hatch two buckets to or from the Fluid car at its. 20 is a stack a second.")
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
        builder.push("electric_locomotive");
        JOULES_PER_FE = builder.comment("What one FE is worth to the Electric Locomotive and the Burner Generator, in joules. 100, as Wireworks' Steam Engine, makes the Locomotive's 600 kW 300 FE a tick.")
                .defineInRange("joulesPerFe", ChargeSpec.DEFAULT_JOULES_PER_FE, 1L, 1_000_000L);
        ELECTRIC_CAPACITY = builder.comment("How many FE the Electric Locomotive's charge holds. 10,000,000 is 1 GJ at 100 J an FE, about four stacks of coal.")
                .defineInRange("capacity", ChargeSpec.DEFAULT_LOCOMOTIVE_CAPACITY, 1L, Long.MAX_VALUE / 2);
        CHARGER_RATE = builder.comment("How many FE a Charger takes in from cables, and gives the Electric Locomotive beside it, each tick at most.")
                .defineInRange("chargerRate", ChargeSpec.DEFAULT_CHARGER_RATE, 1L, Integer.MAX_VALUE);
        CHARGER_BUFFER = builder.comment("How many FE a Charger buffers at most.")
                .defineInRange("chargerBuffer", ChargeSpec.DEFAULT_CHARGER_BUFFER, 1L, Long.MAX_VALUE / 2);
        BURNER_GENERATOR_KILOWATTS = builder.comment("How hard a Burner Generator burns its fuel into FE, in kilowatts. 600 is the Burner Locomotive's full power, so coal lasts as long in either.")
                .defineInRange("burnerGeneratorKilowatts", ChargeSpec.DEFAULT_BURNER_GENERATOR_KILOWATTS, 1.0, 1_000_000.0);
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
                ChargeSpec.reset();
                DriveSide.configure(DriveSide.DEFAULT);
                Platform.configure(Platform.MAX_CARS);
                Hatches.configure(Hatches.DEFAULT_SLOTS, Logistics.DEFAULT_FLUID_HATCH_BUCKETS);
                FluidCarEntity.configure(FluidCar.DEFAULT_TANK_BUCKETS);
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
            ChargeSpec.configure(JOULES_PER_FE.get(), ELECTRIC_CAPACITY.get(), CHARGER_RATE.get(), CHARGER_BUFFER.get(),
                    BURNER_GENERATOR_KILOWATTS.get());
            DriveSide.configure(DRIVE_SIDE.get());
            Platform.configure(PLATFORM_CARS.get());
            Hatches.configure(HATCH_SLOTS.get(), FLUID_HATCH_BUCKETS.get());
            FluidCarEntity.configure(FLUID_CAR_BUCKETS.get());
            Logistics.configure(HATCH_TICKS_PER_STACK.get(), STALL_SECONDS.get());
        }
    }
}
