package com.rajatxo.coral.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coral's vertical navigation rail — ViTune-style positioning.
 *
 * Layout contract (matches ViTune):
 *  - Width: 48dp
 *  - Gear icon at top, vertically aligned with the "Songs" title
 *    (both use statusBarsPadding + 16dp top padding)
 *  - Labels drawn on Canvas using TextMeasurer — slot sized EXACTLY
 *    to the text's natural width, so there's ZERO clipping
 *  - Labels stacked with 8dp gap (tight, list-like — ViTune style)
 *  - Vertically centered in the rail (Spacer weight(1f) above and below)
 *
 * WHY Canvas + TextMeasurer:
 *   Compose's Modifier.rotate(-90f) only rotates the VISUAL — the layout
 *   measurement still uses the text's UNROTATED width. So if the parent
 *   slot is 48dp wide and "Quick picks" wants to be 88dp wide, the Text
 *   is clipped to 48dp horizontally BEFORE rotation. That's why "Quick"
 *   was visible but "picks" was cut off.
 *
 *   The Canvas + TextMeasurer approach lets us:
 *     1. Measure the text at its natural width (no parent constraint)
 *     2. Size the slot to EXACTLY the text's natural width (rotated height)
 *     3. Draw the text rotated -90° around the slot's center
 *
 *   Result: "Quick picks" renders fully, no clipping, ever.
 */
