#!/usr/bin/env python3
"""
Convert the user's logo PNG into Android adaptive icon format.

Input: /home/z/my-project/upload/1791484378788.png (1024x1024, flower on magenta
       rounded square, gray checker pattern in corners baked-in).

Output:
- drawable/ic_launcher_background.xml — solid magenta (auto-extracted)
- drawable/ic_launcher_foreground.png — image with gray corners made transparent,
  scaled to 82% and shifted +8px right, +8px down
- mipmap-{mdpi..xxxhdpi}/ic_launcher.png + ic_launcher_round.png — legacy icons
- drawable/ic_launcher_monochrome.png — flower silhouette for themed icons
"""
from PIL import Image
import os
from collections import Counter

SRC = "/home/z/my-project/upload/1791484378788.png"
RES = "/home/z/coral-push/app/src/main/res"

img = Image.open(SRC).convert("RGBA")
w, h = img.size
print(f"Source: {w}x{h}")

# ─── Make gray checker pixels transparent (like the very first build) ─
# Reverting the "fill corners with magenta" change — that caused the
# whitish border issue. Going back to transparent corners.
new_img = img.copy()
pixels = new_img.load()
gray_count = 0
for x in range(w):
    for y in range(h):
        r, g, b, a = pixels[x, y]
        # Detect gray checker: R≈G≈B and high luminance
        if abs(r - g) < 15 and abs(g - b) < 15 and r > 180:
            pixels[x, y] = (0, 0, 0, 0)
            gray_count += 1
        # Detect near-gray transition pixels
        elif r > 200 and g > 200 and b > 200 and abs(r - g) < 25 and abs(g - b) < 25:
            pixels[x, y] = (0, 0, 0, 0)
            gray_count += 1
img = new_img
print(f"✓ Made {gray_count} gray pixels transparent (back to first-build approach)")

# ─── Auto-extract the magenta color ───────────────────────────────────
colors = Counter()
for x in range(0, w, 5):
    for y in range(0, h, 5):
        r, g, b, a = img.getpixel((x, y))
        if a > 200 and r > 200 and b > 150 and g < 150:
            colors[(r, g, b)] += 1
if colors:
    top = colors.most_common(20)
    avg_r = sum(c[0] for c, n in top) // len(top)
    avg_g = sum(c[1] for c, n in top) // len(top)
    avg_b = sum(c[2] for c, n in top) // len(top)
    magenta_hex = f"#{avg_r:02X}{avg_g:02X}{avg_b:02X}"
else:
    magenta_hex = "#FF61DC"
print(f"✓ Auto-extracted magenta: {magenta_hex}")

# ─── 1. Background XML — solid magenta ────────────────────────────────
bg_drawable = f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="{magenta_hex}" />
</shape>
'''
bg_path = f"{RES}/drawable/ic_launcher_background.xml"
with open(bg_path, "w") as f:
    f.write(bg_drawable)
print(f"✓ Background: {bg_path} (color: {magenta_hex})")

# ─── 2. Foreground PNG (432x432) — like v1.0.6 but moved right + UP ──
# ★ USER REQUEST: "make it like 1.0.6, but just move it little bit right and up"
# v1.0.6 was: 82% scale + 8px right + 8px DOWN
# This build: 82% scale + 5px right + 5px UP (smaller offset, opposite Y direction)
foreground_size = 432
flower_scale = 0.82  # matches v1.0.6 exactly
flower_size = int(foreground_size * flower_scale)  # ~354px
# Center the flower, then apply offset: right = +X, UP = -Y
center_offset = int((foreground_size - flower_size) / 2)  # ~39px (centers the 354px flower in 432px canvas)
offset_x = center_offset + 5   # +5px right shift (less than v1.0.6's 8px)
offset_y = center_offset - 5   # -5px = 5px UP shift (v1.0.6 was +8px down)

fg_canvas = Image.new("RGBA", (foreground_size, foreground_size), (0, 0, 0, 0))
flower_resized = img.resize((flower_size, flower_size), Image.LANCZOS)
fg_canvas.paste(flower_resized, (offset_x, offset_y), flower_resized)

fg_path = f"{RES}/drawable/ic_launcher_foreground.png"
fg_canvas.save(fg_path, "PNG")
print(f"✓ Foreground: {fg_path} ({foreground_size}x{foreground_size})")
print(f"  Flower scaled to {flower_scale*100:.0f}% ({flower_size}x{flower_size}) — matches v1.0.6")
print(f"  Offset: +5px right, -5px up (v1.0.6 was +8px right, +8px down)")

# ─── 3. Legacy PNG icons ──────────────────────────────────────────────
densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for density, size in densities.items():
    out_dir = f"{RES}/mipmap-{density}"
    os.makedirs(out_dir, exist_ok=True)
    legacy_img = fg_canvas.resize((size, size), Image.LANCZOS)
    legacy_img.save(f"{out_dir}/ic_launcher.png", "PNG")
    legacy_img.save(f"{out_dir}/ic_launcher_round.png", "PNG")
    print(f"✓ mipmap-{density}/ic_launcher.png ({size}x{size})")

# ─── 4. Monochrome icon ───────────────────────────────────────────────
mono_img = Image.new("RGBA", img.size, (0, 0, 0, 0))
for x in range(img.size[0]):
    for y in range(img.size[1]):
        r, g, b, a = img.getpixel((x, y))
        if a < 128: continue
        if (r + g + b) / 3 < 100:
            mono_img.putpixel((x, y), (255, 255, 255, 255))
mono_img = mono_img.resize((flower_size, flower_size), Image.LANCZOS)
# Apply the same right+UP offset as the foreground (matches v1.0.6 + right+up)
mono_canvas = Image.new("RGBA", (foreground_size, foreground_size), (0, 0, 0, 0))
mono_canvas.paste(mono_img, (offset_x, offset_y), mono_img)
mono_path = f"{RES}/drawable/ic_launcher_monochrome.png"
mono_canvas.save(mono_path, "PNG")
print(f"✓ Monochrome: {mono_path}")

print(f"\n✅ Done. Background: {magenta_hex}, flower scaled + shifted.")
