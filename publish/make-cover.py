# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Cover and icon, in the suite's style (Wireworks' and Groundworks' make-cover.py): a row of Pylons
# carrying the Rail, and a Train riding on it, seen from the side and drawn from Railworks' own
# textures (scripts/build-textures.py), so nothing of Mojang's is in them (libworks ADR 0012). Run from
# the repo root with Pillow installed.
import os

from PIL import Image, ImageDraw, ImageFont

T = 'src/main/resources/assets/railworks/textures/block/'
BG = (24, 26, 32)
ACCENT = (190, 72, 44)  # rust red, the Rail's colour before it wore Riley's cement
FADE = 50  # 0 leaves the scene at full strength, 255 hides it
PYLON_PITCH = 6  # blocks between Pylons
BEAM = 0.75  # the Rail's depth, in blocks
# The Train from the front, each car's kind and length in blocks, as CarBody draws them
TRAIN = [('burner', 5), ('cargo', 5), ('fluid', 5), ('cargo', 5)]
GAP = 0.2  # between coupled bodies

_cache = {}


def tex(name, s):
    if (name, s) not in _cache:
        _cache[name, s] = Image.open(f'{T}{name}.png').convert('RGBA').crop((0, 0, 16, 16)).resize((s, s), Image.NEAREST)
    return _cache[name, s]


def tile(img, name, s, x0, y0, x1, y1):
    # lay a sprite at s px per block across a box, from its own top-left,
    x0, y0, x1, y1 = round(x0), round(y0), round(x1), round(y1)
    t = tex(name, s)
    for y in range(y0, y1, s):
        for x in range(x0, x1, s):
            piece = t.crop((max(0, x0 - x), max(0, y0 - y), min(s, x1 - x), min(s, y1 - y)))
            img.alpha_composite(piece, (max(x, x0), max(y, y0)))


