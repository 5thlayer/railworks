// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The boxes each car is drawn from, and what a rider in the Locomotive's cab sees past them. */
class CarBodyTest {

    /** A car's box, as {@code CarEntity}'s: a block across, 2.75 tall, from the floor's underside. */
    private static final double HALF_WIDTH = 0.5;
    private static final double HEIGHT = 2.75;

    @Test
    void eachBodyStaysWithinItsCarsBoxAndLength() {
        check(CarBody.LOCOMOTIVE, Locomotive.LENGTH);
        check(CarBody.CARGO, CargoCar.LENGTH);
    }

    private static void check(CarBody body, double length) {
        for (CarBody.Part part : body.parts()) {
            assertTrue(part.minX() >= -HALF_WIDTH && part.maxX() <= HALF_WIDTH
                    && part.minY() >= 0.0 && part.maxY() <= HEIGHT
                    && part.minZ() >= -length / 2.0 && part.maxZ() <= length / 2.0, part + " leaves the car's box");
        }
    }

    @Test
    void noTwoFacesOfABodyShareAPlane() {
        for (CarBody body : List.of(CarBody.LOCOMOTIVE, CarBody.CARGO)) {
            List<CarBody.Part> parts = body.parts();
            for (int i = 0; i < parts.size(); i++) {
                for (int j = i + 1; j < parts.size(); j++) {
                    String shared = sharedPlane(parts.get(i), parts.get(j));
                    if (shared != null) {
                        fail(parts.get(i) + " and " + parts.get(j) + " share the plane " + shared);
                    }
                }
            }
        }
    }

    /** The plane two boxes have faces in that overlap, by more than an edge, or null. */
    private static String sharedPlane(CarBody.Part a, CarBody.Part b) {
        for (double x : new double[] {a.minX(), a.maxX()}) {
            if ((x == b.minX() || x == b.maxX()) && overlap(a.minY(), a.maxY(), b.minY(), b.maxY())
                    && overlap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ())) {
                return "x = " + x;
            }
        }
        for (double y : new double[] {a.minY(), a.maxY()}) {
            if ((y == b.minY() || y == b.maxY()) && overlap(a.minX(), a.maxX(), b.minX(), b.maxX())
                    && overlap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ())) {
                return "y = " + y;
            }
        }
        for (double z : new double[] {a.minZ(), a.maxZ()}) {
            if ((z == b.minZ() || z == b.maxZ()) && overlap(a.minX(), a.maxX(), b.minX(), b.maxX())
                    && overlap(a.minY(), a.maxY(), b.minY(), b.maxY())) {
                return "z = " + z;
            }
        }
        return null;
    }

    private static boolean overlap(double a0, double a1, double b0, double b1) {
        return Math.min(a1, b1) - Math.max(a0, b0) > 0.0;
    }

    @Test
    void theRidersEyeIsAPlayersSeatedEyeAboveTheSeat() {
        // A seated player's eye: their 1.62 eye height, less the 0.6 they sit below the seat.
        assertEquals(CarBody.SEAT_Y + 1.02, CarBody.eye()[1], 1e-9);
        assertEquals(0.0, CarBody.eye()[0]);
        assertEquals(CarBody.SEAT_Z, CarBody.eye()[2]);
    }

    @Test
    void theRiderSitsForwardInTheCabBehindTheFront() {
        assertTrue(CarBody.SEAT_Z > 0.0, "the seat is not forward of the car's middle");
        for (CarBody.Part part : CarBody.LOCOMOTIVE.parts()) {
            assertTrue(!contains(part, CarBody.eye()), "the rider's eye is inside " + part);
        }
    }

    @Test
    void theRiderSeesOutOfTheOpenFrontLookingAheadOrALittleUpOrDown() {
        // Against the car's floor: down to 20 degrees, up to 15 (a rider looking level on a descent
        // at the grade limit), and 20 either side.
        for (int pitch = -20; pitch <= 15; pitch++) {
            for (int yaw = -20; yaw <= 20; yaw++) {
                CarBody.Part hit = firstHit(pitch, yaw);
                if (hit != null) {
                    fail("looking " + pitch + " up and " + yaw + " across, the rider sees " + hit);
                }
            }
        }
    }

    @Test
    void theRiderSeesOutOfTheCabsSideWindowsLookingAcrossOrALittleUpOrDown() {
        for (int pitch = -20; pitch <= 15; pitch++) {
            for (int yaw = 60; yaw <= 120; yaw++) {
                for (int side : new int[] {1, -1}) {
                    CarBody.Part hit = firstHit(pitch, side * yaw);
                    if (hit != null) {
                        fail("looking " + pitch + " up and " + side * yaw + " across, the rider sees " + hit);
                    }
                }
            }
        }
    }

    @Test
    void theRiderDoesNotSeeThroughTheRoofOrTheSidesBehindThem() {
        assertTrue(firstHit(60, 0) != null, "the roof does not cover the rider");
        assertTrue(firstHit(0, 150) != null && firstHit(0, -150) != null, "the sides behind the cab are open");
    }

    /** The first part a ray from the rider's eye meets before it leaves the car's front, or null. */
    private static CarBody.Part firstHit(double pitchDegrees, double yawDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        double[] eye = CarBody.eye();
        double[] direction = {Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
        // Far enough to leave the car's box any way it looks.
        double reach = 4.0;
        CarBody.Part nearest = null;
        double nearestT = Double.POSITIVE_INFINITY;
        for (CarBody.Part part : CarBody.LOCOMOTIVE.parts()) {
            double t = entry(part, eye, direction, reach);
            if (t < nearestT) {
                nearestT = t;
                nearest = part;
            }
        }
        return nearest;
    }

    /** Where along the ray it enters the box, by the slab method, or infinity if it misses. */
    private static double entry(CarBody.Part part, double[] from, double[] direction, double reach) {
        double[] min = {part.minX(), part.minY(), part.minZ()};
        double[] max = {part.maxX(), part.maxY(), part.maxZ()};
        double near = 0.0;
        double far = reach;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1e-12) {
                if (from[axis] < min[axis] || from[axis] > max[axis]) {
                    return Double.POSITIVE_INFINITY;
                }
                continue;
            }
            double t0 = (min[axis] - from[axis]) / direction[axis];
            double t1 = (max[axis] - from[axis]) / direction[axis];
            near = Math.max(near, Math.min(t0, t1));
            far = Math.min(far, Math.max(t0, t1));
            if (near > far) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return near;
    }

    private static boolean contains(CarBody.Part part, double[] at) {
        return at[0] > part.minX() && at[0] < part.maxX() && at[1] > part.minY() && at[1] < part.maxY()
                && at[2] > part.minZ() && at[2] < part.maxZ();
    }

    @Test
    void theCargoCarShowsAChestSetIntoEachSide() {
        List<CarBody.Part> chests = CarBody.CARGO.parts().stream()
                .filter(part -> part.paint() == CarBody.Paint.CHEST).toList();
        assertEquals(2, chests.size(), "a chest on each side");
        double outerWall = CarBody.CARGO.parts().stream()
                .filter(part -> part.paint() == CarBody.Paint.SHELL && part.minY() > 0.6)
                .mapToDouble(CarBody.Part::maxX).max().orElseThrow();
        assertTrue(chests.stream().anyMatch(chest -> chest.maxX() < outerWall && chest.maxX() > outerWall - 0.1),
                "no chest is set just behind the right side's face");
        assertTrue(chests.stream().anyMatch(chest -> chest.minX() > -outerWall && chest.minX() < -outerWall + 0.1),
                "no chest is set just behind the left side's face");
    }
}
