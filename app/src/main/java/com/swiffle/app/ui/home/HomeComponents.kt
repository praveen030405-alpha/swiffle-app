package com.swiffle.app.ui.home

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swiffle.app.theme.ClaudeSans
import com.swiffle.app.theme.ClaudeSerif
import com.swiffle.app.theme.SwiffleMotion
import com.swiffle.app.ui.components.MaroonAccentRose
import com.swiffle.app.ui.components.MaroonBlushSecondary
import com.swiffle.app.ui.components.MaroonIvoryText
import com.swiffle.app.ui.components.PdfThumbnailImage
import com.swiffle.app.ui.convert.FluentIcons
import java.io.File

@Immutable
data class HomeThemeTokens(
    val isDark: Boolean,
    val bgVoid: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val surfaceElevatedTop: Color,
    val surfaceElevatedBottom: Color,
    val borderStrong: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentRose: Color,
    val starAmber: Color,
    val dangerRose: Color
)

@Composable
fun rememberHomeTokens(isDark: Boolean): HomeThemeTokens {
    return remember(isDark) {
        if (isDark) {
            HomeThemeTokens(
                isDark = true,
                bgVoid = Color.Transparent,
                cardTop = Color(0xFF131318),
                cardBottom = Color(0xFF09090D),
                surfaceElevatedTop = Color(0xFF17171E),
                surfaceElevatedBottom = Color(0xFF0B0B10),
                borderStrong = Color.White.copy(alpha = 0.22f),
                borderSubtle = Color.White.copy(alpha = 0.10f),
                textPrimary = MaroonIvoryText,
                textSecondary = MaroonBlushSecondary,
                textMuted = MaroonBlushSecondary.copy(alpha = 0.62f),
                accentRose = MaroonAccentRose,
                starAmber = Color(0xFFF5C469),
                dangerRose = Color(0xFFFF7B7B)
            )
        } else {
            HomeThemeTokens(
                isDark = false,
                bgVoid = Color(0xFFFAF6F6),
                cardTop = Color(0xFFFFFFFF),
                cardBottom = Color(0xFFFBF8F8),
                surfaceElevatedTop = Color(0xFFFFFFFF),
                surfaceElevatedBottom = Color(0xFFF6F0F0),
                borderStrong = Color(0xFF800000).copy(alpha = 0.20f),
                borderSubtle = Color(0xFF1C1B1B).copy(alpha = 0.09f),
                textPrimary = Color(0xFF1C1B1B),
                textSecondary = Color(0xFF5F5757),
                textMuted = Color(0xFF8B8080),
                accentRose = Color(0xFF800000),
                starAmber = Color(0xFFD98200),
                dangerRose = Color(0xFFD32F2F)
            )
        }
    }
}

/**
 * Glassmorphic Floating Search & Profile Bar aligned with Swiffle's Dark Void aesthetic.
 */
