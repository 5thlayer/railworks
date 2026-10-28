// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * A car that carries goods: a {@link CargoCar} items, a {@link FluidCar} one fluid. Each trades only
 * with a hatch of its own kind.
 *
 * <p>Pure: no Minecraft types.
 */
public sealed interface GoodsCar extends Car permits CargoCar, FluidCar {

    /** The kind of goods it carries. */
    Goods.Kind holds();
}
