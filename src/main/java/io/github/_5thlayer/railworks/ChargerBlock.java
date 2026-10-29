// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A Charger (ADR 0005, amended in #9): a block beside a Station's platform, two off the Rail at
 * a Cargo car's chests' height, joining that Station as a hatch does, which takes FE from any mod's
 * cable through its energy handler ({@link ChargerBlockEntity}) into a buffer, its record in the level's
 * {@link LevelChargers}, and charges the Electric Locomotive of any Train at rest beside it. It holds
 * no items and neither offers nor asks. The Creative Charger is one that never runs out, needs no FE
 * in and fills a Locomotive at once. Broken, it loses its buffer.
 */
public final class ChargerBlock extends Block implements EntityBlock {

    public static final String BLOCK_NAME = "charger";
    public static final String CREATIVE_NAME = "creative_charger";

    private final boolean creative;

    public ChargerBlock(BlockBehaviour.Properties props, boolean creative) {
        super(props.strength(2.0F).sound(SoundType.COPPER));
        this.creative = creative;
    }

    /** Whether it is a Creative Charger. */
    public boolean creative() {
        return creative;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargerBlockEntity(pos, state);
    }

    /** In the level's Chargers at once, its buffer empty, in place of any stale record there. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) {
            LevelChargers.of(server).put(pos, creative);
        }
    }
}
