// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A hatch: a container beside a Station's platform at a car's floor, joining that Station, which
 * trades with the Cargo car standing at its car spot or the leading car's place, and, a provider,
 * fuels the Locomotive standing there (ADR 0008). What it holds is its record in the level's
 * {@link LevelHatches}, filled and emptied by hoppers and pipes through its block
 * ({@link HatchBlockEntity}). It is placed a provider or a requester, as the {@link HatchItem}
 * placing it is, and drops as what it is; an empty-hand sneak-use switches it, keeping what it
 * holds, heard as a shulker box opening. Used with an item, a requester asks for a stack more of
 * that kind, another kind replacing its request; an empty hand clears its request, or takes a
 * provider's first stack. Each is told above the hotbar. Broken, it drops what it holds.
 */
public final class HatchBlock extends Block implements EntityBlock {

    public static final String BLOCK_NAME = "hatch";

    /** What is heard as a hatch, placed or in hand, switches either way: a shulker box opening. */
    public static final SoundEvent SWITCH_SOUND = SoundEvents.SHULKER_BOX_OPEN;

    /** Whether it is a provider or a requester, which its sides show; its record in {@link LevelHatches} decides. */
    public static final EnumProperty<HatchMode> MODE = EnumProperty.create("mode", HatchMode.class);

    public HatchBlock(BlockBehaviour.Properties props) {
        super(props.strength(2.0F).sound(SoundType.METAL));
        registerDefaultState(stateDefinition.any().setValue(MODE, HatchMode.PROVIDER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    /** Placed as the item placing it is, a provider or a requester. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        HatchMode mode = context.getItemInHand().getItem() instanceof HatchItem item ? item.mode() : HatchMode.PROVIDER;
        return defaultBlockState().setValue(MODE, mode);
    }

    /** Picked as the item it drops, its mode's. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(HatchItem.of(state.getValue(MODE)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HatchBlockEntity(pos, state);
    }

    /** Ticks on the server only, working out the item its watchers draw on its sides. */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != RailworksRegistries.HATCH_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((HatchBlockEntity) entity).serverTick((ServerLevel) world);
    }

    /** In the level's hatches at once, holding nothing; what a hatch removed with no side effects left there is dropped, not lost. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) {
            Hatches hatches = LevelHatches.of(server);
            hatches.remove(pos).ifPresent(stale -> stale.drop(server));
            hatches.put(pos, state.getValue(MODE));
        }
    }

    /**
     * Used with an item: a requester asks for a stack more of its kind, or one stack of a new kind; a
     * provider is filled by hoppers and pipes, not by hand. The item's own use never runs, so a block
     * is placed against it by sneaking, as against a chest.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof HatchBlockEntity entity)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        entity.hatch().ifPresent(hatch -> {
            if (hatch.mode() == HatchMode.REQUESTER) {
                player.sendOverlayMessage(HatchReport.requesting(Optional.of(hatch.ask(stack.getItem()))));
            } else {
                player.sendOverlayMessage(Component.translatable("message.railworks.hatch.provider_by_hoppers"));
            }
        });
        return InteractionResult.SUCCESS;
    }

    /**
     * With an empty hand: sneaking switches it between provider and requester, keeping what it holds;
     * otherwise it clears a requester's request, or gives a provider's first stack.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof HatchBlockEntity entity)) {
            return InteractionResult.PASS;
        }
        entity.hatch().ifPresent(hatch -> {
            if (player.isSecondaryUseActive()) {
                HatchMode mode = hatch.switchMode();
                level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_ALL);
                level.playSound(null, pos, SWITCH_SOUND, SoundSource.BLOCKS, 0.5F, 1.0F);
                player.sendOverlayMessage(Component.translatable(mode == HatchMode.REQUESTER
                        ? "message.railworks.hatch.now_requester" : "message.railworks.hatch.now_provider"));
            } else if (hatch.mode() == HatchMode.REQUESTER) {
                hatch.clearRequest();
                player.sendOverlayMessage(HatchReport.requesting(Optional.empty()));
            } else {
                giveFirstStack(hatch, player);
            }
        });
        return InteractionResult.SUCCESS;
    }

    private static void giveFirstStack(Hatches.Hatch hatch, Player player) {
        var contents = hatch.contents();
        for (int slot = 0; slot < contents.size(); slot++) {
            var resource = contents.getResource(slot);
            if (!resource.isEmpty()) {
                try (var transaction = Transaction.openRoot()) {
                    int taken = contents.extract(slot, resource, contents.getAmountAsInt(slot), transaction);
                    transaction.commit();
                    player.setItemInHand(InteractionHand.MAIN_HAND, resource.toStack(taken));
                }
                return;
            }
        }
    }
}
