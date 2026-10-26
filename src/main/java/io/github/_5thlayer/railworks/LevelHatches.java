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
import io.github._5thlayer.railworks.rail.HatchRequest;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A level's hatches, saved with it beside its {@link LevelStations} (ADR 0008): where each stands,
 * whether it is a provider or a requester, what a requester asks for and what it holds, so an
 * abstract Train loads and unloads one with no chunk loaded. A hatch's block is a view onto its
 * record here, its item handler these very slots. Which Station a hatch joins is not kept but asked
 * afresh ({@link #joined}), so one whose Station is broken or whose platform is bent away is a plain
 * container until a platform covers it again.
 */
public final class LevelHatches extends SavedData {

    /** How many slots a hatch holds unless {@code railworks-server.toml} says otherwise: a chest's. */
    public static final int DEFAULT_SLOTS = 27;

    private static volatile int slots = DEFAULT_SLOTS;

    private static final Codec<LevelHatches> CODEC = Entry.CODEC.listOf()
            .xmap(LevelHatches::read, data -> data.hatches.values().stream().map(Entry::of).toList())
            .fieldOf("hatches")
            .codec();

    public static final SavedDataType<LevelHatches> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "hatches"),
            LevelHatches::new, CODEC);

    private final Map<BlockPos, Hatch> hatches = new LinkedHashMap<>();

    public LevelHatches() {
    }

    public static LevelHatches of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads: how many slots a hatch placed from now on holds. */
    public static void configure(int slots) {
        if (slots < 1) {
            throw new IllegalArgumentException("hatch slots " + slots);
        }
        LevelHatches.slots = slots;
    }

    /** How many slots a hatch placed now holds. */
    public static int slots() {
        return slots;
    }

    /** Puts a hatch at {@code pos}, holding nothing and asking for nothing, in place of any that was there. */
    public Hatch put(BlockPos pos, HatchMode mode) {
        Hatch hatch = new Hatch(pos.immutable(), mode, Optional.empty(), NonNullList.withSize(slots, ItemStack.EMPTY), this);
        hatches.put(hatch.pos, hatch);
        setDirty();
        return hatch;
    }

    /** The hatch at {@code pos}, taken out, so its block drops what it held; empty if there was none. */
    public Optional<Hatch> remove(BlockPos pos) {
        Optional<Hatch> hatch = Optional.ofNullable(hatches.remove(pos));
        hatch.ifPresent(removed -> setDirty());
        return hatch;
    }

    /** The hatch at {@code pos}, if there is one. */
    public Optional<Hatch> at(BlockPos pos) {
        return Optional.ofNullable(hatches.get(pos));
    }

    public Collection<Hatch> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(hatches.values()));
    }

    /**
     * The hatches of {@code level} that join {@code station}, asked afresh. A record whose block is
     * loaded but no longer a hatch, as after a command replaced it with no side effects, is stale and
     * taken out, its contents lost with the block as a chest's are.
     */
    public List<Hatch> joinedTo(ServerLevel level, Station station) {
        List<Hatch> joined = new ArrayList<>();
        for (Hatch hatch : all()) {
            BlockPos pos = hatch.pos();
            if (Math.abs(pos.getX() - station.x()) > Platform.REACH || Math.abs(pos.getY() - station.y()) > Platform.REACH
                    || Math.abs(pos.getZ() - station.z()) > Platform.REACH) {
                continue;
            }
            if (level.isLoaded(pos) && !level.getBlockState(pos).is(RailworksRegistries.HATCH.get())) {
                remove(pos);
                continue;
            }
            if (joined(level, pos).filter(at -> at.station().samePlace(station)).isPresent()) {
                joined.add(hatch);
            }
        }
        return joined;
    }

    /** Which Station of {@code level} a hatch at {@code pos} joins and at which car spot, asked afresh; empty for none. */
    public static Optional<Platform.Joined> joined(ServerLevel level, BlockPos pos) {
        return HatchItem.joinedAt(level, pos);
    }

    /** Drops everything {@code hatch} holds at the centre of its block, as the block is removed. */
    public static void drop(Level level, Hatch hatch) {
        ItemStacksResourceHandler contents = hatch.contents();
        BlockPos pos = hatch.pos();
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack stack = contents.getResource(slot).toStack(contents.getAmountAsInt(slot));
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    private static LevelHatches read(List<Entry> entries) {
        LevelHatches data = new LevelHatches();
        entries.forEach(entry -> data.hatches.put(entry.pos(), entry.hatch(data)));
        return data;
    }

    /**
     * One hatch: where it stands, which never changes; whether it is a provider or a requester, and a
     * requester's request; and what it holds. It has no owner.
     */
    public static final class Hatch {

        private final BlockPos pos;
        private HatchMode mode;
        private Optional<HatchRequest<Item>> request;
        private final Contents contents;
        private final LevelHatches data;

        private Hatch(BlockPos pos, HatchMode mode, Optional<HatchRequest<Item>> request, NonNullList<ItemStack> stacks,
                      LevelHatches data) {
            this.pos = pos;
            this.mode = mode;
            this.request = request;
            this.contents = new Contents(stacks, data);
            this.data = data;
        }

        public BlockPos pos() {
            return pos;
        }

        public HatchMode mode() {
            return mode;
        }

        /** What a requester asks for; empty for one asking for nothing, and for a provider, which never asks. */
        public Optional<HatchRequest<Item>> request() {
            return mode == HatchMode.REQUESTER ? request : Optional.empty();
        }

        /** What it holds, which its block's item handler fills and empties, and a Train too. */
        public ItemStacksResourceHandler contents() {
            return contents;
        }

        /** Switches it between provider and requester, keeping what it holds and asking for nothing. Returns what it is now. */
        public HatchMode switchMode() {
            mode = mode.other();
            request = Optional.empty();
            data.setDirty();
            return mode;
        }

        /**
         * A requester asks for a stack more of {@code item}'s kind, up to as many stacks as it has slots;
         * another kind replaces the request with one stack. Returns what it asks for now.
         *
         * @throws IllegalStateException for a provider, which never asks
         */
        public HatchRequest<Item> ask(Item item) {
            if (mode != HatchMode.REQUESTER) {
                throw new IllegalStateException("a provider asks for nothing");
            }
            HatchRequest<Item> asked = HatchRequest.ask(request, item, contents.size());
            request = Optional.of(asked);
            data.setDirty();
            return asked;
        }

        /** Asks for nothing. */
        public void clearRequest() {
            request = Optional.empty();
            data.setDirty();
        }
    }

    /** A hatch's slots, which mark the level's hatches to be saved whenever what they hold changes. */
    private static final class Contents extends ItemStacksResourceHandler {

        private final LevelHatches data;

        Contents(NonNullList<ItemStack> stacks, LevelHatches data) {
            super(stacks);
            this.data = data;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            data.setDirty();
        }
    }

    /** A hatch as it is saved: its slots in order, the empty ones too, so it keeps as many as it was placed with. */
    private record Entry(BlockPos pos, HatchMode mode, Optional<HatchRequest<Item>> request, List<ItemStack> stacks) {

        private static final Codec<HatchRequest<Item>> REQUEST_CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(HatchRequest::item),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("stacks").forGetter(HatchRequest::stacks)
        ).apply(i, HatchRequest::new));

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                HatchMode.CODEC.fieldOf("mode").forGetter(Entry::mode),
                REQUEST_CODEC.optionalFieldOf("request").forGetter(Entry::request),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("stacks").forGetter(Entry::stacks)
        ).apply(i, Entry::new));

        static Entry of(Hatch hatch) {
            return new Entry(hatch.pos, hatch.mode, hatch.request, hatch.contents.copyToList());
        }

        Hatch hatch(LevelHatches data) {
            NonNullList<ItemStack> slots = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < stacks.size(); slot++) {
                slots.set(slot, stacks.get(slot));
            }
            return new Hatch(pos, mode, request, slots, data);
        }
    }
}
