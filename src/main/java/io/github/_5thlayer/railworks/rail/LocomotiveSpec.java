// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * The Burner Locomotive's numbers. Where the suite has one it is taken from there: a fuel item's
 * burn tick is worth 2,500 J, as in Wireworks' Boiler, so coal's 1,600 ticks are Factorio's 4 MJ.
 * Where it has none, Factorio's prototype is the source:
 *
 * <ul>
 *   <li>600 kW at full power, which is 30,000 J a Minecraft tick (a twentieth of a second);
 *   <li>braking 10 kN on 2,000 kg, which is 5 blocks a second squared;
 *   <li>friction 0.5 kN on 2,000 kg, which is 0.25 blocks a second squared.
 * </ul>
 *
 * <p>Top speed is not Factorio's, which is 72 blocks a second (1.2 tiles a tick at 60 ticks a
 * second): that outruns chunk loading and elytra, so the default is 25 and the config allows 72.
 * Acceleration is the other number nothing gives: Factorio's depends on the Train's mass and a
 * power curve, so a constant half the braking is this Module's own pick.
 *
 * <p>Speeds are in blocks a second and accelerations in blocks a second squared for the config's
 * sake; the model works in ticks. Pure: no Minecraft types.
 */
public final class LocomotiveSpec {

    public static final int TICKS_PER_SECOND = 20;

    /** Factorio's locomotive draws 600 kW at full power. */
    public static final double POWER_WATTS = 600_000.0;
    public static final long JOULES_PER_TICK_AT_FULL_POWER = Math.round(POWER_WATTS / TICKS_PER_SECOND);

    public static final double DEFAULT_MAX_SPEED = 25.0;
    public static final double DEFAULT_ACCELERATION = 2.5;
    public static final double DEFAULT_BRAKING = 5.0;
    public static final double DEFAULT_FRICTION = 0.25;

    public static final long DEFAULT_JOULES_PER_BURN_TICK = 2_500L;

    private static volatile double maxSpeed = DEFAULT_MAX_SPEED / TICKS_PER_SECOND;
    private static volatile double acceleration = perTickSquared(DEFAULT_ACCELERATION);
    private static volatile double braking = perTickSquared(DEFAULT_BRAKING);
    private static volatile double friction = perTickSquared(DEFAULT_FRICTION);
    private static volatile long joulesPerBurnTick = DEFAULT_JOULES_PER_BURN_TICK;

    private LocomotiveSpec() {
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads. */
    public static void configure(double maxSpeedPerSecond, double accelerationPerSecondSquared,
                                 double brakingPerSecondSquared, double frictionPerSecondSquared) {
        if (maxSpeedPerSecond <= 0 || accelerationPerSecondSquared <= 0 || brakingPerSecondSquared <= 0
                || frictionPerSecondSquared < 0) {
            throw new IllegalArgumentException("locomotive drive " + maxSpeedPerSecond + " "
                    + accelerationPerSecondSquared + " " + brakingPerSecondSquared + " " + frictionPerSecondSquared);
        }
        maxSpeed = maxSpeedPerSecond / TICKS_PER_SECOND;
        acceleration = perTickSquared(accelerationPerSecondSquared);
        braking = perTickSquared(brakingPerSecondSquared);
        friction = perTickSquared(frictionPerSecondSquared);
    }

    public static void configureFuel(long joulesPerBurnTick) {
        if (joulesPerBurnTick <= 0L) {
            throw new IllegalArgumentException("joules per burn tick " + joulesPerBurnTick);
        }
        LocomotiveSpec.joulesPerBurnTick = joulesPerBurnTick;
    }

    private static double perTickSquared(double perSecondSquared) {
        return perSecondSquared / ((double) TICKS_PER_SECOND * TICKS_PER_SECOND);
    }

    /** Blocks a tick, either way. */
    public static double maxSpeed() {
        return maxSpeed;
    }

    /** Blocks a tick squared. */
    public static double acceleration() {
        return acceleration;
    }

    public static double braking() {
        return braking;
    }

    public static double friction() {
        return friction;
    }

    public static long joulesPerBurnTick() {
        return joulesPerBurnTick;
    }

    /** Joules a fuel of this many burn ticks banks; 0 for a stack that is not fuel. */
    public static long joules(int burnTicks) {
        return burnTicks <= 0 ? 0L : burnTicks * joulesPerBurnTick;
    }

    /** A speed in blocks a tick as blocks a second, for a gauge. */
    public static double blocksPerSecond(double blocksPerTick) {
        return blocksPerTick * TICKS_PER_SECOND;
    }
}
