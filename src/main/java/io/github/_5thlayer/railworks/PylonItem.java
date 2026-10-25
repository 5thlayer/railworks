// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** A Pylon in the hand: one segment, which stacks onto a column when used on one ({@link PylonBlock}). */
public final class PylonItem extends BlockItem {

    public PylonItem(Block block, Item.Properties properties) {
        super(block, properties);
    }
}
