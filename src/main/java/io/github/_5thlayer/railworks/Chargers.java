// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.railworks.rail.ChargeSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Every Charger of a level (ADR 0005, amended in #9): where each stands, whether it is a Creative
 * Charger, and the FE its buffer holds, so a Charger charges an abstract Train's Electric Locomotive
 * with no chunk loaded, as a hatch keeps its items in {@link Hatches}. A Charger's block is a view
 * onto its record here, its energy handler this very buffer. Which Station it joins is asked afresh
 * ({@link PlatformsNear#joined}), as a hatch's is. {@link LevelChargers} saves it with the level.
 */
public final class Chargers {

    /** Every Charger as it is saved, in the order they were placed. */
    public static final Codec<Chargers> CODEC = Entry.CODEC.listOf()
            .xmap(Chargers::read, chargers -> chargers.chargers.values().stream().map(Entry::of).toList());

    private final Map<BlockPos, Charger> chargers = new LinkedHashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, what a buffer holds too, so the saved data that holds it is saved. */
    void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Puts a Charger at {@code pos}, its buffer empty, in place of any that was there. */
    public Charger put(BlockPos pos, boolean creative) {
        Charger charger = new Charger(pos.immutable(), creative, 0L, this);
        chargers.put(charger.pos, charger);
        onChange.run();
        return charger;
    }

    /** The Charger at {@code pos}, taken out, its buffer lost with it; empty if there was none. */
    public Optional<Charger> remove(BlockPos pos) {
        Optional<Charger> charger = Optional.ofNullable(chargers.remove(pos));
        charger.ifPresent(removed -> onChange.run());
        return charger;
    }

    /** The Charger at {@code pos}, if there is one. */
    public Optional<Charger> at(BlockPos pos) {
        return Optional.ofNullable(chargers.get(pos));
    }

    /**
     * Takes out the record at {@code pos} if it is stale, its block loaded but no longer the Charger
     * it was, as after a command replaced it with no side effects. Returns whether it was taken out.
     */
    public boolean dropIfStale(Level level, BlockPos pos) {
        Charger charger = chargers.get(pos);
        if (charger == null || !level.isLoaded(pos)
                || level.getBlockState(pos).getBlock() instanceof ChargerBlock block && block.creative() == charger.creative) {
            return false;
        }
        remove(pos);
        return true;
    }

    /** Every Charger, in the order they were placed. */
    public Collection<Charger> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(chargers.values()));
    }

    private static Chargers read(List<Entry> entries) {
        Chargers chargers = new Chargers();
        entries.forEach(entry -> chargers.chargers.put(entry.pos(),
                new Charger(entry.pos(), entry.creative(), Math.max(0L, entry.stored()), chargers)));
        return chargers;
    }

    /**
     * One Charger: where it stands and whether it is a Creative Charger, which never change, and the
     * FE its buffer holds. A Creative Charger's buffer is always full and never runs out.
     */
    public static final class Charger {

        private final BlockPos pos;
        private final boolean creative;
        private long stored;
        private final Chargers chargers;

        private Charger(BlockPos pos, boolean creative, long stored, Chargers chargers) {
            this.pos = pos;
            this.creative = creative;
            this.stored = stored;
            this.chargers = chargers;
        }

        public BlockPos pos() {
            return pos;
        }

        public boolean creative() {
            return creative;
        }

        /** FE it has to give: its buffer's, or for a Creative Charger, without end. */
        public long held() {
            return creative ? Long.MAX_VALUE : stored;
        }

        /** FE its buffer shows: what it holds, a Creative Charger's always its whole capacity. */
        public long shown() {
            return creative ? capacity() : stored;
        }

        /** FE its buffer holds at most. */
        public long capacity() {
            return ChargeSpec.chargerBuffer();
        }

        /** Takes {@code fe} out of its buffer, as far as it holds them; a Creative Charger's never runs out. */
        public void take(long fe) {
            if (!creative && fe > 0L) {
                stored = Math.max(0L, stored - fe);
                chargers.onChange.run();
            }
        }

        /** What its buffer holds, set by its energy handler and put back as a transaction rolls back; saved once one commits. */
        long stored() {
            return stored;
        }

        void setStored(long stored) {
            this.stored = Math.max(0L, stored);
        }

        /** Marks it to be saved, as a transaction filling it commits. */
        void changed() {
            chargers.onChange.run();
        }
    }

    /** A Charger as it is saved. */
    private record Entry(BlockPos pos, boolean creative, long stored) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.BOOL.optionalFieldOf("creative", false).forGetter(Entry::creative),
                Codec.LONG.optionalFieldOf("stored", 0L).forGetter(Entry::stored)
        ).apply(i, Entry::new));

        static Entry of(Charger charger) {
            return new Entry(charger.pos, charger.creative, charger.stored);
        }
    }
}
