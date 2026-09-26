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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A candidate track returned by LrcLib's /api/search endpoint.
 *
 * Each candidate represents one possible set of lyrics for the song
 * the user is playing. The Lyrics Picker UI shows all candidates so
 * the user can choose the one whose duration best matches their song
 * (some songs have multiple releases — single, album version, radio
 * edit, remaster — each with different timelines).
 *
 * @param id             LrcLib internal track ID.
 * @param trackName      Track name as registered on LrcLib.
 * @param artistName     Artist name as registered on LrcLib.
 * @param albumName      Album name (nullable).
 * @param durationSec    Track duration in SECONDS (note: Lyric uses ms).
 * @param hasSynced      True if syncedLyrics (LRC timestamps) is present.
 * @param hasPlain       True if plainLyrics (unsynced text) is present.
 * @param syncedLyrics   Raw LRC text with timestamps, or null.
 * @param plainLyrics    Raw plain text lyrics, or null.
 */
data class LrcLibCandidate(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationSec: Int,
    val hasSynced: Boolean,
    val hasPlain: Boolean,
    val syncedLyrics: String?,
    val plainLyrics: String?
)

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

    // ---------- Network Fetch (LrcLib → NetEase → KuGou) ----------

    /**
     * Fetch lyrics from network providers. Tries LrcLib first (best sync),
     * then NetEase, then KuGou. Uses duration matching for sync accuracy.
     * Results are cached for offline use.
     *
     * @param track       Song title
     * @param artist      Artist name
     * @param album       Album name (optional, improves LrcLib match)
     * @param durationMs  Song duration in ms (CRITICAL for sync matching)
     * @return Lyric or null if all providers fail
     */
    suspend fun fetchFromNetwork(
        track: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null
    ): Lyric? = withContext(Dispatchers.IO) {
        if (track.isBlank()) return@withContext null

        // 1. LrcLib — best source for synced lyrics, uses duration for matching
        val lrcLibResult = fetchFromLrcLib(track, artist, album, durationMs)
        if (lrcLibResult != null) {
            cacheLyrics(track, artist, lrcLibResult)
            return@withContext lrcLibResult
        }

        // 2. NetEase Cloud Music — large Chinese lyrics database
        val netEaseResult = fetchFromNetEase(track, artist, durationMs)
        if (netEaseResult != null) {
            cacheLyrics(track, artist, netEaseResult)
            return@withContext netEaseResult
        }

        // 3. KuGou — another large lyrics database
        val kuGouResult = fetchFromKuGou(track, artist, durationMs)
        if (kuGouResult != null) {
            cacheLyrics(track, artist, kuGouResult)
            return@withContext kuGouResult
        }

        null
    }

    /**
     * Fetch from LrcLib API.
     * Uses duration parameter for exact timeline matching.
     * https://lrclib.net/api/get?track_name=X&artist_name=Y&duration=Z
     */
    private fun fetchFromLrcLib(
        track: String,
        artist: String,
        album: String?,
        durationMs: Long?
    ): Lyric? {
        return try {
            val urlBuilder = StringBuilder("https://lrclib.net/api/get?")
            urlBuilder.append("track_name=").append(encode(track))
            urlBuilder.append("&artist_name=").append(encode(artist))
            if (!album.isNullOrBlank()) {
                urlBuilder.append("&album_name=").append(encode(album))
            }
            // Duration is CRITICAL — it ensures we get the exact version
            // of the lyrics that matches the user's song timeline
            if (durationMs != null && durationMs > 0) {
                val durationSec = durationMs / 1000
                urlBuilder.append("&duration=").append(durationSec)
            }

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
            val obj: JsonObject = json.parseToJsonElement(body).jsonObject
            val syncedLyrics = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            val plainLyrics = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
            val returnedDuration = obj["duration"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

            // Verify duration matches (within 2 seconds tolerance) for sync accuracy
            if (durationMs != null && returnedDuration != null) {
                val expectedSec = durationMs / 1000
                if (kotlin.math.abs(returnedDuration - expectedSec) > 2) {
                    // Duration mismatch — these lyrics won't sync correctly
                    return null
                }
            }

            if (!syncedLyrics.isNullOrBlank()) {
                val lines = LrcParser.parse(syncedLyrics)
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = true,
                        lines = lines,
                        source = LyricSource.NETWORK,
                        trackName = track,
                        artistName = artist,
                        hasWordSync = lines.any { it.hasWordSync }
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
                        trackName = track,
                        artistName = artist
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Search LrcLib for ALL candidate lyric sets matching a song.
     *
     * Unlike [fetchFromLrcLib] which uses the precise `/api/get` endpoint
     * (returns exactly ONE match by duration), this hits `/api/search`
     * which returns a LIST of candidate tracks. Each candidate has its
     * own duration, synced/plain lyrics, album name, etc.
     *
     * Why this exists: some songs have multiple releases (single,
     * album version, radio edit, remaster, live) and each release can
     * have a different timeline. The auto-fetched lyrics may sync to
     * the wrong version. This method lets the user manually pick the
     * release that matches their local file's duration.
     *
     * Inspired by vivi-music's approach (they sort by duration delta
     * and pick best-match automatically) — but Coral exposes all
     * candidates to the user so they can override the auto-pick.
     *
     * @param track       Song title.
     * @param artist      Artist name.
     * @param album       Album name (optional, narrows results).
     * @return List of [LrcLibCandidate]s, sorted by:
     *           1. Has syncedLyrics (synced first, plain last)
     *           2. Duration delta from [durationMs] ascending
     *         Empty list if no results or network failure.
     */
    suspend fun searchLyricsOnLrcLib(
        track: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null
    ): List<LrcLibCandidate> = withContext(Dispatchers.IO) {
        if (track.isBlank()) return@withContext emptyList()

        // Strategy: try the most specific query first (track + artist + album),
        // then fall back to track + artist, then to track only. Return the
        // first non-empty result. This matches vivi-music's cascading approach.
        val queries = buildList {
            add(Triple(track, artist, album))
            add(Triple(track, artist, null))
            add(Triple(track, null, null))
        }

        for ((qTrack, qArtist, qAlbum) in queries) {
            val results = runSearch(qTrack, qArtist, qAlbum)
            if (results.isNotEmpty()) {
                // Sort: synced first, then by duration delta ascending
                val durationSec = durationMs?.div(1000) ?: -1
                return@withContext results.sortedWith(
                    compareByDescending<LrcLibCandidate> { it.hasSynced }
                        .thenBy { candidate ->
                            if (durationSec <= 0) 0
                            else kotlin.math.abs(candidate.durationSec - durationSec)
                        }
                )
            }
        }

        emptyList()
    }

    /** Raw HTTP call to /api/search. Returns parsed list (may be empty). */
    private fun runSearch(
        track: String,
        artist: String?,
        album: String?
    ): List<LrcLibCandidate> {
        return try {
            val urlBuilder = StringBuilder("https://lrclib.net/api/search?")
            urlBuilder.append("track_name=").append(encode(track))
            if (!artist.isNullOrBlank()) {
                urlBuilder.append("&artist_name=").append(encode(artist))
            }
            if (!album.isNullOrBlank()) {
                urlBuilder.append("&album_name=").append(encode(album))
            }

            val url = URL(urlBuilder.toString())
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true

            if (conn.responseCode != 200) return emptyList()

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val array = json.parseToJsonElement(body).jsonArray

            array.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                    val trackName = obj["trackName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val artistName = obj["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                    val albumName = obj["albumName"]?.jsonPrimitive?.contentOrNull
                    val duration = obj["duration"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0
                    val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                    val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull

                    LrcLibCandidate(
                        id = id,
                        trackName = trackName,
                        artistName = artistName,
                        albumName = albumName,
                        durationSec = duration,
                        hasSynced = !synced.isNullOrBlank(),
                        hasPlain = !plain.isNullOrBlank(),
                        syncedLyrics = synced,
                        plainLyrics = plain
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Convert a [LrcLibCandidate] into a [Lyric] object that the
     * LyricsSheet can render. Prefers synced lyrics; falls back to
     * plain. Returns null if the candidate has no usable lyrics text.
     *
     * @param offsetMs Optional offset in MILLISECONDS to shift all
     *   line + word timestamps. Positive = push lyrics later in time
     *   (e.g. lyrics fire 2s after the song's actual timeline).
     *   Negative = pull lyrics earlier.
     *
     *   Used by the Lyrics Picker when the user picks a candidate whose
     *   duration differs from the song's actual duration. We compute:
     *
     *     offsetMs = (songDurationMs) − (candidate.durationSec × 1000)
     *
     *   If song = 3:45 (225000ms) and candidate = 3:43 (223000ms),
     *   offsetMs = +2000 → every line is shifted +2s later in the song.
     *   This means the user gets a perfectly-synced experience in one
     *   tap, even if the candidate's timeline was for a slightly
     *   different release of the song.
     *
     *   The offset is applied to BOTH line.timeMs AND word timestamps
     *   so word-by-word karaoke stays in sync too.
     */
    suspend fun candidateToLyric(
        candidate: LrcLibCandidate,
        offsetMs: Long = 0L
    ): Lyric? = withContext(Dispatchers.IO) {
        val text = candidate.syncedLyrics ?: candidate.plainLyrics
        if (text.isNullOrBlank()) return@withContext null

        val parsedLines = LrcParser.parse(text)
        if (parsedLines.isEmpty()) return@withContext null

        // Apply offset to line timestamps (skip instrumental lines at -1)
        val lines = if (offsetMs != 0L) {
            parsedLines.map { line ->
                if (line.timeMs < 0) line
                else line.copy(
                    timeMs = (line.timeMs + offsetMs).coerceAtLeast(0L),
                    words = line.words?.map { word ->
                        word.copy(
                            startTime = (word.startTime + offsetMs).coerceAtLeast(0L),
                            endTime = (word.endTime + offsetMs).coerceAtLeast(0L)
                        )
                    }
                )
            }
        } else {
            parsedLines
        }

        val isSynced = candidate.hasSynced && lines.any { it.timeMs >= 0 }
        Lyric(
            synced = isSynced,
            lines = lines,
            source = LyricSource.NETWORK,
            trackName = candidate.trackName,
            artistName = candidate.artistName,
            hasWordSync = lines.any { it.hasWordSync }
        )
    }

    /**
     * Fetch from NetEase Cloud Music API.
     * Searches by keyword (artist + title), returns synced LRC.
     */
    private fun fetchFromNetEase(track: String, artist: String, durationMs: Long?): Lyric? {
        return try {
            // Search for the song
            val keyword = encode("$artist $track")
            val searchUrl = URL("https://music.xianqiao.wang/neteaseapiv2/search?keywords=$keyword&limit=5&type=1")
            val searchConn = searchUrl.openConnection() as HttpURLConnection
            searchConn.requestMethod = "GET"
            searchConn.connectTimeout = 8000
            searchConn.readTimeout = 8000
            if (searchConn.responseCode != 200) return null

            val searchBody = searchConn.inputStream.bufferedReader().use { it.readText() }
            val searchObj = json.parseToJsonElement(searchBody).jsonObject
            val songs = searchObj["result"]?.jsonObject?.get("songs")
                ?: return null

            // Find the best matching song by duration
            val songsArray = songs.toString()
            // Extract song IDs from the JSON
            val idRegex = Regex(""""id"\s*:\s*(\d+)""")
            val durRegex = Regex(""""dt"\s*:\s*(\d+)""")
            val ids = idRegex.findAll(songsArray).map { it.groupValues[1] }.toList()
            val durs = durRegex.findAll(songsArray).map { it.groupValues[1].toLongOrNull() ?: 0L }.toList()

            var bestId: String? = null
            if (durationMs != null && durs.isNotEmpty()) {
                // Find the song with the closest duration
                var minDiff = Long.MAX_VALUE
                for (i in ids.indices) {
                    if (i < durs.size) {
                        val diff = kotlin.math.abs(durs[i] - durationMs)
                        if (diff < minDiff && diff < 3000) {  // 3 second tolerance
                            minDiff = diff
                            bestId = ids[i]
                        }
                    }
                }
            }
            if (bestId == null && ids.isNotEmpty()) bestId = ids[0]
            if (bestId == null) return null

            // Fetch lyrics for the matched song
            val lrcUrl = URL("https://music.xianqiao.wang/neteaseapiv2/lyric?id=$bestId")
            val lrcConn = lrcUrl.openConnection() as HttpURLConnection
            lrcConn.requestMethod = "GET"
            lrcConn.connectTimeout = 8000
            lrcConn.readTimeout = 8000
            if (lrcConn.responseCode != 200) return null

            val lrcBody = lrcConn.inputStream.bufferedReader().use { it.readText() }
            val lrcObj = json.parseToJsonElement(lrcBody).jsonObject
            val synced = lrcObj["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.contentOrNull
            val unsynced = lrcObj["tlyric"]?.let { null } ?: synced

            if (!synced.isNullOrBlank()) {
                val lines = LrcParser.parse(synced)
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = lines.any { it.timeMs >= 0 },
                        lines = lines,
                        source = LyricSource.NETWORK,
                        trackName = track,
                        artistName = artist,
                        hasWordSync = lines.any { it.hasWordSync }
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetch from KuGou API.
     * Searches by keyword, returns synced LRC.
     */
    private fun fetchFromKuGou(track: String, artist: String, durationMs: Long?): Lyric? {
        return try {
            val keyword = encode("$artist $track")
            // Search for the song
            val searchUrl = URL("https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&hash=&src_app=&duration=${durationMs?.div(1000) ?: 0}&album_audio_id=0&keyword=$keyword&pagesize=5&area_code=1&page=1")
            val searchConn = searchUrl.openConnection() as HttpURLConnection
            searchConn.requestMethod = "GET"
            searchConn.connectTimeout = 8000
            searchConn.readTimeout = 8000
            if (searchConn.responseCode != 200) return null

            val searchBody = searchConn.inputStream.bufferedReader().use { it.readText() }
            val searchObj = json.parseToJsonElement(searchBody).jsonObject
            val candidates = searchObj["candidates"]
                ?: return null

            // Extract the first candidate's ID and accesskey
            val idRegex = Regex(""""id"\s*:\s*"?(\d+)"?""")
            val accesskeyRegex = Regex(""""accesskey"\s*:\s*"([^"]+)"""")
            val id = idRegex.find(candidates.toString())?.groupValues?.getOrNull(1) ?: return null
            val accesskey = accesskeyRegex.find(candidates.toString())?.groupValues?.getOrNull(1) ?: return null

            // Fetch the lyrics
            val lrcUrl = URL("https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8")
            val lrcConn = lrcUrl.openConnection() as HttpURLConnection
            lrcConn.requestMethod = "GET"
            lrcConn.connectTimeout = 8000
            lrcConn.readTimeout = 8000
            if (lrcConn.responseCode != 200) return null

            val lrcBody = lrcConn.inputStream.bufferedReader().use { it.readText() }
            val lrcObj = json.parseToJsonElement(lrcBody).jsonObject
            val encodedContent = lrcObj["content"]?.jsonPrimitive?.contentOrNull ?: return null

            // KuGou returns base64-encoded LRC
            val lrcText = try {
                String(android.util.Base64.decode(encodedContent, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) { return null }

            if (lrcText.isNotBlank()) {
                val lines = LrcParser.parse(lrcText)
                if (lines.isNotEmpty()) {
                    return Lyric(
                        synced = lines.any { it.timeMs >= 0 },
                        lines = lines,
                        source = LyricSource.NETWORK,
                        trackName = track,
                        artistName = artist,
                        hasWordSync = lines.any { it.hasWordSync }
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Cache lyrics for offline use. */
    private fun cacheLyrics(track: String, artist: String, lyric: Lyric) {
        val cacheFile = File(cacheDir, "${cacheKey(track, artist)}.json")
        writeCache(cacheFile, lyric)
    }

    /**
     * Public cache-write API — used by the Lyrics Picker when the user
     * manually selects a candidate. Caches the chosen candidate under
     * the song's actual track/artist name so it loads on next play.
     */
    fun cacheLyricsPublic(track: String, artist: String, lyric: Lyric) {
        cacheLyrics(track, artist, lyric)
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