def car(img, kind, s, front, length, top):
    # one car seen from the side, facing right: its front at x = front, its roof at y = top
    back = front - (length - GAP) * s
    b = lambda h: top + (2.75 - h) * s  # a height above the floor's underside, in px
    tile(img, 'car/skirt', s, back + 0.1 * s, b(0.62), front - 0.1 * s, b(0))
    if kind == 'fluid':
        # a frame under a round tank, banded at its ends and carrying a Sight glass
        tile(img, 'car/shell', s, back, b(0.75), front, b(0.55))
        tank = Image.new('RGBA', img.size, (0, 0, 0, 0))
        tile(tank, 'car/tank', s, back + 0.1 * s, b(2.55), front - 0.1 * s, b(0.72))
        mask = Image.new('L', img.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle([back + 0.1 * s, b(2.55), front - 0.1 * s, b(0.72)], radius=0.9 * s, fill=255)
        img.paste(tank, (0, 0), mask)
        for x in (back + 0.6 * s, front - 0.9 * s):
            tile(img, 'car/trim', s, x, b(2.6), x + 0.3 * s, b(0.7))
        mid = (back + front) / 2
        tile(img, 'car/sight_glass', s, mid - 0.25 * s, b(2.2), mid + 0.25 * s, b(1.1))
        return
    tile(img, 'car/shell', s, back, b(2.6), front, b(0.55))
    tile(img, 'car/roof', s, back + 0.06 * s, b(2.75), front - 0.06 * s, b(2.6))
    tile(img, 'car/window', s, back + 0.16 * s, b(2.44), front - 0.16 * s, b(1.7))
    if kind == 'cargo':
        # its row of crates, a block square each, set into the side under the window band
        n = int(length - 1)
        x = (back + front) / 2 - n * s / 2
        for i in range(n):
            tile(img, 'car/crate_side', s, x + i * s, b(1.62), x + (i + 1) * s, b(0.62))
    else:
        # the Burner Locomotive's nose band and its firebox door
        tile(img, 'car/burner_nose', s, front - 1.4 * s, b(1.62), front, b(0.62))
        tile(img, 'burner_generator/front_on', s, front - 2.6 * s, b(1.62), front - 1.6 * s, b(0.62))


def scene(W, H, s, rail_top, margin):
    # the Locomotive's front margin px short of the right edge, and a Pylon under its cab
    img = Image.new('RGBA', (W, H), BG + (255,))
    beam = rail_top + BEAM * s
    front = W - margin
    # Pylons: a cement post up from the bottom, capped under the Rail
    for x in range(front - 3 * s - (front - 3 * s) // (PYLON_PITCH * s) * PYLON_PITCH * s - PYLON_PITCH * s, W, PYLON_PITCH * s):
        tile(img, 'pylon/post', s, x, beam + s, x + s, H)
        tile(img, 'pylon/cap', s, x, beam, x + s, beam + s)
    tile(img, 'rail/cement', s, 0, rail_top, W, beam)
    for kind, length in TRAIN:
        car(img, kind, s, front, length, rail_top + 0.45 * s - 2.75 * s)
        front -= length * s
    return img


def font(names, size):
    for n in names:
        if os.path.exists(n):
            return ImageFont.truetype(n, size)
    raise SystemExit('needs one of ' + ', '.join(names))


def make(W, H, out, title_size, s, margin):
    bh = int(title_size * 1.6)
    cy = int(H * 0.25)
    img = scene(W, H, s, cy + bh // 2 + s // 3 + int(2.3 * s), margin)
    img = Image.alpha_composite(img, Image.new('RGBA', (W, H), BG + (FADE,)))
    band = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(band).rectangle([0, cy - bh // 2, W, cy + bh // 2], fill=(16, 17, 22, 225))
    img = Image.alpha_composite(img, band)
    d = ImageDraw.Draw(img)
    lt = max(3, title_size // 25)
    d.rectangle([0, cy - bh // 2, W, cy - bh // 2 + lt], fill=ACCENT)
    d.rectangle([0, cy + bh // 2 - lt, W, cy + bh // 2], fill=ACCENT)
    f1 = font(['/System/Library/Fonts/Supplemental/Impact.ttf', '/usr/share/fonts/TTF/impact.ttf',
               os.path.expanduser('~/.local/share/fonts/msfonts/impact.ttf'),
               '/usr/share/fonts/noto/NotoSans-Black.ttf'], title_size)
    f2 = font(['/System/Library/Fonts/Supplemental/Arial Black.ttf', '/usr/share/fonts/TTF/ariblk.ttf',
               os.path.expanduser('~/.local/share/fonts/msfonts/ariblk.ttf'),
               '/usr/share/fonts/noto/NotoSans-Black.ttf'], int(title_size * 0.62))
    a, w = 'RAIL', 'works'
    b1, b2 = d.textbbox((0, 0), a, font=f1), d.textbbox((0, 0), w, font=f2)
    w1, w2, h1, h2 = b1[2] - b1[0], b2[2] - b2[0], b1[3] - b1[1], b2[3] - b2[1]
    pad, gap, sh = int(title_size * 0.16), int(title_size * 0.1), max(3, title_size // 20)
    bw = w2 + 2 * pad
    x = (W - (w1 + gap + bw)) // 2
    y = cy - h1 // 2 - b1[1]
    d.text((x + sh - b1[0], y + sh), a, font=f1, fill=(0, 0, 0))
    d.text((x - b1[0], y), a, font=f1, fill=ACCENT)
    bx, bt = x + w1 + gap, cy - h1 // 2
    d.rounded_rectangle([bx + sh, bt + sh, bx + bw + sh, bt + h1 + sh], radius=pad, fill=(0, 0, 0))
    d.rounded_rectangle([bx, bt, bx + bw, bt + h1], radius=pad, fill=(240, 240, 236))
    d.text((bx + pad - b2[0], bt + (h1 - h2) // 2 - b2[1]), w, font=f2, fill=BG)
    img.convert('RGB').save(out)


make(1280, 640, 'publish/railworks-cover.png', 150, 72, 60)
make(512, 512, 'publish/railworks-icon.png', 80, 44, 24)
