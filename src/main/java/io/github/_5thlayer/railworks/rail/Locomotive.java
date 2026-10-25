// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.function.LongSupplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Burner Locomotive as a Train: its place on a Span, its speed, the way it faces and its joule
 * fuel buffer. The entity in the world is only its view, so a Train in an unloaded chunk (#6) can
 * run the same code.
 *
 * <p>It faces one end of its Span, {@link #front()}, and {@link #toFront()} is how far it is from
 * that end. Speed is signed against its heading: positive runs toward the front, negative is a
 * reverse that does not turn it round. It runs on through an Arm into the Span beyond and stops at a
 * line's end, an Arm with one Span.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Locomotive {

    /** Where a Locomotive is, in Arm coordinates; the world adds where the Rail hangs from an Arm. */
    public record Point(double x, double y, double z) {
    }

    public static final Codec<Locomotive> CODEC = RecordCodecBuilder.create(i -> i.group(
            Span.CODEC.fieldOf("span").forGetter(Locomotive::span),
            Arm.CODEC.fieldOf("front").forGetter(Locomotive::front),
            Codec.DOUBLE.fieldOf("to_front").forGetter(Locomotive::toFront),
            Codec.DOUBLE.fieldOf("speed").forGetter(Locomotive::speed),
            Codec.LONG.fieldOf("fuel").forGetter(l -> l.fuel.storedJoules()),
            Codec.LONG.fieldOf("last_lit").forGetter(l -> l.fuel.lastLitJoules())
    ).apply(i, Locomotive::restore));

    private Span span;
    private Arm front;
    private double toFront;
    private double speed;
    private final FuelBuffer fuel = new FuelBuffer();

    private Locomotive(Span span, Arm front, double toFront) {
        if (!span.touches(front)) {
            throw new IllegalArgumentException(front + " is not an end of " + span);
        }
        this.span = span;
        this.front = front;
        this.toFront = Math.max(0.0, Math.min(span.length(), toFront));
    }

    /** At rest on {@code span}, facing {@code front}, {@code toFront} blocks from it. */
    public static Locomotive on(Span span, Arm front, double toFront) {
        return new Locomotive(span, front, toFront);
    }

    private static Locomotive restore(Span span, Arm front, double toFront, double speed, long fuel, long lastLit) {
        Locomotive loco = new Locomotive(span, front, toFront);
        loco.speed = speed;
        loco.fuel.load(fuel, lastLit);
        return loco;
    }

    public Span span() {
        return span;
    }

    /** The end of its Span it faces. */
    public Arm front() {
        return front;
    }

    public double toFront() {
        return toFront;
    }

    /** Blocks a tick, positive the way it faces. */
    public double speed() {
        return speed;
    }

    public FuelBuffer fuel() {
        return fuel;
    }

    /** Whether the Span it stands on is still strung. */
    public boolean onRail(RailNetwork network) {
        return network.contains(span.a(), span.b());
    }

    /** Where it is, in Arm coordinates: along the Span, {@code toFront} short of its front. */
    public Point point() {
        Arm back = span.otherEnd(front);
        double t = toFront / span.length();
        return new Point(front.x() + (back.x() - front.x()) * t,
                front.y() + (back.y() - front.y()) * t,
                front.z() + (back.z() - front.z()) * t);
    }

    /**
     * One tick under {@code throttle}. A powered tick draws full power from the fuel buffer, lighting
     * an item through {@code ignite} when the buffer cannot cover it (the item's joules, or 0 for
     * none), and with no fuel it only coasts. Pushing against the motion brakes, for free.
     */
    public void tick(RailNetwork network, Throttle throttle, LongSupplier ignite) {
        int push = throttle.sign();
        if (push == 0) {
            coast();
        } else if (speed * push < 0) {
            speed = towardZero(speed, LocomotiveSpec.braking());
        } else if (blockedAhead(network, push) || !pay(ignite)) {
            coast();
        } else {
            double cap = LocomotiveSpec.maxSpeed();
            speed = Math.max(-cap, Math.min(cap, speed + push * LocomotiveSpec.acceleration()));
        }
        advance(network);
    }

    private void coast() {
        speed = towardZero(speed, LocomotiveSpec.friction());
    }

    private static double towardZero(double value, double by) {
        return value > 0 ? Math.max(0.0, value - by) : Math.min(0.0, value + by);
    }

    /** Pays one tick of full power; the buffer first, an item only when it cannot cover the tick. */
    private boolean pay(LongSupplier ignite) {
        long perTick = LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER;
        if (fuel.drawTick(perTick)) {
            return true;
        }
        long lit = ignite.getAsLong();
        if (lit <= 0L) {
            return false;
        }
        fuel.light(lit);
        return fuel.drawTick(perTick);
    }

    /** Whether it sits at a line's end with the throttle pushing it off. */
    private boolean blockedAhead(RailNetwork network, int push) {
        return push > 0
                ? toFront <= 0.0 && beyond(network, front) == null
                : toFront >= span.length() && beyond(network, span.otherEnd(front)) == null;
    }

    /** The Span past {@code arm} from the current one, or null at a line's end. */
    private Span beyond(RailNetwork network, Arm arm) {
        for (Span other : network.spansAt(arm)) {
            if (!other.a().equals(span.a()) || !other.b().equals(span.b())) {
                return other;
            }
        }
        return null;
    }

    private void advance(RailNetwork network) {
        toFront -= speed;
        while (toFront < 0.0 || toFront > span.length()) {
            if (toFront < 0.0) {
                Span next = beyond(network, front);
                if (next == null) {
                    toFront = 0.0;
                    speed = 0.0;
                    return;
                }
                double over = -toFront;
                front = next.otherEnd(front);
                span = next;
                toFront = next.length() - over;
            } else {
                Arm back = span.otherEnd(front);
                Span next = beyond(network, back);
                if (next == null) {
                    toFront = span.length();
                    speed = 0.0;
                    return;
                }
                double over = toFront - span.length();
                front = back;
                span = next;
                toFront = over;
            }
        }
    }
}
