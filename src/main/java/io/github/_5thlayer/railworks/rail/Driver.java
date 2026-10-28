// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Drives a Train to one Station and stops it there, as an automatic Train is taken to its job's
 * Stations (ADR 0008): it runs on, brakes in time and stops with its leading car, its
 * first car the way it runs, within {@link #ARRIVE} of the Station's {@link Platform#leadMark}, where
 * the leading car's middle stands, well within the Station's {@link Station#STRETCH}.
 *
 * <p>With no Junctions a Train reaches a Station only along its own Line, either way: round a
 * closed loop, or back the way it came when it carries a Locomotive facing that way, and only
 * arriving the Station's serving way, so a Station on the wrong side of the Train for its drive side
 * is out of reach that way, and a Station under the Rail either way. It runs only
 * the way a Locomotive able to pull faces, one with fuel in its buffer or its slots, so a Train with
 * one Locomotive never backs to a Station; of the ways it can, it takes the nearer, and keeps to it
 * until it is there, even should its fuel run out on the way in. Where the Station is out of reach
 * either way it holds where it is.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Driver {

    /** How near the Station's lead mark its leading car's middle stops, in blocks. */
    public static final double ARRIVE = 0.25;

    private final Station station;
    /** Whether it keeps to the way it was given, never picking another. */
    private final boolean fixed;
    /** The way it runs to the Station, +1 the Train's heading and -1 against it; 0 until it picks one. */
    private int way;
    /** Whether the Station was out of reach the last tick it looked. */
    private boolean lost;

    public Driver(Station station) {
        this.station = station;
        this.fixed = false;
    }

    /**
     * A driver that runs only {@code way}, +1 the Train's heading and -1 against it, so the Train
     * arrives with the leading car it was planned to: its cars stand on the car spots its job counts on.
     */
    public Driver(Station station, int way) {
        if (way != 1 && way != -1) {
            throw new IllegalArgumentException("way " + way);
        }
        this.station = station;
        this.fixed = true;
        this.way = way;
    }

    /** The Station it drives to. */
    public Station station() {
        return station;
    }

    /** Whether the Station was out of reach the last tick it looked: cut off from it, or arriving against its serving way. */
    public boolean lost() {
        return lost;
    }

    /**
     * One tick of driving {@code train}, asking {@code waiting} whether fuel waits in a
     * Locomotive's slots or charge in its record, which only the level knows, and lighting or drawing
     * it through {@code ignite}; it does
     * not run onto a Span {@code taken} says another Train holds. Whether the Train now stands at
     * rest at the Station: true from the tick it stopped there on, a Train set going there already
     * included.
     */
    public boolean tick(RailNetwork network, Train train, Predicate<Locomotive> waiting,
                        ToLongFunction<Locomotive> ignite, Predicate<Span> taken) {
        if (there(network, train, waiting)) {
            train.tick(network, 0, false, ignite, taken);
            return true;
        }
        train.tick(network, push(network, train, waiting), false, ignite, taken);
        return false;
    }

    /**
     * Whether it stands at rest with its leading car at the Station: within {@link #ARRIVE} short of
     * its lead mark, or past it by no more than the Station's stretch, where a Train that braked late
     * stops. The leading car is the first the way it runs; set going with no way yet, either way it
     * can pull.
     */
    private boolean there(RailNetwork network, Train train, Predicate<Locomotive> waiting) {
        if (!train.atRest()) {
            return false;
        }
        if (way != 0) {
            return standsAt(network, train, way, station);
        }
        for (int way : new int[] {1, -1}) {
            if (train.pulls(way, waiting) && standsAt(network, train, way, station)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code train}, at rest, stands at {@code station} running {@code way}: its leading car
     * within {@link #ARRIVE} short of the lead mark, or past it by no more than the Station's stretch,
     * as a Train driven there stops.
     */
    static boolean standsAt(RailNetwork network, Train train, int way, Station station) {
        // NaN, out of reach, compares false.
        return train.atRest() && distance(network, train, way, station) <= ARRIVE;
    }

    /** The way {@code train}, at rest, runs to stand at {@code station}, as {@link #standsAt} says; 0 where it does not stand there. */
    static int wayAt(RailNetwork network, Train train, Station station) {
        for (int way : new int[] {1, -1}) {
            if (standsAt(network, train, way, station)) {
                return way;
            }
        }
        return 0;
    }

    /** The way to push it this tick, +1 the Train's heading, -1 against it and 0 for neither. */
    private int push(RailNetwork network, Train train, Predicate<Locomotive> waiting) {
        // At rest unable to pull its way, it looks for the other; moving, it runs on, out of fuel or not.
        if (!fixed && (way == 0 || train.atRest() && !train.pulls(way, waiting))) {
            way = pick(network, train, waiting);
        }
        if (way == 0) {
            lost = true;
            return hold(train);
        }
        double d = distance(network, train, way, station);
        lost = Double.isNaN(d);
        if (lost) {
            // Cut off from it, or left too far behind: the next tick looks again.
            if (!fixed) {
                way = 0;
            }
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

    /** The nearer way it can pull to the Station's lead mark ahead of its leading car, past where it stands; 0 for neither. */
    private int pick(RailNetwork network, Train train, Predicate<Locomotive> waiting) {
        int best = 0;
        double nearest = Double.POSITIVE_INFINITY;
        for (int way : new int[] {1, -1}) {
            if (!train.pulls(way, waiting)) {
                continue;
            }
            double d = distance(network, train, way, station);
            if (d > ARRIVE && d < nearest) {
                nearest = d;
                best = way;
            }
        }
        return best;
    }

    /**
     * How far ahead of its leading car the Station's {@link Platform#leadMark} is, running {@code way},
     * along the Line through the Arms: negative when the car has run past it by up to a Station's
     * stretch, and NaN when it is out of reach or would arrive against the Station's serving way.
     */
    private static double distance(RailNetwork network, Train train, int way, Station station) {
        Place lead = train.lead(network, way);
        return Platform.of(network, station)
                .map(platform -> platform.toLeadMark(network, lead, Station.STRETCH))
                .orElse(Double.NaN);
    }

    @Override
    public String toString() {
        return "Driver[to " + station + ", way " + way + "]";
    }
}
