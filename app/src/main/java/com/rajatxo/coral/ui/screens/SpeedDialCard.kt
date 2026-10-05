package com.rajatxo.coral.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.data.prefs.SpeedDialModeManager
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * ★ SpeedDialCard — glass capsule that lets the user switch Speed Dial mode.
 *
 * Same shape + size + position pattern as TodaysTopCard — renders OUTSIDE
 * the layerBackdrop Box (passed backdrop in from caller, just like nav bar
 * and TodaysTopCard). Inside the capsule, the active mode label slides
 * left/right when the user swipes — exactly like the nav bar's tab switcher.
 *
 * Haptics + sound on each swipe — same as nav bar.
 *
 * Two modes (cycled by swiping):
 *   RANDOM       → "Based on Random songs"
 *   LAST_PLAYED  → "Based on Last Played"
 *
 * Opened by tapping the "Speed dial" text or the chevron beside it in
 * QuickPicksScreen.
 */
@Composable
fun SpeedDialCard(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val cardCustom by com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
        .customization.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current

    // ─── Haptics + Sound (same pattern as TabCapsule) ──────────────────
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
            try {
                view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }
        }
        if (soundsOn && soundLoaded) {
            try {
                soundPool.play(tickSoundId, volume, volume, 1, 0, 1f)
            } catch (_: Exception) { }
        }
    }

    // ─── Mode state (persisted) ────────────────────────────────────────
    val currentMode by SpeedDialModeManager.mode.collectAsState()
    val modes = SpeedDialModeManager.SpeedDialMode.entries
    val activeIndex = modes.indexOf(currentMode).coerceAtLeast(0)

    var slideDirection by remember { mutableStateOf(1) }
    var dragAccumulator by remember { mutableStateOf(0f) }
    val dragThreshold = 40f

    // ─── Card shape (from prefs — same defaults as TodaysTopCard) ──────
    val cornerRadius = cardCustom.cornerRadiusDp.dp
    val cardShape = RoundedCornerShape(cornerRadius)

    // ★ RippleDismissContainer — wraps the scrim + AGSL ripple + card.
    //   The card content receives `progress` (0 → 1) so it can fade/scale
    //   in sync with the ripple animation. Card fades out + scales down
    //   slightly + drifts upward — feels like it's being absorbed into
    //   the ripple, not just disappearing.
    RippleDismissContainer(onDismiss = onDismiss) { progress ->
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        cardCustom.offsetX.toInt(),
                        cardCustom.offsetY.toInt()
                    )
                }
                .width(cardCustom.widthDp.dp)
                .height(cardCustom.heightDp.dp)
                .clip(cardShape)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { cardShape },
                            effects = {
                                vibrancy()
                                colorControls(
                                    brightness = 0.05f,
                                    contrast = 1f,
                                    saturation = 1.5f
                                )
                                blur(12f.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.Black.copy(alpha = 0.25f))
                            }
                        )
                    } else {
                        Modifier.background(Color(0xFF1A1A1A).copy(alpha = 0.88f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), cardShape)
                // ★ Card fade-out — fades + scales + drifts up in sync with
                //   the ripple. As the ripple expands, the card fades away.
                .rippleFadeOut(progress)
                .pointerInput(modes, currentMode) {
                    detectHorizontalDragGestures(
                        onDragEnd = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAccumulator += dragAmount
                            if (dragAccumulator < -dragThreshold) {
                                slideDirection = 1
                                val nextIndex = (activeIndex + 1) % modes.size
                                SpeedDialModeManager.setMode(modes[nextIndex])
                                tickHaptic()
                                dragAccumulator = 0f
                            } else if (dragAccumulator > dragThreshold) {
                                slideDirection = -1
                                val prevIndex = if (activeIndex - 1 < 0) modes.size - 1 else activeIndex - 1
                                SpeedDialModeManager.setMode(modes[prevIndex])
                                tickHaptic()
                                dragAccumulator = 0f
                            }
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // consume tap so it doesn't dismiss
                )
        ) {
            // ─── White horizontal string (same as nav bar) ──────────────
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

            // ─── Sliding mode text (same pattern as nav bar) ────────────
            val modeLabel = when (currentMode) {
                SpeedDialModeManager.SpeedDialMode.RANDOM -> "Based on Random songs"
                SpeedDialModeManager.SpeedDialMode.LAST_PLAYED -> "Based on Last Played"
            }
            AnimatedContent(
                targetState = currentMode,
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
                label = "modeText"
            ) { _ ->
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.Center)
                        .background(Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = modeLabel,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}
