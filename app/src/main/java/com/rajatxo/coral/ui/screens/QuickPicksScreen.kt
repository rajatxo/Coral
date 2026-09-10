package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.theme.NyghtSerifFamily
import com.rajatxo.coral.ui.theme.PlayfairItalicFamily
import com.rajatxo.coral.util.extractPalette

/**
 * Quick Picks Screen — Editorial "hero card" design.
 *
 * Inspired by Rajat's reference video: a hero album-art image at top with
 * a diagonal cut at the bottom edge, and a text area below with bold
 * typography. Only 5 picks shown on this screen. A "VIEW ALL" button
 * opens a full-screen page showing all songs (hides the nav rail).
 *
 * Layout (per pick):
 *   Column(fillMaxSize, bg = #0A0E1A) {
 *     Hero image (45% of screen, diagonal bottom cut)
 *       - Album art, ContentScale.Crop
 *       - Status bar padding at top
 *       - Pagination dots top-right (current pick / total)
 *     Text area (55% of screen)
 *       - Tag pill: "QUICK PICKS" (small gray pill)
 *       - Title: song.title in Playfair Italic Bold, large
 *       - Sub-headline: ▶ artist (with coral play icon)
 *       - Description: album name, muted
 *       - VIEW ALL button (black bg, white text, "VIEW ALL →")
 *         -> Triggers full-screen transition (handled by parent)
 *   }
 *
 * Interactions:
 *   - Swipe left/right -> switch between 5 picks
 *   - Tap "VIEW ALL" -> parent opens full-screen page
 *   - Tap hero image -> plays the song
 *
 * Background contract:
 *   This screen paints its own bg (#0A0E1A). The bg color is also
 *   hoisted to HomeScreen so the nav rail area matches.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickPicksScreen(
    songs: List<Song>,
    currentSongId: Long?,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit = {},
    onBgColorChange: (Color) -> Unit = {},
    onViewAllClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var isRandomMode by remember { mutableStateOf(false) }

    // --- Limited picks (only 5 shown on this first screen) ---
    val limitedPicks = remember(songs, currentSongId, isRandomMode) {
        if (songs.isEmpty()) return@remember emptyList()

        if (isRandomMode) {
            songs.shuffled().take(5)
        } else {
            val currentSong = songs.firstOrNull { it.id == currentSongId }
            if (currentSong != null) {
                val sameArtist = songs.filter {
                    it.artist == currentSong.artist && it.id != currentSong.id
                }
                val sameAlbum = songs.filter {
                    it.album == currentSong.album && it.id != currentSong.id &&
                    it.id !in sameArtist.map { s -> s.id }
                }
                val related = (listOf(currentSong) + sameArtist + sameAlbum).distinct().take(5)
                if (related.size < 3) {
                    val fillers = songs.filter { it.id !in related.map { s -> s.id } }
                        .shuffled()
                        .take(5 - related.size)
                    (related + fillers).distinct()
                } else {
                    related
                }
            } else {
                songs.shuffled().take(5)
            }
        }
    }

    // --- Pager state ---
    val pagerState = rememberPagerState(pageCount = { limitedPicks.size })
    val currentPage = pagerState.currentPage

    // --- Bg color (hoisted to parent) — deep navy ---
    var currentBgColor by remember { mutableStateOf(Color(0xFF0A0E1A)) }
    LaunchedEffect(currentBgColor) { onBgColorChange(currentBgColor) }
    LaunchedEffect(currentPage, limitedPicks) {
        val song = limitedPicks.getOrNull(currentPage)
        if (song?.albumArtUri != null) {
            extractPalette(context, song.albumArtUri)?.let { palette ->
                // Blend album color with deep navy for editorial mood
                currentBgColor = blendWithNavy(palette.primary, 0.65f)
            }
        } else {
            currentBgColor = Color(0xFF0A0E1A)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentBgColor)
    ) {
        if (limitedPicks.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val song = limitedPicks[page]
                EditorialPickCard(
                    song = song,
                    pickNumber = page + 1,
                    totalPicks = limitedPicks.size,
                    onPlayClick = { onSongClick(song) },
                    onViewAllClick = onViewAllClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "🎵", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No songs found",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * A single editorial pick card — hero image with diagonal cut + text area.
 *
 * Hero image: top 50% of screen with a diagonal bottom edge
 *   - Left bottom corner: 40% down (image bleeds less on left)
 *   - Right bottom corner: 55% down (image bleeds more on right)
 *   - Creates a dynamic slanting edge
 *
 * Text area: bottom 50% with:
 *   - Tag pill "QUICK PICKS"
 *   - Title (Playfair Italic Bold, large)
 *   - Artist with coral play icon
 *   - Album (muted)
 *   - VIEW ALL button (black bg, white text)
 */
@Composable
private fun EditorialPickCard(
    song: Song,
    pickNumber: Int,
    totalPicks: Int,
    onPlayClick: () -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        // ============================================================
        // HERO IMAGE (top, with diagonal cut)
        // ============================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)  // takes 55% of card height
                .statusBarsPadding()
                .clip(DiagonalCutShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayClick
                )
        ) {
            // Album art
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .crossfade(400)
                        .build(),
                    contentDescription = "Album art for ${song.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎵", fontSize = 56.sp)
                }
            }

            // Pagination dots (top right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 1..totalPicks) {
                    val isActive = i == pickNumber
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }

        // ============================================================
        // TEXT AREA (bottom 45%)
        // ============================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
                    .navigationBarsPadding()
        ) {
            // Tag pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "QUICK PICKS",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title (Playfair Italic Bold, large)
            Text(
                text = song.title.uppercase(),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlayfairItalicFamily,
                lineHeight = 36.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-headline: ▶ artist (with coral play triangle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "▶",
                    color = Color(0xFFFF6B6B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = song.artist,
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = NyghtSerifFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description: album name (muted)
            Text(
                text = "From the album · ${song.album}",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.weight(1f))

            // VIEW ALL button (primary, white bg, black text)
            // Tapping this opens the full-screen page with all songs.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onViewAllClick
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "VIEW ALL",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "→",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Custom Shape: diagonal cut on the bottom edge.
 *
 * Top-left: (0, 0)
 * Top-right: (w, 0)
 * Bottom-right: (w, h * 1.0)  <- full height on right
 * Bottom-left: (0, h * 0.72)   <- 72% height on left (cut goes UP to the left)
 *
 * This creates a diagonal bottom edge going from top-left low to bottom-right
 * high. The image bleeds further down on the right side than on the left.
 */
private val DiagonalCutShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 0f)                    // top-left
            lineTo(w, 0f)                      // top-right
            lineTo(w, h)                      // bottom-right (full height)
            lineTo(0f, h * 0.72f)             // bottom-left (72% height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Blend a color with deep navy (#0A0E1A) for editorial mood.
 * @param color The source color (e.g., album palette primary)
 * @param navyWeight 0..1 — how much navy to mix in (1 = pure navy)
 */
private fun blendWithNavy(color: Color, navyWeight: Float): Color {
    val navy = Color(0xFF0A0E1A)
    val w = navyWeight.coerceIn(0f, 1f)
    return Color(
        red = navy.red * w + color.red * (1 - w),
        green = navy.green * w + color.green * (1 - w),
        blue = navy.blue * w + color.blue * (1 - w),
        alpha = 1f
    )
}
