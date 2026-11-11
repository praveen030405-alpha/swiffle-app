package com.swiffle.app.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.R
import com.swiffle.app.theme.GoogleSans
import com.swiffle.app.theme.SwiffleNavyPrimary
import kotlinx.coroutines.delay

/**
 * Material 3 Expressive Cinematic Splash Screen:
 * - Fluid dual-spring entrance choreography (kinetic tactile overshoot + soft settling)
 * - Luminous radiant radial halo blooming behind the emblem
 * - Ascending Google Sans wordmark with emphasized deceleration
 * - Seamless zero-jerk handoff to the Get Started Sanctuary Gateway
 */
@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    var isTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Trigger fluid M3 entrance immediately on first frame
        isTriggered = true
        // Allow user to experience the premium kinetic entrance before smooth handoff
        delay(1250)
        onSplashComplete()
    }

    // Logo kinetic tactile spring (0.65f -> 1.0f)
    val logoScale by animateFloatAsState(
        targetValue = if (isTriggered) 1f else 0.65f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "m3_splash_logo_scale"
    )

    // Logo smooth opacity fade
    val logoAlpha by animateFloatAsState(
        targetValue = if (isTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "m3_splash_logo_alpha"
    )

    // Luminous halo bloom behind emblem
    val haloScale by animateFloatAsState(
        targetValue = if (isTriggered) 1.25f else 0.40f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "m3_splash_halo_scale"
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (isTriggered) 0.45f else 0f,
        animationSpec = tween(durationMillis = 650, easing = LinearOutSlowInEasing),
        label = "m3_splash_halo_alpha"
    )

    // Wordmark ascending slide + fade
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (isTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 480, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "m3_splash_text_alpha"
    )
    val wordmarkTranslationY by animateFloatAsState(
        targetValue = if (isTriggered) 0f else 28f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "m3_splash_text_y"
    )

    // Subtle pulsing micro-loader pill at bottom
    val pulseTransition = rememberInfiniteTransition(label = "splash_loader_pulse")
    val loaderAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loader_alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // Emblem Container with Radiant Aurora Halo
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                // Radiant Halo Glow
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .graphicsLayer {
                            scaleX = haloScale
                            scaleY = haloScale
                            alpha = haloAlpha
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x553B82F6),
                                    Color(0x358B5CF6),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Pure Swiffle Emblem
                Image(
                    painter = painterResource(id = R.drawable.ic_swiffle_logo),
                    contentDescription = "Swiffle Logo",
                    modifier = Modifier
                        .size(130.dp)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            alpha = logoAlpha
                        }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ascending Brand Wordmark
            Text(
                text = "Swiffle",
                color = Color(0xFF0F172A),
                fontSize = 35.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSans,
                modifier = Modifier.graphicsLayer {
                    alpha = wordmarkAlpha
                    translationY = wordmarkTranslationY
                }
            )

            // Reserved layout slot for Subtitle so Column height matches Screen 1 pixel-for-pixel!
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Speed, clarity, and intelligence in every page.",
                fontFamily = GoogleSans,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                color = Color.Transparent, // invisible on splash, guarantees IDENTICAL layout center coordinates
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Minimal Material 3 Indeterminate Loading Pill at Screen Bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
                .graphicsLayer { alpha = wordmarkAlpha }
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SwiffleNavyPrimary.copy(alpha = loaderAlpha))
            )
        }
    }
}
