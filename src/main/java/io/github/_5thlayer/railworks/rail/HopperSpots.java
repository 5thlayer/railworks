// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * Where a hopper loads or unloads a Cargo car stopped at a Station: the block over the car's roof,
 * whose hopper points down into the car, and the block under the Rail, whose hopper pulls from the
 * car above it. A hopper reaches an entity in the block it pushes into or pulls from, and the car
 * rides in the row of blocks over the Pylon tops, where no block's face can be clicked to point a
 * hopper sideways into it: clicking the top or the underside of a block places a hopper pointing
 * down, so both spots are set up from above and below the car, in survival, with nothing to remove.
 *
 * <p>Pure: no Minecraft types. It is asked with the car's floor, the centre of its box's underside
 * in world coordinates, and its box's height, so the overlay and the game tests ask the same thing.
 * The box is the entity's, which neither pitches nor turns with the car, so on a grade or a bend
 * the spots are still the column over and under its centre; the Span need not be asked.
 */
public record HopperSpots(Spot fill, Spot empty) {

    /** A block, by its position. */
    public record Spot(int x, int y, int z) {
    }

    /**
     * The spots for a car whose box stands on {@code (x, floor, z)} and is {@code height} tall: both
     * in the column of blocks holding the box's centre line, the filling one the first block wholly
     * over the box, the emptying one the block under the lowest block the box reaches into.
     */
    public static HopperSpots of(double x, double floor, double z, double height) {
        int column = (int) Math.floor(x);
        int row = (int) Math.floor(z);
        // A hopper reaches the block next to it, so each block next to the box must overlap it, not
        // only touch it: the one under the filling hopper reaches above the roof's floor, and the
        // one over the emptying hopper reaches above the car's floor.
        int over = (int) Math.ceil(floor + height);
        int under = (int) Math.floor(floor) - 1;
        return new HopperSpots(new Spot(column, over, row), new Spot(column, under, row));
    }
}
