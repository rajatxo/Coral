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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.platform.LocalContext
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
 * The active tab's text sits ON the string (black, Cal Sans, 17sp).
 *
 * INTERACTION (discrete swipe + infinite wrap):
 *   - One swipe = one tab change (discrete, not continuous scroll)
 *   - Swipe left → next tab (text slides left, new enters from right)
 *   - Swipe right → previous tab (text slides right, new enters from left)
 *   - INFINITE wrap: Quick picks → ... → Folders → Quick picks → ...
 *     (loops forever, never hits an edge)
 *   - Haptic + sound fire TOGETHER, once per swipe (synced)
 *
 * Visual:
 *   - Capsule bg: pure white (Color.White)
 *   - String: black, horizontal, faded at both ends
 *   - Text: black, Cal Sans, 17sp SemiBold, centered on the string
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
    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    // Direction of the last swipe (for slide animation)
    // 1 = forward (next tab, swipe left), -1 = backward (prev tab, swipe right)
    var slideDirection by remember { mutableIntStateOf(1) }

    // Drag accumulator: builds up as user drags, fires one tab change when
    // threshold is reached, then resets. One swipe = one tab.
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 60f  // px needed to trigger one tab change

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
    var soundLoaded by remember { androidx.compose.runtime.mutableStateOf(false) }
    val tickSoundId = remember {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) soundLoaded = true
        }
        soundPool.load(context, com.rajatxo.coral.R.raw.wheel_tick, 1)
    }

    /**
     * Fire haptic + sound TOGETHER (synced), once per tab change.
     * Matches PlaylistWheel's tickHaptic approach.
     */
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

        // 3. Sound (fires right after haptic, so they're synced)
        if (soundLoaded) {
            try {
                soundPool.play(
                    tickSoundId,
                    0.6f, 0.6f,  // left + right volume
                    1, 0, 1f
                )
            } catch (_: Exception) { }
        }
    }

    Box(
        modifier = modifier
            .width(240.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .pointerInput(tabs, activeTab) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        // Drag left (negative) → next tab (forward)
                        if (dragAccumulator < -dragThreshold) {
                            slideDirection = 1
                            // Infinite wrap: next index modulo tabs.size
                            val nextIndex = (activeIndex + 1) % tabs.size
                            onTabSelected(tabs[nextIndex])
                            tickHaptic()  // haptic + sound synced together
                            dragAccumulator = 0f
                        }
                        // Drag right (positive) → previous tab (backward)
                        else if (dragAccumulator > dragThreshold) {
                            slideDirection = -1
                            // Infinite wrap: prev index modulo tabs.size (handle negative)
                            val prevIndex = if (activeIndex - 1 < 0) tabs.size - 1 else activeIndex - 1
                            onTabSelected(tabs[prevIndex])
                            tickHaptic()  // haptic + sound synced together
                            dragAccumulator = 0f
                        }
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

        // --- Sliding tab text (one tab visible at a time, slides on swipe) ---
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                if (slideDirection == 1) {
                    // Forward: old slides left, new enters from right
                    slideInHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth }
                } else {
                    // Backward: old slides right, new enters from left
                    slideInHorizontally(animationSpec = tween(250)) { fullWidth -> -fullWidth } togetherWith
                        slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth }
                }
            },
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            label = "tabText"
        ) { tab ->
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.Center)
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tab.label,
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}
