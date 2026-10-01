package com.rajatxo.coral.ui.cynthia

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shape options for the Cynthia nav bar and search FAB.
 * Each enum value provides a Compose Shape via toComposeShape(size).
 */
enum class CynthiaCustomShape(val displayName: String) {
    PILL("Pill"),
    RECTANGLE("Rectangle"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    SQUIRCLE("Squircle");

    /**
     * Convert to a Compose Shape.
     * @param size The size of the element (used for Circle — makes it fully round).
     */
    fun toComposeShape(cornerRadiusDp: Float, sizeDp: Float): Shape = when (this) {
        PILL -> RoundedCornerShape(50)  // 50% = pill shape
        RECTANGLE -> RoundedCornerShape(0.dp)
        ROUNDED -> RoundedCornerShape(cornerRadiusDp.dp)
        CIRCLE -> RoundedCornerShape(50)  // 50% on a square = circle
        SQUIRCLE -> RoundedCornerShape(cornerRadiusDp.dp)  // similar to rounded but can be tuned
    }
}
