// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.BurnerGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Generator's block entity: its one fuel slot, which hoppers and pipes fill from any side
 * with furnace fuel that leaves nothing behind when burnt, and its {@link Generator}, which burns it
 * at the Burner Locomotive's joules per burn tick into a small FE buffer. Each tick it gives what it
 * holds to the energy handlers on its six sides, so a Charger set against it charges with no cable;
 * its own energy handler is extract only, for any mod's cable to take from. Its menu shows the slot
 * and a burn flame.
 */
public final class BurnerGeneratorBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    /** The fuel slot. */
    public static final int FUEL = 0;
    /** The menu's data: how far through the fuel lit it is, in thousandths. */
    public static final int DATA_LIT = 0;
    public static final int DATA_COUNT = 1;
    /** The flame's steps, a thousand to a full flame. */
    public static final int LIT_STEPS = 1_000;

    private static final int[] SLOTS = {FUEL};

    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final BurnerGenerator generator = new BurnerGenerator();
    private final Energy energy = new Energy();
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            long capacity = generator.fuel().gaugeCapacity();
            return index == DATA_LIT && capacity > 0L ? (int) (generator.fuel().storedJoules() * LIT_STEPS / capacity) : 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public BurnerGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(RailworksRegistries.BURNER_GENERATOR_ENTITY.get(), pos, state);
    }

    /** What it holds to give, in FE. */
    public long stored() {
        return generator.stored();
    }

    /** Its energy handler, on every side: extract only. */
    public EnergyHandler energy() {
        return energy;
    }

    /** Whether {@code stack} may go in its fuel slot: furnace fuel that leaves nothing behind when burnt, as a provider gives a Locomotive. */
    public static boolean burns(Level level, ItemStack stack) {
        return LocomotiveEntity.fuelValue(level, stack) > 0L && stack.getCraftingRemainder() == null;
    }

    /** One tick on the server: it burns what it can into its buffer, lit while it burns, and gives its FE to the blocks beside it. */
    void serverTick(ServerLevel level) {
        boolean burnt = generator.tick(this::ignite);
        if (burnt) {
            setChanged();
        }
        BlockState state = getBlockState();
        if (state.getValue(BurnerGeneratorBlock.LIT) != burnt) {
            level.setBlock(worldPosition, state.setValue(BurnerGeneratorBlock.LIT, burnt), Block.UPDATE_ALL);
        }
        push(level);
    }

    /** Burns one fuel item from its slot and says what it was worth; 0 for none, or for one that would leave something behind. */
    private long ignite() {
        ItemStack fuel = items.get(FUEL);
        if (level == null || !burns(level, fuel)) {
            return 0L;
        }
        long joules = LocomotiveEntity.fuelValue(level, fuel);
        fuel.shrink(1);
        return joules;
    }

    /** Gives what it holds to the energy handlers on its six sides, in turn. */
    private void push(ServerLevel level) {
        for (Direction side : Direction.values()) {
            if (generator.stored() <= 0L) {
                return;
            }
            EnergyHandler beside = level.getCapability(Capabilities.Energy.BLOCK, worldPosition.relative(side), side.getOpposite());
            if (beside == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int given = beside.insert((int) Math.min(Integer.MAX_VALUE, generator.stored()), transaction);
                transaction.commit();
                if (given > 0) {
                    generator.extract(given);
                    setChanged();
                }
            }
        }
    }

    // ---- the fuel slot ----

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.railworks.burner_generator");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new BurnerGeneratorMenu(containerId, inventory, this, data);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return level != null && burns(level, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    /** Nothing comes out of the fuel slot but by hand. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    // ---- saving ----

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        generator.load(input.getLongOr("stored", 0L), input.getLongOr("joules", 0L),
                input.getLongOr("fuel", 0L), input.getLongOr("last_lit", 0L));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putLong("stored", generator.stored());
        output.putLong("joules", generator.joules());
        output.putLong("fuel", generator.fuel().storedJoules());
        output.putLong("last_lit", generator.fuel().lastLitJoules());
    }

    /** The extract-only handler over its buffer, which rolls back with a transaction. */
    private final class Energy extends SnapshotJournal<Long> implements EnergyHandler {

        @Override
        public long getAmountAsLong() {
            return generator.stored();
        }

        @Override
        public long getCapacityAsLong() {
            return BurnerGenerator.capacity();
        }

        /** Nothing puts FE into a Burner Generator: it makes its own. */
        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            int extracted = (int) Math.min(amount, generator.stored());
            if (extracted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            generator.extract(extracted);
            return extracted;
        }

        @Override
        protected Long createSnapshot() {
            return generator.stored();
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            generator.load(snapshot, generator.joules(), generator.fuel().storedJoules(), generator.fuel().lastLitJoules());
        }

        @Override
        protected void onRootCommit(Long originalState) {
            setChanged();
        }
    }
}
