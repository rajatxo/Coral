package com.rajatxo.coral.audio

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CrossfadeVisualState — shared state between the crossfade controller
 * (service side) and the UI (CoralPlayer side).
 *
 * During a crossfade, the controller broadcasts:
 * - isActive: true while a visual crossfade is in progress
 * - progress: 0.0 → 1.0 (same value as the audio fade)
 * - incomingArtUri: the next song's album art URI
 * - incomingTitle/Artist/Album: the next song's metadata
 *
 * CoralPlayer reads this and renders dual-layer art + dual-layer blur
 * with a smart dissolve effect synced to the same progress.
 */
object CrossfadeVisualState {

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _incomingArtUri = MutableStateFlow<Uri?>(null)
    val incomingArtUri: StateFlow<Uri?> = _incomingArtUri.asStateFlow()

    private val _incomingTitle = MutableStateFlow("")
    val incomingTitle: StateFlow<String> = _incomingTitle.asStateFlow()

    private val _incomingArtist = MutableStateFlow("")
    val incomingArtist: StateFlow<String> = _incomingArtist.asStateFlow()

    private val _incomingAlbum = MutableStateFlow<String?>(null)
    val incomingAlbum: StateFlow<String?> = _incomingAlbum.asStateFlow()

    /** Called by SimpleCrossfadeController when a crossfade begins. */
    fun beginTransition(
        artUri: Uri?,
        title: String,
        artist: String,
        album: String?,
    ) {
        _incomingArtUri.value = artUri
        _incomingTitle.value = title
        _incomingArtist.value = artist
        _incomingAlbum.value = album
        _progress.value = 0f
        _isActive.value = true
    }

    /** Called every 8ms during the fade with the current progress 0→1. */
    fun updateProgress(progress: Float) {
        _progress.value = progress
    }

    /** Called when the crossfade is complete. */
    fun endTransition() {
        _isActive.value = false
        _progress.value = 1f
    }
}
