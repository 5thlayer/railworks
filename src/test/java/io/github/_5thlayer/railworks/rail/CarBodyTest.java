// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** The boxes each car is drawn from, and what a rider in the Locomotive's cab sees past them. */
class CarBodyTest {

    /** A car's box, as {@code CarEntity}'s: two blocks across, 2.75 tall, from the floor's underside. */
    private static final double HALF_WIDTH = 1.0;
    private static final double HEIGHT = 2.75;
    private static final List<CarBody> LOCOMOTIVES = List.of(CarBody.LOCOMOTIVE, CarBody.ELECTRIC);

    @Test
    void eachBodyStaysWithinItsCarsBoxAndLength() {
        for (CarBody body : List.of(CarBody.LOCOMOTIVE, CarBody.ELECTRIC, CarBody.CARGO, CarBody.FLUID)) {
            check(body, 4.8);
        }
    }

    @Test
    void everyBodyIsTwoBlocksWideItsSidesOutAtTheCarsBox() {
        assertEquals(2.0, CarBody.WIDTH);
        for (CarBody body : List.of(CarBody.LOCOMOTIVE, CarBody.ELECTRIC, CarBody.CARGO, CarBody.FLUID)) {
            for (double side : new double[] {1.0, -1.0}) {
                double reach = boxes(body).stream().mapToDouble(box -> side > 0 ? box.maxX() : -box.minX()).max().orElseThrow();
                assertTrue(reach > HALF_WIDTH - 0.05, "a body reaches only " + reach + " to side " + side);
            }
        }
    }

    @Test
    void everyCarIsFiveBlocksWithAGapAtEachCoupling() {
        assertEquals(5.0, Locomotive.LENGTH);
        assertEquals(5.0, CargoCar.LENGTH);
        assertEquals(CargoCar.LENGTH, FluidCar.LENGTH);
        for (CarBody body : List.of(CarBody.LOCOMOTIVE, CarBody.ELECTRIC, CarBody.CARGO, CarBody.FLUID)) {
            assertEquals(4.8, body.length(), 1e-9);
        }
    }

    @Test
    void theLocomotivesCabEngineRoomAndBayMeetOnWholeBlocksTheEngineRoomOnTheMiddleOne() {
        // The car's middle block runs from -0.5 to 0.5, and its others a whole block on from there.
        assertEquals(0.5, CarBody.CAB_BACK);
        assertEquals(-0.5, CarBody.BAY_FRONT);
        // The Engine room is closed: from the bay's spot, looking ahead, it is the first thing seen.
        CarBody.Box seen = firstHit(boxes(CarBody.LOCOMOTIVE),
                new double[] {CarBody.BAY_SPOT.x(), CarBody.BAY_SPOT.y() + 1.0, CarBody.BAY_SPOT.z()}, new double[] {0.0, 0.0, 1.0});
        assertTrue(seen != null && seen.minZ() >= CarBody.BAY_FRONT - 0.05 && seen.maxZ() <= CarBody.CAB_BACK + 0.05,
                "the bay looks ahead into " + seen);
    }

    @Test
    void theDriverSitsInTheCabAndTheRiderStandsInTheBay() {
        double half = CarBody.LOCOMOTIVE.length() / 2.0;
        assertTrue(CarBody.CAB_SEAT.z() > CarBody.CAB_BACK && CarBody.CAB_SEAT.z() < half, "the Driver is not in the cab");
        assertTrue(CarBody.BAY_SPOT.z() < CarBody.BAY_FRONT && CarBody.BAY_SPOT.z() > -half, "the Rider is not in the bay");
        assertEquals((CarBody.BAY_FRONT - half) / 2.0, CarBody.BAY_SPOT.z(), 1e-9, "the Rider stands mid-bay");
    }

