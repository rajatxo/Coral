package com.rajatxo.coral.ui.components

import android.graphics.Picture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp

/**
 * A shared holder for capturing page content into an Android [Picture].
 *
 * WHY: Compose's Modifier.blur() only blurs the composable's OWN content,
 * not what's behind it. To get a TRUE backdrop blur (nav rail blurs the
 * actual page content behind it), we need to:
 *   1. Capture the page content as a Picture (during the page's draw phase)
 *   2. Render the SAME Picture inside the rail's bounds
 *   3. Apply Modifier.blur() to that rendered picture
 *
 * Now the rail shows a real blurred version of whatever the page is drawing
 * behind it — the actual pixels, not a duplicate or approximation.
 */
class PageCapture {
    var picture: Picture = Picture()
    var size: Size = Size.Zero
}

/**
 * Creates a remembered [PageCapture] for sharing between the page content
 * and the nav rail's blur layer.
 */
@Composable
fun rememberPageCapture(): PageCapture {
    return remember { PageCapture() }
}

/**
 * Captures the composable's drawn content into the given [PageCapture].
 * Apply this modifier to the root of the content you want to capture.
 * The content is drawn normally (to the screen) AND captured into the
 * Picture for later use by [blurBackground].
 */
fun Modifier.capturePage(capture: PageCapture): Modifier = this.drawWithCache {
    onDrawWithContent {
        // Record the content into the Picture
        val w = size.width.toInt().coerceAtLeast(1)
        val h = size.height.toInt().coerceAtLeast(1)
        capture.picture = Picture()
        capture.size = size
        val canvas = capture.picture.beginRecording(w, h)
        draw(this, layoutDirection, canvas, size)
        capture.picture.endRecording()
        // Also draw the content normally to the screen
        drawContent()
    }
}

/**
 * Renders the captured page content inside this composable's bounds,
 * then applies a blur. Use this inside the rail to show a real backdrop
 * blur of the page content behind the rail.
 *
 * @param capture The PageCapture holding the page's drawn content.
 * @param blurDp The blur radius in dp.
 */
fun Modifier.blurBackground(
    capture: PageCapture,
    blurDp: Dp
): Modifier = this
    .drawWithCache {
        onDrawWithContent {
            // Draw the captured picture into this composable's bounds
            // (the picture is the full page; it gets clipped to the rail's
            // 56dp width automatically since this composable is only 56dp wide)
            drawIntoCanvas { c ->
                c.nativeCanvas.drawPicture(capture.picture)
            }
        }
    }
    .blur(blurDp)
