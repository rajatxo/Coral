package com.rajatxo.coral.data.prefs

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import com.rajatxo.coral.R
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
 * Two types of fonts:
 *  - **Static fonts** (Poppins): one .ttf file per weight. We list each
 *    weight separately so Compose picks the right file for each FontWeight.
 *  - **Variable fonts** (Inter, Manrope, Nunito, Space Grotesk): one .ttf
 *    file contains ALL weights along a 'wght' axis. We use FontVariation
 *    to tell Compose which weight value (100-900) to use for each
 *    FontWeight we request.
 *
 * The OLD approach was to call Font(R.font.inter_variable, FontWeight.Thin)
 * 9 times — but Compose's basic Font(int, FontWeight) constructor doesn't
 * know how to use the variable font's axis, so it tries to find a static
 * weight that doesn't exist and crashes. The FontVariation approach is
 * the correct way.
 */
@OptIn(ExperimentalTextApi::class)
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
        // 9 static .ttf files — one per weight.
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
    ),
    Inter(
        id = "inter",
        displayName = "Inter",
        // Variable font — single .ttf file. Use FontVariation to pick the
        // weight value (100-900) for each FontWeight we want to support.
        family = FontFamily(
            Font(R.font.inter_variable, weight = FontWeight.Thin, variationSettings = FontVariation.Settings(FontVariation.weight(100))),
            Font(R.font.inter_variable, weight = FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
            Font(R.font.inter_variable, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
            Font(R.font.inter_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.inter_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(R.font.inter_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
            Font(R.font.inter_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
            Font(R.font.inter_variable, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
            Font(R.font.inter_variable, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900)))
        )
    ),
    Manrope(
        id = "manrope",
        displayName = "Manrope",
        // Manrope variable font supports weights 200-800.
        family = FontFamily(
            Font(R.font.manrope_variable, weight = FontWeight.Thin, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
            Font(R.font.manrope_variable, weight = FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
            Font(R.font.manrope_variable, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.manrope_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(R.font.manrope_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
            Font(R.font.manrope_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
            Font(R.font.manrope_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
            Font(R.font.manrope_variable, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
            Font(R.font.manrope_variable, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
        )
    ),
    Nunito(
        id = "nunito",
        displayName = "Nunito Sans",
        family = FontFamily(
            Font(R.font.nunito_variable, weight = FontWeight.Thin, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
            Font(R.font.nunito_variable, weight = FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
            Font(R.font.nunito_variable, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.nunito_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(R.font.nunito_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
            Font(R.font.nunito_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
            Font(R.font.nunito_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
            Font(R.font.nunito_variable, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
            Font(R.font.nunito_variable, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
        )
    ),
    SpaceGrotesk(
        id = "spacegrotesk",
        displayName = "Space Grotesk",
        // Space Grotesk variable font only supports weights 300-700.
        family = FontFamily(
            Font(R.font.spacegrotesk_variable, weight = FontWeight.Thin, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
            Font(R.font.spacegrotesk_variable, weight = FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
            Font(R.font.spacegrotesk_variable, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
            Font(R.font.spacegrotesk_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(R.font.spacegrotesk_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(R.font.spacegrotesk_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
            Font(R.font.spacegrotesk_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
            Font(R.font.spacegrotesk_variable, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
            Font(R.font.spacegrotesk_variable, weight = FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
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
