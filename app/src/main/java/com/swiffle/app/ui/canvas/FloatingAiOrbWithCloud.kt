package com.swiffle.app.ui.canvas

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.swiffle.app.R
import com.swiffle.app.theme.GoogleSans
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material 3 Expressive Floating Draggable AI Mode Round & Suggestion Cloud.
 *
 * Features:
 * 1. Freely draggable 56dp circular AI orb with the Swiffle app logo and magnetic edge-snapping.
 * 2. Suggestion Cloud anchored to the orb with:
 *    - Document First Page Preview inside a small square thumbnail + Doc Name.
 *    - Mesmerizing 3-dot harmonic wave thinking animation.
 *    - 1-Line simple explanation, well-framed and highly accurate (expandable to multi-line).
 * 3. Scroll-Driven Auto-Shrink: Automatically shrinks back into the round logo when the user scrolls the canvas!
 */
@Composable
fun FloatingAiOrbWithCloud(
    modifier: Modifier = Modifier,
    docTitle: String,
    firstPageBitmap: Bitmap?,
    isDark: Boolean,
    isCanvasScrolling: Boolean,
    activePrompt: String? = null,
    thinkingStatus: String? = null,
    isThinking: Boolean = false,
    answerText: String? = null,
    onOrbClick: (() -> Unit)? = null,
    onOpenFullChat: () -> Unit,
    onDismissCloud: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val orbSizeDp = 46.dp
    val orbSizePx = with(density) { orbSizeDp.toPx() }

    // Initial position: Bottom right above the dock
    val initialX = screenWidthPx - orbSizePx - with(density) { 16.dp.toPx() }
    val initialY = screenHeightPx - orbSizePx - with(density) { 140.dp.toPx() }

    val animatableX = remember { Animatable(initialX) }
    val animatableY = remember { Animatable(initialY) }

    // Cloud visibility: User togglable + shrinks automatically on canvas scrolling!
    var isCloudVisible by remember { mutableStateOf(false) }
    var isAnswerExpanded by remember { mutableStateOf(false) }

    // Auto-reveal cloud when a query starts thinking or returns an answer
    LaunchedEffect(isThinking, answerText) {
        if (isThinking || !answerText.isNullOrBlank()) {
            isCloudVisible = true
        }
    }

    // Scroll-Driven Auto-Shrink: As soon as user scrolls the PDF canvas, shrink into the round!
    LaunchedEffect(isCanvasScrolling) {
        if (isCanvasScrolling && isCloudVisible) {
            isCloudVisible = false
            isAnswerExpanded = false
            onDismissCloud()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(99f)
    ) {
        val currentX = animatableX.value
        val currentY = animatableY.value

        // 1. Suggestion Cloud Card (Positioned relative to the round logo)
        val cloudWidthDp = 310.dp
        val cloudWidthPx = with(density) { cloudWidthDp.toPx() }

        // Determine if cloud should open to the left or right of orb based on screen position
        val openLeft = currentX > (screenWidthPx / 2f)
        val cloudTargetX = if (openLeft) {
            (currentX - cloudWidthPx + orbSizePx).coerceAtLeast(with(density) { 12.dp.toPx() })
        } else {
            currentX.coerceAtMost(screenWidthPx - cloudWidthPx - with(density) { 12.dp.toPx() })
        }
        val cloudTargetY = (currentY - with(density) { 160.dp.toPx() })
            .coerceIn(with(density) { 70.dp.toPx() }, screenHeightPx - with(density) { 260.dp.toPx() })

        Box(
            modifier = Modifier
                .offset { IntOffset(cloudTargetX.roundToInt(), cloudTargetY.roundToInt()) }
                .width(cloudWidthDp)
        ) {
            AnimatedVisibility(
                visible = isCloudVisible && (isThinking || !answerText.isNullOrBlank() || activePrompt != null),
                enter = scaleIn(
                    initialScale = 0.25f,
                    transformOrigin = if (openLeft) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(200)),
                exit = scaleOut(
                    targetScale = 0.2f,
                    transformOrigin = if (openLeft) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f),
                    animationSpec = tween(180, easing = FastOutLinearInEasing)
                ) + fadeOut(tween(140))
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isDark) Color(0xFF1E1C26).copy(alpha = 0.96f) else Color(0xFFF9F7FD).copy(alpha = 0.96f),
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, Color(0xFF9B72CB).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Header Row: Small Square Document Preview + Doc Name + Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Document First Page Preview Thumbnail inside Small Square
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (isDark) Color(0xFF2B2836) else Color(0xFFEDE9F7),
                                border = BorderStroke(0.8.dp, Color(0xFF9B72CB).copy(alpha = 0.4f)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (firstPageBitmap != null && !firstPageBitmap.isRecycled) {
                                    Image(
                                        bitmap = firstPageBitmap.asImageBitmap(),
                                        contentDescription = "Document Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Description,
                                            contentDescription = "Document",
                                            tint = Color(0xFF9B72CB),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = docTitle.ifBlank { "Active Document" },
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isDark) Color(0xFFF0ECFB) else Color(0xFF241F31)
                                )
                                Text(
                                    text = "Swiffle Intelligence",
                                    fontFamily = GoogleSans,
                                    fontSize = 10.sp,
                                    color = Color(0xFF9B72CB),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Close / Shrink Cloud Button
                            IconButton(
                                onClick = {
                                    isCloudVisible = false
                                    isAnswerExpanded = false
                                    onDismissCloud()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Shrink to round",
                                    tint = if (isDark) Color(0xFFA59FB6) else Color(0xFF6B6679),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Thinking State inside Cloud: Mesmerizing 3-Dot Harmonic Wave
                        if (isThinking) {
                            MaterialExpressiveThinkingIndicator(
                                thoughtText = thinkingStatus?.ifBlank { "Analyzing document..." } ?: "Analyzing document...",
                                isDark = isDark
                            )
                        } else if (!answerText.isNullOrBlank()) {
                            // Answer State: 1-Line Simple Explanation (Well Framed & Accurate)
                            Text(
                                text = answerText,
                                fontFamily = GoogleSans,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = if (isAnswerExpanded) 10 else 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isDark) Color(0xFFEBE6F5) else Color(0xFF221F2C),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isAnswerExpanded = !isAnswerExpanded }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action footer: Expand/Collapse & Open Full Sheet
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (answerText.length > 80 || answerText.contains("\n")) {
                                    Text(
                                        text = if (isAnswerExpanded) "Show less" else "Read more...",
                                        fontFamily = GoogleSans,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF9B72CB),
                                        modifier = Modifier.clickable { isAnswerExpanded = !isAnswerExpanded }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                TextButton(
                                    onClick = onOpenFullChat,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Open Full Chat",
                                        fontFamily = GoogleSans,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF9B72CB)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Filled.OpenInFull,
                                        contentDescription = null,
                                        tint = Color(0xFF9B72CB),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. The Floating Draggable Round Logo (AI Mode Round)
        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .size(orbSizeDp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            // Magnetic Snap to nearest screen edge (left or right)
                            val snapPadding = 16.dp.toPx()
                            val targetSnapX = if (animatableX.value + orbSizePx / 2f < screenWidthPx / 2f) {
                                snapPadding
                            } else {
                                screenWidthPx - orbSizePx - snapPadding
                            }
                            coroutineScope.launch {
                                animatableX.animateTo(
                                    targetValue = targetSnapX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val newX = (animatableX.value + dragAmount.x)
                            .coerceIn(0f, screenWidthPx - orbSizePx)
                        val newY = (animatableY.value + dragAmount.y)
                            .coerceIn(with(density) { 60.dp.toPx() }, screenHeightPx - orbSizePx - with(density) { 80.dp.toPx() })
                        coroutineScope.launch {
                            animatableX.snapTo(newX)
                            animatableY.snapTo(newY)
                        }
                    }
                }
                .clickable {
                    // Tapping the round toggles the suggestion cloud or opens chat!
                    if (onOrbClick != null) {
                        onOrbClick.invoke()
                    } else if (isThinking || !answerText.isNullOrBlank()) {
                        isCloudVisible = !isCloudVisible
                    } else {
                        onOpenFullChat()
                    }
                }
        ) {
            // Elevated Circular Surface with Swiffle App Logo (compact & neutral, no color shift)
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF202024) else Color(0xFFFFFFFF),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDark) Color(0x33FFFFFF) else Color(0x1F000000)
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_swiffle_logo),
                        contentDescription = "Swiffle AI Mode",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
