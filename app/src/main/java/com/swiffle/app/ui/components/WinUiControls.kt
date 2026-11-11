package com.swiffle.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swiffle.app.theme.ThemeManager

/**
 * Windows 11 Fluent WinUI 3 Toggle Switch Specification:
 * - 42dp x 24dp pill track (12dp corner radius)
 * - 1.5dp contour border
 * - Solid sliding thumb knob (12dp off -> 14dp on)
 * - Never hollow or empty when off (features a grounded thumb and visible track outline)
 */
@Composable
fun WinUiToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF0078D4),
    isDark: Boolean = ThemeManager.isDarkThemeActive()
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(durationMillis = 180, easing = CubicBezierEasing(0f, 0f, 0f, 1f)),
        label = "winui_toggle_anim"
    )
    val trackBg by animateColorAsState(
        targetValue = if (checked) activeColor else (if (isDark) Color(0xFF1E1E1E) else Color(0xFFEBEBEB)),
        animationSpec = tween(150),
        label = "winui_toggle_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) activeColor else (if (isDark) Color(0xFF5A5A5A) else Color(0xFF8A8A8A)),
        animationSpec = tween(150),
        label = "winui_toggle_border"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) Color.White else (if (isDark) Color(0xFFD4D4D4) else Color(0xFF5A5A5A)),
        animationSpec = tween(150),
        label = "winui_toggle_thumb"
    )

    Box(
        modifier = modifier
            .width(42.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackBg)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(if (checked) 14.dp else 12.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

