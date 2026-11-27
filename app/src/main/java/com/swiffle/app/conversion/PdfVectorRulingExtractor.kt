package com.swiffle.app.conversion

import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Kotlin 2D affine matrix matching the PDF specification:
 *   x' = a*x + c*y + e
 *   y' = b*x + d*y + f
 */
internal data class Affine2D(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val e: Float = 0f,
    val f: Float = 0f
) {
    /** PDF `cm` concatenation: CTM' = M × CTM */
    fun preMultiply(m: Affine2D): Affine2D {
        return Affine2D(
            a = m.a * a + m.b * c,
            b = m.a * b + m.b * d,
            c = m.c * a + m.d * c,
            d = m.c * b + m.d * d,
            e = m.e * a + m.f * c + e,
            f = m.e * b + m.f * d + f
        )
    }

    fun mapPoint(x: Float, y: Float): Pair<Float, Float> {
        return (a * x + c * y + e) to (b * x + d * y + f)
    }
}

internal data class HLine(
    val y: Float,
    val x1: Float,
    val x2: Float
) {
    val length: Float get() = x2 - x1
}

internal data class VLine(
    val x: Float,
    val y1: Float,
    val y2: Float
) {
    val length: Float get() = y2 - y1
}

internal data class LatticeCellSlot(
    val x1: Float,
    val x2: Float,
    val colStart: Int,
    val colSpan: Int
)

internal data class LatticeRowBand(
    val yTop: Float,
    val yBottom: Float,
    val cells: List<LatticeCellSlot>
)

/**
 * A discovered vector-ruled table grid in top-left visual page coordinates
 * (identical coordinate space to PDFBox TextPosition.xDirAdj / yDirAdj).
 */
internal data class LatticeTableRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val masterColXs: List<Float>,
    val rows: List<LatticeRowBand>,
    val isSemiLattice: Boolean = false
) {
    val colCount: Int get() = (masterColXs.size - 1).coerceAtLeast(1)
}

internal data class PageVectorGeometry(
    val hLines: List<HLine>,
    val vLines: List<VLine>,
    val latticeTables: List<LatticeTableRegion>,
    val pageWidth: Float,
    val pageHeight: Float
)

/**
 * Tabula-Lattice / pdfplumber-grade vector ruling & table topology extractor.
 *
 * Parses PDF page content streams (including nested Form XObjects) to extract exact
 * horizontal and vertical ruling segments in visual top-left coordinates, coalesces
 * segmented cell borders into continuous grid tracks, and uses Row-Band Column Topology
 * Partitioning so stacked sections with different column counts (e.g., a 4-column
 * return header above an 8-column tax table) stay cleanly separated without column bleed.
 */
internal object PdfVectorRulingExtractor {

    private const val LINE_AXIS_TOL = 1.4f
    private const val MERGE_COORD_TOL = 2.4f
    private const val MERGE_GAP_TOL = 4.5f
    private const val INTERSECT_TOL = 4.0f
    private const val MIN_HLINE_LEN = 12f
    private const val MIN_VLINE_LEN = 7f
    private const val THIN_RULE_THICKNESS = 4.2f
    private const val CLUSTER_TOL = 3.8f

    fun extractPageGeometry(page: PDPage): PageVectorGeometry {
        val cropBox = page.cropBox ?: page.mediaBox ?: PDRectangle(0f, 0f, 612f, 792f)
        val rotation = ((page.rotation % 360) + 360) % 360
        val visualWidth = if (rotation == 90 || rotation == 270) cropBox.height else cropBox.width
        val visualHeight = if (rotation == 90 || rotation == 270) cropBox.width else cropBox.height

        val rawHLines = mutableListOf<HLine>()
        val rawVLines = mutableListOf<VLine>()

        try {
            val parser = PDFStreamParser(page)
            parser.parse()
            val tokens = parser.tokens
            if (tokens != null) {
                interpretTokens(
                    tokens = tokens,
                    resources = page.resources,
                    initialCtm = Affine2D(),
                    cropBox = cropBox,
                    rotation = rotation,
                    visualWidth = visualWidth,
                    visualHeight = visualHeight,
                    hOut = rawHLines,
                    vOut = rawVLines,
                    depth = 0
                )
            }
        } catch (_: Exception) {
            // Gracefully degrade to stream-mode layout if a malformed content stream fails
        }

        val mergedH = mergeHLines(rawHLines)
        val mergedV = mergeVLines(rawVLines)
        val tables = discoverLatticeTables(mergedH, mergedV, visualWidth, visualHeight)

        return PageVectorGeometry(
            hLines = mergedH,
            vLines = mergedV,
            latticeTables = tables,
            pageWidth = visualWidth,
            pageHeight = visualHeight
        )
    }

