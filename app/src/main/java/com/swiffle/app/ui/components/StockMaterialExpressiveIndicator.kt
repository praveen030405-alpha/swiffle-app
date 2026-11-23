package com.swiffle.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swiffle.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  MATERIAL 3 EXPRESSIVE MORPHING SHAPE LOADER (DESIGN SYSTEM v1.1 SECTION 3.11)
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Implements Google's flagship Material 3 Expressive Morphing Shape Indicator:
 * - Continuously morphs a closed harmonic polar silhouette between:
 *   1. Soft Rounded Squircle (4-lobe cushion)
 *   2. 8-Lobe Expressive Cookie / Flower
 *   3. 12-Lobe Scalloped Seal
 * - Rendered in Mild-White Frosted Glass with a Silk-Ivory & Rose-Maroon specular
 *   rim over the #440E03 Maroon Cloth background.
 * - Optional centered Swiffle Origami emblem (`showCenterEmblem = true`).
 */
@Composable
fun M3ExpressiveMorphingShapeLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    showCenterEmblem: Boolean = true,
    emblemSize: Dp = 44.dp,
    isPaused: Boolean = false
) {
    if (isPaused) {
        Box(modifier = modifier.size(size))
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "m3_morphing_shape_loader")

    // Continuous slow orbital rotation of the morphing shape
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morph_rotation"
    )

    // Morph phase 0 -> 1 -> 2 -> 0 (Squircle <-> 8-Lobe Cookie <-> 12-Lobe Seal)
    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3600
                0f at 0 using FastOutSlowInEasing
                1f at 1200 using FastOutSlowInEasing
                2f at 2400 using FastOutSlowInEasing
                3f at 3600 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "morph_phase"
    )

    // Subtle breathing scale on RenderThread
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph_breath_scale"
    )

    val morphPath = remember { Path() }
    val innerEchoPath = remember { Path() }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = breathScale
                    scaleY = breathScale
                }
        ) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val baseRadius = (this.size.minDimension / 2f) * 0.80f

            // Compute smooth blend weights across the 3 canonical M3 Expressive shapes
            val p = morphPhase % 3f
            val w4: Float
            val w8: Float
            val w12: Float
            when {
                p < 1f -> {
                    w4 = 1f - p
                    w8 = p
                    w12 = 0f
                }
                p < 2f -> {
                    val t = p - 1f
                    w4 = 0f
                    w8 = 1f - t
                    w12 = t
                }
                else -> {
                    val t = p - 2f
                    w4 = t
                    w8 = 0f
                    w12 = 1f - t
                }
            }

            val steps = 120
            morphPath.reset()
            innerEchoPath.reset()

            for (i in 0..steps) {
                val theta = (i.toFloat() / steps.toFloat()) * (2f * PI.toFloat())
                val harmonic =
                    w4 * (0.085f * cos(4f * theta)) +
                    w8 * (0.115f * cos(8f * theta)) +
                    w12 * (0.065f * cos(12f * theta))

                val rOuter = baseRadius * (1f + harmonic)
                val x = cx + rOuter * cos(theta)
                val y = cy + rOuter * sin(theta)

                val rInner = baseRadius * 0.68f * (1f - harmonic * 0.55f)
                val ix = cx + rInner * cos(theta)
                val iy = cy + rInner * sin(theta)

                if (i == 0) {
                    morphPath.moveTo(x, y)
                    innerEchoPath.moveTo(ix, iy)
                } else {
                    morphPath.lineTo(x, y)
                    innerEchoPath.lineTo(ix, iy)
                }
            }
            morphPath.close()
            innerEchoPath.close()

            rotate(degrees = rotationDeg, pivot = Offset(cx, cy)) {
                // 1. Mild-White Frosted Glass Fill inside the morphing shape
                drawPath(
                    path = morphPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color(0xFFF7DAD9).copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.08f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(this.size.width, this.size.height)
                    )
                )

                // 2. Specular Glass Outer Rim Stroke
                drawPath(
                    path = morphPath,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFFFF8F6).copy(alpha = 0.85f),
                            Color(0xFFD9807F).copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.25f),
                            Color(0xFFD9807F).copy(alpha = 0.65f),
                            Color(0xFFFFF8F6).copy(alpha = 0.85f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Counter-rotating subtle inner glass halo path
            rotate(degrees = -rotationDeg * 0.6f, pivot = Offset(cx, cy)) {
                drawPath(
                    path = innerEchoPath,
                    color = Color.White.copy(alpha = 0.16f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        if (showCenterEmblem) {
            Image(
                painter = painterResource(id = R.drawable.ic_swiffle_logo),
                contentDescription = "Swiffle",
                modifier = Modifier
                    .size(emblemSize)
                    .graphicsLayer {
                        scaleX = 1f / breathScale * 0.98f
                        scaleY = 1f / breathScale * 0.98f
                    }
            )
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  SEAMLESS CONNECTED LOADING -> SUCCESS TICK MORPH (`M3ConnectedLoadingToSuccessTick`)
 * ══════════════════════════════════════════════════════════════════════════════
 * A single continuous vector animation on one unified Canvas that connects the
 * Material 3 Expressive loading state directly into the Success Tick without
 * swapping components:
 * 1. While `!isCompleted`: The outer 120-vertex M3 Expressive scalloped polygon
 *    morphs and rotates while an inner orbital progress arc sweeps around the core.
 * 2. When `isCompleted` becomes `true`:
 *    - `circleCoalesceProgress` (0f -> 1f) smoothly flattens the scalloped wave
 *      harmonics (`harmonic * (1f - circleCoalesceProgress)`) so the morphing
 *      loader physically contracts and coalesces its own vertices into a crisp
 *      glassmorphic circle, while the inner sweeping loading arc wraps all the
 *      way around (`sweepAngle -> 360°`) to lock the circular rim.
 *    - Overlapping seamlessly at 45% coalescence, `tickStrokeProgress` (0f -> 1f)
 *      sweeps the exact same silk-ivory stroke inward to draw the rounded
 *      Success Tick (`✓`) in one unbroken motion with a tactile spring pulse.
 */
@Composable
fun M3ConnectedLoadingToSuccessTick(
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 144.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "connected_loader_to_tick")

    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "connected_rotation"
    )

    val arcOrbitDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "connected_arc_orbit"
    )

    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                0f at 0 using FastOutSlowInEasing
                1f at 1000 using FastOutSlowInEasing
                2f at 2000 using FastOutSlowInEasing
                3f at 3000 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "connected_morph_phase"
    )

    // Stage 1: Scalloped loader vertices coalesce into a perfect circle & spinning arc closes to 360°
    val circleCoalesceProgress by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 460,
            easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
        ),
        label = "circle_coalesce_progress"
    )

    // Stage 2: Overlapping continuous stroke draws the Checkmark Tick inside the coalesced circle
    val tickStrokeProgress by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 480,
            delayMillis = 200,
            easing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
        ),
        label = "tick_stroke_progress"
    )

    // Subtle tactile spring pulse when the tick locks in
    val completionScale by animateFloatAsState(
        targetValue = when {
            !isCompleted -> 1f
            tickStrokeProgress < 0.85f -> 1.05f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 360f),
        label = "completion_pulse_scale"
    )

    // Soft radial completion aura wave
    val haloWaveProgress by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 780,
            delayMillis = 180,
            easing = FastOutSlowInEasing
        ),
        label = "halo_wave_progress"
    )

    val morphPath = remember { Path() }
    val innerEchoPath = remember { Path() }
    val tickFullPath = remember { Path() }
    val tickDrawnPath = remember { Path() }
    val tickPathMeasure = remember { androidx.compose.ui.graphics.PathMeasure() }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = completionScale
                scaleY = completionScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val maxRadius = (this.size.minDimension / 2f)
            val coalesce = circleCoalesceProgress.coerceIn(0f, 1f)

            // 1. Expanding Radial Completion Wave (emanates from the circle as the tick locks in)
            if (haloWaveProgress > 0.01f && haloWaveProgress < 0.99f) {
                val waveRadius = maxRadius * (0.60f + 0.38f * haloWaveProgress)
                val waveAlpha = (1f - haloWaveProgress) * 0.42f
                drawCircle(
                    color = Color(0xFFFFF8F6).copy(alpha = waveAlpha),
                    radius = waveRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }

            // 2. Outer M3 Expressive Morphing Shape -> Coalesces smoothly into the Outer Glass Halo Ring
            val p = morphPhase % 3f
            val w4: Float
            val w8: Float
            val w12: Float
            when {
                p < 1f -> {
                    w4 = 1f - p; w8 = p; w12 = 0f
                }
                p < 2f -> {
                    val t = p - 1f
                    w4 = 0f; w8 = 1f - t; w12 = t
                }
                else -> {
                    val t = p - 2f
                    w4 = t; w8 = 0f; w12 = 1f - t
                }
            }

            val baseRadius = maxRadius * (0.78f - 0.10f * coalesce)
            val waveDamping = (1f - coalesce) * (1f - coalesce)
            val steps = 120
            morphPath.reset()
            innerEchoPath.reset()

            for (i in 0..steps) {
                val theta = (i.toFloat() / steps.toFloat()) * (2f * PI.toFloat())
                val rawHarmonic =
                    w4 * (0.085f * cos(4f * theta)) +
                    w8 * (0.115f * cos(8f * theta)) +
                    w12 * (0.065f * cos(12f * theta))

                // As `coalesce` -> 1f, harmonic ripples flatten to 0 so the polygon becomes a true circle
                val harmonic = rawHarmonic * waveDamping
                val rOuter = baseRadius * (1f + harmonic)
                val x = cx + rOuter * cos(theta)
                val y = cy + rOuter * sin(theta)

                val rInner = baseRadius * (0.70f + 0.14f * coalesce) * (1f - harmonic * 0.5f)
                val ix = cx + rInner * cos(theta)
                val iy = cy + rInner * sin(theta)

                if (i == 0) {
                    morphPath.moveTo(x, y)
                    innerEchoPath.moveTo(ix, iy)
                } else {
                    morphPath.lineTo(x, y)
                    innerEchoPath.lineTo(ix, iy)
                }
            }
            morphPath.close()
            innerEchoPath.close()

            rotate(degrees = rotationDeg * (1f - coalesce * 0.7f), pivot = Offset(cx, cy)) {
                drawPath(
                    path = morphPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f + 0.10f * coalesce),
                            Color(0xFFF7DAD9).copy(alpha = 0.14f + 0.06f * coalesce),
                            Color.White.copy(alpha = 0.08f + 0.06f * coalesce)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(this.size.width, this.size.height)
                    )
                )

                drawPath(
                    path = morphPath,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFFFF8F6).copy(alpha = 0.80f),
                            Color(0xFFD9807F).copy(alpha = 0.50f),
                            Color.White.copy(alpha = 0.28f),
                            Color(0xFFD9807F).copy(alpha = 0.60f),
                            Color(0xFFFFF8F6).copy(alpha = 0.80f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    style = Stroke(
                        width = (2.0f + 0.4f * coalesce).dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            if (coalesce < 0.98f) {
                rotate(degrees = -rotationDeg * 0.6f, pivot = Offset(cx, cy)) {
                    drawPath(
                        path = innerEchoPath,
                        color = Color.White.copy(alpha = 0.16f * (1f - coalesce)),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // 3. Inner Connected Orbital Loading Arc -> Wraps 360° to Lock the Inner Seal Ring
            val innerRingRadius = maxRadius * 0.46f
            val innerArcStroke = 3.2.dp.toPx()
            val arcStart = arcOrbitDeg * (1f - coalesce) - 90f
            val arcSweep = 115f + 245f * coalesce

            // Subtle track under the orbital arc
            drawCircle(
                color = Color.White.copy(alpha = 0.12f + 0.08f * coalesce),
                radius = innerRingRadius,
                center = Offset(cx, cy),
                style = Stroke(width = innerArcStroke * 0.65f)
            )

            // Active sweeping arc that closes into a 360° circle upon completion
            drawArc(
                color = Color(0xFFFFF8F6).copy(alpha = 0.92f),
                startAngle = arcStart,
                sweepAngle = arcSweep,
                useCenter = false,
                topLeft = Offset(cx - innerRingRadius, cy - innerRingRadius),
                size = Size(innerRingRadius * 2f, innerRingRadius * 2f),
                style = Stroke(width = innerArcStroke, cap = StrokeCap.Round)
            )

            // 4. Connected Checkmark Tick Path (drawn by the same 3.2.dp ivory stroke inside the locked ring)
            if (tickStrokeProgress > 0.005f) {
                val checkR = innerRingRadius * 0.56f
                tickFullPath.reset()
                tickFullPath.moveTo(cx - checkR * 0.82f, cy + checkR * 0.04f)
                tickFullPath.lineTo(cx - checkR * 0.18f, cy + checkR * 0.68f)
                tickFullPath.lineTo(cx + checkR * 0.86f, cy - checkR * 0.58f)

                tickPathMeasure.setPath(tickFullPath, false)
                tickDrawnPath.reset()
                tickPathMeasure.getSegment(
                    startDistance = 0f,
                    stopDistance = tickPathMeasure.length * tickStrokeProgress.coerceIn(0f, 1f),
                    destination = tickDrawnPath,
                    startWithMoveTo = true
                )

                drawPath(
                    path = tickDrawnPath,
                    color = Color(0xFFFFF8F6),
                    style = Stroke(
                        width = 3.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/**
 * Material 3 Expressive Multi-Lobe Morphing Loading Indicator (Compact Spinner Variant)
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

    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive_rotate"
    )

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

    val headAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 720f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive_head_angle"
    )

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

    val strokeWidthFactor by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "expressive_stroke_factor"
    )

    val maroonIvory = Color(0xFFFFF8F6)
    val maroonRose = Color(0xFFD9807F)
    val maroonBlush = Color(0xFFF7DAD9)

    val content = @Composable {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = (strokeWidth * strokeWidthFactor).toPx()
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
            val radius = (canvasWidth - strokePx) / 2f
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)

            drawCircle(
                color = tint.copy(alpha = 0.14f),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx * 0.7f)
            )

            val currentStartAngle = (headAngle + baseRotation) % 360f

            if (useGoogleColors) {
                val sweepBrush = Brush.sweepGradient(
                    colors = listOf(
                        maroonIvory,
                        maroonRose,
                        maroonBlush,
                        maroonRose,
                        maroonIvory
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

            val satelliteAngleRad = Math.toRadians((currentStartAngle - satelliteTrailingOffset).toDouble())
            val satelliteCenter = Offset(
                x = center.x + (radius * cos(satelliteAngleRad)).toFloat(),
                y = center.y + (radius * sin(satelliteAngleRad)).toFloat()
            )

            drawCircle(
                color = if (useGoogleColors) maroonRose else tint.copy(alpha = 0.85f),
                radius = strokePx * 0.52f,
                center = satelliteCenter
            )

            if (primaryArcSweep < 100f) {
                val leadDropletAngleRad = Math.toRadians((currentStartAngle + primaryArcSweep + 35f).toDouble())
                val leadDropletCenter = Offset(
                    x = center.x + (radius * cos(leadDropletAngleRad)).toFloat(),
                    y = center.y + (radius * sin(leadDropletAngleRad)).toFloat()
                )
                drawCircle(
                    color = if (useGoogleColors) maroonBlush else tint.copy(alpha = 0.7f),
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
