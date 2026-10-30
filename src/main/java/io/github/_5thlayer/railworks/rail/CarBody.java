// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.rail;

import java.util.ArrayList;
import java.util.List;

/**
 * The boxes a car is drawn from: a boxy car straddling the Rail, its shell riding on top with
 * skirts down both sides of the web, the crates set into a Cargo car's sides, and a Fluid car's tank
 * with a Sight glass along each side. Coordinates are
 * the car's own: x across, y up from the floor's underside on the flange's top, z along the car with
 * +z the end it faces, the car's middle block from -0.5 to 0.5. Each box is a kind of {@link Panel},
 * which {@code CarRenderer} draws as a tiled texture, and nothing else decides the boxes' sizes. It also holds the car's size and where the Locomotive's Driver sits in it and
 * its Rider stands, so the boxes, the seats and the Driver's view out of the cab are checked together. A body is
 * {@link #length()} long, from {@code -length / 2} to {@code length / 2} along z.
 *
 * <p>Where two boxes meet, one stops a little short of the other's faces or reaches into it, so no
 * two faces share a plane and the depth test never has to choose between them.
 *
 * <p>Pure: no Minecraft types.
 */
public record CarBody(double length, List<Part> parts, List<Box> crates, List<SightGlass> sightGlasses) {

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

    /** What kind of panel a part is, which says how it is drawn. */
    public enum Panel {
        /** The shell's walls. */
        SHELL,
        /** A roof. */
        ROOF,
        /** The skirts, and the gangway doors, the Engine room's low band and the Fluid car's saddles. */
        SKIRT,
        /** The cab's frame and the window's, the louvres, the bay's rail and the tank's bands. */
        TRIM,
        /** A window band. */
        WINDOW,
        /** The band across the Burner Locomotive's front. */
        BURNER_NOSE,
        /** The band across the Electric Locomotive's front. */
        ELECTRIC_NOSE,
        /** A headlight. */
        LAMP,
        /** A Fluid car's tank. */
        TANK,
        /** A Burner Locomotive's firebox door, lit while it holds fuel. */
        FIREBOX,
        /** An Electric Locomotive's coil grille, glowing while it holds charge. */
        COIL,
        /** A Burner Locomotive's stack. */
        STACK,
        /** A Sight glass above the fluid it shows. */
        GLASS
    }

    /**
     * An axis-aligned box in the car's coordinates. Among a body's {@link #crates()}, a cube a crate
     * fills, each face drawn in the whole of its sprite.
     */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        /** The same box on the other side of the car. */
        Box mirrored() {
            return new Box(-maxX, minY, minZ, -minX, maxY, maxZ);
        }

        /** The same box upside down about {@code middle}. */
        Box upsideDown(double middle) {
            return new Box(minX, 2.0 * middle - maxY, minZ, maxX, 2.0 * middle - minY, maxZ);
        }

