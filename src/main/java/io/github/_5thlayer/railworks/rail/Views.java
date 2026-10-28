// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Which cars a level shows (ADR 0004, amended in #6): a car has a view where its Train puts it once
 * that place can show one, and loses it once the place no longer can, unless a player rides it, since
 * a Locomotive can outrun the chunks loading under its rider. A car no Train holds has no view. A
 * Train is ticked whether its cars are in view or not, so this only says what the level shows, never
 * where a car is.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Views {

    /** What the level should change: the cars to show and the views to take away, and where each car of a Train stands. */
    public record Change(List<Shown> show, List<UUID> takeAway, Map<UUID, Place.Point> at) {
    }

    /** A car to show: its Train, and the car. */
    public record Shown(Train train, Car car) {
    }

    private Views() {
    }

    /**
     * What to show and take away: every car of {@code trains} whose place {@code showable} says can show
     * a view and that has none in {@code viewed} is shown; every car in {@code viewed} that no Train
     * holds, or whose place cannot show it and that no player rides, as {@code ridden} says, is taken away.
     */
    public static Change plan(RailNetwork network, Trains trains, Predicate<Place.Point> showable, Set<UUID> viewed,
                              Set<UUID> ridden) {
        List<Shown> show = new ArrayList<>();
        List<UUID> takeAway = new ArrayList<>();
        Map<UUID, Place.Point> at = new HashMap<>();
        for (Train train : trains.all()) {
            for (Car car : train.cars()) {
                Place.Point point = train.placeOf(network, car).point(network);
                at.put(car.id(), point);
                boolean canShow = showable.test(point);
                if (viewed.contains(car.id())) {
                    if (!canShow && !ridden.contains(car.id())) {
                        takeAway.add(car.id());
                    }
                } else if (canShow) {
                    show.add(new Shown(train, car));
                }
            }
        }
        for (UUID car : viewed) {
            if (!at.containsKey(car)) {
                takeAway.add(car);
            }
        }
        return new Change(List.copyOf(show), List.copyOf(takeAway), Map.copyOf(at));
    }
}
