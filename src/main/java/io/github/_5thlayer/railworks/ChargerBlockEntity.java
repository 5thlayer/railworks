// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.Optional;

import io.github._5thlayer.railworks.rail.ChargeSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EmptyEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A Charger's block entity, which holds nothing of its own: its buffer is its record in the level's
 * {@link LevelChargers}, saved with the level, so it charges an abstract Train's Locomotive with no
 * chunk loaded. Its energy handler, the same on every side, is insert only: any mod's cable puts FE
 * in, up to {@link ChargeSpec#chargerRate()} a tick and the buffer's room, and nothing takes it out
 * but a Train's Locomotive. A Creative Charger's takes nothing in and shows itself full. A Charger
 * placed with no player, as by a command, puts itself there as it loads.
 */
public final class ChargerBlockEntity extends BlockEntity {

    private final Energy energy = new Energy();

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(RailworksRegistries.CHARGER_ENTITY.get(), pos, state);
    }

    private boolean creative() {
        return getBlockState().getBlock() instanceof ChargerBlock block && block.creative();
    }

    /** Its record in the level's {@link LevelChargers}, put there if it has none; empty for a watcher, which has none. */
    public Optional<Chargers.Charger> charger() {
        if (!(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        Chargers chargers = LevelChargers.of(server);
        return Optional.of(chargers.at(worldPosition).orElseGet(() -> chargers.put(worldPosition, creative())));
    }

    /** Its energy handler, on every side: insert only, into its record's buffer. A watcher's holds nothing. */
    public EnergyHandler energy() {
        return level instanceof ServerLevel ? energy : EmptyEnergyHandler.INSTANCE;
    }

    /** Puts itself in the level's Chargers as it loads, if it is not there yet, as after a command placed it; a stale record of the other kind is replaced. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            LevelChargers.of(server).dropIfStale(server, worldPosition);
        }
        charger();
    }

    /** Broken, it leaves the level's Chargers, its buffer lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            LevelChargers.of(server).remove(pos);
        }
    }

    /**
     * Its record, as {@link #charger()} makes it, while it stands: empty once it is broken, so a cable
     * still holding its handler puts no record back where no Charger is.
     */
    private Optional<Chargers.Charger> standing() {
        return isRemoved() ? Optional.empty() : charger();
    }

    /**
     * The insert-only handler over its record's buffer. What it took this tick is counted against the
     * Charger's rate, and both that count and the buffer roll back with a transaction.
     */
    private final class Energy extends SnapshotJournal<Energy.State> implements EnergyHandler {

        /** The buffer, and the game tick the count was for and the FE taken in that tick. */
        private record State(long stored, long tick, long taken) {
        }

        private long tick = -1L;
        private long taken;

        @Override
        public long getAmountAsLong() {
            return standing().map(Chargers.Charger::shown).orElse(0L);
        }

        @Override
        public long getCapacityAsLong() {
            return ChargeSpec.chargerBuffer();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            Chargers.Charger charger = standing().orElse(null);
            if (charger == null || charger.creative() || level == null) {
                return 0;
            }
            long now = level.getGameTime();
            long takenNow = now == tick ? taken : 0L;
            long inserted = Math.min(amount, Math.min(ChargeSpec.chargerRate() - takenNow, charger.capacity() - charger.stored()));
            if (inserted <= 0L) {
                return 0;
            }
            updateSnapshots(transaction);
            charger.setStored(charger.stored() + inserted);
            tick = now;
            taken = takenNow + inserted;
            return (int) inserted;
        }

        /** Nothing takes FE out of a Charger but a Train's Locomotive beside it. */
        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }

        @Override
        protected State createSnapshot() {
            return new State(standing().map(Chargers.Charger::stored).orElse(0L), tick, taken);
        }

        @Override
        protected void revertToSnapshot(State snapshot) {
            standing().ifPresent(charger -> charger.setStored(snapshot.stored()));
            tick = snapshot.tick();
            taken = snapshot.taken();
        }

        @Override
        protected void onRootCommit(State originalState) {
            standing().ifPresent(Chargers.Charger::changed);
        }
    }
}
