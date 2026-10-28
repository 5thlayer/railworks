// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.railworks.network.SpansPacket;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.Car;
import io.github._5thlayer.railworks.rail.Goods;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Logistics;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Stringing;
import io.github._5thlayer.railworks.rail.Throttle;
import io.github._5thlayer.railworks.rail.Train;
import io.github._5thlayer.railworks.rail.Trains;
import io.github._5thlayer.railworks.rail.Views;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A level's Spans and its Trains, saved with it. The rules are {@link Stringing}'s and the storage
 * {@link RailNetwork}'s and {@link Trains}'; this is where they meet a world. Every Train ticks from
 * the level's tick, in view or not (ADR 0004, amended in #6), and an abstract Train is simply one
 * with none of its cars in view. The level shows a car where its Train puts it once that place ticks
 * entities, as {@link Views} says, and knows each car's view, so a Train's tick can read its drivers'
 * keys and move every car it has in view; its cars' cargo and fuel are their records in
 * {@link LevelCarRecords}, in view or not. Trains never keep a chunk loaded. Its {@link Logistics},
 * which runs the automatic Trains' jobs between the level's hatches, ticks with the level and is not
 * saved.
 */
public final class LevelRails extends SavedData {

    private static final Codec<LevelRails> CODEC = RecordCodecBuilder.create(i -> i.group(
            RailNetwork.CODEC.fieldOf("spans").forGetter(data -> data.network),
            Trains.CODEC.optionalFieldOf("trains").forGetter(data -> Optional.of(data.trains)),
            Fallen.CODEC.listOf().optionalFieldOf("fallen", List.of()).forGetter(data -> List.copyOf(data.fallen))
    ).apply(i, (network, trains, fallen) -> new LevelRails(network, trains.orElseGet(Trains::new), fallen)));

