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
    private static final Arm B = new Arm(60, 5, 0);

    private final RailNetwork network = new RailNetwork();

    @AfterEach
    void reset() {
        Platform.configure(Platform.MAX_CARS);
        DriveSide.configure(DriveSide.DEFAULT);
    }

    private void line() {
        network.add(Span.of(A, B, 60));
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
    void itRunsBackFourCarSpotsFiveBlocksApartBehindTheFiveBlockLeadingCar() {
        line();
        // Serving Trains heading for B: its front edge at 30.5, the leading Locomotive's middle at 28,
        // and the spots back toward A behind its five blocks, the platform 25 blocks to 5.5.
        assertEquals(5.0, Platform.SPOT);
        assertEquals(5.0, Platform.LEAD);
        Platform platform = platform(south(30));
        assertEquals(28.0, along(platform.leadMark()), 1e-9);
        assertEquals(List.of(23.0, 18.0, 13.0, 8.0), middles(platform));
        assertEquals(List.of(0, 1, 2, 3), platform.spots().stream().map(Platform.CarSpot::index).toList());
        assertEquals(B, platform.front().front());
    }

    @Test
    void onTheOtherSideItRunsBackTheOtherWay() {
        line();
        assertEquals(List.of(36.0, 41.0, 46.0, 51.0), middles(platform(north(29))));
    }

    @Test
    void itHasAsManySpotsAsTheServerSetsCappedAtFour() {
        line();
        Platform.configure(2);
        assertEquals(List.of(23.0, 18.0), middles(platform(south(30))));
        Platform.configure(9);
        assertEquals(Platform.MAX_CARS, Platform.cars());
        assertEquals(4, platform(south(30)).spots().size());
    }

    @Test
    void itEndsWhereTheLineEnds() {
        line();
        // From 10.5 back: the leading car's five blocks, and one whole spot before A, at 0, ends the line.
        assertEquals(List.of(3.0), middles(platform(south(10))));
        assertEquals(List.of(), middles(platform(south(9))), "no room for a spot behind the leading car");
    }

    @Test
    void itEndsWhereTheStraightLegEndsAtABend() {
        Arm q = new Arm(0, 5, 0);
        Arm r = new Arm(10, 5, 0);
        network.add(Span.of(new Arm(-6, 5, 4), q, 8));
        network.add(Span.of(q, r, 10));
        network.add(Span.of(r, B, 50));
        // The Rail bends through q, so q to r is no Straight leg: the third spot, from 5.5 to 10.5, would be on it.
        assertEquals(List.of(18.0, 13.0), middles(platform(south(25))));
    }

    @Test
    void aStationAtTheLinesEndHasItsSpotsBackFromTheEnd() {
        line();
        // A car runs on until its middle meets the line's end, so the leading car stands at the Station
        // with its front half a block past the end Arm, and the spots run back from there as anywhere.
        Platform platform = platform(south(60));
        assertEquals(58.0, along(platform.leadMark()), 1e-9);
        assertEquals(List.of(53.0, 48.0, 43.0, 38.0), middles(platform));
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(60, 6, -1), "the leading car's");
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(56, 6, 1), "the leading car's fifth block");
        assertEquals(OptionalInt.of(0), platform.spotBeside(55, 6, 1));
        assertEquals(OptionalInt.of(0), platform.spotBeside(51, 6, -1));
        assertEquals(OptionalInt.of(1), platform.spotBeside(50, 6, 1));
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
        Platform platform = platform(south(30));
        // Either side of the Rail, the five blocks behind the leading car's the first spot's.
        assertEquals(OptionalInt.of(0), platform.spotBeside(25, 6, -1));
        assertEquals(OptionalInt.of(0), platform.spotBeside(21, 6, 1));
        assertEquals(OptionalInt.of(1), platform.spotBeside(20, 6, -1));
        assertEquals(OptionalInt.of(1), platform.spotBeside(16, 6, 1));
        assertEquals(OptionalInt.of(3), platform.spotBeside(6, 6, 1));
    }

    @Test
    void aBlockBesideTheLeadingCarsPlaceIsBesideItAsBesideACarSpot() {
        line();
        Platform platform = platform(south(30));
        // The leading Locomotive stands from 25.5 to 30.5: the Station's own block is one of the ten
        // beside it, five each side, the one beside its back among them.
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(30, 6, -1));
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(28, 6, -1));
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(26, 6, -1), "beside its fifth block");
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(26, 6, 1), "beside its fifth block");
        assertEquals(OptionalInt.of(Platform.LEADING), platform.spotBeside(30, 6, 1), "the Station's own block");
        assertEquals(Optional.of(new Platform.Joined(south(30), Platform.LEADING)),
                Platform.joined(network, List.of(south(30)), 26, 6, -1));
        assertEquals(10, platform.besideBlocks(network).stream().filter(b -> b.spot() == Platform.LEADING).count());
    }

    @Test
    void aBlockOffThePlatformIsBesideNoSpot() {
        line();
        Platform platform = platform(south(30));
        assertEquals(OptionalInt.empty(), platform.spotBeside(31, 6, 1), "ahead of the Station");
        assertEquals(OptionalInt.empty(), platform.spotBeside(5, 6, 1), "behind the last spot");
        assertEquals(OptionalInt.empty(), platform.spotBeside(22, 5, 1), "a block below the car's floor");
        assertEquals(OptionalInt.empty(), platform.spotBeside(22, 7, 1), "a block above it");
        assertEquals(OptionalInt.empty(), platform.spotBeside(22, 6, 2), "two blocks off the Rail");
        assertEquals(OptionalInt.empty(), platform.spotBeside(22, 6, 0), "in the Rail's own row");
    }

    @Test
    void platformsSharingAnyOfTheLineOverlap() {
        line();
        Platform mine = platform(south(30));
        assertTrue(mine.overlaps(platform(south(20))), "behind it, on its spots");
        assertTrue(mine.overlaps(platform(north(30))), "across the Rail, serving the other way");
        assertTrue(platform(north(30)).overlaps(mine));
        assertTrue(mine.overlaps(platform(south(54))), "its last spot over this one's leading car");
        assertFalse(mine.overlaps(platform(south(55))), "its last spot ends where this one's front is");
        assertFalse(mine.overlaps(platform(south(58))));
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
        Platform end = platform(south(60));
        assertTrue(end.overlaps(platform(north(60))));
        assertFalse(end.overlaps(platform(south(35))), "its front at 35.5, where the end Station's last spot ends");
        assertTrue(end.overlaps(platform(south(36))));
    }

    @Test
    void theOverlappingStationIsNamedAndItsOwnBlockIsNotCounted() {
        line();
        Station mine = south(30);
        Station smelter = north(27);
        assertEquals(Optional.of(smelter), Platform.overlapping(network, mine, List.of(mine, south(58), smelter)));
        assertEquals(Optional.empty(), Platform.overlapping(network, mine, List.of(mine, south(58))));
    }

    @Test
    void theStopIsReachedOnlyArrivingTheServingWay() {
        line();
        Platform platform = platform(south(30));
        Place eastbound = new Place(network.get(A, B), B, 60.0 - 2.0);
        Place westbound = new Place(network.get(A, B), A, 2.0);
        assertEquals(26.0, platform.toLeadMark(network, eastbound, Station.STRETCH), 1e-9);
        assertTrue(Double.isNaN(platform.toLeadMark(network, westbound, Station.STRETCH)));
        // Just past it, within the stretch.
        assertEquals(-0.5, platform.toLeadMark(network, new Place(network.get(A, B), B, 31.5), Station.STRETCH), 1e-9);
    }

    @Test
    void drivingOnTheLeftAStationServesAndRunsBackTheOtherWay() {
        line();
        DriveSide.configure(DriveSide.LEFT);
        assertEquals(List.of(36.0, 41.0, 46.0, 51.0), middles(platform(south(29))));
    }

    @Test
    void aBlockBesideACarSpotJoinsThatStationAtThatSpot() {
        line();
        Station mine = south(25);
        Station smelter = north(31);
        List<Station> stations = List.of(smelter, mine);
        assertEquals(Optional.of(new Platform.Joined(mine, 1)), Platform.joined(network, stations, 15, 6, -1));
        assertEquals(Optional.of(new Platform.Joined(smelter, 0)), Platform.joined(network, stations, 38, 6, 1));
        assertEquals(Optional.of(new Platform.Joined(smelter, Platform.LEADING)), Platform.joined(network, stations, 35, 6, 1),
                "beside the smelter's leading car's fifth block");
        assertEquals(Optional.of(new Platform.Joined(smelter, Platform.LEADING)), Platform.joined(network, stations, 31, 6, 1),
                "beside the smelter's leading car");
        assertEquals(Optional.empty(), Platform.joined(network, stations, 28, 6, 1), "between the two platforms");
        assertEquals(Optional.empty(), Platform.joined(network, List.of(), 20, 6, -1), "with no Station");
    }

    @Test
    void aStationOffTheRailJoinsNothing() {
        line();
        assertEquals(Optional.empty(), Platform.joined(network, List.of(new Station(10, 6, 5, "Away")), 10, 6, 4));
    }

    @Test
    void eachCarSpotAndTheLeadingCarsPlaceHaveTenBlocksBesideThemFiveEachSideOfTheRail() {
        line();
        Platform platform = platform(south(30));
        List<Platform.Beside> beside = platform.besideBlocks(network);
        // Four spots and the leading car's place, 25 blocks along each side.
        assertEquals(50, beside.size());
        assertEquals(6, beside.stream().mapToInt(Platform.Beside::x).min().orElseThrow());
        assertEquals(30, beside.stream().mapToInt(Platform.Beside::x).max().orElseThrow());
        Set<List<Integer>> first = beside.stream().filter(b -> b.spot() == 0)
                .map(b -> List.of(b.x(), b.y(), b.z())).collect(Collectors.toSet());
        Set<List<Integer>> expected = new java.util.HashSet<>();
        for (int x = 21; x <= 25; x++) {
            expected.add(List.of(x, 6, -1));
            expected.add(List.of(x, 6, 1));
        }
        assertEquals(expected, first);
        for (Platform.Beside b : beside) {
            assertEquals(OptionalInt.of(b.spot()), platform.spotBeside(b.x(), b.y(), b.z()));
        }
    }

    @Test
    void aShortPlatformHasBlocksOnlyBesideItsLeadingCarAndItsSpots() {
        line();
        assertEquals(20, platform(south(10)).besideBlocks(network).size());
    }

    @Test
    void eachCarSpotHasASideEachSideOfTheRailRunningAlongTheLine() {
        line();
        List<Platform.Side> sides = platform(south(30)).sides(network);
        assertEquals(10, sides.size());
        assertTrue(sides.stream().allMatch(Platform.Side::alongX));
        assertTrue(sides.stream().allMatch(side -> side.blocks().size() == 5),
                "five blocks beside the leading car's place and beside a spot");
        assertEquals(Platform.LEADING, sides.getFirst().spot());
        assertEquals(Set.of(List.of(25, 6, -1), List.of(24, 6, -1), List.of(23, 6, -1), List.of(22, 6, -1), List.of(21, 6, -1)), sides.stream().filter(side -> side.spot() == 0)
                .findFirst().orElseThrow().blocks().stream()
                .map(b -> List.of(b.x(), b.y(), b.z())).collect(Collectors.toSet()));
        // A line along z: its sides run along z.
        RailNetwork alongZ = new RailNetwork();
        alongZ.add(Span.of(new Arm(0, 5, 0), new Arm(0, 5, 60), 60));
        Platform byZ = Platform.of(alongZ, new Station(1, 6, 30, "Z")).orElseThrow();
        assertTrue(byZ.sides(alongZ).stream().noneMatch(Platform.Side::alongX));
        assertEquals(10, byZ.sides(alongZ).size());
    }
}
