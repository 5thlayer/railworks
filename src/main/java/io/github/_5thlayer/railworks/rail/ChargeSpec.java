// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * The numbers of charging (ADR 0005, amended in #9): what an FE is worth to an Electric
 * Locomotive, how much charge it holds, how fast a Charger gives it and how much it buffers, and
 * how hard a Burner Generator burns. An FE is 100 J, as in Wireworks' Steam Engine, so the
 * Locomotive's 600 kW draws 300 FE a tick, and its 10,000,000 FE are 1 GJ, about four stacks of
 * coal at 4 MJ each. The Burner Generator burns at the Locomotive's own 600 kW.
 *
 * <p>Pure: no Minecraft types.
 */
public final class ChargeSpec {

    public static final long DEFAULT_JOULES_PER_FE = 100L;
    public static final long DEFAULT_LOCOMOTIVE_CAPACITY = 10_000_000L;
    public static final long DEFAULT_CHARGER_RATE = 10_000L;
    public static final long DEFAULT_CHARGER_BUFFER = 10_000_000L;
    public static final double DEFAULT_GENERATOR_KILOWATTS = LocomotiveSpec.POWER_WATTS / 1_000.0;
    /** How many ticks of its output a Burner Generator buffers. */
    public static final int GENERATOR_BUFFER_TICKS = 10;

    private static volatile long joulesPerFe = DEFAULT_JOULES_PER_FE;
    private static volatile long locomotiveCapacity = DEFAULT_LOCOMOTIVE_CAPACITY;
    private static volatile long chargerRate = DEFAULT_CHARGER_RATE;
    private static volatile long chargerBuffer = DEFAULT_CHARGER_BUFFER;
    private static volatile double generatorWatts = LocomotiveSpec.POWER_WATTS;

    private ChargeSpec() {
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads. */
    public static void configure(long joulesPerFe, long locomotiveCapacity, long chargerRate, long chargerBuffer,
                                 double generatorKilowatts) {
        if (joulesPerFe <= 0L || locomotiveCapacity <= 0L || chargerRate <= 0L || chargerBuffer <= 0L
                || generatorKilowatts <= 0.0) {
            throw new IllegalArgumentException("charging " + joulesPerFe + " " + locomotiveCapacity + " " + chargerRate
                    + " " + chargerBuffer + " " + generatorKilowatts);
        }
        ChargeSpec.joulesPerFe = joulesPerFe;
        ChargeSpec.locomotiveCapacity = locomotiveCapacity;
        ChargeSpec.chargerRate = chargerRate;
        ChargeSpec.chargerBuffer = chargerBuffer;
        ChargeSpec.generatorWatts = generatorKilowatts * 1_000.0;
    }

    /** Sets every number back to its default. */
    public static void reset() {
        configure(DEFAULT_JOULES_PER_FE, DEFAULT_LOCOMOTIVE_CAPACITY, DEFAULT_CHARGER_RATE, DEFAULT_CHARGER_BUFFER,
                DEFAULT_GENERATOR_KILOWATTS);
    }

    /** What one FE is worth, in joules. */
    public static long joulesPerFe() {
        return joulesPerFe;
    }

    /** How many FE an Electric Locomotive's charge holds at most. */
    public static long locomotiveCapacity() {
        return locomotiveCapacity;
    }

    /** How many FE a Charger takes in, and gives a Locomotive, each tick at most. */
    public static long chargerRate() {
        return chargerRate;
    }

    /** How many FE a Charger buffers at most. */
    public static long chargerBuffer() {
        return chargerBuffer;
    }

    /** What a Burner Generator burns each tick, in joules. */
    public static long generatorJoulesPerTick() {
        return Math.round(generatorWatts / LocomotiveSpec.TICKS_PER_SECOND);
    }

    /** How many FE a Burner Generator makes a tick, rounded down: what its buffer is sized by. */
    public static long generatorFePerTick() {
        return Math.max(1L, generatorJoulesPerTick() / joulesPerFe);
    }

    /** How many FE a Burner Generator buffers: {@link #GENERATOR_BUFFER_TICKS} ticks of its output. */
    public static long generatorBuffer() {
        return GENERATOR_BUFFER_TICKS * generatorFePerTick();
    }

    /** How many whole FE pay at least {@code joules}. */
    public static long feFor(long joules) {
        return joules <= 0L ? 0L : (joules + joulesPerFe - 1) / joulesPerFe;
    }
}
