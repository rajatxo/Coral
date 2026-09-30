package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.rajatxo.coral.domain.model.Song
import com.rajatxo.coral.ui.components.CoralTab
import com.rajatxo.coral.ui.components.TabCapsule
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.screens.SettingsScreen
import com.rajatxo.coral.ui.theme.CalSansFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * CynthiaHomeScreen — the NEW app UI.
 *
 * Uses Haze (dev.chrisbanes.haze) for glass morphism — same library
 * ArchiveTune/BitChord uses. Crash-free, simple:
 *   1. HazeState() — plain remember, no graphics layer
 *   2. .hazeSource(state) on content — marks it as blur source
 *   3. .hazeEffect(state, style) on glass elements — blurs the source
 *
 * NO kyant backdrop, NO layerBackdrop, NO rememberLayerBackdrop.
 * Haze handles everything internally and never crashes.
 */
@androidx.compose.foundation.ExperimentalFoundationApi
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
    // 3 tabs: Quick Picks, Songs, Playlists
    val cynthiaTabs = listOf(CoralTab.QuickPicks, CoralTab.Songs, CoralTab.Playlists)
    var selectedTab by remember { mutableStateOf(CoralTab.QuickPicks) }
    var showSettings by remember { mutableStateOf(false) }

    // Wrap onSongClick to reset mini player (will be used when mini player is added)
    val onSongClickWithReset: (Song) -> Unit = { song -> onSongClick(song) }

    // ★ Haze glass state — ONE per screen. Plain remember, no graphics layer.
    // All glass elements (nav bar, speed dial grid, mini player) read from this.
    val hazeState = remember { HazeState() }

    // Glass style — stronger blur + lighter tint so glass is VISIBLE
    val glassStyle = HazeStyle(
        blurRadius = 30.dp,
        tint = HazeTint(Color.White.copy(alpha = 0.1f)),
        noiseFactor = 0f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── Content Box — marked as hazeSource so glass elements can blur it ───
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
        ) {
            // Tab content
            when (selectedTab) {
                CoralTab.QuickPicks -> com.rajatxo.coral.ui.screens.QuickPicksScreen(
                    songs = songs,
                    currentSongId = currentSongId,
                    currentSongArt = currentSongArt,
                    onSongClick = onSongClickWithReset
                    // NO glassHazeState — hazeEffect too deep inside QuickPicksScreen
                    // to work in Haze 1.6.9. Glass on speed dial grid will be done
                    // via an overlay sibling instead.
                )
                CoralTab.Songs -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Songs",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = CalSansFamily
                    )
                }
                CoralTab.Playlists -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Playlists",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = CalSansFamily
                    )
                }
                else -> {}
            }

            // Top bar: settings gear (top-right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // ★ Glass settings button — hazeEffect
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .hazeEffect(state = hazeState, style = glassStyle)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showSettings = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Nav bar — glass via hazeEffect (but TabCapsule uses kyant backdrop)
            // For now, keep TabCapsule with backdrop=null (no kyant glass).
            // We'll make a haze-based nav bar later.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TabCapsule(
                    tabs = cynthiaTabs,
                    activeTab = selectedTab,
                    onTabSelected = { tab -> selectedTab = tab },
                    backdrop = null  // No kyant glass — will use haze later
                )
            }
        }  // end hazeSource Box

        // Settings overlay — outside hazeSource (sibling)
        if (showSettings) {
            SettingsScreen(
                onBackClick = { showSettings = false },
                onOpenPremium = { showSettings = false },
                onOpenEqualizer = { showSettings = false },
                onOpenSleepTimer = { showSettings = false },
                onOpenFontPicker = { showSettings = false },
                onOpenLyrics = { showSettings = false },
                onOpenSpiralPalette = { showSettings = false }
            )
        }
    }
}
