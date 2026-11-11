package com.swiffle.app.conversion

import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Spatial & Interval-Projection Table Reconstruction Engine.
 *
 * Reconstructs 2D tabular structure from PDF vector glyphs or OCR tokens using:
 *  1. Adaptive Baseline Row Clustering: Groups tokens into horizontal rows using median glyph height.
 *  2. Intra-Row Phrase Bridging: Merges adjacent words separated by normal word spacing into unified phrase tokens
 *     so multi-word headers and values ("Unit Price", "Total Amount") are never split across columns.
 *  3. Interval-Span X-Occupancy Projection: Accumulates full horizontal token spans [left, right] across tabular rows
 *     to identify true vertical column gutters (zero/low-occupancy valleys).
 *  4. Semantic Type Inference: Automatically parses numbers, currencies, percentages, dates, and text.
 */
object TableLatticeReconstructor {

    private val NUMERIC_PATTERN = Pattern.compile("^[+-]?\\d{1,3}(,\\d{3})*(\\.\\d+)?$|^[+-]?\\d+(\\.\\d+)?$")
    private val CURRENCY_PATTERN = Pattern.compile("^([\\$€£₹¥])\\s*([+-]?\\d{1,3}(,\\d{3})*(\\.\\d+)?|[+-]?\\d+(\\.\\d+)?)$|^([+-]?\\d{1,3}(,\\d{3})*(\\.\\d+)?|[+-]?\\d+(\\.\\d+)?)\\s*([\\$€£₹¥])$")
    private val PERCENT_PATTERN = Pattern.compile("^[+-]?\\d+(\\.\\d+)?\\s*%$")
    private val PAREN_NEGATIVE_NUMERIC = Pattern.compile("^\\(([\\$€£₹¥])?\\s*(\\d{1,3}(,\\d{3})*(\\.\\d+)?|\\d+(\\.\\d+)?)\\)$")