    @Test
    void everyRoofIsRoundedAlongItsTopEdgesInHalfPixelSteps() {
        double row = 1.0 / 32.0;
        for (CarBody body : List.of(CarBody.LOCOMOTIVE, CarBody.ELECTRIC, CarBody.CARGO)) {
            List<CarBody.Box> roof = body.parts().stream()
                    .filter(part -> part.panel() == CarBody.Panel.ROOF).map(CarBody.Part::box).toList();
            // A roof is a slab, and over it the rows its long edges are rounded in, each half a pixel
            // high, give or take its reach into the one under it, and drawn in by whole half pixels.
            List<CarBody.Box> slabs = roof.stream().filter(box -> box.maxY() - box.minY() > 2.0 * row).toList();
            assertFalse(slabs.isEmpty(), "a car has no roof");
            for (CarBody.Box slab : slabs) {
                List<CarBody.Box> rows = roof.stream()
                        .filter(box -> box != slab && box.minY() > slab.maxY() - row && box.minY() < slab.maxY() + 4.0 * row
                                && box.minZ() < slab.maxZ() && box.maxZ() > slab.minZ())
                        .toList();
                assertFalse(rows.isEmpty(), slab + " is not rounded");
                for (CarBody.Box step : rows) {
                    assertEquals(row, step.maxY() - step.minY(), 0.0025, step + " is not a half-pixel row");
                    double inset = slab.maxX() - step.maxX();
                    assertTrue(inset > 0.0, step + " is not drawn in from " + slab);
                    assertEquals(Math.round(inset / row), inset / row, 1e-9, step + " steps in by " + inset);
                }
            }
        }
    }

    @Test
    void eachLocomotivesNoseIsRoundedAlongItsTopAndFrontInHalfPixelSteps() {
        double row = 1.0 / 32.0;
        double front = CarBody.LOCOMOTIVE.length() / 2.0;
        for (CarBody body : LOCOMOTIVES) {
            CarBody.Panel band = body == CarBody.ELECTRIC ? CarBody.Panel.ELECTRIC_NOSE : CarBody.Panel.BURNER_NOSE;
            List<CarBody.Box> nose = body.parts().stream().filter(part -> part.panel() == band).map(CarBody.Part::box).toList();
            CarBody.Box slab = nose.stream().filter(box -> box.maxY() - box.minY() > 2.0 * row).findFirst().orElseThrow();
            List<CarBody.Box> rows = nose.stream().filter(box -> box != slab).toList();
            assertFalse(rows.isEmpty(), band + " is not rounded");
            for (CarBody.Box step : rows) {
                assertEquals(row, step.maxY() - step.minY(), 0.0025, step + " is not a half-pixel row");
                double across = slab.maxX() - step.maxX();
                double along = slab.maxZ() - step.maxZ();
                assertTrue(across > 0.0 && along > 0.0, step + " is not drawn in from " + slab + " along its top and front");
                assertEquals(across, along, 1e-9, step + " is drawn in unevenly");
                assertEquals(Math.round(across / row), across / row, 1e-9, step + " steps in by " + across);
            }
            assertTrue(slab.maxZ() > front - 0.05, "the nose is not at the front");
        }
    }

    @Test
    void theBurnerCarriesAStackAndAFireboxEachSideAndTheElectricACoilGrilleEachSideAndNoStack() {
        assertEquals(2, count(CarBody.LOCOMOTIVE, CarBody.Panel.STACK), "the Burner's stack and its cap");
        assertEquals(2, count(CarBody.LOCOMOTIVE, CarBody.Panel.FIREBOX), "a firebox door each side");
        assertEquals(2, sidesShowing(CarBody.LOCOMOTIVE, CarBody.Panel.FIREBOX), "a firebox door each side");
        assertEquals(0, count(CarBody.LOCOMOTIVE, CarBody.Panel.COIL));
        assertEquals(0, count(CarBody.ELECTRIC, CarBody.Panel.STACK), "the Electric has a stack");
        assertEquals(0, count(CarBody.ELECTRIC, CarBody.Panel.FIREBOX));
        assertEquals(2, count(CarBody.ELECTRIC, CarBody.Panel.COIL), "a coil grille each side");
        assertEquals(2, sidesShowing(CarBody.ELECTRIC, CarBody.Panel.COIL), "a coil grille each side");
        for (CarBody body : LOCOMOTIVES) {
            for (CarBody.Part part : body.parts()) {
                CarBody.Box box = part.box();
                switch (part.panel()) {
                    // On the Engine room, over the car's middle block.
                    case STACK, FIREBOX, COIL -> assertTrue(box.minZ() > CarBody.BAY_FRONT && box.maxZ() < CarBody.CAB_BACK,
                            box + " is off the Engine room");
                    default -> { }
                }
            }
        }
        // The stack stands on the roof, its top short of the car's.
        double stackTop = CarBody.LOCOMOTIVE.parts().stream().filter(part -> part.panel() == CarBody.Panel.STACK)
                .mapToDouble(part -> part.box().maxY()).max().orElseThrow();
        assertTrue(stackTop > 2.5 && stackTop <= HEIGHT, "the stack's top is at " + stackTop);
    }

