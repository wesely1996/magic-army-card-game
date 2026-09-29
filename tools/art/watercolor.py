"""Procedural watercolor + ink renderer used to generate the card art.

Each illustration is a game-icons.net silhouette (CC BY 3.0) turned into a
hand-drawn ink outline with loose watercolor washes that bleed past the lines,
on a textured paper background.
"""
import io
import json
import math
import os

import cairosvg
import numpy as np
from PIL import Image
from scipy import ndimage

ICONS = None


def load_icons(path):
    global ICONS
    with open(path) as f:
        ICONS = json.load(f)


def hex_rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


def icon_mask(name, size, flip=False):
    icons = ICONS["icons"]
    alias = ICONS.get("aliases", {})
    if name not in icons and name in alias:
        name = alias[name]["parent"]
    body = icons[name]["body"]
    w = ICONS.get("width", 512)
    h = ICONS.get("height", 512)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{size}" height="{size}">'
           f'<g fill="#000">{body.replace("currentColor", "#000")}</g></svg>')
    png = cairosvg.svg2png(bytestring=svg.encode())
    img = Image.open(io.BytesIO(png)).convert("RGBA")
    a = np.asarray(img)[:, :, 3].astype(np.float32) / 255.0
    if flip:
        a = a[:, ::-1]
    return a


