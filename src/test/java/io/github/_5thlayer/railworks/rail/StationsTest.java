// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Which Stations a Train is stopped at, the question loading its Cargo cars asks (#25). */
class StationsTest {

    /** A level Span along x, its Arms' tops at y 5. */
    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Span AB = Span.of(A, B, 20);
    /** From one car's centre to the next's. */
    private static final double PITCH = (Locomotive.LENGTH + CargoCar.LENGTH) / 2.0;

    private final RailNetwork network = new RailNetwork();
    private final Stations stations = new Stations();
    /** Beside the Rail, ten blocks from A. */
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
    }

    /** A Locomotive with two Cargo cars behind it, the Locomotive's centre {@code fromA} blocks from A, heading for B. */
    private static Train train(double fromA) {
        return Train.of(new Place(AB, B, AB.length() - fromA),
                List.of(Locomotive.burner(), CargoCar.create(), CargoCar.create()));
    }

    @Test
    void aTrainWhoseLocomotiveIsByAStationIsStoppedAtIt() {
        // The Locomotive is at the Mine; its Cargo cars, one and two cars back, are not by it.
        Train train = train(10.0);
        assertTrue(stations.at(network, train.placeOf(network, train.cars().getLast())).isEmpty());
        assertEquals(List.of(mine), stations.stoppedAt(network, train));
    }

    @Test
    void aTrainWhoseLastCarIsByAStationIsStoppedAtIt() {
        assertEquals(List.of(mine), stations.stoppedAt(network, train(10.0 + 2 * PITCH)));
    }

    @Test
    void aTrainAtRestWithNoCarByAStationIsStoppedAtNone() {
        assertTrue(stations.stoppedAt(network, train(16.0)).isEmpty());
    }

    @Test
    void aTrainBetweenTwoStationsIsStoppedAtBoth() {
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
