// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/**
 * The sounds the server played, as NeoForge's {@link PlayLevelSoundEvent} posts each one before it is
 * sent to the players in range: what a test can see of a sound without a client. Each sound is kept
 * with where it played, and a test asks for those in its own structure, so tests running side by
 * side do not hear each other. Only the most recent are kept.
 */
final class HeardSounds {

    /** A sound the server played at {@code at} in {@code level}. */
    record Heard(Level level, SoundEvent sound, Vec3 at) {
    }

    private static final int KEPT = 1024;
    private static final Deque<Heard> RECENT = new ArrayDeque<>();

    private HeardSounds() {
    }

    /** Starts listening: called when game tests register, which a production game never does. */
    static void listen() {
        NeoForge.EVENT_BUS.addListener(HeardSounds::atPosition);
        NeoForge.EVENT_BUS.addListener(HeardSounds::atEntity);
    }

    private static void atPosition(PlayLevelSoundEvent.AtPosition event) {
        heard(event.getLevel(), event.getSound().value(), event.getPosition());
    }

    private static void atEntity(PlayLevelSoundEvent.AtEntity event) {
        heard(event.getLevel(), event.getSound().value(), event.getEntity().position());
    }

    private static synchronized void heard(Level level, SoundEvent sound, Vec3 at) {
        if (level.isClientSide()) {
            return;
        }
        RECENT.addLast(new Heard(level, sound, at));
        while (RECENT.size() > KEPT) {
            RECENT.removeFirst();
        }
    }

    /** Where {@code sound} played in {@code helper}'s structure, oldest first. */
    static synchronized List<Vec3> of(GameTestHelper helper, SoundEvent sound) {
        var bounds = helper.getBounds();
        return RECENT.stream()
                .filter(heard -> heard.level() == helper.getLevel() && heard.sound() == sound && bounds.contains(heard.at()))
                .map(Heard::at)
                .toList();
    }
}
