// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Logistics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * A level's goods as {@link Logistics} asks for them: a hatch's are its record in the level's
 * {@link LevelHatches}, there with its chunk unloaded, and a Cargo car's its entity's, so a car out of
 * view holds nothing, has no room and is not empty. An item's kind is its {@link Item}, its components
 * ignored: a stack of it moves as it is, components and all.
 */
final class LevelGoods implements Goods<Item> {

    private final ServerLevel level;
    private final LevelRails rails;

    LevelGoods(ServerLevel level, LevelRails rails) {
        this.level = level;
        this.rails = rails;
    }

    @Override
    public int stackSize(Item item) {
        return item.getDefaultMaxStackSize();
    }

    @Override
    public long held(Logistics.Hatch<Item> hatch, Item item) {
        ItemStacksResourceHandler contents = contents(hatch);
        long held = 0;
        for (int slot = 0; contents != null && slot < contents.size(); slot++) {
            if (contents.getResource(slot).is(item)) {
                held += contents.getAmountAsLong(slot);
            }
        }
        return held;
    }

    @Override
    public long held(CargoCar car, Item item) {
        SimpleContainer cargo = cargo(car);
        return cargo == null ? 0 : cargo.countItem(item);
    }

    @Override
    public boolean empty(CargoCar car) {
        SimpleContainer cargo = cargo(car);
        return cargo != null && cargo.isEmpty();
    }

    @Override
    public int slots(CargoCar car) {
        return CargoCarEntity.SLOTS;
    }

    @Override
    public long load(Logistics.Hatch<Item> from, CargoCar into, Item item, long most) {
        ItemStacksResourceHandler contents = contents(from);
        SimpleContainer cargo = cargo(into);
        if (contents == null || cargo == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < contents.size() && moved < most; slot++) {
            ItemResource resource = contents.getResource(slot);
            if (!resource.is(item)) {
                continue;
            }
            int taking = (int) Math.min(most - moved, contents.getAmountAsInt(slot));
            ItemStack left = cargo.addItem(resource.toStack(taking));
            int fitted = taking - left.getCount();
            if (fitted > 0) {
                try (var transaction = Transaction.openRoot()) {
                    contents.extract(slot, resource, fitted, transaction);
                    transaction.commit();
                }
                moved += fitted;
            }
            if (!left.isEmpty()) {
                break;
            }
        }
        return moved;
    }

    @Override
    public long unload(CargoCar from, Logistics.Hatch<Item> into, Item item, long most) {
        ItemStacksResourceHandler contents = contents(into);
        SimpleContainer cargo = cargo(from);
        if (contents == null || cargo == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < cargo.getContainerSize() && moved < most; slot++) {
            ItemStack stack = cargo.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int giving = (int) Math.min(most - moved, stack.getCount());
            int given;
            try (var transaction = Transaction.openRoot()) {
                given = ResourceHandlerUtil.insertStacking(contents, ItemResource.of(stack), giving, transaction);
                transaction.commit();
            }
            if (given > 0) {
                cargo.removeItem(slot, given);
                moved += given;
            }
            if (given < giving) {
                break;
            }
        }
        return moved;
    }

    @Override
    public boolean slotFuelled(Locomotive locomotive) {
        return rails.view(locomotive) instanceof LocomotiveEntity view && view.hasFuel();
    }

    private @Nullable ItemStacksResourceHandler contents(Logistics.Hatch<Item> hatch) {
        return LevelHatches.of(level).at(new BlockPos(hatch.x(), hatch.y(), hatch.z()))
                .map(Hatches.Hatch::contents)
                .orElse(null);
    }

    private @Nullable SimpleContainer cargo(CargoCar car) {
        return rails.view(car) instanceof CargoCarEntity view ? view.cargo() : null;
    }
}
