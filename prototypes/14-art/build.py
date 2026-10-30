#!/usr/bin/env python3
"""PROTOTYPE for #14, throwaway: inline the textures into one self-contained three.js page.

    python3 prototypes/14-art/build.py    # writes prototypes/14-art/art-14.prototype.html

Reads Malcolm Riley's unused textures (CC BY 4.0) from the sibling checkout ../unused-textures,
and Railworks' current block textures.
"""
import base64
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
RILEY = REPO.parent / "unused-textures" / "blocks"
OURS = REPO / "src/main/resources/assets/railworks/textures/block"

RILEY_NAMES = """
space-plating-plain space-plating-rivets-horizontal space-plating-convex-rivets-horizontal
space-plating-inset-horizontal space-plating-vent space-plating-access-hatch space-plating-square
space-plating-stripe-yellow-horizontal-undamaged space-plating-stripe-blue-horizontal-undamaged
space-plating-stripe-orange-horizontal-undamaged
space_crate_sides space_crate_top metal_tank_copper_sides metal_tank_copper_ends
gray_cement_horizontal gray_cement_vertical light_gray_cement_horizontal light_gray_cement_vertical
concrete_slabs concrete_plain concrete_paver steel_block metal_steel_block steel_block_rusty
device_smelter_front_lit device_smelter_top brick_refractory copper_coil_side battery_top battery_bottom
stone_tiles_small device_terminal_front_on industrial_metal_duct_flat_1 block_red_metal
""".split()


def uri(path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode()


def main():
    textures = {"r/" + n: uri(RILEY / f"{n}.png") for n in RILEY_NAMES}
    for png in sorted(OURS.rglob("*.png")):
        textures["w/" + png.relative_to(OURS).with_suffix("").as_posix()] = uri(png)
    page = (HERE / "template.html").read_text()
    page = page.replace("/*TEXTURES*/{}", json.dumps(textures))
    out = HERE / "art-14.prototype.html"
    out.write_text(page)
    print(out)


if __name__ == "__main__":
    main()
