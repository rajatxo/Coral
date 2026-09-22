package com.rajatxo.coral.domain.model

import android.net.Uri

/**
 * Represents a single audio track on the device.
 *
 * NOTE: This is NOT @Serializable because Android Uri isn't
 * serializable. The cache layer (SongCache) uses a separate SongJson
 * wrapper class to convert Uris to/from strings for disk persistence.
 *
 * [format] is the audio codec/quality label used by the format capsules
 * on the Songs screen. Detected by MusicScanner via MediaExtractor.
 * Examples: "FLAC", "M4A", "MP3", "WAV", "OGG", "AAC", "Atmos".
 * "Atmos" is reserved for E-AC-3 JOC (Dolby Atmos) — those songs get
 * their own capsule, separate from regular M4A.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val uri: Uri,
    val albumArtUri: Uri?,
    // Unix epoch seconds when the file was added to the device's MediaStore.
    // Used to sort the "Recent" row by most-recently-added (newest first).
    // Default 0L for backward-compat with cache entries saved before this
    // field existed (SongCache/SongJson simply defaults to 0L on read).
    val dateAdded: Long = 0L,
    // Audio format/quality label — used by the format capsules on the
    // Songs screen to filter songs by codec/quality. Default empty
    // string for backward-compat with old caches (will be detected on
    // the next scan).
    val format: String = ""
)

