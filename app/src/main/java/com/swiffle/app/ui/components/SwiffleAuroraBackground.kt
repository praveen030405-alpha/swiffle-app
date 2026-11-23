package com.swiffle.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Shader
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// ── Core #440E03 Maroon & Dark Void Palette Constants ────────────────────────
val MaroonClothPrimary       = Color(0xFF440E03) // Primary Splash Cloth (#440E03)
val MaroonClothElevated      = Color(0xFF4F1205)
val MaroonClothRecessed      = Color(0xFF330902)
val MaroonNeumorphLight      = Color(0xFF731E0D)
val MaroonNeumorphBevelLight = Color(0xFF8A2B17)
val MaroonNeumorphDark       = Color(0xFF1B0400)
val MaroonNeumorphBevelDark  = Color(0xFF220500)
val MaroonIvoryText          = Color(0xFFFFF8F6) // Crisp warm silk-ivory text
val MaroonBlushSecondary     = Color(0xFFEBB0AF) // Secondary warm blush text
val MaroonAccentRose         = Color(0xFFD9807F) // Accent rose highlights

/**
 * Plain #440E03 Maroon Woven Cloth Texture Background with Ultra-Fluid
 * Atmospheric Dissolve into a Deep Dark Black Void (`#000000`).
 *
 * - `darkVoidProgress = 0f`: Full tactile #440E03 woven maroon cloth on the Splash Screen.
 * - As `darkVoidProgress` smoothly advances toward `1f` (across Get Started, Sign Up,
 *   Onboarding Tour, and Homepage), a radial obsidian-black twilight vignette and
 *   silk-black veil imperceptibly melt the #440E03 cloth into a Deep Dark Black Void (#000000).
 */
