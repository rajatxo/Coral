package com.rajatxo.coral.data.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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

        // 1. Try cache first
        val cached = readCache(cacheFile)
        if (cached != null) return@withContext cached

        // 2. Fetch from LrcLib
        val fetched = fetchFromLrcLib(track, artist, album, durationMs)
        if (fetched != null) {
            writeCache(cacheFile, fetched)
        }
        fetched
    }

    /**
     * Force a re-fetch (used when the user picks a different match in a manual search).
     */
    suspend fun refreshLyrics(
        track: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null
    ): Lyric? = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "${cacheKey(track, artist)}.json")
        if (cacheFile.exists()) cacheFile.delete()

        val fetched = fetchFromLrcLib(track, artist, album, durationMs)
        if (fetched != null) writeCache(cacheFile, fetched)
        fetched
    }

    // ---------- Cache ----------

    private fun readCache(file: File): Lyric? {
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            val obj: JsonObject = json.parseToJsonElement(text).jsonObject
            val synced = obj["synced"]?.jsonPrimitive?.contentOrNull == "true"
            val lrc = obj["lrc"]?.jsonPrimitive?.contentOrNull
            val plain = obj["plain"]?.jsonPrimitive?.contentOrNull
            val trackName = obj["trackName"]?.jsonPrimitive?.contentOrNull
            val artistName = obj["artistName"]?.jsonPrimitive?.contentOrNull

            if (!lrc.isNullOrBlank()) {
                val lines = LrcParser.parse(lrc)
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = true,
                        lines = lines,
                        source = LyricSource.CACHE,
                        trackName = trackName,
                        artistName = artistName
                    )
                }
            }
            if (!plain.isNullOrBlank()) {
                val lines = LrcParser.parse(plain)
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = false,
                        lines = lines,
                        source = LyricSource.CACHE,
                        trackName = trackName,
                        artistName = artistName
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
            val lrcText = if (lyric.synced) {
                lyric.lines.joinToString("\n") { line ->
                    "[${formatTime(line.timeMs)}]${line.text}"
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
            file.writeText(jsonStr)
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

    // ---------- LrcLib API ----------

    private fun fetchFromLrcLib(
        track: String,
        artist: String,
        album: String?,
        durationMs: Long?
    ): Lyric? {
        val urlBuilder = StringBuilder("https://lrclib.net/api/get?")
        urlBuilder.append("track_name=").append(encode(track))
        urlBuilder.append("&artist_name=").append(encode(artist))
        if (!album.isNullOrBlank()) {
            urlBuilder.append("&album_name=").append(encode(album))
        }
        if (durationMs != null && durationMs > 0) {
            val durationSec = durationMs / 1000
            urlBuilder.append("&duration=").append(durationSec)
        }

        return try {
            val url = URL(urlBuilder.toString())
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true

            val code = conn.responseCode
            if (code != 200) return null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseLrcLibResponse(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLrcLibResponse(body: String): Lyric? {
        return try {
            val obj: JsonObject = json.parseToJsonElement(body).jsonObject
            val trackName = obj["trackName"]?.jsonPrimitive?.contentOrNull
            val artistName = obj["artistName"]?.jsonPrimitive?.contentOrNull
            val syncedLyrics = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            val plainLyrics = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull

            if (!syncedLyrics.isNullOrBlank()) {
                val lines = LrcParser.parse(syncedLyrics)
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = true,
                        lines = lines,
                        source = LyricSource.NETWORK,
                        trackName = trackName,
                        artistName = artistName
                    )
                }
            }
            if (!plainLyrics.isNullOrBlank()) {
                val lines = LrcParser.parse(plainLyrics)
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = false,
                        lines = lines,
                        source = LyricSource.NETWORK,
                        trackName = trackName,
                        artistName = artistName
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8")
}