    fun reconstructGrid(
        tokens: List<OcrToken>,
        pageWidth: Float,
        pageHeight: Float,
        pageIndex: Int = 0,
        policyAction: TableExtractionPolicyAction = TableReinforcementAgent.DEFAULT_ACTION
    ): ReconstructedTableGrid {
        if (tokens.isEmpty()) {
            return ReconstructedTableGrid(
                rows = emptyList(),
                columns = emptyList(),
                pageIndex = pageIndex,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
        }

        // 1. Group raw word tokens into horizontal rows using adaptive baseline tolerance
        val rawRowClusters = clusterRows(tokens, policyAction.rowToleranceFactor)

        // 2. Bridge adjacent words within each row into cohesive cell phrases
        val phraseRows = rawRowClusters.map { rowTokens ->
            mergePhraseTokensInRow(rowTokens)
        }.filter { it.isNotEmpty() }

        if (phraseRows.isEmpty()) {
            return ReconstructedTableGrid(
                rows = emptyList(),
                columns = emptyList(),
                pageIndex = pageIndex,
                pageWidth = pageWidth,
                pageHeight = pageHeight
            )
        }

        // 3. Separate multi-column tabular rows from single-span title/banner rows for column detection
        val tabularPhraseTokens = extractTabularPhraseTokens(phraseRows, pageWidth)
        val tokensForColumns = if (tabularPhraseTokens.isNotEmpty()) {
            tabularPhraseTokens
        } else {
            phraseRows.flatten()
        }

        // 4. Detect column intervals via full-span X-occupancy projection
        val columnIntervals = detectColumnIntervals(tokensForColumns, phraseRows, pageWidth, policyAction)

        // 5. Bin row phrases into 2D Lattice Cells (ensuring every phrase is assigned to its best column)
        val reconstructedRows = mutableListOf<LatticeRow>()

        phraseRows.forEachIndexed { rIdx, rowPhrases ->
            val rowTop = rowPhrases.minOf { it.bounds.top }
            val rowBottom = rowPhrases.maxOf { it.bounds.bottom }
            val rowBaseline = rowPhrases.map { it.centerY }.average().toFloat()

            // Map columnIndex -> list of phrases assigned to that column
            val columnBuckets = Array(columnIntervals.size) { mutableListOf<OcrToken>() }

            for (phrase in rowPhrases) {
                val bestColIdx = findBestColumnForPhrase(phrase, columnIntervals)
                if (bestColIdx in columnBuckets.indices) {
                    columnBuckets[bestColIdx].add(phrase)
                }
            }

            val rowCells = mutableListOf<LatticeCell>()
            for (cIdx in columnIntervals.indices) {
                val cellPhrases = columnBuckets[cIdx].sortedBy { it.bounds.left }
                if (cellPhrases.isNotEmpty()) {
                    val rawText = cellPhrases.joinToString(" ") { it.text.trim() }
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (rawText.isNotEmpty()) {
                        val cellBounds = LatticeRect(
                            left = cellPhrases.minOf { it.bounds.left },
                            top = rowTop,
                            right = cellPhrases.maxOf { it.bounds.right },
                            bottom = rowBottom
                        )
                        val inferred = inferCellData(rawText, isFirstRow = (rIdx == 0))
                        rowCells.add(
                            LatticeCell(
                                rowIndex = rIdx,
                                columnIndex = cIdx,
                                rawText = rawText,
                                dataType = inferred.dataType,
                                numericValue = inferred.numericValue,
                                currencySymbol = inferred.currencySymbol,
                                isHeader = (rIdx == 0),
                                bounds = cellBounds
                            )
                        )
                    }
                }
            }

            if (rowCells.isNotEmpty()) {
                val textCount = rowCells.count { it.dataType == CellDataType.TEXT }
                val numCount = rowCells.count { it.dataType != CellDataType.TEXT }
                val isHeaderCandidate = (rIdx <= 2) && textCount >= 2 && numCount == 0
                reconstructedRows.add(
                    LatticeRow(
                        rowIndex = rIdx,
                        cells = rowCells,
                        baselineY = rowBaseline,
                        topY = rowTop,
                        bottomY = rowBottom,
                        isHeaderCandidate = isHeaderCandidate
                    )
                )
            }
        }

        return ReconstructedTableGrid(
            rows = reconstructedRows,
            columns = columnIntervals,
            pageIndex = pageIndex,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )
    }

    /**
     * Clusters tokens into horizontal rows using median glyph height and vertical overlap.
     */
    private fun clusterRows(tokens: List<OcrToken>, rowToleranceFactor: Float): List<List<OcrToken>> {
        val sortedTokens = tokens.sortedBy { it.centerY }
        val heights = sortedTokens.map { it.height }.filter { it > 1f }.sorted()
        val medianHeight = if (heights.isNotEmpty()) heights[heights.size / 2] else 14f

        val rows = mutableListOf<MutableList<OcrToken>>()

        for (token in sortedTokens) {
            var bestRow: MutableList<OcrToken>? = null
            var bestDist = Float.MAX_VALUE

            for (row in rows) {
                val rowAvgCenterY = row.map { it.centerY }.average().toFloat()
                val rowAvgHeight = row.map { it.height }.average().toFloat().coerceAtLeast(medianHeight * 0.7f)
                val tolerance = max(rowAvgHeight * rowToleranceFactor.coerceIn(0.38f, 0.75f), 4f)
                val dy = abs(token.centerY - rowAvgCenterY)

                if (dy <= tolerance && dy < bestDist) {
                    bestDist = dy
                    bestRow = row
                }
            }

            if (bestRow != null) {
                bestRow.add(token)
            } else {
                rows.add(mutableListOf(token))
            }
        }

        return rows
            .sortedBy { row -> row.map { it.centerY }.average() }
            .map { row -> row.sortedBy { it.bounds.left } }
    }

    /**
     * Merges adjacent word tokens in the same row when separated by normal single-space word gaps,
     * keeping distinct columns separate when separated by wider column gutters.
     */
    private fun mergePhraseTokensInRow(rowTokens: List<OcrToken>): List<OcrToken> {
        if (rowTokens.size <= 1) return rowTokens

        val sorted = rowTokens.sortedBy { it.bounds.left }
        val avgHeight = sorted.map { it.height }.average().toFloat().coerceAtLeast(8f)
        val avgCharWidth = sorted.map {
            it.width / it.text.length.coerceAtLeast(1).toFloat()
        }.average().toFloat().coerceAtLeast(avgHeight * 0.42f)

        // Single-space gap threshold: words within ~1.65x average character width (or 0.65x row height) belong to the same phrase
        val maxPhraseGap = max(avgCharWidth * 1.65f, avgHeight * 0.62f)

        val merged = mutableListOf<OcrToken>()
        var current = sorted.first()

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val gap = next.bounds.left - current.bounds.right

            // Do not merge two standalone numeric values even if close together
            val bothNumeric = isLikelyStandaloneNumber(current.text) && isLikelyStandaloneNumber(next.text)

            if (gap <= maxPhraseGap && !bothNumeric) {
                current = OcrToken(
                    text = "${current.text.trim()} ${next.text.trim()}".trim(),
                    bounds = LatticeRect(
                        left = min(current.bounds.left, next.bounds.left),
                        top = min(current.bounds.top, next.bounds.top),
                        right = max(current.bounds.right, next.bounds.right),
                        bottom = max(current.bounds.bottom, next.bounds.bottom)
                    ),
                    confidence = min(current.confidence, next.confidence)
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    private fun isLikelyStandaloneNumber(text: String): Boolean {
        val cleaned = text.trim()
        return NUMERIC_PATTERN.matcher(cleaned).matches() ||
                CURRENCY_PATTERN.matcher(cleaned).matches() ||
                PERCENT_PATTERN.matcher(cleaned).matches()
    }

    /**
     * Selects phrases from rows that contain multiple horizontal cells (real table rows),
     * excluding single-cell full-width title banners or paragraph blocks that would obscure vertical column gutters.
     */
    private fun extractTabularPhraseTokens(
        phraseRows: List<List<OcrToken>>,
        pageWidth: Float
    ): List<OcrToken> {
        val multiCellRows = phraseRows.filter { row ->
            if (row.size < 2) return@filter false
            // Exclude rows where a single phrase spans more than 65% of the page width (paragraph line)
            val maxPhraseWidth = row.maxOf { it.width }
            maxPhraseWidth < (pageWidth * 0.65f)
        }
        return if (multiCellRows.isNotEmpty()) {
            multiCellRows.flatten()
        } else {
            phraseRows.flatten()
        }
    }

    /**
     * Projects phrase horizontal intervals [left, right] onto a 1D occupancy histogram along the X-axis
     * and detects column boundaries at occupancy valleys (gutters).
     */
    private fun detectColumnIntervals(
        tabularPhrases: List<OcrToken>,
        allPhraseRows: List<List<OcrToken>>,
        pageWidth: Float,
        policyAction: TableExtractionPolicyAction
    ): List<LatticeColumn> {
        if (tabularPhrases.isEmpty()) {
            return listOf(LatticeColumn(0, 0f, pageWidth, pageWidth))
        }

        // Determine the modal/90th-percentile column count across multi-cell rows
        val rowCellCounts = allPhraseRows.map { it.size }.filter { it >= 2 }.sorted()
        val targetMinCols = if (rowCellCounts.isNotEmpty()) {
            rowCellCounts[(rowCellCounts.size * 0.75).toInt().coerceIn(0, rowCellCounts.lastIndex)]
        } else {
            1
        }

        val bins = (pageWidth / 2f).toInt().coerceIn(240, 1600)
        val occupancy = FloatArray(bins)
        val binWidth = pageWidth / bins.toFloat()

        // Accumulate horizontal span coverage for every phrase in multi-cell rows
        for (phrase in tabularPhrases) {
            val pad = max(2f, phrase.height * 0.12f)
            val startBin = ((phrase.bounds.left + pad) / binWidth).toInt().coerceIn(0, bins - 1)
            val endBin = ((phrase.bounds.right - pad) / binWidth).toInt().coerceIn(startBin, bins - 1)
            for (b in startBin..endBin) {
                occupancy[b] += 1.0f
            }
        }

        // Light 3-bin smoothing to suppress sub-pixel jitter
        val smoothed = FloatArray(bins)
        for (i in 0 until bins) {
            var sum = 0f
            var count = 0
            for (w in -2..2) {
                val idx = i + w
                if (idx in 0 until bins) {
                    sum += occupancy[idx]
                    count++
                }
            }
            smoothed[i] = if (count > 0) sum / count else 0f
        }

        val minX = tabularPhrases.minOf { it.bounds.left }.coerceAtLeast(0f)
        val maxX = tabularPhrases.maxOf { it.bounds.right }.coerceAtMost(pageWidth)
        val minBin = (minX / binWidth).toInt().coerceIn(0, bins - 1)
        val maxBin = (maxX / binWidth).toInt().coerceIn(minBin, bins - 1)

        val maxOccupancy = smoothed.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val valleyThreshold = max(0.35f, maxOccupancy * (policyAction.valleyThreshold * 0.65f))

        val avgCharWidth = tabularPhrases.map {
            it.width / it.text.length.coerceAtLeast(1).toFloat()
        }.average().toFloat().coerceAtLeast(6f)
        val minColumnWidth = max(avgCharWidth * 2.2f, 16f)

        val cutPoints = mutableListOf(0f)
        var inValley = false
        var valleyStartBin = minBin
        var valleyMinBin = minBin
        var valleyMinVal = Float.MAX_VALUE

        for (i in minBin..maxBin) {
            val v = smoothed[i]
            if (v <= valleyThreshold) {
                if (!inValley) {
                    inValley = true
                    valleyStartBin = i
                    valleyMinBin = i
                    valleyMinVal = v
                } else if (v < valleyMinVal) {
                    valleyMinBin = i
                    valleyMinVal = v
                }
            } else if (inValley) {
                val valleyEndBin = i - 1
                val centerValleyBin = if (valleyMinVal == 0f) {
                    (valleyStartBin + valleyEndBin) / 2
                } else {
                    valleyMinBin
                }
                val cutX = centerValleyBin * binWidth
                if (cutX - cutPoints.last() >= minColumnWidth && (pageWidth - cutX) >= minColumnWidth) {
                    cutPoints.add(cutX)
                }
                inValley = false
                valleyMinVal = Float.MAX_VALUE
            }
        }
        cutPoints.add(pageWidth)

        var columns = buildColumnsFromCuts(cutPoints)

        // If interval projection yielded fewer columns than the clear multi-cell row structure, refine via left-edge clustering
        if (columns.size < targetMinCols && targetMinCols >= 2) {
            val clusteredCols = clusterColumnsByPhraseBounds(allPhraseRows, pageWidth, minColumnWidth)
            if (clusteredCols.size > columns.size) {
                columns = clusteredCols
            }
        }

        return if (columns.isNotEmpty()) columns else listOf(LatticeColumn(0, 0f, pageWidth, pageWidth))
    }

    private fun buildColumnsFromCuts(cutPoints: List<Float>): List<LatticeColumn> {
        val cols = mutableListOf<LatticeColumn>()
        for (i in 0 until cutPoints.size - 1) {
            val left = cutPoints[i]
            val right = cutPoints[i + 1]
            if (right - left > 8f) {
                cols.add(
                    LatticeColumn(
                        columnIndex = cols.size,
                        leftBound = left,
                        rightBound = right,
                        preferredWidth = right - left
                    )
                )
            }
        }
        return cols
    }

    /**
     * Fallback column boundary detector that clusters aligned phrase left/center positions across multi-cell rows.
     */
    private fun clusterColumnsByPhraseBounds(
        phraseRows: List<List<OcrToken>>,
        pageWidth: Float,
        minColumnWidth: Float
    ): List<LatticeColumn> {
        val multiRows = phraseRows.filter { it.size >= 2 }
        if (multiRows.isEmpty()) return listOf(LatticeColumn(0, 0f, pageWidth, pageWidth))

        data class SpanCluster(var leftSum: Float, var rightSum: Float, var count: Int) {
            val avgLeft: Float get() = leftSum / count
            val avgRight: Float get() = rightSum / count
            val center: Float get() = (avgLeft + avgRight) / 2f
        }

        val clusters = mutableListOf<SpanCluster>()
        for (row in multiRows.sortedByDescending { it.size }) {
            for (phrase in row) {
                val match = clusters.minByOrNull { c ->
                    val overlap = min(phrase.bounds.right, c.avgRight) - max(phrase.bounds.left, c.avgLeft)
                    if (overlap > 0f) -overlap else abs(phrase.bounds.left - c.avgLeft)
                }
                val isOverlapping = match != null && (
                    (min(phrase.bounds.right, match.avgRight) - max(phrase.bounds.left, match.avgLeft)) > 0f ||
                    abs(phrase.bounds.left - match.avgLeft) <= minColumnWidth
                )
                if (match != null && isOverlapping) {
                    match.leftSum += phrase.bounds.left
                    match.rightSum += phrase.bounds.right
                    match.count++
                } else {
                    clusters.add(SpanCluster(phrase.bounds.left, phrase.bounds.right, 1))
                }
            }
        }

        val sortedClusters = clusters.filter { it.count >= 1 }.sortedBy { it.avgLeft }
        if (sortedClusters.size <= 1) return listOf(LatticeColumn(0, 0f, pageWidth, pageWidth))

        val cuts = mutableListOf(0f)
        for (i in 0 until sortedClusters.size - 1) {
            val midCut = (sortedClusters[i].avgRight + sortedClusters[i + 1].avgLeft) / 2f
            if (midCut - cuts.last() >= minColumnWidth * 0.7f) {
                cuts.add(midCut.coerceIn(0f, pageWidth))
            }
        }
        cuts.add(pageWidth)

        return buildColumnsFromCuts(cuts)
    }

    private fun findBestColumnForPhrase(phrase: OcrToken, columns: List<LatticeColumn>): Int {
        if (columns.isEmpty()) return -1
        var bestIdx = 0
        var bestOverlap = -1f
        var bestCenterDist = Float.MAX_VALUE

        for ((idx, col) in columns.withIndex()) {
            val overlap = max(0f, min(phrase.bounds.right, col.rightBound) - max(phrase.bounds.left, col.leftBound))
            val colCenter = (col.leftBound + col.rightBound) / 2f
            val dist = abs(phrase.centerX - colCenter)

            if (overlap > bestOverlap || (overlap == bestOverlap && dist < bestCenterDist)) {
                bestOverlap = overlap
                bestCenterDist = dist
                bestIdx = idx
            }
        }
        return bestIdx
    }

    private data class InferredData(
        val dataType: CellDataType,
        val numericValue: Double? = null,
        val currencySymbol: String? = null
    )

    private fun inferCellData(text: String, isFirstRow: Boolean): InferredData {
        if (isFirstRow) {
            return InferredData(CellDataType.TEXT)
        }

        val cleaned = text.trim()

        // 1. Accounting negative number in parentheses: e.g. "(1,250.00)" or "($450.00)"
        val parenMatcher = PAREN_NEGATIVE_NUMERIC.matcher(cleaned)
        if (parenMatcher.matches()) {
            val symbol = parenMatcher.group(1)
            val numStr = parenMatcher.group(2)?.replace(",", "")
            val parsed = numStr?.toDoubleOrNull()
            if (parsed != null) {
                return if (!symbol.isNullOrBlank()) {
                    InferredData(CellDataType.CURRENCY, -parsed, symbol)
                } else {
                    InferredData(CellDataType.NUMERIC, -parsed)
                }
            }
        }

        // 2. Currency Check
        val currMatcher = CURRENCY_PATTERN.matcher(cleaned)
        if (currMatcher.matches()) {
            val symbol = currMatcher.group(1) ?: currMatcher.group(8) ?: "$"
            val numStr = (currMatcher.group(2) ?: currMatcher.group(6))?.replace(",", "")
            val parsedDouble = numStr?.toDoubleOrNull()
            if (parsedDouble != null) {
                return InferredData(CellDataType.CURRENCY, parsedDouble, symbol)
            }
        }

        // 3. Percentage Check
        if (PERCENT_PATTERN.matcher(cleaned).matches()) {
            val numStr = cleaned.replace("%", "").trim()
            val parsedDouble = numStr.toDoubleOrNull()
            if (parsedDouble != null) {
                return InferredData(CellDataType.PERCENTAGE, parsedDouble / 100.0)
            }
        }

        // 4. Pure Numeric Check (Integer or Float)
        if (NUMERIC_PATTERN.matcher(cleaned).matches()) {
            val numStr = cleaned.replace(",", "")
            val parsedDouble = numStr.toDoubleOrNull()
            if (parsedDouble != null) {
                return InferredData(CellDataType.NUMERIC, parsedDouble)
            }
        }

        return InferredData(CellDataType.TEXT)
    }
}
