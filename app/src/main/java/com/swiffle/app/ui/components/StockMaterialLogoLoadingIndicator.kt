package com.swiffle.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.R
import com.swiffle.app.theme.ClaudeSans
import com.swiffle.app.theme.ClaudeSerif

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  M3 EXPRESSIVE MAROON LOADING SCREENS & DOCUMENT PILLS
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
fun StockMaterialRocketContrails(
    modifier: Modifier = Modifier,
    isLaunching: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rocket_contrails_transition")

    val streakProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isLaunching) 350 else 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "streak1"
    )

    val streakProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isLaunching) 400 else 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "streak2"
    )

    val streakProgress3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isLaunching) 300 else 650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "streak3"
    )

    val primaryColor = MaroonIvoryText
    val tertiaryColor = MaroonAccentRose

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val baseAngleRad = Math.toRadians(225.0).toFloat()
        val dirX = kotlin.math.cos(baseAngleRad)
        val dirY = kotlin.math.sin(baseAngleRad)

        fun drawContrailStreak(
            progress: Float,
            startXOffset: Float,
            startYOffset: Float,
            streakLength: Float,
            strokeWidth: Float,
            color: Color
        ) {
            val travelDist = w * 0.7f
            val currDist = progress * travelDist
            val headX = (w * 0.5f + startXOffset) + dirX * currDist
            val headY = (h * 0.5f + startYOffset) - dirY * currDist
            val tailX = headX - dirX * streakLength
            val tailY = headY + dirY * streakLength

            val alphaFade = kotlin.math.sin(progress * Math.PI.toFloat()).coerceIn(0f, 1f)

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        color.copy(alpha = 0.85f * alphaFade),
                        color.copy(alpha = 0.35f * alphaFade),
                        Color.Transparent
                    ),
                    start = Offset(headX, headY),
                    end = Offset(tailX, tailY)
                ),
                start = Offset(headX, headY),
                end = Offset(tailX, tailY),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        drawContrailStreak(
            progress = streakProgress1,
            startXOffset = -w * 0.10f,
            startYOffset = h * 0.08f,
            streakLength = if (isLaunching) 90f else 48f,
            strokeWidth = 3.5f,
            color = primaryColor
        )

        drawContrailStreak(
            progress = streakProgress2,
            startXOffset = -w * 0.02f,
            startYOffset = h * 0.12f,
            streakLength = if (isLaunching) 120f else 64f,
            strokeWidth = 4.5f,
            color = tertiaryColor
        )

        drawContrailStreak(
            progress = streakProgress3,
            startXOffset = w * 0.08f,
            startYOffset = h * 0.04f,
            streakLength = if (isLaunching) 80f else 40f,
            strokeWidth = 2.8f,
            color = primaryColor
        )
    }
}

