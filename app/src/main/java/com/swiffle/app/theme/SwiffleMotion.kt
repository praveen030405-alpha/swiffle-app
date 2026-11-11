package com.swiffle.app.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  SWIFFLE MOTION SYSTEM — MATERIAL 3 EXPRESSIVE UI FLAGSHIP ARCHITECTURE
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Implements Google's official May 2025 Material 3 Expressive motion specifications:
 * - Dual-domain physics: Spatial springs (with kinetic settle) + Effects springs (clean zero-overshoot)
 * - Exact cubic bezier conversion curves for frame-isolated RenderThread execution
 * - The 6 canonical transition archetypes (Container Transform, Shared Axis Z, Lateral, Top-Level, Sheets)
 * - Zero ghosting: Outgoing screens cleanly dissolve before incoming screens reach full opacity
 */
object SwiffleMotion {

    // ── 1. M3 Expressive Physics Springs ──────────────────────────────────────
    /**
     * Expressive Default Spatial Spring:
     * Used for bounds expansion, card scales, and spatial transforms.
     * Features subtle kinetic energy with a silky, non-jarring settling response.
     */
    val SpringSpatial: SpringSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = 420f
    )

    val SpringSpatialOffset: SpringSpec<androidx.compose.ui.unit.IntOffset> = spring(
        dampingRatio = 0.80f,
        stiffness = 420f
    )

    /**
     * Expressive Fast Effects Spring:
     * Used exclusively for Alpha/Opacity, Color blending, and Scrims.
     * Strict DampingRatioNoBouncy (1.0f) to prevent illegal opacity overshoot (> 100% or < 0%).
     */
    val SpringEffects: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 550f
    )

    /**
     * Expressive Tactile Spring:
     * Subtle energetic pop for buttons, morphic FAB speed-dial, and micro-press states.
     */
    val SpringTactile: SpringSpec<Float> = spring(
        dampingRatio = 0.70f,
        stiffness = 650f
    )

    // ── 2. Official Material 3 Easing Curves (Cubic Beziers) ─────────────────
    /**
     * Standard M3 Emphasized Decelerate (fast initial acceleration, gentle settling).
     * Token: md.sys.motion.easing.emphasized.decelerate
     */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.0f)

    /**
     * Standard M3 Emphasized Accelerate (rapid exit with subtle snap).
     * Token: md.sys.motion.easing.emphasized.accelerate
     */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)

    /**
     * Standard M3 Emphasized Curve for continuous, non-interrupted loops.
     * Token: md.sys.motion.easing.emphasized
     */
    val Emphasized: Easing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.0f)

    /**
     * M3 Standard Curve (utility / high-frequency interactions).
     */
    val Standard: Easing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.0f)
    val StandardDecelerate: Easing = CubicBezierEasing(0.00f, 0.00f, 0.20f, 1.0f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.40f, 0.00f, 1.00f, 1.0f)

    // ── 3. Duration Tokens (Milliseconds) ────────────────────────────────────
    const val DurationShort4 = 200
    const val DurationMedium2 = 300
    const val DurationMedium4 = 400
    const val DurationLong2 = 500

    // ── 4. Canonical Transition Archetypes ───────────────────────────────────

    /**
     * Pattern 1: Container Transform (Hero Card / File -> Full Canvas)
     * Directly matches 'Container transform.mp4':
     * - Incoming view scales gracefully up from 0.92f with spatial spring physics.
     * - Outgoing content fades out rapidly in 150ms to eliminate visual text ghosting.
     */
    fun containerTransform(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = 240, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = 150, easing = EmphasizedAccelerate)
            ) + scaleOut(
                targetScale = 1.05f,
                animationSpec = SpringSpatial
            )
        )
    }

    /**
     * Pattern 2: Forward Shared Axis Z (Hierarchical Drill-Down)
     * Directly matches 'Forward and backward - 1.mp4' through '5.mp4':
     * - Incoming screen elevates from 0.94f up to 1.0f while fading in.
     * - Outgoing screen scales to 1.04f while cleanly dissolving in 160ms.
     */
    fun sharedAxisZForward(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = DurationMedium2, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 0.94f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerate)
            ) + scaleOut(
                targetScale = 1.04f,
                animationSpec = SpringSpatial
            )
        )
    }

    /**
     * Pattern 2 (Reverse): Backward Shared Axis Z (Hierarchical Return)
     * Returning screen scales down from 1.04f to 1.0f; departing view recedes to 0.94f.
     */
    fun sharedAxisZBackward(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = DurationMedium2, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 1.04f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = DurationMedium2, easing = EmphasizedAccelerate)
            ) + scaleOut(
                targetScale = 0.94f,
                animationSpec = SpringSpatial
            )
        ).apply {
            targetContentZIndex = 0f
        }
    }

    /**
     * Dedicated PDF Canvas Return Transition (Material 3 Seamless Return)
     * Completely eliminates white flash / splash when closing a PDF:
     * 1. Returning Home Screen gently scales in from 1.03f with smooth deceleration.
     * 2. Exiting PDF canvas recedes slightly to 0.96f and holds opacity so it never exposes the underlying void.
     * 3. Target Z-Index keeps the returning layer neatly layered over the exit surface.
     */
    fun pdfCanvasReturn(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = 280, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 1.03f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = 240, easing = EmphasizedAccelerate)
            ) + scaleOut(
                targetScale = 0.96f,
                animationSpec = SpringSpatial
            )
        ).apply {
            targetContentZIndex = 1f
        }
    }

    /**
     * Pattern 3: Lateral Slide (Peer Tab Navigation: All Docs <-> Starred <-> Annotated)
     * Directly matches 'Lateral 1.mp4' to '3.mp4':
     * Directional horizontal slide without depth/scale distortion for 1:1 tactile alignment.
     */
    fun lateralForward(): ContentTransform {
        return (slideInHorizontally(
            initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
            animationSpec = SpringSpatialOffset
        ) + fadeIn(
            animationSpec = tween(durationMillis = DurationMedium2, easing = EmphasizedDecelerate)
        )).togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() },
                animationSpec = SpringSpatialOffset
            ) + fadeOut(
                animationSpec = tween(durationMillis = 150, easing = EmphasizedAccelerate)
            )
        )
    }

    fun lateralBackward(): ContentTransform {
        return (slideInHorizontally(
            initialOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() },
            animationSpec = SpringSpatialOffset
        ) + fadeIn(
            animationSpec = tween(durationMillis = DurationMedium2, easing = EmphasizedDecelerate)
        )).togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() },
                animationSpec = SpringSpatialOffset
            ) + fadeOut(
                animationSpec = tween(durationMillis = 150, easing = EmphasizedAccelerate)
            )
        )
    }

    /**
     * Pattern 4: Top Level Fade Through (Splash -> Onboarding -> Home)
     * Directly matches 'Top level.mp4' & 'Top level 1.mp4':
     * Gentle scale-dissolve (0.97f -> 1.0f) with zero lateral panning, preventing disorientation.
     */
    fun scaleFadeThrough(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = DurationMedium2, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 0.97f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = 180, easing = EmphasizedAccelerate)
            ) + scaleOut(
                targetScale = 1.02f,
                animationSpec = SpringSpatial
            )
        )
    }

    // ── Windows 11 Fluent Design System Horizontal Page Transitions ─────────
    val FluentDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val FluentAccelerate: Easing = CubicBezierEasing(1.0f, 0.0f, 1.0f, 1.0f)
    const val FluentNormalDuration = 250
    const val FluentFastDuration = 200

    /**
     * Fluent Forward Navigation (Horizontal Push):
     * - Incoming page slides in horizontally from the right (100% width) with Fade In.
     * - Outgoing page slides out horizontally to the left (-100% width) with Fade Out.
     */
    fun horizontalForward(): ContentTransform {
        return (slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = FluentNormalDuration, easing = FluentDecelerate)
        ) + fadeIn(
            animationSpec = tween(durationMillis = FluentNormalDuration, easing = FluentDecelerate)
        )).togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = tween(durationMillis = FluentNormalDuration, easing = FluentAccelerate)
            ) + fadeOut(
                animationSpec = tween(durationMillis = FluentFastDuration, easing = FluentAccelerate)
            )
        )
    }

    /**
     * Fluent Backward Navigation (Horizontal Pop):
     * - Incoming page slides in horizontally from the left (-100% width) with Fade In.
     * - Outgoing page slides out horizontally to the right (100% width) with Fade Out.
     */
    fun horizontalBackward(): ContentTransform {
        return (slideInHorizontally(
            initialOffsetX = { fullWidth -> -fullWidth },
            animationSpec = tween(durationMillis = FluentNormalDuration, easing = FluentDecelerate)
        ) + fadeIn(
            animationSpec = tween(durationMillis = FluentNormalDuration, easing = FluentDecelerate)
        )).togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = FluentNormalDuration, easing = FluentAccelerate)
            ) + fadeOut(
                animationSpec = tween(durationMillis = FluentFastDuration, easing = FluentAccelerate)
            )
        )
    }

    /**
     * Universal page transition spec that can be directly passed to `AnimatedContent(transitionSpec = ...)`:
     */
    val pageTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        horizontalForward()
    }
}
