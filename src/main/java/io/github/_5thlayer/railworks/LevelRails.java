// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import io.github._5thlayer.railworks.network.SpansPacket;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Stringing;
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
 * A level's Spans, saved with it. The rules are {@link Stringing}'s and the storage
 * {@link RailNetwork}'s; this is where the two meet a world.
 */
public final class LevelRails extends SavedData {

    private static final Codec<LevelRails> CODEC = RailNetwork.CODEC
            .xmap(LevelRails::new, data -> data.network)
            .fieldOf("spans")
            .codec();

    public static final SavedDataType<LevelRails> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Railworks.MOD_ID, "spans"),
            LevelRails::new, CODEC);

    private final RailNetwork network;

    public LevelRails() {
        this(new RailNetwork());
    }

    private LevelRails(RailNetwork network) {
        this.network = network;
    }

    public static LevelRails of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public RailNetwork network() {
        return network;
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
        network.rekey(from, PylonColumn.arm(pos));
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
