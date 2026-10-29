// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The driver taking a Train unattended to a Station: on a Line of two 20-block Spans with a Station
 * near each end, and round a loop of eight Arms, with round numbers for the drive.
 */
class DriverTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(40, 5, 0);
    /**
     * Beside the Rail five blocks from A, on its north side, so it serves Trains heading for A, and
     * five short of C on its south side, serving Trains heading for C: each a Train's right.
     */
    private static final Station MINE = new Station(5, 7, -2, "Mine");
    private static final Station DEPOT = new Station(35, 7, 2, "Depot");
    /** Far enough for any run here. */
    private static final int BUDGET = 2_000;

    private final RailNetwork network = new RailNetwork();
    private final Stations stations = new Stations();
    private final List<Locomotive> slotsFuelled = new ArrayList<>();

    @BeforeEach
    void spec() {
        // 1 block/tick at most, +0.1 a tick up, -0.25 a tick braking, -0.01 a tick coasting.
        LocomotiveSpec.configure(20.0, 40.0, 100.0, 4.0);
    }

    @AfterEach
    void resetSpec() {
        LocomotiveSpec.configure(LocomotiveSpec.DEFAULT_MAX_SPEED, LocomotiveSpec.DEFAULT_ACCELERATION,
                LocomotiveSpec.DEFAULT_BRAKING, LocomotiveSpec.DEFAULT_FRICTION);
        DriveSide.configure(DriveSide.DEFAULT);
    }

    // ---- the line ----

    private void line() {
        network.add(Span.of(A, B, 20));
        network.add(Span.of(B, C, 20));
        stations.put(MINE);
        stations.put(DEPOT);
    }

    /** A Locomotive facing C, a Cargo car, and a Locomotive facing A, the first car {@code fromA} blocks from A. */
    private Train shuttle(double fromA) {
        Span span = network.get(A, B);
        Train train = Train.of(new Place(span, B, span.length() - fromA), List.of(fuelled(), CargoCar.create()));
        return train.coupled(network, Train.End.BACK, fuelled(), true);
    }

    /** One Locomotive facing C, its middle {@code fromA} blocks from A. */
    private Train lone(double fromA) {
        Span span = fromA <= 20.0 ? network.get(A, B) : network.get(B, C);
        double toFront = fromA <= 20.0 ? 20.0 - fromA : 40.0 - fromA;
        return Train.of(new Place(span, fromA <= 20.0 ? B : C, toFront), fuelled());
    }

    private static Locomotive fuelled() {
        Locomotive locomotive = Locomotive.burner();
        locomotive.fuel().light(1_000_000_000L);
        return locomotive;
    }

    private boolean tick(Driver driver, Train train) {
        return driver.tick(network, train, slotsFuelled::contains, locomotive -> 0L, span -> false);
    }

    /** Ticks until the driver has it at its Station, failing past the budget; how many ticks it took. */
    private int drive(Driver driver, Train train) {
        for (int ticks = 0; ticks < BUDGET; ticks++) {
            if (tick(driver, train)) {
                return ticks;
            }
        }
        throw new AssertionError("never got there: " + train + " " + driver);
    }

    private void runFor(Driver driver, Train train, int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick(driver, train);
        }
    }

    /** How far along A to C the middle of {@code car} is. */
    private double alongLine(Train train, Car car) {
        Place place = train.placeOf(network, car);
        double fromSpanA = place.front().equals(place.span().a()) ? place.toFront() : place.span().length() - place.toFront();
        return place.span().a().x() + fromSpanA;
    }

    @Test
    void itStopsWithItsLeadingCarAtTheStation() {
        line();
        Train train = shuttle(13.0);
        drive(new Driver(DEPOT), train);
        assertTrue(train.atRest());
        // Its middle two blocks short of the Station's centre, its front at the block's front edge.
        assertEquals(33.0, alongLine(train, train.cars().getFirst()), Driver.ARRIVE);
        assertEquals(List.of(DEPOT), stations.stoppedAt(network, train));
    }

    @Test
    void itStaysStoppedThereOnceItArrives() {
        line();
        Train train = shuttle(13.0);
        Driver driver = new Driver(DEPOT);
        drive(driver, train);
        Place stopped = train.head();
        runFor(driver, train, 50);
        assertTrue(train.atRest());
        assertEquals(stopped, train.head());
        assertTrue(tick(driver, train));
    }

    @Test
    void setGoingAtTheStationItIsThereAlready() {
        line();
        // Its last car, leading toward A, at the Mine's stop, at 7.
        Train train = shuttle(17.0);
        assertTrue(tick(new Driver(MINE), train));
        assertTrue(train.atRest());
    }

    @Test
    void withALocomotiveFacingEachWayItRunsBackAndForthAlongTheLine() {
        line();
        Train train = shuttle(13.0);
        for (int trip = 0; trip < 2; trip++) {
            drive(new Driver(DEPOT), train);
            drive(new Driver(MINE), train);
            // Back at the Mine with the last car, the Locomotive facing A, leading.
            assertEquals(7.0, alongLine(train, train.cars().getLast()), Driver.ARRIVE);
            assertEquals(List.of(MINE), stations.stoppedAt(network, train));
        }
    }

    @Test
    void aTrainWithOneLocomotiveNeverBacksToAStation() {
        line();
        Train train = lone(35.0);
        Driver driver = new Driver(MINE);
        runFor(driver, train, 200);
        assertTrue(train.atRest());
        assertFalse(tick(driver, train));
        assertEquals(35.0, alongLine(train, train.cars().getFirst()), 1e-9);
    }

    @Test
    void aTrainWhoseFuelRunsOutAsItBrakesInStillGetsThere() {
        line();
        // Thirty blocks short of the Depot's stop, a run of round numbers.
        Train train = lone(3.0);
        Driver driver = new Driver(DEPOT);
        for (int i = 0; i < BUDGET && alongLine(train, train.cars().getFirst()) < 32.0; i++) {
            tick(driver, train);
        }
        assertTrue(train.speed() > 0.0, "it should still be braking in: " + train);
        ((Locomotive) train.cars().getFirst()).fuel().load(0L, 0L);
        runFor(driver, train, 20);
        assertTrue(train.atRest());
        assertTrue(tick(driver, train), "it stopped at the Station, dry: " + train);
    }

    @Test
    void aCarAtTheStationBehindTheWayItRunsIsNotItsLeadingCar() {
        line();
        // A Locomotive facing C at 38, its Cargo car behind it at 33 on the Depot's mark: it cannot
        // back, so its leading car is the Locomotive, five blocks past the mark.
        Span span = network.get(B, C);
        Train train = Train.of(new Place(span, C, 2.0), List.of(fuelled(), CargoCar.create()));
        assertEquals(33.0, alongLine(train, train.cars().getLast()), 1e-9);
        assertFalse(tick(new Driver(DEPOT), train));
    }

    @Test
    void aLocomotiveWithFuelOnlyInItsSlotStillPullsItThere() {
        line();
        Locomotive locomotive = Locomotive.burner();
        Span span = network.get(A, B);
        Train train = Train.of(new Place(span, B, 15.0), locomotive);
        slotsFuelled.add(locomotive);
        Driver driver = new Driver(DEPOT);
        boolean there = false;
        for (int i = 0; i < BUDGET && !there; i++) {
            there = driver.tick(network, train, slotsFuelled::contains, l -> 1_000_000_000L, s -> false);
        }
        assertTrue(there, "never got there: " + train);
    }

    @Test
    void aStationOnItsLeftIsOutOfReachTheWayItRuns() {
        line();
        Train train = lone(5.0);
        // On the north side by C: it serves Trains heading for A, and this one can only head for C.
        Driver driver = new Driver(new Station(35, 7, -2, "Yard"));
        runFor(driver, train, 200);
        assertTrue(train.atRest());
        assertFalse(tick(driver, train));
        assertEquals(5.0, alongLine(train, train.cars().getFirst()), 1e-9);
    }

    @Test
    void drivingOnTheLeftItStopsAtTheStationsOnItsLeft() {
        DriveSide.configure(DriveSide.LEFT);
        line();
        Train train = lone(5.0);
        Driver toDepot = new Driver(DEPOT);
        runFor(toDepot, train, 200);
        assertFalse(tick(toDepot, train), "the Depot is on its right: " + train);
        Station yard = new Station(35, 7, -2, "Yard");
        stations.put(yard);
        drive(new Driver(yard), train);
        assertEquals(33.0, alongLine(train, train.cars().getFirst()), Driver.ARRIVE);
        assertEquals(List.of(yard), stations.stoppedAt(network, train));
    }

    @Test
    void outOfReachItHoldsWhereItIs() {
        line();
        Train train = lone(10.0);
        // Beside no Rail at all.
        Driver driver = new Driver(new Station(10, 7, 40, "Nowhere"));
        runFor(driver, train, 50);
        assertTrue(train.atRest());
        assertEquals(10.0, alongLine(train, train.cars().getFirst()), 1e-9);
    }

    @Test
    void movingWhenTheStationGoesOutOfReachItBrakesToAStop() {
        line();
        Train train = lone(5.0);
        Driver driver = new Driver(DEPOT);
        runFor(driver, train, 15);
        assertTrue(train.speed() > 0.5, "it should be well under way: " + train);
        network.remove(B, C);
        runFor(driver, train, 20);
        assertTrue(train.atRest());
        assertFalse(tick(driver, train));
    }

    @Test
    void itDoesNotRunOntoASpanAnotherTrainHolds() {
        line();
        Train train = lone(5.0);
        Driver driver = new Driver(DEPOT);
        Span held = network.get(B, C);
        for (int i = 0; i < 200; i++) {
            driver.tick(network, train, slotsFuelled::contains, l -> 0L, span -> span.equals(held));
        }
        assertTrue(train.atRest());
        assertTrue(alongLine(train, train.cars().getFirst()) < 20.0, "it should stop short of B: " + train);
    }

    // ---- the loop ----

    /** Eight Arms round a square of 30 with its corners cut, so each bends 45 degrees; a Station on the south and north sides. */
    private void loop() {
        Arm[] arms = {new Arm(10, 5, 0), new Arm(20, 5, 0), new Arm(30, 5, 10), new Arm(30, 5, 20),
                new Arm(20, 5, 30), new Arm(10, 5, 30), new Arm(0, 5, 20), new Arm(0, 5, 10)};
        for (int i = 0; i < arms.length; i++) {
            Arm one = arms[i];
            Arm other = arms[(i + 1) % arms.length];
            network.add(Span.of(one, other, SpanRules.cost(one, other)));
        }
    }

    private static final Station SOUTH = new Station(15, 7, 2, "South");
    private static final Station NORTH = new Station(15, 7, 28, "North");

    /** One Locomotive on the south side at x {@code x}, heading east. */
    private Train eastbound(double x) {
        Span span = network.get(new Arm(10, 5, 0), new Arm(20, 5, 0));
        return Train.of(new Place(span, new Arm(20, 5, 0), 20.0 - x), fuelled());
    }

    @Test
    void itRunsRoundALoopFromStationToStationTheWayItFaces() {
        loop();
        stations.put(SOUTH);
        stations.put(NORTH);
        Train train = eastbound(15.0);
        for (int lap = 0; lap < 2; lap++) {
            drive(new Driver(NORTH), train);
            assertEquals(List.of(NORTH), stations.stoppedAt(network, train));
            drive(new Driver(SOUTH), train);
            assertEquals(List.of(SOUTH), stations.stoppedAt(network, train));
        }
    }

    @Test
    void aStationJustBehindIsReachedTheLongWayRound() {
        loop();
        Train train = eastbound(17.0);
        Driver driver = new Driver(SOUTH);
        tick(driver, train);
        assertTrue(train.speed() > 0.0, "it should set off forward: " + train);
        int ticks = drive(driver, train);
        // Round the loop's 90-odd blocks, never faster than a block a tick.
        assertTrue(ticks > 80, "it arrived in " + ticks + " ticks");
    }
}
