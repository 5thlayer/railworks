#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
"""Build Railworks' textures from Malcolm Riley's unused textures.

Railworks wears Malcolm Riley's unused textures (CC BY 4.0, credited in NOTICE), committed unmodified
under data/art/, in the "Riley steel" look at 16 px per block (#14). Each texture this script writes
is listed in TEXTURES_BUILT by where it goes under assets/railworks/textures/, with what builds it: most
are his sprites written unchanged, and the rest are drawn on them here.

The Station is his small stone tiles on top, his terminal on the face toward the Rail and his concrete
paver on the other sides and the bottom. Its top keeps the arrow it has worn since 0.1.1, pointing the
way the Trains it serves head: the arrow is inlaid in the tiles as yellow paint, the tiles' shading
showing faintly through it.

The Rail is his horizontal grey cement with a steel running strip, and the Pylon his vertical grey
cement, its top segment capped with his concrete slabs under a steel bearing plate; the renderer
draws the Rail and the cap from these.

A hatch is his access-hatch plating round his flat duct, and a Fluid hatch his copper tank side with
the round Sight glass let into it: the dark glass the renderer fills from the bottom
(SightGlassFill.WINDOW), its dark rim and a ring with its studs drawn here. Each comes in the mode
colours, the plating or copper tinted and the ring painted orange for a provider and blue for a
requester. The Charger is his copper coil, made symmetric as Wireworks' Steam Turbine and Fieldworks'
tier-2 Harvester wear it, between his battery's top and bottom, and the Creative Charger the same
re-lit in the FactoryWorks creative pink, as Wireworks' creative pole is. The Burner Generator is his
smelter front, cold or lit and animated at his timing, in his refractory brick under his smelter top.

The cars are his steel plating, tiled panel by panel: riveted on the shell, plain on the roofs, inset
and greyed on the skirts, plain and darkened on the trim, his black stripe along the window bands,
his yellow stripe across the Burner Locomotive's nose and his orange across the Electric's, and his
copper tank side on the Fluid car's tank. The lamps and Sight glass panes are flat colours on a white
sprite drawn here.

    scripts/build-textures.py
    scripts/build-textures.py --check    # regenerate and diff
"""
import argparse
import json
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "railworks", "textures")
ART = os.path.join(ROOT, "data", "art")

# The Station's arrow, pointing north, which is up on a top face: `#` is paint.
ARROW = [
    "................",
    "................",
    "................",
    ".......##.......",
    "......####......",
    ".....######.....",
    "....########....",
    "...##########...",
    "..############..",
    "......####......",
    "......####......",
    "......####......",
    "......####......",
    "................",
    "................",
    "................",
]
# The arrow's paint at the tiles' mean brightness, and how much of the tile's own shading shows
# through it: a pixel of paint is scaled by the tile's brightness against the mean, by this much.
ARROW_PAINT = (214, 168, 38)
ARROW_SHADING = 0.4


# Each mode's colours: the tint a hatch's plating and a Fluid hatch's copper are multiplied by, a
# provider's warm and a requester's cool, and the Sight glass's ring from its lit to its shaded side.
MODES = {
    "provider": {"plating": (255, 184, 120), "copper": (255, 200, 160),
                 "ring": {"j": (250, 160, 72), "g": (219, 111, 24), "k": (194, 87, 7), "q": (139, 60, 5)}},
    "requester": {"plating": (159, 196, 255), "copper": (184, 208, 255),
                  "ring": {"j": (110, 116, 210), "g": (67, 71, 170), "k": (48, 52, 145), "q": (34, 36, 104)}},
}

# A Fluid hatch's side, over his copper: `o` the dark glass the fill draws on (SightGlassFill.WINDOW),
# `n` its glint, `m` the dark rim round it, `j`, `g`, `k` and `q` the ring from its lit to its shaded
# side, `f` and `p` the ring's studs, lit and shaded; `.` is copper.
SIGHT_GLASS = [
    "................",
    ".......ff.......",
    "....ffgfggff....",
    "...fjgggggkkf...",
    "..fjggmmmmkkkf..",
    "..fggmnooomkkp..",
    "..ggmnooooomqq..",
    ".ffgmoooooomqqp.",
    ".fggmoooooomqfp.",
    "..ggmoooooomqq..",
    "..fkkmoooomqqp..",
    "..fkkkmmmmqqqp..",
    "...fkkqqqqqqp...",
    "....fpqqfqpp....",
    ".......pp.......",
    "................",
]
SIGHT_GLASS_PAINT = {"o": (38, 48, 56), "n": (150, 178, 190), "m": (24, 30, 36), "f": (205, 184, 155),
                     "p": (156, 139, 116)}

