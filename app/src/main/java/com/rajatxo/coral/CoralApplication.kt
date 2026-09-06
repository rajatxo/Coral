package com.rajatxo.coral

import android.app.Application
import com.rajatxo.coral.data.store.PlaylistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CoralApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise the JSON-backed playlist + favorites store on a
        // background thread. Reading two small JSON files shouldn't take
        // more than ~50ms, but doing it on the main thread can trigger
        // ANRs on slower devices (especially on first launch when the
        // files don't exist yet and the filesystem has to stat them).
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            PlaylistStore.init(this@CoralApplication)
        }
    }
}
