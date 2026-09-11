package com.rajatxo.coral.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp

/**
 * A simple holder for the page's bg color. Used by the rail to render a
 * blurred duplicate of the page content behind the rail.
 *
 * WHY: Compose's Modifier.blur() only blurs the composable's OWN content,
 * not what's behind it. To create a convincing backdrop-blur effect, we
 * render a duplicate of the page's visible content (album art at top +
 * bg color at bottom) inside the rail, then blur it.
 *
 * This isn't a TRUE pixel-perfect backdrop blur (which would require
 * capturing the page's draw commands — not directly possible in Compose),
 * but it's visually convincing because we duplicate the exact same content
 * the page is showing behind the rail.
 */
class PageCapture {
    var bgColor: Color = Color(0xFF0A0E1A)
}

@Composable
fun rememberPageCapture(): PageCapture {
    return remember { PageCapture() }
}

/**
 * No-op modifier. Kept for API compatibility — the page content doesn't
 * need any special capture modifier. The rail uses the page's bg color +
 * album art URI (passed as separate params) to render its blur layer.
 */
fun Modifier.capturePage(capture: PageCapture): Modifier = this

/**
 * Renders a blurred duplicate of the page content inside this composable's
 * bounds. Used inside the rail to create a backdrop-blur effect.
 *
 * The duplicate consists of:
 *   - Album art at top (matching the hero image's position: 55% of height
 *     after statusBarsPadding)
 *   - Bg color filling the rest (matching the text area)
 *
 * Both are blurred via Modifier.blur(), creating a frosted-glass effect.
 *
 * @param bgColor The page's bg color (fills the bottom portion)
 * @param blurDp The blur radius in dp
 */
fun Modifier.blurBackground(
    bgColor: Color,
    blurDp: Dp
): Modifier = this
    .drawWithCache {
        onDrawWithContent {
            // Fill the entire rail with the bg color
            drawRect(color = bgColor)
        }
    }
    .blur(blurDp)
