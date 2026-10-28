// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import com.mojang.datafixers.util.Either;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.Logistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * The goods {@link Logistics} moves in a level, of one of its two {@link Goods.Kind kinds}: an item's
 * kind, its components ignored, or a fluid. A hatch of items and a Cargo car hold the first, a Fluid
 * hatch and a Fluid car the second, and one never equals the other, so no load mixes them.
 */
public sealed interface Ware {

    /** A ware as it is sent to a client: an item or a fluid, by its registry id. */
    StreamCodec<RegistryFriendlyByteBuf, Ware> STREAM_CODEC = ByteBufCodecs.either(
                    ByteBufCodecs.registry(Registries.ITEM), ByteBufCodecs.registry(Registries.FLUID))
            .map(either -> either.map(Ware::of, Ware::of),
                    ware -> switch (ware) {
                        case OfItem item -> Either.left(item.item());
                        case OfFluid fluid -> Either.right(fluid.fluid());
                    });

    /** An item's kind. */
    record OfItem(Item item) implements Ware {

        @Override
        public Goods.Kind kind() {
            return Goods.Kind.ITEMS;
        }

        @Override
        public ItemStack icon(long count) {
            return new ItemStack(item, (int) Math.min(Integer.MAX_VALUE, count));
        }

        @Override
        public Component name() {
            return new ItemStack(item).getItemName();
        }
    }

    /** A fluid, counted in millibuckets. */
    record OfFluid(Fluid fluid) implements Ware {

        @Override
        public Goods.Kind kind() {
            return Goods.Kind.FLUID;
        }

        /** Its filled bucket, so many as there are whole buckets of it, and one for less; an empty bucket for a fluid with none. */
        @Override
        public ItemStack icon(long count) {
            long buckets = Math.max(1, count / Logistics.MILLIBUCKETS_PER_BUCKET);
            return new ItemStack(bucket(), (int) Math.min(Integer.MAX_VALUE, buckets));
        }

        /** Its filled bucket's item, or an empty bucket for a fluid that has none. */
        public Item bucket() {
            Item bucket = fluid.getBucket();
            return bucket == Items.AIR ? Items.BUCKET : bucket;
        }

        @Override
        public Component name() {
            return fluid.getFluidType().getDescription();
        }
    }

    static Ware of(Item item) {
        return new OfItem(item);
    }

    static Ware of(Fluid fluid) {
        return new OfFluid(fluid);
    }

    /** Which kind of goods it is. */
    Goods.Kind kind();

    /** How a status line shows {@code count} of it: a stack of an item, or a fluid's filled buckets. */
    ItemStack icon(long count);

    /** Its name, as an item's or a fluid's. */
    Component name();
}
