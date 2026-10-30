# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Cover and icon, in the suite's style (Wireworks' and Groundworks' make-cover.py): a row of Pylons
# carrying the Rail, and a Train riding on it, seen from the side and drawn from Railworks' own
# textures (scripts/build-textures.py), so nothing of Mojang's is in them (libworks ADR 0012). Each car
# is its CarBody's boxes, read from the compiled classes and laid in their panels' sprites as
# CarRenderer lays them, so the cars are the game's own. Run from the repo root with Pillow installed,
# after `sh ./gradlew compileJava`.
import math
import os
import subprocess
import tempfile

from PIL import Image, ImageChops, ImageDraw, ImageFont

T = 'src/main/resources/assets/railworks/textures/block/'
CLASSES = 'build/classes/java/main'
BG = (24, 26, 32)
ACCENT = (190, 72, 44)  # rust red, the Rail's colour before it wore Riley's cement
FADE = 50  # 0 leaves the scene at full strength, 255 hides it
PYLON_PITCH = 6  # blocks between Pylons
# RailGeometry: the flange the Rail rests on, the web the cars straddle, and its steel running strip
FLANGE, WEB, STRIP = 0.15, 0.5, 2 / 16
CAR_PITCH = 5  # coupled cars' middles apart
# Each Train from the front, by the CarBody each car is
TRAIN = ['burner', 'cargo', 'fluid', 'cargo']
ELECTRIC_TRAIN = ['electric', 'fluid', 'fluid', 'cargo']
FLUID = (63, 118, 228)  # what the Sight glasses show, water's blue as a flat colour
FLUID_LEVEL = 0.65

# CarRenderer.look, for a Locomotive holding fuel or charge: each panel's sprite, tint and how it is laid
TILED, CENTRED, WHOLE, FLAT = 'tiled', 'centred', 'whole', 'flat'
LOOKS = {
    'SHELL': ('car/shell', None, TILED), 'ROOF': ('car/roof', None, TILED),
    'SKIRT': ('car/skirt', None, TILED), 'TRIM': ('car/trim', None, TILED),
    'WINDOW': ('car/window', None, CENTRED), 'BURNER_NOSE': ('car/burner_nose', None, CENTRED),
    'ELECTRIC_NOSE': ('car/electric_nose', None, CENTRED), 'TANK': ('car/tank', None, TILED),
    'FIREBOX': ('burner_generator/front_on', None, WHOLE), 'COIL': ('charger/sides', None, WHOLE),
    'STACK': ('steel', (0x55, 0x50, 0x4C), TILED), 'LAMP': (None, (0xF2, 0xE5, 0x9E), FLAT),
    'GLASS': ('car/sight_glass', None, WHOLE), 'GLINT': ('car/glint', None, WHOLE),
    'MARK': (None, (0xE6, 0xE0, 0xC8), FLAT), 'CRATE': ('car/crate_side', None, WHOLE),
}

DUMP = """
import io.github._5thlayer.railworks.rail.CarBody;
class Dump {
    static void box(String name, CarBody.Box b) {
        System.out.println(name + " " + b.minX() + " " + b.minY() + " " + b.minZ() + " " + b.maxX() + " " + b.maxY() + " " + b.maxZ());
    }
    public static void main(String[] args) {
        String[] names = {"burner", "electric", "cargo", "fluid"};
        CarBody[] bodies = {CarBody.LOCOMOTIVE, CarBody.ELECTRIC, CarBody.CARGO, CarBody.FLUID};
        for (int i = 0; i < bodies.length; i++) {
            System.out.println("car " + names[i]);
            for (CarBody.Part p : bodies[i].parts()) box(p.panel().name(), p.box());
            for (CarBody.Box b : bodies[i].crates()) box("CRATE", b);
            for (CarBody.SightGlass g : bodies[i].sightGlasses()) box("PANE", g.pane());
        }
    }
}
"""


def bodies():
    # every car's boxes, each (panel, minX, minY, minZ, maxX, maxY, maxZ), from CarBody itself
    if not os.path.isdir(CLASSES):
        raise SystemExit(f'no {CLASSES}: run sh ./gradlew compileJava first')
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, 'Dump.java')
        with open(src, 'w') as f:
            f.write(DUMP)
        out = subprocess.run(['java', '--class-path', CLASSES, src], check=True, capture_output=True, text=True).stdout
    cars, boxes = {}, None
    for row in out.split('\n'):
        word = row.split()
        if not word:
            continue
        if word[0] == 'car':
            boxes = cars[word[1]] = []
        else:
            boxes.append((word[0], *map(float, word[1:])))
    return cars


BODIES = bodies()
_cache = {}


def sprite(name, crop=(0, 0, 16, 16)):
    if (name, crop) not in _cache:
        _cache[name, crop] = Image.open(f'{T}{name}.png').convert('RGBA').crop(crop)
    return _cache[name, crop]


def tinted(img, colour):
    if colour is None:
        return img
    rgb = ImageChops.multiply(img.convert('RGB'), Image.new('RGB', img.size, colour))
    rgb.putalpha(img.getchannel('A'))
    return rgb


def tiled(img, tile, x0, y0, x1, y1, ax, ay):
    # tile across a box, the tiles' corners on (ax, ay) and every tile's size from it
    tw, th = tile.size
    for y in range(ay + math.floor((y0 - ay) / th) * th, y1, th):
        for x in range(ax + math.floor((x0 - ax) / tw) * tw, x1, tw):
            piece = tile.crop((max(0, x0 - x), max(0, y0 - y), min(tw, x1 - x), min(th, y1 - y)))
            img.alpha_composite(piece, (max(x, x0), max(y, y0)))


