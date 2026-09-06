package com.rajatxo.coral.domain.model

import android.net.Uri

/**
 * Represents a single audio track on the device.
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
