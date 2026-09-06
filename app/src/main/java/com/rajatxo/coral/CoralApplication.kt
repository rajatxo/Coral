package com.rajatxo.coral

import android.app.Application
import com.rajatxo.coral.data.store.PlaylistStore

class CoralApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise the JSON-backed playlist + favorites store.
        // Safe to call on main thread — it just reads two small files.
        PlaylistStore.init(this)
    }
}
