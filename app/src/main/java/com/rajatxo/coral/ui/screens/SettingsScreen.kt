package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.premium.PremiumManager
import com.rajatxo.coral.data.prefs.SoundHapticsManager
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Settings tab — real (read-only for now) settings surface.
 *
 * Phase 7 wiring:
 *  - Premium section shows the current premium status + opens PremiumScreen
 *  - Premium-gated features (Equalizer, Sleep timer) show a 🔒 until premium
 *    is unlocked. When unlocked, they open their respective screens.
 *  - The version row in the About section has a hidden long-press trigger:
 *    7 long-presses within 5 seconds toggles premium in debug builds.
 */
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenFontPicker: () -> Unit
) {
    val isPremium by PremiumManager.isPremium.collectAsState()
    val currentFont by com.rajatxo.coral.data.prefs.FontManager.currentFont.collectAsState()
    val hapticsEnabled by SoundHapticsManager.hapticsEnabled.collectAsState()
    val soundsEnabled by SoundHapticsManager.soundsEnabled.collectAsState()
    val soundVolume by SoundHapticsManager.soundVolume.collectAsState()
    var versionTapCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Big title at top-RIGHT (ViTune style) + back button at top-LEFT
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = com.rajatxo.coral.ui.theme.QuirkFontFamily,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 20.dp, top = 16.dp)
            )
            // Back button (top-left, chevron down)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 16.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.ChevronDown,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Premium section — always at the top so the user knows about it
        SettingsSection(title = "Premium") {
            SettingsRow(
                icon = CoralIcons.Heart,
                title = "Coral Premium",
                subtitle = if (isPremium) "Premium active" else "Unlock all features",
                value = if (isPremium) "✓" else "→",
                onClick = onOpenPremium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Premium-gated features
        SettingsSection(title = "Premium features") {
            SettingsRow(
                icon = CoralIcons.Music,
                title = "Equalizer",
                subtitle = if (isPremium) "5-band + presets" else "Premium required",
                value = if (isPremium) "→" else "🔒",
                onClick = if (isPremium) onOpenEqualizer else onOpenPremium
            )
            SettingsRow(
                icon = CoralIcons.SkipNext,
                title = "Sleep timer",
                subtitle = if (isPremium) "5/15/30/60 min or end of song"
                            else "Premium required",
                value = if (isPremium) "→" else "🔒",
                onClick = if (isPremium) onOpenSleepTimer else onOpenPremium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSection(title = "Appearance") {
            SettingsRow(
                icon = CoralIcons.Settings,
                title = "Theme",
                subtitle = "Follow system",
                value = "Auto"
            )
            SettingsRow(
                icon = CoralIcons.Settings,
                title = "True black (AMOLED)",
                subtitle = "Saves battery on OLED screens",
                value = "On"
            )
            // Font picker — opens a separate screen showing all fonts
            // with each name rendered in its own font for preview.
            SettingsRow(
                icon = CoralIcons.Music,
                title = "Font",
                subtitle = "Used everywhere in Coral",
                value = currentFont.displayName,
                onClick = onOpenFontPicker
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Sound and Haptics ---
        // Controls for haptic feedback + sound effects app-wide.
        // Rules: both off → nothing; only haptics → haptics only;
        // only sounds → sounds only at the chosen volume; both → both fire.
        SettingsSection(title = "Sound and Haptics") {
            ToggleRow(
                icon = CoralIcons.Music,
                title = "Haptics",
                subtitle = "Vibration feedback across the app",
                checked = hapticsEnabled,
                onCheckedChange = { SoundHapticsManager.setHapticsEnabled(it) }
            )
            ToggleRow(
                icon = CoralIcons.Music,
                title = "Sounds",
                subtitle = "Tick sound on swipe, scroll, and tab change",
                checked = soundsEnabled,
                onCheckedChange = { SoundHapticsManager.setSoundsEnabled(it) }
            )
            // Volume slider — only shows when sounds are enabled
            if (soundsEnabled) {
                VolumeSliderRow(
                    volume = soundVolume,
                    onVolumeChange = { SoundHapticsManager.setSoundVolume(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSection(title = "Playback") {
            SettingsRow(
                icon = CoralIcons.Play,
                title = "Crossfade",
                subtitle = "Smooth transition between songs" +
                    (if (isPremium) "" else " (Premium)"),
                value = if (isPremium) "Off" else "🔒"
            )
            SettingsRow(
                icon = CoralIcons.SkipNext,
                title = "Skip on error",
                subtitle = "Auto-skip unplayable files",
                value = "On"
            )
            SettingsRow(
                icon = CoralIcons.Music,
                title = "Replay gain",
                subtitle = "Normalise loudness across songs",
                value = "Off"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSection(title = "Lyrics") {
            SettingsRow(
                icon = CoralIcons.ListMusic,
                title = "Lyrics provider",
                subtitle = "LrcLib (free, no auth)",
                value = "Auto"
            )
            SettingsRow(
                icon = CoralIcons.Music,
                title = "Cache lyrics offline",
                subtitle = "Store fetched lyrics for faster playback",
                value = "On"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSection(title = "About") {
            // The version row has the hidden long-press trigger for debug unlock
            VersionRow(
                isPremium = isPremium,
                onTap = {
                    versionTapCount++
                    if (versionTapCount >= 7) {
                        versionTapCount = 0
                        PremiumManager.debugUnlock()
                    }
                },
                onLongPress = {
                    PremiumManager.debugUnlock()
                }
            )
            SettingsRow(
                icon = CoralIcons.Settings,
                title = "Open-source licenses",
                subtitle = "Tap to view full credits",
                value = "→"
            )
        }
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun VersionRow(
    isPremium: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CoralColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CoralIcons.Music,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Version",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isPremium) "1.0.0 (Premium unlocked)" else "1.0.0",
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp
            )
        }
        Text(
            text = if (isPremium) "✓" else "",
            color = Color(0xFFFF6B6B),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = title.uppercase(),
            color = Color(0xFFFF6B6B),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CoralColors.SurfaceVariant)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (onClick != null) mod.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else mod
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CoralColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp
            )
        }
        Text(
            text = value,
            color = Color(0xFFB0B0B0),
            fontSize = 13.sp
        )
    }
}

/**
 * A settings row with a toggle switch instead of a value text.
 * Used for haptics + sounds toggles.
 */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CoralColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFF6B6B),
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}

/**
 * A volume slider row — only shown when sounds are enabled.
 * Slider goes 0..100, value shown to the right.
 */
@Composable
private fun VolumeSliderRow(
    volume: Int,
    onVolumeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$volume%",
                color = Color(0xFFFF6B6B),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        androidx.compose.material3.Slider(
            value = volume.toFloat(),
            onValueChange = { onVolumeChange(it.toInt()) },
            valueRange = 0f..100f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color(0xFFFF6B6B),
                activeTrackColor = Color(0xFFFF6B6B),
                inactiveTrackColor = Color(0xFF333333)
            )
        )
    }
}
