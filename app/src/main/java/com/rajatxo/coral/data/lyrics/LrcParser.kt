package com.rajatxo.coral.data.lyrics

/**
 * Parses LRC-format synced lyrics into a list of [LyricLine]s.
 *
 * LRC format example:
 *   [00:12.34]First line of lyrics
 *   [00:15.67]Second line
 *   [00:18.90]
 *   [00:21.45]Third line after a gap
 *
 * Timestamps may also appear in other formats:
 *   [mm:ss.xx]    (most common, what LrcLib returns)
 *   [mm:ss.xxx]    (some sources)
 *   [mm:ss]        (no fractional part)
 *
 * Multiple timestamps on a single line are supported:
 *   [00:12.34][00:45.67]Repeated chorus
 *
 * Plain (unsynced) lyrics have no timestamps — they're returned as a single
 * Lyric with synced=false and one LyricLine per text line at timeMs=-1.
 */
object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /**
     * Parse an LRC string into a list of timed lines.
     *
     * Returns an empty list if the input is null/blank or contains no
     * recognisable timestamps AND no plain text lines.
     */
    fun parse(lrcText: String?): List<LyricLine> {
        if (lrcText.isNullOrBlank()) return emptyList()

        val hasTimestamps = TIMESTAMP_REGEX.containsMatchIn(lrcText)

        if (!hasTimestamps) {
            // Plain (unsynced) lyrics — split by newline, no timestamps.
            return lrcText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { LyricLine(timeMs = -1L, text = it) }
        }

        val result = mutableListOf<LyricLine>()

        lrcText.lines().forEach { rawLine ->
            // Skip metadata tags like [ar:Artist], [al:Album], [by:Editor] etc.
            // These look like [xx:value] where xx is letters, not a timestamp.
            if (rawLine.startsWith("[") && !TIMESTAMP_REGEX.containsMatchIn(rawLine)) {
                return@forEach
            }

            val matches = TIMESTAMP_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach

            // The text after the last timestamp on this line
            val lastMatch = matches.last()
            val text = rawLine.substring(lastMatch.range.last + 1).trim()

            // Each timestamp on this line maps to the same text — this handles
            // the [00:12.34][00:45.67]Repeated chorus pattern.
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fracStr = match.groupValues[3]
                val frac = if (fracStr.isEmpty()) 0L
                else when (fracStr.length) {
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.take(3).toLong()
                }
                val timeMs = minutes * 60_000L + seconds * 1000L + frac
                result.add(LyricLine(timeMs = timeMs, text = text))
            }
        }

        // Sort by time so playback lookups are simple.
        return result.sortedBy { it.timeMs }
    }
}
