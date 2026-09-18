package com.rajatxo.coral.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.R
import com.rajatxo.coral.data.depth.QuickPicksFigureStore
import com.rajatxo.coral.data.depth.rememberGyroscopeTilt
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.SleepTimerCapsule
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * QuickPicksScreen — 3D depth composition with ML Kit subject cutout
 * and gyroscope parallax.
 *
 * LAYOUT (3 layers, back → front):
 *   1. Dark gradient background (fills screen)
 *   2. Clock text (large, centered — the depth reference point)
 *   3. Cut-out subject figure (foreground, shifts with gyroscope)
 *
 * HOW THE 3D EFFECT WORKS:
 * - The subject is extracted from the user's photo via ML Kit Subject
 *   Segmentation (works on ANY photo — people, pets, objects)
 * - The cutout is saved as a transparent PNG and persists across launches
 * - The phone's gyroscope reports the tilt angle in real time
 * - The subject shifts in the direction of the tilt (moves WITH the phone)
 * - The clock shifts in the OPPOSITE direction (moves against the phone)
 * - This creates real parallax — the subject feels closer to the viewer
 *   than the clock, because it moves more when you tilt the phone
 *
 * USER FLOW:
 * - First launch: no figure → shows a "Pick a photo" button
 * - User picks a photo → ML Kit runs (~200-500ms) → cutout appears
 * - The cutout persists → loads instantly on every subsequent launch
 * - Tilt the phone → subject and clock shift in opposite directions
 *
 * The old CoverFlowArc card animation has been COMPLETELY REMOVED.
 */
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ─── Figure store + ML Kit cutout ───────────────────────────────
    val figureStore = remember { QuickPicksFigureStore.get(context) }
    val cutoutBitmap by figureStore.cutoutBitmap.collectAsState()
    val isProcessing by figureStore.isProcessing.collectAsState()

    // ─── Gyroscope parallax ─────────────────────────────────────────
    // tilt.x = -1..1 (left-right), tilt.y = -1..1 (up-down)
    val tilt by rememberGyroscopeTilt()

    // ─── Photo picker ───────────────────────────────────────────────
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                figureStore.processAndSave(uri)
            }
        }
    }

    // ─── Clock (updates every second) ───────────────────────────────
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            kotlinx.coroutines.delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F0F1A)
                    )
                )
            )
    ) {
        // ═══════════════════════════════════════════════════════════════
        // LAYER 2: Clock text (BACKGROUND layer — shifts OPPOSITE to tilt)
        // ═══════════════════════════════════════════════════════════════
        // The clock is behind the subject. It shifts in the opposite
        // direction of the phone tilt, so when you tilt the phone right,
        // the subject moves right (with the phone) and the clock moves
        // left (against the phone). This creates real parallax depth —
        // the subject feels closer to the viewer than the clock.
        val clockShiftX = -tilt.x * 40f  // px, opposite direction
        val clockShiftY = -tilt.y * 25f  // px, opposite direction

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = clockShiftX
                    translationY = clockShiftY
                },
            contentAlignment = Alignment.Center
        ) {
            // Large clock — the depth reference point. The subject
            // occludes this clock, then shifts to reveal it when you tilt.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTime,
                    color = Color.White.copy(alpha = 0.15f),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            blurRadius = 20f
                        )
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "QUICK PICKS",
                    color = Color.White.copy(alpha = 0.08f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = CalSansFamily,
                    letterSpacing = 4.sp
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // LAYER 3: Cut-out subject (FOREGROUND — shifts WITH the tilt)
        // ═══════════════════════════════════════════════════════════════
        // The subject moves in the SAME direction as the phone tilt,
        // making it feel closer to the viewer (real parallax).
        val subjectShiftX = tilt.x * 30f  // px, same direction
        val subjectShiftY = tilt.y * 20f  // px, same direction

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = subjectShiftX
                    translationY = subjectShiftY
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                // Processing — show spinner
                isProcessing -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                // Cutout ready — show the figure
                cutoutBitmap != null -> {
                    Image(
                        bitmap = cutoutBitmap!!.asImageBitmap(),
                        contentDescription = "Quick picks figure",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .graphicsLayer {
                                // Drop shadow for depth
                                shadowElevation = 30f
                            }
                    )
                }
                // No figure yet — show the default rabbit + a "pick photo" hint
                else -> {
                    // Default figure (the rabbit) so the screen isn't empty
                    Image(
                        bitmap = android.graphics.BitmapFactory.decodeResource(
                            context.resources,
                            R.drawable.quick_picks_figure
                        ).asImageBitmap(),
                        contentDescription = "Default figure",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxHeight(0.7f)
                            .graphicsLayer { alpha = 0.4f }
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // LAYER 4: Photo picker button (top-right, below status bar)
        // ═══════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sleep timer capsule (only visible when a timer is active)
            if (capsuleVisible && capsuleRemaining > 0) {
                SleepTimerCapsule(
                    visible = capsuleVisible,
                    remainingMs = capsuleRemaining,
                    onExtend = onExtend,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Photo picker button — lets the user pick any photo from
            // their gallery. ML Kit will extract the subject automatically.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        ))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.HeartPlus,
                    contentDescription = "Pick photo for cutout",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // LAYER 5: "Pick a photo" hint (only when no cutout is set)
        // ═══════════════════════════════════════════════════════════════
        if (cutoutBitmap == null && !isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 160.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "Tap + to pick a photo\nML Kit will extract the subject",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontFamily = CalSansFamily,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** Returns the current time as "HH:mm" */
private fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
