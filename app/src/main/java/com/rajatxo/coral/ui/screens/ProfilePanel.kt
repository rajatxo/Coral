package com.rajatxo.coral.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * ★ ProfilePanel — floating glass card for editing the user's name.
 *
 * Opens when tapping the profile icon (top-left of Quick Picks header).
 * Glass morphism: kyant backdrop blur.
 * Tap outside to dismiss.
 *
 * Content:
 *   - "Your name" label
 *   - Text input field (CalSans, white text, underline style)
 *   - "Save" button
 */
@Composable
fun ProfilePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop? = null
) {
    val userName by com.rajatxo.coral.data.prefs.UserName.name.collectAsState()
    var textInput by remember { mutableStateOf(userName) }

    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .width(300.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
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
                                Modifier.background(Color(0xFF1A1A1A).copy(alpha = 0.92f))
                            }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), panelShape)
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your name",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CalSansFamily
                            )
                            // Close button
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
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

                        // Text input field
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (textInput.isEmpty()) {
                                Text(
                                    text = "Enter your name",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 16.sp,
                                    fontFamily = CalSansFamily
                                )
                            }
                            BasicTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontFamily = CalSansFamily
                                ),
                                cursorBrush = SolidColor(Color.White),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Save button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        com.rajatxo.coral.data.prefs.UserName.setName(textInput)
                                        onDismiss()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Save",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CalSansFamily
                            )
                        }
                    }
                }
            }
        }
    }
}
