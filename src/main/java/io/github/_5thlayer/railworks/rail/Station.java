// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Comparator;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Station: a named block beside the Rail (ADR 0007), in the {@link #inRow row} two blocks off its
 * line at the height of a Cargo car's chests, where hatches and Chargers stand too (ADR 0010). It
 * serves the one Span it is nearest of those it is in the row of, and is reached from
 * {@link #STRETCH} blocks either way of its foot, the point of the Span nearest it: a car whose
 * Place is in that stretch is at the Station. It serves only the Trains that have it on their
 * {@link DriveSide}, those heading for its {@link #servingEnd} (ADR 0008). Nothing about it is
 * stored but its block and its name, so a Span strung, cut or moved after it was placed is asked
 * about afresh.
 *
 * <p>Pure: no Minecraft types.
 *
 * <p>Its {@code x}, {@code y} and {@code z} are its block's position, as an Arm's are.
 */
public record Station(int x, int y, int z, String name) {

    /**
     * How far off the Rail's line, seen from above, the row beside it is, in blocks: a two-wide car
     * reaches halfway into the blocks either side of the Rail's, so the row is the next blocks out
     * (ADR 0010).
     */
    public static final double ROW_OFF = 2.0;
    /** How far along the Span either way of its foot a car is still at the Station, in blocks. */
    public static final double STRETCH = 1.0;
    /** The longest name a Station takes. */
    public static final int NAME_LIMIT = 32;
    /** What a Station is called until it is named. */
    public static final String DEFAULT_NAME = "Station";

    public static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Station::x),
            Codec.INT.fieldOf("y").forGetter(Station::y),
            Codec.INT.fieldOf("z").forGetter(Station::z),
            Codec.STRING.fieldOf("name").forGetter(Station::name)
    ).apply(i, Station::new));

    /**
     * A Station's foot: the point of a Span nearest it, as how far along the Span from its first end
     * it is, and how far the Station's centre is from it.
     */
    public record Foot(Span span, double fromA, double distance) {

        /** The foot as a Place, heading for the Span's second end. */
        public Place place() {
            return facing(span.b());
        }

        /** The foot as a Place, heading for {@code end}, one of its Span's ends. */
        public Place facing(Arm end) {
            return new Place(span, end, end.equals(span.b()) ? span.length() - fromA : fromA);
        }
    }

    /** Slack for the floating point in {@link #inRow}. */
    private static final double EPSILON = 1e-6;

    /**
     * What a typed name becomes: trimmed, with formatting codes and control characters taken out and
     * cut to {@link #NAME_LIMIT}, or empty when nothing is left, which leaves the name as it was.
     */
    public static Optional<String> name(String typed) {
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (c == '§') {
                i++;
            } else if (!Character.isISOControl(c)) {
                kept.append(c);
            }
        }
        String name = kept.toString().strip();
        if (name.length() > NAME_LIMIT) {
            name = name.substring(0, NAME_LIMIT).strip();
        }
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    /** Whether {@code other} stands at this Station's block, whatever either is called. */
    public boolean samePlace(Station other) {
        return x == other.x && y == other.y && z == other.z;
    }

    public Station withName(String name) {
        return new Station(x, y, z, name);
    }

    /**
     * Whether a block {@code off} its foot, as {@link #fromFoot} says, is in the row beside the Rail
     * where Stations, hatches and Chargers stand (ADR 0010): {@link #ROW_OFF} off the line seen from
     * above, and in the block a Cargo car's chests sit in, whose bottom is a block above the
     * flange's underside, the line from Arm to Arm.
     */
    static boolean inRow(Place.Point off) {
        return Math.abs(Math.sqrt(off.x() * off.x() + off.z() * off.z()) - ROW_OFF) < EPSILON
                && off.y() >= 1.0 && off.y() < 2.0;
    }

    /**
     * The Span this Station serves: of the Spans it is in the {@link #inRow row} of, the nearest,
     * and between two as near, the one whose ends sort first, so every caller picks the same. Empty
     * when the Station is by no Rail.
     */
    public Optional<Foot> foot(RailNetwork network) {
        return network.all().stream()
                .map(this::footOn)
                .filter(s -> inRow(fromFoot(s)))
                .min(Comparator.comparingDouble(Foot::distance)
                        .thenComparing(s -> s.span().a())
                        .thenComparing(s -> s.span().b()));
    }

    /**
     * The end of its Span that a Train it serves heads for: the one that puts the Station on the
     * Train's {@link DriveSide#current() drive side}. Empty by no Rail.
     */
    public Optional<Arm> servingEnd(RailNetwork network) {
        return foot(network).flatMap(foot -> servingEnd(foot, DriveSide.current()));
    }

    /** {@link #servingEnd} on the Span of {@code foot}, driving on {@code side}. */
    Optional<Arm> servingEnd(Foot foot, DriveSide side) {
        Arm a = foot.span().a();
        Arm b = foot.span().b();
        Place.Point off = fromFoot(foot);
        double sx = off.x();
        double sz = off.z();
        double hx = b.x() - a.x();
        double hz = b.z() - a.z();
        double run = Math.sqrt(hx * hx + hz * hz);
        if (run == 0.0) {
            return Optional.empty();
        }
        // How far to the right of a Train heading for b the Station stands: right of (hx, hz) is (-hz, hx),
        // x running east and z south.
        double right = (sz * hx - sx * hz) / run;
        return Optional.of((right > 0.0) == (side == DriveSide.RIGHT) ? b : a);
    }

    /**
     * From {@code foot}, on the line from Arm to Arm, to the block's centre, in Arm coordinates: how
     * far off the Rail the block stands.
     */
    Place.Point fromFoot(Foot foot) {
        Arm a = foot.span().a();
        Arm b = foot.span().b();
        double t = foot.fromA() / foot.span().length();
        return new Place.Point(
                x + 0.5 - RailGeometry.ATTACH_X - (a.x() + (b.x() - a.x()) * t),
                y + 0.5 - RailGeometry.ATTACH_Y - (a.y() + (b.y() - a.y()) * t),
                z + 0.5 - RailGeometry.ATTACH_Z - (a.z() + (b.z() - a.z()) * t));
    }

    /** Whether a car at {@code place} is at this Station: on the Span it serves, within its stretch. */
    public boolean serves(RailNetwork network, Place place) {
        // Cheap first: a Station out of the row of this Span cannot serve it, whatever else is near.
        Foot here = footOn(place.span());
        if (!inRow(fromFoot(here)) || Math.abs(fromA(place) - here.fromA()) > STRETCH) {
            return false;
        }
        return foot(network).map(s -> sameSpan(s.span(), place.span())).orElse(false);
    }

    /**
     * The foot of this Station on {@code span}: the point of the Span nearest the block's centre seen
     * from above, so a block beside a slope is measured square to it; nearest in space for a Span
     * with no run.
     */
    Foot footOn(Span span) {
        // The block's centre in Arm coordinates, where the line from Arm to Arm is the flange's underside.
        double cx = x + 0.5 - RailGeometry.ATTACH_X;
        double cy = y + 0.5 - RailGeometry.ATTACH_Y;
        double cz = z + 0.5 - RailGeometry.ATTACH_Z;
        Arm a = span.a();
        Arm b = span.b();
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double dz = b.z() - a.z();
        double run = dx * dx + dz * dz;
        double t = run > 0.0
                ? ((cx - a.x()) * dx + (cz - a.z()) * dz) / run
                : ((cx - a.x()) * dx + (cy - a.y()) * dy + (cz - a.z()) * dz) / (dx * dx + dy * dy + dz * dz);
        t = Math.max(0.0, Math.min(1.0, t));
        double px = a.x() + dx * t - cx;
        double py = a.y() + dy * t - cy;
        double pz = a.z() + dz * t - cz;
        return new Foot(span, t * span.length(), Math.sqrt(px * px + py * py + pz * pz));
    }

    /** How far {@code place} is along its Span from the Span's first end. */
    private static double fromA(Place place) {
        return place.front().equals(place.span().a()) ? place.toFront() : place.span().length() - place.toFront();
    }

    private static boolean sameSpan(Span one, Span other) {
        return one.a().equals(other.a()) && one.b().equals(other.b());
    }
}
