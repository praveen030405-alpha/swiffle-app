package com.swiffle.app.ui.canvas.links

import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.util.Log
import com.swiffle.app.ui.canvas.PageTextModel
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Enterprise PDF Link Engine for Swiffle.
 *
 * Detects both:
 * 1. Native embedded PDF link annotations (Android 15+ / API 35+)
 * 2. Semantic text URLs & hyperlinks embedded in the document text (Android 8 - 15+)
 */
data class PdfLinkTarget(
    val url: String,
    val bounds: List<RectF> // in PDF page points
) {
    /**
     * Hit-tests whether (pdfX, pdfY) falls within any bounding rectangle of this link.
     */
    fun contains(pdfX: Float, pdfY: Float, tolerance: Float = 6f): Boolean {
        for (rect in bounds) {
            val minX = rect.left - tolerance
            val maxX = rect.right + tolerance
            val minY = (minOf(rect.top, rect.bottom)) - tolerance
            val maxY = (maxOf(rect.top, rect.bottom)) + tolerance
            if (pdfX in minX..maxX && pdfY in minY..maxY) {
                return true
            }
        }
        return false
    }
}

object PdfLinkEngine {
    private const val TAG = "PdfLinkEngine"

    // High-precision URL pattern detecting web links and email links
    private val URL_PATTERN: Pattern = Pattern.compile(
        """(?i)\b((?:https?://|www\d{0,3}[.]|[a-z0-9.\-]+[.][a-z]{2,6}/)(?:[^\s()<>]+|\(([^\s()<>]+|(\([^\s()<>]+\)))*\))+(?:\(([^\s()<>]+|(\([^\s()<>]+\)))*\)|[^\s`!()\[\]{};:'".,<>?«»“”‘’]))""",
        Pattern.CASE_INSENSITIVE
    )

    private val EMAIL_PATTERN: Pattern = Pattern.compile(
        """\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"""
    )

    // Cache of extracted links per page number (1-based)
    private val pageLinkCache = ConcurrentHashMap<Int, List<PdfLinkTarget>>()

    /**
     * Clears cached links when a new document is opened.
     */
    fun clearCache() {
        pageLinkCache.clear()
    }

    /**
     * Finds a clickable link target at (pdfX, pdfY) on the given page.
     * Returns the normalized URL if hit, or null if no link was touched.
     */
    fun findLinkAt(
        pageModel: PageTextModel,
        pdfX: Float,
        pdfY: Float,
        tolerancePts: Float = 6f
    ): String? {
        val links = getOrExtractLinks(pageModel)
        for (link in links) {
            if (link.contains(pdfX, pdfY, tolerancePts)) {
                return normalizeUrl(link.url)
            }
        }
        return null
    }

    /**
     * Gets or extracts all clickable links for the given page model.
     */
    fun getOrExtractLinks(pageModel: PageTextModel): List<PdfLinkTarget> {
        val pageNum = pageModel.pageNum
        pageLinkCache[pageNum]?.let { return it }

        val links = mutableListOf<PdfLinkTarget>()
        val text = pageModel.text
        if (text.isBlank() || pageModel.glyphs.isEmpty()) {
            pageLinkCache[pageNum] = emptyList()
            return emptyList()
        }

        // 1. Scan for web URLs
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawUrl = matcher.group()
            val startChar = matcher.start()
            val endChar = matcher.end()

            val rects = computeBoundingRectsForRange(pageModel, startChar, endChar)
            if (rects.isNotEmpty()) {
                links.add(PdfLinkTarget(url = rawUrl, bounds = rects))
            }
        }

        // 2. Scan for email addresses
        val emailMatcher = EMAIL_PATTERN.matcher(text)
        while (emailMatcher.find()) {
            val email = emailMatcher.group()
            val startChar = emailMatcher.start()
            val endChar = emailMatcher.end()

            val rects = computeBoundingRectsForRange(pageModel, startChar, endChar)
            if (rects.isNotEmpty()) {
                links.add(PdfLinkTarget(url = "mailto:$email", bounds = rects))
            }
        }

