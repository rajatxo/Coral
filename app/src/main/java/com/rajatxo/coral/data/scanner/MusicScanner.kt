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
 * Format detection strategy (fast → slow fallback):
 *   1. MediaStore MIME_TYPE column (instant — no file I/O)
 *      → covers 95% of cases: FLAC, M4A, MP3, WAV, OGG, etc.
 *   2. MediaExtractor (only for ambiguous cases — .m4a/.ec3 files that
 *      might be Dolby Atmos E-AC-3 JOC)
 *      → ~50ms per song, but only runs on suspected Atmos files
 *
 * This gives us fast scanning for most songs while still detecting
 * Atmos for the rare files that need it.
 *
 * Labels: FLAC, M4A, ALAC, MP3, WAV, OGG, AAC, AC3, Atmos, Unknown.
 * "Atmos" is reserved for E-AC-3 JOC (Dolby Atmos) — those songs get
 * their own capsule, separate from regular M4A.
 */
object MusicScanner {

    private const val TAG = "MusicScanner"

    /**
     * Scans the device for local audio files using MediaStore.
     *
     * NOTE: Takes a [Context] (not ContentResolver) because MediaExtractor
     * needs the Context variant of setDataSource to read content:// URIs.
     */
    fun scanMusic(context: Context): List<Song> {
        val contentResolver = context.contentResolver
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        // Added MIME_TYPE to the projection — this lets us detect the format
        // INSTANTLY from the cursor without opening the file. MediaExtractor
        // is only used as a fallback for ambiguous cases (possible Atmos).
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME
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
            // DATE_ADDED may be null on some obscure Android versions.
            val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val dateAdded = if (dateAddedColumn >= 0) cursor.getLong(dateAddedColumn) else 0L
                val mimeType = if (mimeTypeColumn >= 0) cursor.getString(mimeTypeColumn) else null
                val displayName = if (displayNameColumn >= 0) cursor.getString(displayNameColumn) else null

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )

                // Fast format detection from MIME type (no file I/O).
                // Falls back to MediaExtractor only for ambiguous cases.
                val format = detectFormat(mimeType, displayName, context, uri)

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
     * Fast format detection from the MediaStore MIME type.
     *
     * For most files, the MIME type from MediaStore is enough to classify
     * the format instantly (no file I/O). We only fall back to
     * MediaExtractor when the MIME type is ambiguous — specifically when
     * the file might be Dolby Atmos (E-AC-3 JOC) hiding inside a regular
     * .m4a or .ec3 container.
     *
     * @param mimeType The MediaStore MIME_TYPE (e.g. "audio/flac", "audio/mp4")
     * @param displayName The file name (used for extension-based fallback)
     * @param context For MediaExtractor (only used if needed)
     * @param uri The song's content URI (only used if needed)
     */
    private fun detectFormat(
        mimeType: String?,
        displayName: String?,
        context: Context,
        uri: Uri
    ): String {
        // Try the fast path first — MIME type from MediaStore.
        if (mimeType != null) {
            val fastLabel = classifyAudioMime(mimeType)
            // If the fast path identified it as a format that COULD contain
            // Atmos (M4A), check with MediaExtractor to be sure.
            // Atmos files have .m4a or .ec3 extension but contain E-AC-3 JOC.
            if (fastLabel == "M4A" || fastLabel == "EC3") {
                val deepLabel = detectFormatWithMediaExtractor(context, uri)
                if (deepLabel == "Atmos") return "Atmos"
                // Not Atmos — use the deep label if it found something
                // more specific (e.g. ALAC instead of M4A).
                if (deepLabel != "Unknown") return deepLabel
            }
            return fastLabel
        }

        // No MIME type from MediaStore — fall back to file extension.
        if (displayName != null) {
            val extLabel = classifyByExtension(displayName)
            if (extLabel != "Unknown") return extLabel
        }

        // Last resort: MediaExtractor.
        return detectFormatWithMediaExtractor(context, uri)
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

        // ─── Dolby Atmos (E-EC-3 JOC) ──
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
            lowerMime.contains("eac3") || lowerMime.contains("ec3") -> "EC3"
            lowerMime.contains("ac3") || lowerMime.contains("ac-3") -> "AC3"
            lowerMime.contains("ac4") -> "AC4"
            lowerMime.contains("dts") -> "DTS"
            lowerMime.contains("truehd") -> "TrueHD"
            lowerMime.contains("amr") -> "AMR"
            lowerMime.contains("awb") -> "AMR"
            lowerMime.contains("3gpp") || lowerMime.contains("3gp") -> "3GP"
            lowerMime.contains("webm") -> "WEBM"
            lowerMime.contains("mka") || lowerMime.contains("matroska") -> "MKA"
            else -> {
                // Last resort: use the MIME subtype after "audio/" or "application/"
                val subtype = mime.substringAfter("/", "").uppercase()
                subtype.takeIf { it.isNotEmpty() } ?: "Unknown"
            }
        }
    }

    /**
     * Classifies the format from the file extension.
     * Used as a fallback when MediaStore doesn't provide a MIME type.
     */
    private fun classifyByExtension(displayName: String): String {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "flac" -> "FLAC"
            "m4a", "m4b", "m4r", "alac" -> "M4A"
            "mp3" -> "MP3"
            "wav" -> "WAV"
            "ogg" -> "OGG"
            "aac" -> "AAC"
            "ec3" -> "EC3"
            "ac3" -> "AC3"
            "ac4" -> "AC4"
            "dts" -> "DTS"
            "mka" -> "MKA"
            "webm" -> "WEBM"
            "3gp", "3gpp" -> "3GP"
            "amr", "awb" -> "AMR"
            "opus" -> "OGG"  // Opus files usually have .opus extension
            "wma" -> "WMA"
            "aiff", "aif" -> "AIFF"
            else -> "Unknown"
        }
    }

    /**
     * Slow path: uses MediaExtractor to read the actual codec inside the
     * file. Used only when the fast path (MIME type) is ambiguous or
     * missing — specifically to detect Dolby Atmos (E-AC-3 JOC) hiding
     * inside a regular M4A container.
     *
     * Returns "Unknown" if detection fails. Never throws.
     */
    private fun detectFormatWithMediaExtractor(context: Context, uri: Uri): String {
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
            Log.w(TAG, "MediaExtractor format detection failed for $uri: ${e.message}")
            "Unknown"
        } finally {
            try { extractor?.release() } catch (_: Exception) { }
        }
    }
}
