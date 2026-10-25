// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.List;
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
 * heading: positive runs toward the front, negative is a reverse that does not turn it round. It
 * runs on through an Arm into the Span beyond and stops when its first car meets a line's end ahead
 * or its last car one behind.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Train {

    public static final Codec<Train> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.fieldOf("span").forGetter(t -> t.head.span()),
            Arm.CODEC.fieldOf("front").forGetter(t -> t.head.front()),
            Codec.DOUBLE.fieldOf("to_front").forGetter(t -> t.head.toFront()),
            Codec.DOUBLE.fieldOf("speed").forGetter(Train::speed),
            Car.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("cars").forGetter(Train::cars)
    ).apply(i, (span, front, toFront, speed, cars) -> restore(new Place(span, front, toFront), speed, cars)));

    /**
     * What a Locomotive saved before Trains had cars looks like: the Locomotive was the whole Train,
     * its place, speed and fuel in one record. It loads as a Train of that one Locomotive.
     */
    public static final Codec<Train> LEGACY_LOCOMOTIVE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.fieldOf("span").forGetter(t -> t.head.span()),
            Arm.CODEC.fieldOf("front").forGetter(t -> t.head.front()),
            Codec.DOUBLE.fieldOf("to_front").forGetter(t -> t.head.toFront()),
            Codec.DOUBLE.fieldOf("speed").forGetter(Train::speed),
            Codec.LONG.fieldOf("fuel").forGetter(t -> t.lead().fuel().storedJoules()),
            Codec.LONG.fieldOf("last_lit").forGetter(t -> t.lead().fuel().lastLitJoules())
    ).apply(i, (span, front, toFront, speed, fuel, lastLit) ->
            restore(new Place(span, front, toFront), speed, List.of(Locomotive.restore(fuel, lastLit)))));

    private final List<Car> cars;
    private Place head;
    private double speed;

    private Train(Place head, List<Car> cars) {
        if (cars.isEmpty()) {
            throw new IllegalArgumentException("a Train has at least one car");
        }
        this.head = head;
        this.cars = List.copyOf(cars);
    }

    /** At rest with its first car at {@code head} and the rest behind it. */
    public static Train of(Place head, List<? extends Car> cars) {
        return new Train(head, List.copyOf(cars));
    }

    /** A Train of one car, at rest at {@code head}. */
    public static Train of(Place head, Car car) {
        return new Train(head, List.of(car));
    }

    private static Train restore(Place head, double speed, List<Car> cars) {
        Train train = new Train(head, cars);
        train.speed = speed;
        return train;
    }

    /** Its cars, first to last. */
    public List<Car> cars() {
        return cars;
    }

    /** Where its first car is, and the way the Train faces. */
    public Place head() {
        return head;
    }

    /** Blocks a tick, positive the way it faces. */
    public double speed() {
        return speed;
    }

    /** Kilograms: its cars'. */
    public double mass() {
        double mass = 0.0;
        for (Car car : cars) {
            mass += car.mass();
        }
        return mass;
    }

    /** What its fuelled Locomotives pull with, in kilogram blocks a tick squared. */
    public double pull() {
        double pull = 0.0;
        for (Car car : cars) {
            if (car instanceof Locomotive loco && loco.fuelled()) {
                pull += loco.pull();
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

    /** The Spans its cars stand on, the head's first: one Train holds a Span at a time. */
    public List<Span> spans(RailNetwork network) {
        return head.spansBack(network, length());
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

    /**
     * One tick under {@code throttle}. A powered tick has each Locomotive draw full power from its
     * fuel buffer, lighting an item through {@code ignite} when the buffer cannot cover it (the
     * item's joules, or 0 for none); those that paid pull the Train, and with none it brakes to a
     * stop. Pushing against the motion brakes, for free.
     */
    public void tick(RailNetwork network, Throttle throttle, ToLongFunction<Locomotive> ignite) {
        int push = throttle.sign();
        if (push == 0) {
            coast();
        } else if (speed * push < 0) {
            speed = towardZero(speed, LocomotiveSpec.braking());
        } else if (blocked(network, push)) {
            coast();
        } else {
            double pull = pay(ignite);
            if (pull <= 0.0) {
                // Out of fuel the brakes take it down: coasting on friction alone would run a fast
                // Train on for minutes.
                speed = towardZero(speed, LocomotiveSpec.braking());
            } else {
                double cap = LocomotiveSpec.maxSpeed();
                speed = Math.max(-cap, Math.min(cap, speed + push * pull / mass()));
            }
        }
        advance(network);
    }

    private void coast() {
        speed = towardZero(speed, LocomotiveSpec.friction());
    }

    private static double towardZero(double value, double by) {
        return value > 0 ? Math.max(0.0, value - by) : Math.min(0.0, value + by);
    }

    /** Has every Locomotive pay a tick of full power, and says what those that paid pull with. */
    private double pay(ToLongFunction<Locomotive> ignite) {
        double pull = 0.0;
        for (Car car : cars) {
            if (car instanceof Locomotive loco && loco.pay(() -> ignite.applyAsLong(loco))) {
                pull += loco.pull();
            }
        }
        return pull;
    }

    /** Whether it sits at a line's end with the throttle pushing it off: its head ahead, its tail behind. */
    private boolean blocked(RailNetwork network, int push) {
        return push > 0 ? head.atEnd(network, push) : tail(network).atEnd(network, push);
    }

    /** The first car as the Locomotive a legacy record held; every Train saved that way led with one. */
    private Locomotive lead() {
        if (!(cars.getFirst() instanceof Locomotive loco)) {
            throw new IllegalStateException("a Train led by " + cars.getFirst() + " has no legacy record");
        }
        return loco;
    }

    private Place tail(RailNetwork network) {
        return placeOf(network, cars.getLast());
    }

    private void advance(RailNetwork network) {
        if (speed > 0.0) {
            Place.Moved moved = head.move(network, speed);
            head = moved.place();
            if (moved.stopped()) {
                speed = 0.0;
            }
        } else if (speed < 0.0) {
            // Backing, the last car meets a line's end first.
            Place.Moved moved = tail(network).move(network, speed);
            double length = length();
            head = length == 0.0 ? moved.place() : moved.place().move(network, length).place();
            if (moved.stopped()) {
                speed = 0.0;
            }
        }
    }
}
