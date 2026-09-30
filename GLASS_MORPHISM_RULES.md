# Coral — Glass Morphism Crash Log & Rules

> **READ THIS BEFORE WRITING ANY GLASS MORPHISM CODE.**
> Every approach that crashed or failed is documented here. Do NOT repeat them.

---

## What WORKS (✅)

| Approach | Where | Why it works |
|----------|-------|-------------|
| `rememberLayerBackdrop(graphicsLayer) { drawContent() }` | Root level | Creates the backdrop wire. GraphicsLayer is explicitly provided. |
| `.layerBackdrop(glassBackdrop)` on a SEPARATE inner Box | CynthiaHomeScreen line ~109 | Captures content. NOT on same modifier chain as `.background()`. |
| `.drawBackdrop(glassBackdrop, ...)` on a SIBLING of layerBackdrop Box | Nav bar, settings gear | Sibling = outside the layerBackdrop subtree. No recursive capture. |
| `backdrop = glassBackdrop` passed to TabCapsule | Nav bar | TabCapsule uses drawBackdrop internally. Works because nav bar is a sibling. |
| `drawBackdrop` with `vibrancy() + colorControls() + blur()` | Nav bar, settings gear | Standard kyant effects. Works on API 31+. |

## What CRASHES (❌)

| Approach | Why it crashes | DO NOT DO THIS |
|----------|---------------|----------------|
| `.background(Color.Black).layerBackdrop(backdrop)` on SAME modifier chain | Modifier conflict — background and layerBackdrop fight | Always put `.background()` on OUTER Box, `.layerBackdrop()` on INNER Box |
| `.drawBackdrop()` on a CHILD of the layerBackdrop Box | Recursive capture — the glass element is trying to sample content that includes itself | drawBackdrop ONLY on SIBLINGS of layerBackdrop, never children |
| `.drawBackdrop()` deeply nested inside QuickPicksScreen → LazyColumn → SpeedDialSection → HorizontalPager | Same as above — it's a grandchild of layerBackdrop | Never use drawBackdrop inside content that's captured by layerBackdrop |
| Empty screen with `layerBackdrop` (no real content) | Graphics layer is empty/uninitialized → drawBackdrop samples nothing → crash | Always have real content (song lists, album art) before adding glass |
| `rememberLayerBackdrop()` without explicit graphicsLayer parameter | Missing graphics layer → crash | Always use `rememberGraphicsLayer()` + `rememberLayerBackdrop(graphicsLayer) { drawContent() }` |
| Haze 1.7.2 | Requires AGP 8.9.1 + compileSdk 36 (we have AGP 8.7.3 + SDK 35) | Use Haze 1.6.9 if using Haze at all |
| Haze 1.6.9 deeply nested | hazeEffect doesn't work 3+ levels deep inside hazeSource | Haze needs direct child/sibling, not deep grandchild |
| Settings overlay INSIDE layerBackdrop Box | Recursive capture when overlay appears | Overlays must be SIBLINGS of layerBackdrop Box |

## The GOLDEN RULE

```
Outer Box (background)
├── Inner Box (.layerBackdrop captures content)
│   └── Page content ONLY (QuickPicksScreen, SongsScreen, etc.)
│       NO drawBackdrop here — it's a child, will crash
│
├── Glass element 1 (.drawBackdrop — SIBLING) ✅
├── Glass element 2 (.drawBackdrop — SIBLING) ✅
├── Nav bar (.drawBackdrop via TabCapsule — SIBLING) ✅
└── Overlays (settings, player — SIBLINGS) ✅
```

**drawBackdrop = SIBLING of layerBackdrop. NEVER a child.**

---

## How to add glass to the speed dial grid (CORRECT approach)

The speed dial grid is INSIDE QuickPicksScreen (a child of layerBackdrop).
We CANNOT use drawBackdrop on it directly.

**Approach: Overlay sibling**
1. Create a glass Box as a SIBLING of the layerBackdrop Box
2. Position it over the speed dial grid area (using onGloballyPositioned to track the grid's position)
3. The overlay uses drawBackdrop(glassBackdrop) to sample the content behind it
4. The overlay is transparent to touch (clicks pass through to the grid below)

This is how ArchiveTune does their header blur — `ScreenHeaderHaze` is placed as a sibling overlay, not inside the content.

---

## kyant backdrop library — key facts

- `rememberLayerBackdrop(graphicsLayer) { drawContent() }` — creates the wire
- `.layerBackdrop(backdrop)` — PRODUCER: captures children into the graphics layer
- `.drawBackdrop(backdrop, shape, effects, onDrawSurface)` — CONSUMER: samples + blurs
- `effects { vibrancy(); colorControls(b, c, s); blur(radius) }` — AGSL real-time blur (API 31+)
- `exportedBackdrop` — lets a glass surface become a producer for another glass (glass-on-glass)
- Consumer MUST have a fixed size (fillMaxSize, size, or sized children)
- Consumer MUST be a sibling of the producer, not a child
