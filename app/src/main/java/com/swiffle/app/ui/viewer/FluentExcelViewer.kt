package com.swiffle.app.ui.viewer

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

data class MutableExcelSheet(
    var name: String,
    val headers: MutableList<String>,
    val rows: MutableList<MutableList<String>>
)

data class MutableExcelWorkbook(
    val title: String,
    val sheets: MutableList<MutableExcelSheet>
)

/**
 * Windows 11 Fluent Microsoft Excel Studio:
 *  - True Sticky 2D Grid: Pinned Column Letters (A, B, C...) at top & Pinned Row Numbers (1, 2, 3...) on left
 *  - Accurate 1-based Excel Coordinate Mapping (Row 1 = A1, B1... so formulas and multi-column sheets never shift)
 *  - Responsive Layout Zoom (55% to 240%) without graphicsLayer viewport clipping
 *  - Auto-sized column widths per column based on content length
 *  - Live Formula Bar with Quick Insert (=SUM, =AVERAGE, =COUNT) & Bottom Telemetry Status Bar (SUM / AVG / COUNT)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluentExcelViewer(
    uri: Uri,
    fileName: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRequestFeedback: () -> Unit
) {
    val context = LocalContext.current
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
    val coroutineScope = rememberCoroutineScope()

    var workbookModel by remember { mutableStateOf<MutableExcelWorkbook?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var revisionCounter by remember { mutableIntStateOf(0) }

    var selectedSheetIdx by remember { mutableIntStateOf(0) }
    var selectedRowIdx by remember { mutableIntStateOf(0) }
    var selectedColIdx by remember { mutableIntStateOf(0) }
    var formulaBarText by remember { mutableStateOf("") }
    var isEditingFormula by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val undoStack = remember { mutableListOf<String>() }
    val redoStack = remember { mutableListOf<String>() }

    fun snapshotState() {
        val wb = workbookModel ?: return
        val current = wb.sheets.getOrNull(selectedSheetIdx) ?: return
        val serialized = current.rows.joinToString("\n") { it.joinToString("\t") }
        undoStack.add(serialized)
        redoStack.clear()
        if (undoStack.size > 25) undoStack.removeAt(0)
    }

    var cellAlignment by remember { mutableStateOf(TextAlign.Start) }

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(0.55f, 2.4f)
    }

    // Fluent Excel Palette
    val bgPrimary = if (isDark) Color(0xFF0F1110) else Color(0xFFF8FAF9)
    val surfaceElevated = if (isDark) Color(0xFF171A18) else Color(0xFFFFFFFF)
    val ribbonBg = if (isDark) Color(0xFF1E2220) else Color(0xFFF1F5F2)
    val gridBorder = if (isDark) Color(0xFF2C322F) else Color(0xFFD5DFD9)
    val headerBandFill = if (isDark) Color(0xFF222825) else Color(0xFFE6EFEA)
    val selectedCellBorder = Color(0xFF107C41)
    val selectedCellFill = Color(0xFF107C41).copy(alpha = 0.14f)
    val activeHeaderFill = Color(0xFF107C41).copy(alpha = 0.24f)
    val textPrimary = if (isDark) Color(0xFFF3F6F4) else Color(0xFF1B1F1D)
    val textMuted = if (isDark) Color(0xFF939D97) else Color(0xFF5A655F)
    val excelGreen = Color(0xFF107C41)

    LaunchedEffect(uri) {
        isLoading = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                val model = parseExcelToMutable(context, uri, fileName)
                withContext(Dispatchers.Main) {
                    workbookModel = model
                    val firstCell = model.sheets.firstOrNull()?.rows?.firstOrNull()?.firstOrNull().orEmpty()
                    formulaBarText = firstCell
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg = e.localizedMessage ?: "Failed to read Excel workbook"
                    isLoading = false
                }
            }
        }
    }

    val saveWorkbook = {
        val wb = workbookModel
        if (wb != null) {
            isSaving = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    saveWorkbookToUri(context, uri, fileName, wb)
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        hasUnsavedChanges = false
                        Toast.makeText(context, "Saved changes to $fileName", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isSaving = false
                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgPrimary)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // ── 1. Clean Top App Bar ──────────────────────────────────────────────────
        TopAppBar(
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textPrimary
                        )
                        if (hasUnsavedChanges) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(excelGreen)
                            )
                        }
                    }
                    val activeSheetName = workbookModel?.sheets?.getOrNull(selectedSheetIdx)?.name ?: "Workbook"
                    Text(
                        text = if (isSaving) "Saving changes…" else "$activeSheetName • ${if (hasUnsavedChanges) "Modified" else "Ready"}",
                        fontSize = 11.sp,
                        color = excelGreen,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = { isSearchActive = !isSearchActive }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Find in sheet",
                        tint = if (isSearchActive) excelGreen else textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRequestFeedback) {
                    Icon(
                        imageVector = Icons.Filled.RateReview,
                        contentDescription = "Extraction Feedback",
                        tint = excelGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share Workbook",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSaving) { saveWorkbook() },
                    color = excelGreen
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Save",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Save",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaceElevated)
        )

        // ── 2. In-Document Search Bar ────────────────────────────────────────────
        if (isSearchActive) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ribbonBg,
                border = BorderStroke(0.5.dp, gridBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = excelGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = textPrimary),
                        cursorBrush = SolidColor(excelGreen),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Find values or text in sheet…", fontSize = 13.sp, color = textMuted)
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = excelGreen, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Loading spreadsheet…", fontSize = 13.sp, color = textMuted)
                }
            }
        } else if (errorMsg != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Unable to open workbook: $errorMsg",
                    fontSize = 14.sp,
                    color = Color(0xFFD13438),
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else if (workbookModel != null) {
            val wb = workbookModel!!
            // Read revisionCounter so Compose recomposes when cells/rows/columns mutate
            @Suppress("UNUSED_VARIABLE")
            val rev = revisionCounter
            val currentSheet = wb.sheets.getOrNull(selectedSheetIdx)

            // ── 3. Scrollable Excel Ribbon Toolbar ───────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ribbonBg,
                border = BorderStroke(0.5.dp, gridBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty() && currentSheet != null) {
                                val lastState = undoStack.removeAt(undoStack.lastIndex)
                                val currentState = currentSheet.rows.joinToString("\n") { it.joinToString("\t") }
                                redoStack.add(currentState)
                                val restoredRows = lastState.split("\n").map { line -> line.split("\t").toMutableList() }.toMutableList()
                                currentSheet.rows.clear()
                                currentSheet.rows.addAll(restoredRows)
                                normalizeSheetDimensions(currentSheet)
                                hasUnsavedChanges = true
                                revisionCounter++
                            }
                        },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.isNotEmpty()) textPrimary else textMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty() && currentSheet != null) {
                                val redoState = redoStack.removeAt(redoStack.lastIndex)
                                val currentState = currentSheet.rows.joinToString("\n") { it.joinToString("\t") }
                                undoStack.add(currentState)
                                val restoredRows = redoState.split("\n").map { line -> line.split("\t").toMutableList() }.toMutableList()
                                currentSheet.rows.clear()
                                currentSheet.rows.addAll(restoredRows)
                                normalizeSheetDimensions(currentSheet)
                                hasUnsavedChanges = true
                                revisionCounter++
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) textPrimary else textMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(modifier = Modifier.height(18.dp).width(1.dp).background(gridBorder))

                    // + Row
                    ExcelRibbonChip(
                        icon = Icons.Filled.Add,
                        label = "Row",
                        accentColor = excelGreen,
                        onClick = {
                            if (currentSheet != null) {
                                snapshotState()
                                val colCount = currentSheet.headers.size.coerceAtLeast(1)
                                val insertAt = if (selectedRowIdx in currentSheet.rows.indices) selectedRowIdx + 1 else currentSheet.rows.size
                                currentSheet.rows.add(insertAt, MutableList(colCount) { "" })
                                selectedRowIdx = insertAt
                                formulaBarText = ""
                                hasUnsavedChanges = true
                                revisionCounter++
                            }
                        }
                    )

                    // + Column
                    ExcelRibbonChip(
                        icon = Icons.Filled.Add,
                        label = "Column",
                        accentColor = excelGreen,
                        onClick = {
                            if (currentSheet != null) {
                                snapshotState()
                                val nextIdx = currentSheet.headers.size
                                currentSheet.headers.add(getColumnLetter(nextIdx))
                                currentSheet.rows.forEach { it.add("") }
                                selectedColIdx = nextIdx
                                formulaBarText = ""
                                hasUnsavedChanges = true
                                revisionCounter++
                            }
                        }
                    )

                    // Delete Row
                    if (currentSheet != null && selectedRowIdx in currentSheet.rows.indices && currentSheet.rows.size > 1) {
                        ExcelRibbonChip(
                            icon = Icons.Filled.Delete,
                            label = "Delete Row",
                            accentColor = Color(0xFFD13438),
                            onClick = {
                                snapshotState()
                                currentSheet.rows.removeAt(selectedRowIdx)
                                selectedRowIdx = selectedRowIdx.coerceAtMost(currentSheet.rows.lastIndex)
                                formulaBarText = currentSheet.rows.getOrNull(selectedRowIdx)?.getOrNull(selectedColIdx).orEmpty()
                                hasUnsavedChanges = true
                                revisionCounter++
                            }
                        )
                    }

                    // Quick Formula (=SUM)
                    ExcelRibbonChip(
                        icon = Icons.Filled.Functions,
                        label = "AutoSum",
                        accentColor = excelGreen,
                        onClick = {
                            if (currentSheet != null && selectedRowIdx > 0 && selectedColIdx >= 0) {
                                val colLet = getColumnLetter(selectedColIdx)
                                val sumFormula = "=SUM(${colLet}1:${colLet}${selectedRowIdx})"
                                snapshotState()
                                currentSheet.rows[selectedRowIdx][selectedColIdx] = sumFormula
                                formulaBarText = sumFormula
                                hasUnsavedChanges = true
                                revisionCounter++
                            }
                        }
                    )

                    Box(modifier = Modifier.height(18.dp).width(1.dp).background(gridBorder))

                    // Alignment buttons
                    IconButton(
                        onClick = { cellAlignment = TextAlign.Start },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatAlignLeft,
                            contentDescription = "Align Left",
                            tint = if (cellAlignment == TextAlign.Start) excelGreen else textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { cellAlignment = TextAlign.Center },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.FormatAlignCenter,
                            contentDescription = "Align Center",
                            tint = if (cellAlignment == TextAlign.Center) excelGreen else textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { cellAlignment = TextAlign.End },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatAlignRight,
                            contentDescription = "Align Right",
                            tint = if (cellAlignment == TextAlign.End) excelGreen else textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(modifier = Modifier.height(18.dp).width(1.dp).background(gridBorder))

                    // Zoom Controls
                    IconButton(
                        onClick = { zoomScale = (zoomScale - 0.15f).coerceIn(0.55f, 2.4f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out", tint = textPrimary, modifier = Modifier.size(17.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = surfaceElevated,
                        border = BorderStroke(0.5.dp, gridBorder),
                        modifier = Modifier.clickable { zoomScale = 1.0f }
                    ) {
                        Text(
                            text = "${(zoomScale * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = excelGreen,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.15f).coerceIn(0.55f, 2.4f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In", tint = textPrimary, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // ── 4. Formula & Coordinate Bar ──────────────────────────────────────
            val cellCoord = if (selectedRowIdx >= 0 && selectedColIdx >= 0) {
                "${getColumnLetter(selectedColIdx)}${selectedRowIdx + 1}"
            } else {
                "A1"
            }

            fun commitFormulaEdit() {
                if (currentSheet != null && selectedRowIdx in currentSheet.rows.indices) {
                    val row = currentSheet.rows[selectedRowIdx]
                    if (selectedColIdx in row.indices && row[selectedColIdx] != formulaBarText) {
                        snapshotState()
                        row[selectedColIdx] = formulaBarText
                        hasUnsavedChanges = true
                        revisionCounter++
                    }
                }
                isEditingFormula = false
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = surfaceElevated,
                border = BorderStroke(0.5.dp, gridBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cell Reference Pill (e.g. B4)
                    Box(
                        modifier = Modifier
                            .width(54.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(headerBandFill)
                            .border(0.5.dp, gridBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cellCoord,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = excelGreen,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "fx",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = textMuted
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgPrimary)
                            .border(
                                width = if (isEditingFormula) 1.5.dp else 0.5.dp,
                                color = if (isEditingFormula) excelGreen else gridBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = formulaBarText,
                            onValueChange = {
                                formulaBarText = it
                                isEditingFormula = true
                            },
                            textStyle = TextStyle(
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = textPrimary
                            ),
                            cursorBrush = SolidColor(excelGreen),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitFormulaEdit() }),
                            decorationBox = { inner ->
                                if (formulaBarText.isEmpty()) {
                                    Text(
                                        text = "Enter value or formula (e.g. =SUM(B2:B10))",
                                        fontSize = 11.5.sp,
                                        color = textMuted.copy(alpha = 0.65f)
                                    )
                                }
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isEditingFormula) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { commitFormulaEdit() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Commit edit", tint = excelGreen, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                formulaBarText = currentSheet?.rows?.getOrNull(selectedRowIdx)?.getOrNull(selectedColIdx).orEmpty()
                                isEditingFormula = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel edit", tint = textMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── 5. True Sticky 2D Spreadsheet Grid ───────────────────────────────
            if (currentSheet != null) {
                val horizontalScroll = rememberScrollState()
                val colCount = currentSheet.headers.size.coerceAtLeast(1)

                // Compute adaptive base width per column from cell text lengths
                val colWidthsDp: List<Dp> = remember(selectedSheetIdx, revisionCounter, zoomScale) {
                    (0 until colCount).map { cIdx ->
                        var maxChars = 6
                        for (r in 0 until minOf(currentSheet.rows.size, 80)) {
                            val cellLen = currentSheet.rows[r].getOrNull(cIdx)?.length ?: 0
                            // Ignore single-cell banner rows when sizing column A
                            val nonBlankCols = currentSheet.rows[r].count { it.isNotBlank() }
                            if (cIdx == 0 && nonBlankCols == 1 && colCount > 2) continue
                            if (cellLen > maxChars) maxChars = cellLen
                        }
                        val baseWidth = (maxChars * 7.8f + 28f).coerceIn(96f, 240f)
                        (baseWidth * zoomScale).dp
                    }
                }

                val rowNumberColWidth = (46f * zoomScale).coerceIn(36f, 76f).dp
                val headerRowHeight = (32f * zoomScale).coerceIn(24f, 56f).dp
                val dataRowHeight = (34f * zoomScale).coerceIn(24f, 60f).dp
                val cellFontSp = (11.5f * zoomScale).coerceIn(8.5f, 22f).sp
                val headerFontSp = (11f * zoomScale).coerceIn(8f, 20f).sp

                // Identify the main table header row (first row with >= 2 non-blank cells) for subtle visual styling
                val detectedHeaderRowIdx = remember(selectedSheetIdx, revisionCounter) {
                    currentSheet.rows.indexOfFirst { row -> row.count { it.isNotBlank() } >= 2 }.takeIf { it in 0..4 } ?: 0
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .transformable(transformState)
                ) {
                    // 5a. Sticky Top Column Letter Header Row (Pinned at top, scrolls horizontally)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerRowHeight)
                            .background(headerBandFill)
                            .border(0.5.dp, gridBorder)
                    ) {
                        // Top-left corner "#" box (Pinned both horizontally & vertically)
                        Box(
                            modifier = Modifier
                                .width(rowNumberColWidth)
                                .fillMaxHeight()
                                .background(headerBandFill)
                                .border(0.5.dp, gridBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#",
                                fontSize = headerFontSp,
                                color = textMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Horizontally synced A, B, C, D... column letters
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(horizontalScroll)
                        ) {
                            for (cIdx in 0 until colCount) {
                                val colLetter = getColumnLetter(cIdx)
                                val isColActive = (cIdx == selectedColIdx)
                                Box(
                                    modifier = Modifier
                                        .width(colWidthsDp[cIdx])
                                        .fillMaxHeight()
                                        .background(if (isColActive) activeHeaderFill else headerBandFill)
                                        .border(0.5.dp, if (isColActive) excelGreen else gridBorder)
                                        .clickable {
                                            selectedColIdx = cIdx
                                            if (selectedRowIdx !in currentSheet.rows.indices) selectedRowIdx = 0
                                            formulaBarText = currentSheet.rows.getOrNull(selectedRowIdx)?.getOrNull(cIdx).orEmpty()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = colLetter,
                                        fontSize = headerFontSp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isColActive) excelGreen else textPrimary
                                    )
                                }
                            }
                        }
                    }

                    // 5b. Vertically Virtualized Rows with Sticky Left Row Numbers
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(currentSheet.rows) { rIdx, rowValues ->
                            val isRowActive = (rIdx == selectedRowIdx)
                            val isTableHeaderRow = (rIdx == detectedHeaderRowIdx)
                            val rowBg = when {
                                isTableHeaderRow -> excelGreen.copy(alpha = if (isDark) 0.22f else 0.12f)
                                rIdx % 2 == 1 -> if (isDark) Color(0xFF141816) else Color(0xFFF3F7F4)
                                else -> surfaceElevated
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(dataRowHeight)
                            ) {
                                // Pinned Left Row Number (1, 2, 3...)
                                Box(
                                    modifier = Modifier
                                        .width(rowNumberColWidth)
                                        .fillMaxHeight()
                                        .background(if (isRowActive) activeHeaderFill else headerBandFill)
                                        .border(0.5.dp, if (isRowActive) excelGreen else gridBorder)
                                        .clickable {
                                            selectedRowIdx = rIdx
                                            if (selectedColIdx !in 0 until colCount) selectedColIdx = 0
                                            formulaBarText = rowValues.getOrNull(selectedColIdx).orEmpty()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${rIdx + 1}",
                                        fontSize = (headerFontSp.value * 0.92f).sp,
                                        color = if (isRowActive) excelGreen else textMuted,
                                        fontWeight = if (isRowActive) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                // Horizontally synced cells for this row
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(rowBg)
                                        .horizontalScroll(horizontalScroll)
                                ) {
                                    for (cIdx in 0 until colCount) {
                                        val cellVal = rowValues.getOrNull(cIdx).orEmpty()
                                        val isCellSelected = (rIdx == selectedRowIdx && cIdx == selectedColIdx)
                                        val evaluatedText = if (cellVal.startsWith("=")) {
                                            ExcelFormulaEvaluator.evaluateCell(cellVal, currentSheet)
                                        } else {
                                            cellVal
                                        }
                                        val isSearchMatch = searchQuery.isNotBlank() &&
                                                evaluatedText.contains(searchQuery, ignoreCase = true)

                                        val cellBg = when {
                                            isCellSelected -> selectedCellFill
                                            isSearchMatch -> Color(0xFFFFF176).copy(alpha = 0.45f)
                                            else -> Color.Transparent
                                        }

                                        val isNumericCell = evaluatedText.trim()
                                            .replace(",", "")
                                            .replace("$", "")
                                            .replace("%", "")
                                            .toDoubleOrNull() != null

                                        val effectiveAlignment = when {
                                            cellAlignment != TextAlign.Start -> cellAlignment
                                            isNumericCell && !isTableHeaderRow -> TextAlign.End
                                            else -> TextAlign.Start
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(colWidthsDp[cIdx])
                                                .fillMaxHeight()
                                                .background(cellBg)
                                                .border(
                                                    width = if (isCellSelected) 2.dp else if (isSearchMatch) 1.5.dp else 0.5.dp,
                                                    color = when {
                                                        isCellSelected -> selectedCellBorder
                                                        isSearchMatch -> Color(0xFFFBC02D)
                                                        else -> gridBorder
                                                    }
                                                )
                                                .clickable {
                                                    selectedRowIdx = rIdx
                                                    selectedColIdx = cIdx
                                                    formulaBarText = cellVal
                                                    isEditingFormula = false
                                                }
                                                .padding(horizontal = 8.dp),
                                            contentAlignment = when (effectiveAlignment) {
                                                TextAlign.Center -> Alignment.Center
                                                TextAlign.End -> Alignment.CenterEnd
                                                else -> Alignment.CenterStart
                                            }
                                        ) {
                                            Text(
                                                text = evaluatedText,
                                                fontSize = cellFontSp,
                                                textAlign = effectiveAlignment,
                                                fontWeight = when {
                                                    isTableHeaderRow -> FontWeight.Bold
                                                    cellVal.startsWith("=") -> FontWeight.SemiBold
                                                    else -> FontWeight.Normal
                                                },
                                                color = when {
                                                    isSearchMatch && !isDark -> Color(0xFF4E342E)
                                                    isTableHeaderRow -> excelGreen
                                                    else -> textPrimary
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 6. Bottom Excel Status Bar (Live Column SUM / AVG / COUNT) ──────
                val colNumbers = remember(selectedSheetIdx, selectedColIdx, revisionCounter) {
                    currentSheet.rows.mapNotNull { row ->
                        val raw = row.getOrNull(selectedColIdx).orEmpty()
                        val eval = if (raw.startsWith("=")) ExcelFormulaEvaluator.evaluateCell(raw, currentSheet) else raw
                        eval.replace(",", "").replace("$", "").replace("€", "").replace("£", "").replace("₹", "").trim().toDoubleOrNull()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ribbonBg,
                    border = BorderStroke(0.5.dp, gridBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentSheet.rows.size} Rows × $colCount Cols",
                            fontSize = 11.sp,
                            color = textMuted,
                            fontWeight = FontWeight.Medium
                        )
                        if (colNumbers.isNotEmpty()) {
                            val sum = colNumbers.sum()
                            val avg = colNumbers.average()
                            val colLet = getColumnLetter(selectedColIdx.coerceAtLeast(0))
                            Text(
                                text = "Col $colLet  •  COUNT: ${colNumbers.size}  •  SUM: ${String.format(Locale.US, "%,.2f", sum)}  •  AVG: ${String.format(Locale.US, "%,.2f", avg)}",
                                fontSize = 11.sp,
                                color = excelGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // ── 7. Multi-Sheet Tab Strip + Add Sheet Button ──────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = surfaceElevated,
                border = BorderStroke(0.5.dp, gridBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedSheetIdx.coerceIn(0, (wb.sheets.size - 1).coerceAtLeast(0)),
                        modifier = Modifier.weight(1f),
                        containerColor = surfaceElevated,
                        contentColor = excelGreen,
                        edgePadding = 8.dp,
                        indicator = { tabPositions ->
                            if (selectedSheetIdx in tabPositions.indices) {
                                TabRowDefaults.PrimaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSheetIdx]),
                                    color = excelGreen
                                )
                            }
                        }
                    ) {
                        wb.sheets.forEachIndexed { index, sheet ->
                            Tab(
                                selected = selectedSheetIdx == index,
                                onClick = {
                                    selectedSheetIdx = index
                                    selectedRowIdx = 0
                                    selectedColIdx = 0
                                    formulaBarText = sheet.rows.firstOrNull()?.firstOrNull().orEmpty()
                                },
                                text = {
                                    Text(
                                        text = sheet.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedSheetIdx == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedSheetIdx == index) excelGreen else textPrimary
                                    )
                                }
                            )
                        }
                    }

                    // + New Sheet button
                    IconButton(
                        onClick = {
                            val newIndex = wb.sheets.size + 1
                            val cols = 5
                            val newSheet = MutableExcelSheet(
                                name = "Sheet $newIndex",
                                headers = MutableList(cols) { getColumnLetter(it) },
                                rows = MutableList(10) { MutableList(cols) { "" } }
                            )
                            wb.sheets.add(newSheet)
                            selectedSheetIdx = wb.sheets.lastIndex
                            selectedRowIdx = 0
                            selectedColIdx = 0
                            formulaBarText = ""
                            hasUnsavedChanges = true
                            revisionCounter++
                        },
                        modifier = Modifier.padding(horizontal = 4.dp).size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Sheet",
                            tint = excelGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcelRibbonChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() },
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun getColumnLetter(colIndex: Int): String {
    var c = colIndex
    var result = ""
    while (c >= 0) {
        val rem = c % 26
        result = ('A' + rem) + result
        c = (c / 26) - 1
    }
    return result
}

private fun normalizeSheetDimensions(sheet: MutableExcelSheet) {
    val maxRowCols = sheet.rows.maxOfOrNull { it.size } ?: 0
    val targetCols = max(maxRowCols, sheet.headers.size).coerceAtLeast(4)
    sheet.headers.clear()
    for (c in 0 until targetCols) {
        sheet.headers.add(getColumnLetter(c))
    }
    if (sheet.rows.isEmpty()) {
        sheet.rows.add(MutableList(targetCols) { "" })
    } else {
        for (row in sheet.rows) {
            while (row.size < targetCols) {
                row.add("")
            }
        }
    }
}

private fun parseExcelToMutable(context: Context, uri: Uri, fileName: String): MutableExcelWorkbook {
    if (fileName.endsWith(".csv", ignoreCase = true)) {
        return parseCsvToMutable(context, uri, fileName)
    }

    val inputStream: InputStream = context.contentResolver.openInputStream(uri)
        ?: error("Cannot open input stream for $uri")

    val poiWb = WorkbookFactory.create(inputStream)
    val parsedSheets = mutableListOf<MutableExcelSheet>()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    for (s in 0 until poiWb.numberOfSheets) {
        val sheet = poiWb.getSheetAt(s)
        val sheetName = sheet.sheetName ?: "Sheet ${s + 1}"

        val allRows = mutableListOf<MutableList<String>>()
        var maxCols = 4

        val lastRowNum = sheet.lastRowNum
        for (rIdx in 0..lastRowNum) {
            val r = sheet.getRow(rIdx)
            if (r == null) {
                // Preserve blank separator rows inside the active range
                if (allRows.isNotEmpty() && rIdx < lastRowNum) {
                    allRows.add(mutableListOf())
                }
                continue
            }
            val rowCells = mutableListOf<String>()
            val lastCellNum = r.lastCellNum.toInt().coerceAtLeast(0)
            if (lastCellNum > maxCols) maxCols = lastCellNum

            for (c in 0 until lastCellNum) {
                val cell = r.getCell(c)
                if (cell == null) {
                    rowCells.add("")
                    continue
                }
                val valStr = when (cell.cellType) {
                    CellType.STRING -> cell.stringCellValue ?: ""
                    CellType.NUMERIC -> {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            cell.dateCellValue?.let { dateFormat.format(it) } ?: ""
                        } else {
                            val d = cell.numericCellValue
                            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                        }
                    }
                    CellType.BOOLEAN -> cell.booleanCellValue.toString()
                    CellType.FORMULA -> {
                        "=" + (cell.cellFormula ?: "")
                    }
                    else -> ""
                }
                rowCells.add(valStr)
            }
            allRows.add(rowCells)
        }

        // Pad all rows to maxCols so multi-column tables below a 1-cell title banner keep all columns aligned!
        val colHeaders = MutableList(maxCols) { getColumnLetter(it) }
        if (allRows.isEmpty()) {
            allRows.add(MutableList(maxCols) { "" })
        } else {
            for (row in allRows) {
                while (row.size < maxCols) {
                    row.add("")
                }
            }
        }

        parsedSheets.add(
            MutableExcelSheet(
                name = sheetName,
                headers = colHeaders,
                rows = allRows
            )
        )
    }

    poiWb.close()
    inputStream.close()

    return MutableExcelWorkbook(
        title = fileName,
        sheets = parsedSheets
    )
}

private fun parseCsvToMutable(context: Context, uri: Uri, fileName: String): MutableExcelWorkbook {
    val inputStream: InputStream = context.contentResolver.openInputStream(uri)
        ?: error("Cannot open input stream for $uri")

    val lines = inputStream.bufferedReader().use { it.readLines() }
    val allRows = mutableListOf<MutableList<String>>()
    var maxCols = 4

    for (line in lines) {
        if (line.isBlank()) continue
        val parsed = parseCsvLineToMutable(line)
        if (parsed.size > maxCols) maxCols = parsed.size
        allRows.add(parsed)
    }

    val colHeaders = MutableList(maxCols) { getColumnLetter(it) }
    if (allRows.isEmpty()) {
        allRows.add(MutableList(maxCols) { "" })
    } else {
        for (row in allRows) {
            while (row.size < maxCols) {
                row.add("")
            }
        }
    }

    return MutableExcelWorkbook(
        title = fileName,
        sheets = mutableListOf(
            MutableExcelSheet(
                name = fileName.removeSuffix(".csv").removeSuffix(".CSV"),
                headers = colHeaders,
                rows = allRows
            )
        )
    )
}

private fun parseCsvLineToMutable(line: String): MutableList<String> {
    val result = mutableListOf<String>()
    val cur = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '\"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    cur.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            ch == ',' && !inQuotes -> {
                result.add(cur.toString().trim())
                cur.clear()
            }
            else -> {
                cur.append(ch)
            }
        }
        i++
    }
    result.add(cur.toString().trim())
    return result
}

private fun saveWorkbookToUri(context: Context, uri: Uri, fileName: String, model: MutableExcelWorkbook) {
    val outputStream: OutputStream = context.contentResolver.openOutputStream(uri, "wt")
        ?: error("Cannot open output stream for $uri")

    if (fileName.endsWith(".csv", ignoreCase = true)) {
        outputStream.bufferedWriter().use { writer ->
            val sheet = model.sheets.firstOrNull() ?: return@use
            for (row in sheet.rows) {
                writer.write(row.joinToString(",") { escapeCsv(it) })
                writer.newLine()
            }
        }
        return
    }

    val workbook = XSSFWorkbook()
    val headerFont = workbook.createFont().apply {
        bold = true
        fontName = "Calibri"
        fontHeightInPoints = 11.toShort()
        color = IndexedColors.WHITE.index
    }
    val headerStyle = workbook.createCellStyle().apply {
        fillForegroundColor = IndexedColors.SEA_GREEN.index
        fillPattern = FillPatternType.SOLID_FOREGROUND
        setFont(headerFont)
        borderTop = BorderStyle.THIN
        borderBottom = BorderStyle.THIN
        borderLeft = BorderStyle.THIN
        borderRight = BorderStyle.THIN
    }
    val bodyStyle = workbook.createCellStyle().apply {
        borderTop = BorderStyle.THIN
        borderBottom = BorderStyle.THIN
        borderLeft = BorderStyle.THIN
        borderRight = BorderStyle.THIN
    }

    for (sheetModel in model.sheets) {
        val sheet = workbook.createSheet(sheetModel.name.take(31))
        sheet.setDisplayGridlines(true)

        val headerRowIdx = sheetModel.rows.indexOfFirst { row -> row.count { it.isNotBlank() } >= 2 }.takeIf { it in 0..4 } ?: 0

        sheetModel.rows.forEachIndexed { rIdx, rowValues ->
            val dRow = sheet.createRow(rIdx)
            rowValues.forEachIndexed { cIdx, valStr ->
                val cell = dRow.createCell(cIdx)
                val trimmed = valStr.trim()
                if (trimmed.startsWith("=") && trimmed.length > 1) {
                    try {
                        cell.cellFormula = trimmed.substring(1)
                    } catch (_: Exception) {
                        cell.setCellValue(trimmed)
                    }
                } else {
                    val num = trimmed.replace(",", "").toDoubleOrNull()
                    if (num != null && rIdx != headerRowIdx) {
                        cell.setCellValue(num)
                    } else {
                        cell.setCellValue(valStr)
                    }
                }
                cell.cellStyle = if (rIdx == headerRowIdx) headerStyle else bodyStyle
            }
        }
    }

    workbook.write(outputStream)
    workbook.close()
    outputStream.close()
}

private fun escapeCsv(valStr: String): String {
    return if (valStr.contains(",") || valStr.contains("\"") || valStr.contains("\n")) {
        "\"" + valStr.replace("\"", "\"\"") + "\""
    } else {
        valStr
    }
}