@Composable
fun ObsidianSearchIsland(
    query: String,
    onQueryChange: (String) -> Unit,
    tokens: HomeThemeTokens,
    currentUserEmail: String?,
    currentUserAvatarUrl: String?,
    currentUserName: String?,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchFocused by remember { mutableStateOf(false) }
    var isProfileMenuExpanded by remember { mutableStateOf(false) }

    val pillShape = RoundedCornerShape(999.dp)
    val fillBrush = remember(tokens, isSearchFocused) {
        if (tokens.isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isSearchFocused) 0.14f else 0.10f),
                    Color.White.copy(alpha = if (isSearchFocused) 0.07f else 0.04f)
                )
            )
        } else {
            Brush.linearGradient(listOf(tokens.cardTop, tokens.cardBottom))
        }
    }

    val borderBrush = remember(tokens, isSearchFocused) {
        if (isSearchFocused) {
            Brush.linearGradient(
                colors = listOf(
                    tokens.accentRose.copy(alpha = 0.72f),
                    tokens.textPrimary.copy(alpha = 0.35f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    tokens.borderStrong,
                    tokens.accentRose.copy(alpha = 0.16f),
                    tokens.borderSubtle
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(52.dp)
            .clip(pillShape)
            .background(fillBrush)
            .border(width = 0.8.dp, brush = borderBrush, shape = pillShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onOpenDrawer()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("home_drawer_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Open navigation drawer",
                    tint = tokens.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isSearchFocused = it.isFocused }
                    .testTag("home_search_input"),
                textStyle = TextStyle(
                    color = tokens.textPrimary,
                    fontFamily = ClaudeSans,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                singleLine = true,
                cursorBrush = SolidColor(tokens.accentRose),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search documents...",
                                color = tokens.textMuted,
                                fontSize = 15.sp,
                                fontFamily = ClaudeSans
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_search_clear")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "Clear search",
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Profile Avatar Button + Obsidian Dropdown Menu
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            isProfileMenuExpanded = true
                        }
                        .testTag("home_profile_button"),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileAvatarCircle(
                        avatarUrl = currentUserAvatarUrl,
                        userName = currentUserName,
                        userEmail = currentUserEmail,
                        tokens = tokens,
                        size = 34.dp
                    )
                }

                val menuShape = RoundedCornerShape(20.dp)
                DropdownMenu(
                    expanded = isProfileMenuExpanded,
                    onDismissRequest = { isProfileMenuExpanded = false },
                    shape = menuShape,
                    containerColor = if (tokens.isDark) Color(0xFF0E0E13) else tokens.cardTop,
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(
                        width = 0.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                tokens.borderStrong,
                                tokens.accentRose.copy(alpha = 0.18f),
                                tokens.borderSubtle
                            )
                        )
                    ),
                    modifier = Modifier
                        .widthIn(min = 230.dp, max = 280.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(tokens.surfaceElevatedTop, tokens.surfaceElevatedBottom)
                            ),
                            shape = menuShape
                        )
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = currentUserName ?: currentUserEmail ?: "Guest User",
                                    fontFamily = ClaudeSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = tokens.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentUserEmail ?: "Local workspace",
                                    fontFamily = ClaudeSans,
                                    fontSize = 12.sp,
                                    color = tokens.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = tokens.accentRose,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        enabled = false,
                        colors = MenuDefaults.itemColors(
                            disabledTextColor = tokens.textPrimary,
                            disabledLeadingIconColor = tokens.accentRose
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        onClick = {}
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .height(0.8.dp)
                            .background(tokens.borderSubtle)
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Settings",
                                fontFamily = ClaudeSans,
                                fontSize = 14.sp,
                                color = tokens.textPrimary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("profile_menu_settings"),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        onClick = {
                            isProfileMenuExpanded = false
                            onOpenSettings()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sign out",
                                fontFamily = ClaudeSans,
                                fontSize = 14.sp,
                                color = tokens.dangerRose
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = "Sign out",
                                tint = tokens.dangerRose,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("profile_menu_logout"),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        onClick = {
                            isProfileMenuExpanded = false
                            onLogout()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileAvatarCircle(
    avatarUrl: String?,
    userName: String?,
    userEmail: String?,
    tokens: HomeThemeTokens,
    size: Dp
) {
    val ringBrush = remember(tokens) {
        Brush.linearGradient(
            colors = listOf(
                tokens.accentRose.copy(alpha = 0.85f),
                tokens.textPrimary.copy(alpha = 0.55f),
                tokens.borderSubtle
            )
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            .border(width = 1.1.dp, brush = ringBrush, shape = CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                if (tokens.isDark) Color(0xFF1A1518) else Color(0xFFF3E6E6)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            val model: Any = if (!avatarUrl.startsWith("http")) File(avatarUrl) else avatarUrl
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = "Profile photo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = userName?.firstOrNull()?.uppercase()
                ?: userEmail?.firstOrNull()?.uppercase()
                ?: "S"
            Text(
                text = initial,
                color = tokens.textPrimary,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ClaudeSerif
            )
        }
    }
}

/**
 * Glassmorphic Segmented Tab Strip synced 1:1 with the HorizontalPager offset.
 */
@Composable
fun ObsidianSegmentedTabBar(
    tabs: List<String>,
    pagerState: PagerState,
    tokens: HomeThemeTokens,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(999.dp)
    val indicatorShape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(44.dp)
            .clip(containerShape)
            .background(
                if (tokens.isDark) Color(0xFF0A0A0E).copy(alpha = 0.86f)
                else Color(0xFFEEE6E6)
            )
            .border(0.8.dp, tokens.borderSubtle, containerShape)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        ) {
            val tabCount = tabs.size.coerceAtLeast(1)
            val tabWidth = maxWidth / tabCount
            val density = LocalDensity.current
            val tabWidthPx = with(density) { tabWidth.toPx() }

            // Smooth GPU-translated mild-white glass indicator
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val offset = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .coerceIn(0f, (tabCount - 1).toFloat())
                        translationX = tabWidthPx * offset
                    }
                    .clip(indicatorShape)
                    .background(
                        if (tokens.isDark) {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.17f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        } else {
                            Brush.linearGradient(listOf(Color.White, Color.White))
                        }
                    )
                    .border(
                        width = 0.8.dp,
                        color = if (tokens.isDark) Color.White.copy(alpha = 0.24f) else tokens.borderSubtle,
                        shape = indicatorShape
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(indicatorShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectTab(index) }
                            .testTag("home_tab_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) tokens.textPrimary else tokens.textMuted,
                            fontSize = 13.sp,
                            fontFamily = ClaudeSans,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Immutable
private data class HomeToolCarouselItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentTint: Color
)

/**
 * Top-left "Popular Tools" + top-right "View all" header + Horizontally Swipeable Feature Rectangle Cards
 * placed right below the Search bar.
 */
@Composable
fun ObsidianToolsCarouselSection(
    tokens: HomeThemeTokens,
    onOpenTools: () -> Unit
) {
    val pdfRoseTint = if (tokens.isDark) Color(0xFFF07178) else Color(0xFFD13438)
    val excelMintTint = if (tokens.isDark) Color(0xFF56C288) else Color(0xFF107C41)
    val wordBlueTint = if (tokens.isDark) Color(0xFF6B9EFF) else Color(0xFF185ABD)
    val pptCoralTint = if (tokens.isDark) Color(0xFFF28B66) else Color(0xFFD83B01)
    val lockCyanTint = if (tokens.isDark) Color(0xFF67B7DC) else Color(0xFF0078D4)
    val ocrVioletTint = if (tokens.isDark) Color(0xFFB89BFF) else Color(0xFF6B46C1)

    val toolCards = remember(tokens.isDark) {
        listOf(
            HomeToolCarouselItem(
                id = "pdf_md",
                title = "PDF to Markdown",
                subtitle = "Unlimited pages → clean .md",
                icon = Icons.Rounded.Code,
                accentTint = ocrVioletTint
            ),
            HomeToolCarouselItem(
                id = "img_pdf",
                title = "Convert Image to PDF",
                subtitle = "Photos & scans → crisp PDF",
                icon = FluentIcons.DocumentPdf,
                accentTint = pdfRoseTint
            ),
            HomeToolCarouselItem(
                id = "pdf_excel",
                title = "PDF to Excel Converter",
                subtitle = "Extract tables to spreadsheets",
                icon = FluentIcons.DocumentExcel,
                accentTint = excelMintTint
            ),
            HomeToolCarouselItem(
                id = "word_excel",
                title = "Word to Excel Converter",
                subtitle = "Convert DOCX tables to sheets",
                icon = FluentIcons.DocumentExcel,
                accentTint = excelMintTint
            ),
            HomeToolCarouselItem(
                id = "word_pdf",
                title = "Word to PDF",
                subtitle = "DOCX & docs → formatted PDF",
                icon = FluentIcons.DocumentWord,
                accentTint = wordBlueTint
            ),
            HomeToolCarouselItem(
                id = "pdf_word",
                title = "PDF to Word",
                subtitle = "Turn PDFs into editable DOCX",
                icon = FluentIcons.DocumentWord,
                accentTint = wordBlueTint
            ),
            HomeToolCarouselItem(
                id = "excel_pdf",
                title = "Excel to PDF",
                subtitle = "Format sheets into print PDF",
                icon = FluentIcons.DocumentExcel,
                accentTint = excelMintTint
            ),
            HomeToolCarouselItem(
                id = "pdf_ppt",
                title = "PDF to PPT",
                subtitle = "Turn PDF pages into slides",
                icon = FluentIcons.DocumentPpt,
                accentTint = pptCoralTint
            ),
            HomeToolCarouselItem(
                id = "ppt_pdf",
                title = "PPT to PDF",
                subtitle = "Export decks as crisp PDF",
                icon = FluentIcons.DocumentPpt,
                accentTint = pptCoralTint
            ),
            HomeToolCarouselItem(
                id = "pdf_jpg",
                title = "PDF to JPG",
                subtitle = "Save pages as high-res JPG",
                icon = FluentIcons.DocumentJpg,
                accentTint = pdfRoseTint
            ),
            HomeToolCarouselItem(
                id = "merge_pdf",
                title = "Merge PDF",
                subtitle = "Combine PDFs in custom order",
                icon = FluentIcons.MergePdf,
                accentTint = pptCoralTint
            ),
            HomeToolCarouselItem(
                id = "lock_pdf",
                title = "Lock PDF",
                subtitle = "Protect files with a password",
                icon = FluentIcons.Lock,
                accentTint = lockCyanTint
            ),
            HomeToolCarouselItem(
                id = "unlock_pdf",
                title = "Unlock PDF",
                subtitle = "Remove PDF password lock",
                icon = FluentIcons.Unlock,
                accentTint = lockCyanTint
            ),
            HomeToolCarouselItem(
                id = "smart_ocr",
                title = "Smart OCR",
                subtitle = "AI text extraction from scans",
                icon = FluentIcons.Sparkle,
                accentTint = ocrVioletTint
            )
        )
    }

    val listState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header Row: "Popular Tools" on left, "View all" on right below Search pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Popular Tools",
                color = tokens.textPrimary,
                fontFamily = ClaudeSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = (-0.2).sp
            )

            val viewAllShape = RoundedCornerShape(999.dp)
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(viewAllShape)
                    .clickable(
                        indication = ripple(color = tokens.accentRose.copy(alpha = 0.18f)),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onOpenTools
                    )
                    .padding(start = 10.dp)
                    .testTag("home_tools_view_all"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View all",
                    color = tokens.textSecondary,
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tokens.accentRose.copy(alpha = if (tokens.isDark) 0.16f else 0.12f))
                        .border(0.8.dp, tokens.accentRose.copy(alpha = 0.36f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "View all tools",
                        tint = tokens.accentRose,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Horizontally Swipeable Feature Rectangle Cards
        LazyRow(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(toolCards, key = { it.id }) { item ->
                ObsidianToolCarouselCard(
                    item = item,
                    tokens = tokens,
                    onClick = onOpenTools
                )
            }
        }
    }
}

@Composable
private fun ObsidianToolCarouselCard(
    item: HomeToolCarouselItem,
    tokens: HomeThemeTokens,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = SwiffleMotion.SpringSpatialFast,
        label = "tool_carousel_card_scale"
    )
    val cardShape = RoundedCornerShape(20.dp)
    val cardFill = remember(tokens) {
        Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom))
    }
    val cardBorder = remember(tokens, item.accentTint) {
        Brush.linearGradient(
            colors = listOf(
                tokens.borderStrong,
                item.accentTint.copy(alpha = if (tokens.isDark) 0.34f else 0.26f),
                tokens.borderSubtle
            )
        )
    }

    Column(
        modifier = Modifier
            .width(236.dp)
            .heightIn(min = 122.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(cardShape)
            .background(cardFill)
            .drawBehind {
                if (tokens.isDark) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                item.accentTint.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.18f, size.height * 0.18f),
                            radius = size.width * 0.62f
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.18f),
                        radius = size.width * 0.62f
                    )
                }
            }
            .border(width = 0.8.dp, brush = cardBorder, shape = cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = tokens.textPrimary.copy(alpha = 0.14f)),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag("home_tool_card_${item.id}"),
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(item.accentTint.copy(alpha = if (tokens.isDark) 0.16f else 0.12f))
                .border(
                    width = 0.7.dp,
                    color = item.accentTint.copy(alpha = if (tokens.isDark) 0.32f else 0.24f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = item.accentTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column {
            Text(
                text = item.title,
                color = tokens.textPrimary,
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.subtitle,
                color = tokens.textMuted,
                fontFamily = ClaudeSans,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Obsidian Glass Document List Row.
 */
@Composable
fun ObsidianDocumentRow(
    file: RecentFile,
    tokens: HomeThemeTokens,
    isStarred: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleStar: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val dateStr = remember(file.timestamp) { formatRecentTime(file.timestamp) }
    val sizeStr = remember(file.size) {
        android.text.format.Formatter.formatShortFileSize(context, file.size)
    }

    val rowShape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(rowShape)
            .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        tokens.borderStrong.copy(alpha = 0.72f),
                        tokens.borderSubtle
                    )
                ),
                shape = rowShape
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
            .testTag("document_row_${file.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 54.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.8.dp, tokens.borderSubtle, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            PdfThumbnailImage(
                uri = file.uri,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 10.dp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = tokens.textPrimary,
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (file.size > 0) "$dateStr  ·  $sizeStr" else dateStr,
                color = tokens.textMuted,
                fontFamily = ClaudeSans,
                fontSize = 12.sp,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }

        IconButton(
            onClick = onToggleStar,
            modifier = Modifier
                .size(48.dp)
                .testTag("star_btn_${file.name}")
        ) {
            Icon(
                imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isStarred) "Remove star" else "Star document",
                tint = if (isStarred) tokens.starAmber else tokens.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("more_btn_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Document options",
                    tint = tokens.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            val menuShape = RoundedCornerShape(18.dp)
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = menuShape,
                containerColor = if (tokens.isDark) Color(0xFF0E0E13) else tokens.cardTop,
                tonalElevation = 0.dp,
                shadowElevation = 14.dp,
                border = BorderStroke(0.8.dp, tokens.borderStrong),
                modifier = Modifier
                    .widthIn(min = 196.dp, max = 260.dp)
                    .background(
                        Brush.verticalGradient(listOf(tokens.surfaceElevatedTop, tokens.surfaceElevatedBottom)),
                        menuShape
                    )
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Open document",
                            fontFamily = ClaudeSans,
                            fontSize = 14.sp,
                            color = tokens.textPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = tokens.accentRose,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Share file",
                            fontFamily = ClaudeSans,
                            fontSize = 14.sp,
                            color = tokens.textPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    onClick = {
                        showMenu = false
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, file.uri)
                            clipData = ClipData.newRawUri("Document", file.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .height(0.8.dp)
                        .background(tokens.borderSubtle)
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Remove from library",
                            fontFamily = ClaudeSans,
                            fontSize = 14.sp,
                            color = tokens.dangerRose
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = tokens.dangerRose,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Obsidian Glass Empty State Card matching Onboarding's editorial card language.
 */
@Composable
fun ObsidianEmptyStateCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    tokens: HomeThemeTokens,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
                .border(
                    width = 0.8.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            tokens.borderStrong,
                            tokens.accentRose.copy(alpha = 0.14f),
                            tokens.borderSubtle
                        )
                    ),
                    shape = cardShape
                )
                .padding(horizontal = 26.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (tokens.isDark) Color.White.copy(alpha = 0.06f)
                        else iconTint.copy(alpha = 0.10f)
                    )
                    .border(0.8.dp, tokens.borderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = title,
                color = tokens.textPrimary,
                fontFamily = ClaudeSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                color = tokens.textSecondary.copy(alpha = 0.82f),
                fontFamily = ClaudeSans,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(22.dp))
                val pillShape = RoundedCornerShape(999.dp)
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(pillShape)
                        .background(
                            if (tokens.isDark) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        Color.White.copy(alpha = 0.10f)
                                    )
                                )
                            } else {
                                Brush.linearGradient(listOf(tokens.accentRose, tokens.accentRose))
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (tokens.isDark) Color.White.copy(alpha = 0.40f) else tokens.accentRose,
                            shape = pillShape
                        )
                        .clickable(onClick = onAction)
                        .padding(horizontal = 24.dp)
                        .testTag("empty_browse_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = if (tokens.isDark) tokens.textPrimary else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionLabel,
                            fontFamily = ClaudeSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = if (tokens.isDark) tokens.textPrimary else Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dark Void Skeleton Loader Row matching ObsidianDocumentRow dimensions.
 */
@Composable
fun ObsidianSkeletonDocumentRow(tokens: HomeThemeTokens) {
    val transition = rememberInfiniteTransition(label = "obsidian_skeleton_shimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -280f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    val base = if (tokens.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    val peak = if (tokens.isDark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.12f)
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(base, peak, base),
        start = Offset(shimmerTranslate - 180f, 0f),
        end = Offset(shimmerTranslate + 180f, 0f)
    )

    val rowShape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(rowShape)
            .background(Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom)))
            .border(0.8.dp, tokens.borderSubtle, rowShape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.36f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(shimmerBrush)
            )
        }
    }
}

/**
 * Glassmorphic Morphic Speed-Dial FAB matching Onboarding's mild-white glass CTA.
 */
@Composable
fun ObsidianMorphicFab(
    isCompact: Boolean,
    tokens: HomeThemeTokens,
    onAddPdf: () -> Unit,
    onConvert: () -> Unit,
    forceCollapse: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(forceCollapse) {
        if (forceCollapse && isExpanded) {
            isExpanded = false
        }
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 620f),
        label = "fab_plus_rotation"
    )

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = SwiffleMotion.SpringSpatialFast,
        label = "fab_press_scale"
    )

    Column(
        modifier = Modifier
            .padding(bottom = 12.dp, end = 6.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(180)) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 540f),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(tween(140)) + slideOutVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 650f),
                targetOffsetY = { it / 2 }
            )
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FabMenuPill(
                    label = "Scan document",
                    icon = Icons.Rounded.CameraAlt,
                    tokens = tokens,
                    testTag = "fab_scan_document",
                    onClick = {
                        isExpanded = false
                        onConvert()
                    }
                )
                FabMenuPill(
                    label = "Extract tables",
                    icon = Icons.Rounded.TableChart,
                    tokens = tokens,
                    testTag = "fab_extract_tables",
                    onClick = {
                        isExpanded = false
                        onConvert()
                    }
                )
                FabMenuPill(
                    label = "Browse storage",
                    icon = Icons.Rounded.FolderOpen,
                    tokens = tokens,
                    testTag = "fab_browse_storage",
                    onClick = {
                        isExpanded = false
                        onAddPdf()
                    }
                )
            }
        }

        val pillShape = RoundedCornerShape(999.dp)
        val fabFill = if (tokens.isDark) {
            Brush.linearGradient(
                colors = if (isExpanded) {
                    listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.16f))
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color(0xFFFFF5F2).copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.11f)
                    )
                }
            )
        } else {
            Brush.linearGradient(listOf(tokens.accentRose, tokens.accentRose))
        }
        val fabBorder = if (tokens.isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.54f),
                    tokens.accentRose.copy(alpha = 0.36f),
                    Color.White.copy(alpha = 0.16f)
                )
            )
        } else {
            Brush.linearGradient(listOf(tokens.accentRose, tokens.accentRose))
        }
        val contentTint = if (tokens.isDark) tokens.textPrimary else Color.White

        Box(
            modifier = Modifier
                .height(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(pillShape)
                .background(fabFill)
                .border(1.dp, fabBorder, pillShape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = Color.White.copy(alpha = 0.28f)),
                    onClick = {
                        if (isCompact && !isExpanded) {
                            onAddPdf()
                        } else {
                            isExpanded = !isExpanded
                        }
                    }
                )
                .padding(horizontal = if (isCompact && !isExpanded) 15.dp else 20.dp)
                .testTag("home_primary_fab"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = if (isExpanded) "Close actions" else "New document",
                    tint = contentTint,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = rotationAngle }
                )
                AnimatedVisibility(
                    visible = !isCompact && !isExpanded,
                    enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkHorizontally(tween(180)) + fadeOut(tween(150))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New document",
                            fontFamily = ClaudeSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = contentTint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FabMenuPill(
    label: String,
    icon: ImageVector,
    tokens: HomeThemeTokens,
    testTag: String,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(pillShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(tokens.surfaceElevatedTop, tokens.surfaceElevatedBottom)
                )
            )
            .border(0.8.dp, tokens.borderStrong, pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.accentRose,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontFamily = ClaudeSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = tokens.textPrimary
        )
    }
}

