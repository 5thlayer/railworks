// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * The Module's sounds, those of a hatch's Bellows and Loading arm, are vanilla's sounds retuned: each
 * one {@code RailworksRegistries} registers has a {@code sounds.json} entry naming only vanilla's sound
 * files, so the Module ships no audio of its own, and a subtitle the lang file names.
 */
class SoundsTest {

    /** The sound events {@code RailworksRegistries} registers. */
    private static final Set<String> SOUNDS = Set.of(
            "hatch.bellows.reach",
            "hatch.bellows.fold",
            "hatch.loading_arm.reach",
            "hatch.loading_arm.land",
            "hatch.loading_arm.fold");

    private static final Path ASSETS = Path.of("src/main/resources/assets", Railworks.MOD_ID);

    @Test
    void eachSoundIsVanillasRetunedWithASubtitle() throws IOException {
        JsonObject sounds = read(ASSETS.resolve("sounds.json"));
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        assertEquals(SOUNDS, sounds.keySet());
        for (String name : SOUNDS) {
            JsonObject sound = sounds.getAsJsonObject(name);
            String subtitle = sound.get("subtitle").getAsString();
            assertTrue(lang.has(subtitle), name + "'s subtitle " + subtitle + " is not in en_us.json");
            assertTrue(!sound.getAsJsonArray("sounds").isEmpty(), name + " plays nothing");
            for (JsonElement file : sound.getAsJsonArray("sounds")) {
                String of = file.isJsonObject() ? file.getAsJsonObject().get("name").getAsString() : file.getAsString();
                assertTrue(of.startsWith("minecraft:"), name + " plays " + of + ", not one of vanilla's");
            }
        }
    }

    private static JsonObject read(Path path) throws IOException {
        // Gradle runs the tests from the project directory.
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
