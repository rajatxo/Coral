# Coral — Song Transition (Blur + Palette Sync) Blueprint

> **The "old song blur showing when changing songs" fix.**
> Share this entire file with any future AI assistant (or paste it back to me) and they'll immediately understand how the synced song transition works, why each piece exists, and how to modify it without breaking the sync.

**Working commit:** `550bfac` — September 2026
**Files involved:**
- `app/src/main/java/com/rajatxo/coral/ui/player/SpiralPlayer.kt` (lines ~232–282, ~690–695, ~762–766)
- `app/src/main/java/com/rajatxo/coral/ui/player/MeshBackground.kt` (entire file)
- `app/src/main/java/com/rajatxo/coral/CoralApplication.kt` (lines ~49–61)

---

## The Goal

When the user presses next/prev, the player's background (blur + color mesh) must transition from the **old song's** look to the **new song's** look in **one smooth, synced step** — no flash of the old blur, no step where the new cover sits on top of the old colors, no delay.

BitChord does this perfectly. Coral now matches it.

---

## The Problem (what we fixed)

When `albumArtUri` changes (next/prev button):

1. **Coil** starts crossfading the image at 100ms (the blur AsyncImage + sharp art AsyncImage)
2. **Palette extraction** takes ~200ms (decode bitmap + run Palette API)
3. For ~200ms: **NEW image + OLD palette colors** = "old song blur" flash

The image changed **before** the palette was ready. They were out of sync.

---

## The Solution — 3 pieces that MUST stay together

### Piece 1: `displayedArtUri` (the sync latch)

**File:** `SpiralPlayer.kt`, around line 247

```kotlin
var palette by remember { mutableStateOf(PaletteCache.get(albumArtUri) ?: CoralPalette.Default) }
var displayedArtUri by remember { mutableStateOf(albumArtUri) }

LaunchedEffect(albumArtUri) {
    if (albumArtUri != null) {
        val cached = PaletteCache.get(albumArtUri)
        if (cached != null) {
            // ★ Palette cached — update both at the same time
            palette = cached
            displayedArtUri = albumArtUri
        } else {
            // ★ Not cached — keep OLD art visible while extracting.
            //   Preload image into Coil cache so it's ready instantly
            //   when we flip the displayedArtUri.
            try {
                coil3.ImageLoader(context).execute(
                    coil3.request.ImageRequest.Builder(context)
                        .data(albumArtUri)
                        .build()
                )
            } catch (_: Exception) { }
            extractPalette(context, albumArtUri)?.let {
                palette = it
                PaletteCache.put(albumArtUri, it)
                // ★ NOW flip the displayed art — image + palette change together
                displayedArtUri = albumArtUri
            }
        }
    }
}
```

**Key insight:** `displayedArtUri` LAGS behind `albumArtUri` until the new palette is also ready. The old blur stays fully visible while the palette is being extracted. Once both are ready, they flip together.

### Piece 2: MeshBackground + sharp art use `displayedArtUri`

**File:** `SpiralPlayer.kt`, around lines 690–695 and 762–766

```kotlin
MeshBackground(
    albumArtUri = displayedArtUri,  // ★ synced with palette — changes at the same time
    palette = animatedPalette,
    style = paletteStyle,
    modifier = Modifier.graphicsLayer { alpha = outAlpha }
)
```

```kotlin
AsyncImage(
    model = displayedArtUri,  // ★ synced with palette
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxSize()
)
```

**Both** the blur and the sharp art use `displayedArtUri`, NOT `albumArtUri`. This is critical — if one uses `albumArtUri` and the other uses `displayedArtUri`, they'll be out of sync again.

### Piece 3: Global Coil crossfade (100ms)

**File:** `CoralApplication.kt`, around line 49

```kotlin
coil3.SingletonImageLoader.setSafe {
    coil3.ImageLoader.Builder(it)
        .memoryCache {
            coil3.memory.MemoryCache.Builder()
                .maxSizeBytes(50L * 1024 * 1024)  // 50MB
                .build()
        }
        .crossfade(100)  // ★ Global 100ms crossfade — ALL AsyncImage instances
        .build()
}
```

This enables a 100ms crossfade on **every** AsyncImage in the app. When `displayedArtUri` changes, Coil keeps the old image visible and crossfades to the new one over 100ms. No per-request crossfade needed — pass URIs directly to AsyncImage.

### Piece 4: Animated palette (100ms, matches image crossfade)

**File:** `SpiralPlayer.kt`, around line 277

```kotlin
val animatedTopColor    by animateColorAsState(palette.primary,   tween(100), label = "top")
val animatedMidColor   by animateColorAsState(palette.secondary,  tween(100), label = "mid")
val animatedBottomColor by animateColorAsState(palette.tertiary,  tween(100), label = "bottom")
val animatedAccentColor by animateColorAsState(palette.accent,    tween(100), label = "accent")

val animatedPalette = CoralPalette(
    primary = animatedTopColor,
    secondary = animatedMidColor,
    tertiary = animatedBottomColor,
    accent = animatedAccentColor
)
```

The mesh overlay colors animate at 100ms — the **same** rate as the image crossfade. Without this, the mesh colors would jump instantly while the image crossfades, causing a "sudden colour change" on the blur.

`animatedPalette` is passed to MeshBackground (NOT the raw `palette`).

---

## How it all fits together (timeline)

