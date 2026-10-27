// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        /**
         * A block fills the {@link Clearance} of the Span, or of a Span already at either Arm as the new
         * one would bend it.
         */
        BLOCKED,
        SHORT_OF_RAIL
    }

    /**
     * @param rail    Rail spent on a {@code STRUNG}, refunded on a {@code CUT}, else 0
     * @param blocked on a {@code BLOCKED}, the first block in the way, nearest the held Arm; else null
     */
    public record Outcome(Result result, int rail, Clearance.@Nullable Cell blocked) {

        public Outcome(Result result, int rail) {
            this(result, rail, null);
        }
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
     * @param blocks   which blocks of the world are filled
     * @param railHeld the Rail the player can spend
     */
    public static Outcome decide(RailNetwork network, Clearance.Blocks blocks, Arm anchor, Arm target, int railHeld) {
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
        Optional<Clearance.Cell> blocked = blocked(network, blocks, anchor, target);
        if (blocked.isPresent()) {
            return new Outcome(Result.BLOCKED, 0, blocked.get());
        }
        int cost = SpanRules.cost(anchor, target);
        if (railHeld < cost) {
            return new Outcome(Result.SHORT_OF_RAIL, 0);
        }
        return new Outcome(Result.STRUNG, cost);
    }

    /**
     * Whether a Span from {@code at} to {@code to} bends too sharply against any other Span at
     * {@code at}, the one to {@code to} itself left out where it is already strung. Any, since an Arm
     * takes two Spans and so one line; a Junction's branch leaving beside its sibling would only need to
     * run on from one of them, judged by the same {@link SpanRules#tooSharp}.
     */
    private static boolean bendsTooSharply(RailNetwork network, Arm at, Arm to) {
        for (Span span : network.spansAt(at)) {
            if (!span.otherEnd(at).equals(to) && SpanRules.tooSharp(span.otherEnd(at), at, to)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first block filling the Clearance of a new Span from {@code anchor} to {@code target}, or of
     * a Span already at either Arm, along the curve it would take once the new one bends it there.
     */
    private static Optional<Clearance.Cell> blocked(RailNetwork network, Clearance.Blocks blocks, Arm anchor, Arm target) {
        List<RailCurve> curves = new ArrayList<>();
        curves.add(RailCurve.between(network, anchor, target));
        Span added = Span.of(anchor, target, 0);
        for (Arm end : new Arm[] {anchor, target}) {
            for (Span span : network.spansAt(end)) {
                curves.add(RailCurve.strung(network, added, span.a(), span.b()));
            }
        }
        return firstFilled(curves, blocks);
    }

    /** The first block {@code blocks} says is filled in the Clearance along any of {@code curves}, in their order. */
    private static Optional<Clearance.Cell> firstFilled(Iterable<RailCurve> curves, Clearance.Blocks blocks) {
        for (RailCurve curve : curves) {
            Optional<Clearance.Cell> filled = Clearance.firstFilled(Clearance.along(curve), blocks);
            if (filled.isPresent()) {
                return filled;
            }
        }
        return Optional.empty();
    }

    /**
     * What raising the Arm at {@code from} to {@code to}, a segment stacked on its column, would break,
     * changing nothing: {@code TOO_LONG} for a raised Span past the length limit, {@code TOO_STEEP} for
     * one past the grade limit, {@code TOO_SHARP} for
     * a bend past the limit through the raised Arm or at a raised Span's far Arm, or {@code BLOCKED},
     * with the block, for a block in the Clearance of a Span the raise bends, the new segment at
     * {@code to} counted as filled. Empty when every Span keeps the rules, as with no Spans at all.
     */
    public static Optional<Outcome> raise(RailNetwork network, Clearance.Blocks blocks, Arm from, Arm to) {
        if (network.spansAt(from).isEmpty()) {
            return Optional.empty();
        }
        RailNetwork raised = network.copy();
        raised.rekey(from, to);
        List<Span> atTo = raised.spansAt(to);
        Set<Span> bent = new LinkedHashSet<>(atTo);
        for (Span span : atTo) {
            Arm other = span.otherEnd(to);
            if (SpanRules.tooLong(to, other)) {
                return Optional.of(new Outcome(Result.TOO_LONG, 0));
            }
            if (SpanRules.tooSteep(to, other)) {
                return Optional.of(new Outcome(Result.TOO_STEEP, 0));
            }
            // At the far Arm, against the Spans already there, and at the raised Arm, against each other.
            if (bendsTooSharply(raised, other, to) || bendsTooSharply(raised, to, other)) {
                return Optional.of(new Outcome(Result.TOO_SHARP, 0));
            }
            bent.addAll(raised.spansAt(other));
        }
        Clearance.Blocks withSegment = (x, y, z) -> (x == to.x() && y == to.y() && z == to.z()) || blocks.filled(x, y, z);
        return firstFilled(bent.stream().map(raised::curve).toList(), withSegment)
                .map(cell -> new Outcome(Result.BLOCKED, 0, cell));
    }

    /** Whether {@code result} is a refusal: the click changes nothing and spends nothing. */
    public static boolean refused(Result result) {
        return switch (result) {
            case STRUNG, CUT, CANCELLED -> false;
            case TOO_LONG, TOO_STEEP, TOO_SHARP, ARM_FULL, BLOCKED, SHORT_OF_RAIL -> true;
        };
    }

    /** The slack's tint for an aim at {@code target}, or at nothing when it is null. */
    public static Tint tintFor(RailNetwork network, Clearance.Blocks blocks, Arm anchor, @Nullable Arm target,
                               int railHeld) {
        if (target == null) {
            return Tint.HELD;
        }
        Result result = decide(network, blocks, anchor, target, railHeld).result();
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
     * @param blocks   which blocks of the world are filled
     * @param railHeld the Rail the player can spend
     */
    public static Outcome click(RailNetwork network, Clearance.Blocks blocks, Arm anchor, Arm target, int railHeld) {
        Outcome outcome = decide(network, blocks, anchor, target, railHeld);
        switch (outcome.result()) {
            case STRUNG -> network.add(Span.of(anchor, target, outcome.rail()));
            case CUT -> network.remove(anchor, target);
            default -> {
            }
        }
        return outcome;
    }
}
