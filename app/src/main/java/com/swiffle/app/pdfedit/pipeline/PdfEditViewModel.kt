package com.swiffle.app.pdfedit.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiffle.app.pdfedit.core.ast.PdfCoordinateMapper
import com.swiffle.app.pdfedit.core.ast.PdfPageDom
import com.swiffle.app.pdfedit.core.ast.PdfStreamRecompiler
import com.swiffle.app.pdfedit.core.ast.PdfTextAstExtractor
import com.swiffle.app.pdfedit.core.ast.PdfTextLineNode
import com.swiffle.app.pdfedit.core.font.ChameleonFontResolver
import com.swiffle.app.pdfedit.core.font.FontDna
import com.swiffle.app.pdfedit.core.font.ResolvedChameleonFont
import com.swiffle.app.pdfedit.core.typography.HzMicroTypographySolver
import com.swiffle.app.pdfedit.core.typography.MicroTypographyResult
import com.swiffle.app.pdfedit.core.vision.InpaintedPaperResult
import com.swiffle.app.pdfedit.core.vision.OpticalStyleDna
import com.swiffle.app.pdfedit.core.vision.OpticalStyleProbe
import com.swiffle.app.pdfedit.core.vision.WaveletPaperInpainter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class ModifiedTextRecord(
    val lineId: String,
    val originalText: String,
    val newText: String,
    val originalLine: PdfTextLineNode,
    val typographyResult: MicroTypographyResult,
    val fontDna: FontDna,
    val resolvedFont: ResolvedChameleonFont,
    val opticalStyle: OpticalStyleDna,
    val inpaintResult: InpaintedPaperResult
)

enum class PdfEditTool {
    TEXT_EDIT,
    AI_OCR,
    ADD_TEXT,
    INPAINT_ERASER,
    HIGHLIGHT,
    FIT_SCREEN
}

data class PdfEditUiState(
    val isLoading: Boolean = false,
    val isOcrScanning: Boolean = false,
    val activeTool: PdfEditTool = PdfEditTool.TEXT_EDIT,
    val errorMessage: String? = null,
    val pageIndex: Int = 0,
    val pageDom: PdfPageDom? = null,
    val coordinateMapper: PdfCoordinateMapper? = null,
    val selectedLine: PdfTextLineNode? = null,
    val currentEditText: String = "",
    val activeFontDna: FontDna? = null,
    val resolvedFont: ResolvedChameleonFont? = null,
    val opticalStyle: OpticalStyleDna? = null,
    val subPixelThickness: Float = 0f,
    val scannerSoftness: Float = 0.3f,
    val customInkColor: Color = Color(0xFF1E1E1E),
    val inpaintedPatches: Map<String, InpaintedPaperResult> = emptyMap(),
    val modifiedRecords: Map<String, ModifiedTextRecord> = emptyMap(),
    val highlightedLines: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
    val saveCompletedFile: File? = null
)

class PdfEditViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PdfEditUiState())
    val uiState: StateFlow<PdfEditUiState> = _uiState.asStateFlow()

    private var currentDocument: PDDocument? = null
    private var currentLoadedUri: Uri? = null
    private val undoStack = mutableListOf<Map<String, ModifiedTextRecord>>()
    private val redoStack = mutableListOf<Map<String, ModifiedTextRecord>>()
    private val undoLock = Any()

    // Multi-page edit persistence
    private val documentModifiedPages = ConcurrentHashMap<Int, Map<String, ModifiedTextRecord>>()
    private val documentInpaintedPatches = ConcurrentHashMap<Int, Map<String, InpaintedPaperResult>>()

    fun loadDocument(context: Context, uri: Uri, pageIndex: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            // Save state of the previously active page before switching
            val prevPageIndex = _uiState.value.pageIndex
            if (_uiState.value.modifiedRecords.isNotEmpty()) {
                documentModifiedPages[prevPageIndex] = _uiState.value.modifiedRecords
            }
            if (_uiState.value.inpaintedPatches.isNotEmpty()) {
                documentInpaintedPatches[prevPageIndex] = _uiState.value.inpaintedPatches
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val doc = if (currentDocument != null && currentLoadedUri == uri) {
                    currentDocument!!
                } else {
                    currentDocument?.close()
                    val inputStream = openDocumentInputStream(context, uri)
                        ?: throw IllegalArgumentException("Cannot open PDF stream: $uri")
                    val newDoc = PDDocument.load(inputStream)
                    currentDocument = newDoc
                    currentLoadedUri = uri
                    newDoc
                }

                val dom = PdfTextAstExtractor.extractPageDom(doc, pageIndex)
                val mapper = PdfCoordinateMapper(
                    pageWidthPt = dom.pageWidthPt,
                    pageHeightPt = dom.pageHeightPt,
                    rotationDegrees = dom.rotationDegrees
                )

                // Restore any modifications previously made to this specific page
                val restoredRecords = documentModifiedPages[pageIndex] ?: emptyMap()
                val restoredPatches = documentInpaintedPatches[pageIndex] ?: emptyMap()

                synchronized(undoLock) {
                    undoStack.clear()
                    redoStack.clear()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pageIndex = pageIndex,
                        pageDom = dom,
                        coordinateMapper = mapper,
                        selectedLine = null,
                        currentEditText = "",
                        modifiedRecords = restoredRecords,
                        inpaintedPatches = restoredPatches,
                        canUndo = false,
                        canRedo = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to parse PDF: ${e.message}"
                    )
                }
            }
        }
    }

    fun setActiveTool(tool: PdfEditTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    /**
     * Executes On-Demand PP-OCRv4 Neural OCR when requested by the user,
     * or when vector DOM text is empty.
     */
    fun performAiOcr(
        pageBitmap: Bitmap?,
        onComplete: (lineCount: Int, errorMsg: String?) -> Unit = { _, _ -> }
    ) {
        val bmp = pageBitmap
        if (bmp == null || bmp.isRecycled) {
            onComplete(0, "Page image is not loaded yet.")
            return
        }

        val currentDom = _uiState.value.pageDom
        val pw = currentDom?.pageWidthPt ?: (bmp.width / 2.5f)
        val ph = currentDom?.pageHeightPt ?: (bmp.height / 2.5f)
        val pageIdx = currentDom?.pageIndex ?: 0

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(isOcrScanning = true) }
            val startTime = System.currentTimeMillis()
            var detectedDom: com.swiffle.app.pdfedit.core.ast.PdfPageDom? = null
            var errorMsg: String? = null

            try {
                detectedDom = com.swiffle.app.pdfedit.core.vision.PaddleOcrPipeline.processScannedPage(
                    pageBitmap = bmp,
                    pageIndex = pageIdx,
                    pageWidthPt = pw,
                    pageHeightPt = ph,
                    rotationDegrees = 0
                )
            } catch (e: Exception) {
                errorMsg = e.message
                android.util.Log.e("PdfEdit", "OCR Execution failed", e)
            }

            // Ensure minimum 1.4s for smooth Google Gemini fluid iridescent shimmer animation
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1400L) {
                kotlinx.coroutines.delay(1400L - elapsed)
            }

            val count = detectedDom?.allLines?.size ?: 0
            if (detectedDom != null && count > 0) {
                val mapper = com.swiffle.app.pdfedit.core.ast.PdfCoordinateMapper(
                    pageWidthPt = detectedDom.pageWidthPt,
                    pageHeightPt = detectedDom.pageHeightPt,
                    rotationDegrees = 0
                )
                _uiState.update {
                    it.copy(
                        isOcrScanning = false,
                        pageDom = detectedDom,
                        coordinateMapper = mapper,
                        activeTool = PdfEditTool.TEXT_EDIT
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isOcrScanning = false)
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(count, errorMsg)
            }
        }
    }

    /**
     * Automatically triggers OCR if the vector text DOM is completely empty.
     */
    fun runOcrIfNeeded(pageBitmap: Bitmap?) {
        val bmp = pageBitmap ?: return
        val currentDom = _uiState.value.pageDom
        if (currentDom != null && currentDom.allLines.isNotEmpty()) return
        performAiOcr(bmp)
    }

    private fun openDocumentInputStream(context: Context, uri: Uri): java.io.InputStream? {
        val cacheDocName = "swiffle_doc_${uri.hashCode().toUInt()}.pdf"
        val cachedFile = File(context.cacheDir, cacheDocName)
        val vaultFile = File(File(context.filesDir, "recent_vault"), "vault_${uri.hashCode().toUInt()}.pdf")
        val fileOnDisk = if (vaultFile.exists() && vaultFile.length() > 0) vaultFile
            else if (cachedFile.exists() && cachedFile.length() > 0) cachedFile
            else if (uri.scheme == "file") File(uri.path ?: "")
            else null

        if (fileOnDisk != null && fileOnDisk.exists() && fileOnDisk.length() > 0) {
            return fileOnDisk.inputStream()
        }
        return context.contentResolver.openInputStream(uri)
    }

    fun selectLineForEdit(
        context: Context,
        line: PdfTextLineNode,
        pageBitmap: Bitmap?
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val mapper = _uiState.value.coordinateMapper

            // Convert PDF points to bitmap pixels for optical probe and wavelet inpainting
            val scaleFactor = if (pageBitmap != null && mapper != null) {
                pageBitmap.width.toFloat() / maxOf(1f, mapper.effectiveWidthPt)
            } else 1.0f

            val screenRect = mapper?.pdfBoxToScreenRect(
                pdfRect = line.bounds,
                zoomScale = 1.0f,
                panOffsetX = 0f,
                panOffsetY = 0f,
                scaleFactorPtToPx = scaleFactor
            )

            val bmpRect = Rect(
                screenRect?.left?.toInt()?.coerceAtLeast(0) ?: 0,
                screenRect?.top?.toInt()?.coerceAtLeast(0) ?: 0,
                screenRect?.right?.toInt()?.coerceAtLeast(0) ?: 0,
                screenRect?.bottom?.toInt()?.coerceAtLeast(0) ?: 0
            )

            // Step 1: Probe Optical Style
            val styleDna = OpticalStyleProbe.probeStyle(pageBitmap, bmpRect)

            // Step 2: Wavelet Background Inpainting
            val inpaintPatch = WaveletPaperInpainter.inpaintBackground(pageBitmap, bmpRect, paddingPx = 3)

            // Step 3: Resolve Chameleon Typography
            val resolvedFont = ChameleonFontResolver.resolveFont(context, line.font, line.lineText)

            _uiState.update { current ->
                val updatedPatches = current.inpaintedPatches.toMutableMap()
                updatedPatches[line.id] = inpaintPatch

                current.copy(
                    selectedLine = line,
                    currentEditText = line.lineText,
                    activeFontDna = resolvedFont.fontDna,
                    resolvedFont = resolvedFont,
                    opticalStyle = styleDna,
                    subPixelThickness = styleDna.subPixelDilation,
                    scannerSoftness = styleDna.scannerBlurRadiusPx,
                    customInkColor = Color(styleDna.trueInkRgb),
                    inpaintedPatches = updatedPatches
                )
            }
        }
    }

    fun toggleHighlight(lineId: String) {
        _uiState.update { current ->
            val set = current.highlightedLines.toMutableSet()
            if (lineId in set) set.remove(lineId) else set.add(lineId)
            current.copy(highlightedLines = set)
        }
    }

    fun eraseLineWithInpainting(
        context: Context,
        line: PdfTextLineNode,
        pageBitmap: Bitmap?
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val mapper = _uiState.value.coordinateMapper
            val scaleFactor = if (pageBitmap != null && mapper != null) {
                pageBitmap.width.toFloat() / maxOf(1f, mapper.effectiveWidthPt)
            } else 1.0f

            val screenRect = mapper?.pdfBoxToScreenRect(
                pdfRect = line.bounds,
                zoomScale = 1.0f,
                panOffsetX = 0f,
                panOffsetY = 0f,
                scaleFactorPtToPx = scaleFactor
            )

            val bmpRect = Rect(
                screenRect?.left?.toInt()?.coerceAtLeast(0) ?: 0,
                screenRect?.top?.toInt()?.coerceAtLeast(0) ?: 0,
                screenRect?.right?.toInt()?.coerceAtLeast(0) ?: 0,
                screenRect?.bottom?.toInt()?.coerceAtLeast(0) ?: 0
            )

            val inpaintPatch = WaveletPaperInpainter.inpaintBackground(pageBitmap, bmpRect, paddingPx = 4)
            val resolvedFont = ChameleonFontResolver.resolveFont(context, line.font, "")
            val styleDna = OpticalStyleProbe.probeStyle(pageBitmap, bmpRect)

            val typoResult = MicroTypographyResult(
                fitsInSingleLine = true,
                primaryLineTz = 100f,
                primaryLineTc = 0f,
                primaryLineTw = 0f,
                kerningShifts = emptyList(),
                reflowedLines = emptyList()
            )

            val eraseRecord = ModifiedTextRecord(
                lineId = line.id,
                originalText = line.lineText,
                newText = "",
                originalLine = line,
                typographyResult = typoResult,
                fontDna = resolvedFont.fontDna,
                resolvedFont = resolvedFont,
                opticalStyle = styleDna,
                inpaintResult = inpaintPatch
            )

            pushUndoSnapshot(_uiState.value.modifiedRecords)

            _uiState.update { current ->
                val newPatches = current.inpaintedPatches.toMutableMap()
                newPatches[line.id] = inpaintPatch

                val newRecords = current.modifiedRecords.toMutableMap()
                newRecords[line.id] = eraseRecord

                current.copy(
                    inpaintedPatches = newPatches,
                    modifiedRecords = newRecords,
                    canUndo = true
                )
            }
        }
    }

    fun addNewTextAt(
        context: Context,
        point: PointF,
        pageBitmap: Bitmap?
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val lineId = "user_line_${System.currentTimeMillis()}"
            val defaultFontSize = 14f
            val estimatedWidth = 120f
            val estimatedHeight = defaultFontSize * 1.3f

            val bounds = RectF(
                point.x,
                point.y - estimatedHeight,
                point.x + estimatedWidth,
                point.y
            )

            val newLine = PdfTextLineNode(
                id = lineId,
                lineText = "New Text",
                bounds = bounds,
                baselineY = point.y - (defaultFontSize * 0.2f),
                fontName = "Helvetica",
                font = null,
                fontResourceKey = "/F1",
                fontSizePt = defaultFontSize,
                horizontalScale = 100f,
                charSpacing = 0f,
                wordSpacing = 0f,
                colorRgb = android.graphics.Color.BLACK,
                words = emptyList(),
                tokenStartIndex = -1,
                tokenEndIndex = -1
            )

            // Add into current DOM lines so it can be rendered and selected
            val curDom = _uiState.value.pageDom
            if (curDom != null) {
                val updatedLines = curDom.allLines + newLine
                _uiState.update { it.copy(pageDom = curDom.copy(allLines = updatedLines)) }
            }

            // Immediately select this new line for live text editing
            selectLineForEdit(context, newLine, pageBitmap)
        }
    }

    fun updateEditText(newText: String) {
        val selected = _uiState.value.selectedLine ?: return
        val currentRecord = _uiState.value.modifiedRecords[selected.id]
        val resolvedFont = _uiState.value.resolvedFont ?: return
        val styleDna = _uiState.value.opticalStyle ?: return
        val inpaintPatch = _uiState.value.inpaintedPatches[selected.id] ?: return

        _uiState.update { it.copy(currentEditText = newText) }

        viewModelScope.launch(Dispatchers.Default) {
            val paragraph = _uiState.value.pageDom?.paragraphs?.firstOrNull { p ->
                p.lines.any { it.id == selected.id }
            }

            val typoResult = HzMicroTypographySolver.solve(
                originalLine = selected,
                newText = newText,
                paragraph = paragraph
            )

            val record = ModifiedTextRecord(
                lineId = selected.id,
                originalText = selected.lineText,
                newText = newText,
                originalLine = selected,
                typographyResult = typoResult,
                fontDna = resolvedFont.fontDna,
                resolvedFont = resolvedFont,
                opticalStyle = styleDna,
                inpaintResult = inpaintPatch
            )

            pushUndoSnapshot(_uiState.value.modifiedRecords)

            _uiState.update { current ->
                val newMap = current.modifiedRecords.toMutableMap()
                newMap[selected.id] = record
                current.copy(
                    modifiedRecords = newMap,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = false
                )
            }
        }
    }

    fun updateSubPixelThickness(thickness: Float) {
        _uiState.update { it.copy(subPixelThickness = thickness) }
    }

    fun updateScannerSoftness(softness: Float) {
        _uiState.update { it.copy(scannerSoftness = softness) }
    }

    fun updateCustomInkColor(color: Color) {
        _uiState.update { it.copy(customInkColor = color) }
    }

    fun autoMatchDna() {
        val style = _uiState.value.opticalStyle ?: return
        _uiState.update {
            it.copy(
                subPixelThickness = style.subPixelDilation,
                scannerSoftness = style.scannerBlurRadiusPx,
                customInkColor = Color(style.trueInkRgb)
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedLine = null, currentEditText = "") }
    }

    private fun pushUndoSnapshot(map: Map<String, ModifiedTextRecord>) {
        synchronized(undoLock) {
            undoStack.add(map.toMap())
            redoStack.clear()
            if (undoStack.size > 50) undoStack.removeAt(0)
        }
    }

    fun undo() {
        synchronized(undoLock) {
            if (undoStack.isEmpty()) return
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_uiState.value.modifiedRecords.toMap())
            _uiState.update {
                it.copy(
                    modifiedRecords = previous,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = true
                )
            }
        }
    }

    fun redo() {
        synchronized(undoLock) {
            if (redoStack.isEmpty()) return
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_uiState.value.modifiedRecords.toMap())
            _uiState.update {
                it.copy(
                    modifiedRecords = next,
                    canUndo = true,
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
    }

    fun commitAndSavePdf(
        context: Context,
        destinationFile: File,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val doc = currentDocument ?: run {
            onError("No active document loaded")
            return
        }

        // Persist current page edits before compiling
        if (_uiState.value.modifiedRecords.isNotEmpty()) {
            documentModifiedPages[_uiState.value.pageIndex] = _uiState.value.modifiedRecords
        }

        val allEditedPages = documentModifiedPages.filterValues { it.isNotEmpty() }
        if (allEditedPages.isEmpty()) {
            onError("No edits to save")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Recompile streams across all modified pages in the entire document
                for ((pIndex, recordsMap) in allEditedPages) {
                    val edits = recordsMap.values.map { rec ->
                        PdfStreamRecompiler.TextStreamEdit(
                            tokenIndex = rec.originalLine.tokenStartIndex,
                            targetOperator = "Tj",
                            newText = rec.newText,
                            font = rec.originalLine.font,
                            fontResourceKey = rec.originalLine.fontResourceKey,
                            fontSizePt = rec.originalLine.fontSizePt,
                            horizontalScale = rec.typographyResult.primaryLineTz,
                            charSpacing = rec.typographyResult.primaryLineTc,
                            wordSpacing = rec.typographyResult.primaryLineTw,
                            colorRgb = _uiState.value.customInkColor.toArgb(),
                            kerningShifts = rec.typographyResult.kerningShifts
                        )
                    }

                    PdfStreamRecompiler.recompilePageStream(
                        document = doc,
                        pageIndex = pIndex,
                        edits = edits
                    )
                }

                // Save to destination file
                FileOutputStream(destinationFile).use { out ->
                    doc.save(out)
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSaving = false, saveCompletedFile = destinationFile) }
                    onSuccess(destinationFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSaving = false) }
                    onError("Failed to save PDF: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            currentDocument?.close()
        } catch (_: Exception) {}
        currentDocument = null
    }
}
