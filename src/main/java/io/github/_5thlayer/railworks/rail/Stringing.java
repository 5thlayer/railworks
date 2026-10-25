// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import org.jspecify.annotations.Nullable;

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
        /** The Span would climb more steeply than {@link SpanRules#gradeLimit()}. */
        TOO_STEEP,
        /** The Span would bend more sharply than {@link SpanRules#bendLimit()} against a Span already at either Arm. */
        TOO_SHARP,
        /** One of the Arms already takes {@link SpanRules#SPANS_PER_ARM} Spans. */
        ARM_FULL,
        SHORT_OF_RAIL
    }

    /** @param rail Rail spent on a {@code STRUNG}, refunded on a {@code CUT}, else 0 */
    public record Outcome(Result result, int rail) {
    }

    private Stringing() {
    }

    /** What a held end's slack Rail looks like: the click's outcome as the player sees it before clicking. */
    public enum Tint {
        /** Nothing aimed at, or the held Arm itself, which cancels. */
        HELD,
        /** The click would string a Span. */
        STRING,
        /** The click would cut the Span between the two. */
        CUT,
        /** The click would be refused. */
        REFUSED
    }

    /**
     * What a click from {@code anchor} on {@code target} would do, changing nothing. The one place
     * the rules live: {@link #click} applies what this decides, and the preview draws it, so a Span
     * the preview promises is a Span the click makes.
     *
     * @param railHeld the Rail the player can spend
     */
    public static Outcome decide(RailNetwork network, Arm anchor, Arm target, int railHeld) {
        if (anchor.equals(target)) {
            return new Outcome(Result.CANCELLED, 0);
        }
        Span existing = network.get(anchor, target);
        if (existing != null) {
            return new Outcome(Result.CUT, existing.cost());
        }
        if (network.spansAt(anchor).size() >= SpanRules.SPANS_PER_ARM
                || network.spansAt(target).size() >= SpanRules.SPANS_PER_ARM) {
            return new Outcome(Result.ARM_FULL, 0);
        }
        if (SpanRules.tooLong(anchor, target)) {
            return new Outcome(Result.TOO_LONG, 0);
        }
        if (SpanRules.tooSteep(anchor, target)) {
            return new Outcome(Result.TOO_STEEP, 0);
        }
        if (bendsTooSharply(network, anchor, target) || bendsTooSharply(network, target, anchor)) {
            return new Outcome(Result.TOO_SHARP, 0);
        }
        int cost = SpanRules.cost(anchor, target);
        if (railHeld < cost) {
            return new Outcome(Result.SHORT_OF_RAIL, 0);
        }
        return new Outcome(Result.STRUNG, cost);
    }

    /**
     * Whether a new Span from {@code at} to {@code to} bends too sharply against any Span already at
     * {@code at}. Any, since an Arm takes two Spans and so one line; a Junction's branch leaving beside
     * its sibling would only need to run on from one of them, judged by the same {@link SpanRules#tooSharp}.
     */
    private static boolean bendsTooSharply(RailNetwork network, Arm at, Arm to) {
        for (Span span : network.spansAt(at)) {
            if (SpanRules.tooSharp(span.otherEnd(at), at, to)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code result} is a refusal: the click changes nothing and spends nothing. */
    public static boolean refused(Result result) {
        return switch (result) {
            case STRUNG, CUT, CANCELLED -> false;
            case TOO_LONG, TOO_STEEP, TOO_SHARP, ARM_FULL, SHORT_OF_RAIL -> true;
        };
    }

    /** The slack's tint for an aim at {@code target}, or at nothing when it is null. */
    public static Tint tintFor(RailNetwork network, Arm anchor, @Nullable Arm target, int railHeld) {
        if (target == null) {
            return Tint.HELD;
        }
        Result result = decide(network, anchor, target, railHeld).result();
        if (refused(result)) {
            return Tint.REFUSED;
        }
        return switch (result) {
            case STRUNG -> Tint.STRING;
            case CUT -> Tint.CUT;
            default -> Tint.HELD;
        };
    }

    /**
     * The second click of the gesture, from the Arm held to the Arm clicked: decides it
     * ({@link #decide}) and applies a Span strung or cut.
     *
     * @param railHeld the Rail the player can spend
     */
    public static Outcome click(RailNetwork network, Arm anchor, Arm target, int railHeld) {
        Outcome outcome = decide(network, anchor, target, railHeld);
        switch (outcome.result()) {
            case STRUNG -> network.add(Span.of(anchor, target, outcome.rail()));
            case CUT -> network.remove(anchor, target);
            default -> {
            }
        }
        return outcome;
    }
}
