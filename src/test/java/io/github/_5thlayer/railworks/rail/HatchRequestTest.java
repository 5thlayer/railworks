// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** What a requester hatch asks for as it is used with an item. */
class HatchRequestTest {

    private static final int SLOTS = 3;

    @Test
    void anItemAsksForOneStackOfIt() {
        assertEquals(new HatchRequest<>("iron", 1), HatchRequest.ask(Optional.empty(), "iron", SLOTS));
    }

    @Test
    void theSameKindAgainAsksForAStackMoreUpToItsSlots() {
        HatchRequest<String> asked = HatchRequest.ask(Optional.empty(), "iron", SLOTS);
        asked = HatchRequest.ask(Optional.of(asked), "iron", SLOTS);
        assertEquals(new HatchRequest<>("iron", 2), asked);
        asked = HatchRequest.ask(Optional.of(asked), "iron", SLOTS);
        asked = HatchRequest.ask(Optional.of(asked), "iron", SLOTS);
        assertEquals(new HatchRequest<>("iron", SLOTS), asked);
    }

    @Test
    void anotherKindReplacesTheRequestWithOneStack() {
        assertEquals(new HatchRequest<>("coal", 1), HatchRequest.ask(Optional.of(new HatchRequest<>("iron", 2)), "coal", SLOTS));
    }
}
