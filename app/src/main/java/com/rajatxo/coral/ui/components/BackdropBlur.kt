package com.rajatxo.coral.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Captures the composable's drawn content into a reusable [GraphicsLayer].
 *
 * Returns a [Pair] of (Modifier, GraphicsLayer) — apply the Modifier to
 * the root of the content you want to capture, then pass the GraphicsLayer
 * to [blurBackground] to render it (blurred) inside another composable.
 *
 * WHY: Compose's Modifier.blur() only blurs the composable's OWN content,
 * not what's behind it. To get a TRUE backdrop blur (e.g., nav rail blurs
 * the actual page content behind it), we need to:
 *   1. Capture the page content as a GraphicsLayer
 *   2. Render the SAME layer inside the rail's bounds
 *   3. Apply Modifier.blur() to that rendered layer
 *
 * Now the rail shows a real blurred version of whatever the page is drawing
 * behind it — the actual pixels, not a duplicate or approximation.
 *
 * Usage:
 *   val (captureModifier, layer) = rememberGraphicsLayerCapture()
 *   Box(modifier = Modifier.fillMaxSize().then(captureModifier)) {
 *       // page content
 *   }
 *   // Elsewhere (e.g., inside the nav rail):
 *   Box(modifier = Modifier.fillMaxSize().blurBackground(layer, 28.dp))
 */
@Composable
fun rememberGraphicsLayerCapture(): Pair<Modifier, GraphicsLayer> {
    val layer = rememberGraphicsLayer()
    val captureModifier = Modifier.drawWithCache {
        onDrawWithContent {
            layer.record {
                this@onDrawWithContent.drawContent()
            }
            drawLayer(layer)
        }
    }
    return captureModifier to layer
}

/**
 * Renders the captured [GraphicsLayer] inside this composable's bounds,
 * then applies a blur. Use this inside the rail to show a real backdrop
 * blur of the page content behind the rail.
 *
 * @param layer The GraphicsLayer captured via [rememberGraphicsLayerCapture].
 * @param blurDp The blur radius in dp.
 */
fun Modifier.blurBackground(
    layer: GraphicsLayer,
    blurDp: androidx.compose.ui.unit.Dp
): Modifier = this
    .drawWithCache {
        onDrawWithContent {
            // Translate the layer to align with this composable's bounds.
            // The page is at x=0,y=0; the rail is also at x=0,y=0 (since it
            // overlays the page's top-left). So no translation needed.
            drawLayer(layer)
        }
    }
    .blur(blurDp)
