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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * Tab Capsule (nav bar) — Coral's centered tab switcher with liquid glass.
 *
 * Renders the active song's album art blurred inside the capsule as a
 * glass morphism effect. On API 31+ (Android 12), Modifier.blur() applies
 * real AGSL-based blur to the album art image.
 *
 * The blur is VISIBLE — album art colors show through the glass. It's
 * not a true backdrop blur (doesn't sample what's exactly behind the
 * capsule), but it creates the liquid glass look with real content.
 */
@Composable
fun TabCapsule(
    tabs: List<CoralTab>,
    activeTab: CoralTab,
    onTabSelected: (CoralTab) -> Unit,
    modifier: Modifier = Modifier,
    blurImageUri: android.net.Uri? = null
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activeIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

    var slideDirection by remember { mutableIntStateOf(1) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    val dragThreshold = 60f

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
                        if (dragAccumulator < -dragThreshold) {
                            slideDirection = 1
                            val nextIndex = (activeIndex + 1) % tabs.size
                            onTabSelected(tabs[nextIndex])
                            tickHaptic()
                            dragAccumulator = 0f
                        } else if (dragAccumulator > dragThreshold) {
                            slideDirection = -1
                            val prevIndex = if (activeIndex - 1 < 0) tabs.size - 1 else activeIndex - 1
                            onTabSelected(tabs[prevIndex])
                            tickHaptic()
                            dragAccumulator = 0f
                        }
                    }
                )
            }
    ) {
        // --- Layer 1: Blurred album art (liquid glass effect) ---
        // Renders the active song's album art, blurred via Modifier.blur().
        // This creates VISIBLE glass morphism with real colors from the art.
        // On API 31+ (Android 12), blur() uses AGSL RenderEffect (hardware-accelerated).
        if (blurImageUri != null) {
            coil3.compose.AsyncImage(
                model = blurImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(25.dp)
            )
        } else {
            // Fallback: dark bg when no song is playing
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        }

        // --- Layer 2: Dark translucent tint (glass scrim) ---
        // Makes the blurred bg darker so white text is readable.
        // 30% opacity — dark enough for text, light enough for blur to show.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
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
                    .background(Color.Transparent)
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