val CachedMaroonClothShaderBrush: ShaderBrush by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val tileSize = 96
    val pixels = IntArray(tileSize * tileSize)
    val twoPi = (Math.PI * 2.0).toFloat()

    // Exact #440E03 base RGB: R = 68 (0x44), G = 14 (0x0E), B = 3 (0x03)
    val baseR = 68
    val baseG = 14
    val baseB = 3

    for (y in 0 until tileSize) {
        val weftPhase = (y.toFloat() / tileSize.toFloat()) * twoPi
        val weftSlub = (sin(weftPhase * 6f) * 2.0f + cos(weftPhase * 15f) * 1.2f)

        for (x in 0 until tileSize) {
            val warpPhase = (x.toFloat() / tileSize.toFloat()) * twoPi
            val warpSlub = (cos(warpPhase * 8f) * 2.0f + sin(warpPhase * 19f) * 1.2f)

            val threadX = x / 2
            val threadY = y / 2
            val isWarpOver = ((threadX + threadY) and 1) == 0

            val subX = x and 1
            val subY = y and 1
            val threadCrown = if (isWarpOver) {
                if (subX == 0) 3.2f else -2.4f
            } else {
                if (subY == 0) 2.8f else -2.6f
            }

            val intersectionShadow = if (subX == 1 && subY == 1) -3.0f else 0f

            val hash = ((x * 73856093) xor (y * 19349663)) and 0xFF
            val fiberGrain = ((hash / 255f) - 0.5f) * 2.6f

            val delta = (if (isWarpOver) warpSlub else weftSlub) +
                threadCrown + intersectionShadow + fiberGrain

            val r = (baseR + delta * 1.35f).toInt().coerceIn(32, 102)
            val g = (baseG + delta * 0.42f).toInt().coerceIn(4, 30)
            val b = (baseB + delta * 0.22f).toInt().coerceIn(0, 14)

            pixels[y * tileSize + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    val bitmap = Bitmap.createBitmap(pixels, tileSize, tileSize, Bitmap.Config.ARGB_8888)
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    ShaderBrush(shader)
}

@Composable
fun SwiffleAuroraBackground(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    darkVoidProgress: Float = 0f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val p = darkVoidProgress.coerceIn(0f, 1f)

    // Immediately halt orbital animation clocks once background fade-to-black completes
    if (!enabled || p >= 0.999f) {
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color(0xFF000000))
            }
            content()
        }
        return
    }

    val clothShaderBrush = remember { CachedMaroonClothShaderBrush }

    // Continuous slow orbital phase for organic Aurora lobe drift while visible
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora_void_drift")
    val auroraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 7200,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "aurora_phase_angle"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxDim = size.maxDimension.coerceAtLeast(1f)

            // Smooth perceptual retention of the #440E03 Maroon Cloth:
            // At p = 0.0 -> 1.00 (100% #440E03 Maroon Cloth, zero black)
            // At p = 0.3 -> 0.85 (85% Maroon with drifting Aurora plumes)
            // At p = 0.6 -> 0.52 (52% Maroon & Crimson Aurora ribbons over Black Void)
            // At p = 1.0 -> 0.00 (100% Pure #000000 Dark Black Void)
            val clothRetention = ((1f - p) * (1f - 0.35f * p)).coerceIn(0f, 1f)

            // Solid base color smoothly interpolated between #440E03 Maroon and #000000 Black Void
            drawRect(
                color = androidx.compose.ui.graphics.lerp(
                    Color(0xFF000000),
                    MaroonClothPrimary,
                    clothRetention
                )
            )

            if (clothRetention > 0.002f) {
                // 1. Tactile #440E03 Woven Cloth Layer fading smoothly into the #000000 Void
                drawRect(
                    brush = clothShaderBrush,
                    alpha = clothRetention
                )

                // 2. Drifting Aurora Ribbon A (Warm Crimson-Rose #8C2310 Borealis Wave)
                val auroraWaveIntensity = (sin(p * Math.PI.toFloat()) * 0.85f + (1f - p) * 0.35f).coerceIn(0f, 1f)
                val ribbon1Center = Offset(
                    x = size.width * (0.30f + 0.36f * p + 0.10f * sin(auroraPhase)),
                    y = size.height * (0.28f + 0.24f * p + 0.08f * cos(auroraPhase))
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8C2310).copy(alpha = 0.46f * auroraWaveIntensity),
                            Color(0xFF541105).copy(alpha = 0.28f * auroraWaveIntensity),
                            Color.Transparent
                        ),
                        center = ribbon1Center,
                        radius = maxDim * 0.72f
                    )
                )

                // 3. Drifting Aurora Ribbon B (Deep Ruby-Wine #6E1708 Counter-Drifting Plume)
                val ribbon2Center = Offset(
                    x = size.width * (0.74f - 0.32f * p - 0.09f * cos(auroraPhase * 0.85f)),
                    y = size.height * (0.72f - 0.28f * p + 0.08f * sin(auroraPhase * 0.85f))
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF75190A).copy(alpha = 0.40f * auroraWaveIntensity),
                            Color(0xFF3B0B02).copy(alpha = 0.22f * auroraWaveIntensity),
                            Color.Transparent
                        ),
                        center = ribbon2Center,
                        radius = maxDim * 0.65f
                    )
                )

                // 4. Sweeping Obsidian Void Plume (Expands organically from bottom-right to envelop the cloth)
                if (p > 0.01f) {
                    val voidPlumeCenter = Offset(
                        x = size.width * (0.80f - 0.30f * p + 0.06f * cos(auroraPhase)),
                        y = size.height * (0.86f - 0.36f * p + 0.05f * sin(auroraPhase))
                    )
                    val voidPlumeRadius = (maxDim * (0.28f + 0.95f * p)).coerceAtLeast(60f)
                    val voidPlumeAlpha = (p * p * 0.92f).coerceIn(0f, 1f)

                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF000000).copy(alpha = voidPlumeAlpha),
                                Color(0xFF090100).copy(alpha = voidPlumeAlpha * 0.60f),
                                Color.Transparent
                            ),
                            center = voidPlumeCenter,
                            radius = voidPlumeRadius
                        )
                    )
                }
            }
        }

        content()
    }
}

