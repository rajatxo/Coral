package com.rajatxo.coral.data.model

import kotlinx.serialization.Serializable

/**
 * A user-created playlist.
 *
 * Stored as JSON in the app's internal storage. Each playlist keeps its
 * ordered list of songIds (referencing MediaStore.Audio.Media._ID).
 *
 * @param id           Stable identifier (epoch millis at creation time).
 * @param name         Display name (1-100 chars).
 * @param songIds      Ordered list of MediaStore song IDs in this playlist.
 * @param createdAtMs  Creation timestamp (epoch millis).
 * @param coverUri    Optional custom cover image URI. If null, the UI will
 *                     build a collage from the first few songs' album art.
 */
@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val coverUri: String? = null
)

/**
 * Persisted set of favorite song IDs.
 *
 * Stored as a simple list — favorites are queried by ID, so a Set makes
 * sense in memory but serializes as a List for JSON portability.
 */
@Serializable
data class Favorites(
    val songIds: Set<Long> = emptySet()
)
