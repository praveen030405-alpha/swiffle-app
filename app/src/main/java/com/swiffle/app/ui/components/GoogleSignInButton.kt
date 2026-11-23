package com.swiffle.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.R
import com.swiffle.app.theme.ClaudeSans

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
 * Mild-White Glassmorphic "Continue with Google" Pill Button:
 * - Standard 52.dp height, fully rounded `RoundedCornerShape(999.dp)`
 * - Uses translucent mild-white frosted glass gradient + specular glass border
 * - Uses `ClaudeSans` typography
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isSpinning: Boolean = false,
    isSuccess: Boolean = false,
    enabled: Boolean = true,
    text: String = "Continue with Google",
    spinnerText: String = "Connecting to Google...",
    successText: String = "Signed in with Google"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading && !isSpinning && !isSuccess) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 700f),
        label = "google_glass_scale"
    )

    val showSpinner = (isLoading || isSpinning) && !isSuccess
    val textColor = if (isSuccess) Color(0xFF81C784) else MaroonIvoryText
    val pillShape = RoundedCornerShape(999.dp)

    val glassFillBrush = remember(isPressed) {
        Brush.linearGradient(
            colors = if (isPressed) {
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    Color(0xFFFFF5F2).copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.10f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color(0xFFFFF5F2).copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.07f)
                )
            },
            start = Offset(0f, 0f),
            end = Offset(600f, 180f)
        )
    }

    val glassBorderBrush = remember(isSuccess) {
        if (isSuccess) {
            Brush.linearGradient(listOf(Color(0xFF81C784), Color(0xFF81C784).copy(alpha = 0.6f)))
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.40f),
                    Color(0xFFF7DAD9).copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.12f)
                ),
                start = Offset(0f, 0f),
                end = Offset(600f, 180f)
            )
        }
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(pillShape)
            .background(glassFillBrush)
            .border(width = 1.dp, brush = glassBorderBrush, shape = pillShape)
            .clickable(
                enabled = enabled && !showSpinner && !isSuccess,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.25f)),
                onClick = onClick
            )
    ) {
        // Subtle top glass sheen reflection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    )
                )
        )

        AnimatedContent(
            targetState = Triple(isSuccess, showSpinner, text),
            label = "btn_state"
        ) { (success, spinning, buttonText) ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
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
                        fontFamily = ClaudeSans,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (spinning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaroonIvoryText
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = spinnerText,
                        color = MaroonIvoryText,
                        fontFamily = ClaudeSans,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    GoogleBrandIcon(modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = buttonText,
                        color = textColor,
                        fontFamily = ClaudeSans,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
