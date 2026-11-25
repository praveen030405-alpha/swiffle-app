package com.swiffle.app.ui.chat

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swiffle.app.R
import com.swiffle.app.ai.SwiffleModelTier
import com.swiffle.app.ai.UsageRateSnapshot
import com.swiffle.app.theme.ClaudeSans
import com.swiffle.app.theme.ClaudeSerif
import com.swiffle.app.theme.SwiffleMotion
import com.swiffle.app.theme.SwiffleMotion.FluentAccelerate
import com.swiffle.app.theme.SwiffleMotion.FluentDecelerate
import com.swiffle.app.theme.SwiffleMotion.FluentFastDuration
import com.swiffle.app.theme.SwiffleMotion.FluentNormalDuration
import com.swiffle.app.ui.canvas.RichMarkdownBody
import com.swiffle.app.ui.canvas.SwiffleChatMessage
import com.swiffle.app.ui.home.HomeThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transparent Floating Glassmorphic Top Header for Swiffle AI Chat.
 * Places Back inside a round glass pill, Model Tier selector ("Swiffle Omni" / "Swiffle Flash" / "Swiffle Ultra")
 * in a rounded glass pill beside it, and 3-dot overflow inside a matching round glass pill.
 */
@Composable
internal fun ObsidianChatTopBar(
    tokens: HomeThemeTokens,
    selectedModel: SwiffleModelTier,
    modelMenuExpanded: Boolean,
    overflowMenuExpanded: Boolean,
    onBack: () -> Unit,
    onToggleModelMenu: () -> Unit,
    onToggleOverflowMenu: () -> Unit
) {
    val glassFill = remember(tokens.isDark) {
        if (tokens.isDark) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.11f),
                    Color.White.copy(alpha = 0.05f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.86f),
                    Color(0xFFF3ECEF).copy(alpha = 0.74f)
                )
            )
        }
    }

    val pillBorder = remember(tokens.isDark) {
        Brush.linearGradient(
            listOf(
                if (tokens.isDark) Color.White.copy(alpha = 0.24f) else Color.Black.copy(alpha = 0.12f),
                tokens.accentRose.copy(alpha = if (tokens.isDark) 0.24f else 0.16f),
                if (tokens.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            )
        )
    }

    val modelPillBorder = remember(tokens.isDark, modelMenuExpanded) {
        if (modelMenuExpanded) {
            Brush.linearGradient(
                listOf(
                    tokens.accentRose.copy(alpha = 0.65f),
                    Color.White.copy(alpha = if (tokens.isDark) 0.35f else 0.6f)
                )
            )
        } else {
            pillBorder
        }
    }

    val overflowPillBorder = remember(tokens.isDark, overflowMenuExpanded) {
        if (overflowMenuExpanded) {
            Brush.linearGradient(
                listOf(
                    tokens.accentRose.copy(alpha = 0.65f),
                    Color.White.copy(alpha = if (tokens.isDark) 0.35f else 0.6f)
                )
            )
        } else {
            pillBorder
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (modelMenuExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "model_chevron_rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Round Glassy Back Pill (48dp touch target)
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = if (tokens.isDark) 8.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(CircleShape)
                .background(glassFill)
                .border(0.9.dp, pillBorder, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, color = tokens.textPrimary.copy(alpha = 0.18f)),
                    onClick = onBack
                )
                .testTag("chat_back_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = tokens.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 2. Model Selector Glassy Pill beside Back button
        val modelPillShape = RoundedCornerShape(24.dp)
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .shadow(
                    elevation = if (tokens.isDark) 8.dp else 4.dp,
                    shape = modelPillShape,
                    ambientColor = Color.Black.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(modelPillShape)
                .background(glassFill)
                .border(0.9.dp, modelPillBorder, modelPillShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = tokens.textPrimary.copy(alpha = 0.16f)),
                    onClick = onToggleModelMenu
                )
                .padding(start = 16.dp, end = 12.dp)
                .testTag("chat_model_selector_pill"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = selectedModel.displayName,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f)) togetherWith
                        (fadeOut(tween(130)) + scaleOut(targetScale = 0.96f))
                },
                label = "model_pill_name"
            ) { modelName ->
                Text(
                    text = modelName,
                    fontFamily = ClaudeSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (modelMenuExpanded) "Collapse model selector" else "Select AI model",
                tint = tokens.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 3. Round Glassy 3-Dot Overflow Pill (48dp touch target)
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = if (tokens.isDark) 8.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(CircleShape)
                .background(glassFill)
                .border(0.9.dp, overflowPillBorder, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, color = tokens.textPrimary.copy(alpha = 0.18f)),
                    onClick = onToggleOverflowMenu
                )
                .testTag("chat_overflow_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Chat options",
                tint = tokens.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Fluent-animated Glassmorphic Model Tier Dropdown (Swiffle Omni, Swiffle Flash, Swiffle Ultra).
 */
@Composable
internal fun ObsidianModelSelectorDropdown(
    visible: Boolean,
    selectedModel: SwiffleModelTier,
    tokens: HomeThemeTokens,
    onSelectModel: (SwiffleModelTier) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) +
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f)
            ) +
            scaleIn(
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.24f, 0f),
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f)
            ),
        exit = fadeOut(tween(140)) +
            shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(150, easing = FluentAccelerate)
            ) +
            scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(0.24f, 0f),
                animationSpec = tween(150, easing = FluentAccelerate)
            )
    ) {
        val panelShape = RoundedCornerShape(24.dp)
        // Liquid Glass translucent refractive surface layered over the blurred chat backdrop
        val liquidGlassFill = remember(tokens.isDark) {
            if (tokens.isDark) {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.20f),
                    0.28f to Color.White.copy(alpha = 0.09f),
                    0.72f to Color.White.copy(alpha = 0.045f),
                    1.0f to Color.White.copy(alpha = 0.11f)
                )
            } else {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.84f),
                    0.40f to Color(0xFFFDF8FA).copy(alpha = 0.72f),
                    1.0f to Color(0xFFF3E9ED).copy(alpha = 0.78f)
                )
            }
        }
        val liquidDepthVeil = if (tokens.isDark) {
            Color(0xFF0A070C).copy(alpha = 0.34f)
        } else {
            Color.White.copy(alpha = 0.14f)
        }
        val liquidRimBorder = remember(tokens.isDark) {
            Brush.linearGradient(
                listOf(
                    if (tokens.isDark) Color.White.copy(alpha = 0.44f) else Color.White.copy(alpha = 0.92f),
                    tokens.accentRose.copy(alpha = if (tokens.isDark) 0.32f else 0.20f),
                    if (tokens.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                    if (tokens.isDark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.72f)
                )
            )
        }

        Column(
            modifier = Modifier
                .widthIn(min = 264.dp, max = 312.dp)
                .shadow(
                    elevation = if (tokens.isDark) 18.dp else 10.dp,
                    shape = panelShape,
                    ambientColor = Color.Black.copy(alpha = 0.38f),
                    spotColor = Color.Black.copy(alpha = 0.38f)
                )
                .clip(panelShape)
                .background(liquidDepthVeil)
                .background(liquidGlassFill)
                .drawBehind {
                    // Top-left liquid glass caustic refraction sheen
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (tokens.isDark) 0.18f else 0.55f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.18f, 0f),
                            radius = size.width * 0.78f
                        ),
                        center = Offset(size.width * 0.18f, 0f),
                        radius = size.width * 0.78f
                    )
                    // Bottom-right subtle rose chromatic bounce
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tokens.accentRose.copy(alpha = if (tokens.isDark) 0.14f else 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.88f, size.height * 0.96f),
                            radius = size.width * 0.65f
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.96f),
                        radius = size.width * 0.65f
                    )
                }
                .border(1.dp, liquidRimBorder, panelShape)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SwiffleModelTier.entries.forEach { tier ->
                val isSelected = tier == selectedModel
                val rowBg by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (tokens.isDark) Color.White.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.68f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "model_row_bg_${tier.id}"
                )
                val rowBorder by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (tokens.isDark) Color.White.copy(alpha = 0.34f)
                        else tokens.accentRose.copy(alpha = 0.24f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(180),
                    label = "model_row_border_${tier.id}"
                )

                val rowShape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(rowShape)
                        .background(rowBg)
                        .border(0.85.dp, rowBorder, rowShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = tokens.textPrimary.copy(alpha = 0.14f)),
                            onClick = { onSelectModel(tier) }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("chat_model_option_${tier.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tier.displayName,
                            fontFamily = ClaudeSerif,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tier.tagline,
                            fontFamily = ClaudeSans,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = tokens.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.75f),
                        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.75f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = tokens.textPrimary,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fluent-animated Liquid Glass 3-Dot Overflow Popover Menu.
 * Logical order: New Chat -> Chat History -> Usage & Limits.
 */
