// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A Station's platform (ADR 0008): the Straight leg behind it on its serving way, where a Train
 * stopped there stands. A Train stops with its leading car at the Station, the car's front at the
 * Station block's front edge, and its cars stand back along the platform at car spots
 * {@link #SPOT} blocks apart, the first its leading car's. The platform runs back up to
 * {@link #cars()} spots, ending where the Straight leg ends, at a bend or a line's end, so no spot is
 * where a car stands on a bend.
 *
 * <p>Asked afresh of the network each time, as a Station's Span is, so a Span strung or cut later
 * lengthens or shortens it.
 *
 * <p>Pure: no Minecraft types.
 */
public final class Platform {

    /** The most car spots a platform has, whatever {@code railworks-server.toml} says. */
    public static final int MAX_CARS = 4;
    /** How long a car spot is along the Line, in blocks: a car's pitch, coupling to coupling. */
    public static final double SPOT = 2.0;
    /** How far a block beside a platform's car spots may be from its Station along x, y or z, in blocks. */
    public static final double REACH = MAX_CARS * SPOT + 3.0;
    /** Slack for the floating point, so pieces that only touch end to end do not overlap. */
    private static final double EPSILON = 1e-6;

    private static volatile int cars = MAX_CARS;

    /** A car spot: its place in the platform, 0 the leading car's, and its middle, facing the serving way. */
    public record CarSpot(int index, Place middle) {
    }

    /** A hatch's place: the Station whose platform it stands beside, and the car spot it is beside. */
    public record Joined(Station station, int spot) {
    }

    /** A block beside a car spot, where a hatch may stand: the spot's index and the block's position. */
    public record Beside(int spot, int x, int y, int z) {
    }

    /** Some of one Span the platform covers, as how far along it from its first end, low to high. */
    private record Piece(Span span, double low, double high) {

        boolean overlaps(Piece other) {
            return sameSpan(span, other.span)
                    && Math.min(high, other.high) - Math.max(low, other.low) > EPSILON;
        }

        boolean holds(double fromA) {
            return fromA >= low - EPSILON && fromA <= high + EPSILON;
        }
    }

    private final Station station;
    private final Place front;
    private final Place leadMark;
    private final List<CarSpot> spots;
    /** Each spot's pieces of the Line, by index. */
    private final List<List<Piece>> spotPieces;
    /** What it covers of the Line: its spots, or at least the Station's own block's length. */
    private final List<Piece> covered;

    private Platform(Station station, Place front, Place leadMark, List<CarSpot> spots,
                     List<List<Piece>> spotPieces, List<Piece> covered) {
        this.station = station;
        this.front = front;
        this.leadMark = leadMark;
        this.spots = spots;
        this.spotPieces = spotPieces;
        this.covered = covered;
    }

    /** Set by {@code RailworksConfig} when the server config loads or reloads; capped at {@link #MAX_CARS}. */
    public static void configure(int cars) {
        if (cars < 1) {
            throw new IllegalArgumentException("platform cars " + cars);
        }
        Platform.cars = Math.min(cars, MAX_CARS);
    }

    /** The most car spots a platform has on this server. */
    public static int cars() {
        return cars;
    }

    /**
     * {@code station}'s platform: empty for a Station by no Rail or under it, which serves neither
     * way. A Station whose leg has since been bent or cut short keeps a platform with fewer spots, or
     * none.
     */
    public static Optional<Platform> of(RailNetwork network, Station station) {
        Optional<Station.Foot> foot = station.foot(network);
        if (foot.isEmpty()) {
            return Optional.empty();
        }
        Optional<Arm> end = station.servingEnd(foot.get(), DriveSide.current());
        if (end.isEmpty()) {
            return Optional.empty();
        }
        Place at = foot.get().facing(end.get());
        // The Station block's front edge, half a block on from its centre.
        Place.Moved front = at.move(network, 0.5);
        Place leadMark = front.place().move(network, -SPOT / 2.0).place();
        List<CarSpot> spots = new ArrayList<>();
        List<List<Piece>> spotPieces = new ArrayList<>();
        // A line's end within half a block ahead leaves no room for the leading car: no spots.
        for (int i = 0; !front.stopped() && i < cars; i++) {
            Place.Moved back = front.place().move(network, -SPOT * (i + 1));
            Place middle = front.place().move(network, -SPOT * (i + 0.5)).place();
            if (back.stopped() || !StraightLeg.under(network, middle, SPOT)) {
                break;
            }
            spots.add(new CarSpot(i, middle));
            spotPieces.add(cover(network, front.place().move(network, -SPOT * i).place(), SPOT));
        }
        List<Piece> covered = cover(network, front.place(), Math.max(SPOT * spots.size(), 1.0));
        return Optional.of(new Platform(station, front.place(), leadMark, List.copyOf(spots),
                List.copyOf(spotPieces), covered));
    }

    public Station station() {
        return station;
    }

    /** The Station block's front edge on the Line, facing its serving way: where the leading car's front stops. */
    public Place front() {
        return front;
    }

    /** Where the leading car's middle stands at rest, facing the serving way: the middle of the first car spot. */
    public Place leadMark() {
        return leadMark;
    }

    /** Its car spots, the leading car's first; none where the Straight leg ends at the Station. */
    public List<CarSpot> spots() {
        return spots;
    }

    /**
     * How far ahead of {@code lead}, a car running the way it faces, the {@link #leadMark} is, along the
     * Line through the Arms: negative when the car has run past it by up to {@code past}, and NaN when
     * it is out of reach or the car would arrive against the serving way.
     */
    public double toLeadMark(RailNetwork network, Place lead, double past) {
        Span span = leadMark.span();
        double fromA = fromA(leadMark);
        double behind = lead.turned().distanceTo(network, span, fromA);
        if (behind >= 0.0 && behind <= past) {
            return arriving(network, lead, -behind) ? -behind : Double.NaN;
        }
        double ahead = lead.distanceTo(network, span, fromA);
        return ahead >= 0.0 && arriving(network, lead, ahead) ? ahead : Double.NaN;
    }

    /** Whether {@code lead}, run on {@code by}, stands at the lead mark facing its way. */
    private boolean arriving(RailNetwork network, Place lead, double by) {
        Place there = lead.move(network, by).place();
        return sameSpan(there.span(), leadMark.span()) && there.front().equals(leadMark.front());
    }

    /**
     * Which car spot the block at {@code x}, {@code y}, {@code z} is beside: a block square beside
     * the Rail, a whole block off its line, at a car's floor, the block the Rail's underside runs
     * through, and along the Line within the spot. Empty for a block beside no spot.
     */
    public OptionalInt spotBeside(int x, int y, int z) {
        Station probe = new Station(x, y, z, station.name());
        for (int i = 0; i < spots.size(); i++) {
            for (Piece piece : spotPieces.get(i)) {
                Station.Foot foot = probe.footOn(piece.span());
                if (piece.holds(foot.fromA()) && beside(probe, foot)) {
                    return OptionalInt.of(i);
                }
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Every block beside one of its car spots, where a hatch may stand: two each side of the Rail for
     * each spot, the Station's own block among them, spot by spot.
     */
    public List<Beside> besideBlocks(RailNetwork network) {
        List<Beside> beside = new ArrayList<>();
        for (CarSpot spot : spots) {
            // The middle of the spot as a block position; the blocks beside it are within two of it.
            Place.Point middle = spot.middle().point(network);
            int mx = (int) Math.floor(middle.x() + RailGeometry.ATTACH_X);
            int my = (int) Math.floor(middle.y() + RailGeometry.ATTACH_Y);
            int mz = (int) Math.floor(middle.z() + RailGeometry.ATTACH_Z);
            for (int x = mx - 2; x <= mx + 2; x++) {
                for (int y = my - 1; y <= my + 1; y++) {
                    for (int z = mz - 2; z <= mz + 2; z++) {
                        if (spotBeside(x, y, z).equals(OptionalInt.of(spot.index()))) {
                            beside.add(new Beside(spot.index(), x, y, z));
                        }
                    }
                }
            }
        }
        return List.copyOf(beside);
    }

    /**
     * Which of {@code stations} a hatch at {@code x}, {@code y}, {@code z} joins, and at which car spot:
     * the one whose platform it stands beside, of which there is at most one, since no two platforms
     * overlap. Empty beside no platform's car spot. Asked afresh, so a Station broken or a platform
     * bent away leaves it joining nothing until a platform covers it again.
     */
    public static Optional<Joined> joined(RailNetwork network, Iterable<Station> stations, int x, int y, int z) {
        for (Station station : stations) {
            if (Math.abs(station.x() - x) > REACH || Math.abs(station.z() - z) > REACH || Math.abs(station.y() - y) > REACH) {
                continue;
            }
            Optional<Platform> platform = of(network, station);
            if (platform.isPresent()) {
                OptionalInt spot = platform.get().spotBeside(x, y, z);
                if (spot.isPresent()) {
                    return Optional.of(new Joined(station, spot.getAsInt()));
                }
            }
        }
        return Optional.empty();
    }

    /** Whether {@code block}, footed at {@code foot}, stands a block to the side of the Rail at a car's floor. */
    private static boolean beside(Station block, Station.Foot foot) {
        Place.Point off = block.fromFoot(foot);
        // A whole block off the line seen from above, and the Rail's underside within the block's height.
        return Math.abs(Math.sqrt(off.x() * off.x() + off.z() * off.z()) - 1.0) < EPSILON
                && off.y() >= 0.0 && off.y() < 1.0;
    }

    /**
     * Whether this platform and {@code other} share any of the Line: their car spots, or the Station
     * block's own length for one with none. Two Stations may not have overlapping platforms, so a
     * block beside a platform belongs to one Station only.
     */
    public boolean overlaps(Platform other) {
        for (Piece mine : covered) {
            for (Piece theirs : other.covered) {
                if (mine.overlaps(theirs)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The first of {@code others} whose platform {@code station}'s would overlap, any at its own
     * block aside, or empty when none does: a Station is refused there.
     */
    public static Optional<Station> overlapping(RailNetwork network, Station station, Iterable<Station> others) {
        Optional<Platform> mine = of(network, station);
        if (mine.isEmpty()) {
            return Optional.empty();
        }
        for (Station other : others) {
            if (other.x() == station.x() && other.y() == station.y() && other.z() == station.z()) {
                continue;
            }
            Optional<Platform> theirs = of(network, other);
            if (theirs.isPresent() && mine.get().overlaps(theirs.get())) {
                return Optional.of(other);
            }
        }
        return Optional.empty();
    }

    /** The pieces of the Line from {@code from} back {@code length} blocks, as far as the line goes. */
    private static List<Piece> cover(RailNetwork network, Place from, double length) {
        List<Piece> pieces = new ArrayList<>();
        double left = length;
        Arm entering = null;
        for (Span span : from.spansBack(network, length)) {
            if (left <= EPSILON) {
                break;
            }
            double start;
            double room;
            Arm toward;
            if (entering == null) {
                start = fromA(from);
                room = span.length() - from.toFront();
                toward = from.back();
            } else {
                start = entering.equals(span.a()) ? 0.0 : span.length();
                room = span.length();
                toward = span.otherEnd(entering);
            }
            double take = Math.min(room, left);
            double end = toward.equals(span.a()) ? start - take : start + take;
            pieces.add(new Piece(span, Math.min(start, end), Math.max(start, end)));
            left -= take;
            entering = toward;
        }
        return List.copyOf(pieces);
    }

    /** How far {@code place} is along its Span from the Span's first end. */
    private static double fromA(Place place) {
        return place.front().equals(place.span().a()) ? place.toFront() : place.span().length() - place.toFront();
    }

    private static boolean sameSpan(Span one, Span other) {
        return one.a().equals(other.a()) && one.b().equals(other.b());
    }

    @Override
    public String toString() {
        return "Platform[" + station + ", " + spots.size() + " spots]";
    }
}
