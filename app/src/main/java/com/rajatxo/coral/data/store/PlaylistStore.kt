package com.rajatxo.coral.data.store

import android.content.Context
import com.rajatxo.coral.data.model.Favorites
import com.rajatxo.coral.data.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Single source of truth for playlists and favorites.
 *
 * Persistence strategy:
 *  - All data lives in two JSON files in the app's internal storage:
 *      playlists.json   -> List<Playlist>
 *      favorites.json   -> Favorites
 *  - On init, both files are loaded into memory-backed StateFlows.
 *  - Every mutation writes the entire file back to disk. This is fine for
 *    Coral's scale (~10s of playlists, ~100s of favorites).
 *
 * This is a singleton (object) initialised with [init] from CoralApplication.
 * Using a singleton + StateFlow is simpler than wiring up a DI framework
 * for what is essentially a single-user offline app.
 */
object PlaylistStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private lateinit var appContext: Context

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _favorites = MutableStateFlow<Favorites>(Favorites())
    val favorites: StateFlow<Favorites> = _favorites.asStateFlow()

    private val playlistsFile get() = java.io.File(appContext.filesDir, "playlists.json")
    private val favoritesFile get() = java.io.File(appContext.filesDir, "favorites.json")

    fun init(context: Context) {
        appContext = context.applicationContext
        loadPlaylists()
        loadFavorites()
    }

    // ---------- Playlists ----------

    private fun loadPlaylists() {
        _playlists.value = try {
            if (playlistsFile.exists()) {
                json.decodeFromString<List<Playlist>>(playlistsFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistPlaylists() {
        try {
            playlistsFile.writeText(json.encodeToString(_playlists.value))
        } catch (_: Exception) { /* best-effort */ }
    }

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(
            id = System.currentTimeMillis(),
            name = name.trim().ifEmpty { "Untitled playlist" }
        )
        _playlists.value = _playlists.value + playlist
        persistPlaylists()
        return playlist
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        _playlists.value = _playlists.value.map { p ->
            if (p.id == playlistId) p.copy(name = newName.trim().ifEmpty { "Untitled playlist" }) else p
        }
        persistPlaylists()
    }

    fun deletePlaylist(playlistId: Long) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        persistPlaylists()
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        _playlists.value = _playlists.value.map { p ->
            if (p.id == playlistId && songId !in p.songIds) {
                p.copy(songIds = p.songIds + songId)
            } else p
        }
        persistPlaylists()
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        _playlists.value = _playlists.value.map { p ->
            if (p.id == playlistId) {
                val merged = (p.songIds + songIds).distinct()
                p.copy(songIds = merged)
            } else p
        }
        persistPlaylists()
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        _playlists.value = _playlists.value.map { p ->
            if (p.id == playlistId) p.copy(songIds = p.songIds - songId) else p
        }
        persistPlaylists()
    }

    fun setPlaylistCover(playlistId: Long, coverUri: String?) {
        _playlists.value = _playlists.value.map { p ->
            if (p.id == playlistId) p.copy(coverUri = coverUri) else p
        }
        persistPlaylists()
    }

    fun getPlaylist(playlistId: Long): Playlist? =
        _playlists.value.firstOrNull { it.id == playlistId }

    // ---------- Favorites ----------

    private fun loadFavorites() {
        _favorites.value = try {
            if (favoritesFile.exists()) {
                json.decodeFromString<Favorites>(favoritesFile.readText())
            } else Favorites()
        } catch (e: Exception) {
            Favorites()
        }
    }

    private fun persistFavorites() {
        try {
            favoritesFile.writeText(json.encodeToString(_favorites.value))
        } catch (_: Exception) { /* best-effort */ }
    }

    fun toggleFavorite(songId: Long): Boolean {
        val current = _favorites.value.songIds
        val nowFavorite = songId !in current
        val newSet = if (nowFavorite) current + songId else current - songId
        _favorites.value = Favorites(songIds = newSet)
        persistFavorites()
        return nowFavorite
    }

    fun isFavorite(songId: Long): Boolean = songId in _favorites.value.songIds
}
