package com.rajatxo.coral.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rajatxo.coral.domain.model.Song

/**
 * QuickPicksScreen — currently empty.
 *
 * All previous experiments (CoverFlowArc cards, 3D depth figure, ML Kit
 * subject segmentation, gyroscope parallax, clock) have been removed.
 *
 * The screen now shows nothing — just the app's dark background. The
 * nav bar, search FAB, settings button, and mini player (when playing)
 * still render on top from HomeScreen.
 *
 * Next step: decide what to build here. Options discussed:
 * - 3D avatar character (needs a 3D artist + GLB model file)
 * - AI subject cutout with real depth (needs ML Kit or U-2-Net TFLite)
 * - Something else entirely
 *
 * For now: blank slate.
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))  // matches the app's dark base
            .statusBarsPadding()
    )
}
