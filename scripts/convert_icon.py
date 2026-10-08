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
# ★ USER ADJUSTMENT: make the flower smaller (~82% of canvas) + shift
#   slightly right and down so the petal ends aren't cropped by the
#   launcher's circular/squircle mask.
#
# The adaptive icon safe zone is the center 66% (~285px in a 432px canvas).
# The flower was filling most of the image → petal ends got cropped.
# Fix: scale the source down to 82% and offset it slightly right+down.
foreground_size = 432
flower_scale = 0.82  # 82% of canvas — leaves margin so petals aren't cropped
flower_size = int(foreground_size * flower_scale)  # ~354px
offset_x = int((foreground_size - flower_size) / 2) + 8   # +8px right shift
offset_y = int((foreground_size - flower_size) / 2) + 8   # +8px down shift

# Create a transparent 432x432 canvas + paste the scaled flower at the offset
fg_canvas = Image.new("RGBA", (foreground_size, foreground_size), (0, 0, 0, 0))
flower_resized = img.resize((flower_size, flower_size), Image.LANCZOS)
fg_canvas.paste(flower_resized, (offset_x, offset_y), flower_resized)

fg_path = f"{RES}/drawable/ic_launcher_foreground.png"
fg_canvas.save(fg_path, "PNG")
print(f"✓ Foreground: {fg_path} ({foreground_size}x{foreground_size})")
print(f"  Flower scaled to {flower_scale*100:.0f}% ({flower_size}x{flower_size})")
print(f"  Offset: +{offset_x}px right, +{offset_y}px down")

# ─── 3. Legacy PNG icons (for Android < 8.0) ─────────────────────────
# Uses the same adjusted canvas as the adaptive foreground (smaller flower,
# shifted right+down) so the legacy icons match the adaptive icon look.
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
    # Scale the adjusted canvas down to this density's size
    legacy_img = fg_canvas.resize((size, size), Image.LANCZOS)
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
# ★ Apply the same right+down offset as the foreground so the mono icon
#   aligns with the color icon.
mono_canvas = Image.new("RGBA", (foreground_size, foreground_size), (0, 0, 0, 0))
mono_canvas.paste(mono_img, (offset_x, offset_y), mono_img)
mono_path = f"{RES}/drawable/ic_launcher_monochrome.png"
mono_canvas.save(mono_path, "PNG")
print(f"✓ Monochrome: {mono_path}")

print(f"\n✅ Done. Background: {magenta_hex}, flower preserved.")
