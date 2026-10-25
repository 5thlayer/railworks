// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where a hopper loads or unloads a Cargo car: over its roof and under the Rail. */
class HopperSpotsTest {

    /** A car's box, as {@code CarEntity}'s: 2.75 blocks tall. */
    private static final double HEIGHT = 2.75;

    /** The floor of a car on a level Span between Pylon tops at y 5. */
    private static final double FLOOR = 5 + RailGeometry.RIDE_Y;

    @Test
    void onALevelSpanTheFillingHopperIsOverTheRoofAndTheEmptyingOneAtThePylonTops() {
        HopperSpots spots = HopperSpots.of(12.5, FLOOR, 3.5, HEIGHT);
        // The car stands from 6.15 to 8.9: its roof is in block 8, its floor in block 6.
        assertEquals(new HopperSpots.Spot(12, 9, 3), spots.fill());
        assertEquals(new HopperSpots.Spot(12, 5, 3), spots.empty());
    }

    @Test
    void bothAreInTheColumnHoldingTheCarsCentre() {
        HopperSpots spots = HopperSpots.of(-0.3, FLOOR, 7.99, HEIGHT);
        assertEquals(-1, spots.fill().x());
        assertEquals(7, spots.fill().z());
        assertEquals(spots.fill().x(), spots.empty().x());
        assertEquals(spots.fill().z(), spots.empty().z());
    }

    @Test
    void eachHoppersNeighbourOverlapsTheCarRatherThanTouchingIt() {
        // A roof and a floor on block boundaries: the hoppers' neighbours must still reach into the box.
        for (double floor : new double[] {6.0, 6.25, 6.999}) {
            for (double height : new double[] {HEIGHT, 3.0}) {
                HopperSpots spots = HopperSpots.of(0.5, floor, 0.5, height);
                double top = floor + height;
                int intoFromAbove = spots.fill().y() - 1;
                int intoFromBelow = spots.empty().y() + 1;
                assertTrue(intoFromAbove < top && intoFromAbove + 1 > floor, "filling at " + floor + "+" + height);
                assertTrue(spots.fill().y() >= top, "the filling hopper is out of the car at " + floor + "+" + height);
                assertTrue(intoFromBelow < top && intoFromBelow + 1 > floor, "emptying at " + floor + "+" + height);
                assertTrue(spots.empty().y() + 1 <= floor, "the emptying hopper is out of the car at " + floor);
            }
        }
    }
}
