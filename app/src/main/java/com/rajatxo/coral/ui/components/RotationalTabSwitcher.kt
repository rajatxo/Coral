package com.rajatxo.coral.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Rotational Tab Switcher — Coral's replacement for the nav rail.
 *
 * A wheel of tab labels arranged along a vertical arc (same geometry as
 * PlaylistWheel, rotated 90°). Slides in from the left edge when triggered.
 * The active tab sits at the center (largest, brightest). Neighbors arc
 * away on both sides, getting smaller and dimmer.
 *
 * WHY: Removing the permanent 48dp nav rail frees up the full screen width
 * for content. The tab switcher appears on demand — tap the floating menu
 * button (or swipe from the left edge) → the wheel slides in → tap a tab →
 * it slides away. Coral's rotational DNA is preserved.
 *
 * Interaction:
 *   - Drag vertically to rotate the wheel (change active tab)
 *   - Snap to nearest tab on release (spring)
 *   - Tap a tab label to select it
 *   - Tap outside or system back to dismiss
 *
 * @param visible Whether the switcher is showing
 * @param tabs The list of tabs to display
 * @param activeTab The currently selected tab
 * @param onTabSelected Called when a tab is tapped
 * @param onDismiss Called when the user dismisses the switcher
 */
@Composable
fun RotationalTabSwitcher(
    visible: Boolean,
    tabs: List<CoralTab>,
    activeTab: CoralTab?,
    onTabSelected: (CoralTab) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Continuous rotation: 0 = first tab at center, 1 = second tab, etc.
    val fractionalIndex = remember { Animatable(0f) }
    var lastIntIndex by remember { mutableStateOf(0) }

    // Initialize to the active tab
    LaunchedEffect(activeTab) {
        val activeIdx = tabs.indexOf(activeTab).coerceAtLeast(0)
        fractionalIndex.snapTo(activeIdx.toFloat())
        lastIntIndex = activeIdx
    }

    val totalTabs = tabs.size
    val activeIndex = fractionalIndex.value.roundToInt().coerceIn(0, totalTabs - 1)

    // --- Backdrop: tap to dismiss ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    )

    // --- The wheel panel (slides in from left) ---
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(120.dp)
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(totalTabs) {
                detectDragGestures(
                    onDragEnd = {
                        val target = fractionalIndex.value.roundToInt().coerceIn(0, totalTabs - 1).toFloat()
                        scope.launch {
                            fractionalIndex.animateTo(
                                targetValue = target,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
                            )
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Drag down → decrease index (previous tab)
                        // Drag up → increase index (next tab)
                        val delta = dragAmount.y / 80f  // 80px per tab step
                        scope.launch {
                            fractionalIndex.snapTo(
                                (fractionalIndex.value + delta).coerceIn(0f, (totalTabs - 1).toFloat())
                            )
                        }
                    }
                )
            }
    ) {
        // --- Render tab labels along the vertical arc ---
        val visibleRange = -3..3
        val labelsToRender = visibleRange.mapNotNull { offset ->
            val index = activeIndex + offset
            if (index in tabs.indices) {
                Triple(index, offset, fractionalIndex.value - index)
            } else null
        }.sortedByDescending { abs(it.third) }  // far first, near last (on top)

        val textMeasurer = rememberTextMeasurer()

        labelsToRender.forEach { (index, _, fractionalOffset) ->
            val tab = tabs[index]
            val isActive = index == activeIndex

            // Falloff based on distance from center
            val absOffset = abs(fractionalOffset)
            val scale = (1f - absOffset * 0.3f).coerceIn(0.4f, 1f)
            val alpha = (1f - absOffset * 0.5f).coerceIn(0.15f, 1f)

            // Position along vertical arc (bulging right, like PlaylistWheel rotated 90°)
            // y = fractionalOffset * stepY (vertical displacement)
            // x = (1 - cos(fractionalOffset)) * arcRadiusX (horizontal: 0 at center, positive at edges)
            val stepY = 0.18f  // fraction of panel height per tab
            val arcRadiusX = 0.08f  // how much the arc bulges right
            val yFraction = fractionalOffset * stepY
            val xFraction = (1f - kotlin.math.cos(fractionalOffset.toDouble()).toFloat()) * arcRadiusX

            // Measure the text
            val color = if (isActive) Color.White else Color.White.copy(alpha = alpha)
            val weight = FontWeight.Bold
            val fontSize = if (isActive) 18.sp else 14.sp

            val layoutResult = remember(tab.label, color, weight, fontSize) {
                textMeasurer.measure(
                    text = AnnotatedString(tab.label),
                    style = TextStyle(
                        color = color,
                        fontSize = fontSize,
                        fontWeight = weight,
                        fontFamily = PlayfairItalicFamily
                    ),
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(
                        minWidth = 0,
                        minHeight = 0,
                        maxWidth = Int.MAX_VALUE,
                        maxHeight = Int.MAX_VALUE
                    )
                )
            }

            val textWidthPx = layoutResult.size.width.toFloat()
            val textHeightPx = layoutResult.size.height.toFloat()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isActive) {
                                onTabSelected(tab)
                                onDismiss()
                            } else {
                                scope.launch {
                                    fractionalIndex.animateTo(
                                        targetValue = index.toFloat(),
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
                                    )
                                }
                            }
                        }
                    )
            ) {
                val panelWidth = size.width
                val panelHeight = size.height
                val centerX = panelWidth * (0.3f + xFraction)  // base 30% from left + arc
                val centerY = panelHeight / 2f + yFraction * panelHeight

                // Rotate text 0° (horizontal reading) — simpler than PlaylistWheel's -90°
                // because tabs are short words
                drawText(
                    textLayoutResult = layoutResult,
                    topLeft = Offset(
                        x = centerX - textWidthPx / 2f,
                        y = centerY - textHeightPx / 2f
                    )
                )
            }
        }

        // --- Gear icon at top (settings access) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .width(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CoralIcons.ChevronDown,
                contentDescription = "Close",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

// Helper extension
private fun Float.roundToInt(): Int = Math.round(this)
