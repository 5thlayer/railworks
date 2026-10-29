// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.HatchRequest;
import io.github._5thlayer.railworks.rail.Logistics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Every hatch of a level (ADR 0008): where each stands, whether it is a provider or a requester,
 * whether it is a hatch of items or a Fluid hatch, what a requester asks for and what it holds, so an
 * abstract Train loads and unloads one with no chunk loaded. A hatch's block is a view onto its record
 * here, its item handler these very slots and a Fluid hatch's fluid handler this very tank.
 * Which Station a hatch joins is not kept but asked afresh ({@link PlatformsNear#joined}), so one
 * whose Station is broken or whose platform is bent away is a plain container until a platform
 * covers it again. {@link LevelHatches} saves it with the level.
 */
public final class Hatches {

    /** How many slots a hatch holds unless {@code railworks-server.toml} says otherwise: a chest's. */
    public static final int DEFAULT_SLOTS = 27;

    /** An item hatch's request as it is saved: the item by its id, and the stacks. */
    private static final Codec<HatchRequest<Item>> ITEM_REQUEST_CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(HatchRequest::goods),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("stacks").forGetter(HatchRequest::count)
    ).apply(i, HatchRequest::new));

    /** A Fluid hatch's request as it is saved: the fluid by its id, and the buckets. */
    private static final Codec<HatchRequest<Fluid>> FLUID_REQUEST_CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(HatchRequest::goods),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("buckets").forGetter(HatchRequest::count)
    ).apply(i, HatchRequest::new));

    /** A request as it is saved: an item's, as hatches saved it before Fluid hatches, or a fluid's. */
    public static final Codec<HatchRequest<Ware>> REQUEST_CODEC = Codec.either(ITEM_REQUEST_CODEC, FLUID_REQUEST_CODEC).xmap(
            either -> either.map(asked -> new HatchRequest<>(Ware.of(asked.goods()), asked.count()),
                    asked -> new HatchRequest<>(Ware.of(asked.goods()), asked.count())),
            asked -> switch (asked.goods()) {
                case Ware.OfItem item -> Either.left(new HatchRequest<>(item.item(), asked.count()));
                case Ware.OfFluid fluid -> Either.right(new HatchRequest<>(fluid.fluid(), asked.count()));
            });

    /** A request as it is sent to a client. */
    public static final StreamCodec<RegistryFriendlyByteBuf, HatchRequest<Ware>> REQUEST_STREAM_CODEC = StreamCodec.composite(
            Ware.STREAM_CODEC, HatchRequest::goods,
            ByteBufCodecs.VAR_INT, HatchRequest::count,
            HatchRequest::new);

    /** Every hatch as it is saved, in the order they were placed. */
    public static final Codec<Hatches> CODEC = Entry.CODEC.listOf()
            .xmap(Hatches::read, hatches -> hatches.hatches.values().stream().map(Entry::of).toList());

    private static volatile int slots = DEFAULT_SLOTS;
    private static volatile int tankBuckets = Logistics.DEFAULT_FLUID_HATCH_BUCKETS;

    private final Map<BlockPos, Hatch> hatches = new LinkedHashMap<>();
    private Runnable onChange = () -> {
    };

    /** Runs {@code onChange} after each change, what a hatch holds too, so the saved data that holds it is saved. */
    void onChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Set by {@code RailworksConfig} when the server config loads or reloads: how many slots a hatch
     * placed from now on holds, and how many buckets a Fluid hatch's tank does.
     */
    public static void configure(int slots, int tankBuckets) {
        if (slots < 1 || tankBuckets < 1) {
            throw new IllegalArgumentException("hatch slots " + slots + ", tank " + tankBuckets);
        }
        Hatches.slots = slots;
        Hatches.tankBuckets = tankBuckets;
    }

    /** How many slots a hatch placed now holds. */
    public static int slots() {
        return slots;
    }

    /** How many buckets a Fluid hatch placed now holds. */
    public static int tankBuckets() {
        return tankBuckets;
    }

    /**
     * Puts a hatch at {@code pos}, of items, or a Fluid hatch as {@code holds} says, holding nothing
     * and asking for nothing, in place of any that was there.
     */
    public Hatch put(BlockPos pos, Goods.Kind holds, HatchMode mode) {
        boolean fluid = holds == Goods.Kind.FLUID;
        Hatch hatch = new Hatch(pos.immutable(), holds, mode, Optional.empty(),
                NonNullList.withSize(fluid ? 0 : slots, ItemStack.EMPTY),
                fluid ? Tank.capacityOf(tankBuckets) : 0, FluidStack.EMPTY, this);
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
     * Takes out the record at {@code pos} if it is stale, its block loaded but no longer a hatch of its
     * kind, as after a command replaced it with no side effects, and drops what it held there. Returns
     * whether a stale record was taken out.
     */
    public boolean dropIfStale(Level level, BlockPos pos) {
        Hatch record = hatches.get(pos);
        if (!level.isLoaded(pos) || record == null
                || level.getBlockState(pos).getBlock() instanceof HatchBlock block && block.holds() == record.holds()) {
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
     * One hatch: where it stands and whether it is a hatch of items or a Fluid hatch, which never
     * change; whether it is a provider or a requester, and a requester's request; and what it holds,
     * items in its slots or one fluid in its tank. It has no owner.
     */
    public static final class Hatch {

        private final BlockPos pos;
        private final Goods.Kind holds;
        private HatchMode mode;
        private Optional<HatchRequest<Ware>> request;
        private final Contents contents;
        private final Tank tank;
        private final Hatches hatches;

        private Hatch(BlockPos pos, Goods.Kind holds, HatchMode mode, Optional<HatchRequest<Ware>> request,
                      NonNullList<ItemStack> stacks, int tankCapacity, FluidStack held, Hatches hatches) {
            this.pos = pos;
            this.holds = holds;
            this.mode = mode;
            this.request = request.filter(asked -> asked.goods().kind() == holds);
            this.contents = new Contents(stacks, hatches);
            this.tank = new Tank(tankCapacity, held, () -> hatches.onChange.run());
            this.hatches = hatches;
        }

        public BlockPos pos() {
            return pos;
        }

        /** Whether it is a hatch of items or a Fluid hatch. */
        public Goods.Kind holds() {
            return holds;
        }

        public HatchMode mode() {
            return mode;
        }

        /** What a requester asks for; empty for one asking for nothing, and for a provider, which never asks. */
        public Optional<HatchRequest<Ware>> request() {
            return mode == HatchMode.REQUESTER ? request : Optional.empty();
        }

        /** What a hatch of items holds, which its block's item handler fills and empties, and a Train too; no slots for a Fluid hatch. */
        public ItemStacksResourceHandler contents() {
            return contents;
        }

        /** What a Fluid hatch holds, which its block's fluid handler fills and empties, and a Train too; of no capacity for a hatch of items. */
        public Tank tank() {
            return tank;
        }

        /**
         * What it shows on its sides: a requester its request's item, or its fluid's filled bucket, a
         * provider of items its first stack; {@link ItemResource#EMPTY} for a requester asking for
         * nothing, a provider holding nothing, and a provider Fluid hatch, whose Sight glasses show
         * its fluid.
         */
        public ItemResource shown() {
            if (mode == HatchMode.REQUESTER) {
                return request().map(asked -> ItemResource.of(asked.goods().icon(1))).orElse(ItemResource.EMPTY);
            }
            if (holds == Goods.Kind.FLUID) {
                return ItemResource.EMPTY;
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
         * A requester of items asks for a stack more of {@code item}'s kind, up to as many stacks as it
         * has slots; another kind replaces the request with one stack. Returns what it asks for now.
         *
         * @throws IllegalStateException for a provider, which never asks, or a Fluid hatch
         */
        public HatchRequest<Ware> ask(Item item) {
            return asked(Goods.Kind.ITEMS, HatchRequest.ask(request, Ware.of(item), contents.size()));
        }

        /**
         * A Fluid requester asks for {@link HatchRequest#BUCKETS_PER_ASK} buckets more of {@code fluid},
         * up to its tank; another fluid replaces the request with that many of it. Returns what it asks
         * for now.
         *
         * @throws IllegalStateException for a provider, which never asks, or a hatch of items
         */
        public HatchRequest<Ware> ask(Fluid fluid) {
            int buckets = (int) (tank.capacity() / Logistics.MILLIBUCKETS_PER_BUCKET);
            return asked(Goods.Kind.FLUID, HatchRequest.askFluid(request, Ware.of(fluid), Math.max(1, buckets)));
        }

        private HatchRequest<Ware> asked(Goods.Kind kind, HatchRequest<Ware> asked) {
            if (mode != HatchMode.REQUESTER) {
                throw new IllegalStateException("a provider asks for nothing");
            }
            if (holds != kind) {
                throw new IllegalStateException("a hatch of " + holds + " asks for no " + kind);
            }
            request = Optional.of(asked);
            hatches.onChange.run();
            return asked;
        }

        /** Asks for nothing. */
        public void clearRequest() {
            request = Optional.empty();
            hatches.onChange.run();
        }

        /** Drops every item it holds at the centre of its block, as the block is removed; a Fluid hatch's fluid is spilt. */
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

    /**
     * A hatch as it is saved: its slots in order, the empty ones too, so it keeps as many as it was
     * placed with, and a Fluid hatch's tank, its capacity too. One saved before Fluid hatches is a
     * hatch of items with no tank.
     */
    private record Entry(BlockPos pos, Goods.Kind holds, HatchMode mode, Optional<HatchRequest<Ware>> request,
                         List<ItemStack> stacks, int tankCapacity, FluidStack tank) {

        private static final Codec<Goods.Kind> KIND_CODEC = Codec.STRING.xmap(
                name -> Goods.Kind.valueOf(name.toUpperCase(Locale.ROOT)),
                kind -> kind.name().toLowerCase(Locale.ROOT));

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                KIND_CODEC.optionalFieldOf("holds", Goods.Kind.ITEMS).forGetter(Entry::holds),
                HatchMode.CODEC.fieldOf("mode").forGetter(Entry::mode),
                REQUEST_CODEC.optionalFieldOf("request").forGetter(Entry::request),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("stacks").forGetter(Entry::stacks),
                Codec.INT.optionalFieldOf("tank_capacity", 0).forGetter(Entry::tankCapacity),
                FluidStack.OPTIONAL_CODEC.optionalFieldOf("tank", FluidStack.EMPTY).forGetter(Entry::tank)
        ).apply(i, Entry::new));

        static Entry of(Hatch hatch) {
            return new Entry(hatch.pos, hatch.holds, hatch.mode, hatch.request(), hatch.contents.copyToList(),
                    hatch.tank.capacity(), hatch.tank.held());
        }

        Hatch hatch(Hatches hatches) {
            NonNullList<ItemStack> slots = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < stacks.size(); slot++) {
                slots.set(slot, stacks.get(slot));
            }
            return new Hatch(pos, holds, mode, request, slots, tankCapacity, tank, hatches);
        }
    }
}
