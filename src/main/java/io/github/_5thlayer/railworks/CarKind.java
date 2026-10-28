// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.function.Supplier;

import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Locomotive;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/** The cars an item sets on the Rail: the model's car, its entity, and the block its Placement Preview draws. */
public enum CarKind {
    LOCOMOTIVE(Locomotive.KIND, Locomotive.LENGTH, Blocks.FURNACE, Locomotive::burner,
            () -> RailworksRegistries.LOCOMOTIVE_ENTITY.get()),
    CARGO(CargoCar.KIND, CargoCar.LENGTH, Blocks.CHEST, CargoCar::create,
            () -> RailworksRegistries.CARGO_CAR_ENTITY.get());

    private final String model;
    private final double length;
    private final Block preview;
    private final Supplier<Car> create;
    private final Supplier<EntityType<? extends CarEntity>> entityType;

    CarKind(String model, double length, Block preview, Supplier<Car> create,
            Supplier<EntityType<? extends CarEntity>> entityType) {
        this.model = model;
        this.length = length;
        this.preview = preview;
        this.create = create;
        this.entityType = entityType;
    }

    /** The kind whose model car's {@link Car#kind()} is {@code model}; null for none. */
    static @Nullable CarKind of(String model) {
        for (CarKind kind : values()) {
            if (kind.model.equals(model)) {
                return kind;
            }
        }
        return null;
    }

    /** The {@link Car#kind()} of its model car. */
    public String model() {
        return model;
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
        return create.get();
    }

    EntityType<? extends CarEntity> entityType() {
        return entityType.get();
    }
}
