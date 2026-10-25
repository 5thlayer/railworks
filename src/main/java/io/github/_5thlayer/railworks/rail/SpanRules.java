// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * What a Span costs, how far and how many it may go, how steeply it may climb and how sharply it may
 * bend through an Arm. One length measures the first three: the straight distance between its two
 * Arms, rounded up to a whole block.
 *
 * <p>Pure: no Minecraft types.
 */
public final class SpanRules {

    /** The longest Span, in blocks, unless {@code railworks-server.toml} says otherwise: a Transmission Pole's reach in Wireworks. */
    public static final double DEFAULT_LENGTH_LIMIT = 32.0;

    /** How many Spans an Arm takes until Junctions arrive: every Line is a simple chain. */
    public static final int SPANS_PER_ARM = 2;

    /**
     * The steepest Span, as rise over run, unless {@code railworks-server.toml} says otherwise: a block
     * up for every four across. Factorio's trains climb only on its long, gentle rail ramps; Minecraft's
     * blocky ground asks for a little more.
     */
    public static final double DEFAULT_GRADE_LIMIT = 0.25;

    /**
     * The sharpest bend through an Arm, in degrees, unless {@code railworks-server.toml} says
     * otherwise: Factorio's curved rail pair turns a train through 45 degrees, so a right angle takes
     * two Arms, as it takes two curves there.
     */
    public static final double DEFAULT_BEND_LIMIT = 45.0;

    /** Slack for the floating point, so a grade or a bend landing exactly on its limit is allowed. */
    private static final double EPSILON = 1e-9;

    private static volatile double lengthLimit = DEFAULT_LENGTH_LIMIT;
    private static volatile double gradeLimit = DEFAULT_GRADE_LIMIT;
    private static volatile double bendLimit = DEFAULT_BEND_LIMIT;

    private SpanRules() {
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads. */
    public static void configure(double lengthLimit) {
        if (lengthLimit <= 0) {
            throw new IllegalArgumentException("span length limit " + lengthLimit);
        }
        SpanRules.lengthLimit = lengthLimit;
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads. */
    public static void configureGrade(double gradeLimit) {
        if (gradeLimit <= 0) {
            throw new IllegalArgumentException("span grade limit " + gradeLimit);
        }
        SpanRules.gradeLimit = gradeLimit;
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads. */
    public static void configureBend(double bendLimit) {
        if (bendLimit <= 0 || bendLimit > 180) {
            throw new IllegalArgumentException("span bend limit " + bendLimit);
        }
        SpanRules.bendLimit = bendLimit;
    }

    /** The longest Span, in blocks: the straight distance between its Arms, the length its cost rounds up. */
    public static double lengthLimit() {
        return lengthLimit;
    }

    /** The Rail a Span between these Arms costs: its straight length, rounded up. */
    public static int cost(Arm a, Arm b) {
        long squared = a.distanceSquaredTo(b);
        // Exact in integers: the least n whose square reaches the squared distance.
        long n = (long) Math.ceil(Math.sqrt((double) squared));
        while (n > 0 && (n - 1) * (n - 1) >= squared) {
            n--;
        }
        while (n * n < squared) {
            n++;
        }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    /** Whether a Span between these Arms is longer than the limit, measured as its cost is. */
    public static boolean tooLong(Arm a, Arm b) {
        return a.distanceSquaredTo(b) > lengthLimit * lengthLimit;
    }

    /** The steepest Span, as rise over run: blocks up for each block across. */
    public static double gradeLimit() {
        return gradeLimit;
    }

    /** The sharpest bend through an Arm, in degrees off straight on. */
    public static double bendLimit() {
        return bendLimit;
    }

    /** Whether a Span between these Arms climbs more steeply than the limit, either way along it. A Span straight up is. */
    public static boolean tooSteep(Arm a, Arm b) {
        double dx = (double) a.x() - b.x();
        double dz = (double) a.z() - b.z();
        double rise = Math.abs((double) a.y() - b.y());
        return rise > gradeLimit * Math.sqrt(dx * dx + dz * dz) + EPSILON;
    }

    /**
     * How far a Train turns, in degrees, running from {@code from} through {@code at} on to {@code to}:
     * 0 straight on, 180 back the way it came. A climb that levels off bends too: the angle is between
     * the two Spans' directions in three dimensions.
     */
    public static double bend(Arm from, Arm at, Arm to) {
        double ix = (double) at.x() - from.x();
        double iy = (double) at.y() - from.y();
        double iz = (double) at.z() - from.z();
        double ox = (double) to.x() - at.x();
        double oy = (double) to.y() - at.y();
        double oz = (double) to.z() - at.z();
        double lengths = Math.sqrt((ix * ix + iy * iy + iz * iz) * (ox * ox + oy * oy + oz * oz));
        if (lengths == 0) {
            return 0.0;
        }
        double cos = (ix * ox + iy * oy + iz * oz) / lengths;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
    }

    /**
     * Whether a Train runs straight on from {@code from} through {@code at} to {@code to}, without the
     * least {@link #bend}: exact, as Arms are whole blocks. Running back the way it came is a bend.
     */
    public static boolean inLine(Arm from, Arm at, Arm to) {
        long ix = (long) at.x() - from.x();
        long iy = (long) at.y() - from.y();
        long iz = (long) at.z() - from.z();
        long ox = (long) to.x() - at.x();
        long oy = (long) to.y() - at.y();
        long oz = (long) to.z() - at.z();
        boolean parallel = iy * oz == iz * oy && iz * ox == ix * oz && ix * oy == iy * ox;
        return parallel && ix * ox + iy * oy + iz * oz > 0;
    }

    /**
     * Whether two Spans meeting at {@code at}, from there to {@code one} and to {@code other}, bend
     * through it more sharply than the limit: whether a Train could not run from one onto the other.
     * Either order gives the same answer.
     */
    public static boolean tooSharp(Arm one, Arm at, Arm other) {
        return bend(one, at, other) > bendLimit + EPSILON;
    }
}
