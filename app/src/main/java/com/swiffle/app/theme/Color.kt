package com.swiffle.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════════
//  SWIFFLE DESIGN SYSTEM v1.1 — MAROON (#800000) PRIMITIVE & SEMANTIC TOKENS
// ══════════════════════════════════════════════════════════════════════════════

// 1. Primitive Maroon Tonal Scale (Section 2.1)
val Maroon900 = Color(0xFF3D0000)
val Maroon800 = Color(0xFF590000)
val Maroon700 = Color(0xFF6B0000)
val Maroon600 = Color(0xFF800000) // Brand Primary (Identical in Light & Dark)
val Maroon500 = Color(0xFF9B1C1C)
val Maroon400 = Color(0xFFB84545)
val Maroon300 = Color(0xFFD9807F) // Dark Mode Primary Accent (~7:1 on Black)
val Maroon200 = Color(0xFFEBB0AF)
val Maroon100 = Color(0xFFF7DAD9) // Light Mode Primary Container / Dark OnPrimaryContainer
val Maroon50  = Color(0xFFFDF1F0)

// 2. Semantic Light Mode Tokens (Section 2.1)
val BgPageLight               = Color(0xFFFFFFFF)
val BgSurfaceLight            = Color(0xFFFFFFFF)
val SurfaceContainerLowLight  = Color(0xFFFAF6F6)
val SurfaceContainerLight     = Color(0xFFF4EEEE)
val SurfaceContainerHighLight = Color(0xFFEDE6E6)
val SecondaryContainerLight   = Color(0xFFF1E4E3)
val TextPrimaryLight          = Color(0xFF1C1B1B)
val TextSecondaryLight        = Color(0xFF5F5757)
val TextDisabledLight         = Color(0xFFA39B9B)
val BorderDefaultLight        = Color(0xFFD8CECE)
val BorderFocusLight          = Color(0xFF800000)

// 3. Semantic Dark Mode Tokens (Section 2.1 — True Black Base)
val BgPageDark                = Color(0xFF000000)
val BgSurfaceDark             = Color(0xFF0D0D0D)
val SurfaceContainerLowDark   = Color(0xFF121212)
val SurfaceContainerDark      = Color(0xFF161616)
val SurfaceContainerHighDark  = Color(0xFF1F1F1F)
val SecondaryContainerDark    = Color(0xFF3A2A2A)
val TextPrimaryDark           = Color(0xFFF5F5F5)
val TextSecondaryDark         = Color(0xFFB8B8B8)
val TextDisabledDark          = Color(0xFF6B6B6B)
val BorderDefaultDark         = Color(0xFF2E2E2E)
val BorderFocusDark           = Color(0xFFD9807F)

// 4. Semantic Status Tokens (Light / Dark)
val StatusSuccessLight        = Color(0xFF2E7D32)
val StatusSuccessDark         = Color(0xFF66BB6A)
val StatusWarningLight        = Color(0xFFED6C02)
val StatusWarningDark         = Color(0xFFFFB74D)
val StatusErrorLight          = Color(0xFFD32F2F)
val StatusErrorDark           = Color(0xFFFF6B6B)
val StatusInfoLight           = Color(0xFF0277BD)
val StatusInfoDark            = Color(0xFF4FC3F7)

@Immutable
data class SwiffleSemanticColors(
    val bgPage: Color,
    val bgSurface: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryAccent: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val borderDefault: Color,
    val borderFocus: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val isDark: Boolean
)

val LightSwiffleTokens = SwiffleSemanticColors(
    bgPage               = BgPageLight,
    bgSurface            = BgSurfaceLight,
    surfaceContainerLow  = SurfaceContainerLowLight,
    surfaceContainer     = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    primary              = Maroon600,
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Maroon100,
    onPrimaryContainer   = Maroon900,
    primaryAccent        = Maroon600,
    secondaryContainer   = SecondaryContainerLight,
    onSecondaryContainer = TextPrimaryLight,
    textPrimary          = TextPrimaryLight,
    textSecondary        = TextSecondaryLight,
    textDisabled         = TextDisabledLight,
    borderDefault        = BorderDefaultLight,
    borderFocus          = BorderFocusLight,
    success              = StatusSuccessLight,
    warning              = StatusWarningLight,
    error                = StatusErrorLight,
    info                 = StatusInfoLight,
    isDark               = false
)

