// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StringingTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(10, 5, 0);
    private static final Arm C = new Arm(20, 5, 0);
    private static final Arm D = new Arm(30, 5, 0);

    private final RailNetwork network = new RailNetwork();

    @AfterEach
    void resetLimit() {
        SpanRules.configure(SpanRules.DEFAULT_LENGTH_LIMIT);
    }

    private static int limit() {
        return (int) SpanRules.lengthLimit();
    }

    @Test
    void stringsASpanAndSpendsItsCost() {
        var outcome = Stringing.click(network, A, new Arm(3, 5, 4), 100);
        assertEquals(Stringing.Result.STRUNG, outcome.result());
        assertEquals(5, outcome.rail());
        assertTrue(network.contains(A, new Arm(3, 5, 4)));
    }

    @Test
    void theClicksOrderMakesNoDifference() {
        Stringing.click(network, B, A, 100);
        assertTrue(network.contains(A, B));
    }

    @Test
    void refusesWithoutEnoughRailAndStringsNothing() {
        var outcome = Stringing.click(network, A, B, 9);
        assertEquals(Stringing.Result.SHORT_OF_RAIL, outcome.result());
        assertEquals(0, outcome.rail());
        assertFalse(network.contains(A, B));
    }

    @Test
    void justEnoughRailStrings() {
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, A, B, 10).result());
    }

    @Test
    void refusesPastTheLengthLimit() {
        var far = new Arm(limit() + 1, 5, 0);
        var outcome = Stringing.click(network, A, far, 1000);
        assertEquals(Stringing.Result.TOO_LONG, outcome.result());
        assertFalse(network.contains(A, far));
    }

    @Test
    void stringsAtTheLengthLimit() {
        var edge = new Arm(limit(), 5, 0);
        var outcome = Stringing.click(network, A, edge, 1000);
        assertEquals(Stringing.Result.STRUNG, outcome.result());
        assertEquals(limit(), outcome.rail());
    }

    @Test
    void aConfiguredLimitMovesBothTheRefusalAndTheCost() {
        SpanRules.configure(100.0);
        var outcome = Stringing.click(network, A, new Arm(80, 5, 0), 1000);
        assertEquals(Stringing.Result.STRUNG, outcome.result());
        assertEquals(80, outcome.rail());
        SpanRules.configure(10.0);
        assertEquals(Stringing.Result.TOO_LONG, Stringing.click(network, A, new Arm(11, 5, 0), 1000).result());
    }

    @Test
    void aFractionalLimitAllowsALengthUnderItAndChargesItsCeiling() {
        SpanRules.configure(10.5);
        var outcome = Stringing.click(network, A, new Arm(10, 5, 3), 1000);
        // sqrt(109) = 10.44, inside 10.5, costing 11
        assertEquals(Stringing.Result.STRUNG, outcome.result());
        assertEquals(11, outcome.rail());
    }

    @Test
    void anArmTakesTwoSpans() {
        Stringing.click(network, B, A, 100);
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, B, C, 100).result());
        assertEquals(2, network.spansAt(B).size());
    }

    @Test
    void aThirdSpanIsRefusedAtEitherEnd() {
        Stringing.click(network, B, A, 100);
        Stringing.click(network, B, C, 100);
        var fromFull = Stringing.click(network, B, D, 100);
        assertEquals(Stringing.Result.ARM_FULL, fromFull.result());
        var toFull = Stringing.click(network, D, B, 100);
        assertEquals(Stringing.Result.ARM_FULL, toFull.result());
        assertEquals(2, network.spansAt(B).size());
        assertFalse(network.contains(B, D));
    }

    @Test
    void theFullnessCheckComesBeforeTheRailCheck() {
        Stringing.click(network, B, A, 100);
        Stringing.click(network, B, C, 100);
        assertEquals(Stringing.Result.ARM_FULL, Stringing.click(network, B, D, 0).result());
    }

    @Test
    void theSameArmCancelsAndChangesNothing() {
        var outcome = Stringing.click(network, A, A, 100);
        assertEquals(Stringing.Result.CANCELLED, outcome.result());
        assertEquals(0, outcome.rail());
        assertTrue(network.all().isEmpty());
    }

    @Test
    void repeatingTheGestureCutsAndRefundsWhatWasPaid() {
        Stringing.click(network, A, B, 100);
        var outcome = Stringing.click(network, B, A, 0);
        assertEquals(Stringing.Result.CUT, outcome.result());
        assertEquals(10, outcome.rail());
        assertFalse(network.contains(A, B));
    }

    @Test
    void aCutIsAllowedWhileBothArmsAreFull() {
        Stringing.click(network, B, A, 100);
        Stringing.click(network, B, C, 100);
        assertEquals(Stringing.Result.CUT, Stringing.click(network, A, B, 0).result());
    }

    @Test
    void aCutRefundsTheRailPaidNotTheLengthNow() {
        network.add(Span.of(A, B, 7));
        assertEquals(7, Stringing.click(network, A, B, 0).rail());
    }

    @Test
    void decidingChangesNothing() {
        assertEquals(Stringing.Result.STRUNG, Stringing.decide(network, A, B, 100).result());
        assertTrue(network.all().isEmpty());
        network.add(Span.of(A, B, 10));
        assertEquals(Stringing.Result.CUT, Stringing.decide(network, A, B, 0).result());
        assertTrue(network.contains(A, B));
    }

    @Test
    void theSlackIsHeldColourWhileAimingAtNothingOrTheHeldArm() {
        assertEquals(Stringing.Tint.HELD, Stringing.tintFor(network, A, null, 100));
        assertEquals(Stringing.Tint.HELD, Stringing.tintFor(network, A, A, 100));
    }

    @Test
    void theSlackIsGreenWhereTheClickWouldString() {
        assertEquals(Stringing.Tint.STRING, Stringing.tintFor(network, A, B, 10));
    }

    @Test
    void theSlackIsOrangeWhereTheClickWouldCut() {
        network.add(Span.of(A, B, 10));
        assertEquals(Stringing.Tint.CUT, Stringing.tintFor(network, A, B, 0));
    }

    @Test
    void theSlackIsRedForEveryRefusal() {
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, A, B, 9));
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, A, new Arm(limit() + 1, 5, 0), 1000));
        Stringing.click(network, B, A, 100);
        Stringing.click(network, B, C, 100);
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, B, D, 100));
    }

    @Test
    void thePreviewAndTheClickAgreeOnEveryAim() {
        Arm[] aims = {A, B, C, D, new Arm(limit() + 1, 5, 0), new Arm(3, 5, 4)};
        for (int rail : new int[] {0, 5, 100}) {
            for (Arm aim : aims) {
                var copy = new RailNetwork();
                network.all().forEach(copy::add);
                var decided = Stringing.decide(network, A, aim, rail);
                assertEquals(decided, Stringing.click(copy, A, aim, rail));
            }
        }
    }
}
