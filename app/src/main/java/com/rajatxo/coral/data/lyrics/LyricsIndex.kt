package com.rajatxo.coral.data.lyrics

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * LyricsIndex — scans the device for .lrc and .txt files and matches
 * them to songs by filename/title.
 *
 * On startup, scans common directories (Music, Download, Documents, etc.)
 * for .lrc and .txt files. Builds an index mapping the file's base name
 * (without extension) to its full path.
 *
 * When a song plays, we look up the song's title in the index. If found,
 * the lyrics file is read and parsed.
 *
 * Matching strategy:
 * 1. Exact match: "Dandelions" → "Dandelions.lrc" or "Dandelions.txt"
 * 2. Fuzzy match: "Dandelions (feat. Someone)" → "Dandelions.lrc"
 * 3. Artist - Title match: "Ruth B - Dandelions.lrc"
 */
object LyricsIndex {

    private val index = mutableMapOf<String, String>()  // lowercase basename → file path

    private val searchDirs = listOf(
        Environment.getExternalStorageDirectory(),
        File(Environment.getExternalStorageDirectory(), "Music"),
        File(Environment.getExternalStorageDirectory(), "Download"),
        File(Environment.getExternalStorageDirectory(), "Downloads"),
        File(Environment.getExternalStorageDirectory(), "Documents"),
        File(Environment.getExternalStorageDirectory(), "Lyrics"),
    )

    /**
     * Scan the device for .lrc and .txt files and build the index.
     * Call this on a background thread (Dispatchers.IO).
     */
    fun scan(context: Context) {
        index.clear()
        val scannedDirs = mutableSetOf<File>()

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            scanDirectory(dir, scannedDirs, maxDepth = 3)
        }
    }

    private fun scanDirectory(dir: File, scannedDirs: MutableSet<File>, maxDepth: Int) {
        if (maxDepth <= 0 || dir in scannedDirs) return
        scannedDirs.add(dir)

        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanDirectory(file, scannedDirs, maxDepth - 1)
                } else {
                    val name = file.name.lowercase()
                    if (name.endsWith(".lrc") || name.endsWith(".txt")) {
                        // Index by basename without extension
                        val baseName = file.nameWithoutExtension.lowercase()
                        // Also index by just the part after " - " if present
                        // e.g. "Ruth B - Dandelions" → also index "Dandelions"
                        index[baseName] = file.absolutePath
                        if (baseName.contains(" - ")) {
                            val afterDash = baseName.substringAfter(" - ")
                            if (afterDash.isNotBlank()) {
                                index[afterDash] = file.absolutePath
                            }
                            val beforeDash = baseName.substringBefore(" - ")
                            if (beforeDash.isNotBlank()) {
                                index[beforeDash] = file.absolutePath
                            }
                        }
                        // Also index without parentheses content
                        // e.g. "Dandelions (feat. Someone)" → "Dandelions"
                        if (baseName.contains("(")) {
                            val cleanName = baseName.substringBefore("(").trim()
                            if (cleanName.isNotBlank()) {
                                index[cleanName] = file.absolutePath
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Find a lyrics file for the given song title and artist.
     * Returns the raw lyrics text, or null if not found.
     */
    fun findLyrics(title: String, artist: String? = null): String? {
        if (title.isBlank()) return null

        val titleLower = title.lowercase().trim()

        // 1. Exact title match
        index[titleLower]?.let { path ->
            return try { File(path).readText(Charsets.UTF_8) } catch (_: Exception) { null }
        }

        // 2. "Artist - Title" match
        if (artist != null && artist.isNotBlank()) {
            val artistTitle = "${artist.lowercase().trim()} - $titleLower"
            index[artistTitle]?.let { path ->
                return try { File(path).readText(Charsets.UTF_8) } catch (_: Exception) { null }
            }
        }

        // 3. Title without parentheses
        if (titleLower.contains("(")) {
            val cleanTitle = titleLower.substringBefore("(").trim()
            index[cleanTitle]?.let { path ->
                return try { File(path).readText(Charsets.UTF_8) } catch (_: Exception) { null }
            }
        }

        // 4. Title without "feat." / "ft." etc.
        val cleanTitle = titleLower
            .replace(Regex("\\s*\\(feat\\..*?\\)"), "")
            .replace(Regex("\\s*\\(ft\\..*?\\)"), "")
            .replace(Regex("\\s*-\\s*.*$"), "")
            .trim()
        if (cleanTitle != titleLower && cleanTitle.isNotBlank()) {
            index[cleanTitle]?.let { path ->
                return try { File(path).readText(Charsets.UTF_8) } catch (_: Exception) { null }
            }
        }

        // 5. Partial match — search index keys that contain the title
        for ((key, path) in index) {
            if (key.contains(titleLower) || titleLower.contains(key)) {
                return try { File(path).readText(Charsets.UTF_8) } catch (_: Exception) { null }
            }
        }

        return null
    }

    /**
     * Check if the index has been built.
     */
    fun isIndexed(): Boolean = index.isNotEmpty()
}
