// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** What a requester hatch asks for as it is used with an item, and a Fluid requester with a fluid. */
class HatchRequestTest {

    private static final int SLOTS = 3;
    private static final int TANK = 20;

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

    @Test
    void aFluidAsksForEightBucketsOfIt() {
        assertEquals(new HatchRequest<>("water", 8), HatchRequest.askFluid(Optional.empty(), "water", TANK));
    }

    @Test
    void theSameFluidAgainAsksForEightBucketsMoreUpToItsTank() {
        HatchRequest<String> asked = HatchRequest.askFluid(Optional.empty(), "water", TANK);
        asked = HatchRequest.askFluid(Optional.of(asked), "water", TANK);
        assertEquals(new HatchRequest<>("water", 16), asked);
        asked = HatchRequest.askFluid(Optional.of(asked), "water", TANK);
        assertEquals(new HatchRequest<>("water", TANK), asked);
    }

    @Test
    void anotherFluidReplacesTheRequestWithEightBucketsOfIt() {
        assertEquals(new HatchRequest<>("lava", 8),
                HatchRequest.askFluid(Optional.of(new HatchRequest<>("water", 16)), "lava", TANK));
    }

    @Test
    void aTankSmallerThanEightBucketsIsAskedFull() {
        assertEquals(new HatchRequest<>("water", 5), HatchRequest.askFluid(Optional.empty(), "water", 5));
    }
}
