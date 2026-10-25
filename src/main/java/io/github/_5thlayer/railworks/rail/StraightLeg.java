// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * Whether Rail is a straight leg, where a Cargo car loads and unloads. The Rail bends through an
 * Arm where the Span beyond it runs off line, and a Span bends along all its length when either of
 * its Arms does, so a Span is straight when each of its Arms is a line's end or carries on in line.
 * Loading also wants the leg square to the blocks, along x or along z, since the hopper spots are
 * whole blocks beside the car: on a diagonal the block beside a car is in the Rail's path a block on,
 * and a car leaving would run through its hopper. A leg may climb.
 *
 * <p>Pure: no Minecraft types. Arms are whole blocks, so being in line is exact.
 */
public final class StraightLeg {

    private StraightLeg() {
    }

    /** Whether {@code span} runs without a bend: each of its Arms a line's end or carrying on in line. */
    public static boolean straight(RailNetwork network, Span span) {
        return inLineAt(network, span.a(), span.b()) && inLineAt(network, span.b(), span.a());
    }

    /** Whether {@code span} runs along x or along z, seen from above. */
    public static boolean squareToTheBlocks(Span span) {
        return span.a().x() == span.b().x() || span.a().z() == span.b().z();
    }

    /** Whether a Cargo car loads on {@code span}: straight, and square to the blocks. */
    public static boolean loads(RailNetwork network, Span span) {
        return squareToTheBlocks(span) && straight(network, span);
    }

    /**
     * Whether a body {@code length} long, centred at {@code centre}, stands wholly on Rail a Cargo car
     * loads on: every Span under it {@link #loads}.
     */
    public static boolean under(RailNetwork network, Place centre, double length) {
        Place front = centre.move(network, length / 2.0).place();
        return front.spansBack(network, length).stream().allMatch(span -> loads(network, span));
    }

    /** Whether {@code car} of {@code train} stands wholly on Rail a Cargo car loads on. */
    public static boolean under(RailNetwork network, Train train, Car car) {
        return under(network, train.placeOf(network, car), car.length());
    }

    /** Whether the Rail runs on in line through {@code at}, coming from {@code from}, or ends there. */
    private static boolean inLineAt(RailNetwork network, Arm at, Arm from) {
        long inX = at.x() - from.x();
        long inY = at.y() - from.y();
        long inZ = at.z() - from.z();
        for (Span beyond : network.spansAt(at)) {
            Arm far = beyond.otherEnd(at);
            if (far.equals(from)) {
                continue;
            }
            long outX = far.x() - at.x();
            long outY = far.y() - at.y();
            long outZ = far.z() - at.z();
            boolean parallel = inY * outZ - inZ * outY == 0 && inZ * outX - inX * outZ == 0 && inX * outY - inY * outX == 0;
            if (!parallel || inX * outX + inY * outY + inZ * outZ <= 0) {
                return false;
            }
        }
        return true;
    }
}
