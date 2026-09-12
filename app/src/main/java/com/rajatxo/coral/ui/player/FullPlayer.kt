package com.rajatxo.coral.ui.player

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.rajatxo.coral.data.store.PlaylistStore
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.lyrics.LyricsSheet
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.extractPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FullPlayer(
    mediaController: MediaController?,
    songId: Long?,
    title: String,
    artist: String,
    albumName: String?,
    albumArtUri: Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(albumArtUri) { extractPalette(context, albumArtUri)?.let { palette = it } }

    val immersiveColor = remember(palette) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(palette.primary.toArgb(), hsl)
        hsl[2] = 0.16f
        hsl[1] = if (hsl[1] < 0.06f) 0f else hsl[1].coerceIn(0.32f, 0.54f)
        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
    }

    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaController, isPlaying) {
        while (true) {
            try { mediaController?.let { currentPositionMs = it.currentPosition.coerceAtLeast(0L); durationMs = it.duration.coerceAtLeast(0L) } } catch (_: Exception) { }
            delay(if (isPlaying) 200L else 1000L)
        }
    }

    val favorites by PlaylistStore.favorites.collectAsState()
    val isFavorite = songId != null && songId in favorites.songIds
    var showLyrics by remember { mutableStateOf(false) }

    val wheelRotation = remember { Animatable(0f) }
    fun spinWheel(direction: Int) {
        scope.launch {
            wheelRotation.animateTo(wheelRotation.value + direction * 120f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(immersiveColor)) {
        if (albumArtUri != null) {
            AsyncImage(model = albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

        // ─────────────────────────────────────────────────────────────
        // STEP 1 · Background arc
        // A single, calm, architectural curve — no more saw-tooth bumps.
        // The dark panel rises gently to a peak in the middle, like the
        // underside of a pressed-steel cover. One cubic Bézier, done.
        // ─────────────────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize(), contentDescription = "background") {
            val w = size.width
            val h = size.height
            val arcY = h * 0.58f
            val peak = w * 0.045f          // how high the arch rises in the middle
            val p = Path().apply {
                moveTo(0f, h)
                lineTo(0f, arcY)
                cubicTo(
                    w * 0.28f, arcY - peak * 0.95f,
                    w * 0.72f, arcY - peak * 0.95f,
                    w, arcY
                )
                lineTo(w, h)
                close()
            }
            drawPath(p, immersiveColor)
            // A 1px hairline along the arch to crisp the seam.
            drawPath(
                p,
                color = Color.White.copy(alpha = 0.06f),
                style = Stroke(width = 1f)
            )
        }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(imageVector = CoralIcons.ChevronDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text("NOW PLAYING", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { showMoreMenu = true }, contentAlignment = Alignment.Center) {
                        Icon(imageVector = CoralIcons.MoreVertical, contentDescription = "More", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1A1A1A))) {
                        Row(modifier = Modifier.clickable { showMoreMenu = false; songId?.let { onAddToPlaylist(it) } }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = CoralIcons.Heart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(text = "Add to playlist", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, fontFamily = PlayfairItalicFamily, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.dp))
                Text(text = artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontFamily = NyghtSerifFamily, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
            }

            // ─────────────────────────────────────────────────────────
            // STEP 2 · The mechanical pulley
            // 260dp of cast-iron weight. 60 gear teeth around the rim,
            // precision tick marks inside, 4 spokes through the hub,
            // bolt holes, and a real knob with a stem — not a 6dp dot.
            // ─────────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                val dragModifier = Modifier.size(260.dp).pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {},
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.x > 30f) { spinWheel(-1); onPrevClick() }
                            else if (dragAmount.x < -30f) { spinWheel(1); onNextClick() }
                        }
                    )
                }
                androidx.compose.foundation.Canvas(
                    modifier = dragModifier,
                    contentDescription = "wheel"
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerR = minOf(size.width, size.height) / 2f - 12f
                    val ringR  = outerR - 7.dp.toPx()        // base of the gear teeth
                    val bandR  = ringR  - 4.dp.toPx()         // outer edge of the metal band
                    val innerR = bandR  - 22.dp.toPx()        // inner edge of the metal band
                    val hubR   = innerR * 0.22f
                    val accent = Color(0xFFFF6B6B)

                    // (a) Drop shadow underneath the whole wheel ──────
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.40f),
                        radius = outerR + 1f,
                        center = Offset(cx, cy + 8f)
                    )

                    // (b) Sixty gear teeth around the rim ────────────
                    // Trapezoidal: wider at the base, narrower at the tip.
                    val toothCount = 60
                    val toothAngle = 360f / toothCount
                    val halfBase = 3.0.dp.toPx()
                    val halfTip  = 1.8.dp.toPx()
                    for (i in 0 until toothCount) {
                        rotate(degrees = i * toothAngle, pivot = Offset(cx, cy)) {
                            val tooth = Path().apply {
                                moveTo(cx - halfBase, cy - ringR)
                                lineTo(cx + halfBase, cy - ringR)
                                lineTo(cx + halfTip,  cy - outerR)
                                lineTo(cx - halfTip,  cy - outerR)
                                close()
                            }
                            drawPath(tooth, Color(0xFF262626))
                        }
                    }

                    // (c) Outer metal band — linear gradient gives it a milled-steel feel
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF3D3D3D),   // top-left highlight
                                Color(0xFF2A2A2A),
                                Color(0xFF181818),
                                Color(0xFF0E0E0E)    // bottom-right shadow
                            ),
                            start = Offset(cx - bandR, cy - bandR),
                            end   = Offset(cx + bandR, cy + bandR)
                        ),
                        radius = bandR,
                        center = Offset(cx, cy)
                    )

                    // (d) Precision tick marks — 60 ticks, 12 major ──
                    // This is what separates a real instrument from a
                    // loading spinner.
                    for (i in 0 until 60) {
                        val isMajor  = i % 5 == 0
                        val tickLen  = if (isMajor) 9.dp.toPx() else 4.dp.toPx()
                        val tickA    = if (isMajor) 0.55f else 0.22f
                        val tickW    = if (isMajor) 1.6f else 0.9f
                        rotate(degrees = i * toothAngle, pivot = Offset(cx, cy)) {
                            drawLine(
                                color = Color.White.copy(alpha = tickA),
                                start = Offset(cx, cy - bandR + 2f),
                                end   = Offset(cx, cy - bandR + 2f + tickLen),
                                strokeWidth = tickW
                            )
                        }
                    }

                    // (e) Inner ring outline — crispens the inner edge
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = innerR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // (f) Four bolt holes around the inner ring ─────
                    for (i in 0 until 4) {
                        rotate(degrees = i * 90f + 45f, pivot = Offset(cx, cy)) {
                            val boltY = cy - (innerR + bandR) / 2f
                            drawCircle(
                                color = Color(0xFF050505),
                                radius = 3.2.dp.toPx(),
                                center = Offset(cx, boltY)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.12f),
                                radius = 3.2.dp.toPx(),
                                center = Offset(cx, boltY),
                                style = Stroke(width = 0.8f)
                            )
                        }
                    }

                    // (g) Inner well — radial gradient, slightly recessed
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF1E1E1E), Color(0xFF0A0A0A)),
                            center = Offset(cx, cy),
                            radius = innerR
                        ),
                        radius = innerR,
                        center = Offset(cx, cy)
                    )

                    // (h) Rotating assembly: spokes + hub + knob ───────
                    rotate(degrees = wheelRotation.value, pivot = Offset(cx, cy)) {

                        // (h1) Four cross-spokes from hub to inner ring
                        for (i in 0 until 4) {
                            rotate(degrees = i * 90f, pivot = Offset(cx, cy)) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.10f),
                                    start = Offset(cx, cy),
                                    end   = Offset(cx, cy - innerR + 4f),
                                    strokeWidth = 2.2.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.04f),
                                    start = Offset(cx + 1.2f, cy),
                                    end   = Offset(cx + 1.2f, cy - innerR + 4f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }

                        // (h2) Center hub — dark disc with rim
                        drawCircle(
                            color = Color(0xFF161616),
                            radius = hubR,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = hubR,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        // Hub bolt in accent colour
                        drawCircle(
                            color = accent,
                            radius = hubR * 0.32f,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.6f),
                            radius = hubR * 0.12f,
                            center = Offset(cx - hubR * 0.10f, cy - hubR * 0.10f)
                        )

                        // (h3) The knob — sits ON the inner ring at 12 o'clock
                        // before rotation. Real stem + body + highlight, not a dot.
                        val knobR = 11.dp.toPx()
                        val knobY = cy - innerR + knobR * 0.4f
                        val knobX = cx

                        // Stem — connects rim to knob body
                        drawLine(
                            color = accent.copy(alpha = 0.55f),
                            start = Offset(knobX, cy - innerR + 2f),
                            end   = Offset(knobX, knobY),
                            strokeWidth = 2.6.dp.toPx()
                        )

                        // Knob shadow
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.55f),
                            radius = knobR + 1f,
                            center = Offset(knobX + 2f, knobY + 3f)
                        )

                        // Knob body — radial gradient with off-center highlight
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF8A8A),
                                    accent,
                                    accent.copy(alpha = 0.7f)
                                ),
                                center = Offset(knobX - knobR * 0.3f, knobY - knobR * 0.3f),
                                radius = knobR * 1.6f
                            ),
                            radius = knobR,
                            center = Offset(knobX, knobY)
                        )

                        // Knob rim
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f),
                            radius = knobR,
                            center = Offset(knobX, knobY),
                            style = Stroke(width = 1.dp.toPx())
                        )

                        // Knob specular highlight
                        drawCircle(
                            color = Color.White.copy(alpha = 0.65f),
                            radius = knobR * 0.30f,
                            center = Offset(knobX - knobR * 0.32f, knobY - knobR * 0.32f)
                        )
                    }

                    // (i) Static pointer at top — shows where "zero" is ─
                    // A small triangle just above the ring, doesn't rotate.
                    val pointer = Path().apply {
                        val px = cx
                        val py = cy - outerR - 4f
                        moveTo(px - 4f, py - 6f)
                        lineTo(px + 4f, py - 6f)
                        lineTo(px,     py)
                        close()
                    }
                    drawPath(pointer, Color.White.copy(alpha = 0.45f))
                }
            }

            val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val seekModifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(20.dp).pointerInput(durationMs) {
                detectDragGestures(
                    onDragEnd = {},
                    onDrag = { change, _ ->
                        if (durationMs > 0) {
                            val fraction = (change.position.x / 1000f).coerceIn(0f, 1f)
                            onSeek((fraction * durationMs).toLong())
                        }
                    }
                )
            }
            Box(modifier = seekModifier) {
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).align(Alignment.CenterStart).background(Color.White.copy(alpha = 0.15f)))
                Box(modifier = Modifier.fillMaxWidth(progress).height(2.dp).clip(RoundedCornerShape(1.dp)).align(Alignment.CenterStart).background(Color(0xFFFF6B6B)))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatTime(currentPositionMs), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                Text(text = formatTime(durationMs), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).clickable { spinWheel(-1); onPrevClick() }, contentAlignment = Alignment.Center) { Icon(imageVector = CoralIcons.SkipPrev, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFFF6B6B)).clickable { onPlayPauseClick(); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, contentAlignment = Alignment.Center) { Icon(imageVector = if (isPlaying) CoralIcons.Pause else CoralIcons.Play, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(28.dp)) }
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).clickable { spinWheel(1); onNextClick() }, contentAlignment = Alignment.Center) { Icon(imageVector = CoralIcons.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp)) }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.08f)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }, contentAlignment = Alignment.Center) { Icon(imageVector = CoralIcons.Shuffle, contentDescription = "Shuffle", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable { showLyrics = true }, contentAlignment = Alignment.Center) { Icon(imageVector = CoralIcons.Queue, contentDescription = "Lyrics", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable { if (songId != null) { PlaylistStore.toggleFavorite(songId); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }, contentAlignment = Alignment.Center) { Icon(imageVector = if (isFavorite) CoralIcons.HeartFilled else CoralIcons.Heart, contentDescription = "Favorite", tint = if (isFavorite) palette.accent else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
            }
        }

        if (showLyrics) {
            LyricsSheet(trackName = title, artistName = artist, albumName = albumName, durationMs = durationMs, currentPositionMs = currentPositionMs, isPlaying = isPlaying, onDismiss = { showLyrics = false }, onSeek = onSeek)
        }
    }
}

private fun formatTime(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
