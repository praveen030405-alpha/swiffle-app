package com.swiffle.app.conversion

import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Semantic Multi-Page Table Stitcher.
 * 
 * Replaces page-by-page dumping with holistic document understanding:
 *  1. Segregates metadata/headers from the tabular matrix.
 *  2. Fuses multi-line column headers (e.g., "Scope" + "Qty" -> "Scope Qty").
 *  3. Computes TableColumnSignature per page.
 *  4. Stitches matching continuation pages into a single continuous master table.
 *  5. Automatically de-duplicates repeated headers across page breaks.
 */
object SemanticTableStitcher {

    private val ITEM_SEQUENCE_PATTERN = Pattern.compile("^\\d+(\\.\\d+)+$")

    /**
     * Stitches per-page reconstructed grids into cohesive semantic tables.
     */
    fun stitchTables(
        pageGrids: List<ReconstructedTableGrid>
    ): List<SemanticDocumentTable> {
        if (pageGrids.isEmpty()) return emptyList()

        val stitchedTables = mutableListOf<SemanticDocumentTable>()
        var currentTableTitle = "Table"
        var currentMetadata = mutableListOf<String>()
        var currentHeaders = mutableListOf<String>()
        var currentRows = mutableListOf<List<SemanticCell>>()
        var currentPages = mutableListOf<Int>()
        var currentSignature: TableColumnSignature? = null

        for (grid in pageGrids) {
            if (grid.rows.isEmpty()) continue

            // 1. Separate Top Page Metadata from the Table Body
            val (pageMetadata, headerRowCandidate, dataRows) = partitionPageRows(grid)

            // 2. Synthesize multi-line header for this page
            val synthesizedHeader = synthesizeHeader(headerRowCandidate, grid.columns)

            // 3. Extract Column Signature for continuity matching
            val pageSignature = TableColumnSignature(
                columnCount = grid.columns.size,
                columnBounds = grid.columns,
                headerTokensText = synthesizedHeader.filter { it.isNotBlank() }
            )

            val isContinuation = currentSignature != null && currentSignature.isContinuationOf(pageSignature)

            if (isContinuation && currentHeaders.isNotEmpty()) {
                // Continuation of the current table!
                currentPages.add(grid.pageIndex)

                // Append data rows, converting LatticeCell to SemanticCell and de-duplicating any repeated header
                for (latticeRow in dataRows) {
                    if (isHeaderDuplicate(latticeRow, currentHeaders)) {
                        continue // Skip repeated header row on continuation page
                    }
                    val rowCells = convertRowCells(latticeRow, currentHeaders.size)
                    if (rowCells.any { it.text.isNotBlank() }) {
                        currentRows.add(rowCells)
                    }
                }
            } else {
                // Finalize previous table if it exists
                if (currentRows.isNotEmpty() || currentHeaders.isNotEmpty()) {
                    stitchedTables.add(
                        SemanticDocumentTable(
                            title = currentTableTitle,
                            metadataHeader = currentMetadata.toList(),
                            columnHeaders = currentHeaders.toList(),
                            rows = currentRows.toList(),
                            sourcePageIndices = currentPages.toList()
                        )
                    )
                }

                // Start new semantic table
                currentTableTitle = determineTableTitle(pageMetadata, grid.pageIndex)
                currentMetadata = pageMetadata.toMutableList()
                currentHeaders = synthesizedHeader.toMutableList()
                currentRows = mutableListOf()
                currentPages = mutableListOf(grid.pageIndex)
                currentSignature = pageSignature

                for (latticeRow in dataRows) {
                    val rowCells = convertRowCells(latticeRow, currentHeaders.size)
                    if (rowCells.any { it.text.isNotBlank() }) {
                        currentRows.add(rowCells)
                    }
                }
            }
        }

        // Finalize last table
        if (currentRows.isNotEmpty() || currentHeaders.isNotEmpty()) {
            stitchedTables.add(
                SemanticDocumentTable(
                    title = currentTableTitle,
                    metadataHeader = currentMetadata.toList(),
                    columnHeaders = currentHeaders.toList(),
                    rows = currentRows.toList(),
                    sourcePageIndices = currentPages.toList()
                )
            )
        }

        return stitchedTables
    }

    /**
     * Partitions rows into Metadata (top title, company name), Header Candidate(s), and Body Data Rows.
     */
    private fun partitionPageRows(
        grid: ReconstructedTableGrid
    ): Triple<List<String>, List<LatticeRow>, List<LatticeRow>> {
        val metadata = mutableListOf<String>()
        val headerRows = mutableListOf<LatticeRow>()
        val dataRows = mutableListOf<LatticeRow>()

        var foundHeader = false

        for (row in grid.rows) {
            val fullText = row.cells.joinToString(" ") { it.rawText }.trim()

            // Detect company title, banner or top metadata
            if (!foundHeader && isMetadataRow(row, grid.columns.size)) {
                metadata.add(fullText)
                continue
            }

            // Check if this row is part of the header band (usually first 1-3 rows with text only)
            if (!foundHeader && (row.isHeaderCandidate || isHeaderLikeRow(row))) {
                headerRows.add(row)
                // If the next rows also look like header text fragments (e.g. "Qty", "No."), keep accumulating
                if (headerRows.size >= 3) {
                    foundHeader = true
                }
            } else {
                foundHeader = true
                dataRows.add(row)
            }
        }

        // If page 0 has no explicit header candidate, take the first row if it looks like a header
        if (headerRows.isEmpty() && dataRows.isNotEmpty() && grid.pageIndex == 0) {
            val first = dataRows.first()
            if (isHeaderLikeRow(first)) {
                headerRows.add(dataRows.removeAt(0))
            }
        }

        return Triple(metadata, headerRows, dataRows)
    }

