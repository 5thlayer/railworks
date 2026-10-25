// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;

/**
 * Every Train in a level, the one owner of each, so the cars coupled into a Train share it and a
 * car's entity finds its Train by the car's id. It couples, uncouples and drops cars, replacing a
 * Train with the Trains it becomes, and says which Train holds a Span (ADR 0003).
 *
 * <p>Pure: DataFixerUpper's codec, no Minecraft types.
 */
public final class Trains {

    /** How far from where a car would couple the player may aim and still couple it there, in blocks. */
    public static final double COUPLING_REACH = 1.5;

    public static final Codec<Trains> CODEC = Train.CODEC.listOf().xmap(
            list -> {
                Trains trains = new Trains();
                list.forEach(trains::add);
                return trains;
            },
            trains -> List.copyOf(trains.trains));

    /** Where a car would couple: the Train, its end, and the car's middle there with the Train's heading. */
    public record Slot(Train train, Train.End end, Place place) {
    }

    private final List<Train> trains = new ArrayList<>();
    private final Map<UUID, Train> byCar = new HashMap<>();

    public void add(Train train) {
        for (Car car : train.cars()) {
            if (byCar.containsKey(car.id())) {
                throw new IllegalArgumentException(car + " is already in " + byCar.get(car.id()));
            }
        }
        trains.add(train);
        train.cars().forEach(car -> byCar.put(car.id(), train));
    }

    public void remove(Train train) {
        if (trains.remove(train)) {
            train.cars().forEach(car -> byCar.remove(car.id()));
        }
    }

    /** {@code old} became {@code parts}: the cars it had are theirs now. */
    public void replace(Train old, List<Train> parts) {
        remove(old);
        parts.forEach(this::add);
    }

    /** The Train the car with this id is coupled into, or null. */
    public Train of(UUID carId) {
        return byCar.get(carId);
    }

    public List<Train> all() {
        return List.copyOf(trains);
    }

    /** The Train that holds {@code span}, or null when it is free. */
    public Train holding(RailNetwork network, Span span) {
        for (Train train : trains) {
            if (train.spans(network).contains(span)) {
                return train;
            }
        }
        return null;
    }

    /** The Spans every Train but {@code but} holds, which {@code but} may not run onto. */
    public Predicate<Span> takenFrom(RailNetwork network, Train but) {
        Set<Span> taken = new HashSet<>();
        for (Train train : trains) {
            if (train != but) {
                taken.addAll(train.spans(network));
            }
        }
        return taken::contains;
    }

    /**
     * The nearest place a car {@code length} blocks long would couple to a Train at rest, within
     * {@link #COUPLING_REACH} of {@code aim}; null when there is none in reach.
     */
    public Slot slotNear(RailNetwork network, Place.Point aim, double length) {
        Slot nearest = null;
        double best = COUPLING_REACH * COUPLING_REACH;
        for (Train train : trains) {
            if (train.speed() != 0.0) {
                continue;
            }
            for (Train.End end : Train.End.values()) {
                Place place = train.slot(network, end, length);
                if (place == null) {
                    continue;
                }
                double distance = distanceSquared(place.point(), aim);
                if (distance <= best) {
                    best = distance;
                    nearest = new Slot(train, end, place);
                }
            }
        }
        return nearest;
    }

    /**
     * Couples {@code car} into {@code slot}'s Train, facing against it when {@code reversed}, and
     * returns the Train it is now in; null, changing nothing, when it does not fit there or would
     * stand on a Span another Train holds.
     */
    public Train couple(RailNetwork network, Slot slot, Car car, boolean reversed) {
        Train coupled = slot.train().coupled(network, slot.end(), car, reversed);
        if (coupled == null || !trains.contains(slot.train())) {
            return null;
        }
        Predicate<Span> taken = takenFrom(network, slot.train());
        if (coupled.spans(network).stream().anyMatch(taken)) {
            return null;
        }
        replace(slot.train(), List.of(coupled));
        return coupled;
    }

    /** Parts {@code train} at the coupler {@code at}, and returns the two Trains; null, changing nothing, when it cannot. */
    public List<Train> uncouple(RailNetwork network, Train train, int at) {
        List<Train> parts = train.uncoupled(network, at);
        if (parts == null || !trains.contains(train)) {
            return null;
        }
        replace(train, parts);
        return parts;
    }

    /** The car with this id is gone: its Train closes up without it, parting where it stood. */
    public void removeCar(RailNetwork network, UUID carId) {
        Train train = of(carId);
        if (train != null) {
            replace(train, train.without(network, train.car(carId)));
        }
    }

    /** Every Train on a Span at {@code from}: that Arm moved to {@code to}, so it stays on its Rail. */
    public void moveArm(RailNetwork network, Arm from, Arm to) {
        trains.forEach(train -> train.moveArm(network, from, to));
    }

    private static double distanceSquared(Place.Point one, Place.Point other) {
        double dx = one.x() - other.x();
        double dy = one.y() - other.y();
        double dz = one.z() - other.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
