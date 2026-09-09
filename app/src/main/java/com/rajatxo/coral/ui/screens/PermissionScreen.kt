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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.components.CoralColors
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.ui.theme.VermiglioneFamily
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
            .background(CoralColors.Surface)  // pure black
            .graphicsLayer { alpha = fadeAlpha }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ─────────────────────────────────────────────────────────
            // TOP — Hero typography: "Coral"
            // ─────────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The big italic "C" — Vermiglione, Bold, ~180sp
                Text(
                    text = AnnotatedString("C"),
                    color = Color.White,
                    fontSize = 180.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontFamily = VermiglioneFamily,
                    letterSpacing = 0.sp,
                    lineHeight = 180.sp
                )
                // The structured "ORAL" — Cal Sans SemiBold, ~90sp
                Text(
                    text = "ORAL",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                    letterSpacing = 8.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                // Subtitle
                Text(
                    text = "allow access to begin",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // ─────────────────────────────────────────────────────────
            // MIDDLE — Permission cards
            // ─────────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Card 1: Notifications ---
                PermissionCard(
                    icon = {
                        Icon(
                            imageVector = CoralIcons.Play,  // Bell-ish icon fallback
                            contentDescription = null,
                            tint = if (notifGranted) Color.Black else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    title = "Notifications",
                    subtitle = "For playback controls",
                    isOn = notifGranted,
                    onClick = {
                        if (notifGranted) {
                            openAppSettings()  // already granted → open settings to manage
                        } else {
                            requestNotifications()
                        }
                    }
                )

                // --- Card 2: Music files ---
                PermissionCard(
                    icon = {
                        Icon(
                            imageVector = CoralIcons.ListMusic,
                            contentDescription = null,
                            tint = if (musicGranted) Color.Black else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    title = "Music files",
                    subtitle = "To scan your library",
                    isOn = musicGranted,
                    onClick = {
                        if (musicGranted) {
                            openAppSettings()
                        } else {
                            requestMusic()
                        }
                    }
                )

                // --- Hint (shown briefly when a permission is denied) ---
                AnimatedVisibility(
                    visible = showHint,
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(250))
                ) {
                    Text(
                        text = when (hintTarget) {
                            "notifications" -> "Coral needs notifications to control playback from your lock screen."
                            "music" -> "Coral needs access to your audio files to play them."
                            else -> ""
                        },
                        color = Color(0xFFFF6B6B).copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ─────────────────────────────────────────────────────────
            // BOTTOM — empty space for now (will later hold a "Continue" hint
            // or progress indicator once both toggles are ON)
            // ─────────────────────────────────────────────────────────
            Box(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * A single permission card with icon, title, subtitle, and an iOS-style toggle.
 *
 * When the toggle is OFF: dark gray card, gray toggle
 * When the toggle is ON: coral card with glossy reflection, white toggle
 */
@Composable
private fun PermissionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isOn: Boolean,
    onClick: () -> Unit
) {
    // Animated border color: red flash when denied, back to normal otherwise
    // (Skipped for now — keep it simple)

    val cardBg = if (isOn) {
        // Glossy coral when granted — same style as the wheel's selection capsule
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFF6B6B).copy(alpha = 0.95f),
                Color(0xFFFF6B6B),
                Color(0xFFE55A5A).copy(alpha = 0.92f)
            )
        )
    } else {
        // Dark gray pill when not granted
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1F1F1F),
                Color(0xFF181818)
            )
        )
    }

    val titleColor = if (isOn) Color.Black else Color.White
    val subtitleColor = if (isOn) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f)
    val borderColor = if (isOn) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon in a small circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isOn) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)
                ),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 12.sp
            )
        }

        // iOS-style toggle switch
        IosToggle(isOn = isOn)
    }
}

/**
 * iOS-style toggle switch — pill track with a circular thumb that slides.
 *
 * OFF: gray track, thumb on left
 * ON: coral track, thumb on right (with subtle drop shadow)
 */
@Composable
private fun IosToggle(isOn: Boolean) {
    val thumbOffset by animateDpAsState(
        targetValue = if (isOn) 22.dp else 0.dp,
        animationSpec = tween(200),
        label = "toggleThumb"
    )

    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (isOn) Color.White.copy(alpha = 0.95f) else Color(0xFF3A3A3A)
            )
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        // Thumb (circle that slides)
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isOn) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.85f))
                .align(Alignment.CenterStart)
                .padding(start = thumbOffset)
        )
    }
}