        /** The same box at the other end of the car. */
        Box turned() {
            return new Box(minX, minY, -maxZ, maxX, maxY, -minZ);
        }
    }

    /** A box of the car's, and the kind of panel it is. */
    public record Part(Panel panel, Box box) {

        Part(Panel panel, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this(panel, new Box(minX, minY, minZ, maxX, maxY, maxZ));
        }

        Part mirrored() {
            return new Part(panel, box.mirrored());
        }

        Part turned() {
            return new Part(panel, box.turned());
        }
    }

    /**
     * A Sight glass along one side of a Fluid car's tank: its pane, dark {@link Panel#GLASS} among the
     * body's parts, which the fluid fills from the bottom as far as the tank is full, on its outer face.
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

    /** Where a Cargo car's crates are along z, one a side at each: over its three inner blocks. */
    private static final double[] CRATES_AT = {-1.0, 0.0, 1.0};
    /** The height the Locomotive's chin is turned upside down about. */
    private static final double CHIN_MIDDLE = 0.6;
    /** Half a crate's recess along z. */
    private static final double HALF_RECESS = 0.23;

    public static final CarBody LOCOMOTIVE = locomotive(false);
    public static final CarBody ELECTRIC = locomotive(true);
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
        sides(parts, new Part(Panel.SKIRT, 0.17, 0.0, -half + 0.01, 0.49, 0.62, half - 0.01));
        parts.add(new Part(Panel.SHELL, -1.0, 0.55, -half, 1.0, FLOOR_TOP, half));
        return parts;
    }

    /** The radius a roof's edges are rounded on. */
    static final double ROOF_ROUNDING = 4.0 / 32.0;
    /** The steps a roof's edges are rounded in: half a pixel, up and in. */
    static final double ROUNDING_STEP = 1.0 / 32.0;
    /**
     * How far each step of a rounded roof reaches down into the one under it, and how far short of an
     * end left square each stops of the one under it, so no two share a plane.
     */
    private static final double STEP_OVERLAP = 0.002;
    private static final double STEP_SHORT = 0.001;

    /** A roof from {@code back} to {@code front}, its long edges rounded, and its front's when {@code roundFront}. */
    private static void roof(List<Part> parts, double back, double front, boolean roundFront) {
        rounded(parts, Panel.ROOF, 1.0, 2.6, 2.75, back, front, false, roundFront);
    }

    /**
     * A slab of {@code panel} {@code halfWidth} either side of the middle, from {@code bottom} to
     * {@code top} and {@code back} to {@code front}, its long top edges, and the ends asked for,
     * rounded on a radius of {@link #ROOF_ROUNDING} in rows {@link #ROUNDING_STEP} high: each row drawn in by
     * how far a quarter circle of that radius falls short of the edge at the row's middle, to the
     * nearest step. The rows that stay full width are one box with the slab under them, so the rows
     * all differ in width; each stands a hair down into the one under it.
     */
    private static void rounded(List<Part> parts, Panel panel, double halfWidth, double bottom, double top, double back, double front,
                                boolean roundBack, boolean roundFront) {
        int rows = (int) Math.round(ROOF_ROUNDING / ROUNDING_STEP);
        double[] insets = new double[rows];
        int full = 0;
        for (int row = 0; row < rows; row++) {
            double up = (row + 0.5) * ROUNDING_STEP;
            double reach = Math.sqrt(Math.max(0.0, ROOF_ROUNDING * ROOF_ROUNDING - up * up));
            insets[row] = ROOF_ROUNDING - Math.round(reach / ROUNDING_STEP) * ROUNDING_STEP;
            if (insets[row] == 0.0 && full == row) {
                full++;
            }
        }
        double rowsFrom = top - ROOF_ROUNDING;
        parts.add(new Part(panel, -halfWidth, bottom, back, halfWidth, rowsFrom + full * ROUNDING_STEP, front));
        for (int row = full; row < rows; row++) {
            double inset = insets[row];
            double shortOfEnd = STEP_SHORT * (row - full + 1);
            double lo = rowsFrom + row * ROUNDING_STEP;
            parts.add(new Part(panel, -halfWidth + inset, lo - STEP_OVERLAP, back + (roundBack ? inset : shortOfEnd),
                    halfWidth - inset, lo + ROUNDING_STEP, front - (roundFront ? inset : shortOfEnd)));
        }
    }

    /**
     * The Locomotive: a cab at the front, a closed Engine room over its middle block, and an open bay
     * at its back. The cab has a closed back with a door, an open front framed in dark by the roof's
     * edge, two corner pillars and a low dash, with a nose band and two headlights under it, and
     * an open window in each side, framed by the band. The band is rounded along its top edges and
     * its front, and a dark chin under it along its bottom ones. The Engine room is lower than the
     * cab; it is only drawn. On the Burner Locomotive it carries a stack on its roof and a firebox door
     * in each side with louvres over it, and on the Electric a coil grille in each side, and no stack
     * (the Rail carries no energy, ADR 0005). The bay has no roof, only a low wall with a dark rail
     * along its sides and back, the Engine room closing it at the front.
     */
    private static CarBody locomotive(boolean electric) {
        double half = LOCOMOTIVE_LENGTH / 2.0;
        double windowBack = CAB_BACK + WINDOW_FROM_CAB_BACK;
        double windowFront = half - WINDOW_FROM_FRONT;
        List<Part> parts = shell(LOCOMOTIVE_LENGTH);
        roof(parts, CAB_BACK, half, true);
        // The cab's side wall below and above the window, then behind and ahead of it; the band runs
        // on behind and ahead of the window and frames it above and below.
        sides(parts, new Part(Panel.SHELL, 0.92, 0.68, CAB_BACK + 0.015, 0.99, WINDOW_SILL - 0.005, half - 0.06));
        sides(parts, new Part(Panel.SHELL, 0.92, WINDOW_HEAD + 0.005, CAB_BACK + 0.015, 0.99, 2.62, half - 0.06));
        sides(parts, new Part(Panel.SHELL, 0.925, BAND_BOTTOM + 0.05, CAB_BACK + 0.02, 0.985, BAND_TOP - 0.05, windowBack + 0.005));
        sides(parts, new Part(Panel.SHELL, 0.925, BAND_BOTTOM + 0.05, windowFront - 0.005, 0.985, BAND_TOP - 0.05, half - 0.065));
        sides(parts, new Part(Panel.WINDOW, 0.97, BAND_BOTTOM, CAB_BACK, 1.0, BAND_TOP, windowBack));
        sides(parts, new Part(Panel.WINDOW, 0.97, BAND_BOTTOM, windowFront, 1.0, BAND_TOP, half));
        sides(parts, new Part(Panel.TRIM, 0.972, BAND_BOTTOM + 0.005, windowBack - 0.005, 0.998, WINDOW_SILL, windowFront + 0.005));
        sides(parts, new Part(Panel.TRIM, 0.972, WINDOW_HEAD, windowBack - 0.005, 0.998, BAND_TOP - 0.005, windowFront + 0.005));
        // The cab's back wall, and its door, a door's width whatever the car's.
        parts.add(new Part(Panel.SHELL, -0.95, 0.67, CAB_BACK + 0.01, 0.95, 2.63, CAB_BACK + 0.08));
        parts.add(new Part(Panel.SKIRT, -0.25, 0.75, CAB_BACK, 0.25, 2.35, CAB_BACK + 0.03));
        // The cab's front.
        sides(parts, new Part(Panel.TRIM, 0.8, 0.66, half - 0.14, 0.995, 2.64, half - 0.015));
        parts.add(new Part(Panel.TRIM, -0.99, 2.45, half - 0.05, 0.99, 2.65, half - 0.005));
        parts.add(new Part(Panel.TRIM, -0.81, 0.65, half - 0.12, 0.81, 1.6, half - 0.02));
        rounded(parts, electric ? Panel.ELECTRIC_NOSE : Panel.BURNER_NOSE, 1.0, 0.72, 1.45, half - 0.2, half - 0.01,
                false, true);
        // The chin: a slab rounded as the band is, turned upside down, so its bottom edges are rounded.
        List<Part> chin = new ArrayList<>();
        rounded(chin, Panel.SKIRT, 0.993, 0.4, 0.72 + ROOF_ROUNDING, half - 0.3, half - 0.017, false, true);
        for (Part part : chin) {
            parts.add(new Part(part.panel(), part.box().upsideDown(CHIN_MIDDLE)));
        }
        sides(parts, new Part(Panel.LAMP, 0.72, 1.1, half - 0.025, 0.86, 1.3, half));
        // The Engine room, stopping just short of the cab's door, its roof reaching just under the
        // cab's back wall, and a dark band low down its sides, set into its walls.
        parts.add(new Part(Panel.SHELL, -0.95, 0.69, BAY_FRONT, 0.95, 2.2, CAB_BACK - 0.005));
        rounded(parts, Panel.ROOF, 0.99, ENGINE_ROOM_TOP - 0.15, ENGINE_ROOM_TOP, BAY_FRONT - 0.02, CAB_BACK + 0.005,
                true, false);
        if (electric) {
            sides(parts, new Part(Panel.COIL, 0.94, 1.05, -0.38, 0.975, 2.05, 0.38));
        } else {
            for (double z = BAY_FRONT + 0.2; z + 0.12 < CAB_BACK - 0.1; z += 0.3) {
                sides(parts, new Part(Panel.TRIM, 0.94, 1.65, z, 0.97, 2.05, z + 0.12));
            }
            sides(parts, new Part(Panel.FIREBOX, 0.955, 0.9, -0.28, 0.99, 1.5, 0.28));
            // The stack, standing down into the roof, and its wider cap.
            parts.add(new Part(Panel.STACK, -0.13, ENGINE_ROOM_TOP - 0.01, -0.13, 0.13, 2.66, 0.13));
            parts.add(new Part(Panel.STACK, -0.16, 2.62, -0.16, 0.16, 2.74, 0.16));
        }
        sides(parts, new Part(Panel.SKIRT, 0.945, 0.75, BAY_FRONT + 0.1, 0.98, 1.1, CAB_BACK - 0.1));
        // The bay's low wall along each side and across the back, reaching into the Engine room, and
        // the rail along its top.
        sides(parts, new Part(Panel.SHELL, 0.925, 0.685, -half + 0.015, 0.985, BAY_WALL_TOP, BAY_FRONT + 0.02));
        parts.add(new Part(Panel.SHELL, -0.95, 0.69, -half + 0.01, 0.95, BAY_WALL_TOP - 0.01, -half + 0.07));
        sides(parts, new Part(Panel.TRIM, 0.91, BAY_WALL_TOP - 0.005, -half, 1.0, BAY_WALL_TOP + 0.08, BAY_FRONT + 0.03));
        parts.add(new Part(Panel.TRIM, -0.995, BAY_WALL_TOP - 0.015, -half + 0.005, 0.995, BAY_WALL_TOP + 0.075, -half + 0.065));
        return new CarBody(LOCOMOTIVE_LENGTH, List.copyOf(parts), List.of(), List.of());
    }

    /**
     * The Cargo car: closed, of the Locomotive's height and roof line, a gangway door at both ends,
     * and a crate set into a framed recess in each side over each of its three inner blocks.
     */
    private static CarBody cargo() {
        double length = CargoCar.LENGTH - GAP;
        double half = length / 2.0;
        List<Part> parts = shell(length);
        roof(parts, -half, half, false);
        sides(parts, new Part(Panel.WINDOW, 0.97, BAND_BOTTOM, -half, 1.0, BAND_TOP, half));
        ends(parts, new Part(Panel.SHELL, -0.95, 0.67, half - 0.08, 0.95, 2.63, half - 0.01));
        ends(parts, new Part(Panel.SKIRT, -0.25, 0.75, half - 0.03, 0.25, 2.35, half));
        // The side's inner wall, then the facing between and around the recesses, which stands proud
        // of the wall by the recess's depth, and the facing above and below each recess.
        sides(parts, new Part(Panel.SHELL, 0.83, 0.68, -half + 0.015, 0.89, 2.62, half - 0.015));
        List<Double> edges = new ArrayList<>(List.of(-half + 0.02));
        for (double at : CRATES_AT) {
            edges.add(at - HALF_RECESS + 0.01);
            edges.add(at + HALF_RECESS - 0.01);
        }
        edges.add(half - 0.02);
        for (int i = 0; i < edges.size(); i += 2) {
            sides(parts, new Part(Panel.SHELL, 0.88, 0.665, edges.get(i), 0.99, 2.635, edges.get(i + 1)));
        }
        List<Box> crates = new ArrayList<>();
        for (double at : CRATES_AT) {
            sides(parts, new Part(Panel.SHELL, 0.882, 1.48, at - HALF_RECESS, 0.988, 2.625, at + HALF_RECESS));
            sides(parts, new Part(Panel.SHELL, 0.882, 0.675, at - HALF_RECESS, 0.988, 1.07, at + HALF_RECESS));
            // The crate fills a cube in the recess, its face just behind the facing.
            Box crate = new Box(0.55, 1.075, at - 0.195, 0.94, 1.465, at + 0.195);
            crates.add(crate);
            crates.add(crate.mirrored());
        }
        return new CarBody(length, List.copyOf(parts), List.copyOf(crates), List.of());
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
        parts.add(new Part(Panel.TANK, -0.95, 1.0, -half + 0.07, 0.95, 2.3, half - 0.07));
        parts.add(new Part(Panel.TANK, -0.7, 0.72, -half + 0.06, 0.7, TANK_TOP, half - 0.06));
        parts.add(new Part(Panel.TANK, -0.15, TANK_TOP - 0.01, -0.2, 0.15, DOME_TOP, 0.2));
        ends(parts, new Part(Panel.TRIM, -0.96, 0.99, half - 0.5, 0.96, 2.31, half - 0.44));
        ends(parts, new Part(Panel.SKIRT, -0.8, 0.69, half - 0.35, 0.8, 0.8, half - 0.2));
        double glassEnd = SIGHT_GLASS_LENGTH / 2.0;
        sides(parts, new Part(Panel.TRIM, 0.94, 1.08, -glassEnd - 0.04, 0.96, 2.22, glassEnd + 0.04));
        Box pane = new Box(0.945, 1.12, -glassEnd, 0.965, 2.18, glassEnd);
        sides(parts, new Part(Panel.GLASS, pane));
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
