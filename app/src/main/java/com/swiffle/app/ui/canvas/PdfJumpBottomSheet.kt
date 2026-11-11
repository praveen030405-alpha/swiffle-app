package com.swiffle.app.ui.canvas
import com.swiffle.app.theme.GoogleSans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.swiffle.app.theme.GoogleSans
import com.swiffle.app.ui.components.StockMaterialWavySlider
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfJumpBottomSheet(
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    isDark: Boolean,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val haptic = LocalHapticFeedback.current
    val enterpriseAccent = Color(0xFF0078D4)
    val sheetSurfaceColor = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val sheetBorderColor = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
    val menuText = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val density = LocalDensity.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sliderVal by remember(currentPage, visible) {
        mutableFloatStateOf(currentPage.toFloat())
    }
    var pageInput by remember(currentPage, visible) { mutableStateOf(currentPage.toString()) }
    var lastHapticPage by remember { mutableIntStateOf(currentPage) }

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val cutoutStart = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) }
    val cutoutEnd = with(density) { WindowInsets.displayCutout.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) }

    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetSurfaceColor,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            coroutineScope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                    )
                    .semantics {
                        role = androidx.compose.ui.semantics.Role.Button
                        contentDescription = "Sheet handle, tap to collapse"
                    },
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        },
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .padding(
                    start = if (isLandscape) 16.dp + cutoutStart else 20.dp,
                    end = if (isLandscape) 16.dp + cutoutEnd else 20.dp,
                    top = 0.dp,
                    bottom = if (isLandscape) 16.dp else 28.dp
                )
        ) {
                if (isLandscape) {
                    // ══════════════════════════════════════════════════════════════════
                    // LANDSCAPE OPTIMIZED ULTRA-COMPACT 2-COLUMN SHEET (~175dp tall)
                    // ══════════════════════════════════════════════════════════════════
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        // Integrated Header Row: Title & Integrated Direct Input + Go
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = enterpriseAccent.copy(alpha = 0.12f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Layers, "Page Jump", tint = enterpriseAccent, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Jump to Page",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = menuText
                                )
                            }

                            // Integrated Page Input + Go
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDark) Color(0xFF1E2640) else Color(0xFFF1F5F9))
                                        .border(1.dp, sheetBorderColor, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Page ",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isDark) Color.LightGray else Color.DarkGray
                                    )
                                    BasicPageNumberInput(
                                        value = pageInput,
                                        onValueChange = { str ->
                                            if (str.all { c -> c.isDigit() }) {
                                                pageInput = str
                                                str.toFloatOrNull()?.let { v ->
                                                    if (v in 1f..pageCount.toFloat()) {
                                                        sliderVal = v
                                                        lastHapticPage = v.roundToInt()
                                                    }
                                                }
                                            }
                                        },
                                        onGo = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            val targetNum = pageInput.toIntOrNull() ?: sliderVal.roundToInt()
                                            if (targetNum in 1..pageCount) {
                                                onJumpToPage(targetNum)
                                            }
                                            onDismiss()
                                        },
                                        pageCount = pageCount,
                                        textColor = enterpriseAccent,
                                        fontSize = 13.sp,
                                        modifier = Modifier.widthIn(min = 44.dp, max = 64.dp)
                                    )
                                    Text(
                                        " / $pageCount",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isDark) Color.LightGray else Color.DarkGray
                                    )
                                }

                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        val targetNum = pageInput.toIntOrNull() ?: sliderVal.roundToInt()
                                        if (targetNum in 1..pageCount) {
                                            onJumpToPage(targetNum)
                                        }
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = enterpriseAccent),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Go ➜", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = GoogleSans)
                                }
                            }
                        }

                        // Slider Card with Relative Steppers
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                                .border(1.dp, sheetBorderColor, RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            StockMaterialWavySlider(
                                value = sliderVal,
                                onValueChange = { newV ->
                                    sliderVal = newV
                                    val pageInt = newV.roundToInt()
                                    pageInput = pageInt.toString()
                                    if (pageInt != lastHapticPage) {
                                        lastHapticPage = pageInt
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                valueRange = 1f..pageCount.toFloat().coerceAtLeast(1f),
                                activeColor = enterpriseAccent,
                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                waveAmplitude = 3.5.dp,
                                waveLength = 22.dp,
                                strokeWidth = 5.dp,
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            )

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val chipBg = MaterialTheme.colorScheme.surfaceContainerHigh
                                val chipBorder = sheetBorderColor
                                val chipText = MaterialTheme.colorScheme.onSurface

                                fun jumpRelative(delta: Int) {
                                    val target = (sliderVal.roundToInt() + delta).coerceIn(1, pageCount)
                                    sliderVal = target.toFloat()
                                    pageInput = target.toString()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onJumpToPage(target)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.weight(1f).clickable {
                                        sliderVal = 1f
                                        pageInput = "1"
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onJumpToPage(1)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("⏮", color = chipText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                    }
                                }

                                if (pageCount > 10) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = chipBg,
                                        border = BorderStroke(1.dp, chipBorder),
                                        modifier = Modifier.weight(1f).clickable { jumpRelative(-10) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text("-10", color = chipText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.weight(1f).clickable { jumpRelative(-1) }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("-1", color = chipText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.weight(1f).clickable { jumpRelative(1) }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("+1", color = chipText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                    }
                                }

                                if (pageCount > 10) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = chipBg,
                                        border = BorderStroke(1.dp, chipBorder),
                                        modifier = Modifier.weight(1f).clickable { jumpRelative(10) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text("+10", color = chipText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.weight(1f).clickable {
                                        sliderVal = pageCount.toFloat()
                                        pageInput = pageCount.toString()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onJumpToPage(pageCount)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("⏭", color = chipText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ══════════════════════════════════════════════════════════════════
                    // PORTRAIT ENTERPRISE BOTTOM SHEET
                    // ══════════════════════════════════════════════════════════════════
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        // Header Row: Title + Subtitle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = enterpriseAccent.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Layers, "Page Jump", tint = enterpriseAccent, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Jump to Page",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = menuText
                                )
                                Text(
                                    "Enter a page or glide the wavy slider",
                                    fontFamily = GoogleSans,
                                    fontSize = 11.5.sp,
                                    color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                                )
                            }
                        }

                        // Minimal Blue Page Slider & Document Progress with Integrated Input + Go Button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                                .border(1.dp, sheetBorderColor, RoundedCornerShape(20.dp))
                                .padding(horizontal = 18.dp, vertical = 16.dp)
                        ) {
                            // Integrated Header Row: "Progress" on Left | Integrated Input + Go on Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = enterpriseAccent.copy(alpha = 0.12f),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = enterpriseAccent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Progress",
                                        fontFamily = GoogleSans,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = menuText,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Spacer(Modifier.width(6.dp))

                                // Integrated Input Pill & Go Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, sheetBorderColor)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "Page ",
                                                fontFamily = GoogleSans,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            BasicPageNumberInput(
                                                value = pageInput.ifEmpty { sliderVal.roundToInt().toString() },
                                                onValueChange = { str ->
                                                    if (str.all { c -> c.isDigit() }) {
                                                        pageInput = str
                                                        str.toFloatOrNull()?.let { v ->
                                                            if (v in 1f..pageCount.toFloat()) {
                                                                sliderVal = v
                                                                lastHapticPage = v.roundToInt()
                                                            }
                                                        }
                                                    }
                                                },
                                                pageCount = pageCount,
                                                textColor = enterpriseAccent,
                                                fontSize = 13.sp,
                                                modifier = Modifier.width(30.dp)
                                            )
                                            Text(
                                                " / $pageCount",
                                                fontFamily = GoogleSans,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            val targetNum = pageInput.toIntOrNull() ?: sliderVal.roundToInt()
                                            if (targetNum in 1..pageCount) {
                                                onJumpToPage(targetNum)
                                            }
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = enterpriseAccent),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(
                                            "Go ➜",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            fontFamily = GoogleSans,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            StockMaterialWavySlider(
                                value = sliderVal,
                                onValueChange = { newV ->
                                    sliderVal = newV
                                    val pageInt = newV.roundToInt()
                                    pageInput = pageInt.toString()
                                    if (pageInt != lastHapticPage) {
                                        lastHapticPage = pageInt
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                valueRange = 1f..pageCount.toFloat().coerceAtLeast(1f),
                                activeColor = enterpriseAccent,
                                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                                waveAmplitude = 4.5.dp,
                                waveLength = 26.dp,
                                strokeWidth = 6.dp,
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val milestones = if (pageCount <= 5) {
                                    (1..pageCount).toList()
                                } else {
                                    listOf(1, (pageCount * 0.25f).roundToInt(), (pageCount * 0.5f).roundToInt(), (pageCount * 0.75f).roundToInt(), pageCount).distinct()
                                }
                                milestones.forEach { m ->
                                    Text(
                                        "$m",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontFamily = GoogleSans
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Relative Jump Chips Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val chipBg = MaterialTheme.colorScheme.surfaceContainerHigh
                                val chipBorder = sheetBorderColor
                                val chipText = MaterialTheme.colorScheme.onSurface

                                fun jumpRelative(delta: Int) {
                                    val target = (sliderVal.roundToInt() + delta).coerceIn(1, pageCount)
                                    sliderVal = target.toFloat()
                                    pageInput = target.toString()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }

                                // First
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.weight(1f).clickable {
                                        sliderVal = 1f
                                        pageInput = "1"
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text("⏮", color = chipText, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                    }
                                }

                                // -10
                                if (pageCount > 10) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = chipBg,
                                        border = BorderStroke(1.dp, chipBorder),
                                        modifier = Modifier.weight(1f).clickable { jumpRelative(-10) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                            Text("-10", color = chipText, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                        }
                                    }
                                }

                                // -1
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.weight(1f).clickable { jumpRelative(-1) }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text("-1", color = chipText, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                    }
                                }

                                // +1
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.weight(1f).clickable { jumpRelative(1) }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text("+1", color = chipText, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                    }
                                }

                                // +10
                                if (pageCount > 10) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = chipBg,
                                        border = BorderStroke(1.dp, chipBorder),
                                        modifier = Modifier.weight(1f).clickable { jumpRelative(10) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                            Text("+10", color = chipText, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                        }
                                    }
                                }

                                // Last
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.weight(1f).clickable {
                                        sliderVal = pageCount.toFloat()
                                        pageInput = pageCount.toString()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                        Text("⏭", color = chipText, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSans)
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun BasicPageNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    pageCount: Int,
    textColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    onGo: (() -> Unit)? = null
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = androidx.compose.ui.text.input.ImeAction.Go
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onGo = { onGo?.invoke() }
        ),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontFamily = GoogleSans,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        ),
        modifier = modifier
    )
}
