// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** The boxes each car is drawn from, and what a rider in the Locomotive's cab sees past them. */
class CarBodyTest {

    /** A car's box, as {@code CarEntity}'s: a block across, 2.75 tall, from the floor's underside. */
    private static final double HALF_WIDTH = 0.5;
    private static final double HEIGHT = 2.75;

    @Test
    void eachBodyStaysWithinItsCarsBoxAndLength() {
        check(CarBody.LOCOMOTIVE, 2.8);
        check(CarBody.ELECTRIC, 2.8);
        check(CarBody.CARGO, 1.8);
        check(CarBody.FLUID, 1.8);
    }

    @Test
    void aLocomotiveIsThreeBlocksAndACargoCarTwoWithAGapAtEachCoupling() {
        assertEquals(3.0, Locomotive.LENGTH);
        assertEquals(2.0, CargoCar.LENGTH);
        assertEquals(2.8, CarBody.LOCOMOTIVE.length(), 1e-9);
        assertEquals(1.8, CarBody.CARGO.length(), 1e-9);
    }

    private static void check(CarBody body, double length) {
        for (CarBody.Box box : boxes(body)) {
            assertTrue(box.minX() >= -HALF_WIDTH && box.maxX() <= HALF_WIDTH
                    && box.minY() >= 0.0 && box.maxY() <= HEIGHT
                    && box.minZ() >= -length / 2.0 && box.maxZ() <= length / 2.0, box + " leaves the car's box");
        }
    }

    @Test
    void noTwoFacesOfABodyShareAPlane() {
        for (CarBody body : List.of(CarBody.LOCOMOTIVE, CarBody.CARGO, CarBody.FLUID)) {
            List<CarBody.Box> boxes = boxes(body);
            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    String shared = sharedPlane(boxes.get(i), boxes.get(j));
                    if (shared != null) {
                        fail(boxes.get(i) + " and " + boxes.get(j) + " share the plane " + shared);
                    }
                }
            }
        }
    }

    /** Every box drawn for the body: its painted parts and its chests. */
    private static List<CarBody.Box> boxes(CarBody body) {
        return Stream.concat(body.parts().stream().map(CarBody.Part::box), body.chests().stream()).toList();
    }

    /** The plane two boxes have faces in that overlap, by more than an edge, or null. */
    private static String sharedPlane(CarBody.Box a, CarBody.Box b) {
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
    void theRiderSitsForwardInTheCabBehindTheDashAndTheFrontsFrame() {
        CarBody.Point eye = CarBody.eye();
        assertTrue(eye.z() > 0.0, "the rider does not sit forward of the car's middle");
        for (CarBody.Part part : CarBody.LOCOMOTIVE.parts()) {
            CarBody.Box box = part.box();
            assertTrue(!contains(box, eye), "the rider's eye is inside " + part);
            // The dash, the roof's edge and the nose, across the car in front of the rider.
            if (box.minX() < 0.0 && box.maxX() > 0.0 && box.minZ() > 0.0) {
                assertTrue(box.minZ() > eye.z(), "the rider's eye is not behind " + part);
            }
        }
    }

    @Test
    void theRiderSeesOutOfTheOpenFrontLookingAheadOrALittleUpOrDown() {
        // Against the car's floor: down to 20 degrees, up to 15 (a rider looking level on a descent
        // at the grade limit), and 20 either side.
        for (int pitch = -20; pitch <= 15; pitch++) {
            for (int yaw = -20; yaw <= 20; yaw++) {
                CarBody.Box hit = firstSeen(pitch, yaw);
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
                    CarBody.Box hit = firstSeen(pitch, side * yaw);
                    if (hit != null) {
                        fail("looking " + pitch + " up and " + side * yaw + " across, the rider sees " + hit);
                    }
                }
            }
        }
    }

    @Test
    void theRoofAndTheSidesBehindTheCabBlockTheRidersView() {
        for (int pitch = 40; pitch <= 90; pitch += 10) {
            assertTrue(firstSeen(pitch, 0) != null, "looking " + pitch + " up, the rider sees out through the roof");
        }
        for (int yaw : new int[] {150, -150}) {
            assertTrue(firstSeen(0, yaw) != null, "looking " + yaw + " across, the rider sees out behind the cab");
        }
    }

    /** The first box the rider sees, looking that far up and across from ahead, or null. */
    private static CarBody.Box firstSeen(double pitchDegrees, double yawDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        CarBody.Point eye = CarBody.eye();
        return firstHit(boxes(CarBody.LOCOMOTIVE), new double[] {eye.x(), eye.y(), eye.z()},
                new double[] {Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)});
    }

    /** The first box a ray meets within reach of the car's box, or null. */
    private static CarBody.Box firstHit(List<CarBody.Box> boxes, double[] from, double[] direction) {
        double reach = 4.0;
        CarBody.Box nearest = null;
        double nearestT = Double.POSITIVE_INFINITY;
        for (CarBody.Box box : boxes) {
            double t = entry(box, from, direction, reach);
            if (t < nearestT) {
                nearestT = t;
                nearest = box;
            }
        }
        return nearest;
    }

    /** Where along the ray it enters the box, by the slab method, or infinity if it misses. */
    private static double entry(CarBody.Box box, double[] from, double[] direction, double reach) {
        double[] min = {box.minX(), box.minY(), box.minZ()};
        double[] max = {box.maxX(), box.maxY(), box.maxZ()};
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

    private static boolean contains(CarBody.Box box, CarBody.Point at) {
        return at.x() > box.minX() && at.x() < box.maxX() && at.y() > box.minY() && at.y() < box.maxY()
                && at.z() > box.minZ() && at.z() < box.maxZ();
    }

    @Test
    void theCargoCarShowsAChestSetIntoEachSide() {
        List<CarBody.Box> chests = CarBody.CARGO.chests();
        assertEquals(2, chests.size(), "a chest on each side");
        for (CarBody.Box chest : chests) {
            assertEquals(chest.maxY() - chest.minY(), chest.maxX() - chest.minX(), 1e-9, chest + " is not a cube");
            assertEquals(chest.maxY() - chest.minY(), chest.maxZ() - chest.minZ(), 1e-9, chest + " is not a cube");
            // Looking at the car square on from beside it, at the chest's middle, the chest is the first
            // thing seen, and something stands proud of it around the recess.
            double side = Math.signum(chest.minX() + chest.maxX());
            double y = (chest.minY() + chest.maxY()) / 2.0;
            double z = (chest.minZ() + chest.maxZ()) / 2.0;
            double[] inward = {-side, 0.0, 0.0};
            assertEquals(chest, firstHit(boxes(CarBody.CARGO), new double[] {side, y, z}, inward), "the chest is hidden");
            CarBody.Box frame = firstHit(boxes(CarBody.CARGO), new double[] {side, chest.maxY() + 0.03, z}, inward);
            assertTrue(frame != null && Math.abs(side > 0 ? frame.maxX() : frame.minX()) > Math.abs(side > 0 ? chest.maxX() : chest.minX()),
                    "nothing frames the chest above it");
        }
    }
}
