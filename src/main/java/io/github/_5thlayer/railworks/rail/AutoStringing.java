// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Which Span a newly placed column strings by itself: the one to the nearest Pylon within the length
 * limit whose Arm has a Span to spare. The server places by it and the Placement Preview draws it,
 * both asking this.
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
     * @param railHeld the Rail the placer can spend; the nearest Arm is the only one tried, since a
     *                 farther Span costs at least as much
     * @return the Span to string, or empty where no Arm is in reach and free, or the Rail falls short
     */
    public static Optional<Choice> choose(RailNetwork network, Arm placed, Collection<Arm> arms, int railHeld) {
        return arms.stream()
                .filter(arm -> !arm.equals(placed))
                .filter(arm -> !SpanRules.tooLong(placed, arm))
                .filter(arm -> network.spansAt(arm).size() < SpanRules.SPANS_PER_ARM)
                .min(Comparator.comparingLong((Arm arm) -> placed.distanceSquaredTo(arm)).thenComparing(Comparator.naturalOrder()))
                .map(arm -> new Choice(arm, SpanRules.cost(placed, arm)))
                .filter(choice -> choice.cost() <= railHeld);
    }
}
