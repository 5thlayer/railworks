// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Where a hopper loads or unloads a Cargo car: beside it at its floor, and under the Rail. */
class HopperSpotsTest {

    /** A car's box, as {@code CarEntity}'s: a block across, 2.75 tall. */
    private static final double WIDTH = 1.0;
    private static final double HEIGHT = 2.75;

    /** The floor of a car on a level Span between Pylon tops at y 5. */
    private static final double FLOOR = 5 + RailGeometry.RIDE_Y;

    @Test
    void onASpanAlongXTheFillingSpotsAreEitherSideInZAtTheFloorsRow() {
        HopperSpots spots = HopperSpots.of(12.5, FLOOR, 3.5, WIDTH, HEIGHT, 1.0, 0.0);
        assertEquals(List.of(
                new HopperSpots.Filling(new HopperSpots.Spot(12, 6, 2), 0, 1),
                new HopperSpots.Filling(new HopperSpots.Spot(12, 6, 4), 0, -1)), spots.filling());
        assertEquals(new HopperSpots.Spot(12, 5, 3), spots.emptying());
    }

    @Test
    void onASpanAlongZTheyAreEitherSideInX() {
        HopperSpots spots = HopperSpots.of(3.5, FLOOR, 12.5, WIDTH, HEIGHT, 0.0, -1.0);
        assertEquals(List.of(
                new HopperSpots.Filling(new HopperSpots.Spot(2, 6, 12), 1, 0),
                new HopperSpots.Filling(new HopperSpots.Spot(4, 6, 12), -1, 0)), spots.filling());
    }

    @Test
    void aDiagonalHeadingTakesTheAxisNearerSquareToIt() {
        assertEquals(1, HopperSpots.of(0.5, FLOOR, 0.5, WIDTH, HEIGHT, 0.8, 0.6).filling().getFirst().towardZ());
        assertEquals(1, HopperSpots.of(0.5, FLOOR, 0.5, WIDTH, HEIGHT, 0.6, 0.8).filling().getFirst().towardX());
    }

    @Test
    void theNearerSideIsThePlayers() {
        HopperSpots spots = HopperSpots.of(12.5, FLOOR, 3.5, WIDTH, HEIGHT, 1.0, 0.0);
        assertEquals(2, spots.fillingNearest(14.0, 0.5).spot().z());
        assertEquals(4, spots.fillingNearest(10.0, 7.0).spot().z());
    }

    @Test
    void eachHoppersNeighbourOverlapsTheBoxAndTheHopperIsClearOfIt() {
        // Off the block grid, as on a bend, and with floors on and off block boundaries.
        for (double z : new double[] {3.5, 3.2, 3.0, 3.99}) {
            for (double floor : new double[] {6.0, 6.25, 6.999}) {
                HopperSpots spots = HopperSpots.of(0.5, floor, z, WIDTH, HEIGHT, 1.0, 0.0);
                double minZ = z - WIDTH / 2.0;
                double maxZ = z + WIDTH / 2.0;
                for (HopperSpots.Filling f : spots.filling()) {
                    int hopper = f.spot().z();
                    int into = hopper + f.towardZ();
                    assertTrue(hopper + 1 <= minZ || hopper >= maxZ, "the hopper at z " + hopper + " is in a box at " + z);
                    assertTrue(into < maxZ && into + 1 > minZ, "z " + into + " misses a box at " + z);
                    assertTrue(f.spot().y() < floor + HEIGHT && f.spot().y() + 1 > floor, "the row misses a floor at " + floor);
                }
                int intoFromBelow = spots.emptying().y() + 1;
                assertTrue(intoFromBelow < floor + HEIGHT && intoFromBelow + 1 > floor, "emptying at " + floor);
                assertTrue(spots.emptying().y() + 1 <= floor, "the emptying hopper is in the car at " + floor);
            }
        }
    }
}
