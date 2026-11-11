package com.swiffle.app.conversion

import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Quantum-Wave Table Reconstruction Engine (QW-TRE).
 * 
 * Reconstructs 2D tabular structure from unstructured OCR word tokens using:
 *  1. 1D Continuous Gaussian Wavepacket Column Projection: Identifies column gutters as energy minima (valleys).
 *  2. Adaptive Baseline Hysteresis: Clusters words into discrete horizontal rows.
 *  3. 2D Spatial Lattice Binning: Re-assembles multi-word phrases into cohesive cells.
 *  4. Semantic Type Inference: Automatically tags numbers, currencies, percentages, and headers.
 */
object TableLatticeReconstructor {

    // Regex patterns for type inference
    private val NUMERIC_PATTERN = Pattern.compile("^[+-]?\\d{1,3}(,\\d{3})*(\\.\\d+)?$|^[+-]?\\d+(\\.\\d+)?$")
    private val CURRENCY_PATTERN = Pattern.compile("^([\\$€£₹¥])\\s*([+-]?\\d{1,3}(,\\d{3})*(\\.\\d+)?|[+-]?\\d+(\\.\\d+)?)$|^([+-]?\\d{1,3}(,\\d{3})*(\\.\\d+)?|[+-]?\\d+(\\.\\d+)?)\\s*([\\$€£₹¥])$")
    private val PERCENT_PATTERN = Pattern.compile("^[+-]?\\d+(\\.\\d+)?\\s*%$")

    /**
     * Executes end-to-end table reconstruction on a collection of OCR tokens
     * using dynamic reinforcement learning extraction policy action.
     */
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

        // 1. Group tokens into horizontal rows using adaptive baseline clustering
        val rowClusters = clusterRows(tokens, policyAction.rowToleranceFactor)

        // Filter out full-width banner/header title tokens before detecting columns
        // so wide company titles don't smear out the column gutters!
        val tabularTokens = extractTabularTokens(rowClusters, pageWidth)
        val tokensForColumns = if (tabularTokens.isNotEmpty()) tabularTokens else tokens

        // 2. Compute 1D Gaussian column density wave and detect column intervals
        val columnIntervals = detectColumnIntervals(tokensForColumns, pageWidth, policyAction)

        // 3. Bin tokens into 2D Lattice Cells
        val reconstructedRows = mutableListOf<LatticeRow>()

