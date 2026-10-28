// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.ChargeSpec;
import io.github._5thlayer.railworks.rail.FluidCar;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.GoodsCar;
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
 * {@link LevelHatches}, and a Cargo car's, a Fluid car's tank and a Locomotive's fuel slots the car's
 * record in its {@link LevelCarRecords}, all there with their chunks unloaded (ADR 0008, the Fluid
 * car's and Fluid hatch's tanks as the Cargo car's cargo and the hatch's slots), as are an Electric
 * Locomotive's charge, its car's record too, and a Charger's buffer, its record in the level's
 * {@link LevelChargers} (ADR 0005, amended in #9). Only a car whose view has not yet made its record,
 * one of a world saved before the level kept them whose entity has not loaded since, holds nothing,
 * has no room and is not empty. A ware is an item's kind, its components ignored, a stack of it
 * moving as it is, components and all; or a fluid, counted in millibuckets, moving between a Fluid
 * hatch's tank and a Fluid car's.
 */
final class LevelGoods implements Goods<Ware> {

    private final ServerLevel level;

    LevelGoods(ServerLevel level) {
        this.level = level;
    }

    @Override
    public int stackSize(Ware ware) {
        return ware instanceof Ware.OfItem item ? item.item().getDefaultMaxStackSize() : 1;
    }

    @Override
    public long held(Logistics.Hatch<Ware> hatch, Ware ware) {
        return switch (ware) {
            case Ware.OfItem item -> held(hatchContents(hatch), item.item());
            case Ware.OfFluid fluid -> held(hatchTank(hatch), fluid);
        };
    }

    @Override
    public long held(GoodsCar car, Ware ware) {
        CarRecords.Contents record = record(car);
        if (record == null) {
            return 0;
        }
        return switch (ware) {
            case Ware.OfItem item -> record.countItem(item.item());
            case Ware.OfFluid fluid -> held(record.tank(), fluid);
        };
    }

    @Override
    public Map<Ware, Long> contents(GoodsCar car) {
        CarRecords.Contents record = record(car);
        Map<Ware, Long> contents = new LinkedHashMap<>();
        if (record == null) {
            return contents;
        }
        for (int slot = 0; slot < record.getContainerSize(); slot++) {
            ItemStack stack = record.getItem(slot);
            if (!stack.isEmpty()) {
                contents.merge(Ware.of(stack.getItem()), (long) stack.getCount(), Long::sum);
            }
        }
        tankContents(record.tank(), contents);
        return contents;
    }

    @Override
    public boolean empty(GoodsCar car) {
        CarRecords.Contents record = record(car);
        return record != null && record.isEmpty();
    }

    @Override
    public int slots(CargoCar car) {
        return CargoCarEntity.SLOTS;
    }

    /** Its record's tank's, made with the size the server config gave a Fluid car then; a car with no record yet, the size it gives now. */
    @Override
    public long tank(FluidCar car) {
        CarRecords.Contents record = record(car);
        return record != null ? record.tank().capacity() : FluidCarEntity.tankCapacity();
    }

    @Override
    public long load(Logistics.Hatch<Ware> from, GoodsCar into, Ware ware, long most) {
        CarRecords.Contents record = record(into);
        if (record == null) {
            return 0;
        }
        return switch (ware) {
            case Ware.OfItem item -> loadItems(hatchContents(from), record, item.item(), most);
            case Ware.OfFluid fluid -> move(hatchTank(from), record.tank(), fluid, most);
        };
    }

    private static long loadItems(@Nullable ItemStacksResourceHandler contents, SimpleContainer cargo, Item item, long most) {
        if (contents == null) {
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
    public long unload(GoodsCar from, Logistics.Hatch<Ware> into, Ware ware, long most) {
        CarRecords.Contents record = record(from);
        if (record == null) {
            return 0;
        }
        return switch (ware) {
            case Ware.OfItem item -> unloadItems(record, hatchContents(into), item.item(), most);
            case Ware.OfFluid fluid -> move(record.tank(), hatchTank(into), fluid, most);
        };
    }

    private static long unloadItems(SimpleContainer cargo, @Nullable ItemStacksResourceHandler contents, Item item, long most) {
        if (contents == null) {
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

    /**
     * Moves up to {@code most} millibuckets of {@code fluid} from one tank to the other, as far as the
     * one holds it and the other has room; how many moved.
     */
    private static long move(@Nullable Tank from, @Nullable Tank into, Ware.OfFluid fluid, long most) {
        if (from == null || into == null) {
            return 0;
        }
        try (var transaction = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(from, into, resource -> resource.is(fluid.fluid()),
                    (int) Math.min(Integer.MAX_VALUE, most), transaction);
            transaction.commit();
            return moved;
        }
    }

    @Override
    public Map<Ware, Long> contents(Logistics.Hatch<Ware> hatch) {
        Map<Ware, Long> held = new LinkedHashMap<>();
        ItemStacksResourceHandler contents = hatchContents(hatch);
        for (int slot = 0; contents != null && slot < contents.size(); slot++) {
            ItemResource resource = contents.getResource(slot);
            if (!resource.isEmpty()) {
                held.merge(Ware.of(resource.getItem()), contents.getAmountAsLong(slot), Long::sum);
            }
        }
        Tank tank = hatchTank(hatch);
        if (tank != null) {
            tankContents(tank, held);
        }
        return held;
    }

    /**
     * The room for the first stack of {@code ware} that {@code from} holds that the fuel slots take
     * any of, components and all, which {@link #refuel} moves first; none for a fluid.
     */
    @Override
    public long fuelRoom(Logistics.Hatch<Ware> from, Locomotive locomotive, Ware ware) {
        ItemStacksResourceHandler contents = hatchContents(from);
        FuelSlots fuel = fuel(locomotive);
        if (!(ware instanceof Ware.OfItem item) || contents == null || fuel == null) {
            return 0;
        }
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemResource resource = contents.getResource(slot);
            long room = resource.is(item.item()) ? fuel.roomFromHatch(resource.toStack(1)) : 0;
            if (room > 0) {
                return room;
            }
        }
        return 0;
    }

    @Override
    public long refuel(Logistics.Hatch<Ware> from, Locomotive into, Ware ware, long most) {
        ItemStacksResourceHandler contents = hatchContents(from);
        FuelSlots fuel = fuel(into);
        if (!(ware instanceof Ware.OfItem item) || contents == null || fuel == null) {
            return 0;
        }
        long moved = 0;
        for (int slot = 0; slot < contents.size() && moved < most; slot++) {
            ItemResource resource = contents.getResource(slot);
            if (!resource.is(item.item())) {
                continue;
            }
            ItemStack stack = resource.toStack(contents.getAmountAsInt(slot));
            int taking = (int) Math.min(most - moved, Math.min(stack.getCount(), fuel.roomFromHatch(stack)));
            if (taking <= 0) {
                // Its components match no slot's: the next stack may still fit.
                continue;
            }
            int fitted = fuel.add(stack.copyWithCount(taking));
            if (fitted > 0) {
                try (var transaction = Transaction.openRoot()) {
                    contents.extract(slot, resource, fitted, transaction);
                    transaction.commit();
                }
                moved += fitted;
            }
            if (fitted < taking) {
                break;
            }
        }
        return moved;
    }

    @Override
    public boolean slotsFuelled(Locomotive locomotive) {
        FuelSlots fuel = fuel(locomotive);
        return fuel != null && fuel.hasFuel();
    }

    @Override
    public long charge(Locomotive locomotive) {
        CarRecords.Contents record = record(locomotive);
        return record == null ? 0L : record.charge();
    }

    @Override
    public boolean spendCharge(Locomotive locomotive, long fe) {
        CarRecords.Contents record = record(locomotive);
        if (record == null || record.charge() < fe) {
            return false;
        }
        record.setCharge(record.charge() - fe);
        return true;
    }

    @Override
    public long giveCharge(Logistics.Charger from, Locomotive into, long most) {
        CarRecords.Contents record = record(into);
        Chargers.Charger charger = LevelChargers.of(level).at(new BlockPos(from.x(), from.y(), from.z())).orElse(null);
        if (record == null || charger == null) {
            return 0L;
        }
        long moved = Math.max(0L, Math.min(most, Math.min(ChargeSpec.locomotiveCapacity() - record.charge(), charger.held())));
        if (moved > 0L) {
            charger.take(moved);
            record.setCharge(record.charge() + moved);
        }
        return moved;
    }

    /** {@code locomotive}'s record, for an Electric Locomotive only; null for a Burner, or one whose view has not made it. */
    private CarRecords.@Nullable Contents record(Locomotive locomotive) {
        return locomotive.isElectric() ? LevelCarRecords.of(level).of(locomotive.id()).orElse(null) : null;
    }

    private static long held(@Nullable ItemStacksResourceHandler contents, Item item) {
        long held = 0;
        for (int slot = 0; contents != null && slot < contents.size(); slot++) {
            if (contents.getResource(slot).is(item)) {
                held += contents.getAmountAsLong(slot);
            }
        }
        return held;
    }

    private static long held(@Nullable Tank tank, Ware.OfFluid fluid) {
        return tank != null && tank.fluid().is(fluid.fluid()) ? tank.amount() : 0;
    }

    /** Adds what {@code tank} holds to {@code contents}. */
    private static void tankContents(Tank tank, Map<Ware, Long> contents) {
        if (tank.amount() > 0) {
            contents.merge(Ware.of(tank.fluid().getFluid()), (long) tank.amount(), Long::sum);
        }
    }

    private Optional<Hatches.Hatch> hatch(Logistics.Hatch<Ware> hatch) {
        return LevelHatches.of(level).at(new BlockPos(hatch.x(), hatch.y(), hatch.z()));
    }

    private @Nullable ItemStacksResourceHandler hatchContents(Logistics.Hatch<Ware> hatch) {
        return hatch(hatch).map(Hatches.Hatch::contents).orElse(null);
    }

    private @Nullable Tank hatchTank(Logistics.Hatch<Ware> hatch) {
        return hatch(hatch).map(Hatches.Hatch::tank).orElse(null);
    }

    private CarRecords.@Nullable Contents record(GoodsCar car) {
        return LevelCarRecords.of(level).of(car.id()).orElse(null);
    }

    private @Nullable FuelSlots fuel(Locomotive locomotive) {
        return FuelSlots.of(level, locomotive);
    }
}