# Half the width of the dark band down the copper coil side's left edge.
COIL_BAND_HALF = 2

# The colour a creative block's mid-bright pixel lands on (Wireworks' creative pole), and Rec. 601
# luma, which the pink is re-lit by as Wireworks' is, where the Station's arrow takes a plain mean.
CREATIVE_PINK = (0xE6, 0x5A, 0xC8)
LUMA = (0.299, 0.587, 0.114)

# The tints the cars' darker panels are multiplied by: the skirts' inset plating a mid grey, as the
# prototype has it, and the trim's plain plating near black.
CAR_TINT = {"skirt": (138, 138, 138), "trim": (64, 64, 66)}
# The Burner Locomotive's stack, as CarRenderer tints the steel for it.
STACK_TINT = (0x55, 0x50, 0x4C)

# The lit smelter's animation, at his own timing.
SMELTER_LIT_ANIMATION = {"animation": {"frametime": 37, "interpolate": True}}


def png(pixels):
    """A 16-wide RGBA PNG, as bytes; taller than 16 rows, an animation's frames stacked."""
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(tag, body):
        data = tag + body
        return struct.pack(">I", len(body)) + data + struct.pack(">I", zlib.crc32(data) & 0xFFFFFFFF)

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, len(pixels), 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def read_png(path):
    """A 16-wide 8-bit RGBA PNG's pixels, as rows of [r, g, b, a]: 16 rows, or an animation's frames stacked."""
    data = open(path, "rb").read()
    width, height, depth, kind = struct.unpack(">IIBB", data[16:26])
    if (width, depth, kind) != (16, 8, 6) or height % 16:
        sys.exit(f"{path} is not a 16-wide 8-bit RGBA PNG of whole frames")
    idat, i = b"", 8
    while i < len(data):
        (length,) = struct.unpack(">I", data[i:i + 4])
        if data[i + 4:i + 8] == b"IDAT":
            idat += data[i + 8:i + 8 + length]
        i += 12 + length
    raw, stride, rows, prev = zlib.decompress(idat), 64, [], bytearray(64)
    for y in range(height):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b, c = prev[x], prev[x - 4] if x >= 4 else 0
            if kind == 1:
                line[x] = (line[x] + a) & 255
            elif kind == 2:
                line[x] = (line[x] + b) & 255
            elif kind == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([list(line[x * 4:x * 4 + 4]) for x in range(16)])
        prev = line
    return rows


def art(name):
    """One of his sprites under data/art/."""
    return read_png(os.path.join(ART, name))


def unchanged(name):
    """A builder writing his sprite `name` as it is."""
    return lambda: art(name)


def brightness(pixel):
    return (pixel[0] + pixel[1] + pixel[2]) / 3


def station_top():
    """His small stone tiles with the Station's arrow inlaid, pointing north."""
    tiles = art("stone_tiles_small.png")
    mean = sum(brightness(p) for row in tiles for p in row) / 256
    for y, line in enumerate(ARROW):
        for x, mark in enumerate(line):
            if mark == "#":
                scale = 1 + (brightness(tiles[y][x]) / mean - 1) * ARROW_SHADING
                tiles[y][x] = [min(255, round(c * scale)) for c in ARROW_PAINT] + [255]
    return tiles


def tinted(pixels, tint):
    """Each pixel multiplied by `tint`, as a block's tint colours it."""
    return [[[round(p[c] * tint[c] / 255) for c in range(3)] + [p[3]] for p in row] for row in pixels]


def hatch_side(mode):
    """His access-hatch plating in the mode's tint."""
    return tinted(art("space-plating-access-hatch.png"), MODES[mode]["plating"])


def fluid_hatch_side(mode):
    """His copper tank side in the mode's tint, with the Sight glass let into it, its ring in the
    mode's colour."""
    side = tinted(art("metal_tank_copper_sides.png"), MODES[mode]["copper"])
    paint = dict(SIGHT_GLASS_PAINT, **MODES[mode]["ring"])
    for y, line in enumerate(SIGHT_GLASS):
        for x, mark in enumerate(line):
            if mark != ".":
                side[y][x] = list(paint[mark]) + [255]
    return side


def coil():
    """The copper coil's side turned half its dark band over, so the band's darker half runs down the
    left edge, and then its left half mirrored onto its right: the same dark half band down either
    edge and the coil's shine in the middle, as Fieldworks' tier-2 Harvester wears it."""
    turned = [row[COIL_BAND_HALF:] + row[:COIL_BAND_HALF] for row in art("copper_coil_side.png")]
    return [row[:8] + row[7::-1] for row in turned]


