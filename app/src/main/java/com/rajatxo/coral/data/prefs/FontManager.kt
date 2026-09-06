package com.rajatxo.coral.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The available fonts in Coral's font picker.
 *
 * Each font is identified by a stable [id] (stored in prefs) and has a
 * [displayName] (shown in the picker UI) and a [family] that gets applied
 * to the app's MaterialTheme typography.
 *
 * Adding a new font:
 *   1. Drop the .ttf file in res/font/ (lowercase, underscores).
 *   2. Add a FontFamily entry here pointing to it.
 *   3. Add a new [CoralFont] enum entry with a unique id, name, and family.
 *
 * Fonts are loaded synchronously on first access — they're tiny (~150KB each)
 * and Android's font cache keeps them resident.
 */
enum class CoralFont(
    val id: String,
    val displayName: String,
    val family: androidx.compose.ui.text.font.FontFamily
) {
    System(
        id = "system",
        displayName = "System default",
        family = androidx.compose.ui.text.font.FontFamily.Default
    ),
    Poppins(
        id = "poppins",
        displayName = "Poppins",
        family = androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_thin, androidx.compose.ui.text.font.FontWeight.Thin),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_extralight, androidx.compose.ui.text.font.FontWeight.ExtraLight),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_light, androidx.compose.ui.text.font.FontWeight.Light),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_regular, androidx.compose.ui.text.font.FontWeight.Normal),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_medium, androidx.compose.ui.text.font.FontWeight.Medium),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_semibold, androidx.compose.ui.text.font.FontWeight.SemiBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_bold, androidx.compose.ui.text.font.FontWeight.Bold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_extrabold, androidx.compose.ui.text.font.FontWeight.ExtraBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.poppins_black, androidx.compose.ui.text.font.FontWeight.Black)
        )
    ),
    Inter(
        id = "inter",
        displayName = "Inter",
        family = androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.Thin),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.ExtraLight),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.Light),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.Normal),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.Medium),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.SemiBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.Bold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.ExtraBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.inter_variable, androidx.compose.ui.text.font.FontWeight.Black)
        )
    ),
    Manrope(
        id = "manrope",
        displayName = "Manrope",
        family = androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.Thin),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.ExtraLight),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.Light),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.Normal),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.Medium),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.SemiBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.Bold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.ExtraBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.manrope_variable, androidx.compose.ui.text.font.FontWeight.Black)
        )
    ),
    Nunito(
        id = "nunito",
        displayName = "Nunito Sans",
        family = androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.Thin),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.ExtraLight),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.Light),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.Normal),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.Medium),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.SemiBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.Bold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.ExtraBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.nunito_variable, androidx.compose.ui.text.font.FontWeight.Black)
        )
    ),
    SpaceGrotesk(
        id = "spacegrotesk",
        displayName = "Space Grotesk",
        family = androidx.compose.ui.text.font.FontFamily(
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.spacegrotesk_variable, androidx.compose.ui.text.font.FontWeight.Light),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.spacegrotesk_variable, androidx.compose.ui.text.font.FontWeight.Normal),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.spacegrotesk_variable, androidx.compose.ui.text.font.FontWeight.Medium),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.spacegrotesk_variable, androidx.compose.ui.text.font.FontWeight.SemiBold),
            androidx.compose.ui.text.font.Font(com.rajatxo.coral.R.font.spacegrotesk_variable, androidx.compose.ui.text.font.FontWeight.Bold)
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
 * Persistence strategy:
 *  - Uses SharedPreferences (simple key-value, fine for one setting).
 *  - Reads the saved value synchronously on first access (fast — file is
 *    tiny and Android caches SharedPreferences in memory after first read).
 *  - Writes are synchronous too — this is a once-per-app-launch setting,
 *    no need for async I/O.
 *
 * The rest of the app observes [currentFont] as a StateFlow and rebuilds
 * the MaterialTheme typography whenever it changes.
 */
object FontManager {

    private const val PREFS_NAME = "coral_prefs"
    private const val KEY_FONT_ID = "font_id"

    private lateinit var prefs: android.content.SharedPreferences

    private val _currentFont = MutableStateFlow(CoralFont.Poppins)
    val currentFont: StateFlow<CoralFont> = _currentFont.asStateFlow()

    fun init(context: android.content.Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        _currentFont.value = CoralFont.fromId(prefs.getString(KEY_FONT_ID, null))
    }

    fun setFont(font: CoralFont) {
        prefs.edit().putString(KEY_FONT_ID, font.id).apply()
        _currentFont.value = font
    }
}
