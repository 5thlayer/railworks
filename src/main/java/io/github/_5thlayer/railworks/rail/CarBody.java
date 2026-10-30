// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.List;

/**
 * The boxes a car is drawn from: a boxy car straddling the Rail, its shell riding on top with
 * skirts down both sides of the web, the chests set into a Cargo car's sides, and a Fluid car's tank
 * with a Sight glass along each side. Coordinates are
 * the car's own: x across, y up from the floor's underside on the flange's top, z along the car with
 * +z the end it faces, the car's middle block from -0.5 to 0.5. Placeholder art, so {@code CarRenderer} only paints these and nothing else
 * decides their sizes. It also holds the car's size and where the Locomotive's Driver sits in it and
 * its Rider stands, so the boxes, the seats and the Driver's view out of the cab are checked together. A body is
 * {@link #length()} long, from {@code -length / 2} to {@code length / 2} along z.
 *
 * <p>Where two boxes meet, one stops a little short of the other's faces or reaches into it, so no
 * two faces share a plane and the depth test never has to choose between them.
 *
 * <p>Pure: no Minecraft types.
 */
public record CarBody(double length, List<Part> parts, List<Box> chests, List<SightGlass> sightGlasses) {

    /** A car's box across: two blocks, centred on the Rail (ADR 0010). */
    public static final double WIDTH = 2.0;
    /** A car's box up from the floor's underside. */
    public static final double HEIGHT = 2.75;
    /**
     * What a car's body falls short of its length, coupler to coupler, half at each end: two coupled
     * bodies stand this far apart. Every car is a whole number of blocks long, so a stopped Train's
     * cars line up with the blocks.
     */
    public static final double GAP = 0.2;

    /** What colour a part is drawn in, red, green and blue. */
    public enum Paint {
        /** The shell, rust red as the Rail is. */
        SHELL(0.62F, 0.27F, 0.18F),
        /** The skirts and the gangway doors, a darker rust than the shell. */
        DARK_RUST(0.40F, 0.17F, 0.12F),
        /** The window bands and the cab's frame, near black. */
        TRIM(0.13F, 0.08F, 0.07F),
        /** The band across the Locomotive's front, deep red. */
        DEEP_RED(0.30F, 0.10F, 0.08F),
        /** A headlight. */
        LAMP(0.95F, 0.90F, 0.62F),
        /** The Electric Locomotive's nose band, copper. */
        COPPER(0.78F, 0.45F, 0.28F),
        /** A Fluid car's tank, a paler rust than the shell. */
        TANK(0.72F, 0.42F, 0.33F),
        /** A Sight glass above the fluid it shows, dark. */
        GLASS(0.05F, 0.06F, 0.07F);

        public final float red;
        public final float green;
        public final float blue;

        Paint(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    /**
     * An axis-aligned box in the car's coordinates. Among a body's {@link #chests()}, a cube a vanilla
     * chest fills as it would a block, drawn with its own texture, its front out of the car's nearer
     * side.
     */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        /** The same box on the other side of the car. */
        Box mirrored() {
            return new Box(-maxX, minY, minZ, -minX, maxY, maxZ);
        }

        /** The same box at the other end of the car. */
        Box turned() {
            return new Box(minX, minY, -maxZ, maxX, maxY, -minZ);
        }
    }

    /** A painted box of the car's. */
    public record Part(Paint paint, Box box) {

        Part(Paint paint, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this(paint, new Box(minX, minY, minZ, maxX, maxY, maxZ));
        }

        Part mirrored() {
            return new Part(paint, box.mirrored());
        }

        Part turned() {
            return new Part(paint, box.turned());
        }
    }

    /**
     * A Sight glass along one side of a Fluid car's tank: its pane, painted dark among the body's
     * parts, which the fluid fills from the bottom as far as the tank is full, on its outer face.
     */
    public record SightGlass(Box pane) {

        /** Which side of the car it looks out of: 1 for +x, -1 for -x. */
        public double side() {
            return Math.signum(pane.minX() + pane.maxX());
        }

        /**
         * How high the fluid stands in it for a tank holding {@code amount} of {@code capacity}: from
         * the pane's bottom, empty, to its top, full, in proportion between, not in steps.
         */
        public double surface(long amount, long capacity) {
            return SightGlassFill.surface(pane.minY(), pane.maxY(), amount, capacity);
        }
    }

    /** A point in the car's own coordinates. */
    public record Point(double x, double y, double z) {
    }

    /**
     * A seated player's eye above the seat: their 1.62 eye height less the 0.6 they sit below a
     * seat ({@code Avatar.DEFAULT_VEHICLE_ATTACHMENT}).
     */
    private static final double EYE_ABOVE_SEAT = 1.62 - 0.6;

