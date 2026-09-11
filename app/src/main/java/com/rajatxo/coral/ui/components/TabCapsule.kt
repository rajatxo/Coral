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
 * A glossy pill-shaped capsule at the bottom center of the screen. Inside:
 * a HORIZONTAL STRING (thin line, faded at both ends like the PlaylistWheel
 * arc) with the active tab's text sitting ON the string in Cal Sans.
 *
 * The string is clearly visible — it passes through the capsule horizontally
 * at the vertical center, and the text sits ON TOP of it (text background
 * creates a small "gap" in the string where the text is, like the text is
 * threaded onto the string).
 *
 * Interaction:
 *   - Swipe left → next tab (text slides left, new enters from right)
 *   - Swipe right → previous tab (text slides right, new enters from left)
 *   - Haptic CLOCK_TICK on each tab change
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
            .height(52.dp)  // taller so the string + text both fit clearly
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.7f))
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
        // --- The STRING: horizontal line at vertical center, faded at both ends ---
        // Drawn FIRST (bottom layer) so the text sits ON TOP of it.
        // The string is clearly visible: white at 40% alpha in the center,
        // fading to transparent at both ends (like the PlaylistWheel arc).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val stringHeight = 1.5f  // slightly thicker so it's visible
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.1f to Color.White.copy(alpha = 0.1f),
                        0.25f to Color.White.copy(alpha = 0.4f),
                        0.75f to Color.White.copy(alpha = 0.4f),
                        0.9f to Color.White.copy(alpha = 0.1f),
                        1.0f to Color.Transparent
                    )
                ),
                topLeft = Offset(0f, centerY - stringHeight / 2f),
                size = Size(size.width, stringHeight)
            )
        }

        // --- Sliding tab text (sits ON TOP of the string) ---
        // The text has a small horizontal padding so it "breaks" the string
        // visually — like the text is threaded onto the string.
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
            // Text with a small dark bg behind it so the string appears
            // to "break" where the text is (threaded effect)
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = tab.label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
        }

        // --- Glossy overlay: white gradient on top half (glass reflection) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.White.copy(alpha = 0.15f),
                            0.5f to Color.White.copy(alpha = 0.04f),
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.15f)
                        )
                    )
                )
        )
    }
}
