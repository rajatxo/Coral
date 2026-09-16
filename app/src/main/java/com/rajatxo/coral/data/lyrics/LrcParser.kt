package com.rajatxo.coral.data.lyrics

/**
 * Parses LRC, Enhanced LRC, and TTML format lyrics into [LyricLine]s.
 *
 * Supported formats:
 *  - Standard LRC: [mm:ss.xx]text
 *  - Enhanced LRC (word-by-word): [mm:ss.xx]<mm:ss.xx>word <mm:ss.xx>word
 *  - TTML: <p begin="Xs" end="Ys"><span begin="Xs" end="Ys">word</span>...</p>
 *  - Plain text (unsynced)
 */
object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    // Enhanced LRC word timestamp: <mm:ss.xx> or <mm:ss.xxx>
    private val WORD_TIMESTAMP_REGEX = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")
    // Also supports <milliseconds> format: <12345>
    private val WORD_MS_REGEX = Regex("""<(\d{1,8})>""")

    /**
     * Parse an LRC/enhanced-LRC/TTML string into a list of timed lines.
     * Returns empty list if input is null/blank or contains no recognizable content.
     */
    fun parse(lrcText: String?): List<LyricLine> {
        if (lrcText.isNullOrBlank()) return emptyList()

        // Check if it's TTML
        val trimmed = lrcText.trim()
        if (trimmed.startsWith("<") && (trimmed.contains("<tt") || trimmed.contains("ttml"))) {
            return parseTtml(trimmed)
        }

        val hasTimestamps = TIMESTAMP_REGEX.containsMatchIn(lrcText)

        if (!hasTimestamps) {
            return lrcText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = -1L, text = it) }
        }

        val result = mutableListOf<LyricLine>()

        lrcText.lines().forEach { rawLine ->
            if (rawLine.startsWith("[") && !TIMESTAMP_REGEX.containsMatchIn(rawLine)) {
                return@forEach
            }

            val matches = TIMESTAMP_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach

            val lastMatch = matches.last()
            val textAfterTimestamp = rawLine.substring(lastMatch.range.last + 1).trim()

            // Check for enhanced LRC word timestamps in the text
            val words = parseEnhancedLrcWords(textAfterTimestamp, lastMatch)

            // Clean text: remove word timestamp tags for the display text
            val cleanText = if (words != null) {
                words.joinToString("") { it.text }
            } else {
                textAfterTimestamp
            }

            matches.forEach { match ->
                val timeMs = parseTimestampToMs(match)
                result.add(LyricLine(
                    timeMs = timeMs,
                    text = cleanText,
                    words = words
                ))
            }
        }

        return result.sortedBy { it.timeMs }
    }

    /**
     * Parse enhanced LRC word timestamps from text like:
     * <00:12.34>Hello <00:12.60>world <00:13.00>from
     * Returns null if no word timestamps found (regular LRC).
     */
    private fun parseEnhancedLrcWords(text: String, lineMatch: MatchResult): List<WordTimestamp>? {
        // Check if there are word timestamps
        val hasWordTimestamps = WORD_TIMESTAMP_REGEX.containsMatchIn(text) || WORD_MS_REGEX.containsMatchIn(text)
        if (!hasWordTimestamps) return null

        val words = mutableListOf<WordTimestamp>()
        val lineStartMs = parseTimestampToMs(lineMatch)

        // Try <mm:ss.xx> format first
        val wordMatches = WORD_TIMESTAMP_REGEX.findAll(text).toList()
        if (wordMatches.isNotEmpty()) {
            for (i in wordMatches.indices) {
                val match = wordMatches[i]
                val wordStartMs = parseTimestampToMs(match)
                val wordEndMs = if (i + 1 < wordMatches.size) {
                    parseTimestampToMs(wordMatches[i + 1])
                } else {
                    // Last word: end = line start + 2000ms (default duration)
                    wordStartMs + 2000L
                }

                // Extract word text: everything between this timestamp and the next
                val textStart = match.range.last + 1
                val textEnd = if (i + 1 < wordMatches.size) wordMatches[i + 1].range.first else text.length
                val wordText = text.substring(textStart, textEnd).trim()

                if (wordText.isNotEmpty()) {
                    words.add(WordTimestamp(
                        text = wordText,
                        startTime = wordStartMs,
                        endTime = wordEndMs
                    ))
                }
            }
        }

        // Try <milliseconds> format
        if (words.isEmpty()) {
            val msMatches = WORD_MS_REGEX.findAll(text).toList()
            if (msMatches.isNotEmpty()) {
                for (i in msMatches.indices) {
                    val match = msMatches[i]
                    val wordStartMs = match.groupValues[1].toLong()
                    val wordEndMs = if (i + 1 < msMatches.size) {
                        msMatches[i + 1].groupValues[1].toLong()
                    } else {
                        wordStartMs + 2000L
                    }

                    val textStart = match.range.last + 1
                    val textEnd = if (i + 1 < msMatches.size) msMatches[i + 1].range.first else text.length
                    val wordText = text.substring(textStart, textEnd).trim()

                    if (wordText.isNotEmpty()) {
                        words.add(WordTimestamp(
                            text = wordText,
                            startTime = wordStartMs,
                            endTime = wordEndMs
                        ))
                    }
                }
            }
        }

        return if (words.isNotEmpty()) words else null
    }

    /**
     * Parse a timestamp match (from TIMESTAMP_REGEX or WORD_TIMESTAMP_REGEX) to milliseconds.
     */
    private fun parseTimestampToMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fracStr = match.groupValues[3]
        val frac = if (fracStr.isEmpty()) 0L
        else when (fracStr.length) {
            1 -> fracStr.toLong() * 100
            2 -> fracStr.toLong() * 10
            else -> fracStr.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1000L + frac
    }

    /**
     * Parse TTML format lyrics with word-by-word timing.
     * Format:
     * <tt ...>
     *   <div>
     *     <p begin="12.345s" end="15.000s">
     *       <span begin="12.345s" end="12.820s">Hello</span>
     *       <span begin="12.820s" end="13.300s">world</span>
     *     </p>
     *   </div>
     * </tt>
     */
    private fun parseTtml(ttml: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        // Match <p> tags with begin and end attributes (any order)
        val pRegex = Regex("""<p\s[^>]*?(?:begin|end)="([^"]+)"[^>]*?(?:begin|end)="([^"]+)"[^>]*?>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val spanRegex = Regex("""<span\s[^>]*?(?:begin|end)="([^"]+)"[^>]*?(?:begin|end)="([^"]+)"[^>]*?>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

        pRegex.findAll(ttml).forEach { pMatch ->
            // Determine which group is begin and which is end based on the full match
            val pFullText = pMatch.value
            val beginIdx = pFullText.indexOf("begin=\"")
            val endIdx = pFullText.indexOf("end=\"")
            val (lineStart, lineEnd) = if (beginIdx < endIdx && beginIdx >= 0) {
                parseTtmlTime(pMatch.groupValues[1]) to parseTtmlTime(pMatch.groupValues[2])
            } else {
                parseTtmlTime(pMatch.groupValues[2]) to parseTtmlTime(pMatch.groupValues[1])
            }
            val pContent = pMatch.groupValues[3]

            val spanMatches = spanRegex.findAll(pContent).toList()

            if (spanMatches.isNotEmpty()) {
                // Word-by-word timing from spans
                val words = spanMatches.map { spanMatch ->
                    val spanFullText = spanMatch.value
                    val sBeginIdx = spanFullText.indexOf("begin=\"")
                    val sEndIdx = spanFullText.indexOf("end=\"")
                    val (wordStart, wordEnd) = if (sBeginIdx < sEndIdx && sBeginIdx >= 0) {
                        parseTtmlTime(spanMatch.groupValues[1]) to parseTtmlTime(spanMatch.groupValues[2])
                    } else {
                        parseTtmlTime(spanMatch.groupValues[2]) to parseTtmlTime(spanMatch.groupValues[1])
                    }
                    val wordText = spanMatch.groupValues[3]
                        .replace(Regex("<[^>]+>"), "")  // strip nested tags
                        .trim()
                    WordTimestamp(
                        text = wordText,
                        startTime = wordStart,
                        endTime = wordEnd
                    )
                }.filter { it.text.isNotEmpty() }

                val fullText = words.joinToString("") { it.text }
                result.add(LyricLine(
                    timeMs = lineStart,
                    text = fullText,
                    words = words.ifEmpty { null }
                ))
            } else {
                // No spans — just use the paragraph text
                val cleanText = pContent.replace(Regex("<[^>]+>"), "").trim()
                if (cleanText.isNotEmpty()) {
                    result.add(LyricLine(
                        timeMs = lineStart,
                        text = cleanText,
                        words = null
                    ))
                }
            }
        }

        return result.sortedBy { it.timeMs }
    }

    /**
     * Parse a TTML time string to MILLISECONDS.
     * Supports: "12.345s", "12345ms", "12.345" (seconds), "0:12.345" (mm:ss.ms)
     */
    private fun parseTtmlTime(timeStr: String): Long {
        return when {
            timeStr.endsWith("ms") -> {
                timeStr.dropLast(2).toLongOrNull() ?: 0L
            }
            timeStr.endsWith("s") -> {
                val seconds = timeStr.dropLast(1).toDoubleOrNull() ?: 0.0
                (seconds * 1000).toLong()
            }
            timeStr.contains(":") -> {
                // mm:ss.ms format
                val parts = timeStr.split(":")
                if (parts.size == 2) {
                    val minutes = parts[0].toLongOrNull() ?: 0L
                    val seconds = parts[1].toDoubleOrNull() ?: 0.0
                    (minutes * 60_000L + (seconds * 1000).toLong())
                } else 0L
            }
            else -> {
                val seconds = timeStr.toDoubleOrNull() ?: 0.0
                (seconds * 1000).toLong()
            }
        }
    }
}