@Composable
fun StockMaterialLogoShimmer(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showContainerHalo: Boolean = false,
    showRocketContrails: Boolean = false,
    isLaunching: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_shimmer_transition")

    val floatOffsetY = infiniteTransition.animateFloat(
        initialValue = -3.0f,
        targetValue = 3.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset_y"
    )

    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val containerSize = if (showRocketContrails) size * 1.5f else size

    Box(
        modifier = modifier.size(containerSize),
        contentAlignment = Alignment.Center
    ) {
        if (showRocketContrails) {
            StockMaterialRocketContrails(
                modifier = Modifier
                    .size(containerSize)
                    .align(Alignment.Center),
                isLaunching = isLaunching
            )
        }

        if (showContainerHalo) {
            Box(
                modifier = Modifier
                    .size(size * 1.15f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaroonAccentRose.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationY = floatOffsetY.value * density
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_swiffle_logo),
                contentDescription = "Loading",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Full-Screen M3 Expressive Morphing Shape Loading Screen on #440E03 Maroon Cloth:
 * - Uses `M3ExpressiveMorphingShapeLoader` with centered Swiffle emblem
 * - Uses `ClaudeSerif` for headline and `ClaudeSans` for subtitle
 * - Styled over the #440E03 Maroon Cloth with mild-white glassmorphism
 */
@Composable
fun StockMaterialLogoLoadingScreen(
    visible: Boolean,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
        label = "logo_loading_alpha"
    )

    if (visible || alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .alpha(alpha)
                .background(Color(0xFF440E03).copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                M3ExpressiveMorphingShapeLoader(
                    size = 116.dp,
                    showCenterEmblem = true,
                    emblemSize = 50.dp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = title,
                    fontFamily = ClaudeSerif,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaroonIvoryText,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.2).sp
                )

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        fontFamily = ClaudeSans,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaroonBlushSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

fun getDocumentClosingTitle(fileNameOrTitle: String, uri: android.net.Uri? = null): String {
    val clean = (fileNameOrTitle.ifBlank { uri?.lastPathSegment ?: "" }).trim().lowercase()
    return when {
        clean.endsWith(".pdf") -> "Closing PDF"
        clean.endsWith(".xlsx") || clean.endsWith(".xls") || clean.endsWith(".csv") -> "Closing Excel File"
        clean.endsWith(".docx") || clean.endsWith(".doc") -> "Closing Docx"
        clean.endsWith(".pptx") || clean.endsWith(".ppt") -> "Closing PowerPoint"
        clean.endsWith(".txt") -> "Closing Text File"
        clean.endsWith(".epub") -> "Closing EPUB Book"
        else -> "Closing Document"
    }
}

fun getDocumentOpeningTitle(fileNameOrTitle: String, uri: android.net.Uri? = null): String {
    val clean = (fileNameOrTitle.ifBlank { uri?.lastPathSegment ?: "" }).trim().lowercase()
    return when {
        clean.endsWith(".pdf") -> "Opening PDF"
        clean.endsWith(".xlsx") || clean.endsWith(".xls") || clean.endsWith(".csv") -> "Opening Excel File"
        clean.endsWith(".docx") || clean.endsWith(".doc") -> "Opening Docx"
        clean.endsWith(".pptx") || clean.endsWith(".ppt") -> "Opening PowerPoint"
        clean.endsWith(".txt") -> "Opening Text File"
        clean.endsWith(".epub") -> "Opening EPUB Book"
        else -> "Opening Document"
    }
}

/**
 * Centered Mild-White Glassmorphic Pill with M3 Expressive Morphing Shape Loader
 * displayed while opening a document.
 */
@Composable
fun PdfOpeningPill(
    documentTitle: String = "",
    documentUri: android.net.Uri? = null,
    isDark: Boolean = com.swiffle.app.theme.ThemeManager.isDarkThemeActive(),
    modifier: Modifier = Modifier
) {
    val titleText = remember(documentTitle, documentUri) {
        getDocumentOpeningTitle(documentTitle, documentUri)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "opening_pill_anim")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "opening_pulse_alpha"
    )

    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF240601).copy(alpha = 0.56f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 24.dp)
                .clip(pillShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4E1205).copy(alpha = 0.96f),
                            Color(0xFF380B02).copy(alpha = 0.98f)
                        )
                    )
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.42f),
                            Color(0xFFF7DAD9).copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = pillShape
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 18.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                M3ExpressiveMorphingShapeLoader(
                    size = 46.dp,
                    showCenterEmblem = true,
                    emblemSize = 22.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = titleText,
                        color = MaroonIvoryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ClaudeSerif,
                        letterSpacing = (-0.2).sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = pulseAlpha.value
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "High-resolution vector canvas",
                        color = MaroonBlushSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = ClaudeSans
                    )
                }
            }
        }
    }
}

@Composable
fun ExternalPdfLoadingPill(
    documentTitle: String = "",
    documentUri: android.net.Uri? = null,
    isDark: Boolean = com.swiffle.app.theme.ThemeManager.isDarkThemeActive(),
    modifier: Modifier = Modifier
) {
    PdfOpeningPill(documentTitle, documentUri, isDark, modifier)
}

/**
 * Centered Mild-White Glassmorphic Pill with M3 Expressive Morphing Shape Loader
 * displayed seamlessly while closing a document.
 */
@Composable
fun PdfClosingPill(
    documentTitle: String = "",
    documentUri: android.net.Uri? = null,
    isDark: Boolean = com.swiffle.app.theme.ThemeManager.isDarkThemeActive(),
    modifier: Modifier = Modifier
) {
    val titleText = remember(documentTitle, documentUri) {
        getDocumentClosingTitle(documentTitle, documentUri)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "closing_pill_anim")
    val pulseAlpha = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "closing_pulse_alpha"
    )

    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF240601).copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 24.dp)
                .clip(pillShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4E1205).copy(alpha = 0.96f),
                            Color(0xFF380B02).copy(alpha = 0.98f)
                        )
                    )
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.42f),
                            Color(0xFFF7DAD9).copy(alpha = 0.20f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = pillShape
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 18.dp, end = 24.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                M3ExpressiveMorphingShapeLoader(
                    size = 46.dp,
                    showCenterEmblem = true,
                    emblemSize = 22.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = titleText,
                        color = MaroonIvoryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ClaudeSerif,
                        letterSpacing = (-0.2).sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = pulseAlpha.value
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Saving reading progress",
                        color = MaroonBlushSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = ClaudeSans
                    )
                }
            }
        }
    }
}
