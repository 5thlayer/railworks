// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How far a hatch's Bellows or Loading arm is reached out to its car, tick by tick. */
class ReachTest {

    @Test
    void aCarThereAtTheFirstLookIsReachedAtOnceAndQuietly() {
        Reach reach = new Reach();
        assertEquals(Reach.Change.NONE, reach.tick(true));
        assertEquals(1.0, reach.out(0.0F), 1e-12);
        assertEquals(1.0, reach.out(1.0F), 1e-12);
        assertEquals(Reach.Change.NONE, reach.tick(true), "nothing left to reach");
    }

    @Test
    void noCarAtTheFirstLookIsNotReached() {
        Reach reach = new Reach();
        assertEquals(Reach.Change.NONE, reach.tick(false));
        assertEquals(0.0, reach.out(1.0F), 1e-12);
    }

    @Test
    void reachingOutTakesItsTicksEasingOutAndSaysWhenItStartsAndLands() {
        Reach reach = idle();
        assertEquals(Reach.Change.OUT, reach.tick(true), "the reach starts on the tick the car is there");
        // The tick it starts on is its first.
        for (int i = 2; i < Reach.OUT_TICKS; i++) {
            assertEquals(Reach.Change.NONE, reach.tick(true), "tick " + i);
            assertTrue(reach.out(1.0F) < 1.0, "landed early, at tick " + i);
        }
        assertEquals(Reach.Change.LANDED, reach.tick(true), "it lands on its last tick");
        assertEquals(1.0, reach.out(1.0F), 1e-12);
        assertEquals(Reach.Change.NONE, reach.tick(true), "it lands once");
    }

    @Test
    void reachingOutEasesOutFastFirstThenSlowing() {
        Reach reach = idle();
        reach.tick(true);
        double first = reach.out(1.0F);
        for (int i = 2; i <= Reach.OUT_TICKS; i++) {
            reach.tick(true);
        }
        double before = reach.out(0.0F);
        double last = reach.out(1.0F) - before;
        assertTrue(first > 1.0 / Reach.OUT_TICKS, "its first tick goes further than an even share: " + first);
        assertTrue(last < 1.0 / Reach.OUT_TICKS, "its last tick goes less far than an even share: " + last);
    }

    @Test
    void foldingBackTakesItsTicksEasingInAndSaysWhenItStarts() {
        Reach reach = new Reach();
        reach.tick(true);
        assertEquals(Reach.Change.BACK, reach.tick(false), "the fold starts on the tick the car is gone");
        double first = 1.0 - reach.out(1.0F);
        for (int i = 1; i < Reach.BACK_TICKS; i++) {
            assertTrue(reach.out(1.0F) > 0.0, "folded early, at tick " + i);
            assertEquals(Reach.Change.NONE, reach.tick(false), "tick " + i);
        }
        assertEquals(0.0, reach.out(1.0F), 1e-12);
        assertTrue(first < 1.0 / Reach.BACK_TICKS, "its first tick goes less far than an even share: " + first);
        assertEquals(Reach.Change.NONE, reach.tick(false), "nothing left to fold");
    }

    @Test
    void aFoldMidReachStartsWhereTheReachHadGotToAndTakesThatShareOfItsTicks() {
        Reach reach = idle();
        // Two fifths of the way out, in its ticks.
        int out = Reach.OUT_TICKS * 2 / 5;
        for (int i = 0; i < out; i++) {
            reach.tick(true);
        }
        double reached = reach.out(1.0F);
        assertEquals(Reach.Change.BACK, reach.tick(false));
        assertTrue(Math.abs(reach.out(0.0F) - reached) < 1e-12, "it jumped from " + reached + " to " + reach.out(0.0F));
        int back = 1;
        while (reach.out(1.0F) > 0.0) {
            reach.tick(false);
            back++;
        }
        assertEquals(Math.ceil(Reach.BACK_TICKS * 2.0 / 5), back, 1e-12, "two fifths of the fold's ticks");
    }

    @Test
    void aReachMidFoldStartsWhereTheFoldHadGotTo() {
        Reach reach = new Reach();
        reach.tick(true);
        reach.tick(false);
        double folded = reach.out(1.0F);
        assertEquals(Reach.Change.OUT, reach.tick(true));
        assertTrue(Math.abs(reach.out(0.0F) - folded) < 1e-12, "it jumped from " + folded + " to " + reach.out(0.0F));
        assertTrue(reach.out(1.0F) > folded);
    }

    @Test
    void itNeverJumpsBetweenTicks() {
        Reach reach = idle();
        boolean[] cars = {true, true, true, false, true, true, false, false, true, true, true, true, true, true, true,
                true, true, false, false, false, false, false};
        for (boolean car : cars) {
            double was = reach.out(1.0F);
            reach.tick(car);
            assertEquals(was, reach.out(0.0F), 1e-12);
            double step = Math.abs(reach.out(1.0F) - reach.out(0.0F));
            // The steepest a tick goes: the fold's last, eased in.
            assertTrue(step <= 2.0 / Reach.BACK_TICKS + 1e-12, "a step of " + step);
        }
    }

    /** A Reach that has had its first look, with no car there. */
    private static Reach idle() {
        Reach reach = new Reach();
        reach.tick(false);
        return reach;
    }
}
