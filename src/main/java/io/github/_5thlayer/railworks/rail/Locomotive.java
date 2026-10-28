// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A Locomotive: the car of a {@link Train} that pulls it, for as long as its joule fuel buffer pays a
 * tick of full power, and brakes it, fuelled or not. Where it is and how fast it goes are its
 * Train's; which way it faces in its Train is the Train's too. A Burner Locomotive fills its buffer
 * by lighting fuel from its slots; an Electric Locomotive by drawing whole FE from its charge, which
 * is its record's in the level (ADR 0008) and which {@link Goods} reads and spends, so no more than a
 * tick's draw is ever in its buffer. Both have the same mass, power and drive.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Locomotive implements Car {

    public static final String KIND = "locomotive";
    public static final String ELECTRIC_KIND = "electric_locomotive";

    /**
     * Blocks along the Line, coupler to coupler: a cab, an Engine room over its middle block and a
     * bay behind it, five blocks, as every car is (ADR 0010).
     */
    public static final double LENGTH = 5.0;
    /** How many fuel slots it has, each a stack of one fuel, the fuels free to differ. */
    public static final int FUEL_SLOTS = 4;

    static final MapCodec<Locomotive> MAP_CODEC = codec(false);
    static final MapCodec<Locomotive> ELECTRIC_MAP_CODEC = codec(true);

    private final UUID id;
    private final boolean electric;
    private final FuelBuffer fuel = new FuelBuffer();

    private Locomotive(UUID id, boolean electric) {
        this.id = id;
        this.electric = electric;
    }

    /** A Burner Locomotive with an empty buffer. */
    public static Locomotive burner() {
        return new Locomotive(UUID.randomUUID(), false);
    }

    /** An Electric Locomotive with an empty buffer; its charge, its record's, starts empty too. */
    public static Locomotive electric() {
        return new Locomotive(UUID.randomUUID(), true);
    }

    static Locomotive restore(UUID id, long fuel, long lastLit) {
        return restore(id, false, fuel, lastLit);
    }

    private static Locomotive restore(UUID id, boolean electric, long fuel, long lastLit) {
        Locomotive locomotive = new Locomotive(id, electric);
        locomotive.fuel.load(fuel, lastLit);
        return locomotive;
    }

    private static MapCodec<Locomotive> codec(boolean electric) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                ID_FIELD.forGetter(Locomotive::id),
                Codec.LONG.fieldOf("fuel").forGetter(l -> l.fuel.storedJoules()),
                Codec.LONG.fieldOf("last_lit").forGetter(l -> l.fuel.lastLitJoules())
        ).apply(i, (id, fuel, lastLit) -> restore(id, electric, fuel, lastLit)));
    }

    /**
     * What pays each Locomotive's tick once its buffer runs short: a Burner's fuel lit through
     * {@code ignite}, or an Electric's charge drawn through {@code goods}.
     */
    public static ToLongFunction<Locomotive> power(Goods<?> goods, ToLongFunction<Locomotive> ignite) {
        return locomotive -> locomotive.electric ? locomotive.draw(goods) : ignite.applyAsLong(locomotive);
    }

    /**
     * Whether each Locomotive can pay a tick beyond its buffer, as {@link Train#pulls} asks: fuel waits
     * in a Burner's slots, or an Electric's charge covers what its buffer lacks. A charge short of a
     * tick's draw, under 300 FE at the defaults, pulls nothing and waits for a Charger.
     */
    public static Predicate<Locomotive> waiting(Goods<?> goods) {
        return locomotive -> locomotive.electric ? goods.charge(locomotive) >= locomotive.feToDraw()
                : goods.slotsFuelled(locomotive);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String kind() {
        return electric ? ELECTRIC_KIND : KIND;
    }

    /** Whether it is an Electric Locomotive, running on a charge of FE, rather than a Burner. */
    public boolean isElectric() {
        return electric;
    }

    @Override
    public double mass() {
        return LocomotiveSpec.MASS_KG;
    }

    @Override
    public double length() {
        return LENGTH;
    }

    public FuelBuffer fuel() {
        return fuel;
    }

    /**
     * What it pulls with at full power, in kilogram blocks a tick squared: its own mass at the
     * configured acceleration, so a Train's acceleration is its pulling share of that.
     */
    public double pull() {
        return LocomotiveSpec.MASS_KG * LocomotiveSpec.acceleration();
    }

    /** Whether its buffer covers a tick of full power without lighting anything. */
    public boolean fuelled() {
        return fuel.storedJoules() >= LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER;
    }

    /**
     * Pays one tick of full power: the buffer first, lighting an item through {@code ignite} (the
     * item's joules, or 0 for none) only when the buffer cannot cover the tick.
     */
    boolean pay(LongSupplier ignite) {
        long perTick = LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER;
        if (fuel.drawTick(perTick)) {
            return true;
        }
        long lit = ignite.getAsLong();
        if (lit <= 0L) {
            return false;
        }
        fuel.light(lit);
        return fuel.drawTick(perTick);
    }

    /** How many whole FE an Electric Locomotive draws to cover what its buffer lacks of a tick: 0 with a tick in it. */
    long feToDraw() {
        return ChargeSpec.feFor(LocomotiveSpec.JOULES_PER_TICK_AT_FULL_POWER - fuel.storedJoules());
    }

    /**
     * Draws from an Electric Locomotive's charge through {@code goods} what its buffer lacks of a
     * tick, in whole FE, all or nothing: the joules drawn, or 0 where the charge cannot cover it.
     */
    private long draw(Goods<?> goods) {
        long fe = feToDraw();
        return fe > 0L && goods.spendCharge(this, fe) ? fe * ChargeSpec.joulesPerFe() : 0L;
    }

    @Override
    public String toString() {
        return (electric ? "ElectricLocomotive[" : "Locomotive[") + id + "]";
    }
}
