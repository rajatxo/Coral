package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * SearchHistory — persists search history + pinned searches.
 *
 * Stores up to 20 recent searches and up to 5 pinned searches
 * in SharedPreferences as JSON.
 *
 * Also tracks whether the onboarding guide has been shown.
 */
object SearchHistory {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_HISTORY = "search_history_v1"
    private const val KEY_PINNED = "search_pinned_v1"
    private const val KEY_GUIDE_SHOWN = "search_guide_shown_v1"

    private const val MAX_HISTORY = 20
    private const val MAX_PINS = 5

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SearchEntry(
        val query: String,
        val isPlaylist: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private lateinit var prefs: android.content.SharedPreferences

    private val _history = MutableStateFlow<List<SearchEntry>>(emptyList())
    val history: StateFlow<List<SearchEntry>> = _history.asStateFlow()

    private val _pinned = MutableStateFlow<List<SearchEntry>>(emptyList())
    val pinned: StateFlow<List<SearchEntry>> = _pinned.asStateFlow()

    private val _guideShown = MutableStateFlow(false)
    val guideShown: StateFlow<Boolean> = _guideShown.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        _history.value = try {
            prefs.getString(KEY_HISTORY, null)?.let {
                json.decodeFromString(ListSerializer(SearchEntry.serializer()), it)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        _pinned.value = try {
            prefs.getString(KEY_PINNED, null)?.let {
                json.decodeFromString(ListSerializer(SearchEntry.serializer()), it)
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        _guideShown.value = prefs.getBoolean(KEY_GUIDE_SHOWN, false)
    }

    fun addSearch(query: String, isPlaylist: Boolean) {
        if (query.isBlank()) return
        val entry = SearchEntry(query = query.trim(), isPlaylist = isPlaylist)
        // Remove duplicates (same query + same type)
        val updated = (listOf(entry) + _history.value.filter {
            !(it.query.equals(entry.query, ignoreCase = true) && it.isPlaylist == entry.isPlaylist)
        }).take(MAX_HISTORY)
        _history.value = updated
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(
            ListSerializer(SearchEntry.serializer()), updated
        )).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun removeHistoryEntry(query: String, isPlaylist: Boolean) {
        _history.value = _history.value.filterNot {
            it.query.equals(query, ignoreCase = true) && it.isPlaylist == isPlaylist
        }
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(
            ListSerializer(SearchEntry.serializer()), _history.value
        )).apply()
    }

    fun togglePin(query: String, isPlaylist: Boolean) {
        val existing = _pinned.value.find {
            it.query.equals(query, ignoreCase = true) && it.isPlaylist == isPlaylist
        }
        val updated = if (existing != null) {
            // Unpin
            _pinned.value.filterNot {
                it.query.equals(query, ignoreCase = true) && it.isPlaylist == isPlaylist
            }
        } else {
            // Pin — add to front, keep max 5
            listOf(SearchEntry(query.trim(), isPlaylist)) + _pinned.value
        }.take(MAX_PINS)
        _pinned.value = updated
        prefs.edit().putString(KEY_PINNED, json.encodeToString(
            ListSerializer(SearchEntry.serializer()), updated
        )).apply()
    }

    fun isPinned(query: String, isPlaylist: Boolean): Boolean {
        return _pinned.value.any {
            it.query.equals(query, ignoreCase = true) && it.isPlaylist == isPlaylist
        }
    }

    fun markGuideShown() {
        _guideShown.value = true
        prefs.edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
    }
}
