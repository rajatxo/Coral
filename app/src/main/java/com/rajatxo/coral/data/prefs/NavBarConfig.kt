package com.rajatxo.coral.data.prefs

import android.content.Context
import com.rajatxo.coral.ui.components.CoralTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NavBarConfig — Coral's nav bar customization singleton.
 *
 * Lets the user:
 *   1. Enable/disable each tab (Quick Picks, Songs, Playlists, Albums,
 *      Artists, Discover). Disabled tabs disappear from the nav bar.
 *   2. Reorder the tabs — drag a tab up or down in the settings list
 *      to change its position in the nav bar.
 *   3. Pick which tab opens by default when the app launches.
 *
 * Default order on first install:
 *   Quick Picks → Songs → Playlists → Albums → Artists → Discover
 *
 * Default opening page: Quick Picks (but the user can change it to any
 * enabled tab — e.g. if they prefer Songs to open by default).
 *
 * Persistence: SharedPreferences ('coral_prefs'). The enabled+ordered
 * tab list is stored as a comma-separated list of tab ordinals. The
 * default opening tab is stored as a single ordinal.
 *
 * Versioning: keys are versioned (_v1) so changes to the defaults are
 * picked up by existing users who have stale saved configurations.
 */
object NavBarConfig {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_ORDER = "nav_bar_order_v1"
    private const val KEY_DEFAULT_TAB = "nav_bar_default_tab_v1"

    /**
     * The default tab order for first-install. Per the user's spec:
     *   Quick Picks → Songs → Playlists → Albums → Artists → Discover
     *
     * All tabs are enabled by default — the user can disable ones they
     * don't want from the settings.
     */
    private val DEFAULT_ORDER: List<CoralTab> = listOf(
        CoralTab.QuickPicks,
        CoralTab.Songs,
        CoralTab.Playlists,
        CoralTab.Albums,
        CoralTab.Artists,
        CoralTab.Discover
    )

    private lateinit var prefs: android.content.SharedPreferences

    /** The enabled tabs in their display order (left→right in the nav bar). */
    private val _enabledTabs = MutableStateFlow(DEFAULT_ORDER)
    val enabledTabs: StateFlow<List<CoralTab>> = _enabledTabs.asStateFlow()

    /** Which tab opens when the app launches. Must be in [enabledTabs]. */
    private val _defaultTab = MutableStateFlow(CoralTab.QuickPicks)
    val defaultTab: StateFlow<CoralTab> = _defaultTab.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    /**
     * Loads the saved tab order + default tab from SharedPreferences.
     *
     * The saved order is a comma-separated list of tab ordinals (e.g.
     * "0,2,3,4,5,1" for QuickPicks, Songs, Playlists, Albums, Artists,
     * Discover). Tabs not in the saved list are considered disabled.
     *
     * If the saved data is missing or invalid, falls back to DEFAULT_ORDER.
     */
    private fun load() {
        val orderStr = prefs.getString(KEY_ORDER, null)
        if (orderStr.isNullOrEmpty()) {
            // First install — use defaults.
            _enabledTabs.value = DEFAULT_ORDER
            _defaultTab.value = CoralTab.QuickPicks
            return
        }
        val ordinals = orderStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        val tabs = ordinals.mapNotNull { ord ->
            // Map ordinal → CoralTab. CoralTab.values()[ord] returns null if
            // the ordinal is out of bounds (e.g. an enum value was removed
            // in a future version). We skip invalid ordinals.
            CoralTab.values().firstOrNull { it.ordinal == ord }
        }
        if (tabs.isEmpty()) {
            _enabledTabs.value = DEFAULT_ORDER
        } else {
            _enabledTabs.value = tabs
        }

        // Default tab
        val defaultOrd = prefs.getInt(KEY_DEFAULT_TAB, CoralTab.QuickPicks.ordinal)
        val savedDefault = CoralTab.values().firstOrNull { it.ordinal == defaultOrd }
        // If the saved default isn't in the enabled list (user disabled it),
        // fall back to the first enabled tab so the app always opens on
        // something that exists.
        _defaultTab.value = if (savedDefault != null && savedDefault in _enabledTabs.value) {
            savedDefault
        } else {
            _enabledTabs.value.firstOrNull() ?: CoralTab.QuickPicks
        }
    }

    /**
     * Sets the enabled tabs in their display order. Persists immediately.
     *
     * Also ensures [defaultTab] is still in the list — if the user just
     * disabled the current default, the default falls back to the first
     * enabled tab.
     */
    fun setEnabledTabs(tabs: List<CoralTab>) {
        prefs.edit().putString(KEY_ORDER, tabs.joinToString(",") { it.ordinal.toString() }).apply()
        _enabledTabs.value = tabs
        // If the current default is no longer enabled, fall back.
        if (_defaultTab.value !in tabs) {
            val fallback = tabs.firstOrNull() ?: CoralTab.QuickPicks
            setDefaultTab(fallback)
        }
    }

    /**
     * Enables or disables a single tab.
     *
     * When enabling, the tab is added to the end of the enabled list
     * (rightmost in the nav bar).
     *
     * When disabling, the tab is removed from the list. If the disabled
     * tab was the default opening tab, the default falls back to the
     * first remaining tab.
     */
    fun setTabEnabled(tab: CoralTab, enabled: Boolean) {
        val current = _enabledTabs.value.toMutableList()
        if (enabled) {
            if (tab !in current) current.add(tab)
        } else {
            // Don't allow disabling the last tab — the nav bar would be empty.
            if (current.size <= 1) return
            current.remove(tab)
        }
        setEnabledTabs(current)
    }

    /**
     * Moves a tab one position up (left in the nav bar).
     * No-op if the tab is already at the top.
     */
    fun moveTabUp(tab: CoralTab) {
        val current = _enabledTabs.value.toMutableList()
        val index = current.indexOf(tab)
        if (index <= 0) return
        // Swap with the previous tab.
        current[index] = current[index - 1]
        current[index - 1] = tab
        setEnabledTabs(current)
    }

    /**
     * Moves a tab one position down (right in the nav bar).
     * No-op if the tab is already at the bottom.
     */
    fun moveTabDown(tab: CoralTab) {
        val current = _enabledTabs.value.toMutableList()
        val index = current.indexOf(tab)
        if (index < 0 || index >= current.size - 1) return
        // Swap with the next tab.
        current[index] = current[index + 1]
        current[index + 1] = tab
        setEnabledTabs(current)
    }

    /**
     * Sets which tab opens by default when the app launches.
     * The tab must be in [enabledTabs] — if not, the call is a no-op.
     */
    fun setDefaultTab(tab: CoralTab) {
        if (tab !in _enabledTabs.value) return
        prefs.edit().putInt(KEY_DEFAULT_TAB, tab.ordinal).apply()
        _defaultTab.value = tab
    }

    /**
     * Resets the tab order + default tab to factory defaults.
     * Useful for a "Reset to defaults" button in settings.
     */
    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_ORDER)
            .remove(KEY_DEFAULT_TAB)
            .apply()
        _enabledTabs.value = DEFAULT_ORDER
        _defaultTab.value = CoralTab.QuickPicks
    }
}
