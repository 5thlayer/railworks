// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A Burner Generator's burning (#9): coal's 4 MJ into FE at 600 kW and 100 J an FE, unless a test sets otherwise. */
class BurnerGeneratorTest {

    private static final long COAL = 4_000_000L;

    private final BurnerGenerator burnerGenerator = new BurnerGenerator();
    private int lit;

    @AfterEach
    void reset() {
        ChargeSpec.reset();
    }

    private boolean tick() {
        return burnerGenerator.tick(() -> {
            lit++;
            return COAL;
        });
    }

    @Test
    void itMakesThreeHundredFeATickAndBuffersTenTicksOfIt() {
        assertEquals(3_000L, BurnerGenerator.capacity());
        assertTrue(tick());
        assertEquals(300L, burnerGenerator.stored());
        for (int i = 0; i < 9; i++) {
            assertTrue(tick());
        }
        assertEquals(3_000L, burnerGenerator.stored());
        assertFalse(tick(), "full, it burns nothing");
        assertEquals(COAL - 10 * 30_000L, burnerGenerator.fuel().storedJoules());
    }

    @Test
    void coalLastsAsLongAsInABurnerLocomotive() {
        int ticks = 0;
        while (lit < 2) {
            tick();
            burnerGenerator.extract(Long.MAX_VALUE);
            ticks++;
        }
        // 4 MJ at 30,000 J a tick is 133 ticks, the 134th lighting the second lump.
        assertEquals(134, ticks);
        assertEquals(2 * COAL - 134 * 30_000L, burnerGenerator.fuel().storedJoules(), "every joule lit is burnt or banked");
    }

    @Test
    void withNoFuelItMakesNothing() {
        assertFalse(burnerGenerator.tick(() -> 0L));
        assertEquals(0L, burnerGenerator.stored());
    }

    @Test
    void whatIsGivenAwayMakesRoomToBurnAgain() {
        for (int i = 0; i < 10; i++) {
            tick();
        }
        assertEquals(250L, burnerGenerator.extract(250L));
        assertFalse(tick(), "less than a tick's room");
        burnerGenerator.extract(50L);
        assertTrue(tick());
        assertEquals(3_000L, burnerGenerator.stored());
    }

    @Test
    void aRateThatSplitsATickKeepsTheJoulesForTheNext() {
        // 30,000 J a tick at 70 J an FE: 428 FE and 40 J over, then 30,040 J, which is 429 FE and 10 J over.
        ChargeSpec.configure(70L, ChargeSpec.DEFAULT_LOCOMOTIVE_CAPACITY, ChargeSpec.DEFAULT_CHARGER_RATE,
                ChargeSpec.DEFAULT_CHARGER_BUFFER, ChargeSpec.DEFAULT_BURNER_GENERATOR_KILOWATTS);
        tick();
        assertEquals(428L, burnerGenerator.stored());
        assertEquals(40L, burnerGenerator.joules());
        tick();
        assertEquals(857L, burnerGenerator.stored());
        assertEquals(10L, burnerGenerator.joules());
    }
}
