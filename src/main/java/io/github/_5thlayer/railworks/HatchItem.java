// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.Placements;
import io.github._5thlayer.groundworks.PlansPlacement;
import io.github._5thlayer.railworks.rail.CarBody;
import io.github._5thlayer.railworks.rail.Platform;
import io.github._5thlayer.railworks.rail.RailNetwork;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A hatch in hand, a provider's or a requester's, which places the hatch as that and which the hatch
 * drops as what it is. Sneak-used in the air, the whole stack turns into the other. The block's own
 * item, the one its recipe makes, is the provider's. It places only beside a car spot of a Station's
 * platform ({@link Platform#joined}), so the Placement Preview and the click ask one rule; a refused
 * click says why on the action bar. While one is held, the free blocks beside the car spots of the
 * platforms near are drawn.
 */
public final class HatchItem extends BlockItem implements PlansPlacement {

    /** Half the gap between two coupled cars' bodies, by which a drawn car spot falls short of each of its ends. */
    private static final double HALF_GAP = (CarBody.PITCH - CarBody.LENGTH) / 2.0;

    private final HatchMode mode;

    public HatchItem(Block block, HatchMode mode, Item.Properties properties) {
        super(block, properties);
        this.mode = mode;
    }

    /** What a hatch it places is. */
    public HatchMode mode() {
        return mode;
    }

    /** The item a hatch that is {@code mode} is in hand. */
    public static HatchItem of(HatchMode mode) {
        return mode == HatchMode.REQUESTER ? RailworksRegistries.REQUESTER_HATCH_ITEM.get() : RailworksRegistries.HATCH_ITEM.get();
    }

    /**
     * Which Station a hatch at {@code pos} joins or would join, and at which car spot: of the level's
     * Stations on a server, and of the Station blocks loaded near it on a client.
     */
    static Optional<Platform.Joined> joinedAt(Level level, BlockPos pos) {
        return Platform.joined(StationItem.networkOf(level), StationItem.stationsNear(level, pos),
                pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Where a hatch could go near {@code pos}, as a held hatch draws it, car spot by car spot: the
     * free blocks beside a spot of the platform of a Station near, empty enough to place in, the two
     * on one side of the Rail as one box, or the one of them still free, each falling half a car gap
     * short of its spot's ends, so the spots read as the cars that will stand there.
     */
    public static List<AABB> freeSpots(Level level, BlockPos pos) {
        RailNetwork network = StationItem.networkOf(level);
        List<AABB> boxes = new ArrayList<>();
        for (Station station : StationItem.stationsNear(level, pos)) {
            Platform.of(network, station).filter(platform -> !platform.spots().isEmpty()).ifPresent(platform -> {
                List<Platform.Beside> beside = platform.besideBlocks(network);
                // A Straight leg runs along x or z: the spots run that way.
                Span leg = platform.spots().getFirst().middle().span();
                boolean alongX = Math.abs(leg.b().x() - leg.a().x()) >= Math.abs(leg.b().z() - leg.a().z());
                List<Platform.Beside> paired = new ArrayList<>();
                for (Platform.Beside one : beside) {
                    if (paired.contains(one)) {
                        continue;
                    }
                    // Its pair: the other block beside the same spot, on the same side of the Rail.
                    Platform.Beside other = beside.stream()
                            .filter(b -> b != one && b.spot() == one.spot() && b.y() == one.y()
                                    && Math.abs(b.x() - one.x()) + Math.abs(b.z() - one.z()) == 1)
                            .findFirst().orElse(one);
                    paired.add(one);
                    paired.add(other);
                    // Along the Line, half a car gap short of each end of the spot.
                    AABB spot = new AABB(block(one)).minmax(new AABB(block(other)));
                    spot = alongX ? spot.deflate(HALF_GAP, 0, 0) : spot.deflate(0, 0, HALF_GAP);
                    boolean oneFree = level.getBlockState(block(one)).canBeReplaced();
                    boolean otherFree = level.getBlockState(block(other)).canBeReplaced();
                    if (oneFree && otherFree) {
                        boxes.add(spot);
                    } else if (oneFree || otherFree) {
                        boxes.add(new AABB(block(oneFree ? one : other)).intersect(spot));
                    }
                }
            });
        }
        return boxes;
    }

    private static BlockPos block(Platform.Beside beside) {
        return new BlockPos(beside.x(), beside.y(), beside.z());
    }

    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        PlacementPlan plan = Placements.vanillaPlan(this, context);
        if (plan == null || plan.isRefused() || joinedAt(context.getLevel(), context.getClickedPos()).isPresent()) {
            return plan;
        }
        return PlacementPlan.refused(context.getClickedPos(), getBlock().defaultBlockState(), RailworksRefusal.HATCH_OFF_PLATFORM);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (joinedAt(context.getLevel(), context.getClickedPos()).isPresent()) {
            return super.place(context);
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.railworks.hatch_off_platform"), true);
        }
        return InteractionResult.FAIL;
    }

    /** Sneak-used in the air, the whole stack turns into the other mode's, heard as a shulker box opening; otherwise it does what any block in hand does. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), HatchBlock.SWITCH_SOUND, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        return InteractionResult.SUCCESS.heldItemTransformedTo(player.getItemInHand(hand).transmuteCopy(of(mode.other())));
    }

    /** Only the provider's is the block's own item, the one it is picked and named as when nothing says otherwise. */
    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        if (mode == HatchMode.PROVIDER) {
            super.registerBlocks(map, item);
        }
    }
}