        val immutableList = links.toList()
        pageLinkCache[pageNum] = immutableList
        return immutableList
    }

    /**
     * Computes line-sliced bounding boxes for a character range [startChar, endChar) in pageModel.
     */
    private fun computeBoundingRectsForRange(
        pageModel: PageTextModel,
        startChar: Int,
        endChar: Int
    ): List<RectF> {
        val rects = mutableListOf<RectF>()
        val glyphs = pageModel.glyphs

        // Slice by line to support multi-line wrapped URLs
        for (line in pageModel.lines) {
            if (line.charEnd < startChar || line.charStart >= endChar) continue

            val lineCharStart = maxOf(line.charStart, startChar)
            val lineCharEnd = minOf(line.charEnd + 1, endChar) // exclusive

            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var top = line.top
            var bottom = line.bottom
            var foundGlyphs = false

            val gStart = maxOf(line.glyphStart, 0)
            val gEnd = minOf(line.glyphEnd, glyphs.lastIndex)

            for (gIdx in gStart..gEnd) {
                // Approximate glyph char offset within the line
                val charOffsetInLine = gIdx - line.glyphStart
                val glyphGlobalChar = line.charStart + charOffsetInLine

                if (glyphGlobalChar in lineCharStart until lineCharEnd) {
                    val g = glyphs[gIdx]
                    minX = minOf(minX, g.left)
                    maxX = maxOf(maxX, g.right)
                    top = minOf(top, g.top)
                    bottom = maxOf(bottom, g.bottom)
                    foundGlyphs = true
                }
            }

            if (foundGlyphs && minX < maxX) {
                rects.add(RectF(minX, top, maxX, bottom))
            }
        }

        return rects
    }

    /**
     * Inspects native PDF link annotations on Android 15 (API 35+) via safe reflection.
     */
    fun inspectNativeLinkAnnotations(page: PdfRenderer.Page, pageIndex: Int): List<PdfLinkTarget> {
        if (Build.VERSION.SDK_INT < 35) return emptyList()

        val results = mutableListOf<PdfLinkTarget>()
        try {
            // Android 15 introduced getLinkAnnotations() / getLinkContents() on PdfRenderer.Page
            val getLinksMethod = page.javaClass.methods.firstOrNull { 
                it.name == "getLinkAnnotations" || it.name == "getLinkContents" || it.name == "getLinks"
            } ?: return emptyList()

            val rawList = getLinksMethod.invoke(page) as? List<*> ?: return emptyList()
            for (raw in rawList) {
                if (raw == null) continue
                val clazz = raw.javaClass

                // URL resolution
                val urlMethod = clazz.methods.firstOrNull { 
                    it.name == "getLinkUrl" || it.name == "getUrl" || it.name == "getUri" 
                }
                val rawUrlObj = urlMethod?.invoke(raw)
                val urlString = rawUrlObj?.toString() ?: continue

                // Bounds resolution
                val boundsMethod = clazz.methods.firstOrNull { 
                    it.name == "getBounds" || it.name == "getRect" || it.name == "getWidgetRect" 
                }
                val boundsObj = boundsMethod?.invoke(raw)

                val rects = mutableListOf<RectF>()
                when (boundsObj) {
                    is RectF -> rects.add(boundsObj)
                    is List<*> -> {
                        for (item in boundsObj) {
                            if (item is RectF) rects.add(item)
                        }
                    }
                }

                if (urlString.isNotBlank() && rects.isNotEmpty()) {
                    results.add(PdfLinkTarget(url = urlString, bounds = rects))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Native link inspection skipped: ${e.message}")
        }
        return results
    }

    /**
     * Normalizes a tapped URL to ensure valid protocol scheme.
     */
    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("mailto:", ignoreCase = true) -> trimmed
            trimmed.startsWith("ftp://", ignoreCase = true) -> trimmed
            trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
            else -> "https://$trimmed"
        }
    }

    /**
     * Extracts the first valid web or email URL from an arbitrary text string, or null if none found.
     */
    fun extractFirstUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val matcher = URL_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return normalizeUrl(matcher.group())
        }

        val emailMatcher = EMAIL_PATTERN.matcher(trimmed)
        if (emailMatcher.find()) {
            return "mailto:${emailMatcher.group()}"
        }

        // Direct fallback: if string looks like example.com or bit.ly
        if (trimmed.matches(Regex("""^(https?://|www\.)?[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(/.*)?$"""))) {
            return normalizeUrl(trimmed)
        }

        return null
    }
}

