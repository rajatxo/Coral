package com.rajatxo.coral.domain.model

import android.net.Uri

/**
 * Represents a single audio track on the device.
 *
 * NOTE: This is NOT @Serializable because Android Uri isn't
 * serializable. The cache layer (SongCache) uses a separate SongJson
 * wrapper class to convert Uris to/from strings for disk persistence.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val uri: Uri,
    val albumArtUri: Uri?
)
