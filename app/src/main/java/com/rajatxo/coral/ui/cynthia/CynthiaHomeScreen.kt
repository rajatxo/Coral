package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * CynthiaHomeScreen — the NEW app UI.
 *
 * Currently a pure black screen for all tabs. This is the starting
 * point — we'll build out the glass morphism pages on top of this.
 *
 * When the user selects "Cynthia" in Settings → App UI, they see this.
 * When they select "Astra", they see the existing HomeScreen (everything
 * we built so far).
 */
@Composable
fun CynthiaHomeScreen(
    selectedTab: String = "Quick Picks"
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Cynthia",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            fontFamily = CalSansFamily
        )
    }
}
