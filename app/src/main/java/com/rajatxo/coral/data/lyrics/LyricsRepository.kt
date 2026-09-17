package com.rajatxo.coral.data.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder

/**
 * Fetches lyrics from LrcLib (https://lrclib.net) and caches them on disk.
 *
 * LrcLib API:
 *  - Free, no auth required.
 *  - Endpoint: GET https://lrclib.net/api/get
 *  - Query params: track_name, artist_name, album_name (optional), duration (seconds)
 *  - Returns JSON with:
 *      id, trackName, artistName, albumName, duration,
 *      plainLyrics  (unsynced text or null),
 *      syncedLyrics (LRC format with [mm:ss.xx] timestamps or null)
 *
 * Cache strategy:
 *  - Each fetched lyric is stored as JSON in internal storage at
 *    lyrics/<trackName>_<artistName>.json
 *  - On next play, the cache is hit first; if missing or older than 30 days,
 *    we re-fetch.
 *
 * Error handling:
 *  - Network failure → return cached version if available, else null.
 *  - 404 from LrcLib → return null (no lyrics for this track).
 *  - Parse failure → return null.
 *
 * All operations are on Dispatchers.IO; this is a suspend function.
 */
class LyricsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val cacheDir: File by lazy {
        File(context.filesDir, "lyrics").apply { if (!exists()) mkdirs() }
    }

    /**
     * Get lyrics for [track] by [artist].
     *
     * @param track       Track name (e.g. "Bohemian Rhapsody").
     * @param artist     Artist name (e.g. "Queen").
     * @param album      Album name (optional, improves match accuracy).
     * @param durationMs Track duration in ms (optional, improves match accuracy).
     * @return [Lyric], or null if not found / fetch failed.
     */
    suspend fun getLyrics(
        track: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null
    ): Lyric? = withContext(Dispatchers.IO) {
        if (track.isBlank() || artist.isBlank()) return@withContext null

        val cacheKey = cacheKey(track, artist)
        val cacheFile = File(cacheDir, "$cacheKey.json")

        // Read from cache only (no network fetching)
        val cached = readCache(cacheFile)
        cached
    }

    /**
     * Clear cached lyrics for a track (used when re-importing).
     */
    suspend fun refreshLyrics(
        track: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null
    ): Lyric? = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "${cacheKey(track, artist)}.json")
        if (cacheFile.exists()) cacheFile.delete()
        null
    }

    /**
     * Read previously imported lyrics from the cache (no network call).
     */
    suspend fun getCachedLyrics(
        track: String,
        artist: String
    ): Lyric? = withContext(Dispatchers.IO) {
        if (track.isBlank() || artist.isBlank()) return@withContext null
        val cacheFile = File(cacheDir, "${cacheKey(track, artist)}.json")
        readCache(cacheFile)
    }

    /**
     * Extract embedded lyrics from the audio file at [uri] using
     * [MediaMetadataRetriever].
     *
     * Android's MediaMetadataRetriever exposes a single (hidden) key for
     * lyrics: METADATA_KEY_LYRICS = 19. We reference it by integer value
     * because the constant is annotated @hide in the SDK and not directly
     * resolvable from Kotlin.
     *
     * For MP3 files this typically maps to the ID3 UNSYNCEDLYRICS frame
     * (plain text) or SYNCEDLYRICS frame (LRC-formatted), depending on
     * what the file's tagger wrote. For FLAC/M4A, it surfaces the LYRICS
     * Vorbis comment / MOV box. The returned text may be plain or LRC —
     * the caller decides how to parse it via [LrcParser].
     *
     * Returns the raw lyric text if found, null otherwise.
     * Safe to call from any thread — dispatches to IO internally.
     */
    suspend fun getEmbeddedLyrics(uri: Uri): String? = withContext(Dispatchers.IO) {
        if (uri == Uri.EMPTY) return@withContext null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            // Try ALL metadata keys from 0 to 35. Different devices/Android
            // versions store lyrics under different key numbers.
            // Known: 30=LYRICS(API33+), 19=hidden LYRICS, 15=WRITER,
            //         20=COMPILATION, 23=NUM_TRACKS etc.
            // We try them all and check if the value looks like lyrics.
            for (key in 0..35) {
                try {
                    val value = retriever.extractMetadata(key)
                    if (!value.isNullOrBlank() && value.length > 10) {
                        // Check if it contains LRC timestamps or is multi-line text
                        if (value.contains("[") && Regex("""\[\d{1,2}:\d{2}""").containsMatchIn(value)) {
                            // Has LRC timestamps — definitely lyrics
                            return@withContext value
                        }
                        if (value.contains("<tt") || value.contains("<span")) {
                            // TTML — definitely lyrics
                            return@withContext value
                        }
                        val lineCount = value.lines().filter { it.isNotBlank() }.size
                        if (lineCount >= 4 && value.length > 50) {
                            // Multi-line text — likely lyrics
                            return@withContext value
                        }
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }

        null
    }

    /**
     * Heuristic: check if a string looks like actual lyrics (not a title/artist).
     */
    private fun looksLikeLyrics(text: String): Boolean {
        if (text.length < 10) return false
        // LRC timestamps = definitely lyrics
        if (text.contains("[") && Regex("""\[\d{1,2}:\d{2}""").containsMatchIn(text)) return true
        // TTML = definitely lyrics
        if (text.contains("<tt") || text.contains("<span")) return true
        // Multiple lines of text = likely lyrics
        val lineCount = text.lines().filter { it.isNotBlank() }.size
        if (lineCount >= 3) return true
        // Single long text = could be lyrics
        if (text.length > 50) return true
        return false
    }

    /**
     * Save a manually imported .lrc file's text as the lyrics for this track.
     * Stored separately from the LrcLib cache (different filename suffix) so
     * that the priority "imported > fetched" can be respected on next open.
     *
     * Returns the parsed [Lyric] on success, null on failure.
     */
    suspend fun saveImportedLrc(
        track: String,
        artist: String,
        lrcText: String
    ): Lyric? = withContext(Dispatchers.IO) {
        if (track.isBlank() || artist.isBlank() || lrcText.isBlank()) return@withContext null
        val lines = LrcParser.parse(lrcText)
        if (lines.isEmpty()) return@withContext null
        val hasWordSync = lines.any { it.hasWordSync }
        val hasTimestamps = lines.any { it.timeMs >= 0 }
        val lyric = Lyric(
            synced = hasTimestamps,
            lines = lines,
            source = LyricSource.MANUAL,
            trackName = track,
            artistName = artist,
            hasWordSync = hasWordSync
        )
        val importedFile = File(cacheDir, "${cacheKey(track, artist)}.imported.json")
        writeCache(importedFile, lyric)
        lyric
    }

    /**
     * Read a previously imported .lrc file from disk (no network call).
     * Used as the middle-priority source when the lyrics sheet first opens
     * (above cached-fetched, below embedded).
     */
    suspend fun getImportedLrc(
        track: String,
        artist: String
    ): Lyric? = withContext(Dispatchers.IO) {
        if (track.isBlank() || artist.isBlank()) return@withContext null
        val importedFile = File(cacheDir, "${cacheKey(track, artist)}.imported.json")
        readCache(importedFile)?.copy(source = LyricSource.MANUAL)
    }

    // ---------- Cache ----------

    private fun readCache(file: File): Lyric? {
        if (!file.exists()) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            val obj: JsonObject = json.parseToJsonElement(text).jsonObject
            val synced = obj["synced"]?.jsonPrimitive?.contentOrNull == "true"
            val lrc = obj["lrc"]?.jsonPrimitive?.contentOrNull
            val plain = obj["plain"]?.jsonPrimitive?.contentOrNull
            val trackName = obj["trackName"]?.jsonPrimitive?.contentOrNull
            val artistName = obj["artistName"]?.jsonPrimitive?.contentOrNull

            if (!lrc.isNullOrBlank()) {
                val lines = LrcParser.parse(lrc)
                val hasWordSync = lines.any { it.hasWordSync }
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = true,
                        lines = lines,
                        source = LyricSource.CACHE,
                        trackName = trackName,
                        artistName = artistName,
                        hasWordSync = hasWordSync
                    )
                }
            }
            if (!plain.isNullOrBlank()) {
                val lines = LrcParser.parse(plain)
                val hasWordSync = lines.any { it.hasWordSync }
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = false,
                        lines = lines,
                        source = LyricSource.CACHE,
                        trackName = trackName,
                        artistName = artistName,
                        hasWordSync = false
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(file: File, lyric: Lyric) {
        try {
            // If lines have word sync, write as Enhanced LRC (preserve word tags)
            val lrcText = if (lyric.synced) {
                lyric.lines.joinToString("\n") { line ->
                    if (line.hasWordSync && line.words != null) {
                        // Enhanced LRC: [mm:ss.xx]<mm:ss.xx>word <mm:ss.xx>word
                        val wordTags = line.words.joinToString("") { word ->
                            "<${formatTime(word.startTime)}>${word.text} "
                        }
                        "[${formatTime(line.timeMs)}]$wordTags"
                    } else {
                        "[${formatTime(line.timeMs)}]${line.text}"
                    }
                }
            } else {
                lyric.lines.joinToString("\n") { it.text }
            }
            val jsonStr = buildString {
                append("{")
                append("\"synced\":${if (lyric.synced) "\"true\"" else "\"false\""},")
                append("\"lrc\":${jsonPrimitiveEscape(lrcText)},")
                append("\"plain\":${jsonPrimitiveEscape(lrcText)},")
                append("\"trackName\":${jsonPrimitiveEscape(lyric.trackName ?: "")},")
                append("\"artistName\":${jsonPrimitiveEscape(lyric.artistName ?: "")}")
                append("}")
            }
            file.writeText(jsonStr, Charsets.UTF_8)
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        val cs = (ms % 1000) / 10
        return String.format(java.util.Locale.US, "%02d:%02d.%02d", mm, ss, cs)
    }

    private fun jsonPrimitiveEscape(s: String): String {
        val escaped = s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun cacheKey(track: String, artist: String): String {
        return (track + "_" + artist)
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8")
}
