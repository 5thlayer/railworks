// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.libworks.machine.jade.MachineItems;
import io.github._5thlayer.railworks.CargoCarEntity;
import io.github._5thlayer.railworks.Railworks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * A Cargo car in Jade: what it holds, as icons with their counts. The car is no {@link Container}
 * and gives no item capability (ADR 0008), so Jade's own item storage, which reads either, finds
 * nothing; this reads {@link CargoCarEntity#cargo()} on the server instead, and libworks-runtime's
 * {@link MachineItems.Client} draws it. Found by Jade's annotation scan and named nowhere else, so
 * Jade stays a soft dependency.
 */
@WailaPlugin
public class CargoCarJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "cargo_car_items");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(new Provider(), CargoCarEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerItemStorageClient(new MachineItems.Client(UID));
    }

    /** The car's cargo, slot by slot, skipping empty slots. Server-side. */
    private static final class Provider implements IServerExtensionProvider<ItemStack> {

        @Override
        public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof EntityAccessor entity) || !(entity.getEntity() instanceof CargoCarEntity car)) {
                return null;
            }
            Container cargo = car.cargo();
            List<ItemStack> stacks = new ArrayList<>();
            for (int slot = 0; slot < cargo.getContainerSize(); slot++) {
                ItemStack stack = cargo.getItem(slot);
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                }
            }
            return List.of(new ViewGroup<>(stacks));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
