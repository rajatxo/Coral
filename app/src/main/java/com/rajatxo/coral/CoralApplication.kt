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

        // Playlist + favorites JSON files are slightly slower (~50ms),
        // so we read them on a background thread to avoid blocking
        // app launch.
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            PlaylistStore.init(this@CoralApplication)
        }
    }
}