@Composable
internal fun ObsidianOverflowMenuDropdown(
    visible: Boolean,
    hasMessages: Boolean,
    tokens: HomeThemeTokens,
    onNewChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenUsageLimits: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) +
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f)
            ) +
            scaleIn(
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.86f, 0f),
                animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f)
            ),
        exit = fadeOut(tween(140)) +
            shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(150, easing = FluentAccelerate)
            ) +
            scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(0.86f, 0f),
                animationSpec = tween(150, easing = FluentAccelerate)
            )
    ) {
        val panelShape = RoundedCornerShape(22.dp)
        val liquidGlassFill = remember(tokens.isDark) {
            if (tokens.isDark) {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.18f),
                    0.32f to Color.White.copy(alpha = 0.08f),
                    0.75f to Color.White.copy(alpha = 0.04f),
                    1.0f to Color.White.copy(alpha = 0.10f)
                )
            } else {
                Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = 0.86f),
                    0.50f to Color(0xFFFAF4F6).copy(alpha = 0.74f),
                    1.0f to Color(0xFFF3E9ED).copy(alpha = 0.80f)
                )
            }
        }
        val liquidDepthVeil = if (tokens.isDark) Color(0xFF0A070C).copy(alpha = 0.36f) else Color.White.copy(alpha = 0.14f)
        val panelBorder = remember(tokens.isDark) {
            Brush.linearGradient(
                listOf(
                    if (tokens.isDark) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.90f),
                    tokens.accentRose.copy(alpha = if (tokens.isDark) 0.32f else 0.20f),
                    if (tokens.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
                )
            )
        }

        Column(
            modifier = Modifier
                .widthIn(min = 212.dp, max = 252.dp)
                .shadow(
                    elevation = if (tokens.isDark) 18.dp else 10.dp,
                    shape = panelShape,
                    ambientColor = Color.Black.copy(alpha = 0.40f),
                    spotColor = Color.Black.copy(alpha = 0.40f)
                )
                .clip(panelShape)
                .background(liquidDepthVeil)
                .background(liquidGlassFill)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (tokens.isDark) 0.16f else 0.50f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.80f, 0f),
                            radius = size.width * 0.75f
                        ),
                        center = Offset(size.width * 0.80f, 0f),
                        radius = size.width * 0.75f
                    )
                }
                .border(1.dp, panelBorder, panelShape)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ObsidianOverflowMenuItem(
                icon = Icons.Rounded.EditNote,
                label = "New Chat",
                iconTint = tokens.accentRose,
                labelColor = if (hasMessages) tokens.textPrimary else tokens.textSecondary,
                tokens = tokens,
                testTag = "chat_menu_new_chat",
                onClick = onNewChat
            )
            ObsidianOverflowMenuItem(
                icon = Icons.Rounded.History,
                label = "Chat History",
                iconTint = tokens.textSecondary,
                labelColor = tokens.textPrimary,
                tokens = tokens,
                testTag = "chat_menu_history",
                onClick = onOpenHistory
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                thickness = 0.8.dp,
                color = tokens.borderSubtle
            )
            ObsidianOverflowMenuItem(
                icon = Icons.Outlined.DataUsage,
                label = "Usage & Limits",
                iconTint = tokens.textSecondary,
                labelColor = tokens.textPrimary,
                tokens = tokens,
                testTag = "chat_menu_usage_limits",
                onClick = onOpenUsageLimits
            )
        }
    }
}

