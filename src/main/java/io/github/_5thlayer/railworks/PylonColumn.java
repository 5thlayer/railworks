// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.Arm;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

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
}