def noise(shape, scale, rng, octaves=3):
    """Smooth value noise in [0,1]."""
    h, w = shape
    out = np.zeros(shape, np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        s = max(1, int(scale / (2 ** o)))
        small = rng.random((h // s + 2, w // s + 2)).astype(np.float32)
        big = ndimage.zoom(small, s, order=3)[:h, :w]
        out += big * amp
        total += amp
        amp *= 0.5
    out /= total
    out -= out.min()
    out /= max(out.max(), 1e-6)
    return out


def paper(h, w, rng, base="#F3EAD8"):
    col = hex_rgb(base)
    fiber = noise((h, w), 3, rng, 2) * 0.05 + noise((h, w), 40, rng, 3) * 0.06
    img = np.ones((h, w, 3), np.float32) * col
    img -= fiber[..., None] * 0.6
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2)
    img *= (1 - 0.10 * np.clip(d - 0.55, 0, 1))[..., None]
    return np.clip(img, 0, 1)


def wobble(mask, rng, amount=4.0, scale=60):
    h, w = mask.shape
    dx = (noise((h, w), scale, rng, 2) - 0.5) * 2 * amount
    dy = (noise((h, w), scale, rng, 2) - 0.5) * 2 * amount
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    return ndimage.map_coordinates(mask, [yy + dy, xx + dx], order=1, mode="constant")


def wash_layer(img, mask, color, rng, strength=0.55, edge=0.35, granulation=0.25):
    """Composite a watercolor wash (subtractive-ish multiply) for a soft mask."""
    h, w = mask.shape
    col = hex_rgb(color)
    m = np.clip(mask, 0, 1)
    # pigment pools at edges
    blurred = ndimage.gaussian_filter(m, 6)
    edge_dark = np.clip(m - blurred, 0, 1) * 2.5
    density = m * (0.55 + 0.45 * noise((h, w), 50, rng, 3))
    density += edge_dark * edge
    density *= 1 - granulation + granulation * noise((h, w), 2, rng, 1)
    density = np.clip(density * strength, 0, 1)[..., None]
    # multiply blend toward pigment colour
    return img * (1 - density) + img * col * density


def blob_mask(h, w, rng, cx, cy, rx, ry, rough=0.35, scale=80):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)
    n = noise((h, w), scale, rng, 3)
    field = 1 - d + (n - 0.5) * rough * 2
    return np.clip(field * 4, 0, 1)


def ink_outline(mask, rng, width=2.2, color="#2B2320", alpha=0.9):
    m = wobble(mask, rng, 2.5, 50)
    hard = (m > 0.5).astype(np.float32)
    er = ndimage.binary_erosion(hard, iterations=max(1, int(width))).astype(np.float32)
    di = ndimage.binary_dilation(hard, iterations=1).astype(np.float32)
    line = np.clip(di - er, 0, 1)
    line = ndimage.gaussian_filter(line, 0.7)
    # dry-brush breaks
    breaks = noise(mask.shape, 18, rng, 2)
    line *= np.clip((breaks - 0.12) * 4, 0.35, 1)
    return line * alpha, hex_rgb(color)


def apply_ink(img, line, col):
    a = np.clip(line, 0, 1)[..., None]
    return img * (1 - a) + col * a


def splatter(h, w, rng, n=14):
    m = np.zeros((h, w), np.float32)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    for _ in range(n):
        cx, cy = rng.random() * w, rng.random() * h
        r = rng.random() * 5 + 1.5
        m = np.maximum(m, np.clip(1 - np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r, 0, 1) * 3)
    return np.clip(m, 0, 1)


def paint(subject, palette, size=(400, 560), seed=1, crown=False, accent_icon=None, flip=False,
          subject_scale=0.78, subject_offset=(0, -0.11)):
    w, h = size
    rng = np.random.default_rng(seed)
    img = paper(h, w, rng)
    # background washes
    bgs = palette["bg"]
    for i, c in enumerate(bgs):
        cx = w * (0.25 + 0.5 * rng.random())
        cy = h * (0.2 + 0.6 * rng.random())
        m = blob_mask(h, w, rng, cx, cy, w * (0.55 + 0.25 * rng.random()), h * (0.45 + 0.2 * rng.random()))
        img = wash_layer(img, m, c, rng, strength=0.45, edge=0.5)
    # ground wash near bottom
    m = blob_mask(h, w, rng, w * 0.5, h * 1.02, w * 0.9, h * 0.28, rough=0.5)
    img = wash_layer(img, m, palette["ground"], rng, strength=0.5, edge=0.6)

    # subject
    s = int(min(w, h) * subject_scale)
    sm = icon_mask(subject, s, flip)
    full = np.zeros((h, w), np.float32)
    ox = (w - s) // 2 + int(subject_offset[0] * w)
    oy = (h - s) // 2 + int(subject_offset[1] * h)
    full[oy:oy + s, ox:ox + s] = sm
    # colour bleeds outside the lines, slightly offset, with uneven pigment
    bleed = ndimage.gaussian_filter(ndimage.shift(full, (5, -6), order=1), 4)
    bleed = np.clip(bleed * 1.7, 0, 1) * (0.75 + 0.25 * noise((h, w), 70, rng, 2))
    img = wash_layer(img, bleed, palette["subject"], rng, strength=0.8, edge=0.9, granulation=0.2)
    # soft shadow glaze on one side of the figure
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    grad = np.clip((xx - ox) / max(s, 1) * 0.8 + (yy - oy) / max(s, 1) * 0.6 - 0.35, 0, 1)
    shade = ndimage.gaussian_filter(full, 3) * grad
    img = wash_layer(img, shade, palette["shade"], rng, strength=0.55, edge=0.3, granulation=0.15)
    # a highlight lifted out of the wash (paper showing through)
    lift = ndimage.gaussian_filter(full, 6) * np.clip(1 - grad * 2.5, 0, 1) * blob_mask(h, w, rng, ox + s * 0.35, oy + s * 0.35, s * 0.3, s * 0.25, 0.5, 30)
    img = img * (1 - lift[..., None] * 0.35) + paper(h, w, rng) * lift[..., None] * 0.35

    if accent_icon:
        a_s = int(s * 0.34)
        am = icon_mask(accent_icon, a_s)
        acc = np.zeros((h, w), np.float32)
        ax, ay = int(w * 0.72 - a_s / 2), int(h * 0.16 - a_s / 2)
        acc[ay:ay + a_s, ax:ax + a_s] = am
        img = wash_layer(img, ndimage.gaussian_filter(acc, 2.5) * 1.3, palette.get("accent", "#C9A227"), rng, 0.8, 0.9)
        line, col = ink_outline(acc, rng, 1.6, alpha=0.8)
        img = apply_ink(img, line, col)

    if crown:
        c_s = int(s * 0.36)
        cm = icon_mask("crown", c_s)
        cr = np.zeros((h, w), np.float32)
        cx0, cy0 = (w - c_s) // 2 + int(subject_offset[0] * w), max(4, oy - int(c_s * 0.45))
        cr[cy0:cy0 + c_s, cx0:cx0 + c_s] = cm
        img = wash_layer(img, ndimage.gaussian_filter(cr, 2) * 1.4, "#E0A526", rng, 0.9, 0.9)
        line, col = ink_outline(cr, rng, 1.8, alpha=0.85)
        img = apply_ink(img, line, col)

    # ink linework (outer + interior details) with a faint second sketch pass
    line, col = ink_outline(full, rng, 2.4, alpha=0.92)
    img = apply_ink(img, line, col)
    line2, _ = ink_outline(ndimage.shift(full, (1.5, 2), order=1), rng, 1.2, alpha=0.25)
    img = apply_ink(img, line2, col)

    sp = splatter(h, w, rng, 10)
    img = wash_layer(img, sp, palette["subject"], rng, strength=0.55, edge=0.2, granulation=0.1)
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
