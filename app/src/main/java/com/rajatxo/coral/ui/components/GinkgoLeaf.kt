package com.rajatxo.coral.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ginkgo leaf — hand-drawn Canvas composable.
 *
 * Why Canvas instead of ImageVector:
 *   - ImageVector paths only support SolidColor fills — no gradients.
 *   - We want a radial gradient body (yellow center → amber edge) for
 *     that "alive" autumn look, not a flat fill.
 *   - We want visible veins with subtle alpha + a separate stem color.
 *   - Each leaf can be a different color (autumn palette: yellow, amber,
 *     deep gold, red-amber).
 *
 * Anatomy (24×24 viewport, scaled to whatever size you pass):
 *   1. BODY silhouette — fan shape with a distinctive notch cut into
 *      the top edge. Hand-drawn with 4 cubic Beziers + 2 quads for the
 *      corners + 2 cubic Beziers for the notch dip.
 *   2. RADIAL GRADIENT fill — 3 stops: bright yellow at the bottom
 *      center (where the stem attaches), amber in the middle, deep
 *      amber at the outer edges. Centered at (12, 14), radius ~12.
 *   3. VEINS — 5 radial lines fanning from the stem-attachment point
 *      (12, 20) up to the top edge at angles -60°, -30°, 0°, 30°, 60°.
 *      Drawn with low alpha (0.45) so they're visible but not loud.
 *   4. STEM — short straight line from (12, 20) to (12, 22). Dark brown.
 *   5. SUBTLE HIGHLIGHT — small lighter ellipse near top-left of the
 *      body for a 3D volumetric feel (sun hitting the upper edge).
 *
 * The leaf can be tinted via [bodyColor] / [edgeColor] / [veinColor] /
 * [stemColor] so multiple leaves in a particle system don't all look
 * identical. Pass different colors for the autumn palette variety.
 */
