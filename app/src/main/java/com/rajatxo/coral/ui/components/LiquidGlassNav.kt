package com.rajatxo.coral.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Real-time backdrop blur for the nav bar (Tab Capsule).
 *
 * Approach (same as SimpMusic's liquid glass, without the kyant library):
 *   1. Capture the page content into a GraphicsLayer via rememberGraphicsLayer()
 *      + Modifier.drawWithCache { onDrawWithContent { layer.record { drawContent() }; drawLayer(layer) } }
 *   2. Pass that GraphicsLayer to the nav bar
 *   3. Inside the nav bar, render the SAME GraphicsLayer with Modifier.blur()
 *      applied — this creates a real-time blurred version of whatever the page
 *      is drawing behind the nav bar.
 *
 * This is a TRUE backdrop blur (not a duplicate or approximation). The actual
 * page pixels are captured and blurred. When the page scrolls or changes, the
 * blur updates in real time.
 *
 * Requires API 31+ (Android 12) for Modifier.blur() to work — below that,
 * Modifier.blur() is a no-op and the capsule falls back to a solid tint.
 *
 * Usage:
 *   val (captureModifier, blurLayer) = rememberNavBlurLayer()
 *   Box(modifier = Modifier.fillMaxSize().then(captureModifier)) {
 *       // page content here
 *   }
 *   // Elsewhere (inside the nav bar):
 *   Box(modifier = Modifier.fillMaxSize().blurBackground(blurLayer, 24.dp))
 */
@Composable
fun rememberNavBlurLayer(): Pair<Modifier, GraphicsLayer> {
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
