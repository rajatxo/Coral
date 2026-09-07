package com.rajatxo.coral.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.theme.QuirkFontFamily

/**
 * Shared tab header with auto-adjustable sleep timer capsule.
 *
 * Layout (left to right):
 *   [SleepTimerCapsule (weight=1f, auto-fills space)] [gap=12dp] [Big Title Text]
 *
 * The capsule uses weight(1f) so it automatically fills the available space
 * before the title text. The gap is always 12dp. This makes the capsule
 * auto-adjust its width per tab — short titles (Songs) get a wider capsule,
 * long titles (Quick picks) get a narrower capsule.
 *
 * @param title         The big tab title text (e.g., "Songs", "Quick picks")
 * @param capsuleVisible Whether the sleep timer capsule should show
 * @param capsuleRemaining Remaining time in ms for the capsule
 * @param onExtend       Called when "+10" is tapped
 */
@Composable
fun TabHeader(
    title: String,
    capsuleVisible: Boolean = false,
    capsuleRemaining: Long = 0L,
    onExtend: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sleep timer capsule — auto-fills available space
        SleepTimerCapsule(
            visible = capsuleVisible,
            remainingMs = capsuleRemaining,
            onExtend = onExtend,
            modifier = Modifier.weight(1f)
        )

        // Gentle gap between capsule and title
        if (capsuleVisible && capsuleRemaining > 0) {
            Spacer(modifier = Modifier.size(12.dp))
        }

        // Big title text (right-aligned)
        Text(
            text = title,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = QuirkFontFamily
        )
    }
}
