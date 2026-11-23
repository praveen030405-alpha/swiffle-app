package com.swiffle.app.ui.splash

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieCompositionFactory
import com.swiffle.app.R
import com.swiffle.app.theme.ClaudeSans
import com.swiffle.app.theme.ClaudeSerif
import com.swiffle.app.ui.components.M3ExpressiveMorphingShapeLoader
import com.swiffle.app.ui.components.MaroonIvoryText

/**
 * Splash Screen Resource Disposer & Controller:
 * Immediately clears heavy Lottie composition caches, unbinds any lingering
 * CameraX `ProcessCameraProvider` hardware sessions, and halts splash loader
 * animations upon completion of the background fade-to-black sequence.
 */
object SplashScreenController {
    @Volatile
    var isResourcesDisposed: Boolean = false
        private set

    fun disposeHeavyLottieAndCameraResources(context: Context) {
        if (isResourcesDisposed) return
        isResourcesDisposed = true

        val appContext = context.applicationContext
        try {
            // 1. Clear heavy in-memory Lottie composition and asset caches
            LottieCompositionFactory.clearCache(appContext)
        } catch (_: Throwable) {}

        try {
            // 2. Immediately release/unbind any CameraX ProcessCameraProvider hardware resources
            val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get()?.unbindAll()
            }
        } catch (_: Throwable) {}
    }

    fun resetForNewLaunch() {
        isResourcesDisposed = false
    }
}

/**
 * Custom Swiffle Splash Screen controlled by `MainActivity`'s high-precision
 * `AnimatedVisibility` multi-stage transition state machine:
 * - Renders at 100% scale and opacity on Frame 0 over `#440E03` Maroon Cloth.
 * - Executes `SplashScreenController.disposeHeavyLottieAndCameraResources` immediately
 *   when `isFadeToBlackCompleted` transitions to `true` (and on composable disposal).
 */
@Composable
fun SplashScreen(
    fadeProgress: Float = 0f,
    isFadeToBlackCompleted: Boolean = false,
    onSplashComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val loaderAlpha = (1f - (fadeProgress - 0.65f) / 0.35f).coerceIn(0f, 1f)
    val shouldPauseLoader = isFadeToBlackCompleted || fadeProgress >= 0.99f

    // Trigger immediate disposal of heavy Lottie and Camera resources upon fade-to-black completion
    LaunchedEffect(isFadeToBlackCompleted) {
        if (isFadeToBlackCompleted) {
            SplashScreenController.disposeHeavyLottieAndCameraResources(context)
            onSplashComplete()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            SplashScreenController.disposeHeavyLottieAndCameraResources(context)
        }
    }

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
            // Pure Centered Swiffle Logo (Anchored coordinates with Get Started screen)
            Image(
                painter = painterResource(id = R.drawable.ic_swiffle_logo),
                contentDescription = "Swiffle Logo",
                modifier = Modifier.size(112.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Editorial Brand Wordmark in Claude Serif
            Text(
                text = "Swiffle",
                color = MaroonIvoryText,
                fontSize = 42.sp,
                lineHeight = 50.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ClaudeSerif
            )

            // Reserved layout slot for Subtitle so Column height matches Get Started screen pixel-for-pixel
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Speed, clarity, and intelligence in every page.",
                fontFamily = ClaudeSans,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Color.Transparent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // M3 Expressive Morphing Shape Loader at Screen Bottom (halted & disposed once fade-to-black completes)
        if (loaderAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .graphicsLayer {
                        alpha = loaderAlpha
                    }
            ) {
                M3ExpressiveMorphingShapeLoader(
                    size = 40.dp,
                    showCenterEmblem = false,
                    isPaused = shouldPauseLoader
                )
            }
        }
    }
}
