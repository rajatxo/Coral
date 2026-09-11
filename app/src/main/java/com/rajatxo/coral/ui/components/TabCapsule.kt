package com.rajatxo.coral.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Tab Capsule (nav bar) — Coral's centered tab switcher.
 *
 * A pure white glossy pill at the bottom center of the screen. Inside:
 * a BLACK horizontal string at the vertical center, faded at both ends.
 * The active tab's text sits ON the string (black, Cal Sans, 17sp).
 *
 * SCROLL MECHANICS (matches PlaylistWheel):
 *   - Continuous scroll offset (not discrete +1/-1 steps)
 *   - Drag updates offset directly → smooth, 1:1 with finger
 *   - Haptic + sound fires on every INDEX CROSSING (when the center
 *     tab changes during scroll, not on a threshold)
 *   - Fling: animateDecay with velocity from drag release
 *   - Snap: spring to nearest item after fling settles
 *   - INFINITE scroll: modulo wraps the index (Quick picks → ... →
 *     Folders → Quick picks → ...). Swipe left from Quick picks shows
 *     Folders (the last tab), swipe right from Folders shows Quick picks.
 *
 * Interaction:
 *   - Swipe left → next tab (text slides left, new enters from right)
 *   - Swipe right → previous tab (text slides right, new enters from left)
 *   - Haptic CLOCK_TICK + tick sound on every index crossing
 *   - Long swipe → multiple tabs change at once (continuous, not stepwise)
 */
@Composable
fun TabCapsule(
    tabs: List<CoralTab>,
    activeTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    // --- Continuous scroll offset (matches PlaylistWheel approach) ---
    // scrollOffset in "px" — dragAmount updates it directly.
    // pxPerItem = how many px of drag = one tab. 80px feels right.
    val pxPerItem = 80f
    val scrollOffset = remember { Animatable(activeIndex * pxPerItem) }

    // Track the last snapped index to fire haptic on crossing
    var lastSnappedIndex by remember { mutableIntStateOf(activeIndex) }

    // --- Vibrator fallback (same as PlaylistWheel) ---
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                    as? android.os.VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                    as? android.os.Vibrator
        }
    }

    // --- Sound effect (same as PlaylistWheel) ---
    val soundPool = remember {
        android.media.SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    var soundLoaded by remember { mutableStateOf(false) }
    val tickSoundId = remember {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) soundLoaded = true
        }
        soundPool.load(context, com.rajatxo.coral.R.raw.wheel_tick, 1)
    }

    /** Index of the tab currently at center, with infinite wrap (modulo). */
    fun indexAtOffset(offset: Float): Int {
        val raw = (offset / pxPerItem).roundToInt()
        val mod = raw % tabs.size
        return if (mod < 0) mod + tabs.size else mod
    }

    /** Fire haptic + sound on tab crossing (matches PlaylistWheel's tickHaptic). */
    fun tickHaptic() {
        // 1. Haptic via View.performHapticFeedback (FLAG_IGNORE_VIEW_SETTING
        //    so it fires even if the user disabled haptics in settings)
        var hapticPerformed = false
        try {
            hapticPerformed = view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Exception) { }

        // 2. Vibrator fallback (some OEMs return false from performHapticFeedback)
        if (!hapticPerformed) {
            try {
                val v = vibrator
                if (v != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        v.vibrate(
                            android.os.VibrationEffect.createPredefined(
                                android.os.VibrationEffect.EFFECT_CLICK
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(30)
                    }
                }
            } catch (_: Exception) { }
        }

        // 3. Sound (plays after haptic)
        if (soundLoaded) {
            try {
                soundPool.play(
                    tickSoundId,
                    0.6f, 0.6f,
                    1, 0, 1f
                )
            } catch (_: Exception) { }
        }
    }

    // Notify parent whenever the center tab changes (during scroll + at rest)
    val centerIndex = indexAtOffset(scrollOffset.value)
    LaunchedEffect(centerIndex, tabs) {
        if (centerIndex != activeIndex && tabs.isNotEmpty()) {
            onTabSelected(tabs[centerIndex])
        }
    }

    Box(
        modifier = modifier
            .width(240.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .pointerInput(tabs) {
                var velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        velocityTracker = VelocityTracker()
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().x
                        scope.launch {
                            // Fling: decay with the drag velocity
                            scrollOffset.animateDecay(
                                initialVelocity = -velocity * 0.5f,  // negated: drag left → offset increases
                                animationSpec = exponentialDecay(frictionMultiplier = 0.95f)
                            )
                            // Snap to nearest item with spring
                            val nearest = (scrollOffset.value / pxPerItem).roundToInt() * pxPerItem
                            scrollOffset.animateTo(
                                targetValue = nearest,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        scope.launch {
                            // Drag RIGHT (positive dragAmount) → previous tab → offset DECREASES
                            // Drag LEFT (negative dragAmount) → next tab → offset INCREASES
                            scrollOffset.snapTo(scrollOffset.value - dragAmount)
                        }
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        // Fire haptic on every index crossing (not threshold-based)
                        val currentIdx = indexAtOffset(scrollOffset.value)
                        if (currentIdx != lastSnappedIndex) {
                            lastSnappedIndex = currentIdx
                            tickHaptic()
                        }
                        change.consume()
                    }
                )
            }
    ) {
        // --- The STRING: black horizontal line at vertical center, faded ends ---
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

        // --- Tab text (centered, on the string) ---
        // We show the center index's text. For smooth sliding, we render
        // the text with a horizontal offset based on the fractional position
        // between items. This makes the text slide smoothly with the finger.
        val currentTab = tabs.getOrElse(centerIndex) { tabs[0] }
        // Fractional position within the current item (0..1)
        val fractional = (scrollOffset.value / pxPerItem) - (scrollOffset.value / pxPerItem).toInt()
        // Offset the text by the fractional amount (in dp)
        val textOffsetDp = (fractional * 80f)  // px equivalent, applied as translation

        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Text(
                text = currentTab.label,
                color = Color.Black,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                modifier = Modifier.graphicsLayer {
                    translationX = -textOffsetDp
                }
            )
        }
    }
}
