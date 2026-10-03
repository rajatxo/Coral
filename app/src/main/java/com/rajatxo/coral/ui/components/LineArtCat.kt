package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * ★ LineArtCat — a cat drawn with ANIMATED horizontal scanlines.
 *
 * The cat's silhouette is a Path. Inside it, horizontal lines continuously
 * scroll upward — creating a "living/breathing" digital shimmer effect
 * (same as the reference video).
 *
 * The body also subtly scales (breathing), and the tail sways.
 *
 * Expressions change the eye shape:
 *   HAPPY   — narrow eyes (music playing)
 *   SLEEPY  — half-height eyes (paused)
 *   NEUTRAL — standard eyes
 */
@Composable
fun LineArtCat(
    expression: CatExpression = CatExpression.NEUTRAL,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White,
    lineSpacing: Float = 3f,
    strokeWidth: Float = 1.5f
) {
    // ★ ANIMATION 1: scanlines scroll upward continuously
    val infiniteTransition = rememberInfiniteTransition(label = "cat")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = lineSpacing,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = { it }),
            repeatMode = RepeatMode.Restart
        ),
        label = "scrollOffset"
    )

    // ★ ANIMATION 2: breathing (subtle body scale)
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = { it }),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    // ★ ANIMATION 3: tail sway
    val tailSway by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = { it }),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tailSway"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // ★ Apply breathing scale to the body
        val bodyScale = breathScale
        val bodyW = w * bodyScale
        val bodyOffsetX = (w - bodyW) / 2f

        // ★ Cat silhouette path — sitting cat facing forward
        val catPath = Path().apply {
            val bw = bodyW
            val bx = bodyOffsetX

            // Bottom-left of body
            moveTo(bx + bw * 0.25f, h * 0.95f)
            // Left side of body going up
            lineTo(bx + bw * 0.22f, h * 0.70f)
            lineTo(bx + bw * 0.20f, h * 0.55f)
            // Left shoulder
            lineTo(bx + bw * 0.18f, h * 0.45f)
            // Left side of head
            lineTo(bx + bw * 0.22f, h * 0.35f)
            lineTo(bx + bw * 0.25f, h * 0.28f)
            // Left ear (triangle pointing up)
            lineTo(bx + bw * 0.22f, h * 0.12f)
            lineTo(bx + bw * 0.28f, h * 0.22f)
            // Top of head between ears
            lineTo(bx + bw * 0.38f, h * 0.20f)
            // Right ear (triangle pointing up)
            lineTo(bx + bw * 0.45f, h * 0.10f)
            lineTo(bx + bw * 0.50f, h * 0.22f)
            // Right side of head
            lineTo(bx + bw * 0.55f, h * 0.30f)
            lineTo(bx + bw * 0.58f, h * 0.40f)
            // Right shoulder
            lineTo(bx + bw * 0.60f, h * 0.50f)
            // Right side of body going down
            lineTo(bx + bw * 0.62f, h * 0.65f)
            lineTo(bx + bw * 0.60f, h * 0.80f)

            // ★ Tail — with sway animation
            val tailOffset = tailSway
            cubicTo(
                bx + bw * 0.72f + tailOffset, h * 0.82f,
                bx + bw * 0.82f + tailOffset, h * 0.65f,
                bx + bw * 0.78f + tailOffset, h * 0.45f
            )
            cubicTo(
                bx + bw * 0.76f + tailOffset, h * 0.35f,
                bx + bw * 0.70f + tailOffset, h * 0.38f,
                bx + bw * 0.68f + tailOffset, h * 0.50f
            )
            // Back down the inner tail
            cubicTo(
                bx + bw * 0.65f, h * 0.65f,
                bx + bw * 0.58f, h * 0.78f,
                bx + bw * 0.55f, h * 0.90f
            )
            // Bottom of body
            lineTo(bx + bw * 0.25f, h * 0.95f)
            close()
        }

        // ★ FILL with ANIMATED scanlines — lines scroll upward
        val bounds = catPath.getBounds()

        clipPath(catPath) {
            // Draw horizontal lines that scroll upward
            var y = bounds.top - lineSpacing + scrollOffset
            while (y <= bounds.bottom + lineSpacing) {
                drawLine(
                    color = lineColor,
                    start = Offset(bounds.left - 5f, y),
                    end = Offset(bounds.right + 5f, y),
                    strokeWidth = strokeWidth
                )
                y += lineSpacing
            }
        }

        // ★ Eyes — solid rectangles that change with expression
        val eyeY = h * 0.38f
        val leftEyeX = bodyOffsetX + bodyW * 0.30f
        val rightEyeX = bodyOffsetX + bodyW * 0.44f
        val eyeWidth = bodyW * 0.06f

        val eyeHeight = when (expression) {
            CatExpression.HAPPY -> h * 0.02f
            CatExpression.SLEEPY -> h * 0.015f
            CatExpression.NEUTRAL -> h * 0.04f
            CatExpression.CURIOUS -> h * 0.05f
        }

        drawRect(
            color = lineColor,
            topLeft = Offset(leftEyeX, eyeY),
            size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight)
        )
        drawRect(
            color = lineColor,
            topLeft = Offset(rightEyeX, eyeY),
            size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight)
        )

        // ★ Nose — small triangle
        val noseY = h * 0.48f
        val noseX = bodyOffsetX + bodyW * 0.37f
        val noseSize = bodyW * 0.03f
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
            start = Offset(noseX - bodyW * 0.02f, mouthY + h * 0.02f),
            end = Offset(noseX + bodyW * 0.02f, mouthY + h * 0.02f),
            strokeWidth = strokeWidth
        )
    }
}

enum class CatExpression {
    HAPPY,
    SLEEPY,
    NEUTRAL,
    CURIOUS
}
