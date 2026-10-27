// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.client.ClientRails;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.Clearance;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Where {@link Clearance} meets a world: which of its blocks are filled, a block a car would hit being
 * one with a collision shape, and the refusal of a block placed into a strung Span's Clearance. Both
 * sides refuse it, the client from its own Spans, so a refused block never shows for a moment.
 */
public final class LevelClearance {

    /** How far a Span's curve may swing out past the box of its two Arms, with a car's height on top. */
    private static final int REACH = 4 + (int) Math.ceil(CarBody.HEIGHT);

    private LevelClearance() {
    }

    /** The blocks of {@code level} as {@link Clearance} asks of them: filled where a car would hit one. */
    public static Clearance.Blocks blocks(BlockGetter level) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        return (x, y, z) -> !level.getBlockState(at.set(x, y, z)).getCollisionShape(level, at).isEmpty();
    }

    /**
     * The first Span of {@code network} whose Clearance holds {@code pos}, or empty. A Pylon segment
     * stacked on one of the Span's own Arms is not in its way: it raises the Arm, and the Span with it.
     */
    static Optional<Span> through(Level level, RailNetwork network, BlockPos pos) {
        Clearance.Cell cell = new Clearance.Cell(pos.getX(), pos.getY(), pos.getZ());
        boolean stacked = PylonColumn.isPylon(level, pos) && PylonColumn.isPylon(level, pos.below());
        for (Span span : network.all()) {
            if (!near(span, pos) || (stacked && span.touches(PylonColumn.arm(pos.below())))) {
                continue;
            }
            if (Clearance.along(network.curve(span)).contains(cell)) {
                return Optional.of(span);
            }
        }
        return Optional.empty();
    }

    /** Whether {@code pos} is within reach of {@code span}'s Clearance at all: a cheap test before the sweep. */
    private static boolean near(Span span, BlockPos pos) {
        return pos.getX() >= Math.min(span.a().x(), span.b().x()) - REACH
                && pos.getX() <= Math.max(span.a().x(), span.b().x()) + REACH
                && pos.getY() >= Math.min(span.a().y(), span.b().y()) - REACH
                && pos.getY() <= Math.max(span.a().y(), span.b().y()) + REACH
                && pos.getZ() >= Math.min(span.a().z(), span.b().z()) - REACH
                && pos.getZ() <= Math.max(span.a().z(), span.b().z()) + REACH;
    }

    /** A block placed into a strung Span's Clearance is refused, the placer told why. */
    static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        RailNetwork network = level instanceof ServerLevel server ? LevelRails.of(server).network() : ClientRails.network();
        List<BlockSnapshot> placed = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots() : List.of(event.getBlockSnapshot());
        for (BlockSnapshot snapshot : placed) {
            BlockPos pos = snapshot.getPos();
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                continue;
            }
            if (through(level, network, pos).isPresent()) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.translatable("message.railworks.in_clearance"), true);
                }
                return;
            }
        }
    }
}
