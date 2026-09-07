package com.rajatxo.coral.ui.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Rect

/**
 * A pill shape with a smooth concave U-shaped notch cut into the bottom-center.
 *
 * The notch is drawn as a SEMICIRCLE (arc) — not bezier curves — which
 * guarantees a perfectly smooth, anti-aliased curve.
 *
 * The search capsule nests inside this notch.
 *
 * @param notchWidth  Width of the U-notch at the bottom (in dp)
 * @param notchDepth  How deep the notch scoops upward (in dp)
 * @param cornerRadius Corner radius of the pill (in dp)
 */
class NotchedPillShape(
    private val notchWidth: Float = 140f,  // dp
    private val notchDepth: Float = 28f,   // dp
    private val cornerRadius: Float = 32f  // dp
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val r = cornerRadius * density.density
        val notchW = notchWidth * density.density
        val notchD = notchDepth * density.density

        // The notch is centered horizontally
        val notchStart = (w - notchW) / 2f
        val notchEnd = notchStart + notchW

        val path = Path().apply {
            // Start at top-left corner
            moveTo(0f + r, 0f)

            // Top edge (left to right)
            lineTo(w - r, 0f)

            // Top-right corner (270° → 360° = 90° sweep clockwise)
            arcTo(
                rect = Rect(left = w - 2 * r, top = 0f, right = w, bottom = 2 * r),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            // Right edge (top to bottom)
            lineTo(w, h - r)

            // Bottom-right corner (0° → 90° = 90° sweep clockwise)
            arcTo(
                rect = Rect(left = w - 2 * r, top = h - 2 * r, right = w, bottom = h),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            // Bottom edge — right side (from bottom-right corner to notch end)
            lineTo(notchEnd, h)

            // === THE U-NOTCH ===
            // Draw a smooth semicircle that scoops UPWARD into the player body.
            //
            // The arc rect is centered on the bottom edge:
            //   left = notchStart, right = notchEnd
            //   top = h - notchDepth*2, bottom = h (so arc center is at h)
            //
            // startAngle = 0° (right side of arc, at bottom edge)
            // sweepAngle = -180° (counter-clockwise = goes UP and over to the left)
            //
            // This creates a perfectly smooth semicircular scoop:
            //   starts at (notchEnd, h) → curves up to (centerX, h - notchDepth) →
            //   curves back down to (notchStart, h)
            arcTo(
                rect = Rect(
                    left = notchStart,
                    top = h - notchD * 2f,
                    right = notchEnd,
                    bottom = h
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )

            // Bottom edge — left side (from notch start to bottom-left corner)
            lineTo(0f + r, h)

            // Bottom-left corner (90° → 180° = 90° sweep clockwise)
            arcTo(
                rect = Rect(left = 0f, top = h - 2 * r, right = 2 * r, bottom = h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            // Left edge (bottom to top)
            lineTo(0f, 0f + r)

            // Top-left corner (180° → 270° = 90° sweep clockwise)
            arcTo(
                rect = Rect(left = 0f, top = 0f, right = 2 * r, bottom = 2 * r),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            close()
        }

        return Outline.Generic(path)
    }
}