@Composable
private fun ObsidianOverflowMenuItem(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    labelColor: Color,
    tokens: HomeThemeTokens,
    testTag: String,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(rowShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = tokens.textPrimary.copy(alpha = 0.14f)),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = ClaudeSans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor
        )
    }
}

@Immutable
private data class ChatPromptSuggestion(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val accentTint: Color
)

/**
 * Obsidian Editorial Empty / Welcome Hero View with quick document prompt cards.
 */
@Composable
internal fun ObsidianChatHeroView(
    tokens: HomeThemeTokens,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    onPromptClick: (String) -> Unit
) {
    val roseTint = tokens.accentRose
    val emeraldTint = if (tokens.isDark) Color(0xFF56C288) else Color(0xFF107C41)
    val violetTint = if (tokens.isDark) Color(0xFFB89BFF) else Color(0xFF6B46C1)
    val skyTint = if (tokens.isDark) Color(0xFF67B7DC) else Color(0xFF0078D4)

    val suggestions = remember(tokens.isDark) {
        listOf(
            ChatPromptSuggestion(
                id = "summarize",
                title = "Summarize active document with structured takeaways",
                icon = Icons.Rounded.AutoAwesome,
                accentTint = roseTint
            ),
            ChatPromptSuggestion(
                id = "tables",
                title = "Extract and format document tables into clean data",
                icon = Icons.Rounded.TableChart,
                accentTint = emeraldTint
            ),
            ChatPromptSuggestion(
                id = "deep_dive",
                title = "Deep dive into technical concepts or complex clauses",
                icon = Icons.Rounded.Search,
                accentTint = skyTint
            ),
            ChatPromptSuggestion(
                id = "notes",
                title = "Draft structured study notes and key action items",
                icon = Icons.Rounded.EditNote,
                accentTint = violetTint
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Obsidian Emblem Pedestal
            val emblemShape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(emblemShape)
                    .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                    .drawBehind {
                        if (tokens.isDark) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        tokens.accentRose.copy(alpha = 0.22f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.minDimension * 0.68f
                            )
                        }
                    }
                    .border(
                        width = 0.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                tokens.borderStrong,
                                tokens.accentRose.copy(alpha = 0.36f),
                                tokens.borderSubtle
                            )
                        ),
                        shape = emblemShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_swiffle_logo),
                    contentDescription = "Swiffle AI",
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "How can I help with your files?",
                fontFamily = ClaudeSerif,
                fontSize = 25.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Attach a PDF, Word file, or photo for grounded summaries, table extraction, and Q&A.",
                fontFamily = ClaudeSans,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(26.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                suggestions.forEach { item ->
                    ObsidianPromptSuggestionCard(
                        item = item,
                        tokens = tokens,
                        onClick = { onPromptClick(item.title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ObsidianPromptSuggestionCard(
    item: ChatPromptSuggestion,
    tokens: HomeThemeTokens,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = SwiffleMotion.SpringSpatialFast,
        label = "prompt_suggestion_scale"
    )
    val cardShape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        tokens.borderStrong,
                        item.accentTint.copy(alpha = if (tokens.isDark) 0.24f else 0.18f),
                        tokens.borderSubtle
                    )
                ),
                shape = cardShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = tokens.textPrimary.copy(alpha = 0.14f)),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("chat_suggestion_${item.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(item.accentTint.copy(alpha = if (tokens.isDark) 0.15f else 0.11f))
                .border(
                    width = 0.7.dp,
                    color = item.accentTint.copy(alpha = if (tokens.isDark) 0.30f else 0.22f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.accentTint,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.title,
            fontFamily = ClaudeSans,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Right-aligned Obsidian Crimson Glass User Message Bubble.
 */
@Composable
internal fun ObsidianUserMessageBubble(
    message: SwiffleChatMessage,
    tokens: HomeThemeTokens
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 6.dp
    )
    val bubbleFill = if (tokens.isDark) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF2B1217), Color(0xFF1A0B0F))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF7B161B), Color(0xFF5D0F13))
        )
    }
    val bubbleBorder = if (tokens.isDark) {
        tokens.accentRose.copy(alpha = 0.36f)
    } else {
        Color(0xFF8C1D24).copy(alpha = 0.45f)
    }
    val bubbleTextColor = if (tokens.isDark) tokens.textPrimary else Color(0xFFFFF8F8)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 336.dp)
                .clip(bubbleShape)
                .background(bubbleFill)
                .border(0.8.dp, bubbleBorder, bubbleShape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (message.attachedPhotoUri != null) {
                AsyncImage(
                    model = message.attachedPhotoUri,
                    contentDescription = "Attached Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!message.attachedFileName.isNullOrBlank()) {
                val pillShape = RoundedCornerShape(10.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(pillShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.22f), pillShape)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = bubbleTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = message.attachedFileName,
                        fontFamily = ClaudeSans,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = bubbleTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = message.text,
                fontFamily = ClaudeSans,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = bubbleTextColor
            )
        }
    }
}

