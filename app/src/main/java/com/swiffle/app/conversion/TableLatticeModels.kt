package com.swiffle.app.conversion

import android.graphics.RectF

/**
 * Pure Kotlin platform-independent bounding box representation.
 */
data class LatticeRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun toRectF(): RectF = RectF(left, top, right, bottom)

    companion object {
        fun from(r: RectF): LatticeRect = LatticeRect(r.left, r.top, r.right, r.bottom)
    }
}

/**
 * Semantic inferred data types for Excel cell emission.
 */
enum class CellDataType {
    TEXT,
    NUMERIC,
    CURRENCY,
    PERCENTAGE,
    DATE
}

/**
 * Normalized OCR Token with geometric coordinates and confidence.
 */
data class OcrToken(
    val text: String,
    val bounds: LatticeRect,
    val confidence: Float = 1.0f
) {
    constructor(text: String, rectF: RectF, confidence: Float = 1.0f) :
        this(text, LatticeRect.from(rectF), confidence)

    val centerX: Float get() = bounds.centerX
    val centerY: Float get() = bounds.centerY
    val width: Float get() = bounds.width
    val height: Float get() = bounds.height
}

/**
 * Reconstructed 2D Table Cell.
 */
data class LatticeCell(
    val rowIndex: Int,
    val columnIndex: Int,
    val rawText: String,
    val dataType: CellDataType,
    val numericValue: Double? = null,
    val currencySymbol: String? = null,
    val isHeader: Boolean = false,
    val bounds: LatticeRect = LatticeRect()
)

/**
 * Reconstructed Row containing sequential horizontal cells.
 */
data class LatticeRow(
    val rowIndex: Int,
    val cells: List<LatticeCell>,
    val baselineY: Float,
    val topY: Float,
    val bottomY: Float,
    val isHeaderCandidate: Boolean = false
)

/**
 * Detected Column Boundary across the entire sheet.
 */
data class LatticeColumn(
    val columnIndex: Int,
    val leftBound: Float,
    val rightBound: Float,
    val preferredWidth: Float
)

/**
 * Complete Reconstructed 2D Table Grid ready for native Excel workbook synthesis.
 */
data class ReconstructedTableGrid(
    val rows: List<LatticeRow>,
    val columns: List<LatticeColumn>,
    val pageIndex: Int = 0,
    val pageWidth: Float,
    val pageHeight: Float
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = columns.size

    fun getCell(row: Int, col: Int): LatticeCell? {
        if (row !in rows.indices) return null
        return rows[row].cells.find { it.columnIndex == col }
    }
}
