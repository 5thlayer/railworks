// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * The second click of the Rail gesture, from the Arm held to the Arm clicked: what it does to the
 * Spans and how much Rail moves. Nothing here touches a player's items; the caller moves
 * {@link Outcome#rail()}.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Stringing {

    public enum Result {
        /** A Span was strung and {@link Outcome#rail()} Rail is spent. */
        STRUNG,
        /** The Span between the two Arms was cut and {@link Outcome#rail()} Rail is refunded. */
        CUT,
        /** The same Arm again: the pending Span is dropped, nothing changed. */
        CANCELLED,
        TOO_LONG,
        /** One of the Arms already takes {@link SpanRules#SPANS_PER_ARM} Spans. */
        ARM_FULL,
        SHORT_OF_RAIL
    }

    /** @param rail Rail spent on a {@code STRUNG}, refunded on a {@code CUT}, else 0 */
    public record Outcome(Result result, int rail) {
    }

    private Stringing() {
    }

    /**
     * @param railHeld the Rail the player can spend
     */
    public static Outcome click(RailNetwork network, Arm anchor, Arm target, int railHeld) {
        if (anchor.equals(target)) {
            return new Outcome(Result.CANCELLED, 0);
        }
        Span existing = network.get(anchor, target);
        if (existing != null) {
            network.remove(anchor, target);
            return new Outcome(Result.CUT, existing.cost());
        }
        if (network.spansAt(anchor).size() >= SpanRules.SPANS_PER_ARM
                || network.spansAt(target).size() >= SpanRules.SPANS_PER_ARM) {
            return new Outcome(Result.ARM_FULL, 0);
        }
        if (SpanRules.tooLong(anchor, target)) {
            return new Outcome(Result.TOO_LONG, 0);
        }
        int cost = SpanRules.cost(anchor, target);
        if (railHeld < cost) {
            return new Outcome(Result.SHORT_OF_RAIL, 0);
        }
        network.add(Span.of(anchor, target, cost));
        return new Outcome(Result.STRUNG, cost);
    }
}
