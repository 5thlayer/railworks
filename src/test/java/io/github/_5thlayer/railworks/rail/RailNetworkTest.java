// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class RailNetworkTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(10, 5, 0);
    private static final Arm C = new Arm(20, 5, 0);
    private static final Arm FAR = new Arm(100, 5, 100);

    private final RailNetwork network = new RailNetwork();

    @Test
    void aPairIsOneSpanInEitherOrder() {
        network.add(Span.of(A, B, 10));
        network.add(Span.of(B, A, 99));
        assertEquals(1, network.all().size());
        assertEquals(10, network.get(B, A).cost());
    }

    @Test
    void removeAllAtCutsEverySpanOfTheArmAndReturnsThem() {
        network.add(Span.of(A, B, 10));
        network.add(Span.of(B, C, 10));
        network.add(Span.of(A, C, 20));
        List<Span> cut = network.removeAllAt(B);
        assertEquals(2, cut.size());
        assertEquals(20, cut.stream().mapToInt(Span::cost).sum());
        assertEquals(List.of(Span.of(A, C, 20)), List.copyOf(network.all()));
    }

    @Test
    void rekeyMovesSpansAndKeepsTheirCost() {
        network.add(Span.of(A, B, 10));
        Arm higher = new Arm(10, 6, 0);
        network.rekey(B, higher);
        assertFalse(network.contains(A, B));
        assertEquals(10, network.get(A, higher).cost());
    }

    @Test
    void rekeyDropsASpanThatWouldJoinTheArmToItself() {
        Arm higher = new Arm(10, 6, 0);
        network.add(Span.of(B, higher, 1));
        network.rekey(B, higher);
        assertTrue(network.all().isEmpty());
    }

    @Test
    void touchingFindsSpansByEitherEndsChunk() {
        network.add(Span.of(A, FAR, 100));
        network.add(Span.of(A, B, 10));
        assertEquals(2, network.touching(0, 0).size());
        assertEquals(1, network.touching(6, 6).size());
        assertTrue(network.touching(3, 3).isEmpty());
    }

    @Test
    void aClientsChunkIsReplacedWhole() {
        network.add(Span.of(A, B, 10));
        network.add(Span.of(FAR, new Arm(100, 5, 120), 20));
        network.replaceTouching(0, 0, List.of(Span.of(A, C, 20)));
        assertFalse(network.contains(A, B));
        assertTrue(network.contains(A, C));
        assertEquals(2, network.all().size());
    }

    @Test
    void roundTripsThroughItsCodec() {
        network.add(Span.of(A, B, 10));
        network.add(Span.of(C, B, 10));
        JsonElement json = RailNetwork.CODEC.encodeStart(JsonOps.INSTANCE, network).getOrThrow();
        RailNetwork read = RailNetwork.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.copyOf(network.all()), List.copyOf(read.all()));
    }
}
