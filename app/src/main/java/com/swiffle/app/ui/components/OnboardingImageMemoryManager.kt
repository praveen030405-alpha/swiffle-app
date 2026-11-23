package com.swiffle.app.ui.components

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.LruCache
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import coil.imageLoader
import com.airbnb.lottie.LottieCompositionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  CUSTOM ONBOARDING IMAGE MEMORY MANAGER (`OnboardingImageMemoryManager`)
 * ══════════════════════════════════════════════════════════════════════════════
 * Specifically clears and trims bitmap caches when moving between onboarding
 * screens (`GATEWAY` <-> `CREDENTIALS` <-> `FEATURE_TOUR` <-> `WORKSPACE_READY`)
 * and vertical onboarding tour pages (`01` <-> `02` <-> `03`) so frame-time stays
 * locked at 60fps with zero GC stalls or bitmap memory pressure during transitions.
 */
object OnboardingImageMemoryManager {
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Scoped LRU Bitmap Cache (capped at 1/16th of heap for onboarding visual buffers)
    private val maxCacheKb = ((Runtime.getRuntime().maxMemory() / 1024).toInt() / 16).coerceAtLeast(1024)

    private val stageBitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    @Volatile
    private var activeStageIndex: Int = 0

    @Volatile
    private var activeTourPageIndex: Int = 0

    fun putStageBitmap(stageIndex: Int, stepIndex: Int, tag: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val key = buildKey(stageIndex, stepIndex, tag)
        synchronized(stageBitmapCache) {
            stageBitmapCache.put(key, bitmap)
        }
    }

