package com.rajatxo.coral.ui.cynthia

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * CynthiaSearchScreen — billing/receipt styled search overlay.
 *
 * STRUCTURE (the "billing" skeleton — content inside the paper comes later):
 *
 *   ┌─────────────────────────────────────┐
 *   │  ← (back)                           │  ← header (back icon only)
 *   ├─────────────────────────────────────┤
 *   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  ← dark printer slot
 *   │  ────────────────────────────      │   (with slit opening)
 *   │ ┌─────────────────────────────────┐ │
 *   │ │                                 │ │
 *   │ │   (paper content — placeholder) │ │ ← paper emerging
 *   │ │                                 │ │
 *   │ │  ▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼▲▼    │ │ ← jagged torn bottom
 *   │ └─────────────────────────────────┘ │
 *   └─────────────────────────────────────┘
 *
 * Matches the reference video (MEVER_2026.10.04):
 *   • Dark printer slot at top (fixed)
 *   • Paper slides DOWN out of the slot on entry (spring animation)
 *   • Paper has jagged/zigzag torn bottom edge (thermal receipt aesthetic)
 *
 * The paper's inside is empty for now — search text + results + history
 * will go inside later. Existing Astra SearchScreen.kt is left untouched.
 */
@Composable
fun CynthiaSearchScreen(
    onDismiss: () -> Unit
) {
    // Reveal fraction: 0 = paper fully tucked under slot, 1 = fully out.
    // Animates from 0 → 1 on screen entry, mirroring the reference video.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Cynthia uses a near-black background — matches the rest of Cynthia UI.
    val cynthiaBg = Color(0xFF050507)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cynthiaBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ─── Header: back button only ─────────────────────────────
            // No title — paper content (search field) goes inside the paper
            // later, not in the header. Header is minimal: just a back arrow.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoralIcons.ChevronLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ─── Billing structure: printer slot + paper ───────────────
            BillingPaperStructure(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                revealFraction = reveal.value
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// BILLING STRUCTURE — printer slot + paper emerging + jagged bottom
// ════════════════════════════════════════════════════════════════════

/**
 * Outer container for the billing-style search area. Composes a
 * [PrinterSlot] at the top (always visible) and a [ReceiptPaper]
 * below it that animates its reveal on entry.
 */
@Composable
private fun BillingPaperStructure(
    modifier: Modifier = Modifier,
    revealFraction: Float = 1f
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        val density = LocalDensity.current
        val fullHeightPx = constraints.maxHeight.toFloat()
        val slotHeight = 48.dp
        val slotHeightPx = with(density) { slotHeight.toPx() }
        // Paper visible height grows from 0 → (totalHeight - slotHeight)
        // as `revealFraction` animates 0 → 1.
        val visiblePaperHeightPx = ((fullHeightPx - slotHeightPx).coerceAtLeast(0f)) * revealFraction
        val visiblePaperHeightDp = with(density) { visiblePaperHeightPx.toDp() }

        Box(modifier = Modifier.fillMaxSize()) {
            // ─── Paper (anchored to top, height = visiblePaperHeight) ───
            // Padding-top = slotHeight so the paper's top edge sits just
            // under the slot. The slot is drawn AFTER (on top of) the paper
            // so the paper's top edge is visually hidden by the slot.
            ReceiptPaper(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = slotHeight)
                    .height(visiblePaperHeightDp)
            )

            // ─── Slot (drawn ON TOP of paper's top edge) ───
            PrinterSlot(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight)
                    .align(Alignment.TopCenter)
            )
        }
    }
}

// ─── Printer slot ──────────────────────────────────────────────────────

/**
 * The dark printer housing at the top of the billing structure.
 * Visually sits on top of the paper's top edge so the paper
 * appears to emerge from under it.
 *
 * Drawn as a dark rounded rectangle with:
 *   • A subtle top-to-bottom gradient (slightly lighter at top edge).
 *   • A thin "slot opening" slit near the bottom of the housing —
 *     the slit the paper comes out of.
 *   • A soft drop shadow underneath, cast onto the paper below.
 */
