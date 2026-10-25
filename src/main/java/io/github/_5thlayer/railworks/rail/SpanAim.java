// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.Optional;

/**
 * Which Span a player's line of sight is on, for placing a car on it. A Span is a straight segment
 * between its Arms, and the Rail has no block to hit, so the ray is measured against the segments.
 *
 * <p>Coordinates are the Arms': the caller subtracts where the Rail rests on an Arm. Pure: no
 * Minecraft types.
 */
public final class SpanAim {

    /** The Span aimed at, and how far from its first end the closest point of the ray is. */
    public record Aim(Span span, double fromA, double distance) {
    }

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
            Aim aim = measure(span, ox, oy, oz, dx, dy, dz, reach);
            if (aim.distance() <= tolerance && (best == null || aim.distance() < best.distance())) {
                best = aim;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Closest approach of the ray segment to the Span segment, both clamped to their ends. */
    private static Aim measure(Span span, double ox, double oy, double oz, double dx, double dy, double dz,
                               double reach) {
        Arm a = span.a();
        double ex = span.b().x() - a.x();
        double ey = span.b().y() - a.y();
        double ez = span.b().z() - a.z();
        double length = span.length();
        double wx = ox - a.x();
        double wy = oy - a.y();
        double wz = oz - a.z();
        // Rays are P(s) = o + d s, Spans Q(t) = a + e t, with |d| = 1 and |e| = length.
        double b = dx * ex + dy * ey + dz * ez;
        double c = length * length;
        double d = dx * wx + dy * wy + dz * wz;
        double e = ex * wx + ey * wy + ez * wz;
        double denominator = c - b * b;
        // Minimising |w + d s - e t|^2: d.r = 0 gives s = b t - d.w and e.r = 0 gives c t = e.w + b s.
        double t = denominator < 1e-12 ? 0.0 : clamp((e - b * d) / denominator, 0.0, 1.0);
        double s = clamp(b * t - d, 0.0, reach);
        t = clamp((e + b * s) / c, 0.0, 1.0);
        double px = ox + dx * s - (a.x() + ex * t);
        double py = oy + dy * s - (a.y() + ey * t);
        double pz = oz + dz * s - (a.z() + ez * t);
        return new Aim(span, t * length, Math.sqrt(px * px + py * py + pz * pz));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
