package com.swiffle.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authentic Stock Material 4 Pro Celebratory Checkmark with Burst Effect
 * Features:
 * - Play Store / Pixel setup complete burst physics
 * - Smooth slow-expanding tonal badge
 * - 16 multi-color radiant particles with dynamic velocity curves
 * - Animated drawn checkmark vector path (stroke draws progressively across the circle)
 * - Concentric shockwave ripple
 */
@Composable
fun StockMaterialCheckmark(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    triggerAnimation: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    checkColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    // 1. Badge Blossom
    val badgeScale = remember { Animatable(0f) }

    // 2. Shockwave Burst
    val shockwaveRadius = remember { Animatable(0.2f) }
    val shockwaveAlpha = remember { Animatable(1f) }

    // 3. Radiant Particle Burst (16 particles)
    val particleProgress = remember { Animatable(0f) }
    val particleAlpha = remember { Animatable(1f) }

    // 4. Progressively Drawn Checkmark Vector
    val checkDrawProgress = remember { Animatable(0f) }

    // Official Google 4-Color Palette for the burst sparks
    val googleBlue = Color(0xFF4285F4)
    val googleRed = Color(0xFFEA4335)
    val googleYellow = Color(0xFFFBBC05)
    val googleGreen = Color(0xFF34A853)
    val burstColors = remember {
        listOf(
            googleBlue, googleYellow, googleGreen, googleRed,
            googleBlue, googleGreen, googleYellow, googleRed,
            googleGreen, googleBlue, googleRed, googleYellow,
            googleBlue, googleYellow, googleGreen, googleRed
        )
    }

    // 5. Continuous Subtle Ambient Particle Aura (seamlessly looped with harmonic continuity)
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_celebration")
    val ambientRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_rotation"
    )
    val ambientHaloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_halo_scale"
    )
    val ambientHaloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_halo_alpha"
    )

    LaunchedEffect(triggerAnimation) {
        if (!triggerAnimation) {
            badgeScale.snapTo(0f)
            shockwaveRadius.snapTo(0.2f)
            shockwaveAlpha.snapTo(1f)
            particleProgress.snapTo(0f)
            particleAlpha.snapTo(1f)
            checkDrawProgress.snapTo(0f)
            return@LaunchedEffect
        }

        // Small pause to let the page settle into view
        kotlinx.coroutines.delay(120)

        // Step 1: Badge blooms smoothly
        badgeScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f) // Material Emphasized
            )
        )

        // Step 2: Concurrently trigger shockwave & particles
        shockwaveRadius.animateTo(
            targetValue = 1.4f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        shockwaveAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 350, easing = LinearEasing)
        )
    }

    LaunchedEffect(triggerAnimation) {
        if (!triggerAnimation) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        // Particle burst travels outwards smoothly
        launch {
            particleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1100, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f))
            )
        }
        // Fade particles out towards the end of travel
        launch {
            kotlinx.coroutines.delay(500)
            particleAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 600, easing = LinearEasing)
            )
        }
    }

    LaunchedEffect(triggerAnimation) {
        if (!triggerAnimation) return@LaunchedEffect
        // Checkmark draws progressively across the badge with gentle overswing
        checkDrawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 950,
                delayMillis = 420,
                easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
            )
        )
    }

    Box(
        modifier = modifier.size(size * 1.8f),
        contentAlignment = Alignment.Center
    ) {
        // Shockwave Ripple
        if (shockwaveAlpha.value > 0f) {
            Canvas(modifier = Modifier.size(size * 1.6f)) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val baseRadius = (size.toPx() * 0.42f) * shockwaveRadius.value
                drawCircle(
                    color = googleGreen.copy(alpha = 0.4f * shockwaveAlpha.value),
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }
        }

        // Radiant Multi-Particle Blast (16 vibrant Google dots)
        if (particleAlpha.value > 0f) {
            Canvas(modifier = Modifier.size(size * 1.7f)) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val minRadius = size.toPx() * 0.48f
                val maxDistance = size.toPx() * 0.38f

                for (i in 0 until 16) {
                    val angle = (i * 22.5) * (Math.PI / 180.0)
                    val distance = minRadius + (maxDistance * particleProgress.value)
                    val px = (center.x + distance * cos(angle)).toFloat()
                    val py = (center.y + distance * sin(angle)).toFloat()
                    val particleRadius = (4.dp.toPx() * (1f - particleProgress.value * 0.35f))

                    drawCircle(
                        color = burstColors[i % burstColors.size].copy(alpha = particleAlpha.value),
                        radius = particleRadius,
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Continuous Ambient Subtle Orbital Sparkles (mathematically continuous, zero-jump seamless loop)
        if (triggerAnimation && badgeScale.value > 0.8f) {
            Canvas(modifier = Modifier.size(size * 1.55f)) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val baseOrbit = size.toPx() * 0.52f
                val twoPi = 2.0 * Math.PI

                // 8 subtle ambient floating sparks with gentle twinkle & continuous 360-degree orbital drift
                for (i in 0 until 8) {
                    val baseAngle = (i.toDouble() / 8.0) * twoPi
                    val angle = baseAngle + (ambientRotation.toDouble() * twoPi)
                    val radialWobble = sin((ambientRotation.toDouble() * twoPi * 2.0) + (i * Math.PI / 4.0)).toFloat() * 4.dp.toPx()
                    val dist = baseOrbit + radialWobble
                    val px = (center.x + dist * cos(angle)).toFloat()
                    val py = (center.y + dist * sin(angle)).toFloat()

                    // Twinkle is completely periodic and continuous across the 0 -> 1 loop
                    val twinklePhase = (ambientRotation.toDouble() * twoPi * 3.0) + (i * Math.PI / 2.0)
                    val harmonicNormalized = ((sin(twinklePhase) + 1.0) / 2.0).toFloat()
                    val sparkAlpha = (0.20f + 0.45f * harmonicNormalized)
                    val sparkRadius = (2.2.dp.toPx() * (0.85f + 0.30f * harmonicNormalized))

                    drawCircle(
                        color = burstColors[i % 4].copy(alpha = sparkAlpha),
                        radius = sparkRadius,
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Ambient Outer Breathing Halo
        Box(
            modifier = Modifier
                .size(size * ambientHaloScale)
                .scale(badgeScale.value)
                .clip(CircleShape)
                .background(containerColor.copy(alpha = ambientHaloAlpha))
        )

        // Main Material 4 Pro Tonal Container
        Box(
            modifier = Modifier
                .size(size * 0.84f)
                .scale(badgeScale.value)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            // Path-Drawn Vector Checkmark
            Canvas(modifier = Modifier.size(size * 0.48f)) {
                val w = this.size.width
                val h = this.size.height

                // Canonical Google Material Rounded Checkmark Path coordinates
                val fullPath = Path().apply {
                    moveTo(w * 0.18f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.76f)
                    lineTo(w * 0.82f, h * 0.26f)
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(fullPath, false)
                val totalLength = pathMeasure.length

                val drawnPath = Path()
                pathMeasure.getSegment(
                    startDistance = 0f,
                    stopDistance = totalLength * checkDrawProgress.value,
                    destination = drawnPath,
                    startWithMoveTo = true
                )

                drawPath(
                    path = drawnPath,
                    color = checkColor,
                    style = Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