/**
 * Obsidian Glass Navigation Drawer matching Swiffle's Dark Void & Editorial visual system.
 */
@Composable
fun ObsidianNavigationDrawerContent(
    drawerWidth: Dp,
    swiffleLogoPainter: Painter,
    tokens: HomeThemeTokens,
    selectedDrawerItem: String,
    documentCount: Int,
    starredCount: Int,
    onSelectTab: (itemKey: String, tabIndex: Int) -> Unit,
    onConvert: () -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val drawerScrollState = rememberScrollState()
    val drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    val drawerContainerColor = if (tokens.isDark) Color(0xFF060609) else Color(0xFFFAF6F6)

    ModalDrawerSheet(
        drawerShape = drawerShape,
        drawerContainerColor = drawerContainerColor,
        drawerTonalElevation = 0.dp,
        modifier = Modifier
            .width(drawerWidth)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Subtle top crimson-rose ambient light matching Dark Void Onboarding
                    if (tokens.isDark) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.00f to Color(0xFF6B180B).copy(alpha = 0.14f),
                                    0.55f to Color(0xFF280703).copy(alpha = 0.05f),
                                    1.00f to Color.Transparent
                                ),
                                center = Offset(size.width * 0.25f, size.height * 0.12f),
                                radius = size.maxDimension * 0.65f
                            )
                        )
                    }
                    // Delicate right-edge hairline glass border
                    drawLine(
                        color = tokens.borderSubtle,
                        start = Offset(size.width - 0.5f, 0f),
                        end = Offset(size.width - 0.5f, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Brand Identity Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = swiffleLogoPainter,
                        contentDescription = "Swiffle Logo",
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Swiffle",
                            fontFamily = ClaudeSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            color = tokens.textPrimary
                        )
                        Text(
                            text = "Document Studio",
                            fontFamily = ClaudeSans,
                            fontSize = 12.sp,
                            color = tokens.textMuted
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(0.8.dp)
                        .background(tokens.borderSubtle)
                )

                // Scrollable Navigation Items
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(drawerScrollState)
                        .padding(vertical = 10.dp)
                ) {
                    DrawerSectionOverline(text = "LIBRARY", tokens = tokens)

                    ObsidianDrawerItem(
                        label = "All Documents",
                        icon = Icons.Outlined.Folder,
                        isSelected = selectedDrawerItem == "all_docs",
                        badgeText = documentCount.toString(),
                        tokens = tokens,
                        testTag = "drawer_all_docs",
                        onClick = { onSelectTab("all_docs", 0) }
                    )

                    ObsidianDrawerItem(
                        label = "Starred",
                        icon = Icons.Outlined.StarBorder,
                        isSelected = selectedDrawerItem == "starred",
                        badgeText = if (starredCount > 0) starredCount.toString() else null,
                        tokens = tokens,
                        testTag = "drawer_starred",
                        onClick = { onSelectTab("starred", 1) }
                    )

                    ObsidianDrawerItem(
                        label = "Annotated",
                        icon = Icons.Outlined.EditNote,
                        isSelected = selectedDrawerItem == "annotated",
                        tokens = tokens,
                        testTag = "drawer_annotated",
                        onClick = { onSelectTab("annotated", 2) }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .height(0.8.dp)
                            .background(tokens.borderSubtle)
                    )

                    DrawerSectionOverline(text = "TOOLS & INTELLIGENCE", tokens = tokens)

                    ObsidianDrawerItem(
                        label = "Convert & PDF Tools",
                        icon = Icons.Rounded.SwapHoriz,
                        isSelected = false,
                        tokens = tokens,
                        testTag = "drawer_convert",
                        onClick = onConvert
                    )

                    ObsidianDrawerItem(
                        label = "Swiffle AI Chat",
                        icon = Icons.Outlined.AutoAwesome,
                        isSelected = false,
                        badgeText = "AI",
                        isAccentBadge = true,
                        tokens = tokens,
                        testTag = "drawer_ai_chat",
                        onClick = onOpenAiChat
                    )
                }

                // Bottom Footer Actions (Settings & Sign Out)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(0.8.dp)
                        .background(tokens.borderSubtle)
                )

                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ObsidianDrawerItem(
                        label = "Settings",
                        icon = Icons.Outlined.Settings,
                        isSelected = false,
                        tokens = tokens,
                        testTag = "drawer_settings",
                        onClick = onOpenSettings
                    )
                    ObsidianDrawerItem(
                        label = "Sign out",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        isSelected = false,
                        isDanger = true,
                        tokens = tokens,
                        testTag = "drawer_logout",
                        onClick = onLogout
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionOverline(
    text: String,
    tokens: HomeThemeTokens
) {
    Text(
        text = text,
        fontFamily = ClaudeSans,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        color = tokens.accentRose.copy(alpha = 0.88f),
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    )
}

@Composable
private fun ObsidianDrawerItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    tokens: HomeThemeTokens,
    badgeText: String? = null,
    isAccentBadge: Boolean = false,
    isDanger: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val itemShape = RoundedCornerShape(14.dp)
    val contentColor = when {
        isDanger -> tokens.dangerRose
        isSelected -> tokens.textPrimary
        else -> tokens.textSecondary
    }
    val iconColor = when {
        isDanger -> tokens.dangerRose
        isSelected -> tokens.accentRose
        else -> tokens.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .heightIn(min = 48.dp)
            .clip(itemShape)
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            if (tokens.isDark) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.13f),
                                        Color.White.copy(alpha = 0.05f)
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(tokens.accentRose.copy(alpha = 0.10f), tokens.accentRose.copy(alpha = 0.04f))
                                )
                            }
                        )
                        .border(0.8.dp, tokens.borderStrong.copy(alpha = 0.72f), itemShape)
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = label }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left rose accent indicator for active item
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (isSelected) tokens.accentRose else Color.Transparent)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(13.dp))

        Text(
            text = label,
            fontFamily = ClaudeSans,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (badgeText != null) {
            val badgeShape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .clip(badgeShape)
                    .background(
                        when {
                            isAccentBadge -> tokens.accentRose.copy(alpha = if (tokens.isDark) 0.22f else 0.12f)
                            tokens.isDark -> Color.White.copy(alpha = 0.08f)
                            else -> Color.Black.copy(alpha = 0.06f)
                        }
                    )
                    .border(
                        width = 0.7.dp,
                        color = if (isAccentBadge) tokens.accentRose.copy(alpha = 0.45f) else tokens.borderSubtle,
                        shape = badgeShape
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = if (isAccentBadge) tokens.textPrimary else tokens.textSecondary,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}
