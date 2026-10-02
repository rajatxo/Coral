package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shape options for the Cynthia nav bar and search FAB.
 * Each enum value provides a Compose Shape via toComposeShape(size).
 *
 * ★ All shapes respect the cornerRadiusDp parameter:
 *   - PILL: uses min(cornerRadiusDp, 50) so it's pill-like but adjustable
 *   - RECTANGLE: cornerRadiusDp (0 = sharp rectangle)
 *   - ROUNDED: cornerRadiusDp
 *   - CIRCLE: min(cornerRadiusDp, 50) so it's circular but adjustable
 *   - SQUIRCLE: cornerRadiusDp
 *
 * This way the corner slider works for ALL shapes — the user can dial in
 * any corner roundness from 0 (sharp) to 50 (fully round) on any shape.
 */
enum class CynthiaCustomShape(val displayName: String) {
    PILL("Pill"),
    RECTANGLE("Rectangle"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    SQUIRCLE("Squircle");

    /**
     * Convert to a Compose Shape.
     * @param cornerRadiusDp The corner radius in dp (from the slider).
     * @param sizeDp The size of the element (unused now, kept for API stability).
     */
    fun toComposeShape(cornerRadiusDp: Float, sizeDp: Float): Shape = when (this) {
        // ★ PILL — use the corner radius, clamped to 0..50 percent so it
        //   stays pill-like but adjustable. 50 = full pill, 0 = sharp.
        PILL -> RoundedCornerShape(cornerRadiusDp.coerceIn(0f, 50f).toInt())
        // ★ RECTANGLE — corner radius in dp (0 = sharp rectangle)
        RECTANGLE -> RoundedCornerShape(cornerRadiusDp.dp)
        // ★ ROUNDED — corner radius in dp
        ROUNDED -> RoundedCornerShape(cornerRadiusDp.dp)
        // ★ CIRCLE — use the corner radius as percent (0..50), so the user
        //   can dial from sharp to fully circular. 50 = perfect circle.
        CIRCLE -> RoundedCornerShape(cornerRadiusDp.coerceIn(0f, 50f).toInt())
        // ★ SQUIRCLE — corner radius in dp (can be tuned for super-elliptical)
        SQUIRCLE -> RoundedCornerShape(cornerRadiusDp.dp)
    }
}
