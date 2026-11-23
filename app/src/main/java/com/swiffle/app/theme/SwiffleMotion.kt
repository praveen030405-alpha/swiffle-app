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
import androidx.compose.ui.unit.IntOffset

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  SWIFFLE MOTION SYSTEM — MATERIAL 3 EXPRESSIVE UI (v1.1 GUIDELINES)
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Implements the Design System v1.1 Section 2.7 Motion specifications:
 * - Spatial Fast (stiffness 800, damping 0.65): Switches, buttons, chips, step dots
 * - Spatial Default (stiffness 380, damping 0.80): Shared-Axis Z depth scale, cards, menus
 * - Spatial Slow (stiffness 200, damping 0.80): Full-screen top-level dissolves
 * - Effects Fast/Default (stiffness 1600 / 800, damping 1.0): Opacity & color (zero overshoot)
 * - Strictly animates transform (scale/translation) and opacity on RenderThread for 60/120fps.
 */
object SwiffleMotion {

    // ── 1. M3 Expressive Dual-Domain Physics Springs (Section 2.7) ────────────

    /**
     * Spatial Fast Spring (stiffness = 800f, damping = 0.65f):
     * Used for switches, buttons, segmented sliders, and morphing step indicators.
     */
    val SpringSpatialFast: SpringSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = 800f
    )

    /**
     * Spatial Default Spring (stiffness = 380f, damping = 0.80f):
     * Used for Shared-Axis Z depth transitions, cards, sheets, and menus.
     */
    val SpringSpatial: SpringSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = 380f
    )

    val SpringSpatialOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.80f,
        stiffness = 380f
    )

    /**
     * Spatial Slow Spring (stiffness = 200f, damping = 0.80f):
     * Used for full-screen workspace entry dissolves.
     */
    val SpringSpatialSlow: SpringSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = 200f
    )

    /**
     * Effects Fast Spring (stiffness = 1600f, damping = 1.0f):
     * Used exclusively for Alpha/Opacity, Color blending, and Scrims (zero overshoot).
     */
    val SpringEffects: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1600f
    )

    /**
     * Expressive Tactile Spring:
     * Subtle energetic pop for buttons and micro-press states.
     */
    val SpringTactile: SpringSpec<Float> = spring(
        dampingRatio = 0.68f,
        stiffness = 750f
    )

    // ── 2. Official Material 3 Easing Curves (Cubic Beziers) ─────────────────
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)
    val Emphasized: Easing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.0f)
    val Standard: Easing = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.0f)
    val StandardDecelerate: Easing = CubicBezierEasing(0.00f, 0.00f, 0.20f, 1.0f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.40f, 0.00f, 1.00f, 1.0f)

    // ── 3. Duration Tokens (Milliseconds) ────────────────────────────────────
    const val DurationShort4 = 180
    const val DurationMedium2 = 260
    const val DurationMedium4 = 350
    const val DurationLong2 = 450

    // Aliases mapped to M3 Expressive easing & duration tokens
    val FluentDecelerate: Easing = EmphasizedDecelerate
    val FluentAccelerate: Easing = EmphasizedAccelerate
    const val FluentNormalDuration = DurationMedium2
    const val FluentFastDuration = DurationShort4

    // ── 4. Canonical M3 Expressive Transition Archetypes ─────────────────────

    /**
     * Pattern 1: Container Transform (Hero Card / File -> Full Canvas)
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
     * Pattern 2: Forward Shared Axis Z (Primary M3 Expressive Page-to-Page Transition)
     * - Incoming screen elevates from 0.94f up to 1.0f using Spatial Default spring (stiffness 380, damping 0.8)
     *   while fading in over 240ms.
     * - Outgoing screen scales subtly to 1.04f while cleanly dissolving in 150ms (zero text ghosting).
     * - Executes strictly on RenderThread via scale & alpha transforms for 60/120fps zero-lag motion.
     */
    fun sharedAxisZForward(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = 240, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 0.94f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = 150, easing = EmphasizedAccelerate)
            ) + scaleOut(
                targetScale = 1.04f,
                animationSpec = SpringSpatial
            )
        ).apply {
            targetContentZIndex = 1f
        }
    }

    /**
     * Pattern 2 (Reverse): Backward Shared Axis Z (Hierarchical Return)
     * - Returning screen scales down from 1.04f to 1.0f; departing view recedes to 0.94f.
     */
    fun sharedAxisZBackward(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = 240, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 1.04f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerate)
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
     */
    fun pdfCanvasReturn(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = 260, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 1.03f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = 200, easing = EmphasizedAccelerate)
            ) + scaleOut(
                targetScale = 0.96f,
                animationSpec = SpringSpatial
            )
        ).apply {
            targetContentZIndex = 1f
        }
    }

    /**
     * Pattern 3: Expressive Step Lateral + Depth Transition (For Feature Tour Steps)
     * Combines a subtle 14% horizontal spring glide with a 0.96f -> 1.0f depth scale.
     */
    fun lateralForward(): ContentTransform {
        return (slideInHorizontally(
            initialOffsetX = { fullWidth -> (fullWidth * 0.14f).toInt() },
            animationSpec = SpringSpatialOffset
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = SpringSpatial
        ) + fadeIn(
            animationSpec = tween(durationMillis = 220, easing = EmphasizedDecelerate)
        )).togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> (-fullWidth * 0.14f).toInt() },
                animationSpec = SpringSpatialOffset
            ) + scaleOut(
                targetScale = 0.96f,
                animationSpec = SpringSpatial
            ) + fadeOut(
                animationSpec = tween(durationMillis = 140, easing = EmphasizedAccelerate)
            )
        )
    }

    fun lateralBackward(): ContentTransform {
        return (slideInHorizontally(
            initialOffsetX = { fullWidth -> (-fullWidth * 0.14f).toInt() },
            animationSpec = SpringSpatialOffset
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = SpringSpatial
        ) + fadeIn(
            animationSpec = tween(durationMillis = 220, easing = EmphasizedDecelerate)
        )).togetherWith(
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> (fullWidth * 0.14f).toInt() },
                animationSpec = SpringSpatialOffset
            ) + scaleOut(
                targetScale = 0.96f,
                animationSpec = SpringSpatial
            ) + fadeOut(
                animationSpec = tween(durationMillis = 140, easing = EmphasizedAccelerate)
            )
        )
    }

    /**
     * Pattern 4: Top Level Fade Through (Splash -> Onboarding -> Home)
     */
    fun scaleFadeThrough(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(durationMillis = 260, easing = EmphasizedDecelerate)
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = SpringSpatial
        )).togetherWith(
            fadeOut(
                animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerate)
            ) + scaleOut(
                targetScale = 1.03f,
                animationSpec = SpringSpatial
            )
        )
    }

    /**
     * Forward & Backward aliases mapped to Shared-Axis Z for 60fps zero-lag M3 Expressive navigation
     */
    fun horizontalForward(): ContentTransform = sharedAxisZForward()
    fun horizontalBackward(): ContentTransform = sharedAxisZBackward()

    val pageTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        sharedAxisZForward()
    }
}
