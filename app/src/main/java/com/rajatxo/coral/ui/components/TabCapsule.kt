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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
    modifier: Modifier = Modifier,
    blurLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null,
    blurImageUri: android.net.Uri? = null
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
     * Respects the user's SoundHapticsManager preferences:
     *   - Haptic only fires if hapticsEnabled = true
     *   - Sound only fires if soundsEnabled = true
     *   - Sound plays at the user's chosen volume (0..100 → 0..1)
     */
    fun tickHaptic() {
        val hapticsOn = com.rajatxo.coral.data.prefs.SoundHapticsManager.hapticsEnabled.value
        val soundsOn = com.rajatxo.coral.data.prefs.SoundHapticsManager.soundsEnabled.value
        val volume = com.rajatxo.coral.data.prefs.SoundHapticsManager.soundVolume.value / 100f

        // 1. Haptic (only if enabled)
        if (hapticsOn) {
            var hapticPerformed = false
            try {
                hapticPerformed = view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }

            // Vibrator fallback (some OEMs return false from performHapticFeedback)
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
        }

        // 2. Sound (only if enabled, fires right after haptic so they're synced)
        if (soundsOn && soundLoaded) {
            try {
                soundPool.play(
                    tickSoundId,
                    volume, volume,  // left + right volume (from user pref)
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
        // --- Layer 1: REAL BACKDROP BLUR (liquid glass effect) ---
        // Two approaches combined for reliability:
        //  A) blurImageUri (album art) — renders the active song's album art
        //     blurred inside the capsule. This DEFINITELY shows visible blur
        //     because the image has real content + colors.
        //  B) blurLayer (captured page content) — renders the captured page
        //     as a secondary blur layer. May or may not align perfectly, but
        //     adds to the glassy texture.
        if (blurImageUri != null) {
            // A: Album art blurred — reliable, visible blur
            coil3.compose.AsyncImage(
                model = blurImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
            )
        } else if (blurLayer != null) {
            // B: Captured page content (fallback)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawWithContent {
                            drawLayer(blurLayer)
                        }
                    }
                    .blur(20.dp)
            )
        }

        // --- Layer 2: Light translucent tint (glass morphism scrim) ---
        // Light enough so the blur is visible through it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.15f))
        )

        // --- Layer 3: The STRING (white, faded at both ends) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val stringHeight = 1.5f
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.1f to Color.White.copy(alpha = 0.15f),
                        0.25f to Color.White.copy(alpha = 0.6f),
                        0.75f to Color.White.copy(alpha = 0.6f),
                        0.9f to Color.White.copy(alpha = 0.15f),
                        1.0f to Color.Transparent
                    )
                ),
                topLeft = Offset(0f, centerY - stringHeight / 2f),
                size = Size(size.width, stringHeight)
            )
        }

        // --- Layer 4: Sliding tab text (white, on the string) ---
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
            // Text bg matches the dark tint so it covers the string where it is
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tab.label,
                    color = Color.White,
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
