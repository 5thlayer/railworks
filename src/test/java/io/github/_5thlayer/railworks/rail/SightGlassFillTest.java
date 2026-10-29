// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** How far a Sight glass is filled, and the fluid in a Fluid hatch's round window. */
class SightGlassFillTest {

    /** The round window's glass, row by row, bottom up, in a side's pixels: as the hatch's side texture draws it inside its dark rim. */
    private static final List<SightGlassFill.Rect> WINDOW = List.of(
            new SightGlassFill.Rect(6, 5, 10, 6),
            new SightGlassFill.Rect(5, 6, 11, 7),
            new SightGlassFill.Rect(5, 7, 11, 8),
            new SightGlassFill.Rect(5, 8, 11, 9),
            new SightGlassFill.Rect(5, 9, 11, 10),
            new SightGlassFill.Rect(6, 10, 10, 11));

    @Test
    void aTankIsAsFullAsItsAmountOverItsCapacity() {
        assertEquals(0.0, SightGlassFill.full(0, 8_000), 1e-12);
        assertEquals(0.25, SightGlassFill.full(2_000, 8_000), 1e-12);
        assertEquals(1.0, SightGlassFill.full(8_000, 8_000), 1e-12);
        assertEquals(1.0, SightGlassFill.full(9_000, 8_000), 1e-12, "past full");
        assertEquals(0.0, SightGlassFill.full(5, 0), 1e-12, "a tank of no capacity");
    }

    @Test
    void anEmptyHatchsWindowHoldsNoFluid() {
        assertEquals(List.of(), SightGlassFill.roundWindow(0, 8_000));
        assertEquals(List.of(), SightGlassFill.roundWindow(1_000, 0), "a tank of no capacity");
    }

    @Test
    void aFullHatchsWindowIsFilledRowByRowAndNoFurtherThanItsRim() {
        assertEquals(WINDOW, SightGlassFill.roundWindow(8_000, 8_000));
        assertEquals(WINDOW, SightGlassFill.roundWindow(9_000, 8_000), "past full");
    }

    @Test
    void aHalfFullHatchsWindowIsFilledToItsMiddle() {
        assertEquals(WINDOW.subList(0, 3), SightGlassFill.roundWindow(4_000, 8_000));
    }

    @Test
    void theTopRowIsCroppedAtTheSurfaceSoTheLevelMovesSmoothly() {
        // A quarter full: a pixel and a half of the window's six.
        assertEquals(List.of(WINDOW.get(0), new SightGlassFill.Rect(5, 6, 11, 6.5)), SightGlassFill.roundWindow(1, 4));
        List<SightGlassFill.Rect> drop = SightGlassFill.roundWindow(1, 160_000);
        assertEquals(1, drop.size(), "a millibucket in fills more than the bottom row");
        assertTrue(drop.getFirst().top() > 5 && drop.getFirst().top() < 5.001, "a millibucket in shows " + drop);
        List<SightGlassFill.Rect> nearly = SightGlassFill.roundWindow(159_999, 160_000);
        assertEquals(WINDOW.size(), nearly.size());
        assertTrue(nearly.getLast().top() < 11, "a millibucket short of full shows full");
    }
}
