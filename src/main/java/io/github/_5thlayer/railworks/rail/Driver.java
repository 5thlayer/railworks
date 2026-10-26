// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Drives a Train to one Station and stops it there, as an automatic Train is taken to its job's
 * Stations (ADR 0008): it runs on, brakes in time and stops with its leading car, its
 * first car the way it runs, within {@link #ARRIVE} of the Station's foot, well within the
 * Station's {@link Station#STRETCH}.
 *
 * <p>With no Junctions a Train reaches a Station only along its own Line, either way: round a
 * closed loop, or back the way it came when it carries a Locomotive facing that way. It runs only
 * the way a Locomotive able to pull faces, one with fuel in its buffer or its slot, so a Train with
 * one Locomotive never backs to a Station; of the ways it can, it takes the nearer, and keeps to it
 * until it is there, even should its fuel run out on the way in. Where the Station is out of reach
 * either way it holds where it is.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Driver {

    /** How near the Station's foot its leading car's middle stops, in blocks. */
    public static final double ARRIVE = 0.25;

    private final Station station;
    /** The way it runs to the Station, +1 the Train's heading and -1 against it; 0 until it picks one. */
    private int way;

    public Driver(Station station) {
        this.station = station;
    }

    /** The Station it drives to. */
    public Station station() {
        return station;
    }

    /**
     * One tick of driving {@code train}, asking {@code slotFuelled} whether fuel waits in a
     * Locomotive's slot, which only its entity knows, and lighting it through {@code ignite}; it does
     * not run onto a Span {@code taken} says another Train holds. Whether the Train now stands at
     * rest at the Station: true from the tick it stopped there on, a Train set going there already
     * included.
     */
    public boolean tick(RailNetwork network, Train train, Predicate<Locomotive> slotFuelled,
                        ToLongFunction<Locomotive> ignite, Predicate<Span> taken) {
        if (there(network, train, slotFuelled)) {
            train.tick(network, 0, false, ignite, taken);
            return true;
        }
        train.tick(network, push(network, train, slotFuelled), false, ignite, taken);
        return false;
    }

    /**
     * Whether it stands at rest with its leading car at the Station: within {@link #ARRIVE} short of
     * the foot, or past it by no more than the Station's stretch, where a Train that braked late
     * stops. The leading car is the first the way it runs; set going with no way yet, either way it
     * can pull.
     */
    private boolean there(RailNetwork network, Train train, Predicate<Locomotive> slotFuelled) {
        if (!train.atRest()) {
            return false;
        }
        // NaN, out of reach, compares false.
        if (way != 0) {
            return distance(network, train, way) <= ARRIVE;
        }
        for (int way : new int[] {1, -1}) {
            if (train.pulls(way, slotFuelled) && distance(network, train, way) <= ARRIVE) {
                return true;
            }
        }
        return false;
    }

    /** The way to push it this tick, +1 the Train's heading, -1 against it and 0 for neither. */
    private int push(RailNetwork network, Train train, Predicate<Locomotive> slotFuelled) {
        // At rest unable to pull its way, it looks for the other; moving, it runs on, out of fuel or not.
        if (way == 0 || train.atRest() && !train.pulls(way, slotFuelled)) {
            way = pick(network, train, slotFuelled);
        }
        if (way == 0) {
            return hold(train);
        }
        double d = distance(network, train, way);
        if (Double.isNaN(d)) {
            // Cut off from it, or left too far behind: the next tick looks again.
            way = 0;
            return hold(train);
        }
        double v = train.speed() * way;
        if (v < 0.0) {
            return way;
        }
        if (d <= ARRIVE) {
            return -way;
        }
        // Braking from v covers about v^2/2b, and this tick's v more before it bites.
        if (v * v / (2.0 * train.brakingRate()) + v >= d) {
            return -way;
        }
        return way;
    }

    /** Brakes a moving Train, and holds one at rest. */
    private static int hold(Train train) {
        return train.speed() > 0.0 ? -1 : train.speed() < 0.0 ? 1 : 0;
    }

    /** The nearer way it can pull to the Station's foot ahead of its leading car, past where it stands; 0 for neither. */
    private int pick(RailNetwork network, Train train, Predicate<Locomotive> slotFuelled) {
        int best = 0;
        double nearest = Double.POSITIVE_INFINITY;
        for (int way : new int[] {1, -1}) {
            if (!train.pulls(way, slotFuelled)) {
                continue;
            }
            double d = distance(network, train, way);
            if (d > ARRIVE && d < nearest) {
                nearest = d;
                best = way;
            }
        }
        return best;
    }

    /** Its first car the way it runs, facing that way. */
    private static Place lead(RailNetwork network, Train train, int way) {
        return way > 0 ? train.placeOf(network, train.cars().getFirst())
                : train.placeOf(network, train.cars().getLast()).turned();
    }

    /**
     * How far ahead of its leading car the Station's foot is, running {@code way}, along the
     * Line through the Arms ({@link Place#distanceTo}): negative when the car has run past it by up
     * to a Station's stretch, and NaN when it is out of reach.
     */
    private double distance(RailNetwork network, Train train, int way) {
        Optional<Station.Foot> foot = station.foot(network);
        if (foot.isEmpty()) {
            return Double.NaN;
        }
        Place lead = lead(network, train, way);
        Span span = foot.get().span();
        double fromA = foot.get().fromA();
        double behind = lead.turned().distanceTo(network, span, fromA);
        if (behind >= 0.0 && behind <= Station.STRETCH) {
            return -behind;
        }
        double ahead = lead.distanceTo(network, span, fromA);
        return ahead >= 0.0 ? ahead : Double.NaN;
    }

    @Override
    public String toString() {
        return "Driver[to " + station + ", way " + way + "]";
    }
}
