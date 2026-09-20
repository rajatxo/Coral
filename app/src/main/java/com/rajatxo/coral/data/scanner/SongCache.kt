package com.rajatxo.coral.data.scanner

import android.content.Context
import android.net.Uri
import com.rajatxo.coral.domain.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * SongCache — persist the song list to disk so QuickPicksScreen can
 * render INSTANTLY on app launch, without waiting for the MediaStore
 * scan to finish.
 *
 * The "black screen with only section headers" bug:
 *   • On cold start, [MusicScanner.scanMusic] runs in a background
 *     LaunchedEffect and takes 200-500ms to query MediaStore.
 *   • During that window, [QuickPicksScreen] renders with an EMPTY
 *     song list → only the section headers are visible on a black
 *     background (no cards).
 *   • User sees a brief black "loading" state on every app open.
 *
 * The fix:
 *   • After each successful scan, save the song list to a JSON file
 *     in internal storage ([save]).
 *   • On app launch, [load] is called SYNCHRONOUSLY (it's just a
 *     file read + JSON parse, ~5-20ms for a typical library).
 *   • The loaded list populates `songs` IMMEDIATELY → QuickPicksScreen
 *     renders fully populated on the first frame → no black screen.
 *   • The background scan then runs. If the scanned list differs from
 *     the cache, the UI updates. If it's the same, no visible change.
 *
 * The cache is keyed on nothing (single file). The library is small
 * enough (typically <2000 songs) that the JSON file is <500KB and
 * parses in <20ms.
 *
 * Thread safety: [save] is called from background coroutines.
 * [load] is called from the main thread on startup. File reads/writes
 * are atomic enough at the Android filesystem level for this use case
 * (worst case: a corrupt read returns null and we fall back to a
 * fresh scan, which is the original behavior).
 */
object SongCache {

    private const val CACHE_FILE = "song_cache.json"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Synchronously load the cached song list from disk.
     * Call this on the MAIN thread before the first compose render.
     *
     * Returns an empty list if:
     *   • The cache file doesn't exist yet (first launch)
     *   • The cache is corrupt (will be overwritten on next scan)
     *   • The cache is older than [maxAgeMillis] (default: never expire)
     *
     * @param maxAgeMillis Max age of the cache in millis. 0 = never
     *   expire. Pass [System.currentTimeMillis] - 7 days to expire
     *   after a week. Default: never expire (cache is always trusted
     *   and the background scan updates it if needed).
     */
    fun load(context: Context, maxAgeMillis: Long = 0L): List<Song> {
        return try {
            val file = File(context.filesDir, CACHE_FILE)

            // No cache yet (first launch) → return empty
            if (!file.exists()) return emptyList()

            // Check max age (if requested)
            if (maxAgeMillis > 0L) {
                val fileAge = System.currentTimeMillis() - file.lastModified()
                if (fileAge > maxAgeMillis) return emptyList()
            }

            // Read + parse
            val text = file.readText()
            val cached = json.decodeFromString<List<SongJson>>(text)
            cached.map { it.toDomain() }
        } catch (_: Exception) {
            // Corrupt cache, IO error, parse error, etc.
            // Return empty → app falls back to fresh scan.
            emptyList()
        }
    }

    /**
     * Save the song list to disk. Call from a BACKGROUND thread
     * (file writes are blocking).
     *
     * Writes to a temp file first, then atomically renames — so a
     * crash mid-write never leaves a corrupt cache file.
     */
    fun save(context: Context, songs: List<Song>) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            val tempFile = File(context.filesDir, "$CACHE_FILE.tmp")

            val jsonSongs = songs.map { SongJson.fromDomain(it) }
            val text = json.encodeToString(jsonSongs)

            tempFile.writeText(text)
            // Atomic rename: temp → real
            tempFile.renameTo(file)
        } catch (_: Exception) {
            // Best-effort cache. If save fails, the app still works
            // (just no instant load next time).
        }
    }

    /**
     * Clear the cache. Currently unused but useful for a future
     * "refresh library" or "clear cache" button.
     */
    fun clear(context: Context) {
        try {
            File(context.filesDir, CACHE_FILE).delete()
            File(context.filesDir, "$CACHE_FILE.tmp").delete()
        } catch (_: Exception) { }
    }

    // ─── Internal JSON model ──────────────────────────────────────
    // We can't serialize Android Uri directly (it's not @Serializable),
    // so we store Uris as strings and convert.
    @Serializable
    private data class SongJson(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val uri: String,
        val albumArtUri: String?
    ) {
        fun toDomain(): Song = Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = Uri.parse(uri),
            albumArtUri = albumArtUri?.let { Uri.parse(it) }
        )

        companion object {
            fun fromDomain(song: Song): SongJson = SongJson(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                duration = song.duration,
                uri = song.uri.toString(),
                albumArtUri = song.albumArtUri?.toString()
            )
        }
    }
}
