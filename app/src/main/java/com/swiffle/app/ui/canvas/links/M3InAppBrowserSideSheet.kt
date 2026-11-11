package com.swiffle.app.ui.canvas.links

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.swiffle.app.theme.GoogleSans
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Official Google Material Design 3 Emphasized Motion Easing Curves
private val M3EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val M3EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

/**
 * Enterprise Material Design 3 (M3) In-App Multi-Tab Browser System.
 *
 * Highlights:
 * 1. Cinematic M3 Slide-In Entrance: 420ms Emphasized Decelerate slide from outside the right screen edge.
 * 2. Uninhibited Smooth 120Hz WebView Scrolling: No gesture listener wrapping the WebView.
 *    Horizontal swipe-to-minimize is strictly attached to the floating capsule handle and top header.
 * 3. Compact Floating Edge Orbs: Minimized tags are ultra-compact 48dp discs that hug the right rail,
 *    never blocking the PDF.
 * 4. Free Vertical Dragging: Users can freely move the docked tags rail up and down.
 * 5. Top Bar Alignment: Top edge begins flush right beneath the PDF Top Header Toolbar.
 */
@Composable
fun M3MultiTabBrowserOverlay(
    tabManager: BrowserTabManager,
    topBarVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val activeTab = tabManager.activeTab
    val density = LocalDensity.current

    // Calculate top bar baseline offset so sheet begins right below the PDF top header
    val statusBarTop: Dp = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding() }
    val topHeaderOffsetDp = if (topBarVisible) statusBarTop + 64.dp else statusBarTop

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Right-Rail Movable Compact Docked Orbs (Tabs minimized or in background)
        val minimizedTabs = tabManager.tabs.filter { it.id != activeTab?.id }
        if (minimizedTabs.isNotEmpty()) {
            CompactMovableDockedTagsRail(
                tabs = minimizedTabs,
                topBoundaryDp = topHeaderOffsetDp + 8.dp,
                onTabClick = { tabId -> tabManager.activateTab(tabId) },
                onTabClose = { tabId -> tabManager.closeTab(tabId) },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // 2. Active Side Sheet (with smooth entrance animation and isolated touch handling)
        if (activeTab != null) {
            M3BrowserSideSheetContent(
                session = activeTab,
                topOffsetDp = topHeaderOffsetDp,
                onMinimize = { tabManager.minimizeActiveTab() },
                onClose = { tabManager.closeTab(activeTab.id) }
            )
        }
    }
}

/**
 * Active In-App Browser Side Sheet Content.
 */
