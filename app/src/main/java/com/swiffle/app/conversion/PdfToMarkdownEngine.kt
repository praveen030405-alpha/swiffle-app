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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class MarkdownPreset(
    val title: String,
    val subtitle: String
) {
    ACADEMIC_BOOK(
        title = "Academic & Book",
        subtitle = "Vector tables, headings, page breaks & clean prose"
    ),
    LLM_RAG_READY(
        title = "LLM / RAG Ready",
        subtitle = "YAML metadata, page anchors & structured tables"
    ),
    TECHNICAL_DOCS(
        title = "Technical Docs",
        subtitle = "Code fences, inline code & exact grid fidelity"
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
    val insertPageMarkers: Boolean = true,
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
                    insertPageMarkers = true,
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
                    insertPageMarkers = true,
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

/**
 * Industrial-grade, Unlimited-Page Streaming PDF-to-Markdown Conversion Engine (v2).
 *
 * Architecture modeled after Tabula-Lattice / pdfplumber / PyMuPDF4LLM, running natively on Android:
 * - Constant O(1) memory footprint for 500–1,000+ page PDFs via `MemoryUsageSetting.setupTempFileOnly()`
 *   and buffered disk-backed Markdown streaming
 * - Direct PDF vector graphics stream parsing (`PdfVectorRulingExtractor`) to reconstruct exact
 *   ruled table grids with Row-Band Column Topology Partitioning & explicit blank-cell preservation
 * - Raw `processTextPosition` glyph collection with affine TextMatrix rotation filtering so diagonal
 *   watermarks (e.g. 45° "FINAL") never leak stray fragments into tables, headings, or TOC
 * - Page-wide visual row reassembly + X-projection borderless table alignment
 * - Airtight multi-column academic prose reordering that never scrambles forms or financial tables
 * - Unicode ligature expansion & dropped-"fi" word restoration
 * - Automatic ML Kit OCR fallback for scanned image-only pages
 */
object PdfToMarkdownEngine {

    private const val PREVIEW_CHAR_LIMIT = 45_000

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
                val hasPdfOutline = doc.documentCatalog?.documentOutline?.firstChild != null

                // ── PASS 1: Fast Statistical Typography & Margin Profiling ──
                onProgress(
                    PdfToMarkdownProgress(
                        currentPage = 0,
                        totalSelectedPages = totalSelected,
                        phase = "Profiling typography, vector geometry & margins…",
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

                val profile = buildTypographyProfile(doc, selectedPages, hasPdfOutline)
                val glyphCollector = SpatialGlyphCollector()
                val ocrRecognizer = if (config.ocrFallbackForScannedPages) {
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                } else null

                val tocEntries = mutableListOf<MarkdownTocEntry>()
                val usedAnchors = mutableMapOf<String, Int>()
                val allDetectedWatermarks = linkedSetOf<String>()
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

                // ── PASS 2: Memory-Safe Page-by-Page Vector + Spatial Streaming ──
                BufferedWriter(OutputStreamWriter(FileOutputStream(tempBodyFile), Charsets.UTF_8), 65_536).use { writer ->
                    for ((index, pageNum) in selectedPages.withIndex()) {
                        coroutineContext.ensureActive()

                        val pdPage = doc.getPage(pageNum - 1)
                        val geometry = PdfVectorRulingExtractor.extractPageGeometry(pdPage)

                        glyphCollector.prepareForPage(pdPage.rotation)
                        glyphCollector.startPage = pageNum
                        glyphCollector.endPage = pageNum
                        try {
                            glyphCollector.getText(doc)
                        } catch (_: Exception) {
                            // Continue to OCR fallback if text extraction fails
                        }

                        var bodyGlyphs = glyphCollector.bodyGlyphs.toList()
                        val rotatedWatermarks = glyphCollector.rotatedWatermarkGlyphs.toList()
                        val rawCharCount = bodyGlyphs.sumOf { it.unicode.length }

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
                            val ocrGlyphs = pdfRenderer?.let { renderer ->
                                extractPageViaOcrAsGlyphs(renderer, pageNum - 1, ocrRecognizer)
                            }.orEmpty()
                            if (ocrGlyphs.isNotEmpty()) {
                                bodyGlyphs = ocrGlyphs
                                totalOcrPages++
                            }
                        }

                        val pageConv = PdfSpatialLayoutAnalyzer.analyzeAndRenderPage(
                            bodyGlyphsInput = bodyGlyphs,
                            rotatedWatermarkGlyphs = rotatedWatermarks,
                            geometry = geometry,
                            pageNum = pageNum,
                            profile = profile,
                            config = config,
                            usedAnchors = usedAnchors
                        )

                        tocEntries.addAll(pageConv.headings.map { entry -> entry.copy(title = MarkdownIpPrivacy.redact(entry.title), anchor = MarkdownIpPrivacy.redact(entry.anchor)) })
                        allDetectedWatermarks.addAll(pageConv.detectedWatermarks)
                        totalHeadings += pageConv.headings.size
                        totalTables += pageConv.tableCount
                        totalCodeBlocks += pageConv.codeBlockCount
                        totalHeadersStripped += pageConv.headersStripped

                        val pageMarkdown = buildString {
                            if (config.insertPageMarkers) {
                                if (index > 0) append("\n\n---\n\n")
                                append("<!-- Page $pageNum of $totalDocPages -->\n\n")
                            } else if (index > 0 && pageConv.markdown.isNotBlank()) {
                                append("\n\n")
                            }
                            append(MarkdownIpPrivacy.redact(pageConv.markdown))
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

                // ── PASS 3: Assemble Honest Frontmatter + Genuine TOC + Streamed Body ──
                onProgress(
                    buildProgress(
                        done = totalSelected,
                        total = totalSelected,
                        phase = "Assembling frontmatter & final .md file…",
                        loopStartMs = conversionLoopStartMs,
                        words = totalWords,
                        headings = totalHeadings,
                        tables = totalTables,
                        codeBlocks = totalCodeBlocks,
                        ocrPages = totalOcrPages,
                        headersStripped = totalHeadersStripped
                    )
                )

                // Deduplicate identical repeated page headings so TOC never lists duplicates
                val uniqueTocEntries = deduplicateTocEntries(tocEntries)
                val shouldEmitToc = config.generateTableOfContents &&
                    uniqueTocEntries.size >= 3 &&
                    (hasPdfOutline || totalTables < totalSelected || uniqueTocEntries.size >= 4)

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
                        append("headings: ${uniqueTocEntries.size}\n")
                        append("tables: $totalTables\n")
                        if (allDetectedWatermarks.isNotEmpty()) {
                            append("watermarks_filtered: \"${escapeYaml(allDetectedWatermarks.joinToString(", "))}\"\n")
                        }
                        append("converted_at: \"$isoDate\"\n")
                        append("converter: \"Swiffle PDF-to-Markdown Engine v2 (Lattice+Spatial)\"\n")
                        append("---\n\n")
                    }

                    if (uniqueTocEntries.none { it.level == 1 }) {
                        append("# $docTitle\n\n")
                    }

                    if (shouldEmitToc) {
                        append("## Table of Contents\n\n")
                        val cappedToc = uniqueTocEntries.take(250)
                        for (entry in cappedToc) {
                            val indent = "  ".repeat((entry.level - 1).coerceIn(0, 3))
                            append("$indent- [${entry.title}](#${entry.anchor})\n")
                        }
                        if (uniqueTocEntries.size > 250) {
                            append("- *…and ${uniqueTocEntries.size - 250} more sections*\n")
                        }
                        append("\n---\n\n")
                    }
                }.let(MarkdownIpPrivacy::redact)

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
                        headingCount = uniqueTocEntries.size,
                        tableCount = totalTables,
                        codeBlockCount = totalCodeBlocks,
                        ocrPageCount = totalOcrPages,
                        headersFootersStripped = totalHeadersStripped,
                        elapsedMs = elapsedMs,
                        previewMarkdown = combinedPreview,
                        isPreviewTruncated = previewTruncated,
                        tocEntries = if (shouldEmitToc) uniqueTocEntries else emptyList()
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
    // Pass 1: Statistical Typography & Repeating Edge Profiler
    // =========================================================================

    private fun buildTypographyProfile(
        doc: PDDocument,
        selectedPages: List<Int>,
        hasPdfOutline: Boolean
    ): TypographyProfile {
        val sampleCount = min(selectedPages.size, 24)
        val sampledPages = if (selectedPages.size <= sampleCount) {
            selectedPages
        } else {
            (0 until sampleCount).map { i ->
                val idx = ((i.toFloat() / (sampleCount - 1)) * (selectedPages.size - 1)).roundToInt()
                selectedPages[idx]
            }.distinct()
        }

        val fontSizeHistogram = mutableMapOf<Int, Int>()
        val spacingHistogram = mutableMapOf<Int, Int>()
        val leftMargins = mutableListOf<Float>()
        val topBandCounts = mutableMapOf<String, Int>()
        val bottomBandCounts = mutableMapOf<String, Int>()

        val collector = SpatialGlyphCollector()

        for (pageNum in sampledPages) {
            val pdPage = doc.getPage(pageNum - 1)
            val box = pdPage.cropBox ?: pdPage.mediaBox
            val rot = ((pdPage.rotation % 360) + 360) % 360
            val pageW = if (rot == 90 || rot == 270) (box?.height ?: 792f) else (box?.width ?: 612f)
            val pageH = if (rot == 90 || rot == 270) (box?.width ?: 612f) else (box?.height ?: 792f)

            collector.prepareForPage(rot)
            collector.startPage = pageNum
            collector.endPage = pageNum
            try {
                collector.getText(doc)
            } catch (_: Exception) {
                continue
            }

            // Exclude diagonal watermarks automatically via collector.bodyGlyphs!
            val lines = PdfSpatialLayoutAnalyzer.assembleVisualLines(
                glyphs = collector.bodyGlyphs,
                pageWidth = pageW,
                pageHeight = pageH,
                preserveStyles = false
            )
            if (lines.isEmpty()) continue

            val seenTopThisPage = mutableSetOf<String>()
            val seenBottomThisPage = mutableSetOf<String>()

            for (i in lines.indices) {
                val line = lines[i]
                // Ignore giant watermark/cover outliers when computing modal body font size
                if (line.avgFontSize in 6f..22f) {
                    val ptBucket = (line.avgFontSize * 2f).roundToInt()
                    fontSizeHistogram[ptBucket] = (fontSizeHistogram[ptBucket] ?: 0) + line.rawText.length
                }

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

                val norm = PdfSpatialLayoutAnalyzer.normalizeEdgeKey(line.rawText)
                if (norm.isNotEmpty()) {
                    if (line.y <= pageH * 0.09f && seenTopThisPage.add(norm)) {
                        topBandCounts[norm] = (topBandCounts[norm] ?: 0) + 1
                    } else if (line.y >= pageH * 0.91f && seenBottomThisPage.add(norm)) {
                        bottomBandCounts[norm] = (bottomBandCounts[norm] ?: 0) + 1
                    }
                }
            }
        }

        val modalBodyPt = (fontSizeHistogram.maxByOrNull { it.value }?.key?.div(2f) ?: 10.5f).coerceIn(7f, 18f)
        val modalSpacing = (spacingHistogram.maxByOrNull { it.value }?.key?.toFloat() ?: (modalBodyPt * 1.35f))
            .coerceIn(modalBodyPt * 1.05f, modalBodyPt * 2.4f)
        val typicalLeft = if (leftMargins.isNotEmpty()) {
            val sortedMargins = leftMargins.sorted()
            sortedMargins[(sortedMargins.size * 0.25f).toInt().coerceIn(0, sortedMargins.lastIndex)]
        } else 54f

        val minRepeat = if (sampledPages.size >= 4) max(3, (sampledPages.size * 0.50f).roundToInt()) else Int.MAX_VALUE
        val repeatingHeaders = topBandCounts.filterValues { it >= minRepeat }.keys
        val repeatingFooters = bottomBandCounts.filterValues { it >= minRepeat }.keys

        return TypographyProfile(
            modalBodyFontSize = modalBodyPt,
            modalLineSpacing = modalSpacing,
            typicalLeftMargin = typicalLeft,
            repeatingHeaders = repeatingHeaders,
            repeatingFooters = repeatingFooters,
            hasPdfOutline = hasPdfOutline
        )
    }

    private fun deduplicateTocEntries(entries: List<MarkdownTocEntry>): List<MarkdownTocEntry> {
        if (entries.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<MarkdownTocEntry>()
        for (e in entries) {
            val key = e.title.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
            if (seen.add(key)) {
                result.add(e)
            }
        }
        return result
    }

    // =========================================================================
    // On-Device ML Kit OCR Fallback (Emits Spatial RawGlyphs for Full Pipeline)
    // =========================================================================

    private suspend fun extractPageViaOcrAsGlyphs(
        renderer: PdfRenderer,
        zeroBasedPageIndex: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ): List<RawGlyph> {
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
            val glyphs = mutableListOf<RawGlyph>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    for (elem in line.elements) {
                        val txt = PdfTextCleaner.normalizeGlyphUnicode(elem.text)
                        if (txt.isBlank()) continue
                        val rect = elem.boundingBox ?: line.boundingBox ?: continue
                        val x = rect.left / scale
                        val w = (rect.right - rect.left).coerceAtLeast(2) / scale
                        val yTop = rect.top / scale
                        val yBottom = rect.bottom / scale
                        val h = (yBottom - yTop).coerceAtLeast(6f)
                        val pt = (h * 0.78f).coerceIn(7.5f, 36f)
                        glyphs.add(
                            RawGlyph(
                                unicode = txt,
                                x = x,
                                y = yBottom - h * 0.15f,
                                width = w,
                                height = h,
                                yTop = yTop,
                                yBottom = yBottom,
                                fontSizePt = pt,
                                widthOfSpace = pt * 0.30f,
                                isBold = false,
                                isItalic = false,
                                isMonospace = false,
                                isObliqueRotated = false
                            )
                        )
                    }
                }
            }
            glyphs
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
