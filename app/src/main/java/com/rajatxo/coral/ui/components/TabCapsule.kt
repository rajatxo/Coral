package com.rajatxo.coral.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * Tab Capsule — Coral's centered tab switcher.
 *
 * A glossy pill-shaped capsule at the bottom center of the screen, below
 * the mini player and above the system nav buttons. Inside the capsule:
 * a horizontal "string" (thin line) with faded ends, and the active tab's
 * text sits centered on the string in Cal Sans.
 *
 * Interaction:
 *   - Swipe/drag left → next tab (text slides left, new text enters from right)
 *   - Swipe/drag right → previous tab (text slides right, new text enters from left)
 *   - Haptic CLOCK_TICK on each tab change
 *   - Only ONE tab text visible at a time (like Reddit's community switcher)
 *
 * Visual:
 *   - Glossy capsule: dark semi-transparent bg + white gradient highlight on top
 *     (glass reflection effect) + subtle white border
 *   - Horizontal string: thin line at vertical center, fades at both ends
 *     (gradient: transparent → white@25% → transparent)
 *   - Text: Cal Sans, 15sp, SemiBold, white, centered on the string
 *   - Slide animation: 250ms tween, smooth
 */
@Composable
fun TabCapsule(
    tabs: List<CoralTab>,
    activeTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    var slideDirection by remember { mutableIntStateOf(1) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 60f

    Box(
        modifier = modifier
            .width(220.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(tabs, activeTab) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        if (dragAccumulator < -dragThreshold) {
                            if (activeIndex < tabs.size - 1) {
                                slideDirection = 1
                                onTabSelected(tabs[activeIndex + 1])
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            dragAccumulator = 0f
                        } else if (dragAccumulator > dragThreshold) {
                            if (activeIndex > 0) {
                                slideDirection = -1
                                onTabSelected(tabs[activeIndex - 1])
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            dragAccumulator = 0f
                        }
                    }
                )
            }
    ) {
        // --- The string: horizontal line with faded ends ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.15f to Color.White.copy(alpha = 0.25f),
                        0.85f to Color.White.copy(alpha = 0.25f),
                        1.0f to Color.Transparent
                    )
                ),
                topLeft = Offset(0f, centerY - 0.5f),
                size = Size(size.width, 1f)
            )
        }

        // --- Sliding tab text ---
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                if (slideDirection == 1) {
                    slideInHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth }
                } else {
                    slideInHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth }
                }
            },
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            label = "tabText"
        ) { tab ->
            Text(
                text = tab.label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // --- Glossy overlay: white gradient on top half ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = 0.12f),
                            0.5f to Color.White.copy(alpha = 0.03f),
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.1f)
                        )
                    )
                )
        )
    }
}