        rowClusters.forEachIndexed { rIdx, clusterTokens ->
            val rowTop = clusterTokens.minOf { it.bounds.top }
            val rowBottom = clusterTokens.maxOf { it.bounds.bottom }
            val rowBaseline = clusterTokens.map { it.centerY }.average().toFloat()

            val rowCells = mutableListOf<LatticeCell>()

            columnIntervals.forEachIndexed { cIdx, col ->
                // Collect all tokens that fall within this column's interval
                val cellTokens = clusterTokens.filter { token ->
                    val overlap = calculateOverlap(token.bounds.left, token.bounds.right, col.leftBound, col.rightBound)
                    overlap >= policyAction.minCellOverlap || (token.centerX >= col.leftBound && token.centerX <= col.rightBound)
                }.sortedBy { it.bounds.left }

                if (cellTokens.isNotEmpty()) {
                    val rawText = cellTokens.joinToString(" ") { it.text.trim() }.trim()
                    val cellBounds = LatticeRect(
                        left = cellTokens.minOf { it.bounds.left },
                        top = rowTop,
                        right = cellTokens.maxOf { it.bounds.right },
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

            // Only add rows that contain at least one valid cell
            if (rowCells.isNotEmpty()) {
                val isHeaderRow = (rIdx == 0) && rowCells.any { it.dataType == CellDataType.TEXT }
                reconstructedRows.add(
                    LatticeRow(
                        rowIndex = rIdx,
                        cells = rowCells,
                        baselineY = rowBaseline,
                        topY = rowTop,
                        bottomY = rowBottom,
                        isHeaderCandidate = isHeaderRow
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
     * Clusters tokens into discrete horizontal rows using baseline hysteresis.
     */
    private fun clusterRows(tokens: List<OcrToken>, rowToleranceFactor: Float): List<List<OcrToken>> {
        val sortedTokens = tokens.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrToken>>()

        for (token in sortedTokens) {
            var matchedRow: MutableList<OcrToken>? = null

            for (row in rows) {
                val rowAvgCenterY = row.map { it.centerY }.average().toFloat()
                val rowAvgHeight = row.map { it.height }.average().toFloat()
                val tolerance = max(rowAvgHeight * rowToleranceFactor, 12f)

                if (abs(token.centerY - rowAvgCenterY) <= tolerance) {
                    matchedRow = row
                    break
                }
            }

            if (matchedRow != null) {
                matchedRow.add(token)
            } else {
                rows.add(mutableListOf(token))
            }
        }

        // Sort rows top-down, and tokens within each row left-to-right
        return rows.sortedBy { row -> row.map { it.centerY }.average() }
            .map { row -> row.sortedBy { it.bounds.left } }
    }

    /**
     * Projects tokens into 1D continuous Gaussian density wave along X-axis
     * and extracts column intervals from probability valleys using RL policy action.
     */
    private fun detectColumnIntervals(
        tokens: List<OcrToken>,
        pageWidth: Float,
        policyAction: TableExtractionPolicyAction
    ): List<LatticeColumn> {
        if (tokens.isEmpty()) return emptyList()

        // Adaptive spatial resolution: 1 sample per 2 effective pixels
        val bins = (pageWidth / 2f).toInt().coerceIn(200, 1500)
        val density = FloatArray(bins)
        val binWidth = pageWidth / bins.toFloat()

        val avgTokenWidth = tokens.map { it.width }.average().toFloat()
        val sigma = (avgTokenWidth * policyAction.sigmaFactor).coerceIn(8f, 50f)
        val twoSigmaSq = 2f * sigma * sigma

        // Accumulate Gaussian wavepackets across all token spans
        for (token in tokens) {
            val startBin = (token.bounds.left / binWidth).toInt().coerceIn(0, bins - 1)
            val endBin = (token.bounds.right / binWidth).toInt().coerceIn(0, bins - 1)
            val center = token.centerX

            // Bounded window around token for 99.7% Gaussian energy
            val windowRadius = (3f * sigma / binWidth).toInt()
            val winStart = max(0, startBin - windowRadius)
            val winEnd = min(bins - 1, endBin + windowRadius)

            for (b in winStart..winEnd) {
                val x = b * binWidth + (binWidth / 2f)
                val dist = x - center
                density[b] += exp(- (dist * dist) / twoSigmaSq)
            }
        }

        // Smooth density array with moving average
        val smoothed = FloatArray(bins)
        val window = 5
        for (i in 0 until bins) {
            var sum = 0f
            var count = 0
            for (w in -window..window) {
                val idx = i + w
                if (idx in 0 until bins) {
                    sum += density[idx]
                    count++
                }
            }
            smoothed[i] = if (count > 0) sum / count else 0f
        }

        // Find valley cut points (local minima below relative threshold)
        val maxDensity = smoothed.maxOrNull() ?: 1f
        val valleyThreshold = maxDensity * policyAction.valleyThreshold

        val cutPoints = mutableListOf<Float>()
        cutPoints.add(0f)

        var inValley = false
        var valleyMinBin = -1
        var valleyMinVal = Float.MAX_VALUE

        for (i in 1 until bins - 1) {
            val v = smoothed[i]
            if (v < valleyThreshold) {
                if (!inValley) {
                    inValley = true
                    valleyMinBin = i
                    valleyMinVal = v
                } else if (v < valleyMinVal) {
                    valleyMinBin = i
                    valleyMinVal = v
                }
            } else {
                if (inValley) {
                    // Exiting valley: record the minimum point as boundary
                    val valleyX = valleyMinBin * binWidth
                    // Only add if reasonable separation from previous cut
                    if (cutPoints.isEmpty() || (valleyX - cutPoints.last()) > (avgTokenWidth * 0.6f)) {
                        cutPoints.add(valleyX)
                    }
                    inValley = false
                    valleyMinVal = Float.MAX_VALUE
                }
            }
        }
        cutPoints.add(pageWidth)

        // If no internal cuts detected, fall back to token-based column bounding
        if (cutPoints.size <= 2) {
            val sortedLefts = tokens.map { it.bounds.left }.distinct().sorted()
            if (sortedLefts.size > 1) {
                return fallbackColumnSegmentation(tokens, pageWidth)
            }
            return listOf(LatticeColumn(0, 0f, pageWidth, pageWidth))
        }

        val columns = mutableListOf<LatticeColumn>()
        for (i in 0 until cutPoints.size - 1) {
            val left = cutPoints[i]
            val right = cutPoints[i + 1]
            if (right - left > 10f) {
                columns.add(
                    LatticeColumn(
                        columnIndex = columns.size,
                        leftBound = left,
                        rightBound = right,
                        preferredWidth = right - left
                    )
                )
            }
        }

        return if (columns.isNotEmpty()) columns else listOf(LatticeColumn(0, 0f, pageWidth, pageWidth))
    }

    /**
     * Extracts tokens from rows that participate in tabular structure,
     * filtering out top full-width metadata titles (e.g. "Name of Company: ECO PROTECTION...")
     * that would otherwise blur out column gutters.
     */
    private fun extractTabularTokens(rowClusters: List<List<OcrToken>>, pageWidth: Float): List<OcrToken> {
        val result = mutableListOf<OcrToken>()
        for ((idx, row) in rowClusters.withIndex()) {
            if (row.isEmpty()) continue
            val rowText = row.joinToString(" ") { it.text }.lowercase()
            val isBanner = (idx < 5) && (
                rowText.contains("name of company") ||
                rowText.contains("bill of quantity report") ||
                rowText.contains("survey no") ||
                rowText.contains("pursuant to section")
            )
            val isSingleWide = row.size <= 2 && (row.last().bounds.right - row.first().bounds.left) > (pageWidth * 0.7f)
            if (!isBanner && !isSingleWide) {
                result.addAll(row)
            }
        }
        return result
    }

    /**
     * Fallback segmentation when wave density is flat or single-column.
     */
    private fun fallbackColumnSegmentation(tokens: List<OcrToken>, pageWidth: Float): List<LatticeColumn> {
        val leftEdges = tokens.map { it.bounds.left }.sorted()
        val clusters = mutableListOf<MutableList<Float>>()

        for (x in leftEdges) {
            val matched = clusters.find { cluster -> abs(cluster.average() - x) < 30f }
            if (matched != null) {
                matched.add(x)
            } else {
                clusters.add(mutableListOf(x))
            }
        }

        val centers = clusters.map { it.average().toFloat() }.sorted()
        val cuts = mutableListOf(0f)
        for (i in 0 until centers.size - 1) {
            cuts.add((centers[i] + centers[i + 1]) / 2f)
        }
        cuts.add(pageWidth)

        val columns = mutableListOf<LatticeColumn>()
        for (i in 0 until cuts.size - 1) {
            columns.add(
                LatticeColumn(
                    columnIndex = i,
                    leftBound = cuts[i],
                    rightBound = cuts[i + 1],
                    preferredWidth = cuts[i + 1] - cuts[i]
                )
            )
        }
        return columns
    }

    /**
     * Calculates 1D overlap between two bounding intervals [a1, a2] and [b1, b2].
     */
    private fun calculateOverlap(a1: Float, a2: Float, b1: Float, b2: Float): Float {
        val overlapStart = max(a1, b1)
        val overlapEnd = min(a2, b2)
        val overlap = max(0f, overlapEnd - overlapStart)
        val lenA = max(1f, a2 - a1)
        return overlap / lenA
    }

    private data class InferredData(
        val dataType: CellDataType,
        val numericValue: Double? = null,
        val currencySymbol: String? = null
    )

    /**
     * Analyzes raw cell string and derives semantic numeric / currency / percentage type.
     */
    private fun inferCellData(text: String, isFirstRow: Boolean): InferredData {
        if (isFirstRow) {
            return InferredData(CellDataType.TEXT)
        }

        val cleaned = text.trim()

        // 1. Currency Check
        val currMatcher = CURRENCY_PATTERN.matcher(cleaned)
        if (currMatcher.matches()) {
            val symbol = currMatcher.group(1) ?: currMatcher.group(8) ?: "$"
            val numStr = (currMatcher.group(2) ?: currMatcher.group(6))?.replace(",", "")
            val parsedDouble = numStr?.toDoubleOrNull()
            if (parsedDouble != null) {
                return InferredData(CellDataType.CURRENCY, parsedDouble, symbol)
            }
        }

        // 2. Percentage Check
        if (PERCENT_PATTERN.matcher(cleaned).matches()) {
            val numStr = cleaned.replace("%", "").trim()
            val parsedDouble = numStr.toDoubleOrNull()
            if (parsedDouble != null) {
                return InferredData(CellDataType.PERCENTAGE, parsedDouble / 100.0)
            }
        }

        // 3. Pure Numeric Check (Integer or Float)
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
