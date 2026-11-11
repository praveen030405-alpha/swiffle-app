package com.swiffle.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.R
import com.swiffle.app.theme.GoogleSans

/**
 * Official Google Brand "G" Icon using canonical 24dp Vector Drawable.
 */
@Composable
fun GoogleBrandIcon(
    modifier: Modifier = Modifier.size(20.dp)
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_google_logo),
        contentDescription = "Google Logo",
        tint = Color.Unspecified,
        modifier = modifier
    )
}

/**
 * Stock Google Material 4 Pro "Sign in with Google" Pill Button
 * - Standard 44dp pill shape (RoundedCornerShape 22dp)
 * - Material 4 Pro smooth spinner rotating inside pill during auth & transition
 * - Tactile spring feedback
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isSpinning: Boolean = false,
    isSuccess: Boolean = false,
    enabled: Boolean = true,
    text: String = "Sign in with Google",
    spinnerText: String = "Connecting to Google...",
    successText: String = "Sign in Successful"
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading && !isSpinning && !isSuccess) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
        label = "google_btn_scale"
    )

    // Official Material 3 Pro Colors (Solid, Pristine, Completely Non-Glassy)
    val targetBg = when {
        isSuccess -> if (isDark) Color(0xFF132D1B) else Color(0xFFE8F5E9)
        isDark -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Color.White
    }
    val targetBorder = when {
        isSuccess -> if (isDark) Color(0xFF2E7D32) else Color(0xFF81C784)
        isDark -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
    }
    val targetText = when {
        isSuccess -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        isDark -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    val containerColor by animateColorAsState(targetBg, tween(250), label = "btn_bg")
    val borderColor by animateColorAsState(targetBorder, tween(250), label = "btn_border")
    val textColor by animateColorAsState(targetText, tween(250), label = "btn_text")

    val showSpinner = (isLoading || isSpinning) && !isSuccess

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .shadow(
                elevation = if (!enabled) 0.dp else if (isPressed) 0.5.dp else 1.dp,
                shape = RoundedCornerShape(4.dp),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = if (enabled) 1f else 0.4f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !showSpinner && !isSuccess,
                onClick = onClick
            )
            .height(50.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = Triple(isSuccess, showSpinner, text),
            label = "btn_state"
        ) { (success, spinning, buttonText) ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (success) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Success",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = successText,
                        color = textColor,
                        fontFamily = GoogleSans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (spinning) {
                    StockMaterialExpressiveIndicator(
                        size = 18.dp,
                        strokeWidth = 2.5.dp,
                        useGoogleColors = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = spinnerText,
                        color = textColor.copy(alpha = 0.85f),
                        fontFamily = GoogleSans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    GoogleBrandIcon(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = buttonText,
                        color = textColor,
                        fontFamily = GoogleSans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.25.sp
                    )
                }
            }
        }
    }
}
