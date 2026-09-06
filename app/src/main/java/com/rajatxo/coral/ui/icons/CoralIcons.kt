package com.rajatxo.coral.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Coral's custom icon set.
 *
 * All glyphs are hand-drawn vector paths in the Lucide (MIT) visual idiom:
 *  - 24 x 24 viewport
 *  - 2dp stroke width
 *  - round caps & joins
 *  - filled variants use PathFillType.EvenOdd
 *
 * Keeping these in-house (instead of pulling Material Icons or Lucide as a
 * dependency) keeps Coral's dependency tree 100 % permissively licensed and
 * avoids the GPL-tainted Material Icons asset pack.
 */
object CoralIcons {

    private fun stroke(
        name: String,
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            solidColor = SolidColor(Color.Black),
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
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillType = PathFillType.EvenOdd,
            pathBuilder = pathBuilder
        )
    }.build()

    /**
     * Helper to append a full circle to a PathBuilder using 4 arcs.
     * Compose's PathBuilder has no circle() method, so we approximate.
     */
    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(
        cx: Float, cy: Float, r: Float
    ) {
        arcTo(r, r, 0f, false, true, cx, cy + r)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        arcTo(r, r, 0f, false, true, cx, cy - r)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        close()
    }

    /** Single musical note. Used as the "Songs" tab icon. */
    val Music: ImageVector = ImageVector.Builder(
        name = "Music",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // stem + bar
        path(
            fill = null,
            solidColor = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 18f)
            verticalLineToRelative(-13f)
            lineToRelative(12f, -2f)
            verticalLineToRelative(13f)
        }
        // left note head
        path(
            fill = null,
            solidColor = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 18f)
            circle(6f, 18f, 3f)
        }
        // right note head
        path(
            fill = null,
            solidColor = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 16f)
            circle(18f, 16f, 3f)
        }
    }.build()

    /** Stacked playlist lines with a small note. Used as the "Playlists" tab icon. */
    val ListMusic: ImageVector = stroke("ListMusic") {
        // note head (right side)
        moveTo(21f, 18f)
        circle(15.5f, 18f, 1.5f)
        // bar connecting note to its stem
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

    /** Sliders icon — used as the "Settings" tab icon. Clean & minimal. */
    val Settings: ImageVector = stroke("Settings") {
        // 3 horizontal lines, each interrupted by a knob
        // top line: 3 -> 14, gap, 16 -> 21
        moveTo(3f, 5f)
        horizontalLineTo(14f)
        moveTo(16f, 5f)
        horizontalLineTo(21f)
        // middle line: 3 -> 8, gap, 10 -> 21
        moveTo(3f, 12f)
        horizontalLineTo(8f)
        moveTo(10f, 12f)
        horizontalLineTo(21f)
        // bottom line: 3 -> 14, gap, 16 -> 21
        moveTo(3f, 19f)
        horizontalLineTo(14f)
        moveTo(16f, 19f)
        horizontalLineTo(21f)
        // 3 vertical "knobs" on each gap
        // top knob at x=15
        moveTo(15f, 3f)
        verticalLineToRelative(4f)
        // middle knob at x=9
        moveTo(9f, 10f)
        verticalLineToRelative(4f)
        // bottom knob at x=15
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
}
