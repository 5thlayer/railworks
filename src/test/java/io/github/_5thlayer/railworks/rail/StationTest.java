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

    /** In the row two blocks off the Rail, at the height of a Cargo car's chests, where hatches stand too (ADR 0010). */
    private static Station beside(int x) {
        return new Station(x, 7, 2, "Mine");
    }

    /** {@code fromA} blocks along the Span from A, heading for B. */
    private static Place along(Span span, double fromA) {
        return new Place(span, span.b(), span.length() - fromA);
    }

    @Test
    void aStationBesideTheRailServesItsSpan() {
        Station.Foot stop = beside(4).foot(network).orElseThrow();
        assertEquals(AB, stop.span());
        assertEquals(4.0, stop.fromA(), 1e-9);
    }

    @Test
    void aStationWithinTheOldReachOfTheRailServesNoSpan() {
        assertTrue(new Station(4, 6, 1, "Mine").foot(network).isEmpty(), "one off at a car's floor");
        assertTrue(new Station(4, 5, 1, "Mine").foot(network).isEmpty(), "one off, a block lower");
        assertTrue(new Station(4, 5, 0, "Mine").foot(network).isEmpty(), "under the Rail");
        assertTrue(new Station(4, 7, 1, "Mine").foot(network).isEmpty(), "one off at the chests' height");
        assertTrue(new Station(4, 6, 2, "Mine").foot(network).isEmpty(), "two off at a car's floor");
    }

    @Test
    void aStationServesTheTrainsWithItOnTheirRight() {
        try {
            // South of a Rail along x: the right of a Train heading east, for B; north, of one heading for A.
            assertEquals(Optional.of(B), beside(4).servingEnd(network));
            assertEquals(Optional.of(A), new Station(4, 7, -2, "Mine").servingEnd(network));
            DriveSide.configure(DriveSide.LEFT);
            assertEquals(Optional.of(A), beside(4).servingEnd(network));
            assertEquals(Optional.of(B), new Station(4, 7, -2, "Mine").servingEnd(network));
        } finally {
            DriveSide.configure(DriveSide.DEFAULT);
        }
    }

    @Test
    void aStationAlongZServesByTheSameRule() {
        RailNetwork alongZ = new RailNetwork();
        Arm north = new Arm(0, 5, 0);
        Arm south = new Arm(0, 5, 10);
        alongZ.add(Span.of(north, south, 10));
        // East of a Rail along z: the right of a Train heading north.
        assertEquals(Optional.of(north), new Station(2, 7, 4, "Mine").servingEnd(alongZ));
        assertEquals(Optional.of(south), new Station(-2, 7, 4, "Mine").servingEnd(alongZ));
    }

    @Test
    void aStationUnderTheRailOrByNoneServesNeitherWay() {
        assertEquals(Optional.empty(), new Station(4, 5, 0, "Mine").servingEnd(network));
        assertEquals(Optional.empty(), new Station(4, 7, 5, "Mine").servingEnd(network));
    }

    @Test
    void aStationOffTheRowIsByNoRail() {
        assertTrue(new Station(4, 7, 3, "Mine").foot(network).isEmpty(), "three off");
        assertTrue(new Station(4, 8, 2, "Mine").foot(network).isEmpty(), "above the chests' height");
        assertTrue(new Station(4, 7, 0, "Mine").foot(network).isEmpty(), "over the Rail");
        assertTrue(new Station(4, 3, 0, "Mine").foot(network).isEmpty(), "far under the Rail");
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
        assertEquals(0.0, station.foot(network).orElseThrow().fromA(), 1e-9);
        assertTrue(station.serves(network, along(AB, 0.0)));
    }

    @Test
    void aStationOnASlopeIsMeasuredSquareToTheRail() {
        RailNetwork sloped = new RailNetwork();
        Span climb = Span.of(new Arm(0, 5, 0), new Arm(8, 7, 0), 9);
        sloped.add(climb);
        // Halfway up, the Rail is at y 6 in Arm coordinates: the block two off it at the chests' height
        // there, measured square to it seen from above, serves it.
        Station station = new Station(4, 8, 2, "Hill");
        Station.Foot stop = station.foot(sloped).orElseThrow();
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
    void ofTwoLinesItServesTheOneItIsInTheRowOf() {
        // A second line alongside, a block over from the Station, nearer it than AB but not two off.
        Span near = Span.of(new Arm(0, 5, 3), new Arm(10, 5, 3), 10);
        network.add(near);
        Station station = beside(4);
        assertEquals(AB, station.foot(network).orElseThrow().span());
        assertTrue(station.serves(network, along(AB, 4.5)));
        assertFalse(station.serves(network, along(near, 4.5)));
    }

    @Test
    void betweenTwoLinesAsNearItServesTheOneWhoseEndsSortFirst() {
        Span other = Span.of(new Arm(0, 5, 4), new Arm(10, 5, 4), 10);
        network.add(other);
        RailNetwork reversed = new RailNetwork();
        reversed.add(other);
        reversed.add(AB);
        assertEquals(AB, beside(4).foot(network).orElseThrow().span());
        assertEquals(AB, beside(4).foot(reversed).orElseThrow().span());
    }

    @Test
    void aStationWhoseSpanIsCutServesNothingUntilOneIsStrungAgain() {
        Station station = beside(4);
        network.remove(A, B);
        assertTrue(station.foot(network).isEmpty());
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
        stations.put(new Station(5, 7, -2, "Depot"));
        assertEquals(List.of(first, second), stations.named("Mine"));
        assertEquals(List.of(second), stations.at(network, along(AB, 8.5)));
        assertEquals(List.of(), stations.at(network, along(AB, 6.5)));
    }

    @Test
    void renamingAStationReplacesItAtItsBlock() {
        Stations stations = new Stations();
        stations.put(beside(4));
        stations.put(beside(4).withName("Smelter"));
        assertEquals(1, stations.all().size());
        assertEquals("Smelter", stations.get(4, 7, 2).name());
        stations.remove(4, 7, 2);
        assertNull(stations.get(4, 7, 2));
    }

    @Test
    void stationsRoundTripThroughTheCodec() {
        Stations stations = new Stations();
        stations.put(beside(2));
        stations.put(beside(8).withName("Smelter"));
        var json = Stations.CODEC.encodeStart(JsonOps.INSTANCE, stations).getOrThrow();
        Stations read = Stations.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.copyOf(stations.all()), List.copyOf(read.all()));
    }
}