    /** The window bands, at one height on every car so they line up along a Train. */
    private static final double BAND_BOTTOM = 1.64;
    private static final double BAND_TOP = 2.5;

    /**
     * The Locomotive's open side windows, along the cab inside the band's frame: from this far
     * ahead of the cab's back to this far short of the car's front.
     */
    private static final double WINDOW_SILL = 1.7;
    private static final double WINDOW_HEAD = 2.44;
    private static final double WINDOW_FROM_CAB_BACK = 0.45;
    private static final double WINDOW_FROM_FRONT = 0.16;

    /** The Locomotive's body, and the low wall around its bay. */
    private static final double LOCOMOTIVE_LENGTH = Locomotive.LENGTH - GAP;
    private static final double BAY_WALL_TOP = 1.3;
    /** The top of the Locomotive's Engine room, under the cab's roof. */
    private static final double ENGINE_ROOM_TOP = 2.32;
    /** The top of every car's floor, over its underside. */
    public static final double FLOOR_TOP = 0.7;
    /** The top of a Fluid car's tank, over the floor's underside. */
    public static final double TANK_TOP = 2.58;
    /** The top of the dome on a Fluid car's tank, over its middle block, over the floor's underside. */
    public static final double DOME_TOP = 2.72;

    /**
     * Where the Locomotive's cab ends and its Engine room begins, and where the Engine room ends and
     * its bay begins, along z: the Engine room is the car's middle block, and the cab and the bay its
     * two blocks either side.
     */
    static final double CAB_BACK = 0.5;
    static final double BAY_FRONT = -0.5;

    /**
     * Where the Locomotive's Driver sits, in the cab, above the floor's underside and 0.8 short of
     * its body's front: far enough back and low enough that, across the two-wide cab, the side
     * windows still frame the view past the front's pillars. Only a Locomotive is ridden.
     */
    public static final Point CAB_SEAT = new Point(0.0, 1.1, LOCOMOTIVE_LENGTH / 2.0 - 0.8);
    /**
     * Where the Locomotive's Rider stands, mob or player: on the bay's floor, not raised to a seat,
     * halfway between the Engine room's back and the car's back end.
     */
    public static final Point BAY_SPOT = new Point(0.0, FLOOR_TOP, (BAY_FRONT - LOCOMOTIVE_LENGTH / 2.0) / 2.0);

    /** How long a Fluid car's Sight glass is along z, across the car's middle. */
    private static final double SIGHT_GLASS_LENGTH = 0.8;

    /** Where a Cargo car's chests are along z, one a side at each: over its three inner blocks. */
    private static final double[] CHESTS_AT = {-1.0, 0.0, 1.0};
    /** Half a chest's recess along z. */
    private static final double HALF_RECESS = 0.23;

    public static final CarBody LOCOMOTIVE = locomotive();
    public static final CarBody ELECTRIC = electric();
    public static final CarBody CARGO = cargo();
    public static final CarBody FLUID = fluid();

    /** Where the Locomotive's Driver's eye is. */
    public static Point eye() {
        return new Point(CAB_SEAT.x(), CAB_SEAT.y() + EYE_ABOVE_SEAT, CAB_SEAT.z());
    }

    /** What every car shares: the skirts and the floor, along a body {@code length} long. */
    private static List<Part> shell(double length) {
        double half = length / 2.0;
        List<Part> parts = new ArrayList<>();
        sides(parts, new Part(Paint.DARK_RUST, 0.17, 0.0, -half + 0.01, 0.49, 0.62, half - 0.01));
        parts.add(new Part(Paint.SHELL, -1.0, 0.55, -half, 1.0, FLOOR_TOP, half));
        return parts;
    }

    /** A roof from {@code back} to {@code front}. */
    private static Part roof(double back, double front) {
        return new Part(Paint.SHELL, -1.0, 2.6, back, 1.0, 2.75, front);
    }

