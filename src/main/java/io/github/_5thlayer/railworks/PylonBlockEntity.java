// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Pylon segment's block entity, which holds nothing: it is how the client draws the Spans off the
 * column's Arm. Only the top segment's draws.
 */
public final class PylonBlockEntity extends BlockEntity {

    public PylonBlockEntity(BlockPos pos, BlockState state) {
        super(RailworksRegistries.PYLON_ENTITY.get(), pos, state);
    }
}
