// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** A level's Trains on a Line of three Arms, 20 blocks a Span. */
class TrainsTest {

    private static final Arm A = new Arm(0, 5, 0);
    private static final Arm B = new Arm(20, 5, 0);
    private static final Arm C = new Arm(40, 5, 0);
    private static final double EPS = 1e-9;

    private final RailNetwork network = new RailNetwork();
    private final Trains trains = new Trains();

    TrainsTest() {
        network.add(Span.of(A, B, 20));
        network.add(Span.of(B, C, 20));
    }

    private Train locomotiveAt(double toFront) {
        Train train = Train.of(new Place(network.get(A, B), B, toFront), Locomotive.burner());
        trains.add(train);
        return train;
    }

    @Test
    void aCarFindsItsTrainByItsId() {
        Train train = locomotiveAt(10.0);
        assertSame(train, trains.of(train.cars().getFirst().id()));
        assertNull(trains.of(CargoCar.create().id()));
    }

    @Test
    void aSpanIsHeldByTheTrainStandingOnIt() {
        Train train = locomotiveAt(10.0);
        assertSame(train, trains.holding(network, network.get(A, B)));
        assertNull(trains.holding(network, network.get(B, C)));
        assertTrue(trains.takenFrom(network, null).test(network.get(A, B)));
        assertTrue(!trains.takenFrom(network, train).test(network.get(A, B)), "a Train does not keep itself off its own Span");
    }

    @Test
    void aCarAimedBesideAnEndOfATrainAtRestCouplesThere() {
        Train train = locomotiveAt(10.0);
        // Five blocks behind the Locomotive's middle, toward A, is where a Cargo car would stand.
        Place aimed = new Place(network.get(A, B), B, 15.0);
        Trains.Slot slot = trains.slotNear(network, aimed.point(network), CargoCar.LENGTH);
        assertNotNull(slot);
        assertSame(train, slot.train());
        assertEquals(Train.End.BACK, slot.end());
        assertEquals(15.0, slot.place().toFront(), EPS);
        assertNull(trains.slotNear(network, new Place(network.get(A, B), B, 18.5).point(network), CargoCar.LENGTH),
                "too far from either end");
    }

    @Test
    void aMovingTrainOffersNoSlot() {
        Train train = locomotiveAt(10.0);
        train.cars().stream().map(Locomotive.class::cast).forEach(l -> l.fuel().light(1_000_000_000L));
        train.tick(network, Throttle.FORWARD, car -> 0L);
        assertNull(trains.slotNear(network, new Place(network.get(A, B), B, 15.0).point(network), CargoCar.LENGTH));
    }

    @Test
    void couplingReplacesTheTrainWithTheLongerOne() {
        Train train = locomotiveAt(10.0);
        CargoCar cargo = CargoCar.create();
        Trains.Slot slot = trains.slotNear(network, new Place(network.get(A, B), B, 15.0).point(network), CargoCar.LENGTH);
        Train coupled = trains.couple(network, slot, cargo, false);
        assertEquals(List.of(coupled), trains.all());
        assertSame(coupled, trains.of(cargo.id()));
        assertSame(coupled, trains.of(train.cars().getFirst().id()));
    }

    @Test
    void aCarDoesNotCoupleOntoASpanAnotherTrainHolds() {
        // A Locomotive 0.5 from B: a car coupled ahead of it would stand on B to C.
        Train train = locomotiveAt(0.5 + Locomotive.LENGTH / 2.0);
        trains.add(Train.of(new Place(network.get(B, C), C, 10.0), Locomotive.burner()));
        Trains.Slot slot = new Trains.Slot(train, Train.End.FRONT, train.slot(network, Train.End.FRONT, CargoCar.LENGTH));
        assertNull(trains.couple(network, slot, CargoCar.create(), false));
        assertEquals(2, trains.all().size());
    }

    @Test
    void uncouplingLeavesTwoTrainsEachFoundByItsCars() {
        Train train = Train.of(new Place(network.get(A, B), B, 10.0), List.of(Locomotive.burner(), CargoCar.create()));
        trains.add(train);
        List<Train> parts = trains.uncouple(network, train, 1);
        assertEquals(parts, trains.all());
        assertSame(parts.get(1), trains.of(train.cars().get(1).id()));
    }

    @Test
    void aCarGoneTakesItselfOutOfItsTrain() {
        Train train = Train.of(new Place(network.get(A, B), B, 10.0), List.of(Locomotive.burner(), CargoCar.create()));
        trains.add(train);
        trains.removeCar(network, train.cars().get(1).id());
        assertEquals(1, trains.all().size());
        assertEquals(List.of(train.cars().getFirst()), trains.all().getFirst().cars());
        assertNull(trains.of(train.cars().get(1).id()));
        trains.removeCar(network, train.cars().getFirst().id());
        assertTrue(trains.all().isEmpty());
    }

    @Test
    void itRoundTripsThroughItsCodec() {
        Train train = Train.of(new Place(network.get(A, B), B, 10.0), List.of(Locomotive.burner(), CargoCar.create()));
        trains.add(train);
        locomotiveAt(17.0);
        var json = Trains.CODEC.encodeStart(JsonOps.INSTANCE, trains).getOrThrow();
        Trains back = Trains.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(2, back.all().size());
        Train loaded = back.of(train.cars().get(1).id());
        assertNotNull(loaded);
        assertEquals(train.head(), loaded.head());
        assertEquals(train.spans(network), loaded.spans(network));
    }
}
