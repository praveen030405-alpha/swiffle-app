package com.swiffle.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material 3 Expressive Wavy Progress Indicator
 * Directly implements the official Google M3 Expressive specification from:
 * "m0qvxgu6-Progress-Indicator-Overview.mp4" (Seconds 00:00 - 00:02)
 *
 * Features:
 * - Fluid traveling sine-wave line during indeterminate / active progress
 * - Terminal stop dot at the track boundary
 * - Smooth transition from wavy motion to flat determinate completion
 */
@Composable
fun StockMaterialWavyProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null, // null for indeterminate wavy motion; 0f..1f for determinate
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    strokeWidth: Dp = 3.5.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
    showStopDot: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3_wavy_progress")

    // Traveling phase for sine wave
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavy_phase"
    )

    // Breathing wave height for living pulse
    val waveAmpFactor by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavy_amp"
    )

    val isIndeterminate = progress == null

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokePx = strokeWidth.toPx()
        val ampPx = amplitude.toPx() * if (isIndeterminate) waveAmpFactor else 0f
        val waveLenPx = wavelength.toPx()
        val stopDotRadius = strokePx * 0.9f
        val rightMargin = if (showStopDot) strokePx * 3f else 0f
        val activeWidth = (width - rightMargin).coerceAtLeast(0f)

        // 1. Draw subtle background track line
        drawLine(
            color = trackColor.copy(alpha = 0.4f),
            start = Offset(0f, centerY),
            end = Offset(width - if (showStopDot) stopDotRadius * 2.5f else 0f, centerY),
            strokeWidth = strokePx * 0.6f,
            cap = StrokeCap.Round
        )

        // 2. Draw Terminal Stop Dot (M3 Official Anchor Dot)
        if (showStopDot) {
            drawCircle(
                color = color,
                radius = stopDotRadius,
                center = Offset(width - stopDotRadius - 2f, centerY)
            )
        }

        // 3. Draw Traveling Wavy Sine-Wave Path
        val path = Path()
        val endX = if (isIndeterminate) activeWidth else (activeWidth * (progress ?: 0f)).coerceAtLeast(0f)

        if (endX > 0f) {
            val stepPx = 2.5f // High resolution sampling for pristine anti-aliased wave
            var x = 0f
            var isFirst = true

            while (x <= endX) {
                // Sine wave equation: y = sin(kx + phase) * amplitude
                val rad = (x / waveLenPx) * (2 * PI).toFloat() + (if (isIndeterminate) wavePhase else 0f)
                val y = centerY + sin(rad) * ampPx

                if (isFirst) {
                    path.moveTo(x, y)
                    isFirst = false
                } else {
                    path.lineTo(x, y)
                }
                x += stepPx
            }

            // Ensure the line meets the exact endX
            val finalRad = (endX / waveLenPx) * (2 * PI).toFloat() + (if (isIndeterminate) wavePhase else 0f)
            val finalY = centerY + sin(finalRad) * ampPx
            path.lineTo(endX, finalY)

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
