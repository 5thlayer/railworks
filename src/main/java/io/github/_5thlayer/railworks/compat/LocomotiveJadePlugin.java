// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github._5thlayer.libworks.machine.jade.MachineItems;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * A Burner Locomotive in Jade: its Train's status line, its speed and its fuel buffer, read from
 * the entity's synched data, which the server fills every tick; and its fuel slots, as icons with
 * their counts, as {@link CargoCarJadePlugin} shows a Cargo car's cargo. The slots are the car's
 * record in the level, which no client holds, so a server-side item storage provider reads
 * {@link LocomotiveEntity#fuelSlot(int)} and libworks-runtime's {@link MachineItems.Client} draws
 * them. Found by Jade's annotation scan and named nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public class LocomotiveJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "burner_locomotive");
    private static final Identifier ITEMS_UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "burner_locomotive_items");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(new FuelSlotsProvider(), LocomotiveEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new StatusProvider(), LocomotiveEntity.class);
        registration.registerItemStorageClient(new MachineItems.Client(ITEMS_UID));
    }

    private static final class StatusProvider implements IEntityComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof LocomotiveEntity car)) {
                return;
            }
            tooltip.add(car.statusSeen());
            double speed = car.speedPerSecond();
            tooltip.add(Component.translatable("tooltip.railworks.locomotive.jade.speed",
                    String.format(Locale.ROOT, "%.1f", Math.abs(speed)),
                    Component.translatable(speed < -1e-6
                            ? "tooltip.railworks.locomotive.jade.reversing" : "tooltip.railworks.locomotive.jade.forward")));
            long joules = car.fuelJoules();
            // Seconds at full power, which is how long the buffer drives it.
            double seconds = joules / (LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER * (double) LocomotiveSpec.TICKS_PER_SECOND);
            tooltip.add(Component.translatable("tooltip.railworks.locomotive.jade.fuel",
                    String.format(Locale.ROOT, "%.1f", joules / 1_000_000.0),
                    String.format(Locale.ROOT, "%.1f", seconds)));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    /** The car's fuel slots, slot by slot, skipping empty slots. Server-side. */
    private static final class FuelSlotsProvider implements IServerExtensionProvider<ItemStack> {

        @Override
        public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof EntityAccessor entity) || !(entity.getEntity() instanceof LocomotiveEntity car)) {
                return null;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (int slot = 0; slot < Locomotive.FUEL_SLOTS; slot++) {
                ItemStack stack = car.fuelSlot(slot);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            return List.of(new ViewGroup<>(stacks));
        }

        @Override
        public Identifier getUid() {
            return ITEMS_UID;
        }
    }
}
