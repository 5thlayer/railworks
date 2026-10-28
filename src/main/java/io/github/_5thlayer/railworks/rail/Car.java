// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

/**
 * One car of a {@link Train}: what it weighs and how long it is along the Line, and the id its
 * entity finds it by in whatever Train it is coupled into. A {@link Locomotive} is the car that
 * pulls and brakes; a {@link CargoCar} carries items, and a {@link FluidCar} one fluid.
 *
 * <p>Pure: no Minecraft types.
 */
public sealed interface Car permits Locomotive, GoodsCar {

    /** A UUID as its string, the way a car's id is saved. */
    Codec<UUID> ID_CODEC = Codec.STRING.comapFlatMap(text -> {
        try {
            return DataResult.success(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "no car id " + text);
        }
    }, UUID::toString);

    /** Its id as a car saves it; a car saved before cars had ids is given a new one. */
    MapCodec<UUID> ID_FIELD = ID_CODEC.optionalFieldOf("id")
            .xmap(id -> id.orElseGet(UUID::randomUUID), Optional::of);

    /** A car saved with its kind, so a Train's cars load back as what they were. */
    Codec<Car> CODEC = Codec.STRING.comapFlatMap(
            kind -> fields(kind) != null ? DataResult.success(kind) : DataResult.error(() -> "no car kind " + kind),
            kind -> kind
    ).dispatch("kind", Car::kind, Car::fields);

    /**
     * A kind of car's fields, by the name it is saved under; null for no kind. A method, not a map:
     * each car's codec names {@link #ID_FIELD}, so the cars' classes initialise after this one.
     */
    static MapCodec<? extends Car> fields(String kind) {
        return switch (kind) {
            case Locomotive.KIND -> Locomotive.MAP_CODEC;
            case Locomotive.ELECTRIC_KIND -> Locomotive.ELECTRIC_MAP_CODEC;
            case CargoCar.KIND -> CargoCar.MAP_CODEC;
            case FluidCar.KIND -> FluidCar.MAP_CODEC;
            default -> null;
        };
    }

    /** Which car it is, whatever Train it is coupled into. */
    UUID id();

    /** The name it is saved under. */
    String kind();

    /** Kilograms. */
    double mass();

    /** Blocks along the Line, coupler to coupler. */
    double length();
}