def luma(pixel):
    return sum(weight * channel for weight, channel in zip(LUMA, pixel[:3]))


def creative(pixels):
    """Re-lit in the creative pink, each pixel keeping its brightness against the sprite's mean, as
    Wireworks' creative pole is; capped so the brightest channel lands on 255 rather than clipping."""
    shown = [p for row in pixels for p in row if p[3] > 0]
    midpoint = sum(luma(p) for p in shown) / max(1, len(shown))
    out = []
    for row in pixels:
        line = []
        for p in row:
            scale = min(luma(p) / midpoint, 255.0 / max(CREATIVE_PINK))
            line.append([round(c * scale) for c in CREATIVE_PINK] + [p[3]] if p[3] else p)
        out.append(line)
    return out


def white():
    """A white sprite, which the cars' flat panels are drawn on in their colours."""
    return [[[255, 255, 255, 255] for _ in range(16)] for _ in range(16)]


def sight_glass():
    """The inside of a Fluid car's Sight glass tube, seen through it: dark, and darker toward the top."""
    out = []
    for y in range(16):
        line = []
        for x in range(16):
            k = 10 + y * 1.3 + (x * 7 + y * 13) % 5
            line.append([round(k * 0.8), round(k * 1.05), round(k * 1.15), 255])
        out.append(line)
    return out


def glint():
    """The glint down a Sight glass tube, over its fluid: two pale streaks, the rest clear."""
    out = [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]
    for y in range(16):
        out[y][4] = out[y][5] = [214, 236, 242, 255]
        out[y][10] = [150, 170, 178, 255]
    return out


# The item icons: each car seen from its +x side, its front to the right, a car's 4.8 blocks across
# the icon's 16 pixels and as many pixels a block up it from its roof on row 2, standing on a stub of
# the Rail. Each letter is a panel the car wears, drawn from its sprite:
#   R roof   S shell   K skirt   T trim   W window band   G an open window's dark inside
#   M the Rail's cement   X the Burner's stack   F its lit firebox door   C the Electric's coil grille
#   N a nose band   B a crate   A the Fluid car's tank   U the fluid in its Sight glass
# Each car is drawn down to its floor's top; the floor, the skirts and the Rail under them are the same
# for every car.
UNDERFRAME = [
    "SSSSSSSSSSSSSSSS",
    "KKKKKKKKKKKKKKKK",
    "MMMMMMMMMMMMMMMM",
    "MMMMMMMMMMMMMMMM",
    "................",
    "................",
    "................",
]
BURNER_ICON = [
    "................",
    "................",
    ".......XX.RRRRRR",
    "......RXXRWGGGGT",
    "......STTSWGGGGT",
    "......STTSWGGGGT",
    "TTTTTTSFFSSSSSSN",
    "SSSSSSSFFSSSSSSN",
    "SSSSSSKKKKSSSSSN",
] + UNDERFRAME
ELECTRIC_ICON = [
    "................",
    "................",
    "..........RRRRRR",
    "......RRRRWGGGGT",
    "......SCCSWGGGGT",
    "......SCCSWGGGGT",
    "TTTTTTSCCSSSSSSN",
    "SSSSSSSCCSSSSSSN",
    "SSSSSSKKKKSSSSSN",
] + UNDERFRAME
CARGO_ICON = [
    "................",
    "................",
    "RRRRRRRRRRRRRRRR",
    "WWWWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWWWW",
    "WWWWWWWWWWWWWWWW",
    "SSSSBBSBBSBBSSSS",
    "SSSSBBSBBSBBSSSS",
    "SSSSSSSSSSSSSSSS",
] + UNDERFRAME
FLUID_ICON = [
    "................",
    "................",
    ".......AA.......",
    "ATAAAAAAAAAAAATA",
    "ATAAAAAGGAAAAATA",
    "ATAAAAAGGAAAAATA",
    "ATAAAAAUUAAAAATA",
    "ATAAAAAUUAAAAATA",
    "ATAAAAAAAAAAAATA",
] + UNDERFRAME
# How much of a panel's sprite shows through its mean colour on an icon, which sees a block in a few
# pixels: enough to keep its grain, not its pattern.
ICON_GRAIN = 0.35
# The flat colours: an open window's dark inside, the fluid in a Sight glass and a lit firebox.
ICON_DARK = (28, 30, 34)
ICON_FLUID = (60, 110, 210)
ICON_FIRE = (255, 150, 40)
# How dark an icon's outline is drawn, its silhouette's edge pixels, and a seam under a panel where
# another panel meets it from above, so the panels stand apart in so few pixels.
ICON_OUTLINE = 0.6
ICON_SEAM = 0.82
# How much brighter the Rail icon's top face is than its side.
ICON_TOP_LIGHT = 2.0