/**
 * Open Editorial Assistant Response without enclosing box clutter.
 * Renders Copy, Read Aloud, and Regenerate inline only for the latest completed non-error
 * response, while keeping earlier turns uncluttered with a subtle long-press utility menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ObsidianAssistantMessageBubble(
    message: SwiffleChatMessage,
    tokens: HomeThemeTokens,
    showInlineActions: Boolean,
    onToggleThinking: () -> Unit,
    onCopy: () -> Unit,
    onReadAloud: () -> Unit,
    onRegenerate: () -> Unit
) {
    var isCopied by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val canLongPressUtility = !showInlineActions &&
        !message.isStreaming &&
        !message.isThinking &&
        !message.isErrorNotice &&
        message.text.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (message.isThinking) {
            FluentCopilotThinkingIndicator(
                thoughtText = message.activePhaseText?.ifBlank { "Connecting to the Swiffle AI streaming service." }
                    ?: "Connecting to the Swiffle AI streaming service.",
                isDark = tokens.isDark
            )
        } else if (!message.thinkingText.isNullOrBlank()) {
            ObsidianReasoningExpander(
                durationSec = message.thinkingDurationSec ?: 1,
                thinkingText = message.thinkingText,
                isExpanded = message.isThinkingExpanded,
                onToggle = onToggleThinking,
                tokens = tokens
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (message.text.isNotEmpty()) {
            Box(
                modifier = if (canLongPressUtility) {
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { showLongPressMenu = true }
                        )
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                RichMarkdownBody(
                    text = message.text,
                    isDark = tokens.isDark,
                    textPrimary = tokens.textPrimary
                )

                if (canLongPressUtility) {
                    val menuShape = RoundedCornerShape(14.dp)
                    val menuBg = if (tokens.isDark) Color(0xFF151115) else tokens.surfaceElevatedTop
                    DropdownMenu(
                        expanded = showLongPressMenu,
                        onDismissRequest = { showLongPressMenu = false },
                        modifier = Modifier
                            .background(menuBg, menuShape)
                            .border(0.8.dp, tokens.borderStrong, menuShape)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Copy",
                                    fontFamily = ClaudeSans,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.textPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    tint = tokens.textSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            onClick = {
                                showLongPressMenu = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Read aloud",
                                    fontFamily = ClaudeSans,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.textPrimary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = tokens.textSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            onClick = {
                                showLongPressMenu = false
                                onReadAloud()
                            }
                        )
                    }
                }
            }
        }

        if (showInlineActions &&
            !message.isStreaming &&
            !message.isThinking &&
            !message.isErrorNotice &&
            message.text.isNotEmpty()
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onCopy()
                        isCopied = true
                        coroutineScope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_copy_button_${message.id}")
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy response",
                        tint = if (isCopied) {
                            if (tokens.isDark) Color(0xFF56C288) else Color(0xFF107C41)
                        } else {
                            tokens.textMuted
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onReadAloud,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_tts_button_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Read aloud",
                        tint = tokens.textMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onRegenerate,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_regenerate_button_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Regenerate response",
                        tint = tokens.textMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact Orbital Thinking Indicator with Small Swiffle SVG Inside.
 * Kept public with stable signature so AskSwiffleBottomSheet can reuse it directly.
 */
