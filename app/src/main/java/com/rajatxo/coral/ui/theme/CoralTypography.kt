package com.rajatxo.coral.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.R
import com.rajatxo.coral.data.prefs.CoralFont
import com.rajatxo.coral.data.prefs.FontManager

/**
 * Poppins Medium Italic — used ONLY for the big tab titles on the top-right
 * corner of each screen (Quick picks, Discover, Songs, Playlists, Artists,
 * Albums, Folders, Settings).
 *
 * This is a distinctive italic display font that gives Coral its brand identity
 * on the screen titles. Everything else in the app uses the user-selected font
 * (Poppins regular by default) via MaterialTheme typography.
 *
 * Poppins is licensed under the SIL Open Font License (OFL) — fully free for
 * commercial use, including bundling in closed-source apps.
 *
 * The font is bundled in res/font/poppins_medium_italic.ttf.
 */
val QuirkFontFamily = FontFamily(
    Font(R.font.poppins_medium_italic, FontWeight.Normal),
    Font(R.font.poppins_medium_italic, FontWeight.Bold),
    Font(R.font.poppins_medium_italic, FontWeight.SemiBold),
    Font(R.font.poppins_medium_italic, FontWeight.Medium)
)

/**
 * Coral's type scale — dynamic, generated from the user's font choice.
 *
 * The user picks a font in Settings → Appearance → Font. [FontManager]
 * holds the choice as a StateFlow. When the choice changes, MainActivity
 * collects it and rebuilds the MaterialTheme typography via this function.
 *
 * All 15 Material 3 typography styles are overridden so EVERY Text() in
 * the app renders in the selected font. (We learned this the hard way —
 * leaving any style at its default caused Text() calls without an
 * explicit style to fall back to Roboto.)
 *
 * ViTune uses big, bold titles (typically 28-32sp Bold) at the top of
 * each screen. We mirror that here.
 */
fun coralTypographyFor(font: CoralFont): Typography {
    val family: FontFamily = font.family
    return Typography(
        // Display styles (rarely used in Coral — largest headlines)
        displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),

        // Headline styles — screen titles use headlineMedium
        headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),

        // Title styles — song titles, dialog titles
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),

        // Body styles — descriptions, dialog text
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

        // Label styles — buttons, captions, all-caps section headers
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp)
    )
}

/** Default typography (Poppins). Used before FontManager.init() runs. */
val CoralTypography: Typography = coralTypographyFor(CoralFont.Poppins)
