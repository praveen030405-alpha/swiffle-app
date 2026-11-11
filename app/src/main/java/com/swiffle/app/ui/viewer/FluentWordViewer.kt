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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.OutputStream

sealed class EditableWordBlock {
    data class Paragraph(
        val text: String,
        val isHeading: Boolean = false,
        val headingLevel: Int = 0, // 0 = Body, 1 = H1, 2 = H2, 3 = Title
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isBullet: Boolean = false,
        val alignment: Int = 0, // 0 = Start, 1 = Center, 2 = End
        val fontSize: Int = 15
    ) : EditableWordBlock()

    data class Table(
        val rows: List<List<String>>
    ) : EditableWordBlock()
}

/**
 * Windows 11 Fluent Microsoft Word Studio:
 *  - Reactive immutable state updates so Bold, Italic, H1, H2, Bullet, and Alignment buttons update immediately
 *  - Responsive Print-Layout Paper Canvas (zoom scales typography and page layout smoothly without graphicsLayer clipping)
 *  - Safe multi-pass stream reader (re-opens URI stream on fallback so legacy .doc or text files never crash)
 *  - Live Bottom Status Bar with Word Count, Character Count, Block Count, and Zoom controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluentWordViewer(
    uri: Uri,
    fileName: String,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
    val coroutineScope = rememberCoroutineScope()

    val blockList = remember { mutableStateListOf<EditableWordBlock>() }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    var selectedBlockIndex by remember { mutableIntStateOf(0) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val undoStack = remember { mutableListOf<List<EditableWordBlock>>() }
    val redoStack = remember { mutableListOf<List<EditableWordBlock>>() }

    fun snapshotState() {
        undoStack.add(blockList.toList())
        redoStack.clear()
        if (undoStack.size > 25) undoStack.removeAt(0)
    }

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(0.6f, 2.2f)
    }

    // Fluent Word Palette
    val bgPrimary = if (isDark) Color(0xFF0E1014) else Color(0xFFEBEFF5)
    val surfaceElevated = if (isDark) Color(0xFF171A21) else Color(0xFFFFFFFF)
    val ribbonBg = if (isDark) Color(0xFF1D222B) else Color(0xFFF4F7FC)
    val paperColor = if (isDark) Color(0xFF181B20) else Color(0xFFFFFFFF)
    val strokeColor = if (isDark) Color(0xFF2D3340) else Color(0xFFD5DCE6)
    val textPrimary = if (isDark) Color(0xFFF2F5FA) else Color(0xFF1B1F24)
    val textMuted = if (isDark) Color(0xFF9099A6) else Color(0xFF5D6673)
    val wordBlue = Color(0xFF2B579A)

    LaunchedEffect(uri) {
        isLoading = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                val parsed = parseWordToEditable(context, uri)
                withContext(Dispatchers.Main) {
                    blockList.clear()
                    blockList.addAll(parsed)
                    selectedBlockIndex = if (parsed.isNotEmpty()) 0 else -1
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg = e.localizedMessage ?: "Failed to read Word document"
                    isLoading = false
                }
            }
        }
    }

    val saveWordDocument = {
        isSaving = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                saveDocxToUri(context, uri, blockList.toList())
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
                                    .background(wordBlue)
                            )
                        }
                    }
                    Text(
                        text = if (isSaving) "Saving document…" else "Microsoft Word • ${if (hasUnsavedChanges) "Modified" else "Print Layout"}",
                        fontSize = 11.sp,
                        color = wordBlue,
                        fontWeight = FontWeight.Medium
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
                        contentDescription = "Find in document",
                        tint = if (isSearchActive) wordBlue else textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share Document",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isSaving) { saveWordDocument() },
                    color = wordBlue
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

        // ── 2. Find Bar ──────────────────────────────────────────────────────────
        if (isSearchActive) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ribbonBg,
                border = BorderStroke(0.5.dp, strokeColor)
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
                        tint = wordBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = textPrimary),
                        cursorBrush = SolidColor(wordBlue),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Find in document…", fontSize = 13.sp, color = textMuted)
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
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // ── 3. Microsoft Word Formatting & Structure Ribbon ─────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ribbonBg,
            border = BorderStroke(0.5.dp, strokeColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val currentBlock = blockList.getOrNull(selectedBlockIndex)
                val currentPara = currentBlock as? EditableWordBlock.Paragraph

                // Undo
                IconButton(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            val last = undoStack.removeAt(undoStack.lastIndex)
                            redoStack.add(blockList.toList())
                            blockList.clear()
                            blockList.addAll(last)
                            hasUnsavedChanges = true
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
                        if (redoStack.isNotEmpty()) {
                            val next = redoStack.removeAt(redoStack.lastIndex)
                            undoStack.add(blockList.toList())
                            blockList.clear()
                            blockList.addAll(next)
                            hasUnsavedChanges = true
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

                Box(modifier = Modifier.height(18.dp).width(1.dp).background(strokeColor))

                // Bold
                WordToolbarButton(
                    icon = Icons.Filled.FormatBold,
                    label = "Bold",
                    isActive = currentPara?.isBold == true,
                    onClick = {
                        if (currentPara != null && selectedBlockIndex in blockList.indices) {
                            snapshotState()
                            blockList[selectedBlockIndex] = currentPara.copy(isBold = !currentPara.isBold)
                            hasUnsavedChanges = true
                        }
                    }
                )

                // Italic
                WordToolbarButton(
                    icon = Icons.Filled.FormatItalic,
                    label = "Italic",
                    isActive = currentPara?.isItalic == true,
                    onClick = {
                        if (currentPara != null && selectedBlockIndex in blockList.indices) {
                            snapshotState()
                            blockList[selectedBlockIndex] = currentPara.copy(isItalic = !currentPara.isItalic)
                            hasUnsavedChanges = true
                        }
                    }
                )

                // Heading 1
                WordToolbarButton(
                    icon = Icons.Filled.Title,
                    label = "H1",
                    isActive = currentPara?.headingLevel == 1,
                    onClick = {
                        if (currentPara != null && selectedBlockIndex in blockList.indices) {
                            snapshotState()
                            val nextLevel = if (currentPara.headingLevel == 1) 0 else 1
                            blockList[selectedBlockIndex] = currentPara.copy(
                                headingLevel = nextLevel,
                                isHeading = nextLevel > 0,
                                fontSize = if (nextLevel == 1) 20 else 15
                            )
                            hasUnsavedChanges = true
                        }
                    }
                )

                // Heading 2
                WordToolbarButton(
                    icon = Icons.Filled.FormatSize,
                    label = "H2",
                    isActive = currentPara?.headingLevel == 2,
                    onClick = {
                        if (currentPara != null && selectedBlockIndex in blockList.indices) {
                            snapshotState()
                            val nextLevel = if (currentPara.headingLevel == 2) 0 else 2
                            blockList[selectedBlockIndex] = currentPara.copy(
                                headingLevel = nextLevel,
                                isHeading = nextLevel > 0,
                                fontSize = if (nextLevel == 2) 17 else 15
                            )
                            hasUnsavedChanges = true
                        }
                    }
                )

                // Bulleted List
                WordToolbarButton(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    label = "List",
                    isActive = currentPara?.isBullet == true,
                    onClick = {
                        if (currentPara != null && selectedBlockIndex in blockList.indices) {
                            snapshotState()
                            blockList[selectedBlockIndex] = currentPara.copy(isBullet = !currentPara.isBullet)
                            hasUnsavedChanges = true
                        }
                    }
                )

                Box(modifier = Modifier.height(18.dp).width(1.dp).background(strokeColor))

                // Paragraph Alignment (Left, Center, Right)
                IconButton(
                    onClick = {
                        if (currentPara != null && selectedBlockIndex in blockList.indices) {
                            snapshotState()
                            blockList[selectedBlockIndex] = currentPara.copy(alignment = 0)
                            hasUnsavedChanges = true
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatAlignLeft,
                        contentDescription = "Align Left",
                        tint = if (currentPara?.alignment == 0) wordBlue else textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (currentPara != null && selectedBlockIndex in blockList.indices) {
                            snapshotState()
                            blockList[selectedBlockIndex] = currentPara.copy(alignment = 1)
                            hasUnsavedChanges = true
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.FormatAlignCenter,
                        contentDescription = "Align Center",
                        tint = if (currentPara?.alignment == 1) wordBlue else textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (currentPara != null && selectedBlockIndex in blockList.indices) {
                            snapshotState()
                            blockList[selectedBlockIndex] = currentPara.copy(alignment = 2)
                            hasUnsavedChanges = true
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatAlignRight,
                        contentDescription = "Align Right",
                        tint = if (currentPara?.alignment == 2) wordBlue else textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(modifier = Modifier.height(18.dp).width(1.dp).background(strokeColor))

                // Insert Paragraph
                WordToolbarButton(
                    icon = Icons.Filled.Add,
                    label = "Paragraph",
                    isActive = false,
                    onClick = {
                        snapshotState()
                        val insertIdx = if (selectedBlockIndex in blockList.indices) {
                            selectedBlockIndex + 1
                        } else {
                            blockList.size
                        }
                        blockList.add(insertIdx, EditableWordBlock.Paragraph(text = ""))
                        selectedBlockIndex = insertIdx
                        hasUnsavedChanges = true
                    }
                )

                // Insert Table
                WordToolbarButton(
                    icon = Icons.Filled.TableChart,
                    label = "Table",
                    isActive = false,
                    onClick = {
                        snapshotState()
                        val insertIdx = if (selectedBlockIndex in blockList.indices) {
                            selectedBlockIndex + 1
                        } else {
                            blockList.size
                        }
                        val newTable = EditableWordBlock.Table(
                            rows = listOf(
                                listOf("Column 1", "Column 2", "Column 3"),
                                listOf("", "", "")
                            )
                        )
                        blockList.add(insertIdx, newTable)
                        selectedBlockIndex = insertIdx
                        hasUnsavedChanges = true
                    }
                )

                // Delete Block
                if (selectedBlockIndex in blockList.indices && blockList.size > 1) {
                    WordToolbarButton(
                        icon = Icons.Filled.Delete,
                        label = "Delete",
                        isActive = false,
                        tint = Color(0xFFD13438),
                        onClick = {
                            snapshotState()
                            blockList.removeAt(selectedBlockIndex)
                            selectedBlockIndex = selectedBlockIndex.coerceAtMost(blockList.lastIndex)
                            hasUnsavedChanges = true
                        }
                    )
                }
            }
        }

        // ── 4. Print-Layout Paper Canvas (Responsive Zoom, No Clipping) ─────────
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = wordBlue, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Opening Word Document…", fontSize = 13.sp, color = textMuted)
                }
            }
        } else if (errorMsg != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Error: $errorMsg",
                    fontSize = 14.sp,
                    color = Color(0xFFD13438),
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .transformable(state = transformState),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item { Spacer(modifier = Modifier.height(14.dp)) }

                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = (760f * zoomScale).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, strokeColor, RoundedCornerShape(10.dp)),
                            color = paperColor,
                            shadowElevation = 4.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = (22f * zoomScale).coerceIn(14f, 40f).dp,
                                    vertical = (24f * zoomScale).coerceIn(16f, 44f).dp
                                )
                            ) {
                                blockList.forEachIndexed { index, block ->
                                    val isSelected = (index == selectedBlockIndex)

                                    when (block) {
                                        is EditableWordBlock.Paragraph -> {
                                            WordParagraphItem(
                                                para = block,
                                                isSelected = isSelected,
                                                zoomScale = zoomScale,
                                                searchQuery = searchQuery,
                                                textColor = textPrimary,
                                                mutedColor = textMuted,
                                                accentColor = wordBlue,
                                                onSelect = { selectedBlockIndex = index },
                                                onTextChange = { updatedText ->
                                                    blockList[index] = block.copy(text = updatedText)
                                                    selectedBlockIndex = index
                                                    hasUnsavedChanges = true
                                                }
                                            )
                                        }
                                        is EditableWordBlock.Table -> {
                                            WordTableItem(
                                                table = block,
                                                isSelected = isSelected,
                                                zoomScale = zoomScale,
                                                strokeColor = strokeColor,
                                                accentColor = wordBlue,
                                                textColor = textPrimary,
                                                onSelect = { selectedBlockIndex = index },
                                                onCellChange = { r, c, updated ->
                                                    val updatedRows = block.rows.mapIndexed { rIdx, row ->
                                                        if (rIdx == r) {
                                                            row.mapIndexed { cIdx, oldVal -> if (cIdx == c) updated else oldVal }
                                                        } else row
                                                    }
                                                    blockList[index] = block.copy(rows = updatedRows)
                                                    selectedBlockIndex = index
                                                    hasUnsavedChanges = true
                                                },
                                                onAddRow = {
                                                    snapshotState()
                                                    val colCount = block.rows.firstOrNull()?.size ?: 2
                                                    val newRows = block.rows.toMutableList().apply {
                                                        add(List(colCount) { "" })
                                                    }
                                                    blockList[index] = block.copy(rows = newRows)
                                                    hasUnsavedChanges = true
                                                },
                                                onAddCol = {
                                                    snapshotState()
                                                    val newRows = block.rows.map { it + "" }
                                                    blockList[index] = block.copy(rows = newRows)
                                                    hasUnsavedChanges = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(64.dp)) }
                }
            }

            // ── 5. Bottom Microsoft Word Status & Word Count Bar ─────────────────
            val allText = blockList.joinToString(" ") { block ->
                when (block) {
                    is EditableWordBlock.Paragraph -> block.text
                    is EditableWordBlock.Table -> block.rows.flatten().joinToString(" ")
                }
            }.trim()
            val wordCount = if (allText.isBlank()) 0 else allText.split(Regex("\\s+")).size
            val charCount = allText.length

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = surfaceElevated,
                border = BorderStroke(0.5.dp, strokeColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${blockList.size} Blocks  •  $wordCount Words  •  $charCount Characters",
                        fontSize = 11.sp,
                        color = textMuted,
                        fontWeight = FontWeight.Medium
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { zoomScale = (zoomScale - 0.15f).coerceIn(0.6f, 2.2f) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out", tint = textPrimary, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${(zoomScale * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = wordBlue,
                            modifier = Modifier
                                .clickable { zoomScale = 1.0f }
                                .padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { zoomScale = (zoomScale + 0.15f).coerceIn(0.6f, 2.2f) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In", tint = textPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val wordBlue = Color(0xFF2B579A)
    val activeBg = wordBlue.copy(alpha = 0.15f)

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .then(
                if (isActive) Modifier.border(1.dp, wordBlue, RoundedCornerShape(6.dp))
                else Modifier
            ),
        color = if (isActive) activeBg else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint ?: if (isActive) wordBlue else Color(0xFF707885),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = tint ?: if (isActive) wordBlue else Color(0xFF707885)
            )
        }
    }
}

@Composable
private fun WordParagraphItem(
    para: EditableWordBlock.Paragraph,
    isSelected: Boolean,
    zoomScale: Float,
    searchQuery: String = "",
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onSelect: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val fontWeight = when {
        para.isHeading || para.isBold -> FontWeight.Bold
        else -> FontWeight.Normal
    }

    val fontStyle = if (para.isItalic) FontStyle.Italic else FontStyle.Normal

    val baseSp = when (para.headingLevel) {
        1 -> 21f
        2 -> 17.5f
        3 -> 24f
        else -> para.fontSize.toFloat()
    }
    val scaledSp = (baseSp * zoomScale).coerceIn(10f, 38f).sp

    val textAlign = when (para.alignment) {
        1 -> TextAlign.Center
        2 -> TextAlign.End
        else -> TextAlign.Start
    }

    val isSearchMatch = searchQuery.isNotBlank() && para.text.contains(searchQuery, ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(
                when {
                    isSelected -> Modifier
                        .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.06f))
                    isSearchMatch -> Modifier
                        .border(1.dp, Color(0xFFFBC02D), RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFF59D).copy(alpha = 0.35f))
                    else -> Modifier
                }
            )
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (para.isBullet) {
                Text(
                    text = "• ",
                    color = accentColor,
                    fontSize = scaledSp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            BasicTextField(
                value = para.text,
                onValueChange = { updated ->
                    onTextChange(updated)
                },
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = scaledSp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textAlign = textAlign,
                    lineHeight = (scaledSp.value * 1.42f).sp
                ),
                cursorBrush = SolidColor(accentColor),
                decorationBox = { inner ->
                    if (para.text.isEmpty()) {
                        Text(
                            text = "Type paragraph text…",
                            color = mutedColor.copy(alpha = 0.5f),
                            fontSize = scaledSp,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WordTableItem(
    table: EditableWordBlock.Table,
    isSelected: Boolean,
    zoomScale: Float,
    strokeColor: Color,
    accentColor: Color,
    textColor: Color,
    onSelect: () -> Unit,
    onCellChange: (Int, Int, String) -> Unit,
    onAddRow: () -> Unit,
    onAddCol: () -> Unit
) {
    val cellWidth = (128f * zoomScale).coerceIn(84f, 260f).dp
    val cellFontSp = (12f * zoomScale).coerceIn(9f, 24f).sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else strokeColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Column {
                table.rows.forEachIndexed { rIdx, rowCells ->
                    Row(
                        modifier = Modifier
                            .background(if (rIdx == 0) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                            .border(0.5.dp, strokeColor)
                    ) {
                        rowCells.forEachIndexed { cIdx, cellValue ->
                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .border(0.5.dp, strokeColor)
                                    .padding(horizontal = 8.dp, vertical = 7.dp)
                            ) {
                                BasicTextField(
                                    value = cellValue,
                                    onValueChange = { updated ->
                                        onCellChange(rIdx, cIdx, updated)
                                    },
                                    textStyle = TextStyle(
                                        color = textColor,
                                        fontSize = cellFontSp,
                                        fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(accentColor),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isSelected) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "+ Add Row",
                    fontSize = 11.sp,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onAddRow() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+ Add Column",
                    fontSize = 11.sp,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onAddCol() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun parseWordToEditable(context: Context, uri: Uri): List<EditableWordBlock> {
    val blocks = mutableListOf<EditableWordBlock>()

    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val doc = XWPFDocument(inputStream)
            for (bodyElement in doc.bodyElements) {
                when (bodyElement) {
                    is org.apache.poi.xwpf.usermodel.XWPFParagraph -> {
                        val p = bodyElement
                        val fullText = p.text ?: ""
                        val styleLower = p.style?.lowercase().orEmpty()
                        val isHeading1 = styleLower.contains("heading1") || styleLower.contains("heading 1") || styleLower == "title"
                        val isHeading2 = styleLower.contains("heading2") || styleLower.contains("heading 2")
                        val isHeading = isHeading1 || isHeading2 || styleLower.contains("heading")
                        val headingLevel = when {
                            isHeading1 -> 1
                            isHeading2 -> 2
                            isHeading -> 1
                            else -> 0
                        }
                        val runs = p.runs
                        val isBold = runs.any { it.isBold }
                        val isItalic = runs.any { it.isItalic }
                        val isBullet = p.numID != null
                        val align = when (p.alignment) {
                            ParagraphAlignment.CENTER -> 1
                            ParagraphAlignment.RIGHT -> 2
                            else -> 0
                        }

                        if (fullText.isNotBlank() || blocks.isNotEmpty()) {
                            blocks.add(
                                EditableWordBlock.Paragraph(
                                    text = fullText,
                                    isHeading = isHeading,
                                    headingLevel = headingLevel,
                                    isBold = isBold,
                                    isItalic = isItalic,
                                    isBullet = isBullet,
                                    alignment = align,
                                    fontSize = if (headingLevel == 1) 20 else if (headingLevel == 2) 17 else 15
                                )
                            )
                        }
                    }
                    is org.apache.poi.xwpf.usermodel.XWPFTable -> {
                        val t = bodyElement
                        val tableRows = t.rows.map { row ->
                            row.tableCells.map { cell ->
                                cell.text?.trim() ?: ""
                            }
                        }
                        if (tableRows.isNotEmpty()) {
                            blocks.add(EditableWordBlock.Table(rows = tableRows))
                        }
                    }
                }
            }
            doc.close()
        }
    } catch (_: Exception) {
        // Safe fallback: open a fresh InputStream for plain text or legacy .doc streams
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readLines().forEach { line ->
                    blocks.add(EditableWordBlock.Paragraph(text = line))
                }
            }
        } catch (_: Exception) {}
    }

    if (blocks.isEmpty()) {
        blocks.add(EditableWordBlock.Paragraph(text = ""))
    }

    return blocks
}

private fun saveDocxToUri(context: Context, uri: Uri, blocks: List<EditableWordBlock>) {
    val outputStream: OutputStream = context.contentResolver.openOutputStream(uri, "wt")
        ?: error("Cannot open output stream for $uri")

    val doc = XWPFDocument()

    for (block in blocks) {
        when (block) {
            is EditableWordBlock.Paragraph -> {
                val p = doc.createParagraph()
                p.alignment = when (block.alignment) {
                    1 -> ParagraphAlignment.CENTER
                    2 -> ParagraphAlignment.RIGHT
                    else -> ParagraphAlignment.LEFT
                }
                val r = p.createRun()
                val textToWrite = if (block.isBullet && !block.text.startsWith("•")) {
                    "• ${block.text}"
                } else {
                    block.text
                }
                r.setText(textToWrite)
                r.isBold = block.isBold || block.isHeading
                r.isItalic = block.isItalic
                r.fontSize = when (block.headingLevel) {
                    1 -> 18
                    2 -> 15
                    else -> 12
                }
            }
            is EditableWordBlock.Table -> {
                if (block.rows.isNotEmpty()) {
                    val rowCount = block.rows.size
                    val colCount = block.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                    val table = doc.createTable(rowCount, colCount)
                    block.rows.forEachIndexed { rIdx, rowCells ->
                        val row = table.getRow(rIdx)
                        rowCells.forEachIndexed { cIdx, cellVal ->
                            val cell = row.getCell(cIdx) ?: row.createCell()
                            cell.text = cellVal
                        }
                    }
                }
            }
        }
    }

    doc.write(outputStream)
    doc.close()
    outputStream.close()
}