@Composable
fun CoralNavRail(
    mode: RailMode,
    selectedMainTab: CoralTab?,
    selectedSettingsTab: CoralSettingsTab?,
    onMainTabSelected: (CoralTab) -> Unit,
    onSettingsTabSelected: (CoralSettingsTab) -> Unit,
    onGearClick: () -> Unit,
    onBackClick: () -> Unit,
    onPlaylistLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(CoralColors.Surface)
            .padding(start = 4.dp)  // explicit horizontal padding — pushes text slightly right
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()  // aligns gear icon with the "Songs" title vertically
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Top icon: gear (Main mode) or back arrow (Settings mode) ----
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = if (mode == RailMode.Main) onGearClick else onBackClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mode == RailMode.Main) CoralIcons.Settings else CoralIcons.ChevronDown,
                    contentDescription = if (mode == RailMode.Main) "Open settings" else "Back to tabs",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Rail labels, vertically CENTERED (matches ViTune) ----
            // ViTune clusters all labels together in the vertical MIDDLE
            // of the rail, with empty space above AND below the group.
            // Gap between labels is 23dp — gentle space between them.
            Spacer(modifier = Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(23.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (mode == RailMode.Main) {
                    CoralTab.values().forEach { tab ->
                        val longPressHandler: (() -> Unit)? =
                            if (tab == CoralTab.Playlists) onPlaylistLongPress else null
                        RailLabel(
                            label = tab.label,
                            isSelected = tab == selectedMainTab,
                            onClick = { onMainTabSelected(tab) },
                            onLongPress = longPressHandler
                        )
                    }
                } else {
                    CoralSettingsTab.values().forEach { tab ->
                        RailLabel(
                            label = tab.label,
                            isSelected = tab == selectedSettingsTab,
                            onClick = { onSettingsTabSelected(tab) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * A single rotated label, drawn on Canvas with TextMeasurer.
 *
 * The slot width is 48dp (rail width). The slot height is sized EXACTLY
 * to the text's natural width (so after rotation, the text fits vertically
 * with no clipping). The text is drawn centered in the slot, rotated -90°
 * around the slot's center.
 */
@Composable
private fun RailLabel(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val color = if (isSelected) Color.White else CoralColors.TextMuted
    val weight = if (isSelected) FontWeight.Bold else FontWeight.Bold  // both bold now

    // Step 1: measure the text at its natural size (no parent constraints).
    val textMeasurer = rememberTextMeasurer()
    val layoutResult = remember(label, color, weight) {
        textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                color = color,
                fontSize = 16.sp,
                fontWeight = weight
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

    // Step 2: convert text size from px to dp.
    val density = LocalDensity.current
    val textWidthDp = with(density) { layoutResult.size.width.toDp() }
    val textHeightPx = layoutResult.size.height.toFloat()

    // --- Long-press countdown state (only used when onLongPress != null) ---
    var showCountdown by remember { mutableStateOf(false) }
    var countdownNumber by remember { mutableStateOf(3) }
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val longPressScope = rememberCoroutineScope()
    var isLongPressing by remember { mutableStateOf(false) }

    // Pop-up animation: scale 0 → 1 bouncy on show, fade 0 → 1 on show
    val capsuleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showCountdown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "longPressScale"
    )
    val capsuleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showCountdown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(250),
        label = "longPressAlpha"
    )

    // Step 3: build the slot with COMBINED click + long-press handling.
    // Putting both in one pointerInput avoids the clickable modifier
    // stealing touch events from the long-press detector.
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(textWidthDp)
            .pointerInput(onLongPress) {
                if (onLongPress == null) {
                    // No long-press for this tab — just act as a click target
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    released = true
                                    onClick()
                                }
                            }
                        }
                    }
                } else {
                    // Combined click + long-press detection
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            isLongPressing = false
                            longPressJob?.cancel()
                            down.consume()  // ← consume so clickable doesn't grab it

                            // Start 1.7s hold, then 3-2-1 countdown, then fire onLongPress
                            longPressJob = longPressScope.launch {
                                delay(1700L)
                                showCountdown = true
                                countdownNumber = 3
                                delay(1000L)
                                countdownNumber = 2
                                delay(1000L)
                                countdownNumber = 1
                                delay(1000L)
                                showCountdown = false
                                delay(250L)
                                isLongPressing = true
                                onLongPress()
                            }

                            // Wait for finger release
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()
                                if (!change.pressed) {
                                    released = true
                                    // If countdown didn't complete, treat as click
                                    if (!isLongPressing) {
                                        longPressJob?.cancel()
                                        showCountdown = false
                                        onClick()
                                    }
                                    isLongPressing = false
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Step 4: draw the rotated text on a Canvas centered in the slot.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val textWidthPx = layoutResult.size.width.toFloat()
            val textHeight = textHeightPx

            rotate(degrees = -90f, pivot = Offset(canvasWidth / 2f, canvasHeight / 2f)) {
                drawText(
                    textLayoutResult = layoutResult,
                    topLeft = Offset(
                        x = (canvasWidth - textWidthPx) / 2f,
                        y = (canvasHeight - textHeight) / 2f
                    )
                )
            }
        }

        // --- Countdown capsule overlay (shown during long-press) ---
        // Rendered OUTSIDE the narrow 48dp slot using absolute positioning,
        // so the capsule appears in the playlist wheel area to the right.
        if (onLongPress != null && capsuleAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(Alignment.End)
                    .graphicsLayer {
                        scaleX = capsuleScale
                        scaleY = capsuleScale
                        alpha = capsuleAlpha
                    }
            ) {
                Row(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A1A1A))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Colour wheel opening in",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    // Small inner capsule with the countdown number
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = countdownNumber.toString(),
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Coral's color palette — ViTune-style pure black.
 *
 * ViTune uses pure black (#000000) for the main surface, NOT dark gray.
 * This is the AMOLED-friendly approach — saves battery on OLED screens
 * and gives the "infinite depth" look ViTune is known for.
 */
object CoralColors {
    /** Main app surface — pure black, matches ViTune's background. */
    val Surface: Color = Color(0xFF000000)

    /** Slightly lighter for cards / mini player / nav rail pills. */
    val SurfaceVariant: Color = Color(0xFF1A1A1A)

    /** Primary text — pure white. */
    val TextPrimary: Color = Color.White

    /** Secondary text — muted gray for inactive items, subtitles, etc. */
    val TextMuted: Color = Color(0xFF888888)

    /** Coral accent — the brand color, used for active states + buttons. */
    val Coral: Color = Color(0xFFFF6B6B)
}

/**
 * Which rail is currently shown: main tabs or settings categories.
 */
enum class RailMode { Main, Settings }

/**
 * The 6 main destinations on the rail.
 */
enum class CoralTab(val label: String) {
    QuickPicks("Quick picks"),
    Discover("Discover"),
    Songs("Songs"),
    Playlists("Playlists"),
    Artists("Artists"),
    Albums("Albums"),
    Folders("Folders")
}

/**
 * The settings categories shown when the gear icon is tapped.
 * First entry is Premium (per the user's request).
 */
enum class CoralSettingsTab(val label: String) {
    Premium("Premium"),
    Appearance("Appearance"),
    Playback("Playback"),
    About("About")
}
