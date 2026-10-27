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
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Every hatch of a level (ADR 0008): where each stands, whether it is a provider or a requester,
 * what a requester asks for and what it holds, so an abstract Train loads and unloads one with no
 * chunk loaded. A hatch's block is a view onto its record here, its item handler these very slots.
 * Which Station a hatch joins is not kept but asked afresh ({@link PlatformsNear#joined}), so one
 * whose Station is broken or whose platform is bent away is a plain container until a platform
 * covers it again. {@link LevelHatches} saves it with the level.
 */
public final class Hatches {

    /** How many slots a hatch holds unless {@code railworks-server.toml} says otherwise: a chest's. */
    public static final int DEFAULT_SLOTS = 27;

    /** A request as it is saved: the item by its id, and the stacks. */
    public static final Codec<HatchRequest<Item>> REQUEST_CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(HatchRequest::item),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("stacks").forGetter(HatchRequest::stacks)
    ).apply(i, HatchRequest::new));

    /** A request as it is sent to a client. */
    public static final StreamCodec<RegistryFriendlyByteBuf, HatchRequest<Item>> REQUEST_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), HatchRequest::item,
            ByteBufCodecs.VAR_INT, HatchRequest::stacks,
            HatchRequest::new);

    /** Every hatch as it is saved, in the order they were placed. */
    public static final Codec<Hatches> CODEC = Entry.CODEC.listOf()
            .xmap(Hatches::read, hatches -> hatches.hatches.values().stream().map(Entry::of).toList());

    private static volatile int slots = DEFAULT_SLOTS;

    private final Map<BlockPos, Hatch> hatches = new LinkedHashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, what a hatch holds too, so the saved data that holds it is saved. */
    void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads: how many slots a hatch placed from now on holds. */
    public static void configure(int slots) {
        if (slots < 1) {
            throw new IllegalArgumentException("hatch slots " + slots);
        }
        Hatches.slots = slots;
    }

    /** How many slots a hatch placed now holds. */
    public static int slots() {
        return slots;
    }

    /** Puts a hatch at {@code pos}, holding nothing and asking for nothing, in place of any that was there. */
    public Hatch put(BlockPos pos, HatchMode mode) {
        Hatch hatch = new Hatch(pos.immutable(), mode, Optional.empty(), NonNullList.withSize(slots, ItemStack.EMPTY), this);
        hatches.put(hatch.pos, hatch);
        onChange.run();
        return hatch;
    }

    /** The hatch at {@code pos}, taken out, so its block drops what it held; empty if there was none. */
    public Optional<Hatch> remove(BlockPos pos) {
        Optional<Hatch> hatch = Optional.ofNullable(hatches.remove(pos));
        hatch.ifPresent(removed -> onChange.run());
        return hatch;
    }

    /** The hatch at {@code pos}, if there is one. */
    public Optional<Hatch> at(BlockPos pos) {
        return Optional.ofNullable(hatches.get(pos));
    }

    /**
     * Takes out the record at {@code pos} if it is stale, its block loaded but no longer a hatch, as
     * after a command replaced it with no side effects, and drops what it held there. Returns whether
     * a stale record was taken out.
     */
    public boolean dropIfStale(Level level, BlockPos pos) {
        if (!level.isLoaded(pos) || level.getBlockState(pos).is(RailworksRegistries.HATCH.get())) {
            return false;
        }
        Optional<Hatch> stale = remove(pos);
        stale.ifPresent(hatch -> hatch.drop(level));
        return stale.isPresent();
    }

    /** Every hatch, in the order they were placed. */
    public Collection<Hatch> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(hatches.values()));
    }

    private static Hatches read(List<Entry> entries) {
        Hatches hatches = new Hatches();
        entries.forEach(entry -> hatches.hatches.put(entry.pos(), entry.hatch(hatches)));
        return hatches;
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
        private final Hatches hatches;

        private Hatch(BlockPos pos, HatchMode mode, Optional<HatchRequest<Item>> request, NonNullList<ItemStack> stacks,
                      Hatches hatches) {
            this.pos = pos;
            this.mode = mode;
            this.request = request;
            this.contents = new Contents(stacks, hatches);
            this.hatches = hatches;
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

        /**
         * What it shows on its sides: a requester its request's item, a provider its first stack;
         * {@link ItemResource#EMPTY} for a requester asking for nothing and a provider holding nothing.
         */
        public ItemResource shown() {
            if (mode == HatchMode.REQUESTER) {
                return request().map(asked -> ItemResource.of(asked.item())).orElse(ItemResource.EMPTY);
            }
            for (int slot = 0; slot < contents.size(); slot++) {
                ItemResource resource = contents.getResource(slot);
                if (!resource.isEmpty()) {
                    return resource;
                }
            }
            return ItemResource.EMPTY;
        }

        /** Switches it between provider and requester, keeping what it holds and asking for nothing. Returns what it is now. */
        public HatchMode switchMode() {
            mode = mode.other();
            request = Optional.empty();
            hatches.onChange.run();
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
            hatches.onChange.run();
            return asked;
        }

        /** Asks for nothing. */
        public void clearRequest() {
            request = Optional.empty();
            hatches.onChange.run();
        }

        /** Drops everything it holds at the centre of its block, as the block is removed. */
        public void drop(Level level) {
            for (int slot = 0; slot < contents.size(); slot++) {
                ItemStack stack = contents.getResource(slot).toStack(contents.getAmountAsInt(slot));
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                }
            }
        }
    }

    /** A hatch's slots, which mark the hatches to be saved whenever what they hold changes. */
    private static final class Contents extends ItemStacksResourceHandler {

        private final Hatches hatches;

        Contents(NonNullList<ItemStack> stacks, Hatches hatches) {
            super(stacks);
            this.hatches = hatches;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            hatches.onChange.run();
        }
    }

    /** A hatch as it is saved: its slots in order, the empty ones too, so it keeps as many as it was placed with. */
    private record Entry(BlockPos pos, HatchMode mode, Optional<HatchRequest<Item>> request, List<ItemStack> stacks) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                HatchMode.CODEC.fieldOf("mode").forGetter(Entry::mode),
                REQUEST_CODEC.optionalFieldOf("request").forGetter(Entry::request),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("stacks").forGetter(Entry::stacks)
        ).apply(i, Entry::new));

        static Entry of(Hatch hatch) {
            return new Entry(hatch.pos, hatch.mode, hatch.request(), hatch.contents.copyToList());
        }

        Hatch hatch(Hatches hatches) {
            NonNullList<ItemStack> slots = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < stacks.size(); slot++) {
                slots.set(slot, stacks.get(slot));
            }
            return new Hatch(pos, mode, request, slots, hatches);
        }
    }
}
