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
        viewportWidth: Float = 24f,
        viewportHeight: Float = 24f,
        pathBuilder: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathBuilder = pathBuilder
        )
    }.build()

    /** Material-style outline icon (stroke only, 960×960 viewport). */
    private fun materialStroke(
        name: String,
        pathBuilder: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 40f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 4f,
            pathBuilder = pathBuilder
        )
    }.build()

    /** Material-style filled icon (fill only, 960×960 viewport). */
    private fun materialFilled(
        name: String,
        pathBuilder: PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
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

    /**
     * Lucide "settings" gear icon — gear with a center dot.
     * Used as the floating settings button on the top-left of every page.
     *
     * Path from Lucide (MIT licensed):
     *   <path d="M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915"/>
     *   <circle cx="12" cy="12" r="3"/>
     *
     * Translated to Compose path commands. The gear teeth are drawn as a
     * single continuous path (8 arcs around a circle), plus a small circle
     * in the center.
     */
    val Gear: ImageVector = stroke("Gear") {
        // Gear outline (Lucide's continuous path, simplified to close)
        // This draws the gear's outer shape with 8 teeth.
        moveTo(9.671f, 4.136f)
        // First tooth (top-right): arc to the right
        curveTo(11.5f, 3.0f, 13.5f, 3.0f, 14.329f, 4.136f)
        // Move to next position (tooth at right-upper)
        curveTo(15.0f, 5.5f, 16.5f, 6.5f, 17.988f, 6.051f)
        curveTo(19.5f, 5.5f, 21.5f, 7.5f, 20.989f, 9.084f)
        curveTo(20.5f, 10.5f, 21.0f, 12.0f, 22.0f, 13.0f)
        // bottom right area
        curveTo(21.5f, 14.5f, 21.5f, 16.0f, 20.989f, 16.916f)
        curveTo(20.5f, 18.5f, 19.5f, 20.5f, 17.988f, 19.949f)
        curveTo(16.5f, 19.5f, 15.0f, 20.5f, 14.329f, 21.864f)
        curveTo(13.5f, 23.0f, 11.5f, 23.0f, 10.671f, 21.864f)
        curveTo(10.0f, 20.5f, 8.5f, 19.5f, 7.012f, 19.949f)
        curveTo(5.5f, 20.5f, 3.5f, 18.5f, 4.011f, 16.916f)
        curveTo(4.5f, 15.5f, 4.0f, 14.0f, 3.0f, 13.0f)
        curveTo(3.5f, 11.5f, 3.5f, 10.0f, 4.011f, 9.084f)
        curveTo(4.5f, 7.5f, 5.5f, 5.5f, 7.012f, 6.051f)
        curveTo(8.5f, 6.5f, 10.0f, 5.5f, 9.671f, 4.136f)
        close()
        // Center circle (radius 3 at center 12,12)
        // Draw as 4 cubic curves to approximate a circle
        moveTo(15f, 12f)
        curveTo(15f, 13.657f, 13.657f, 15f, 12f, 15f)
        curveTo(10.343f, 15f, 9f, 13.657f, 9f, 12f)
        curveTo(9f, 10.343f, 10.343f, 9f, 12f, 9f)
        curveTo(13.657f, 9f, 15f, 10.343f, 15f, 12f)
        close()
    }

    /**
     * Lucide "cog" icon — settings gear with spokes + two concentric
     * circles. Used for the settings button (replaces the old Gear icon
     * which had a filled gear body). This is the cleaner line-art version
     * the user requested.
     */
    val Cog: ImageVector = stroke("Cog") {
        // Spokes (lines radiating from center)
        // Top-left spoke: (11,10.27) to (7,3.34)
        moveTo(11f, 10.27f)
        lineTo(7f, 3.34f)
        // Bottom-left spoke: (11,13.73) to (7,20.07)  [m11 13.73 -4 6.93]
        moveTo(11f, 13.73f)
        lineTo(7f, 20.66f)
        // Top vertical spoke: (12,2) to (12,4)
        moveTo(12f, 2f)
        lineTo(12f, 4f)
        // Bottom vertical spoke: (12,20) to (12,22)
        moveTo(12f, 20f)
        lineTo(12f, 22f)
        // Right horizontal spoke: (14,12) to (22,12)
        moveTo(14f, 12f)
        lineTo(22f, 12f)
        // Top-right spoke: (17,3.34) to (16,5.07)
        moveTo(17f, 3.34f)
        lineTo(16f, 5.07f)
        // Bottom-right spoke: (17,20.66) to (16,18.93)
        moveTo(17f, 20.66f)
        lineTo(16f, 18.93f)
        // Left horizontal spoke: (2,12) to (4,12)
        moveTo(2f, 12f)
        lineTo(4f, 12f)
        // Upper-right diagonal spoke: (20.66,17) to (18.93,16)
        moveTo(20.66f, 17f)
        lineTo(18.93f, 16f)
        // Lower-right diagonal spoke: (20.66,7) to (18.93,8)
        moveTo(20.66f, 7f)
        lineTo(18.93f, 8f)
        // Lower-left diagonal spoke: (3.34,17) to (5.07,16)
        moveTo(3.34f, 17f)
        lineTo(5.07f, 16f)
        // Upper-left diagonal spoke: (3.34,7) to (5.07,8)
        moveTo(3.34f, 7f)
        lineTo(5.07f, 8f)
        // Inner circle (radius 2 at center 12,12)
        moveTo(14f, 12f)
        curveTo(14f, 13.105f, 13.105f, 14f, 12f, 14f)
        curveTo(10.895f, 14f, 10f, 13.105f, 10f, 12f)
        curveTo(10f, 10.895f, 10.895f, 10f, 12f, 10f)
        curveTo(13.105f, 10f, 14f, 10.895f, 14f, 12f)
        close()
        // Outer circle (radius 8 at center 12,12)
        moveTo(20f, 12f)
        curveTo(20f, 16.418f, 16.418f, 20f, 12f, 20f)
        curveTo(7.582f, 20f, 4f, 16.418f, 4f, 12f)
        curveTo(4f, 7.582f, 7.582f, 4f, 12f, 4f)
        curveTo(16.418f, 4f, 20f, 7.582f, 20f, 12f)
        close()
    }

    /**
     * Lucide "circle-user" icon — a circle with a person silhouette
     * (head + shoulders). Used for the account/user button on the
     * top-left of the Quick Picks page.
     */
    val CircleUser: ImageVector = stroke("CircleUser") {
        // Outer circle (radius 10 at center 12,12)
        moveTo(22f, 12f)
        curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
        curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
        curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
        curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
        close()
        // Head circle (radius 3 at center 12,10)
        moveTo(15f, 10f)
        curveTo(15f, 11.657f, 13.657f, 13f, 12f, 13f)
        curveTo(10.343f, 13f, 9f, 11.657f, 9f, 10f)
        curveTo(9f, 8.343f, 10.343f, 7f, 12f, 7f)
        curveTo(13.657f, 7f, 15f, 8.343f, 15f, 10f)
        close()
        // Shoulders path: M7 20.662V19a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v1.662
        moveTo(7f, 20.662f)
        lineTo(7f, 19f)
        curveTo(7f, 17.895f, 7.895f, 17f, 9f, 17f)
        lineTo(15f, 17f)
        curveTo(16.105f, 17f, 17f, 17.895f, 17f, 19f)
        lineTo(17f, 20.662f)
    }

    /**
     * Lucide "pin" icon — a push pin/thumbtack. Used as a decorative
     * icon in the search bar (not clickable).
     */
    val Pin: ImageVector = stroke("Pin") {
        // Vertical pin stem: M12 17v5
        moveTo(12f, 17f)
        lineTo(12f, 22f)
        // Pin body path: M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z
        moveTo(9f, 10.76f)
        curveTo(9f, 11.864f, 8.381f, 12.874f, 7.39f, 13.37f)
        lineTo(5.61f, 14.27f)
        curveTo(4.619f, 14.766f, 4f, 15.776f, 4f, 16.88f)
        // Simplified: just draw the outline
        lineTo(4f, 16f)
        curveTo(4f, 15.448f, 4.448f, 15f, 5f, 15f)
        lineTo(19f, 15f)
        curveTo(19.552f, 15f, 20f, 15.448f, 20f, 16f)
        lineTo(20f, 15.24f)
        curveTo(20f, 14.136f, 19.381f, 13.126f, 18.39f, 12.63f)
        lineTo(16.61f, 11.73f)
        curveTo(15.619f, 11.234f, 15f, 10.224f, 15f, 9.12f)
        lineTo(15f, 7f)
        curveTo(15f, 6.448f, 15.448f, 6f, 16f, 6f)
        curveTo(17.105f, 6f, 18f, 5.105f, 18f, 4f)
        curveTo(18f, 2.895f, 17.105f, 2f, 16f, 2f)
        lineTo(8f, 2f)
        curveTo(6.895f, 2f, 6f, 2.895f, 6f, 4f)
        curveTo(6f, 5.105f, 6.895f, 6f, 8f, 6f)
        curveTo(8.552f, 6f, 9f, 6.448f, 9f, 7f)
        close()
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
    /** Lucide fast-forward (filled) — double right-pointing triangle. */
    /** Phosphor fast-forward fill (MIT) — double right triangle. */
    val SkipNext: ImageVector = filled("SkipNext", viewportWidth = 256f, viewportHeight = 256f) {
        moveTo(256f, 128f)
        arcToRelative(15.76f, 15.76f, 0f, false, true, -7.33f, 13.34f)
        lineToRelative(-88.19f, 56.16f)
        arcToRelative(15.91f, 15.91f, 0f, false, true, -24.48f, -13.34f)
        lineToRelative(0f, -37.3f)
        lineToRelative(-79.52f, 50.64f)
        arcToRelative(15.91f, 15.91f, 0f, false, true, -24.48f, -13.34f)
        lineToRelative(0f, -112.32f)
        arcToRelative(15.91f, 15.91f, 0f, false, true, 24.48f, -13.34f)
        lineToRelative(79.52f, 50.64f)
        lineToRelative(0f, -37.3f)
        arcToRelative(15.91f, 15.91f, 0f, false, true, 24.48f, -13.34f)
        lineToRelative(88.19f, 56.16f)
        arcToRelative(15.76f, 15.76f, 0f, false, true, 7.33f, 13.34f)
        close()
    }

    /** Heroicons backward (filled) — double left-pointing triangle. */
    /** Lucide rewind (filled) — double left-pointing triangle. */
    /** Phosphor rewind fill (MIT) — double left triangle. */
    val SkipPrev: ImageVector = filled("SkipPrev", viewportWidth = 256f, viewportHeight = 256f) {
        moveTo(232f, 71.84f)
        lineToRelative(0f, 112.32f)
        arcToRelative(15.92f, 15.92f, 0f, false, true, -24.48f, 13.34f)
        lineToRelative(-79.52f, -50.64f)
        lineToRelative(0f, 37.3f)
        arcToRelative(15.92f, 15.92f, 0f, false, true, -24.48f, 13.34f)
        lineToRelative(-88.19f, -56.16f)
        arcToRelative(15.8f, 15.8f, 0f, false, true, 0f, -26.68f)
        lineToRelative(88.19f, -56.16f)
        arcTo(15.91f, 15.91f, 0f, false, true, 128f, 71.84f)
        lineToRelative(0f, 37.3f)
        lineToRelative(79.52f, -50.64f)
        arcTo(15.91f, 15.91f, 0f, false, true, 232f, 71.84f)
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

    /** Two crossed arrows — shuffle.
     *
     *  Replaced with the standard lucide-shuffle path (the version with
     *  the S-curve crossover lines) per user request. The old simple
     *  cross-paths version is replaced in-place so all callers
     *  (CoralPlayer bottom row, Spiral2/Spiral3 menu capsule) get the
     *  new look without changing their code.
     */
    val Shuffle: ImageVector = stroke("Shuffle") {
        // top-right arrow tip (18,2) → 22,6 → 18,10
        moveTo(18f, 2f)
        lineToRelative(4f, 4f)
        lineToRelative(-4f, 4f)
        // bottom-right arrow tip (18,14) → 22,18 → 18,22
        moveTo(18f, 14f)
        lineToRelative(4f, 4f)
        lineToRelative(-4f, 4f)
        // top curve: from (2,18) horizontal, then a 4-unit arc bends
        // downward at (3.3, 1.7), continues through (5.454, 8.6) which
        // bends upward at (3.3, 1.7) — ends at (22,6) joining top arrow.
        moveTo(2f, 18f)
        horizontalLineToRelative(1.973f)
        arcToRelative(4f, 4f, 0f, false, false, 3.3f, -1.7f)
        lineToRelative(5.454f, -8.6f)
        arcToRelative(4f, 4f, 0f, false, true, 3.3f, -1.7f)
        horizontalLineToRelative(6f)
        // bottom curve start: short hook at (2,6)
        moveTo(2f, 6f)
        horizontalLineToRelative(1.972f)
        arcToRelative(4f, 4f, 0f, false, true, 3.6f, 2.2f)
        // bottom curve end: (22,18) → (-6.041, ...) horizontal then a
        // small arc to (-.359, -.45)
        moveTo(22f, 18f)
        horizontalLineToRelative(-6.041f)
        arcToRelative(4f, 4f, 0f, false, true, -3.3f, -1.8f)
        lineToRelative(-0.359f, -0.45f)
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

    /**
     * Material-style outline heart (user-provided SVG, 960×960 viewport).
     * Heart shape with a small notch at the bottom + rounded lobes.
     * Used as the favorite toggle on the Cynthia miniplayer (RIGHT side).
     */
    val HeartOutline: ImageVector = materialStroke("HeartOutline") {
        // ★ Y coords offset by +960 (SVG viewBox was 0 -960 960 960, Compose needs 0..960)
        moveTo(480f, 840f)
        lineTo(422f, 788f)
        quadTo(321f, 697f, 255f, 631f)
        quadTo(189f, 565f, 150f, 512.5f)
        quadTo(111f, 460f, 95.5f, 416f)
        quadTo(80f, 372f, 80f, 326f)
        quadTo(80f, 232f, 143f, 169f)
        quadTo(206f, 106f, 300f, 106f)
        quadTo(352f, 106f, 399f, 128f)
        quadTo(446f, 150f, 480f, 190f)
        quadTo(514f, 150f, 561f, 128f)
        quadTo(608f, 106f, 660f, 106f)
        quadTo(754f, 106f, 817f, 169f)
        quadTo(880f, 232f, 880f, 326f)
        quadTo(880f, 372f, 864.5f, 416f)
        quadTo(849f, 460f, 810f, 512.5f)
        quadTo(771f, 565f, 705f, 631f)
        quadTo(639f, 697f, 538f, 788f)
        lineTo(480f, 840f)
        close()
    }

    /**
     * Material-style filled heart (user-provided SVG, 960×960 viewport).
     * Same shape as HeartOutline but filled solid. Used when the song is
     * favorited — coral color.
     */
    val HeartFilledMaterial: ImageVector = filled("HeartFilledMaterial", 960f, 960f) {
        // ★ Y coords offset by +960 (SVG viewBox was 0 -960 960 960, Compose needs 0..960)
        moveTo(480f, 840f)
        lineTo(422f, 788f)
        quadTo(321f, 697f, 255f, 631f)
        quadTo(189f, 565f, 150f, 512.5f)
        quadTo(111f, 460f, 95.5f, 416f)
        quadTo(80f, 372f, 80f, 326f)
        quadTo(80f, 232f, 143f, 169f)
        quadTo(206f, 106f, 300f, 106f)
        quadTo(352f, 106f, 399f, 128f)
        quadTo(446f, 150f, 480f, 190f)
        quadTo(514f, 150f, 561f, 128f)
        quadTo(608f, 106f, 660f, 106f)
        quadTo(754f, 106f, 817f, 169f)
        quadTo(880f, 232f, 880f, 326f)
        quadTo(880f, 372f, 864.5f, 416f)
        quadTo(849f, 460f, 810f, 512.5f)
        quadTo(771f, 565f, 705f, 631f)
        quadTo(639f, 697f, 538f, 788f)
        lineTo(480f, 840f)
        close()
    }


    /** Lucide search icon (magnifying glass, MIT licensed). */
    val Search: ImageVector = stroke("Search") {
        moveTo(19f, 11f)
        arcTo(8f, 8f, 0f, true, true, 3f, 11f)
        arcTo(8f, 8f, 0f, true, true, 19f, 11f)
        moveTo(21f, 21f)
        lineTo(16.7f, 16.7f)
    }

    /** Lucide chevron-left (MIT licensed). Back arrow pointing LEFT. */
    val ChevronLeft: ImageVector = stroke("ChevronLeft") {
        moveTo(15f, 18f)
        lineTo(9f, 12f)
        lineTo(15f, 6f)
    }

    /** Lucide ellipsis — three horizontal dots (MIT licensed). */
    val Ellipsis: ImageVector = stroke("Ellipsis") {
        // Dot 1 (left)
        moveTo(5f, 12f)
        arcTo(1f, 1f, 0f, true, true, 5.001f, 12f)
        // Dot 2 (center)
        moveTo(12f, 12f)
        arcTo(1f, 1f, 0f, true, true, 12.001f, 12f)
        // Dot 3 (right)
        moveTo(19f, 12f)
        arcTo(1f, 1f, 0f, true, true, 19.001f, 12f)
    }

    /** Lucide shuffle (MIT licensed). Crossing arrows. */
    val ShuffleLucide: ImageVector = stroke("ShuffleLucide") {
        // Right arrow top
        moveTo(18f, 14f)
        lineTo(22f, 18f)
        lineTo(18f, 22f)
        // Right arrow bottom
        moveTo(18f, 2f)
        lineTo(22f, 6f)
        lineTo(18f, 10f)
        // Top line going right
        moveTo(2f, 18f)
        horizontalLineToRelative(1.973f)
        // Curve to arrows
        arcToRelative(4f, 4f, 0f, false, false, 3.3f, -1.7f)
        lineToRelative(5.454f, -8.6f)
        arcToRelative(4f, 4f, 0f, false, true, 3.3f, -1.7f)
        horizontalLineTo(22f)
        // Bottom line
        moveTo(2f, 6f)
        horizontalLineToRelative(1.972f)
        arcToRelative(4f, 4f, 0f, false, true, 3.6f, 2.2f)
        // Bottom right
        moveTo(22f, 18f)
        horizontalLineToRelative(-6.041f)
        arcToRelative(4f, 4f, 0f, false, true, -3.3f, -1.8f)
        lineToRelative(-0.359f, -0.45f)
    }

    /** Lucide play — filled triangle (MIT licensed). */
    /** Lucide play (filled) — solid triangle. */
    val PlayLucide: ImageVector = filled("PlayLucide") {
        moveTo(5f, 5f)
        arcTo(2f, 2f, 0f, false, true, 8.008f, 3.272f)
        lineToRelative(11.997f, 6.998f)
        arcTo(2f, 2f, 0f, false, true, 20.008f, 13.728f)
        lineToRelative(-12f, 7f)
        arcTo(2f, 2f, 0f, false, true, 5f, 19f)
        close()
    }

    /** Heroicons pause (filled) — two vertical bars with rounded corners. */
    /** Lucide pause (filled) — two rounded rectangles. */
    val PauseLucide: ImageVector = filled("PauseLucide") {
        // Right bar: rect x=14 y=3 w=5 h=18 rx=1
        moveTo(15f, 3f)
        arcTo(1f, 1f, 0f, false, false, 14f, 4f)
        verticalLineTo(20f)
        arcTo(1f, 1f, 0f, false, false, 15f, 21f)
        horizontalLineTo(18f)
        arcTo(1f, 1f, 0f, false, false, 19f, 20f)
        verticalLineTo(4f)
        arcTo(1f, 1f, 0f, false, false, 18f, 3f)
        close()
        // Left bar: rect x=5 y=3 w=5 h=18 rx=1
        moveTo(6f, 3f)
        arcTo(1f, 1f, 0f, false, false, 5f, 4f)
        verticalLineTo(20f)
        arcTo(1f, 1f, 0f, false, false, 6f, 21f)
        horizontalLineTo(9f)
        arcTo(1f, 1f, 0f, false, false, 10f, 20f)
        verticalLineTo(4f)
        arcTo(1f, 1f, 0f, false, false, 9f, 3f)
        close()
    }

    /**
     * Lucide bell-ring icon (MIT licensed).
     *
     * A bell with four little sound-wave lines on top — used by the permission
     * screen for the Notifications card.
     */
    val BellRing: ImageVector = stroke("BellRing") {
        // Bottom notch (the clapper)
        moveTo(10.268f, 21f)
        arcToRelative(2f, 2f, 0f, false, false, 3.464f, 0f)
        // Bell body
        moveTo(3.262f, 15.326f)
        arcToRelative(1f, 1f, 0f, false, false, 0.738f, 0.674f)
        horizontalLineToRelative(16f)
        arcToRelative(1f, 1f, 0f, false, false, 0.74f, -1.673f)
        curveToRelative(-1.33f, -1.371f, -2.74f, -2.828f, -2.74f, -7.327f)
        arcToRelative(6f, 6f, 0f, false, false, -12f, 0f)
        curveToRelative(0f, 4.499f, -1.411f, 5.956f, -2.738f, 7.326f)
        // Four sound-wave lines at the top
        moveTo(4f, 2f)
        verticalLineToRelative(2f)
        moveTo(8f, 2f)
        verticalLineToRelative(2f)
        moveTo(16f, 2f)
        verticalLineToRelative(2f)
        moveTo(20f, 2f)
        verticalLineToRelative(2f)
    }

    /**
     * Lucide file-headphone icon (MIT licensed).
     *
     * A document/file outline with a small headphone graphic inside — used
     * by the permission screen for the Music files card.
     */
    val FileHeadphone: ImageVector = stroke("FileHeadphone") {
        // File outline (open-bottom shape, no bottom border)
        moveTo(4f, 22f)
        horizontalLineToRelative(14f)
        arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
        verticalLineTo(7f)
        lineTo(15f, 2f)
        horizontalLineTo(6f)
        arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
        verticalLineToRelative(2f)
        // Corner fold
        moveTo(14f, 2f)
        verticalLineToRelative(4f)
        arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
        horizontalLineToRelative(4f)
        // Headphone (left vertical line + curved ear cup)
        moveTo(7f, 10f)
        verticalLineToRelative(5f)
        // Right ear cup
        moveTo(10f, 14f)
        arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
        verticalLineToRelative(1f)
        arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
        arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
        verticalLineToRelative(-3f)
    }

    /**
     * Clipboard with a paste arrow — lucide clipboard-paste style.
     * Used by the "Paste Lyrics" menu item in the lyrics sheet.
     */
    val ClipboardPaste: ImageVector = stroke("ClipboardPaste") {
        // Clipboard body (rounded rectangle outline)
        moveTo(8f, 4f)
        arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
        verticalLineTo(20f)
        arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
        horizontalLineToRelative(6f)
        // Clipboard clip (tab at top)
        moveTo(8f, 4f)
        horizontalLineToRelative(4f)
        arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
        verticalLineToRelative(2f)
        arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
        horizontalLineToRelative(-6f)
        arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
        verticalLineTo(5f)
        arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
        // Paste arrow (downward) to the right of the clipboard
        moveTo(15f, 11f)
        verticalLineToRelative(6f)
        moveTo(12f, 15f)
        lineToRelative(3f, 3f)
        lineToRelative(3f, -3f)
    }

    /** Heroicons volume-off (filled) — speaker with X (muted/low). */
    /** Phosphor speaker-slash fill (MIT) — muted/low volume. */
    val VolumeLow: ImageVector = filled("VolumeLow", viewportWidth = 256f, viewportHeight = 256f) {
        moveTo(168f, 32f)
        lineTo(168f, 224f)
        arcToRelative(8f, 8f, 0f, false, true, -12.91f, 6.31f)
        lineToRelative(-69.84f, -54.31f)
        lineTo(40f, 176f)
        arcToRelative(16f, 16f, 0f, false, true, -16f, -16f)
        lineTo(24f, 96f)
        arcTo(16f, 16f, 0f, false, true, 40f, 80f)
        lineTo(85.25f, 80f)
        lineToRelative(69.84f, -54.31f)
        arcTo(8f, 8f, 0f, false, true, 168f, 32f)
        close()
        moveTo(200f, 96f)
        arcTo(8f, 8f, 0f, false, false, 192f, 104f)
        lineTo(192f, 152f)
        arcTo(8f, 8f, 0f, false, false, 208f, 152f)
        lineTo(208f, 104f)
        arcTo(8f, 8f, 0f, false, false, 200f, 96f)
        close()
    }

    /** Speaker with two sound waves — high volume. */
    /** Phosphor speaker-high fill (MIT) — speaker with sound waves. */
    val VolumeHigh: ImageVector = filled("VolumeHigh", viewportWidth = 256f, viewportHeight = 256f) {
        moveTo(160f, 32.25f)
        lineTo(160f, 223.69f)
        arcToRelative(8.29f, 8.29f, 0f, false, true, -3.91f, 7.18f)
        arcTo(8f, 8f, 0f, false, true, 147.09f, 230.31f)
        lineToRelative(-65.57f, -51f)
        arcTo(4f, 4f, 0f, false, true, 80f, 176.16f)
        lineTo(80f, 79.84f)
        arcTo(4f, 4f, 0f, false, true, 81.55f, 76.69f)
        lineToRelative(65.57f, -51f)
        arcTo(8f, 8f, 0f, false, true, 157.12f, 25.85f)
        arcTo(8.27f, 8.27f, 0f, false, true, 160f, 32.25f)
        close()
        moveTo(60f, 80f)
        lineTo(32f, 80f)
        arcTo(16f, 16f, 0f, false, false, 16f, 96f)
        lineToRelative(0f, 64f)
        arcTo(16f, 16f, 0f, false, false, 32f, 176f)
        lineTo(60f, 176f)
        arcTo(4f, 4f, 0f, false, false, 64f, 172f)
        lineTo(64f, 84f)
        arcTo(4f, 4f, 0f, false, false, 60f, 80f)
        close()
        moveTo(186.77f, 100.84f)
        arcTo(8f, 8f, 0f, false, false, 186.05f, 112.14f)
        arcTo(24f, 24f, 0f, false, true, 186.05f, 143.86f)
        arcTo(8f, 8f, 0f, true, false, 198.05f, 154.44f)
        arcTo(40f, 40f, 0f, false, false, 198.05f, 101.56f)
        arcTo(8f, 8f, 0f, false, false, 186.77f, 100.84f)
        close()
        moveTo(227.66f, 74.67f)
        arcTo(8f, 8f, 0f, true, false, 215.74f, 85.33f)
        arcTo(64f, 64f, 0f, false, true, 215.74f, 170.67f)
        arcTo(8f, 8f, 0f, true, false, 227.66f, 181.33f)
        arcTo(80f, 80f, 0f, false, false, 227.66f, 74.67f)
        close()
    }

    /** Right-pointing chevron — for tappable rows that navigate forward. */
    val ChevronRight: ImageVector = stroke("ChevronRight") {
        moveTo(9f, 6f)
        lineToRelative(6f, 6f)
        lineToRelative(-6f, 6f)
    }

    /**
     * Right-pointing chevron, THICKER variant (strokeLineWidth = 2.8f vs
     * the default 2f). Used in the Spiral 2.0 lyrics strip where the
     * chevron hugs the end of the lyric line — the extra weight makes it
     * read clearly as a visual cue at small sizes (18dp).
     */
    val ChevronRightThick: ImageVector = ImageVector.Builder(
        name = "ChevronRightThick",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 4f,
            pathBuilder = {
                moveTo(9f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(-6f, 6f)
            }
        )
    }.build()

    /**
     * Lucide rewind (MIT licensed) — two left-pointing triangles.
     * FILLED solid version. Used for the "previous track" button.
     */
    val Rewind: ImageVector = filled("Rewind") {
        // First triangle (left, pointing left)
        moveTo(11f, 19f)
        lineTo(2f, 12f)
        lineTo(11f, 5f)
        close()
        // Second triangle (right, pointing left)
        moveTo(22f, 19f)
        lineTo(13f, 12f)
        lineTo(22f, 5f)
        close()
    }

    /**
     * Lucide fast-forward (MIT licensed) — two right-pointing triangles.
     * Mirrored rewind. FILLED solid version. Used for the "next track" button.
     */
    val FastForward: ImageVector = filled("FastForward") {
        // First triangle (left, pointing right)
        moveTo(13f, 19f)
        lineTo(22f, 12f)
        lineTo(13f, 5f)
        close()
        // Second triangle (right, pointing right)
        moveTo(2f, 19f)
        lineTo(11f, 12f)
        lineTo(2f, 5f)
        close()
    }

    /**
     * Lucide heart-plus (outline) — heart with a + sign.
     * Used when a song is NOT favorited. Tap to add to favorites.
     */
    val HeartPlus: ImageVector = stroke("HeartPlus") {
        // Heart outline (same path as HeartLucide)
        moveTo(2f, 9.5f)
        arcToRelative(5.5f, 5.5f, 0f, false, true, 9.591f, -3.676f)
        arcToRelative(0.56f, 0.56f, 0f, false, false, 0.818f, 0f)
        arcTo(5.49f, 5.49f, 0f, false, true, 22f, 9.5f)
        curveToRelative(0f, 2.29f, -1.5f, 4f, -3f, 5.5f)
        lineToRelative(-5.492f, 5.313f)
        arcToRelative(2f, 2f, 0f, false, true, -3f, 0.019f)
        lineTo(5f, 15f)
        curveToRelative(-1.5f, -1.5f, -3f, -3.2f, -3f, -5.5f)
        // Plus sign
        moveTo(15f, 15f)
        horizontalLineToRelative(6f)
        moveTo(18f, 12f)
        verticalLineToRelative(6f)
    }

    /**
     * Lucide heart-minus (outline) — heart with a - sign.
     * Used when a song IS favorited. Tap to remove from favorites.
     */
    val HeartMinus: ImageVector = stroke("HeartMinus") {
        // Heart outline (same path as HeartLucide)
        moveTo(2f, 9.5f)
        arcToRelative(5.5f, 5.5f, 0f, false, true, 9.591f, -3.676f)
        arcToRelative(0.56f, 0.56f, 0f, false, false, 0.818f, 0f)
        arcTo(5.49f, 5.49f, 0f, false, true, 22f, 9.5f)
        curveToRelative(0f, 2.29f, -1.5f, 4f, -3f, 5.5f)
        lineToRelative(-5.492f, 5.313f)
        arcToRelative(2f, 2f, 0f, false, true, -3f, 0.019f)
        lineTo(5f, 15f)
        curveToRelative(-1.5f, -1.5f, -3f, -3.2f, -3f, -5.5f)
        // Minus sign
        moveTo(15f, 15f)
        horizontalLineToRelative(6f)
    }

    /**
     * Lucide share-2 (outline) — three circles connected by lines.
     * Standard iOS-style share icon.
     */
    val Share2: ImageVector = stroke("Share2") {
        // Circle 1: center (18, 5), radius 3
        moveTo(21f, 5f)
        arcTo(3f, 3f, 0f, true, true, 15f, 5f)
        arcTo(3f, 3f, 0f, true, true, 21f, 5f)
        // Circle 2: center (6, 12), radius 3
        moveTo(9f, 12f)
        arcTo(3f, 3f, 0f, true, true, 3f, 12f)
        arcTo(3f, 3f, 0f, true, true, 9f, 12f)
        // Circle 3: center (18, 19), radius 3
        moveTo(21f, 19f)
        arcTo(3f, 3f, 0f, true, true, 15f, 19f)
        arcTo(3f, 3f, 0f, true, true, 21f, 19f)
        // Connecting lines
        moveTo(8.59f, 13.51f)
        lineTo(15.42f, 17.49f)
        moveTo(15.41f, 6.51f)
        lineTo(8.59f, 10.49f)
    }


    val ChevronsRight: ImageVector = stroke("ChevronsRight") {
        moveTo(6f, 17f)
        lineToRelative(5f, -5f)
        lineToRelative(-5f, -5f)
        moveTo(13f, 17f)
        lineToRelative(5f, -5f)
        lineToRelative(-5f, -5f)
    }

    val ChevronsLeft: ImageVector = stroke("ChevronsLeft") {
        moveTo(11f, 17f)
        lineToRelative(-5f, -5f)
        lineToRelative(5f, -5f)
        moveTo(18f, 17f)
        lineToRelative(-5f, -5f)
        lineToRelative(5f, -5f)
    }

    val Timer: ImageVector = stroke("Timer") {
        moveTo(10f, 2f)
        lineTo(14f, 2f)
        moveTo(12f, 14f)
        lineTo(15f, 11f)
        moveTo(20f, 14f)
        arcTo(8f, 8f, 0f, true, true, 4f, 14f)
        arcTo(8f, 8f, 0f, true, true, 20f, 14f)
    }

    val TimerOff: ImageVector = stroke("TimerOff") {
        moveTo(10f, 2f)
        lineTo(14f, 2f)
        moveTo(2f, 2f)
        lineTo(22f, 22f)
        moveTo(12f, 12f)
        lineTo(12f, 10f)
        moveTo(20f, 14f)
        arcTo(8f, 8f, 0f, true, true, 4f, 14f)
        arcTo(8f, 8f, 0f, true, true, 20f, 14f)
    }

    /** Repeat-off (slash through repeat) — repeat disabled. */
    val RepeatOff: ImageVector = stroke("RepeatOff") {
        moveTo(11f, 6f)
        lineTo(21f, 6f)
        lineTo(17f, 2f)
        moveTo(17.9f, 17.9f)
        arcTo(4f, 4f, 0f, false, true, 17f, 18f)
        lineTo(3f, 18f)
        lineTo(7f, 14f)
        moveTo(2f, 2f)
        lineTo(22f, 22f)
        moveTo(21f, 13f)
        verticalLineToRelative(1f)
        arcTo(4f, 4f, 0f, false, true, 20.83f, 15.16f)
        moveTo(21f, 6f)
        lineTo(17f, 10f)
        moveTo(3f, 11f)
        verticalLineToRelative(-1f)
        arcTo(4f, 4f, 0f, false, true, 6.1f, 7.1f)
        moveTo(7f, 22f)
        lineTo(3f, 18f)
        lineTo(7f, 14f)
    }

    /** Infinity — repeat one (loop single track). */
    val Infinity: ImageVector = stroke("Infinity") {
        moveTo(6f, 16f)
        curveTo(11f, 16f, 13f, 8f, 18f, 8f)
        arcTo(4f, 4f, 0f, false, true, 18f, 16f)
        curveTo(13f, 16f, 11f, 8f, 6f, 8f)
        arcTo(4f, 4f, 0f, false, false, 6f, 16f)
        close()
    }

    /** Lucide Bug icon. Used as the pull-to-refresh indicator on Quick Picks. */
    val Bug: ImageVector = stroke("Bug") {
        // body stem (vertical line from top to bottom of body)
        moveTo(12f, 20f)
        verticalLineToRelative(-9f)
        // body shell (rounded rectangle with flat bottom)
        moveTo(14f, 7f)
        arcTo(4f, 4f, 0f, false, true, 18f, 11f)
        verticalLineToRelative(3f)
        arcTo(6f, 6f, 0f, false, true, 6f, 14f)
        verticalLineToRelative(-3f)
        arcTo(4f, 4f, 0f, false, true, 10f, 7f)
        close()
        // right antenna
        moveTo(14.12f, 3.88f)
        lineTo(16f, 2f)
        // right-bottom leg
        moveTo(21f, 21f)
        arcTo(4f, 4f, 0f, false, false, 17.19f, 17f)
        // right-top leg
        moveTo(21f, 5f)
        arcTo(4f, 4f, 0f, false, true, 17.45f, 8.97f)
        // right-mid leg
        moveTo(22f, 13f)
        horizontalLineToRelative(-4f)
        // left-bottom leg
        moveTo(3f, 21f)
        arcTo(4f, 4f, 0f, false, true, 6.81f, 17f)
        // left-top leg
        moveTo(3f, 5f)
        arcTo(4f, 4f, 0f, false, false, 6.55f, 8.97f)
        // left-mid leg
        moveTo(6f, 13f)
        horizontalLineTo(2f)
        // left antenna
        moveTo(8f, 2f)
        lineToRelative(1.88f, 1.88f)
        // head (small circle/arc at top)
        moveTo(9f, 7.13f)
        verticalLineToRelative(-1.13f)
        arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
        verticalLineToRelative(1.13f)
    }

    /**
     * Unplug — lucide-unplug style.
     * A plug on the left + socket on the right, with two prongs.
     * Used by the "Fetch Lyrics" menu item in the lyrics sheet —
     * implies disconnecting from the local cache to fetch fresh
     * lyrics from the network.
     */
    val Unplug: ImageVector = stroke("Unplug") {
        // Top-right small line (plug antenna)
        moveTo(19f, 5f)
        lineTo(22f, 2f)
        // Bottom-left small line (socket antenna)
        moveTo(2f, 22f)
        lineTo(5f, 19f)
        // Plug body (left side)
        moveTo(6.3f, 20.3f)
        arcToRelative(2.4f, 2.4f, 0f, false, false, 3.4f, 0f)
        lineTo(12f, 18f)
        lineToRelative(-6f, -6f)
        lineToRelative(-2.3f, 2.3f)
        arcToRelative(2.4f, 2.4f, 0f, false, false, 0f, 3.4f)
        close()
        // Prong 1 (diagonal line on plug body)
        moveTo(7.5f, 13.5f)
        lineTo(10f, 11f)
        // Prong 2 (diagonal line on plug body)
        moveTo(10.5f, 16.5f)
        lineTo(13f, 14f)
        // Socket body (right side)
        moveTo(12f, 6f)
        lineToRelative(6f, 6f)
        lineToRelative(2.3f, -2.3f)
        arcToRelative(2.4f, 2.4f, 0f, false, false, 0f, -3.4f)
        lineToRelative(-2.6f, -2.6f)
        arcToRelative(2.4f, 2.4f, 0f, false, false, -3.4f, 0f)
        close()
    }

    /**
     * MousePointerClick — lucide-mouse-pointer-click style.
     * A cursor arrow with small radiating lines (click burst).
     * Used by the "Pick Lyrics" menu item — implies user interaction
     * (clicking to pick a specific candidate).
     */
    val MousePointerClick: ImageVector = stroke("MousePointerClick") {
        // Cursor arrow (the pointer body)
        moveTo(9.037f, 9.69f)
        arcToRelative(0.498f, 0.498f, 0f, false, true, 0.653f, -0.653f)
        lineToRelative(11f, 4.5f)
        arcToRelative(0.5f, 0.5f, 0f, false, true, -0.074f, 0.949f)
        lineToRelative(-4.349f, 1.041f)
        arcToRelative(1f, 1f, 0f, false, false, -0.74f, 0.739f)
        lineToRelative(-1.04f, 4.35f)
        arcToRelative(0.5f, 0.5f, 0f, false, true, -0.95f, 0.074f)
        close()
        // Radiating click-burst lines (4 short ticks around the pointer)
        moveTo(14f, 4.1f)
        lineTo(12f, 6f)
        moveTo(5.1f, 8f)
        lineTo(2.2f, 7.2f)
        moveTo(6f, 12f)
        lineTo(4.1f, 14f)
        moveTo(7.2f, 2.2f)
        lineTo(8f, 5.1f)
    }

    /**
     * FolderInput — lucide-folder-input style.
     * A folder outline with an arrow pointing into it from the left.
     * Used by the "Import LRC File" menu item — implies bringing
     * an external file into Coral's lyrics cache.
     */
    val FolderInput: ImageVector = stroke("FolderInput") {
        // Folder body (open at bottom-left to receive the arrow)
        moveTo(2f, 9f)
        verticalLineTo(5f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
        horizontalLineToRelative(3.9f)
        arcToRelative(2f, 2f, 0f, false, true, 1.69f, 0.9f)
        lineToRelative(0.81f, 1.2f)
        arcToRelative(2f, 2f, 0f, false, false, 1.67f, 0.9f)
        horizontalLineTo(20f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        verticalLineToRelative(10f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(4f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        verticalLineToRelative(-1f)
        // Arrow shaft (horizontal line going into the folder)
        moveTo(2f, 13f)
        horizontalLineToRelative(10f)
        // Arrowhead (right-pointing chevron)
        moveTo(9f, 16f)
        lineToRelative(3f, -3f)
        lineToRelative(-3f, -3f)
    }

    /**
     * ClipboardType — lucide-clipboard-type style.
     * A clipboard with a stylized "T" letter on it (for "type/paste text").
     * Used by the "Paste Lyrics" menu item — implies pasting typed text.
     */
    val ClipboardType: ImageVector = stroke("ClipboardType") {
        // Top clip (small rounded rectangle)
        moveTo(9f, 2f)
        horizontalLineToRelative(6f)
        arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
        verticalLineToRelative(2f)
        arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
        horizontalLineToRelative(-6f)
        arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
        verticalLineToRelative(-2f)
        arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
        close()
        // Clipboard body (large rounded rectangle)
        moveTo(16f, 4f)
        horizontalLineToRelative(2f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        verticalLineToRelative(14f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(6f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        verticalLineTo(6f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
        horizontalLineToRelative(2f)
        // Top bar of the "T" letter
        moveTo(9f, 12f)
        verticalLineToRelative(-1f)
        horizontalLineToRelative(6f)
        verticalLineToRelative(1f)
        // Crossbar of the "T" letter
        moveTo(11f, 17f)
        horizontalLineToRelative(2f)
        // Vertical stem of the "T" letter
        moveTo(12f, 11f)
        verticalLineToRelative(6f)
    }

    /**
     * Trash — lucide-trash style.
     * A trash can with two vertical lines inside (the "lids").
     * Used by the player 3-dot menu to delete the current song.
     */
    val Trash: ImageVector = stroke("Trash") {
        // Two vertical lines inside the can (the "lids")
        moveTo(10f, 11f)
        verticalLineToRelative(6f)
        moveTo(14f, 11f)
        verticalLineToRelative(6f)
        // Can body: starts at top-left of body, goes down, across bottom,
        // back up the right side, with rounded top.
        // (19,6) → v+14 → arc 2,2 to (-2,-2) [bottom-right corner]
        // → h-14 → arc 2,2 (-2,2) [bottom-left] → v-14 → close
        moveTo(19f, 6f)
        verticalLineToRelative(14f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(7f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        verticalLineTo(6f)
        // Top horizontal bar (the lid rest)
        moveTo(3f, 6f)
        horizontalLineToRelative(18f)
        // Top lid handle (small rounded rect)
        moveTo(8f, 6f)
        verticalLineTo(4f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
        horizontalLineToRelative(4f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        verticalLineTo(6f)
    }

    /**
     * Lucide message-square-quote (MIT) — speech bubble with quote marks.
     * Used for the lyrics button in the Spiral player bottom row.
     */
    val MessageSquareQuote: ImageVector = stroke("MessageSquareQuote") {
        // M14 14a2 2 0 0 0 2-2V8h-2
        moveTo(14f, 14f)
        arcTo(2f, 2f, 0f, false, false, 16f, 12f)
        verticalLineTo(8f)
        horizontalLineToRelative(-2f)
        // M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z
        moveTo(22f, 17f)
        arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(6.828f)
        arcToRelative(2f, 2f, 0f, false, false, -1.414f, 0.586f)
        lineToRelative(-2.202f, 2.202f)
        arcToRelative(0.71f, 0.71f, 0f, false, true, -1.212f, -0.502f)
        verticalLineTo(5f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
        horizontalLineToRelative(16f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        close()
        // M8 14a2 2 0 0 0 2-2V8H8
        moveTo(8f, 14f)
        arcTo(2f, 2f, 0f, false, false, 10f, 12f)
        verticalLineTo(8f)
        horizontalLineToRelative(-2f)
    }

    /**
     * Lucide radio (MIT) — radio/connectivity icon with waves.
     * Used for the bluetooth/audio output button in the Spiral player bottom row.
     */
    val Radio: ImageVector = stroke("Radio") {
        // M16.247 7.761a6 6 0 0 1 0 8.478
        moveTo(16.247f, 7.761f)
        arcTo(6f, 6f, 0f, false, true, 16.247f, 16.239f)
        // M19.075 4.933a10 10 0 0 1 0 14.134
        moveTo(19.075f, 4.933f)
        arcTo(10f, 10f, 0f, false, true, 19.075f, 19.067f)
        // M4.925 19.067a10 10 0 0 1 0-14.134
        moveTo(4.925f, 19.067f)
        arcTo(10f, 10f, 0f, false, true, 4.925f, 4.933f)
        // M7.753 16.239a6 6 0 0 1 0-8.478
        moveTo(7.753f, 16.239f)
        arcTo(6f, 6f, 0f, false, true, 7.753f, 7.761f)
        // Circle cx=12 cy=12 r=2
        moveTo(14f, 12f)
        arcTo(2f, 2f, 0f, true, true, 10f, 12f)
        arcTo(2f, 2f, 0f, true, true, 14f, 12f)
    }

    /**
     * Lucide logs (MIT) — list with dots and lines.
     * Used for the queue button in the Spiral player bottom row.
     */
    val Logs: ImageVector = stroke("Logs") {
        // M3 5h1
        moveTo(3f, 5f)
        horizontalLineToRelative(1f)
        // M3 12h1
        moveTo(3f, 12f)
        horizontalLineToRelative(1f)
        // M3 19h1
        moveTo(3f, 19f)
        horizontalLineToRelative(1f)
        // M8 5h1
        moveTo(8f, 5f)
        horizontalLineToRelative(1f)
        // M8 12h1
        moveTo(8f, 12f)
        horizontalLineToRelative(1f)
        // M8 19h1
        moveTo(8f, 19f)
        horizontalLineToRelative(1f)
        // M13 5h8
        moveTo(13f, 5f)
        horizontalLineToRelative(8f)
        // M13 12h8
        moveTo(13f, 12f)
        horizontalLineToRelative(8f)
        // M13 19h8
        moveTo(13f, 19f)
        horizontalLineToRelative(8f)
    }

    /**
     * Material-style filled pin (location pin icon, 960×960 viewport).
     * Source: Material Symbols "location_on" filled.
     * Used for the pinned state in search.
     */
    val PinFilled: ImageVector = materialFilled("PinFilled") {
        // ★ User-provided SVG path (filled):
        // M480-388q54-50 84-80t47-50q16-20 22.5-37t6.5-37q0-36-26-62t-62-26q-21 0-40.5 8.5T480-648q-12-15-31-23.5t-41-8.5q-36 0-62 26t-26 62q0 21 6 37t22 36q17 20 46 50t86 81Z
        moveTo(480f, 388f)
        quadTo(534f, 338f, 564f, 308f)
        quadTo(611f, 258f, 611f, 258f)
        quadTo(627f, 238f, 633.5f, 221f)
        quadTo(640f, 204f, 640f, 184f)
        quadToRelative(0f, -36f, -26f, -62f)
        reflectiveQuadToRelative(-62f, -26f)
        quadToRelative(0f, -21f, 8.5f, -40.5f)
        quadTo(480f, 648f, 480f, 648f)
        quadTo(468f, 633f, 449f, 624.5f)
        reflectiveQuadToRelative(-41f, -8.5f)
        quadToRelative(0f, -36f, -26f, -62f)
        reflectiveQuadToRelative(-62f, -26f)
        quadToRelative(0f, 21f, 6f, 37f)
        quadTo(342f, 236f, 342f, 236f)
        quadTo(359f, 256f, 388f, 286f)
        quadToRelative(86f, 81f, 86f, 81f)
        close()
        // M480-186q122-112 181-203.5T720-552q0-109-69.5-178.5T480-800q-101 0-170.5 69.5T240-552q0 71 59 162.5T480-186Z
        moveTo(480f, 186f)
        quadTo(602f, 74f, 661f, -17.5f)
        quadTo(720f, -109f, 720f, -109f)
        quadTo(720f, -218f, 650.5f, -287.5f)
        quadTo(580f, -357f, 480f, -357f)
        quadToRelative(0f, -101f, -170.5f, -69.5f)
        quadTo(240f, -287f, 240f, -287f)
        quadTo(240f, -216f, 299f, -125.5f)
        quadTo(358f, -35f, 480f, -35f)
        close()
        // m0 106Q319-217 239.5-334.5T160-552q0-150 96.5-239T480-880q127 0 223.5 89T800-552q0 100-79.5 217.5T480-80Z
        moveTo(480f, 80f)
        quadTo(319f, -217f, 239.5f, -334.5f)
        quadTo(160f, -452f, 160f, -452f)
        quadToRelative(0f, -150f, 96.5f, -239f)
        quadTo(256f, -691f, 480f, -691f)
        quadToRelative(0f, 127f, 223.5f, 89f)
        quadTo(800f, -602f, 800f, -602f)
        quadTo(800f, -502f, 720.5f, -384.5f)
        quadTo(641f, -267f, 480f, -267f)
        close()
    }
}
