// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * Whether Rail is a Straight leg, where a Station stands (ADR 0007) and its platform runs (ADR
 * 0008). The Rail bends through an Arm where the Span beyond it runs off line, and a Span bends
 * along all its length when either of its Arms does, so a Span is straight when each of its Arms is
 * a line's end or carries on in line. A Straight leg is also square to the blocks, along x or along
 * z, since what stands beside a stopped car is whole blocks: on a diagonal the block beside a car is
 * in the Rail's path a block on, and a car leaving would run through it. And it runs on into no bent
 * Span shorter than {@link Clearance#swingClearLength()}, whose cars swing into the row beside its
 * end. A leg may climb.
 *
 * <p>It agrees with how the Rail curves (#19), which bends a Span through an Arm toward the Span
 * beyond it there, an Arm holding two at most. Pure: no Minecraft types.
 */
public final class StraightLeg {

    private StraightLeg() {
    }

    /** Whether {@code span} runs without a bend: each of its Arms a line's end or carrying on in line. */
    static boolean straight(RailNetwork network, Span span) {
        return inLineAt(network, span.a(), span.b()) && inLineAt(network, span.b(), span.a());
    }

    /** Whether {@code span} runs along x or along z, seen from above. */
    static boolean squareToTheBlocks(Span span) {
        return span.a().x() == span.b().x() || span.a().z() == span.b().z();
    }

    /**
     * Whether {@code span} is a Straight leg: straight, square to the blocks, and clear of a bend's
     * swing, so the row beside it where hatches stand is outside every Clearance.
     */
    public static boolean is(RailNetwork network, Span span) {
        return squareToTheBlocks(span) && straight(network, span) && clearOfSwing(network, span);
    }

    /**
     * Whether each Span {@code span} runs on into, in line, is straight too or at least
     * {@link Clearance#swingClearLength()} long: on a shorter bent one a car's body, swinging round the
     * bend at its far Arm, reaches the row beside {@code span}'s end.
     */
    private static boolean clearOfSwing(RailNetwork network, Span span) {
        for (Arm end : new Arm[] {span.a(), span.b()}) {
            for (Span beyond : network.spansAt(end)) {
                if (!beyond.otherEnd(end).equals(span.otherEnd(end)) && !straight(network, beyond) && beyond.length() < Clearance.swingClearLength()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a stretch {@code length} long, centred at {@code centre}, lies wholly on a Straight
     * leg: every Span under it {@link #is}.
     */
    public static boolean under(RailNetwork network, Place centre, double length) {
        // Each half from the centre, so a body stopped at a line's end is not counted past its back.
        return centre.spansBack(network, length / 2.0).stream().allMatch(span -> is(network, span))
                && centre.turned().spansBack(network, length / 2.0).stream().allMatch(span -> is(network, span));
    }

    /** Whether the Rail runs on in line through {@code at}, coming from {@code from}, or ends there. */
    private static boolean inLineAt(RailNetwork network, Arm at, Arm from) {
        return network.spansAt(at).stream()
                .map(beyond -> beyond.otherEnd(at))
                .filter(far -> !far.equals(from))
                .allMatch(far -> SpanRules.inLine(from, at, far));
    }

    /**
     * Whether {@code station} stands by a Straight leg: every Span its stretch touches, a block either
     * way of its foot and over an Arm onto the Span beyond, a Straight leg. A Station is
     * placed only there, and Jade warns of one a Span strung later has bent. False for a Station by no
     * Rail.
     */
    public static boolean at(RailNetwork network, Station station) {
        return station.foot(network)
                .map(foot -> under(network, foot.place(), 2.0 * Station.STRETCH))
                .orElse(false);
    }
}
