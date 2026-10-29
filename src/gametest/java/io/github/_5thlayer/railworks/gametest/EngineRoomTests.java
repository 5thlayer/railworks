// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.List;

import io.github._5thlayer.railworks.HatchBlock;
import io.github._5thlayer.railworks.HatchMode;
import io.github._5thlayer.railworks.LevelRails;
import io.github._5thlayer.railworks.LocomotiveEntity;
import io.github._5thlayer.railworks.PylonColumn;
import io.github._5thlayer.railworks.RailworksRegistries;
import io.github._5thlayer.railworks.rail.Arm;
import io.github._5thlayer.railworks.rail.CargoCar;
import io.github._5thlayer.railworks.rail.Locomotive;
import io.github._5thlayer.railworks.rail.Place;
import io.github._5thlayer.railworks.rail.Span;
import io.github._5thlayer.railworks.rail.SpanRules;
import io.github._5thlayer.railworks.rail.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * A Locomotive standing at a car spot, not the leading car's place, is fuelled from beside its own
 * Engine room (#74): a straight line along z 3 from x -11 to 11, out past the structure's west edge,
 * and a Station south of its east end in the row, serving Trains heading east, its leading car's place
 * beside x 7 to 11 and its car spots back west, the first beside x 2 to 6 and the second beside x -3
 * to 1. A shuttle, Locomotive, Cargo car and Locomotive, stands there automatic, its back Locomotive,
 * facing west, at the second car spot, its Engine room over x -1. That the leading Locomotive is
 * fuelled from beside its Engine room and not its cab or bay is {@code LogisticsTests}', and a
 * Charger's {@code ElectricTests}'. Prefixed {@code engine_room_}, as they are.
 */
final class EngineRoomTests {

    /** The line's Pylons along x, none under the lead mark at x 9. */
    private static final int[] PYLONS = {-11, 0, 11};
    private static final int Z = 3;
    private static final BlockPos STATION = new BlockPos(11, 3, 5);
    /** Beside the second car spot's middle block, the back Locomotive's Engine room. */
    private static final BlockPos ENGINE_ROOM = new BlockPos(-1, 3, 5);
    /** Beside the second car spot's last block nearest the Station, the back Locomotive's bay, since it faces west. */
    private static final BlockPos BAY = new BlockPos(1, 3, 5);

    private EngineRoomTests() {
    }

    static void register(RailworksGameTests.Registrar tests) {
        // Its line runs out past the structure.
        tests.testOnItsOwn("engine_room_the_back_locomotive_of_a_shuttle_is_fuelled_from_beside_its_own_engine_room", 400,
                EngineRoomTests::backLocomotive);
    }

    private static void backLocomotive(GameTestHelper helper) {
        Span last = line(helper);
        LogisticsTests.station(helper, STATION);
        for (BlockPos pos : List.of(ENGINE_ROOM, BAY)) {
            helper.setBlock(pos, RailworksRegistries.HATCH.get().defaultBlockState().setValue(HatchBlock.MODE, HatchMode.PROVIDER));
        }
        LogisticsTests.insert(LogisticsTests.record(helper, ENGINE_ROOM), new ItemStack(Items.COAL, 64));
        LogisticsTests.insert(LogisticsTests.record(helper, BAY), new ItemStack(Items.CHARCOAL, 64));
        Locomotive east = Locomotive.burner();
        CargoCar wagon = CargoCar.create();
        Locomotive west = Locomotive.burner();
        // The leading Locomotive's middle on the lead mark, two short of the line's east end.
        Train train = Train.of(new Place(last, last.b(), 2.0), List.of(east, wagon))
                .coupled(LevelRails.of(helper.getLevel()).network(), Train.End.BACK, west, true);
        LocomotiveEntity leading = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, east);
        LogisticsTests.spawn(helper, RailworksRegistries.CARGO_CAR_ENTITY.get(), train, wagon);
        LocomotiveEntity back = LogisticsTests.spawn(helper, RailworksRegistries.LOCOMOTIVE_ENTITY.get(), train, west);
        train.setAutomatic(true);
        ItemStack none = ItemStack.EMPTY;
        helper.startSequence()
                .thenWaitUntil(() -> LocomotiveTests.expectSlots(helper, back, List.of(new ItemStack(Items.COAL, 64), none, none, none)))
                .thenIdle(40)
                .thenExecute(() -> {
                    LocomotiveTests.expectSlots(helper, back, List.of(new ItemStack(Items.COAL, 64), none, none, none));
                    LocomotiveTests.expectSlots(helper, leading, List.of(none, none, none, none));
                    if (LogisticsTests.held(LogisticsTests.record(helper, BAY)) != 64) {
                        helper.fail("the provider beside the back Locomotive's bay gave it fuel", BAY);
                    }
                })
                .thenExecute(() -> forceLoad(helper, false))
                .thenSucceed();
    }

    /** The line, the structure's roof cleared over it and its chunks loaded: its last Span, the one to the east end. */
    private static Span line(GameTestHelper helper) {
        forceLoad(helper, true);
        for (int x = PYLONS[0]; x <= PYLONS[PYLONS.length - 1]; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
            }
        }
        for (int x : PYLONS) {
            helper.setBlock(new BlockPos(x, 1, Z), RailworksRegistries.PYLON.get().defaultBlockState());
        }
        Span span = null;
        for (int i = 0; i + 1 < PYLONS.length; i++) {
            Arm one = PylonColumn.arm(helper.absolutePos(new BlockPos(PYLONS[i], 1, Z)));
            Arm other = PylonColumn.arm(helper.absolutePos(new BlockPos(PYLONS[i + 1], 1, Z)));
            span = Span.of(one, other, SpanRules.cost(one, other));
            LevelRails.of(helper.getLevel()).network().add(span);
        }
        return span;
    }

    /** Forces the chunks under the line loaded, or lets them go. */
    private static void forceLoad(GameTestHelper helper, boolean forced) {
        BlockPos west = helper.absolutePos(new BlockPos(PYLONS[0], 1, 2));
        BlockPos east = helper.absolutePos(new BlockPos(PYLONS[PYLONS.length - 1], 1, 6));
        for (int cx = Math.min(west.getX(), east.getX()) >> 4; cx <= Math.max(west.getX(), east.getX()) >> 4; cx++) {
            for (int cz = Math.min(west.getZ(), east.getZ()) >> 4; cz <= Math.max(west.getZ(), east.getZ()) >> 4; cz++) {
                helper.getLevel().setChunkForced(cx, cz, forced);
            }
        }
    }
}
