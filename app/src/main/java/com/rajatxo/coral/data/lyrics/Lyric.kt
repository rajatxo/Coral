package com.rajatxo.coral.data.lyrics

/**
 * One line of synced lyrics.
 *
 * @param timeMs  When this line should appear (epoch-style: 0 = song start).
 * @param text    The lyric text (may be empty — represents an instrumental gap).
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * Parsed lyrics for a single track.
 *
 * @param synced     True if these are timestamped lyrics (vs plain unsynced text).
 * @param lines      Ordered list of [LyricLine]. For plain lyrics, contains
 *                   one entry per line with timeMs = -1.
 * @param source     Where the lyrics came from (cache, network, manual search).
 * @param trackName  Track name as returned by LrcLib (for display in the UI).
 * @param artistName Artist name as returned by LrcLib.
 */
data class Lyric(
    val synced: Boolean,
    val lines: List<LyricLine>,
    val source: LyricSource,
    val trackName: String? = null,
    val artistName: String? = null
) {
    companion object {
        /** Empty lyrics — used as a placeholder while loading or when none found. */
        val Empty = Lyric(
            synced = false,
            lines = emptyList(),
            source = LyricSource.NONE
        )
    }
}

enum class LyricSource {
    CACHE,   // loaded from on-disk cache
    NETWORK, // freshly fetched from LrcLib
    MANUAL,  // user manually picked a match
    NONE     // no lyrics found yet
}
