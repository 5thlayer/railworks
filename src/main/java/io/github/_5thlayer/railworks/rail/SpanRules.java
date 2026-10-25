// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * What a Span costs and how far and how many it may go. One length measures all three: the straight
 * distance between its two Arms, rounded up to a whole block.
 *
 * <p>Pure: no Minecraft types.
 */
public final class SpanRules {

    /** The longest Span, in blocks, unless {@code railworks-server.toml} says otherwise: a Transmission Pole's reach in Wireworks. */
    public static final double DEFAULT_LENGTH_LIMIT = 32.0;

    /** How many Spans an Arm takes until Junctions arrive: every Line is a simple chain. */
    public static final int SPANS_PER_ARM = 2;

    private static volatile double lengthLimit = DEFAULT_LENGTH_LIMIT;

    private SpanRules() {
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads. */
    public static void configure(double lengthLimit) {
        if (lengthLimit <= 0) {
            throw new IllegalArgumentException("span length limit " + lengthLimit);
        }
        SpanRules.lengthLimit = lengthLimit;
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
}
