"""Generates all watercolor card art and UI textures for the app.

Usage:  python3 tools/art/generate.py <path to @iconify-json/game-icons icons.json> [card ids...]

With card ids, only those illustrations are (re)painted; seeds stay tied to manifest order,
so repainting a single card gives the same picture as a full run.

Get icons.json from the npm package @iconify-json/game-icons (CC BY 3.0,
https://game-icons.net). Requires: pillow numpy scipy cairosvg.
"""
import json
import os
import re
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

import watercolor as wc

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")

RACE_PALETTES = {
    "w": {"bg": ["#9FB4D0", "#C8B8DE"], "ground": "#8E9E7C", "subject": "#5B6C8F", "shade": "#2F3A57", "accent": "#E8D48A"},
    "b": {"bg": ["#C7A27A", "#9DB38A"], "ground": "#7D8F5E", "subject": "#8A5A3B", "shade": "#4A2E1C", "accent": "#D98C3A"},
    "h": {"bg": ["#9CC7E0", "#F0D9A0"], "ground": "#B7C98F", "subject": "#B7832F", "shade": "#5E3F12", "accent": "#6FA8D6"},
    "s": {"bg": ["#9CC9A6", "#B9A5D0"], "ground": "#7E9C6B", "subject": "#3F7D57", "shade": "#1F4230", "accent": "#9BC53D"},
    "l": {"bg": ["#F2C57C", "#E89B6B"], "ground": "#B9A265", "subject": "#C0662B", "shade": "#6E3317", "accent": "#E0A526"},
    "v": {"bg": ["#AEB58C", "#9A88AE"], "ground": "#6F7556", "subject": "#5E5648", "shade": "#2B2822", "accent": "#8FD14F"},
}
TYPE_TINT = {"magic": "#B89AD8", "strategy": "#D8C08A", "equipment": "#A9B4BE"}



def card_types():
    """Reads each card's type straight from the Kotlin card database."""
    src = open(os.path.join(ROOT, "core/src/main/kotlin/com/kingofthebeasts/core/data/CardDatabase.kt")).read()
    found = re.findall(r'add\((unit|king|magic|strategy|equipment)\("(\w+)"', src)
    return {cid: ("unit" if kind in ("unit", "king") else kind) for kind, cid in found}


def main(icons_path, only=None):
    wc.load_icons(icons_path)
    manifest = json.load(open(os.path.join(HERE, "art_manifest.json")))
    types = card_types()
    os.makedirs(RES, exist_ok=True)
    for i, (cid, meta) in enumerate(manifest.items()):
        if only and cid not in only:
            continue
        pal = dict(RACE_PALETTES[cid[0]])
        t = types[cid]
        if t in TYPE_TINT:
            pal["bg"] = [pal["bg"][0], TYPE_TINT[t]]
        img = wc.paint(meta["icon"], pal, seed=1000 + i * 17, crown=meta.get("crown", False),
                       accent_icon=meta.get("accent"), flip=meta.get("flip", False),
                       subject_scale=0.52 if t == "unit" else 0.54,
                       subject_offset=(0, -0.145 if t == "unit" else -0.12))
        img.save(os.path.join(RES, f"art_{cid}.webp"), quality=82, method=6)
        print("art", cid)
    if only:
        return
    board_texture()
    paper_texture()
    launcher_icon()


def board_texture(size=1024):
    rng = np.random.default_rng(7)
    img = wc.paper(size, size, rng, base="#F1E6D0")
    tile = size // 8
    light, dark = "#E9D9B5", "#9CAF88"
    for y in range(8):
        for x in range(8):
            m = np.zeros((size, size), np.float32)
            pad = 3
            m[y * tile + pad:(y + 1) * tile - pad, x * tile + pad:(x + 1) * tile - pad] = 1
            m = wc.wobble(ndimage.gaussian_filter(m, 2.5), rng, 3, 40)
            col = dark if (x + y) % 2 == 0 else light
            img = wc.wash_layer(img, m, col, rng, strength=0.75 if col == dark else 0.45, edge=0.8, granulation=0.3)
    # hand-drawn grid
    grid = np.zeros((size, size), np.float32)
    for k in range(9):
        c = min(size - 2, k * tile)
        grid[:, max(0, c - 1):c + 2] = 1
        grid[max(0, c - 1):c + 2, :] = 1
    grid = wc.wobble(grid, rng, 2.5, 90)
    grid = ndimage.gaussian_filter(grid, 0.8) * np.clip(wc.noise((size, size), 25, rng, 2) * 1.4, 0.35, 1)
    img = wc.apply_ink(img, grid * 0.55, wc.hex_rgb("#3B3026"))
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(os.path.join(RES, "board_texture.webp"), quality=85, method=6)
    print("board")


def paper_texture(w=720, h=1280):
    rng = np.random.default_rng(3)
    img = wc.paper(h, w, rng, base="#F4ECDC")
    for c, (cx, cy) in zip(["#C9D6E8", "#EBCFA8", "#CFE0C4"], [(0.1, 0.08), (0.95, 0.5), (0.2, 0.95)]):
        m = wc.blob_mask(h, w, rng, w * cx, h * cy, w * 0.55, h * 0.3, 0.5, 90)
        img = wc.wash_layer(img, m, c, rng, strength=0.35, edge=0.5)
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(os.path.join(RES, "paper_background.webp"), quality=80, method=6)
    print("paper")


def launcher_icon():
    """Adaptive-icon foreground (108dp canvas, art inside the 66dp safe zone) plus legacy PNGs."""
    rng = np.random.default_rng(11)
    size = 432
    pal = {"bg": ["#C8B8DE"], "ground": "#9DB38A", "subject": "#5B6C8F", "shade": "#2F3A57"}
    art = wc.paint("wolf-howl", pal, size=(size, size), seed=5, crown=True, subject_scale=0.5, subject_offset=(0, 0.06))
    art.save(os.path.join(RES, "ic_launcher_foreground.webp"), quality=90, method=6)
    mip = os.path.join(ROOT, "app", "src", "main", "res")
    for folder, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        crop = art.crop((int(size * 0.14), int(size * 0.14), int(size * 0.86), int(size * 0.86))).resize((px, px), Image.LANCZOS)
        mask = Image.new("L", (px, px), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, px - 1, px - 1), radius=px // 5, fill=255)
        out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        out.paste(crop, (0, 0), mask)
        os.makedirs(os.path.join(mip, f"mipmap-{folder}"), exist_ok=True)
        out.save(os.path.join(mip, f"mipmap-{folder}", "ic_launcher.png"))
    print("launcher")


if __name__ == "__main__":
    main(sys.argv[1], set(sys.argv[2:]))
