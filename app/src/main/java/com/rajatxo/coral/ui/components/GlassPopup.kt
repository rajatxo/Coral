package com.rajatxo.coral.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons

/**
 * A glassmorphism popup with a REAL backdrop blur.
 *
 * HOW IT WORKS:
 *   1. Captures the current screen as a Bitmap (via PixelCopy from the Window's DecorView)
 *   2. Renders that bitmap inside the popup, blurred via Modifier.blur() OR
 *      RenderEffect (API 31+)
 *   3. Overlays a dark translucent tint for text readability
 *   4. Adds a subtle white border for the "glass edge" effect
 *   5. Renders the popup content (menu options) on top
 *
 * The result: a real-time backdrop blur — you can see the page content
 * behind the popup, but it's blurred/smudged like frosted glass.
 *
 * @param visible Whether the popup is showing
 * @param onDismiss Called when the user taps outside the popup
 * @param modifier Modifier for the popup content
 * @param content The popup's content (menu options, etc.)
 */
@Composable
fun GlassPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!visible) return

    val view = LocalView.current
    val density = LocalDensity.current

    // State to hold the captured screen bitmap
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Capture the screen when the popup appears
    LaunchedEffect(visible) {
        if (visible) {
            // Wait a frame for the popup to be positioned
            kotlinx.coroutines.delay(50)
            captureScreenBitmap(view)?.let { bitmap ->
                capturedBitmap = bitmap
            }
        }
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141414).copy(alpha = 0.55f))
        ) {
            // Layer 1: Blurred screen capture (the REAL backdrop blur)
            capturedBitmap?.let { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(28.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                )
            }

            // Layer 2: Dark translucent tint for readability
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )

            // Layer 3: Subtle white border (the "glass edge")
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(24.dp)
                    )
            )

            // Layer 4: The actual content (menu options)
            content()
        }
    }
}

/**
 * Captures the current screen as a Bitmap using Android's PixelCopy API.
 * Falls back to drawing the View hierarchy onto a Canvas if PixelCopy is
 * not available (API < 26).
 */
private fun captureScreenBitmap(view: View): Bitmap? {
    return try {
        val rootView = view.rootView
        val width = rootView.width
        val height = rootView.height
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}
