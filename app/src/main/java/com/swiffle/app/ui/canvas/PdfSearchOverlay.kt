package com.swiffle.app.ui.canvas
import com.swiffle.app.theme.GoogleSans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.theme.GoogleSans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

// Bounded native background indexing dispatcher ($N-2$ cores)
private val boundedSearchDispatcher = Executors.newFixedThreadPool(
    (Runtime.getRuntime().availableProcessors() - 2).coerceAtLeast(2)
).asCoroutineDispatcher()

@Composable
fun PdfSearchOverlay(
    visible: Boolean,
    pdfNativePtr: Long,
    currentPage: Int,
    pageCount: Int,
    isDark: Boolean,
    onClose: () -> Unit,
    onNavigateToMatch: (pageNum: Int, matchIndex: Int) -> Unit,
    onMatchesUpdated: (List<Pair<Int, CharRect>>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchMatchesList by remember { mutableStateOf<List<Pair<Int, CharRect>>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val accentRed = Color(0xFFFC2828)
    val pillText = if (isDark) Color.White else Color.Black

    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
        } else {
            searchQuery = ""
            searchMatchesList = emptyList()
            currentMatchIndex = 0
            onMatchesUpdated(emptyList())
        }
    }

    // Debounced search using bounded thread pool (leaves UI and audio cores free)
    LaunchedEffect(searchQuery, visible) {
        if (!visible || searchQuery.isBlank() || pdfNativePtr == 0L) {
            isSearching = false
            searchMatchesList = emptyList()
            currentMatchIndex = 0
            onMatchesUpdated(emptyList())
            return@LaunchedEffect
        }

        kotlinx.coroutines.delay(100) // Fast 100ms debounce
        isSearching = true

        val startPage = currentPage
        val totalPages = pageCount

        withContext(boundedSearchDispatcher) {
            val currentMatches = mutableListOf<Pair<Int, CharRect>>()
            val searchOrder = (startPage..totalPages).toList() + (1 until startPage).toList()

            for (pageNum in searchOrder) {
                if (!isActive) break

                val jsonStr = com.swiffle.app.NativeEngine.searchPageFromPtr(pdfNativePtr, pageNum, searchQuery)
                if (jsonStr.isNotBlank() && jsonStr != "[]") {
                    try {
                        val jsonArray = JSONArray(jsonStr)
                        var foundNew = false
                        for (j in 0 until jsonArray.length()) {
                            val rectObj = jsonArray.getJSONObject(j)
                            currentMatches.add(
                                Pair(
                                    pageNum,
                                    CharRect(
                                        "",
                                        rectObj.getDouble("x").toFloat(),
                                        rectObj.getDouble("y").toFloat(),
                                        rectObj.getDouble("w").toFloat(),
                                        rectObj.getDouble("h").toFloat()
                                    )
                                )
                            )
                            foundNew = true
                        }

                        if (foundNew) {
                            val immutableCopy = currentMatches.toList()
                            withContext(Dispatchers.Main) {
                                searchMatchesList = immutableCopy
                                onMatchesUpdated(immutableCopy)
                                if (immutableCopy.size == jsonArray.length()) {
                                    currentMatchIndex = 0
                                    onNavigateToMatch(pageNum, 0)
                                    isSearching = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                isSearching = false
                if (currentMatches.isEmpty()) {
                    searchMatchesList = emptyList()
                    currentMatchIndex = 0
                    onMatchesUpdated(emptyList())
                }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier
    ) {
        val density = LocalDensity.current
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val statusBarTop: Dp = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding() }
        val cutoutStart: Dp = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(LayoutDirection.Ltr) }
        val cutoutEnd: Dp = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateEndPadding(LayoutDirection.Ltr) }

        val effectiveTopPadding: Dp = if (isLandscape) {
            if (statusBarTop > 0.dp) statusBarTop + 4.dp else 8.dp
        } else {
            if (statusBarTop > 0.dp) statusBarTop + 8.dp else 12.dp
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = effectiveTopPadding, start = 16.dp + cutoutStart, end = 16.dp + cutoutEnd),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 720.dp else 1200.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(23.dp))
                    .background(if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF))
                    .border(
                        1.dp,
                        if (isDark) Color(0x33FFFFFF) else Color(0x18000000),
                        RoundedCornerShape(23.dp)
                    )
                    .padding(
                        horizontal = if (isLandscape) 10.dp else 12.dp,
                        vertical = if (isLandscape) 5.dp else 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / Close Search Button
                val backInteraction = remember { MutableInteractionSource() }
                val backPressed by backInteraction.collectIsPressedAsState()
                val backScale by animateFloatAsState(
                    if (backPressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "searchBackScale"
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = backScale; scaleY = backScale }
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(0.05f) else Color.Black.copy(0.03f))
                        .clickable(interactionSource = backInteraction, indication = null) {
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Field Input
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = pillText,
                        fontSize = 16.sp,
                        fontFamily = GoogleSans
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search document...",
                                    color = Color.Gray,
                                    fontSize = 16.sp,
                                    fontFamily = GoogleSans
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Loading Spinner or Interactive Stepper
                if (isSearching) {
                    com.swiffle.app.ui.components.StockMaterialLoadingIndicator(
                        size = 20.dp,
                        strokeWidth = 2.5.dp,
                        useGoogleColors = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else if (searchQuery.isNotEmpty()) {
                    if (searchMatchesList.isNotEmpty()) {
                        // Interactive Stepper Pill: [ i of N ]
                        Text(
                            "${currentMatchIndex + 1} of ${searchMatchesList.size}",
                            color = accentRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSans
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Up Arrow (Previous Match)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (currentMatchIndex > 0) {
                                        currentMatchIndex--
                                    } else {
                                        currentMatchIndex = searchMatchesList.size - 1
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val match = searchMatchesList[currentMatchIndex]
                                    onNavigateToMatch(match.first, currentMatchIndex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Previous Match",
                                tint = accentRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Down Arrow (Next Match)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (currentMatchIndex < searchMatchesList.size - 1) {
                                        currentMatchIndex++
                                    } else {
                                        currentMatchIndex = 0
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val match = searchMatchesList[currentMatchIndex]
                                    onNavigateToMatch(match.first, currentMatchIndex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Next Match",
                                tint = accentRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Text(
                            "0 matches",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontFamily = GoogleSans
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Vertical Document Heatmap Strip on right edge showing search match distribution
 */
@Composable
fun SearchHeatmapStrip(
    matches: List<Pair<Int, CharRect>>,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (matches.isEmpty() || pageCount <= 0) return

    val matchPages = remember(matches) { matches.map { it.first }.toSet() }

    val density = LocalDensity.current
    val cutoutEnd = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .width(6.dp)
            .fillMaxHeight()
            .padding(top = if (isLandscape) 48.dp else 120.dp, bottom = if (isLandscape) 48.dp else 120.dp, end = cutoutEnd)
    ) {
        matchPages.forEach { p ->
            val fraction = (p - 1).toFloat() / pageCount.toFloat().coerceAtLeast(1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = fraction * 800f // Relative vertical projection
                    }
                    .background(Color(0xFFFFC107).copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                    .clickable { onPageSelected(p) }
            )
        }
    }
}
