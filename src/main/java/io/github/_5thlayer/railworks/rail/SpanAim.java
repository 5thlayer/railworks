// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Optional;

/**
 * Which Span a player's line of sight is on, for placing a car on it. The Rail has no block to hit,
 * so the ray is measured against each Span's {@link RailCurve}, the line it is drawn along.
 *
 * <p>Coordinates are the Arms': the caller subtracts where the Rail rests on an Arm. Pure: no
 * Minecraft types.
 */
public final class SpanAim {

    /** The Span aimed at, and how far from its first end the closest point of the ray is. */
    public record Aim(Span span, double fromA, double distance) {
    }

    /** How many straight pieces a curve is measured as: a bend's sag between them is a hair's breadth. */
    static final int PIECES = 16;

    private SpanAim() {
    }

    /**
     * The Span nearest the ray from ({@code ox}, {@code oy}, {@code oz}) along the unit
     * ({@code dx}, {@code dy}, {@code dz}) for {@code reach} blocks, if one passes within
     * {@code tolerance} blocks of it.
     */
    public static Optional<Aim> nearest(RailNetwork network, double ox, double oy, double oz,
                                        double dx, double dy, double dz, double reach, double tolerance) {
        Aim best = null;
        for (Span span : network.all()) {
            Aim aim = measure(network, span, ox, oy, oz, dx, dy, dz, reach);
            if (aim.distance() <= tolerance && (best == null || aim.distance() < best.distance())) {
                best = aim;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Closest approach of the ray segment to the Span's curve, taken as {@link #PIECES} straight pieces. */
    private static Aim measure(RailNetwork network, Span span, double ox, double oy, double oz,
                               double dx, double dy, double dz, double reach) {
        RailCurve curve = network.curve(span);
        double length = span.length();
        Aim best = null;
        Place.Point from = curve.at(0.0).point();
        for (int i = 0; i < PIECES; i++) {
            Place.Point to = curve.at(length * (i + 1) / PIECES).point();
            double[] piece = measure(from, to, ox, oy, oz, dx, dy, dz, reach);
            if (best == null || piece[1] < best.distance()) {
                best = new Aim(span, length * (i + piece[0]) / PIECES, piece[1]);
            }
            from = to;
        }
        return best;
    }

    /**
     * Closest approach of the ray segment to the straight piece from {@code a} to {@code b}, both
     * clamped to their ends: how far along the piece, as a fraction, and how near.
     */
    private static double[] measure(Place.Point a, Place.Point b, double ox, double oy, double oz,
                                    double dx, double dy, double dz, double reach) {
        double ex = b.x() - a.x();
        double ey = b.y() - a.y();
        double ez = b.z() - a.z();
        double wx = ox - a.x();
        double wy = oy - a.y();
        double wz = oz - a.z();
        // Rays are P(s) = o + d s, pieces Q(t) = a + e t, with |d| = 1.
        double b1 = dx * ex + dy * ey + dz * ez;
        double c = ex * ex + ey * ey + ez * ez;
        double d = dx * wx + dy * wy + dz * wz;
        double e = ex * wx + ey * wy + ez * wz;
        double denominator = c - b1 * b1;
        // Minimising |w + d s - e t|^2: d.r = 0 gives s = b t - d.w and e.r = 0 gives c t = e.w + b s.
        double t = denominator < 1e-12 ? 0.0 : clamp((e - b1 * d) / denominator, 0.0, 1.0);
        double s = clamp(b1 * t - d, 0.0, reach);
        t = c < 1e-12 ? 0.0 : clamp((e + b1 * s) / c, 0.0, 1.0);
        double px = ox + dx * s - (a.x() + ex * t);
        double py = oy + dy * s - (a.y() + ey * t);
        double pz = oz + dz * s - (a.z() + ez * t);
        return new double[] {t, Math.sqrt(px * px + py * py + pz * pz)};
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
