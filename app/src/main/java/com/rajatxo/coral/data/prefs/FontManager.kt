package com.rajatxo.coral.data.prefs

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.rajatxo.coral.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The available fonts in Coral's font picker.
 *
 * DELIBERATELY MINIMAL — only 2 entries:
 *  - System default (falls back to Roboto on Android, SF on some devices)
 *  - Poppins (Coral's brand font — same as ViTune)
 *
 * Why only 2?
 *  - Poppins ships as 9 static .ttf files (one per weight). Static fonts
 *    work reliably in all Compose versions with the standard Font(int,
 *    FontWeight) constructor — no experimental APIs, no FontVariation,
 *    no crashes.
 *  - Variable fonts (Inter, Manrope, Nunito, Space Grotesk) require the
 *    FontVariation API which is marked @ExperimentalTextApi and was
 *    crashing on the user's device. Dropping them eliminates the crash
 *    surface entirely. The user said: 'only Poppins, plus system font,
 *    whichever won't crash.'
 *
 * To add a new STATIC font in the future:
 *   1. Drop the .ttf file(s) in res/font/ (lowercase, underscores).
 *   2. Add a FontFamily entry here pointing to it.
 *   3. Add a new CoralFont enum entry with id + displayName + family.
 *
 * Avoid variable fonts unless we deliberately opt into the experimental
 * API and test thoroughly on the target device.
 */
enum class CoralFont(
    val id: String,
    val displayName: String,
    val family: FontFamily
) {
    System(
        id = "system",
        displayName = "System default",
        family = FontFamily.Default
    ),
    Poppins(
        id = "poppins",
        displayName = "Poppins",
        // 9 static .ttf files — one per weight. Standard, non-experimental
        // Font(resId, FontWeight) constructor. No FontVariation, no
        // ExperimentalTextApi opt-in needed, no crashes.
        family = FontFamily(
            Font(R.font.poppins_thin, FontWeight.Thin),
            Font(R.font.poppins_extralight, FontWeight.ExtraLight),
            Font(R.font.poppins_light, FontWeight.Light),
            Font(R.font.poppins_regular, FontWeight.Normal),
            Font(R.font.poppins_medium, FontWeight.Medium),
            Font(R.font.poppins_semibold, FontWeight.SemiBold),
            Font(R.font.poppins_bold, FontWeight.Bold),
            Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
            Font(R.font.poppins_black, FontWeight.Black)
        )
    );

    companion object {
        fun fromId(id: String?): CoralFont =
            values().firstOrNull { it.id == id } ?: Poppins  // default = Poppins
    }
}

/**
 * Singleton that holds the user's font choice as a StateFlow.
 *
 * Persistence: SharedPreferences ('font_id' key in 'coral_prefs').
 * Reads synchronously on init (<1ms), writes synchronously on change.
 */
object FontManager {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_FONT_ID = "font_id"

    private lateinit var prefs: android.content.SharedPreferences

    private val _currentFont = MutableStateFlow(CoralFont.Poppins)
    val currentFont: StateFlow<CoralFont> = _currentFont.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _currentFont.value = CoralFont.fromId(prefs.getString(KEY_FONT_ID, null))
    }

    fun setFont(font: CoralFont) {
        prefs.edit().putString(KEY_FONT_ID, font.id).apply()
        _currentFont.value = font
    }
}
