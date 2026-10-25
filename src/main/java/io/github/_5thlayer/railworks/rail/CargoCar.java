// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Cargo car: a car of a {@link Train} that carries items. It neither pulls nor brakes, so it is
 * weight a Locomotive pulls and stops. What it holds is its entity's, since items are Minecraft's.
 *
 * <p>Pure: no Minecraft types.
 */
public final class CargoCar implements Car {

    public static final String KIND = "cargo_car";

    /** Factorio's cargo wagon weighs 1,000 kg, half a Locomotive. */
    public static final double MASS_KG = 1_000.0;

    /** Blocks along the Line: as long as a Locomotive, as Factorio's wagon is. */
    public static final double LENGTH = 1.8;

    static final MapCodec<CargoCar> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ID_FIELD.forGetter(CargoCar::id)
    ).apply(i, CargoCar::new));

    private final UUID id;

    private CargoCar(UUID id) {
        this.id = id;
    }

    /** A new, empty Cargo car. */
    public static CargoCar create() {
        return new CargoCar(UUID.randomUUID());
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
    public String toString() {
        return "CargoCar[" + id + "]";
    }
}
