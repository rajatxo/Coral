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
    val albumArtUri: Uri?,
    // Unix epoch seconds when the file was added to the device's MediaStore.
    // Used to sort the "Recent" row by most-recently-added (newest first).
    // Default 0L for backward-compat with cache entries saved before this
    // field existed (SongCache/SongJson simply default to 0L on read).
    val dateAdded: Long = 0L
)
