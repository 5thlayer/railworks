// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where Rail is a straight leg, square to the blocks, so a Cargo car loads there. */
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
        assertTrue(StraightLeg.loads(network, string(A, B)));
    }

    @Test
    void spansCarryingOnInLineAreStraight() {
        Span ab = string(A, B);
        Span on = string(B, IN_LINE);
        assertTrue(StraightLeg.loads(network, ab));
        assertTrue(StraightLeg.loads(network, on));
    }

    @Test
    void aBendAtAnArmBendsBothSpansThere() {
        Span ab = string(A, B);
        Span off = string(B, OFF_LINE);
        assertFalse(StraightLeg.straight(network, ab));
        assertFalse(StraightLeg.straight(network, off));
    }

    @Test
    void aBendTwoArmsAwayLeavesASpanStraight() {
        Span ab = string(A, B);
        string(B, IN_LINE);
        string(IN_LINE, new Arm(26, 5, 6));
        assertTrue(StraightLeg.loads(network, ab));
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
        assertFalse(StraightLeg.loads(network, diagonal));
    }

    @Test
    void aClimbingSpanAlongTheBlocksLoads() {
        assertTrue(StraightLeg.loads(network, string(A, new Arm(10, 7, 0))));
    }

    @Test
    void aBodyAcrossAnArmNeedsBothSpansStraight() {
        Span ab = string(A, B);
        Span on = string(B, IN_LINE);
        assertTrue(StraightLeg.under(network, along(ab, 9.5), 1.8));
        network.remove(B, IN_LINE);
        string(B, OFF_LINE);
        assertFalse(StraightLeg.under(network, along(ab, 9.5), 1.8));
        assertFalse(StraightLeg.under(network, along(ab, 2.0), 1.8));
        assertTrue(on.length() > 0.0);
    }

    @Test
    void eachCarOfATrainIsAskedWhereItStands() {
        // A to B straight; B on to IN_LINE bent at IN_LINE.
        string(A, B);
        Span bent = string(B, IN_LINE);
        string(IN_LINE, OFF_LINE);
        // The Locomotive just past B, on the bent Span; its Cargo car behind it, wholly before B.
        Train train = Train.of(along(bent, 0.5), java.util.List.of(Locomotive.burner(), CargoCar.create()));
        assertFalse(StraightLeg.under(network, train, train.cars().getFirst()));
        assertTrue(StraightLeg.under(network, train, train.cars().getLast()));
    }
}
