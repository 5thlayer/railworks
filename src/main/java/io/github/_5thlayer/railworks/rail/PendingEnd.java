// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

/**
 * Whether a Span's first Arm is still held by the Rail in a player's hand.
 *
 * <p>Pure: no Minecraft types.
 */
public final class PendingEnd {

    /**
     * The player holding the end.
     *
     * @param interactionRange how far the player can click; the end is held to the longest Span plus this
     * @param railInMainHand   the Rail that holds the end is still in the main hand
     * @param sameDimension    the player is in the Arm's dimension
     */
    public record Holder(double x, double y, double z, double interactionRange,
                         boolean railInMainHand, boolean sameDimension) {
    }

    private PendingEnd() {
    }

    /** @param armStanding the anchor is still the top of a Pylon column */
    public static boolean stillHeld(Arm anchor, boolean armStanding, Holder holder) {
        double dx = holder.x() - (anchor.x() + 0.5);
        double dy = holder.y() - (anchor.y() + 0.5);
        double dz = holder.z() - (anchor.z() + 0.5);
        double reach = SpanRules.lengthLimit() + holder.interactionRange();
        boolean inReach = dx * dx + dy * dy + dz * dz <= reach * reach;
        return armStanding && holder.railInMainHand() && holder.sameDimension() && inReach;
    }
}
