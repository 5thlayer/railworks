// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Fluid car: a car of a {@link Train} that carries one fluid in its tank. It neither pulls nor
 * brakes, so it is weight a Locomotive pulls and stops, the same full or empty. What it holds is the
 * level's, since fluids are Minecraft's.
 *
 * <p>Pure: no Minecraft types.
 */
public final class FluidCar implements GoodsCar {

    public static final String KIND = "fluid_car";

    /** As much as a Cargo car, full or empty. */
    public static final double MASS_KG = CargoCar.MASS_KG;

    /** Blocks along the Line: as long as a Cargo car. */
    public static final double LENGTH = CargoCar.LENGTH;

    /** How many buckets its tank holds by default, as the server config starts from. */
    public static final int DEFAULT_TANK_BUCKETS = 64;

    static final MapCodec<FluidCar> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ID_FIELD.forGetter(FluidCar::id)
    ).apply(i, FluidCar::new));

    private final UUID id;

    private FluidCar(UUID id) {
        this.id = id;
    }

    /** A new Fluid car, its tank empty. */
    public static FluidCar create() {
        return new FluidCar(UUID.randomUUID());
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
        return MASS_KG;
    }

    @Override
    public double length() {
        return LENGTH;
    }

    @Override
    public Goods.Kind holds() {
        return Goods.Kind.FLUID;
    }

    @Override
    public String toString() {
        return "FluidCar[" + id + "]";
    }
}
