package com.swiffle.app.conversion

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class MarkdownPreset(
    val title: String,
    val subtitle: String
) {
    ACADEMIC_BOOK(
        title = "Academic & Book",
        subtitle = "Headings, TOC, tables & clean prose"
    ),
    LLM_RAG_READY(
        title = "LLM / RAG Ready",
        subtitle = "YAML metadata, page anchors & structured chunks"
    ),
    TECHNICAL_DOCS(
        title = "Technical Docs",
        subtitle = "Code fences, inline code & tabular fidelity"
    ),
    CLEAN_PROSE(
        title = "Clean Prose",
        subtitle = "Stripped chrome, unbroken reading flow"
    )
}

data class PdfToMarkdownConfig(
    val preset: MarkdownPreset = MarkdownPreset.ACADEMIC_BOOK,
    val pageRangeExpr: String = "",
    val stripHeadersAndFooters: Boolean = true,
    val detectTablesAsGfm: Boolean = true,
    val reorderMultiColumn: Boolean = true,
    val preserveRichTypography: Boolean = true,
    val dehyphenateAndUnwrap: Boolean = true,
    val includeYamlFrontmatter: Boolean = true,
    val generateTableOfContents: Boolean = true,
    val insertPageMarkers: Boolean = false,
    val ocrFallbackForScannedPages: Boolean = true
) {
    companion object {
        fun forPreset(preset: MarkdownPreset, currentRange: String = ""): PdfToMarkdownConfig {
            return when (preset) {
                MarkdownPreset.ACADEMIC_BOOK -> PdfToMarkdownConfig(
                    preset = preset,
                    pageRangeExpr = currentRange,
                    stripHeadersAndFooters = true,
                    detectTablesAsGfm = true,
                    reorderMultiColumn = true,
                    preserveRichTypography = true,
                    dehyphenateAndUnwrap = true,
                    includeYamlFrontmatter = true,
                    generateTableOfContents = true,
                    insertPageMarkers = false,
                    ocrFallbackForScannedPages = true
                )
                MarkdownPreset.LLM_RAG_READY -> PdfToMarkdownConfig(
                    preset = preset,
                    pageRangeExpr = currentRange,
                    stripHeadersAndFooters = true,
                    detectTablesAsGfm = true,
                    reorderMultiColumn = true,
                    preserveRichTypography = true,
                    dehyphenateAndUnwrap = true,
                    includeYamlFrontmatter = true,
                    generateTableOfContents = true,
                    insertPageMarkers = true,
                    ocrFallbackForScannedPages = true
                )
                MarkdownPreset.TECHNICAL_DOCS -> PdfToMarkdownConfig(
                    preset = preset,
                    pageRangeExpr = currentRange,
                    stripHeadersAndFooters = true,
                    detectTablesAsGfm = true,
                    reorderMultiColumn = false,
                    preserveRichTypography = true,
                    dehyphenateAndUnwrap = false,
                    includeYamlFrontmatter = true,
                    generateTableOfContents = true,
                    insertPageMarkers = false,
                    ocrFallbackForScannedPages = true
                )
                MarkdownPreset.CLEAN_PROSE -> PdfToMarkdownConfig(
                    preset = preset,
                    pageRangeExpr = currentRange,
                    stripHeadersAndFooters = true,
                    detectTablesAsGfm = true,
                    reorderMultiColumn = true,
                    preserveRichTypography = true,
                    dehyphenateAndUnwrap = true,
                    includeYamlFrontmatter = false,
                    generateTableOfContents = false,
                    insertPageMarkers = false,
                    ocrFallbackForScannedPages = true
                )
            }
        }
    }
}

data class PdfDocumentInspectInfo(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val title: String?,
    val author: String?,
    val subject: String?,
    val outlineCount: Int
)

data class MarkdownTocEntry(
    val level: Int,
    val title: String,
    val anchor: String,
    val pageNumber: Int
)

data class PdfToMarkdownProgress(
    val currentPage: Int,
    val totalSelectedPages: Int,
    val phase: String,
    val pagesPerSecond: Float,
    val etaSeconds: Int,
    val wordsExtracted: Int,
    val headingsFound: Int,
    val tablesFound: Int,
    val codeBlocksFound: Int,
    val ocrPagesProcessed: Int,
    val headersStripped: Int
)

data class PdfToMarkdownResult(
    val outputFile: File,
    val fileName: String,
    val fileSizeBytes: Long,
    val totalPagesConverted: Int,
    val totalDocumentPages: Int,
    val wordCount: Int,
    val charCount: Int,
    val headingCount: Int,
    val tableCount: Int,
    val codeBlockCount: Int,
    val ocrPageCount: Int,
    val headersFootersStripped: Int,
    val elapsedMs: Long,
    val previewMarkdown: String,
    val isPreviewTruncated: Boolean,
    val tocEntries: List<MarkdownTocEntry>
)

// ============================================================================
// Internal Layout Geometry Data Structures
// ============================================================================

private data class LineSegment(
    val xStart: Float,
    val xEnd: Float,
    val rawText: String,
    val styledText: String,
    val avgFontSize: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isMonospace: Boolean
)

