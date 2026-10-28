// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * What a level shows of its Trains (#6): a Line of two 20-block Spans along x, where only the spots
 * west of x 20 can show a view, as if the chunks east of it were not loaded.
 */
class ViewsTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(40, 5, 0);
    private static final double EPS = 1e-9;
    private static final Predicate<Place.Point> WEST_OF_B = point -> point.x() < 20.0;

    private final RailNetwork network = new RailNetwork();
    private final Trains trains = new Trains();

    ViewsTest() {
        network.add(Span.of(A, B, 20));
        network.add(Span.of(B, C, 20));
    }

    /** A Train heading east with its first car {@code toFront} blocks short of {@code front}, on the Span ending there. */
    private Train train(Arm back, Arm front, double toFront, Car... cars) {
        Train train = Train.of(new Place(network.get(back, front), front, toFront), List.of(cars));
        trains.add(train);
        return train;
    }

    private Views.Change plan(Set<UUID> viewed, Set<UUID> ridden) {
        return Views.plan(network, trains, WEST_OF_B, viewed, ridden);
    }

    @Test
    void aCarWithNoViewIsShownWhereItsSpotCanShowOne() {
        Locomotive car = Locomotive.burner();
        Train train = train(A, B, 10.0, car);
        Views.Change change = plan(Set.of(), Set.of());
        assertEquals(List.of(new Views.Shown(train, car)), change.show());
        assertTrue(change.takeAway().isEmpty());
    }

    @Test
    void aCarOutOfViewIsNotShownAndKeepsRunning() {
        Locomotive car = Locomotive.burner();
        Train train = train(B, C, 10.0, car);
        Views.Change change = plan(Set.of(), Set.of());
        assertTrue(change.show().isEmpty());
        assertTrue(change.takeAway().isEmpty());
        // Still the model's: it is where its Train puts it.
        assertEquals(30.0, change.at().get(car.id()).x(), EPS);
        assertEquals(train.placeOf(network, car).point(network), change.at().get(car.id()));
    }

    @Test
    void aViewWhoseSpotCanNoLongerShowItIsTakenAway() {
        Locomotive car = Locomotive.burner();
        train(B, C, 10.0, car);
        Views.Change change = plan(Set.of(car.id()), Set.of());
        assertEquals(List.of(car.id()), change.takeAway());
        assertTrue(change.show().isEmpty());
    }

    @Test
    void aViewAPlayerRidesIsNeverTakenAway() {
        Locomotive car = Locomotive.burner();
        train(B, C, 10.0, car);
        Views.Change change = plan(Set.of(car.id()), Set.of(car.id()));
        assertTrue(change.takeAway().isEmpty());
        assertTrue(change.show().isEmpty());
    }

    @Test
    void aViewAlreadyShownIsNotShownAgain() {
        Locomotive car = Locomotive.burner();
        train(A, B, 10.0, car);
        Views.Change change = plan(Set.of(car.id()), Set.of());
        assertTrue(change.show().isEmpty());
        assertTrue(change.takeAway().isEmpty());
    }

    @Test
    void aTrainAcrossTheEdgeShowsOnlyItsCarsWhoseSpotsCanShowThem() {
        Locomotive first = Locomotive.burner();
        CargoCar second = CargoCar.create();
        // Its first car just east of B, the one behind it west of B.
        Train train = train(B, C, 19.0, first, second);
        Views.Change change = plan(Set.of(first.id()), Set.of());
        assertEquals(List.of(new Views.Shown(train, second)), change.show());
        assertEquals(List.of(first.id()), change.takeAway());
    }

    @Test
    void aViewOfACarNoTrainHoldsIsTakenAway() {
        UUID gone = UUID.randomUUID();
        Views.Change change = plan(Set.of(gone), Set.of(gone));
        assertEquals(List.of(gone), change.takeAway());
    }

    @Test
    void aTrainOutOfViewAdvancesAsOneInView() {
        // Two copies of one Train, ticked the same: what the level shows never changes where it is.
        Train seen = Train.of(new Place(network.get(A, B), B, 10.0), Locomotive.burner());
        Train unseen = Train.of(new Place(network.get(A, B), B, 10.0), Locomotive.burner());
        trains.add(unseen);
        for (int tick = 0; tick < 100; tick++) {
            seen.tick(network, Throttle.FORWARD, locomotive -> 1_000_000L);
            unseen.tick(network, Throttle.FORWARD, locomotive -> 1_000_000L);
            Views.plan(network, trains, point -> false, Set.of(), Set.of());
        }
        assertEquals(seen.head(), unseen.head());
        assertEquals(seen.speed(), unseen.speed(), EPS);
        assertTrue(unseen.head().span().equals(network.get(B, C)), "it ran on past B: " + unseen.head());
    }
}
