// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.List;

import io.github._5thlayer.railworks.network.ScheduleMenuPacket;
import io.github._5thlayer.railworks.network.SchedulePacket;
import io.github._5thlayer.railworks.rail.Schedule;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A Burner Locomotive's menu: the cab's slots over the player's inventory, laid out as a vanilla
 * container's. The cab holds the fuel slot only, which takes furnace fuel, so {@link #CAB_SLOTS} is
 * the number of slots the cab holds, not "one". It opens with vanilla's plain screen packet, and its
 * Train's Schedule as it stood follows in a {@link ScheduleMenuPacket}, which the client's screen
 * edits in a screen of its own and sends back as a {@link SchedulePacket}.
 */
public final class LocomotiveMenu extends AbstractContainerMenu {

    public static final int FUEL = 0;
    public static final int CAB_SLOTS = 1;

    /** How far from the car the player may stand with its menu open, or edit its Schedule, in blocks. */
    public static final double REACH = 8.0;
    /** The most Station names the menu offers. */
    private static final int MAX_NAMES = 256;

    /**
     * What the server sends after the menu as it opens: the Locomotive's entity id, its Train's
     * Schedule, and the names of the level's Stations to pick from.
     */
    public record Opened(int car, List<Schedule.Entry> entries, boolean automatic, List<String> stations) {

        public static final Opened NONE = new Opened(-1, List.of(), false, List.of());

        public static final StreamCodec<ByteBuf, Opened> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Opened::car,
                SchedulePacket.ENTRIES_STREAM_CODEC, Opened::entries,
                ByteBufCodecs.BOOL, Opened::automatic,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_NAMES)), Opened::stations,
                Opened::new);

        /** At most as many names as the codec takes. */
        public Opened {
            stations = List.copyOf(stations.subList(0, Math.min(stations.size(), MAX_NAMES)));
        }
    }
    private static final int FUEL_X = 80;
    private static final int FUEL_Y = 18;
    private static final int INVENTORY_Y = 49;

    private final Container cab;
    private final @Nullable LocomotiveEntity car;
    private Opened opened;

    /** The server's menu, over the car's own cab. */
    public LocomotiveMenu(int containerId, Inventory inventory, Container cab, LocomotiveEntity car) {
        super(RailworksRegistries.LOCOMOTIVE_MENU.get(), containerId);
        this.cab = cab;
        this.car = car;
        this.opened = Opened.NONE;
        addSlots(inventory);
    }

    /**
     * The client's menu, over an empty cab the server fills. It knows no car, since the server decides
     * when the menu closes, only what {@link Opened} says of it once that arrives.
     */
    public LocomotiveMenu(int containerId, Inventory inventory) {
        super(RailworksRegistries.LOCOMOTIVE_MENU.get(), containerId);
        this.cab = new SimpleContainer(CAB_SLOTS);
        this.car = null;
        this.opened = Opened.NONE;
        addSlots(inventory);
    }

    /** What the server sent after it opened, on the client; {@link Opened#NONE} until then, and on the server. */
    public Opened opened() {
        return opened;
    }

    /** The server's {@link ScheduleMenuPacket} for this menu arrived, on the client. */
    public void opened(Opened opened) {
        this.opened = opened;
    }

    private void addSlots(Inventory inventory) {
        checkContainerSize(cab, CAB_SLOTS);
        Level level = inventory.player.level();
        addSlot(new Slot(cab, FUEL, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return LocomotiveEntity.fuelValue(level, stack) > 0L;
            }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    @Override
    public boolean stillValid(Player player) {
        if (car == null) {
            return true;
        }
        return car.isAlive() && (car.hasPassenger(player) || player.distanceToSqr(car) < REACH * REACH);
    }

    /** Shift-click: the cab's slots to the inventory, and the inventory's fuel to the fuel slot. */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = slotIndex < CAB_SLOTS
                ? moveItemStackTo(stack, CAB_SLOTS, slots.size(), true)
                : moveItemStackTo(stack, 0, CAB_SLOTS, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }
}
