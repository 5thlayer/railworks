// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A Station's platform: its leading car's place and the car spots back behind it along the Straight leg, the blocks beside each, and overlap (ADR 0008). */
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
        return platform.spots().stream().map(spot -> along(spot.middle())).toList();
    }

    /** How far along the line from A {@code p} is. */
    private static double along(Place p) {
        return p.front().equals(p.span().a()) ? p.toFront() + p.span().a().x() : p.span().b().x() - p.toFront();
    }

    @Test
    void itRunsBackFourCarSpotsTwoBlocksApartBehindTheThreeBlockLeadingCar() {
        line();
        // Serving Trains heading for B: its front edge at 11.5, the leading Locomotive's middle at 10,
        // and the spots back toward A behind its three blocks.
        Platform platform = platform(south(11));
        assertEquals(10.0, along(platform.leadMark()), 1e-9);
        assertEquals(List.of(7.5, 5.5, 3.5, 1.5), middles(platform));
        assertEquals(List.of(0, 1, 2, 3), platform.spots().stream().map(Platform.CarSpot::index).toList());
        assertEquals(B, platform.front().front());
    }

    @Test
    void onTheOtherSideItRunsBackTheOtherWay() {
        line();
        assertEquals(List.of(13.5, 15.5, 17.5, 19.5), middles(platform(north(10))));
    }

    @Test
    void itHasAsManySpotsAsTheServerSetsCappedAtFour() {
        line();
        Platform.configure(2);
        assertEquals(List.of(7.5, 5.5), middles(platform(south(11))));
        Platform.configure(9);
        assertEquals(Platform.MAX_CARS, Platform.cars());
        assertEquals(4, platform(south(11)).spots().size());
    }

    @Test
    void itEndsWhereTheLineEnds() {
        line();
        // From 5.5 back: the leading car's three blocks, and one whole spot before A, at 0, ends the line.
        assertEquals(List.of(1.5), middles(platform(south(5))));
        assertEquals(List.of(), middles(platform(south(4))), "no room for a spot behind the leading car");
    }

    @Test
    void itEndsWhereTheStraightLegEndsAtABend() {
        Arm q = new Arm(0, 5, 0);
        Arm r = new Arm(6, 5, 0);
        network.add(Span.of(new Arm(-6, 5, 4), q, 8));
        network.add(Span.of(q, r, 6));
        network.add(Span.of(r, B, 34));
        // The Rail bends through q, so q to r is no Straight leg: the third spot, over r, would be on it.
        assertEquals(List.of(9.5, 7.5), middles(platform(south(13))));
    }

    @Test
    void aStationAtTheLinesEndHasItsSpotsBackFromTheEnd() {
        line();
        // A car runs on until its middle meets the line's end, so the leading car stands at the Station
        // with its front half a block past the end Arm, and the spots run back from there as anywhere.
        Platform platform = platform(south(40));
        assertEquals(39.0, along(platform.leadMark()), 1e-9);
        assertEquals(List.of(36.5, 34.5, 32.5, 30.5), middles(platform));
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(40, 6, -1), "the leading car's");
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(38, 6, 1), "the leading car's third block");
        assertEquals(OptionalInt.of(0), platform.spotBeside(37, 6, 1));
        assertEquals(OptionalInt.of(0), platform.spotBeside(36, 6, -1));
        assertEquals(OptionalInt.of(1), platform.spotBeside(35, 6, 1));
    }

    /** A Station by an Arm the Rail bends through, its platform running back over the bend. */
    private Station byABend() {
        network.add(Span.of(new Arm(-6, 5, 4), new Arm(0, 5, 0), 8));
        network.add(Span.of(new Arm(0, 5, 0), new Arm(6, 5, 0), 6));
        return new Station(1, 6, 1, "Bend");
    }

    @Test
    void aStationWhosePlatformStartsOnABendHasNoSpots() {
        assertTrue(platform(byABend()).spots().isEmpty());
        assertTrue(platform(new Station(1, 6, 1, "Bend")).besideBlocks(network).isEmpty());
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
        Platform platform = platform(south(11));
        // Either side of the Rail, the two blocks behind the leading car's the first spot's.
        assertEquals(OptionalInt.of(0), platform.spotBeside(8, 6, -1));
        assertEquals(OptionalInt.of(0), platform.spotBeside(7, 6, 1));
        assertEquals(OptionalInt.of(1), platform.spotBeside(6, 6, -1));
        assertEquals(OptionalInt.of(1), platform.spotBeside(5, 6, 1));
        assertEquals(OptionalInt.of(3), platform.spotBeside(1, 6, 1));
    }

    @Test
    void aBlockBesideTheLeadingCarsPlaceIsBesideItAsBesideACarSpot() {
        line();
        Platform platform = platform(south(11));
        // The leading Locomotive stands from 8.5 to 11.5: the Station's own block is one of the six
        // beside it, three each side, the one beside its back among them.
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(11, 6, -1));
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(10, 6, -1));
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(9, 6, -1), "beside its third block");
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(9, 6, 1), "beside its third block");
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(11, 6, 1), "the Station's own block");
        assertEquals(Optional.of(new Platform.Joined(south(11), Platform.LEADING)),
                Platform.joined(network, List.of(south(11)), 9, 6, -1));
        assertEquals(6, platform.besideBlocks(network).stream().filter(b -> b.spot() == Platform.LEADING).count());
    }

    @Test
    void aBlockOffThePlatformIsBesideNoSpot() {
        line();
        Platform platform = platform(south(11));
        assertEquals(OptionalInt.empty(), platform.spotBeside(12, 6, 1), "ahead of the Station");
        assertEquals(OptionalInt.empty(), platform.spotBeside(0, 6, 1), "behind the last spot");
        assertEquals(OptionalInt.empty(), platform.spotBeside(7, 5, 1), "a block below the car's floor");
        assertEquals(OptionalInt.empty(), platform.spotBeside(7, 7, 1), "a block above it");
        assertEquals(OptionalInt.empty(), platform.spotBeside(7, 6, 2), "two blocks off the Rail");
        assertEquals(OptionalInt.empty(), platform.spotBeside(7, 6, 0), "in the Rail's own row");
    }

    @Test
    void platformsSharingAnyOfTheLineOverlap() {
        line();
        Platform mine = platform(south(11));
        assertTrue(mine.overlaps(platform(south(5))), "behind it, on its spots");
        assertTrue(mine.overlaps(platform(north(11))), "across the Rail, serving the other way");
        assertTrue(platform(north(11)).overlaps(mine));
        assertTrue(mine.overlaps(platform(south(21))), "its last spot over this one's leading car");
        assertFalse(mine.overlaps(platform(south(22))), "its last spot ends where this one's front is");
        assertFalse(mine.overlaps(platform(south(32))));
    }

    @Test
    void aStationWithNoSpotsStillHoldsItsOwnBlock() {
        Platform none = platform(byABend());
        assertTrue(none.spots().isEmpty());
        assertTrue(none.overlaps(platform(new Station(1, 6, -1, "Across"))));
    }

    @Test
    void aStationAtTheLinesEndOverlapsOnlyWhatItsSpotsCover() {
        line();
        Platform end = platform(south(40));
        assertTrue(end.overlaps(platform(north(40))));
        assertFalse(end.overlaps(platform(south(29))), "its front at 29.5, where the end Station's last spot ends");
        assertTrue(end.overlaps(platform(south(31))));
    }

    @Test
    void theOverlappingStationIsNamedAndItsOwnBlockIsNotCounted() {
        line();
        Station mine = south(11);
        Station smelter = north(8);
        assertEquals(Optional.of(smelter), Platform.overlapping(network, mine, List.of(mine, south(30), smelter)));
        assertEquals(Optional.empty(), Platform.overlapping(network, mine, List.of(mine, south(30))));
    }

    @Test
    void theStopIsReachedOnlyArrivingTheServingWay() {
        line();
        Platform platform = platform(south(11));
        Place eastbound = new Place(network.get(A, B), B, 40.0 - 2.0);
        Place westbound = new Place(network.get(A, B), A, 2.0);
        assertEquals(8.0, platform.toLeadMark(network, eastbound, Station.STRETCH), 1e-9);
        assertTrue(Double.isNaN(platform.toLeadMark(network, westbound, Station.STRETCH)));
        // Just past it, within the stretch.
        assertEquals(-0.5, platform.toLeadMark(network, new Place(network.get(A, B), B, 29.5), Station.STRETCH), 1e-9);
    }

    @Test
    void drivingOnTheLeftAStationServesAndRunsBackTheOtherWay() {
        line();
        DriveSide.configure(DriveSide.LEFT);
        assertEquals(List.of(13.5, 15.5, 17.5, 19.5), middles(platform(south(10))));
    }

    @Test
    void aBlockBesideACarSpotJoinsThatStationAtThatSpot() {
        line();
        Station mine = south(11);
        Station smelter = north(30);
        List<Station> stations = List.of(smelter, mine);
        assertEquals(Optional.of(new Platform.Joined(mine, 1)), Platform.joined(network, stations, 6, 6, -1));
        assertEquals(Optional.of(new Platform.Joined(smelter, 0)), Platform.joined(network, stations, 33, 6, 1));
        assertEquals(Optional.of(new Platform.Joined(smelter, Platform.LEADING)), Platform.joined(network, stations, 32, 6, 1),
                "beside the smelter's leading car's third block");
        assertEquals(Optional.of(new Platform.Joined(smelter, Platform.LEADING)), Platform.joined(network, stations, 30, 6, 1),
                "beside the smelter's leading car");
        assertEquals(Optional.empty(), Platform.joined(network, stations, 20, 6, 1), "between the two platforms");
        assertEquals(Optional.empty(), Platform.joined(network, List.of(), 8, 6, -1), "with no Station");
    }

    @Test
    void aStationOffTheRailJoinsNothing() {
        line();
        assertEquals(Optional.empty(), Platform.joined(network, List.of(new Station(10, 6, 5, "Away")), 10, 6, 4));
    }

    @Test
    void eachCarSpotHasFourBlocksBesideItTwoEachSideOfTheRailAndTheLeadingCarsPlaceSix() {
        line();
        Platform platform = platform(south(11));
        List<Platform.Beside> beside = platform.besideBlocks(network);
        // Four spots and the leading car's place.
        assertEquals(22, beside.size());
        Set<List<Integer>> first = beside.stream().filter(b -> b.spot() == 0)
                .map(b -> List.of(b.x(), b.y(), b.z())).collect(Collectors.toSet());
        assertEquals(Set.of(List.of(8, 6, -1), List.of(7, 6, -1), List.of(8, 6, 1), List.of(7, 6, 1)), first);
        for (Platform.Beside b : beside) {
            assertEquals(OptionalInt.of(b.spot()), platform.spotBeside(b.x(), b.y(), b.z()));
        }
    }

    @Test
    void aShortPlatformHasBlocksOnlyBesideItsLeadingCarAndItsSpots() {
        line();
        assertEquals(10, platform(south(5)).besideBlocks(network).size());
    }

    @Test
    void eachCarSpotHasASideEachSideOfTheRailRunningAlongTheLine() {
        line();
        List<Platform.Side> sides = platform(south(11)).sides(network);
        assertEquals(10, sides.size());
        assertTrue(sides.stream().allMatch(Platform.Side::alongX));
        assertTrue(sides.stream().allMatch(side -> side.blocks().size() == (side.spot() == Platform.LEADING ? 3 : 2)),
                "three blocks beside the leading car's place, two beside a spot");
        assertEquals(Platform.LEADING, sides.getFirst().spot());
        assertEquals(Set.of(List.of(8, 6, -1), List.of(7, 6, -1)), sides.stream().filter(side -> side.spot() == 0)
                .findFirst().orElseThrow().blocks().stream()
                .map(b -> List.of(b.x(), b.y(), b.z())).collect(Collectors.toSet()));
        // A line along z: its sides run along z.
        RailNetwork alongZ = new RailNetwork();
        alongZ.add(Span.of(new Arm(0, 5, 0), new Arm(0, 5, 40), 40));
        Platform byZ = Platform.of(alongZ, new Station(1, 6, 11, "Z")).orElseThrow();
        assertTrue(byZ.sides(alongZ).stream().noneMatch(Platform.Side::alongX));
        assertEquals(10, byZ.sides(alongZ).size());
    }
}