```
User presses Next
│
├─ albumArtUri changes (instant)
│
├─ LaunchedEffect(albumArtUri) fires:
│   ├─ Check PaletteCache for new URI
│   │   ├─ CACHED (common — mini player extracts it):
│   │   │   palette = cached          ← both update
│   │   │   displayedArtUri = new     ← at the same time
│   │   │   → Coil crossfades image 100ms + palette animates 100ms
│   │   │   → Done. Total: ~100ms
│   │   │
│   │   └─ NOT cached:
│   │       ├─ Preload image into Coil cache (background)
│   │       ├─ Extract palette (background, ~200ms)
│   │       ├─ displayedArtUri stays at OLD value (old blur visible)
│   │       └─ Once palette ready:
│   │           palette = new
│   │           displayedArtUri = new  ← both flip together
│   │           → Coil crossfades 100ms + palette animates 100ms
│   │           → Done. Total: ~300ms (but old blur was visible the whole time — no flash)
│
└─ Result: ONE smooth transition, no old/new mismatch
```

---

## What NOT to change (will break the sync)

1. **Do NOT pass `albumArtUri` to MeshBackground or the sharp art.** Always use `displayedArtUri`. If you use `albumArtUri`, the image will change before the palette is ready → old blur flash returns.

2. **Do NOT remove the `remember { }` (no key) on `palette` and `displayedArtUri`.** If you key them on `albumArtUri`, the state will be recreated on every song change — `displayedArtUri` will reset to the new URI instantly (defeating the lag), and `palette` will reset to `CoralPalette.Default` (dark grey) while extraction runs.

3. **Do NOT change the crossfade duration away from 100ms.** The image crossfade (Coil global) and the palette animation (`tween(100)`) MUST be the same duration. If they differ, one finishes before the other → visible mismatch.

4. **Do NOT add per-request crossfade via `remember(albumArtUri) { ImageRequest.Builder().crossfade() }`.** This creates a new request object each song change, which can desync from Coil's internal crossfade state. Use the global ImageLoader crossfade + pass URIs directly.

5. **Do NOT use the raw `palette` in MeshBackground.** Always pass `animatedPalette` (built from the `animateColorAsState` values). The raw palette changes instantly; the animated palette transitions at 100ms matching the image.

---

## How to modify (safely)

### To change the crossfade speed:
Change **both** of these to the same value:
- `CoralApplication.kt`: `.crossfade(100)` → `.crossfade(200)` (or whatever)
- `SpiralPlayer.kt`: `tween(100)` on all four `animateColorAsState` calls

They MUST match. If one is 100ms and the other is 200ms, you'll get a mismatch flash.

### To change the palette extraction priority:
The `LaunchedEffect(albumArtUri)` block in SpiralPlayer checks `PaletteCache.get(albumArtUri)` first. If you want to add another source (e.g. embedded ID3 lyrics-style palette), add it inside the `else` branch before `extractPalette`.

### To add a new palette style:
1. Add the enum value in `SpiralPaletteStyle.kt` with `blurRadiusDp`, `meshType`, `saturationBoost`, `brightnessFactor`, `hueShiftDeg`.
2. Add a preview gradient in `SpiralPaletteSettingsScreen.kt`.
3. The `MeshBackground` will automatically use the new style's params — no changes needed there.

---

## Files involved (quick reference)

| File | What it does |
|------|-------------|
| `SpiralPlayer.kt` ~232–282 | `displayedArtUri` latch + palette extraction |
| `SpiralPlayer.kt` ~277 | `animatedPalette` (100ms color animation) |
| `SpiralPlayer.kt` ~690 | MeshBackground call (uses `displayedArtUri` + `animatedPalette`) |
| `SpiralPlayer.kt` ~762 | Sharp art AsyncImage (uses `displayedArtUri`) |
| `MeshBackground.kt` | Single-layer blur + ColorMatrix + mesh overlay |
| `CoralApplication.kt` ~49 | Global Coil crossfade(100ms) |
| `PaletteExtractor.kt` | `extractPalette()` + `PaletteCache` |
| `SpiralPaletteStyle.kt` | 12 styles with blur/mesh/color params |

---

## The "single layer" principle in MeshBackground

MeshBackground renders the blur + color treatment + mesh overlay as **ONE render pass** inside a single `graphicsLayer` (Offscreen compositing). This is critical for the sync:

```kotlin
Box(
    modifier = modifier
        .fillMaxSize()
        .graphicsLayer {
            this.alpha = alpha
            compositingStrategy = CompositingStrategy.Offscreen  // ★ required for BlendMode
        }
) {
    // Layer 1: blurred image with ColorMatrix ColorFilter (hue/sat/brightness baked into pixels)
    AsyncImage(
        model = albumArtUri,  // ← this is displayedArtUri from SpiralPlayer
        colorFilter = colorFilter,  // ← ColorMatrix combining hue + sat + brightness
        modifier = Modifier.fillMaxSize().blur(blurDp)
    )

    // Layer 2: mesh overlay (radial blobs / dual-tone / tritone) — drawn ON TOP
    // but INSIDE the same graphicsLayer, so it composites as one unit
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                drawMeshOverlay(style, palette, size.width, size.height)
            }
    )
}
```

Both children are in the same `graphicsLayer`, so they composite together as a **single texture** — no separate timing, no desync.

---

## Summary

The sync works because:
1. `displayedArtUri` lags until palette is ready → image + palette change together
2. Global Coil crossfade (100ms) → image crossfades smoothly
3. `animateColorAsState(tween(100))` → palette colors animate at the same 100ms
4. MeshBackground renders blur + mesh as one layer → no internal desync

**All four pieces must stay in sync.** Change one, change all.
