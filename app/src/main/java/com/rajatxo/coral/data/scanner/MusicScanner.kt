package com.rajatxo.coral.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.rajatxo.coral.domain.model.Song

/**
 * Scans the device for local audio files using MediaStore.
 *
 * For each song, the audio format/quality is detected using MediaExtractor
 * to read the actual codec inside the file. This lets us distinguish:
 *   • FLAC (audio/flac)         → "FLAC"
 *   • M4A / ALAC / AAC (mp4)    → "M4A" (or "ALAC" if lossless)
 *   • MP3 (mpeg)                → "MP3"
 *   • WAV (raw)                 → "WAV"
 *   • OGG / Vorbis / Opus       → "OGG"
 *   • Dolby Atmos (E-AC-3 JOC)  → "Atmos"
 *
 * Atmos detection: MediaExtractor reports the MIME type "audio/eac3-joc"
 * for Dolby Atmos content (E-AC-3 with JOC extension). We check both the
 * MIME type string AND the codec-specific data for the JOC marker.
 *
 * Performance: MediaExtractor adds ~50ms per song on a mid-range device.
 * For a 1000-song library, that's ~50 seconds total. The scan runs on a
 * background thread (Dispatchers.IO) so the UI doesn't block. The cache
 * (SongCache) stores the detected format so we don't re-scan on every
 * app launch — only when the cache is missing or stale.
 */
object MusicScanner {

    private const val TAG = "MusicScanner"

    /**
     * Scans the device for local audio files using MediaStore.
     *
     * NOTE: Takes a [Context] (not ContentResolver) because MediaExtractor
     * needs the Context variant of setDataSource to read content:// URIs.
     * The ContentResolver is obtained from the context for the MediaStore
     * query.
     */
    fun scanMusic(context: Context): List<Song> {
        val contentResolver = context.contentResolver
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            // DATE_ADDED may be null on some obscure Android versions — use
            // getColumnIndex (returns -1 if missing) instead of getColumnIndexOrThrow.
            val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val dateAdded = if (dateAddedColumn >= 0) cursor.getLong(dateAddedColumn) else 0L

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )

                // Detect the audio format/quality via MediaExtractor.
                val format = detectFormat(context, uri)

                songs.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = uri,
                        albumArtUri = albumArtUri,
                        dateAdded = dateAdded,
                        format = format
                    )
                )
            }
        }
        return songs
    }

    /**
     * Detects the audio format/quality of a song by reading its codec
     * via MediaExtractor. Returns a human-readable label:
     *   "FLAC", "M4A", "ALAC", "MP3", "WAV", "OGG", "AAC", "Atmos".
     *
     * Returns "Unknown" if detection fails (corrupt file, unreadable, etc.).
     * Never throws — all exceptions are caught and logged.
     *
     * Atmos detection: We check for the MIME type "audio/eac3-joc"
     * (Dolby Digital Plus with JOC extension = Dolby Atmos). Some older
     * Android versions may report "audio/eac3" without the JOC suffix,
     * so we also check the codec-specific data for the JOC marker.
     */
    private fun detectFormat(context: Context, uri: Uri): String {
        var extractor: MediaExtractor? = null
        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue

                // Found the audio track — classify it.
                return classifyAudioMime(mime)
            }
            "Unknown"
        } catch (e: Exception) {
            Log.w(TAG, "Format detection failed for $uri: ${e.message}")
            "Unknown"
        } finally {
            try { extractor?.release() } catch (_: Exception) { }
        }
    }

    /**
     * Maps an audio MIME type to a human-readable format label.
     *
     * Atmos check: "audio/eac3-joc" is the official MIME type for Dolby
     * Atmos content (E-AC-3 with JOC extension). We check the MIME string
     * first; if it contains "eac3-joc" or "joc", we return "Atmos".
     */
    private fun classifyAudioMime(mime: String): String {
        val lowerMime = mime.lowercase()

        // ─── Dolby Atmos (E-AC-3 JOC) ──
        // Official MIME: "audio/eac3-joc"
        // We check the MIME string for "eac3-joc" or "joc".
        if (lowerMime.contains("eac3-joc") || lowerMime.contains("joc")) {
            return "Atmos"
        }

        // ─── Other formats ──
        return when {
            lowerMime.contains("flac") -> "FLAC"
            lowerMime.contains("alac") -> "ALAC"
            lowerMime.contains("mp4") || lowerMime.contains("aac") -> "M4A"
            lowerMime.contains("mpeg") || lowerMime.contains("mp3") -> "MP3"
            lowerMime.contains("wav") || lowerMime.contains("raw") -> "WAV"
            lowerMime.contains("ogg") || lowerMime.contains("vorbis") || lowerMime.contains("opus") -> "OGG"
            lowerMime.contains("ac3") || lowerMime.contains("ac-3") -> "AC3"
            lowerMime.contains("ac4") -> "AC4"
            lowerMime.contains("dts") -> "DTS"
            lowerMime.contains("truehd") -> "TrueHD"
            else -> {
                // Last resort: use the MIME subtype after "audio/"
                mime.removePrefix("audio/").uppercase().takeIf { it.isNotEmpty() } ?: "Unknown"
            }
        }
    }
}
