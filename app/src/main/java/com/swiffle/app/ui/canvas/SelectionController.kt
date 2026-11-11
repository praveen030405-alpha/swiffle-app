package com.swiffle.app.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Enterprise Single Source of Truth for Text Selection & Handle Interaction.
 * Completely eliminates handle inversion (Bug 1), cross-line collapse (Bug 2),
 * gesture cancellation starvation (Bug 3), and disconnected pointer coordinates (Bug 4).
 */
data class StemInfo(
    val x: Float,
    val topY: Float,
    val bottomY: Float
)

class SelectionController {
    private val _currentSelection = mutableStateOf<TextSelection?>(null)
    var currentSelection: TextSelection?
        get() = _currentSelection.value
        set(value) {
            _currentSelection.value = value
            if (value == null) {
                startEndpoint = null
                endEndpoint = null
                draggingHandle = null
                isHandleDragging = false
                magnifierPosition = null
                justEndedDrag = false
            }
        }

    var startEndpoint by mutableStateOf<Endpoint?>(null)
        private set

    var endEndpoint by mutableStateOf<Endpoint?>(null)
        private set

    var draggingHandle by mutableStateOf<String?>(null) // "start" or "end"
        private set

    var isHandleDragging by mutableStateOf(false)
        private set

    var justEndedDrag by mutableStateOf(false)
        private set

    var dragTouchAnchorOffset by mutableStateOf(Offset.Zero)
        private set

    var magnifierPosition by mutableStateOf<Offset?>(null)
        private set

    var dragAnchorEndpoint by mutableStateOf<Endpoint?>(null)
        private set

    fun getStartStem(pageModel: PageTextModel): StemInfo? {
        val ep = startEndpoint ?: return null
        if (pageModel.lines.isEmpty()) return null
        val line = pageModel.lines.getOrNull(ep.lineIndex) ?: return null
        val gIdx = ep.glyphIndex.coerceIn(0, (line.boundaries.size - 2).coerceAtLeast(0))
        val x = if (line.boundaries.isNotEmpty()) line.boundaries[gIdx] else 0f
        return StemInfo(x, line.top, line.bottom)
    }

    fun getEndStem(pageModel: PageTextModel): StemInfo? {
        val ep = endEndpoint ?: return null
        if (pageModel.lines.isEmpty()) return null
        val line = pageModel.lines.getOrNull(ep.lineIndex) ?: return null
        val gIdx = (ep.glyphIndex + 1).coerceIn(0, (line.boundaries.size - 1).coerceAtLeast(0))
        val x = if (line.boundaries.isNotEmpty()) line.boundaries[gIdx] else 0f
        return StemInfo(x, line.top, line.bottom)
    }

    /**
     * Initializes selection on a single word via long-press or double-tap.
     */
    fun selectWord(
        pageNum: Int,
        pageModel: PageTextModel,
        pdfX: Float,
        pdfY: Float
    ): Boolean {
        if (pageModel.lines.isEmpty()) return false
        val ep = pageModel.nearestEndpoint(pdfX, pdfY)
        val line = pageModel.lines.getOrNull(ep.lineIndex) ?: return false

        val charIdx = pageModel.getCharIndex(ep)
        val word = pageModel.words.firstOrNull { charIdx in it.charStart..it.charEnd }
            ?: pageModel.words.filter { it.lineIndex == line.lineIndex }
                .minByOrNull { kotlin.math.abs(it.charStart - charIdx) }

        val startEp: Endpoint
        val endEp: Endpoint

        if (word != null) {
            val startLine = pageModel.lines.getOrNull(word.lineIndex) ?: line
            val sGlyph = (word.glyphStart - startLine.glyphStart).coerceIn(0, (startLine.glyphCount - 1).coerceAtLeast(0))
            val eGlyph = (word.glyphEnd - startLine.glyphStart).coerceIn(0, (startLine.glyphCount - 1).coerceAtLeast(0))
            startEp = Endpoint(pageNum - 1, word.lineIndex, sGlyph)
            endEp = Endpoint(pageNum - 1, word.lineIndex, eGlyph)
        } else {
            startEp = ep
            endEp = ep
        }

        setSelection(pageNum, pageModel, startEp, endEp)
        return true
    }

