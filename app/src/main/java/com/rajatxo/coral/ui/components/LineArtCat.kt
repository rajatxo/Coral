package com.rajatxo.coral.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * ★ LineArtCat — a cat drawn using horizontal line art technique.
 *
 * The cat's silhouette is defined as a Path, then filled with evenly-spaced
 * horizontal lines (scanline effect) — the same style as the reference image.
 *
 * Expressions change the EYE shape:
 *   HAPPY   — narrow rectangles (content, listening to music)
 *   SLEEPY  — half-height rectangles (paused, resting)
 *   NEUTRAL — standard rectangles
 *
 * The cat sits facing forward with:
 *   - Two pointy triangular ears
 *   - Rectangular eyes
 *   - Small triangular nose
 *   - Body wider at shoulders, narrowing at base
 *   - Curved tail on the right
 */
@Composable
fun LineArtCat(
    expression: CatExpression = CatExpression.NEUTRAL,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White,
    lineSpacing: Float = 3f,
    strokeWidth: Float = 2f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // ★ Cat silhouette path — sitting cat facing forward
        val catPath = Path().apply {
            // Start at bottom-left of body
            moveTo(w * 0.25f, h * 0.95f)
            // Left side of body going up
            lineTo(w * 0.22f, h * 0.70f)
            lineTo(w * 0.20f, h * 0.55f)
            // Left shoulder
            lineTo(w * 0.18f, h * 0.45f)
            // Left side of head
            lineTo(w * 0.22f, h * 0.35f)
            lineTo(w * 0.25f, h * 0.28f)
            // Left ear (triangle pointing up)
            lineTo(w * 0.22f, h * 0.12f)
            lineTo(w * 0.28f, h * 0.22f)
            // Top of head between ears
            lineTo(w * 0.38f, h * 0.20f)
            // Right ear (triangle pointing up)
            lineTo(w * 0.45f, h * 0.10f)
            lineTo(w * 0.50f, h * 0.22f)
            // Right side of head
            lineTo(w * 0.55f, h * 0.30f)
            lineTo(w * 0.58f, h * 0.40f)
            // Right shoulder
            lineTo(w * 0.60f, h * 0.50f)
            // Right side of body going down
            lineTo(w * 0.62f, h * 0.65f)
            lineTo(w * 0.60f, h * 0.80f)
            // Tail starts here — curves out and up
            cubicTo(
                w * 0.72f, h * 0.82f,
                w * 0.82f, h * 0.65f,
                w * 0.78f, h * 0.45f
            )
            cubicTo(
                w * 0.76f, h * 0.35f,
                w * 0.70f, h * 0.38f,
                w * 0.68f, h * 0.50f
            )
            // Back down the inner tail
            cubicTo(
                w * 0.65f, h * 0.65f,
                w * 0.58f, h * 0.78f,
                w * 0.55f, h * 0.90f
            )
            // Bottom of body
            lineTo(w * 0.25f, h * 0.95f)
            close()
        }

        // ★ Fill the cat with horizontal lines (scanline effect)
        // Get the bounding rect of the path
        val bounds = catPath.getBounds()
        val startY = bounds.top
        val endY = bounds.bottom

        var y = startY
        while (y <= endY) {
            // For each Y, find the left and right intersections with the path
            // We draw a horizontal line across the cat at this Y
            // Simple approach: draw a clip path, then fill horizontal lines
            y += lineSpacing
        }

        // ★ Better approach: use clipPath to clip to the cat silhouette,
        // then draw horizontal lines across the full width
        clipPath(catPath) {
            val lineCount = ((endY - startY) / lineSpacing).toInt()
            for (i in 0..lineCount) {
                val lineY = startY + i * lineSpacing
                drawLine(
                    color = lineColor,
                    start = Offset(bounds.left - 5f, lineY),
                    end = Offset(bounds.right + 5f, lineY),
                    strokeWidth = strokeWidth
                )
            }
        }

        // ★ Draw eyes (on top of the scanlines)
        val eyeY = h * 0.38f
        val leftEyeX = w * 0.30f
        val rightEyeX = w * 0.44f
        val eyeWidth = w * 0.06f

        val (eyeHeight, eyeStyle) = when (expression) {
            CatExpression.HAPPY -> {
                // Narrow rectangles — content
                Pair(h * 0.02f, true)
            }
            CatExpression.SLEEPY -> {
                // Half-height — sleepy
                Pair(h * 0.015f, true)
            }
            CatExpression.NEUTRAL -> {
                // Standard rectangles
                Pair(h * 0.04f, true)
            }
            CatExpression.CURIOUS -> {
                // Slightly taller — alert
                Pair(h * 0.05f, true)
            }
        }

        // Eyes are drawn as solid filled rectangles (gaps in the scanlines)
        // Clear the area first, then draw solid
        if (eyeStyle) {
            // Left eye
            drawRect(
                color = lineColor,
                topLeft = Offset(leftEyeX, eyeY),
                size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight)
            )
            // Right eye
            drawRect(
                color = lineColor,
                topLeft = Offset(rightEyeX, eyeY),
                size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight)
            )
        }

        // ★ Nose — small triangle
        val noseY = h * 0.48f
        val noseX = w * 0.37f
        val noseSize = w * 0.03f
        drawLine(
            color = lineColor,
            start = Offset(noseX - noseSize / 2, noseY),
            end = Offset(noseX + noseSize / 2, noseY + noseSize / 2),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = lineColor,
            start = Offset(noseX + noseSize / 2, noseY + noseSize / 2),
            end = Offset(noseX, noseY),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = lineColor,
            start = Offset(noseX, noseY),
            end = Offset(noseX - noseSize / 2, noseY),
            strokeWidth = strokeWidth
        )

        // ★ Mouth — small lines below nose
        val mouthY = noseY + noseSize
        drawLine(
            color = lineColor,
            start = Offset(noseX, mouthY),
            end = Offset(noseX, mouthY + h * 0.02f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = lineColor,
            start = Offset(noseX - w * 0.02f, mouthY + h * 0.02f),
            end = Offset(noseX + w * 0.02f, mouthY + h * 0.02f),
            strokeWidth = strokeWidth
        )
    }
}

enum class CatExpression {
    HAPPY,       // narrow eyes — listening to music
    SLEEPY,      // half-closed eyes — paused
    NEUTRAL,     // standard eyes
    CURIOUS      // wide eyes — alert
}
