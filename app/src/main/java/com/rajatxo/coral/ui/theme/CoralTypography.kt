package com.rajatxo.coral.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.R

/**
 * Poppins — Coral's brand font, same as ViTune.
 *
 * Loaded from res/font/. We bundle 4 weights: Regular (400), Medium (500),
 * SemiBold (600), Bold (700). Compose's FontFamily picks the closest match
 * for any FontWeight we request.
 *
 * Poppins is licensed under the SIL Open Font License (OFL) — fully free
 * for commercial use, including bundling in closed-source apps. No
 * attribution required in the UI, but we credit it in the About screen.
 */
val CoralFontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

/**
 * Coral's type scale.
 *
 * ViTune uses big, bold titles (typically 28-32sp Bold) at the top of each
 * screen. We mirror that here — the `headlineMedium` style is what each
 * screen's title uses (e.g. "Songs", "Playlists", "Quick Picks").
 *
 * Body text stays at 14-16sp Medium for readability. Labels go down to 11sp
 * for tiny all-caps section headers (like "PRESETS", "PREMIUM").
 */
val CoralTypography = Typography(
    // Big screen titles — "Songs", "Playlists", etc.
    headlineMedium = TextStyle(
        fontFamily = CoralFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    // Smaller headers
    headlineSmall = TextStyle(
        fontFamily = CoralFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // Song titles in lists
    titleMedium = TextStyle(
        fontFamily = CoralFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    // Subtitles, artist names
    titleSmall = TextStyle(
        fontFamily = CoralFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    // Body text in dialogs, descriptions
    bodyMedium = TextStyle(
        fontFamily = CoralFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Small labels (time, duration, hints)
    bodySmall = TextStyle(
        fontFamily = CoralFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // All-caps section headers ("PREMIUM", "BANDS")
    labelMedium = TextStyle(
        fontFamily = CoralFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp
    ),
    // Mini player title
    labelLarge = TextStyle(
        fontFamily = CoralFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
)
