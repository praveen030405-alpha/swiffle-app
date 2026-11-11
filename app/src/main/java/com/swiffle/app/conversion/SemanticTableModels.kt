package com.swiffle.app.conversion

import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision semantic table representations supporting:
 *  1. Document-level multi-page continuous tables.
 *  2. Header band multi-line fusion.
 *  3. Table metadata extraction (company name, report title, date).
 */

data class SemanticDocumentTable(
    val title: String,
    val metadataHeader: List<String> = emptyList(),
    val columnHeaders: List<String>,
    val rows: List<List<SemanticCell>>,
    val sourcePageIndices: List<Int>
)

data class SemanticCell(
    val text: String,
    val dataType: CellDataType = CellDataType.TEXT,
    val numericValue: Double? = null,
    val currencySymbol: String? = null,
    val isHeader: Boolean = false
)

/**
 * Signature representing the physical geometry and structure of a table on a page.
 * Used to identify continuous tables across consecutive pages.
 */
data class TableColumnSignature(
    val columnCount: Int,
    val columnBounds: List<LatticeColumn>,
    val headerTokensText: List<String>
) {
    /**
     * Determines whether another page's table is a continuation of this table.
     */
    fun isContinuationOf(other: TableColumnSignature, tolerancePx: Float = 45f): Boolean {
        if (columnCount == 0 || other.columnCount == 0) return false
        
        // Direct column count match or off-by-one boundary
        if (abs(columnCount - other.columnCount) > 1) return false

        // Compare column gutter positions
        val minCols = min(columnBounds.size, other.columnBounds.size)
        var matchCount = 0
        for (i in 0 until minCols) {
            val diffLeft = abs(columnBounds[i].leftBound - other.columnBounds[i].leftBound)
            val diffRight = abs(columnBounds[i].rightBound - other.columnBounds[i].rightBound)
            if (diffLeft <= tolerancePx || diffRight <= tolerancePx) {
                matchCount++
            }
        }

        val overlapRatio = matchCount.toFloat() / minCols.toFloat()
        if (overlapRatio >= 0.70f) return true

        // Header text similarity
        if (headerTokensText.isNotEmpty() && other.headerTokensText.isNotEmpty()) {
            val common = headerTokensText.intersect(other.headerTokensText.toSet()).size
            val headerOverlap = common.toFloat() / max(headerTokensText.size, other.headerTokensText.size).toFloat()
            if (headerOverlap >= 0.50f) return true
        }

        return false
    }
}

