// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.railworks.network.SpansPacket;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Stringing;
import io.github._5thlayer.railworks.rail.Throttle;
import io.github._5thlayer.railworks.rail.Train;
import io.github._5thlayer.railworks.rail.Trains;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A level's Spans and its Trains, saved with it. The rules are {@link Stringing}'s and the storage
 * {@link RailNetwork}'s and {@link Trains}'; this is where they meet a world. It also knows each car's
 * loaded entity, its view, so a Train's tick can read its drivers' keys and its Locomotives' fuel
 * slots and move every car it has in view.
 */
public final class LevelRails extends SavedData {

    private static final Codec<LevelRails> CODEC = RecordCodecBuilder.create(i -> i.group(
            RailNetwork.CODEC.fieldOf("spans").forGetter(data -> data.network),
            Trains.CODEC.optionalFieldOf("trains").forGetter(data -> Optional.of(data.trains))
    ).apply(i, (network, trains) -> new LevelRails(network, trains.orElseGet(Trains::new))));

    public static final SavedDataType<LevelRails> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "spans"),
            LevelRails::new, CODEC);

    private final RailNetwork network;
    private final Trains trains;
    /** Each loaded car's entity, by its car's id. */
    private final Map<UUID, CarEntity> views = new HashMap<>();

    public LevelRails() {
        this(new RailNetwork(), new Trains());
    }

    private LevelRails(RailNetwork network, Trains trains) {
        this.network = network;
        this.trains = trains;
    }

    public static LevelRails of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public RailNetwork network() {
        return network;
    }

    public Trains trains() {
        return trains;
    }

    // ---- the Trains ----

    /** {@code car} is in view: its Train's tick moves it. */
    void bind(CarEntity car) {
        if (car.carId() != null) {
            views.put(car.carId(), car);
        }
    }

    void unbind(CarEntity car) {
        if (car.carId() != null) {
            views.remove(car.carId(), car);
        }
    }

    /** The loaded entity of {@code car}, or null when it has none in view. */
    public CarEntity view(Car car) {
        CarEntity view = views.get(car.id());
        return view == null || view.isRemoved() ? null : view;
    }

    /** A new Train, set on the Rail: it is saved and ticks from now on. */
    public void add(Train train) {
        trains.add(train);
        setDirty();
    }

    /**
     * One tick of {@code train}, from its first car's view: the keys of the first player driving one
     * of its Locomotives, each Locomotive's fuel slot, and every car it has in view set where the
     * model now puts it.
     */
    void tick(ServerLevel level, Train train) {
        Car driven = train.cars().getFirst();
        Throttle throttle = Throttle.NONE;
        for (Car car : train.cars()) {
            if (view(car) instanceof LocomotiveEntity locomotive && locomotive.throttle() != Throttle.NONE) {
                driven = car;
                throttle = locomotive.throttle();
                break;
            }
        }
        double speed = train.speed();
        Place head = train.head();
        train.tick(network, throttle, driven,
                locomotive -> view(locomotive) instanceof LocomotiveEntity view ? view.ignite() : 0L,
                trains.takenFrom(network, train));
        if (train.speed() != speed || !train.head().equals(head)) {
            setDirty();
        }
        for (Car car : train.cars()) {
            CarEntity view = view(car);
            if (view != null) {
                view.moveToModel(train);
                view.syncFromModel(train);
            }
        }
    }

    /** {@code train} fell off the Rail: it is gone, and each of its cars in view breaks, dropping itself and what it holds. */
    void derail(ServerLevel level, Train train) {
        trains.remove(train);
        setDirty();
        for (Car car : train.cars()) {
            CarEntity view = view(car);
            if (view != null) {
                view.breakOff(level);
            }
        }
    }

    /** Parts {@code train} at coupler {@code at}; null, changing nothing, while it moves. */
    List<Train> uncouple(ServerLevel level, Train train, int at) {
        List<Train> parts = trains.uncouple(network, train, at);
        if (parts != null) {
            setDirty();
            parts.forEach(part -> refresh(part));
        }
        return parts;
    }

    /** Couples {@code car} into {@code slot}'s Train; null, changing nothing, where it cannot. */
    Train couple(Trains.Slot slot, Car car, boolean reversed) {
        Train coupled = trains.couple(network, slot, car, reversed);
        if (coupled != null) {
            setDirty();
            refresh(coupled);
        }
        return coupled;
    }

    /** The car with this id is gone for good: its Train closes up without it. */
    void carGone(UUID carId) {
        Train train = trains.of(carId);
        views.remove(carId);
        if (train == null) {
            return;
        }
        trains.removeCar(network, carId);
        setDirty();
        for (Car car : train.cars()) {
            Train now = trains.of(car.id());
            if (now != null) {
                refresh(now);
            }
        }
    }

    /** Sets every car of {@code train} in view where the model puts it, and what a client sees of it. */
    private void refresh(Train train) {
        for (Car car : train.cars()) {
            CarEntity view = view(car);
            if (view != null) {
                view.moveToModel(train);
                view.syncFromModel(train);
            }
        }
    }

    public boolean contains(BlockPos a, BlockPos b) {
        return network.contains(PylonColumn.arm(a), PylonColumn.arm(b));
    }

    /** A Rail gesture's second click, from the Arm at {@code anchor} to the one at {@code target}. */
    public Stringing.Outcome click(ServerLevel level, BlockPos anchor, BlockPos target, int railHeld) {
        Arm from = PylonColumn.arm(anchor);
        Arm to = PylonColumn.arm(target);
        Stringing.Outcome outcome = Stringing.click(network, from, to, railHeld);
        if (outcome.result() == Stringing.Result.STRUNG || outcome.result() == Stringing.Result.CUT) {
            changed(level, from, to);
        }
        return outcome;
    }

    /**
     * A segment was placed at {@code pos}. On top of a column it is the new top: the Arm moves up
     * and keeps its Spans. Under another column it joins two, and the lower column's Arm is gone.
     */
    public void placed(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!PylonColumn.isPylon(level, below)) {
            return;
        }
        if (PylonColumn.isPylon(level, pos.above())) {
            armLost(level, below);
            return;
        }
        Arm from = PylonColumn.arm(below);
        List<Span> moving = network.spansAt(from);
        Arm to = PylonColumn.arm(pos);
        network.rekey(from, to);
        // A Train riding one of those Spans stays on it, its Arm now one segment higher.
        trains.moveArm(network, from, to);
        trains.all().forEach(this::refresh);
        setDirty();
        if (!moving.isEmpty()) {
            changed(level, moving.stream().flatMap(s -> Stream.of(s.a(), s.b())).toArray(Arm[]::new));
        }
    }

    /**
     * A new column was placed at {@code pos} by {@code placer}: it strings itself as
     * {@link AutoStringing} says, for Rail from the placer's inventory. A sneaking placer, a
     * column that grew, or too little Rail strings nothing.
     */
    public void autoString(ServerLevel level, BlockPos pos, Player placer) {
        var choice = PlacedColumn.autoString(level, pos, placer, network);
        if (choice.isEmpty()) {
            return;
        }
        Arm placed = PylonColumn.arm(pos);
        network.add(Span.of(placed, choice.get().target(), choice.get().cost()));
        RailPurse.spend(placer, choice.get().cost());
        changed(level, placed, choice.get().target());
        SpanGesture.play(level, pos, SoundEvents.CHAIN_PLACE);
        SpanGesture.along(level, pos, PylonColumn.block(choice.get().target()), ParticleTypes.ELECTRIC_SPARK);
    }

    /** The Arm at {@code top} is gone: its Spans are cut and the Rail they cost is dropped there. */
    public void armLost(ServerLevel level, BlockPos top) {
        List<Span> cut = network.removeAllAt(PylonColumn.arm(top));
        if (cut.isEmpty()) {
            return;
        }
        int refund = cut.stream().mapToInt(Span::cost).sum();
        while (refund > 0) {
            int part = Math.min(refund, RailworksRegistries.RAIL.get().getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(RailworksRegistries.RAIL.get(), part);
            level.addFreshEntity(new ItemEntity(level, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, stack));
            refund -= part;
        }
        changed(level, cut.stream().flatMap(s -> Stream.of(s.a(), s.b())).toArray(Arm[]::new));
    }

    /** Save, and resend each touched chunk's Spans to whoever watches it. */
    private void changed(ServerLevel level, Arm... touched) {
        setDirty();
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (Arm arm : touched) {
            chunks.add(new ChunkPos(arm.chunkX(), arm.chunkZ()));
        }
        for (ChunkPos chunk : chunks) {
            SpansPacket packet = new SpansPacket(chunk.x(), chunk.z(), network.touching(chunk.x(), chunk.z()));
            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
                send(player, packet);
            }
        }
    }

    /** Only to a connection that negotiated the channel; sending to one that didn't throws. */
    private static void send(ServerPlayer player, SpansPacket packet) {
        if (player.connection.hasChannel(packet)) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    /** A chunk reached a player: send the Spans with an end in it. */
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        ChunkPos chunk = event.getPos();
        List<Span> touching = of(event.getLevel()).network.touching(chunk.x(), chunk.z());
        send(event.getPlayer(), new SpansPacket(chunk.x(), chunk.z(), touching));
    }
}