private data class GlyphLine(
    val y: Float,
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

private data class TypographyProfile(
    val modalBodyFontSize: Float,
    val modalLineSpacing: Float,
    val typicalLeftMargin: Float,
    val repeatingHeaders: Set<String>,
    val repeatingFooters: Set<String>
)

/**
 * Custom PDFBox text stripper that captures exact per-glyph coordinates, font sizes,
 * bold/italic/monospace styles, and horizontal column segments for high-fidelity
 * layout-aware Markdown reconstruction.
 */
private class GlyphLayoutStripper(
    private val preserveStyles: Boolean,
    private var currentPageWidth: Float = 612f,
    private var currentPageHeight: Float = 792f
) : PDFTextStripper() {

    val extractedLines = mutableListOf<GlyphLine>()

    init {
        sortByPosition = true
        suppressDuplicateOverlappingText = true
    }

    fun setPageDimensions(width: Float, height: Float) {
        currentPageWidth = if (width > 10f) width else 612f
        currentPageHeight = if (height > 10f) height else 792f
    }

    fun clearLines() {
        extractedLines.clear()
    }

    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
        if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return

        // Group textPositions into visual sub-lines by baseline Y (handles occasional merged rows)
        val sorted = textPositions.filter { !it.unicode.isNullOrEmpty() }
        if (sorted.isEmpty()) return

        val yBuckets = mutableListOf<MutableList<TextPosition>>()
        for (tp in sorted) {
            val y = tp.yDirAdj
            val targetBucket = yBuckets.firstOrNull { bucket ->
                val refY = bucket.first().yDirAdj
                val tol = max(2.5f, bucket.first().fontSizeInPt * 0.42f)
                abs(refY - y) <= tol
            }
            if (targetBucket != null) {
                targetBucket.add(tp)
            } else {
                yBuckets.add(mutableListOf(tp))
            }
        }

        for (bucket in yBuckets) {
            val ordered = bucket.sortedBy { it.xDirAdj }
            val line = buildGlyphLine(ordered)
            if (line != null && line.rawText.isNotBlank()) {
                extractedLines.add(line)
            }
        }
    }

    private fun buildGlyphLine(positions: List<TextPosition>): GlyphLine? {
        if (positions.isEmpty()) return null

        val y = positions.map { it.yDirAdj }.average().toFloat()
        val xStart = positions.minOf { it.xDirAdj }
        val xEnd = positions.maxOf { it.xDirAdj + max(it.widthDirAdj, 0f) }

        var fontSizeSum = 0f
        var maxFontSize = 0f
        var nonSpaceChars = 0
        var boldChars = 0
        var italicChars = 0
        var monoChars = 0

        val rawBuilder = StringBuilder()
        val segments = mutableListOf<LineSegment>()

        // Split into horizontal segments separated by wide visual gaps (tables / columns)
        val segStartIndices = mutableListOf(0)
        for (i in 1 until positions.size) {
            val prev = positions[i - 1]
            val curr = positions[i]
            val prevEnd = prev.xDirAdj + max(prev.widthDirAdj, 0f)
            val gap = curr.xDirAdj - prevEnd
            val spaceWidth = max(prev.widthOfSpace, prev.fontSizeInPt * 0.28f).coerceAtLeast(2.5f)
            if (gap > spaceWidth * 2.4f) {
                segStartIndices.add(i)
            }
        }
        segStartIndices.add(positions.size)

        for (sIdx in 0 until segStartIndices.size - 1) {
            val from = segStartIndices[sIdx]
            val to = segStartIndices[sIdx + 1]
            val slice = positions.subList(from, to)
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
                val str = runBuffer.toString()
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

            var prevPos: TextPosition? = null
            for (tp in slice) {
                val u = tp.unicode ?: continue
                val fontName = tp.font?.name?.lowercase(Locale.US).orEmpty()
                val pt = resolveFontSize(tp)
                val isBoldChar = fontName.contains("bold") || fontName.contains("black") ||
                    fontName.contains("heavy") || fontName.contains("demi") || fontName.contains("semibold")
                val isItalicChar = fontName.contains("italic") || fontName.contains("oblique") ||
                    fontName.contains("slant")
                val isMonoChar = fontName.contains("mono") || fontName.contains("courier") ||
                    fontName.contains("consola") || fontName.contains("menlo") ||
                    fontName.contains("fira") || fontName.contains("jetbrains") ||
                    fontName.contains("sourcecode") || fontName.contains("inconsolata") ||
                    fontName.contains("code")

                // Insert space if there is a clear inter-word horizontal gap
                if (prevPos != null) {
                    val prevEnd = prevPos.xDirAdj + max(prevPos.widthDirAdj, 0f)
                    val gap = tp.xDirAdj - prevEnd
                    val wordGap = max(prevPos.widthOfSpace * 0.42f, pt * 0.16f).coerceAtLeast(1.2f)
                    if (gap > wordGap && !u.startsWith(" ") && !segRaw.endsWith(" ")) {
                        segRaw.append(' ')
                        runBuffer.append(' ')
                    }
                }

                if (!u.isBlank()) {
                    if (runBuffer.isNotEmpty() && (isBoldChar != runBold || isItalicChar != runItalic || isMonoChar != runMono)) {
                        flushRun()
                    }
                    runBold = isBoldChar
                    runItalic = isItalicChar
                    runMono = isMonoChar

                    nonSpaceChars += u.length
                    fontSizeSum += pt * u.length
                    if (pt > maxFontSize) maxFontSize = pt
                    if (isBoldChar) boldChars += u.length
                    if (isItalicChar) italicChars += u.length
                    if (isMonoChar) monoChars += u.length

                    segNonSpace += u.length
                    segFontSum += pt * u.length
                    if (isBoldChar) segBoldChars += u.length
                    if (isItalicChar) segItalicChars += u.length
                    if (isMonoChar) segMonoChars += u.length
                }

                segRaw.append(u)
                runBuffer.append(u)
                prevPos = tp
            }
            flushRun()

            val cleanedSegRaw = segRaw.toString().replace(Regex("\\s+"), " ").trim()
            val cleanedSegStyled = segStyled.toString().replace(Regex(" {2,}"), " ").trim()
            if (cleanedSegRaw.isNotEmpty()) {
                val segAvgPt = if (segNonSpace > 0) segFontSum / segNonSpace else 11f
                segments.add(
                    LineSegment(
                        xStart = slice.first().xDirAdj,
                        xEnd = slice.last().xDirAdj + max(slice.last().widthDirAdj, 0f),
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

        val avgFontSize = if (nonSpaceChars > 0) fontSizeSum / nonSpaceChars else 11f
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
            xStart = xStart,
            xEnd = xEnd,
            pageWidth = currentPageWidth,
            pageHeight = currentPageHeight,
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

    private fun resolveFontSize(tp: TextPosition): Float {
        val pt = tp.fontSizeInPt
        if (pt in 4f..144f) return pt
        val h = tp.heightDir
        if (h in 4f..144f) return h * 1.2f
        return 11f
    }
}

/**
 * Industrial-grade, Unlimited-Page Streaming PDF-to-Markdown Conversion Engine.
 *
 * Engineered to handle 500+ / 1,000+ page PDFs on Android with constant O(1) memory footprint:
 * - Opens PDFs with disk-backed `MemoryUsageSetting.setupTempFileOnly()` to prevent OOM
 * - Streams Markdown directly to disk page-by-page via buffered writers
 * - Profiles typography statistically across sampled pages for accurate `#`–`####` headings
 * - Automatically strips repeating running headers, footers & page numbers
 * - Detects and reorders 2-column academic/magazine layouts
 * - Reconstructs coordinate-aligned tabular regions into GitHub Flavored Markdown (GFM) tables
 * - Reconstructs fenced code blocks with language detection and inline code/bold/italic spans
 * - Performs smart de-hyphenation, paragraph unwrapping, nested list & blockquote formatting
 * - Falls back automatically to on-device ML Kit OCR for scanned image-only pages
 * - Emits YAML frontmatter + auto-linked Table of Contents (TOC)
 */
object PdfToMarkdownEngine {

    private const val PREVIEW_CHAR_LIMIT = 45_000
    private val PAGE_NUMBER_REGEX = Regex(
        "^(?:page\\s+)?\\d{1,5}(?:\\s*(?:/|of)\\s*\\d{1,5})?$|^[-–—]\\s*\\d{1,5}\\s*[-–—]$|^[ivxlcdm]{1,7}$",
        RegexOption.IGNORE_CASE
    )
    private val BULLET_REGEX = Regex("^([•●▪■◦‣▸▹◆◇✓✔☐☑\\-*–—])\\s+(.+)$")
    private val ORDERED_LIST_REGEX = Regex("^(\\d{1,4}[.)]|\\([a-zA-Z0-9]{1,3}\\)|[a-zA-Z]\\))\\s+(.+)$")
    private val CHAPTER_HEADING_REGEX = Regex(
        "^(?:chapter|section|part|appendix|unit|module)\\s+[\\dIVXLC]+\\b.*$|^\\d+(?:\\.\\d+){0,3}\\s+[A-Z].*$",
        RegexOption.IGNORE_CASE
    )

    fun parsePageRange(expr: String, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        val trimmed = expr.trim()
        if (trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true)) {
            return (1..totalPages).toList()
        }
        val selected = sortedSetOf<Int>()
        for (token in trimmed.split(",", ";")) {
            val part = token.trim()
            if (part.isEmpty()) continue
            if (part.contains("-")) {
                val bounds = part.split("-", limit = 2)
                val start = bounds[0].trim().toIntOrNull()?.coerceIn(1, totalPages) ?: continue
                val end = bounds[1].trim().toIntOrNull()?.coerceIn(1, totalPages) ?: totalPages
                val lo = min(start, end)
                val hi = max(start, end)
                for (p in lo..hi) selected.add(p)
            } else {
                val single = part.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    selected.add(single)
                }
            }
        }
        return if (selected.isEmpty()) (1..totalPages).toList() else selected.toList()
    }

    suspend fun inspectPdf(context: Context, uri: Uri): Result<PdfDocumentInspectInfo> = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val fileName = queryDisplayName(context, uri)
            val fileSize = queryFileSize(context, uri)
            val tempCopy = copyUriToTempPdf(context, uri)
            try {
                PDDocument.load(tempCopy, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val info = doc.documentInformation
                    val outlineCount = countOutlineItems(doc.documentCatalog?.documentOutline?.firstChild)
                    Result.success(
                        PdfDocumentInspectInfo(
                            uri = uri,
                            fileName = fileName,
                            fileSizeBytes = if (fileSize > 0L) fileSize else tempCopy.length(),
                            pageCount = doc.numberOfPages,
                            title = info?.title?.trim()?.takeIf { it.isNotBlank() },
                            author = info?.author?.trim()?.takeIf { it.isNotBlank() },
                            subject = info?.subject?.trim()?.takeIf { it.isNotBlank() },
                            outlineCount = outlineCount
                        )
                    )
                }
            } finally {
                tempCopy.delete()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun convertPdfToMarkdown(
        context: Context,
        uri: Uri,
        config: PdfToMarkdownConfig,
        onProgress: (PdfToMarkdownProgress) -> Unit
    ): Result<PdfToMarkdownResult> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        var tempPdfFile: File? = null
        var tempBodyFile: File? = null
        var pdfRenderer: PdfRenderer? = null
        var rendererPfd: ParcelFileDescriptor? = null

        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val sourceFileName = queryDisplayName(context, uri)
            val baseTitle = sourceFileName.substringBeforeLast(".").ifBlank { "Document" }
            val safeFileStem = baseTitle.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim().ifBlank { "Document" }

            onProgress(
                PdfToMarkdownProgress(
                    currentPage = 0,
                    totalSelectedPages = 1,
                    phase = "Preparing disk-backed PDF stream…",
                    pagesPerSecond = 0f,
                    etaSeconds = 0,
                    wordsExtracted = 0,
                    headingsFound = 0,
                    tablesFound = 0,
                    codeBlocksFound = 0,
                    ocrPagesProcessed = 0,
                    headersStripped = 0
                )
            )

            tempPdfFile = copyUriToTempPdf(context, uri)
            val outputDir = File(context.cacheDir, "markdown_exports").apply { mkdirs() }
            tempBodyFile = File(outputDir, ".tmp_body_${System.currentTimeMillis()}.md")
            val finalMdName = "${safeFileStem}.md"
            val finalOutputFile = File(outputDir, "${safeFileStem}_${System.currentTimeMillis()}.md")

            PDDocument.load(tempPdfFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val totalDocPages = doc.numberOfPages
                if (totalDocPages <= 0) {
                    return@withContext Result.failure(IllegalStateException("This PDF contains no pages."))
                }

                val selectedPages = parsePageRange(config.pageRangeExpr, totalDocPages)
                val totalSelected = selectedPages.size
                val docInfo = doc.documentInformation
                val docTitle = docInfo?.title?.trim()?.takeIf { it.isNotBlank() } ?: baseTitle
                val docAuthor = docInfo?.author?.trim()?.takeIf { it.isNotBlank() }
                val docSubject = docInfo?.subject?.trim()?.takeIf { it.isNotBlank() }

                // ── PASS 1: Fast Statistical Typography & Header/Footer Profiling ──
                onProgress(
                    PdfToMarkdownProgress(
                        currentPage = 0,
                        totalSelectedPages = totalSelected,
                        phase = "Profiling typography, margins & repeating headers…",
                        pagesPerSecond = 0f,
                        etaSeconds = 0,
                        wordsExtracted = 0,
                        headingsFound = 0,
                        tablesFound = 0,
                        codeBlocksFound = 0,
                        ocrPagesProcessed = 0,
                        headersStripped = 0
                    )
                )

                val profile = buildTypographyProfile(doc, selectedPages)
                val stripper = GlyphLayoutStripper(preserveStyles = config.preserveRichTypography)
                val ocrRecognizer = if (config.ocrFallbackForScannedPages) {
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                } else null

                val tocEntries = mutableListOf<MarkdownTocEntry>()
                val usedAnchors = mutableMapOf<String, Int>()
                val previewBuffer = StringBuilder()
                var previewTruncated = false

                var totalWords = 0
                var totalChars = 0
                var totalHeadings = 0
                var totalTables = 0
                var totalCodeBlocks = 0
                var totalOcrPages = 0
                var totalHeadersStripped = 0
                val conversionLoopStartMs = System.currentTimeMillis()

                // ── PASS 2: Memory-Safe Page-by-Page Streaming to Disk ──
                BufferedWriter(OutputStreamWriter(FileOutputStream(tempBodyFile), Charsets.UTF_8), 65_536).use { writer ->
                    for ((index, pageNum) in selectedPages.withIndex()) {
                        coroutineContext.ensureActive()

                        val pdPage = doc.getPage(pageNum - 1)
                        val mediaBox = pdPage.cropBox ?: pdPage.mediaBox
                        val pageW = mediaBox?.width ?: 612f
                        val pageH = mediaBox?.height ?: 792f

                        stripper.clearLines()
                        stripper.setPageDimensions(pageW, pageH)
                        stripper.startPage = pageNum
                        stripper.endPage = pageNum
                        stripper.getText(doc)

                        var pageLines = stripper.extractedLines.toList()
                        val rawCharCount = pageLines.sumOf { it.rawText.length }

                        // Automatic ML Kit OCR Fallback for scanned / image-only pages
                        if (rawCharCount < 12 && config.ocrFallbackForScannedPages && ocrRecognizer != null) {
                            onProgress(
                                buildProgress(
                                    done = index,
                                    total = totalSelected,
                                    phase = "Running on-device OCR on scanned page $pageNum…",
                                    loopStartMs = conversionLoopStartMs,
                                    words = totalWords,
                                    headings = totalHeadings,
                                    tables = totalTables,
                                    codeBlocks = totalCodeBlocks,
                                    ocrPages = totalOcrPages,
                                    headersStripped = totalHeadersStripped
                                )
                            )
                            if (pdfRenderer == null) {
                                try {
                                    rendererPfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                                    pdfRenderer = PdfRenderer(rendererPfd)
                                } catch (_: Exception) {
                                }
                            }
                            val ocrLines = pdfRenderer?.let { renderer ->
                                extractPageViaOcr(renderer, pageNum - 1, ocrRecognizer)
                            }.orEmpty()
                            if (ocrLines.isNotEmpty()) {
                                pageLines = ocrLines
                                totalOcrPages++
                            }
                        }

                        // Filter running headers, footers & standalone page numbers
                        val filteredResult = if (config.stripHeadersAndFooters) {
                            stripRunningChrome(pageLines, profile)
                        } else {
                            pageLines to 0
                        }
                        pageLines = filteredResult.first
                        totalHeadersStripped += filteredResult.second

                        // Multi-column layout reading-order reordering
                        if (config.reorderMultiColumn && pageLines.size >= 6) {
                            pageLines = reorderMultiColumnLines(pageLines)
                        } else {
                            pageLines = pageLines.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
                        }

                        // Convert structured page lines into clean Markdown
                        val pageConv = renderPageToMarkdown(
                            lines = pageLines,
                            pageNum = pageNum,
                            profile = profile,
                            config = config,
                            usedAnchors = usedAnchors
                        )

                        tocEntries.addAll(pageConv.headings)
                        totalHeadings += pageConv.headings.size
                        totalTables += pageConv.tableCount
                        totalCodeBlocks += pageConv.codeBlockCount

                        val pageMarkdown = buildString {
                            if (config.insertPageMarkers) {
                                if (index > 0) append("\n---\n\n")
                                append("<!-- Page $pageNum of $totalDocPages -->\n\n")
                            } else if (index > 0 && pageConv.markdown.isNotBlank()) {
                                append("\n\n")
                            }
                            append(pageConv.markdown)
                        }

                        if (pageMarkdown.isNotEmpty()) {
                            writer.write(pageMarkdown)
                            totalChars += pageMarkdown.length
                            totalWords += countWords(pageConv.markdown)

                            if (!previewTruncated) {
                                if (previewBuffer.length + pageMarkdown.length <= PREVIEW_CHAR_LIMIT) {
                                    previewBuffer.append(pageMarkdown)
                                } else {
                                    val remain = (PREVIEW_CHAR_LIMIT - previewBuffer.length).coerceAtLeast(0)
                                    if (remain > 0) {
                                        previewBuffer.append(pageMarkdown.substring(0, remain))
                                    }
                                    previewTruncated = true
                                }
                            }
                        }

                        val pagesDone = index + 1
                        if (pagesDone == totalSelected || pagesDone <= 10 || pagesDone % 2 == 0) {
                            onProgress(
                                buildProgress(
                                    done = pagesDone,
                                    total = totalSelected,
                                    phase = "Converting page $pageNum of $totalDocPages…",
                                    loopStartMs = conversionLoopStartMs,
                                    words = totalWords,
                                    headings = totalHeadings,
                                    tables = totalTables,
                                    codeBlocks = totalCodeBlocks,
                                    ocrPages = totalOcrPages,
                                    headersStripped = totalHeadersStripped
                                )
                            )
                        }
                    }
                    writer.flush()
                }

                // ── PASS 3: Assemble Frontmatter + TOC + Streamed Body to Final .md ──
                onProgress(
                    buildProgress(
                        done = totalSelected,
                        total = totalSelected,
                        phase = "Assembling frontmatter, TOC & final .md file…",
                        loopStartMs = conversionLoopStartMs,
                        words = totalWords,
                        headings = totalHeadings,
                        tables = totalTables,
                        codeBlocks = totalCodeBlocks,
                        ocrPages = totalOcrPages,
                        headersStripped = totalHeadersStripped
                    )
                )

                val headerPreamble = buildString {
                    if (config.includeYamlFrontmatter) {
                        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                        append("---\n")
                        append("title: \"${escapeYaml(docTitle)}\"\n")
                        if (!docAuthor.isNullOrBlank()) append("author: \"${escapeYaml(docAuthor)}\"\n")
                        if (!docSubject.isNullOrBlank()) append("subject: \"${escapeYaml(docSubject)}\"\n")
                        append("source_file: \"${escapeYaml(sourceFileName)}\"\n")
                        append("pages_converted: $totalSelected\n")
                        append("total_pages: $totalDocPages\n")
                        append("word_count: $totalWords\n")
                        append("headings: $totalHeadings\n")
                        append("tables: $totalTables\n")
                        append("converted_at: \"$isoDate\"\n")
                        append("converter: \"Swiffle PDF-to-Markdown Engine\"\n")
                        append("---\n\n")
                    }

                    // If document didn't start with an H1 heading, add document title H1
                    if (tocEntries.none { it.level == 1 }) {
                        append("# $docTitle\n\n")
                    }

                    if (config.generateTableOfContents && tocEntries.size >= 2) {
                        append("## Table of Contents\n\n")
                        val cappedToc = tocEntries.take(250)
                        for (entry in cappedToc) {
                            val indent = "  ".repeat((entry.level - 1).coerceIn(0, 3))
                            append("$indent- [${entry.title}](#${entry.anchor})\n")
                        }
                        if (tocEntries.size > 250) {
                            append("- *…and ${tocEntries.size - 250} more sections*\n")
                        }
                        append("\n---\n\n")
                    }
                }

                // Stream preamble + disk-backed body into final file (zero heap blowup)
                BufferedOutputStream(FileOutputStream(finalOutputFile), 65_536).use { out ->
                    out.write(headerPreamble.toByteArray(Charsets.UTF_8))
                    BufferedInputStream(FileInputStream(tempBodyFile), 65_536).use { bodyIn ->
                        val buffer = ByteArray(65_536)
                        while (true) {
                            val read = bodyIn.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                    out.flush()
                }

                val combinedPreview = buildString {
                    append(headerPreamble)
                    append(previewBuffer)
                }.let { raw ->
                    if (raw.length > PREVIEW_CHAR_LIMIT) {
                        previewTruncated = true
                        raw.substring(0, PREVIEW_CHAR_LIMIT)
                    } else raw
                }

                val elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                Result.success(
                    PdfToMarkdownResult(
                        outputFile = finalOutputFile,
                        fileName = finalMdName,
                        fileSizeBytes = finalOutputFile.length(),
                        totalPagesConverted = totalSelected,
                        totalDocumentPages = totalDocPages,
                        wordCount = totalWords,
                        charCount = totalChars + headerPreamble.length,
                        headingCount = totalHeadings,
                        tableCount = totalTables,
                        codeBlockCount = totalCodeBlocks,
                        ocrPageCount = totalOcrPages,
                        headersFootersStripped = totalHeadersStripped,
                        elapsedMs = elapsedMs,
                        previewMarkdown = combinedPreview,
                        isPreviewTruncated = previewTruncated,
                        tocEntries = tocEntries
                    )
                )
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { rendererPfd?.close() } catch (_: Exception) {}
            try { tempBodyFile?.delete() } catch (_: Exception) {}
            try { tempPdfFile?.delete() } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // Pass 1: Statistical Typography & Repeating Header/Footer Profiler
    // =========================================================================

    private fun buildTypographyProfile(doc: PDDocument, selectedPages: List<Int>): TypographyProfile {
        val sampleCount = min(selectedPages.size, 24)
        val sampledPages = if (selectedPages.size <= sampleCount) {
            selectedPages
        } else {
            (0 until sampleCount).map { i ->
                val idx = ((i.toFloat() / (sampleCount - 1)) * (selectedPages.size - 1)).roundToInt()
                selectedPages[idx]
            }.distinct()
        }

        val fontSizeHistogram = mutableMapOf<Int, Int>() // rounded 0.5pt buckets -> char weight
        val spacingHistogram = mutableMapOf<Int, Int>()
        val leftMargins = mutableListOf<Float>()
        val topBandCounts = mutableMapOf<String, Int>()
        val bottomBandCounts = mutableMapOf<String, Int>()

        val profilerStripper = GlyphLayoutStripper(preserveStyles = false)

        for (pageNum in sampledPages) {
            val pdPage = doc.getPage(pageNum - 1)
            val box = pdPage.cropBox ?: pdPage.mediaBox
            val pageW = box?.width ?: 612f
            val pageH = box?.height ?: 792f

            profilerStripper.clearLines()
            profilerStripper.setPageDimensions(pageW, pageH)
            profilerStripper.startPage = pageNum
            profilerStripper.endPage = pageNum
            try {
                profilerStripper.getText(doc)
            } catch (_: Exception) {
                continue
            }

            val lines = profilerStripper.extractedLines.sortedBy { it.y }
            if (lines.isEmpty()) continue

            val seenTopThisPage = mutableSetOf<String>()
            val seenBottomThisPage = mutableSetOf<String>()

            for (i in lines.indices) {
                val line = lines[i]
                val ptBucket = (line.avgFontSize * 2f).roundToInt()
                fontSizeHistogram[ptBucket] = (fontSizeHistogram[ptBucket] ?: 0) + line.rawText.length

                if (line.rawText.length >= 25) {
                    leftMargins.add(line.xStart)
                }

                if (i > 0) {
                    val dy = line.y - lines[i - 1].y
                    if (dy in 6f..40f) {
                        val dyBucket = dy.roundToInt()
                        spacingHistogram[dyBucket] = (spacingHistogram[dyBucket] ?: 0) + 1
                    }
                }

                val norm = normalizeEdgeKey(line.rawText)
                if (norm.isNotEmpty()) {
                    if (line.y <= pageH * 0.11f && seenTopThisPage.add(norm)) {
                        topBandCounts[norm] = (topBandCounts[norm] ?: 0) + 1
                    } else if (line.y >= pageH * 0.89f && seenBottomThisPage.add(norm)) {
                        bottomBandCounts[norm] = (bottomBandCounts[norm] ?: 0) + 1
                    }
                }
            }
        }

        val modalBodyPt = (fontSizeHistogram.maxByOrNull { it.value }?.key?.div(2f) ?: 11f).coerceIn(7f, 18f)
        val modalSpacing = (spacingHistogram.maxByOrNull { it.value }?.key?.toFloat() ?: (modalBodyPt * 1.35f))
            .coerceIn(modalBodyPt * 1.05f, modalBodyPt * 2.4f)
        val typicalLeft = if (leftMargins.isNotEmpty()) {
            val sortedMargins = leftMargins.sorted()
            sortedMargins[(sortedMargins.size * 0.25f).toInt().coerceIn(0, sortedMargins.lastIndex)]
        } else 54f

        val minRepeat = if (sampledPages.size >= 4) max(2, (sampledPages.size * 0.35f).roundToInt()) else Int.MAX_VALUE
        val repeatingHeaders = topBandCounts.filterValues { it >= minRepeat }.keys
        val repeatingFooters = bottomBandCounts.filterValues { it >= minRepeat }.keys

        return TypographyProfile(
            modalBodyFontSize = modalBodyPt,
            modalLineSpacing = modalSpacing,
            typicalLeftMargin = typicalLeft,
            repeatingHeaders = repeatingHeaders,
            repeatingFooters = repeatingFooters
        )
    }

    private fun normalizeEdgeKey(text: String): String {
        val cleaned = text.trim()
            .replace(Regex("\\d+"), "#")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.US)
        return if (cleaned.length in 1..90) cleaned else ""
    }

    private fun stripRunningChrome(
        lines: List<GlyphLine>,
        profile: TypographyProfile
    ): Pair<List<GlyphLine>, Int> {
        if (lines.isEmpty()) return lines to 0
        val kept = mutableListOf<GlyphLine>()
        var stripped = 0

        for (line in lines) {
            val inTopBand = line.y <= line.pageHeight * 0.11f
            val inBottomBand = line.y >= line.pageHeight * 0.89f
            val raw = line.rawText.trim()
            val norm = normalizeEdgeKey(raw)

            val isPageNum = (inTopBand || inBottomBand) && PAGE_NUMBER_REGEX.matches(raw)
            val isRepeatingHeader = inTopBand && norm in profile.repeatingHeaders
            val isRepeatingFooter = inBottomBand && norm in profile.repeatingFooters

            if (isPageNum || isRepeatingHeader || isRepeatingFooter) {
                stripped++
            } else {
                kept.add(line)
            }
        }
        return kept to stripped
    }

    // =========================================================================
    // Multi-Column Reading-Order Reconstruction
    // =========================================================================

    private fun reorderMultiColumnLines(lines: List<GlyphLine>): List<GlyphLine> {
        if (lines.size < 6) return lines.sortedWith(compareBy<GlyphLine> { it.y }.thenBy { it.xStart })
        val pageWidth = lines.first().pageWidth.coerceAtLeast(200f)
        val midX = pageWidth * 0.50f
        val leftZoneEnd = pageWidth * 0.48f
        val rightZoneStart = pageWidth * 0.52f

        val leftLines = mutableListOf<GlyphLine>()
        val rightLines = mutableListOf<GlyphLine>()
        val fullWidthLines = mutableListOf<GlyphLine>()

        for (line in lines) {
            // Skip lines that look like multi-column table rows (segments spanning both sides)
            if (line.segments.size >= 3 && line.xStart < leftZoneEnd && line.xEnd > rightZoneStart) {
                fullWidthLines.add(line)
            } else if (line.xEnd <= rightZoneStart && line.xStart < midX) {
                leftLines.add(line)
            } else if (line.xStart >= leftZoneEnd) {
                rightLines.add(line)
            } else {
                fullWidthLines.add(line)
            }
        }

        // Only treat page as 2-column if both left & right columns carry substantial prose
        val isTwoColumn = leftLines.size >= 4 && rightLines.size >= 4 &&
            (leftLines.size + rightLines.size) >= lines.size * 0.55f

        if (!isTwoColumn) {
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
    // Structured Block & Markdown Renderer (Headings, Tables, Code, Lists, Prose)
    // =========================================================================

    private data class PageMarkdownOutput(
        val markdown: String,
        val headings: List<MarkdownTocEntry>,
        val tableCount: Int,
        val codeBlockCount: Int
    )

    private fun renderPageToMarkdown(
        lines: List<GlyphLine>,
        pageNum: Int,
        profile: TypographyProfile,
        config: PdfToMarkdownConfig,
        usedAnchors: MutableMap<String, Int>
    ): PageMarkdownOutput {
        if (lines.isEmpty()) {
            return PageMarkdownOutput("", emptyList(), 0, 0)
        }

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

            // 1. Coordinate-Aligned GFM Table Detection
            if (config.detectTablesAsGfm && line.segments.size >= 2) {
                val tableRows = collectTableBlock(lines, i, profile)
                if (tableRows.size >= 2) {
                    flushParagraph()
                    out.append(formatGfmTable(tableRows))
                    out.append("\n\n")
                    tableCount++
                    i += tableRows.size
                    continue
                }
            }

            // 2. Monospace / Fenced Code Block Detection
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
                    out.append("```$lang\n")
                    out.append(codeText)
                    out.append("\n```\n\n")
                    codeBlockCount++
                    i = j
                    continue
                }
            }

            // 3. Semantic Heading Detection (`#` to `####`)
            val headingLevel = detectHeadingLevel(line, profile)
            if (headingLevel > 0) {
                flushParagraph()
                val cleanTitle = cleanHeadingText(raw)
                val anchor = createUniqueAnchor(cleanTitle, usedAnchors)
                headings.add(MarkdownTocEntry(headingLevel, cleanTitle, anchor, pageNum))
                out.append("#".repeat(headingLevel)).append(" ").append(cleanTitle).append("\n\n")
                i++
                continue
            }

            // 4. Bullet & Ordered Lists (with indentation nesting)
            val bulletMatch = BULLET_REGEX.matchEntire(raw)
            val orderedMatch = ORDERED_LIST_REGEX.matchEntire(raw)
            if (bulletMatch != null || orderedMatch != null) {
                flushParagraph()
                val indentDepth = ((line.xStart - profile.typicalLeftMargin) / 18f)
                    .toInt()
                    .coerceIn(0, 3)
                val indentStr = "  ".repeat(indentDepth)
                if (bulletMatch != null) {
                    val itemBody = stripLeadingBullet(line.styledText)
                    out.append("$indentStr- $itemBody\n")
                } else {
                    val marker = orderedMatch!!.groupValues[1]
                    val normalizedNum = marker.trimEnd(')', '.').toIntOrNull()
                    val itemBody = stripLeadingOrderedMarker(line.styledText)
                    if (normalizedNum != null) {
                        out.append("$indentStr$normalizedNum. $itemBody\n")
                    } else {
                        out.append("$indentStr- $itemBody\n")
                    }
                }
                i++
                continue
            }

            // 5. Blockquote Detection (significantly indented non-list prose)
            val isIndentedQuote = line.xStart > profile.typicalLeftMargin + 26f &&
                line.xEnd < line.pageWidth - 36f &&
                raw.length >= 24 &&
                line.isItalic
            if (isIndentedQuote) {
                flushParagraph()
                out.append("> ${line.styledText}\n\n")
                i++
                continue
            }

            // 6. Prose Paragraph with Smart De-Hyphenation & Line Unwrapping
            val textToAppend = if (config.preserveRichTypography) line.styledText else raw
            val hasParagraphGap = lastParagraphY > 0f && (line.y - lastParagraphY) > profile.modalLineSpacing * 1.48f
            val previousEndedSentence = paragraphBuffer.isNotEmpty() &&
                (paragraphBuffer.endsWith(".") || paragraphBuffer.endsWith("!") ||
                    paragraphBuffer.endsWith("?") || paragraphBuffer.endsWith(":")) &&
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

        val cleanedMarkdown = out.toString()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        return PageMarkdownOutput(
            markdown = cleanedMarkdown,
            headings = headings,
            tableCount = tableCount,
            codeBlockCount = codeBlockCount
        )
    }

    private fun collectTableBlock(
        lines: List<GlyphLine>,
        startIdx: Int,
        profile: TypographyProfile
    ): List<GlyphLine> {
        val rows = mutableListOf<GlyphLine>()
        var idx = startIdx
        var prevY = lines[startIdx].y

        while (idx < lines.size) {
            val l = lines[idx]
            if (l.segments.size < 2) break
            if (idx > startIdx && abs(l.y - prevY) > profile.modalLineSpacing * 2.6f) break
            // Reject two-column body prose that has very long paragraphs in both halves
            val looksLikeTwoColProse = l.segments.size == 2 &&
                l.segments.all { it.rawText.length > 42 }
            if (looksLikeTwoColProse) break

            rows.add(l)
            prevY = l.y
            idx++
        }

        if (rows.size < 2) return emptyList()
        // Verify column count consistency across rows
        val maxCols = rows.maxOf { it.segments.size }
        if (maxCols !in 2..12) return emptyList()
        return rows
    }

    private fun formatGfmTable(rows: List<GlyphLine>): String {
        // Cluster X-starts across all rows into stable column anchors
        val allStarts = rows.flatMap { r -> r.segments.map { it.xStart } }.sorted()
        val anchors = mutableListOf<Float>()
        for (x in allStarts) {
            val existing = anchors.indexOfFirst { abs(it - x) <= 22f }
            if (existing == -1) {
                anchors.add(x)
            }
        }
        anchors.sort()
        val colCount = anchors.size.coerceIn(2, 12)

        val grid = rows.map { row ->
            val cells = Array(colCount) { "" }
            for (seg in row.segments) {
                var bestCol = 0
                var bestDist = Float.MAX_VALUE
                for (c in 0 until colCount) {
                    val d = abs(anchors[c] - seg.xStart)
                    if (d < bestDist) {
                        bestDist = d
                        bestCol = c
                    }
                }
                val escaped = seg.rawText.replace("|", "\\|").trim()
                cells[bestCol] = if (cells[bestCol].isEmpty()) escaped else "${cells[bestCol]} $escaped"
            }
            cells.toList()
        }

        val sb = StringBuilder()
        val header = grid.first()
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        sb.append("| ").append(List(colCount) { "---" }.joinToString(" | ")).append(" |\n")
        for (r in 1 until grid.size) {
            sb.append("| ").append(grid[r].joinToString(" | ")).append(" |\n")
        }
        return sb.toString().trimEnd()
    }

    private fun detectHeadingLevel(line: GlyphLine, profile: TypographyProfile): Int {
        val raw = line.rawText.trim()
        if (raw.length < 2 || raw.length > 140) return 0
        if (BULLET_REGEX.matches(raw)) return 0
        if (raw.endsWith(".") && raw.length > 65) return 0
        if (line.segments.size >= 3) return 0

        val ratio = line.avgFontSize / profile.modalBodyFontSize
        return when {
            ratio >= 1.58f -> 1
            ratio >= 1.32f -> 2
            ratio >= 1.15f -> 3
            ratio >= 1.04f && line.isBold && raw.length <= 95 -> 3
            line.isBold && CHAPTER_HEADING_REGEX.matches(raw) && raw.length <= 95 -> 2
            line.isBold && raw.length in 4..72 && raw == raw.uppercase(Locale.US) &&
                raw.any { it.isLetter() } -> 3
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
            code.contains("function ") || code.contains("const ") && code.contains("=>") -> "javascript"
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

    private fun stripLeadingOrderedMarker(styled: String): String {
        return styled.replaceFirst(Regex("^(\\*\\*|\\*)?(\\d{1,4}[.)]|\\([a-zA-Z0-9]{1,3}\\)|[a-zA-Z]\\))\\s*(\\*\\*|\\*)?\\s*"), "").trim()
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

    private suspend fun extractPageViaOcr(
        renderer: PdfRenderer,
        zeroBasedPageIndex: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): List<GlyphLine> {
        if (zeroBasedPageIndex < 0 || zeroBasedPageIndex >= renderer.pageCount) return emptyList()
        var bitmap: Bitmap? = null
        return try {
            val page = renderer.openPage(zeroBasedPageIndex)
            val origW = page.width.coerceAtLeast(1)
            val origH = page.height.coerceAtLeast(1)
            val scale = min(1.8f, 1500f / max(origW, origH).toFloat()).coerceAtLeast(1f)
            val renderW = (origW * scale).roundToInt().coerceIn(320, 1600)
            val renderH = (origH * scale).roundToInt().coerceIn(320, 2200)

            bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val lines = mutableListOf<GlyphLine>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.trim()
                    if (text.isEmpty()) continue
                    val rect = line.boundingBox
                    val y = rect?.top?.toFloat() ?: 0f
                    val xStart = rect?.left?.toFloat() ?: 0f
                    val xEnd = rect?.right?.toFloat() ?: renderW.toFloat()
                    val estPt = ((rect?.height()?.toFloat() ?: 22f) / scale * 0.72f).coerceIn(8f, 36f)
                    val seg = LineSegment(
                        xStart = xStart / scale,
                        xEnd = xEnd / scale,
                        rawText = text,
                        styledText = text,
                        avgFontSize = estPt,
                        isBold = false,
                        isItalic = false,
                        isMonospace = false
                    )
                    lines.add(
                        GlyphLine(
                            y = y / scale,
                            xStart = xStart / scale,
                            xEnd = xEnd / scale,
                            pageWidth = origW.toFloat(),
                            pageHeight = origH.toFloat(),
                            avgFontSize = estPt,
                            maxFontSize = estPt,
                            isBold = false,
                            isItalic = false,
                            isMonospace = false,
                            rawText = text,
                            styledText = text,
                            segments = listOf(seg)
                        )
                    )
                }
            }
            lines.sortedBy { it.y }
        } catch (_: Exception) {
            emptyList()
        } finally {
            bitmap?.recycle()
        }
    }

    // =========================================================================
    // Download & Export Utilities (Zero-Permission MediaStore + SAF)
    // =========================================================================

    suspend fun saveMarkdownToDownloads(
        context: Context,
        sourceFile: File,
        desiredFileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanName = if (desiredFileName.endsWith(".md", ignoreCase = true)) {
                desiredFileName
            } else {
                "$desiredFileName.md"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Swiffle")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to create Downloads entry."))

                resolver.openOutputStream(itemUri)?.use { out ->
                    BufferedInputStream(FileInputStream(sourceFile), 65_536).use { input ->
                        input.copyTo(out, 65_536)
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Unable to write Markdown file."))

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                Result.success("Downloads/Swiffle/$cleanName")
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                    "Swiffle"
                ).apply { mkdirs() }
                val dest = File(dir, cleanName)
                sourceFile.copyTo(dest, overwrite = true)
                Result.success(dest.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeMarkdownToUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                BufferedInputStream(FileInputStream(sourceFile), 65_536).use { input ->
                    input.copyTo(out, 65_536)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open destination file."))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Helper Functions
    // =========================================================================

    private fun buildProgress(
        done: Int,
        total: Int,
        phase: String,
        loopStartMs: Long,
        words: Int,
        headings: Int,
        tables: Int,
        codeBlocks: Int,
        ocrPages: Int,
        headersStripped: Int
    ): PdfToMarkdownProgress {
        val elapsedSec = ((System.currentTimeMillis() - loopStartMs) / 1000f).coerceAtLeast(0.05f)
        val pps = if (done > 0) done / elapsedSec else 0f
        val remaining = (total - done).coerceAtLeast(0)
        val eta = if (pps > 0.05f) (remaining / pps).roundToInt() else 0
        return PdfToMarkdownProgress(
            currentPage = done,
            totalSelectedPages = total,
            phase = phase,
            pagesPerSecond = pps,
            etaSeconds = eta,
            wordsExtracted = words,
            headingsFound = headings,
            tablesFound = tables,
            codeBlocksFound = codeBlocks,
            ocrPagesProcessed = ocrPages,
            headersStripped = headersStripped
        )
    }

    private fun copyUriToTempPdf(context: Context, uri: Uri): File {
        val tmpDir = File(context.cacheDir, "pdf_md_tmp").apply { mkdirs() }
        val tmpFile = File(tmpDir, "src_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedOutputStream(FileOutputStream(tmpFile), 65_536).use { out ->
                input.copyTo(out, 65_536)
            }
        } ?: throw IllegalStateException("Cannot read selected PDF file.")
        return tmpFile
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = c.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Document.pdf" } ?: "Document.pdf"
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return c.getLong(idx)
                }
            }
        } catch (_: Exception) {
        }
        return 0L
    }

    private fun countOutlineItems(first: PDOutlineItem?): Int {
        var count = 0
        var node = first
        while (node != null && count < 1000) {
            count++
            count += countOutlineItems(node.firstChild)
            node = node.nextSibling
        }
        return count
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        var count = 0
        var inWord = false
        for (ch in text) {
            if (ch.isLetterOrDigit()) {
                if (!inWord) {
                    count++
                    inWord = true
                }
            } else {
                inWord = false
            }
        }
        return count
    }

    private fun escapeYaml(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }
}
