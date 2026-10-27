// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImpactTest {

    private static final double EPS = 1e-9;

    @Test
    void aStoppedTrainDealsNoDamage() {
        assertEquals(0.0, Impact.damage(0.0), EPS);
    }

    @Test
    void theDamageGrowsWithTheSpeedEitherWay() {
        assertTrue(Impact.damage(2.0) > 0.0);
        assertTrue(Impact.damage(10.0) > Impact.damage(2.0));
        assertEquals(Impact.damage(10.0), Impact.damage(-10.0), EPS);
    }

    @Test
    void whatIsStruckIsPushedAsideToTheSideItStandsOn() {
        // Heading east; north is -z, so a mob north of the Rail goes north and one south goes south.
        Impact.Push north = Impact.push(0.5, 0.0, 0.0, 0.2, -0.3);
        Impact.Push south = Impact.push(0.5, 0.0, 0.0, 0.2, 0.3);
        assertTrue(north.dz() < 0.0, "north: " + north);
        assertTrue(south.dz() > 0.0, "south: " + south);
        assertEquals(Impact.ASIDE, -north.dz(), EPS);
    }

    @Test
    void whatIsStruckIsCarriedOnAtTheCarsSpeedAndLifted() {
        Impact.Push push = Impact.push(0.0, 0.0, -0.8, 0.3, 0.0);
        assertEquals(-0.8, push.dz(), EPS);
        assertEquals(Impact.ASIDE, push.dx(), EPS);
        assertTrue(push.dy() > 0.0);
    }

    @Test
    void oneRightOnTheLineIsStillPushedOffIt() {
        Impact.Push push = Impact.push(0.5, 0.0, 0.0, 0.0, 0.0);
        assertEquals(Impact.ASIDE, Math.abs(push.dz()), EPS);
    }
}