fun Modifier.neumorphicExtruded(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 7.dp,
    isPressed: Boolean = false,
    surfaceColor: Color = MaroonClothPrimary,
    accentRim: Boolean = false
): Modifier = this.drawBehind {
    val radiusPx = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
    val elevPx = if (isPressed) (elevation * 0.3f).toPx() else elevation.toPx()
    val blurPx = (elevPx * 1.65f).coerceAtLeast(1f)

    drawIntoCanvas { canvas ->
        val lightPaint = Paint().also { p ->
            p.asFrameworkPaint().apply {
                isAntiAlias = true
                color = MaroonNeumorphLight.copy(alpha = if (isPressed) 0.35f else 0.72f).toArgb()
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
        }
        val darkPaint = Paint().also { p ->
            p.asFrameworkPaint().apply {
                isAntiAlias = true
                color = MaroonNeumorphDark.copy(alpha = if (isPressed) 0.65f else 0.92f).toArgb()
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
        }

        canvas.drawRoundRect(
            left = -elevPx * 0.75f,
            top = -elevPx * 0.75f,
            right = size.width - elevPx * 0.75f,
            bottom = size.height - elevPx * 0.75f,
            radiusX = radiusPx,
            radiusY = radiusPx,
            paint = lightPaint
        )

        canvas.drawRoundRect(
            left = elevPx * 0.85f,
            top = elevPx * 0.85f,
            right = size.width + elevPx * 0.85f,
            bottom = size.height + elevPx * 0.85f,
            radiusX = radiusPx,
            radiusY = radiusPx,
            paint = darkPaint
        )
    }

    val bodyBrush = if (isPressed) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF3A0B02), surfaceColor, Color(0xFF4B1004)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF521306), surfaceColor, Color(0xFF390B02)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    }

    drawRoundRect(
        brush = bodyBrush,
        cornerRadius = CornerRadius(radiusPx, radiusPx)
    )

    val topRim = if (accentRim) MaroonAccentRose.copy(alpha = 0.65f) else MaroonNeumorphBevelLight.copy(alpha = 0.75f)
    val midRim = if (accentRim) MaroonAccentRose.copy(alpha = 0.22f) else surfaceColor.copy(alpha = 0.25f)
    val botRim = MaroonNeumorphBevelDark.copy(alpha = 0.90f)

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(topRim, midRim, botRim),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(width = 1.2.dp.toPx())
    )
}

fun Modifier.neumorphicInset(
    cornerRadius: Dp = 16.dp,
    wellColor: Color = MaroonClothRecessed,
    isFocused: Boolean = false,
    isError: Boolean = false
): Modifier = this.drawBehind {
    val radiusPx = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
    val strokePx = 1.4.dp.toPx()

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF280601), wellColor, Color(0xFF3D0C03)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        cornerRadius = CornerRadius(radiusPx, radiusPx)
    )

    val innerShadowInset = 1.5.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xCC160200), Color.Transparent, Color(0x556E1C0C)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        topLeft = Offset(innerShadowInset, innerShadowInset),
        size = androidx.compose.ui.geometry.Size(
            (size.width - innerShadowInset * 2).coerceAtLeast(0f),
            (size.height - innerShadowInset * 2).coerceAtLeast(0f)
        ),
        cornerRadius = CornerRadius((radiusPx - innerShadowInset).coerceAtLeast(0f)),
        style = Stroke(width = 2.2.dp.toPx())
    )

    val rimBrush = when {
        isError -> Brush.linearGradient(colors = listOf(Color(0xFFFF6B6B), Color(0xFFD32F2F)))
        isFocused -> Brush.linearGradient(colors = listOf(MaroonAccentRose, MaroonBlushSecondary))
        else -> Brush.linearGradient(
            colors = listOf(Color(0xFF1A0300), Color(0xFF3B0C02), Color(0xFF701D0C)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
    }

    drawRoundRect(
        brush = rimBrush,
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(width = if (isFocused || isError) 1.8.dp.toPx() else strokePx)
    )
}