    fun getStageBitmap(stageIndex: Int, stepIndex: Int, tag: String): Bitmap? {
        val key = buildKey(stageIndex, stepIndex, tag)
        val bmp = synchronized(stageBitmapCache) {
            stageBitmapCache.get(key)
        }
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    private fun buildKey(stageIndex: Int, stepIndex: Int, tag: String): String {
        return "s_${stageIndex}_p_${stepIndex}_$tag"
    }

    /**
     * Invoked specifically when moving between major Onboarding Screens
     * (Get Started -> Sign In / Create Account -> Feature Tour -> Loading/Tick).
     * Evicts all bitmaps belonging to previous screens off the main thread so
     * the Shared-Axis Z transition runs at a locked 60fps.
     */
    fun onOnboardingStageTransition(
        context: Context,
        previousStageIndex: Int,
        newStageIndex: Int
    ) {
        activeStageIndex = newStageIndex
        val appContext = context.applicationContext

        cleanupScope.launch {
            try {
                // 1. Evict any cached bitmaps from the departed onboarding stage
                val prefixToKeep = "s_${newStageIndex}_"
                synchronized(stageBitmapCache) {
                    val snapshot = stageBitmapCache.snapshot()
                    for (key in snapshot.keys) {
                        if (!key.startsWith(prefixToKeep)) {
                            stageBitmapCache.remove(key)
                        }
                    }
                }

                // 2. Clear Coil's in-memory bitmap cache so inactive screen bitmaps are freed immediately
                appContext.imageLoader.memoryCache?.clear()

                // 3. Evict non-essential PDF thumbnail bitmaps while inside onboarding
                if (previousStageIndex != newStageIndex) {
                    PdfThumbnailManager.clearMemoryCache()
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Invoked specifically when the user swipes or taps "Next" between the vertical
     * Onboarding Feature Tour pages (`VerticalPager` settle events).
     */
    fun onFeatureTourPageSettled(
        context: Context,
        settledPage: Int
    ) {
        activeTourPageIndex = settledPage
        val appContext = context.applicationContext

        cleanupScope.launch {
            try {
                val activePrefix = "s_${activeStageIndex}_p_${settledPage}_"
                synchronized(stageBitmapCache) {
                    val snapshot = stageBitmapCache.snapshot()
                    for (key in snapshot.keys) {
                        if (key.startsWith("s_${activeStageIndex}_p_") && !key.startsWith(activePrefix)) {
                            stageBitmapCache.remove(key)
                        }
                    }
                }

                appContext.imageLoader.memoryCache?.clear()
            } catch (_: Throwable) {}
        }
    }

    fun clearAllOnboardingBitmapCaches(context: Context) {
        val appContext = context.applicationContext
        cleanupScope.launch {
            try {
                synchronized(stageBitmapCache) {
                    stageBitmapCache.evictAll()
                }
                appContext.imageLoader.memoryCache?.clear()
                LottieCompositionFactory.clearCache(appContext)
            } catch (_: Throwable) {}
        }
    }

    fun onTrimMemory(context: Context, level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        ) {
            clearAllOnboardingBitmapCaches(context)
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  TACTILE ONBOARDING HAPTIC FEEDBACK CONTROLLER (`OnboardingHaptics`)
 * ══════════════════════════════════════════════════════════════════════════════
 * Delivers calibrated hardware haptic vibration waveforms and View haptic ticks
 * across onboarding stage transitions, vertical tour page steps, and the final
 * Loading-to-Success-Tick completion lock-in.
 */
object OnboardingHaptics {
    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Crisp tactile confirmation when transitioning between major onboarding stages
     * (Get Started <-> Sign In / Create Account <-> Feature Tour <-> Loading/Tick).
     */
    fun performStageTransitionHaptic(context: Context, view: View? = null) {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } catch (_: Throwable) {}

        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18L, 110))
            }
        } catch (_: Throwable) {}
    }

    /**
     * Subtle velvet micro-tick when advancing or swiping vertically between the
     * 3 Feature Tour pages or toggling the segmented Sign In / Create Account slider.
     */
    fun performPageStepHaptic(context: Context, view: View? = null) {
        try {
            view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Throwable) {}

        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12L, 80))
            }
        } catch (_: Throwable) {}
    }

    /**
     * Signature two-pulse celebratory haptic confirmation when the M3 Expressive
     * loader coalesces into the Success Tick (`✓`) at the end of onboarding.
     */
    fun performSuccessTickHaptic(context: Context, view: View? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (_: Throwable) {}

        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 16L, 65L, 24L),
                        intArrayOf(0, 120, 0, 180),
                        -1
                    )
                )
            }
        } catch (_: Throwable) {}
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  DARK VOID AMBIENT LIGHT PULSE (`DarkVoidAmbientLightPulse`)
 * ══════════════════════════════════════════════════════════════════════════════
 * Renders a slow-moving, non-distracting radial gradient ambient light pulse
 * over the `#000000` Dark Void background. Multi-stop falloff and subtle
 * stage/swipe-coupled depth drift give the onboarding transitions studio depth
 * while preserving pure OLED black framing and zero visual clutter.
 */