@Composable
private fun PrinterSlot(modifier: Modifier = Modifier) {
    // Charcoal tones — match Cynthia's near-black palette.
    val slotColorTop = Color(0xFF1F1F26)
    val slotColorBottom = Color(0xFF0A0A0F)
    val slotOpeningColor = Color(0xFF2A2A33)
    val shadowColor = Color.Black.copy(alpha = 0.55f)

    Box(modifier = modifier) {
        // Drop shadow cast onto the paper below (a thin band, fading down).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to shadowColor,
                            1.0f to Color.Transparent
                        )
                    )
                )
        )

        // Slot housing body (dark, rounded bottom corners only).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to slotColorTop,
                            0.65f to slotColorBottom,
                            1.0f to slotColorBottom
                        )
                    )
                )
        ) {
            // Top edge highlight (subtle, suggests a 3D rounded top).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.07f))
            )

            // Slot opening — a thin darker slit near the bottom of the
            // housing, indicating where the paper emerges from.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(slotOpeningColor)
            )

            // Tiny "status LED" dot on the right side of the slot —
            // subtle detail that suggests this is a printer, not just
            // a dark bar. Pulses softly to suggest the printer is "active".
            // (Static color for now — animation can be added later.)
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6B6BFF).copy(alpha = 0.7f))
            )
        }
    }
}

// ─── Receipt paper ────────────────────────────────────────────────────

/**
 * The receipt paper, clipped to a [JaggedBottomShape] so the bottom edge
 * has a torn/zigzag appearance.
 *
 * Content is rendered on top of the cream background — for now it's
 * just a placeholder; later this is where search text + results + history
 * will live (themed as ink on paper).
 */
@Composable
private fun ReceiptPaper(
    modifier: Modifier = Modifier
) {
    // Warm cream paper — matches the reference video's "thermal receipt" look
    // (Chaayos receipt was pale yellow/cream). Slight gradient for depth.
    val paperColorTop = Color(0xFFF8F2E3)
    val paperColorBottom = Color(0xFFEFE7CF)
    val paperEdgeShadow = Color.Black.copy(alpha = 0.20f)

    // Tooth dimensions for the jagged bottom edge.
    val toothWidthDp = 12f
    val toothHeightDp = 8f

    val shape = remember(toothWidthDp, toothHeightDp) {
        JaggedBottomShape(
            toothWidthDp = toothWidthDp,
            toothHeightDp = toothHeightDp
        )
    }

    Box(
        modifier = modifier
            // Soft outer shadow — gives the paper lift off the dark bg.
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = paperEdgeShadow,
                spotColor = paperEdgeShadow
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to paperColorTop,
                        0.85f to paperColorTop,
                        1.0f to paperColorBottom
                    )
                )
            )
    ) {
        // Soft side shadows (paper depth against dark background).
        Box(
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.18f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.18f)
                        )
                    )
                )
        )

        // ─── Paper content (placeholder — will be filled later) ───
        // Empty for now. Search text + results + history will go here,
        // themed as ink on cream paper.
        PaperContentPlaceholder()
    }
}

// ─── Jagged bottom edge shape ──────────────────────────────────────────

/**
 * A rectangle with the bottom edge replaced by a zigzag (torn paper)
 * pattern. The top, left and right edges are straight — only the
 * bottom is jagged.
 *
 * @param toothWidthDp   Width of each zigzag tooth in dp.
 * @param toothHeightDp  Height of each zigzag tooth in dp (how deep
 *                        the tear goes up into the paper).
 */
private class JaggedBottomShape(
    private val toothWidthDp: Float,
    private val toothHeightDp: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val toothW = with(density) { toothWidthDp.dp.toPx() }
        val toothH = with(density) { toothHeightDp.dp.toPx() }
        val w = size.width
        val h = size.height
        // Number of teeth that fit across the width.
        val toothCount = ((w / toothW).toInt().coerceAtLeast(1))
        // Recompute actual tooth width so teeth distribute evenly and
        // end exactly at the right edge.
        val actualToothW = w / toothCount

        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h - toothH)
            // Zigzag bottom from right → left.
            var x = w
            for (i in 0 until toothCount) {
                val nextX = x - actualToothW
                if (i % 2 == 0) {
                    // Tooth pointing DOWN (paper extends further down).
                    lineTo(nextX, h)
                } else {
                    // Notch pointing UP (tear cuts into the paper).
                    lineTo(nextX, h - toothH)
                }
                x = nextX
            }
            // Close back up the left edge → top-left corner.
            lineTo(0f, h - toothH)
            close()
        }
        return Outline.Generic(path)
    }
}

// ─── Paper content placeholder ────────────────────────────────────────

/**
 * Empty placeholder rendered inside the receipt paper. Will be
 * replaced with the actual search content (text + results +
 * history) later — themed as ink on cream paper.
 */
@Composable
private fun PaperContentPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Subtle hint text — visible only as a placeholder so it's
        // obvious where content will go. Will be removed once real
        // search text + results are moved in.
        Text(
            text = "• paper content •",
            color = Color(0xFF9C9583),
            fontSize = 11.sp,
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.Light
        )
    }
}
