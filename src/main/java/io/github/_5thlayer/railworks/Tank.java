// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.Logistics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A tank of one fluid, the fluid handler of a Fluid hatch's record in {@link Hatches} and of a Fluid
 * car's in {@link CarRecords}: one slot, holding up to its capacity in millibuckets, which marks its
 * record to be saved whenever what it holds changes. A hatch of items and a Cargo car or Locomotive
 * have one of no capacity, which takes nothing.
 */
public final class Tank extends FluidStacksResourceHandler {

    private final Runnable onChange;

    /** A tank of {@code capacity} millibuckets holding {@code held}, as much of it as fits. */
    Tank(int capacity, FluidStack held, Runnable onChange) {
        super(NonNullList.of(FluidStack.EMPTY, held.copyWithAmount(Math.min(held.getAmount(), capacity))), capacity);
        this.onChange = onChange;
    }

    /** {@code buckets} buckets, in millibuckets, a tank's capacity. */
    static int capacityOf(int buckets) {
        return Math.toIntExact(buckets * Logistics.MILLIBUCKETS_PER_BUCKET);
    }

    /**
     * {@code player} uses the fluid container in {@code hand} on it, at {@code pos}: an empty bucket
     * fills from it, a filled one pours in, one bucket at a time, the fluid permitting, as a bucket
     * sounds. A container that would move more than a bucket at once moves nothing. Whether anything
     * moved.
     */
    public boolean useByHand(Player player, InteractionHand hand, BlockPos pos) {
        int before = amount();
        try (var transaction = Transaction.openRoot()) {
            boolean moved = FluidUtil.interactWithFluidHandler(player, hand, pos, this, transaction);
            if (!moved || Math.abs(amount() - before) > Logistics.MILLIBUCKETS_PER_BUCKET) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /** How many millibuckets it holds when full. */
    public int capacity() {
        return capacity;
    }

    /** The fluid it holds; {@link FluidResource#EMPTY} for none. */
    public FluidResource fluid() {
        return getResource(0);
    }

    /** How many millibuckets it holds. */
    public int amount() {
        return getAmountAsInt(0);
    }

    /** What it holds, as a stack. */
    public FluidStack held() {
        return fluid().toStack(amount());
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChange.run();
    }
}