    /**
     * The Locomotive: a cab at the front, a closed Engine room over its middle block, and an open bay
     * at its back. The cab has a closed back with a door, an open front framed in dark by the roof's
     * edge, two corner pillars and a low dash, with a deep red nose and two headlights under it, and
     * an open window in each side, framed by the band. The Engine room is lower than the cab, with
     * louvres down its sides; it is only drawn. The bay has no roof, only a low wall with a dark rail
     * along its sides and back, the Engine room closing it at the front.
     */
    private static CarBody locomotive() {
        double half = LOCOMOTIVE_LENGTH / 2.0;
        double windowBack = CAB_BACK + WINDOW_FROM_CAB_BACK;
        double windowFront = half - WINDOW_FROM_FRONT;
        List<Part> parts = shell(LOCOMOTIVE_LENGTH);
        parts.add(roof(CAB_BACK, half));
        // The cab's side wall below and above the window, then behind and ahead of it; the band runs
        // on behind and ahead of the window and frames it above and below.
        sides(parts, new Part(Paint.SHELL, 0.92, 0.68, CAB_BACK + 0.015, 0.99, WINDOW_SILL - 0.005, half - 0.06));
        sides(parts, new Part(Paint.SHELL, 0.92, WINDOW_HEAD + 0.005, CAB_BACK + 0.015, 0.99, 2.62, half - 0.06));
        sides(parts, new Part(Paint.SHELL, 0.925, BAND_BOTTOM + 0.05, CAB_BACK + 0.02, 0.985, BAND_TOP - 0.05, windowBack + 0.005));
        sides(parts, new Part(Paint.SHELL, 0.925, BAND_BOTTOM + 0.05, windowFront - 0.005, 0.985, BAND_TOP - 0.05, half - 0.065));
        sides(parts, new Part(Paint.TRIM, 0.97, BAND_BOTTOM, CAB_BACK, 1.0, BAND_TOP, windowBack));
        sides(parts, new Part(Paint.TRIM, 0.97, BAND_BOTTOM, windowFront, 1.0, BAND_TOP, half));
        sides(parts, new Part(Paint.TRIM, 0.972, BAND_BOTTOM + 0.005, windowBack - 0.005, 0.998, WINDOW_SILL, windowFront + 0.005));
        sides(parts, new Part(Paint.TRIM, 0.972, WINDOW_HEAD, windowBack - 0.005, 0.998, BAND_TOP - 0.005, windowFront + 0.005));
        // The cab's back wall, and its door, a door's width whatever the car's.
        parts.add(new Part(Paint.SHELL, -0.95, 0.67, CAB_BACK + 0.01, 0.95, 2.63, CAB_BACK + 0.08));
        parts.add(new Part(Paint.DARK_RUST, -0.25, 0.75, CAB_BACK, 0.25, 2.35, CAB_BACK + 0.03));
        // The cab's front.
        sides(parts, new Part(Paint.TRIM, 0.8, 0.66, half - 0.14, 0.995, 2.64, half - 0.015));
        parts.add(new Part(Paint.TRIM, -0.99, 2.45, half - 0.05, 0.99, 2.65, half - 0.005));
        parts.add(new Part(Paint.TRIM, -0.81, 0.65, half - 0.12, 0.81, 1.6, half - 0.02));
        parts.add(new Part(Paint.DEEP_RED, -1.0, 0.72, half - 0.04, 1.0, 1.45, half - 0.01));
        sides(parts, new Part(Paint.LAMP, 0.72, 1.1, half - 0.025, 0.86, 1.3, half));
        // The Engine room, stopping just short of the cab's door, its roof reaching just under the
        // cab's back wall, and its louvres and a dark band low down its sides, set into its walls.
        parts.add(new Part(Paint.SHELL, -0.95, 0.69, BAY_FRONT, 0.95, 2.2, CAB_BACK - 0.005));
        parts.add(new Part(Paint.SHELL, -0.99, ENGINE_ROOM_TOP - 0.15, BAY_FRONT - 0.02, 0.99, ENGINE_ROOM_TOP, CAB_BACK + 0.005));
        for (double z = BAY_FRONT + 0.2; z + 0.12 < CAB_BACK - 0.1; z += 0.3) {
            sides(parts, new Part(Paint.TRIM, 0.94, 1.2, z, 0.97, 2.05, z + 0.12));
        }
        sides(parts, new Part(Paint.DARK_RUST, 0.945, 0.75, BAY_FRONT + 0.1, 0.98, 1.1, CAB_BACK - 0.1));
        // The bay's low wall along each side and across the back, reaching into the Engine room, and
        // the rail along its top.
        sides(parts, new Part(Paint.SHELL, 0.925, 0.685, -half + 0.015, 0.985, BAY_WALL_TOP, BAY_FRONT + 0.02));
        parts.add(new Part(Paint.SHELL, -0.95, 0.69, -half + 0.01, 0.95, BAY_WALL_TOP - 0.01, -half + 0.07));
        sides(parts, new Part(Paint.TRIM, 0.91, BAY_WALL_TOP - 0.005, -half, 1.0, BAY_WALL_TOP + 0.08, BAY_FRONT + 0.03));
        parts.add(new Part(Paint.TRIM, -0.995, BAY_WALL_TOP - 0.015, -half + 0.005, 0.995, BAY_WALL_TOP + 0.075, -half + 0.065));
        return new CarBody(LOCOMOTIVE_LENGTH, List.copyOf(parts), List.of(), List.of());
    }

