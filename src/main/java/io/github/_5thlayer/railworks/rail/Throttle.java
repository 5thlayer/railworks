// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * What a driver asks of a Train for one tick: forward accelerates the way it faces, back
 * brakes and then reverses, as in Factorio's manual driving. With no driver it is {@link #NONE}.
 *
 * <p>Pure: no Minecraft types.
 */
public enum Throttle {
    NONE(0),
    FORWARD(1),
    BACK(-1);

    private final int sign;

    Throttle(int sign) {
        this.sign = sign;
    }

    /** +1 for the way it faces, -1 for the way it does not, 0 for neither. */
    public int sign() {
        return sign;
    }
}
