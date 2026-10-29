// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where Rail is a Straight leg: straight, and square to the blocks. */
class StraightLegTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(10, 5, 0);
    private static final Arm IN_LINE = new Arm(20, 5, 0);
    private static final Arm OFF_LINE = new Arm(18, 5, 6);

    private final RailNetwork network = new RailNetwork();

    private Span string(Arm one, Arm other) {
        Span span = Span.of(one, other, 10);
        network.add(span);
        return span;
    }

    /** {@code fromA} along {@code span} from its first Arm, heading for its second. */
    private static Place along(Span span, double fromA) {
        return new Place(span, span.b(), span.length() - fromA);
    }

    @Test
    void aSpanWithALinesEndAtEachArmIsStraight() {
        assertTrue(StraightLeg.is(network, string(A, B)));
    }

    @Test
    void spansCarryingOnInLineAreStraight() {
        Span ab = string(A, B);
        Span on = string(B, IN_LINE);
        assertTrue(StraightLeg.is(network, ab));
        assertTrue(StraightLeg.is(network, on));
    }

    @Test
    void aBendAtAnArmBendsBothSpansThere() {
        Span ab = string(A, B);
        Span off = string(B, OFF_LINE);
        assertFalse(StraightLeg.straight(network, ab));
        assertFalse(StraightLeg.straight(network, off));
    }

    @Test
    void aSpanRunningOnIntoABentSpanShorterThanTheSwingClearLengthIsNoStraightLeg() {
        // A car's body swinging round the bend at its far end reaches the row beside the leg's end.
        Span ab = string(A, B);
        Arm short3 = new Arm(10 + Clearance.swingClearLength() - 1, 5, 0);
        string(B, short3);
        string(short3, new Arm(short3.x() + 5, 5, 5));
        assertTrue(StraightLeg.straight(network, ab));
        assertFalse(StraightLeg.is(network, ab));
    }

    @Test
    void aSpanRunningOnIntoABentSpanOfTheSwingClearLengthIsAStraightLeg() {
        Span ab = string(A, B);
        Arm four = new Arm(10 + Clearance.swingClearLength(), 5, 0);
        string(B, four);
        string(four, new Arm(four.x() + 5, 5, 5));
        assertTrue(StraightLeg.is(network, ab));
    }

    @Test
    void aBendTwoArmsAwayLeavesASpanStraight() {
        Span ab = string(A, B);
        string(B, IN_LINE);
        string(IN_LINE, new Arm(26, 5, 6));
        assertTrue(StraightLeg.is(network, ab));
    }

    @Test
    void aSpanDoublingBackIsABend() {
        Span ab = string(A, B);
        string(B, new Arm(4, 5, 0));
        assertFalse(StraightLeg.straight(network, ab));
    }

    @Test
    void aStraightDiagonalIsNotSquareToTheBlocks() {
        Span diagonal = string(A, new Arm(8, 5, 8));
        assertTrue(StraightLeg.straight(network, diagonal));
        assertFalse(StraightLeg.is(network, diagonal));
    }

    @Test
    void aClimbingSpanAlongTheBlocksLoads() {
        assertTrue(StraightLeg.is(network, string(A, new Arm(10, 7, 0))));
    }

    @Test
    void aBodyAcrossAnArmNeedsBothSpansStraight() {
        Span ab = string(A, B);
        string(B, IN_LINE);
        assertTrue(StraightLeg.under(network, along(ab, 9.5), 1.8));
        network.remove(B, IN_LINE);
        string(B, OFF_LINE);
        assertFalse(StraightLeg.under(network, along(ab, 9.5), 1.8));
        assertFalse(StraightLeg.under(network, along(ab, 2.0), 1.8));
    }

    @Test
    void aBodyStoppedAtALinesEndIsAskedOnlyOfTheRailUnderIt() {
        // A to B bends at A; B on to a line's end a block further is straight.
        string(A, B);
        string(A, new Arm(0, 5, 6));
        Arm end = new Arm(11, 5, 0);
        Span last = string(B, end);
        // A car stopped with its centre at the line's end reaches 0.9 back, all on the last Span.
        assertTrue(StraightLeg.under(network, new Place(last, end, 0.0), 1.8));
        assertFalse(StraightLeg.under(network, new Place(last, end, 0.0), 2.4));
    }

    /** A Station in the row beside the Rail, {@code x} blocks along it, on the side z is 2. */
    private static Station beside(int x, int y) {
        return new Station(x, y, 2, "Mine");
    }

    @Test
    void aStationByAStraightLegIsAtOne() {
        string(A, B);
        assertTrue(StraightLeg.at(network, beside(5, 7)));
    }

    @Test
    void aStationByASpanBentThroughAnArmIsNot() {
        string(A, B);
        string(B, OFF_LINE);
        assertFalse(StraightLeg.at(network, beside(5, 7)));
    }

    @Test
    void aStationNearAPylonIsNotWhereItsStretchReachesABentSpanBeyond() {
        // A to B straight; B on to IN_LINE in line, but bent at IN_LINE.
        string(A, B);
        string(B, IN_LINE);
        string(IN_LINE, new Arm(26, 5, 6));
        // By B the stretch reaches a block on over it; a block short, it ends at B.
        assertFalse(StraightLeg.at(network, beside(10, 7)));
        assertTrue(StraightLeg.at(network, beside(9, 7)));
    }

    @Test
    void aStationByADiagonalIsNot() {
        // No block's centre is a whole two off a diagonal, so none is in its row, by it at all.
        Span diagonal = string(A, new Arm(8, 5, 8));
        Station station = new Station(4, 7, 2, "Mine");
        assertTrue(station.foot(network).isEmpty());
        assertFalse(StraightLeg.at(network, station));
        assertTrue(StraightLeg.straight(network, diagonal));
    }

    @Test
    void aStationByAClimbingLegIsAtOne() {
        string(A, new Arm(10, 7, 0));
        // Halfway up the Rail is a block higher, so the row is too.
        assertTrue(StraightLeg.at(network, beside(5, 8)));
    }

    @Test
    void aStationByNoRailIsNot() {
        assertFalse(StraightLeg.at(network, beside(5, 7)));
    }
}
