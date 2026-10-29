// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.railworks.client.ClientRails;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.Clearance;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Stringing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import org.jspecify.annotations.Nullable;

/**
 * Where {@link Clearance} meets a world: which of its blocks are filled, a block a car would hit being
 * one with a collision shape, and the refusal of a block put into a strung Span's Clearance. A block
 * placed by a player, an item or an Enderman is refused on both sides, the client from its own Spans,
 * so a refused block never shows for a moment; a piston's push, a fluid's block and a growing plant
 * are refused on the server. Left in: a falling block landing, a block a dispenser places (a shulker
 * box), lava that turns as it arrives beside water, and water freezing to ice or snow piling up
 * under falling snow, which no NeoForge event reports before the block is set; a tree or a huge
 * mushroom grown from a sapling, growth by bone meal, a melon or a pumpkin set beside its stem and a
 * chorus flower branching sideways, whose events come before the blocks they will fill are chosen,
 * so refusing them would be a guess; and a bamboo sapling's first stalk, which fires none.
 */
public final class LevelClearance {


    private LevelClearance() {
    }

    /** The blocks of {@code level} as {@link Clearance} asks of them: filled where a car would hit one. */
    public static Clearance.Blocks blocks(BlockGetter level) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        return (x, y, z) -> !level.getBlockState(at.set(x, y, z)).getCollisionShape(level, at).isEmpty();
    }

    /** The Spans {@code level}'s side holds: the server's saved ones, or the client's copy of those it was sent. */
    static RailNetwork network(Level level) {
        return level instanceof ServerLevel server ? LevelRails.of(server).network() : ClientRails.network();
    }

    /**
     * The first Span of {@code network} whose Clearance holds {@code pos}, or empty. A Pylon segment
     * stacked on one of the Span's own Arms is not in its way: it raises the Arm, and the Span with it.
     */
    static Optional<Span> through(Level level, RailNetwork network, BlockPos pos) {
        boolean stacked = PylonColumn.isPylon(level, pos) && PylonColumn.isPylon(level, pos.below());
        return through(network, pos, stacked ? PylonColumn.arm(pos.below()) : null);
    }

    /** The first Span of {@code network} not at {@code exempt} whose Clearance holds {@code pos}, or empty. */
    private static Optional<Span> through(RailNetwork network, BlockPos pos, @Nullable Arm exempt) {
        Clearance.Cell cell = new Clearance.Cell(pos.getX(), pos.getY(), pos.getZ());
        for (Span span : network.all()) {
            if (!near(span, pos) || (exempt != null && span.touches(exempt))) {
                continue;
            }
            if (Clearance.along(network.curve(span)).contains(cell)) {
                return Optional.of(span);
            }
        }
        return Optional.empty();
    }

    /**
     * Why a Pylon segment at {@code pos}, on top of the Arm below it, is refused, the action bar's line,
     * or empty where it may go: it would stand in the Clearance of another Arm's Span, or raise one of
     * its own Arm's Spans past a rule ({@link Stringing#raise}). The segment need not be there yet; the
     * Placement Preview asks before.
     */
    public static Optional<Component> stacking(Level level, BlockPos pos) {
        RailNetwork network = network(level);
        Arm from = PylonColumn.arm(pos.below());
        if (through(network, pos, from).isPresent()) {
            return Optional.of(Component.translatable("message.railworks.in_clearance"));
        }
        Arm to = PylonColumn.arm(pos);
        return Stringing.raise(network, blocks(level), from, to).map(outcome -> SpanGesture.reason(outcome, from, to));
    }

    /** Whether {@code pos} is within reach of {@code span}'s Clearance at all: a cheap test before the sweep. */
    private static boolean near(Span span, BlockPos pos) {
        return pos.getX() >= Math.min(span.a().x(), span.b().x()) - Clearance.REACH
                && pos.getX() <= Math.max(span.a().x(), span.b().x()) + Clearance.REACH
                && pos.getY() >= Math.min(span.a().y(), span.b().y()) - Clearance.REACH
                && pos.getY() <= Math.max(span.a().y(), span.b().y()) + Clearance.REACH
                && pos.getZ() >= Math.min(span.a().z(), span.b().z()) - Clearance.REACH
                && pos.getZ() <= Math.max(span.a().z(), span.b().z()) + Clearance.REACH;
    }

    /** A block placed into a strung Span's Clearance is refused, the placer told why. */
    static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        RailNetwork network = network(level);
        List<BlockSnapshot> placed = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots() : List.of(event.getBlockSnapshot());
        for (BlockSnapshot snapshot : placed) {
            BlockPos pos = snapshot.getPos();
            // An item has set its block by now, an Enderman not yet: one block placed is the event's own.
            BlockState state = placed.size() == 1 ? event.getPlacedBlock() : level.getBlockState(pos);
            if (state.getCollisionShape(level, pos).isEmpty()) {
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

    /**
     * A piston that would push or pull a block into a strung Span's Clearance, or put its head there,
     * does not move. The server decides, and a move it refuses is never sent to a client.
     */
    static void onPistonMove(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        RailNetwork network = network(level);
        Direction push = resolver.getPushDirection();
        for (BlockPos pos : resolver.getToPush()) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && through(level, network, pos.relative(push)).isPresent()) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.getPistonMoveType().isExtend && through(level, network, event.getFaceOffsetPos()).isPresent()) {
            event.setCanceled(true);
        }
    }

    /**
     * A block a fluid would make in a strung Span's Clearance is not made, the fluid staying as it was:
     * the obsidian or cobblestone where water reaches lava, and the stone where lava flows down into
     * water. Lava placed or flowing in beside water still turns at once, since NeoForge's
     * {@code LiquidBlock.onPlace} runs vanilla's own check, which fires no event.
     */
    static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!event.getNewState().getCollisionShape(level, pos).isEmpty() && through(level, network(level), pos).isPresent()) {
            event.setNewState(event.getOriginalState());
        }
    }

    /**
     * A plant with a collision shape does not grow into a strung Span's Clearance: a cactus or bamboo
     * stops short of it. The block it grows into is the one the event names, where that is not the
     * plant yet, as for a cactus, or else the one above the plant.
     */
    static void onCropGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState plant = event.getState();
        if (plant.getCollisionShape(level, pos).isEmpty()) {
            return;
        }
        BlockPos into = level.getBlockState(pos).is(plant.getBlock()) ? pos.above() : pos;
        if (through(level, network(level), into).isPresent()) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }
}
