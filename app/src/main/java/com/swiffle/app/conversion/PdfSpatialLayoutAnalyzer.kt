package com.swiffle.app.conversion

import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ============================================================================
// Spatial Geometry & Typography Data Structures
// ============================================================================

internal data class RawGlyph(
    val unicode: String,
    val x: Float,
    val y: Float,           // Baseline Y in top-left visual coordinates
    val width: Float,
    val height: Float,
    val yTop: Float,
    val yBottom: Float,
    val fontSizePt: Float,
    val widthOfSpace: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isMonospace: Boolean,
    val isObliqueRotated: Boolean
) {
    val cx: Float get() = x + width * 0.5f
    val cy: Float get() = (yTop + yBottom) * 0.5f
    val xEnd: Float get() = x + width
}

internal data class LineSegment(
    val xStart: Float,
    val xEnd: Float,
    val rawText: String,
    val styledText: String,
    val avgFontSize: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isMonospace: Boolean
)

internal data class GlyphLine(
    val y: Float,
    val yTop: Float,
    val yBottom: Float,
    val xStart: Float,
    val xEnd: Float,
    val pageWidth: Float,
    val pageHeight: Float,
    val avgFontSize: Float,
    val maxFontSize: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isMonospace: Boolean,
    val rawText: String,
    val styledText: String,
    val segments: List<LineSegment>
)

internal data class TypographyProfile(
    val modalBodyFontSize: Float,
    val modalLineSpacing: Float,
    val typicalLeftMargin: Float,
    val repeatingHeaders: Set<String>,
    val repeatingFooters: Set<String>,
    val hasPdfOutline: Boolean = false
)

internal data class PageMarkdownOutput(
    val markdown: String,
    val headings: List<MarkdownTocEntry>,
    val tableCount: Int,
    val codeBlockCount: Int,
    val headersStripped: Int,
    val detectedWatermarks: Set<String>
)

/**
 * Raw page-level glyph collector that hooks directly into PDFBox's `processTextPosition`,
 * bypassing PDFTextStripper's fragile cell-splitting heuristics and capturing exact
 * per-glyph affine rotation angles so diagonal watermarks (e.g. 45° "FINAL") never
 * corrupt body text, table rows, or headings.
 */
internal class SpatialGlyphCollector : PDFTextStripper() {

    val bodyGlyphs = mutableListOf<RawGlyph>()
    val rotatedWatermarkGlyphs = mutableListOf<RawGlyph>()
    private var pageRotationDeg: Int = 0

    init {
        sortByPosition = false
        suppressDuplicateOverlappingText = true
    }

    fun prepareForPage(rotationDeg: Int) {
        bodyGlyphs.clear()
        rotatedWatermarkGlyphs.clear()
        pageRotationDeg = ((rotationDeg % 360) + 360) % 360
    }

    override fun processTextPosition(text: TextPosition) {
        val rawUnicode = text.unicode
        if (rawUnicode.isNullOrEmpty()) return

        val normalized = PdfTextCleaner.normalizeGlyphUnicode(rawUnicode)
        if (normalized.isEmpty()) return

        val fontName = text.font?.name?.lowercase(Locale.US).orEmpty()
        val fontSizePt = resolveFontSize(text)
        val width = max(text.widthDirAdj, 0f)
        val height = max(text.heightDir, fontSizePt * 0.72f).coerceAtLeast(3f)
        val x = text.xDirAdj
        val y = text.yDirAdj
        val yTop = y - height * 0.85f
        val yBottom = y + height * 0.18f

        val isBold = fontName.contains("bold") || fontName.contains("black") ||
            fontName.contains("heavy") || fontName.contains("demi") || fontName.contains("semibold")
        val isItalic = fontName.contains("italic") || fontName.contains("oblique") ||
            fontName.contains("slant")
        val isMono = fontName.contains("mono") || fontName.contains("courier") ||
            fontName.contains("consola") || fontName.contains("menlo") ||
            fontName.contains("fira") || fontName.contains("jetbrains") ||
            fontName.contains("sourcecode") || fontName.contains("inconsolata") ||
            fontName.contains("code")

        val isOblique = isGlyphObliqueRotated(text)
        val spaceW = max(text.widthOfSpace, fontSizePt * 0.27f).coerceAtLeast(2.2f)

        val glyph = RawGlyph(
            unicode = normalized,
            x = x,
            y = y,
            width = width,
            height = height,
            yTop = yTop,
            yBottom = yBottom,
            fontSizePt = fontSizePt,
            widthOfSpace = spaceW,
            isBold = isBold,
            isItalic = isItalic,
            isMonospace = isMono,
            isObliqueRotated = isOblique
        )

        if (isOblique) {
            rotatedWatermarkGlyphs.add(glyph)
        } else {
            bodyGlyphs.add(glyph)
        }
    }

    /**
     * Detects diagonal / oblique rotated text (such as 30°–60° "FINAL" or "DRAFT" watermarks)
     * from the glyph's affine TextMatrix, while allowing normal 0°/90°/180°/270° text.
     */
    private fun isGlyphObliqueRotated(tp: TextPosition): Boolean {
        val tm = tp.textMatrix ?: return false
        val a = tm.scaleX.toDouble()
        val b = tm.shearY.toDouble()
        if (abs(a) < 1e-5 && abs(b) < 1e-5) return false
        var deg = Math.toDegrees(atan2(b, a))
        deg = ((deg % 360.0) + 360.0) % 360.0
        val distToCardinal = min(
            min(abs(deg - 0.0), abs(deg - 360.0)),
            min(abs(deg - 90.0), min(abs(deg - 180.0), abs(deg - 270.0)))
        )
        return distToCardinal > 7.0
    }

    private fun resolveFontSize(tp: TextPosition): Float {
        val pt = tp.fontSizeInPt
        if (pt in 3.5f..180f) return pt
        val h = tp.heightDir
        if (h in 3.5f..180f) return h * 1.18f
        return 10.5f
    }
}

/**
 * Unicode ligature expansion and PDF text-layer dropped-"fi" ligature repair.
 */
internal object PdfTextCleaner {

    // Repairs common words where PDF generators (e.g. Indian GST Portal iText/OpenPDF)
    // omit the 'fi' / 'i' glyph in embedded subset fonts.
    private val LIGATURE_WORD_FIXES: List<Pair<Regex, String>> = listOf(
        Regex("\\b([Vv])erifcation\\b") to "$1erification",
        Regex("\\b([Vv])erifed\\b") to "$1erified",
        Regex("\\b([Aa])ffrm\\b") to "$1ffirm",
        Regex("\\b([Aa])ffrmed\\b") to "$1ffirmed",
        Regex("\\b([Bb])eneft(s?)\\b") to "$1enefit$2",
        Regex("\\b([Cc])ertifcate(s?)\\b") to "$1ertificate$2",
        Regex("\\b([Cc])ertifed\\b") to "$1ertified",
        Regex("\\b([Nn])otifcation(s?)\\b") to "$1otification$2",
        Regex("\\b([Nn])otifed\\b") to "$1otified",
        Regex("\\b([Ss])pecifed\\b") to "$1pecified",
        Regex("\\b([Cc])lassifcation(s?)\\b") to "$1lassification$2",
        Regex("\\b([Mm])odifcation(s?)\\b") to "$1odification$2",
        Regex("\\b([Mm])odifed\\b") to "$1odified",
        Regex("\\b([Ii])dentifcation\\b") to "$1dentification",
        Regex("\\b([Ii])dentifed\\b") to "$1dentified",
        Regex("\\b([Ss])ignifcant(ly)?\\b") to "$1ignificant$2",
        Regex("\\b([Ff])nancial\\b") to "$1inancial",
        Regex("\\b([Oo])ffcial(s?)\\b") to "$1fficial$2",
        Regex("\\b([Oo])ffce(s?)\\b") to "$1ffice$2",
        Regex("\\b([Ff])ling(s?)\\b") to "$1iling$2",
        Regex("\\b([Ff])led\\b") to "$1iled",
        Regex("\\b([Ff])nal\\b") to "$1inal",
        Regex("\\b([Pp])roft(s?)\\b") to "$1rofit$2",
        Regex("\\b([Dd])efcit(s?)\\b") to "$1eficit$2",
        Regex("\\b([Rr])ectifcation\\b") to "$1ectification"
    )

