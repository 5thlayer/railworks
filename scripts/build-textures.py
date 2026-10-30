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

    scripts/build-textures.py
    scripts/build-textures.py --check    # regenerate and diff
"""
import argparse
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


# Every texture this script writes, by its path under textures/, with what builds it.
TEXTURES_BUILT = {
    "block/station/top.png": station_top,
    "block/station/front.png": unchanged("device_terminal_front_on.png"),
    "block/station/side.png": unchanged("concrete_paver.png"),
}


def build():
    return {os.path.join(TEXTURES, path): png(builder()) for path, builder in TEXTURES_BUILT.items()}


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
        print(f"ok   {len(files)} textures match the generator")
        return 0

    for path, image in sorted(files.items()):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "wb").write(image)
    print(f"wrote {len(files)} textures")
    return 0


if __name__ == "__main__":
    sys.exit(main())