def car(img, kind, s, cx, ride):
    # a car seen from its left side, its front to the right: its middle at x = cx, the bottom of its
    # box, on the Rail's flange, at y = ride. Each box shows its -x face, the farthest first, the
    # ones deeper in the car shaded darker.
    boxes = BODIES[kind]
    for panel, x0, y0, z0, x1, y1, z1 in sorted(boxes, key=lambda b: -b[1]):
        if panel == 'PANE':
            continue
        name, colour, lay = LOOKS[panel]
        left, right = round(cx + z0 * s), round(cx + z1 * s)
        top, bottom = round(ride - y1 * s), round(ride - y0 * s)
        if right <= left or bottom <= top:
            continue
        shade = 1 - 0.45 * min(1.0, max(0.0, (x0 + 0.99) / 1.9))
        light = tuple(round(c * shade) for c in (colour or (255, 255, 255)))
        if lay == FLAT:
            img.alpha_composite(Image.new('RGBA', (right - left, bottom - top), light + (255,)), (left, top))
        elif lay == WHOLE:
            img.alpha_composite(tinted(sprite(name), light).resize((right - left, bottom - top), Image.NEAREST), (left, top))
        else:
            # tiled a block to a tile from the block edges either side of the car's middle block, and a
            # band's sprite centred up it
            lift = 0.5 - (y0 + y1) / 2 if lay == CENTRED else 0.0
            tile = tinted(sprite(name), light).resize((s, s), Image.NEAREST)
            tiled(img, tile, left, top, right, bottom, round(cx - 0.5 * s), round(ride + lift * s))
    for panel, x0, y0, z0, x1, y1, z1 in boxes:
        if panel == 'PANE' and x0 < 0:
            # the near Sight glass, its fluid up to the level
            fill = y0 + (y1 - y0) * FLUID_LEVEL
            img.alpha_composite(Image.new('RGBA', (round((z1 - z0) * s), round((fill - y0) * s)), FLUID + (255,)),
                                (round(cx + z0 * s), round(ride - fill * s)))


def line(W, H, s, rail_top, margin, train):
    # a line of Pylons and its Train heading right, the Locomotive's front margin px short of the right
    # edge and a Pylon under its Engine room; rail_top is the top of the Rail's web
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ride = round(rail_top + WEB * s)
    arm = round(ride + FLANGE * s)  # a Pylon's top, which the flange rests on
    cx = round(W - margin - 2.4 * s)
    px = lambda n: round(n * s / 16)  # pixels of a 16-pixel block
    # Pylons: a cement post up from the bottom, its top segment capped under a steel plate (SpanRenderer)
    pitch = PYLON_PITCH * s
    for x in range(round(cx - 0.5 * s) % pitch - pitch, W, pitch):
        tiled(img, sprite('pylon/post', (5, 0, 11, 16)).resize((px(6), s), Image.NEAREST),
              x + px(5), arm, x + px(11), H, x + px(5), arm)
        tiled(img, sprite('pylon/cap', (2, 1, 14, 5)).resize((px(12), px(4)), Image.NEAREST),
              x + px(2), arm + px(1), x + px(14), arm + px(5), x + px(2), arm + px(1))
        tiled(img, sprite('steel', (3, 0, 13, 1)).resize((px(10), px(1)), Image.NEAREST),
              x + px(3), arm, x + px(13), arm + px(1), x + px(3), arm)
    # the Rail: the flange, the web over it and the steel strip along the web's top
    cement = sprite('rail/cement').resize((s, s), Image.NEAREST)
    tiled(img, cement, 0, ride, W, arm, 0, ride)
    tiled(img, cement, 0, round(rail_top), W, ride, 0, round(rail_top))
    tiled(img, sprite('steel').resize((s, s), Image.NEAREST), 0, round(rail_top), W, round(rail_top + STRIP * s),
          0, round(rail_top))
    for i, kind in enumerate(train):
        car(img, kind, s, cx - i * CAR_PITCH * s, ride)
    return img


def font(names, size):
    for n in names:
        if os.path.exists(n):
            return ImageFont.truetype(n, size)
    raise SystemExit('needs one of ' + ', '.join(names))


def make(W, H, out, title_size, s, band_at, lines=None):
    # lines: each (rail_top, margin, train, heading left), back to front; by default one Train just under
    # the band
    bh = int(title_size * 1.6)
    cy = int(H * band_at)  # the title band's middle, as a fraction of the height
    if lines is None:
        lines = [(cy + bh // 2 + s // 3 + int(2.3 * s), 60, TRAIN, False)]
    img = Image.new('RGBA', (W, H), BG + (255,))
    for rail_top, margin, train, left in lines:
        layer = line(W, H, s, rail_top, margin, train)
        img.alpha_composite(layer.transpose(Image.FLIP_LEFT_RIGHT) if left else layer)
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


make(1280, 640, 'publish/railworks-cover.png', 150, 72, 0.25)
# the icon's Burner Train runs right above the band, its Pylons standing down behind it, and an Electric
# Train runs left below it
make(512, 512, 'publish/railworks-icon.png', 80, 40, 0.5,
     [(140, 20, TRAIN, False), (432, 20, ELECTRIC_TRAIN, True)])
