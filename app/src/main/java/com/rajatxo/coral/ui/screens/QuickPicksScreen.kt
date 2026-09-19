package com.rajatxo.coral.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import com.rajatxo.coral.ui.theme.QuirkFontFamily
import com.rajatxo.coral.util.CoralPalette
import com.rajatxo.coral.util.PaletteCache
import com.rajatxo.coral.util.extractPalette
import kotlin.random.Random

/**
 * QuickPicksScreen — "Editorial Gallery" edition.
 *
 * Light/dark theme-aware. Asymmetric hero grid + horizontal carousels.
 * Songs are randomized on each app launch. Carousels are infinite
 * (repeat the song list so you can swipe forever).
 */
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    currentSongArt: android.net.Uri? = null,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // ─── Current song's palette → dark gradient background ──────────
    // The background is a dark gradient using the current song's palette
    // colors. If no song is playing, use a default dark palette.
    var palette by remember { mutableStateOf(CoralPalette.Default) }
    LaunchedEffect(currentSongArt) {
        if (currentSongArt != null) {
            PaletteCache.get(currentSongArt)?.let { palette = it }
            extractPalette(context, currentSongArt)?.let {
                palette = it
                PaletteCache.put(currentSongArt, it)
            }
        }
    }
    // Animate the gradient colors smoothly when the song changes
    val animatedTop by animateColorAsState(
        targetValue = palette.primary.copy(alpha = 0.6f),
        animationSpec = tween(800), label = "bgTop"
    )
    val animatedMid by animateColorAsState(
        targetValue = palette.secondary.copy(alpha = 0.8f),
        animationSpec = tween(800), label = "bgMid"
    )
    val animatedBottom by animateColorAsState(
        targetValue = Color(0xFF05050A),  // near-black at the bottom
        animationSpec = tween(800), label = "bgBottom"
    )

    // Dark text colors (always white on the dark gradient)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)

    // ─── Random seed — changes on every app launch ─────────────────
    // This ensures the song selection is different each time the user
    // opens the app. The seed is remembered for the lifetime of this
    // composable (which is tied to the app session).
    val launchSeed = remember { Random.nextInt() }

    // ─── Prepare song groups (randomized per launch) ────────────────
    // BUG FIX: previously used remember(songs, currentSongId) which
    // didn't recompute when songs loaded async after the screen was
    // already shown. Now we key on songs.size so it recomputes when
    // songs actually arrive. Also use launchSeed for randomization so
    // the selection changes on every app open.
    val heroSongs = remember(songs.size, currentSongId, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else {
            val current = songs.firstOrNull { it.id == currentSongId }
            val pool = songs.shuffled(Random(launchSeed))
            if (current != null) {
                listOf(current, pool.firstOrNull { it.id != current.id } ?: songs.first())
            } else {
                pool.take(2)
            }
        }
    }
    val recentSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 1)).take(10)
    }
    val moreSongs = remember(songs.size, launchSeed) {
        if (songs.isEmpty()) emptyList()
        else songs.shuffled(Random(launchSeed + 2)).take(10)
    }

    // Infinite-repeat versions for the carousels (repeat 10x so you
    // can swipe essentially forever)
    val infiniteRecent = remember(recentSongs) {
        if (recentSongs.isEmpty()) emptyList() else List(10) { recentSongs }.flatten()
    }
    val infiniteMore = remember(moreSongs) {
        if (moreSongs.isEmpty()) emptyList() else List(10) { moreSongs }.flatten()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(animatedTop, animatedMid, animatedBottom)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 200.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // ═══ Header (no shuffle button — moved to floating FAB) ═══
            item {
                Text(
                    text = "Quick picks",
                    color = textPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = QuirkFontFamily
                )
            }

            // ═══ Hero grid ═══
            item {
                if (heroSongs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (heroSongs.size >= 1) {
                            EditorialCard(
                                song = heroSongs[0],
                                isCurrent = heroSongs[0].id == currentSongId,
                                onClick = { onSongClick(heroSongs[0]) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        if (heroSongs.size >= 2) {
                            EditorialCard(
                                song = heroSongs[1],
                                isCurrent = heroSongs[1].id == currentSongId,
                                onClick = { onSongClick(heroSongs[1]) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(0.85f)
                                    .align(Alignment.Bottom)
                            )
                        }
                    }
                }
            }

            // ═══ Recent ═══
            item {
                SectionHeader(
                    title = "Recent",
                    count = recentSongs.size,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(infiniteRecent) { song ->
                        SquareCard(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            onClick = { onSongClick(song) },
                            modifier = Modifier.size(140.dp)
                        )
                    }
                }
            }

            // ═══ More ═══
            item {
                SectionHeader(
                    title = "More picks",
                    count = moreSongs.size,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(infiniteMore) { song ->
                        LandscapeCard(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            onClick = { onSongClick(song) },
                            modifier = Modifier
                                .width(220.dp)
                                .height(140.dp)
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// EDITORIAL CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun EditorialCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 0.4f
                    )
                )
        )

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "NOW",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SQUARE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SquareCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        startY = 0.5f
                    )
                )
        )

        Text(
            text = song.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF007AFF))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// LANDSCAPE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LandscapeCard(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CoralIcons.Music,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        startY = 0.4f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SECTION HEADER
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count",
                color = textSecondary,
                fontSize = 14.sp,
                fontFamily = CalSansFamily
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = CoralIcons.ChevronRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
