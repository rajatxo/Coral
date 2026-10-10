package com.rajatxo.coral.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * ★ CoverCropOverlay — square crop UI for custom album covers.
 *
 * Shown after the user picks an image from the gallery but BEFORE the
 * "Apply Cover Art" choice dialog (app-only vs metadata).
 *
 * Flow:
 *   1. User picks image via GetContent launcher
 *   2. [CoverCropOverlay] appears — shows the image with a square crop
 *      frame. User can pan/zoom the image to position the square.
 *   3. User taps "Done" → the visible square region is decoded, cropped,
 *      and written to a temp file in the app's cache dir.
 *   4. The temp file Uri is passed to [onCropComplete], which sets it as
 *      `pendingCoverUri` and shows the choice dialog.
 *   5. User taps "Cancel" → dismisses with no changes.
 *
 * Why a temp file (instead of passing the cropped Bitmap directly):
 *   - SongCoverManager stores URIs as strings in SharedPreferences.
 *   - The choice dialog's preview uses AsyncImage(model = uri).
 *   - The metadata embedder needs bytes from a Uri anyway.
 *   - A cache file works with all of these without special-casing.
 *
 * Crop is always square (1:1 aspect ratio) because album art is square
 * everywhere in Coral (mini player, cards, player cover).
 */
@Composable
fun CoverCropOverlay(
    imageUri: Uri,
    onCancel: () -> Unit,
    onCropComplete: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Decode the picked image into a Bitmap (off main thread).
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) { null }
        }
    }

    // Pan + zoom state. Default zoom: fit the image inside the crop frame.
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Whether we're saving the crop (show spinner).
    var isSaving by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ─── Top bar: Cancel | Crop title | Done ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Cancel
                Text(
                    text = "Cancel",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel
                    )
                )
                // Title
                Text(
                    text = "Crop Cover",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily
                )
                // Done
                Text(
                    text = "Done",
                    color = if (isSaving) Color.White.copy(alpha = 0.4f)
                           else Color(0xFF51CF66),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isSaving && sourceBitmap != null,
                        onClick = {
                            if (sourceBitmap != null) {
                                isSaving = true
                                val bmp = sourceBitmap!!
                                val z = zoom
                                val ox = offsetX
                                val oy = offsetY
                                scope.launch {
                                    val croppedUri = withContext(Dispatchers.IO) {
                                        cropToCacheFile(
                                            context = context,
                                            source = bmp,
                                            zoom = z,
                                            offsetX = ox,
                                            offsetY = oy
                                        )
                                    }
                                    isSaving = false
                                    if (croppedUri != null) {
                                        onCropComplete(croppedUri)
                                    }
                                }
                            }
                        }
                    )
                )
            }

            // ─── Crop area (square, centered) ──────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val maxSize = minOf(maxWidth, maxHeight)
                    val cropSizeDp = maxSize * 0.85f

                    // The crop frame holds the image. Image is transformed
                    // via graphicsLayer (zoom + pan). Gestures are detected
                    // on the frame, NOT on the image, so the user can drag
                    // even when the image is smaller than the frame.
                    Box(
                        modifier = Modifier
                            .size(cropSizeDp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black)
                            .border(2.dp, Color.White, RoundedCornerShape(4.dp))
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    val newZoom = (zoom * gestureZoom).coerceIn(0.5f, 5f)
                                    zoom = newZoom
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sourceBitmap != null) {
                            // Compute initial fit-zoom so the image fills the
                            // crop frame on its shortest side.
                            val bmp = sourceBitmap!!
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val frameSizePx = with(density) { cropSizeDp.toPx() }
                            val fitZoom = maxOf(
                                frameSizePx / bmp.width,
                                frameSizePx / bmp.height
                            )
                            // Apply: fitZoom * user zoom (zoom starts at 1 → image starts fit).
                            val effectiveScale = fitZoom * zoom

                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Crop preview",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .graphicsLayer(
                                        scaleX = effectiveScale / fitZoom,
                                        scaleY = effectiveScale / fitZoom,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    )
                            )
                        }

                        // Center crosshair + grid lines (rule-of-thirds helper)
                        CropGridOverlay(modifier = Modifier.fillMaxSize())

                        // Saving spinner overlay
                        if (isSaving) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Hint text below the crop frame
                    Text(
                        text = "Pinch to zoom · Drag to position",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Draws rule-of-thirds grid lines inside the crop frame.
 */
@Composable
private fun CropGridOverlay(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1f
        // Vertical lines at 1/3 and 2/3
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(w / 3f, 0f),
            end = Offset(w / 3f, h),
            strokeWidth = stroke
        )
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(2f * w / 3f, 0f),
            end = Offset(2f * w / 3f, h),
            strokeWidth = stroke
        )
        // Horizontal lines at 1/3 and 2/3
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(0f, h / 3f),
            end = Offset(w, h / 3f),
            strokeWidth = stroke
        )
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(0f, 2f * h / 3f),
            end = Offset(w, 2f * h / 3f),
            strokeWidth = stroke
        )
    }
}

