// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

/**
 * A Train's Schedule: the Stations it runs through, by name, each with when to leave, and how far
 * through them it is. While it is {@link #automatic()} it drives its Train unattended, as Factorio's
 * automatic mode does: on to the nearest Station of the entry's name it can reach, where it stops,
 * waits until the entry's {@link Wait} holds, and goes on to the next entry, back to the first after
 * the last.
 *
 * <p>With no Junctions a Train reaches a Station only along its own Line, either way: round a
 * closed loop, or back the way it came when it carries a Locomotive facing that way. It runs only the
 * way a Locomotive able to pull faces, one with fuel in its buffer or its slot, so a Train with one
 * Locomotive never backs to a Station; it goes the nearer way of those it can, and waits where no
 * Station of the name is in reach.
 *
 * <p>It stops with its leading car, its first car the way it runs, at the Station's foot, within
 * {@link #ARRIVE} of it: well within the Station's {@link Station#STRETCH}.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Schedule {

    /** The most entries a Schedule holds, which its menu shows at once. */
    public static final int MAX_ENTRIES = 8;
    /** The longest a {@link Wait#TIME} wait may be, in seconds: an hour. */
    public static final int MAX_SECONDS = 3_600;
    /** What a {@link Wait#TIME} entry waits until set otherwise, in seconds. */
    public static final int DEFAULT_SECONDS = 5;
    /** How near the Station's foot its leading car's middle stops, in blocks. */
    public static final double ARRIVE = 0.25;

    /** When a Train leaves a Station: once every Cargo car is full, or empty, or after its seconds there. */
    public enum Wait {
        CARGO_FULL, CARGO_EMPTY, TIME;

        public static final Codec<Wait> CODEC = Codec.STRING.comapFlatMap(name -> {
            for (Wait wait : values()) {
                if (wait.serializedName().equals(name)) {
                    return DataResult.success(wait);
                }
            }
            return DataResult.error(() -> "no wait " + name);
        }, Wait::serializedName);

        /** The name it is saved and sent under. */
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The next one, for a menu's button that cycles through them. */
        public Wait next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * One stop: the name of the Station, and when to leave it, {@code until}; {@code seconds} counts for a
     * {@link Wait#TIME} wait only, and is kept for the others so switching back finds it.
     */
    public record Entry(String station, Wait until, int seconds) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("station").forGetter(Entry::station),
                Wait.CODEC.fieldOf("until").forGetter(Entry::until),
                Codec.INT.optionalFieldOf("seconds", DEFAULT_SECONDS).forGetter(Entry::seconds)
        ).apply(i, Entry::new));

        public Entry {
            seconds = Math.max(1, Math.min(MAX_SECONDS, seconds));
        }

        public static Entry of(String station, Wait until) {
            return new Entry(station, until, DEFAULT_SECONDS);
        }
    }

    /** How full a Cargo car is, for a cargo wait. */
    public enum Load {
        EMPTY, PARTLY, FULL
    }

    /** What a Schedule asks of its Train's cars that only their entities know. */
    public interface Cars {

        /** Whether an item waits in the Locomotive's fuel slot, to light when its buffer runs low. */
        boolean hasFuel(Locomotive locomotive);

        /** How full the Cargo car is; {@link Load#PARTLY} when nothing can tell. */
        Load load(CargoCar car);
    }

    /** The Station it is running to, by its block, and the way it runs there: +1 the Train's heading. */
    record Target(int x, int y, int z, int way) {

        static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Target::x),
                Codec.INT.fieldOf("y").forGetter(Target::y),
                Codec.INT.fieldOf("z").forGetter(Target::z),
                Codec.INT.fieldOf("way").forGetter(Target::way)
        ).apply(i, Target::new));
    }

    public static final Codec<Schedule> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(s -> s.entries),
            Codec.BOOL.fieldOf("automatic").forGetter(s -> s.automatic),
            Codec.INT.optionalFieldOf("index", 0).forGetter(s -> s.index),
            Codec.BOOL.optionalFieldOf("waiting", false).forGetter(s -> s.waiting),
            Codec.INT.optionalFieldOf("waited", 0).forGetter(s -> s.waited),
            Target.CODEC.optionalFieldOf("target").forGetter(s -> Optional.ofNullable(s.target))
    ).apply(i, Schedule::restore));

    private List<Entry> entries = List.of();
    private boolean automatic;
    /** The entry it runs to, or waits at. */
    private int index;
    private boolean waiting;
    /** Ticks it has waited at the Station of its entry. */
    private int waited;
    private @Nullable Target target;

    /** An empty Schedule, which drives nothing. */
    public Schedule() {
    }

    private static Schedule restore(List<Entry> entries, boolean automatic, int index, boolean waiting, int waited,
                                    Optional<Target> target) {
        Schedule schedule = new Schedule();
        schedule.set(entries, automatic);
        if (index < schedule.entries.size()) {
            schedule.index = index;
            schedule.waiting = waiting;
            schedule.waited = Math.max(0, waited);
            schedule.target = target.orElse(null);
        }
        return schedule;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Whether it drives its Train. */
    public boolean automatic() {
        return automatic;
    }

    /** The entry it runs to or waits at. */
    public int index() {
        return index;
    }

    /** Whether it waits at the Station of its entry. */
    public boolean waiting() {
        return waiting;
    }

    /**
     * Sets what the menu edited: the entries, at most {@link #MAX_ENTRIES} of them, and whether it
     * drives the Train, which an empty Schedule never does. Any change starts its entry afresh, the
     * same entry where there still is one, so a Train stopped at that entry's Station waits there anew.
     */
    public void set(List<Entry> entries, boolean automatic) {
        List<Entry> kept = List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
        boolean drives = automatic && !kept.isEmpty();
        if (kept.equals(this.entries) && drives == this.automatic) {
            return;
        }
        this.entries = kept;
        this.automatic = drives;
        if (index >= kept.size()) {
            index = 0;
        }
        restart();
    }

    /** A driver took over: it stops driving, and starts its entry afresh when it is set going again. */
    public void manual() {
        if (automatic) {
            automatic = false;
            restart();
        }
    }

    /** The same entries for a Train parted from its Train, which does not drive it: it starts at the first. */
    Schedule parted() {
        Schedule parted = new Schedule();
        parted.set(entries, false);
        return parted;
    }

    /** The name of the Station it runs to, while it drives and is not waiting; null otherwise. */
    public @Nullable String heading() {
        return automatic && !waiting ? entries.get(index).station() : null;
    }

    /** The name of the Station it waits at, while it drives; null otherwise. */
    public @Nullable String waitingAt() {
        return automatic && waiting ? entries.get(index).station() : null;
    }

    /** The name of the Station after the one it waits at, while it waits; null otherwise. */
    public @Nullable String after() {
        return automatic && waiting ? entries.get((index + 1) % entries.size()).station() : null;
    }

    private void restart() {
        waiting = false;
        waited = 0;
        target = null;
    }

    // ---- the drive ----

    /**
     * One tick of driving {@code train}: the way to push it, +1 its heading, -1 against it and 0 for
     * neither, under which it {@link Train#tick(RailNetwork, int, boolean, java.util.function.ToLongFunction,
     * java.util.function.Predicate) runs} without falling back on Locomotives facing the other way.
     * Arriving, waiting and leaving happen here; 0 while it does not drive.
     */
    int drive(RailNetwork network, Stations stations, Train train, Cars cars) {
        if (!automatic) {
            return 0;
        }
        if (waiting) {
            waited++;
            if (!done(entries.get(index), train, cars)) {
                return 0;
            }
            index = (index + 1) % entries.size();
            restart();
        }
        Entry entry = entries.get(index);
        Station station = target == null ? null : stations.get(target.x(), target.y(), target.z());
        if (station == null || !station.name().equals(entry.station())) {
            target = null;
            // Set going at rest at a Station of its entry, it is there already.
            if (train.atRest() && stations.stoppedAt(network, train).stream()
                    .anyMatch(s -> s.name().equals(entry.station()))) {
                waiting = true;
                return 0;
            }
            target = pick(network, stations, train, cars, entry.station());
            station = target == null ? null : stations.get(target.x(), target.y(), target.z());
        }
        if (station == null) {
            return hold(train);
        }
        int way = target.way();
        double d = distance(network, train, station, way);
        if (Double.isNaN(d)) {
            // Cut off from it, or left too far behind: the next tick looks again.
            target = null;
            return hold(train);
        }
        double v = train.speed() * way;
        if (v < 0.0) {
            return way;
        }
        if (d <= ARRIVE) {
            if (train.atRest()) {
                waiting = true;
                target = null;
                return 0;
            }
            return -way;
        }
        // Braking from v covers about v^2/2b, and this tick's v more before it bites.
        double braking = train.brakingRate();
        if (v * v / (2.0 * braking) + v >= d) {
            return -way;
        }
        return way;
    }

    /** Whether the wait at {@code entry}'s Station is over. */
    private boolean done(Entry entry, Train train, Cars cars) {
        return switch (entry.until()) {
            case TIME -> waited >= entry.seconds() * LocomotiveSpec.TICKS_PER_SECOND;
            case CARGO_FULL -> everyCargoCar(train, cars, Load.FULL);
            case CARGO_EMPTY -> everyCargoCar(train, cars, Load.EMPTY);
        };
    }

    private static boolean everyCargoCar(Train train, Cars cars, Load load) {
        for (Car car : train.cars()) {
            if (car instanceof CargoCar cargo && cars.load(cargo) != load) {
                return false;
            }
        }
        return true;
    }

    /** Brakes a moving Train, and holds one at rest. */
    private static int hold(Train train) {
        return train.speed() > 0.0 ? -1 : train.speed() < 0.0 ? 1 : 0;
    }

    /**
     * The nearest Station called {@code name} ahead of its leading car, past where it stands, either
     * way a Locomotive able to pull faces; null when it can reach none.
     */
    private static @Nullable Target pick(RailNetwork network, Stations stations, Train train, Cars cars, String name) {
        Target best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (int way : new int[] {1, -1}) {
            if (!pulls(train, cars, way)) {
                continue;
            }
            Place lead = lead(network, train, way);
            for (Station station : stations.named(name)) {
                Optional<Station.Foot> foot = station.foot(network);
                if (foot.isEmpty()) {
                    continue;
                }
                double d = lead.distanceTo(network, foot.get().span(), foot.get().fromA());
                if (d > ARRIVE && d < nearest) {
                    nearest = d;
                    best = new Target(station.x(), station.y(), station.z(), way);
                }
            }
        }
        return best;
    }

    /** Whether a Locomotive facing {@code way} has fuel to pull with, in its buffer or its slot. */
    private static boolean pulls(Train train, Cars cars, int way) {
        for (Car car : train.cars()) {
            if (car instanceof Locomotive locomotive && (train.reversed(car) ? -1 : 1) == way
                    && (locomotive.fuelled() || cars.hasFuel(locomotive))) {
                return true;
            }
        }
        return false;
    }

    /** Its first car the way it runs, facing that way. */
    private static Place lead(RailNetwork network, Train train, int way) {
        return way > 0 ? train.placeOf(network, train.cars().getFirst())
                : train.placeOf(network, train.cars().getLast()).turned();
    }

    /**
     * How far ahead of its leading car {@code station}'s foot is, running {@code way}: negative when
     * the car has run past it by up to a Station's stretch, and NaN when it is out of reach.
     */
    private static double distance(RailNetwork network, Train train, Station station, int way) {
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
        return "Schedule[" + entries + (automatic ? ", automatic" : "") + ", at " + index
                + (waiting ? " waiting " + waited : "") + ", target " + target + "]";
    }
}
