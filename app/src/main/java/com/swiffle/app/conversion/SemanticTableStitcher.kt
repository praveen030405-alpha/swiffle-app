package com.swiffle.app.conversion

import java.util.regex.Pattern

/**
 * Semantic Multi-Page Table Stitcher.
 *
 * General-purpose multi-page table assembly without document-specific hardcoded strings:
 *  1. Segregates single-span document titles/banners above the table from the multi-column header & body.
 *  2. Fuses multi-line column headers (e.g., stacked header bands) into clean column titles.
 *  3. Computes TableColumnSignature per page and stitches continuation pages seamlessly.
 *  4. Automatically de-duplicates repeated header rows across page breaks.
 *  5. Prunes completely empty columns so exported Excel workbooks are compact and aligned.
 */
object SemanticTableStitcher {

    private val ITEM_SEQUENCE_PATTERN = Pattern.compile("^\\d+(\\.\\d+)*[.)]?$")

    fun stitchTables(
        pageGrids: List<ReconstructedTableGrid>
    ): List<SemanticDocumentTable> {
        if (pageGrids.isEmpty()) return emptyList()

        val stitchedTables = mutableListOf<SemanticDocumentTable>()
        var currentTableTitle = "Sheet 1"
        var currentMetadata = mutableListOf<String>()
        var currentHeaders = mutableListOf<String>()
        var currentRows = mutableListOf<List<SemanticCell>>()
        var currentPages = mutableListOf<Int>()
        var currentSignature: TableColumnSignature? = null

        for (grid in pageGrids) {
            if (grid.rows.isEmpty()) continue

            val (pageMetadata, headerRowCandidates, dataRows) = partitionPageRows(grid)
            val synthesizedHeader = synthesizeHeader(headerRowCandidates, grid.columns)

            val pageSignature = TableColumnSignature(
                columnCount = grid.columns.size,
                columnBounds = grid.columns,
                headerTokensText = synthesizedHeader.filter { it.isNotBlank() }
            )

            val isContinuation = currentSignature != null && currentSignature.isContinuationOf(pageSignature)

            if (isContinuation && currentHeaders.isNotEmpty()) {
                currentPages.add(grid.pageIndex)

                for (latticeRow in dataRows) {
                    if (isHeaderDuplicate(latticeRow, currentHeaders)) {
                        continue
                    }
                    val rowCells = convertRowCells(latticeRow, currentHeaders.size)
                    if (rowCells.any { it.text.isNotBlank() }) {
                        currentRows.add(rowCells)
                    }
                }
            } else {
                if (currentRows.isNotEmpty() || currentHeaders.isNotEmpty()) {
                    stitchedTables.add(
                        pruneEmptyColumns(
                            SemanticDocumentTable(
                                title = currentTableTitle,
                                metadataHeader = currentMetadata.toList(),
                                columnHeaders = currentHeaders.toList(),
                                rows = currentRows.toList(),
                                sourcePageIndices = currentPages.toList()
                            )
                        )
                    )
                }

                currentTableTitle = determineTableTitle(pageMetadata, stitchedTables.size)
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

        if (currentRows.isNotEmpty() || currentHeaders.isNotEmpty()) {
            stitchedTables.add(
                pruneEmptyColumns(
                    SemanticDocumentTable(
                        title = currentTableTitle,
                        metadataHeader = currentMetadata.toList(),
                        columnHeaders = currentHeaders.toList(),
                        rows = currentRows.toList(),
                        sourcePageIndices = currentPages.toList()
                    )
                )
            )
        }

        return stitchedTables
    }

    /**
     * Removes columns that have no data cells and only a generic "Column N" header.
     */
    private fun pruneEmptyColumns(table: SemanticDocumentTable): SemanticDocumentTable {
        val colCount = table.columnHeaders.size
        if (colCount <= 1 || table.rows.isEmpty()) return table

        val activeColIndices = (0 until colCount).filter { cIdx ->
            val header = table.columnHeaders.getOrNull(cIdx).orEmpty()
            val hasCustomHeader = header.isNotBlank() && !header.matches(Regex("^Column \\d+$", RegexOption.IGNORE_CASE))
            val hasAnyCellData = table.rows.any { row -> row.getOrNull(cIdx)?.text?.isNotBlank() == true }
            hasCustomHeader || hasAnyCellData
        }

        if (activeColIndices.isEmpty() || activeColIndices.size == colCount) return table

        val prunedHeaders = activeColIndices.map { table.columnHeaders[it] }
        val prunedRows = table.rows.map { row ->
            activeColIndices.map { cIdx -> row.getOrNull(cIdx) ?: SemanticCell("") }
        }

        return table.copy(
            columnHeaders = prunedHeaders,
            rows = prunedRows
        )
    }

    /**
     * Partitions rows into top metadata banners, column header row(s), and tabular data rows
     * using structural cell counts and data-type distribution.
     */
    private fun partitionPageRows(
        grid: ReconstructedTableGrid
    ): Triple<List<String>, List<LatticeRow>, List<LatticeRow>> {
        val metadata = mutableListOf<String>()
        val headerRows = mutableListOf<LatticeRow>()
        val dataRows = mutableListOf<LatticeRow>()

        val totalCols = grid.columns.size
        val maxCellsInAnyRow = grid.rows.maxOfOrNull { it.cells.size } ?: 1
        var foundHeader = false

        for ((rIdx, row) in grid.rows.withIndex()) {
            val fullText = row.cells.joinToString(" ") { it.rawText }.trim()
            if (fullText.isBlank()) continue

            // Top single-span title/banner before the multi-column grid starts
            val isLeadingSingleSpan = !foundHeader &&
                    headerRows.isEmpty() &&
                    rIdx < 4 &&
                    row.cells.size == 1 &&
                    maxCellsInAnyRow >= 2 &&
                    totalCols >= 2

            if (isLeadingSingleSpan) {
                metadata.add(fullText)
                continue
            }

            if (!foundHeader && headerRows.size < 2 && isHeaderLikeRow(row, headerRows.isEmpty())) {
                headerRows.add(row)
                // If this row already populates most columns, one header row is usually sufficient unless next row is short sub-headers
                if (row.cells.size >= (maxCellsInAnyRow * 0.75f).toInt().coerceAtLeast(2)) {
                    foundHeader = true
                }
            } else {
                foundHeader = true
                dataRows.add(row)
            }
        }

        if (headerRows.isEmpty() && dataRows.isNotEmpty()) {
            headerRows.add(dataRows.removeAt(0))
        }

        return Triple(metadata, headerRows, dataRows)
    }

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
                tokens.joinToString(" ").replace(Regex("\\s+"), " ").trim().ifBlank { "Column ${idx + 1}" }
            }
        }
    }

    private fun isHeaderLikeRow(row: LatticeRow, isFirstCandidate: Boolean): Boolean {
        val firstCellText = row.cells.firstOrNull()?.rawText?.trim().orEmpty()
        if (ITEM_SEQUENCE_PATTERN.matcher(firstCellText).matches() && row.cells.size >= 2) {
            // Check if the rest of the row has numbers (which makes it a numbered data row like "1 | Widget | $10.00")
            val hasNumbers = row.cells.drop(1).any {
                it.dataType == CellDataType.NUMERIC ||
                it.dataType == CellDataType.CURRENCY ||
                it.dataType == CellDataType.PERCENTAGE
            }
            if (hasNumbers) return false
        }

        val textCount = row.cells.count { it.dataType == CellDataType.TEXT }
        val numCount = row.cells.count {
            it.dataType == CellDataType.NUMERIC ||
            it.dataType == CellDataType.CURRENCY ||
            it.dataType == CellDataType.PERCENTAGE
        }
        return if (isFirstCandidate) {
            textCount >= numCount && textCount > 0
        } else {
            textCount > 0 && numCount == 0
        }
    }

    private fun isHeaderDuplicate(row: LatticeRow, existingHeaders: List<String>): Boolean {
        val rowTexts = row.cells.map { it.rawText.trim().lowercase() }.filter { it.isNotBlank() }
        if (rowTexts.isEmpty()) return false
        val headerTexts = existingHeaders.map { it.trim().lowercase() }
        val matches = rowTexts.count { rt -> headerTexts.any { ht -> ht == rt || ht.contains(rt) || rt.contains(ht) } }
        return matches >= (rowTexts.size * 0.65f)
    }

    private fun convertRowCells(latticeRow: LatticeRow, targetColCount: Int): List<SemanticCell> {
        val result = MutableList(targetColCount) { SemanticCell(text = "") }
        for (cell in latticeRow.cells) {
            if (cell.columnIndex in 0 until targetColCount) {
                val existing = result[cell.columnIndex]
                val combinedText = if (existing.text.isBlank()) {
                    cell.rawText
                } else {
                    "${existing.text} ${cell.rawText}".trim()
                }
                result[cell.columnIndex] = SemanticCell(
                    text = combinedText,
                    dataType = if (existing.text.isBlank()) cell.dataType else CellDataType.TEXT,
                    numericValue = if (existing.text.isBlank()) cell.numericValue else null,
                    currencySymbol = cell.currencySymbol,
                    isHeader = false
                )
            }
        }
        return result
    }

    private fun determineTableTitle(metadata: List<String>, tableIndex: Int): String {
        val firstMeta = metadata.firstOrNull { it.isNotBlank() }?.trim()
        if (!firstMeta.isNullOrBlank()) {
            val cleaned = firstMeta.replace(Regex("[\\\\/*?:\\[\\]]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.length in 3..28) {
                return cleaned
            } else if (cleaned.length > 28) {
                return cleaned.take(26).trim()
            }
        }
        return "Table ${tableIndex + 1}"
    }
}
