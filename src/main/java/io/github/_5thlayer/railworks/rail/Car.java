// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * One car of a {@link Train}: what it weighs and how long it is along the Line. A
 * {@link Locomotive} is the car that pulls; Cargo cars (#23) join it here.
 *
 * <p>Pure: no Minecraft types.
 */
public sealed interface Car permits Locomotive {

    /** A car saved with its kind, so a Train's cars load back as what they were. */
    Codec<Car> CODEC = Codec.STRING.comapFlatMap(
            kind -> Locomotive.KIND.equals(kind) ? DataResult.success(kind) : DataResult.error(() -> "no car kind " + kind),
            kind -> kind
    ).dispatch("kind", Car::kind, kind -> Locomotive.MAP_CODEC);

    /** The name it is saved under. */
    String kind();

    /** Kilograms. */
    double mass();

    /** Blocks along the Line, coupler to coupler. */
    double length();
}
