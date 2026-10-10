package com.rajatxo.coral.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.audio.exceptions.CannotWriteException
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File

/**
 * MetadataEmbedder — embeds cover art directly into an audio file's metadata.
 *
 * Supports MP3 (ID3 APIC frame), M4A/AAC (MP4 covr atom), FLAC (PICTURE block),
 * OGG (METADATA_BLOCK_PICTURE), and other formats JAudioTagger handles.
 *
 * Uses JAudioTagger's Android-compatible fork (AdrienPoupa:jaudiotagger:2.2.3-PRE2).
 *
 * Android scoped storage notes:
 *   - On Android 10+, audio files in shared storage (Music, Download, etc.)
 *     are accessible via file paths IF the app has READ_MEDIA_AUDIO.
 *   - For WRITE access without MANAGE_EXTERNAL_STORAGE, the file must be
 *     one the app created OR the user must have granted access via SAF.
 *   - For files the app didn't create (most user music files), this will
 *     work on most devices because media files are NOT subject to the
 *     scoped-storage write restrictions — they can be modified via their
 *     file path as long as the app has READ_MEDIA_AUDIO + WRITE permissions.
 *   - If write fails, the user is informed via the result callback so they
 *     can retry or grant additional permissions.
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
        // Step 1: Resolve songUri → file path
        val filePath = resolveFilePath(context, songUri)
            ?: return Result(
                success = false,
                message = "Could not resolve file path for this song. " +
                         "If it's in cloud storage or an SD card, embedding won't work."
            )

        val audioFile = File(filePath)
        if (!audioFile.exists() || !audioFile.canWrite()) {
            // Try to make it writable via MediaStore (sometimes fixes perms on Android 11+)
            try {
                audioFile.setWritable(true, false)
            } catch (_: Exception) { }
            if (!audioFile.exists()) {
                return Result(
                    success = false,
                    message = "Audio file no longer exists at: $filePath",
                    needsPermission = false
                )
            }
            if (!audioFile.canWrite()) {
                return Result(
                    success = false,
                    message = "Coral doesn't have write permission for this file. " +
                             "Grant \"All files access\" in Android Settings to embed metadata.",
                    needsPermission = true
                )
            }
        }

        // Step 2: Copy cover image bytes into memory
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
            return Result(
                success = false,
                message = "Cover image file is empty."
            )
        }

        // Step 3: Detect cover image MIME type from the first bytes
        val mimeType = detectMimeType(coverBytes)

        // Step 4: Open the audio file with JAudioTagger
        val audioTagFile = try {
            AudioFileIO.read(audioFile)
        } catch (e: CannotReadException) {
            return Result(
                success = false,
                message = "JAudioTagger cannot read this file format. " +
                         "It may be DRM-protected or an unusual codec."
            )
        } catch (e: ReadOnlyFileException) {
            return Result(
                success = false,
                message = "File is on read-only storage (some SD cards). " +
                         "Move the file to internal storage and retry.",
                needsPermission = false
            )
        } catch (e: InvalidAudioFrameException) {
            return Result(
                success = false,
                message = "Audio file has corrupted metadata frames. " +
                         "Cannot embed cover art safely."
            )
        } catch (e: Exception) {
            // Permission errors on Android 11+ manifest as generic IOException
            // — detect write-permission failures and prompt the user.
            val msg = e.message ?: ""
            val needsPermission = msg.contains("Permission denied", true) ||
                                  msg.contains("EACCES", true) ||
                                  msg.contains("Read-only", true)
            return Result(
                success = false,
                message = if (needsPermission)
                    "Coral doesn't have write permission for this file. " +
                    "Grant \"All files access\" in Android Settings to embed metadata."
                else "Unexpected error reading audio: $msg",
                needsPermission = needsPermission
            )
        }

        // Step 5: Create an Artwork object and set it on the tag
        try {
            // ★ getTagOrCreateAndSetDefault() — returns the existing tag, or
            //   creates a format-appropriate default tag (ID3v2 for MP3,
            //   VorbisComment for FLAC/OGG, MP4 tag for M4A/AAC) AND sets it
            //   on the AudioFile so commit() will persist it.
            val tag = audioTagFile.tagOrCreateAndSetDefault

            val artwork = AndroidArtwork()
            artwork.setBinaryData(coverBytes)
            artwork.setMimeType(mimeType)
            // Picture type 0 = "Other". JAudioTagger expects an Int here.
            // PictureTypes.DEFAULT_ID == 0 → cover art (front) is 3, but
            // some formats (FLAC) ignore this field entirely.
            artwork.setPictureType(0)
            artwork.setWidth(0)
            artwork.setHeight(0)
            artwork.setLinked(false)

            // Remove existing artwork (otherwise MP3 ends up with multiple APIC frames)
            try {
                tag.deleteArtworkField()
            } catch (_: Exception) {
                // Some tag types throw — that's fine, we'll just add the new field.
            }

            tag.setField(artwork)

            // Step 6: Save the modified file
            audioTagFile.commit()

            // Step 7: Tell MediaStore to refresh the file so other apps see new art
            try {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
                )
                intent.data = Uri.fromFile(audioFile)
                context.sendBroadcast(intent)
            } catch (_: Exception) { }

            // Also use the modern MediaScannerConnection for Android Q+
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    arrayOf("audio/*")
                ) { _, _ -> }
            } catch (_: Exception) { }

            return Result(
                success = true,
                message = "Cover art embedded into file metadata."
            )
        } catch (e: CannotWriteException) {
            return Result(
                success = false,
                message = "Cannot write to this file. " +
                         "It may be read-only or in use by another app. " +
                         "Grant \"All files access\" in Android Settings.",
                needsPermission = true
            )
        } catch (e: Exception) {
            return Result(
                success = false,
                message = "Failed to embed cover: ${e.message}"
            )
        }
    }

    /**
     * Resolve a content:// URI to an absolute file path.
     *
     * Strategy:
     *   1. Try MediaStore DATA column (works for all MediaStore-tracked files)
     *   2. Fall back to SAF document ID parsing (for SAF-granted URIs)
     *   3. Fall back to file:// URI path (for direct file URIs)
     *
     * Returns null if no file path can be resolved (e.g. cloud URIs).
     */
    private fun resolveFilePath(context: Context, uri: Uri): String? {
        // Direct file:// URI
        if (uri.scheme == "file") {
            return uri.path
        }

        // MediaStore content:// URI
        if (uri.scheme == "content") {
            // Try MediaStore.DATA first (most reliable for audio files)
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

            // Try splitting the document ID and reconstructing the path
            try {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    val basePath = when (type) {
                        "primary" -> android.os.Environment.getExternalStorageDirectory().absolutePath
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
            // JPEG: FF D8 FF
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
                "image/jpeg"
            // PNG: 89 50 4E 47
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() ->
                "image/png"
            // WebP: "RIFF" + skip 4 + "WEBP"
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