    /** The Electric Locomotive: the Burner's body, its nose band copper rather than deep red. Placeholder. */
    private static CarBody electric() {
        List<Part> parts = new ArrayList<>();
        for (Part part : LOCOMOTIVE.parts()) {
            parts.add(part.paint() == Paint.DEEP_RED ? new Part(Paint.COPPER, part.box()) : part);
        }
        return new CarBody(LOCOMOTIVE.length(), List.copyOf(parts), List.of(), List.of());
    }

    /**
     * The Cargo car: closed, of the Locomotive's height and roof line, a gangway door at both ends,
     * and a chest set into a framed recess in each side over each of its three inner blocks.
     */
    private static CarBody cargo() {
        double length = CargoCar.LENGTH - GAP;
        double half = length / 2.0;
        List<Part> parts = shell(length);
        parts.add(roof(-half, half));
        sides(parts, new Part(Paint.TRIM, 0.97, BAND_BOTTOM, -half, 1.0, BAND_TOP, half));
        ends(parts, new Part(Paint.SHELL, -0.95, 0.67, half - 0.08, 0.95, 2.63, half - 0.01));
        ends(parts, new Part(Paint.DARK_RUST, -0.25, 0.75, half - 0.03, 0.25, 2.35, half));
        // The side's inner wall, then the facing between and around the recesses, which stands proud
        // of the wall by the recess's depth, and the facing above and below each recess.
        sides(parts, new Part(Paint.SHELL, 0.83, 0.68, -half + 0.015, 0.89, 2.62, half - 0.015));
        List<Double> edges = new ArrayList<>(List.of(-half + 0.02));
        for (double at : CHESTS_AT) {
            edges.add(at - HALF_RECESS + 0.01);
            edges.add(at + HALF_RECESS - 0.01);
        }
        edges.add(half - 0.02);
        for (int i = 0; i < edges.size(); i += 2) {
            sides(parts, new Part(Paint.SHELL, 0.88, 0.665, edges.get(i), 0.99, 2.635, edges.get(i + 1)));
        }
        List<Box> chests = new ArrayList<>();
        for (double at : CHESTS_AT) {
            sides(parts, new Part(Paint.SHELL, 0.882, 1.48, at - HALF_RECESS, 0.988, 2.625, at + HALF_RECESS));
            sides(parts, new Part(Paint.SHELL, 0.882, 0.675, at - HALF_RECESS, 0.988, 1.07, at + HALF_RECESS));
            // The chest fills a cube in the recess, its lock just behind the facing.
            Box chest = new Box(0.55, 1.075, at - 0.195, 0.94, 1.465, at + 0.195);
            chests.add(chest);
            chests.add(chest.mirrored());
        }
        return new CarBody(length, List.copyOf(parts), List.copyOf(chests), List.of());
    }

    /**
     * The Fluid car: an open floor carrying a tank along it, rounded by a wide box crossing a tall one,
     * on a saddle at each end, a band round it near each end and a dome on top. Across the middle of
     * each side of the wide box, {@value #SIGHT_GLASS_LENGTH} long, a Sight glass: a dark pane standing just
     * proud of a frame a little larger, which shows round it as a rim.
     */
    private static CarBody fluid() {
        double length = FluidCar.LENGTH - GAP;
        double half = length / 2.0;
        List<Part> parts = shell(length);
        parts.add(new Part(Paint.TANK, -0.95, 1.0, -half + 0.07, 0.95, 2.3, half - 0.07));
        parts.add(new Part(Paint.TANK, -0.7, 0.72, -half + 0.06, 0.7, TANK_TOP, half - 0.06));
        parts.add(new Part(Paint.TANK, -0.15, TANK_TOP - 0.01, -0.2, 0.15, DOME_TOP, 0.2));
        ends(parts, new Part(Paint.TRIM, -0.96, 0.99, half - 0.5, 0.96, 2.31, half - 0.44));
        ends(parts, new Part(Paint.DARK_RUST, -0.8, 0.69, half - 0.35, 0.8, 0.8, half - 0.2));
        double glassEnd = SIGHT_GLASS_LENGTH / 2.0;
        sides(parts, new Part(Paint.TRIM, 0.94, 1.08, -glassEnd - 0.04, 0.96, 2.22, glassEnd + 0.04));
        Box pane = new Box(0.945, 1.12, -glassEnd, 0.965, 2.18, glassEnd);
        sides(parts, new Part(Paint.GLASS, pane));
        List<SightGlass> glasses = List.of(new SightGlass(pane), new SightGlass(pane.mirrored()));
        return new CarBody(length, List.copyOf(parts), List.of(), glasses);
    }

    /** The part and its mirror across the car. */
    private static void sides(List<Part> parts, Part right) {
        parts.add(right);
        parts.add(right.mirrored());
    }

    /** The part at the front and its turn at the back. */
    private static void ends(List<Part> parts, Part front) {
        parts.add(front);
        parts.add(front.turned());
    }
}
