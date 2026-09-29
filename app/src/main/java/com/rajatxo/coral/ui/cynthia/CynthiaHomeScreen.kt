package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * CynthiaHomeScreen — the NEW app UI skeleton.
 *
 * CLEAN STRUCTURE — no UI elements, no copied code. Just the skeleton
 * with all the state + callbacks that the real UI will need. This is
 * designed from the ground up for glass morphism — no limitations.
 *
 * What's here:
 *   - Tab selection state (Quick Picks, Songs, Playlists, Artists, Albums)
 *   - Player state (current song, playing, position)
 *   - Show/hide player, settings, search
 *   - Mini player dismissed state
 *   - Playlist selection
 *
 * What's NOT here (will be added as we build the glass UI):
 *   - No nav bar (we'll build a glass one)
 *   - No mini player (we'll build a glass one)
 *   - No page content (we'll build glass pages)
 *   - No player overlay (we'll build a glass player)
 *   - No search FAB, header, sleep timer, etc.
 *
 * This is the FOUNDATION — everything below can be freely modified
 * for glass morphism without any structural limitations.
 */
@Composable
fun CynthiaHomeScreen(
    songs: List<Song>,
    mediaController: MediaController?,
    currentSongId: Long?,
    currentSongTitle: String?,
    currentSongArtist: String?,
    currentSongAlbum: String?,
    currentSongArt: android.net.Uri?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongClickWithQueue: (Song, List<Song>) -> Unit,
    onMiniPlayerClick: () -> Unit,
    showFullPlayer: Boolean,
    onFullPlayerDismiss: () -> Unit,
    onSongEnded: () -> Unit,
    onRefresh: suspend () -> Unit = {}
) {
    // ═══════════════════════════════════════════════════════════════
    // STATE — all the state the UI will need, no rendering yet
    // ═══════════════════════════════════════════════════════════════

    // Tab selection (5 tabs)
    val enabledTabs by com.rajatxo.coral.data.prefs.NavBarConfig.enabledTabs.collectAsState()
    val defaultTab by com.rajatxo.coral.data.prefs.NavBarConfig.defaultTab.collectAsState()
    var selectedTab by remember { mutableStateOf(defaultTab) }

    // Mini player dismissed state (swipe left/right to dismiss)
    var miniPlayerDismissed by remember { mutableStateOf(false) }

    // Show/hide overlays
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showFullPlayerState by remember { mutableStateOf(showFullPlayer) }

    // Playlist selection (for playlist detail view)
    var selectedPlaylist by remember { mutableStateOf<com.rajatxo.coral.data.model.Playlist?>(null) }

    // Playback position polling (for mini player progress)
    var miniPlayerPositionMs by remember { mutableStateOf(0L) }
    var miniPlayerDurationMs by remember { mutableStateOf(0L) }

    androidx.compose.runtime.LaunchedEffect(mediaController) {
        mediaController?.let { controller ->
            while (true) {
                try {
                    miniPlayerPositionMs = controller.currentPosition.coerceAtLeast(0L)
                    miniPlayerDurationMs = controller.duration.coerceAtLeast(0L)
                } catch (_: Exception) { }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    // Reset mini player dismissed when song changes
    androidx.compose.runtime.LaunchedEffect(currentSongId) {
        if (currentSongId != null && miniPlayerDismissed) {
            miniPlayerDismissed = false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ROOT — single Box. This is where the glass backdrop will live.
    // ONE root LayerBackdrop, all glass elements as siblings inside.
    // ═══════════════════════════════════════════════════════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)  // Pure black base — glass will sample this
    ) {
        // ─── Tab content placeholder ───
        // Each tab is a placeholder for now. We'll build glass pages here.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = selectedTab.name,
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                fontFamily = CalSansFamily
            )
        }

        // TODO: Glass nav bar (will use kyant drawBackdrop, sampling this root)
        // TODO: Glass mini player (will use exportedBackdrop from nav bar)
        // TODO: Glass player overlay (full screen, samples root)
        // TODO: Glass settings screen
        // TODO: Glass search FAB
        // TODO: Glass page content (Quick Picks cards, Songs rows, etc.)
    }
}
