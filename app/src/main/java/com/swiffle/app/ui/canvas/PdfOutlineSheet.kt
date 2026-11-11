package com.swiffle.app.ui.canvas

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.swiffle.app.theme.GoogleSans

/**
 * Pure Material 3 Pro Interactive Thumbnail Grid & Outline Sheet
 * - Native Material 3 Surface & Color Scheme tokens
 * - Expressive Segmented Tabs (Pages vs Bookmarks)
 * - Elevated Squircle Page Cards with M3 Selection States & Tonal Badges
 * - Smooth Drag Gesture Dismissal & Fullscreen Toggle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOutlineSheet(
    visible: Boolean,
    pageCount: Int,
    currentPage: Int,
    pageAspectRatio: Float,
    isDark: Boolean,
    thumbBitmaps: Map<Int, Bitmap>,
    bookmarkedPages: List<Int>,
    pageDimensions: List<Pair<Float, Float>> = emptyList(),
    pageCrops: Map<Int, PageCrop> = emptyMap(),
    isSmartCropEnabled: Boolean = true,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRequestThumbnail: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember { mutableIntStateOf(0) } // 0=Pages, 1=Bookmarks

    // Google/OpenAI Pro Pattern: SINGLETON Shared Shimmer Clock for entire grid!
    // Never instantiate rememberInfiniteTransition inside LazyGrid items!
    val sharedTransition = rememberInfiniteTransition(label = "shared_thumb_shimmer")
    val sharedShimmerX by sharedTransition.animateFloat(
        initialValue = -150f,
        targetValue = 350f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shared_shimmer_x"
    )

    // Windows 11 Fluent WinUI 3 Tokens
    val sheetBg = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val sheetBorder = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
    val primaryAccent = Color(0xFF0078D4)
    val primaryContainer = Color(0xFF0078D4).copy(alpha = 0.15f)
    val onPrimaryContainer = Color(0xFF0078D4)
    val surfaceContainerHigh = if (isDark) Color(0xFF141414) else Color(0xFFF3F3F3)
    val menuText = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val textSecondary = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)
    val density = LocalDensity.current

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val cutoutStart = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) }
    val cutoutEnd = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = if (isLandscape) 820.dp else 1200.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                    // Native M3 Header with Segmented Tabs & Action Controls (Centered)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (isLandscape) 20.dp + cutoutStart else 20.dp,
                                end = if (isLandscape) 16.dp + cutoutEnd else 16.dp,
                                top = 4.dp,
                                bottom = 10.dp
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Native M3 Segmented Tabs
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = surfaceContainerHigh,
                            border = BorderStroke(1.dp, sheetBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pages Tab
                                Surface(
                                    shape = RoundedCornerShape(17.dp),
                                    color = if (activeTab == 0) primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(17.dp))
                                        .clickable { activeTab = 0 }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.GridView,
                                            contentDescription = null,
                                            tint = if (activeTab == 0) onPrimaryContainer else textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Pages ($pageCount)",
                                            fontFamily = GoogleSans,
                                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (activeTab == 0) onPrimaryContainer else textSecondary
                                        )
                                    }
                                }

                                // Bookmarks Tab
                                Surface(
                                    shape = RoundedCornerShape(17.dp),
                                    color = if (activeTab == 1) primaryContainer else Color.Transparent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(17.dp))
                                        .clickable { activeTab = 1 }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (bookmarkedPages.isNotEmpty()) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = null,
                                            tint = if (activeTab == 1) onPrimaryContainer else textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Bookmarks (${bookmarkedPages.size})",
                                            fontFamily = GoogleSans,
                                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (activeTab == 1) onPrimaryContainer else textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Right Action area - tabs fill cleanly without an X button
                    }

                    HorizontalDivider(color = sheetBorder)

                    val navBarBottom = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() }

                    if (activeTab == 0) {
                        // All Pages M3 Grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = if (isLandscape) 96.dp else 112.dp),
                            contentPadding = PaddingValues(
                                start = if (isLandscape) 18.dp + cutoutStart else 18.dp,
                                end = if (isLandscape) 18.dp + cutoutEnd else 18.dp,
                                top = 16.dp,
                                bottom = 24.dp + navBarBottom
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(pageCount) { i ->
                                val isActive = (i + 1 == currentPage)
                                val cardBorder = if (isActive) {
                                    BorderStroke(2.5.dp, primaryAccent)
                                } else {
                                    BorderStroke(1.dp, sheetBorder)
                                }

                                LaunchedEffect(i) {
                                    onRequestThumbnail?.invoke(i)
                                }

                                val crop = if (isSmartCropEnabled) pageCrops[i] ?: pageCrops[0] ?: PageCrop() else PageCrop()
                                val itemRatio = pageDimensions.getOrNull(i)?.let {
                                    if (it.second > 0f) {
                                        if (crop.width > 0f && crop.height > 0f) {
                                            (it.first * crop.width) / (it.second * crop.height)
                                        } else {
                                            it.first / it.second
                                        }
                                    } else null
                                } ?: pageAspectRatio

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = cardBorder,
                                    shadowElevation = if (isActive) 6.dp else 2.dp,
                                    modifier = Modifier
                                        .aspectRatio(itemRatio.coerceIn(0.45f, 2.2f))
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            onSelectPage(i)
                                            onDismiss()
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val thumb = thumbBitmaps[i]
                                        if (thumb != null && !thumb.isRecycled) {
                                            Image(
                                                bitmap = thumb.asImageBitmap(),
                                                contentDescription = "Page ${i + 1}",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            // M3 Shimmering Skeleton Thumbnail Placeholder (Driven by Shared Singleton Clock)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(
                                                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                                                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                                            ),
                                                            start = Offset(sharedShimmerX, 0f),
                                                            end = Offset(sharedShimmerX + 100f, 150f)
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "${i + 1}",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp,
                                                    fontFamily = GoogleSans
                                                )
                                            }
                                        }

                                        // M3 Badge Tag (Bottom Right)
                                        Surface(
                                            shape = RoundedCornerShape(topStart = 8.dp),
                                            color = if (isActive) primaryAccent else Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                "${i + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                fontFamily = GoogleSans
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Bookmarks Tab
                        if (bookmarkedPages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = navBarBottom),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = surfaceContainerHigh,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.BookmarkBorder,
                                                contentDescription = null,
                                                tint = textSecondary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "No Bookmarked Pages",
                                        color = menuText,
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Tap the bookmark icon in reader to save pages here",
                                        color = textSecondary,
                                        fontFamily = GoogleSans,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = if (isLandscape) 96.dp else 112.dp),
                                contentPadding = PaddingValues(
                                    start = if (isLandscape) 18.dp + cutoutStart else 18.dp,
                                    end = if (isLandscape) 18.dp + cutoutEnd else 18.dp,
                                    top = 16.dp,
                                    bottom = 24.dp + navBarBottom
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(bookmarkedPages) { pageIdx ->
                                    val isActive = (pageIdx + 1 == currentPage)
                                    val cardBorder = if (isActive) {
                                        BorderStroke(2.5.dp, primaryAccent)
                                    } else {
                                        BorderStroke(1.dp, sheetBorder)
                                    }

                                    LaunchedEffect(pageIdx) {
                                        onRequestThumbnail?.invoke(pageIdx)
                                    }

                                    val crop = if (isSmartCropEnabled) pageCrops[pageIdx] ?: pageCrops[0] ?: PageCrop() else PageCrop()
                                    val bookmarkedRatio = pageDimensions.getOrNull(pageIdx)?.let {
                                        if (it.second > 0f) {
                                            if (crop.width > 0f && crop.height > 0f) {
                                                (it.first * crop.width) / (it.second * crop.height)
                                            } else {
                                                it.first / it.second
                                            }
                                        } else null
                                    } ?: pageAspectRatio

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = cardBorder,
                                        shadowElevation = if (isActive) 6.dp else 2.dp,
                                        modifier = Modifier
                                            .aspectRatio(bookmarkedRatio.coerceIn(0.45f, 2.2f))
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                onSelectPage(pageIdx)
                                                onDismiss()
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val thumb = thumbBitmaps[pageIdx]
                                            if (thumb != null && !thumb.isRecycled) {
                                                Image(
                                                    bitmap = thumb.asImageBitmap(),
                                                    contentDescription = "Page ${pageIdx + 1}",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                // M3 Shimmering Skeleton Thumbnail Placeholder (Driven by Shared Singleton Clock)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.linearGradient(
                                                                colors = listOf(
                                                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                                                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                                                ),
                                                                start = Offset(sharedShimmerX, 0f),
                                                                end = Offset(sharedShimmerX + 100f, 150f)
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "${pageIdx + 1}",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 17.sp,
                                                        fontFamily = GoogleSans
                                                    )
                                                }
                                            }

                                            // Bookmark Ribbon Icon (Top Left)
                                            Surface(
                                                shape = CircleShape,
                                                color = primaryContainer,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                                    .size(22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Bookmark,
                                                        contentDescription = null,
                                                        tint = onPrimaryContainer,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }

                                            // M3 Badge Tag (Bottom Right)
                                            Surface(
                                                shape = RoundedCornerShape(topStart = 8.dp),
                                                color = if (isActive) primaryAccent else Color.Black.copy(alpha = 0.65f),
                                                modifier = Modifier.align(Alignment.BottomEnd)
                                            ) {
                                                Text(
                                                    "${pageIdx + 1}",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    fontFamily = GoogleSans
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
