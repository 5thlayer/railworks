// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.railworks.rail.Arm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A Pylon column is Pylon segments stacked, and its Arm is on the top segment. Being a segment or
 * the top is derived from the blocks, never stored, so no saved state can disagree with the world.
 */
public final class PylonColumn {

    private PylonColumn() {
    }

    public static boolean isPylon(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof PylonBlock;
    }

    /** The top segment of the column holding {@code pos}, where its Arm is. */
    public static BlockPos topOf(BlockGetter level, BlockPos pos) {
        BlockPos top = pos;
        while (isPylon(level, top.above())) {
            top = top.above();
        }
        return top;
    }

    /** Whether {@code pos} is a Pylon segment with no segment above it. */
    public static boolean isArm(BlockGetter level, BlockPos pos) {
        return isPylon(level, pos) && !isPylon(level, pos.above());
    }

    public static Arm arm(BlockPos pos) {
        return new Arm(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos block(Arm arm) {
        return new BlockPos(arm.x(), arm.y(), arm.z());
    }

    /**
     * Every Arm within {@code radius} blocks of {@code pos} other than one at {@code pos}, found
     * through the loaded chunks' block entities rather than by walking blocks. The server places by
     * this and the client's preview draws by it.
     */
    public static List<Arm> armsNear(Level level, BlockPos pos, int radius) {
        List<Arm> found = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(pos.getX() - radius);
             cx <= SectionPos.blockToSectionCoord(pos.getX() + radius); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(pos.getZ() - radius);
                 cz <= SectionPos.blockToSectionCoord(pos.getZ() + radius); cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos at = entity.getBlockPos();
                    if (!at.equals(pos) && isArm(level, at)) {
                        found.add(arm(at));
                    }
                }
            }
        }
        return found;
    }

    /**
     * Whether a Pylon segment at {@code pos}, placed or about to be, goes on top of a column and raises
     * its Arm: a segment below and none above. One with a segment above joins two columns instead.
     */
    public static boolean raisesArm(BlockGetter level, BlockPos pos) {
        return isPylon(level, pos.below()) && !isPylon(level, pos.above());
    }

    /** Whether a Pylon placed at {@code pos} starts a column of its own, with no segment above or below. */
    public static boolean startsColumn(BlockGetter level, BlockPos pos) {
        return !isPylon(level, pos.below()) && !isPylon(level, pos.above());
    }
}
