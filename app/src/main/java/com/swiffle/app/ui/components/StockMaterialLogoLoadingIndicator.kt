package com.swiffle.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
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
import com.swiffle.app.theme.GoogleSans

/**
 * Authentic Native Material 3 Pro SVG Logo Loading Indicator:
 * - Uses the official vector origami SVG logo (ic_swiffle_logo / ic_swiffle_icon)
 * - Continuous high-end diagonal specular shimmer beam traversing the origami folds
 * - Gentle pulsing breathing scale and subtle elevation glow
 * - Smooth upward diagonal fade-away exit transition when loading completes
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Angle ~45° downward-left from center (exhaust trail for a rocket pointed ~45° upward-right)
        // streak trajectories: Start near center, move diagonally down-left
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
            val headY = (h * 0.5f + startYOffset) - dirY * currDist // invert Y for canvas coords
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

        // Streak 1 (Center-left stream)
        drawContrailStreak(
            progress = streakProgress1,
            startXOffset = -w * 0.10f,
            startYOffset = h * 0.08f,
            streakLength = if (isLaunching) 90f else 48f,
            strokeWidth = 3.5f,
            color = primaryColor
        )

        // Streak 2 (Center core stream)
        drawContrailStreak(
            progress = streakProgress2,
            startXOffset = -w * 0.02f,
            startYOffset = h * 0.12f,
            streakLength = if (isLaunching) 120f else 64f,
            strokeWidth = 4.5f,
            color = tertiaryColor
        )

        // Streak 3 (Right stream)
        drawContrailStreak(
            progress = streakProgress3,
            startXOffset = w * 0.08f,
            startYOffset = h * 0.04f,
            streakLength = if (isLaunching) 80f else 40f,
            strokeWidth = 2.8f,
            color = primaryColor
        )

        // Streak 4 (Outer subtle stream)
        val outerProgress = ((streakProgress1 + 0.45f) % 1f)
        drawContrailStreak(
            progress = outerProgress,
            startXOffset = -w * 0.18f,
            startYOffset = h * 0.02f,
            streakLength = if (isLaunching) 70f else 36f,
            strokeWidth = 2.2f,
            color = tertiaryColor
        )
    }
}

/**
 * Authentic Native Material 3 Pro SVG Logo Loading Indicator:
 * - Uses the official vector origami SVG logo (ic_swiffle_logo / ic_swiffle_icon)
 * - Aerodynamic zero-gravity floating with dual harmonic bobbing & micro pitch tilt
 * - Native Canvas rocket launch speed contrail lines
 * - Continuous high-end diagonal specular shimmer beam traversing origami folds
 * - Explosive hyper-drive diagonal rocket launch take-off on exit
 */
@Composable
fun StockMaterialLogoShimmer(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showContainerHalo: Boolean = false,
    showRocketContrails: Boolean = false,
    isLaunching: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_shimmer_transition")

    // Aerodynamic Harmonic Float (Y bobbing) - state read inside graphicsLayer directly without recomposition
    val floatOffsetY = infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset_y"
    )

    // Aerodynamic Drift (X sway)
    val driftOffsetX = infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift_offset_x"
    )

    // Aerodynamic Pitch Tilt (Nose pitches slightly with buoyancy)
    val pitchAngle = infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pitch_angle"
    )

    // Breathing pulse
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
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
        // Rocket Contrail Speed Lines streaming beneath
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
                                Color(0xFFFFB74D).copy(alpha = 0.22f),
                                Color(0xFFE91E63).copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // Floating Aerodynamic SVG Logo - RenderThread direct execution, zero jank
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    translationX = driftOffsetX.value * density
                    translationY = floatOffsetY.value * density
                    rotationZ = pitchAngle.value
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
 * Fullscreen Native Material 3 Pro Loading Screen with:
 * - Centered shimmering vector origami SVG logo
 * - Dynamic rocket floating & animated speed streak contrails
 * - Modern typography labels (compact 15sp SemiBold)
 * - Seamless diagonal upward rocket launch take-off exit transition:
 *   Alpha decays while blasting diagonally (X += 130dp, Y -= 190dp)
 */
