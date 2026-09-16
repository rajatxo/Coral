package com.rajatxo.coral.data.lyrics

/**
 * A single word with timing for word-by-word (karaoke) sync.
 *
 * @param text      The word text.
 * @param startTime Start time in MILLISECONDS (when this word starts being sung).
 * @param endTime   End time in MILLISECONDS (when this word finishes being sung).
 */
data class WordTimestamp(
    val text: String,
    val startTime: Long,
    val endTime: Long
)

/**
 * One line of synced lyrics.
 *
 * @param timeMs  When this line should appear (epoch-style: 0 = song start).
 * @param text    The full line text (may be empty — represents an instrumental gap).
 * @param words   Word-by-word timing for karaoke. Null = line-synced only (no word timing).
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<WordTimestamp>? = null
) {
    /** True if this line has word-by-word timing (karaoke). */
    val hasWordSync: Boolean get() = words != null && words.isNotEmpty()
}

/**
 * Parsed lyrics for a single track.
 *
 * @param synced     True if these are timestamped lyrics (vs plain unsynced text).
 * @param lines      Ordered list of [LyricLine]. For plain lyrics, contains
 *                   one entry per line with timeMs = -1.
 * @param source     Where the lyrics came from (cache, network, manual search).
 * @param trackName  Track name as returned by LrcLib (for display in the UI).
 * @param artistName Artist name as returned by LrcLib.
 * @param hasWordSync True if ANY line has word-by-word timing.
 */
data class Lyric(
    val synced: Boolean,
    val lines: List<LyricLine>,
    val source: LyricSource,
    val trackName: String? = null,
    val artistName: String? = null,
    val hasWordSync: Boolean = false
) {
    companion object {
        val Empty = Lyric(
            synced = false,
            lines = emptyList(),
            source = LyricSource.NONE
        )
    }
}

enum class LyricSource {
    CACHE,
    NETWORK,
    EMBEDDED,
    MANUAL,
    NONE
}
