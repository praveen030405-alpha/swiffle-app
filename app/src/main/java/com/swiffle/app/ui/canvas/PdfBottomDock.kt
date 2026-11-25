package com.swiffle.app.ui.canvas

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.theme.GoogleSans
import com.swiffle.app.theme.SwiffleTheme

/**
 * Official Windows 11 Fluent CommandBar Dock:
 * - 8px progressive container radius with 1px specular contour border
 * - Dual-layer composite shadow (Elevation 8dp)
 * - 4px AppBarButtons for Thumbnails and Page Jump Selector
 * - Centered floating commanding dock matching Microsoft WinUI 3 specifications
 */
@Composable
fun PdfBottomDock(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    isDark: Boolean,
    isTtsPlaying: Boolean = false,
    isEditMode: Boolean = false,
    onToggleEditMode: () -> Unit = {},
    onToggleTts: () -> Unit = {},
    onOpenTtsSheet: () -> Unit = {},
    onOpenThumbnails: () -> Unit,
    onOpenJumpSheet: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val layoutDirection = LocalLayoutDirection.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(180)
        ),
        modifier = modifier
    ) {
        val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
        val navBarBottom = navBarInsets.calculateBottomPadding()
        val navBarStart = navBarInsets.calculateStartPadding(layoutDirection)
        val navBarEnd = navBarInsets.calculateEndPadding(layoutDirection)

        val bottomMargin = if (isLandscape) 14.dp + navBarBottom else 20.dp + navBarBottom
        val startMargin = 16.dp + navBarStart
        val endMargin = 16.dp + navBarEnd

        val colors = SwiffleTheme.colors
        val commandBarBg = colors.surfaceContainerLow
        val specularBorder = colors.borderDefault
        val textPrimary = colors.textPrimary
        val textSecondary = colors.textSecondary
        val separatorColor = if (isDark) Color(0x22FFFFFF) else Color(0x12000000)
        val hoverPressBg = if (isDark) Color(0x1AFFFFFF) else Color(0x0A000000)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomMargin, start = startMargin, end = endMargin),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Windows 11 Fluent CommandBar Container (8px corner radius, 1px contour border)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = commandBarBg,
                border = BorderStroke(1.dp, specularBorder),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = MaterialTheme.shapes.medium,
                        spotColor = Color.Black.copy(alpha = 0.22f),
                        ambientColor = Color.Black.copy(alpha = 0.08f)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1. Fluent AppBarButton: Thumbnails Grid (4px corner radius)
                    val thumbInteraction = remember { MutableInteractionSource() }
                    val thumbPressed by thumbInteraction.collectIsPressedAsState()
                    val thumbScale by animateFloatAsState(
                        targetValue = if (thumbPressed) 0.94f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "thumbScale"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer { scaleX = thumbScale; scaleY = thumbScale }
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (thumbPressed) hoverPressBg else Color.Transparent)
                            .clickable(interactionSource = thumbInteraction, indication = null) {
                                onOpenThumbnails()
                            }
                            .heightIn(min = 48.dp).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GridView,
                                contentDescription = "Thumbnails",
                                tint = textPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Pages",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = textPrimary
                            )
                        }
                    }

                    // 2. WinUI 3 AppBarSeparator: 1px vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(separatorColor)
                    )

                    // 3. Fluent AppBarButton: Page Jump Selector (4px corner radius)
                    val pageInteraction = remember { MutableInteractionSource() }
                    val pagePressed by pageInteraction.collectIsPressedAsState()
                    val pageScale by animateFloatAsState(
                        targetValue = if (pagePressed) 0.94f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "pageScale"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer { scaleX = pageScale; scaleY = pageScale }
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (pagePressed) hoverPressBg else Color.Transparent)
                            .clickable(interactionSource = pageInteraction, indication = null) {
                                onOpenJumpSheet()
                            }
                            .heightIn(min = 48.dp).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (pageCount > 0) "$currentPage of $pageCount" else "Page ...",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = textPrimary
                            )
                            Icon(
                                imageVector = Icons.Rounded.UnfoldMore,
                                contentDescription = "Jump to page",
                                tint = textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // 4. WinUI 3 AppBarSeparator: 1px vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(separatorColor)
                    )

                    // 5. Fluent AppBarButton: Realistic PDF Editor Toggle
                    val editInteraction = remember { MutableInteractionSource() }
                    val editPressed by editInteraction.collectIsPressedAsState()
                    val editScale by animateFloatAsState(
                        targetValue = if (editPressed) 0.94f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "editScale"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer { scaleX = editScale; scaleY = editScale }
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isEditMode) colors.primary
                                else if (editPressed) hoverPressBg
                                else Color.Transparent
                            )
                            .clickable(interactionSource = editInteraction, indication = null) {
                                onToggleEditMode()
                            }
                            .heightIn(min = 48.dp).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit PDF",
                                tint = if (isEditMode) Color.White else textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isEditMode) "Editing" else "Edit",
                                fontFamily = GoogleSans,
                                fontWeight = if (isEditMode) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isEditMode) Color.White else textPrimary
                            )
                        }
                    }
                    TextButton(onClick = onAsk, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.primaryAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ask", color = colors.primaryAccent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
