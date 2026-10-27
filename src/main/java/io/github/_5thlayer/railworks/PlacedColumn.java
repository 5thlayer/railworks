// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.rail.Arm;
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
        if (!strings(level, pos, placer)) {
            return Optional.empty();
        }
        return AutoStringing.choose(network, LevelClearance.blocks(level), PylonColumn.arm(pos), armsInReach(level, pos),
                RailPurse.count(placer));
    }

    /**
     * The Arm the column would have strung to but for the grade or bend limit or a filled Clearance,
     * which the preview draws red, as {@link AutoStringing#refused} says.
     */
    public static Optional<Arm> refusedString(Level level, BlockPos pos, Player placer, RailNetwork network) {
        if (!strings(level, pos, placer)) {
            return Optional.empty();
        }
        return AutoStringing.refused(network, LevelClearance.blocks(level), PylonColumn.arm(pos), armsInReach(level, pos),
                RailPurse.count(placer));
    }

    private static boolean strings(Level level, BlockPos pos, Player placer) {
        return !placer.isSecondaryUseActive() && PylonColumn.startsColumn(level, pos);
    }

    private static List<Arm> armsInReach(Level level, BlockPos pos) {
        return PylonColumn.armsNear(level, pos, (int) Math.ceil(SpanRules.lengthLimit()));
    }
}