/**
 * Immersive Fullscreen Backdrop-Blurred Loading Screen:
 * - Replaces any awkward floating square boxes with a seamless, full-screen frosted glass overlay
 * - Authentic backdrop blur effect over underlying auth and onboarding flow
 * - Radiant origami vector logo with ambient breathing aura glow
 * - High-precision indeterminate progress indicator
 * - Executive typography for title and descriptive sync message
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
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "logo_loading_alpha"
    )

    if (visible || alpha > 0.01f) {
        val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
        val frostedScrim = if (isDark) {
            Color(0xFF000000).copy(alpha = 0.78f)
        } else {
            Color(0xFFFFFFFF).copy(alpha = 0.82f)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .alpha(alpha)
                .background(frostedScrim),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp)
            ) {
                // Floating Aerodynamic Logo with Ambient Starburst Aura Glow
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Soft ambient gradient backplate glow
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF0078D4).copy(alpha = if (isDark) 0.35f else 0.18f),
                                        Color(0xFF9B51E0).copy(alpha = if (isDark) 0.20f else 0.08f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    StockMaterialLogoShimmer(
                        size = 72.dp,
                        showContainerHalo = false,
                        showRocketContrails = false,
                        isLaunching = false
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Fluent Cobalt Blue Progress Spinner
                StockMaterialLoadingIndicator(
                    size = 30.dp,
                    strokeWidth = 3.dp,
                    useGoogleColors = false,
                    tint = Color(0xFF0078D4)
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Title
                Text(
                    text = title,
                    fontFamily = GoogleSans,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.2.sp
                )

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        fontFamily = GoogleSans,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Floating Material 3 Rounded Pill for Third-Party PDF Launches:
 * Displays over the transparent background of the caller application
 * showing "Opening Document" alongside our official SVG Origami logo.
 */
/**
 * Resolves human-friendly display titles based on the file extension.
 * Handles PDF, Excel (.xlsx, .xls, .csv), Word Docx (.docx, .doc), PowerPoint, Text, and generic documents.
 */
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
 * Centered rounded-corner pill displayed while opening a document.
 * Adapts to user preference (Light and Dark themes) with crisp M3 Expressive aesthetics.
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

    val supportsBlur = remember { android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S }
    val scrimColor = if (isDark) {
        if (supportsBlur) Color.Black.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.58f)
    } else {
        if (supportsBlur) Color(0xFF0F172A).copy(alpha = 0.15f) else Color(0xFF0F172A).copy(alpha = 0.30f)
    }

    val surfaceColor = if (isDark) Color(0xFF1E1E1E).copy(alpha = 0.95f) else Color(0xFFFFFFFF).copy(alpha = 0.95f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0xFF0F172A).copy(alpha = 0.08f)
    val textPrimaryColor = if (isDark) Color.White else Color(0xFF0F172A)
    val textMutedColor = if (isDark) Color.White.copy(alpha = 0.60f) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = surfaceColor,
            shadowElevation = 14.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 18.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StockMaterialLogoShimmer(
                    size = 36.dp,
                    showContainerHalo = true,
                    showRocketContrails = false
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = titleText,
                        color = textPrimaryColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSans,
                        letterSpacing = (-0.2).sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = pulseAlpha.value
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "High-Resolution Display",
                        color = textMutedColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = GoogleSans
                    )
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for ExternalPdfLoadingPill.
 */
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
 * Centered rounded-corner pill displayed seamlessly while closing a document.
 * Adapts to user preference (Light and Dark themes) with crisp M3 Expressive aesthetics.
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

    val supportsBlur = remember { android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S }
    val scrimColor = if (isDark) {
        if (supportsBlur) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.55f)
    } else {
        if (supportsBlur) Color(0xFF0F172A).copy(alpha = 0.15f) else Color(0xFF0F172A).copy(alpha = 0.30f)
    }

    val surfaceColor = if (isDark) Color(0xFF1E1E1E).copy(alpha = 0.95f) else Color(0xFFFFFFFF).copy(alpha = 0.95f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else Color(0xFF0F172A).copy(alpha = 0.08f)
    val textPrimaryColor = if (isDark) Color.White else Color(0xFF0F172A)
    val textMutedColor = if (isDark) Color.White.copy(alpha = 0.60f) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = surfaceColor,
            shadowElevation = 14.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 18.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StockMaterialLogoShimmer(
                    size = 36.dp,
                    showContainerHalo = true,
                    showRocketContrails = false
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = titleText,
                        color = textPrimaryColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSans,
                        letterSpacing = (-0.2).sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = pulseAlpha.value
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Saving Reading Progress",
                        color = textMutedColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = GoogleSans
                    )
                }
            }
        }
    }
}
