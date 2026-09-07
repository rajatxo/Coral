package com.rajatxo.coral.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Coral's custom icon set.
 *
 * All glyphs are hand-drawn vector paths in the Lucide (MIT) visual idiom:
 *  - 24 x 24 viewport
 *  - 2dp stroke width
 *  - round caps & joins
 *  - filled variants use SolidColor fill
 *
 * Keeping these in-house (instead of pulling Material Icons or Lucide as a
 * dependency) keeps Coral's dependency tree 100 % permissively licensed and
 * avoids the GPL-tainted Material Icons asset pack.
 */
object CoralIcons {

    private fun stroke(
        name: String,
        pathBuilder: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 4f,
            pathBuilder = pathBuilder
        )
    }.build()

    private fun filled(
        name: String,
        pathBuilder: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathBuilder = pathBuilder
        )
    }.build()

    /** Single musical note. Used as the "Songs" tab icon. */
    val Music: ImageVector = stroke("Music") {
        // stem + cross-bar
        moveTo(9f, 18f)
        verticalLineToRelative(-13f)
        lineToRelative(12f, -2f)
        verticalLineToRelative(13f)
        // left note head (open circle)
        moveTo(9f, 18f)
        arcToRelative(3f, 3f, 0f, true, false, -0.01f, 0f)
        // right note head
        moveTo(21f, 16f)
        arcToRelative(3f, 3f, 0f, true, false, -0.01f, 0f)
    }

    /** Stacked playlist lines with a small note. Used as the "Playlists" tab icon. */
    val ListMusic: ImageVector = stroke("ListMusic") {
        // note head
        moveTo(21f, 18f)
        arcToRelative(1.5f, 1.5f, 0f, true, false, -0.01f, 0f)
        // stem
        moveTo(18f, 18f)
        verticalLineToRelative(-13f)
        lineToRelative(3f, -1f)
        // left playlist lines
        moveTo(3f, 6f)
        horizontalLineTo(11f)
        moveTo(3f, 12f)
        horizontalLineTo(11f)
        moveTo(3f, 18f)
        horizontalLineTo(10f)
    }

    /** Sliders icon — used as the "Settings" tab icon. */
    val Settings: ImageVector = stroke("Settings") {
        // top horizontal — split by knob
        moveTo(3f, 5f)
        horizontalLineTo(14f)
        moveTo(16f, 5f)
        horizontalLineTo(21f)
        // middle horizontal
        moveTo(3f, 12f)
        horizontalLineTo(8f)
        moveTo(10f, 12f)
        horizontalLineTo(21f)
        // bottom horizontal
        moveTo(3f, 19f)
        horizontalLineTo(14f)
        moveTo(16f, 19f)
        horizontalLineTo(21f)
        // 3 vertical "knobs"
        moveTo(15f, 3f)
        verticalLineToRelative(4f)
        moveTo(9f, 10f)
        verticalLineToRelative(4f)
        moveTo(15f, 17f)
        verticalLineToRelative(4f)
    }

    /** Filled play triangle. */
    val Play: ImageVector = filled("Play") {
        moveTo(7f, 4f)
        lineTo(20f, 12f)
        lineTo(7f, 20f)
        close()
    }

    /** Two vertical pause bars. */
    val Pause: ImageVector = filled("Pause") {
        moveTo(6f, 4f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(16f)
        horizontalLineToRelative(-4f)
        close()
        moveTo(14f, 4f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(16f)
        horizontalLineToRelative(-4f)
        close()
    }

    /** Skip to next — triangle + bar. */
    val SkipNext: ImageVector = filled("SkipNext") {
        moveTo(5f, 4f)
        lineTo(17f, 12f)
        lineTo(5f, 20f)
        close()
        moveTo(18f, 4f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(16f)
        horizontalLineToRelative(-2f)
        close()
    }

    /** Skip to previous — bar + triangle. */
    val SkipPrev: ImageVector = filled("SkipPrev") {
        moveTo(19f, 4f)
        lineTo(7f, 12f)
        lineTo(19f, 20f)
        close()
        moveTo(4f, 4f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(16f)
        horizontalLineToRelative(-2f)
        close()
    }

    /** Simple chevron pointing up — used to expand the mini player. */
    val ChevronUp: ImageVector = stroke("ChevronUp") {
        moveTo(6f, 15f)
        lineTo(12f, 9f)
        lineTo(18f, 15f)
    }

    /** Simple chevron pointing down — used to dismiss the full player. */
    val ChevronDown: ImageVector = stroke("ChevronDown") {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }

    /** Two crossed arrows — shuffle. */
    val Shuffle: ImageVector = stroke("Shuffle") {
        // top arrow
        moveTo(16f, 3f)
        lineToRelative(5f, 0f)
        lineToRelative(0f, 5f)
        moveTo(21f, 3f)
        lineToRelative(-7f, 7f)
        moveTo(3f, 21f)
        lineToRelative(7f, -7f)
        // bottom arrow
        moveTo(16f, 21f)
        lineToRelative(5f, 0f)
        lineToRelative(0f, -5f)
        moveTo(21f, 21f)
        lineToRelative(-5f, -5f)
        moveTo(3f, 3f)
        lineToRelative(5f, 5f)
    }

    /** Circular arrows — repeat. */
    val Repeat: ImageVector = stroke("Repeat") {
        moveTo(17f, 2f)
        lineToRelative(4f, 4f)
        lineToRelative(-4f, 4f)
        moveTo(3f, 11f)
        verticalLineToRelative(-1f)
        arcToRelative(4f, 4f, 0f, false, true, 4f, -4f)
        horizontalLineToRelative(14f)
        moveTo(7f, 22f)
        lineToRelative(-4f, -4f)
        lineToRelative(4f, -4f)
        moveTo(21f, 13f)
        verticalLineToRelative(1f)
        arcToRelative(4f, 4f, 0f, false, true, -4f, 4f)
        horizontalLineToRelative(-14f)
    }

    /** Outline heart — not favorited. */
    val Heart: ImageVector = stroke("Heart") {
        moveTo(19f, 14f)
        curveTo(19.55f, 13.42f, 20f, 12.65f, 20f, 11.7f)
        arcToRelative(3.1f, 3.1f, 0f, false, false, -3.1f, -3.1f)
        curveToRelative(-1.4f, 0f, -2.7f, 0.8f, -3.3f, 2f)
        curveToRelative(-0.6f, -1.2f, -1.9f, -2f, -3.3f, -2f)
        arcTo(3.1f, 3.1f, 0f, false, false, 7.2f, 11.7f)
        curveToRelative(0f, 0.95f, 0.45f, 1.72f, 1f, 2.3f)
        lineToRelative(5f, 5f)
        close()
    }

    /** Filled heart — favorited. */
    val HeartFilled: ImageVector = filled("HeartFilled") {
        moveTo(12f, 21f)
        lineToRelative(-1.45f, -1.32f)
        curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
        arcTo(5.5f, 5.5f, 0f, false, true, 7.5f, 3f)
        curveToRelative(1.74f, 0f, 3.41f, 0.81f, 4.5f, 2.09f)
        curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
        arcTo(5.5f, 5.5f, 0f, false, true, 22f, 8.5f)
        curveToRelative(0f, 3.78f, -3.4f, 6.86f, -8.55f, 11.18f)
        close()
    }

    /** Stacked lines — queue/list. */
    val Queue: ImageVector = stroke("Queue") {
        moveTo(3f, 6f)
        horizontalLineTo(21f)
        moveTo(3f, 12f)
        horizontalLineTo(21f)
        moveTo(3f, 18f)
        horizontalLineTo(15f)
    }

    /** Three vertical dots — more options. */
    val MoreVertical: ImageVector = filled("MoreVertical") {
        moveTo(12f, 7f)
        arcTo(1f, 1f, 0f, false, true, 12f, 5f)
        arcTo(1f, 1f, 0f, false, true, 12f, 7f)
        close()
        moveTo(12f, 13f)
        arcTo(1f, 1f, 0f, false, true, 12f, 11f)
        arcTo(1f, 1f, 0f, false, true, 12f, 13f)
        close()
        moveTo(12f, 19f)
        arcTo(1f, 1f, 0f, false, true, 12f, 17f)
        arcTo(1f, 1f, 0f, false, true, 12f, 19f)
        close()
    }

    /**
     * Lucide heart outline (MIT licensed).
     * Source: lucide.dev — cleaner, more elegant than the previous heart.
     * Used in the mini player for the unfavorite state.
     */
    val HeartLucide: ImageVector = stroke("HeartLucide") {
        moveTo(2f, 9.5f)
        arcToRelative(5.5f, 5.5f, 0f, false, true, 9.591f, -3.676f)
        arcToRelative(0.56f, 0.56f, 0f, false, false, 0.818f, 0f)
        arcTo(5.49f, 5.49f, 0f, false, true, 22f, 9.5f)
        curveToRelative(0f, 2.29f, -1.5f, 4f, -3f, 5.5f)
        lineToRelative(-5.492f, 5.313f)
        arcToRelative(2f, 2f, 0f, false, true, -3f, 0.019f)
        lineTo(5f, 15f)
        curveToRelative(-1.5f, -1.5f, -3f, -3.2f, -3f, -5.5f)
    }

    /**
     * Lucide heart filled (MIT licensed).
     * Same path as HeartLucide but filled solid + closed.
     * Used in the mini player for the favorited state (coral color).
     */
    val HeartLucideFilled: ImageVector = filled("HeartLucideFilled") {
        moveTo(2f, 9.5f)
        arcToRelative(5.5f, 5.5f, 0f, false, true, 9.591f, -3.676f)
        arcToRelative(0.56f, 0.56f, 0f, false, false, 0.818f, 0f)
        arcTo(5.49f, 5.49f, 0f, false, true, 22f, 9.5f)
        curveToRelative(0f, 2.29f, -1.5f, 4f, -3f, 5.5f)
        lineToRelative(-5.492f, 5.313f)
        arcToRelative(2f, 2f, 0f, false, true, -3f, 0.019f)
        lineTo(5f, 15f)
        curveToRelative(-1.5f, -1.5f, -3f, -3.2f, -3f, -5.5f)
        close()
    }
}