@Composable
fun FluentCopilotThinkingIndicator(
    thoughtText: String = "Thinking...",
    isDark: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GeminiAiStudioThinking")

    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GeminiOrbitRotation"
    )

    val arcSweep by infiniteTransition.animateFloat(
        initialValue = 75f,
        targetValue = 265f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GeminiArcSweep"
    )

    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SwiffleLogoPulse"
    )

    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MonochromeTextShimmerProgress"
    )

    var measuredTextWidthPx by remember { mutableFloatStateOf(0f) }

    val ringTone = if (isDark) Color(0xFFF4A9A8) else Color(0xFF800000)
    val ringTrackColor = ringTone.copy(alpha = if (isDark) 0.16f else 0.12f)
    val ringColors = remember(isDark) {
        listOf(
            ringTone.copy(alpha = 0.08f),
            ringTone.copy(alpha = 0.45f),
            ringTone.copy(alpha = 0.92f),
            ringTone.copy(alpha = 0.08f)
        )
    }

    val baseTextColor = if (isDark) Color(0xFF9F898B) else Color(0xFF71717A)
    val highlightTextColor = if (isDark) Color(0xFFF7EFEF) else Color(0xFF1C1B1B)

    val effectiveWidthPx = measuredTextWidthPx.coerceAtLeast(360f)
    val shimmerBandWidthPx = (effectiveWidthPx * 0.42f).coerceAtLeast(180f)
    val shimmerStartX = -shimmerBandWidthPx + (effectiveWidthPx + shimmerBandWidthPx * 2f) * shimmerProgress
    val shimmerEndX = shimmerStartX + shimmerBandWidthPx

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseTextColor, highlightTextColor, baseTextColor),
        start = Offset(shimmerStartX, 0f),
        end = Offset(shimmerEndX, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 1.5.dp.toPx()
                val radius = (size.minDimension - strokeWidthPx) / 2f

                drawCircle(
                    color = ringTrackColor,
                    radius = radius,
                    style = Stroke(width = 1.0.dp.toPx())
                )

                rotate(degrees = orbitRotation) {
                    drawArc(
                        brush = Brush.sweepGradient(ringColors),
                        startAngle = 0f,
                        sweepAngle = arcSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }

            Image(
                painter = painterResource(id = R.drawable.ic_swiffle_logo),
                contentDescription = "Swiffle AI Thinking",
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (thoughtText.isNotBlank()) thoughtText else "Thinking...",
            fontFamily = ClaudeSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(brush = shimmerBrush),
            onTextLayout = { layoutResult ->
                val lineEnd = if (layoutResult.lineCount > 0) {
                    layoutResult.getLineRight(0)
                } else {
                    layoutResult.size.width.toFloat()
                }
                if (lineEnd > 0f) {
                    measuredTextWidthPx = lineEnd
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ObsidianReasoningExpander(
    durationSec: Int,
    thinkingText: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    tokens: HomeThemeTokens
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(FluentNormalDuration, easing = FluentDecelerate),
        label = "ReasoningChevronRotation"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        val pillShape = RoundedCornerShape(999.dp)
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    if (tokens.isDark) Color.White.copy(alpha = 0.05f)
                    else Color.Black.copy(alpha = 0.04f)
                )
                .border(0.7.dp, tokens.borderSubtle, pillShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = tokens.textPrimary.copy(alpha = 0.14f)),
                    onClick = onToggle
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Thought for ${durationSec}s",
                fontFamily = ClaudeSans,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.textSecondary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse reasoning" else "Expand reasoning",
                tint = tokens.textMuted,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(FluentNormalDuration, easing = FluentDecelerate)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(FluentFastDuration, easing = FluentAccelerate)) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .defaultMinSize(minHeight = 20.dp)
                        .background(tokens.accentRose.copy(alpha = 0.55f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = thinkingText,
                    fontFamily = ClaudeSans,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = tokens.textSecondary
                )
            }
        }
    }
}

/**
 * Obsidian Floating Glass Composer Dock with Attachments, Voice Input, and Mild-White Glass Send CTA.
 */
@Composable
internal fun ObsidianChatInputDock(
    query: String,
    tokens: HomeThemeTokens,
    isGenerating: Boolean,
    attachedFileName: String?,
    attachedPhotoUri: Uri?,
    isExtractingAttachment: Boolean,
    onRemoveAttachment: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit,
    onUploadFiles: () -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }

    val dockShape = RoundedCornerShape(26.dp)
    val dockFill = remember(tokens.isDark) {
        if (tokens.isDark) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF141416),
                    Color(0xFF0E0E10)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFF5F5F7)
                )
            )
        }
    }
    val dockBorder = remember(tokens, isInputFocused) {
        if (isInputFocused) {
            Brush.linearGradient(
                colors = listOf(
                    if (tokens.isDark) Color.White.copy(alpha = 0.36f) else Color.Black.copy(alpha = 0.24f),
                    if (tokens.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    if (tokens.isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f),
                    if (tokens.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(
                elevation = if (tokens.isDark) 16.dp else 8.dp,
                shape = dockShape,
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.40f)
            )
            .clip(dockShape)
            .background(dockFill)
            .border(width = 0.9.dp, brush = dockBorder, shape = dockShape)
            .padding(vertical = 4.dp)
    ) {
        // Attachment flyout chips
        AnimatedVisibility(
            visible = showAttachmentMenu,
            enter = expandVertically(animationSpec = tween(FluentFastDuration)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(FluentFastDuration)) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ObsidianAttachmentChip(
                    icon = Icons.Rounded.Description,
                    label = "Document",
                    tokens = tokens,
                    testTag = "chat_attach_doc",
                    onClick = {
                        showAttachmentMenu = false
                        onUploadFiles()
                    }
                )
                ObsidianAttachmentChip(
                    icon = Icons.Rounded.PhotoLibrary,
                    label = "Gallery",
                    tokens = tokens,
                    testTag = "chat_attach_gallery",
                    onClick = {
                        showAttachmentMenu = false
                        onPhotos()
                    }
                )
                ObsidianAttachmentChip(
                    icon = Icons.Rounded.PhotoCamera,
                    label = "Camera",
                    tokens = tokens,
                    testTag = "chat_attach_camera",
                    onClick = {
                        showAttachmentMenu = false
                        onCamera()
                    }
                )
            }
        }

        // Attachment Extraction Status / Attached Preview Pill
        if (isExtractingAttachment) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = tokens.textPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reading attachment content...",
                    fontFamily = ClaudeSans,
                    fontSize = 12.sp,
                    color = tokens.textSecondary
                )
            }
        } else if (attachedFileName != null || attachedPhotoUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pillShape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(
                            if (tokens.isDark) Color.White.copy(alpha = 0.08f)
                            else Color.Black.copy(alpha = 0.05f)
                        )
                        .border(0.7.dp, tokens.borderStrong, pillShape)
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (attachedPhotoUri != null) {
                        AsyncImage(
                            model = attachedPhotoUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Photo attached",
                            fontFamily = ClaudeSans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = attachedFileName ?: "Document",
                            fontFamily = ClaudeSans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                    }

                    IconButton(
                        onClick = onRemoveAttachment,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove attachment",
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Main Composer Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = { showAttachmentMenu = !showAttachmentMenu },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_attach_button")
            ) {
                Icon(
                    imageVector = if (showAttachmentMenu) Icons.Rounded.Close else Icons.Rounded.Add,
                    contentDescription = if (showAttachmentMenu) "Close attachments" else "Attach file or photo",
                    tint = if (showAttachmentMenu) tokens.textPrimary else tokens.textSecondary,
                    modifier = Modifier.size(21.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isInputFocused = it.isFocused }
                        .testTag("chat_query_input"),
                    maxLines = 5,
                    textStyle = TextStyle(
                        fontFamily = ClaudeSans,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = tokens.textPrimary
                    ),
                    cursorBrush = SolidColor(tokens.textPrimary),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = if (attachedFileName != null || attachedPhotoUri != null) {
                                    "Ask about this attachment..."
                                } else {
                                    "Ask Swiffle AI anything..."
                                },
                                fontFamily = ClaudeSans,
                                fontSize = 15.sp,
                                color = tokens.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                )
            }

            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_mic_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Voice prompt",
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Send / Stop CTA aligned at bottom of 48dp slot
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (tokens.isDark) Color.White.copy(alpha = 0.14f)
                                else Color.Black.copy(alpha = 0.08f)
                            )
                            .border(
                                0.9.dp,
                                if (tokens.isDark) Color.White.copy(alpha = 0.34f)
                                else Color.Black.copy(alpha = 0.22f),
                                CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, color = tokens.textPrimary),
                                onClick = onStop
                            )
                            .testTag("chat_stop_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(tokens.textPrimary)
                        )
                    }
                } else {
                    val isEnabled = query.trim().isNotEmpty() || attachedFileName != null || attachedPhotoUri != null
                    val sendFill = if (!isEnabled) {
                        Brush.linearGradient(
                            listOf(
                                if (tokens.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f),
                                if (tokens.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f)
                            )
                        )
                    } else if (tokens.isDark) {
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.26f),
                                Color.White.copy(alpha = 0.14f)
                            )
                        )
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF18181B), Color(0xFF27272A)))
                    }

                    val sendBorder = if (!isEnabled) {
                        tokens.borderSubtle
                    } else if (tokens.isDark) {
                        Color.White.copy(alpha = 0.44f)
                    } else {
                        Color(0xFF18181B)
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(sendFill)
                            .border(0.9.dp, sendBorder, CircleShape)
                            .then(
                                if (isEnabled) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false, color = Color.White.copy(alpha = 0.28f)),
                                        onClick = onSend
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("chat_send_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Send message",
                            tint = when {
                                !isEnabled -> tokens.textMuted
                                tokens.isDark -> tokens.textPrimary
                                else -> Color.White
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianAttachmentChip(
    icon: ImageVector,
    label: String,
    tokens: HomeThemeTokens,
    testTag: String,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(chipShape)
            .background(
                if (tokens.isDark) Color.White.copy(alpha = 0.06f)
                else Color.Black.copy(alpha = 0.04f)
            )
            .border(0.8.dp, tokens.borderSubtle, chipShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = tokens.textPrimary.copy(alpha = 0.14f)),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tokens.textPrimary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = ClaudeSans,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.textPrimary
        )
    }
}

@Immutable
internal data class ChatHistorySessionItem(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtMs: Long,
    val messageCount: Int,
    val isCurrent: Boolean
)

/**
 * Obsidian Glass Modal Sheet displaying live Gemini Free Tier Rate Limits (RPM / TPM / RPD),
 * actual Input/Output/Thought token usage, and per-model quotas from OrigamiPrefs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObsidianUsageLimitsSheet(
    snapshot: UsageRateSnapshot,
    tokens: HomeThemeTokens,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (tokens.isDark) Color(0xFF100E11) else tokens.surfaceElevatedTop
    val numberFmt = remember { NumberFormat.getIntegerInstance() }
    val tokenProgress = (snapshot.totalTokensToday.toFloat() / snapshot.dailyTokenQuota.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    val remainingTokens = (snapshot.dailyTokenQuota - snapshot.totalTokensToday).coerceAtLeast(0)
    val rpmProgress = (snapshot.currentRpm.toFloat() / snapshot.rpmLimit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val tpmProgress = (snapshot.currentTpm.toFloat() / snapshot.tpmLimit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val rpdProgress = (snapshot.currentRpd.toFloat() / snapshot.rpdLimit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val scrollState = rememberScrollState()
    val statusGreen = if (tokens.isDark) Color(0xFF56C288) else Color(0xFF107C41)
    val warnAmber = if (tokens.isDark) Color(0xFFF5B041) else Color(0xFFD97706)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(tokens.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE RATE & TELEMETRY",
                        fontFamily = ClaudeSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        color = tokens.accentRose
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Usage & Limits",
                        fontFamily = ClaudeSerif,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            (if (snapshot.cooldownRemainingSec > 0) warnAmber else statusGreen)
                                .copy(alpha = 0.16f)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (snapshot.cooldownRemainingSec > 0) {
                            "Cooldown ${snapshot.cooldownRemainingSec}s"
                        } else if (snapshot.hasGeminiKey) {
                            "Gemini Free"
                        } else {
                            "Fallback"
                        },
                        fontFamily = ClaudeSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (snapshot.cooldownRemainingSec > 0) warnAmber else statusGreen
                    )
                }
            }

            if (!snapshot.lastRateLimitMessage.isNullOrBlank()) {
                val warnShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(warnShape)
                        .background(warnAmber.copy(alpha = 0.14f))
                        .border(0.7.dp, warnAmber.copy(alpha = 0.35f), warnShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = snapshot.lastRateLimitMessage.orEmpty(),
                        fontFamily = ClaudeSans,
                        fontSize = 12.sp,
                        color = warnAmber
                    )
                }
            }

            val cardShape = RoundedCornerShape(20.dp)

            // Live Rate Limits Card (RPM / TPM / RPD)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                    .border(0.8.dp, tokens.borderSubtle, cardShape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = snapshot.activeModelDisplayName,
                        fontFamily = ClaudeSans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = snapshot.keySourceLabel,
                        fontFamily = ClaudeSans,
                        fontSize = 11.5.sp,
                        color = tokens.textMuted
                    )
                }

                SheetRateRow(
                    label = "Requests / Min (RPM)",
                    value = "${numberFmt.format(snapshot.currentRpm)} / ${numberFmt.format(snapshot.rpmLimit)}",
                    progress = rpmProgress,
                    barColor = Color(0xFF4285F4),
                    tokens = tokens
                )
                SheetRateRow(
                    label = "Tokens / Min (TPM)",
                    value = "${numberFmt.format(snapshot.currentTpm)} / ${numberFmt.format(snapshot.tpmLimit)}",
                    progress = tpmProgress,
                    barColor = statusGreen,
                    tokens = tokens
                )
                SheetRateRow(
                    label = "Requests / Day (RPD)",
                    value = "${numberFmt.format(snapshot.currentRpd)} / ${numberFmt.format(snapshot.rpdLimit)}",
                    progress = rpdProgress,
                    barColor = Color(0xFF9B72CB),
                    tokens = tokens
                )
            }

            // Daily Token Allowance & Breakdown Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                    .border(0.8.dp, tokens.borderSubtle, cardShape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Token Usage",
                        fontFamily = ClaudeSans,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textSecondary
                    )
                    Text(
                        text = "${(tokenProgress * 100).toInt()}% used",
                        fontFamily = ClaudeSans,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.accentRose
                    )
                }

                Text(
                    text = "${numberFmt.format(snapshot.totalTokensToday)} / ${numberFmt.format(snapshot.dailyTokenQuota)}",
                    fontFamily = ClaudeSerif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )

                LinearProgressIndicator(
                    progress = { tokenProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = tokens.accentRose,
                    trackColor = tokens.borderSubtle
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "In: ${numberFmt.format(snapshot.promptTokensToday)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = tokens.textSecondary
                    )
                    Text(
                        text = "Out: ${numberFmt.format(snapshot.outputTokensToday)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = tokens.textSecondary
                    )
                    Text(
                        text = "Thought: ${numberFmt.format(snapshot.thoughtTokensToday)}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = tokens.textSecondary
                    )
                }

                Text(
                    text = "${numberFmt.format(remainingTokens)} tokens remaining today.",
                    fontFamily = ClaudeSans,
                    fontSize = 12.sp,
                    color = tokens.textMuted
                )
            }

            // Queries, Latency & Activity Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(cardShape)
                        .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                        .border(0.8.dp, tokens.borderSubtle, cardShape)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Queries Today",
                        fontFamily = ClaudeSans,
                        fontSize = 12.sp,
                        color = tokens.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = numberFmt.format(snapshot.queriesToday),
                        fontFamily = ClaudeSerif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(cardShape)
                        .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                        .border(0.8.dp, tokens.borderSubtle, cardShape)
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (snapshot.lastLatencyMs > 0L) "Latency • Activity" else "Last Activity",
                        fontFamily = ClaudeSans,
                        fontSize = 12.sp,
                        color = tokens.textMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (snapshot.lastLatencyMs > 0L) {
                            "${snapshot.lastLatencyMs}ms • ${formatRelativeTimeLabel(snapshot.lastQueryTimestampMs)}"
                        } else {
                            formatRelativeTimeLabel(snapshot.lastQueryTimestampMs)
                        },
                        fontFamily = ClaudeSans,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Per-Model Gemini Free Tier Breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                    .border(0.8.dp, tokens.borderSubtle, cardShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Free Tier Models (${snapshot.activeProviderLabel})",
                    fontFamily = ClaudeSans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.textSecondary
                )
                snapshot.perModelStats.forEachIndexed { idx, stat ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stat.displayName,
                                fontFamily = ClaudeSans,
                                fontSize = 13.sp,
                                fontWeight = if (stat.modelId == snapshot.activeModelId) FontWeight.Bold else FontWeight.Medium,
                                color = tokens.textPrimary
                            )
                            Text(
                                text = if (stat.cooldownRemainingSec > 0) {
                                    "Cooldown ${stat.cooldownRemainingSec}s"
                                } else {
                                    "${stat.requestsToday}/${stat.rpdLimit} RPD"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = if (stat.cooldownRemainingSec > 0) warnAmber else tokens.textSecondary
                            )
                        }
                        Text(
                            text = "${stat.requestsLastMinute}/${stat.rpmLimit} RPM • ${numberFmt.format(stat.tokensLastMinute)}/${numberFmt.format(stat.tpmLimit)} TPM • ${numberFmt.format(stat.tokensToday)} tok today",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = tokens.textMuted
                        )
                    }
                    if (idx < snapshot.perModelStats.lastIndex) {
                        HorizontalDivider(thickness = 0.6.dp, color = tokens.borderSubtle)
                    }
                }
            }

            // Close Action
            val closeShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(closeShape)
                    .background(
                        if (tokens.isDark) Color.White.copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.06f)
                    )
                    .border(0.8.dp, tokens.borderSubtle, closeShape)
                    .clickable(onClick = onDismiss)
                    .testTag("usage_limits_close_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    fontFamily = ClaudeSans,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetRateRow(
    label: String,
    value: String,
    progress: Float,
    barColor: Color,
    tokens: HomeThemeTokens
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontFamily = ClaudeSans,
                fontSize = 12.sp,
                color = tokens.textMuted
            )
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = barColor,
            trackColor = tokens.borderSubtle
        )
    }
}

/**
 * Obsidian Glass Modal Sheet listing saved Swiffle AI conversations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObsidianChatHistorySheet(
    sessions: List<ChatHistorySessionItem>,
    tokens: HomeThemeTokens,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartNewChat: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (tokens.isDark) Color(0xFF100E11) else tokens.surfaceElevatedTop

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(tokens.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CONVERSATIONS",
                        fontFamily = ClaudeSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        color = tokens.accentRose
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chat History",
                        fontFamily = ClaudeSerif,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                }

                val pillShape = RoundedCornerShape(999.dp)
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(pillShape)
                        .background(tokens.accentRose.copy(alpha = if (tokens.isDark) 0.16f else 0.10f))
                        .border(0.8.dp, tokens.accentRose.copy(alpha = 0.34f), pillShape)
                        .clickable(onClick = {
                            onDismiss()
                            onStartNewChat()
                        })
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        tint = tokens.accentRose,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New Chat",
                        fontFamily = ClaudeSans,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (sessions.isEmpty()) {
                val emptyShape = RoundedCornerShape(20.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(emptyShape)
                        .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                        .border(0.8.dp, tokens.borderSubtle, emptyShape)
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = tokens.textMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No saved conversations yet",
                        fontFamily = ClaudeSerif,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your Swiffle AI chats are saved automatically as you ask questions.",
                        fontFamily = ClaudeSans,
                        fontSize = 12.5.sp,
                        color = tokens.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val itemShape = RoundedCornerShape(18.dp)
                        val borderColor = if (session.isCurrent) {
                            tokens.accentRose.copy(alpha = 0.45f)
                        } else {
                            tokens.borderSubtle
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                                .border(0.8.dp, borderColor, itemShape)
                                .clickable {
                                    onSelectSession(session.id)
                                    onDismiss()
                                }
                                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
                                .testTag("chat_history_item_${session.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = session.title,
                                        fontFamily = ClaudeSans,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (session.isCurrent) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Active",
                                            fontFamily = ClaudeSans,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.accentRose
                                        )
                                    }
                                }
                                if (session.preview.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = session.preview,
                                        fontFamily = ClaudeSans,
                                        fontSize = 12.sp,
                                        color = tokens.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${session.messageCount} messages • ${formatHistoryTimestamp(session.updatedAtMs)}",
                                    fontFamily = ClaudeSans,
                                    fontSize = 11.sp,
                                    color = tokens.textMuted
                                )
                            }

                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_history_delete_${session.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete conversation",
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun formatRelativeTimeLabel(timestampMs: Long): String {
    if (timestampMs <= 0L) return "No queries yet"
    val deltaMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = deltaMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "$minutes min ago"
        hours < 24L -> "$hours hr ago"
        else -> "$days d ago"
    }
}

private fun formatHistoryTimestamp(timestampMs: Long): String {
    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return fmt.format(Date(timestampMs))
}
