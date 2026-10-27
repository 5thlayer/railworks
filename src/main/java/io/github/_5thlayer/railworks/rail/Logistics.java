// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * Automatic Trains running jobs between hatches (ADR 0008): the one place that plans jobs, claims
 * loads against the hatches, drives each automatic Train to its job's Stations, moves stacks between
 * hatches and the cars standing at their car spots, decides when a Train leaves, and gives its status.
 * The server {@link #tick ticks} it once a tick with the level's hatches and a {@link Goods} view of
 * what they and the Cargo cars hold, and {@link #drive drives} each automatic Train in place of a
 * driver's keys.
 *
 * <p>Once every {@link #PLAN_TICKS} it plans: requesters in the order they were placed, each with
 * need left (its request less what it holds and what is on its way to it), are paired with the
 * nearest provider Station offering that item, never their own Station, and the nearest free Train,
 * one automatic with no job and empty Cargo cars, that reaches the provider arriving its way and the
 * requester after it, takes the job. The job carries a load for every car spot of the requester
 * Station whose hatch asks for what the provider Station's hatch offers at the spot that car stood
 * at there, the same spot for a Train running on the same way, so one Train carries a different item
 * in each car, and two in one car where two hatch pairs share a spot. Each load is the least of the
 * need, the provider's unclaimed stock and the car's room.
 *
 * <p>Claims are the loads themselves: a load claims what it has still to take against its provider,
 * and what it has still to give against its requester, so a load on its way counts against a request
 * and stock promised to one requester is not promised to another. At a Station each hatch moves one
 * stack every {@link #hatchTicksPerStack()} ticks with the car at its spot, and a Train leaves when
 * every load to move there is moved or after {@link #stallSeconds()} with nothing moving: from a
 * provider with a part load its claims shrink to what it took, and having taken nothing its job is
 * dropped. A Train with no job waits where it is. Jobs are not saved: a Train set going again has none.
 *
 * <p>Claims break with what they were made against. A Train switched to manual, or coupled, parted or
 * gone, drops its job and every claim with it, its cars keeping what they hold. A hatch broken, or
 * switched to the other mode, drops the loads claimed against it: one gone from a provider keeps only
 * what it took there, and one gone from a requester leaves what it took as a <b>leftover</b> in its car;
 * a job with no loads left is cancelled. Where a request changed on the way, the Train loads and
 * unloads no more than the requester now wants, and what it carries past that is a leftover. A job
 * that can no longer reach its requester is given up, what it carries a leftover. A Train holding
 * anything, leftovers or cargo loaded by hand, takes only <b>delivery jobs</b>, with no provider: to
 * the nearest requester Station, arriving either way it pulls, whose hatch at a car's spot asks for
 * what that car holds, planned before any job from a provider. With none, it waits where it is,
 * holding.
 *
 * <p>Pure: no Minecraft types. {@code I} is the item's kind, the game's {@code Item}.
 */
public final class Logistics<I> {

    /** How often it plans, in ticks: once a second. */
    public static final int PLAN_TICKS = LocomotiveSpec.TICKS_PER_SECOND;
    /** How many ticks a hatch takes for each stack unless {@code railworks-server.toml} says otherwise. */
    public static final int DEFAULT_HATCH_TICKS_PER_STACK = 20;
    /** How long a Train waits at a Station with nothing moving unless {@code railworks-server.toml} says otherwise, in seconds. */
    public static final double DEFAULT_STALL_SECONDS = 5.0;

    private static volatile int hatchTicksPerStack = DEFAULT_HATCH_TICKS_PER_STACK;
    private static volatile double stallSeconds = DEFAULT_STALL_SECONDS;

    /**
     * A hatch as logistics sees it: where it stands, which is its identity, the Station it joins and
     * the car spot it is beside there, whether it is a provider or a requester, and what a requester
     * asks for. Only a hatch that joins a Station is handed in.
     */
    public record Hatch<I>(int x, int y, int z, Station station, int spot, boolean provider,
                           Optional<HatchRequest<I>> request) {

        boolean samePlace(Hatch<?> other) {
            return x == other.x && y == other.y && z == other.z;
        }
    }

    /** What a status line says an automatic Train does. */
    public enum Kind {
        MANUAL, WAITING, HOLDING, TO_LOAD, LOADING, TO_UNLOAD, UNLOADING, CANNOT_REACH
    }

    /** So many stacks of an item, as a status line counts what a Train carries. */
    public record Cargo<I>(I item, int stacks) {
    }

    /**
     * A Train's status line: what it does, the Station it does it at or heads for (empty for none),
     * and, heading to unload, what it carries there, item by item in the order they were loaded, or,
     * holding with nowhere to deliver, what its Cargo cars hold, item by item from its first car.
     */
    public record Status<I>(Kind kind, String station, List<Cargo<I>> cargo) {

        static <I> Status<I> of(Kind kind) {
            return new Status<>(kind, "", List.of());
        }

        static <I> Status<I> at(Kind kind, Station station) {
            return new Status<>(kind, station.name(), List.of());
        }
    }

    /** Where a job's Train is: on its way to a Station, or stopped there trading. */
    private enum Leg {
        TO_PROVIDER, AT_PROVIDER, TO_REQUESTER, AT_REQUESTER
    }

    /** One load of a job: from a provider hatch, in one car, to a requester hatch; with no provider, what the car holds already. */
    private static final class Load<I> {

        /** Null for a delivery job's load. */
        final Hatch<I> provider;
        final Hatch<I> requester;
        final CargoCar car;
        final I item;
        /** What it claims, which shrinks to what was taken as the Train leaves the provider. */
        long amount;
        long loaded;
        long unloaded;

        Load(Hatch<I> provider, Hatch<I> requester, CargoCar car, I item, long amount) {
            this.provider = provider;
            this.requester = requester;
            this.car = car;
            this.item = item;
            this.amount = amount;
        }

        /** A delivery job's load: {@code amount} of what {@code car} holds, to {@code requester}. */
        static <I> Load<I> delivery(Hatch<I> requester, CargoCar car, I item, long amount) {
            Load<I> load = new Load<>(null, requester, car, item, amount);
            load.loaded = amount;
            return load;
        }

        /** Whether it is a delivery job's, with no provider. */
        boolean delivery() {
            return provider == null;
        }
    }

    /** A Train's errand: from a provider Station to a requester Station, arriving each running its way; a delivery job's to the requester only. */
    private static final class Job<I> {

        /** Null for a delivery job. */
        final Station provider;
        final Station requester;
        final int wayAtProvider;
        final int wayAtRequester;
        final List<Load<I>> loads;
        Leg leg = Leg.TO_PROVIDER;
        Driver driver;

        Job(Station provider, Station requester, int wayAtProvider, int wayAtRequester, List<Load<I>> loads) {
            this.provider = provider;
            this.requester = requester;
            this.wayAtProvider = wayAtProvider;
            this.wayAtRequester = wayAtRequester;
            this.loads = loads;
            this.driver = new Driver(provider, wayAtProvider);
        }

        /** A delivery job: what the Train holds, to {@code requester}, arriving it running {@code way}. */
        Job(Station requester, int way, List<Load<I>> loads) {
            this.provider = null;
            this.requester = requester;
            this.wayAtProvider = 0;
            this.wayAtRequester = way;
            this.loads = loads;
            this.leg = Leg.TO_REQUESTER;
            this.driver = new Driver(requester, way);
        }

        boolean stopped() {
            return leg == Leg.AT_PROVIDER || leg == Leg.AT_REQUESTER;
        }
    }

    /** An automatic Train's state: its job, and stopped at a Station, how long since a stack moved and since anything did. */
    private static final class Run<I> {

        Job<I> job;
        int sinceStack;
        int stalled;
    }

    /** How a Train comes to a job's Stations: the way it arrives at each, and how far it is from the provider. */
    private record Approach(int wayAtProvider, int wayAtRequester, double toProvider) {
    }

    /** Each automatic Train's state, by the Train, which coupling and parting replace. */
    private final Map<Train, Run<I>> runs = new HashMap<>();
    private int ticks;

    /** Set by {@code RailworksConfig} when the server config loads or reloads. */
    public static void configure(int hatchTicksPerStack, double stallSeconds) {
        if (hatchTicksPerStack < 1 || stallSeconds < 0.0) {
            throw new IllegalArgumentException("hatch ticks per stack " + hatchTicksPerStack + ", stall " + stallSeconds);
        }
        Logistics.hatchTicksPerStack = hatchTicksPerStack;
        Logistics.stallSeconds = stallSeconds;
    }

    /** How many ticks a hatch takes for each stack it moves. */
    public static int hatchTicksPerStack() {
        return hatchTicksPerStack;
    }

    /** How long a Train waits at a Station with nothing moving, in seconds. */
    public static double stallSeconds() {
        return stallSeconds;
    }

    // ---- the tick ----

    /**
     * One tick: Trains no longer automatic, or gone, coupled or parted, drop their jobs and with them
     * their claims; loads claimed against a hatch no longer there as it was are dropped; each automatic
     * Train stopped on a job trades with the hatches there and leaves when it is done; and once every
     * {@link #PLAN_TICKS} jobs are planned. {@code hatches} are those that join a Station, in the
     * order they were placed.
     */
    public void tick(RailNetwork network, Trains trains, List<Hatch<I>> hatches, Goods<I> goods) {
        Set<Train> automatic = new HashSet<>();
        for (Train train : trains.all()) {
            if (train.automatic()) {
                automatic.add(train);
                runs.computeIfAbsent(train, t -> new Run<>());
            }
        }
        runs.keySet().retainAll(automatic);
        for (Run<I> run : runs.values()) {
            if (run.job != null) {
                breakClaims(run, hatches);
            }
        }
        for (var entry : runs.entrySet()) {
            Run<I> run = entry.getValue();
            if (run.job != null && run.job.stopped()) {
                trade(network, entry.getKey(), run, hatches, goods);
            }
        }
        if (++ticks % PLAN_TICKS == 0) {
            plan(network, trains, hatches, goods);
        }
    }

    /**
     * One tick of driving an automatic {@code train}: to its job's Station, or held where it is with
     * no job or stopped at one. It lights fuel through {@code ignite} and runs onto no Span
     * {@code taken} says another Train holds.
     */
    public void drive(RailNetwork network, Train train, Goods<I> goods, ToLongFunction<Locomotive> ignite,
                      Predicate<Span> taken) {
        Run<I> run = runs.get(train);
        Job<I> job = run == null ? null : run.job;
        if (job == null || job.stopped()) {
            train.tick(network, hold(train), false, ignite, taken);
            return;
        }
        if (job.driver.tick(network, train, goods::slotFuelled, ignite, taken)) {
            job.leg = job.leg == Leg.TO_PROVIDER ? Leg.AT_PROVIDER : Leg.AT_REQUESTER;
            // The first stack moves on the first tick at the Station.
            run.sinceStack = hatchTicksPerStack;
            run.stalled = 0;
        }
    }

    /** Brakes a moving Train, and holds one at rest. */
    private static int hold(Train train) {
        return train.speed() > 0.0 ? -1 : train.speed() < 0.0 ? 1 : 0;
    }

    /** What {@code train}'s status line says. */
    public Status<I> status(Train train, Goods<I> goods) {
        if (!train.automatic()) {
            return Status.of(Kind.MANUAL);
        }
        Run<I> run = runs.get(train);
        Job<I> job = run == null ? null : run.job;
        if (job == null) {
            List<Cargo<I>> holding = holding(train, goods);
            return holding.isEmpty() ? Status.of(Kind.WAITING) : new Status<>(Kind.HOLDING, "", holding);
        }
        return switch (job.leg) {
            case TO_PROVIDER -> Status.at(job.driver.lost() ? Kind.CANNOT_REACH : Kind.TO_LOAD, job.provider);
            case AT_PROVIDER -> Status.at(Kind.LOADING, job.provider);
            case TO_REQUESTER -> job.driver.lost() ? Status.at(Kind.CANNOT_REACH, job.requester)
                    : new Status<>(Kind.TO_UNLOAD, job.requester.name(), carried(job, goods));
            case AT_REQUESTER -> Status.at(Kind.UNLOADING, job.requester);
        };
    }

    /** What a job's loads carry, item by item in the order they were loaded, in stacks rounded up. */
    private static <I> List<Cargo<I>> carried(Job<I> job, Goods<I> goods) {
        Map<I, Long> items = new LinkedHashMap<>();
        for (Load<I> load : job.loads) {
            items.merge(load.item, load.loaded - load.unloaded, Long::sum);
        }
        return inStacks(items, goods);
    }

    /** What {@code train}'s Cargo cars hold between them, item by item from its first car, in stacks rounded up. */
    private static <I> List<Cargo<I>> holding(Train train, Goods<I> goods) {
        Map<I, Long> items = new LinkedHashMap<>();
        for (Car car : train.cars()) {
            if (car instanceof CargoCar cargo) {
                goods.contents(cargo).forEach((item, count) -> items.merge(item, count, Long::sum));
            }
        }
        return inStacks(items, goods);
    }

    /** {@code items}' counts, those above none, in stacks rounded up. */
    private static <I> List<Cargo<I>> inStacks(Map<I, Long> items, Goods<I> goods) {
        List<Cargo<I>> cargo = new ArrayList<>();
        items.forEach((item, count) -> {
            if (count > 0) {
                cargo.add(new Cargo<>(item, (int) stacks(count, goods.stackSize(item))));
            }
        });
        return cargo;
    }

    // ---- claims breaking ----

    /**
     * Drops the loads of {@code run}'s job claimed against a hatch no longer among {@code hatches} as
     * it was, broken or switched in mode: one whose provider is gone keeps only what it took, and goes
     * if it took nothing; one whose requester is gone goes, what it took a leftover. A job with no
     * loads left is cancelled.
     */
    private static <I> void breakClaims(Run<I> run, List<Hatch<I>> hatches) {
        List<Load<I>> loads = run.job.loads;
        loads.removeIf(load -> !standing(load.requester, false, hatches));
        for (Load<I> load : loads) {
            if (!load.delivery() && !standing(load.provider, true, hatches)) {
                load.amount = load.loaded;
            }
        }
        loads.removeIf(load -> load.amount <= 0);
        if (loads.isEmpty()) {
            run.job = null;
        }
    }

    /** Whether {@code hatch} still stands, joining its Station at its spot, a provider or not as {@code provider} says. */
    private static <I> boolean standing(Hatch<I> hatch, boolean provider, List<Hatch<I>> hatches) {
        return asItStands(hatch, hatches).filter(h -> h.provider() == provider).isPresent();
    }

    /** {@code hatch} as it is now, joining its Station at its spot; empty once it is broken or joins elsewhere. */
    private static <I> Optional<Hatch<I>> asItStands(Hatch<I> hatch, List<Hatch<I>> hatches) {
        return hatches.stream()
                .filter(h -> h.samePlace(hatch) && h.station().samePlace(hatch.station()) && h.spot() == hatch.spot())
                .findFirst();
    }

    // ---- at a Station ----

    /** One tick of {@code train} stopped on its job: each hatch moves a stack when its time comes, and the Train leaves when done or stalled. */
    private void trade(RailNetwork network, Train train, Run<I> run, List<Hatch<I>> hatches, Goods<I> goods) {
        Job<I> job = run.job;
        boolean loading = job.leg == Leg.AT_PROVIDER;
        boolean moved = false;
        if (++run.sinceStack >= hatchTicksPerStack) {
            run.sinceStack = 0;
            int way = loading ? job.wayAtProvider : job.wayAtRequester;
            // Each hatch moves one stack a turn, however many loads it serves.
            List<Hatch<I>> turned = new ArrayList<>();
            for (Load<I> load : job.loads) {
                Hatch<I> hatch = loading ? load.provider : load.requester;
                long left = left(job, load, loading, hatches, goods);
                if (left <= 0 || turned.stream().anyMatch(hatch::samePlace) || carAt(train, way, hatch.spot()) != load.car) {
                    continue;
                }
                long most = Math.min(goods.stackSize(load.item), left);
                long count = loading ? goods.load(hatch, load.car, load.item, most)
                        : goods.unload(load.car, hatch, load.item, most);
                if (count > 0) {
                    turned.add(hatch);
                    moved = true;
                    if (loading) {
                        load.loaded += count;
                    } else {
                        load.unloaded += count;
                    }
                }
            }
        }
        run.stalled = moved ? 0 : run.stalled + 1;
        boolean done = job.loads.stream().allMatch(load -> left(job, load, loading, hatches, goods) <= 0);
        // Stalled only once a hatch's turn has come round and moved nothing, however slow the turns.
        if (done || run.stalled >= Math.max(stallSeconds * LocomotiveSpec.TICKS_PER_SECOND, hatchTicksPerStack)) {
            leave(run, loading);
        }
    }

    /**
     * What is left of {@code load} to move at this Station: to take at its provider, no more than its
     * requester now wants less what the job carries to it already, or to give at its requester, no
     * more than it now wants. A request changed on the way cuts both, so the job takes nothing just to
     * leave it over; other Trains' loads to the same requester it does not count.
     */
    private static <I> long left(Job<I> job, Load<I> load, boolean loading, List<Hatch<I>> hatches, Goods<I> goods) {
        long wanted = wanted(load, hatches, goods);
        if (!loading) {
            return Math.min(load.loaded - load.unloaded, wanted);
        }
        long carried = 0;
        for (Load<I> other : job.loads) {
            if (other.requester.samePlace(load.requester) && other.item.equals(load.item)) {
                carried += other.loaded - other.unloaded;
            }
        }
        return Math.min(load.amount - load.loaded, wanted - carried);
    }

    /** What {@code load}'s requester, as it stands, wants of its item: its request less what it holds; none once it asks for another. */
    private static <I> long wanted(Load<I> load, List<Hatch<I>> hatches, Goods<I> goods) {
        return asItStands(load.requester, hatches).flatMap(Hatch::request)
                .filter(request -> request.item().equals(load.item))
                .map(request -> (long) request.stacks() * goods.stackSize(load.item) - goods.held(load.requester, load.item))
                .orElse(0L);
    }

    /**
     * The Train leaves the Station it is stopped at. From the provider its claims shrink to what it took, loads that took
     * nothing going, and with nothing taken the job is dropped; from the requester the job is done.
     */
    private static <I> void leave(Run<I> run, boolean loading) {
        Job<I> job = run.job;
        if (!loading) {
            run.job = null;
            return;
        }
        job.loads.removeIf(load -> load.loaded <= 0);
        job.loads.forEach(load -> load.amount = load.loaded);
        if (job.loads.isEmpty()) {
            run.job = null;
            return;
        }
        job.leg = Leg.TO_REQUESTER;
        job.driver = new Driver(job.requester, job.wayAtRequester);
    }

    // ---- planning ----

    private void plan(RailNetwork network, Trains trains, List<Hatch<I>> hatches, Goods<I> goods) {
        for (Train train : trains.all()) {
            Run<I> run = runs.get(train);
            // A job that can no longer reach its requester is given up, what it carries a leftover for a delivery job.
            if (run != null && run.job != null && run.job.leg == Leg.TO_REQUESTER && run.job.driver.lost()) {
                run.job = null;
            }
            if (run != null && run.job == null && !free(train, goods)) {
                deliver(network, train, run, hatches, goods);
            }
        }
        for (Hatch<I> requester : hatches) {
            if (requester.provider() || requester.request().isEmpty()) {
                continue;
            }
            I item = requester.request().get().item();
            if (need(requester, hatches, goods) <= 0) {
                continue;
            }
            planFor(network, trains, requester, item, hatches, goods);
        }
    }

    /** Plans a job for {@code requester}'s need of {@code item}: the nearest provider Station first, and for it the nearest free Train that can do it. */
    private void planFor(RailNetwork network, Trains trains, Hatch<I> requester, I item, List<Hatch<I>> hatches,
                         Goods<I> goods) {
        Station at = requester.station();
        Optional<Platform> requesterPlatform = Platform.of(network, at);
        if (requesterPlatform.isEmpty()) {
            return;
        }
        List<Station> providers = stations(hatches, hatch -> hatch.provider() && !hatch.station().samePlace(at)
                && unclaimed(hatch, item, goods) > 0);
        Map<Station, Double> distances = new HashMap<>();
        for (Station provider : providers) {
            Platform.of(network, provider).ifPresent(platform -> {
                double ahead = Math.min(nonNegative(requesterPlatform.get().toLeadMark(network, platform.leadMark(), 0.0)),
                        nonNegative(requesterPlatform.get().toLeadMark(network, platform.leadMark().turned(), 0.0)));
                if (ahead < Double.POSITIVE_INFINITY) {
                    distances.put(provider, ahead);
                }
            });
        }
        List<Station> nearest = distances.keySet().stream()
                .sorted((one, other) -> Double.compare(distances.get(one), distances.get(other)))
                .toList();
        for (Station provider : nearest) {
            Platform providerPlatform = Platform.of(network, provider).orElseThrow();
            List<Map.Entry<Train, Approach>> free = new ArrayList<>();
            for (Train train : trains.all()) {
                if (!free(train, goods)) {
                    continue;
                }
                Approach approach = approach(network, train, providerPlatform, requesterPlatform.get(), goods);
                if (approach != null) {
                    free.add(Map.entry(train, approach));
                }
            }
            free.sort((one, other) -> Double.compare(one.getValue().toProvider(), other.getValue().toProvider()));
            for (var candidate : free) {
                List<Load<I>> loads = loads(candidate.getKey(), candidate.getValue(), provider, at, hatches, goods);
                if (!loads.isEmpty()) {
                    Approach approach = candidate.getValue();
                    runs.get(candidate.getKey()).job = new Job<>(provider, at, approach.wayAtProvider(), approach.wayAtRequester(), loads);
                    return;
                }
            }
        }
    }

    /**
     * Plans a delivery job for {@code train}, which holds something: to the nearest requester Station,
     * arriving either way it pulls, where a hatch at a car's spot asks for what that car holds, each
     * load the least of what the car holds and the hatch's need.
     */
    private void deliver(RailNetwork network, Train train, Run<I> run, List<Hatch<I>> hatches, Goods<I> goods) {
        List<Station> requesters = stations(hatches, hatch -> !hatch.provider() && hatch.request().isPresent());
        Job<I> nearest = null;
        double shortest = Double.POSITIVE_INFINITY;
        for (Station station : requesters) {
            Optional<Platform> platform = Platform.of(network, station);
            if (platform.isEmpty()) {
                continue;
            }
            for (int way : new int[] {1, -1}) {
                double distance = toLeadMark(network, train, way, platform.get(), goods);
                if (Double.isNaN(distance) || Math.max(distance, 0.0) >= shortest) {
                    continue;
                }
                List<Load<I>> loads = deliveryLoads(train, way, station, hatches, goods);
                if (!loads.isEmpty()) {
                    shortest = Math.max(distance, 0.0);
                    nearest = new Job<>(station, way, loads);
                }
            }
        }
        run.job = nearest;
    }

    /** A delivery job's loads for {@code train} arriving at {@code requester} running {@code way}: for each hatch there with need left, what the car at its spot holds of its item. */
    private List<Load<I>> deliveryLoads(Train train, int way, Station requester, List<Hatch<I>> hatches, Goods<I> goods) {
        List<Load<I>> loads = new ArrayList<>();
        for (Asking<I> asking : asking(train, way, requester, hatches)) {
            I item = asking.item();
            CargoCar car = asking.car();
            long need = needLeft(asking.hatch(), loads, hatches, goods);
            long holds = goods.held(car, item) - planned(loads, load -> load.car == car && load.item.equals(item));
            long amount = Math.min(need, holds);
            if (amount > 0) {
                loads.add(Load.delivery(asking.hatch(), car, item, amount));
            }
        }
        return loads;
    }

    /** The Stations of {@code hatches} that {@code which} picks, each once, in the order their hatches were placed. */
    private static <I> List<Station> stations(List<Hatch<I>> hatches, Predicate<Hatch<I>> which) {
        List<Station> stations = new ArrayList<>();
        for (Hatch<I> hatch : hatches) {
            if (which.test(hatch) && stations.stream().noneMatch(hatch.station()::samePlace)) {
                stations.add(hatch.station());
            }
        }
        return stations;
    }

    /** A requester hatch asking for {@code item}, and the Cargo car of a Train that will stand at its spot. */
    private record Asking<I>(Hatch<I> hatch, I item, CargoCar car) {
    }

    /**
     * The requester hatches of {@code station} that ask for something, in the order they were placed,
     * each with the Cargo car of {@code train} that will stand at its spot arriving running {@code way};
     * those with none there left out.
     */
    private static <I> List<Asking<I>> asking(Train train, int way, Station station, List<Hatch<I>> hatches) {
        List<Asking<I>> asking = new ArrayList<>();
        for (Hatch<I> hatch : hatches) {
            if (hatch.provider() || hatch.request().isEmpty() || !hatch.station().samePlace(station)) {
                continue;
            }
            CargoCar car = carAt(train, way, hatch.spot());
            if (car != null) {
                asking.add(new Asking<>(hatch, hatch.request().get().item(), car));
            }
        }
        return asking;
    }

    /** How far {@code train}, running {@code way}, is from {@code platform}'s lead mark, as {@link Platform#toLeadMark}; NaN where it cannot pull that way. */
    private static <I> double toLeadMark(RailNetwork network, Train train, int way, Platform platform, Goods<I> goods) {
        return train.pulls(way, goods::slotFuelled)
                ? platform.toLeadMark(network, train.lead(network, way), Station.STRETCH) : Double.NaN;
    }

    private static double nonNegative(double distance) {
        return Double.isNaN(distance) || distance < 0.0 ? Double.POSITIVE_INFINITY : distance;
    }

    /** Whether {@code train} is free: automatic, with no job and its Cargo cars empty. */
    private boolean free(Train train, Goods<I> goods) {
        Run<I> run = runs.get(train);
        if (!train.automatic() || run == null || run.job != null) {
            return false;
        }
        for (Car car : train.cars()) {
            if (car instanceof CargoCar cargo && !goods.empty(cargo)) {
                return false;
            }
        }
        return true;
    }

    /**
     * How {@code train} reaches the provider and then the requester: the ways it can pull, arriving at
     * the provider running one and setting off from there running either, the nearest in all; null
     * where it reaches the provider not at all or the requester not after it.
     */
    private static <I> Approach approach(RailNetwork network, Train train, Platform provider, Platform requester, Goods<I> goods) {
        Approach best = null;
        double shortest = Double.POSITIVE_INFINITY;
        for (int atProvider : new int[] {1, -1}) {
            double toProvider = toLeadMark(network, train, atProvider, provider, goods);
            if (Double.isNaN(toProvider)) {
                continue;
            }
            // At the provider its leading car stands at the lead mark; its other end leads the other way.
            Place lead = provider.leadMark();
            Place otherEnd = train.length() == 0.0 ? lead.turned() : lead.move(network, -train.length()).place().turned();
            for (int atRequester : new int[] {1, -1}) {
                if (!train.pulls(atRequester, goods::slotFuelled)) {
                    continue;
                }
                double toRequester = requester.toLeadMark(network, atRequester == atProvider ? lead : otherEnd, 0.0);
                if (Double.isNaN(toRequester) || toRequester < 0.0) {
                    continue;
                }
                double total = Math.max(toProvider, 0.0) + toRequester;
                if (total < shortest) {
                    shortest = total;
                    best = new Approach(atProvider, atRequester, Math.max(toProvider, 0.0));
                }
            }
        }
        return best;
    }

    /**
     * A job's loads for {@code train} coming {@code approach} from {@code provider} to {@code requester}:
     * for each requester hatch there with need left, in the order they were placed, the car that will
     * stand at its spot takes what the provider hatches at the spot that car stood at offer of its item,
     * each the least of the need, the unclaimed stock and the car's room.
     */
    private List<Load<I>> loads(Train train, Approach approach, Station provider, Station requester, List<Hatch<I>> hatches,
                                Goods<I> goods) {
        List<Load<I>> loads = new ArrayList<>();
        Map<CargoCar, Long> slotsUsed = new HashMap<>();
        for (Asking<I> asking : asking(train, approach.wayAtRequester(), requester, hatches)) {
            I item = asking.item();
            CargoCar car = asking.car();
            int spot = spotOf(train, approach.wayAtProvider(), car);
            long need = needLeft(asking.hatch(), loads, hatches, goods);
            for (Hatch<I> offering : hatches) {
                if (need <= 0) {
                    break;
                }
                if (!offering.provider() || offering.spot() != spot || !offering.station().samePlace(provider)) {
                    continue;
                }
                long stock = unclaimed(offering, item, goods)
                        - planned(loads, load -> load.provider.samePlace(offering) && load.item.equals(item));
                long room = (goods.slots(car) - slotsUsed.getOrDefault(car, 0L)) * goods.stackSize(item);
                long amount = Math.min(need, Math.min(stock, room));
                if (amount <= 0) {
                    continue;
                }
                loads.add(new Load<>(offering, asking.hatch(), car, item, amount));
                slotsUsed.merge(car, stacks(amount, goods.stackSize(item)), Long::sum);
                need -= amount;
            }
        }
        return loads;
    }

    /** What {@code requester} needs less what {@code loads}, being planned, already take to it. */
    private long needLeft(Hatch<I> requester, List<Load<I>> loads, List<Hatch<I>> hatches, Goods<I> goods) {
        return need(requester, hatches, goods) - planned(loads, load -> load.requester.samePlace(requester));
    }

    private static <I> long planned(List<Load<I>> loads, Predicate<Load<I>> which) {
        return loads.stream().filter(which).mapToLong(load -> load.amount).sum();
    }

    /** What {@code requester} still needs: its request less what it holds and what is on its way to it. */
    private long need(Hatch<I> requester, List<Hatch<I>> hatches, Goods<I> goods) {
        HatchRequest<I> request = requester.request().orElseThrow();
        I item = request.item();
        long onItsWay = 0;
        for (Load<I> load : loads()) {
            if (load.requester.samePlace(requester) && load.item.equals(item)) {
                onItsWay += load.amount - load.unloaded;
            }
        }
        return (long) request.stacks() * goods.stackSize(item) - goods.held(requester, item) - onItsWay;
    }

    /** What {@code provider} holds of {@code item} that no load has claimed and still to take. */
    private long unclaimed(Hatch<I> provider, I item, Goods<I> goods) {
        long claimed = 0;
        for (Load<I> load : loads()) {
            if (!load.delivery() && load.provider.samePlace(provider) && load.item.equals(item)) {
                claimed += load.amount - load.loaded;
            }
        }
        return goods.held(provider, item) - claimed;
    }

    /** Every load of every job. */
    private List<Load<I>> loads() {
        List<Load<I>> loads = new ArrayList<>();
        for (Run<I> run : runs.values()) {
            if (run.job != null) {
                loads.addAll(run.job.loads);
            }
        }
        return loads;
    }

    // ---- car spots ----

    /** Its cars from the one leading running {@code way}, +1 its heading and -1 against it. */
    private static List<Car> fromLead(Train train, int way) {
        return way > 0 ? train.cars() : train.cars().reversed();
    }

    /** The Cargo car that stands at car {@code spot} behind the leading car running {@code way}; null for none, or for a Locomotive there. */
    private static CargoCar carAt(Train train, int way, int spot) {
        List<Car> cars = fromLead(train, way);
        return spot + 1 < cars.size() && cars.get(spot + 1) instanceof CargoCar cargo ? cargo : null;
    }

    /** The car spot {@code car} stands at behind the leading car running {@code way}. */
    private static int spotOf(Train train, int way, Car car) {
        return fromLead(train, way).indexOf(car) - 1;
    }

    private static long stacks(long count, int stackSize) {
        return (count + stackSize - 1) / stackSize;
    }
}