val DarkSwiffleTokens = SwiffleSemanticColors(
    bgPage               = BgPageDark,
    bgSurface            = BgSurfaceDark,
    surfaceContainerLow  = SurfaceContainerLowDark,
    surfaceContainer     = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    primary              = Maroon600,
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Maroon800,
    onPrimaryContainer   = Maroon100,
    primaryAccent        = Maroon300, // #D9807F (~7:1 contrast on black)
    secondaryContainer   = SecondaryContainerDark,
    onSecondaryContainer = TextPrimaryDark,
    textPrimary          = TextPrimaryDark,
    textSecondary        = TextSecondaryDark,
    textDisabled         = TextDisabledDark,
    borderDefault        = BorderDefaultDark,
    borderFocus          = BorderFocusDark,
    success              = StatusSuccessDark,
    warning              = StatusWarningDark,
    error                = StatusErrorDark,
    info                 = StatusInfoDark,
    isDark               = true
)

val LocalSwiffleColors = staticCompositionLocalOf { LightSwiffleTokens }

// ── Compatibility Aliases Mapped to Maroon v1.1 Tokens ─────────────────────
val OrigamiRed             = Maroon600
val OrigamiRedDim          = Maroon700
val OrigamiRedSubtle       = Maroon900

val DarkModeRedNatural     = StatusErrorDark
val DarkModeRedContainer   = Maroon800
val DarkModeRedOnContainer = Maroon100

val GoogleBlue             = Maroon600
val GoogleBlueLight        = Maroon100
val GoogleBlueSubtle       = Maroon900

val DarkModeBlueNatural    = Maroon300
val DarkModeBlueSurface    = Maroon800
val DarkModeBlueOnSurface  = Maroon100
val GoogleGreen            = StatusSuccessLight
val GoogleGreenLight       = Color(0xFFC4EED0)
val GoogleGreenSubtle      = Color(0xFF072711)
val GoogleYellow           = StatusWarningLight
val GoogleYellowLight      = Color(0xFFFFDDB3)

val VoidBlack              = BgPageDark
val SurfaceDark900         = BgPageDark
val SurfaceDark800         = SurfaceContainerLowDark
val SurfaceDark700         = SurfaceContainerDark
val SurfaceDark600         = SurfaceContainerHighDark
val SurfaceDark500         = BorderDefaultDark
val TextMutedDark          = TextDisabledDark

val SurfaceLight100        = BgPageLight
val SurfaceLight200        = SurfaceContainerLowLight
val SurfaceLight300        = SurfaceContainerLight
val SurfaceLight400        = SurfaceContainerHighLight
val SurfaceLight500        = BorderDefaultLight
val TextMutedLight         = TextDisabledLight

val SwiffleNavyPrimary     = Maroon600
val SwiffleCobaltAccent    = Maroon500
val SwiffleIceBlue         = Maroon100

val SemanticSuccess        = StatusSuccessLight
val SemanticWarning        = StatusWarningLight
val SemanticError          = StatusErrorLight
val SemanticInfo           = StatusInfoLight

val ExcelGreen             = Color(0xFF107C41)
val ExcelGreenLight        = Color(0xFF185ABD)
val ConvertBlue            = Maroon600
val ConvertPurple          = Maroon500
val ConvertAmber           = StatusWarningLight

val Purple80               = Maroon200
val PurpleGrey80           = Maroon100
val Pink80                 = Maroon200
val Purple40               = Maroon600
val PurpleGrey40           = Maroon700
val Pink40                 = Maroon500
