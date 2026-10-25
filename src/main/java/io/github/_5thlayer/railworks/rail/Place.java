// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.List;

/**
 * A spot on the Line, with a heading: it faces one end of its Span, {@link #front()}, and
 * {@link #toFront()} is how far it is from that end. Moving it runs on through an Arm into the Span
 * beyond and stops at a line's end, an Arm with one Span.
 *
 * <p>Pure: no Minecraft types.
 */
public record Place(Span span, Arm front, double toFront) {

    /** Where a Place is, in Arm coordinates; the world adds where the Rail rests on an Arm. */
    public record Point(double x, double y, double z) {
    }

    /** Where a move ended, and whether a line's end cut it short. */
    public record Moved(Place place, boolean stopped) {
    }

    public Place {
        if (!span.touches(front)) {
            throw new IllegalArgumentException(front + " is not an end of " + span);
        }
        toFront = Math.max(0.0, Math.min(span.length(), toFront));
    }

    /** The end of its Span it does not face. */
    public Arm back() {
        return span.otherEnd(front);
    }

    /** Where it is, in Arm coordinates: along the Span, {@code toFront} short of its front. */
    public Point point() {
        Arm back = back();
        double t = toFront / span.length();
        return new Point(front.x() + (back.x() - front.x()) * t,
                front.y() + (back.y() - front.y()) * t,
                front.z() + (back.z() - front.z()) * t);
    }

    /** Whether it sits at a line's end, ahead for a positive {@code way} and behind for a negative one. */
    public boolean atEnd(RailNetwork network, int way) {
        return way > 0
                ? toFront <= 0.0 && beyond(network, front) == null
                : toFront >= span.length() && beyond(network, back()) == null;
    }

    /**
     * {@code by} blocks on toward its front, or back for a negative {@code by}, keeping its heading
     * through each Arm; a line's end stops it there.
     */
    public Moved move(RailNetwork network, double by) {
        Span span = this.span;
        Arm front = this.front;
        double toFront = this.toFront - by;
        while (toFront < 0.0 || toFront > span.length()) {
            if (toFront < 0.0) {
                Span next = beyond(network, span, front);
                if (next == null) {
                    return new Moved(new Place(span, front, 0.0), true);
                }
                double over = -toFront;
                front = next.otherEnd(front);
                span = next;
                toFront = next.length() - over;
            } else {
                Arm back = span.otherEnd(front);
                Span next = beyond(network, span, back);
                if (next == null) {
                    return new Moved(new Place(span, front, span.length()), true);
                }
                double over = toFront - span.length();
                front = back;
                span = next;
                toFront = over;
            }
        }
        return new Moved(new Place(span, front, toFront), false);
    }

    /** The Spans from here back {@code length} blocks, this one first, as far as the line goes. */
    public List<Span> spansBack(RailNetwork network, double length) {
        List<Span> spans = new ArrayList<>();
        Span span = this.span;
        Arm back = back();
        spans.add(span);
        double left = length - (span.length() - toFront);
        while (left > 0.0) {
            Span next = beyond(network, span, back);
            if (next == null) {
                break;
            }
            back = next.otherEnd(back);
            span = next;
            spans.add(span);
            left -= span.length();
        }
        return spans;
    }

    /**
     * The Arm at {@code from} has moved to {@code to}, {@code network} already holding its Spans
     * there: a Place on one of them stays on it, the same fraction of the way along, rather than
     * losing the Rail it was on.
     */
    public Place movedArm(RailNetwork network, Arm from, Arm to) {
        if (!span.touches(from)) {
            return this;
        }
        Span moved = network.get(to, span.otherEnd(from));
        if (moved == null) {
            return this;
        }
        double fraction = toFront / span.length();
        return new Place(moved, front.equals(from) ? to : front, fraction * moved.length());
    }

    private Span beyond(RailNetwork network, Arm arm) {
        return beyond(network, span, arm);
    }

    /** The Span past {@code arm} from {@code span}, or null at a line's end. */
    private static Span beyond(RailNetwork network, Span span, Arm arm) {
        for (Span other : network.spansAt(arm)) {
            if (!other.a().equals(span.a()) || !other.b().equals(span.b())) {
                return other;
            }
        }
        return null;
    }
}
