// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * The line the Rail takes along a Span: a smooth curve from its first Arm to its second, so that
 * what draws the Rail and what rides it agree, as {@link RailGeometry} makes them agree on the
 * cross-section. At each Arm the curve runs along the bisector of the two Spans meeting there, so
 * the Rail rests on every Pylon and a Train runs from one Span onto the next without turning on the
 * spot; at a line's end, an Arm with one Span, it heads straight at the other Arm. A Span with a
 * line's end at both of its Arms is straight.
 *
 * <p>The curve is a cubic Hermite between the Arms, measured by how far along it is in the Span's
 * own length: {@code fromA} runs from 0 at the first Arm to {@link Span#length()}, the straight
 * distance, at the second, and equal steps of it are equal steps along the curve. Rail cost, the
 * length limit and a Train's run along the Span all stay that straight distance; a Train on a bend
 * covers the slightly longer curve in the same time.
 *
 * <p>Coordinates are the Arms': the world adds where the Rail rests on an Arm. Pure: no Minecraft
 * types.
 */
public final class RailCurve {

    /** A spot on the curve and the unit direction it runs in there. */
    public record Pose(double x, double y, double z, double dx, double dy, double dz) {

        public Place.Point point() {
            return new Place.Point(x, y, z);
        }

        /** The same spot running the other way. */
        public Pose reversed() {
            return new Pose(x, y, z, -dx, -dy, -dz);
        }
    }

    /** How finely the curve is measured to space equal steps evenly along it. */
    static final int SAMPLES = 32;

    private final double length;
    private final double ax;
    private final double ay;
    private final double az;
    private final double bx;
    private final double by;
    private final double bz;
    /** The tangents at each end, scaled by the straight length. */
    private final double[] m0;
    private final double[] m1;
    /** How far along the curve each of the {@link #SAMPLES} + 1 evenly spaced parameters is. */
    private final double[] arc = new double[SAMPLES + 1];

    private RailCurve(Arm a, Arm b, double[] startHeading, double[] endHeading) {
        this.length = Math.sqrt(a.distanceSquaredTo(b));
        this.ax = a.x();
        this.ay = a.y();
        this.az = a.z();
        this.bx = b.x();
        this.by = b.y();
        this.bz = b.z();
        this.m0 = scale(startHeading, length);
        this.m1 = scale(endHeading, length);
        double[] last = evaluate(0.0);
        for (int i = 1; i <= SAMPLES; i++) {
            double[] next = evaluate((double) i / SAMPLES);
            arc[i] = arc[i - 1] + distance(last, next);
            last = next;
        }
    }

    /**
     * The curve a Span between {@code a} and {@code b} takes in {@code network}, whether or not it is
     * strung there yet: at each Arm it meets the Span beyond that Arm, the first other Span there, as
     * a Train runs on through it. A Span not yet strung, such as a pending one, takes the curve it
     * would on stringing, the Spans already there unchanged.
     */
    public static RailCurve between(RailNetwork network, Arm a, Arm b) {
        Arm beforeA = beyond(network, a, b);
        Arm afterB = beyond(network, b, a);
        double[] chord = unit(b.x() - a.x(), b.y() - a.y(), b.z() - a.z());
        return new RailCurve(a, b,
                beforeA == null ? chord : bisector(beforeA, a, chord),
                afterB == null ? chord : bisector(a, b, unit(afterB.x() - b.x(), afterB.y() - b.y(), afterB.z() - b.z())));
    }

    /** The straight distance between the Arms, the length {@code fromA} runs over. */
    public double length() {
        return length;
    }

    /** How far the curve runs, Arm to Arm: a little more than {@link #length()} on a bend. */
    public double arcLength() {
        return arc[SAMPLES];
    }

    /** Where the curve is {@code fromA} along it from its first Arm, running toward the second. */
    public Pose at(double fromA) {
        double s = parameter(fromA);
        double[] p = evaluate(s);
        double[] d = derivative(s);
        double[] heading = unit(d[0], d[1], d[2]);
        if (heading == null) {
            heading = unit(bx - ax, by - ay, bz - az);
        }
        return new Pose(p[0], p[1], p[2], heading[0], heading[1], heading[2]);
    }

    /** The Hermite parameter {@code fromA} along, so equal steps of it are equal steps along the curve. */
    private double parameter(double fromA) {
        if (length == 0.0) {
            return 0.0;
        }
        double target = Math.max(0.0, Math.min(1.0, fromA / length)) * arc[SAMPLES];
        int lo = 0;
        int hi = SAMPLES;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (arc[mid] <= target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double step = arc[hi] - arc[lo];
        double within = step == 0.0 ? 0.0 : (target - arc[lo]) / step;
        return (lo + within) / SAMPLES;
    }

    private double[] evaluate(double s) {
        double s2 = s * s;
        double s3 = s2 * s;
        double h00 = 2 * s3 - 3 * s2 + 1;
        double h10 = s3 - 2 * s2 + s;
        double h01 = -2 * s3 + 3 * s2;
        double h11 = s3 - s2;
        return new double[] {
                h00 * ax + h10 * m0[0] + h01 * bx + h11 * m1[0],
                h00 * ay + h10 * m0[1] + h01 * by + h11 * m1[1],
                h00 * az + h10 * m0[2] + h01 * bz + h11 * m1[2]
        };
    }

    private double[] derivative(double s) {
        double s2 = s * s;
        double h00 = 6 * s2 - 6 * s;
        double h10 = 3 * s2 - 4 * s + 1;
        double h01 = -6 * s2 + 6 * s;
        double h11 = 3 * s2 - 2 * s;
        return new double[] {
                h00 * ax + h10 * m0[0] + h01 * bx + h11 * m1[0],
                h00 * ay + h10 * m0[1] + h01 * by + h11 * m1[1],
                h00 * az + h10 * m0[2] + h01 * bz + h11 * m1[2]
        };
    }

    /**
     * The direction through {@code at}, running in from {@code from}: halfway between the way in and
     * {@code out}, the unit way on. Where the two run straight back on each other there is no
     * halfway, and it runs on along {@code out}.
     */
    private static double[] bisector(Arm from, Arm at, double[] out) {
        double[] in = unit(at.x() - from.x(), at.y() - from.y(), at.z() - from.z());
        double[] half = in == null ? null : unit(in[0] + out[0], in[1] + out[1], in[2] + out[2]);
        return half == null ? out : half;
    }

    /** The far end of the first Span at {@code arm} other than the one toward {@code other}, or null at a line's end. */
    private static Arm beyond(RailNetwork network, Arm arm, Arm other) {
        for (Span span : network.spansAt(arm)) {
            Arm far = span.otherEnd(arm);
            if (!far.equals(other)) {
                return far;
            }
        }
        return null;
    }

    private static double[] unit(double x, double y, double z) {
        double norm = Math.sqrt(x * x + y * y + z * z);
        return norm < 1e-9 ? null : new double[] {x / norm, y / norm, z / norm};
    }

    private static double[] scale(double[] v, double by) {
        return v == null ? new double[3] : new double[] {v[0] * by, v[1] * by, v[2] * by};
    }

    private static double distance(double[] one, double[] other) {
        double dx = one[0] - other[0];
        double dy = one[1] - other[1];
        double dz = one[2] - other[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
