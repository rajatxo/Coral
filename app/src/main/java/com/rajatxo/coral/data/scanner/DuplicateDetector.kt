package com.rajatxo.coral.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import com.rajatxo.coral.domain.model.Song

/**
 * DuplicateDetector — finds songs in the library that have the same
 * title AND the same artist (case-insensitive, trimmed).
 *
 * Why this exists:
 *   Local music libraries tend to accumulate duplicates over time —
 *   the same song imported twice from different sources, the same
 *   track in two albums, or the same download re-imported after a
 *   device switch. Coral's library scan shows them all, which clutters
 *   the Songs tab.
 *
 * Detection rule:
 *   Two songs are "duplicates" if:
 *     title.lowercase().trim() == title.lowercase().trim()
 *     AND
 *     artist.lowercase().trim() == artist.lowercase().trim()
 *
 *   We intentionally do NOT use the file path or album name in the
 *   match — the user wants to find the SAME SONG by the SAME ARTIST
 *   even if it's in different albums or at different paths.
 *
 * Smart filter (which one to keep):
 *   For each duplicate group, we recommend keeping ONE song (the
 *   "keeper") and marking the rest for deletion (the "dupes").
 *
 *   Keeper priority (in order):
 *     1. Longer duration — full-length track beats radio edit
 *     2. Newer dateAdded — recent import beats old copy
 *     3. Lower id — tiebreaker for stability
 *
 *   The user can override the recommendation in the UI.
 */
object DuplicateDetector {

    /**
     * A group of songs that are duplicates of each other.
     *
     * @property title Normalized title shared by all songs in this group.
     * @property artist Normalized artist name shared by all songs in this group.
     * @property keeper The recommended song to KEEP (longest duration wins).
     * @property dupes The recommended songs to DELETE (everything else).
     */
    data class DuplicateGroup(
        val title: String,
        val artist: String,
        val keeper: Song,
        val dupes: List<Song>
    ) {
        /** All songs in this group, keeper first. */
        val all: List<Song> get() = listOf(keeper) + dupes
    }

    /**
     * Find all duplicate groups in [songs].
     *
     * @return Empty list if no duplicates. Otherwise, one [DuplicateGroup]
     *   per title+artist combo that has more than one song.
     */
    fun findDuplicates(songs: List<Song>): List<DuplicateGroup> {
        if (songs.isEmpty()) return emptyList()

        // Group by normalized (lowercase, trimmed) title + artist
        val groups = songs.groupBy { song ->
            normalizeTitle(song.title) + "|" + normalizeArtist(song.artist)
        }

        return groups.values
            .filter { it.size > 1 }
            .map { group ->
                val keeper = pickKeeper(group)
                val dupes = group.filter { it.id != keeper.id }
                DuplicateGroup(
                    title = group.first().title,  // original case for display
                    artist = group.first().artist,
                    keeper = keeper,
                    dupes = dupes
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    /**
     * Smart filter — pick the song to KEEP. See class doc for priority.
     */
    private fun pickKeeper(songs: List<Song>): Song {
        return songs.sortedWith(
            compareByDescending<Song> { it.duration }       // longest first
                .thenByDescending { it.dateAdded }         // newest first
                .thenBy { it.id }                          // lowest id (stable)
        ).first()
    }

    private fun normalizeTitle(s: String): String =
        s.trim().lowercase()

    private fun normalizeArtist(s: String): String =
        s.trim().lowercase().ifBlank { "unknown_artist" }

    /**
     * Delete a list of songs from the device's internal storage via
     * MediaStore's createDeleteRequest API (Android 10+).
     *
     * This is the SAFE way to delete files on Android 10+ — it shows
     * the user a system confirmation dialog asking them to approve
     * the deletion. The app NEVER actually has direct write access
     * to the files; the system handles it.
     *
     * On Android 9 and below, falls back to direct ContentResolver.delete()
     * which requires WRITE_EXTERNAL_STORAGE permission (declared in manifest
     * for ≤SDK 32, but on ≤SDK 28 it works without further permission).
     *
     * @param context Any context (application or activity).
     * @param songs The songs to delete.
     * @return A [DeleteRequest] — the caller passes this to
     *   [androidx.activity.result.ActivityResultRegistry.launchIntentSender]
     *   (via rememberLauncherForActivityResult + StartIntentSenderForResult).
     *   On Android 9 and below, the deletion happens immediately and
     *   the request is null (no user prompt needed).
     */
    fun buildDeleteRequest(context: Context, songs: List<Song>): DeleteRequest {
        val uris = songs.map { it.uri }
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ — use MediaStore.createDeleteRequest (safe, prompts user)
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
            DeleteRequest(pendingIntent = pendingIntent, uris = uris, requiresUserConfirmation = true)
        } else {
            // Android 9 and below — direct delete via ContentResolver
            uris.forEach { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) { /* best-effort */ }
            }
            DeleteRequest(pendingIntent = null, uris = uris, requiresUserConfirmation = false)
        }
    }

    /**
     * Holder for a delete request. The caller (UI) needs to:
     *   1. Check requiresUserConfirmation
     *   2. If true → launch pendingIntent.intentSender via an
     *      ActivityResultLauncher<StartIntentSenderForResult>
     *   3. If false → deletion already done, just refresh the library
     */
    data class DeleteRequest(
        val pendingIntent: android.app.PendingIntent?,
        val uris: List<Uri>,
        val requiresUserConfirmation: Boolean
    )
}