    public static final SavedDataType<LevelRails> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "spans"),
            LevelRails::new, CODEC);

    /**
     * A car of a Train that fell off the Rail out of view (#6), standing where it was, {@code at} in the
     * world, until its place ticks entities and it comes off the Rail there.
     */
    record Fallen(UUID car, CarKind kind, Vec3 at) {

        static final Codec<Fallen> CODEC = RecordCodecBuilder.create(i -> i.group(
                Car.ID_CODEC.fieldOf("car").forGetter(Fallen::car),
                CarKind.CODEC.fieldOf("kind").forGetter(Fallen::kind),
                Vec3.CODEC.fieldOf("at").forGetter(Fallen::at)
        ).apply(i, Fallen::new));
    }

    private final RailNetwork network;
    private final Trains trains;
    private final List<Fallen> fallen;
    /** Each car's view, by its car's id. */
    private final Map<UUID, CarEntity> views = new HashMap<>();
    /** Entities added to the level that are no car's view, dropped or broken at its next tick. */
    private final List<CarEntity> stale = new ArrayList<>();
    /** Where each car of a Train stood in the world after the level's last tick. */
    private Map<UUID, Vec3> standing = Map.of();
    private final Logistics<Item> logistics = new Logistics<>();
    /** The hatches that join a Station as logistics last asked, in the order they were placed; asked afresh once a plan. */
    private List<Logistics.Hatch<Item>> joined = List.of();
    /** The Chargers that join a Station, asked afresh with the hatches. */
    private List<Logistics.Charger> joinedChargers = List.of();
    private int ticks;

    public LevelRails() {
        this(new RailNetwork(), new Trains(), List.of());
    }

    private LevelRails(RailNetwork network, Trains trains, List<Fallen> fallen) {
        this.network = network;
        this.trains = trains;
        this.fallen = new ArrayList<>(fallen);
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

    /** {@code car} is its car's view: its Train's tick moves it. */
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

    /** The view of {@code car}, or null when it has none. */
    public @Nullable CarEntity view(Car car) {
        return viewOf(car.id());
    }

    /** The view of the car with this id, or null when it has none. */
    @Nullable CarEntity viewOf(UUID car) {
        CarEntity view = views.get(car);
        return view == null || view.isRemoved() ? null : view;
    }

    /** {@code entity} was added to the level as no car's view: the level's next tick drops it, or breaks it. */
    void markStale(CarEntity entity) {
        stale.add(entity);
    }

    /** A new Train, set on the Rail: it is saved and ticks from now on. */
    public void add(Train train) {
        trains.add(train);
        setDirty();
    }

    /**
     * The level ticked: every Train, once, in view or not, then its views, shown and taken away as
     * their places tick entities or stop. Frozen by {@code /tick freeze}, as entities are.
     */
    private void tickTrains(ServerLevel level) {
        dropStale(level);
        for (Train train : trains.all()) {
            // Not one a Train before it this tick has parted from or closed up into.
            if (trains.of(train.cars().getFirst().id()) == train) {
                step(level, train);
            }
        }
        showViews(level);
    }

    /**
     * One tick of {@code train}, as the level ticks it: off the Rail when a Span it stands on was cut or
     * its Arm moved, else its {@link #tick}.
     */
    public void step(ServerLevel level, Train train) {
        if (!train.onRail(network)) {
            derail(level, train);
            return;
        }
        tick(level, train);
    }

    /**
     * Each entity added as no car's view goes: dropped, breaking nothing, when a Train holds its car,
     * which the level shows by a view of its own; broken, dropping itself and what its car held, when
     * none does, as a car whose Train is gone, or one summoned with no car.
     */
    private void dropStale(ServerLevel level) {
        List<CarEntity> found = List.copyOf(stale);
        stale.clear();
        for (CarEntity entity : found) {
            if (entity.isRemoved()) {
                continue;
            }
            UUID car = entity.carId();
            if (car != null && trains.of(car) != null) {
                entity.drop();
            } else {
                entity.breakOff(level);
            }
        }
    }

    /**
     * Shows each car whose place ticks entities and that has no view, and takes away each view whose
     * place does not, but one a player rides, which it ticks itself where the level did not, so its
     * rider is never frozen or left behind. A fallen car comes off the Rail once its place ticks.
     */
    private void showViews(ServerLevel level) {
        Set<UUID> viewed = new LinkedHashSet<>();
        Set<UUID> ridden = new LinkedHashSet<>();
        views.forEach((car, view) -> {
            if (!view.isRemoved()) {
                viewed.add(car);
                if (view.riddenByPlayer()) {
                    ridden.add(car);
                }
            }
        });
        Views.Change change = Views.plan(network, trains, point -> ticks(level, CarPlacement.railPoint(point)), viewed, ridden);
        Map<UUID, Vec3> now = new HashMap<>();
        change.at().forEach((car, point) -> now.put(car, CarPlacement.railPoint(point)));
        standing = now;
        for (UUID car : change.takeAway()) {
            CarEntity view = viewOf(car);
            if (view != null) {
                view.takeAway(level);
            }
        }
        for (Views.Shown shown : change.show()) {
            show(level, shown.train(), shown.car());
        }
        for (UUID car : ridden) {
            CarEntity view = viewOf(car);
            if (view != null && !view.tickedThisTick()) {
                level.guardEntityTick(level::tickNonPassenger, view);
            }
        }
        if (fallen.removeIf(car -> {
            if (!ticks(level, car.at())) {
                return false;
            }
            CarEntity.fallOff(level, car);
            return true;
        })) {
            setDirty();
        }
    }

    /** Whether {@code at} ticks entities, its chunk's saved ones loaded, so a view there is shown and moves. */
    private static boolean ticks(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        return level.isPositionEntityTicking(pos) && level.areEntitiesActuallyLoadedAndTicking(ChunkPos.containing(pos));
    }

    /** Makes {@code car}'s view where {@code train} puts it, with the mob its record says rode it. */
    private void show(ServerLevel level, Train train, Car car) {
        CarKind kind = CarKind.of(car.kind());
        CarEntity view = kind == null ? null : kind.entityType().create(level, EntitySpawnReason.LOAD);
        if (view == null) {
            return;
        }
        view.setCar(train, car);
        level.addFreshEntity(view);
        view.seatRider(level);
    }

    /**
     * Keeps in each car's record the mob riding its view now, as the server stops, so it rides on, as
     * it is, when the server starts again: no chunk saves it with the view.
     */
    void keepRiders(ServerLevel level) {
        views.values().stream().filter(view -> !view.isRemoved()).toList().forEach(view -> view.keepRider(level));
    }

    /** The server is stopping: each level keeps its views' riders before it is saved for the last time. */
    public static void onServerStopping(ServerStoppingEvent event) {
        event.getServer().getAllLevels().forEach(level -> of(level).keepRiders(level));
    }

    /**
     * One tick of {@code train}, from the level's tick: the keys of the first player driving one
     * of its Locomotives, or, with no driver pressing one, its job while it is automatic; each
     * Locomotive's fuel slot; and every car it has in view set where the model now puts it. A driver's
     * keys switch an automatic Train to manual, as in Factorio. Left at rest by a Station with nobody
     * driving it, it settles onto the Station's mark. Moving, each car in view strikes what is in the
     * space it swept.
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
        if (throttle != Throttle.NONE && train.automatic()) {
            train.setAutomatic(false);
            setDirty();
        }
        double speed = train.speed();
        Place head = train.head();
        Map<CarEntity, Vec3> before = new HashMap<>();
        for (Car car : train.cars()) {
            CarEntity view = view(car);
            if (view != null) {
                before.put(view, view.position());
            }
        }
        Predicate<Span> taken = trains.takenFrom(network, train);
        ToLongFunction<Locomotive> ignite = locomotive -> ignite(level, locomotive);
        if (train.automatic()) {
            logistics.drive(network, train, goods(level), ignite, taken);
        } else {
            train.tick(network, throttle, driven, Locomotive.power(goods(level), ignite), taken);
        }
        if (train.cars().stream().noneMatch(car -> view(car) instanceof LocomotiveEntity locomotive && locomotive.driven())) {
            // At rest within a Station's stretch, nobody driving it, it eases onto the Station's mark,
            // its cars onto the car spots.
            LevelStations.of(level).stations().settle(network, train, taken);
        }
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
        if (train.speed() != 0.0) {
            before.forEach((view, from) -> view.strike(level, from));
        }
    }

    /**
     * Burns a fuel item from {@code locomotive}'s fuel slots, its record's (ADR 0008): through its view,
     * which drops what a fuel leaves behind where it is, or, out of view, only a fuel that leaves nothing.
     */
    private long ignite(ServerLevel level, Locomotive locomotive) {
        if (view(locomotive) instanceof LocomotiveEntity view) {
            return view.ignite();
        }
        FuelSlots fuel = FuelSlots.of(level, locomotive);
        return fuel == null ? 0L : fuel.igniteLeavingNothing();
    }

    // ---- logistics ----

    /** {@code train}'s status line, as its Locomotive's menu and Jade show it. */
    public Logistics.Status<Item> status(ServerLevel level, Train train) {
        return logistics.status(train, goods(level));
    }

    /** The goods of {@code level} as its logistics sees them: what its hatches and its cars' records hold. */
    public Goods<Item> goods(ServerLevel level) {
        return new LevelGoods(level);
    }

    /**
     * The level ticked: its Trains, unless the game is frozen, and its logistics, with the hatches and
     * Chargers that join a Station asked afresh once a plan.
     */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LevelRails rails = of(level);
            if (level.tickRateManager().runsNormally()) {
                rails.tickTrains(level);
            }
            rails.tickLogistics(level);
        }
    }

    private void tickLogistics(ServerLevel level) {
        // In step with the logistics' own count, so each plan sees the hatches as they are that tick.
        if (++ticks % Logistics.PLAN_TICKS == 0) {
            joined = joinedHatches(level);
            joinedChargers = joinedChargers(level);
        }
        logistics.tick(network, trains, joined, joinedChargers, goods(level));
    }

    /**
     * Every Charger of the level that joins a Station, as logistics sees it, in the order they were
     * placed. A record whose block is loaded but no longer that Charger is stale, and goes.
     */
    private List<Logistics.Charger> joinedChargers(ServerLevel level) {
        Chargers chargers = LevelChargers.of(level);
        List<Logistics.Charger> joined = new ArrayList<>();
        for (Chargers.Charger charger : chargers.all()) {
            BlockPos pos = charger.pos();
            if (chargers.dropIfStale(level, pos)) {
                continue;
            }
            PlatformsNear.joined(level, pos).ifPresent(at -> joined.add(new Logistics.Charger(pos.getX(), pos.getY(),
                    pos.getZ(), at.station(), at.spot(), charger.creative())));
        }
        return List.copyOf(joined);
    }

    /** Every hatch of the level that joins a Station, as logistics sees it, in the order they were placed. */
    private List<Logistics.Hatch<Item>> joinedHatches(ServerLevel level) {
        List<Logistics.Hatch<Item>> joined = new ArrayList<>();
        for (Hatches.Hatch hatch : LevelHatches.of(level).all()) {
            BlockPos pos = hatch.pos();
            PlatformsNear.joined(level, pos).ifPresent(at -> joined.add(new Logistics.Hatch<>(pos.getX(), pos.getY(),
                    pos.getZ(), at.station(), at.spot(), hatch.mode() == HatchMode.PROVIDER, Goods.Kind.ITEMS, hatch.request())));
        }
        return List.copyOf(joined);
    }

    /**
     * {@code train} fell off the Rail: it is gone, and each of its cars in view breaks, dropping itself
     * and what it holds. Each out of view stands where it was until its place ticks, so nothing drops
     * into a chunk that is not loaded and no car is lost.
     */
    void derail(ServerLevel level, Train train) {
        trains.remove(train);
        setDirty();
        for (Car car : train.cars()) {
            CarEntity view = view(car);
            if (view != null) {
                view.breakOff(level);
            } else {
                Vec3 at = standing.get(car.id());
                CarKind kind = CarKind.of(car.kind());
                if (kind != null) {
                    fallen.add(new Fallen(car.id(), kind, at != null ? at : CarPlacement.railPoint(train.head().point(network))));
                }
            }
        }
    }

    /** Where the car with this id stood in the world after the level's last tick, in view or not; null for no car of a Train. */
    public @Nullable Vec3 standing(UUID car) {
        return standing.get(car);
    }

    /** The cars that fell off the Rail out of view and have not yet come off it, by id. */
    public List<UUID> fallen() {
        return fallen.stream().map(Fallen::car).toList();
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
        Stringing.Outcome outcome = Stringing.click(network, LevelClearance.blocks(level), from, to, railHeld);
        if (outcome.result() == Stringing.Result.STRUNG || outcome.result() == Stringing.Result.CUT) {
            changed(level, from, to);
        }
        if (outcome.result() == Stringing.Result.CUT) {
            derailOffRail(level);
        }
        return outcome;
    }

    /**
     * Strings a Span from the Arm at {@code anchor} to the one at {@code target} for no Rail, under the
     * rules a player's click is, its Clearance included: {@code /railworks span}. Only a {@code STRUNG}
     * changes anything; a Span already there is left, as {@code CUT}, and a refusal is returned as the
     * click's would be.
     */
    public Stringing.Outcome string(ServerLevel level, BlockPos anchor, BlockPos target) {
        Arm from = PylonColumn.arm(anchor);
        Arm to = PylonColumn.arm(target);
        Stringing.Outcome outcome = Stringing.decide(network, LevelClearance.blocks(level), from, to, Integer.MAX_VALUE);
        if (outcome.result() == Stringing.Result.STRUNG) {
            network.add(Span.of(from, to, outcome.rail()));
            changed(level, from, to);
        }
        return outcome;
    }

    /**
     * A segment was placed at {@code pos}. On top of a column it is the new top: the Arm moves up
     * and keeps its Spans. Under another column it joins two, and the lower column's Arm is gone.
     * A segment raising a Span past a rule is refused before it is placed ({@code PylonItem}); one
     * put there some other way, by a command, cuts the Arm's Spans and drops their Rail, as losing
     * the Arm would, so no Span stands past a rule.
     */
    public void placed(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!PylonColumn.isPylon(level, below)) {
            return;
        }
        if (!PylonColumn.raisesArm(level, pos)
                || Stringing.raise(network, LevelClearance.blocks(level), PylonColumn.arm(below), PylonColumn.arm(pos)).isPresent()) {
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
        derailOffRail(level);
    }

    /**
     * Each Train a Span was just cut under falls off the Rail now, not at the level's next tick, so a
     * Span strung again in the same tick, as a command rebuilding a line strings it, is never held by
     * a Train that is no longer on it.
     */
    private void derailOffRail(ServerLevel level) {
        for (Train train : trains.all()) {
            if (trains.of(train.cars().getFirst().id()) == train && !train.onRail(network)) {
                derail(level, train);
            }
        }
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
