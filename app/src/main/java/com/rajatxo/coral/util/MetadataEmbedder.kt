package com.rajatxo.coral.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.audio.exceptions.CannotWriteException
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File
import java.io.FileOutputStream

/**
 * MetadataEmbedder — embeds cover art directly into an audio file's metadata.
 *
 * Supports MP3 (ID3 APIC frame), M4A/AAC (MP4 covr atom), FLAC (PICTURE block),
 * OGG (METADATA_BLOCK_PICTURE), and other formats JAudioTagger handles.
 *
 * ★ Android scoped storage strategy:
 *   On Android 11+, JAudioTagger's `java.io.File` access to shared-storage
 *   music files fails even with READ_MEDIA_AUDIO — the file appears to exist
 *   but the underlying `FileInputStream` can't read the bytes (EACCES).
 *   ExoPlayer works because it uses ContentResolver.openInputStream(), but
 *   JAudioTagger only uses java.io.File.
 *
 *   To work around this, we:
 *     1. Copy the audio file to a TEMP file in cacheDir (always accessible).
 *     2. Run JAudioTagger on the temp file.
 *     3. Write the modified temp file back to the original location via
 *        ContentResolver.openOutputStream(songUri) — this works with
 *        READ_MEDIA_AUDIO for app-created files, or MANAGE_EXTERNAL_STORAGE
 *        for any file.
 *
 *   If the write-back fails, we return needsPermission=true so the UI can
 *   prompt the user to grant "All files access" (MANAGE_EXTERNAL_STORAGE).
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
        //   file format" error on Android 11+.
        //
        //   JAudioTagger uses java.io.File + FileInputStream, which on
        //   Android 11+ scoped storage fails with EACCES for files the
        //   app didn't create — even though the file "exists" and
        //   canRead() might return true. By copying to cacheDir first,
        //   we guarantee JAudioTagger can read AND write the temp file.
        val tempAudioFile = File.createTempFile(
            "coral_embed_${System.currentTimeMillis()}",
            ".audio",
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

        // Step 3: Run JAudioTagger on the temp file
        try {
            val audioTagFile = AudioFileIO.read(tempAudioFile)
            val tag = audioTagFile.tagOrCreateAndSetDefault

            val artwork = AndroidArtwork()
            artwork.setBinaryData(coverBytes)
            artwork.setMimeType(mimeType)
            artwork.setPictureType(0)
            artwork.setWidth(0)
            artwork.setHeight(0)
            artwork.setLinked(false)

            try {
                tag.deleteArtworkField()
            } catch (_: Exception) { }

            tag.setField(artwork)
            audioTagFile.commit()
        } catch (e: CannotReadException) {
            tempAudioFile.delete()
            return Result(
                success = false,
                message = "The audio format isn't supported for metadata editing. " +
                         "JAudioTagger couldn't parse the file structure. " +
                         "Supported: MP3, M4A/AAC, FLAC, OGG."
            )
        } catch (e: ReadOnlyFileException) {
            tempAudioFile.delete()
            return Result(
                success = false,
                message = "Temp file is read-only (internal error)."
            )
        } catch (e: InvalidAudioFrameException) {
            tempAudioFile.delete()
            return Result(
                success = false,
                message = "Audio file has corrupted metadata frames. " +
                         "Cannot embed cover art safely."
            )
        } catch (e: CannotWriteException) {
            tempAudioFile.delete()
            return Result(
                success = false,
                message = "Failed to write metadata to temp file: ${e.message}"
            )
        } catch (e: Exception) {
            tempAudioFile.delete()
            return Result(
                success = false,
                message = "Failed to embed cover: ${e.message}"
            )
        }

        // Step 4: Write the modified temp file back to the original location.
        //   We try multiple approaches in order:
        //   a. ContentResolver.openOutputStream(songUri) — works for
        //      app-created files or with MANAGE_EXTERNAL_STORAGE.
        //   b. Direct file copy via java.io.File — works with
        //      MANAGE_EXTERNAL_STORAGE.
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
     *   1. ContentResolver.openOutputStream(songUri) — works for app-created
     *      files or with MANAGE_EXTERNAL_STORAGE.
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
        // Approach 1: ContentResolver.openOutputStream
        //   This works for app-created files on Android 10+, and for ALL
        //   files if the app has MANAGE_EXTERNAL_STORAGE.
        try {
            context.contentResolver.openOutputStream(songUri, "wt")?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
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
     */
    private fun refreshMediaStore(context: Context, songUri: Uri) {
        try {
            // Modern API: MediaScannerConnection.scanFile needs a file path.
            val path = resolveFilePath(context, songUri)
            if (path != null) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    arrayOf("audio/*")
                ) { _, _ -> }
            }
        } catch (_: Exception) { }

        try {
            // Also notify via the legacy broadcast (pre-Q)
            @Suppress("DEPRECATION")
            val intent = android.content.Intent(
                android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
            ).apply { data = songUri }
            context.sendBroadcast(intent)
        } catch (_: Exception) { }
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

            try {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    val basePath = when (type) {
                        "primary" -> Environment.getExternalStorageDirectory().absolutePath
                        else -> "/storage/$type"
                    }
                    val candidate = "$basePath/$relativePath"
                    if (File(candidate).exists()) return candidate
                }
            } catch (_: Exception) { }
        }

        return null
    }

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