@Composable
fun DarkVoidAmbientLightPulse(
    activeStageIndex: Int,
    activeSubStepIndex: Int = 0,
    transitionDepthProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dark_void_ambient_pulse")

    // Slow, calm breathing luminance pulse (0f <-> 1f over 5.6s)
    val breathPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_breath_pulse"
    )

    // Slow continuous orbital drift (16s cycle) for subtle organic movement
    val driftAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_drift_angle"
    )

    // Smooth vertical anchor interpolation across stages and feature-tour swipe steps
    val targetDepthFraction = ((activeStageIndex * 0.055f) + (transitionDepthProgress * 0.14f)).coerceIn(0f, 0.34f)
    val smoothedDepthFraction by animateFloatAsState(
        targetValue = targetDepthFraction,
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 110f),
        label = "ambient_depth_fraction"
    )

    // Gentle radial swell whenever transitioning between onboarding stages or vertical tour pages
    val transitionSwell = remember { Animatable(0f) }
    LaunchedEffect(activeStageIndex, activeSubStepIndex) {
        transitionSwell.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
        transitionSwell.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 1150, easing = FastOutSlowInEasing)
        )
    }

    val swellValue = transitionSwell.value

    Canvas(modifier = modifier.fillMaxSize()) {
        val maxDim = size.maxDimension.coerceAtLeast(1f)

        // 1. Primary Slow-Moving Atmospheric Radial Glow (Multi-stop smooth falloff -> pure #000000 edge)
        val primaryAlpha = (0.065f + 0.032f * breathPulse + 0.052f * swellValue).coerceIn(0f, 0.155f)
        val primaryRadius = maxDim * (0.76f + 0.06f * breathPulse + 0.09f * swellValue)
        val primaryCenter = Offset(
            x = size.width * (0.50f + 0.055f * sin(driftAngle)),
            y = size.height * (0.24f + smoothedDepthFraction + 0.035f * cos(driftAngle))
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFF6B180B).copy(alpha = primaryAlpha),
                    0.36f to Color(0xFF3E0C05).copy(alpha = primaryAlpha * 0.56f),
                    0.72f to Color(0xFF1A0402).copy(alpha = primaryAlpha * 0.20f),
                    1.00f to Color.Transparent
                ),
                center = primaryCenter,
                radius = primaryRadius
            )
        )

        // 2. Subtle Specular Depth Core (whisper-soft rose-ivory plane offset for layered depth)
        val specularAlpha = (0.018f + 0.012f * breathPulse + 0.022f * swellValue).coerceIn(0f, 0.055f)
        val specularRadius = maxDim * (0.42f + 0.04f * breathPulse + 0.05f * swellValue)
        val specularCenter = Offset(
            x = size.width * (0.50f - 0.04f * sin(driftAngle * 0.5f)),
            y = size.height * (0.22f + smoothedDepthFraction * 0.85f + 0.025f * sin(driftAngle))
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFFF7DAD9).copy(alpha = specularAlpha),
                    0.45f to Color(0xFF7D2415).copy(alpha = specularAlpha * 0.45f),
                    1.00f to Color.Transparent
                ),
                center = specularCenter,
                radius = specularRadius
            )
        )
    }
}

/**
 * Composable lifecycle hook that automatically clears inactive bitmap caches
 * and triggers tactile haptic confirmation whenever the user moves between
 * Onboarding stages.
 */
@Composable
fun OnboardingStageMemoryEffect(currentStageIndex: Int) {
    val context = LocalContext.current
    val view = LocalView.current
    val previousStageRef = remember { intArrayOf(currentStageIndex) }

    LaunchedEffect(currentStageIndex) {
        val prev = previousStageRef[0]
        if (prev != currentStageIndex) {
            OnboardingHaptics.performStageTransitionHaptic(context, view)
            OnboardingImageMemoryManager.onOnboardingStageTransition(
                context = context,
                previousStageIndex = prev,
                newStageIndex = currentStageIndex
            )
            previousStageRef[0] = currentStageIndex
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            OnboardingImageMemoryManager.clearAllOnboardingBitmapCaches(context)
        }
    }
}

/**
 * Composable hook for the `VerticalPager` inside the Onboarding Feature Tour.
 * Triggers tactile haptic confirmation when the vertical page changes and
 * evicts off-screen page bitmaps once the transition settles.
 */
@Composable
fun OnboardingPagerMemoryEffect(pagerState: PagerState) {
    val context = LocalContext.current
    val view = LocalView.current
    val previousPageRef = remember { intArrayOf(pagerState.currentPage) }

    // Tactile haptic confirmation as soon as the target/current vertical page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (previousPageRef[0] != page) {
                    previousPageRef[0] = page
                    OnboardingHaptics.performPageStepHaptic(context, view)
                }
            }
    }

    // Evict off-screen bitmap caches once the vertical pager animation settles
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                OnboardingImageMemoryManager.onFeatureTourPageSettled(
                    context = context,
                    settledPage = settledPage
                )
            }
    }
}
