package com.rajatxo.coral.ui.cynthia

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.data.prefs.SearchCardCustomization
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.screens.RippleDismissContainer
import com.rajatxo.coral.ui.screens.rippleFadeOut
import com.rajatxo.coral.ui.theme.CalSansFamily

private const val SEARCH_SATURATION = 1.6f
private const val SEARCH_BRIGHTNESS = 0.08f

/**
 * ★ CynthiaSearchScreen — glass card from top with inline customization panel.
 *
 * Features:
 *   - Card from top, rounded bottom corners
 *   - "Search" text centered, 3-dot menu icon on left
 *   - Tap menu → customization panel fades IN inside the card (no separate card)
 *   - Customization: card height (expand), blur value, darkness value, corner roundness
 *   - Arc dial + haptics + sound (same as nav bar customization)
 *   - Tap outside → ripple + card fades together
 */
@Composable
fun CynthiaSearchScreen(
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null,
    isMiniPlayerVisible: Boolean = false
) {
    val config by SearchCardCustomization.config.collectAsState()
    val miniPlayerCustom by com.rajatxo.coral.data.prefs.CynthiaMiniPlayerCustomization
        .customization.collectAsState()

    // ★ Read nav bar position to calculate the exact same gap as miniplayer-nav.

    var showCustomization by remember { mutableStateOf(false) }

    val cardShape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = config.corner.dp, bottomEnd = config.corner.dp
    )

    // ★ Card height — animates smoothly when miniplayer is dismissed.
    //   When miniplayer is visible: heightExtra - 48dp (room for miniplayer)
    //   When miniplayer is dismissed: heightExtra (card expands down)
    //   The transition is animated with a spring for a cool expand effect.
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val targetHeightExtra = if (isMiniPlayerVisible) {
        config.heightExtra - 48f
    } else {
        config.heightExtra
    }
    val animatedHeightExtra by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetHeightExtra,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "cardHeight"
    )
    val cardHeight = screenHeight + animatedHeightExtra.dp

    RippleDismissContainer(onDismiss = onDismiss) { progress ->
    Box(modifier = Modifier.fillMaxSize()) {
        // ─── Search glass card ───────────────────────────────────────
        //   ★ rippleFadeOut MUST come BEFORE clip + drawBackdrop + border
        //   so graphicsLayer wraps the ENTIRE card (glass + border + content).
        //   If placed after, only the inner content fades — the glass stays
        //   visible until the ripple finishes. Same lesson as TodaysTopCard.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(cardHeight)
                .rippleFadeOut(progress)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clip(cardShape)
                .background(Color(0xFF0A0A0F))
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { cardShape },
                            effects = {
                                vibrancy()
                                colorControls(
                                    brightness = SEARCH_BRIGHTNESS,
                                    contrast = 1f,
                                    saturation = SEARCH_SATURATION
                                )
                                blur(config.blur.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(Color.Black.copy(alpha = config.darkness))
                            }
                        )
                    } else {
                        Modifier.background(Color.Black.copy(alpha = 0.7f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), cardShape)
                .statusBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // ─── Header: menu icon (left) + Search text (center) ──
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 3-dot menu icon (left)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showCustomization = !showCustomization }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CoralIcons.MoreVertical,
                            contentDescription = "Customize",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Search text (center)
                    Text(
                        text = "Search",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalSansFamily,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ─── Customization panel (fades in/out inside the card) ──
                AnimatedVisibility(
                    visible = showCustomization,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SearchCardCustomizationPanel(
                        config = config,
                        onBlurChange = { SearchCardCustomization.setBlur(it) },
                        onDarknessChange = { SearchCardCustomization.setDarkness(it) },
                        onHeightChange = { SearchCardCustomization.setHeightExtra(it) },
                        onCornerChange = { SearchCardCustomization.setCorner(it) },
                        onReset = { SearchCardCustomization.reset() }
                    )
                }

                // ─── Search content placeholder ──────────────────────────
                if (!showCustomization) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Search content goes here",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontFamily = CalSansFamily
                    )
                }
            }
        }
    }
    }
}

// ════════════════════════════════════════════════════════════════════
// INLINE CUSTOMIZATION PANEL — fades inside the search card
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SearchCardCustomizationPanel(
    config: SearchCardCustomization.SearchCardConfig,
    onBlurChange: (Float) -> Unit,
    onDarknessChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onCornerChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // ★ SoundPool + haptics (same as CynthiaCustomizationPanel)
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
        soundPool.setOnLoadCompleteListener { _, _, status -> if (status == 0) soundLoaded = true }
        soundPool.load(context, com.rajatxo.coral.R.raw.wheel_tick, 1)
    }

    fun tickHaptic() {
        if (SoundHapticsManager.hapticsEnabled.value) {
            try {
                view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }
        }
        if (SoundHapticsManager.soundsEnabled.value && soundLoaded) {
            try {
                val vol = SoundHapticsManager.soundVolume.value / 100f
                soundPool.play(tickSoundId, vol, vol, 1, 0, 1f)
            } catch (_: Exception) { }
        }
    }

    var selectedField by remember { mutableStateOf(0) }

    data class Field(
        val label: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val suffix: String,
        val onValueChange: (Float) -> Unit
    )

    val fields = listOf(
        Field("Blur", config.blur, 0f..80f, "dp") { onBlurChange(it); tickHaptic() },
        Field("Darkness", config.darkness * 100f, 0f..100f, "%") { onDarknessChange(it / 100f); tickHaptic() },
        Field("Height", config.heightExtra, -150f..200f, "dp") { onHeightChange(it); tickHaptic() },
        Field("Corner", config.corner, 0f..50f, "dp") { onCornerChange(it); tickHaptic() }
    )

    val currentField = fields[selectedField]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header: title + reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search Card",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onReset(); tickHaptic() }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Reset", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, fontFamily = CalSansFamily)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Field selector capsules (same style as nav bar customization)
        fields.forEachIndexed { index, field ->
            val isSelected = index == selectedField
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { selectedField = index; tickHaptic() }
                    )
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = field.label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = CalSansFamily
                )
                Text(
                    text = "${field.value.toInt()}${field.suffix}",
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // +/- buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            // -1 button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val newValue = (currentField.value - 1f)
                                .coerceIn(currentField.range.start, currentField.range.endInclusive)
                            if (newValue.toInt() != currentField.value.toInt()) tickHaptic()
                            currentField.onValueChange(newValue)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("−", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = CalSansFamily)
            }
            // +1 button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val newValue = (currentField.value + 1f)
                                .coerceIn(currentField.range.start, currentField.range.endInclusive)
                            if (newValue.toInt() != currentField.value.toInt()) tickHaptic()
                            currentField.onValueChange(newValue)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = CalSansFamily)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Arc dial (same as nav bar customization panel)
        CynthiaArcDial(
            label = currentField.label,
            value = currentField.value,
            range = currentField.range,
            suffix = currentField.suffix,
            onValueChange = currentField.onValueChange,
            onReset = { onReset(); tickHaptic() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
