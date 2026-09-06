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
 * Coral's type scale — ALL styles use Poppins.
 *
 * IMPORTANT: We override EVERY style in Material 3's Typography (display,
 * headline, title, body, label — large/medium/small for each). If any
 * style is left at its default, Text() calls that pick up that style
 * will fall back to Roboto instead of Poppins.
 *
 * ViTune uses big, bold titles (typically 28-32sp Bold) at the top of each
 * screen. We mirror that here.
 */
private val poppins = CoralFontFamily  // alias for brevity

val CoralTypography = Typography(
    // Display styles (rarely used in Coral — largest headlines)
    displayLarge = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),

    // Headline styles — screen titles use headlineMedium
    headlineLarge = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),

    // Title styles — song titles, dialog titles
    titleLarge = TextStyle(fontFamily = poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),

    // Body styles — descriptions, dialog text
    bodyLarge = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),

    // Label styles — buttons, captions, all-caps section headers
    labelLarge = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontFamily = poppins, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp)
)
