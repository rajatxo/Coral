package com.rajatxo.coral.data.prefs

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
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
            Font(R.font.inter_variable, FontWeight.Thin, FontVariation.Settings(FontVariation.weight(100f))),
            Font(R.font.inter_variable, FontWeight.ExtraLight, FontVariation.Settings(FontVariation.weight(200f))),
            Font(R.font.inter_variable, FontWeight.Light, FontVariation.Settings(FontVariation.weight(300f))),
            Font(R.font.inter_variable, FontWeight.Normal, FontVariation.Settings(FontVariation.weight(400f))),
            Font(R.font.inter_variable, FontWeight.Medium, FontVariation.Settings(FontVariation.weight(500f))),
            Font(R.font.inter_variable, FontWeight.SemiBold, FontVariation.Settings(FontVariation.weight(600f))),
            Font(R.font.inter_variable, FontWeight.Bold, FontVariation.Settings(FontVariation.weight(700f))),
            Font(R.font.inter_variable, FontWeight.ExtraBold, FontVariation.Settings(FontVariation.weight(800f))),
            Font(R.font.inter_variable, FontWeight.Black, FontVariation.Settings(FontVariation.weight(900f)))
        )
    ),
    Manrope(
        id = "manrope",
        displayName = "Manrope",
        // Manrope variable font supports weights 200-800.
        family = FontFamily(
            Font(R.font.manrope_variable, FontWeight.Thin, FontVariation.Settings(FontVariation.weight(200f))),
            Font(R.font.manrope_variable, FontWeight.ExtraLight, FontVariation.Settings(FontVariation.weight(300f))),
            Font(R.font.manrope_variable, FontWeight.Light, FontVariation.Settings(FontVariation.weight(400f))),
            Font(R.font.manrope_variable, FontWeight.Normal, FontVariation.Settings(FontVariation.weight(500f))),
            Font(R.font.manrope_variable, FontWeight.Medium, FontVariation.Settings(FontVariation.weight(600f))),
            Font(R.font.manrope_variable, FontWeight.SemiBold, FontVariation.Settings(FontVariation.weight(700f))),
            Font(R.font.manrope_variable, FontWeight.Bold, FontVariation.Settings(FontVariation.weight(800f))),
            Font(R.font.manrope_variable, FontWeight.ExtraBold, FontVariation.Settings(FontVariation.weight(700f))),
            Font(R.font.manrope_variable, FontWeight.Black, FontVariation.Settings(FontVariation.weight(800f)))
        )
    ),
    Nunito(
        id = "nunito",
        displayName = "Nunito Sans",
        family = FontFamily(
            Font(R.font.nunito_variable, FontWeight.Thin, FontVariation.Settings(FontVariation.weight(200f))),
            Font(R.font.nunito_variable, FontWeight.ExtraLight, FontVariation.Settings(FontVariation.weight(300f))),
            Font(R.font.nunito_variable, FontWeight.Light, FontVariation.Settings(FontVariation.weight(400f))),
            Font(R.font.nunito_variable, FontWeight.Normal, FontVariation.Settings(FontVariation.weight(500f))),
            Font(R.font.nunito_variable, FontWeight.Medium, FontVariation.Settings(FontVariation.weight(600f))),
            Font(R.font.nunito_variable, FontWeight.SemiBold, FontVariation.Settings(FontVariation.weight(700f))),
            Font(R.font.nunito_variable, FontWeight.Bold, FontVariation.Settings(FontVariation.weight(800f))),
            Font(R.font.nunito_variable, FontWeight.ExtraBold, FontVariation.Settings(FontVariation.weight(700f))),
            Font(R.font.nunito_variable, FontWeight.Black, FontVariation.Settings(FontVariation.weight(800f)))
        )
    ),
    SpaceGrotesk(
        id = "spacegrotesk",
        displayName = "Space Grotesk",
        // Space Grotesk variable font only supports weights 300-700.
        family = FontFamily(
            Font(R.font.spacegrotesk_variable, FontWeight.Thin, FontVariation.Settings(FontVariation.weight(300f))),
            Font(R.font.spacegrotesk_variable, FontWeight.ExtraLight, FontVariation.Settings(FontVariation.weight(300f))),
            Font(R.font.spacegrotesk_variable, FontWeight.Light, FontVariation.Settings(FontVariation.weight(300f))),
            Font(R.font.spacegrotesk_variable, FontWeight.Normal, FontVariation.Settings(FontVariation.weight(400f))),
            Font(R.font.spacegrotesk_variable, FontWeight.Medium, FontVariation.Settings(FontVariation.weight(500f))),
            Font(R.font.spacegrotesk_variable, FontWeight.SemiBold, FontVariation.Settings(FontVariation.weight(600f))),
            Font(R.font.spacegrotesk_variable, FontWeight.Bold, FontVariation.Settings(FontVariation.weight(700f))),
            Font(R.font.spacegrotesk_variable, FontWeight.ExtraBold, FontVariation.Settings(FontVariation.weight(700f))),
            Font(R.font.spacegrotesk_variable, FontWeight.Black, FontVariation.Settings(FontVariation.weight(700f)))
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
