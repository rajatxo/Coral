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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * Tab Capsule (nav bar) — Coral's centered tab switcher with REAL liquid glass.
 *
 * Uses Kyant's backdrop library (same as SimpMusic) for TRUE real-time
 * backdrop blur. The page content is marked as the backdrop source via
 * Modifier.layerBackdrop() in HomeScreen. This capsule samples that content
 * and applies AGSL-based blur via drawBackdrop + effects { blur() }.
 *
 * The blur is REAL — whatever is behind the capsule on screen gets blurred
 * in real-time. Album art colors, text, etc. bleed through the glass.
 *
 * Interaction:
 *   - Swipe left → next tab (text slides left, new enters from right)
 *   - Swipe right → previous tab (text slides right, new enters from left)
 *   - Haptic + sound fire TOGETHER, once per swipe (synced)
 *   - Infinite wrap (Quick picks → ... → Folders → Quick picks)
 *
 * @param backdrop The LayerBackdrop shared with the page content (created in HomeScreen)
 */
@Composable
fun TabCapsule(
    tabs: List<CoralTab>,
    activeTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    var slideDirection by remember { mutableIntStateOf(1) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 60f

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

    /** Fire haptic + sound TOGETHER (synced), once per tab change. */
    fun tickHaptic() {
        val hapticsOn = SoundHapticsManager.hapticsEnabled.value
        val soundsOn = SoundHapticsManager.soundsEnabled.value
        val volume = SoundHapticsManager.soundVolume.value / 100f

        if (hapticsOn) {
            var hapticPerformed = false
            try {
                hapticPerformed = view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }

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

        if (soundsOn && soundLoaded) {
            try {
                soundPool.play(
                    tickSoundId,
                    volume, volume,
                    1, 0, 1f
                )
            } catch (_: Exception) { }
        }
    }

    // --- Build the modifier chain ---
    // If we have a backdrop, apply drawBackdrop with blur effect (REAL liquid glass)
    // If no backdrop (shouldn't happen), fall back to a dark translucent tint
    val capsuleShape: Shape = RoundedCornerShape(26.dp)

    val glassModifier = if (backdrop != null) {
        modifier
            .width(240.dp)
            .height(52.dp)
            .clip(capsuleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { capsuleShape },
                effects = {
                    vibrancy()
                    colorControls(
                        brightness = 0.05f,
                        contrast = 1f,
                        saturation = 1.5f
                    )
                    blur(12f.dp.toPx())  // AGSL-based real-time backdrop blur
                },
                onDrawSurface = {
                    // Dark scrim for text readability (SimpMusic's "đục đen" approach)
                    drawRect(Color.Black.copy(alpha = 0.25f))
                }
            )
    } else {
        modifier
            .width(240.dp)
            .height(52.dp)
            .clip(capsuleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    }

    Box(
        modifier = glassModifier
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
                                tickHaptic()
                            } else {
                                // Infinite wrap: from last tab to first
                                slideDirection = 1
                                onTabSelected(tabs[0])
                                tickHaptic()
                            }
                            dragAccumulator = 0f
                        } else if (dragAccumulator > dragThreshold) {
                            if (activeIndex > 0) {
                                slideDirection = -1
                                onTabSelected(tabs[activeIndex - 1])
                                tickHaptic()
                            } else {
                                // Infinite wrap: from first tab to last
                                slideDirection = -1
                                onTabSelected(tabs[tabs.size - 1])
                                tickHaptic()
                            }
                            dragAccumulator = 0f
                        }
                    }
                )
            }
    ) {
        // --- The STRING: white horizontal line at vertical center, faded ends ---
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

        // --- Sliding tab text (white, on the string) ---
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
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.Center)
                    .background(Color.Transparent)  // let the blur show through
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
