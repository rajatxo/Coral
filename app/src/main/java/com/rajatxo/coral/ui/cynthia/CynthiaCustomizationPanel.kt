package com.rajatxo.coral.ui.cynthia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/**
 * ★ PREMIUM FLOATING CUSTOMIZATION PANEL
 *
 * A floating square panel with rounded corners and kyant blur (same glass
 * morphism as the nav bar). Appears when the user holds the nav bar or
 * search FAB for 5 seconds. Lets the user customize:
 *   - Nav bar size (width, height)
 *   - Search FAB size
 *   - Corner roundness for both
 *   - Shape (Pill, Rectangle, Rounded, Circle, Squircle) for both
 *   - Position (X, Y sliders for both — TODO: can be added later)
 *
 * The panel is centered on screen, with a dimmed background. Tapping
 * outside dismisses it. Has a "Done" button to close.
 */
@Composable
internal fun CynthiaCustomizationPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop?,
    // ★ true → show nav bar settings only, false → show search FAB settings only
    isNavBar: Boolean = true
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            // ★ COMPACT panel — 240dp wide, minimal padding. Small enough
            //   to see the nav bar / search FAB changes happening behind it.
            Box(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .width(240.dp)
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {} // consume click so it doesn't dismiss
                    )
            ) {
                // ★ Glass morphism background — same kyant blur as nav bar
                val panelShape = RoundedCornerShape(20.dp)
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
                                        drawRect(Color.Black.copy(alpha = 0.4f))
                                    }
                                )
                            } else {
                                Modifier.background(Color(0xFF1A1A1A).copy(alpha = 0.9f))
                            }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), panelShape)
                        .padding(14.dp)
                ) {
                    CustomizationPanelContent(
                        onDismiss = onDismiss,
                        isNavBar = isNavBar
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomizationPanelContent(
    onDismiss: () -> Unit,
    isNavBar: Boolean
) {
    val navCustom by CynthiaNavBarCustomization.customization.collectAsState()
    val searchCustom by CynthiaSearchFabCustomization.customization.collectAsState()
    // ★ Read saved search FAB position for the position sliders
    val savedSearchPos by com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition.position.collectAsState()
    // ★ Read saved nav bar position for the position sliders
    val savedTabPos by com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition.position.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header — dynamic title based on which element is being customized
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isNavBar) "Nav Bar" else "Search Button",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = com.rajatxo.coral.ui.theme.CalSansFamily
            )
            // Done button
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
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ★ Only show the relevant section
        if (isNavBar) {
            // ─── NAV BAR SECTION ───
            // ★ Position X slider — manually set the nav bar's X position
            SliderRow(
                label = "Position X",
                value = savedTabPos.first * 100f,
                range = 5f..95f,
                suffix = "%",
                onValueChange = { xPercent ->
                    com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition
                        .setPosition(xPercent / 100f, savedTabPos.second)
                }
            )

            // ★ Position Y slider — manually set the nav bar's Y position
            //   (constrained to ≥ 0.794 by the drag handler, but the slider
            //   allows 5%-95% so the user can set it freely)
            SliderRow(
                label = "Position Y",
                value = savedTabPos.second * 100f,
                range = 79.4f..95f,
                suffix = "%",
                onValueChange = { yPercent ->
                    com.rajatxo.coral.data.prefs.CynthiaTabCapsulePosition
                        .setPosition(savedTabPos.first, yPercent / 100f)
                }
            )

            // Width slider
            SliderRow(
                label = "Width",
                value = navCustom.widthDp,
                range = 100f..320f,
                suffix = "dp",
                onValueChange = { CynthiaNavBarCustomization.setWidth(it) }
            )

            // Height slider — max 150dp so the nav bar can be tall but stays
            // within the Y constraint (0.794 * screen height ≈ 164dp from bottom).
            SliderRow(
                label = "Height",
                value = navCustom.heightDp,
                range = 40f..150f,
                suffix = "dp",
                onValueChange = { CynthiaNavBarCustomization.setHeight(it) }
            )

            // Corner radius slider
            SliderRow(
                label = "Corner",
                value = navCustom.cornerRadiusDp,
                range = 0f..50f,
                suffix = "dp",
                onValueChange = { CynthiaNavBarCustomization.setCornerRadius(it) }
            )

            // Shape picker
            ShapePicker(
                selected = navCustom.shape,
                onSelected = { CynthiaNavBarCustomization.setShape(it) }
            )

            // Reset button
            ResetButton {
                CynthiaNavBarCustomization.reset()
            }
        } else {
            // ─── SEARCH FAB SECTION ───
            // ★ Position X slider — manually set the search FAB's X position
            SliderRow(
                label = "Position X",
                value = savedSearchPos.first * 100f,
                range = 5f..95f,
                suffix = "%",
                onValueChange = { xPercent ->
                    com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition
                        .setPosition(xPercent / 100f, savedSearchPos.second)
                }
            )

            // ★ Position Y slider — manually set the search FAB's Y position
            SliderRow(
                label = "Position Y",
                value = savedSearchPos.second * 100f,
                range = 5f..95f,
                suffix = "%",
                onValueChange = { yPercent ->
                    com.rajatxo.coral.data.prefs.CynthiaSearchFabPosition
                        .setPosition(savedSearchPos.first, yPercent / 100f)
                }
            )

            // Size slider
            SliderRow(
                label = "Size",
                value = searchCustom.sizeDp,
                range = 36f..80f,
                suffix = "dp",
                onValueChange = { CynthiaSearchFabCustomization.setSize(it) }
            )

            // Corner radius slider
            SliderRow(
                label = "Corner",
                value = searchCustom.cornerRadiusDp,
                range = 0f..50f,
                suffix = "dp",
                onValueChange = { CynthiaSearchFabCustomization.setCornerRadius(it) }
            )

            // Shape picker
            ShapePicker(
                selected = searchCustom.shape,
                onSelected = { CynthiaSearchFabCustomization.setShape(it) }
            )

            // Reset button
            ResetButton {
                CynthiaSearchFabCustomization.reset()
            }
        }
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Reset to Defaults",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            Text(
                text = "${value.toInt()}$suffix",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.6f),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
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
            fontSize = 12.sp
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
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
