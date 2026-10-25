// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.groundworks.Refusal;

/** Why a Pylon would not place. */
public enum RailworksRefusal implements Refusal {
    /** A Pylon aimed at a column whose next segment's position is occupied or past the build limit. */
    BLOCKED_TOP,
}
