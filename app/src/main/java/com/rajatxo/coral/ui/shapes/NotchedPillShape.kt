package com.rajatxo.coral.ui.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius

/**
 * A pill shape with a concave U-shaped notch cut into the bottom-center.
 *
 * The search capsule nests inside this notch — partially recessed into
 * the player body, partially hanging below.
 *
 * @param notchWidth  Width of the U-notch at the bottom (in dp)
 * @param notchDepth   How deep the notch scoops upward (in dp)
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
            // Start at top-left, after the corner
            moveTo(0f + r, 0f)

            // Top edge (left to right)
            lineTo(w - r, 0f)

            // Top-right corner
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = w - 2 * r, top = 0f,
                    right = w, bottom = 2 * r
                ),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            // Right edge (top to bottom)
            lineTo(w, h - r)

            // Bottom-right corner
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = w - 2 * r, top = h - 2 * r,
                    right = w, bottom = h
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            // Bottom edge — right side (from bottom-right corner to notch end)
            lineTo(notchEnd, h)

            // The U-shaped concave notch (scooping UP into the player)
            // Using cubicTo for a smooth U-curve
            cubicTo(
                x1 = notchEnd - (notchW * 0.15f), y1 = h,           // start tangent (going left + flat)
                x2 = notchEnd - (notchW * 0.35f), y2 = h - notchD,  // first control point (rising up)
                x3 = w / 2f + (notchW * 0.2f), y3 = h - notchD      // midpoint of the U (top of the scoop)
            )
            cubicTo(
                x1 = w / 2f - (notchW * 0.2f), y1 = h - notchD,    // continue from midpoint
                x2 = notchStart + (notchW * 0.35f), y2 = h - notchD, // falling back down
                x3 = notchStart, y3 = h                             // end of the notch (back to bottom edge)
            )

            // Bottom edge — left side (from notch start to bottom-left corner)
            lineTo(0f + r, h)

            // Bottom-left corner
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = 0f, top = h - 2 * r,
                    right = 2 * r, bottom = h
                ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            // Left edge (bottom to top)
            lineTo(0f, 0f + r)

            // Top-left corner
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = 0f, top = 0f,
                    right = 2 * r, bottom = 2 * r
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            close()
        }

        return Outline.Generic(path)
    }
}
