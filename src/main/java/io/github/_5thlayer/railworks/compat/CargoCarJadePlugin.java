// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import java.util.List;

import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.Railworks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
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
import snownee.jade.api.view.ItemViewUtils;
import snownee.jade.api.view.ViewGroup;

/**
 * A Cargo car in Jade: its contents, through Jade's own item storage view, as a chest's, the server
 * reading the car's cargo and Jade drawing it; and the Station its Train is stopped at, from the
 * entity's synched data, so that needs no server data provider. Found by Jade's annotation scan and
 * named nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public class CargoCarJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "cargo_car");
    private static final Identifier STATION_UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "cargo_car_station");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(new Cargo(), CargoCarEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new AtStation(), CargoCarEntity.class);
    }

    private static final class AtStation implements IEntityComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (accessor.getEntity() instanceof CargoCarEntity car && car.atStation()) {
                tooltip.add(Component.translatable("tooltip.railworks.cargo_car.jade.station", car.stationName()));
                if (!car.loading()) {
                    tooltip.add(Component.translatable("tooltip.railworks.cargo_car.jade.not_straight"));
                }
            }
        }

        @Override
        public Identifier getUid() {
            return STATION_UID;
        }
    }

    private static final class Cargo implements IServerExtensionProvider<ItemStack> {

        @Override
        public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            return accessor.getTarget() instanceof CargoCarEntity car ? ItemViewUtils.groupOf(car.cargo(), accessor) : List.of();
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
