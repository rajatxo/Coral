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
    onOpenPremium: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenFontPicker: () -> Unit
) {
    val isPremium by PremiumManager.isPremium.collectAsState()
    val currentFont by com.rajatxo.coral.data.prefs.FontManager.currentFont.collectAsState()
    var versionTapCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Big title at top-RIGHT (ViTune style)
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = com.rajatxo.coral.ui.theme.QuirkFontFamily,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 20.dp, top = 16.dp)
            )
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
