package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * Settings tab — real (read-only for now) settings surface.
 *
 * Phase 6/7 will wire these to actual SharedPreferences / DataStore:
 *  - Theme picker (system / dark / true black AMOLED)
 *  - Audio: skip on error, replay gain, crossfade duration
 *  - Premium: equalizer, sleep timer, lyrics provider
 *  - About: version, license, open-source credits
 *
 * For now we just render the rows so the tab looks intentional and Phase 7
 * can replace each "—" with a real control.
 */
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 16.dp)) {
            Text(
                text = "Settings",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

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
                value = "Off"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSection(title = "Playback") {
            SettingsRow(
                icon = CoralIcons.Play,
                title = "Crossfade",
                subtitle = "Smooth transition between songs",
                value = "Off"
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

        SettingsSection(title = "Premium") {
            SettingsRow(
                icon = CoralIcons.Music,
                title = "Equalizer",
                subtitle = "Unlock with Coral Premium",
                value = "🔒"
            )
            SettingsRow(
                icon = CoralIcons.Music,
                title = "Sleep timer",
                subtitle = "Pause playback after a set time",
                value = "🔒"
            )
            SettingsRow(
                icon = CoralIcons.ListMusic,
                title = "Lyrics provider",
                subtitle = "LrcLib (free) or Musixmatch (premium)",
                value = "Auto"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsSection(title = "About") {
            SettingsRow(
                icon = CoralIcons.Music,
                title = "Version",
                subtitle = "Coral music player",
                value = "1.0.0"
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
                .background(Color(0xFF141414))
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
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1F1F1F)),
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
