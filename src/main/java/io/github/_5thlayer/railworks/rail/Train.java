// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Cars coupled on the Rail that move as one: an ordered chain, the first car at its {@link #head()}
 * and each next one spaced behind it along the Line. It carries the Train's speed, its mass (its
 * cars') and its pull (its fuelled Locomotives'), and it holds every Span its cars stand on. A
 * car's entity in the world is only its view, so a Train in an unloaded chunk (#6) can run the same
 * code.
 *
 * <p>The head faces one end of its Span and the Train runs that way. Speed is signed against that
 * heading: positive runs toward the front, negative is a reverse that does not turn it round. Each
 * car faces the Train's way or, {@link #reversed}, the other, so a Train can carry a Locomotive
 * facing out at each end. It runs on through an Arm into the Span beyond and stops when its first
 * car meets a line's end ahead or its last car one behind, or at the Arm of a Span another Train
 * holds.
 *
 * <p>Its cars are fixed: coupling and uncoupling make new Trains, at rest.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Train {

    /** Which end of a Train a car couples to: ahead of its first car or behind its last. */
    public enum End {
        FRONT, BACK
    }

    public static final Codec<Train> CODEC = RecordCodecBuilder.create(i -> i.group(
            Place.MAP_CODEC.forGetter(Train::head),
            Codec.DOUBLE.fieldOf("speed").forGetter(Train::speed),
            Car.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("cars").forGetter(Train::cars),
            Car.ID_CODEC.listOf().optionalFieldOf("reversed", List.of()).forGetter(t -> List.copyOf(t.reversed))
    ).apply(i, (head, speed, cars, reversed) -> restore(head, speed, cars, Set.copyOf(reversed))));

    /**
     * What a Locomotive saved before Trains had cars looks like: the Locomotive was the whole Train,
     * its place, speed and fuel in one record. It loads as a Train of that one Locomotive.
     */
    public static final Codec<Train> LEGACY_LOCOMOTIVE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Place.MAP_CODEC.forGetter(Train::head),
            Codec.DOUBLE.fieldOf("speed").forGetter(Train::speed),
            Codec.LONG.fieldOf("fuel").forGetter(t -> t.legacyLocomotive().fuel().storedJoules()),
            Codec.LONG.fieldOf("last_lit").forGetter(t -> t.legacyLocomotive().fuel().lastLitJoules())
    ).apply(i, (head, speed, fuel, lastLit) -> restore(head, speed,
            List.of(Locomotive.restore(UUID.randomUUID(), fuel, lastLit)), Set.of())));

    private final List<Car> cars;
    /** The ids of the cars that face against the Train's heading. */
    private final Set<UUID> reversed;
    private Place head;
    private double speed;

    private Train(Place head, List<? extends Car> cars, Set<UUID> reversed) {
        if (cars.isEmpty()) {
            throw new IllegalArgumentException("a Train has at least one car");
        }
        this.head = head;
        this.cars = List.copyOf(cars);
        Set<UUID> ids = new HashSet<>();
        for (Car car : this.cars) {
            if (!ids.add(car.id())) {
                throw new IllegalArgumentException("a car is coupled twice into " + this.cars);
            }
        }
        this.reversed = Set.copyOf(reversed.stream().filter(ids::contains).toList());
    }

    /** At rest with its first car at {@code head} and the rest behind it, all facing its way. */
    public static Train of(Place head, List<? extends Car> cars) {
        return new Train(head, cars, Set.of());
    }

    /** A Train of one car, at rest at {@code head}, facing its way. */
    public static Train of(Place head, Car car) {
        return new Train(head, List.of(car), Set.of());
    }

    private static Train restore(Place head, double speed, List<Car> cars, Set<UUID> reversed) {
        Train train = new Train(head, cars, reversed);
        train.speed = speed;
        return train;
    }

    /** Its cars, first to last. */
    public List<Car> cars() {
        return cars;
    }

    /** Its car with this id, or null when it has none. */
    public Car car(UUID id) {
        for (Car car : cars) {
            if (car.id().equals(id)) {
                return car;
            }
        }
        return null;
    }

    /** Whether {@code car} faces against the Train's heading. */
    public boolean reversed(Car car) {
        return reversed.contains(car.id());
    }

    /** Where its first car is, and the way the Train faces. */
    public Place head() {
        return head;
    }

    /** Blocks a tick, positive the way it faces. */
    public double speed() {
        return speed;
    }

    /** Whether it stands still: a car couples to it, and a Cargo car loads at a Station, only then. */
    public boolean atRest() {
        return speed == 0.0;
    }

    /** Kilograms: its cars'. */
    public double mass() {
        double mass = 0.0;
        for (Car car : cars) {
            mass += car.mass();
        }
        return mass;
    }

    /**
     * What its fuelled Locomotives pull with, in kilogram blocks a tick squared: those whose buffer
     * covers a tick. A tick also counts one that lights an item to pay for it.
     */
    public double pull() {
        double pull = 0.0;
        for (Car car : cars) {
            if (car instanceof Locomotive locomotive && locomotive.fuelled()) {
                pull += locomotive.pull();
            }
        }
        return pull;
    }

    /** Blocks along the Line from its first car to {@code car}, each car's middle to the next's. */
    public double offset(Car car) {
        double offset = 0.0;
        for (int i = 0; i < cars.size(); i++) {
            if (cars.get(i) == car) {
                return offset;
            }
            if (i + 1 < cars.size()) {
                offset += (cars.get(i).length() + cars.get(i + 1).length()) / 2.0;
            }
        }
        throw new IllegalArgumentException(car + " is not in this Train");
    }

    /** Blocks along the Line from its first car to its last. */
    public double length() {
        return offset(cars.getLast());
    }

    /** Where {@code car} is: {@link #offset} behind the head, with the Train's heading. */
    public Place placeOf(RailNetwork network, Car car) {
        double offset = offset(car);
        return offset == 0.0 ? head : head.move(network, -offset).place();
    }

    /** Where {@code car} is, facing its own way: {@link #placeOf}, turned round for a reversed car. */
    public Place facing(RailNetwork network, Car car) {
        Place place = placeOf(network, car);
        return reversed(car) ? place.turned() : place;
    }

    /**
     * Its leading car running {@code way}, +1 its heading and -1 against it: its first car, or its
     * last turned round, facing the way it runs.
     */
    Place lead(RailNetwork network, int way) {
        return way > 0 ? placeOf(network, cars.getFirst()) : placeOf(network, cars.getLast()).turned();
    }

    /**
     * The Spans its cars stand on, from its first car's front to its last car's back, the head's
     * first: one Train holds a Span at a time.
     */
    public List<Span> spans(RailNetwork network) {
        Place front = frontFace(network);
        Place back = backFace(network).turned();
        List<Span> spans = front.spansBack(network,
                cars.getFirst().length() / 2.0 + length() + cars.getLast().length() / 2.0);
        // A front stopped at a line's end ran short, so the count back overshoots the last car.
        int last = spans.indexOf(back.span());
        return last < 0 ? spans : List.copyOf(spans.subList(0, last + 1));
    }

    /** Whether every Span it stands on is still strung: a cut behind its head leaves its last car off the line. */
    public boolean onRail(RailNetwork network) {
        if (length() > 0.0 && head.move(network, -length()).stopped()) {
            return false;
        }
        for (Span span : spans(network)) {
            if (!network.contains(span.a(), span.b())) {
                return false;
            }
        }
        return true;
    }

    /**
     * The Arm at {@code from} has moved to {@code to}, {@code network} already holding its Spans
     * there: the Train stays on the Rail it was on, the same fraction of the way along.
     */
    public void moveArm(RailNetwork network, Arm from, Arm to) {
        head = head.movedArm(network, from, to);
    }

    // ---- coupling ----

    /**
     * Where a car {@code length} blocks long would stand coupled at {@code end}: its middle, with
     * the Train's heading. Null where the line ends before the whole car would fit on it.
     */
    public Place slot(RailNetwork network, End end, double length) {
        int way = end == End.FRONT ? 1 : -1;
        Place face = end == End.FRONT ? frontFace(network) : backFace(network).turned();
        if (face.move(network, way * length).stopped()) {
            return null;
        }
        return face.move(network, way * length / 2.0).place();
    }

    /**
     * This Train with {@code car} coupled at {@code end}, facing against its heading when
     * {@code reversed}; null while it moves or where the car would not fit on the line.
     */
    public Train coupled(RailNetwork network, End end, Car car, boolean reversed) {
        Place slot = slot(network, end, car.length());
        if (speed != 0.0 || slot == null || car(car.id()) != null) {
            return null;
        }
        List<Car> joined = new ArrayList<>(cars);
        if (end == End.FRONT) {
            joined.addFirst(car);
        } else {
            joined.addLast(car);
        }
        Set<UUID> facing = new HashSet<>(this.reversed);
        if (reversed) {
            facing.add(car.id());
        }
        return new Train(end == End.FRONT ? slot : head, joined, facing);
    }

    /**
     * The coupler on {@code car}'s {@code side}, +1 ahead of it the way it faces and -1 behind: the
     * index of the first car behind that coupler in the Train, or, when that side of the car has no
     * car coupled, the other side's. -1 for a Train of one car.
     */
    public int coupler(Car car, int side) {
        int index = cars.indexOf(car);
        if (index < 0) {
            throw new IllegalArgumentException(car + " is not in this Train");
        }
        // Ahead in the Train's own heading is toward its first car.
        boolean ahead = (reversed(car) ? -side : side) > 0;
        int asked = ahead ? index : index + 1;
        int other = ahead ? index + 1 : index;
        if (asked >= 1 && asked < cars.size()) {
            return asked;
        }
        return other >= 1 && other < cars.size() ? other : -1;
    }

    /**
     * The two Trains it parts into at {@code at}: the cars before it and those from it on, each
     * where it stood and facing as it did. Null while it moves or for a coupler it has not.
     */
    public List<Train> uncoupled(RailNetwork network, int at) {
        if (speed != 0.0 || at < 1 || at >= cars.size()) {
            return null;
        }
        return List.of(part(network, 0, at), part(network, at, cars.size()));
    }

    /**
     * What is left of it without {@code car}: nothing for a Train of that car alone, one Train for
     * a car at an end and two for one in the middle, each where it stood and at its speed.
     */
    public List<Train> without(RailNetwork network, Car car) {
        int index = cars.indexOf(car);
        if (index < 0) {
            throw new IllegalArgumentException(car + " is not in this Train");
        }
        List<Train> left = new ArrayList<>();
        if (index > 0) {
            left.add(part(network, 0, index));
        }
        if (index + 1 < cars.size()) {
            left.add(part(network, index + 1, cars.size()));
        }
        return left;
    }

    /** Its cars from {@code from} up to {@code to} as a Train of their own, where they stand. */
    private Train part(RailNetwork network, int from, int to) {
        Train part = new Train(placeOf(network, cars.get(from)), cars.subList(from, to), reversed);
        part.speed = speed;
        return part;
    }

    // ---- the drive ----

    /** One tick under {@code throttle} from its first car, with no other Train on the Rail. */
    public void tick(RailNetwork network, Throttle throttle, ToLongFunction<Locomotive> ignite) {
        tick(network, throttle, cars.getFirst(), ignite, span -> false);
    }

    /**
     * One tick under {@code throttle} from the cab of {@code driven}, whose forward is the way that
     * car faces. A powered tick has the Locomotives facing the way it pushes draw full power from
     * their fuel buffers, or those facing the other way when none of them pays, lighting an item through
     * {@code ignite} when a buffer cannot cover it (the item's joules, or 0 for none); those that
     * paid pull the Train, and with none it brakes to a stop. Pushing against the motion brakes, for
     * free. Its Locomotives brake and its other cars do not, so a heavier Train starts and stops
     * more slowly. It does not run onto a Span {@code taken} says another Train holds.
     */
    public void tick(RailNetwork network, Throttle throttle, Car driven, ToLongFunction<Locomotive> ignite,
                     Predicate<Span> taken) {
        tick(network, throttle.sign() * (reversed(driven) ? -1 : 1), true, ignite, taken);
    }

    /**
     * One tick pushed {@code push}, +1 the Train's heading and -1 against it: those Locomotives facing
     * that way pull, or, {@code eitherFacing}, those facing the other way when none of them pays.
     */
    void tick(RailNetwork network, int push, boolean eitherFacing, ToLongFunction<Locomotive> ignite,
              Predicate<Span> taken) {
        if (push == 0) {
            coast();
        } else if (speed * push < 0) {
            brake();
        } else if (blocked(network, push, taken)) {
            coast();
        } else {
            double pulling = eitherFacing ? pay(push, ignite) : pay(push, true, ignite);
            if (pulling <= 0.0) {
                // Out of fuel the brakes take it down: coasting on friction alone would run a fast
                // Train on for minutes.
                brake();
            } else {
                double cap = LocomotiveSpec.maxSpeed();
                // Pull over mass, as the configured acceleration times the share of the mass that
                // pulls: a lone Locomotive gets that acceleration exactly.
                double acceleration = LocomotiveSpec.acceleration() * (pulling / mass());
                speed = Math.max(-cap, Math.min(cap, speed + push * acceleration));
            }
        }
        advance(network, taken);
    }

    private void coast() {
        speed = towardZero(speed, LocomotiveSpec.friction());
    }

    /** Slows it by {@link #brakingRate()}, to a stop and no further. */
    private void brake() {
        speed = towardZero(speed, brakingRate());
    }

    /** Its Locomotives' brakes over its whole mass, in blocks a tick squared: a lone Locomotive brakes at the configured rate. */
    double brakingRate() {
        double braking = 0.0;
        for (Car car : cars) {
            if (car instanceof Locomotive) {
                braking += car.mass();
            }
        }
        return LocomotiveSpec.braking() * (braking / mass());
    }

    private static double towardZero(double value, double by) {
        return value > 0 ? Math.max(0.0, value - by) : Math.min(0.0, value + by);
    }

    /**
     * Has the Locomotives facing the way it pushes pay a tick of full power, or, when none of them
     * pays, those facing the other way, and says what those that paid weigh: the mass that pulls.
     */
    private double pay(int push, ToLongFunction<Locomotive> ignite) {
        double pulling = pay(push, true, ignite);
        return pulling > 0.0 ? pulling : pay(push, false, ignite);
    }

    /** Has the Locomotives facing the way it pushes, or {@code facing} false the others, pay a tick; what those that paid weigh. */
    private double pay(int push, boolean facing, ToLongFunction<Locomotive> ignite) {
        double pulling = 0.0;
        for (Car car : cars) {
            if (car instanceof Locomotive locomotive && (facingSign(car) == push) == facing
                    && locomotive.pay(() -> ignite.applyAsLong(locomotive))) {
                pulling += locomotive.mass();
            }
        }
        return pulling;
    }

    /**
     * Whether a Locomotive facing {@code way}, +1 the Train's heading and -1 against it, has fuel to
     * pull with, in its buffer or, as {@code slotFuelled} says, waiting in its slot.
     */
    boolean pulls(int way, Predicate<Locomotive> slotFuelled) {
        for (Car car : cars) {
            if (car instanceof Locomotive locomotive && facingSign(car) == way
                    && (locomotive.fuelled() || slotFuelled.test(locomotive))) {
                return true;
            }
        }
        return false;
    }

    private int facingSign(Car car) {
        return reversed(car) ? -1 : 1;
    }

    /**
     * Whether it cannot move the way it is pushed: its head at a line's end ahead or its tail
     * behind, or its front or back face at the Arm of a Span another Train holds.
     */
    private boolean blocked(RailNetwork network, int push, Predicate<Span> taken) {
        boolean atEnd = push > 0 ? head.atEnd(network, push) : tail(network).atEnd(network, push);
        return atEnd || room(network, push, 1.0, taken) <= 0.0;
    }

    /** How far it can run {@code way}, up to {@code want} blocks, before its front or back face meets a taken Span. */
    private double room(RailNetwork network, int way, double want, Predicate<Span> taken) {
        return (way > 0 ? frontFace(network) : backFace(network)).room(network, want, taken);
    }

    /** Its first car's front, with the Train's heading. */
    private Place frontFace(RailNetwork network) {
        return head.move(network, cars.getFirst().length() / 2.0).place();
    }

    /** Its last car's back, facing back the way the Train came. */
    private Place backFace(RailNetwork network) {
        return tail(network).move(network, -cars.getLast().length() / 2.0).place().turned();
    }

    /** The one Locomotive a legacy record holds: only a Train of one Locomotive has one. */
    private Locomotive legacyLocomotive() {
        if (cars.size() != 1 || !(cars.getFirst() instanceof Locomotive locomotive)) {
            throw new IllegalStateException("only a Train of one Locomotive has a legacy record, not " + cars);
        }
        return locomotive;
    }

    private Place tail(RailNetwork network) {
        return placeOf(network, cars.getLast());
    }

    private void advance(RailNetwork network, Predicate<Span> taken) {
        if (speed > 0.0) {
            double by = Math.min(speed, room(network, 1, speed, taken));
            Place.Moved moved = head.move(network, by);
            head = moved.place();
            if (moved.stopped() || by < speed) {
                speed = 0.0;
            }
        } else if (speed < 0.0) {
            // Backing, the last car meets a line's end first.
            double by = -Math.min(-speed, room(network, -1, -speed, taken));
            Place.Moved moved = tail(network).move(network, by);
            double length = length();
            head = length == 0.0 ? moved.place() : moved.place().move(network, length).place();
            if (moved.stopped() || by > speed) {
                speed = 0.0;
            }
        }
    }

    @Override
    public String toString() {
        return "Train[" + cars + " at " + head + ", speed " + speed + "]";
    }
}
