// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.Refusal;

/** Why a Pylon, a car, a Station, a hatch or a Charger would not place. */
public enum RailworksRefusal implements Refusal {
    /** A Pylon aimed at a column whose next segment's position is occupied or past the build limit. */
    BLOCKED_TOP,
    /**
     * A Pylon segment stacked on an Arm that would raise one of its Spans past the length, grade or
     * bend limit or into a block, or would stand in another Span's Clearance.
     */
    RAISES_SPAN,
    /** A car aimed at no Span. */
    OFF_SPAN,
    /** A car aimed at a Span a Train already holds, other than where it would couple: one Train holds a Span at a time. */
    SPAN_TAKEN,
    /**
     * A car whose coupling would leave a Locomotive between other cars of its Train: Locomotives
     * stand only at a Train's ends ({@code Train.takes}).
     */
    LOCOMOTIVE_BETWEEN_CARS,
    /** A car that would sit in a block, so the Span's Clearance is not clear. */
    BLOCKED_CAR,
    /** A Station by no Rail: not beside a Span. */
    STATION_OFF_RAIL,
    /** A Station under the Rail, which has no side and so would serve neither way. */
    STATION_UNDER_RAIL,
    /** A Station in the Clearance of the Span it would serve, where a passing car would hit it. */
    STATION_IN_CLEARANCE,
    /** A Station by Rail that bends or runs on a diagonal along its stretch, where no Cargo car would load. */
    STATION_NOT_STRAIGHT,
    /** A Station whose block or platform would overlap another Station's, so a hatch beside it would belong to two. */
    STATION_OVERLAPS,
    /** A hatch beside no car spot of a Station's platform, where it would join no Station. */
    HATCH_OFF_PLATFORM,
    /** A Charger beside no car spot of a Station's platform, where it would join no Station. */
    CHARGER_OFF_PLATFORM,
}
