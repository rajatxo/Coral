package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rajatxo.coral.R

/**
 * Astroid pull-to-refresh indicator — replaces the default Material3
 * circular-arrow spinner with a custom Lucide "astroid" icon that
 * rotates while pulling/refreshing.
 *
 * Behavior:
 *  - progress = 0, isRefreshing = false → invisible (early return).
 *  - Pull in progress (progress rising 0→1) → icon fades in,
 *    rotates proportionally to pull progress (0° → 180° as you pull
 *    from rest to threshold). So you see the star twist as you pull.
 *  - isRefreshing = true → full visibility, continuous infinite
 *    rotation (one full turn per 1000ms, linear, never snaps).
 *
 * Visual:
 *  - 32dp Lucide astroid icon (4-point spiral/asterisk shape)
 *  - White stroke, no fill — high contrast against the dark gradient
 *    background of QuickPicks / Songs screens
 *  - Stroke-width = 2 in the 24x24 viewport (preserved by the
 *    VectorDrawable's strokeWidth attribute)
 *
 * @param progress Pull distance as a fraction of the refresh threshold
 *                 (0 = no pull, 1 = threshold reached, >1 = over-pulled).
 *                 Pass `ptrState.distanceFraction` from PullToRefreshBox.
 * @param isRefreshing True when a refresh is in progress. Pass the same
 *                     isRefreshing state that PullToRefreshBox uses.
 * @param modifier Standard modifier. Caller is responsible for
 *                 positioning (typically `Modifier.align(TopCenter).offset(y = 120.dp)`).
 */
@Composable
fun AstroidRefreshIndicator(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val pullProgress = progress.coerceIn(0f, 1f)
    val visibility = if (isRefreshing) 1f else pullProgress
    if (visibility <= 0.05f) return

    // ─── Continuous infinite rotation while refreshing ────────────────
    // rememberInfiniteTransition always runs (cheap when nothing's
    // observing it). We only READ the rotation value when isRefreshing
    // is true, so the animation doesn't waste CPU on visible updates
    // while idle (recomposition only happens for the rotation value
    // when we're actually using it).
    //
    // RepeatMode.Restart + LinearEasing = continuous uniform spin,
    // one full 360° turn per 1000ms (1Hz). Linear is correct here —
    // we want a steady mechanical rotation, not FastOutSlowIn.
    val infiniteTransition = rememberInfiniteTransition(label = "astroidRotation")
    val refreshRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // While pulling (not refreshing): rotate proportional to pull progress.
    // 0° at rest → 180° at threshold. Gives a "winding up" feel as you
    // pull the star into a refresh state.
    val pullRotation = pullProgress * 180f

    val finalRotation = if (isRefreshing) refreshRotation else pullRotation

    Icon(
        painter = painterResource(id = R.drawable.astroid),
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(32.dp)
            .rotate(finalRotation)
            .alpha(visibility)
    )
}