@Composable
fun GinkgoLeaf(
    modifier: Modifier = Modifier,
    bodyColor: Color = Color(0xFFF4C724),     // golden yellow (center)
    edgeColor: Color = Color(0xFFC8851B),     // deep amber (outer)
    veinColor: Color = Color(0xFF8B5A1C),     // brown
    stemColor: Color = Color(0xFF6B4423),      // darker brown
    rotationDegrees: Float = 0f,
    alpha: Float = 1f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Scale our 24×24 design to whatever size the canvas is.
        val s = minOf(w, h) / 24f
        val cx = w / 2f
        val cy = h / 2f

        // Translate so the leaf is centered (design is centered at 12,12).
        rotate(degrees = rotationDegrees, pivot = Offset(cx, cy)) {
            // ── (1) BODY silhouette ────────────────────────────────────────
            // Hand-drawn fan shape with the distinctive center notch.
            val body = Path().apply {
                // Bottom point (stem attachment)
                moveTo(12f * s + (cx - 12 * s), 20f * s + (cy - 12 * s))

                // LEFT SIDE — fan curve going up from bottom to top-left
                cubicTo(
                    6f * s + (cx - 12 * s), 18f * s + (cy - 12 * s),
                    3f * s + (cx - 12 * s), 13f * s + (cy - 12 * s),
                    5f * s + (cx - 12 * s), 6f * s + (cy - 12 * s)
                )

                // TOP-LEFT CORNER — small rounded curve
                quadraticTo(
                    5f * s + (cx - 12 * s), 4f * s + (cy - 12 * s),
                    7f * s + (cx - 12 * s), 4f * s + (cy - 12 * s)
                )

                // TOP EDGE (left half) — to just before the notch
                lineTo(11f * s + (cx - 12 * s), 4f * s + (cy - 12 * s))

                // NOTCH (left side going down) — distinctive ginkgo split
                cubicTo(
                    11.5f * s + (cx - 12 * s), 5.5f * s + (cy - 12 * s),
                    11.7f * s + (cx - 12 * s), 6.5f * s + (cy - 12 * s),
                    12f * s + (cx - 12 * s), 7f * s + (cy - 12 * s)
                )

                // NOTCH (right side coming back up)
                cubicTo(
                    12.3f * s + (cx - 12 * s), 6.5f * s + (cy - 12 * s),
                    12.5f * s + (cx - 12 * s), 5.5f * s + (cy - 12 * s),
                    13f * s + (cx - 12 * s), 4f * s + (cy - 12 * s)
                )

                // TOP EDGE (right half) — past the notch to top-right
                lineTo(17f * s + (cx - 12 * s), 4f * s + (cy - 12 * s))

                // TOP-RIGHT CORNER — small rounded curve
                quadraticTo(
                    19f * s + (cx - 12 * s), 4f * s + (cy - 12 * s),
                    19f * s + (cx - 12 * s), 6f * s + (cy - 12 * s)
                )

                // RIGHT SIDE — fan curve going down to bottom
                cubicTo(
                    21f * s + (cx - 12 * s), 13f * s + (cy - 12 * s),
                    18f * s + (cx - 12 * s), 18f * s + (cy - 12 * s),
                    12f * s + (cx - 12 * s), 20f * s + (cy - 12 * s)
                )

                close()
            }

            // ── (2) RADIAL GRADIENT fill ───────────────────────────────────
            // Centered at the lower-middle of the leaf (where the stem
            // attaches). Yellow at the center fades to deep amber at the
            // edges. Gives the leaf a 3D, sun-lit feel.
            val bodyBrush = Brush.radialGradient(
                colors = listOf(bodyColor, edgeColor, edgeColor.copy(alpha = 0.85f)),
                center = Offset(12f * s + (cx - 12 * s), 14f * s + (cy - 12 * s)),
                radius = 14f * s
            )
            drawPath(
                path = body,
                brush = bodyBrush,
                alpha = alpha
            )

            // ── (3) SUBTLE HIGHLIGHT ───────────────────────────────────────
            // A small lighter ellipse near the top-left of the body —
            // simulates sunlight hitting the upper-left of the leaf.
            drawOval(
                color = Color.White.copy(alpha = 0.18f * alpha),
                topLeft = Offset(
                    8f * s + (cx - 12 * s),
                    7f * s + (cy - 12 * s)
                ),
                size = Size(4f * s, 2.5f * s)
            )

            // ── (4) VEINS ──────────────────────────────────────────────────
            // 5 radial lines from (12, 20) [stem attachment] to the top
            // edge at angles -60°, -30°, 0°, 30°, 60°.
            // The center vein stops at the notch bottom (12, 7) so it
            // doesn't poke through the notch.
            val veinStroke = Stroke(
                width = 0.6f * s,
                cap = StrokeCap.Round
            )
            val stemX = 12f * s + (cx - 12 * s)
            val stemY = 20f * s + (cy - 12 * s)

            // Vein 1: -60° (leftmost)
            drawLine(
                color = veinColor.copy(alpha = 0.45f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(6f * s + (cx - 12 * s), 6f * s + (cy - 12 * s)),
                strokeWidth = 0.6f * s,
                cap = StrokeCap.Round
            )

            // Vein 2: -30°
            drawLine(
                color = veinColor.copy(alpha = 0.45f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(8.5f * s + (cx - 12 * s), 4.5f * s + (cy - 12 * s)),
                strokeWidth = 0.55f * s,
                cap = StrokeCap.Round
            )

            // Vein 3: 0° (center — stops at notch bottom)
            drawLine(
                color = veinColor.copy(alpha = 0.50f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(12f * s + (cx - 12 * s), 7.2f * s + (cy - 12 * s)),
                strokeWidth = 0.6f * s,
                cap = StrokeCap.Round
            )

            // Vein 4: +30°
            drawLine(
                color = veinColor.copy(alpha = 0.45f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(15.5f * s + (cx - 12 * s), 4.5f * s + (cy - 12 * s)),
                strokeWidth = 0.55f * s,
                cap = StrokeCap.Round
            )

            // Vein 5: +60° (rightmost)
            drawLine(
                color = veinColor.copy(alpha = 0.45f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(18f * s + (cx - 12 * s), 6f * s + (cy - 12 * s)),
                strokeWidth = 0.6f * s,
                cap = StrokeCap.Round
            )

            // ── (5) STEM ───────────────────────────────────────────────────
            // Short straight line from the bottom of the leaf downward.
            drawLine(
                color = stemColor.copy(alpha = 0.9f * alpha),
                start = Offset(stemX, stemY),
                end = Offset(12f * s + (cx - 12 * s), 23f * s + (cy - 12 * s)),
                strokeWidth = 1.2f * s,
                cap = StrokeCap.Round
            )
        }
    }
}
