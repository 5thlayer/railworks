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

    /** The longest Span, in blocks, measured as its cost is. */
    public static final int LENGTH_LIMIT = 64;

    /** How many Spans an Arm takes until Junctions arrive: every Line is a simple chain. */
    public static final int SPANS_PER_ARM = 2;

    private SpanRules() {
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

    /** Whether a Span between these Arms is longer than the limit. */
    public static boolean tooLong(Arm a, Arm b) {
        return cost(a, b) > LENGTH_LIMIT;
    }
}
