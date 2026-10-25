// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.Refusal;

/** Why a Pylon, a car or a Station would not place. */
public enum RailworksRefusal implements Refusal {
    /** A Pylon aimed at a column whose next segment's position is occupied or past the build limit. */
    BLOCKED_TOP,
    /** A car aimed at no Span. */
    OFF_SPAN,
    /** A car aimed at a Span a Train already holds, other than where it would couple: one Train holds a Span at a time. */
    SPAN_TAKEN,
    /** A car that would sit in a block, so the Span's Clearance is not clear. */
    BLOCKED_CAR,
    /** A Station by no Rail: neither beside a Span nor under it. */
    STATION_OFF_RAIL,
    /** A Station in the Clearance of the Span it would serve, where a passing car would hit it. */
    STATION_IN_CLEARANCE,
}
