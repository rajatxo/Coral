# Coral — Quick Picks Header Blur Blueprint

> **The "Speed dial text leaking through behind the status bar" fix.**
> Share this entire file with any future AI assistant (or paste it back to me) and they'll immediately understand what's there, why, and how to modify it.

**Working commit:** `6691232` (build #594) — September 2026
**File:** `app/src/main/java/com/rajatxo/coral/ui/home/HomeScreen.kt`
**Location:** Inside `HomeScreen` composable, in the top-level `Box` overlay. Search for the comment `// ─── FIXED HEADER (Quick Picks page only) ───────────────────`

---

## The Goal

A frosted-glass blur behind the Quick Picks header (user icon + "Quick picks" title + settings gear), 120dp tall, anchored to the top of the screen. The blur must:

1. Cover the status bar area (top ~24dp behind the time/battery/signal icons)
2. Be **uniform intensity** — the top (y=0) must match the mid (y=72) blur strength
3. Fade smoothly at the bottom (y=72 → y=120) into the content below — no hard edge
4. Not blur the header content (icons + title) — only what's behind it

---

## The Working Code (build #594)

```kotlin
// ─── FIXED HEADER (Quick Picks page only) ───────────────────
if (selectedTab == CoralTab.QuickPicks && !showSearch) {
    val useRenderEffect =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(120.dp)
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                clip = true
                if (useRenderEffect) {
                    renderEffect = BlurEffect(
                        radiusX = 20.dp.toPx(),
                        radiusY = 20.dp.toPx()
                    )
                }
            }
            .drawWithContent {
                if (useRenderEffect) {
                    drawLayer(graphicsLayer)
                } else {
                    drawContent()
                }

                // DstIn gradient mask — fades the bottom 40% to transparent
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black,
                            0.6f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
            }
    ) {
        // Inner Box with drawBackdrop — only rendered in the fallback path (API < 31)
        if (!useRenderEffect) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = glassBackdrop,
                        shape = { androidx.compose.ui.graphics.RectangleShape },
                        effects = {
                            vibrancy()
                            blur(20f.dp.toPx())
                        }
                    )
            )
        }
    }

    // The header content — ON TOP of the blur, NOT blurred.
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User icon (left) ... "Quick picks" title (center) ... settings icon (right)
    }
}
```

### Required imports (at the top of HomeScreen.kt)

```kotlin
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
```

### Required setup (already exists elsewhere in HomeScreen.kt — don't touch)

The `glassBackdrop` and `graphicsLayer` are defined ONCE at the top of the HomeScreen composable, around line 261:

```kotlin
val graphicsLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
val glassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(
    graphicsLayer = graphicsLayer
) {
    drawContent()
}
```

And the main content (the `PullToRefreshBox` containing all the tab screens) is wrapped with `layerBackdrop(glassBackdrop)`:

```kotlin
Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
    // ... all the tab screens (QuickPicks, Songs, etc.) ...
}
```

This `layerBackdrop` modifier is what captures the screen content into the `graphicsLayer` object — that's what `drawLayer(graphicsLayer)` draws in the blur block above.

---

## How It Works (the science)

### The fundamental problem

A blur kernel samples a region around each pixel. For a 20dp blur at y=0 (top of screen), the kernel samples y=-20 to y=+20. The y<0 region is **above the screen** — there's no app content there.

The `graphicsLayer` object (populated by `layerBackdrop(glassBackdrop)` on the main content) is screen-sized, starting at y=0. So sampling y<0 returns **transparent**, which weakens the blur to ~50% strength at the very top.

This is why the user could see "Speed dial" text leaking through behind the status bar — the blur there was too weak to hide it.

### The solution: BlurEffect with CLAMP edge treatment

`BlurEffect(radiusX, radiusY)` (Compose's `androidx.compose.ui.graphics.BlurEffect`) defaults to `BlurredEdgeTreatment.Rectangle`, which maps to `Shader.TileMode.CLAMP` at the Android RenderEffect level.

**CLAMP** makes the blur use the **edge pixel** for out-of-bounds samples. So the blur at y=0 samples:
- y<0 → uses the pixel at y=0 (the edge pixel)
- y≥0 → uses the real content

This gives **full-strength blur** at y=0, matching the blur at y=72 (mid). Uniform intensity across the entire 120dp header.

### Why the renderEffect is on the MODIFIER, not the graphicsLayer object

This was the critical bug in build #592. The correct pattern:

```kotlin
// ✅ CORRECT — renderEffect on the MODIFIER's graphicsLayer block
Modifier.graphicsLayer {
    renderEffect = BlurEffect(...)
}
.drawWithContent {
    drawLayer(graphicsLayer)  // draws the captured screen content
}
```

```kotlin
// ❌ WRONG — renderEffect on the graphicsLayer OBJECT
.drawWithContent {
    graphicsLayer.renderEffect = BlurEffect(...)  // does NOTHING
    drawLayer(graphicsLayer)  // draws without blur, or draws nothing
}
```

The `renderEffect` on the modifier blurs the Box's **composited output** — everything drawn inside the Box (including the `drawLayer` output). The `renderEffect` on the object is NOT applied by `drawLayer()`.

### Why DstIn + CompositingStrategy.Offscreen

The bottom 40% of the blur needs to fade smoothly to transparent (so it blends into the content below without a hard line). This is done with a `DstIn` blend mode gradient:

- `CompositingStrategy.Offscreen` is required for `BlendMode.DstIn` to work correctly. Without it, the blend mode behaves unpredictably because the compositor can't isolate the layer.
- `drawRect` with a `verticalGradient` (Black at 0-60%, Transparent at 100%) + `BlendMode.DstIn` keeps the top 60% fully opaque and fades the bottom 40% to transparent.

### Why clip = true

`drawLayer(graphicsLayer)` draws the **entire** captured screen-sized graphicsLayer. Without `clip = true`, it would draw the whole screen's worth of content (just blurred). `clip = true` on the modifier's graphicsLayer block clips the drawing to the Box's 120dp bounds, so only the top 120dp is visible.

---

## Failed Approaches (don't try these again)

### ❌ Build #590: `translationY = -40.dp.toPx()` to extend the blur layer above the screen

The idea: shift the blur layer UP by 40dp so 40dp of it sits off-screen above y=0. Then the blur at y=0 would sample real content from y=+40 down.

**Why it failed:** The `graphicsLayer` object (the captured content) is still screen-sized. Shifting the blur LAYER up just made it sample empty space above y=0. The top was still weak.

### ❌ Build #592: `graphicsLayer.renderEffect = BlurEffect(...)` on the object

```kotlin
// BROKEN — does not apply the renderEffect
graphicsLayer.renderEffect = BlurEffect(20f, 20f)
drawLayer(graphicsLayer)
```

**Why it failed:** Setting `renderEffect` on the `GraphicsLayer` object does NOT cause `drawLayer()` to apply that blur. The API doesn't work that way. Result: no blur rendered at all. User saw a completely unblurred header.

### ❌ Using `drawBackdrop` alone (the original build #571)

The kyant/backdrop library's `drawBackdrop` with `blur(20f.dp.toPx())` works, but it has the edge-sampling weakness at y=0. The blur there is ~50% strength because of the transparent out-of-bounds samples.

This is kept as the **fallback** for API < 31 (Android < 12) where `RenderEffect` isn't available. On modern devices, the RenderEffect path is used.

---

## Tuning Knobs

If the user wants to adjust the blur, here are the parameters to change:

### Blur strength (currently 20dp)
```kotlin
renderEffect = BlurEffect(
    radiusX = 20.dp.toPx(),  // ← change this (higher = more blurry)
    radiusY = 20.dp.toPx()   // ← and this (keep equal to radiusX)
)
```
Also change the fallback path's blur to match:
```kotlin
blur(20f.dp.toPx())  // in drawBackdrop effects block
```

### Blur layer height (currently 120dp)
```kotlin
.height(120.dp)  // ← change this
```
This is the visible height of the blur. The header Row is 56dp + status bar (~24dp) = 80dp. The remaining 40dp (120 - 80) is the fade-out zone. If you make it taller, the fade zone gets longer.

### Bottom fade position (currently 60% → 100%)
```kotlin
colorStops = arrayOf(
    0.0f to Color.Black,
    0.6f to Color.Black,        // ← opaque until 60% (72dp)
    1.0f to Color.Transparent   // ← fully transparent at 100% (120dp)
)
```
- `0.6f` = where the fade STARTS (60% of 120dp = 72dp from top)
- `1.0f` = where the fade ENDS (100% = 120dp, the bottom of the layer)

To make the fade start higher (shorter full-opacity zone), change `0.6f` to e.g. `0.5f`. To make the fade start lower (longer full-opacity zone), change to e.g. `0.7f`.

### Header content position (currently statusBarsPadding + 56dp)
```kotlin
Row(
    modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .statusBarsPadding()        // ← pushes below status bar
        .height(56.dp)              // ← header row height
        .padding(horizontal = 16.dp),
    // ...
)
```

---

## Common Issues & Solutions

### Issue: Blur is too weak at the top again
**Cause:** The RenderEffect path isn't being used (API < 31), OR the `renderEffect` is on the graphicsLayer object instead of the modifier.

**Check:** Look at the `graphicsLayer {}` block on the modifier. The `renderEffect = BlurEffect(...)` must be INSIDE that block, not on a separate `graphicsLayer.renderEffect = ...` line.

### Issue: No blur at all
**Cause:** `drawLayer(graphicsLayer)` isn't drawing anything. This happens if:
1. The `graphicsLayer` object isn't being populated (check that the main content is wrapped with `layerBackdrop(glassBackdrop)`)
2. The `renderEffect` is on the object instead of the modifier (see #592's bug)

**Check:** Temporarily add a `drawRect(color = Color.Red)` before `drawLayer` — if you see red but no blur, the issue is with `drawLayer`. If you see neither, the issue is with the Box itself.

### Issue: Hard line at the bottom of the blur
**Cause:** The DstIn gradient mask isn't being applied, OR `CompositingStrategy.Offscreen` is missing.

**Check:** The `graphicsLayer {}` block on the modifier must have `compositingStrategy = CompositingStrategy.Offscreen`. Without it, `BlendMode.DstIn` doesn't work correctly.

### Issue: Header content is also blurred
**Cause:** The header Row is inside the blur Box, or the blur Box is on top of the Row in z-order.

**Check:** The header Row must be a SIBLING of the blur Box (both inside the same parent), and it must come AFTER the blur Box in the code so it draws on top. The blur Box and the Row are both aligned to `Alignment.TopCenter`.

---

## The Build History (so future you/AI knows what each build did)

| Build | Approach | Result |
|---|---|---|
| #571 | `drawBackdrop` with 20dp blur + DstIn mask (120dp layer) | Blur working, top ~50% weak |
| #577 | + `translationY = -40dp` to extend layer above screen | Top still weak (graphicsLayer still screen-sized) |
| #578 | Direct `drawBackdrop` without DstIn, separate gradient overlay | Hard line at bottom, top still weak |
| #580 | 13 color stops for smoother fade | Smooth fade, top still weak |
| #590 | `translationY = -40dp` + 160dp layer + recomputed gradient | Top still weak (same root cause) |
| #592 | `graphicsLayer.renderEffect` on the OBJECT + `drawLayer()` | **NO BLUR** — wrong API usage |
| #593 | Reverted to #571's exact code | Blur back, top still weak |
| **#594** ✅ | `Modifier.graphicsLayer { renderEffect = BlurEffect(...) }` + `drawLayer()` + `clip = true` | **UNIFORM BLUR** — top matches mid |

---

## Quick Recovery Instructions

If a future AI session messes up the blur, do this:

1. `git clone https://github.com/rajatxo/Coral.git`
2. `git checkout 6691232` (the working commit)
3. Copy the blur block from `app/src/main/java/com/rajatxo/coral/ui/home/HomeScreen.kt` (search for `FIXED HEADER (Quick Picks page only)`)
4. Paste it into the current version of that file, replacing whatever's there
5. Make sure these 3 imports are present:
   ```kotlin
   import androidx.compose.ui.graphics.BlurEffect
   import androidx.compose.ui.graphics.graphicsLayer
   import androidx.compose.ui.graphics.layer.drawLayer
   ```
6. Commit and push — GitHub Actions will build the APK

The 3 critical pieces that MUST be present for this to work:
1. `Modifier.graphicsLayer { renderEffect = BlurEffect(...) }` — on the MODIFIER, not the object
2. `drawLayer(graphicsLayer)` — inside `drawWithContent`, draws the captured screen content
3. `clip = true` — on the modifier's graphicsLayer block, so only the 120dp is visible

If any one of these is missing or in the wrong place, the blur will break.
