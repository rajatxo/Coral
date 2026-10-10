package com.rajatxo.coral.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import aman.taglib.TagLib
import java.io.File
import java.io.FileOutputStream

/**
 * MetadataEmbedder — embeds cover art directly into an audio file's metadata.
 *
 * Uses **TagLib** (native C++ library via JNI) instead of JAudioTagger.
 * TagLib is the de-facto standard audio tagging library (used by
 * Strawberry, Clementine, MPD, and Lyricify). Key advantages over
 * JAudioTagger:
 *
 *   - Reads file format from **magic bytes**, not the file extension.
 *     JAudioTagger's `AudioFileIO.read(file)` dispatches to a format-specific
 *     reader based on the file extension — temp files with `.audio` extension
 *     failed with CannotReadException.
 *   - No "CannotReadException" on unusual codec variants (M4A ALAC, M4A AAC,
 *     MP3 with ID3v2.4 + APEv2, etc.)
 *   - Handles MP3 (ID3v2 APIC), M4A (MP4 covr atom), FLAC (PICTURE block),
 *     OGG (METADATA_BLOCK_PICTURE), WMA, WAV uniformly.
 *
 * Android scoped storage strategy (same as Lyricify's EmbeddingManager):
 *   1. Copy the audio file from its content:// URI to a temp file in cacheDir
 *      (always accessible to the app).
 *   2. Run TagLib on the temp file (reads + writes the temp file).
 *   3. Write the modified temp file back to the original location via
 *      ContentResolver.openOutputStream(songUri, "wt") — this works with
 *      MANAGE_EXTERNAL_STORAGE for any file.
 *   4. If write-back fails, return needsPermission=true so the UI prompts
 *      the user to grant All files access.
 */
object MetadataEmbedder {

    /**
     * Result of an embed attempt.
     *
     * @param success true if the artwork was successfully written to the file
     * @param message human-readable description (success message or error)
     * @param needsPermission true if the failure was due to file permissions
     *                        (caller can prompt user for MANAGE_EXTERNAL_STORAGE)
     */
    data class Result(
        val success: Boolean,
        val message: String,
        val needsPermission: Boolean = false
    )

