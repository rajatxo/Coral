package com.rajatxo.coral.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.data.prefs.SpiralPaletteStyle
import com.rajatxo.coral.util.CoralPalette

/**
 * MeshBackground — renders the Spiral player's background based on the
 * selected palette style. Combines a blurred (or sharp) album cover
 * with a color mesh overlay (radial blobs, gradient, wash, etc.).
 *
 * The album cover is always rendered first (as the base layer). Then,
 * depending on the style's meshType, a color overlay is drawn on top
 * using the palette colors extracted from the same album art.
 *
 * For MONOCHROME style, the album cover itself is desaturated via a
 * ColorFilter (grayscale ColorMatrix), and a single accent radial blob
 * is drawn on top.
 */
@Composable
fun MeshBackground(
    albumArtUri: android.net.Uri?,
    palette: CoralPalette,
    style: SpiralPaletteStyle.PaletteStyle,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                // Offscreen compositing for BlendMode to work correctly
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        // ─── Layer 1: Blurred (or sharp) album cover ────────────
        // The blur radius comes from the style. 0dp = sharp (no blur modifier).
        // For MONOCHROME, a grayscale ColorFilter is applied.
        val blurDp = style.blurRadiusDp.dp
        val colorFilter = if (style == SpiralPaletteStyle.PaletteStyle.MONOCHROME) {
            // Grayscale ColorMatrix: luminance-based desaturation
            // R*0.299 + G*0.587 + B*0.114 → all channels equal → grayscale
            ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0.299f, 0.587f, 0.114f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        } else {
            null
        }

        if (albumArtUri != null) {
            // ★ Crossfade: Coil keeps the OLD image visible until the new one
            //   is fully loaded, then crossfades. Without this, the old image
            //   clears instantly when albumArtUri changes → transparent gap →
            //   home page visible behind. Crossfade = seamless song change.
            val context = androidx.compose.ui.platform.LocalContext.current
            val request = remember(albumArtUri) {
                ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .crossfade(300)  // 300ms crossfade — old → new
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (blurDp > 0.dp) Modifier.blur(blurDp) else Modifier
                    )
            )
        }

        // ─── Layer 2: Color mesh overlay ────────────────────────
        // Drawn on top of the album cover. Each meshType has its own
        // rendering strategy. The overlay uses the palette colors that
        // were extracted + transformed per the style's settings.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawMeshOverlay(style, palette, size.width, size.height)
                }
        )
    }
}

/**
 * Draws the color mesh overlay based on the style's meshType.
 * Called inside drawBehind — has access to the DrawScope.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeshOverlay(
    style: SpiralPaletteStyle.PaletteStyle,
    palette: CoralPalette,
    width: Float,
    height: Float
) {
    when (style.meshType) {
        SpiralPaletteStyle.MeshType.NONE -> {
            // No overlay — just the blurred image
        }

        SpiralPaletteStyle.MeshType.RADIAL_BLOBS -> {
            // 4 large radial color blobs at corners + center
            // Each blob is a radial gradient from a palette color → transparent
            val blobRadius = width * 0.6f

            // Top-left blob (primary)
            drawRadialGradient(
                color = palette.primary,
                center = Offset(width * 0.15f, height * 0.15f),
                radius = blobRadius
            )
            // Top-right blob (secondary)
            drawRadialGradient(
                color = palette.secondary,
                center = Offset(width * 0.85f, height * 0.2f),
                radius = blobRadius
            )
            // Bottom-left blob (tertiary)
            drawRadialGradient(
                color = palette.tertiary,
                center = Offset(width * 0.2f, height * 0.85f),
                radius = blobRadius
            )
            // Bottom-right blob (accent)
            drawRadialGradient(
                color = palette.accent,
                center = Offset(width * 0.8f, height * 0.8f),
                radius = blobRadius
            )
        }

        SpiralPaletteStyle.MeshType.SOLID_WASH -> {
            // Solid color overlay at 50% alpha
            drawRect(color = palette.primary.copy(alpha = 0.5f))
            // Subtle gradient from top (primary) to bottom (tertiary) for depth
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = 0.3f),
                        Color.Transparent,
                        palette.tertiary.copy(alpha = 0.4f)
                    )
                )
            )
        }

        SpiralPaletteStyle.MeshType.VERTICAL_GRADIENT -> {
            // Top→bottom gradient: primary → secondary → tertiary → near-black
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to palette.primary.copy(alpha = 0.85f),
                        0.35f to palette.secondary.copy(alpha = 0.7f),
                        0.65f to palette.tertiary.copy(alpha = 0.8f),
                        1.0f to Color(0xFF05050A)
                    )
                )
            )
        }

        SpiralPaletteStyle.MeshType.DUAL_TONE -> {
            // Diagonal split: primary (top-left) → tertiary (bottom-right)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.primary.copy(alpha = 0.7f),
                        palette.tertiary.copy(alpha = 0.7f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                )
            )
        }

        SpiralPaletteStyle.MeshType.TRITONE -> {
            // Three horizontal bands: primary (top) → secondary (middle) → tertiary (bottom)
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to palette.primary.copy(alpha = 0.75f),
                        0.33f to palette.primary.copy(alpha = 0.4f),
                        0.5f to palette.secondary.copy(alpha = 0.6f),
                        0.66f to palette.tertiary.copy(alpha = 0.4f),
                        1.0f to palette.tertiary.copy(alpha = 0.8f)
                    )
                )
            )
        }

        SpiralPaletteStyle.MeshType.GRAYSCALE_ACCENT -> {
            // The album cover is already desaturated via ColorFilter.
            // Draw a single accent radial blob at center for a pop of color.
            drawRadialGradient(
                color = palette.accent.copy(alpha = 0.4f),
                center = Offset(width * 0.5f, height * 0.4f),
                radius = width * 0.5f
            )
            // Dark vignette at edges for depth
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF05050A).copy(alpha = 0.6f)
                    ),
                    center = Offset(width * 0.5f, height * 0.5f),
                    radius = width * 0.8f
                )
            )
        }
    }

    // Always add a subtle dark gradient at the very bottom for control readability
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.7f to Color.Transparent,
                1.0f to Color(0xFF05050A).copy(alpha = 0.3f)
            )
        )
    )
}

/**
 * Helper: draw a radial gradient blob from a color → transparent.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRadialGradient(
    color: Color,
    center: Offset,
    radius: Float
) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.6f),
                color.copy(alpha = 0.0f)
            ),
            center = center,
            radius = radius
        )
    )
}
