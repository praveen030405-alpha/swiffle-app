package com.swiffle.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Authentic Stock Material 4 / Material 3 Expressive Loading Indicator:
 * - Dynamic 4-color Google brand sweep or Dynamic Color Monet sweep
 * - Continuous variable-arc sweep with expanding/collapsing easing (Android 15+ System style)
 * - Rounded caps and subtle underglow track
 */
@Composable
fun StockMaterialLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 3.dp,
    useGoogleColors: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stock_m4_loading")

    // Rotation angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1332, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m4_rotate"
    )

    // Sweep arc start & length animation (Expanding & Collapsing arc)
    val baseAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 720f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2664, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m4_base_angle"
    )

    val arcLength by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2664
                20f at 0 using FastOutSlowInEasing
                280f at 666 using FastOutSlowInEasing
                20f at 1332 using FastOutSlowInEasing
                280f at 1998 using FastOutSlowInEasing
                20f at 2664
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "m4_arc_length"
    )

    // Google Brand Palette
    val googleBlue = Color(0xFF4285F4)
    val googleRed = Color(0xFFEA4335)
    val googleYellow = Color(0xFFFBBC05)
    val googleGreen = Color(0xFF34A853)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val canvasSize = this.size
            val arcSize = Size(canvasSize.width - strokePx, canvasSize.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // 1. Tonal under-track
            drawCircle(
                color = tint.copy(alpha = 0.12f),
                radius = (canvasSize.width - strokePx) / 2f,
                style = Stroke(width = strokePx)
            )

            // 2. Animated Expressive Sweep Arc
            val currentStartAngle = (baseAngle + rotation) % 360f

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
                rotate(degrees = rotation) {
                    drawArc(
                        brush = sweepBrush,
                        startAngle = currentStartAngle,
                        sweepAngle = arcLength,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            } else {
                rotate(degrees = rotation) {
                    drawArc(
                        color = tint,
                        startAngle = currentStartAngle,
                        sweepAngle = arcLength,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/**
 * Authentic Material 3 Expressive Wavy Circular Progress Indicator (Android 15+ / M3 Next Spec):
 * - Modulated wavy circular geometry where radius varies along theta: r(theta) = R + A * sin(frequency * theta + phase)
 * - Fluid rotating animation with smooth amplitude breathing
 * - Material 3 Expressive tonal styling with Google Brand sweep or primary accent
 */
@Composable
fun StockMaterialWavyLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 4.5.dp,
    waveCount: Int = 8,
    amplitude: Dp = 3.5.dp,
    useGoogleColors: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3_wavy_progress")

    // Rotation around center
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavy_rotation"
    )

    // Undulation wave phase shift
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavy_phase"
    )

    // Amplitude pulsation (breathing effect)
    val amplitudeScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavy_amp_scale"
    )

    val googleBlue = Color(0xFF4285F4)
    val googleRed = Color(0xFFEA4335)
    val googleYellow = Color(0xFFFBBC05)
    val googleGreen = Color(0xFF34A853)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val ampPx = amplitude.toPx() * amplitudeScale
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.width - strokePx * 2 - ampPx * 2) / 2f

            // 1. Subtle circular track behind the wave
            drawCircle(
                color = tint.copy(alpha = 0.08f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = strokePx * 0.75f)
            )

            // 2. Generate smooth closed wavy path
            val path = androidx.compose.ui.graphics.Path()
            val steps = 180
            val angleStep = (2 * Math.PI / steps).toFloat()

            for (i in 0..steps) {
                val theta = i * angleStep
                val currentRadius = baseRadius + ampPx * kotlin.math.sin((waveCount * theta) + phase).toFloat()
                val x = center.x + currentRadius * kotlin.math.cos(theta)
                val y = center.y + currentRadius * kotlin.math.sin(theta)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            // 3. Draw Wavy Path with Gradient or Tint
            rotate(degrees = rotation, pivot = center) {
                if (useGoogleColors) {
                    val sweepBrush = Brush.sweepGradient(
                        colors = listOf(
                            googleBlue,
                            googleRed,
                            googleYellow,
                            googleGreen,
                            googleBlue
                        ),
                        center = center
                    )
                    drawPath(
                        path = path,
                        brush = sweepBrush,
                        style = Stroke(
                            width = strokePx,
                            cap = StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                } else {
                    drawPath(
                        path = path,
                        color = tint,
                        style = Stroke(
                            width = strokePx,
                            cap = StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

/**
 * Authentic Android 15/16 Material 3 Expressive Wavy Slider:
 * - Fluid sinusoidal undulating active track: y(x) = centerY + A * sin(2π * x / wavelength - phase)
 * - Seamless amplitude tapering at thumb junction
 * - Hardware-accelerated Canvas with tactile round thumb & ripple feedback
 * - Realtime dragging and tapping with micro-haptic ticks on step change
 */
@Composable
fun StockMaterialWavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    waveAmplitude: Dp = 4.5.dp,
    waveLength: Dp = 26.dp,
    strokeWidth: Dp = 6.dp,
    thumbRadius: Dp = 10.dp,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "m3_wavy_slider_anim")

    // Continuous wave phase motion (2000ms period)
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavy_slider_phase"
    )

    var isDragging by remember { mutableStateOf(false) }
    var lastHapticVal by remember { mutableIntStateOf(value.roundToInt()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val trackWidth = size.width.toFloat()
                        if (trackWidth > 0f) {
                            val fraction = (offset.x / trackWidth).coerceIn(0f, 1f)
                            val newVal = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newVal)
                            onValueChangeFinished?.invoke()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                .pointerInput(valueRange) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            onValueChangeFinished?.invoke()
                        },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val trackWidth = size.width.toFloat()
                            if (trackWidth > 0f) {
                                val fraction = (change.position.x / trackWidth).coerceIn(0f, 1f)
                                val newVal = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                                onValueChange(newVal)

                                val rounded = newVal.roundToInt()
                                if (rounded != lastHapticVal) {
                                    lastHapticVal = rounded
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    )
                }
        ) {
            val totalWidth = size.width
            val totalHeight = size.height
            val centerY = totalHeight / 2f

            val strokePx = strokeWidth.toPx()
            val ampPx = waveAmplitude.toPx()
            val wlPx = waveLength.toPx().coerceAtLeast(10f)
            val thumbPx = thumbRadius.toPx()

            val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
            val activeWidth = (totalWidth * fraction).coerceIn(0f, totalWidth)

            // 1. Inactive Straight Track (Ahead of Thumb)
            if (activeWidth < totalWidth) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(activeWidth.coerceAtLeast(strokePx / 2f), centerY),
                    end = Offset(totalWidth - strokePx / 2f, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }

            // 2. Active Sinusoidal Wavy Track (Behind Thumb)
            if (activeWidth > 2f) {
                val path = Path()
                val stepCount = (activeWidth / 2f).toInt().coerceIn(16, 240)
                val stepX = activeWidth / stepCount.toFloat()

                for (i in 0..stepCount) {
                    val x = i * stepX
                    // Taper down amplitude smoothly as we approach the thumb so it anchors cleanly
                    val taper = if (i > stepCount - 8) {
                        (stepCount - i) / 8f
                    } else if (i < 6) {
                        i / 6f
                    } else 1f

                    val waveY = centerY + (ampPx * taper * sin((2 * Math.PI * x / wlPx) - phase).toFloat())

                    if (i == 0) {
                        path.moveTo(x.coerceAtLeast(strokePx / 2f), centerY)
                    } else {
                        path.lineTo(x, waveY)
                    }
                }

                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Material 3 Tactile Floating Thumb
            val thumbCenterX = activeWidth.coerceIn(thumbPx, totalWidth - thumbPx)

            // Ripple halo when dragging
            if (isDragging) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.18f),
                    radius = thumbPx * 1.8f,
                    center = Offset(thumbCenterX, centerY)
                )
            }

            // Primary Thumb Head
            drawCircle(
                color = activeColor,
                radius = thumbPx,
                center = Offset(thumbCenterX, centerY)
            )

            // Inner Accent Core
            drawCircle(
                color = Color.White,
                radius = thumbPx * 0.42f,
                center = Offset(thumbCenterX, centerY)
            )
        }
    }
}