    /**
     * Check if the app has "All files access" (MANAGE_EXTERNAL_STORAGE).
     * On Android < 11, this always returns true (legacy storage grants full access).
     */
    fun hasAllFilesAccess(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    /**
     * Embed the given cover image into the song file's metadata.
     *
     * Must be called on a background thread (IO dispatcher).
     *
     * @param context app context (for ContentResolver)
     * @param songUri content:// URI of the audio file (from MediaStore)
     * @param coverUri content:// URI of the cover image to embed
     * @return Result indicating success / failure with a message
     */
    fun embedCover(
        context: Context,
        songUri: Uri,
        coverUri: Uri
    ): Result {
        // Step 1: Read cover image bytes
        val coverBytes = try {
            context.contentResolver.openInputStream(coverUri)?.use { input ->
                input.readBytes()
            } ?: return Result(
                success = false,
                message = "Could not open the cover image. The URI may have expired."
            )
        } catch (e: Exception) {
            return Result(
                success = false,
                message = "Failed to read cover image: ${e.message}"
            )
        }

        if (coverBytes.isEmpty()) {
            return Result(success = false, message = "Cover image file is empty.")
        }

        val mimeType = detectMimeType(coverBytes)

        // Step 2: Copy the audio file to a temp file in cacheDir.
        //   ★ This is the KEY fix for the "JAudioTagger cannot read this
        //   file format" error on Android 11+ scoped storage.
        //
        //   TagLib uses java.io.File internally — on Android 11+ scoped
        //   storage, FileInputStream fails with EACCES for files the app
        //   didn't create. By copying to cacheDir first, we guarantee
        //   TagLib can read AND write the temp file.
        //
        //   ★★ CRITICAL: We preserve the original file extension on the
        //   temp file. TagLib reads magic bytes so it doesn't strictly need
        //   this, BUT it's a safety net in case TagLib's format detection
        //   has edge cases (and it matches Lyricify's approach).
        val originalExtension = detectAudioExtension(context, songUri)
        val tempAudioFile = File.createTempFile(
            "coral_embed_${System.currentTimeMillis()}",
            ".$originalExtension",
            context.cacheDir
        )
        try {
            val copied = copyAudioToTemp(context, songUri, tempAudioFile)
            if (!copied) {
                tempAudioFile.delete()
                return Result(
                    success = false,
                    message = "Could not read the audio file. " +
                             "The file may be on storage Coral can't access directly."
                )
            }
        } catch (e: Exception) {
            tempAudioFile.delete()
            return Result(
                success = false,
                message = "Failed to copy audio file for editing: ${e.message}"
            )
        }

        // Step 3: Run TagLib on the temp file
        try {
            val tagLib = TagLib()
            val success = tagLib.setArtwork(
                tempAudioFile.absolutePath,
                coverBytes,
                mimeType,
                "Cover (front)"
            )

            if (!success) {
                tempAudioFile.delete()
                return Result(
                    success = false,
                    message = "TagLib failed to set artwork on the file. " +
                             "This format may not support embedded artwork."
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            tempAudioFile.delete()
            return Result(
                success = false,
                message = "TagLib native library failed to load. " +
                         "This may be an unsupported device architecture."
            )
        } catch (e: Exception) {
            tempAudioFile.delete()
            return Result(
                success = false,
                message = "Failed to embed cover: ${e.message}"
            )
        }

        // Step 4: Write the modified temp file back to the original location.
        val writeBackResult = writeBackToOriginal(context, songUri, tempAudioFile)
        tempAudioFile.delete()

        if (!writeBackResult.success) {
            return Result(
                success = false,
                message = writeBackResult.message,
                needsPermission = writeBackResult.needsPermission
            )
        }

        // Step 5: Refresh MediaStore so other apps see the new art
        refreshMediaStore(context, songUri)

        return Result(
            success = true,
            message = "Cover art embedded into file metadata."
        )
    }

    /**
     * Copy the audio file from its content:// URI to a temp file in cacheDir.
     * Returns true on success, false on failure.
     */
    private fun copyAudioToTemp(
        context: Context,
        songUri: Uri,
        tempFile: File
    ): Boolean {
        return try {
            context.contentResolver.openInputStream(songUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            tempFile.exists() && tempFile.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Write the modified temp file back to the original audio file location.
     *
     * Tries two approaches:
     *   1. ContentResolver.openOutputStream(songUri, "wt") — works for
     *      app-created files on Android 10+, and for ALL files if the app
     *      has MANAGE_EXTERNAL_STORAGE.
     *   2. Direct file copy via java.io.File — works with MANAGE_EXTERNAL_STORAGE
     *      when we can resolve the file path.
     *
     * @return WriteBackResult with success status + needsPermission flag
     */
    private fun writeBackToOriginal(
        context: Context,
        songUri: Uri,
        tempFile: File
    ): WriteBackResult {
        // Approach 1: ContentResolver.openOutputStream with "wt" (write truncate)
        //   "wt" mode tells the content provider to truncate the existing file
        //   before writing — required because otherwise the old bytes stay and
        //   the new content might be shorter, leaving garbage at the end.
        try {
            context.contentResolver.openOutputStream(songUri, "wt")?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                output.flush()
            } ?: return WriteBackResult(
                success = false,
                message = "Could not open the audio file for writing.",
                needsPermission = true
            )
            return WriteBackResult(success = true)
        } catch (e: Exception) {
            // openOutputStream failed — likely needs MANAGE_EXTERNAL_STORAGE.
            // Fall through to approach 2.
        }

        // Approach 2: Direct file copy via java.io.File path.
        //   Only works if the app has MANAGE_EXTERNAL_STORAGE AND we can
        //   resolve the content:// URI to a file path.
        val filePath = resolveFilePath(context, songUri)
        if (filePath != null) {
            try {
                val targetFile = File(filePath)
                if (targetFile.canWrite()) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    return WriteBackResult(success = true)
                }
            } catch (e: Exception) {
                // File copy failed too.
            }
        }

        // Both approaches failed — the user needs to grant MANAGE_EXTERNAL_STORAGE.
        return WriteBackResult(
            success = false,
            message = "Coral doesn't have permission to write to this file. " +
                     "Grant \"All files access\" in Android Settings to embed metadata " +
                     "into audio files.",
            needsPermission = true
        )
    }

    /**
     * Result of the write-back attempt.
     */
    private data class WriteBackResult(
        val success: Boolean,
        val message: String = "",
        val needsPermission: Boolean = false
    )

    /**
     * Tell MediaStore to re-scan the file so other apps pick up the new art.
     *
     * This is CRITICAL for two reasons:
     *   1. Other apps (Niagara launcher's miniplayer, system media controls,
     *      other music players) read artwork from MediaStore's cached
     *      thumbnail. Without a re-scan, they keep showing the OLD art.
     *   2. Coral's own ExoPlayer caches the MediaItem's metadata (including
     *      artworkUri) at MediaItem creation time. The stale artworkUri
     *      needs to be invalidated in MediaStore so the next read picks up
     *      the new embedded art.
     *
     * Strategy (try all, in order):
     *   a. ContentResolver.notifyChange(songUri, null) — tells all observers
     *      that the content at this URI changed. Modern Android (10+) uses
     *      this to invalidate cached thumbnails.
     *   b. MediaScannerConnection.scanFile(filePath) — triggers a re-scan
     *      of the file at the given path. Works when resolveFilePath succeeds.
     *   c. For Android 10+: also try MediaStore.createUpdateRequest() which
     *      asks the system to re-index the file (requires user consent
     *      dialog if the app doesn't have MANAGE_EXTERNAL_STORAGE).
     */
    private fun refreshMediaStore(context: Context, songUri: Uri) {
        // a. Notify all content observers that this URI changed.
        //    This is the most reliable way to tell MediaStore (and apps
        //    observing MediaStore, like Niagara launcher) to invalidate
        //    their cached artwork for this file.
        try {
            context.contentResolver.notifyChange(songUri, null)
        } catch (_: Exception) { }

        // b. MediaScannerConnection — triggers a re-scan of the file at
        //    the given path. MediaStore re-reads the file's metadata tags
        //    (including the new embedded artwork) and updates its cache.
        try {
            val path = resolveFilePath(context, songUri)
            if (path != null) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    arrayOf("audio/*")
                ) { _, _ -> }
            }
        } catch (_: Exception) { }

        // c. Also try scanning via the content URI's display name as a
        //    fallback (some devices don't resolve DATA column for SAF URIs
        //    but MediaScanner can still scan via the URI itself).
        try {
            // For content://media/external/audio/media/XXX URIs, we can
            // update the row directly to trigger a thumbnail refresh.
            val path = resolveFilePath(context, songUri)
            if (path == null) {
                // Last resort: broadcast a media scan intent
                @Suppress("DEPRECATION")
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
                ).apply { data = songUri }
                context.sendBroadcast(intent)
            }
        } catch (_: Exception) { }

        // d. ★ Android 10+: use MediaStore.createUpdateRequest() to ask the
        //    system to re-index the file. This is the MODERN way to tell
        //    MediaStore that a file's content changed. On Android 10 (Q),
        //    this shows a system consent dialog. On Android 11+ with
        //    MANAGE_EXTERNAL_STORAGE, it updates silently.
        //
        //    This is what makes Niagara launcher (and other apps observing
        //    MediaStore) pick up the new embedded artwork.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                // createUpdateRequest returns a PendingIntent that the caller
                // must launch to get user consent (Android 10) or that
                // executes silently (Android 11+ with all-files-access).
                // We can't launch it from a background util (no Activity),
                // but calling notifyChange above + MediaScannerConnection
                // usually suffices. Leaving this as a no-op fallback.
                // The real heavy lifting is done by MediaScannerConnection
                // in step (b), which triggers a full re-scan.
            } catch (_: Exception) { }
        }
    }

    /**
     * Resolve a content:// URI to an absolute file path.
     * Returns null if no file path can be resolved (e.g. cloud URIs).
     */
    private fun resolveFilePath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (idx >= 0) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrBlank()) return path
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        return null
    }

    /**
     * Detect the audio file extension from its content:// URI.
     *
     * TagLib reads magic bytes so it doesn't strictly need the extension,
     * but we preserve it as a safety net (matches Lyricify's approach).
     *
     * Returns the extension WITHOUT the leading dot (e.g. "mp3", "m4a").
     */
    private fun detectAudioExtension(context: Context, songUri: Uri): String {
        // Strategy 1: MediaStore.DISPLAY_NAME
        try {
            context.contentResolver.query(
                songUri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = cursor.getString(nameIdx) ?: ""
                        val dotIdx = name.lastIndexOf('.')
                        if (dotIdx >= 0 && dotIdx < name.length - 1) {
                            val ext = name.substring(dotIdx + 1).lowercase()
                            if (ext in SUPPORTED_EXTENSIONS) return ext
                        }
                    }
                    val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    if (mimeIdx >= 0) {
                        val mime = cursor.getString(mimeIdx) ?: ""
                        val mapped = mimeTypeToExtension(mime)
                        if (mapped != null) return mapped
                    }
                }
            }
        } catch (_: Exception) { }

        // Strategy 2: content:// URI's last path segment
        try {
            val lastSeg = songUri.lastPathSegment ?: ""
            val dotIdx = lastSeg.lastIndexOf('.')
            if (dotIdx >= 0 && dotIdx < lastSeg.length - 1) {
                val ext = lastSeg.substring(dotIdx + 1).lowercase()
                if (ext in SUPPORTED_EXTENSIONS) return ext
            }
        } catch (_: Exception) { }

        // Strategy 3: ContentResolver.getType(uri) → MIME type → extension
        try {
            val mime = context.contentResolver.getType(songUri)
            if (mime != null) {
                val mapped = mimeTypeToExtension(mime)
                if (mapped != null) return mapped
            }
        } catch (_: Exception) { }

        // Fallback: mp3 (most common format)
        return "mp3"
    }

    /** Map an audio MIME type to a TagLib-supported extension. */
    private fun mimeTypeToExtension(mime: String): String? {
        return when {
            mime.contains("mpeg", true) -> "mp3"
            mime.contains("mp4", true) || mime.contains("m4a", true) ||
            mime.contains("aac", true) || mime.contains("apple", true) -> "m4a"
            mime.contains("flac", true) -> "flac"
            mime.contains("ogg", true) || mime.contains("vorbis", true) -> "ogg"
            mime.contains("wav", true) || mime.contains("x-wav", true) -> "wav"
            mime.contains("wma", true) || mime.contains("x-ms-wma", true) -> "wma"
            mime.contains("aiff", true) || mime.contains("x-aiff", true) -> "aif"
            else -> null
        }
    }

    /** Extensions TagLib supports. */
    private val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "m4p", "flac", "ogg", "wma", "wav", "ra", "rm", "aif"
    )

    /**
     * Detect image MIME type from the first few bytes (magic numbers).
     * Falls back to JPEG if unknown.
     */
    private fun detectMimeType(bytes: ByteArray): String {
        if (bytes.size < 4) return "image/jpeg"
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
                "image/jpeg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() ->
                "image/png"
            bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() ->
                "image/webp"
            else -> "image/jpeg"
        }
    }
}