    private data class PendingRect(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    private data class PendingSeg(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    private fun interpretTokens(
        tokens: List<Any>,
        resources: PDResources?,
        initialCtm: Affine2D,
        cropBox: PDRectangle,
        rotation: Int,
        visualWidth: Float,
        visualHeight: Float,
        hOut: MutableList<HLine>,
        vOut: MutableList<VLine>,
        depth: Int
    ) {
        if (depth > 4) return

        val ctmStack = ArrayDeque<Affine2D>()
        var ctm = initialCtm
        val operands = mutableListOf<COSBase>()

        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        var hasCurrentPoint = false

        val pendingSubpaths = mutableListOf<MutableList<PendingSeg>>()
        var currentSubpath = mutableListOf<PendingSeg>()
        val pendingRects = mutableListOf<PendingRect>()

        fun toVisual(pdfX: Float, pdfY: Float): Pair<Float, Float> {
            val (tx, ty) = ctm.mapPoint(pdfX, pdfY)
            return when (rotation) {
                90 -> (cropBox.upperRightY - ty) to (tx - cropBox.lowerLeftX)
                180 -> (cropBox.upperRightX - tx) to (ty - cropBox.lowerLeftY)
                270 -> (ty - cropBox.lowerLeftY) to (cropBox.upperRightX - tx)
                else -> (tx - cropBox.lowerLeftX) to (cropBox.upperRightY - ty)
            }
        }

        fun addSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
            val dx = abs(x2 - x1)
            val dy = abs(y2 - y1)
            if (dy <= LINE_AXIS_TOL && dx >= MIN_HLINE_LEN) {
                val lx1 = min(x1, x2).coerceIn(0f, visualWidth)
                val lx2 = max(x1, x2).coerceIn(0f, visualWidth)
                val ly = (y1 + y2) * 0.5f
                if (ly in -2f..(visualHeight + 2f) && (lx2 - lx1) >= MIN_HLINE_LEN) {
                    hOut.add(HLine(ly, lx1, lx2))
                }
            } else if (dx <= LINE_AXIS_TOL && dy >= MIN_VLINE_LEN) {
                val ly1 = min(y1, y2).coerceIn(0f, visualHeight)
                val ly2 = max(y1, y2).coerceIn(0f, visualHeight)
                val lx = (x1 + x2) * 0.5f
                if (lx in -2f..(visualWidth + 2f) && (ly2 - ly1) >= MIN_VLINE_LEN) {
                    vOut.add(VLine(lx, ly1, ly2))
                }
            }
        }

        fun finishCurrentSubpath() {
            if (currentSubpath.isNotEmpty()) {
                pendingSubpaths.add(currentSubpath)
                currentSubpath = mutableListOf()
            }
        }

        fun clearPath() {
            pendingSubpaths.clear()
            currentSubpath = mutableListOf()
            pendingRects.clear()
            hasCurrentPoint = false
        }

        fun commitStrokedPath() {
            finishCurrentSubpath()
            for (subpath in pendingSubpaths) {
                for (seg in subpath) {
                    addSegment(seg.x1, seg.y1, seg.x2, seg.y2)
                }
            }
            for (rect in pendingRects) {
                val w = rect.x2 - rect.x1
                val h = rect.y2 - rect.y1
                if (w >= MIN_HLINE_LEN) {
                    addSegment(rect.x1, rect.y1, rect.x2, rect.y1)
                    addSegment(rect.x1, rect.y2, rect.x2, rect.y2)
                }
                if (h >= MIN_VLINE_LEN) {
                    addSegment(rect.x1, rect.y1, rect.x1, rect.y2)
                    addSegment(rect.x2, rect.y1, rect.x2, rect.y2)
                }
            }
            clearPath()
        }

        fun commitFilledPath() {
            finishCurrentSubpath()
            for (rect in pendingRects) {
                val w = rect.x2 - rect.x1
                val h = rect.y2 - rect.y1
                // Thin filled bar used as a drawn horizontal or vertical rule
                if (h <= THIN_RULE_THICKNESS && w >= MIN_HLINE_LEN) {
                    addSegment(rect.x1, (rect.y1 + rect.y2) * 0.5f, rect.x2, (rect.y1 + rect.y2) * 0.5f)
                } else if (w <= THIN_RULE_THICKNESS && h >= MIN_VLINE_LEN) {
                    addSegment((rect.x1 + rect.x2) * 0.5f, rect.y1, (rect.x1 + rect.x2) * 0.5f, rect.y2)
                } else if (w >= 16f && h >= 10f && w < visualWidth * 0.96f && h < visualHeight * 0.14f) {
                    // Shaded cell or table header bar: top & bottom boundaries act as reliable row dividers
                    addSegment(rect.x1, rect.y1, rect.x2, rect.y1)
                    addSegment(rect.x1, rect.y2, rect.x2, rect.y2)
                }
            }
            // Check each filled polygon subpath individually for thin filled rules
            for (subpath in pendingSubpaths) {
                if (subpath.size in 3..5) {
                    val minX = subpath.minOf { min(it.x1, it.x2) }
                    val maxX = subpath.maxOf { max(it.x1, it.x2) }
                    val minY = subpath.minOf { min(it.y1, it.y2) }
                    val maxY = subpath.maxOf { max(it.y1, it.y2) }
                    val w = maxX - minX
                    val h = maxY - minY
                    if (h <= THIN_RULE_THICKNESS && w >= MIN_HLINE_LEN) {
                        addSegment(minX, (minY + maxY) * 0.5f, maxX, (minY + maxY) * 0.5f)
                    } else if (w <= THIN_RULE_THICKNESS && h >= MIN_VLINE_LEN) {
                        addSegment((minX + maxX) * 0.5f, minY, (minX + maxX) * 0.5f, maxY)
                    }
                }
            }
            clearPath()
        }

        fun numAt(idx: Int): Float? = (operands.getOrNull(idx) as? COSNumber)?.floatValue()

        for (token in tokens) {
            if (token is COSBase) {
                operands.add(token)
            } else if (token is Operator) {
                when (token.name) {
                    "q" -> {
                        if (ctmStack.size < 64) ctmStack.addLast(ctm)
                    }
                    "Q" -> {
                        if (ctmStack.isNotEmpty()) ctm = ctmStack.removeLast()
                    }
                    "cm" -> {
                        if (operands.size >= 6) {
                            val a = numAt(0) ?: 1f
                            val b = numAt(1) ?: 0f
                            val c = numAt(2) ?: 0f
                            val d = numAt(3) ?: 1f
                            val e = numAt(4) ?: 0f
                            val f = numAt(5) ?: 0f
                            ctm = ctm.preMultiply(Affine2D(a, b, c, d, e, f))
                        }
                    }
                    "m" -> {
                        val x = numAt(0)
                        val y = numAt(1)
                        if (x != null && y != null) {
                            finishCurrentSubpath()
                            val (vx, vy) = toVisual(x, y)
                            curX = vx
                            curY = vy
                            startX = vx
                            startY = vy
                            hasCurrentPoint = true
                        }
                    }
                    "l" -> {
                        val x = numAt(0)
                        val y = numAt(1)
                        if (x != null && y != null && hasCurrentPoint) {
                            val (vx, vy) = toVisual(x, y)
                            currentSubpath.add(PendingSeg(curX, curY, vx, vy))
                            curX = vx
                            curY = vy
                        }
                    }
                    "re" -> {
                        val x = numAt(0)
                        val y = numAt(1)
                        val w = numAt(2)
                        val h = numAt(3)
                        if (x != null && y != null && w != null && h != null) {
                            val p1 = toVisual(x, y)
                            val p2 = toVisual(x + w, y)
                            val p3 = toVisual(x + w, y + h)
                            val p4 = toVisual(x, y + h)
                            val rx1 = min(min(p1.first, p2.first), min(p3.first, p4.first))
                            val rx2 = max(max(p1.first, p2.first), max(p3.first, p4.first))
                            val ry1 = min(min(p1.second, p2.second), min(p3.second, p4.second))
                            val ry2 = max(max(p1.second, p2.second), max(p3.second, p4.second))
                            pendingRects.add(PendingRect(rx1, ry1, rx2, ry2))
                        }
                    }
                    "h" -> {
                        if (hasCurrentPoint) {
                            currentSubpath.add(PendingSeg(curX, curY, startX, startY))
                            curX = startX
                            curY = startY
                        }
                    }
                    "S" -> commitStrokedPath()
                    "s" -> {
                        if (hasCurrentPoint) currentSubpath.add(PendingSeg(curX, curY, startX, startY))
                        commitStrokedPath()
                    }
                    "B", "B*" -> {
                        commitStrokedPath()
                    }
                    "b", "b*" -> {
                        if (hasCurrentPoint) currentSubpath.add(PendingSeg(curX, curY, startX, startY))
                        commitStrokedPath()
                    }
                    "f", "F", "f*" -> commitFilledPath()
                    "W", "W*" -> Unit // Clipping operator; path is only discarded if followed by `n`
                    "n" -> clearPath()
                    "Do" -> {
                        val name = operands.firstOrNull() as? COSName
                        if (name != null && resources != null) {
                            try {
                                val xobj = resources.getXObject(name)
                                if (xobj is PDFormXObject) {
                                    val fm = xobj.matrix
                                    val formAffine = if (fm != null) {
                                        ctm.preMultiply(
                                            Affine2D(
                                                a = fm.scaleX,
                                                b = fm.shearY,
                                                c = fm.shearX,
                                                d = fm.scaleY,
                                                e = fm.translateX,
                                                f = fm.translateY
                                            )
                                        )
                                    } else ctm
                                    val childParser = PDFStreamParser(xobj)
                                    childParser.parse()
                                    val childTokens = childParser.tokens
                                    if (childTokens != null) {
                                        interpretTokens(
                                            tokens = childTokens,
                                            resources = xobj.resources ?: resources,
                                            initialCtm = formAffine,
                                            cropBox = cropBox,
                                            rotation = rotation,
                                            visualWidth = visualWidth,
                                            visualHeight = visualHeight,
                                            hOut = hOut,
                                            vOut = vOut,
                                            depth = depth + 1
                                        )
                                    }
                                }
                            } catch (_: Exception) {
                                // Ignore malformed XObjects
                            }
                        }
                    }
                }
                operands.clear()
            }
        }
    }

    private fun mergeHLines(raw: List<HLine>): List<HLine> {
        if (raw.isEmpty()) return emptyList()
        val sorted = raw.sortedWith(compareBy<HLine> { it.y }.thenBy { it.x1 })
        val groups = mutableListOf<MutableList<HLine>>()

        for (line in sorted) {
            val target = groups.lastOrNull { abs(it.first().y - line.y) <= MERGE_COORD_TOL }
            if (target != null) {
                target.add(line)
            } else {
                groups.add(mutableListOf(line))
            }
        }

        val result = mutableListOf<HLine>()
        for (group in groups) {
            val byX = group.sortedBy { it.x1 }
            var curY = byX.first().y
            var curX1 = byX.first().x1
            var curX2 = byX.first().x2
            var count = 1
            for (i in 1 until byX.size) {
                val seg = byX[i]
                if (seg.x1 <= curX2 + MERGE_GAP_TOL) {
                    curX2 = max(curX2, seg.x2)
                    curY = (curY * count + seg.y) / (count + 1)
                    count++
                } else {
                    result.add(HLine(curY, curX1, curX2))
                    curY = seg.y
                    curX1 = seg.x1
                    curX2 = seg.x2
                    count = 1
                }
            }
            result.add(HLine(curY, curX1, curX2))
        }
        return result
    }

    private fun mergeVLines(raw: List<VLine>): List<VLine> {
        if (raw.isEmpty()) return emptyList()
        val sorted = raw.sortedWith(compareBy<VLine> { it.x }.thenBy { it.y1 })
        val groups = mutableListOf<MutableList<VLine>>()

        for (line in sorted) {
            val target = groups.lastOrNull { abs(it.first().x - line.x) <= MERGE_COORD_TOL }
            if (target != null) {
                target.add(line)
            } else {
                groups.add(mutableListOf(line))
            }
        }

        val result = mutableListOf<VLine>()
        for (group in groups) {
            val byY = group.sortedBy { it.y1 }
            var curX = byY.first().x
            var curY1 = byY.first().y1
            var curY2 = byY.first().y2
            var count = 1
            for (i in 1 until byY.size) {
                val seg = byY[i]
                if (seg.y1 <= curY2 + MERGE_GAP_TOL) {
                    curY2 = max(curY2, seg.y2)
                    curX = (curX * count + seg.x) / (count + 1)
                    count++
                } else {
                    result.add(VLine(curX, curY1, curY2))
                    curX = seg.x
                    curY1 = seg.y1
                    curY2 = seg.y2
                    count = 1
                }
            }
            result.add(VLine(curX, curY1, curY2))
        }
        return result
    }

    private fun intersects(h: HLine, v: VLine): Boolean {
        return v.x >= h.x1 - INTERSECT_TOL &&
            v.x <= h.x2 + INTERSECT_TOL &&
            h.y >= v.y1 - INTERSECT_TOL &&
            h.y <= v.y2 + INTERSECT_TOL
    }

    /**
     * Discovers connected vector grids and partitions each grid into homogeneous
     * sub-tables whenever column topology changes between stacked sections (e.g.,
     * GSTR-1 header metadata grid stacked directly above the main 8-column return table).
     */
    private fun discoverLatticeTables(
        hLines: List<HLine>,
        vLines: List<VLine>,
        pageWidth: Float,
        pageHeight: Float
    ): List<LatticeTableRegion> {
        if (hLines.size < 2) return emptyList()

        val tables = mutableListOf<LatticeTableRegion>()
        val usedH = BooleanArray(hLines.size)
        val usedV = BooleanArray(vLines.size)

        // ── 1. Full Lattice Grids (Intersecting HLines + VLines) ──────────────
        if (vLines.size >= 2) {
            val hAdj = Array(hLines.size) { mutableListOf<Int>() }
            val vAdj = Array(vLines.size) { mutableListOf<Int>() }

            for (hi in hLines.indices) {
                for (vi in vLines.indices) {
                    if (intersects(hLines[hi], vLines[vi])) {
                        hAdj[hi].add(vi)
                        vAdj[vi].add(hi)
                    }
                }
            }

            for (startH in hLines.indices) {
                if (usedH[startH] || hAdj[startH].isEmpty()) continue

                val compH = mutableListOf<HLine>()
                val compV = mutableListOf<VLine>()
                val qH = ArrayDeque<Int>()
                val qV = ArrayDeque<Int>()

                usedH[startH] = true
                qH.addLast(startH)

                while (qH.isNotEmpty() || qV.isNotEmpty()) {
                    while (qH.isNotEmpty()) {
                        val hi = qH.removeFirst()
                        compH.add(hLines[hi])
                        for (vi in hAdj[hi]) {
                            if (!usedV[vi]) {
                                usedV[vi] = true
                                qV.addLast(vi)
                            }
                        }
                    }
                    while (qV.isNotEmpty()) {
                        val vi = qV.removeFirst()
                        compV.add(vLines[vi])
                        for (hi in vAdj[vi]) {
                            if (!usedH[hi]) {
                                usedH[hi] = true
                                qH.addLast(hi)
                            }
                        }
                    }
                }

                val subTables = buildPartitionedTablesFromComponent(compH, compV, pageWidth, pageHeight)
                tables.addAll(subTables)
            }
        }

        // ── 2. Horizontal-Rule Semi-Lattice Tables (for borderless-column reports) ──
        val remainingH = hLines.indices.filter { !usedH[it] && hLines[it].length >= pageWidth * 0.35f }
            .map { hLines[it] }
            .sortedBy { it.y }

        if (remainingH.size >= 3) {
            var start = 0
            while (start < remainingH.size) {
                val band = mutableListOf(remainingH[start])
                var idx = start + 1
                while (idx < remainingH.size) {
                    val prev = band.last()
                    val curr = remainingH[idx]
                    val sameXSpan = abs(curr.x1 - prev.x1) <= 22f && abs(curr.x2 - prev.x2) <= 22f
                    val reasonableGap = (curr.y - prev.y) in 7f..120f
                    if (sameXSpan && reasonableGap) {
                        band.add(curr)
                        idx++
                    } else {
                        break
                    }
                }
                if (band.size >= 3) {
                    val left = band.minOf { it.x1 }
                    val right = band.maxOf { it.x2 }
                    val top = band.first().y
                    val bottom = band.last().y
                    val overlapsExisting = tables.any { t ->
                        min(t.bottom, bottom) - max(t.top, top) > 6f &&
                            min(t.right, right) - max(t.left, left) > 20f
                    }
                    if (!overlapsExisting && (bottom - top) >= 24f) {
                        val rowYs = clusterCoords(band.map { it.y }, CLUSTER_TOL)
                        if (rowYs.size >= 3) {
                            val rowBands = (0 until rowYs.size - 1).mapNotNull { r ->
                                val y1 = rowYs[r]
                                val y2 = rowYs[r + 1]
                                if (y2 - y1 < 6f) null
                                else LatticeRowBand(
                                    yTop = y1,
                                    yBottom = y2,
                                    cells = listOf(LatticeCellSlot(left, right, 0, 1))
                                )
                            }
                            if (rowBands.size >= 2) {
                                tables.add(
                                    LatticeTableRegion(
                                        left = left,
                                        top = rowYs.first(),
                                        right = right,
                                        bottom = rowYs.last(),
                                        masterColXs = listOf(left, right),
                                        rows = rowBands,
                                        isSemiLattice = true
                                    )
                                )
                            }
                        }
                    }
                }
                start = max(idx, start + 1)
            }
        }

        return tables.sortedWith(compareBy<LatticeTableRegion> { it.top }.thenBy { it.left })
    }

    /**
     * Converts a connected component of HLines and VLines into one or more cleanly
     * aligned LatticeTableRegions by partitioning consecutive rows that share a compatible
     * column topology.
     */
    private fun buildPartitionedTablesFromComponent(
        compH: List<HLine>,
        compV: List<VLine>,
        pageWidth: Float,
        pageHeight: Float
    ): List<LatticeTableRegion> {
        val left = min(compH.minOf { it.x1 }, compV.minOf { it.x })
        val right = max(compH.maxOf { it.x2 }, compV.maxOf { it.x })
        val top = min(compH.minOf { it.y }, compV.minOf { it.y1 })
        val bottom = max(compH.maxOf { it.y }, compV.maxOf { it.y2 })

        val totalW = right - left
        val totalH = bottom - top
        if (totalW < pageWidth * 0.14f || totalH < 18f) return emptyList()

        val allRowYs = clusterCoords(compH.map { it.y }, CLUSTER_TOL)
        if (allRowYs.size < 2) return emptyList()

        // For each horizontal strip [yTop, yBottom], find the exact VLine X-positions crossing it
        data class RawStrip(val yTop: Float, val yBottom: Float, val xs: List<Float>)

        val strips = mutableListOf<RawStrip>()
        for (r in 0 until allRowYs.size - 1) {
            val yTop = allRowYs[r]
            val yBottom = allRowYs[r + 1]
            val stripH = yBottom - yTop
            if (stripH < 5.5f) continue

            // Verify there is actual horizontal ruling coverage at top or bottom of this strip
            val hasTopCover = compH.any { abs(it.y - yTop) <= CLUSTER_TOL + 1f && it.length >= totalW * 0.18f }
            val hasBotCover = compH.any { abs(it.y - yBottom) <= CLUSTER_TOL + 1f && it.length >= totalW * 0.18f }
            if (!hasTopCover && !hasBotCover) continue

            val crossingXs = compV.filter { v ->
                val overlap = min(v.y2, yBottom) - max(v.y1, yTop)
                overlap >= stripH * 0.55f
            }.map { it.x }

            val clusteredXs = clusterCoords(crossingXs, CLUSTER_TOL)
            if (clusteredXs.size >= 2) {
                strips.add(RawStrip(yTop, yBottom, clusteredXs))
            }
        }

        if (strips.isEmpty()) return emptyList()
        // Reject a single giant empty outer page border (1 row × 1 cell)
        if (strips.size == 1 && strips.first().xs.size <= 2) return emptyList()

        // Group consecutive strips into sub-tables with compatible column grids.
        // A row with fewer dividers (e.g. a full-width merged section banner or sub-spanned row)
        // stays attached to its table as long as ALL its dividers align with the active table's
        // master columns; if a row introduces NEW interior dividers incompatible with the active
        // table (e.g. switching from a 4-col key-value header box to an 8-col tax grid),
        // we cleanly start a new LatticeTableRegion!
        val resultTables = mutableListOf<LatticeTableRegion>()
        var groupStrips = mutableListOf<RawStrip>()
        var masterXs = mutableListOf<Float>()

        fun isSubsetOf(candidate: List<Float>, master: List<Float>): Boolean {
            return candidate.all { x -> master.any { abs(it - x) <= CLUSTER_TOL + 2.2f } }
        }

        fun flushGroup() {
            if (groupStrips.isEmpty() || masterXs.size < 2) {
                groupStrips.clear()
                masterXs.clear()
                return
            }
            val finalMaster = clusterCoords(masterXs, CLUSTER_TOL)
            val maxColsInAnyRow = groupStrips.maxOf { (it.xs.size - 1).coerceAtLeast(1) }
            // Must have either >= 2 columns OR (>= 2 rows & multiple cells) to be a real table
            if (finalMaster.size >= 3 || (groupStrips.size >= 2 && maxColsInAnyRow >= 2)) {
                val rows = groupStrips.map { strip ->
                    val cells = mutableListOf<LatticeCellSlot>()
                    for (c in 0 until strip.xs.size - 1) {
                        val cx1 = strip.xs[c]
                        val cx2 = strip.xs[c + 1]
                        if (cx2 - cx1 < 4f) continue
                        val startCol = nearestMasterIndex(cx1, finalMaster).coerceIn(0, finalMaster.size - 2)
                        val endCol = nearestMasterIndex(cx2, finalMaster).coerceIn(startCol + 1, finalMaster.size - 1)
                        cells.add(
                            LatticeCellSlot(
                                x1 = cx1,
                                x2 = cx2,
                                colStart = startCol,
                                colSpan = (endCol - startCol).coerceAtLeast(1)
                            )
                        )
                    }
                    LatticeRowBand(strip.yTop, strip.yBottom, cells)
                }.filter { it.cells.isNotEmpty() }

                if (rows.isNotEmpty()) {
                    // Reject a giant single-cell outer page frame wrapping non-tabular prose
                    val isSingleGiantBox = rows.size == 1 && finalMaster.size == 2 &&
                        (rows.first().yBottom - rows.first().yTop) > pageHeight * 0.45f
                    if (!isSingleGiantBox) {
                        resultTables.add(
                            LatticeTableRegion(
                                left = finalMaster.first(),
                                top = rows.first().yTop,
                                right = finalMaster.last(),
                                bottom = rows.last().yBottom,
                                masterColXs = finalMaster,
                                rows = rows,
                                isSemiLattice = false
                            )
                        )
                    }
                }
            }
            groupStrips.clear()
            masterXs.clear()
        }

        for (strip in strips) {
            if (groupStrips.isEmpty()) {
                groupStrips.add(strip)
                masterXs.addAll(strip.xs)
                continue
            }

            val gapFromPrev = strip.yTop - groupStrips.last().yBottom
            val sameOuterBounds = abs(strip.xs.first() - masterXs.first()) <= 16f &&
                abs(strip.xs.last() - masterXs.last()) <= 16f
            val compatible = gapFromPrev <= 18f && sameOuterBounds && (
                isSubsetOf(strip.xs, masterXs) ||
                    // Allow a more detailed row to refine an initially spanned banner row (e.g. 1-col title bar above 8-col header)
                    (masterXs.size <= 3 && isSubsetOf(masterXs, strip.xs)) ||
                    // Allow minor 1-line header sub-column split if existing rows are subset of new strip
                    isSubsetOf(masterXs, strip.xs)
                )

            if (compatible) {
                groupStrips.add(strip)
                masterXs = clusterCoords(masterXs + strip.xs, CLUSTER_TOL).toMutableList()
            } else {
                flushGroup()
                groupStrips.add(strip)
                masterXs.addAll(strip.xs)
            }
        }
        flushGroup()

        return resultTables
    }

    private fun nearestMasterIndex(x: Float, master: List<Float>): Int {
        var bestIdx = 0
        var bestDist = Float.MAX_VALUE
        for (i in master.indices) {
            val d = abs(master[i] - x)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    internal fun clusterCoords(values: List<Float>, tolerance: Float): List<Float> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
        val clusters = mutableListOf<MutableList<Float>>()
        for (v in sorted) {
            val last = clusters.lastOrNull()
            if (last != null && abs(v - last.average().toFloat()) <= tolerance) {
                last.add(v)
            } else {
                clusters.add(mutableListOf(v))
            }
        }
        return clusters.map { it.average().toFloat() }
    }
}
