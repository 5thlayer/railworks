// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Comparator;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Station: a named block beside the Rail (ADR 0007). It serves the one Span it is nearest, and is
 * reached from {@link #STRETCH} blocks either way of its foot, the point of the Span nearest it: a
 * car whose Place is in that stretch is at the Station. It serves only the Trains that have it on
 * their {@link DriveSide}, those heading for its {@link #servingEnd} (ADR 0008); one under the Rail
 * has no side and serves neither way. Nothing about it is stored but its block and its name, so a
 * Span strung, cut or moved after it was placed is asked about afresh.
 *
 * <p>Pure: no Minecraft types.
 *
 * <p>Its {@code x}, {@code y} and {@code z} are its block's position, as an Arm's are.
 */
public record Station(int x, int y, int z, String name) {

    /** How far the Station block's centre may be from the Rail and still serve it, in blocks. */
    public static final double REACH = 1.25;
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

    /**
     * How far off the Rail's line a Station's centre may be, seen from above, and still count as under
     * it: a block under the Rail is right under its line, and one beside it a whole block off.
     */
    static final double UNDER = 0.5;

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

    public Station withName(String name) {
        return new Station(x, y, z, name);
    }

    /**
     * The Span this Station serves: of the Spans within {@link #REACH} of it, the nearest, and between
     * two as near, the one whose ends sort first, so every caller picks the same. Empty when the Station
     * is by no Rail.
     */
    public Optional<Foot> foot(RailNetwork network) {
        return network.all().stream()
                .map(this::footOn)
                .filter(s -> s.distance() <= REACH)
                .min(Comparator.comparingDouble(Foot::distance)
                        .thenComparing(s -> s.span().a())
                        .thenComparing(s -> s.span().b()));
    }

    /**
     * The end of its Span that a Train it serves heads for: the one that puts the Station on the
     * Train's {@link DriveSide#current() drive side}. Empty under the Rail, where it has no side, and
     * by no Rail.
     */
    public Optional<Arm> servingEnd(RailNetwork network) {
        return foot(network).flatMap(foot -> servingEnd(foot, DriveSide.current()));
    }

    /** {@link #servingEnd} on the Span of {@code foot}, driving on {@code side}. */
    Optional<Arm> servingEnd(Foot foot, DriveSide side) {
        Span span = foot.span();
        Arm a = span.a();
        Arm b = span.b();
        double t = foot.fromA() / span.length();
        // From the foot to the Station's centre, seen from above, in Arm coordinates.
        double sx = x + 0.5 - RailGeometry.ATTACH_X - (a.x() + (b.x() - a.x()) * t);
        double sz = z + 0.5 - RailGeometry.ATTACH_Z - (a.z() + (b.z() - a.z()) * t);
        double hx = b.x() - a.x();
        double hz = b.z() - a.z();
        double run = Math.sqrt(hx * hx + hz * hz);
        if (run == 0.0) {
            return Optional.empty();
        }
        // How far to the right of a Train heading for b the Station stands: right of (hx, hz) is (-hz, hx),
        // x running east and z south.
        double right = (sz * hx - sx * hz) / run;
        if (Math.abs(right) < UNDER) {
            return Optional.empty();
        }
        return Optional.of((right > 0.0) == (side == DriveSide.RIGHT) ? b : a);
    }

    /** Whether a car at {@code place} is at this Station: on the Span it serves, within its stretch. */
    public boolean serves(RailNetwork network, Place place) {
        // Cheap first: a Station out of reach of this Span cannot serve it, whatever else is near.
        Foot here = footOn(place.span());
        if (here.distance() > REACH || Math.abs(fromA(place) - here.fromA()) > STRETCH) {
            return false;
        }
        return foot(network).map(s -> sameSpan(s.span(), place.span())).orElse(false);
    }

    /** The foot of this Station on {@code span}: the point of the Span nearest the block's centre. */
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
        double length = span.length();
        double t = ((cx - a.x()) * dx + (cy - a.y()) * dy + (cz - a.z()) * dz) / (length * length);
        t = Math.max(0.0, Math.min(1.0, t));
        double px = a.x() + dx * t - cx;
        double py = a.y() + dy * t - cy;
        double pz = a.z() + dz * t - cz;
        return new Foot(span, t * length, Math.sqrt(px * px + py * py + pz * pz));
    }

    /** How far {@code place} is along its Span from the Span's first end. */
    private static double fromA(Place place) {
        return place.front().equals(place.span().a()) ? place.toFront() : place.span().length() - place.toFront();
    }

    private static boolean sameSpan(Span one, Span other) {
        return one.a().equals(other.a()) && one.b().equals(other.b());
    }
}
