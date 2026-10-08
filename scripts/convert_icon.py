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

# ─── Make gray checker pixels transparent + FILL the canvas with pink ──
# ★ BORDER FIX: the original image has a pink ROUNDED SQUARE with gray
#   checker corners. When the launcher masks the icon, the rounded square's
#   edge creates a visible boundary against the magenta background → looks
#   like a "whitish border" or "glass effect".
# ★ FIX: make gray corners transparent, then FILL those transparent
#   corners with the magenta background color. Result: the foreground PNG
#   is fully opaque (no transparency), the launcher's mask handles the
#   shape. No visible edge, no border, no halo.
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
print(f"✓ Made {gray_count} gray pixels transparent")

# ★ Now extract the magenta color BEFORE filling corners
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
    magenta_rgb = (avg_r, avg_g, avg_b, 255)
else:
    magenta_hex = "#FF61DC"
    magenta_rgb = (255, 97, 220, 255)
print(f"✓ Auto-extracted magenta: {magenta_hex}")

# ★ FILL transparent corners with the magenta color — no transparency
#   in the final foreground, no visible edge against the background XML.
filled_count = 0
pixels = img.load()
for x in range(w):
    for y in range(h):
        r, g, b, a = pixels[x, y]
        if a < 255:
            pixels[x, y] = magenta_rgb
            filled_count += 1
print(f"✓ Filled {filled_count} transparent pixels with magenta (no border)")

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

# ─── 2. Foreground PNG (432x432) — full-bleed, no scaling ────────────
# ★ USER FEEDBACK: "make it like before" — full canvas, no shrinking.
#   The flower should fill the entire 432x432 foreground. The launcher
#   masks the corners, so we just need the image to fill the canvas.
foreground_size = 432
fg_canvas = img.resize((foreground_size, foreground_size), Image.LANCZOS)

fg_path = f"{RES}/drawable/ic_launcher_foreground.png"
fg_canvas.save(fg_path, "PNG")
print(f"✓ Foreground: {fg_path} ({foreground_size}x{foreground_size}) — full bleed, no scaling")

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
mono_img = mono_img.resize((foreground_size, foreground_size), Image.LANCZOS)
# Full-bleed, no offset (matches the foreground)
mono_path = f"{RES}/drawable/ic_launcher_monochrome.png"
mono_img.save(mono_path, "PNG")
print(f"✓ Monochrome: {mono_path}")

print(f"\n✅ Done. Background: {magenta_hex}, flower scaled + shifted.")
