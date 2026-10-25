// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** Where a Station is by the Rail, and which stretch of its Span a car is at it on (ADR 0007). */
class StationTest {

    /** A level Span along x, its Arms' tops at y 5. */
    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(10, 5, 0);
    private static final Span AB = Span.of(A, B, 10);

    private final RailNetwork network = new RailNetwork();

    StationTest() {
        network.add(AB);
    }

    /** At ride height, one block to the side of the Rail: where a hopper beside a stopped car would be. */
    private static Station beside(int x) {
        return new Station(x, 6, 1, "Mine");
    }

    /** {@code fromA} blocks along the Span from A, heading for B. */
    private static Place along(Span span, double fromA) {
        return new Place(span, span.b(), span.length() - fromA);
    }

    @Test
    void aStationBesideTheRailServesItsSpan() {
        Station.Stop stop = beside(4).stop(network).orElseThrow();
        assertEquals(AB, stop.span());
        assertEquals(4.0, stop.fromA(), 1e-9);
    }

    @Test
    void aStationUnderTheRailServesItsSpan() {
        assertTrue(new Station(4, 5, 0, "Mine").stop(network).isPresent());
    }

    @Test
    void aStationTwoBlocksOffOrAboveTheRailIsByNoRail() {
        assertTrue(new Station(4, 6, 2, "Mine").stop(network).isEmpty());
        assertTrue(new Station(4, 7, 0, "Mine").stop(network).isEmpty());
        assertTrue(new Station(4, 3, 0, "Mine").stop(network).isEmpty());
    }

    @Test
    void aCarIsAtTheStationWithinItsStretchEitherWay() {
        Station station = beside(4);
        assertTrue(station.serves(network, along(AB, 4.0)));
        assertTrue(station.serves(network, along(AB, 3.0)));
        assertTrue(station.serves(network, along(AB, 5.0)));
        assertFalse(station.serves(network, along(AB, 2.9)));
        assertFalse(station.serves(network, along(AB, 5.1)));
    }

    @Test
    void theHeadingOfTheCarDoesNotMatter() {
        Station station = beside(4);
        assertTrue(station.serves(network, new Place(AB, A, 4.5)));
        assertFalse(station.serves(network, new Place(AB, A, 7.0)));
    }

    @Test
    void aStationByAnArmIsReachedFromItsEnd() {
        Station station = beside(0);
        assertEquals(0.0, station.stop(network).orElseThrow().fromA(), 1e-9);
        assertTrue(station.serves(network, along(AB, 0.0)));
    }

    @Test
    void aStationOnASlopeIsMeasuredSquareToTheRail() {
        RailNetwork sloped = new RailNetwork();
        Span climb = Span.of(new Arm(0, 5, 0), new Arm(8, 7, 0), 9);
        sloped.add(climb);
        // Halfway up, the Rail is at y 6 in Arm coordinates: a block beside it at that height serves it.
        Station station = new Station(4, 7, 1, "Hill");
        Station.Stop stop = station.stop(sloped).orElseThrow();
        assertEquals(climb, stop.span());
        assertTrue(station.serves(sloped, along(climb, stop.fromA())));
    }

    @Test
    void aCarOnAnotherSpanIsNotAtTheStation() {
        Arm c = new Arm(20, 5, 0);
        Span bc = Span.of(B, c, 10);
        network.add(bc);
        assertFalse(beside(4).serves(network, along(bc, 4.5)));
    }

    @Test
    void ofTwoLinesInReachItServesTheNearer() {
        // A second line alongside, one block over: the Station under it is nearer it than AB.
        Span near = Span.of(new Arm(0, 5, 1), new Arm(10, 5, 1), 10);
        network.add(near);
        Station station = new Station(4, 5, 1, "Mine");
        assertEquals(near, station.stop(network).orElseThrow().span());
        assertTrue(station.serves(network, along(near, 4.5)));
        assertFalse(station.serves(network, along(AB, 4.5)));
    }

    @Test
    void betweenTwoLinesAsNearItServesTheOneWhoseEndsSortFirst() {
        Span other = Span.of(new Arm(0, 5, 2), new Arm(10, 5, 2), 10);
        network.add(other);
        RailNetwork reversed = new RailNetwork();
        reversed.add(other);
        reversed.add(AB);
        assertEquals(AB, beside(4).stop(network).orElseThrow().span());
        assertEquals(AB, beside(4).stop(reversed).orElseThrow().span());
    }

    @Test
    void aStationWhoseSpanIsCutServesNothingUntilOneIsStrungAgain() {
        Station station = beside(4);
        network.remove(A, B);
        assertTrue(station.stop(network).isEmpty());
        network.add(AB);
        assertTrue(station.serves(network, along(AB, 4.5)));
    }

    @Test
    void aNameIsTrimmedCutToTheLimitAndClearedOfFormatting() {
        assertEquals(Optional.of("Iron Mine"), Station.name("  Iron Mine \n"));
        assertEquals(Optional.of("Red"), Station.name("§cRed"));
        assertEquals(Station.NAME_LIMIT, Station.name("x".repeat(50)).orElseThrow().length());
        assertEquals(Optional.empty(), Station.name("   "));
    }

    @Test
    void severalStationsShareANameAndACarFindsTheOneItIsAt() {
        Stations stations = new Stations();
        Station first = beside(2);
        Station second = beside(8);
        stations.put(first);
        stations.put(second);
        stations.put(new Station(5, 6, -1, "Depot"));
        assertEquals(List.of(first, second), stations.named("Mine"));
        assertEquals(List.of(second), stations.at(network, along(AB, 8.5)));
        assertEquals(List.of(), stations.at(network, along(AB, 6.5)));
    }

    @Test
    void renamingAStationReplacesItAtItsBlock() {
        Stations stations = new Stations();
        stations.put(beside(4));
        stations.put(beside(4).named("Smelter"));
        assertEquals(1, stations.all().size());
        assertEquals("Smelter", stations.get(4, 6, 1).name());
        stations.remove(4, 6, 1);
        assertNull(stations.get(4, 6, 1));
    }

    @Test
    void stationsRoundTripThroughTheCodec() {
        Stations stations = new Stations();
        stations.put(beside(2));
        stations.put(beside(8).named("Smelter"));
        var json = Stations.CODEC.encodeStart(JsonOps.INSTANCE, stations).getOrThrow();
        Stations read = Stations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.copyOf(stations.all()), List.copyOf(read.all()));
    }
}
