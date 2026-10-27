// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import io.github._5thlayer.libworks.gallery.GalleryPack;
import io.github._5thlayer.railworks.Railworks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Switches on the Module's dev pack, {@code dev_pack/}, in the {@code client} run and its game-test
 * pack, {@code gametest_pack/}, in {@code gameTestServer}, each when the Module has one: datapacks
 * in {@code src/gametest/resources}, which only the Module's own runs load and the jar leaves out
 * (libworks ADR 0004). The convention plugin names the run in {@code libworks.run}.
 *
 * <p>In the {@code client} run it also turns the Gallery pack on in every world it opens, so
 * {@code /function railworks:gallery/build} works in any dev save; the jar offers it off everywhere
 * else (libworks ADR 0010).
 */
@EventBusSubscriber(modid = Railworks.MOD_ID)
final class DevPacks {

    private DevPacks() {
    }

    @SubscribeEvent
    static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        String folder = switch (System.getProperty("libworks.run", "")) {
            case "client" -> "dev_pack";
            case "gameTestServer" -> "gametest_pack";
            default -> null;
        };
        if (folder == null || !has(folder)) {
            return;
        }
        event.addPackFinders(Identifier.fromNamespaceAndPath(Railworks.MOD_ID, folder), PackType.SERVER_DATA,
                Component.literal(Railworks.MOD_ID + " " + folder), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    @SubscribeEvent
    static void serverStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        String gallery = GalleryPack.id(Railworks.MOD_ID);
        if (!"client".equals(System.getProperty("libworks.run", ""))
                || server.getPackRepository().getSelectedIds().contains(gallery)) {
            return;
        }
        // As a player would, and kept in the save from then on.
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "datapack enable \"" + gallery + "\"");
    }

    private static boolean has(String folder) {
        return ModList.get().getModFileById(Railworks.MOD_ID).getFile().getContents().findFile(folder + "/pack.mcmeta").isPresent();
    }
}
