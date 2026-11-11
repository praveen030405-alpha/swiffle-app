package com.swiffle.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Official Material 3 Expressive Skeleton Loader
 * Directly implements "Skeleton loaders - 1.mp4" transition specifications:
 * - Fluid harmonic shimmer wave moving across squircle container bounds
 * - Tonal blending using surfaceContainerHighest & surfaceContainerLow
 * - Standardized 8dp grid spacing with 20dp squircle radius
 */
@Composable
fun M3SkeletonDocumentRow(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shimmerTranslate - 200f, 0f),
        end = Offset(shimmerTranslate + 200f, 0f)
    )

    val containerBg = MaterialTheme.colorScheme.surfaceContainerLow
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp)) // M3 Expressive Large Corner
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Bone
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmerBrush)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Text & Meta Bones
        Column(modifier = Modifier.weight(1f)) {
            // Title Bone
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle / Date Bone
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )
        }
    }
}