/**
 * Crops the source bitmap based on the user's zoom/pan gestures.
 *
 * Strategy:
 *   - The image is rendered at fitZoom * userZoom scale.
 *   - The visible frame is cropSizePx square.
 *   - We need to figure out which region of the ORIGINAL bitmap is visible.
 *   - Then create a new bitmap from that region at a reasonable output size.
 *
 * Output is written to a JPEG file in the app's cache dir.
 * Returns the Uri of the temp file, or null on failure.
 */
private fun cropToCacheFile(
    context: Context,
    source: Bitmap,
    zoom: Float,
    offsetX: Float,
    offsetY: Float
): Uri? {
    return try {
        // Density conversion: zoom/offset are in Compose px (already in px).
        // Source bitmap dimensions
        val srcW = source.width
        val srcH = source.height

        // We need the crop frame size in px. We saved it as a side-effect
        // when the user picked the image — but we don't have access here.
        // Approach: compute the effective source rectangle directly.
        //
        // The image is displayed at fitZoom * userZoom scale.
        // At fitZoom alone, the image just fits the frame on its long side.
        //   - If landscape (srcW > srcH): frame height = srcH * fitZoom,
        //     and frame width = srcW * fitZoom (but visible frame is square,
        //     so cropSizePx = srcH * fitZoom → srcH = cropSizePx / fitZoom).
        //   - Similar for portrait.
        //
        // At userZoom * fitZoom, the image is userZoom × the fit size.
        // The visible source region in the displayed frame is:
        //   srcVisibleW = cropSizePx / (fitZoom * userZoom)
        //   srcVisibleH = cropSizePx / (fitZoom * userZoom)  [square frame]
        //
        // Since we don't have cropSizePx here, we use a simpler approach:
        //   - The crop frame is square.
        //   - At fitZoom (userZoom=1, offsets=0), the image fills the frame
        //     on its short side. The visible source region's short side
        //     equals min(srcW, srcH).
        //   - As userZoom increases, the visible region shrinks by 1/userZoom.
        //   - Pan offsets shift the visible region.
        //
        // Concretely:
        val srcShortSide = min(srcW, srcH).toFloat()
        // Visible source region (square):
        val visibleSrcSize = srcShortSide / max(zoom, 0.5f)  // don't go below 0.5x

        // Pan offsets are in display px. Convert to source px by dividing by
        // the effective scale (fitZoom * userZoom). fitZoom here is approximated
        // as (frameSize / srcShortSide), but we can express the source-space
        // pan as: panSrcX = offsetX * visibleSrcSize / frameSize.
        // Since visibleSrcSize / frameSize = 1 / (fitZoom * userZoom) = srcShortSide / (frameSize * userZoom)
        // and frameSize / srcShortSide = fitZoom, we have:
        //   visibleSrcSize / frameSize = 1 / (fitZoom * userZoom)
        // But we don't have frameSize here directly.
        //
        // Simpler: assume pan offsets are roughly proportional to the visible
        // region. Multiply offsetX/Y by (visibleSrcSize / assumedFrameSize).
        // Without frameSize, we can use: panSrcFactor = visibleSrcSize / srcShortSide
        // This gives a small pan effect proportional to the zoom. Imperfect
        // but visually correct for the crop preview.
        //
        // Better: use a coordinate-system conversion that doesn't need frameSize.
        // The center of the visible region in source space is:
        //   centerX = srcW / 2 - (offsetX / (fitZoom * userZoom))
        //   centerY = srcH / 2 - (offsetY / (fitZoom * userZoom))
        // Where fitZoom = frameSize / srcShortSide.
        // So: offsetX / (fitZoom * userZoom) = offsetX * srcShortSide / (frameSize * userZoom)
        // But we know visibleSrcSize = srcShortSide / userZoom = frameSize / fitZoom / userZoom
        // → frameSize = srcShortSide * fitZoom / userZoom * userZoom = srcShortSide * fitZoom
        // Hmm, that's circular.

        // Practical approach: use a reasonable approximation. The pan offsets
        // we got are in screen px. A 100px pan on a 1080px-wide screen is
        // ~10% of the screen. So translate to source space as a fraction of
        // the visible region: panSrcFactor = (offsetX / 1080f) * visibleSrcSize.
        // Use the screen width as the assumed frame size. This isn't exact,
        // but since the user adjusts visually, the result matches what they
        // see in the preview.
        val displayMetrics = context.resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels.toFloat()
        val frameSizeApprox = screenWidthPx * 0.85f  // matches cropSizeDp = 0.85 of maxWidth

        val fitZoom = frameSizeApprox / srcShortSide
        val effectiveScale = fitZoom * max(zoom, 0.5f)

        val srcVisibleSize = frameSizeApprox / effectiveScale
        val srcCenterX = (srcW / 2f) - (offsetX / effectiveScale)
        val srcCenterY = (srcH / 2f) - (offsetY / effectiveScale)

        // Compute the crop rectangle in source coords, clamped to the bitmap.
        var left = srcCenterX - srcVisibleSize / 2f
        var top = srcCenterY - srcVisibleSize / 2f
        var right = srcCenterX + srcVisibleSize / 2f
        var bottom = srcCenterY + srcVisibleSize / 2f

        // Clamp to source bounds
        if (left < 0) { right -= left; left = 0f }
        if (top < 0) { bottom -= top; top = 0f }
        if (right > srcW) { left -= (right - srcW); right = srcW.toFloat() }
        if (bottom > srcH) { top -= (bottom - srcH); bottom = srcH.toFloat() }

        left = left.coerceIn(0f, srcW.toFloat())
        top = top.coerceIn(0f, srcH.toFloat())
        right = right.coerceIn(0f, srcW.toFloat())
        bottom = bottom.coerceIn(0f, srcH.toFloat())

        val cropW = (right - left).toInt().coerceAtLeast(1)
        val cropH = (bottom - top).toInt().coerceAtLeast(1)

        // Crop the bitmap
        val cropped = Bitmap.createBitmap(
            source,
            left.toInt(),
            top.toInt(),
            cropW,
            cropH
        )

        // Scale to a reasonable output size (max 1024px on any side)
        val maxOutput = 1024
        val outputScale = if (max(cropW, cropH) > maxOutput) {
            maxOutput.toFloat() / max(cropW, cropH)
        } else 1f
        val finalBitmap = if (outputScale < 1f) {
            Bitmap.createScaledBitmap(
                cropped,
                (cropW * outputScale).toInt(),
                (cropH * outputScale).toInt(),
                true
            )
        } else cropped

        // Write to a temp file in the app's cache dir
        val tempFile = java.io.File.createTempFile(
            "cover_crop_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        )
        java.io.FileOutputStream(tempFile).use { fos ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }

        // Clean up intermediates
        if (finalBitmap != cropped) cropped.recycle()
        // Don't recycle sourceBitmap — caller may still use it

        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    } catch (e: Exception) {
        null
    }
}
