// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Which Span a newly placed column strings by itself: the one to the nearest Pylon the click's own rule
 * would string to, within the length, grade and bend limits and with a Span to spare at its Arm. The
 * server places by it and the Placement Preview draws it, both asking this.
 *
 * <p>Pure: no Minecraft types.
 */
public final class AutoStringing {

    /** The Arm to string to and the Rail it costs. */
    public record Choice(Arm target, int cost) {
    }

    private AutoStringing() {
    }

    /**
     * @param placed   the new column's Arm
     * @param arms     every other Arm that might be in reach
     * @param railHeld the Rail the placer can spend; a farther Span costs at least as much as a
     *                 nearer one, so too little Rail for the nearest is too little for all
     * @return the Span to string, or empty where no Arm is in reach and free, or the Rail falls short
     */
    public static Optional<Choice> choose(RailNetwork network, Arm placed, Collection<Arm> arms, int railHeld) {
        return arms.stream()
                .filter(arm -> !arm.equals(placed))
                // The click's own rule decides each candidate, so a refusal added there applies here too.
                .filter(arm -> Stringing.decide(network, placed, arm, railHeld).result() == Stringing.Result.STRUNG)
                .min(Comparator.comparingLong((Arm arm) -> placed.distanceSquaredTo(arm)).thenComparing(Comparator.naturalOrder()))
                .map(arm -> new Choice(arm, SpanRules.cost(placed, arm)));
    }

    /**
     * The Arm a placed column would have strung to but for the grade or bend limit, which the Placement
     * Preview draws red: where {@link #choose} finds nothing, the nearest Arm the click's rule refuses
     * as {@link Stringing.Result#TOO_STEEP} or {@link Stringing.Result#TOO_SHARP}.
     */
    public static Optional<Arm> refused(RailNetwork network, Arm placed, Collection<Arm> arms, int railHeld) {
        if (choose(network, placed, arms, railHeld).isPresent()) {
            return Optional.empty();
        }
        return arms.stream()
                .filter(arm -> !arm.equals(placed))
                .filter(arm -> {
                    Stringing.Result result = Stringing.decide(network, placed, arm, railHeld).result();
                    return result == Stringing.Result.TOO_STEEP || result == Stringing.Result.TOO_SHARP;
                })
                .min(Comparator.comparingLong((Arm arm) -> placed.distanceSquaredTo(arm)).thenComparing(Comparator.naturalOrder()));
    }
}
