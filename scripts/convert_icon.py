#!/usr/bin/env python3
"""
Convert the user's logo PNG into Android adaptive icon format.

Input: 1024x1024 RGB PNG — flower on pink/magenta gradient, gray checker
       pattern in corners (baked-in transparency representation).

Output:
- drawable/ic_launcher_background.xml — solid magenta (#FF61DC)
- drawable/ic_launcher_foreground.png — image with gray corners made transparent
- mipmap-{mdpi..xxxhdpi}/ic_launcher.png — legacy icons with transparent corners
- drawable/ic_launcher_monochrome.png — flower silhouette only (for themed icons)
"""
from PIL import Image
import os

SRC = "/home/z/my-project/upload/1791468979420.png"
RES = "/home/z/coral-push/app/src/main/res"

# Open source and convert to RGBA (add alpha channel)
img = Image.open(SRC).convert("RGBA")
w, h = img.size
print(f"Source: {w}x{h}, converted to RGBA")

# ─── Make gray checker pixels transparent ─────────────────────────────
# The gray checker pattern is in the corners (outside the pink rounded square).
# Gray pixels have R≈G≈B and high luminance (>200). Pink pixels have R>G>B.
# The flower is dark (luminance < 100).
# So: if R≈G≈B AND luminance > 180 → it's the gray checker → make transparent.
# Let's also handle the transition pixels (anti-aliased edges of the pink square).
new_img = img.copy()
pixels = new_img.load()
for x in range(w):
    for y in range(h):
        r, g, b, a = pixels[x, y]
        # Detect gray checker: R≈G≈B and high luminance
        if abs(r - g) < 15 and abs(g - b) < 15 and r > 180:
            # This is a gray checker pixel → make transparent
            pixels[x, y] = (0, 0, 0, 0)
        # Detect near-gray transition pixels (slightly pink-tinted gray)
        elif r > 200 and g > 200 and b > 200 and abs(r - g) < 25 and abs(g - b) < 25:
            # Transition pixel → make transparent
            pixels[x, y] = (0, 0, 0, 0)
img = new_img
print("✓ Made gray checker corners transparent")

# ─── Extract the magenta/pink color ───────────────────────────────────
# Sample the brightest pink pixel (near center of the pink square)
# The brightest sampled was #FF61DC at (716, 716)
magenta_hex = "#FF61DC"
print(f"Using magenta: {magenta_hex}")

# ─── 1. Background XML — solid magenta ────────────────────────────────
bg_drawable = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
    Coral's adaptive icon background.
    Solid magenta — matches the user's logo color.
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="{magenta_hex}" />
</shape>
'''
bg_path = f"{RES}/drawable/ic_launcher_background.xml"
with open(bg_path, "w") as f:
    f.write(bg_drawable)
print(f"✓ Background: {bg_path} (color: {magenta_hex})")

# ─── 2. Foreground PNG (432x432 = 108dp @ xxxhdpi) ───────────────────
# The foreground layer includes the pink square + flower.
# The launcher masks it into circle/squircle, so corners (now transparent)
# will be outside the mask.
foreground_size = 432
fg_img = img.resize((foreground_size, foreground_size), Image.LANCZOS)
fg_path = f"{RES}/drawable/ic_launcher_foreground.png"
fg_img.save(fg_path, "PNG")
print(f"✓ Foreground: {fg_path} ({foreground_size}x{foreground_size})")

# ─── 3. Legacy PNG icons (for Android < 8.0) ─────────────────────────
densities = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
for density, size in densities.items():
    out_dir = f"{RES}/mipmap-{density}"
    os.makedirs(out_dir, exist_ok=True)
    legacy_img = img.resize((size, size), Image.LANCZOS)
    legacy_img.save(f"{out_dir}/ic_launcher.png", "PNG")
    legacy_img.save(f"{out_dir}/ic_launcher_round.png", "PNG")
    print(f"✓ mipmap-{density}/ic_launcher.png ({size}x{size})")

# ─── 4. Monochrome icon (for themed icons on Android 13+) ─────────────
# Extract the flower silhouette: dark pixels → white opaque, everything else → transparent.
mono_img = Image.new("RGBA", img.size, (0, 0, 0, 0))
for x in range(img.size[0]):
    for y in range(img.size[1]):
        r, g, b, a = img.getpixel((x, y))
        if a < 128:
            continue
        luminance = (r + g + b) / 3
        if luminance < 100:  # dark = flower
            mono_img.putpixel((x, y), (255, 255, 255, 255))
mono_img = mono_img.resize((foreground_size, foreground_size), Image.LANCZOS)
mono_path = f"{RES}/drawable/ic_launcher_monochrome.png"
mono_img.save(mono_path, "PNG")
print(f"✓ Monochrome: {mono_path}")

print(f"\n✅ Done. Background: {magenta_hex}, flower preserved.")
