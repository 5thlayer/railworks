// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.UUID;
import java.util.function.LongSupplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Burner Locomotive: the car of a {@link Train} that pulls it, for as long as its joule fuel
 * buffer pays a tick of full power, and brakes it, fuelled or not. Where it is and how fast it goes
 * are its Train's; which way it faces in its Train is the Train's too.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Locomotive implements Car {

    public static final String KIND = "locomotive";

    /** Blocks along the Line: the length of its {@link CarBody}. */
    public static final double LENGTH = CarBody.LENGTH;

    static final MapCodec<Locomotive> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ID_FIELD.forGetter(Locomotive::id),
            Codec.LONG.fieldOf("fuel").forGetter(l -> l.fuel.storedJoules()),
            Codec.LONG.fieldOf("last_lit").forGetter(l -> l.fuel.lastLitJoules())
    ).apply(i, Locomotive::restore));

    private final UUID id;
    private final FuelBuffer fuel = new FuelBuffer();

    private Locomotive(UUID id) {
        this.id = id;
    }

    /** A Burner Locomotive with an empty buffer. */
    public static Locomotive burner() {
        return new Locomotive(UUID.randomUUID());
    }

    static Locomotive restore(UUID id, long fuel, long lastLit) {
        Locomotive locomotive = new Locomotive(id);
        locomotive.fuel.load(fuel, lastLit);
        return locomotive;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public double mass() {
        return LocomotiveSpec.MASS_KG;
    }

    @Override
    public double length() {
        return LENGTH;
    }

    public FuelBuffer fuel() {
        return fuel;
    }

    /**
     * What it pulls with at full power, in kilogram blocks a tick squared: its own mass at the
     * configured acceleration, so a Train's acceleration is its pulling share of that.
     */
    public double pull() {
        return LocomotiveSpec.MASS_KG * LocomotiveSpec.acceleration();
    }

    /** Whether its buffer covers a tick of full power without lighting anything. */
    public boolean fuelled() {
        return fuel.storedJoules() >= LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER;
    }

    /**
     * Pays one tick of full power: the buffer first, lighting an item through {@code ignite} (the
     * item's joules, or 0 for none) only when the buffer cannot cover the tick.
     */
    boolean pay(LongSupplier ignite) {
        long perTick = LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER;
        if (fuel.drawTick(perTick)) {
            return true;
        }
        long lit = ignite.getAsLong();
        if (lit <= 0L) {
            return false;
        }
        fuel.light(lit);
        return fuel.drawTick(perTick);
    }

    @Override
    public String toString() {
        return "Locomotive[" + id + "]";
    }
}
