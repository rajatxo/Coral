package com.rajatxo.coral.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.delay

/**
 * Permission Screen — the first thing the user sees when opening Coral.
 *
 * Replaces the old "Grant Permission" button UI with a premium typographic
 * lock screen. The hero typography renders the app name "Coral" using two
 * fonts: a dramatic Vermiglione italic "C" paired with a structured Cal Sans
 * "ORAL" — a Vogue-masthead-style composition.
 *
 * Two permission cards (Notifications + Music files) sit below, each with an
 * iOS-style toggle. The user must flip BOTH toggles to ON before the app
 * unlocks and proceeds to HomeScreen. There's no skip button — permissions
 * are mandatory for Coral to function.
 *
 * Auto-flow:
 *   - Page appears → both toggles OFF (gray)
 *   - User taps Notifications card → toggle flips to coral, system dialog
 *   - User taps Music files card → toggle flips to coral, system dialog
 *   - Both granted → 800ms delay → fade-out → onPermissionsGranted() called
 *   - If denied → toggle flips back OFF, hint appears
 */
@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    // Track each permission's state independently
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true  // pre-Android 13: notifications granted at install
        )
    }
    var musicGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Hint state: shows when user denies a permission
    var showHint by remember { mutableStateOf(false) }
    var hintTarget by remember { mutableStateOf("") }  // "notifications" or "music"

    // Fade-out animation when both granted
    var fadingOut by remember { mutableStateOf(false) }
    val fadeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (fadingOut) 0f else 1f,
        animationSpec = tween(600),
        label = "permissionFade"
    )

    // When both granted, wait 800ms then trigger fade-out, then call onGranted
    LaunchedEffect(notifGranted, musicGranted) {
        if (notifGranted && musicGranted) {
            delay(800)
            fadingOut = true
            delay(600)
            onPermissionsGranted()
        }
    }

    // --- Permission launchers ---
    // Notifications (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notifGranted = true
        } else {
            showHint = true
            hintTarget = "notifications"
        }
    }

    // Music files
    val musicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            musicGranted = true
        } else {
            showHint = true
            hintTarget = "music"
        }
    }

    /** Request the system permission for notifications. */
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-Android 13: notifications auto-granted at install time
            notifGranted = true
        }
    }

    /** Request the system permission for reading music files. */
    fun requestMusic() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        musicLauncher.launch(perms)
    }

    /** Open the app's system settings page (for "Display over other apps" style flows). */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Cream gradient background: #F6E9C7 → #FFFACD
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF6E9C7),
                        Color(0xFFFFFACD)
                    )
                )
            )
            .graphicsLayer { alpha = fadeAlpha }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // ─────────────────────────────────────────────────────────
            // TOP — "CORAL" word, all same size, Cal Sans, black
            // ─────────────────────────────────────────────────────────
            Text(
                text = "CORAL",
                color = Color.Black,
                fontSize = 64.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // ─────────────────────────────────────────────────────────
            // MIDDLE — Pure black rounded box containing everything
            // ─────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black)  // pure black, no border
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // "Allow access to begin" — Poppins, white
                Text(
                    text = "Allow Access to begin",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                    textAlign = TextAlign.Center
                )

                // --- Two permission cards with red gradient background ---
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PermissionCard(
                        icon = {
                            Icon(
                                imageVector = CoralIcons.Play,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        title = "Notifications",
                        subtitle = "For playback controls",
                        isOn = notifGranted,
                        onClick = {
                            if (notifGranted) openAppSettings()
                            else requestNotifications()
                        }
                    )

                    PermissionCard(
                        icon = {
                            Icon(
                                imageVector = CoralIcons.ListMusic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        title = "Music files",
                        subtitle = "To scan your library",
                        isOn = musicGranted,
                        onClick = {
                            if (musicGranted) openAppSettings()
                            else requestMusic()
                        }
                    )
                }

                // --- Hint (shown briefly when a permission is denied) ---
                AnimatedVisibility(
                    visible = showHint,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(250))
                ) {
                    Text(
                        text = when (hintTarget) {
                            "notifications" -> "Coral needs notifications to control playback."
                            "music" -> "Coral needs access to your audio files."
                            else -> ""
                        },
                        color = Color(0xFFFF6B6B).copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─────────────────────────────────────────────────────────
            // BOTTOM — "we respect your privacy"
            // ─────────────────────────────────────────────────────────
            Text(
                text = "we respect your privacy",
                color = Color.Black.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * A single permission card with icon, title, subtitle, and an iOS-style toggle.
 *
 * Card background: red gradient #ee0039 → #54091b (always, regardless of state).
 * Title/subtitle: always white.
 */
@Composable
private fun PermissionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isOn: Boolean,
    onClick: () -> Unit
) {
    // Red gradient background — always (per user spec)
    val cardBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFee0039),
            Color(0xFF54091b)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        // Title + subtitle (always white)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }

        // iOS-style toggle switch
        IosToggle(isOn = isOn)
    }
}

/**
 * iOS-style toggle switch — pill track with a circular thumb that slides.
 *
 * OFF: dark track, white thumb on left
 * ON: white track, red thumb on right
 *
 * FIX: previous version used align(CenterStart) + padding(start) which
 * doesn't move the thumb. Now using offset() which actually translates
 * the thumb position.
 */
@Composable
private fun IosToggle(isOn: Boolean) {
    val thumbOffset by animateDpAsState(
        targetValue = if (isOn) 20.dp else 0.dp,
        animationSpec = tween(200),
        label = "toggleThumb"
    )

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isOn) Color.White else Color(0xFF3A3A3A)
            )
    ) {
        // Thumb — uses offset() instead of padding() to actually slide
        Box(
            modifier = Modifier
                .size(20.dp)
                .offset(x = thumbOffset + 2.dp, y = 2.dp)
                .clip(CircleShape)
                .background(if (isOn) Color(0xFFee0039) else Color.White.copy(alpha = 0.85f))
        )
    }
}
