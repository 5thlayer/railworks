// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A Station's platform: its car spots back along the Straight leg, the block beside each, and overlap (ADR 0008). */
class PlatformTest {

    /** A level line along x, its Arms' tops at y 5, long enough for any platform here. */
    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(40, 5, 0);

    private final RailNetwork network = new RailNetwork();

    @AfterEach
    void reset() {
        Platform.configure(Platform.MAX_CARS);
        DriveSide.configure(DriveSide.DEFAULT);
    }

    private void line() {
        network.add(Span.of(A, B, 40));
    }

    /** On the south side at the car's floor: on the right of a Train heading for B. */
    private static Station south(int x) {
        return new Station(x, 6, 1, "Mine");
    }

    /** On the north side at the car's floor: on the right of a Train heading for A. */
    private static Station north(int x) {
        return new Station(x, 6, -1, "Smelter");
    }

    private Platform platform(Station station) {
        return Platform.of(network, station).orElseThrow();
    }

    /** How far along the line from A the middle of each spot is. */
    private List<Double> middles(Platform platform) {
        return platform.spots().stream().map(spot -> {
            Place p = spot.middle();
            return p.front().equals(p.span().a()) ? p.toFront() + p.span().a().x() : p.span().b().x() - p.toFront();
        }).toList();
    }

    @Test
    void itRunsBackFourCarSpotsTwoBlocksApartFromTheStationsFrontEdge() {
        line();
        // Serving Trains heading for B: its front edge at 10.5, its spots back toward A.
        Platform platform = platform(south(10));
        assertEquals(List.of(9.5, 7.5, 5.5, 3.5), middles(platform));
        assertEquals(List.of(0, 1, 2, 3), platform.spots().stream().map(Platform.CarSpot::index).toList());
        assertEquals(B, platform.front().front());
        assertEquals(platform.spots().getFirst().middle(), platform.leadMark());
    }

    @Test
    void onTheOtherSideItRunsBackTheOtherWay() {
        line();
        assertEquals(List.of(10.5, 12.5, 14.5, 16.5), middles(platform(north(10))));
    }

    @Test
    void itHasAsManySpotsAsTheServerSetsCappedAtFour() {
        line();
        Platform.configure(2);
        assertEquals(List.of(9.5, 7.5), middles(platform(south(10))));
        Platform.configure(9);
        assertEquals(Platform.MAX_CARS, Platform.cars());
        assertEquals(4, platform(south(10)).spots().size());
    }

    @Test
    void itEndsWhereTheLineEnds() {
        line();
        // From 4.5 back: two whole spots before A, at 0, ends the line.
        assertEquals(List.of(3.5, 1.5), middles(platform(south(4))));
    }

    @Test
    void itEndsWhereTheStraightLegEndsAtABend() {
        Arm q = new Arm(0, 5, 0);
        Arm r = new Arm(6, 5, 0);
        network.add(Span.of(new Arm(-6, 5, 4), q, 8));
        network.add(Span.of(q, r, 6));
        network.add(Span.of(r, B, 34));
        // The Rail bends through q, so q to r is no Straight leg: the fourth spot, over r, would be on it.
        assertEquals(List.of(11.5, 9.5, 7.5), middles(platform(south(12))));
    }

    @Test
    void aStationAtTheLinesEndHasNoRoomForASpot() {
        line();
        assertTrue(platform(south(40)).spots().isEmpty());
    }

    @Test
    void aStationUnderTheRailOrByNoneHasNoPlatform() {
        line();
        assertEquals(Optional.empty(), Platform.of(network, new Station(10, 5, 0, "Under")));
        assertEquals(Optional.empty(), Platform.of(network, new Station(10, 6, 5, "Away")));
    }

    @Test
    void aBlockBesideTheRailAtACarsFloorIsBesideTheSpotItIsIn() {
        line();
        Platform platform = platform(south(10));
        // Either side of the Rail, the Station's own block and the one behind it the first spot's.
        assertEquals(OptionalInt.of(0), platform.spotBeside(10, 6, -1));
        assertEquals(OptionalInt.of(0), platform.spotBeside(9, 6, 1));
        assertEquals(OptionalInt.of(1), platform.spotBeside(8, 6, -1));
        assertEquals(OptionalInt.of(1), platform.spotBeside(7, 6, 1));
        assertEquals(OptionalInt.of(3), platform.spotBeside(3, 6, 1));
    }

    @Test
    void aBlockOffThePlatformIsBesideNoSpot() {
        line();
        Platform platform = platform(south(10));
        assertEquals(OptionalInt.empty(), platform.spotBeside(11, 6, 1), "ahead of the Station");
        assertEquals(OptionalInt.empty(), platform.spotBeside(2, 6, 1), "behind the last spot");
        assertEquals(OptionalInt.empty(), platform.spotBeside(9, 5, 1), "a block below the car's floor");
        assertEquals(OptionalInt.empty(), platform.spotBeside(9, 7, 1), "a block above it");
        assertEquals(OptionalInt.empty(), platform.spotBeside(9, 6, 2), "two blocks off the Rail");
        assertEquals(OptionalInt.empty(), platform.spotBeside(9, 6, 0), "in the Rail's own row");
    }

    @Test
    void platformsSharingAnyOfTheLineOverlap() {
        line();
        Platform mine = platform(south(10));
        assertTrue(mine.overlaps(platform(south(5))), "behind it, on its spots");
        assertTrue(mine.overlaps(platform(north(10))), "across the Rail, serving the other way");
        assertTrue(platform(north(10)).overlaps(mine));
        assertFalse(mine.overlaps(platform(south(18))), "its last spot ends where this one's front is");
        assertFalse(mine.overlaps(platform(south(30))));
    }

    @Test
    void aStationWithNoSpotsStillHoldsItsOwnBlock() {
        line();
        Platform end = platform(south(40));
        assertTrue(end.overlaps(platform(north(40))));
        assertFalse(end.overlaps(platform(south(38))), "its front at 38.5, short of the end Station's block");
    }

    @Test
    void theOverlappingStationIsNamedAndItsOwnBlockIsNotCounted() {
        line();
        Station mine = south(10);
        Station smelter = north(8);
        assertEquals(Optional.of(smelter), Platform.overlapping(network, mine, List.of(mine, south(30), smelter)));
        assertEquals(Optional.empty(), Platform.overlapping(network, mine, List.of(mine, south(30))));
    }

    @Test
    void theStopIsReachedOnlyArrivingTheServingWay() {
        line();
        Platform platform = platform(south(10));
        Place eastbound = new Place(network.get(A, B), B, 40.0 - 2.0);
        Place westbound = new Place(network.get(A, B), A, 2.0);
        assertEquals(7.5, platform.toLeadMark(network, eastbound, Station.STRETCH), 1e-9);
        assertTrue(Double.isNaN(platform.toLeadMark(network, westbound, Station.STRETCH)));
        // Just past it, within the stretch.
        assertEquals(-0.5, platform.toLeadMark(network, new Place(network.get(A, B), B, 30.0), Station.STRETCH), 1e-9);
    }

    @Test
    void drivingOnTheLeftAStationServesAndRunsBackTheOtherWay() {
        line();
        DriveSide.configure(DriveSide.LEFT);
        assertEquals(List.of(10.5, 12.5, 14.5, 16.5), middles(platform(south(10))));
    }
}
