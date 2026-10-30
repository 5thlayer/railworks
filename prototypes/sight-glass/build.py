#!/usr/bin/env python3
"""PROTOTYPE, throwaway: the Fluid car's Sight glass, so it stops reading as a screen.

    python3 prototypes/sight-glass/build.py    # writes prototypes/sight-glass/sight-glass.prototype.html

Inlines Railworks' own car and block textures (Malcolm Riley's, CC BY 4.0) into one page.
"""
import base64
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
BLOCK = REPO / "src/main/resources/assets/railworks/textures/block"
NAMES = ["car/tank", "car/trim", "car/shell", "car/skirt", "steel", "fluid_hatch/provider"]


def main():
    textures = {n: "data:image/png;base64," + base64.b64encode((BLOCK / f"{n}.png").read_bytes()).decode() for n in NAMES}
    page = (HERE / "template.html").read_text().replace("/*TEXTURES*/{}", json.dumps(textures))
    out = HERE / "sight-glass.prototype.html"
    out.write_text(page)
    print(out)


if __name__ == "__main__":
    main()
