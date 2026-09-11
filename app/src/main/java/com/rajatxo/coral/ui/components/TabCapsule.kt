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
 * Tab Capsule (nav bar) — Coral's centered tab switcher.
 *
 * A pure white glossy pill at the bottom center of the screen. Inside:
 * a BLACK horizontal string at the vertical center, faded at both ends.
 * The active tab's text sits ON the string (black, Cal Sans, slightly
 * bigger), covering the string where it passes through.
 *
 * Interaction:
 *   - Swipe left → next tab (text slides left, new enters from right)
 *   - Swipe right → previous tab (text slides right, new enters from left)
 *   - Haptic CLOCK_TICK on each tab change
 *
 * Visual:
 *   - Capsule bg: pure white (Color.White)
 *   - String: black, horizontal, faded at both ends (gradient:
 *     transparent → black@60% → black@60% → transparent)
 *   - Text: black, Cal Sans, 17sp SemiBold, centered on the string
 *   - String drawn FIRST (bottom layer), text ON TOP (covers the string)
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
            .width(240.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)  // pure white
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
        // --- The STRING: black horizontal line at vertical center, faded ends ---
        // Drawn FIRST so the text sits ON TOP of it (covering it).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val stringHeight = 1.5f
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.1f to Color.Black.copy(alpha = 0.15f),
                        0.25f to Color.Black.copy(alpha = 0.6f),
                        0.75f to Color.Black.copy(alpha = 0.6f),
                        0.9f to Color.Black.copy(alpha = 0.15f),
                        1.0f to Color.Transparent
                    )
                ),
                topLeft = Offset(0f, centerY - stringHeight / 2f),
                size = Size(size.width, stringHeight)
            )
        }

        // --- Sliding tab text (sits ON TOP of the string, covering it) ---
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
            // Text with white bg behind it so it covers the string where it is
            Box(
                modifier = Modifier
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = tab.label,
                    color = Color.Black,
                    fontSize = 17.sp,  // bigger
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}