def mean(pixels):
    opaque = [p for row in pixels for p in row if p[3]]
    return [sum(p[c] for p in opaque) / len(opaque) for c in range(3)]


def grained(pixels):
    """A panel as an icon paints it: each pixel its sprite's mean colour, with a little of the sprite's
    own pixel there."""
    m = mean(pixels)
    return lambda x, y: [min(255, max(0, round(m[c] + (pixels[y][x][c] - m[c]) * ICON_GRAIN))) for c in range(3)]


def stripe(pixels):
    """A band's stripe, the middle row of its sprite, for a band an icon draws a pixel high."""
    return lambda x, y: pixels[8][x][:3]


# The window bands' rows on the icons, rows 3 to 5 of every mask that has one, and the rows of their
# sprite each shows: the plating above the stripe, the stripe, and the plating below it, as the band is
# drawn on the car.
BAND_TOP = 3
BAND_SPRITE_ROWS = (2, 8, 13)


def band(pixels):
    """A window band, three pixels high from BAND_TOP, each row its sprite's row there."""
    return lambda x, y: pixels[BAND_SPRITE_ROWS[y - BAND_TOP]][x][:3]


def flat(colour):
    return lambda x, y: list(colour)


def car_panels():
    """The panels every car's icon paints: those it shares, and the Rail under it."""
    return {
        "R": grained(art("space-plating-plain.png")),
        "S": grained(art("space-plating-rivets-horizontal.png")),
        "K": grained(tinted(art("space-plating-inset-horizontal.png"), CAR_TINT["skirt"])),
        "T": grained(tinted(art("space-plating-plain.png"), CAR_TINT["trim"])),
        "W": band(art("space-plating-stripe-black-horizontal-undamaged.png")),
        "G": flat(ICON_DARK),
        "M": grained(art("gray_cement_horizontal.png")),
    }


def icon(mask, panels):
    """An item icon from its mask, each letter painted by its panel and '.' clear, its silhouette's edge
    darkened to an outline and a seam darkened where one panel meets another under it."""
    def at(x, y):
        return mask[y][x] if 0 <= x < 16 and 0 <= y < 16 else "."

    out = []
    for y, line in enumerate(mask):
        row = []
        for x, mark in enumerate(line):
            if mark == ".":
                row.append([0, 0, 0, 0])
                continue
            shade = 1.0
            if "." in (at(x - 1, y), at(x + 1, y), at(x, y - 1), at(x, y + 1)) or x in (0, 15):
                shade = ICON_OUTLINE
            elif at(x, y - 1) != mark:
                shade = ICON_SEAM
            row.append([round(c * shade) for c in panels[mark](x, y)] + [255])
        out.append(row)
    return out


def burner_icon():
    """The Burner Locomotive: its stack, its firebox door, lit, and its yellow nose band."""
    panels = car_panels()
    panels["X"] = grained(tinted(art("steel_block.png"), STACK_TINT))
    panels["F"] = flat(ICON_FIRE)
    panels["N"] = stripe(art("space-plating-stripe-yellow-horizontal-undamaged.png"))
    return icon(BURNER_ICON, panels)


def electric_icon():
    """The Electric Locomotive: no stack, a coil grille and an orange nose band."""
    panels = car_panels()
    panels["C"] = grained(coil())
    panels["N"] = stripe(art("space-plating-stripe-orange-horizontal-undamaged.png"))
    return icon(ELECTRIC_ICON, panels)


def cargo_icon():
    """The Cargo car: a crate in each of its three recesses."""
    panels = car_panels()
    panels["B"] = grained(art("space_crate_sides.png"))
    return icon(CARGO_ICON, panels)


def fluid_icon():
    """The Fluid car: its copper tank, ringed near each end, its dome and its Sight glass part full."""
    panels = car_panels()
    panels["A"] = grained(art("metal_tank_copper_sides.png"))
    panels["U"] = flat(ICON_FLUID)
    return icon(FLUID_ICON, panels)


