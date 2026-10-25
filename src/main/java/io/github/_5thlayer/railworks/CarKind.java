// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Locomotive;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The cars an item sets on the Rail: the model's car, its entity, and the block its Placement Preview draws. */
public enum CarKind {
    LOCOMOTIVE(Locomotive.LENGTH, Blocks.FURNACE),
    CARGO(CargoCar.LENGTH, Blocks.CHEST);

    private final double length;
    private final Block preview;

    CarKind(double length, Block preview) {
        this.length = length;
        this.preview = preview;
    }

    /** Blocks along the Line, as its model car's. */
    public double length() {
        return length;
    }

    Block preview() {
        return preview;
    }

    /** A new car of this kind, for a new entity to view. */
    Car create() {
        return this == LOCOMOTIVE ? Locomotive.burner() : CargoCar.create();
    }

    EntityType<? extends CarEntity> entityType() {
        return this == LOCOMOTIVE ? RailworksRegistries.LOCOMOTIVE_ENTITY.get() : RailworksRegistries.CARGO_CAR_ENTITY.get();
    }
}