    /**
     * Selects all content on the given page.
     */
    fun selectAll(pageNum: Int, pageModel: PageTextModel) {
        if (pageModel.lines.isEmpty()) return
        val firstLine = pageModel.lines.first()
        val lastLine = pageModel.lines.last()
        val startEp = Endpoint(pageNum - 1, firstLine.lineIndex, 0)
        val endEp = Endpoint(pageNum - 1, lastLine.lineIndex, (lastLine.glyphCount - 1).coerceAtLeast(0))
        setSelection(pageNum, pageModel, startEp, endEp)
    }

    /**
     * Sets selection strictly with normalized endpoints.
     */
    fun setSelection(
        pageNum: Int,
        pageModel: PageTextModel,
        start: Endpoint,
        end: Endpoint
    ) {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        startEndpoint = s
        endEndpoint = e

        val text = pageModel.getSelectedText(s, e)
        val rects = pageModel.getSelectedRects(s, e)
        val startCharIdx = pageModel.getCharIndex(s)
        val endCharIdx = pageModel.getCharIndex(e)

        currentSelection = TextSelection(
            pageNum = pageNum,
            startIndex = startCharIdx,
            endIndex = endCharIdx,
            rects = rects,
            text = text
        )
    }

    /**
     * Bridges an existing TextSelection by resolving its exact Endpoints from the PageTextModel.
     */
    fun setManualSelection(selection: TextSelection, pageModel: PageTextModel) {
        val s = pageModel.endpointFromCharIndex(selection.startIndex)
        val e = pageModel.endpointFromCharIndex(selection.endIndex)
        startEndpoint = minOf(s, e)
        endEndpoint = maxOf(s, e)
        currentSelection = selection
    }

    /**
     * Begins dragging a handle with True Anchor-Based Crossover support.
     * Sets isHandleDragging = true immediately to gate parent gestures.
     * Pins the stationary anchor to the opposite handle so crossover is completely stable.
     */
    fun startDrag(handle: String, touchOffset: Offset) {
        draggingHandle = handle
        isHandleDragging = true
        justEndedDrag = false
        dragTouchAnchorOffset = touchOffset

        // True Anchor: lock the stationary endpoint
        dragAnchorEndpoint = if (handle == "start") {
            endEndpoint
        } else {
            startEndpoint
        }
    }

    /**
     * Updates handle drag position in real-time with True Anchor-Based Crossover.
     * - The stationary anchor is rock-solid and NEVER moves.
     * - When the dragging finger is before the stationary anchor:
     *     targetEp is the start, anchor is the end, draggingHandle becomes "start".
     * - When the dragging finger is at or after the stationary anchor:
     *     anchor is the start, targetEp is the end, draggingHandle becomes "end".
     * Provides 100% native Android handle crossover freedom with zero oscillations.
     */
    fun updateDrag(
        pdfX: Float,
        pdfY: Float,
        pageModel: PageTextModel,
        screenCursorOffset: Offset
    ) {
        if (!isHandleDragging || draggingHandle == null) return
        val anchor = dragAnchorEndpoint ?: return

        magnifierPosition = screenCursorOffset

        if (pageModel.lines.isEmpty()) return

        val targetEp = pageModel.nearestEndpoint(pdfX, pdfY)

        if (targetEp < anchor) {
            // Dragged point is before the stationary anchor
            draggingHandle = "start"
            setSelection(pageModel.pageNum, pageModel, targetEp, anchor)
        } else {
            // Dragged point is at or after the stationary anchor
            draggingHandle = "end"
            setSelection(pageModel.pageNum, pageModel, anchor, targetEp)
        }
    }

    /**
     * Ends handle drag. Safely resets states and activates brief justEndedDrag guard.
     */
    fun endDrag() {
        draggingHandle = null
        isHandleDragging = false
        magnifierPosition = null
        dragAnchorEndpoint = null
        justEndedDrag = true
    }

    fun clearJustEndedDrag() {
        justEndedDrag = false
    }

    /**
     * Clears selection completely.
     */
    fun clearSelection() {
        currentSelection = null
        startEndpoint = null
        endEndpoint = null
        draggingHandle = null
        isHandleDragging = false
        magnifierPosition = null
        dragAnchorEndpoint = null
        justEndedDrag = false
    }
}
