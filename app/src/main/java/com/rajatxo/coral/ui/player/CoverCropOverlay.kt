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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.rajatxo.coral.ui.theme.CalSansFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * ★ CoverCropOverlay — multi-ratio crop UI for custom album covers.
 *
 * Shown after the user picks an image from the gallery but BEFORE the
 * "Apply Cover Art" choice dialog (app-only vs metadata).
 *
 * Flow:
 *   1. User picks image via GetContent launcher
 *   2. [CoverCropOverlay] appears — shows the image inside a crop frame
 *      at the selected aspect ratio. User can pan/zoom the image.
 *   3. User taps "Done" → the visible crop region is decoded, cropped,
 *      and written to a temp JPEG in the app's cache dir.
 *   4. The temp file Uri is passed to [onCropComplete], which sets it as
 *      `pendingCoverUri` and shows the choice dialog.
 *   5. User taps "Cancel" → dismisses with no changes.
 *
 * Aspect ratios (cycled via the row of pills below the crop box):
 *   - 1:1  (square — album art default)
 *   - 3:4  (portrait — vertical wallpaper style)
 *   - 4:3  (landscape)
 *   - 9:16 (tall portrait — phone wallpaper style)
 *   - 16:9 (cinematic landscape)
 *   - 2:3  (portrait — classic photo)
 *   - Free (no constraint — uses the image's own ratio)
 *
 * Why a temp file (instead of passing the cropped Bitmap directly):
 *   - SongCoverManager stores URIs as strings in SharedPreferences.
 *   - The choice dialog's preview uses AsyncImage(model = uri).
 *   - The metadata embedder needs bytes from a Uri anyway.
 *   - A cache file works with all of these without special-casing.
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

    // ★ Aspect ratio state — defaults to 1:1 (square) since album covers
    //   are square everywhere in Coral.
    var selectedRatio by remember { mutableStateOf(CropRatio.SQUARE) }

    // Pan + zoom state. Reset whenever the ratio changes so the image
    // re-fits to the new frame shape.
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Whether we're saving the crop (show spinner).
    var isSaving by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.97f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // ★ Push the whole column below the status bar so Cancel /
                //   Done / Crop title don't get clipped by the system bar.
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
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
                            sourceBitmap?.let { bmp ->
                                isSaving = true
                                val z = zoom
                                val ox = offsetX
                                val oy = offsetY
                                val ratio = selectedRatio
                                scope.launch {
                                    val croppedUri = withContext(Dispatchers.IO) {
                                        cropToCacheFile(
                                            context = context,
                                            source = bmp,
                                            zoom = z,
                                            offsetX = ox,
                                            offsetY = oy,
                                            aspectWidth = ratio.width,
                                            aspectHeight = ratio.height
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

            // ─── Crop area (centered, sized to selected ratio) ──────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ★ Compute crop frame dimensions for the selected ratio.
                    //   - Limit the frame to 85% of the available space on
                    //     BOTH axes so it fits even when the ratio is tall
                    //     (9:16) or wide (16:9).
                    //   - For portrait ratios (h > w), the height is the
                    //     limiting axis. For landscape ratios (w > h), the
                    //     width is the limiting axis.
                    val maxWidthPx = maxWidth
                    val maxHeightPx = maxHeight
                    val frameMaxW = maxWidthPx * 0.88f
                    val frameMaxH = maxHeightPx * 0.88f

                    val ratioValue = if (selectedRatio.height == 0) {
                        // Free ratio — use the source bitmap's ratio
                        val bmp = sourceBitmap
                        if (bmp != null && bmp.height > 0) {
                            bmp.width.toFloat() / bmp.height.toFloat()
                        } else 1f
                    } else {
                        selectedRatio.width.toFloat() / selectedRatio.height.toFloat()
                    }

                    // Try fitting by width first, then check if height fits.
                    var frameW = frameMaxW
                    var frameH = frameW / ratioValue
                    if (frameH > frameMaxH) {
                        frameH = frameMaxH
                        frameW = frameH * ratioValue
                    }

                    // The crop frame holds the image. Image is transformed
                    // via graphicsLayer (zoom + pan). Gestures are detected
                    // on the frame, NOT on the image, so the user can drag
                    // even when the image is smaller than the frame.
                    Box(
                        modifier = Modifier
                            .size(frameW, frameH)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black)
                            .border(2.dp, Color.White, RoundedCornerShape(4.dp))
                            .pointerInput(selectedRatio) {
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
                            val bmp = sourceBitmap!!
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val frameWpx = with(density) { frameW.toPx() }
                            val frameHpx = with(density) { frameH.toPx() }
                            // fitZoom = scale that makes the image JUST cover the frame
                            //   on its shorter side (so no empty bands show).
                            val fitZoomX = frameWpx / bmp.width
                            val fitZoomY = frameHpx / bmp.height
                            val fitZoom = max(fitZoomX, fitZoomY)

                            // ★ When the ratio changes, reset pan/zoom so the
                            //   image re-fits cleanly to the new frame shape.
                            LaunchedEffect(selectedRatio) {
                                zoom = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }

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

                        // Rule-of-thirds grid overlay
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
                }
            }

            // ─── Aspect ratio selector + hint (below the crop box) ──────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Horizontal scrollable row of ratio pills
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp
                    )
                ) {
                    items(CropRatio.ALL) { ratio ->
                        val isSelected = ratio == selectedRatio
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) Color.White
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.White
                                            else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { selectedRatio = ratio }
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = ratio.label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold
                                             else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Hint text
                Text(
                    text = "Pinch to zoom · Drag to position",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Available crop ratios.
 *
 * `height == 0` means "Free" — the image keeps its own aspect ratio.
 */
enum class CropRatio(val label: String, val width: Int, val height: Int) {
    SQUARE("1:1", 1, 1),
    PORTRAIT_3_4("3:4", 3, 4),
    PORTRAIT_2_3("2:3", 2, 3),
    PORTRAIT_9_16("9:16", 9, 16),
    LANDSCAPE_4_3("4:3", 4, 3),
    LANDSCAPE_3_2("3:2", 3, 2),
    LANDSCAPE_16_9("16:9", 16, 9),
    FREE("Free", 0, 0);

    companion object {
        val ALL = listOf(
            SQUARE,
            PORTRAIT_3_4,
            PORTRAIT_2_3,
            PORTRAIT_9_16,
            LANDSCAPE_4_3,
            LANDSCAPE_3_2,
            LANDSCAPE_16_9,
            FREE
        )
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
 * Crops the source bitmap based on the user's zoom/pan + selected ratio.
 *
 * Strategy:
 *   - The image is rendered at fitZoom * userZoom scale.
 *   - The visible frame is frameWpx × frameHpx (in screen px).
 *   - We compute which region of the ORIGINAL bitmap is visible inside
 *     that frame, accounting for the user's pan offsets.
 *   - That region is extracted via [Bitmap.createBitmap] and scaled
 *     down if larger than 1024px on any side.
 *
 * Output is written to a JPEG file in the app's cache dir.
 * Returns the Uri of the temp file, or null on failure.
 *
 * @param aspectWidth ratio width (0 means "use source's own ratio")
 * @param aspectHeight ratio height (0 means "use source's own ratio")
 */
private fun cropToCacheFile(
    context: Context,
    source: Bitmap,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    aspectWidth: Int,
    aspectHeight: Int
): Uri? {
    return try {
        val srcW = source.width
        val srcH = source.height

        // ★ Compute the target output aspect ratio.
        //   If aspectHeight == 0 (Free), use the source bitmap's own ratio.
        val targetRatio = if (aspectWidth == 0 || aspectHeight == 0) {
            srcW.toFloat() / srcH.toFloat()
        } else {
            aspectWidth.toFloat() / aspectHeight.toFloat()
        }

        // ★ The visible region in source coords has the SAME ratio as the
        //   crop frame (which is set to the selected ratio). So we compute
        //   the visible region's width based on the visible height, using
        //   the target ratio.
        //
        // First: compute the "short" fit — how much of the source's shorter
        // side is visible at zoom=1 (the default fit-to-cover state).
        //
        // At fitZoom, the image just covers the frame on its shorter side.
        //   - If the image is landscape (srcW > srcH) and the frame is portrait,
        //     the image fills the frame's height. Visible src height = srcH.
        //   - In general, the visible region's shorter side at zoom=1 equals
        //     min(srcW, srcH). At higher zoom, it shrinks by 1/zoom.
        //
        // For the ratio-aware crop, we need the visible region to have the
        // target ratio. The crop frame itself has the target ratio, so the
        // visible source region (which matches the frame) also has the target
        // ratio.
        //
        // Computing the visible region:
        //   - Visible width (in source px) = frameWpx / (fitZoom * userZoom)
        //   - Visible height (in source px) = frameHpx / (fitZoom * userZoom)
        //   - Visible width / visible height = frameWpx / frameHpx = target ratio ✓
        //
        // We need fitZoom. fitZoom = max(frameWpx / srcW, frameHpx / srcH).
        // We don't know frameWpx/frameHpx directly, but we know their RATIO
        // (targetRatio), and we can compute their absolute values from the
        // screen size (assuming the frame is at most 88% of the smaller
        // screen dimension, like the UI code does).
        //
        // We'll approximate the frame size using the screen dimensions.

        val displayMetrics = context.resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels.toFloat()
        val screenHeightPx = displayMetrics.heightPixels.toFloat()

        // Match the UI logic: frame fits in 88% of available width/height.
        // For portrait ratios, height is the limiting axis. For landscape,
        // width is the limiting axis. We compute both candidates and pick
        // the one that fits.
        val frameMaxW = screenWidthPx * 0.88f
        val frameMaxH = screenHeightPx * 0.55f  // crop area is roughly half the screen tall (rest is for top bar + ratios)

        // Try fitting by width first, then check if height fits.
        var frameWpx = frameMaxW
        var frameHpx = frameWpx / targetRatio
        if (frameHpx > frameMaxH) {
            frameHpx = frameMaxH
            frameWpx = frameHpx * targetRatio
        }

        // fitZoom = scale that makes the image cover the frame (larger axis).
        val fitZoomX = frameWpx / srcW
        val fitZoomY = frameHpx / srcH
        val fitZoom = max(fitZoomX, fitZoomY)
        val effectiveScale = fitZoom * max(zoom, 0.5f)

        // ★ Visible source region dimensions (in source px).
        val srcVisibleW = frameWpx / effectiveScale
        val srcVisibleH = frameHpx / effectiveScale

        // ★ Visible region center in source coords.
        //   offsetX/Y are in screen px. Convert to source px by dividing by
        //   effectiveScale. Positive offset = image moved right/down, which
        //   means the visible region center moves LEFT/UP in source space.
        val srcCenterX = (srcW / 2f) - (offsetX / effectiveScale)
        val srcCenterY = (srcH / 2f) - (offsetY / effectiveScale)

        // Compute the crop rectangle in source coords.
        var left = srcCenterX - srcVisibleW / 2f
        var top = srcCenterY - srcVisibleH / 2f
        var right = srcCenterX + srcVisibleW / 2f
        var bottom = srcCenterY + srcVisibleH / 2f

        // Clamp to source bounds (shift the rectangle if it sticks out).
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
