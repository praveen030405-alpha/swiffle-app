package com.swiffle.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

// Pre-calculated immutable star anchor data (Zero garbage collection in draw loop)
private data class StarAnchor(
    val seedX: Float,
    val seedY: Float,
    val speedMultiplier: Float,
    val radius: Float,
    val colorType: Int
)

/**
 * Pure Material 3 / Gemini Multi-Color Subtle Aurora Mesh Background.
 * Renders undulating, ethereal chromatic radial gradients with soft alpha
 * creating a breathing luxury aesthetic while consuming minimal GPU/CPU overhead.
 */
@Composable
fun SwiffleAuroraBackground(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()

    val phase by if (enabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "aurora_phase")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.283185f, // 2 * PI
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    // Base background tone strictly light grey-white canvas as requested: #F4F5F8
    val baseBgColor = Color(0xFFF4F5F8)

    // Vibrant, luminous watercolor palette on light grey-white canvas
    val colorIndigo = Color(0x353B82F6) // Glacial Cobalt
    val colorViolet = Color(0x308B5CF6) // Soft Lavender Mist
    val colorCoral = Color(0x2806B6D4)  // Ice Cyan Shimmer
    val colorAmber = Color(0x22F59E0B)  // Gentle Dawn Warmth
    val colorCyan = Color(0x2D60A5FA)   // Electric Ice Blue

    val colors1 = remember { listOf(colorViolet, colorIndigo, Color.Transparent) }
    val colors2 = remember { listOf(colorCoral, Color.Transparent) }
    val colors3 = remember { listOf(colorAmber, Color.Transparent) }
    val colors4 = remember { listOf(colorCyan, Color.Transparent) }

    val starAnchors = remember {
        List(14) { i ->
            val seedX = ((i * 137.5f) % 1000f) / 1000f
            val seedY = (((i * 223.7f) + 120f) % 1000f) / 1000f
            val speed = 0.5f + ((i % 5) * 0.15f)
            val rad = if (i % 7 == 0) 2.2f else if (i % 3 == 0) 1.6f else 1.1f
            StarAnchor(seedX, seedY, speed, rad, i % 4)
        }
    }

    val starBaseColor0 = Color(0xFF4285F4)
    val starBaseColor1 = Color(0xFF9B72CB)
    val starBaseColor2 = Color(0xFFF4B400)
    val starBaseColor3 = Color(0xFF0F172A)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = baseBgColor)

            val w = size.width
            val h = size.height

            // 1. Primary Violet/Indigo Cloud (Orb 1: Floating upper-left to center)
            val orb1X = w * (0.35f + 0.20f * cos(phase))
            val orb1Y = h * (0.28f + 0.12f * sin(phase))
            val orb1Radius = w * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = colors1,
                    center = Offset(orb1X, orb1Y),
                    radius = orb1Radius
                ),
                radius = orb1Radius,
                center = Offset(orb1X, orb1Y)
            )

            // 2. Rose Coral Cloud (Orb 2: Floating upper-right)
            val orb2X = w * (0.75f - 0.22f * sin(phase * 0.9f))
            val orb2Y = h * (0.20f + 0.15f * cos(phase * 0.9f))
            val orb2Radius = w * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = colors2,
                    center = Offset(orb2X, orb2Y),
                    radius = orb2Radius
                ),
                radius = orb2Radius,
                center = Offset(orb2X, orb2Y)
            )

            // 3. Warm Amber Glow (Orb 3: Lower-left gentle warmth)
            val orb3X = w * (0.22f + 0.15f * sin(phase * 1.1f))
            val orb3Y = h * (0.70f - 0.14f * cos(phase * 1.1f))
            val orb3Radius = w * 0.80f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = colors3,
                    center = Offset(orb3X, orb3Y),
                    radius = orb3Radius
                ),
                radius = orb3Radius,
                center = Offset(orb3X, orb3Y)
            )

            // 4. Cyan / Indigo Mist (Orb 4: Lower-right anchor)
            val orb4X = w * (0.78f + 0.12f * cos(phase * 0.8f))
            val orb4Y = h * (0.82f + 0.10f * sin(phase * 0.8f))
            val orb4Radius = w * 0.75f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = colors4,
                    center = Offset(orb4X, orb4Y),
                    radius = orb4Radius
                ),
                radius = orb4Radius,
                center = Offset(orb4X, orb4Y)
            )

            // 5. Celestial Stardust (Zero allocation, smooth twinkling)
            val baseStarScale = if (isDark) 0.85f else 0.55f
            for (idx in starAnchors.indices) {
                val star = starAnchors[idx]
                val currentY = (h * star.seedY + (phase * 60f * star.speedMultiplier)) % h
                val currentX = (w * star.seedX + (sin(phase + idx) * 16f)) % w
                val twinkle = (sin(phase * 2.5f + idx * 1.7f) + 1f) * 0.5f
                val starAlpha = (0.25f + 0.55f * twinkle) * baseStarScale

                val baseColor = when (star.colorType) {
                    0 -> starBaseColor0
                    1 -> starBaseColor1
                    2 -> starBaseColor2
                    else -> starBaseColor3
                }

                drawCircle(
                    color = baseColor.copy(alpha = starAlpha),
                    radius = star.radius,
                    center = Offset(currentX, currentY)
                )
            }
        }

        content()
    }
}
