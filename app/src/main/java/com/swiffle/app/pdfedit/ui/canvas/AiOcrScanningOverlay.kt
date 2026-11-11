package com.swiffle.app.pdfedit.ui.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.swiffle.app.ui.components.StockMaterialLogoShimmer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-Screen Google Gemini / Galaxy AI Fluid Iridescent Shimmering Overlay:
 * - 100% Laser-Free Ethereal Chromatic Aura
 * - Fluid Multi-pole Radial Gradient Shimmer (Cyan, Indigo, Purple, Sunset Coral, Amber Gold)
 * - Soft Translucent Breathing Luminescence (Document remains visible beneath)
 * - Glassmorphic Centered Processing Pill with Pulsing Star Sparkle
 */
@Composable
fun AiOcrScanningOverlay(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isScanning,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(500)),
        modifier = modifier
    ) {
        val transition = rememberInfiniteTransition(label = "GeminiShimmer")

        // 1. Fluid Chromatic Angle Rotation (0 to 360 degrees)
        val rotationAngle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Rotation"
        )

        // 2. Breathing Shimmer Intensity (Alpha oscillation between 0.14f and 0.28f)
        val shimmerAlpha by transition.animateFloat(
            initialValue = 0.14f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ShimmerAlpha"
        )

        // 3. Chromatic Wave Oscillation (-1f to 1f)
        val wavePhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "WavePhase"
        )

        // 4. Centered Icon Pulse Scale
        val iconPulse by transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.10f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "IconPulse"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
        ) {
            // Full-Screen Fluid Gemini Shimmer Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Calculate organic dynamic focal centers for multi-pole aurora
                val rad = Math.toRadians(rotationAngle.toDouble())
                val centerOffsetX1 = (cos(rad) * (w * 0.28f)).toFloat()
                val centerOffsetY1 = (sin(rad) * (h * 0.22f)).toFloat()

                val centerOffsetX2 = (cos(rad + Math.PI) * (w * 0.24f)).toFloat()
                val centerOffsetY2 = (sin(rad + Math.PI) * (h * 0.20f)).toFloat()

                val centerOffsetX3 = (sin(rad * 1.5) * (w * 0.20f)).toFloat()
                val centerOffsetY3 = (cos(rad * 1.5) * (h * 0.18f)).toFloat()

                // Primary Gemini Bloom: Electric Indigo to Vivid Cyan
                val bloom1 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4285F4).copy(alpha = shimmerAlpha * 0.95f),
                        Color(0xFF00E5FF).copy(alpha = shimmerAlpha * 0.60f),
                        Color(0xFF2979FF).copy(alpha = shimmerAlpha * 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f + centerOffsetX1, h * 0.45f + centerOffsetY1),
                    radius = maxOf(w, h) * 0.72f
                )
                drawRect(brush = bloom1)

                // Secondary Gemini Bloom: Royal Purple to Sunset Pink
                val bloom2 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFAA00FF).copy(alpha = shimmerAlpha * 0.85f),
                        Color(0xFFFF4081).copy(alpha = shimmerAlpha * 0.55f),
                        Color(0xFF7C4DFF).copy(alpha = shimmerAlpha * 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f + centerOffsetX2, h * 0.55f + centerOffsetY2),
                    radius = maxOf(w, h) * 0.68f
                )
                drawRect(brush = bloom2)

                // Tertiary Warm Accent Bloom: Amber Gold to Coral
                val bloom3 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFB300).copy(alpha = shimmerAlpha * 0.50f),
                        Color(0xFFFF5252).copy(alpha = shimmerAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f + centerOffsetX3, h * 0.50f + centerOffsetY3),
                    radius = maxOf(w, h) * 0.55f
                )
                drawRect(brush = bloom3)

                // Perimeter Chromatic Edge Aurora (Soft Iridescent Vignette)
                val edgeBrush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = shimmerAlpha * 1.2f),
                        Color(0xFF4285F4).copy(alpha = shimmerAlpha * 1.2f),
                        Color(0xFFAA00FF).copy(alpha = shimmerAlpha * 1.2f),
                        Color(0xFFFF4081).copy(alpha = shimmerAlpha * 1.2f),
                        Color(0xFFFFB300).copy(alpha = shimmerAlpha * 1.2f),
                        Color(0xFF00E5FF).copy(alpha = shimmerAlpha * 1.2f)
                    ),
                    center = Offset(w / 2f, h / 2f)
                )

                drawRoundRect(
                    brush = edgeBrush,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            // Transparent Floating Aerodynamic Swiffle Origami Icon with Ambient Breathing Aura Glow
            // (Exactly matching the Onboarding Screen loading design - No Pills, No Boxes)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft ambient radiant gradient backplate halo
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF0078D4).copy(alpha = 0.38f),
                                    Color(0xFF9B51E0).copy(alpha = 0.22f),
                                    Color(0xFF00E5FF).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Shimmering aerodynamic vector origami logo
                StockMaterialLogoShimmer(
                    size = 80.dp,
                    showContainerHalo = false,
                    showRocketContrails = false,
                    isLaunching = false
                )
            }
        }
    }
}
