package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AppUIManager — controls which COMPLETE app UI the user sees.
 *
 * This is NOT the player style (PlayerStyleManager handles that).
 * This controls the ENTIRE app UI — home screen, tabs, nav bar,
 * mini player, pages, everything.
 *
 * Two UIs:
 *   CYNTHIA — the NEW UI (default). Glass morphism-first, built from
 *             scratch. Currently a pure black screen (will be built out).
 *
 *   ASTRA   — the EXISTING UI. Everything we built so far: Spiral player,
 *             glass pill mini player, TabCapsule nav bar, Quick Picks /
 *             Songs / Playlists / Artists / Albums pages with their
 *             current design.
 *
 * When the user selects CYNTHIA, they see the new UI.
 * When the user selects ASTRA, they see the UI we created so far.
 */
object AppUIManager {

    const val CYNTHIA = "Cynthia"
    const val ASTRA = "Astra"

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_APP_UI = "app_ui_v1"

    private lateinit var prefs: android.content.SharedPreferences

    private val _appUI = MutableStateFlow(CYNTHIA)  // ★ Default = CYNTHIA (new UI)
    val appUI: StateFlow<String> = _appUI.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_APP_UI, null)
        if (saved != null) {
            _appUI.value = saved
        }
    }

    fun setAppUI(ui: String) {
        _appUI.value = ui
        prefs.edit().putString(KEY_APP_UI, ui).apply()
    }
}
