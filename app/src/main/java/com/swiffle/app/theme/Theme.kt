package com.swiffle.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

// ── Design System v1.1 Shape Tokens (Section 2.5) ──────────────────────────
// shape.xs = 4dp | shape.sm = 8dp | shape.md = 12dp | shape.lg = 16dp | shape.xl = 28dp
val PlayStoreShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // shape.xs: Tooltips, badges
    small      = RoundedCornerShape(8.dp),   // shape.sm: Chips, snackbars
    medium     = RoundedCornerShape(12.dp),  // shape.md: Inputs, small cards
    large      = RoundedCornerShape(16.dp),  // shape.lg: Cards, menus
    extraLarge = RoundedCornerShape(28.dp)   // shape.xl: Dialogs, sheets, FAB
)

// ── Design System v1.1 Dark Color Scheme (True Black #000000 + Maroon) ─────
private val DarkColorScheme = darkColorScheme(
    primary                 = Maroon600,               // #800000 filled primary surface
    onPrimary               = Color(0xFFFFFFFF),       // #FFFFFF (10.9:1 AAA)
    primaryContainer        = Maroon800,               // #590000
    onPrimaryContainer      = Maroon100,               // #F7DAD9
    secondary               = Maroon300,               // #D9807F primaryAccent (~7:1 on black)
    onSecondary             = Maroon900,
    secondaryContainer      = SecondaryContainerDark,  // #3A2A2A
    onSecondaryContainer    = TextPrimaryDark,         // #F5F5F5
    tertiary                = Maroon200,
    onTertiary              = Maroon900,
    tertiaryContainer       = Maroon800,
    onTertiaryContainer     = Maroon100,
    background              = BgPageDark,              // #000000
    onBackground            = TextPrimaryDark,         // #F5F5F5
    surface                 = BgSurfaceDark,           // #0D0D0D
    onSurface               = TextPrimaryDark,         // #F5F5F5
    surfaceVariant          = SurfaceContainerDark,    // #161616
    onSurfaceVariant        = TextSecondaryDark,       // #B8B8B8
    surfaceContainerLowest  = BgPageDark,              // #000000
    surfaceContainerLow     = SurfaceContainerLowDark, // #121212
    surfaceContainer        = SurfaceContainerDark,    // #161616
    surfaceContainerHigh    = SurfaceContainerHighDark,// #1F1F1F
    surfaceContainerHighest = BorderDefaultDark,       // #2E2E2E
    outline                 = BorderDefaultDark,       // #2E2E2E
    outlineVariant          = SurfaceContainerHighDark,// #1F1F1F
    error                   = StatusErrorDark,         // #FF6B6B
    onError                 = Color(0xFF000000),
    errorContainer          = Color(0xFF491212),
    onErrorContainer        = Color(0xFFFFDAD6)
)

// ── Design System v1.1 Light Color Scheme (White #FFFFFF + Maroon) ─────────
private val LightColorScheme = lightColorScheme(
    primary                 = Maroon600,                // #800000
    onPrimary               = Color(0xFFFFFFFF),        // #FFFFFF
    primaryContainer        = Maroon100,                // #F7DAD9
    onPrimaryContainer      = Maroon900,                // #3D0000
    secondary               = Maroon600,                // #800000
    onSecondary             = Color(0xFFFFFFFF),
    secondaryContainer      = SecondaryContainerLight,  // #F1E4E3
    onSecondaryContainer    = TextPrimaryLight,         // #1C1B1B
    tertiary                = Maroon500,
    onTertiary              = Color(0xFFFFFFFF),
    tertiaryContainer       = Maroon100,
    onTertiaryContainer     = Maroon900,
    background              = BgPageLight,              // #FFFFFF
    onBackground            = TextPrimaryLight,         // #1C1B1B
    surface                 = BgSurfaceLight,           // #FFFFFF
    onSurface               = TextPrimaryLight,         // #1C1B1B
    surfaceVariant          = SurfaceContainerLight,    // #F4EEEE
    onSurfaceVariant        = TextSecondaryLight,       // #5F5757
    surfaceContainerLowest  = BgPageLight,              // #FFFFFF
    surfaceContainerLow     = SurfaceContainerLowLight, // #FAF6F6
    surfaceContainer        = SurfaceContainerLight,    // #F4EEEE
    surfaceContainerHigh    = SurfaceContainerHighLight,// #EDE6E6
    surfaceContainerHighest = BorderDefaultLight,       // #D8CECE
    outline                 = BorderDefaultLight,       // #D8CECE
    outlineVariant          = SurfaceContainerHighLight,// #EDE6E6
    error                   = StatusErrorLight,         // #D32F2F
    onError                 = Color(0xFFFFFFFF),
    errorContainer          = Color(0xFFFFDAD6),
    onErrorContainer        = Color(0xFF410002)
)

object SwiffleTheme {
    val colors: SwiffleSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSwiffleColors.current
}

@Composable
fun OrigamiPDFTheme(
    darkTheme: Boolean = ThemeManager.isDarkThemeActive(),
    dynamicColor: Boolean = false, // Strictly disabled to enforce Maroon #800000 Design System v1.1
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticTokens = if (darkTheme) DarkSwiffleTokens else LightSwiffleTokens

    val currentDensity = LocalDensity.current
    val clampedDensity = remember(currentDensity) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.15f)
        )
    }

    CompositionLocalProvider(
        LocalDensity provides clampedDensity,
        LocalSwiffleColors provides semanticTokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PlayStoreShapes,
            content = content
        )
    }
}
