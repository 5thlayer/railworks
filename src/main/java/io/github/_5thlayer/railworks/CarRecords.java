// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.railworks.rail.Car;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * What each car of a level holds (ADR 0008, amended in #6): a Cargo car's cargo and a Locomotive's
 * fuel slots, by the car's id, whole stacks with their components, in view or not. A car's view is
 * a window onto its record here, as a hatch's block is onto its own in {@link Hatches}. A car's
 * record is made with its view, keeps its id through coupling and uncoupling, and is taken out when
 * the car is gone for good, its view dropping what it held. {@link LevelCarRecords} saves it with
 * the level.
 */
public final class CarRecords {

    /** Every car's record as it is saved, in the order they were made. */
    public static final Codec<CarRecords> CODEC = Entry.CODEC.listOf()
            .xmap(CarRecords::read, records -> records.records.entrySet().stream()
                    .map(e -> new Entry(e.getKey(), e.getValue().getItems())).toList());

    private final Map<UUID, Contents> records = new LinkedHashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, what a car holds too, so the saved data that holds it is saved. */
    void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** The record of the car with this id; empty for one whose view has not yet made it, or a car gone. */
    public Optional<Contents> of(UUID car) {
        return Optional.ofNullable(records.get(car));
    }

    /** The record of the car with this id, made holding nothing in {@code slots} slots if it has none. */
    Contents ensure(UUID car, int slots) {
        Contents record = records.get(car);
        return record != null ? record : make(car, slots, List.of());
    }

    /**
     * Takes in what a car's entity saved on itself before the level kept its record, slot by slot,
     * as a record of {@code slots} slots: only once, for a car with no record yet, so an entity loaded
     * again from a chunk saved before its record was keeps the record's contents. Returns whether it
     * took them.
     */
    boolean handOver(UUID car, int slots, List<ItemStack> saved) {
        if (records.containsKey(car)) {
            return false;
        }
        make(car, slots, saved);
        return true;
    }

    /** The record of the car with this id, taken out as the car is gone for good, so its view drops what it held. */
    public Optional<Contents> remove(UUID car) {
        Optional<Contents> record = Optional.ofNullable(records.remove(car));
        record.ifPresent(removed -> onChange.run());
        return record;
    }

    private Contents make(UUID car, int slots, List<ItemStack> saved) {
        Contents record = new Contents(slots, saved, this);
        records.put(car, record);
        onChange.run();
        return record;
    }

    private static CarRecords read(List<Entry> entries) {
        CarRecords records = new CarRecords();
        entries.forEach(entry -> records.records.put(entry.car(), new Contents(entry.stacks().size(), entry.stacks(), records)));
        return records;
    }

    /** One car's slots, which mark the records to be saved whenever what they hold changes. */
    public static final class Contents extends SimpleContainer {

        private final CarRecords records;

        /** {@code slots} slots holding {@code stacks} in order, as many as fit, the rest empty. */
        private Contents(int slots, List<ItemStack> stacks, CarRecords records) {
            super(slots);
            this.records = records;
            for (int slot = 0; slot < Math.min(slots, stacks.size()); slot++) {
                getItems().set(slot, stacks.get(slot));
            }
        }

        @Override
        public void setChanged() {
            super.setChanged();
            records.onChange.run();
        }
    }

    /** A record as it is saved: its slots in order, the empty ones too, so what was put where stays there. */
    private record Entry(UUID car, List<ItemStack> stacks) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Car.ID_CODEC.fieldOf("car").forGetter(Entry::car),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("stacks").forGetter(Entry::stacks)
        ).apply(i, Entry::new));
    }
}
