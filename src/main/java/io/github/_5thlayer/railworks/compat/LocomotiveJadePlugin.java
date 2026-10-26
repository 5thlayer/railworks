// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import java.util.Locale;

import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.rail.LocomotiveSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * A Burner Locomotive in Jade: its fuel and its speed, read from the entity's synched data, which
 * the server fills every tick, so no server data provider is needed. Found by Jade's annotation scan
 * and named nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public class LocomotiveJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "burner_locomotive");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new Provider(), LocomotiveEntity.class);
    }

    private static final class Provider implements IEntityComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof LocomotiveEntity car)) {
                return;
            }
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
            ItemStack slot = car.fuelItem();
            tooltip.add(slot.isEmpty()
                    ? Component.translatable("tooltip.railworks.locomotive.jade.slot_empty")
                    : Component.translatable("tooltip.railworks.locomotive.jade.slot", slot.getCount(), slot.getHoverName()));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
