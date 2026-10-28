// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.compat;

import java.util.List;

import io.github._5thlayer.railworks.FluidCarEntity;
import io.github._5thlayer.railworks.Railworks;
import io.github._5thlayer.railworks.Tank;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * A Fluid car in Jade: its tank, as Jade draws any tank. The car gives no fluid capability, as the
 * Cargo car gives no item capability (ADR 0008), so Jade's own fluid storage, which reads one, finds
 * nothing; this reads {@link FluidCarEntity#tank()} on the server instead, and Jade's own fluid view
 * draws it on the client, as it draws a Fluid hatch's from its capability. Found by Jade's annotation scan and named
 * nowhere else, so Jade stays a soft dependency.
 */
@WailaPlugin
public class FluidCarJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "fluid_car_tank");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerFluidStorage(new Provider(), FluidCarEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerFluidStorageClient(new Client());
    }

    /** The car's tank: the fluid it holds, or none, and its capacity. Server-side. */
    private static final class Provider implements IServerExtensionProvider<FluidView.Data> {

        @Override
        public @Nullable List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof EntityAccessor entity) || !(entity.getEntity() instanceof FluidCarEntity car)) {
                return null;
            }
            Tank tank = car.tank();
            if (tank == null) {
                return null;
            }
            FluidStack held = tank.held();
            JadeFluidObject fluid = held.isEmpty() ? JadeFluidObject.empty()
                    : JadeFluidObject.of(held.getFluid(), held.getAmount(), held.getComponentsPatch());
            return List.of(new ViewGroup<>(List.of(new FluidView.Data(fluid, tank.capacity()))));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    /** Draws the car's tank with Jade's own fluid view, as Jade's default fluid storage draws a capability's. */
    private static final class Client implements IClientExtensionProvider<FluidView.Data, FluidView> {

        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<FluidView.Data>> groups) {
            return ClientViewGroup.map(groups, FluidView::readDefault, null);
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