    fun normalizeGlyphUnicode(raw: String): String {
        val sb = StringBuilder(raw.length + 2)
        for (ch in raw) {
            when (ch) {
                '\uFB00' -> sb.append("ff")
                '\uFB01' -> sb.append("fi")
                '\uFB02' -> sb.append("fl")
                '\uFB03' -> sb.append("ffi")
                '\uFB04' -> sb.append("ffl")
                '\uFB05', '\uFB06' -> sb.append("st")
                '\u00A0', '\u2007', '\u202F' -> sb.append(' ')
                '\u200B', '\u200C', '\u200D', '\uFEFF', '\u00AD' -> Unit // strip zero-width & soft hyphen
                else -> if (ch >= ' ' || ch == '\t') sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun repairText(input: String): String {
        if (input.isEmpty()) return input
        var text = input
        // Fast check: only run regexes when 'f' appears in text
        if (text.contains('f') || text.contains('F')) {
            for ((regex, replacement) in LIGATURE_WORD_FIXES) {
                if (regex.containsMatchIn(text)) {
                    text = regex.replace(text, replacement)
                }
            }
        }
        return text
    }
}

/**
 * Spatial 2D Page Layout Analyzer & Markdown Compiler.
 *
 * Combines:
 * - Vector Lattice Table cell assignment (locking multi-line text & blank cells to exact (r, c))
 * - Semi-Lattice & Stream-Mode borderless table detection with X-projection gutter alignment
 * - Page-wide visual row reassembly (eliminating mid-word / mid-sentence PDF chunk breaks)
 * - Airtight multi-column prose detection (never scrambles forms, invoices, or wide tables)
 * - Disciplined heading & list extraction (preserves (a)/(b) markers & meaningful footers)
 */
internal object PdfSpatialLayoutAnalyzer {

    private val PAGE_NUMBER_REGEX = Regex(
        "^(?:page\\s+)?\\d{1,5}(?:\\s*(?:/|of)\\s*\\d{1,5})?$|^[-–—]\\s*\\d{1,5}\\s*[-–—]$|^[ivxlcdm]{1,7}$",
        RegexOption.IGNORE_CASE
    )
    private val IP_ADDRESS_REGEX = Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")
    private val PROTECTED_FOOTER_REGEX = Regex(
        "(?:\\bFINAL\\b|\\bDRAFT\\b|\\bGSTIN\\b|\\bARN\\b|\\bVERIFICATION\\b|\\bSIGNATURE\\b|IP\\s*ADDRESS|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})",
        RegexOption.IGNORE_CASE
    )
    private val BULLET_REGEX = Regex("^([•●▪■◦‣▸▹◆◇✓✔☐☑\\-*–—])\\s+(.+)$")
    private val NUMERIC_ORDERED_REGEX = Regex("^(\\d{1,4})([.)])\\s+(.+)$")
    private val ALPHA_SUBITEM_REGEX = Regex("^(\\([a-zA-Z]{1,3}\\)|\\([ivxlcdm]{1,5}\\)|[a-zA-Z]\\))\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val CHAPTER_HEADING_REGEX = Regex(
        "^(?:chapter|section|part|appendix|unit|module|annexure|schedule)\\s+[\\dIVXLC]+\\b.*$",
        RegexOption.IGNORE_CASE
    )
    private val NUMERIC_OR_CURRENCY_REGEX = Regex("^[₹$€£¥+\\-]?[\\d,]+(?:\\.\\d+)?%?$")
    private val FORM_LABEL_BLACKLIST = setOf(
        "date", "date:", "signature", "signature:", "name", "name:", "designation", "designation:",
        "place", "place:", "verification", "verification:", "records", "no. of records",
        "type", "document type", "value", "value (₹)", "taxable value", "taxable value (₹)",
        "total", "subtotal", "grand total", "status", "remarks", "amount", "cess", "cess (₹)",
        "integrated tax", "central tax", "state/ut tax", "sr. no.", "sl. no.", "s. no.",
        "description", "particulars", "details", "page", "final", "draft", "original", "nil"
    )
    private val DANGLING_END_WORDS = setOf(
        "of", "in", "to", "for", "with", "on", "at", "by", "from", "the", "a", "an", "and", "or", "as", "that", "which"
    )

    private data class PositionedBlock(
        val yTop: Float,
        val xLeft: Float,
        val markdown: String,
        val headings: List<MarkdownTocEntry> = emptyList(),
        val tableCount: Int = 0,
        val codeBlockCount: Int = 0
    )

    fun analyzeAndRenderPage(
        bodyGlyphsInput: List<RawGlyph>,
        rotatedWatermarkGlyphs: List<RawGlyph>,
        geometry: PageVectorGeometry,
        pageNum: Int,
        profile: TypographyProfile,
        config: PdfToMarkdownConfig,
        usedAnchors: MutableMap<String, Int>
    ): PageMarkdownOutput {
        val detectedWatermarks = mutableSetOf<String>()
        if (rotatedWatermarkGlyphs.isNotEmpty()) {
            val wmText = reconstructWatermarkText(rotatedWatermarkGlyphs)
            if (wmText.isNotEmpty()) detectedWatermarks.add(wmText)
        }

        // Filter out oversized upright background watermarks overlapping normal body text
        val (cleanGlyphs, uprightWatermarks) = filterOversizedBackgroundWatermarks(
            bodyGlyphsInput,
            profile.modalBodyFontSize,
            geometry.pageHeight
        )
        detectedWatermarks.addAll(uprightWatermarks)

        if (cleanGlyphs.isEmpty()) {
            return PageMarkdownOutput("", emptyList(), 0, 0, 0, detectedWatermarks)
        }

        val consumed = BooleanArray(cleanGlyphs.size)
        val blocks = mutableListOf<PositionedBlock>()

        // ── Stage 1: Assign glyphs to Vector Lattice Tables ───────────────────
        val acceptedTables = mutableListOf<LatticeTableRegion>()
        if (config.detectTablesAsGfm && geometry.latticeTables.isNotEmpty()) {
            for (table in geometry.latticeTables) {
                val matchedIndices = mutableListOf<Int>()
                for (gi in cleanGlyphs.indices) {
                    if (consumed[gi]) continue
                    val g = cleanGlyphs[gi]
                    if (g.cx >= table.left - 3.0f && g.cx <= table.right + 3.0f &&
                        g.cy >= table.top - 3.0f && g.cy <= table.bottom + 3.0f
                    ) {
                        matchedIndices.add(gi)
                    }
                }
                if (matchedIndices.isEmpty()) continue

                val tableGlyphs = matchedIndices.map { cleanGlyphs[it] }
                val rendered = if (!table.isSemiLattice) {
                    renderFullLatticeTable(table, tableGlyphs, config)
                } else {
                    renderSemiLatticeTable(table, tableGlyphs, config)
                }

                if (rendered != null) {
                    for (gi in matchedIndices) consumed[gi] = true
                    acceptedTables.add(table)
                    blocks.add(
                        PositionedBlock(
                            yTop = table.top,
                            xLeft = table.left,
                            markdown = rendered,
                            tableCount = 1
                        )
                    )
                }
            }
        }

        // ── Stage 2: Assemble Remaining Non-Table Glyphs into True Visual Rows ─
        val remainingGlyphs = cleanGlyphs.indices.filter { !consumed[it] }.map { cleanGlyphs[it] }
        var nonTableLines = assembleVisualLines(
            glyphs = remainingGlyphs,
            pageWidth = geometry.pageWidth,
            pageHeight = geometry.pageHeight,
            preserveStyles = config.preserveRichTypography
        )

        // ── Stage 3: Safe Header/Footer Chrome Stripping (Preserves IP & FINAL) ─
        var strippedCount = 0
        if (config.stripHeadersAndFooters && nonTableLines.isNotEmpty()) {
            val (kept, stripped) = stripRunningChromeSafe(nonTableLines, profile)
            nonTableLines = kept
            strippedCount = stripped
        }

        // ── Stage 4: Partition Non-Table Lines into Vertical Bands Around Tables ─
        val sortedTables = acceptedTables.sortedBy { it.top }
        val bands = partitionLinesAroundTables(nonTableLines, sortedTables)

        for (band in bands) {
            if (band.isEmpty()) continue
            val orderedBand = if (config.reorderMultiColumn && band.size >= 12) {
                reorderMultiColumnProseSafe(band)
            } else {
                band.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
            }

            val bandOut = renderNonTableBand(
                lines = orderedBand,
                pageNum = pageNum,
                profile = profile,
                config = config,
                usedAnchors = usedAnchors
            )
            if (bandOut.markdown.isNotBlank()) {
                blocks.add(
                    PositionedBlock(
                        yTop = band.minOf { it.yTop },
                        xLeft = band.minOf { it.xStart },
                        markdown = bandOut.markdown,
                        headings = bandOut.headings,
                        tableCount = bandOut.tableCount,
                        codeBlockCount = bandOut.codeBlockCount
                    )
                )
            }
        }

        // ── Stage 5: Interleave All Blocks in Exact Top-to-Bottom, Left-to-Right Order ─
        val orderedBlocks = blocks.sortedWith(compareBy<PositionedBlock> { (it.yTop / 10f).toInt() }.thenBy { it.xLeft })
        val combinedMd = orderedBlocks.joinToString("\n\n") { it.markdown.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        return PageMarkdownOutput(
            markdown = combinedMd,
            headings = orderedBlocks.flatMap { it.headings },
            tableCount = orderedBlocks.sumOf { it.tableCount },
            codeBlockCount = orderedBlocks.sumOf { it.codeBlockCount },
            headersStripped = strippedCount,
            detectedWatermarks = detectedWatermarks
        )
    }

    // =========================================================================
    // Lattice Table Renderer (Preserves Exact Cells, Blank Slots, Multi-line)
    // =========================================================================

    private fun renderFullLatticeTable(
        table: LatticeTableRegion,
        glyphs: List<RawGlyph>,
        config: PdfToMarkdownConfig
    ): String? {
        val colCount = table.colCount
        if (colCount < 2 || table.rows.isEmpty()) return null

        val gridRows = mutableListOf<Array<String>>()
        val isFullWidthBanner = mutableListOf<Boolean>()

        for (rowBand in table.rows) {
            val rowGlyphs = glyphs.filter { g ->
                g.cy >= rowBand.yTop - 2.2f && g.cy <= rowBand.yBottom + 2.2f
            }
            if (rowGlyphs.isEmpty()) continue

            val rowArr = Array(colCount) { "" }
            val singleWideCell = rowBand.cells.size == 1 && rowBand.cells.first().colSpan >= max(2, (colCount * 0.7f).roundToInt())

            for (cell in rowBand.cells) {
                val cellGlyphs = rowGlyphs.filter { g ->
                    g.cx >= cell.x1 - 2.0f && g.cx <= cell.x2 + 2.0f
                }
                if (cellGlyphs.isEmpty()) continue

                val cellText = buildCellText(cellGlyphs, config.dehyphenateAndUnwrap)
                if (cellText.isNotEmpty()) {
                    val colIdx = cell.colStart.coerceIn(0, colCount - 1)
                    rowArr[colIdx] = if (rowArr[colIdx].isEmpty()) cellText else "${rowArr[colIdx]} $cellText"
                }
            }

            if (rowArr.any { it.isNotEmpty() }) {
                gridRows.add(rowArr)
                isFullWidthBanner.add(singleWideCell)
            }
        }

        if (gridRows.isEmpty()) return null

        // Safety guard: if every populated row only ever uses 1 column (e.g. a bordered prose box
        // like a Verification declaration), release it back to prose rather than making a 1-col table!
        val distinctUsedCols = (0 until colCount).count { c -> gridRows.any { it[c].isNotEmpty() } }
        if (distinctUsedCols < 2) return null

        // Remove completely empty columns across the entire table (e.g. narrow decorative margin borders)
        val activeColIndices = (0 until colCount).filter { c ->
            gridRows.any { it[c].isNotEmpty() }
        }
        if (activeColIndices.size < 2) return null

        val compactRows = gridRows.map { row ->
            activeColIndices.map { idx -> escapeGfmCell(row[idx]) }.toMutableList()
        }.toMutableList()

        // If Row 0 is a single full-width banner title sitting above a multi-column table header,
        // emit it cleanly as a bold caption above the Markdown table so Row 1 becomes the GFM header!
        val sb = StringBuilder()
        if (compactRows.size >= 3 && isFullWidthBanner.firstOrNull() == true) {
            val bannerText = compactRows.first().firstOrNull { it.isNotEmpty() }.orEmpty()
            if (bannerText.isNotEmpty()) {
                sb.append("**").append(bannerText).append("**\n\n")
                compactRows.removeAt(0)
                isFullWidthBanner.removeAt(0)
            }
        }

        // Merge split 2-row table headers (e.g. Row 0 has parent column group, Row 1 has sub-headers
        // and contains zero numeric/currency data values)
        if (compactRows.size >= 3 && shouldMergeSplitHeader(compactRows[0], compactRows[1])) {
            val mergedHeader = MutableList(compactRows[0].size) { c ->
                val top = compactRows[0][c]
                val bot = compactRows[1][c]
                when {
                    top.isEmpty() -> bot
                    bot.isEmpty() -> top
                    top.equals(bot, ignoreCase = true) -> top
                    else -> "$top $bot"
                }
            }
            compactRows[0] = mergedHeader
            compactRows.removeAt(1)
            isFullWidthBanner.removeAt(1)
        }

        // Format full-width interior section banners inside the table cleanly in bold
        for (r in 1 until compactRows.size) {
            if (r < isFullWidthBanner.size && isFullWidthBanner[r]) {
                val firstIdx = compactRows[r].indexOfFirst { it.isNotEmpty() }
                if (firstIdx >= 0 && !compactRows[r][firstIdx].startsWith("**")) {
                    compactRows[r][firstIdx] = "**${compactRows[r][firstIdx]}**"
                }
            }
        }

        // Build clean GitHub Flavored Markdown table
        val finalCols = compactRows.first().size
        sb.append("| ").append(compactRows.first().joinToString(" | ")).append(" |\n")
        sb.append("| ").append(List(finalCols) { "---" }.joinToString(" | ")).append(" |\n")
        for (r in 1 until compactRows.size) {
            sb.append("| ").append(compactRows[r].joinToString(" | ")).append(" |\n")
        }
        return sb.toString().trimEnd()
    }

    /**
     * Renders a Horizontal-Rule Semi-Lattice table (horizontal row dividers, no vertical lines)
     * by computing column tracks via X-interval projection across all rows.
     */
    private fun renderSemiLatticeTable(
        table: LatticeTableRegion,
        glyphs: List<RawGlyph>,
        config: PdfToMarkdownConfig
    ): String? {
        val rowSegments = mutableListOf<List<LineSegment>>()
        for (rowBand in table.rows) {
            val rowGlyphs = glyphs.filter { g ->
                g.cy >= rowBand.yTop - 2.0f && g.cy <= rowBand.yBottom + 2.0f
            }
            if (rowGlyphs.isEmpty()) continue
            val lines = assembleVisualLines(
                glyphs = rowGlyphs,
                pageWidth = table.right,
                pageHeight = table.bottom,
                preserveStyles = false
            )
            val segs = lines.flatMap { it.segments }
            if (segs.isNotEmpty()) {
                rowSegments.add(segs)
            }
        }
        if (rowSegments.size < 2) return null

        val tracks = discoverColumnTracksByProjection(rowSegments, table.left, table.right)
        if (tracks.size < 2 || tracks.size > 14) return null

        // Verify at least 40% of rows actually populate >= 2 columns
        val multiColRows = rowSegments.count { segs ->
            segs.map { findColumnTrackIndex(it, tracks) }.distinct().size >= 2
        }
        if (multiColRows < max(2, (rowSegments.size * 0.4f).roundToInt())) return null

        val grid = rowSegments.map { segs ->
            val arr = Array(tracks.size) { "" }
            for (seg in segs.sortedBy { it.xStart }) {
                val c = findColumnTrackIndex(seg, tracks)
                val clean = escapeGfmCell(PdfTextCleaner.repairText(seg.rawText))
                arr[c] = if (arr[c].isEmpty()) clean else joinCellFragments(arr[c], clean, config.dehyphenateAndUnwrap)
            }
            arr.toList()
        }

        val sb = StringBuilder()
        sb.append("| ").append(grid.first().joinToString(" | ")).append(" |\n")
        sb.append("| ").append(List(tracks.size) { "---" }.joinToString(" | ")).append(" |\n")
        for (r in 1 until grid.size) {
            sb.append("| ").append(grid[r].joinToString(" | ")).append(" |\n")
        }
        return sb.toString().trimEnd()
    }

    private fun shouldMergeSplitHeader(row0: List<String>, row1: List<String>): Boolean {
        if (row0.size != row1.size) return false
        val row1NonEmpty = row1.filter { it.isNotEmpty() }
        if (row1NonEmpty.isEmpty()) return false
        // Reject if row1 contains any numeric data values (0.00, ₹123, 12, etc.)
        val hasNumericData = row1NonEmpty.any { cell ->
            val stripped = cell.replace(" ", "")
            NUMERIC_OR_CURRENCY_REGEX.matches(stripped) || stripped.contains("0.00")
        }
        if (hasNumericData) return false
        // Merge if row0 has blank slots filled by row1 OR row1 has short header continuations (e.g. "(₹)", "Tax")
        val fillsBlanks = row0.indices.any { c -> row0[c].isEmpty() && row1[c].isNotEmpty() }
        val allShort = row1NonEmpty.all { it.length <= 28 }
        return allShort && (fillsBlanks || row1NonEmpty.all { !it.endsWith(".") })
    }

    private fun buildCellText(cellGlyphs: List<RawGlyph>, dehyphenate: Boolean): String {
        val visualSubLines = clusterGlyphsByBaseline(cellGlyphs)
        val parts = mutableListOf<String>()
        for (subLine in visualSubLines) {
            val ordered = subLine.sortedBy { it.x }
            val lineText = buildPlainLineText(ordered)
            if (lineText.isNotEmpty()) {
                parts.add(PdfTextCleaner.repairText(lineText))
            }
        }
        if (parts.isEmpty()) return ""
        val sb = StringBuilder(parts.first())
        for (i in 1 until parts.size) {
            val next = parts[i]
            val current = sb.toString()
            if (dehyphenate && current.endsWith("-") && !current.endsWith(" -") &&
                next.firstOrNull()?.isLowerCase() == true
            ) {
                sb.setLength(sb.length - 1)
                sb.append(next)
            } else {
                sb.append(' ').append(next)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun joinCellFragments(existing: String, next: String, dehyphenate: Boolean): String {
        if (existing.isEmpty()) return next
        if (dehyphenate && existing.endsWith("-") && !existing.endsWith(" -") &&
            next.firstOrNull()?.isLowerCase() == true
        ) {
            return existing.dropLast(1) + next
        }
        return "$existing $next"
    }

    private fun escapeGfmCell(text: String): String {
        return text.replace("|", "\\|")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // =========================================================================
    // Page-Wide Visual Row Reassembly (Eliminates Mid-Word / Mid-Sentence Splits)
    // =========================================================================

    fun assembleVisualLines(
        glyphs: List<RawGlyph>,
        pageWidth: Float,
        pageHeight: Float,
        preserveStyles: Boolean
    ): List<GlyphLine> {
        if (glyphs.isEmpty()) return emptyList()
        val buckets = clusterGlyphsByBaseline(glyphs)
        val result = mutableListOf<GlyphLine>()
        for (bucket in buckets) {
            val line = buildGlyphLineFromRaw(
                positions = bucket.sortedBy { it.x },
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                preserveStyles = preserveStyles
            )
            if (line != null && line.rawText.isNotBlank()) {
                result.add(line)
            }
        }
        return result.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
    }

    private fun clusterGlyphsByBaseline(glyphs: List<RawGlyph>): List<List<RawGlyph>> {
        if (glyphs.isEmpty()) return emptyList()
        val sorted = glyphs.sortedWith(compareBy<RawGlyph> { it.y }.thenBy { it.x })
        val buckets = mutableListOf<MutableList<RawGlyph>>()
        val bucketAnchorY = mutableListOf<Float>()
        val bucketAvgH = mutableListOf<Float>()

        for (g in sorted) {
            var bestIdx = -1
            var bestDist = Float.MAX_VALUE
            // Check recent buckets (since glyphs are sorted by Y)
            val scanStart = max(0, buckets.size - 8)
            for (bi in scanStart until buckets.size) {
                val refY = bucketAnchorY[bi]
                val tol = max(2.4f, min(bucketAvgH[bi], g.fontSizePt) * 0.42f)
                val dist = abs(refY - g.y)
                if (dist <= tol && dist < bestDist) {
                    bestDist = dist
                    bestIdx = bi
                }
            }
            if (bestIdx >= 0) {
                val b = buckets[bestIdx]
                val n = b.size
                b.add(g)
                bucketAnchorY[bestIdx] = (bucketAnchorY[bestIdx] * n + g.y) / (n + 1)
                bucketAvgH[bestIdx] = (bucketAvgH[bestIdx] * n + g.fontSizePt) / (n + 1)
            } else {
                buckets.add(mutableListOf(g))
                bucketAnchorY.add(g.y)
                bucketAvgH.add(g.fontSizePt)
            }
        }
        return buckets
    }

    private fun buildPlainLineText(ordered: List<RawGlyph>): String {
        if (ordered.isEmpty()) return ""
        val sb = StringBuilder()
        var prev: RawGlyph? = null
        for (g in ordered) {
            if (prev != null) {
                val gap = g.x - prev.xEnd
                val wordGap = max(prev.widthOfSpace * 0.38f, prev.fontSizePt * 0.15f).coerceAtLeast(1.1f)
                if (gap > wordGap && !g.unicode.startsWith(" ") && !sb.endsWith(" ")) {
                    sb.append(' ')
                }
            }
            sb.append(g.unicode)
            prev = g
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun buildGlyphLineFromRaw(
        positions: List<RawGlyph>,
        pageWidth: Float,
        pageHeight: Float,
        preserveStyles: Boolean
    ): GlyphLine? {
        if (positions.isEmpty()) return null

        val y = positions.map { it.y }.average().toFloat()
        val yTop = positions.minOf { it.yTop }
        val yBottom = positions.maxOf { it.yBottom }
        val xStart = positions.minOf { it.x }
        val xEnd = positions.maxOf { it.xEnd }

        var fontSizeSum = 0f
        var maxFontSize = 0f
        var nonSpaceChars = 0
        var boldChars = 0
        var italicChars = 0
        var monoChars = 0

        val rawBuilder = StringBuilder()
        val segments = mutableListOf<LineSegment>()

        // Split horizontal column segments ONLY at wide inter-column whitespace gaps
        val segStartIndices = mutableListOf(0)
        for (i in 1 until positions.size) {
            val prev = positions[i - 1]
            val curr = positions[i]
            val gap = curr.x - prev.xEnd
            val colGapThreshold = max(prev.widthOfSpace * 2.6f, prev.fontSizePt * 0.92f).coerceAtLeast(9f)
            if (gap > colGapThreshold) {
                segStartIndices.add(i)
            }
        }
        segStartIndices.add(positions.size)

        for (sIdx in 0 until segStartIndices.size - 1) {
            val slice = positions.subList(segStartIndices[sIdx], segStartIndices[sIdx + 1])
            if (slice.isEmpty()) continue

            val segRaw = StringBuilder()
            val segStyled = StringBuilder()
            var segBoldChars = 0
            var segItalicChars = 0
            var segMonoChars = 0
            var segNonSpace = 0
            var segFontSum = 0f

            var runBold = false
            var runItalic = false
            var runMono = false
            val runBuffer = StringBuilder()

            fun flushRun() {
                if (runBuffer.isEmpty()) return
                val str = PdfTextCleaner.repairText(runBuffer.toString())
                runBuffer.clear()
                if (!preserveStyles || str.isBlank()) {
                    segStyled.append(str)
                    return
                }
                val leading = str.takeWhile { it.isWhitespace() }
                val trailing = str.takeLastWhile { it.isWhitespace() }
                val core = str.trim()
                if (core.isEmpty()) {
                    segStyled.append(str)
                    return
                }
                val wrapped = when {
                    runMono -> "`${core.replace("`", "'")}`"
                    runBold && runItalic -> "***$core***"
                    runBold -> "**$core**"
                    runItalic -> "*$core*"
                    else -> core
                }
                segStyled.append(leading).append(wrapped).append(trailing)
            }

            var prevPos: RawGlyph? = null
            for (g in slice) {
                val u = g.unicode
                if (prevPos != null) {
                    val gap = g.x - prevPos.xEnd
                    val wordGap = max(prevPos.widthOfSpace * 0.38f, g.fontSizePt * 0.15f).coerceAtLeast(1.1f)
                    if (gap > wordGap && !u.startsWith(" ") && !segRaw.endsWith(" ")) {
                        segRaw.append(' ')
                        runBuffer.append(' ')
                    }
                }

                if (!u.isBlank()) {
                    if (runBuffer.isNotEmpty() && (g.isBold != runBold || g.isItalic != runItalic || g.isMonospace != runMono)) {
                        flushRun()
                    }
                    runBold = g.isBold
                    runItalic = g.isItalic
                    runMono = g.isMonospace

                    nonSpaceChars += u.length
                    fontSizeSum += g.fontSizePt * u.length
                    if (g.fontSizePt > maxFontSize) maxFontSize = g.fontSizePt
                    if (g.isBold) boldChars += u.length
                    if (g.isItalic) italicChars += u.length
                    if (g.isMonospace) monoChars += u.length

                    segNonSpace += u.length
                    segFontSum += g.fontSizePt * u.length
                    if (g.isBold) segBoldChars += u.length
                    if (g.isItalic) segItalicChars += u.length
                    if (g.isMonospace) segMonoChars += u.length
                }

                segRaw.append(u)
                runBuffer.append(u)
                prevPos = g
            }
            flushRun()

            val cleanedSegRaw = PdfTextCleaner.repairText(segRaw.toString().replace(Regex("\\s+"), " ").trim())
            val cleanedSegStyled = segStyled.toString().replace(Regex(" {2,}"), " ").trim()
            if (cleanedSegRaw.isNotEmpty()) {
                val segAvgPt = if (segNonSpace > 0) segFontSum / segNonSpace else 10.5f
                segments.add(
                    LineSegment(
                        xStart = slice.first().x,
                        xEnd = slice.last().xEnd,
                        rawText = cleanedSegRaw,
                        styledText = cleanedSegStyled.ifBlank { cleanedSegRaw },
                        avgFontSize = segAvgPt,
                        isBold = segNonSpace > 0 && segBoldChars >= segNonSpace * 0.65f,
                        isItalic = segNonSpace > 0 && segItalicChars >= segNonSpace * 0.65f,
                        isMonospace = segNonSpace > 0 && segMonoChars >= segNonSpace * 0.65f
                    )
                )
                if (rawBuilder.isNotEmpty()) rawBuilder.append("   ")
                rawBuilder.append(cleanedSegRaw)
            }
        }

        if (segments.isEmpty()) return null

        val avgFontSize = if (nonSpaceChars > 0) fontSizeSum / nonSpaceChars else 10.5f
        val lineIsBold = nonSpaceChars > 0 && boldChars >= nonSpaceChars * 0.65f
        val lineIsItalic = nonSpaceChars > 0 && italicChars >= nonSpaceChars * 0.65f
        val lineIsMono = nonSpaceChars > 0 && monoChars >= nonSpaceChars * 0.65f
        val combinedStyled = if (segments.size == 1) {
            segments.first().styledText
        } else {
            segments.joinToString(" ") { it.styledText }
        }

        return GlyphLine(
            y = y,
            yTop = yTop,
            yBottom = yBottom,
            xStart = xStart,
            xEnd = xEnd,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            avgFontSize = avgFontSize,
            maxFontSize = if (maxFontSize > 0f) maxFontSize else avgFontSize,
            isBold = lineIsBold,
            isItalic = lineIsItalic,
            isMonospace = lineIsMono,
            rawText = rawBuilder.toString().trim(),
            styledText = combinedStyled.trim(),
            segments = segments
        )
    }

    // =========================================================================
    // Watermark Filtering & Safe Chrome Stripping
    // =========================================================================

    private fun reconstructWatermarkText(glyphs: List<RawGlyph>): String {
        if (glyphs.isEmpty()) return ""
        val joined = glyphs.sortedBy { it.x }
            .joinToString("") { it.unicode }
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (joined.length in 3..48) joined else ""
    }

    /**
     * Filters giant upright background watermarks (e.g., 60pt "CONFIDENTIAL" or "FINAL" stamped
     * across the center of the page overlapping normal body text).
     */
    private fun filterOversizedBackgroundWatermarks(
        glyphs: List<RawGlyph>,
        modalBodyPt: Float,
        pageHeight: Float
    ): Pair<List<RawGlyph>, Set<String>> {
        if (glyphs.isEmpty()) return glyphs to emptySet()
        val giantThreshold = max(modalBodyPt * 3.1f, 34f)
        val giantGlyphs = glyphs.filter {
            it.fontSizePt >= giantThreshold && it.y in (pageHeight * 0.14f)..(pageHeight * 0.86f)
        }
        if (giantGlyphs.isEmpty()) return glyphs to emptySet()

        val normalGlyphs = glyphs.filter { it.fontSizePt < giantThreshold }
        if (normalGlyphs.size < 10) return glyphs to emptySet()

        // Confirm these giant glyphs actually overlap normal body glyphs vertically (true background watermark,
        // NOT a genuine cover-page H1 title sitting cleanly in whitespace)
        val overlappingGiant = giantGlyphs.filter { g ->
            normalGlyphs.any { n ->
                min(g.yBottom, n.yBottom) - max(g.yTop, n.yTop) > 2f &&
                    min(g.xEnd, n.xEnd) - max(g.x, n.x) > -20f
            }
        }
        if (overlappingGiant.isEmpty()) return glyphs to emptySet()

        val giantSet = overlappingGiant.toHashSet()
        val wmText = reconstructWatermarkText(overlappingGiant)
        val filtered = glyphs.filter { it !in giantSet }
        return filtered to (if (wmText.isNotEmpty()) setOf(wmText) else emptySet())
    }

    private fun stripRunningChromeSafe(
        lines: List<GlyphLine>,
        profile: TypographyProfile
    ): Pair<List<GlyphLine>, Int> {
        if (lines.isEmpty()) return lines to 0
        val kept = mutableListOf<GlyphLine>()
        var stripped = 0

        for (line in lines) {
            val inTopBand = line.y <= line.pageHeight * 0.09f
            val inBottomBand = line.y >= line.pageHeight * 0.91f
            val raw = line.rawText.trim()

            // NEVER strip legally/technically meaningful footers (IP address, FINAL, ARN, GSTIN, etc.)
            if (PROTECTED_FOOTER_REGEX.containsMatchIn(raw) || IP_ADDRESS_REGEX.containsMatchIn(raw)) {
                kept.add(line)
                continue
            }

            val norm = normalizeEdgeKey(raw)
            val isPageNum = (inTopBand || inBottomBand) && PAGE_NUMBER_REGEX.matches(raw)
            val isRepeatingHeader = inTopBand && norm in profile.repeatingHeaders && line.segments.size <= 2
            val isRepeatingFooter = inBottomBand && norm in profile.repeatingFooters && line.segments.size <= 2

            if (isPageNum || isRepeatingHeader || isRepeatingFooter) {
                stripped++
            } else {
                kept.add(line)
            }
        }
        return kept to stripped
    }

    internal fun normalizeEdgeKey(text: String): String {
        val cleaned = text.trim()
            .replace(Regex("\\d+"), "#")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.US)
        return if (cleaned.length in 1..90) cleaned else ""
    }

    private fun partitionLinesAroundTables(
        lines: List<GlyphLine>,
        tables: List<LatticeTableRegion>
    ): List<List<GlyphLine>> {
        if (lines.isEmpty()) return emptyList()
        if (tables.isEmpty()) return listOf(lines)

        val bands = mutableListOf<MutableList<GlyphLine>>()
        val boundaries = mutableListOf(Float.NEGATIVE_INFINITY)
        for (t in tables) {
            boundaries.add(t.top)
            boundaries.add(t.bottom)
        }
        boundaries.add(Float.POSITIVE_INFINITY)

        val sortedLines = lines.sortedBy { it.y }
        var curBand = mutableListOf<GlyphLine>()
        var curTableIdx = 0

        for (line in sortedLines) {
            while (curTableIdx < tables.size && line.y > tables[curTableIdx].bottom) {
                if (curBand.isNotEmpty()) {
                    bands.add(curBand)
                    curBand = mutableListOf()
                }
                curTableIdx++
            }
            curBand.add(line)
        }
        if (curBand.isNotEmpty()) bands.add(curBand)
        return bands
    }

    // =========================================================================
    // Airtight 2-Column Academic Prose Reordering (Never Scrambles Forms/Tables)
    // =========================================================================

    private fun reorderMultiColumnProseSafe(lines: List<GlyphLine>): List<GlyphLine> {
        if (lines.size < 12) return lines.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
        val pageWidth = lines.first().pageWidth.coerceAtLeast(200f)
        val gutterLeft = pageWidth * 0.45f
        val gutterRight = pageWidth * 0.55f

        val leftLines = mutableListOf<GlyphLine>()
        val rightLines = mutableListOf<GlyphLine>()
        val fullWidthLines = mutableListOf<GlyphLine>()

        for (line in lines) {
            when {
                line.segments.size >= 3 -> fullWidthLines.add(line)
                line.xEnd <= gutterRight && line.xStart < gutterLeft -> leftLines.add(line)
                line.xStart >= gutterLeft -> rightLines.add(line)
                else -> fullWidthLines.add(line)
            }
        }

        // Strict academic-prose criteria:
        // 1. Both halves must have >= 6 prose lines
        // 2. Average line length >= 26 chars in BOTH halves (rejects short labels / numbers / codes!)
        // 3. Right column cannot be mostly numeric/currency/dates (rejects key-value forms & tables!)
        if (leftLines.size < 6 || rightLines.size < 6) {
            return lines.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
        }
        val totalSplit = leftLines.size + rightLines.size
        if (totalSplit < lines.size * 0.65f) {
            return lines.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
        }
        val leftAvgLen = leftLines.map { it.rawText.length }.average()
        val rightAvgLen = rightLines.map { it.rawText.length }.average()
        if (leftAvgLen < 26.0 || rightAvgLen < 26.0) {
            return lines.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
        }
        val rightNumericRatio = rightLines.count { l ->
            val words = l.rawText.split(" ")
            words.count { it.any { ch -> ch.isDigit() } } >= max(1, words.size / 2)
        }.toFloat() / rightLines.size
        if (rightNumericRatio >= 0.35f) {
            return lines.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
        }

        val minColY = min(leftLines.minOf { it.y }, rightLines.minOf { it.y })
        val maxColY = max(leftLines.maxOf { it.y }, rightLines.maxOf { it.y })
        val topBanners = fullWidthLines.filter { it.y < minColY }.sortedBy { it.y }
        val midFullWidth = fullWidthLines.filter { it.y in minColY..maxColY }.sortedBy { it.y }
        val bottomFullWidth = fullWidthLines.filter { it.y > maxColY }.sortedBy { it.y }

        return buildList {
            addAll(topBanners)
            addAll(leftLines.sortedBy { it.y })
            addAll(rightLines.sortedBy { it.y })
            addAll(midFullWidth)
            addAll(bottomFullWidth)
        }
    }

    // =========================================================================
    // Non-Table Band Renderer (Stream Tables, Code, Headings, Lists, Prose)
    // =========================================================================

    private data class BandOutput(
        val markdown: String,
        val headings: List<MarkdownTocEntry>,
        val tableCount: Int,
        val codeBlockCount: Int
    )

    private fun renderNonTableBand(
        lines: List<GlyphLine>,
        pageNum: Int,
        profile: TypographyProfile,
        config: PdfToMarkdownConfig,
        usedAnchors: MutableMap<String, Int>
    ): BandOutput {
        val out = StringBuilder()
        val headings = mutableListOf<MarkdownTocEntry>()
        var tableCount = 0
        var codeBlockCount = 0

        val paragraphBuffer = StringBuilder()
        var lastParagraphY = -1f

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                if (out.isNotEmpty() && !out.endsWith("\n\n")) {
                    out.append(if (out.endsWith("\n")) "\n" else "\n\n")
                }
                out.append(paragraphBuffer.toString().trim())
                out.append("\n\n")
                paragraphBuffer.clear()
            }
            lastParagraphY = -1f
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val raw = line.rawText.trim()
            if (raw.isEmpty()) {
                i++
                continue
            }

            // 1. Borderless Stream-Mode GFM Table Detection (with X-Projection Blank Cell Preservation)
            if (config.detectTablesAsGfm && line.segments.size >= 2) {
                val consumedRows = collectStreamTableBlock(lines, i, profile)
                if (consumedRows.size >= 2) {
                    val formatted = formatStreamGfmTable(consumedRows, config.dehyphenateAndUnwrap)
                    if (formatted != null) {
                        flushParagraph()
                        out.append(formatted).append("\n\n")
                        tableCount++
                        i += consumedRows.size
                        continue
                    }
                }
            }

            // 2. Fenced Code Block Detection
            if (config.preserveRichTypography && isCodeLine(line, profile)) {
                val codeLines = mutableListOf<GlyphLine>()
                var j = i
                while (j < lines.size && (isCodeLine(lines[j], profile) ||
                        (codeLines.isNotEmpty() && lines[j].rawText.length <= 80 && looksLikeContinuationCode(lines[j].rawText)))
                ) {
                    codeLines.add(lines[j])
                    j++
                }
                if (codeLines.size >= 2 || (codeLines.size == 1 && raw.length >= 24 && raw.contains("{"))) {
                    flushParagraph()
                    val codeText = codeLines.joinToString("\n") { it.rawText }
                    val lang = detectCodeLanguage(codeText)
                    out.append("```$lang\n").append(codeText).append("\n```\n\n")
                    codeBlockCount++
                    i = j
                    continue
                }
            }

            // 3. Disciplined Semantic Heading Detection
            val headingLevel = detectHeadingLevelStrict(line, profile)
            if (headingLevel > 0) {
                flushParagraph()
                val cleanTitle = cleanHeadingText(raw)
                val anchor = createUniqueAnchor(cleanTitle, usedAnchors)
                headings.add(MarkdownTocEntry(headingLevel, cleanTitle, anchor, pageNum))
                out.append("#".repeat(headingLevel)).append(" ").append(cleanTitle).append("\n\n")
                i++
                continue
            }

            // 4. Bullet, Numbered & Alphabetic Sub-Item Lists (Preserving (a)/(b) Labels!)
            val bulletMatch = BULLET_REGEX.matchEntire(raw)
            val numOrderMatch = NUMERIC_ORDERED_REGEX.matchEntire(raw)
            val alphaSubMatch = ALPHA_SUBITEM_REGEX.matchEntire(raw)
            if (bulletMatch != null || numOrderMatch != null || alphaSubMatch != null) {
                flushParagraph()
                val indentDepth = ((line.xStart - profile.typicalLeftMargin) / 18f)
                    .toInt()
                    .coerceIn(0, 3)
                val indentStr = "  ".repeat(indentDepth)
                when {
                    bulletMatch != null -> {
                        val itemBody = stripLeadingBullet(line.styledText)
                        out.append("$indentStr- $itemBody\n")
                    }
                    numOrderMatch != null -> {
                        val num = numOrderMatch.groupValues[1]
                        val itemBody = stripLeadingNumericOrder(line.styledText)
                        out.append("$indentStr$num. $itemBody\n")
                    }
                    else -> {
                        // Explicitly preserve (a), (b), (i), (ii) labels!
                        val marker = alphaSubMatch!!.groupValues[1]
                        val itemBody = stripLeadingAlphaOrder(line.styledText)
                        out.append("$indentStr- **$marker** $itemBody\n")
                    }
                }
                i++
                continue
            }

            // 5. Blockquote Detection
            val isIndentedQuote = line.xStart > profile.typicalLeftMargin + 26f &&
                line.xEnd < line.pageWidth - 36f &&
                raw.length >= 28 &&
                line.isItalic
            if (isIndentedQuote) {
                flushParagraph()
                out.append("> ${line.styledText}\n\n")
                i++
                continue
            }

            // 6. Prose Paragraph with Smart De-Hyphenation & Unwrapping
            val textToAppend = if (config.preserveRichTypography) line.styledText else raw
            val hasParagraphGap = lastParagraphY > 0f && (line.y - lastParagraphY) > profile.modalLineSpacing * 1.48f
            val previousEndedSentence = paragraphBuffer.isNotEmpty() &&
                (paragraphBuffer.endsWith(".") || paragraphBuffer.endsWith("!") ||
                    paragraphBuffer.endsWith("?")) &&
                line.xStart > profile.typicalLeftMargin + 10f

            if (paragraphBuffer.isNotEmpty() && (hasParagraphGap || previousEndedSentence || !config.dehyphenateAndUnwrap)) {
                flushParagraph()
            }

            if (paragraphBuffer.isEmpty()) {
                paragraphBuffer.append(textToAppend)
            } else {
                val currentText = paragraphBuffer.toString()
                if (config.dehyphenateAndUnwrap &&
                    currentText.endsWith("-") &&
                    !currentText.endsWith(" -") &&
                    textToAppend.firstOrNull()?.isLowerCase() == true
                ) {
                    paragraphBuffer.setLength(paragraphBuffer.length - 1)
                    paragraphBuffer.append(textToAppend)
                } else {
                    paragraphBuffer.append(" ").append(textToAppend)
                }
            }
            lastParagraphY = line.y
            i++
        }

        flushParagraph()

        return BandOutput(
            markdown = out.toString().replace(Regex("\n{3,}"), "\n\n").trim(),
            headings = headings,
            tableCount = tableCount,
            codeBlockCount = codeBlockCount
        )
    }

    // =========================================================================
    // Stream-Mode Borderless Table Detector with X-Projection Column Tracks
    // =========================================================================

    private fun collectStreamTableBlock(
        lines: List<GlyphLine>,
        startIdx: Int,
        profile: TypographyProfile
    ): List<GlyphLine> {
        val rows = mutableListOf<GlyphLine>()
        var idx = startIdx
        var prevY = lines[startIdx].y
        val col0MaxX = lines[startIdx].segments.first().xEnd + 18f

        while (idx < lines.size) {
            val l = lines[idx]
            if (idx > startIdx && abs(l.y - prevY) > profile.modalLineSpacing * 2.5f) break

            val isMultiSeg = l.segments.size >= 2
            // Allow single-segment wrapped label continuations inside Column 0
            val isWrappedCol0 = !isMultiSeg && rows.isNotEmpty() &&
                l.xEnd <= col0MaxX &&
                (l.y - prevY) <= profile.modalLineSpacing * 1.45f &&
                idx + 1 < lines.size && lines[idx + 1].segments.size >= 2

            if (!isMultiSeg && !isWrappedCol0) break

            // Reject two-column body prose where both halves are long sentences
            val looksLikeTwoColProse = l.segments.size == 2 &&
                l.segments.all { it.rawText.length > 40 }
            if (looksLikeTwoColProse) break

            rows.add(l)
            prevY = l.y
            idx++
        }

        val multiSegCount = rows.count { it.segments.size >= 2 }
        if (multiSegCount < 2) return emptyList()
        return rows
    }

    private fun formatStreamGfmTable(rows: List<GlyphLine>, dehyphenate: Boolean): String? {
        val pageLeft = rows.minOf { it.xStart }
        val pageRight = rows.maxOf { it.xEnd }
        val tracks = discoverColumnTracksByProjection(rows.map { it.segments }, pageLeft, pageRight)
        if (tracks.size !in 2..14) return null

        val logicalRows = mutableListOf<MutableList<String>>()
        for (row in rows) {
            if (row.segments.size == 1 && logicalRows.isNotEmpty()) {
                // Wrapped continuation line belonging to Column 0 of the previous logical row
                val c = findColumnTrackIndex(row.segments.first(), tracks)
                val clean = escapeGfmCell(row.segments.first().rawText)
                logicalRows.last()[c] = joinCellFragments(logicalRows.last()[c], clean, dehyphenate)
            } else {
                val arr = MutableList(tracks.size) { "" }
                for (seg in row.segments) {
                    val c = findColumnTrackIndex(seg, tracks)
                    val clean = escapeGfmCell(seg.rawText)
                    arr[c] = if (arr[c].isEmpty()) clean else "${arr[c]} $clean"
                }
                logicalRows.add(arr)
            }
        }

        if (logicalRows.size < 2) return null

        val sb = StringBuilder()
        sb.append("| ").append(logicalRows.first().joinToString(" | ")).append(" |\n")
        sb.append("| ").append(List(tracks.size) { "---" }.joinToString(" | ")).append(" |\n")
        for (r in 1 until logicalRows.size) {
            sb.append("| ").append(logicalRows[r].joinToString(" | ")).append(" |\n")
        }
        return sb.toString().trimEnd()
    }

    /**
     * Computes exact horizontal column tracks [xStart..xEnd] by building a 1D X-occupancy
     * projection across all segments in the table, finding true whitespace gutters so blank
     * cells are NEVER collapsed or shifted left.
     */
    private fun discoverColumnTracksByProjection(
        rowSegments: List<List<LineSegment>>,
        minX: Float,
        maxX: Float
    ): List<Pair<Float, Float>> {
        val span = (maxX - minX).coerceAtLeast(10f)
        val binCount = (span / 2f).roundToInt().coerceIn(20, 400)
        val binSize = span / binCount
        val occupied = IntArray(binCount)

        for (segs in rowSegments) {
            for (seg in segs) {
                // Trim 1pt from edges so tightly adjacent columns still show a clean gutter
                val b0 = (((seg.xStart + 0.8f) - minX) / binSize).toInt().coerceIn(0, binCount - 1)
                val b1 = (((seg.xEnd - 0.8f) - minX) / binSize).toInt().coerceIn(b0, binCount - 1)
                for (b in b0..b1) occupied[b]++
            }
        }

        val tracks = mutableListOf<Pair<Float, Float>>()
        var inTrack = false
        var startBin = 0
        for (b in 0 until binCount) {
            if (occupied[b] > 0) {
                if (!inTrack) {
                    inTrack = true
                    startBin = b
                }
            } else if (inTrack) {
                tracks.add((minX + startBin * binSize) to (minX + b * binSize))
                inTrack = false
            }
        }
        if (inTrack) {
            tracks.add((minX + startBin * binSize) to maxX)
        }
        return tracks
    }

    private fun findColumnTrackIndex(seg: LineSegment, tracks: List<Pair<Float, Float>>): Int {
        var bestIdx = 0
        var bestOverlap = -1f
        val segCenter = (seg.xStart + seg.xEnd) * 0.5f

        for (i in tracks.indices) {
            val (tStart, tEnd) = tracks[i]
            val overlap = min(seg.xEnd, tEnd) - max(seg.xStart, tStart)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                bestIdx = i
            }
        }
        if (bestOverlap > 0f) return bestIdx

        // Fallback to nearest track center
        var bestDist = Float.MAX_VALUE
        for (i in tracks.indices) {
            val center = (tracks[i].first + tracks[i].second) * 0.5f
            val d = abs(center - segCenter)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }

    // =========================================================================
    // Disciplined Semantic Heading, Code & List Heuristics
    // =========================================================================

    private fun detectHeadingLevelStrict(line: GlyphLine, profile: TypographyProfile): Int {
        val raw = line.rawText.trim()
        if (raw.length < 3 || raw.length > 130) return 0
        if (line.segments.size >= 3) return 0
        if (BULLET_REGEX.matches(raw) || ALPHA_SUBITEM_REGEX.matches(raw)) return 0

        // Reject lowercase-starting fragments (e.g. "of any reduction in output tax lia")
        val firstLetter = raw.firstOrNull { it.isLetter() } ?: return 0
        if (firstLetter.isLowerCase()) return 0

        // Reject pure numbers, currencies, IP addresses, or dates
        if (NUMERIC_OR_CURRENCY_REGEX.matches(raw.replace(" ", ""))) return 0
        if (IP_ADDRESS_REGEX.containsMatchIn(raw)) return 0

        // Reject common form / signature / table-column labels
        val lower = raw.lowercase(Locale.US).trim()
        if (lower in FORM_LABEL_BLACKLIST) return 0

        // Reject key-value form lines like "Date: 11/02/2025" or "GSTIN: 33AAB..."
        if (raw.contains(":") && raw.substringAfter(":").trim().isNotEmpty()) return 0

        // Reject truncated / mid-sentence fragments
        if (raw.endsWith(",") || raw.endsWith(";")) return 0
        val lastWord = raw.substringAfterLast(' ').lowercase(Locale.US)
        if (lastWord in DANGLING_END_WORDS && raw.length > 18) return 0
        if (raw.endsWith(".") && raw.length > 55) return 0

        val ratio = line.avgFontSize / profile.modalBodyFontSize
        return when {
            ratio >= 1.55f -> 1
            ratio >= 1.28f -> 2
            ratio >= 1.14f && (line.isBold || raw.length <= 75) -> 3
            line.isBold && CHAPTER_HEADING_REGEX.matches(raw) && raw.length <= 95 -> 2
            else -> 0
        }
    }

    private fun isCodeLine(line: GlyphLine, profile: TypographyProfile): Boolean {
        if (line.isMonospace) return true
        val raw = line.rawText.trim()
        if (raw.length < 4) return false
        val codeIndicators = listOf("fun ", "class ", "import ", "def ", "SELECT ", "const ", "function ", "return ", "=>", "->", "();", "{}", "#!/")
        return line.xStart > profile.typicalLeftMargin + 14f &&
            codeIndicators.any { raw.contains(it) }
    }

    private fun looksLikeContinuationCode(raw: String): Boolean {
        val t = raw.trim()
        return t == "}" || t == "{" || t == ");" || t.startsWith("//") || t.startsWith("# ") || t.contains(" = ")
    }

    private fun detectCodeLanguage(code: String): String {
        return when {
            code.contains("fun ") && (code.contains("val ") || code.contains("var ")) -> "kotlin"
            code.contains("def ") && code.contains(":") -> "python"
            code.contains("SELECT ") && code.contains("FROM ") -> "sql"
            code.trimStart().startsWith("{") && code.contains("\":") -> "json"
            code.contains("function ") || (code.contains("const ") && code.contains("=>")) -> "javascript"
            code.contains("public class ") || code.contains("System.out.") -> "java"
            code.contains("#!/bin/") || code.contains("curl ") || code.contains("gradle ") -> "bash"
            else -> ""
        }
    }

    private fun cleanHeadingText(raw: String): String {
        return raw.replace(Regex("^#+\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun stripLeadingBullet(styled: String): String {
        return styled.replaceFirst(Regex("^(\\*\\*|\\*)?[•●▪■◦‣▸▹◆◇✓✔☐☑\\-*–—]\\s*(\\*\\*|\\*)?\\s*"), "").trim()
    }

    private fun stripLeadingNumericOrder(styled: String): String {
        return styled.replaceFirst(Regex("^(\\*\\*|\\*)?\\d{1,4}[.)]\\s*(\\*\\*|\\*)?\\s*"), "").trim()
    }

    private fun stripLeadingAlphaOrder(styled: String): String {
        return styled.replaceFirst(Regex("^(\\*\\*|\\*)?(?:\\([a-zA-Z]{1,3}\\)|\\([ivxlcdm]{1,5}\\)|[a-zA-Z]\\))\\s*(\\*\\*|\\*)?\\s*", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun createUniqueAnchor(title: String, usedAnchors: MutableMap<String, Int>): String {
        val base = title.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
            .ifBlank { "section" }
        val count = usedAnchors[base] ?: 0
        usedAnchors[base] = count + 1
        return if (count == 0) base else "$base-$count"
    }
}