@Composable
private fun M3BrowserSideSheetContent(
    session: BrowserTabSession,
    topOffsetDp: Dp,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Smooth horizontal swipe offset (starts off-screen for a satisfying slide-in)
    val dragOffsetX = remember(session.id) { Animatable(1400f) }

    // Intercept back presses: go back in WebView history first, else minimize into Tag
    BackHandler {
        if (session.webView.canGoBack()) {
            session.webView.goBack()
        } else {
            onMinimize()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topOffsetDp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        // 3.5 / 4th = 87.5% width
        val sheetWidthRatio = 0.875f
        val sheetWidthPx = totalWidthPx * sheetWidthRatio

        // Trigger satisfying cinematic slide-in from the right edge on appearance
        LaunchedEffect(session.id, sheetWidthPx) {
            dragOffsetX.snapTo(sheetWidthPx)
            dragOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 420, easing = M3EmphasizedDecelerate)
            )
        }

        // Shared draggable state for intentional swipe-to-dismiss areas (Handle & Header only)
        val handleDraggableState = rememberDraggableState { delta ->
            val next = (dragOffsetX.value + delta).coerceAtLeast(0f)
            scope.launch { dragOffsetX.snapTo(next) }
        }

        val onDragStoppedAction: suspend kotlinx.coroutines.CoroutineScope.(Float) -> Unit = { velocity ->
            val dismissThreshold = sheetWidthPx * 0.28f
            if (dragOffsetX.value > dismissThreshold || velocity > 800f) {
                dragOffsetX.animateTo(sheetWidthPx, tween(durationMillis = 240, easing = M3EmphasizedAccelerate))
                onMinimize()
            } else {
                dragOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 500f))
            }
        }

        // Dynamic Scrim over PDF (dims as sheet slides in)
        val currentOffset = dragOffsetX.value
        val scrimProgress = ((sheetWidthPx - currentOffset) / sheetWidthPx).coerceIn(0f, 1f)
        val scrimAlpha = scrimProgress * 0.35f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        scope.launch {
                            dragOffsetX.animateTo(sheetWidthPx, tween(220, easing = M3EmphasizedAccelerate))
                            onMinimize()
                        }
                    }
                )
        )

        // Trailing Side Sheet Container (NOTE: No draggable modifier here, preventing scroll theft!)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(sheetWidthRatio)
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
        ) {
            // Authentic M3 Floating Pill Drag Capsule Handle (Draggable!)
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-18).dp)
                    .size(width = 28.dp, height = 72.dp)
                    .draggable(
                        state = handleDraggableState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = onDragStoppedAction
                    )
                    .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(
                    0.75.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                            )
                        }
                    }
                }
            }

            // Sheet Body
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp)
                    .shadow(16.dp, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Chrome Header (Draggable for intentional top swipe-to-dismiss)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .draggable(
                                state = handleDraggableState,
                                orientation = Orientation.Horizontal,
                                onDragStopped = onDragStoppedAction
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minimize Affordance
                        IconButton(
                            onClick = {
                                scope.launch {
                                    dragOffsetX.animateTo(sheetWidthPx, tween(220, easing = M3EmphasizedAccelerate))
                                    onMinimize()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Minimize to Tag",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Web Host & Page Title
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (session.currentUrl.startsWith("https://", ignoreCase = true)) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Secure HTTPS",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = session.hostName.ifBlank { "Browser" },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        fontFamily = GoogleSans
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = session.pageTitle.ifBlank { session.currentUrl },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = GoogleSans
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Reload Button
                        IconButton(
                            onClick = { session.webView.reload() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Copy Link
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("URL", session.currentUrl)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy link",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Open in Chrome
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(session.currentUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in Chrome",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Loading Progress Indicator
                    val animatedProgress by animateFloatAsState(
                        targetValue = session.loadingProgress / 100f,
                        animationSpec = tween(durationMillis = 200),
                        label = "webProgress"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    ) {
                        if (session.isLoading && session.loadingProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        } else {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // 100% Native Unimpeded WebView (Zero gesture interception -> Smooth 120Hz scrolling!)
                    AndroidView(
                        factory = {
                            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
                            session.webView
                        },
                        update = { view ->
                            if (view.parent == null) {
                                (session.webView.parent as? ViewGroup)?.removeView(session.webView)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Vertically Movable Compact Docked Tags Rail.
 * Takes minimal horizontal space (~52dp), keeping the PDF completely legible!
 */
@Composable
private fun CompactMovableDockedTagsRail(
    tabs: List<BrowserTabSession>,
    topBoundaryDp: Dp,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var verticalOffsetY by remember { mutableFloatStateOf(0f) }

    val topBoundaryPx = with(density) { topBoundaryDp.toPx() }
    val bottomBoundaryPaddingPx = with(density) { 100.dp.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val maxAvailableHeightPx = constraints.maxHeight.toFloat()
        val minY = topBoundaryPx
        val maxY = (maxAvailableHeightPx - bottomBoundaryPaddingPx).coerceAtLeast(minY)

        val clampedY = (minY + verticalOffsetY).coerceIn(minY, maxY)

        Column(
            modifier = Modifier
                .offset { IntOffset(0, (clampedY - minY).roundToInt()) }
                .padding(top = topBoundaryDp, end = 4.dp)
                .width(52.dp)
                .draggable(
                    state = rememberDraggableState { deltaY ->
                        verticalOffsetY += deltaY
                    },
                    orientation = Orientation.Vertical
                ),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tabs.forEach { tab ->
                CompactEdgeOrbTag(
                    session = tab,
                    onClick = { onTabClick(tab.id) },
                    onClose = { onTabClose(tab.id) }
                )
            }
        }
    }
}

/**
 * Ultra-Compact Material Design 3 Edge Orb Tag.
 * Sized at 44dp x 44dp with 22dp pill curvature.
 * Docks flush to the right bezel, keeping the reading surface entirely unblocked.
 */
@Composable
private fun CompactEdgeOrbTag(
    session: BrowserTabSession,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 4.dp,
        label = "tagElevation"
    )

    Box(contentAlignment = Alignment.TopEnd) {
        // Main Docked Orb Pill
        Surface(
            modifier = Modifier
                .size(46.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .shadow(elevation, CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 5.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                val fav = session.favicon
                if (fav != null && !fav.isRecycled) {
                    Image(
                        bitmap = fav.asImageBitmap(),
                        contentDescription = session.hostName,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = session.hostName,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Discrete Close Dot Badge (Corner 'x')
        Box(
            modifier = Modifier
                .offset(x = 2.dp, y = (-2).dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(bounded = true, radius = 9.dp),
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Tab",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}
