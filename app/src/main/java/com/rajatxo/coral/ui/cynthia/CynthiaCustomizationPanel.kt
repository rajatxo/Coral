package com.rajatxo.coral.ui.cynthia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.rajatxo.coral.data.prefs.CynthiaNavBarCustomization
import com.rajatxo.coral.data.prefs.CynthiaSearchFabCustomization
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * ★ PREMIUM SQUARE CUSTOMIZATION PANEL with ARC DIAL
 *
 * A square floating glass panel with:
 *   - Stacked capsule tabs on top (Position X, Position Y, Width, etc.)
 *   - An arc dial in the center (semi-circular gauge with gradient ticks,
 *     needle, glowing dot — like the user's reference video)
 *   - CalSans font everywhere
 *   - Tick sound + haptic feedback on every value change
 *
 * Hold the nav bar or search FAB for 5 seconds → panel opens.
 * Tapping the menu (•••) icon on the TodaysTopCard also opens this panel
 * in TOP_CARD mode.
 */
enum class CustomizationMode { NAV_BAR, SEARCH_FAB, TOP_CARD }

@Composable
internal fun CynthiaCustomizationPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop?,
    isNavBar: Boolean = true,
    mode: CustomizationMode? = null
) {
    // ★ Draggable card state — the user can move the card anywhere by
    //   dragging the handle bar at the top.
    var cardOffsetX by remember { mutableStateOf(0f) }
    var cardOffsetY by remember { mutableStateOf(0f) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // ★ SQUARE panel — 300×340dp, glass morphism, rounded 24dp corners.
            //   Draggable via offset (cardOffsetX, cardOffsetY) updated by the
            //   drag handle at the top.
            Box(
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .size(width = 320.dp, height = 440.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(cardOffsetX.toInt(), cardOffsetY.toInt()) }
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {} // consume click so it doesn't dismiss
                    )
            ) {
                val panelShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .clip(panelShape)
                        .then(
                            if (backdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { panelShape },
                                    effects = {
                                        vibrancy()
                                        colorControls(
                                            brightness = 0.1f,
                                            contrast = 1f,
                                            saturation = 1.3f
                                        )
                                        blur(30f.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(Color.Black.copy(alpha = 0.45f))
                                    }
                                )
                            } else {
                                Modifier.background(Color(0xFF1A1A1A).copy(alpha = 0.92f))
                            }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), panelShape)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ★ iOS-style drag handle bar at the top center.
                        //   User holds and drags this to move the card.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            cardOffsetX += dragAmount.x
                                            cardOffsetY += dragAmount.y
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // The handle bar (small rounded pill)
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.4f))
                            )
                        }
                        // Panel content (with padding)
                        Box(modifier = Modifier.padding(16.dp)) {
                            CustomizationPanelContent(
                                onDismiss = onDismiss,
                                isNavBar = isNavBar,
                                mode = mode
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizationPanelContent(
    onDismiss: () -> Unit,
    isNavBar: Boolean,
    mode: CustomizationMode? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val vibrator = remember {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
            as? android.os.Vibrator
    }
    // ★ SoundPool for +/- button ticks
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
    val hapticsOn = com.rajatxo.coral.data.prefs.SoundHapticsManager.hapticsEnabled.collectAsState()
    val soundsOn = com.rajatxo.coral.data.prefs.SoundHapticsManager.soundsEnabled.collectAsState()
    val soundVolume = com.rajatxo.coral.data.prefs.SoundHapticsManager.soundVolume.collectAsState()

    fun tickHaptic() {
        if (hapticsOn.value) {
            try {
                view.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (_: Exception) { }
        }
        if (soundsOn.value && soundLoaded) {
            try {
                val vol = soundVolume.value / 100f
                soundPool.play(tickSoundId, vol, vol, 1, 0, 1f)
            } catch (_: Exception) { }
        }
    }

    val navCustom by CynthiaNavBarCustomization.customization.collectAsState()
    val searchCustom by CynthiaSearchFabCustomization.customization.collectAsState()
    val topCardCustom by com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.customization.collectAsState()
    val savedSearchPos by com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition.position.collectAsState()
    val savedTabPos by com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.position.collectAsState()

    // Effective mode — if `mode` is explicitly passed, it overrides
    //   the legacy `isNavBar: Boolean` argument. This keeps backward
    //   compatibility with existing call sites.
    val effectiveMode = mode ?: if (isNavBar) CustomizationMode.NAV_BAR else CustomizationMode.SEARCH_FAB

    // ★ Which field is selected for the arc dial. Defaults to first field.
    //   Each field has: label, current value, range, suffix, setter.
    data class Field(
        val label: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val suffix: String,
        val onValueChange: (Float) -> Unit
    )

    val fields: List<Field> = when (effectiveMode) {
        CustomizationMode.NAV_BAR -> listOf(
            Field("Position X", savedTabPos.first * 100f, 5f..95f, "%") { x ->
                com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition
                    .setPosition(x / 100f, savedTabPos.second)
            },
            Field("Position Y", savedTabPos.second * 100f, 79.4f..95f, "%") { y ->
                com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition
                    .setPosition(savedTabPos.first, y / 100f)
            },
            Field("Width", navCustom.widthDp, 100f..320f, "dp") {
                CynthiaNavBarCustomization.setWidth(it)
            },
            Field("Height", navCustom.heightDp, 40f..150f, "dp") {
                CynthiaNavBarCustomization.setHeight(it)
            },
            Field("Corner", navCustom.cornerRadiusDp, 0f..50f, "dp") {
                CynthiaNavBarCustomization.setCornerRadius(it)
            }
        )
        CustomizationMode.SEARCH_FAB -> listOf(
            Field("Position X", savedSearchPos.first * 100f, 5f..95f, "%") { x ->
                com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition
                    .setPosition(x / 100f, savedSearchPos.second)
            },
            Field("Position Y", savedSearchPos.second * 100f, 5f..95f, "%") { y ->
                com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition
                    .setPosition(savedSearchPos.first, y / 100f)
            },
            Field("Size", searchCustom.sizeDp, 36f..80f, "dp") {
                CynthiaSearchFabCustomization.setSize(it)
            },
            Field("Corner", searchCustom.cornerRadiusDp, 0f..50f, "dp") {
                CynthiaSearchFabCustomization.setCornerRadius(it)
            }
        )
        CustomizationMode.TOP_CARD -> listOf(
            Field("Width", topCardCustom.widthDp, 100f..400f, "dp") {
                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.setWidth(it)
            },
            Field("Height", topCardCustom.heightDp, 48f..200f, "dp") {
                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.setHeight(it)
            },
            Field("Corner", topCardCustom.cornerRadiusDp, 0f..50f, "dp") {
                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.setCornerRadius(it)
            },
            Field("Pos X", topCardCustom.offsetX, -300f..300f, "") {
                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
                    .setOffset(it, topCardCustom.offsetY)
            },
            Field("Pos Y", topCardCustom.offsetY, 50f..400f, "") {
                com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization
                    .setOffset(topCardCustom.offsetX, it)
            }
        )
    }

    // Currently selected field index
    var selectedField by remember { mutableStateOf(0) }
    val currentField = fields[selectedField]

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ★ Header row — title + Reset button + close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title (Nav Bar / Search Button / Today's Card)
            Text(
                text = when (effectiveMode) {
                    CustomizationMode.NAV_BAR -> "Nav Bar"
                    CustomizationMode.SEARCH_FAB -> "Search Button"
                    CustomizationMode.TOP_CARD -> "Today's Card"
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CalSansFamily
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset button (text)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = {
                                when (effectiveMode) {
                                    CustomizationMode.NAV_BAR -> {
                                        CynthiaNavBarCustomization.reset()
                                        com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.reset()
                                    }
                                    CustomizationMode.SEARCH_FAB -> {
                                        CynthiaSearchFabCustomization.reset()
                                        com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition.reset()
                                    }
                                    CustomizationMode.TOP_CARD -> {
                                        com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.reset()
                                    }
                                }
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reset",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalSansFamily
                    )
                }
                // Close button (✕)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalSansFamily
                    )
                }
            }
        }

        // ★ Capsule tabs — stacked vertically, scrollable if too many
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            fields.forEachIndexed { index, field ->
                val isSelected = index == selectedField
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = { selectedField = index }
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
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ★ +1 / -1 buttons — just below the last capsule (Corner).
        //   Lets the user nudge the selected field's value by exactly 1 unit.
        //   Fires tick sound + haptic on each tap. CalSans font, glass pill.
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
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {
                            val newValue = (currentField.value - 1f)
                                .coerceIn(currentField.range.start, currentField.range.endInclusive)
                            if (newValue.toInt() != currentField.value.toInt()) {
                                tickHaptic()
                            }
                            currentField.onValueChange(newValue)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "−",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }
            // +1 button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {
                            val newValue = (currentField.value + 1f)
                                .coerceIn(currentField.range.start, currentField.range.endInclusive)
                            if (newValue.toInt() != currentField.value.toInt()) {
                                tickHaptic()
                            }
                            currentField.onValueChange(newValue)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ★ ARC DIAL — the semi-circular gauge from the reference video.
        // Shows the currently selected field. Drag along the arc to change
        // the value. Tick sound + haptic on each step.
        CynthiaArcDial(
            label = currentField.label,
            value = currentField.value,
            range = currentField.range,
            suffix = currentField.suffix,
            onValueChange = currentField.onValueChange,
            onReset = {
                when (effectiveMode) {
                    CustomizationMode.NAV_BAR -> {
                        CynthiaNavBarCustomization.reset()
                        com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.reset()
                    }
                    CustomizationMode.SEARCH_FAB -> {
                        CynthiaSearchFabCustomization.reset()
                        com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition.reset()
                    }
                    CustomizationMode.TOP_CARD -> {
                        com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.reset()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ★ Shape picker (still useful — shapes don't fit on a dial)
        ShapePicker(
            selected = when (effectiveMode) {
                CustomizationMode.NAV_BAR -> navCustom.shape
                CustomizationMode.SEARCH_FAB -> searchCustom.shape
                CustomizationMode.TOP_CARD -> topCardCustom.shape
            },
            onSelected = {
                when (effectiveMode) {
                    CustomizationMode.NAV_BAR -> CynthiaNavBarCustomization.setShape(it)
                    CustomizationMode.SEARCH_FAB -> CynthiaSearchFabCustomization.setShape(it)
                    CustomizationMode.TOP_CARD ->
                        com.rajatxo.coral.data.prefs.CynthiaTodaysTopCardCustomization.setShape(it)
                }
            }
        )
    }
}

@Composable
private fun ShapePicker(
    selected: CynthiaCustomShape,
    onSelected: (CynthiaCustomShape) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Shape",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontFamily = CalSansFamily
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            CynthiaCustomShape.entries.forEach { shape ->
                val isSelected = shape == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(13.dp)
                        )
                        .clickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = { onSelected(shape) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shape.displayName,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = CalSansFamily
                    )
                }
            }
        }
    }
}
