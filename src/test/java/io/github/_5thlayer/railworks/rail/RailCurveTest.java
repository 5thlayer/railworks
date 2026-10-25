// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RailCurveTest {

    private static final double EPS = 1e-9;
    /** Slack for the curve's measured spacing, which is a polyline's. */
    private static final double NEAR = 1e-3;

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    /** Off B at 45 degrees from A to B: the sharpest bend the default limit takes. */
    private static final Arm C = new Arm(30, 5, 10);

    private final RailNetwork network = new RailNetwork();

    @Test
    void aSpanWithALineEndAtBothArmsIsStraight() {
        string(A, B);
        RailCurve curve = network.curve(network.get(A, B));
        RailCurve.Pose pose = curve.at(5.0);
        assertPoint(5.0, 5.0, 0.0, pose);
        assertHeading(1.0, 0.0, 0.0, pose);
        assertEquals(20.0, curve.arcLength(), EPS);
    }

    @Test
    void itRunsFromArmToArm() {
        string(A, B);
        string(B, C);
        RailCurve curve = network.curve(network.get(A, B));
        assertPoint(0.0, 5.0, 0.0, curve.at(0.0));
        assertPoint(20.0, 5.0, 0.0, curve.at(20.0));
    }

    @Test
    void atAnArmWithTwoSpansItRunsAlongTheirBisector() {
        string(A, B);
        string(B, C);
        double half = Math.toRadians(22.5);
        assertHeading(Math.cos(half), 0.0, Math.sin(half), network.curve(network.get(A, B)).at(20.0));
        assertHeading(Math.cos(half), 0.0, Math.sin(half), network.curve(network.get(B, C)).at(0.0));
    }

    @Test
    void atALineEndItHeadsStraightAtTheOtherArm() {
        string(A, B);
        string(B, C);
        assertHeading(1.0, 0.0, 0.0, network.curve(network.get(A, B)).at(0.0));
        double diagonal = Math.sqrt(0.5);
        assertHeading(diagonal, 0.0, diagonal, network.curve(network.get(B, C)).at(network.get(B, C).length()));
    }

    @Test
    void aBendLeavesTheStraightLineButKeepsItsLength() {
        string(A, B);
        string(B, C);
        Span span = network.get(A, B);
        RailCurve curve = network.curve(span);
        assertTrue(Math.abs(curve.at(10.0).z()) > 0.5, "the middle of a bent Span sits off the chord");
        assertTrue(curve.arcLength() > span.length());
        assertEquals(span.length(), curve.length(), EPS);
        assertEquals(20, SpanRules.cost(A, B));
    }

    @Test
    void equalStepsAlongItAreEqualStepsOnTheCurve() {
        string(A, B);
        string(B, C);
        RailCurve curve = network.curve(network.get(A, B));
        double step = curve.arcLength() / 10.0;
        for (int i = 0; i < 10; i++) {
            Place.Point from = curve.at(2.0 * i).point();
            Place.Point to = curve.at(2.0 * (i + 1)).point();
            assertEquals(step, distance(from, to), 0.01);
        }
    }

    @Test
    void aPlaceFacingTheFirstArmRunsBackAlongTheCurve() {
        string(A, B);
        string(B, C);
        Span span = network.get(A, B);
        RailCurve.Pose towardB = new Place(span, B, 6.0).pose(network);
        RailCurve.Pose towardA = new Place(span, A, 14.0).pose(network);
        assertEquals(towardB.x(), towardA.x(), EPS);
        assertEquals(towardB.z(), towardA.z(), EPS);
        assertEquals(-towardB.dx(), towardA.dx(), EPS);
        assertEquals(-towardB.dz(), towardA.dz(), EPS);
    }

    @Test
    void aTrainRunsThroughAnArmWithoutAJump() {
        string(A, B);
        string(B, C);
        Place before = new Place(network.get(A, B), B, 0.01);
        Place after = before.move(network, 0.02).place();
        assertEquals(network.get(B, C), after.span());
        RailCurve.Pose one = before.pose(network);
        RailCurve.Pose other = after.pose(network);
        assertTrue(distance(one.point(), other.point()) < 0.03, "no jump in position");
        // Only as far round as the bend turns it in a fiftieth of a block.
        assertEquals(one.dx(), other.dx(), 0.01);
        assertEquals(one.dz(), other.dz(), 0.01);
        // At the Arm itself, both Spans run the same way.
        RailCurve.Pose endOfOne = new Place(network.get(A, B), B, 0.0).pose(network);
        RailCurve.Pose startOfOther = new Place(network.get(B, C), C, network.get(B, C).length()).pose(network);
        assertEquals(endOfOne.dx(), startOfOther.dx(), EPS);
        assertEquals(endOfOne.dz(), startOfOther.dz(), EPS);
    }

    @Test
    void aClimbPitchesTheCurve() {
        Arm top = new Arm(20, 10, 0);
        string(A, top);
        RailCurve.Pose pose = network.curve(network.get(A, top)).at(10.0);
        assertTrue(pose.dy() > 0.0);
        assertEquals(5.0 / Math.sqrt(425.0), pose.dy(), EPS);
    }

    @Test
    void stringingTheNextSpanBendsTheOneBeforeIt() {
        string(A, B);
        RailCurve straight = network.curve(network.get(A, B));
        string(B, C);
        RailCurve bent = network.curve(network.get(A, B));
        assertNotEquals(straight.at(20.0).dz(), bent.at(20.0).dz());
        network.remove(B, C);
        assertEquals(0.0, network.curve(network.get(A, B)).at(20.0).dz(), EPS);
    }

    @Test
    void aPendingSpanTakesTheCurveItWouldOnStringing() {
        string(A, B);
        RailCurve pending = RailCurve.between(network, C, B);
        string(B, C);
        RailCurve strung = network.curve(network.get(B, C));
        // The pending curve runs from C to B, the strung one from B to C.
        double length = network.get(B, C).length();
        for (double fromC = 0.0; fromC <= length; fromC += length / 4.0) {
            RailCurve.Pose one = pending.at(fromC);
            RailCurve.Pose other = strung.at(length - fromC);
            assertEquals(one.x(), other.x(), NEAR);
            assertEquals(one.z(), other.z(), NEAR);
        }
    }

    @Test
    void aSpanAimMeasuresTheCurveNotTheChord() {
        string(A, B);
        string(B, C);
        Place.Point middle = network.curve(network.get(A, B)).at(10.0).point();
        // From below the curve's middle, well off the chord, looking straight up.
        var aim = SpanAim.nearest(network, middle.x(), 0, middle.z(), 0, 1, 0, 8, 0.25).orElseThrow();
        assertEquals(network.get(A, B), aim.span());
        assertEquals(10.0, aim.fromA(), 0.05);
    }

    private void string(Arm one, Arm other) {
        network.add(Span.of(one, other, SpanRules.cost(one, other)));
    }

    private static void assertPoint(double x, double y, double z, RailCurve.Pose pose) {
        assertEquals(x, pose.x(), EPS);
        assertEquals(y, pose.y(), EPS);
        assertEquals(z, pose.z(), EPS);
    }

    private static void assertHeading(double dx, double dy, double dz, RailCurve.Pose pose) {
        assertEquals(dx, pose.dx(), NEAR);
        assertEquals(dy, pose.dy(), NEAR);
        assertEquals(dz, pose.dz(), NEAR);
    }

    private static double distance(Place.Point one, Place.Point other) {
        double dx = one.x() - other.x();
        double dy = one.y() - other.y();
        double dz = one.z() - other.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
