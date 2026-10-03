package com.rajatxo.coral.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the user's name for the greeting + mood header.
 * Default is empty string — greeting falls back to no name.
 */
object UserName {
    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_NAME = "user_name_v1"

    private const val DEFAULT_NAME = ""

    private lateinit var prefs: android.content.SharedPreferences

    private val _name = MutableStateFlow(DEFAULT_NAME)
    val name: StateFlow<String> = _name.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _name.value = prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
    }

    fun setName(name: String) {
        prefs.edit().putString(KEY_NAME, name.trim()).apply()
        _name.value = name.trim()
    }
}
