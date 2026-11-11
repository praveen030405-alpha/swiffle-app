package com.swiffle.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

// ─────────────────────────────────────────────────────────────────────────────
//  Origami Lottie Animation Catalogue
//  All animation files live in:  assets/lottie/*.json
// ─────────────────────────────────────────────────────────────────────────────

/** Canonical animation names. Use these everywhere instead of raw strings. */
object OrigamiAnim {
    const val LOADING_DOTS    = "lottie/loading_dots.json"    // 3 bouncing red dots
    const val SUCCESS_CHECK   = "lottie/success_check.json"   // green circle + check
    const val EMPTY_FOLDER    = "lottie/empty_folder.json"    // rocking empty folder
    const val PDF_CONVERTING  = "lottie/pdf_converting.json"  // floating page + arrow
    const val CAMERA_SCAN     = "lottie/camera_scan.json"     // scan line sweep
    const val EXCEL_BUILDING  = "lottie/excel_building.json"  // rows building up
}

/** Swiffle branded alias */
typealias SwiffleAnim = OrigamiAnim

@Composable
fun SwiffleLottie(
    anim: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    loop: Boolean = true,
    speed: Float = 1f,
    isPlaying: Boolean = true,
) = OrigamiLottie(anim, modifier, size, loop, speed, isPlaying)

@Composable
fun SwiffleLottieOneShot(
    anim: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    speed: Float = 1f,
) = OrigamiLottieOneShot(anim, modifier, size, speed)

/**
 * Drop-in Lottie player that loops indefinitely by default.
 *
 * Usage:
 * ```
 * OrigamiLottie(anim = OrigamiAnim.LOADING_DOTS, size = 120.dp)
 * ```
 */
@Composable
fun OrigamiLottie(
    anim: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    loop: Boolean = true,
    speed: Float = 1f,
    isPlaying: Boolean = true,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(anim)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        speed = speed,
        isPlaying = isPlaying,
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * One-shot animation that plays once and stays on the last frame.
 * Perfect for success/error feedback.
 */
@Composable
fun OrigamiLottieOneShot(
    anim: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    speed: Float = 1f,
) {
    OrigamiLottie(
        anim = anim,
        modifier = modifier,
        size = size,
        loop = false,
        speed = speed,
        isPlaying = true,
    )
}
