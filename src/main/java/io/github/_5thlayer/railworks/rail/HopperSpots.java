// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Comparator;
import java.util.List;

/**
 * Where a hopper loads or unloads a Cargo car stopped at a Station: a block on either side of the
 * car in the row it rides in, whose hopper points across at the car, and the block under the Rail,
 * whose hopper pulls from the car above it. A hopper reaches an entity in the block it pushes into
 * or pulls from. Nothing in the car's row has a face to click to point a hopper sideways, so a
 * player sets a filling hopper by sneak-using one at the car, which puts it in the spot on their
 * side, pointing in; the emptying one stands on a block under it, as any hopper does.
 *
 * <p>Pure: no Minecraft types. It is asked with the car's floor, the centre of its box's underside
 * in world coordinates, its box's width and height, and its heading on the level, so the overlay,
 * the hopper placement and the game tests ask the same thing. The box is the entity's, which
 * neither pitches nor turns with the car, so on a grade or a bend it is still the box that counts.
 */
public record HopperSpots(List<Filling> filling, Spot emptying) {

    /** A block, by its position. */
    public record Spot(int x, int y, int z) {
    }

    /** A filling spot, and the step along x or z from it toward the car, which its hopper points. */
    public record Filling(Spot spot, int towardX, int towardZ) {
    }

    /**
     * The spots for a car whose box stands on {@code (x, floor, z)}, {@code width} across and
     * {@code height} tall, heading {@code (headingX, headingZ)}. The filling spots are across the
     * Rail from each other, along the level axis nearer square to the heading, each the first block
     * wholly clear of the box beside the floor's row; the emptying spot is under the lowest block the
     * box reaches into, in the column of its centre.
     */
    public static HopperSpots of(double x, double floor, double z, double width, double height,
                                 double headingX, double headingZ) {
        int row = (int) Math.floor(floor);
        double half = width / 2.0;
        boolean alongX = Math.abs(headingX) >= Math.abs(headingZ);
        int columnX = (int) Math.floor(x);
        int columnZ = (int) Math.floor(z);
        // Each hopper's neighbour must overlap the box, not only touch it, and the hopper must be clear of it.
        List<Filling> filling = alongX
                ? List.of(new Filling(new Spot(columnX, row, (int) Math.floor(z - half) - 1), 0, 1),
                        new Filling(new Spot(columnX, row, (int) Math.ceil(z + half)), 0, -1))
                : List.of(new Filling(new Spot((int) Math.floor(x - half) - 1, row, columnZ), 1, 0),
                        new Filling(new Spot((int) Math.ceil(x + half), row, columnZ), -1, 0));
        return new HopperSpots(filling, new Spot(columnX, row - 1, columnZ));
    }

    /** The filling spot on the side of the car nearer {@code (x, z)}, where a player stands. */
    public Filling fillingNearest(double x, double z) {
        return filling.stream()
                .min(Comparator.comparingDouble(f -> square(f.spot().x() + 0.5 - x) + square(f.spot().z() + 0.5 - z)))
                .orElseThrow();
    }

    private static double square(double d) {
        return d * d;
    }
}
