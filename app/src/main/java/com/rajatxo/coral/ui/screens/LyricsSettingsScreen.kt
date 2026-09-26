package com.rajatxo.coral.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.coral.data.prefs.LyricsAnimationManager
import com.rajatxo.coral.data.prefs.LyricsAnimationManager.LyricsAnimation
import com.rajatxo.coral.ui.icons.CoralIcons
import com.rajatxo.coral.ui.theme.CalSansFamily

/**
 * LyricsSettingsScreen — Settings → Lyrics.
 *
 * Currently holds one option: the highlight animation used in the
 * Lyrics Picker when an exact-match candidate is shown.
 *
 * Layout:
 *  - Top bar: back button | "Lyrics" title
 *  - Section header: "Highlight Animation"
 *  - Animation cards (one per option). Each card shows:
 *      • A live animated preview on the left (small duration circle
 *        with the actual animation running)
 *      • Animation name + short description on the right
 *      • A coral check mark if selected
 *
 * Tapping a card instantly switches the animation. The Lyrics Picker
 * reads the pref via StateFlow, so the change shows up next time the
 * user opens the picker.
 */
@Composable
fun LyricsSettingsScreen(
    onBackClick: () -> Unit
) {
    val currentAnim by LyricsAnimationManager.animation.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // ─── Top bar ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable(
                            interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackClick
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
                Spacer(Modifier.size(16.dp))
                Text(
                    text = "Lyrics",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalSansFamily
                )
            }

            Spacer(Modifier.height(8.dp))

            // ─── Section header ───
            Text(
                text = "HIGHLIGHT ANIMATION",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // ─── Animation cards ───
            LyricsAnimation.values().forEach { anim ->
                AnimationCard(
                    animation = anim,
                    isSelected = anim == currentAnim,
                    onClick = { LyricsAnimationManager.setAnimation(anim) }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(20.dp))

            // ─── Description explainer ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "The selected animation plays on an exact-match candidate in the Lyrics Picker. Pick the one whose vibe you like — both work the same way under the hood.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// ANIMATION CARD — one row per LyricsAnimation option
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AnimationCard(
    animation: LyricsAnimation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(16.dp)
    val accentColor = if (isSelected) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.2f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(cardShape)
            .background(Color(0xFF1A1A1A))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = accentColor,
                shape = cardShape
            )
            .clickable(
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Live preview on the left
        AnimationPreview(
            animation = animation,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.size(16.dp))
        // Text on the right
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = animation.displayName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when (animation) {
                    LyricsAnimation.PULSE_RING ->
                        "Soft glowing ring expands outward, layered echo"
                    LyricsAnimation.SUN_GLOW ->
                        "Duration circle glows like a sun, dramatic radial light"
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = CalSansFamily,
                lineHeight = 14.sp
            )
        }
        // Check mark if selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B6B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// ANIMATION PREVIEW — live mini version of the actual animation
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AnimationPreview(
    animation: LyricsAnimation,
    modifier: Modifier = Modifier
) {
    when (animation) {
        LyricsAnimation.PULSE_RING -> PulseRingPreview(modifier)
        LyricsAnimation.SUN_GLOW -> SunGlowPreview(modifier)
    }
}

@Composable
private fun PulseRingPreview(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "preview_pulse")
    val progress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "p1"
    )
    val progress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(delayMillis = 1000, durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "p2"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size.minDimension
            val center = Offset(canvasSize / 2f, canvasSize / 2f)
            val startRadius = canvasSize * 0.20f
            val maxRadius = canvasSize * 0.48f

            // Ring 1
            val r1 = startRadius + (maxRadius - startRadius) * progress1
            val a1 = (1f - progress1) * 0.7f
            if (a1 > 0.01f) {
                drawCircle(
                    color = Color(0xFF4ADE80).copy(alpha = a1),
                    radius = r1,
                    center = center,
                    style = Stroke(width = canvasSize * 0.04f, cap = StrokeCap.Round)
                )
            }
            // Ring 2
            val r2 = startRadius + (maxRadius - startRadius) * progress2
            val a2 = (1f - progress2) * 0.5f
            if (a2 > 0.01f) {
                drawCircle(
                    color = Color(0xFF4ADE80).copy(alpha = a2),
                    radius = r2,
                    center = center,
                    style = Stroke(width = canvasSize * 0.04f, cap = StrokeCap.Round)
                )
            }
        }
        // Center circle (the duration circle)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF4ADE80), Color(0xFF16A34A))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        )
    }
}

@Composable
private fun SunGlowPreview(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "preview_sun")
    // Glow breathes in/out (0.6 → 1.0) over 2.4s
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    // Slow rotation of the corona rays (one revolution per 6s)
    val coronaAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "corona"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size.minDimension
            val center = Offset(canvasSize / 2f, canvasSize / 2f)

            // ─── Outer aura (soft radial gradient glow) ───
            // Multiple concentric circles with decreasing alpha create
            // a soft, dramatic glow — like sunlight radiating outward
            // and illuminating the space around the sun.
            val sunRadius = canvasSize * 0.16f
            val maxGlowRadius = canvasSize * 0.48f
            val currentGlowRadius = sunRadius + (maxGlowRadius - sunRadius) * glowScale

            // Draw the glow as ~10 concentric circles with fading alpha
            val glowSteps = 10
            for (i in glowSteps downTo 1) {
                val stepRadius = sunRadius + (currentGlowRadius - sunRadius) * (i.toFloat() / glowSteps)
                val stepAlpha = (1f - i.toFloat() / glowSteps) * 0.18f * glowScale
                if (stepAlpha > 0.01f) {
                    drawCircle(
                        color = Color(0xFFFFA500).copy(alpha = stepAlpha),
                        radius = stepRadius,
                        center = center
                    )
                }
            }

            // ─── Corona rays (8 thin lines radiating outward, slowly rotating) ───
            val rayCount = 8
            val rayInnerRadius = sunRadius * 1.1f
            val rayOuterRadius = sunRadius * (1.4f + 0.2f * glowScale)
            val rayColor = Color(0xFFFFD700).copy(alpha = 0.5f * glowScale)
            val rayWidth = canvasSize * 0.025f
            for (i in 0 until rayCount) {
                val angleDeg = (360f / rayCount) * i + coronaAngle
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val startX = center.x + (Math.cos(angleRad) * rayInnerRadius).toFloat()
                val startY = center.y + (Math.sin(angleRad) * rayInnerRadius).toFloat()
                val endX = center.x + (Math.cos(angleRad) * rayOuterRadius).toFloat()
                val endY = center.y + (Math.sin(angleRad) * rayOuterRadius).toFloat()
                drawLine(
                    color = rayColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = rayWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        // The sun itself — warm golden radial gradient
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF4B0),  // warm pale center
                            Color(0xFFFFD700),  // golden mid
                            Color(0xFFFFA500)   // orange edge
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
        )
    }
}
