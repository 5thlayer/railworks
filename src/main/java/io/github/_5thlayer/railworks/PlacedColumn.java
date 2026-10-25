// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import io.github._5thlayer.railworks.rail.AutoStringing;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.SpanRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What a player placing a new column at a spot would string, as the world sees it. The server places
 * by it and the client's preview draws it, each handing its own copy of the Spans, so the two ask one
 * rule.
 */
public final class PlacedColumn {

    private PlacedColumn() {
    }

    /**
     * @return the Span to string, or empty for a sneaking placer, a segment added to a column, no
     *         free Arm in reach, or too little Rail
     */
    public static Optional<AutoStringing.Choice> autoString(Level level, BlockPos pos, Player placer, RailNetwork network) {
        if (placer.isSecondaryUseActive() || !PylonColumn.startsColumn(level, pos)) {
            return Optional.empty();
        }
        int reach = (int) Math.ceil(SpanRules.lengthLimit());
        return AutoStringing.choose(network, PylonColumn.arm(pos), PylonColumn.armsNear(level, pos, reach),
                RailPurse.count(placer));
    }
}
