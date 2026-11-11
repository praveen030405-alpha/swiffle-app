package com.swiffle.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ── Google Play Store / Material 3 Pro Shape System ────────────────────────
val PlayStoreShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(16.dp),  // Standard cards
    large      = RoundedCornerShape(24.dp),  // Play Store hero cards & dialogs
    extraLarge = RoundedCornerShape(28.dp)   // Bottom sheets & floating search dock
)

// ── Google Play Store M3 Dark Color Scheme ─────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary                 = DarkModeBlueNatural,
    onPrimary               = Color.White,
    primaryContainer        = DarkModeBlueSurface,
    onPrimaryContainer      = DarkModeBlueOnSurface,
    secondary               = DarkModeBlueNatural,
    onSecondary             = Color.White,
    secondaryContainer      = DarkModeBlueSurface,
    onSecondaryContainer    = DarkModeBlueOnSurface,
    tertiary                = GoogleGreen,
    onTertiary              = Color.White,
    tertiaryContainer       = GoogleGreenSubtle,
    onTertiaryContainer     = GoogleGreenLight,
    background              = SurfaceDark900,
    onBackground            = TextPrimaryDark,
    surface                 = SurfaceDark800,
    onSurface               = TextPrimaryDark,
    surfaceVariant          = SurfaceDark700,
    onSurfaceVariant        = TextSecondaryDark,
    surfaceContainerLowest  = SurfaceDark900,
    surfaceContainerLow     = SurfaceDark800,
    surfaceContainer        = SurfaceDark700,
    surfaceContainerHigh    = SurfaceDark600,
    surfaceContainerHighest = SurfaceDark500,
    outline                 = SurfaceDark500,
    outlineVariant          = SurfaceDark600,
    error                   = DarkModeRedNatural,
    onError                 = Color.White,
    errorContainer          = DarkModeRedContainer,
    onErrorContainer        = DarkModeRedOnContainer
)

// ── Google Play Store M3 Light Color Scheme ────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary                 = SwiffleNavyPrimary,
    onPrimary               = Color.White,
    primaryContainer        = Color(0xFFD3E3FD),
    onPrimaryContainer      = Color(0xFF041E49),
    secondary               = SwiffleCobaltAccent,
    onSecondary             = Color.White,
    secondaryContainer      = Color(0xFFD3E3FD),
    onSecondaryContainer    = Color(0xFF041E49),
    tertiary                = GoogleGreen,
    onTertiary              = Color.White,
    tertiaryContainer       = Color(0xFFC4EED0),
    onTertiaryContainer     = Color(0xFF072711),
    background              = SurfaceLight100,
    onBackground            = TextPrimaryLight,
    surface                 = SurfaceLight200,
    onSurface               = TextPrimaryLight,
    surfaceVariant          = SurfaceLight300,
    onSurfaceVariant        = TextSecondaryLight,
    surfaceContainerLowest  = Color.White,
    surfaceContainerLow     = SurfaceLight200,
    surfaceContainer        = SurfaceLight300,
    surfaceContainerHigh    = SurfaceLight400,
    surfaceContainerHighest = SurfaceLight500,
    outline                 = SurfaceLight500,
    outlineVariant          = SurfaceLight400,
    error                   = SemanticError,
    onError                 = Color.White
)

@Composable
fun OrigamiPDFTheme(
    darkTheme: Boolean = ThemeManager.isDarkThemeActive(),
    dynamicColor: Boolean = false,          // Preserves crafted, rich natural brand colors without OS pastel washing
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }
    // Pro Adaptive System: Density & Font Scale Clamping
    // Protects chips, tabs, toolbars, and cards from shattering when users enable "Large" or "Largest" text (1.3x - 2.0x) in Android settings.
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val clampedDensity = androidx.compose.runtime.remember(currentDensity) {
        androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.15f)
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides clampedDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = PlayStoreShapes,
            content = content
        )
    }
}

