"""Draws the grey, animated portal swirl that colored portals are tinted from.

Generated from math (two counter-rotating spirals, the way Minecraft's portal
animation was first made), not copied from Minecraft's texture, so it can
ship with the mod. Run from the repo root after changing it:
    python3 scripts/make_portal_texture.py
Writes common/src/main/resources/assets/colorednetherportals/textures/block/portal_swirl.png
(16x16 per frame, FRAMES frames stacked vertically) and its .mcmeta, plus
portal_overlay.png: the texture behind the in-portal screen effect and the
loading screen. The game replaces its frames at runtime (vanilla's frames,
or the swirl in a portal's color); the purple swirl shipped here is only a
fallback.
Needs Pillow.
"""
import json
import math
import os
import random

from PIL import Image

SIZE = 16
FRAMES = 32
OUT = "common/src/main/resources/assets/colorednetherportals/textures/block/portal_swirl.png"
ALPHA = 215            # more opaque than vanilla (155), so colors stay deep against a bright sky
LOW, HIGH = 0.45, 1.0  # brightness range: dark lanes to bright highlights; the tint is a lighter version of the chosen color, so mid-tones land on it

rng = random.Random(1906)
frames = Image.new("RGBA", (SIZE, SIZE * FRAMES))
for frame in range(FRAMES):
    for y in range(SIZE):
        for x in range(SIZE):
            n = 0.0
            for k in range(2):
                # Two spirals, centred half a tile apart, turning in opposite directions.
                xd = (x - k * SIZE / 2) / SIZE * 2
                yd = (y - k * SIZE / 2) / SIZE * 2
                xd = xd + 2 if xd < -1 else xd - 2 if xd >= 1 else xd
                yd = yd + 2 if yd < -1 else yd - 2 if yd >= 1 else yd
                dd = xd * xd + yd * yd
                angle = math.atan2(yd, xd) + (frame / FRAMES * 2 * math.pi - dd * 10 + k * 2) * (k * 2 - 1)
                n += (math.sin(angle) + 1) / 2 / (dd + 1) * 0.5
            n = min(1.0, max(0.0, n + rng.random() * 0.1))
            level = LOW + (HIGH - LOW) * n ** 1.5
            g = round(255 * level)
            frames.putpixel((x, frame * SIZE + y), (g, g, g, ALPHA))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
frames.save(OUT)
with open(OUT + ".mcmeta", "w") as f:
    json.dump({"animation": {"frametime": 2}}, f, indent=2)
    f.write("\n")
print("wrote", OUT)

# The screen-effect texture: same swirl, vanilla-like purple, one frame per tick like vanilla.
OVERLAY = OUT.replace("portal_swirl.png", "portal_overlay.png")
PURPLE = (126, 46, 255)
overlay = Image.new("RGBA", frames.size)
for y in range(frames.height):
    for x in range(SIZE):
        g = frames.getpixel((x, y))[0]
        overlay.putpixel((x, y), (PURPLE[0] * g // 255, PURPLE[1] * g // 255, PURPLE[2] * g // 255, 158))
overlay.save(OVERLAY)
with open(OVERLAY + ".mcmeta", "w") as f:
    json.dump({"animation": {"frametime": 1}}, f, indent=2)
    f.write("\n")
print("wrote", OVERLAY)
