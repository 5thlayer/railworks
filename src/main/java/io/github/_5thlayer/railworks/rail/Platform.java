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
 * Station block's front edge, and the cars behind it stand back along the platform at car spots
 * {@link #SPOT} blocks apart, the first the place of the car coupled behind the leading car. The leading car,
 * usually a Locomotive, has no spot, so no hatch stands beside it. The platform runs back up to
 * {@link #cars()} spots, ending where the Straight leg ends, at a bend or a line's end, so no spot is
 * where a car stands on a bend. A Station at a line's end has its spots too: the leading car's front
 * stands over the end Arm, half a block past it, as a car runs on until its middle meets the end.
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
    /** How far a block beside a platform's car spots may be from its Station along x, y or z, in blocks: the leading car and the spots, and a block or two across. */
    public static final double REACH = (MAX_CARS + 1) * SPOT + 3.0;
    /** How far the Station block's front edge is ahead of its centre along the Line, in blocks. */
    private static final double FRONT = 0.5;
    /** Slack for the floating point, so pieces that only touch end to end do not overlap. */
    private static final double EPSILON = 1e-6;

    private static volatile int cars = MAX_CARS;

    /** A car spot: its place in the platform, 0 the one behind the leading car's, and its middle, facing the serving way. */
    public record CarSpot(int index, Place middle) {
    }

    /** A hatch's place: the Station whose platform it stands beside, and the car spot it is beside. */
    public record Joined(Station station, int spot) {
    }

    /** A block beside a car spot, where a hatch may stand: the spot's index and the block's position. */
    public record Beside(int spot, int x, int y, int z) {
    }

    /**
     * The blocks beside one car spot on one side of the Rail, the two a hatch may stand in there,
     * and whether the Line runs along x there, rather than z.
     */
    public record Side(int spot, List<Beside> blocks, boolean alongX) {
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
        // The Station block's front edge, half a block on from its centre. Measured from the centre,
        // since the edge may lie past a line's end: a car runs on until its middle meets the end, so
        // the leading car stands there with its front over the end Arm.
        Place.Moved front = at.move(network, FRONT);
        Place leadMark = at.move(network, FRONT - SPOT / 2.0).place();
        Place.Moved leadBack = at.move(network, FRONT - SPOT);
        // The leading car's place, from its back edge forward, so it ends at a line's end ahead; or,
        // with no room for it, the Station block's own length.
        boolean leads = !leadBack.stopped() && StraightLeg.under(network, leadMark, SPOT);
        List<Piece> covered = new ArrayList<>(leads ? cover(network, leadBack.place().turned(), SPOT)
                : cover(network, front.place(), 1.0));
        List<CarSpot> spots = new ArrayList<>();
        List<List<Piece>> spotPieces = new ArrayList<>();
        for (int i = 0; leads && i < cars; i++) {
            Place.Moved back = at.move(network, FRONT - SPOT * (i + 2));
            Place middle = at.move(network, FRONT - SPOT * (i + 1.5)).place();
            if (back.stopped() || !StraightLeg.under(network, middle, SPOT)) {
                break;
            }
            spots.add(new CarSpot(i, middle));
            spotPieces.add(cover(network, back.place().turned(), SPOT));
            covered.addAll(spotPieces.getLast());
        }
        return Optional.of(new Platform(station, front.place(), leadMark, List.copyOf(spots),
                List.copyOf(spotPieces), List.copyOf(covered)));
    }

    public Station station() {
        return station;
    }

    /**
     * The Station block's front edge on the Line, facing its serving way: where the leading car's
     * front stops. At a line's end it is the end, which the edge lies past.
     */
    public Place front() {
        return front;
    }

    /** Where the leading car's middle stands at rest, facing the serving way: a car's length ahead of the first car spot's. */
    public Place leadMark() {
        return leadMark;
    }

    /** Its car spots, behind the leading car's place, the nearest first; none where the Straight leg ends at the Station or the leading car. */
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
     * each spot, spot by spot. None is beside the leading car's place, the Station's own block among them.
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

    /** Its {@link #besideBlocks} side by side: for each car spot, the blocks beside it on each side of the Rail. */
    public List<Side> sides(RailNetwork network) {
        List<Side> sides = new ArrayList<>();
        if (spots.isEmpty()) {
            return sides;
        }
        // A Straight leg runs along x or z, so every spot runs the way the first does.
        Span leg = spots.getFirst().middle().span();
        boolean alongX = Math.abs(leg.b().x() - leg.a().x()) >= Math.abs(leg.b().z() - leg.a().z());
        List<Beside> left = new ArrayList<>(besideBlocks(network));
        while (!left.isEmpty()) {
            Beside one = left.removeFirst();
            List<Beside> side = new ArrayList<>(List.of(one));
            // Its pair: the next block along the Line beside the same spot.
            left.stream()
                    .filter(b -> b.spot() == one.spot() && b.y() == one.y()
                            && (alongX ? b.z() == one.z() && Math.abs(b.x() - one.x()) == 1
                                       : b.x() == one.x() && Math.abs(b.z() - one.z()) == 1))
                    .findFirst()
                    .ifPresent(pair -> {
                        left.remove(pair);
                        side.add(pair);
                    });
            sides.add(new Side(one.spot(), List.copyOf(side), alongX));
        }
        return sides;
    }

    /** Whether a block at {@code x}, {@code y}, {@code z} is near enough {@code station} to stand beside its platform. */
    public static boolean near(Station station, int x, int y, int z) {
        return Math.abs(station.x() - x) <= REACH && Math.abs(station.y() - y) <= REACH && Math.abs(station.z() - z) <= REACH;
    }

    /**
     * Which of {@code stations} a hatch at {@code x}, {@code y}, {@code z} joins, and at which car spot:
     * the one whose platform it stands beside, of which there is at most one, since no two platforms
     * overlap. Empty beside no platform's car spot. Asked afresh, so a Station broken or a platform
     * bent away leaves it joining nothing until a platform covers it again.
     */
    public static Optional<Joined> joined(RailNetwork network, Iterable<Station> stations, int x, int y, int z) {
        for (Station station : stations) {
            if (!near(station, x, y, z)) {
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
     * Whether this platform and {@code other} share any of the Line: their leading car's place and car spots, or the Station
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
