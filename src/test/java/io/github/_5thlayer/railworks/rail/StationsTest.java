// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Which Stations a Train is stopped at: those its leading car is at, arriving their serving way, the
 * question its hatches will ask (ADR 0008).
 */
class StationsTest {

    /** A level Span along x, its Arms' tops at y 5. */
    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Span AB = Span.of(A, B, 20);
    /** From one car's centre to the next's. */
    private static final double PITCH = (Locomotive.LENGTH + CargoCar.LENGTH) / 2.0;

    private final RailNetwork network = new RailNetwork();
    private final Stations stations = new Stations();
    /** Beside the Rail ten blocks from A, on its south side: on the right of a Train heading for B. */
    private final Station mine = new Station(10, 6, 1, "Mine");

    @BeforeEach
    void line() {
        LocomotiveSpec.configure(20.0, 40.0, 100.0, 4.0);
        network.add(AB);
        stations.put(mine);
    }

    @AfterEach
    void resetSpec() {
        LocomotiveSpec.configure(LocomotiveSpec.DEFAULT_MAX_SPEED, LocomotiveSpec.DEFAULT_ACCELERATION,
                LocomotiveSpec.DEFAULT_BRAKING, LocomotiveSpec.DEFAULT_FRICTION);
        DriveSide.configure(DriveSide.DEFAULT);
    }

    /** A Locomotive with two Cargo cars behind it, the Locomotive's centre {@code fromA} blocks from A, heading for B. */
    private static Train train(double fromA) {
        return Train.of(new Place(AB, B, AB.length() - fromA),
                List.of(Locomotive.burner(), CargoCar.create(), CargoCar.create()));
    }

    /** The same, heading for A, its first car {@code fromA} blocks from A and the rest behind it toward B. */
    private static Train westbound(double fromA) {
        return Train.of(new Place(AB, A, fromA),
                List.of(Locomotive.burner(), CargoCar.create(), CargoCar.create()));
    }

    @Test
    void aTrainWhoseLeadingCarIsAtAStationIsStoppedAtIt() {
        // The Locomotive is at the Mine's stop, half a block short of it; its Cargo cars stand behind it.
        Train train = train(9.5);
        assertTrue(stations.at(network, train.placeOf(network, train.cars().getLast())).isEmpty());
        assertEquals(List.of(mine), stations.stoppedAt(network, train));
    }

    @Test
    void aTrainBackedInWithItsLastCarLeadingIsStoppedAtAStationServingThatWay() {
        // On the north side: it serves Trains heading for A, and this one's last car leads that way.
        Station yard = new Station(10, 6, -1, "Yard");
        stations.put(yard);
        assertEquals(List.of(yard), stations.stoppedAt(network, train(10.5 + 2 * PITCH)));
    }

    @Test
    void aTrainThatCameInTheOtherWayIsNotStoppedAtTheStation() {
        // Its Locomotive at the Mine, heading for A, its cars past the Station on the side it serves from.
        assertTrue(stations.stoppedAt(network, westbound(10.0)).isEmpty());
        assertTrue(stations.stoppedAt(network, westbound(10.5)).isEmpty());
    }

    @Test
    void aLoneLocomotiveIsStoppedAtAStationOnlyFacingItsServingWay() {
        assertEquals(List.of(mine), stations.stoppedAt(network, Train.of(new Place(AB, B, 10.5), Locomotive.burner())));
        assertTrue(stations.stoppedAt(network, Train.of(new Place(AB, A, 9.5), Locomotive.burner())).isEmpty());
    }

    @Test
    void drivingOnTheLeftTheSameStationServesTheOtherWay() {
        DriveSide.configure(DriveSide.LEFT);
        assertTrue(stations.stoppedAt(network, train(9.5)).isEmpty());
        assertEquals(List.of(mine), stations.stoppedAt(network, westbound(10.5)));
    }

    @Test
    void aTrainAtRestWithNoCarByAStationIsStoppedAtNone() {
        assertTrue(stations.stoppedAt(network, train(16.0)).isEmpty());
    }

    @Test
    void aTrainBetweenTwoStationsIsStoppedAtBoth() {
        // The Smelter on the north side by its last car, which leads toward A.
        Station smelter = new Station(10 - (int) Math.round(2 * PITCH), 6, -1, "Smelter");
        stations.put(smelter);
        assertEquals(List.of(mine, smelter), stations.stoppedAt(network, train(10.0)));
    }

    @Test
    void aMovingTrainIsStoppedAtNoneEvenBesideAStation() {
        Train train = train(10.0);
        ((Locomotive) train.cars().getFirst()).fuel().light(1_000_000_000L);
        train.tick(network, Throttle.FORWARD, locomotive -> 0L);
        assertTrue(train.speed() > 0.0);
        assertTrue(stations.at(network, train.placeOf(network, train.cars().getFirst())).contains(mine));
        assertTrue(stations.stoppedAt(network, train).isEmpty());
    }
}