    /**
     * Synthesizes multi-line header rows into a single cohesive column header list.
     * E.g.
     *  Row 0: ["Scope", "Tender", "Done"]
     *  Row 1: ["Qty",   "Qty",    "Qty"]
     *  -> ["Scope Qty", "Tender Qty", "Done Qty"]
     */
    fun synthesizeHeader(
        headerRows: List<LatticeRow>,
        columns: List<LatticeColumn>
    ): List<String> {
        if (headerRows.isEmpty()) {
            return columns.mapIndexed { idx, _ -> "Column ${idx + 1}" }
        }

        val colCount = columns.size
        val mergedHeaders = Array(colCount) { mutableListOf<String>() }

        for (row in headerRows) {
            for (cell in row.cells) {
                if (cell.columnIndex in 0 until colCount && cell.rawText.isNotBlank()) {
                    mergedHeaders[cell.columnIndex].add(cell.rawText.trim())
                }
            }
        }

        return mergedHeaders.mapIndexed { idx, tokens ->
            if (tokens.isEmpty()) {
                "Column ${idx + 1}"
            } else {
                // Join vertically stacked tokens and remove duplicate repeated words
                val combined = tokens.joinToString(" ").replace("\\s+".toRegex(), " ").trim()
                combined.ifBlank { "Column ${idx + 1}" }
            }
        }
    }

    /**
     * Determines if a row is a page-top metadata banner (company name, address, report subtitle)
     * rather than an actual tabular column header.
     */
    private fun isMetadataRow(row: LatticeRow, totalColumns: Int): Boolean {
        val text = row.cells.joinToString(" ") { it.rawText }.lowercase()
        val isExplicitMeta = text.contains("name of company") ||
                text.contains("bill of quantity") ||
                text.contains("survey no") ||
                text.contains("report") ||
                text.contains("meeting") ||
                text.contains("balance sheet") ||
                text.contains("pursuant to section")

        // If row spans only 1 wide cell across a multi-column table
        val isSingleSpan = row.cells.size <= 2 && totalColumns >= 4
        return isExplicitMeta && isSingleSpan
    }

    private fun isHeaderLikeRow(row: LatticeRow): Boolean {
        // If the first cell is an item sequence number like 1.2 or 1.24.1, it's a data row
        val firstCellText = row.cells.firstOrNull()?.rawText?.trim() ?: ""
        if (ITEM_SEQUENCE_PATTERN.matcher(firstCellText).matches()) {
            return false
        }
        // Headers predominantly contain text, with zero or minimal numeric values
        val textCount = row.cells.count { it.dataType == CellDataType.TEXT }
        val numCount = row.cells.count { it.dataType == CellDataType.NUMERIC || it.dataType == CellDataType.CURRENCY }
        return textCount > 0 && numCount == 0
    }

    private fun isHeaderDuplicate(row: LatticeRow, existingHeaders: List<String>): Boolean {
        val rowTexts = row.cells.map { it.rawText.trim().lowercase() }.filter { it.isNotBlank() }
        if (rowTexts.isEmpty()) return false
        val headerTexts = existingHeaders.map { it.trim().lowercase() }
        val matches = rowTexts.count { rt -> headerTexts.any { ht -> ht.contains(rt) || rt.contains(ht) } }
        return matches >= (rowTexts.size * 0.6f)
    }

    private fun convertRowCells(latticeRow: LatticeRow, targetColCount: Int): List<SemanticCell> {
        val result = MutableList(targetColCount) { SemanticCell(text = "") }
        for (cell in latticeRow.cells) {
            if (cell.columnIndex in 0 until targetColCount) {
                result[cell.columnIndex] = SemanticCell(
                    text = cell.rawText,
                    dataType = cell.dataType,
                    numericValue = cell.numericValue,
                    currencySymbol = cell.currencySymbol,
                    isHeader = false
                )
            }
        }
        return result
    }

    private fun determineTableTitle(metadata: List<String>, pageIndex: Int): String {
        for (line in metadata) {
            val lower = line.lowercase()
            when {
                lower.contains("bill of quantity") -> return "Bill of Quantity"
                lower.contains("board meeting") -> return "Board Meeting"
                lower.contains("extract of board's report") -> return "Board Report"
                lower.contains("balance sheet") -> return "Balance Sheet"
                lower.contains("profit and loss") || lower.contains("p&l") -> return "Profit & Loss"
                lower.contains("audit") -> return "Auditor Report"
                lower.contains("attendance") -> return "Attendance"
                lower.contains("tender") -> return "Tender Schedule"
            }
        }
        return if (metadata.isNotEmpty()) {
            metadata.first().take(25).trim()
        } else {
            "Table ${pageIndex + 1}"
        }
    }
}
