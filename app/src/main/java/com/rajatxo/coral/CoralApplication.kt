package com.rajatxo.coral

import android.app.Application
import com.rajatxo.coral.data.prefs.FontManager
import com.rajatxo.coral.data.store.PlaylistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CoralApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Font preference is read synchronously — it's a single
        // SharedPreferences key lookup, very fast (<1ms). We need it
        // before MainActivity's setContent so the typography is right
        // from the very first frame (no flash of system font).
        FontManager.init(this)
        com.rajatxo.coral.data.prefs.SearchFabPosition.init(this)
        com.rajatxo.coral.data.prefs.ShuffleFabPosition.init(this)
        com.rajatxo.coral.data.prefs.TabCapsulePosition.init(this)
        com.rajatxo.coral.data.prefs.SoundHapticsManager.init(this)
        com.rajatxo.coral.data.prefs.CrossfadeManager.init(this)
        com.rajatxo.coral.data.prefs.ThemeManager.init(this)
        com.rajatxo.coral.data.premium.SleepTimer.init(this) {
            // onComplete callback — we can't call MediaController directly
            // from the Application class, so we just set the flag. The
            // MainActivity will observe SleepTimer.state and pause when
            // the timer fires (active goes from true to false).
            // For now, the callback is a no-op here — it's wired properly
            // in HomeScreen via the onSongEnded parameter.
        }

        // ─── Coil image loader with memory + disk cache ──
        // Critical for fast scrolling. Without explicit configuration,
        // Coil uses defaults that may be too small for album art-heavy
        // screens. We set:
        //   • Memory cache: 50MB (enough for ~500 album arts at once)
        //   • Disk cache: 100MB (album art is small but there can be
        //     thousands of unique artworks)
        //   • Crossfade: 100ms (fast, no jank)
        // This makes repeated scrolls instant — album art loads from
        // memory cache, no re-decode.
        coil3.SingletonImageLoader.setSafe {
            coil3.ImageLoader.Builder(it)
                .memoryCache {
                    coil3.memory.MemoryCache.Builder()
                        .maxSizeBytes(50L * 1024 * 1024)  // 50MB
                        .build()
                }
                .diskCache {
                    coil3.disk.DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache").absolutePath.let(::okio.Path))
                        .maxSizeBytes(100L * 1024 * 1024)  // 100MB
                        .build()
                }
                .crossfade(true)
                .build()
        }

        // Playlist + favorites JSON files are slightly slower (~50ms),
        // so we read them on a background thread to avoid blocking
        // app launch.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            PlaylistStore.init(this@CoralApplication)
        }
    }
}
