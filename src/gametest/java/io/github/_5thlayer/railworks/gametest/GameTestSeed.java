// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.railworks.Railworks;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Lays the game tests out alike every run of {@code gameTestServer}, before the first tick lays them
 * out at all. Vanilla's game-test server draws the grid's first corner from the overworld's random,
 * anywhere within 15 million blocks, and runs its batches in the order a {@code groupingBy} over the
 * environments' holders gives, which hash by identity and so change from run to run. The grid fills
 * batch after batch, so each run put a plot beside different neighbours, against different chunk
 * borders and over the ground other tests left behind, and a test that builds past its plot could
 * pass or fail by where it landed. This seeds that random and sorts the batches by environment.
 * {@code -Drailworks.gametest.seed=<n>} lays the tests out from another seed, to look for a test
 * that holds only where the default puts it; the seed is logged either way.
 *
 * <p>The batches are vanilla's private {@code GameTestServer.testBatches}, sorted by reflection: a
 * mixin would need a config in the shipped {@code neoforge.mods.toml}.
 */
@EventBusSubscriber(modid = Railworks.MOD_ID)
final class GameTestSeed {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DEFAULT_SEED = 5L;

    private GameTestSeed() {
    }

    @SubscribeEvent
    static void serverStarted(ServerStartedEvent event) {
        if (!"gameTestServer".equals(System.getProperty("libworks.run", ""))) {
            return;
        }
        long seed = Long.getLong("railworks.gametest.seed", DEFAULT_SEED);
        ServerLevel overworld = event.getServer().overworld();
        overworld.getRandom().setSeed(seed);
        LOGGER.info("Game tests laid out from seed {}", seed);
        if (event.getServer() instanceof GameTestServer server) {
            sortBatches(server);
        }
    }

    /** The server's batches by environment, then by their index within it. */
    private static void sortBatches(GameTestServer server) {
        try {
            Field field = GameTestServer.class.getDeclaredField("testBatches");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<GameTestBatch> batches = new ArrayList<>((List<GameTestBatch>) field.get(server));
            batches.sort(Comparator.comparing((GameTestBatch batch) -> batch.environment().getRegisteredName())
                    .thenComparingInt(GameTestBatch::index));
            field.set(server, batches);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot sort the game-test batches", e);
        }
    }
}
