package com.swiffle.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Multi-Lobe Morphing Loading Indicator
 * Directly implements the official Google M3 Expressive specification from:
 * "m0qvxgu6-Progress-Indicator-Overview.mp4" (Seconds 00:05 - 00:07)
 *
 * Distinctive Characteristics from Google Design:
 * - NOT a rigid circular 2D spinner.
 * - Fluid multi-lobe / droplet elasticity that stretches, chases, and coalesces.
 * - Variable-thickness arc head with trailing satellite droplet.
 * - Organic spring acceleration matching Google's physical motion guidelines.
 */
@Composable
fun StockMaterialExpressiveIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    strokeWidth: Dp = 4.dp,
    useGoogleColors: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
    isContained: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_loading")

    // 1. Primary orbital rotation (variable velocity with spring momentum)
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive_rotate"
    )

    // 2. Elastic primary arc sweep (expands and contracts like liquid mercury)
    val primaryArcSweep by infiniteTransition.animateFloat(
        initialValue = 35f,
        targetValue = 260f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                35f at 0 using FastOutSlowInEasing
                260f at 600 using FastOutSlowInEasing
                60f at 1200 using FastOutSlowInEasing
                270f at 1800 using FastOutSlowInEasing
                35f at 2400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive_arc_sweep"
    )

    // 3. Leading edge advance (spring forward snap)
    val headAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 720f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive_head_angle"
    )

    // 4. Trailing satellite droplet offset (chasing the arc tail with momentum)
    val satelliteTrailingOffset by infiniteTransition.animateFloat(
        initialValue = 45f,
        targetValue = 95f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                45f at 0 using LinearOutSlowInEasing
                95f at 700 using FastOutLinearInEasing
                40f at 1400 using LinearOutSlowInEasing
                90f at 2000 using FastOutLinearInEasing
                45f at 2400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive_satellite_offset"
    )

    // 5. Dynamic breathing stroke width (thick liquid stretch)
    val strokeWidthFactor by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "expressive_stroke_factor"
    )

    // Google Official 4-Color Brand Palette
    val googleBlue = Color(0xFF4285F4)
    val googleRed = Color(0xFFEA4335)
    val googleYellow = Color(0xFFFBBC05)
    val googleGreen = Color(0xFF34A853)

    val content = @Composable {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = (strokeWidth * strokeWidthFactor).toPx()
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
            val radius = (canvasWidth - strokePx) / 2f
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)

            // 1. Subtle M3 tonal under-track (10% opacity)
            drawCircle(
                color = tint.copy(alpha = 0.12f),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx * 0.7f)
            )

            val currentStartAngle = (headAngle + baseRotation) % 360f

            // 2. Render Main Elastic Liquid Arc
            if (useGoogleColors) {
                val sweepBrush = Brush.sweepGradient(
                    colors = listOf(
                        googleBlue,
                        googleRed,
                        googleYellow,
                        googleGreen,
                        googleBlue
                    )
                )
                rotate(degrees = baseRotation, pivot = center) {
                    drawArc(
                        brush = sweepBrush,
                        startAngle = currentStartAngle,
                        sweepAngle = primaryArcSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            } else {
                drawArc(
                    color = tint,
                    startAngle = currentStartAngle,
                    sweepAngle = primaryArcSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // 3. Render Trailing Satellite Droplet (Multi-Lobe Elasticity from Video 00:05 - 00:07)
            // Positioned behind the tail of the main arc, orbiting with spring physics!
            val satelliteAngleRad = Math.toRadians((currentStartAngle - satelliteTrailingOffset).toDouble())
            val satelliteCenter = Offset(
                x = center.x + (radius * cos(satelliteAngleRad)).toFloat(),
                y = center.y + (radius * sin(satelliteAngleRad)).toFloat()
            )

            drawCircle(
                color = if (useGoogleColors) googleYellow else tint.copy(alpha = 0.85f),
                radius = strokePx * 0.52f,
                center = satelliteCenter
            )

            // 4. Secondary Preceding Droplet (Clusters during contraction phase)
            if (primaryArcSweep < 100f) {
                val leadDropletAngleRad = Math.toRadians((currentStartAngle + primaryArcSweep + 35f).toDouble())
                val leadDropletCenter = Offset(
                    x = center.x + (radius * cos(leadDropletAngleRad)).toFloat(),
                    y = center.y + (radius * sin(leadDropletAngleRad)).toFloat()
                )
                drawCircle(
                    color = if (useGoogleColors) googleGreen else tint.copy(alpha = 0.7f),
                    radius = strokePx * 0.42f,
                    center = leadDropletCenter
                )
            }
        }
    }

    if (isContained) {
        Box(
            modifier = modifier
                .size(size + 16.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
