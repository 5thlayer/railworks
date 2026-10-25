// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import java.util.List;

import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.Railworks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemViewUtils;
import snownee.jade.api.view.ViewGroup;

/**
 * A Cargo car's contents in Jade, through Jade's own item storage view, as a chest's: the server
 * reads the car's cargo and Jade draws it. Found by Jade's annotation scan and named nowhere else, so
 * Jade stays a soft dependency.
 */
@WailaPlugin
public class CargoCarJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "cargo_car");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(new Cargo(), CargoCarEntity.class);
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