def rail_icon():
    """The Rail: a stub of it laid corner to corner, its steel strip along its top face and grey cement
    down its side, outlined."""
    cement = grained(art("gray_cement_horizontal.png"))
    steel = grained(art("steel_block.png"))
    out = [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            across = x + y
            along = x - y
            if abs(along) > 12 or not 13 <= across <= 19:
                continue
            edge = abs(along) == 12 or across in (13, 19)
            pixel = steel(x, y) if across <= 15 else cement(x, y)
            # The top face lit as a block's top is, over the side.
            shade = ICON_OUTLINE if edge else (ICON_TOP_LIGHT if across <= 15 else 1.0)
            out[y][x] = [min(255, round(c * shade)) for c in pixel] + [255]
    return out


# Every texture this script writes, by its path under textures/, with what builds it.
TEXTURES_BUILT = {
    "block/station/top.png": station_top,
    "block/station/front.png": unchanged("device_terminal_front_on.png"),
    "block/station/side.png": unchanged("concrete_paver.png"),
    "block/rail/cement.png": unchanged("gray_cement_horizontal.png"),
    "block/pylon/post.png": unchanged("gray_cement_vertical.png"),
    "block/pylon/cap.png": unchanged("concrete_slabs.png"),
    # The Rail's running strip and the bearing plate it rests on.
    "block/steel.png": unchanged("steel_block.png"),
    "block/hatch/casing.png": unchanged("industrial_metal_duct_flat_1.png"),
    "block/hatch/provider.png": lambda: hatch_side("provider"),
    "block/hatch/requester.png": lambda: hatch_side("requester"),
    "block/fluid_hatch/provider.png": lambda: fluid_hatch_side("provider"),
    "block/fluid_hatch/requester.png": lambda: fluid_hatch_side("requester"),
    "block/charger/sides.png": coil,
    "block/charger/top.png": unchanged("battery_top.png"),
    "block/charger/bottom.png": unchanged("battery_bottom.png"),
    "block/creative_charger/sides.png": lambda: creative(coil()),
    "block/creative_charger/top.png": lambda: creative(art("battery_top.png")),
    "block/creative_charger/bottom.png": lambda: creative(art("battery_bottom.png")),
    "block/burner_generator/front.png": unchanged("device_smelter_front.png"),
    "block/burner_generator/front_on.png": unchanged("device_smelter_front_lit.png"),
    "block/burner_generator/side.png": unchanged("brick_refractory.png"),
    "block/burner_generator/top.png": unchanged("device_smelter_top.png"),
    "block/car/shell.png": unchanged("space-plating-rivets-horizontal.png"),
    "block/car/roof.png": unchanged("space-plating-plain.png"),
    "block/car/skirt.png": lambda: tinted(art("space-plating-inset-horizontal.png"), CAR_TINT["skirt"]),
    "block/car/trim.png": lambda: tinted(art("space-plating-plain.png"), CAR_TINT["trim"]),
    "block/car/window.png": unchanged("space-plating-stripe-black-horizontal-undamaged.png"),
    "block/car/burner_nose.png": unchanged("space-plating-stripe-yellow-horizontal-undamaged.png"),
    "block/car/electric_nose.png": unchanged("space-plating-stripe-orange-horizontal-undamaged.png"),
    "block/car/tank.png": unchanged("metal_tank_copper_sides.png"),
    "block/car/crate_side.png": unchanged("space_crate_top.png"),
    "block/car/crate_top.png": unchanged("space_crate_top.png"),
    "block/car/crate_bottom.png": unchanged("space_crate_bottom.png"),
    "block/car/paint.png": white,
    "block/car/sight_glass.png": sight_glass,
    "block/car/glint.png": glint,
    "item/burner_locomotive.png": burner_icon,
    "item/electric_locomotive.png": electric_icon,
    "item/cargo_car.png": cargo_icon,
    "item/fluid_car.png": fluid_icon,
    "item/rail.png": rail_icon,
}

# The animations, by the texture they play, with their .mcmeta.
ANIMATIONS = {"block/burner_generator/front_on.png": SMELTER_LIT_ANIMATION}


def build():
    files = {os.path.join(TEXTURES, path): png(builder()) for path, builder in TEXTURES_BUILT.items()}
    for path, meta in ANIMATIONS.items():
        files[os.path.join(TEXTURES, path + ".mcmeta")] = (json.dumps(meta, indent=2) + "\n").encode()
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="regenerate and compare, without writing")
    args = parser.parse_args()

    files = build()
    if args.check:
        stale = [path for path, image in sorted(files.items())
                 if not os.path.exists(path) or open(path, "rb").read() != image]
        for path in stale:
            print(f"FAIL: {os.path.relpath(path, ROOT)} is missing or stale")
        if stale:
            return 1
        print(f"ok   {len(files)} textures and animations match the generator")
        return 0

    for path, image in sorted(files.items()):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "wb").write(image)
    print(f"wrote {len(files)} textures and animations")
    return 0


if __name__ == "__main__":
    sys.exit(main())