    private static long count(CarBody body, CarBody.Panel panel) {
        return body.parts().stream().filter(part -> part.panel() == panel).count();
    }

    /** How many sides of the car {@code panel} shows on. */
    private static long sidesShowing(CarBody body, CarBody.Panel panel) {
        return body.parts().stream().filter(part -> part.panel() == panel)
                .map(part -> Math.signum(part.box().minX() + part.box().maxX()))
                .filter(side -> side != 0.0).distinct().count();
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
        for (CarBody body : List.of(CarBody.LOCOMOTIVE, CarBody.ELECTRIC, CarBody.CARGO, CarBody.FLUID)) {
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

    /** Every box drawn for the body: its painted parts and its crates. */
    private static List<CarBody.Box> boxes(CarBody body) {
        return Stream.concat(body.parts().stream().map(CarBody.Part::box), body.crates().stream()).toList();
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
        assertTrue(eye.z() > CarBody.CAB_BACK, "the rider does not sit in the cab");
        for (CarBody body : LOCOMOTIVES) {
            for (CarBody.Part part : body.parts()) {
                CarBody.Box box = part.box();
                assertTrue(!contains(box, eye), "the rider's eye is inside " + part);
                // The dash, the roof's edge and the nose, across the car ahead of the cab's back wall.
                if (box.minX() < 0.0 && box.maxX() > 0.0 && box.minZ() > CarBody.CAB_BACK + 0.08) {
                    assertTrue(box.minZ() > eye.z(), "the rider's eye is not behind " + part);
                }
            }
        }
    }

    @Test
    void theRiderSeesOutOfTheOpenFrontLookingAheadOrALittleUpOrDown() {
        // Against the car's floor: down to 20 degrees, up to 15 (a rider looking level on a descent
        // at the grade limit), and 20 either side.
        for (CarBody body : LOCOMOTIVES) {
            for (int pitch = -20; pitch <= 15; pitch++) {
                for (int yaw = -20; yaw <= 20; yaw++) {
                    CarBody.Box hit = firstSeen(body, pitch, yaw);
                    if (hit != null) {
                        fail("looking " + pitch + " up and " + yaw + " across, the rider sees " + hit);
                    }
                }
            }
        }
    }

    @Test
    void theRiderSeesOutOfTheCabsSideWindowsLookingAcrossOrALittleUpOrDown() {
        for (CarBody body : LOCOMOTIVES) {
            for (int pitch = -20; pitch <= 15; pitch++) {
                for (int yaw = 60; yaw <= 120; yaw++) {
                    for (int side : new int[] {1, -1}) {
                        CarBody.Box hit = firstSeen(body, pitch, side * yaw);
                        if (hit != null) {
                            fail("looking " + pitch + " up and " + side * yaw + " across, the rider sees " + hit);
                        }
                    }
                }
            }
        }
    }

    @Test
    void theRoofAndTheSidesBehindTheCabBlockTheRidersView() {
        for (CarBody body : LOCOMOTIVES) {
            for (int pitch = 40; pitch <= 90; pitch += 10) {
                assertTrue(firstSeen(body, pitch, 0) != null, "looking " + pitch + " up, the rider sees out through the roof");
            }
            for (int yaw : new int[] {150, -150}) {
                assertTrue(firstSeen(body, 0, yaw) != null, "looking " + yaw + " across, the rider sees out behind the cab");
            }
        }
    }

    /** The first box of {@code body} the rider sees, looking that far up and across from ahead, or null. */
    private static CarBody.Box firstSeen(CarBody body, double pitchDegrees, double yawDegrees) {
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        CarBody.Point eye = CarBody.eye();
        return firstHit(boxes(body), new double[] {eye.x(), eye.y(), eye.z()},
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
    void theCargoCarShowsACrateSetIntoEachSideOverEachOfItsThreeInnerBlocks() {
        List<CarBody.Box> crates = CarBody.CARGO.crates();
        assertEquals(6, crates.size(), "three crates a side");
        for (double at : new double[] {-1.0, 0.0, 1.0}) {
            for (double side : new double[] {1.0, -1.0}) {
                assertEquals(1, crates.stream().filter(crate -> (crate.minZ() + crate.maxZ()) / 2.0 == at
                        && Math.signum(crate.minX() + crate.maxX()) == side).count(), "a crate on side " + side + " at " + at);
            }
        }
        for (CarBody.Box crate : crates) {
            assertEquals(crate.maxY() - crate.minY(), crate.maxX() - crate.minX(), 1e-9, crate + " is not a cube");
            assertEquals(crate.maxY() - crate.minY(), crate.maxZ() - crate.minZ(), 1e-9, crate + " is not a cube");
            // Looking at the car square on from beside it, at the crate's middle, the crate is the first
            // thing seen, and something stands proud of it around the recess.
            double side = Math.signum(crate.minX() + crate.maxX());
            double y = (crate.minY() + crate.maxY()) / 2.0;
            double z = (crate.minZ() + crate.maxZ()) / 2.0;
            double[] inward = {-side, 0.0, 0.0};
            assertEquals(crate, firstHit(boxes(CarBody.CARGO), new double[] {side, y, z}, inward), "the crate is hidden");
            CarBody.Box frame = firstHit(boxes(CarBody.CARGO), new double[] {side, crate.maxY() + 0.03, z}, inward);
            assertTrue(frame != null && Math.abs(side > 0 ? frame.maxX() : frame.minX()) > Math.abs(side > 0 ? crate.maxX() : crate.minX()),
                    "nothing frames the crate above it");
            for (double along : new double[] {crate.minZ() - 0.1, crate.maxZ() + 0.1}) {
                CarBody.Box beside = firstHit(boxes(CarBody.CARGO), new double[] {side, y, along}, inward);
                assertTrue(beside != null && Math.abs(side > 0 ? beside.maxX() : beside.minX()) > Math.abs(side > 0 ? crate.maxX() : crate.minX()),
                        "nothing frames the crate beside it");
            }
        }
    }

    @Test
    void theFluidCarsTankIsACylinderSteppedEveryHalfPixelWithinItsOldOutlineFlatAcrossItsBelt() {
        double row = 1.0 / 32.0;
        // The tank's own rows, the dome on top left out: along most of the car.
        List<CarBody.Box> tank = CarBody.FLUID.parts().stream().filter(part -> part.panel() == CarBody.Panel.TANK)
                .map(CarBody.Part::box).filter(box -> box.maxZ() - box.minZ() > 4.0).sorted(Comparator.comparingDouble(CarBody.Box::minY))
                .toList();
        assertTrue(tank.size() > 10, "the tank is not stepped: " + tank.size() + " rows");
        CarBody.Box belt = tank.stream().max(Comparator.comparingDouble(CarBody.Box::maxX)).orElseThrow();
        assertTrue(belt.minY() <= 1.12 && belt.maxY() >= 2.18, "the belt does not carry the Sight glasses: " + belt);
        for (int i = 0; i < tank.size(); i++) {
            CarBody.Box box = tank.get(i);
            // Within the old tank: a wide box from 1.0 to 2.3 up, 0.95 either side, crossing a tall one
            // from 0.72 to 2.58, 0.7 either side.
            boolean inWide = box.maxX() <= 0.95 && box.minY() >= 1.0 - 0.002 && box.maxY() <= 2.3 + 0.002;
            boolean inTall = box.maxX() <= 0.7 && box.minY() >= 0.72 - 0.002 && box.maxY() <= 2.58 + 0.002;
            assertTrue(inWide || inTall, box + " leaves the old tank's outline");
            assertEquals(Math.round((box.maxY() - box.minY()) / row), (box.maxY() - box.minY()) / row, 0.1,
                    box + " is not a whole number of half-pixel rows");
            // Widening up to the belt and narrowing above it.
            if (i > 0) {
                CarBody.Box under = tank.get(i - 1);
                if (box.maxY() <= belt.maxY()) {
                    assertTrue(box.maxX() > under.maxX(), box + " does not widen over " + under);
                } else {
                    assertTrue(box.maxX() < under.maxX(), box + " does not narrow over " + under);
                }
            }
        }
        // Each ring stands out round the tank all the way round: past every row of the tank it covers.
        double front = CarBody.FLUID.length() / 2.0;
        List<CarBody.Box> ring = CarBody.FLUID.parts().stream().filter(part -> part.panel() == CarBody.Panel.TRIM)
                .map(CarBody.Part::box).filter(box -> box.minZ() > front - 0.6 && box.maxZ() < front - 0.4).toList();
        assertTrue(ring.size() == tank.size(), "the ring is not round the tank");
        for (CarBody.Box tankRow : tank) {
            for (CarBody.Box ringRow : ring) {
                // Past the hair each reaches into the next.
                if (ringRow.minY() < tankRow.maxY() - 0.01 && ringRow.maxY() > tankRow.minY() + 0.01) {
                    assertTrue(ringRow.maxX() > tankRow.maxX(), ringRow + " leaves " + tankRow + " showing through it");
                }
            }
        }
    }

    @Test
    void theFluidCarHasASightGlass0Point8LongAcrossTheMiddleOfEachSideOfItsTank() {
        List<CarBody.SightGlass> glasses = CarBody.FLUID.sightGlasses();
        assertEquals(2, glasses.size(), "a Sight glass a side");
        for (double side : new double[] {1.0, -1.0}) {
            assertEquals(1, glasses.stream().filter(glass -> glass.side() == side).count(), "a Sight glass on side " + side);
        }
        // The tank's belt runs from 1.08 to 2.22 up, and its rings stand 0.44 to 0.5 in from its ends.
        double trims = CarBody.FLUID.length() / 2.0 - 0.5;
        for (CarBody.SightGlass glass : glasses) {
            CarBody.Box pane = glass.pane();
            assertTrue(pane.minZ() > -trims && pane.maxZ() < trims, pane + " reaches past the end trims");
            assertEquals(0.8, pane.maxZ() - pane.minZ(), 1e-9, pane + " is not 0.8 long");
            assertEquals(0.0, pane.minZ() + pane.maxZ(), 1e-9, pane + " is not across the car's middle");
            assertTrue(pane.minY() > 1.0 && pane.maxY() < 2.3 && pane.maxY() - pane.minY() > 0.9,
                    pane + " is not across most of the tank's height");
            // Looking at the car square on from beside it, at the pane's middle, the pane is the first thing seen.
            double[] from = {glass.side(), (pane.minY() + pane.maxY()) / 2.0, 0.0};
            assertEquals(pane, firstHit(boxes(CarBody.FLUID), from, new double[] {-glass.side(), 0.0, 0.0}), "the pane is hidden");
        }
        for (CarBody body : List.of(CarBody.LOCOMOTIVE, CarBody.ELECTRIC, CarBody.CARGO)) {
            assertTrue(body.sightGlasses().isEmpty(), "a car that is no Fluid car has a Sight glass");
        }
    }

    @Test
    void aSightGlassIsFilledFromItsBottomAsFarAsTheTankIsFull() {
        CarBody.SightGlass glass = CarBody.FLUID.sightGlasses().getFirst();
        double bottom = glass.pane().minY();
        double top = glass.pane().maxY();
        assertEquals(bottom, glass.surface(0, 160_000), 1e-9, "an empty tank");
        assertEquals(top, glass.surface(160_000, 160_000), 1e-9, "a full tank");
        assertEquals((bottom + top) / 2.0, glass.surface(80_000, 160_000), 1e-9, "a half-full tank");
        assertEquals(bottom + (top - bottom) / 4.0, glass.surface(3, 12), 1e-9, "a quarter-full tank");
        // Smooth, not in steps: a millibucket in shows, and a millibucket short of full is short of the top.
        assertTrue(glass.surface(1, 160_000) > bottom, "a millibucket shows nothing");
        assertTrue(glass.surface(159_999, 160_000) < top, "a millibucket short of full shows full");
        assertEquals(top, glass.surface(200_000, 160_000), 1e-9, "past full");
        assertEquals(bottom, glass.surface(0, 0), 1e-9, "a tank of no capacity");
    }
}
