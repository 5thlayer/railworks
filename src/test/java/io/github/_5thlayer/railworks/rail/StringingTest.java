// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static io.github._5thlayer.railworks.rail.Clearance.Blocks.OPEN;
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
        SpanRules.configureGrade(SpanRules.DEFAULT_GRADE_LIMIT);
        SpanRules.configureBend(SpanRules.DEFAULT_BEND_LIMIT);
    }

    private static int limit() {
        return (int) SpanRules.lengthLimit();
    }

    @Test
    void stringsASpanAndSpendsItsCost() {
        var outcome = Stringing.click(network, OPEN, A, new Arm(3, 5, 4), 100);
        assertEquals(Stringing.Result.STRUNG, outcome.result());
        assertEquals(5, outcome.rail());
        assertTrue(network.contains(A, new Arm(3, 5, 4)));
    }

    @Test
    void theClicksOrderMakesNoDifference() {
        Stringing.click(network, OPEN, B, A, 100);
        assertTrue(network.contains(A, B));
    }

    @Test
    void refusesWithoutEnoughRailAndStringsNothing() {
        var outcome = Stringing.click(network, OPEN, A, B, 9);
        assertEquals(Stringing.Result.SHORT_OF_RAIL, outcome.result());
        assertEquals(0, outcome.rail());
        assertFalse(network.contains(A, B));
    }

    @Test
    void justEnoughRailStrings() {
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, A, B, 10).result());
    }

    @Test
    void refusesPastTheLengthLimit() {
        var far = new Arm(limit() + 1, 5, 0);
        var outcome = Stringing.click(network, OPEN, A, far, 1000);
        assertEquals(Stringing.Result.TOO_LONG, outcome.result());
        assertFalse(network.contains(A, far));
    }

    @Test
    void stringsAtTheLengthLimit() {
        var edge = new Arm(limit(), 5, 0);
        var outcome = Stringing.click(network, OPEN, A, edge, 1000);
        assertEquals(Stringing.Result.STRUNG, outcome.result());
        assertEquals(limit(), outcome.rail());
    }

    @Test
    void aConfiguredLimitMovesBothTheRefusalAndTheCost() {
        SpanRules.configure(100.0);
        var outcome = Stringing.click(network, OPEN, A, new Arm(80, 5, 0), 1000);
        assertEquals(Stringing.Result.STRUNG, outcome.result());
        assertEquals(80, outcome.rail());
        SpanRules.configure(10.0);
        assertEquals(Stringing.Result.TOO_LONG, Stringing.click(network, OPEN, A, new Arm(11, 5, 0), 1000).result());
    }

    @Test
    void aFractionalLimitAllowsALengthUnderItAndChargesItsCeiling() {
        SpanRules.configure(10.5);
        var outcome = Stringing.click(network, OPEN, A, new Arm(10, 5, 3), 1000);
        // sqrt(109) = 10.44, inside 10.5, costing 11
        assertEquals(Stringing.Result.STRUNG, outcome.result());
        assertEquals(11, outcome.rail());
    }

    @Test
    void refusesPastTheGradeLimitAndStringsNothing() {
        var steep = new Arm(8, 8, 0);
        var outcome = Stringing.click(network, OPEN, A, steep, 1000);
        assertEquals(Stringing.Result.TOO_STEEP, outcome.result());
        assertEquals(0, outcome.rail());
        assertFalse(network.contains(A, steep));
    }

    @Test
    void stringsAtTheGradeLimit() {
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, A, new Arm(8, 7, 0), 1000).result());
    }

    @Test
    void aConfiguredGradeLimitMovesTheRefusal() {
        var steep = new Arm(8, 8, 0);
        SpanRules.configureGrade(0.5);
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, A, steep, 1000).result());
    }

    @Test
    void refusesABendPastTheLimitAtTheHeldArm() {
        network.add(Span.of(A, B, 10));
        var corner = new Arm(10, 5, 10);
        var outcome = Stringing.click(network, OPEN, B, corner, 1000);
        assertEquals(Stringing.Result.TOO_SHARP, outcome.result());
        assertEquals(0, outcome.rail());
        assertFalse(network.contains(B, corner));
    }

    @Test
    void refusesABendPastTheLimitAtTheClickedArm() {
        network.add(Span.of(A, B, 10));
        assertEquals(Stringing.Result.TOO_SHARP, Stringing.click(network, OPEN, new Arm(10, 5, 10), B, 1000).result());
    }

    @Test
    void aBendWithinTheLimitStringsAtEitherArm() {
        network.add(Span.of(A, B, 10));
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, B, new Arm(17, 5, 7), 1000).result());
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, new Arm(-7, 5, 7), A, 1000).result());
    }

    @Test
    void aBendIsJudgedAgainstTheSpanAtTheArmNotTheOneAcross() {
        // Through A the line runs on from B; through B it doubles back, which is refused
        network.add(Span.of(A, B, 10));
        assertEquals(Stringing.Result.TOO_SHARP, Stringing.click(network, OPEN, B, new Arm(-5, 5, 0), 1000).result());
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, A, new Arm(-5, 5, 0), 1000).result());
    }

    @Test
    void aConfiguredBendLimitMovesTheRefusal() {
        network.add(Span.of(A, B, 10));
        SpanRules.configureBend(90.0);
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, B, new Arm(10, 5, 10), 1000).result());
    }

    @Test
    void theGradeAndBendChecksComeBeforeTheRailCheck() {
        assertEquals(Stringing.Result.TOO_STEEP, Stringing.click(network, OPEN, A, new Arm(0, 9, 1), 0).result());
        network.add(Span.of(A, B, 10));
        assertEquals(Stringing.Result.TOO_SHARP, Stringing.click(network, OPEN, B, new Arm(10, 5, 10), 0).result());
    }

    @Test
    void aCutIsAllowedWhateverTheLimitsSayNow() {
        var steep = new Arm(2, 9, 0);
        network.add(Span.of(A, steep, 5));
        assertEquals(Stringing.Result.CUT, Stringing.click(network, OPEN, A, steep, 0).result());
    }

    @Test
    void anArmTakesTwoSpans() {
        Stringing.click(network, OPEN, B, A, 100);
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, B, C, 100).result());
        assertEquals(2, network.spansAt(B).size());
    }

    @Test
    void aThirdSpanIsRefusedAtEitherEnd() {
        Stringing.click(network, OPEN, B, A, 100);
        Stringing.click(network, OPEN, B, C, 100);
        var fromFull = Stringing.click(network, OPEN, B, D, 100);
        assertEquals(Stringing.Result.ARM_FULL, fromFull.result());
        var toFull = Stringing.click(network, OPEN, D, B, 100);
        assertEquals(Stringing.Result.ARM_FULL, toFull.result());
        assertEquals(2, network.spansAt(B).size());
        assertFalse(network.contains(B, D));
    }

    @Test
    void theFullnessCheckComesBeforeTheRailCheck() {
        Stringing.click(network, OPEN, B, A, 100);
        Stringing.click(network, OPEN, B, C, 100);
        assertEquals(Stringing.Result.ARM_FULL, Stringing.click(network, OPEN, B, D, 0).result());
    }

    @Test
    void theSameArmCancelsAndChangesNothing() {
        var outcome = Stringing.click(network, OPEN, A, A, 100);
        assertEquals(Stringing.Result.CANCELLED, outcome.result());
        assertEquals(0, outcome.rail());
        assertTrue(network.all().isEmpty());
    }

    @Test
    void repeatingTheGestureCutsAndRefundsWhatWasPaid() {
        Stringing.click(network, OPEN, A, B, 100);
        var outcome = Stringing.click(network, OPEN, B, A, 0);
        assertEquals(Stringing.Result.CUT, outcome.result());
        assertEquals(10, outcome.rail());
        assertFalse(network.contains(A, B));
    }

    @Test
    void aCutIsAllowedWhileBothArmsAreFull() {
        Stringing.click(network, OPEN, B, A, 100);
        Stringing.click(network, OPEN, B, C, 100);
        assertEquals(Stringing.Result.CUT, Stringing.click(network, OPEN, A, B, 0).result());
    }

    @Test
    void aCutRefundsTheRailPaidNotTheLengthNow() {
        network.add(Span.of(A, B, 7));
        assertEquals(7, Stringing.click(network, OPEN, A, B, 0).rail());
    }

    @Test
    void decidingChangesNothing() {
        assertEquals(Stringing.Result.STRUNG, Stringing.decide(network, OPEN, A, B, 100).result());
        assertTrue(network.all().isEmpty());
        network.add(Span.of(A, B, 10));
        assertEquals(Stringing.Result.CUT, Stringing.decide(network, OPEN, A, B, 0).result());
        assertTrue(network.contains(A, B));
    }

    @Test
    void theSlackIsHeldColourWhileAimingAtNothingOrTheHeldArm() {
        assertEquals(Stringing.Tint.HELD, Stringing.tintFor(network, OPEN, A, null, 100));
        assertEquals(Stringing.Tint.HELD, Stringing.tintFor(network, OPEN, A, A, 100));
    }

    @Test
    void theSlackIsGreenWhereTheClickWouldString() {
        assertEquals(Stringing.Tint.STRING, Stringing.tintFor(network, OPEN, A, B, 10));
    }

    @Test
    void theSlackIsOrangeWhereTheClickWouldCut() {
        network.add(Span.of(A, B, 10));
        assertEquals(Stringing.Tint.CUT, Stringing.tintFor(network, OPEN, A, B, 0));
    }

    @Test
    void theSlackIsRedForEveryRefusal() {
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, OPEN, A, B, 9));
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, OPEN, A, new Arm(limit() + 1, 5, 0), 1000));
        Stringing.click(network, OPEN, B, A, 100);
        Stringing.click(network, OPEN, B, C, 100);
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, OPEN, B, D, 100));
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, OPEN, D, new Arm(30, 25, 0), 1000));
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, OPEN, C, new Arm(20, 5, 10), 1000));
    }

    @Test
    void everyRefusalIsARefusalAndNothingElseIs() {
        for (Stringing.Result result : Stringing.Result.values()) {
            boolean refused = switch (result) {
                case STRUNG, CUT, CANCELLED -> false;
                default -> true;
            };
            assertEquals(refused, Stringing.refused(result));
        }
    }

    @Test
    void thePreviewAndTheClickAgreeOnEveryAim() {
        network.add(Span.of(A, new Arm(-10, 5, 0), 10));
        Arm[] aims = {A, B, C, D, new Arm(limit() + 1, 5, 0), new Arm(3, 5, 4), new Arm(3, 12, 0), new Arm(-5, 5, 2)};
        for (int rail : new int[] {0, 5, 100}) {
            for (Arm aim : aims) {
                var copy = new RailNetwork();
                network.all().forEach(copy::add);
                var decided = Stringing.decide(network, OPEN, A, aim, rail);
                assertEquals(decided, Stringing.click(copy, OPEN, A, aim, rail));
            }
        }
    }

    @Test
    void refusesASpanWhoseClearanceIsFilledAndSpendsNothing() {
        Clearance.Blocks inTheWay = (x, y, z) -> x == 5 && y == 7 && z == 0;
        var outcome = Stringing.click(network, inTheWay, A, B, 100);
        assertEquals(Stringing.Result.BLOCKED, outcome.result());
        assertEquals(0, outcome.rail());
        assertFalse(network.contains(A, B));
        assertTrue(Stringing.refused(Stringing.Result.BLOCKED));
        assertEquals(Stringing.Tint.REFUSED, Stringing.tintFor(network, inTheWay, A, B, 100));
    }

    @Test
    void aBlockBesideTheRailOrUnderItLeavesTheClearanceClear() {
        Clearance.Blocks besideAndUnder = (x, y, z) -> (y == 6 && z == 1) || (y == 6 && z == -1) || y == 5;
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, besideAndUnder, A, B, 100).result());
    }

    @Test
    void refusesASpanThatWouldBendTheSpanAlreadyAtItsArmIntoABlock() {
        Stringing.click(network, OPEN, A, B, 100);
        // South of the straight Span from A, which bends that way once a Span turns north off B.
        Clearance.Blocks south = (x, y, z) -> z < 0 && x < 8;
        Arm north = new Arm(20, 5, 8);
        assertEquals(Stringing.Result.STRUNG, Stringing.decide(new RailNetwork(), south, B, north, 100).result());
        assertEquals(Stringing.Result.BLOCKED, Stringing.click(network, south, B, north, 100).result());
        assertFalse(network.contains(B, north));
    }

    @Test
    void aSpanIsCutWhateverFillsItsClearance() {
        Stringing.click(network, OPEN, A, B, 100);
        assertEquals(Stringing.Result.CUT, Stringing.click(network, (x, y, z) -> true, A, B, 0).result());
    }

    @Test
    void raisingAnArmWithNoSpansBreaksNothing() {
        assertTrue(Stringing.raise(network, (x, y, z) -> true, A, new Arm(0, 6, 0)).isEmpty());
    }

    @Test
    void raisingWithinTheLimitsBreaksNothingAndChangesNothing() {
        Stringing.click(network, OPEN, A, B, 100);
        assertTrue(Stringing.raise(network, OPEN, A, new Arm(0, 6, 0)).isEmpty());
        assertTrue(network.contains(A, B));
    }

    @Test
    void raisingASpanPastTheGradeLimitIsTooSteep() {
        Arm high = new Arm(8, 7, 0);
        assertEquals(Stringing.Result.STRUNG, Stringing.click(network, OPEN, A, high, 100).result());
        var broken = Stringing.raise(network, OPEN, high, new Arm(8, 8, 0));
        assertEquals(Stringing.Result.TOO_STEEP, broken.orElseThrow().result());
        assertTrue(network.contains(A, high));
    }

    @Test
    void raisingAnArmPastTheBendLimitThroughItIsTooSharp() {
        Stringing.click(network, OPEN, A, B, 100);
        Stringing.click(network, OPEN, B, C, 100);
        SpanRules.configureBend(10.0);
        // A block up over ten across either way bends the line about 11 degrees through B.
        assertEquals(Stringing.Result.TOO_SHARP, Stringing.raise(network, OPEN, B, new Arm(10, 6, 0)).orElseThrow().result());
    }

    @Test
    void raisingAnArmPastTheBendLimitAtTheFarArmIsTooSharp() {
        Stringing.click(network, OPEN, A, B, 100);
        Stringing.click(network, OPEN, B, C, 100);
        SpanRules.configureBend(10.0);
        assertTrue(Stringing.raise(network, OPEN, A, new Arm(0, 6, 0)).isEmpty());
        assertEquals(Stringing.Result.TOO_SHARP, Stringing.raise(network, OPEN, A, new Arm(0, 7, 0)).orElseThrow().result());
    }

    @Test
    void raisingASpanIntoABlockIsBlocked() {
        Stringing.click(network, OPEN, A, B, 100);
        // Over the cars of the Span as it is, in the way of those of the Span raised a block at A.
        Clearance.Blocks over = (x, y, z) -> y >= 9;
        assertTrue(Clearance.firstFilled(Clearance.along(network.curve(network.get(A, B))), over).isEmpty());
        var broken = Stringing.raise(network, over, A, new Arm(0, 6, 0)).orElseThrow();
        assertEquals(Stringing.Result.BLOCKED, broken.result());
        assertTrue(broken.blocked().y() >= 9);
    }
}
