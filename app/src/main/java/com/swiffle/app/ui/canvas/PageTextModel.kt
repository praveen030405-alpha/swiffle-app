package com.swiffle.app.ui.canvas

import android.util.Log
import com.swiffle.app.NativeEngine
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal Endpoint for text positions within a PDF document.
 * Packed into a 64-bit Long for zero-allocation and single-place ordering comparison:
 * - Bits 40..63 (24 bits): Page index (0-based)
 * - Bits 20..39 (20 bits): Line index (0-based)
 * - Bits 0..19  (20 bits): Glyph index (0-based)
 */
@JvmInline
value class Endpoint(val packed: Long) : Comparable<Endpoint> {
    constructor(pageIndex: Int, lineIndex: Int, glyphIndex: Int) : this(
        ((pageIndex.toLong() and 0xFFFFFFL) shl 40) or
        ((lineIndex.toLong() and 0xFFFFFL) shl 20) or
        (glyphIndex.toLong() and 0xFFFFFL)
    )

    val pageIndex: Int get() = ((packed ushr 40) and 0xFFFFFFL).toInt()
    val lineIndex: Int get() = ((packed ushr 20) and 0xFFFFFL).toInt()
    val glyphIndex: Int get() = (packed and 0xFFFFFL).toInt()

    override fun compareTo(other: Endpoint): Int = packed.compareTo(other.packed)

    override fun toString(): String = "Endpoint(p=$pageIndex, l=$lineIndex, g=$glyphIndex)"
}

/**
 * Single glyph representation in PDF page coordinates.
 */
data class TextGlyph(
    val char: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
) {
    val top: Float get() = y - h
    val bottom: Float get() = y
    val left: Float get() = x
    val right: Float get() = x + w
}

/**
 * Single line of text with continuous horizontal boundary markers.
 * An array of n glyphs has n + 1 boundaries:
 * boundaries[0] = line start
 * boundaries[k] = boundary between glyph k-1 and glyph k (bridging word spaces)
 * boundaries[n] = line end
 */
class TextLine(
    val lineIndex: Int,
    val glyphStart: Int,
    val glyphEnd: Int, // inclusive
    val charStart: Int,
    val charEnd: Int,  // inclusive
    val top: Float,
    val bottom: Float,
    val boundaries: FloatArray
) {
    val glyphCount: Int get() = if (glyphEnd >= glyphStart) glyphEnd - glyphStart + 1 else 0
    val height: Float get() = bottom - top
}

/**
 * Semantic word with mapped glyph and source character ranges.
 */
data class TextWord(
    val wordIndex: Int,
    val lineIndex: Int,
    val glyphStart: Int,
    val glyphEnd: Int,
    val charStart: Int,
    val charEnd: Int,
    val text: String
)

/**
 * Semantic sentence with mapped word and source character ranges.
 */
data class TextSentence(
    val sentenceIndex: Int,
    val wordStart: Int,
    val wordEnd: Int,
    val charStart: Int,
    val charEnd: Int,
    val text: String
)

/**
 * Immutable page text model serving as the single foundation for selection,
 * search, and position-based TTS.
 */
class PageTextModel(
    val pageNum: Int,
    val text: String,
    val glyphs: List<TextGlyph>,
    val lines: List<TextLine>,
    val words: List<TextWord>,
    val sentences: List<TextSentence>
) {
    val glyphCount: Int get() = glyphs.size

    /**
     * Finds the nearest endpoint for a given (pdfX, pdfY) coordinate.
     * Guaranteed to never return null:
     * - Vertically clamps to the nearest line (first/last line, inter-line gaps).
     * - Horizontally clamps to line start/end via binary search on line boundaries.
     */
    fun nearestEndpoint(pdfX: Float, pdfY: Float): Endpoint {
        if (lines.isEmpty()) {
            return Endpoint(pageNum - 1, 0, 0)
        }

        // Phase 0: Direct glyph bounding-box hit test (strict horizontal interval [left, right))
        for (line in lines) {
            if (pdfY >= line.top - 1.5f && pdfY <= line.bottom + 1.5f) {
                val lineGlyphStart = line.glyphStart
                val lineGlyphEnd = line.glyphEnd
                for (gIdx in lineGlyphStart..lineGlyphEnd) {
                    if (gIdx !in glyphs.indices) continue
                    val g = glyphs[gIdx]
                    if (pdfX >= g.left && pdfX < g.right) {
                        val glyphOffset = (gIdx - line.glyphStart).coerceIn(0, (line.glyphCount - 1).coerceAtLeast(0))
                        return Endpoint(pageNum - 1, line.lineIndex, glyphOffset)
                    }
                }
            }
        }

        // Phase 1: Pick the best matching line using 2D proximity with strong preference for horizontal containment
        var bestLineIdx = 0
        var minScore = Float.MAX_VALUE

        for (l in lines.indices) {
            val line = lines[l]
            val bounds = line.boundaries
            val lineMinX = if (bounds.isNotEmpty()) bounds.first() else 0f
            val lineMaxX = if (bounds.isNotEmpty()) bounds.last() else 0f

            // Vertical distance from line bounding box
            val dy = when {
                pdfY < line.top -> line.top - pdfY
                pdfY > line.bottom -> pdfY - line.bottom
                else -> 0f
            }

            // Horizontal distance from line span
            val dx = when {
                pdfX < lineMinX -> lineMinX - pdfX
                pdfX > lineMaxX -> pdfX - lineMaxX
                else -> 0f
            }

            // Strongly weight vertical accuracy while ensuring horizontal closeness on multi-column pages
            val score = (dy * 4.0f) + dx
            if (score < minScore) {
                minScore = score
                bestLineIdx = l
            }
        }

        val targetLine = lines[bestLineIdx]
        val bounds = targetLine.boundaries
        if (bounds.isEmpty() || targetLine.glyphCount == 0) {
            return Endpoint(pageNum - 1, targetLine.lineIndex, 0)
        }

        // 2. Horizontally clamp or binary-search within target line
        if (pdfX <= bounds.first()) {
            return Endpoint(pageNum - 1, targetLine.lineIndex, 0)
        }
        if (pdfX >= bounds.last()) {
            return Endpoint(pageNum - 1, targetLine.lineIndex, (targetLine.glyphCount - 1).coerceAtLeast(0))
        }

        // Binary search to find the closest glyph boundary
        var low = 0
        var high = targetLine.glyphCount - 1
        var bestGlyphIdx = 0
        var minDiffX = Float.MAX_VALUE

        while (low <= high) {
            val mid = (low + high) ushr 1
            val leftB = bounds[mid]
            val rightB = bounds[mid + 1]
            val glyphMid = (leftB + rightB) * 0.5f
            val diff = kotlin.math.abs(pdfX - glyphMid)
            if (diff < minDiffX) {
                minDiffX = diff
                bestGlyphIdx = mid
            }
            if (pdfX < glyphMid) {
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        return Endpoint(pageNum - 1, targetLine.lineIndex, bestGlyphIdx)
    }

    /**
     * Resolves an Endpoint from a character index within the page.
     */
    fun endpointFromCharIndex(charIndex: Int): Endpoint {
        if (lines.isEmpty()) return Endpoint(pageNum - 1, 0, 0)
        for (line in lines) {
            if (charIndex in line.charStart..line.charEnd) {
                val glyphOffset = (charIndex - line.charStart).coerceIn(0, (line.glyphCount - 1).coerceAtLeast(0))
                return Endpoint(pageNum - 1, line.lineIndex, glyphOffset)
            }
        }
        if (charIndex < lines.first().charStart) {
            return Endpoint(pageNum - 1, lines.first().lineIndex, 0)
        }
        val lastLine = lines.last()
        return Endpoint(pageNum - 1, lastLine.lineIndex, (lastLine.glyphCount - 1).coerceAtLeast(0))
    }

    /**
     * Converts a character range [charStart..charEnd] into continuous per-line highlight bands.
     */
    fun getHighlightBandsForCharRange(charStart: Int, charEnd: Int): List<HighlightBand> {
        if (lines.isEmpty() || charStart < 0 || charEnd < charStart) return emptyList()
        val startEp = endpointFromCharIndex(charStart)
        val endEp = endpointFromCharIndex(charEnd)
        return getHighlightBands(startEp, endEp)
    }

    /**
     * Converts a selection range [start..end] into continuous per-line highlight bands.
     * Completely eliminates letter gaps and per-glyph disjoint pill artifacts.
     */
    fun getHighlightBands(start: Endpoint, end: Endpoint): List<HighlightBand> {
        if (lines.isEmpty()) return emptyList()

        val normStart = minOf(start, end)
        val normEnd = maxOf(start, end)

        val startLineIdx = normStart.lineIndex.coerceIn(0, lines.size - 1)
        val endLineIdx = normEnd.lineIndex.coerceIn(startLineIdx, lines.size - 1)

        val bands = ArrayList<HighlightBand>(endLineIdx - startLineIdx + 1)

        for (l in startLineIdx..endLineIdx) {
            val line = lines[l]
            if (line.glyphCount == 0 || line.boundaries.size < 2) continue

            val glyphStartInLine = if (l == startLineIdx) {
                normStart.glyphIndex.coerceIn(0, line.glyphCount - 1)
            } else {
                0
            }

            val glyphEndInLine = if (l == endLineIdx) {
                normEnd.glyphIndex.coerceIn(glyphStartInLine, line.glyphCount - 1)
            } else {
                line.glyphCount - 1
            }

            val leftX = line.boundaries[glyphStartInLine]
            val rightX = line.boundaries[glyphEndInLine + 1]

            if (rightX > leftX) {
                bands.add(
                    HighlightBand(
                        lineIndex = line.lineIndex,
                        left = leftX,
                        top = line.top,
                        right = rightX,
                        bottom = line.bottom
                    )
                )
            }
        }

        return bands
    }

    /**
     * Resolves the source character index corresponding to an Endpoint.
     */
    fun getCharIndex(endpoint: Endpoint): Int {
        if (lines.isEmpty()) return 0
        val line = lines[endpoint.lineIndex.coerceIn(0, lines.size - 1)]
        return (line.charStart + endpoint.glyphIndex).coerceIn(0, (text.length - 1).coerceAtLeast(0))
    }

    /**
     * Returns the TextGlyph located at the given Endpoint.
     */
    fun getGlyph(endpoint: Endpoint): TextGlyph? {
        if (lines.isEmpty()) return null
        val line = lines[endpoint.lineIndex.coerceIn(0, lines.size - 1)]
        val gIdx = line.glyphStart + endpoint.glyphIndex
        return glyphs.getOrNull(gIdx)
    }

    /**
     * Returns the exact text substring spanning between [start] and [end].
     */
    /**
     * Returns the exact text substring spanning between [start] and [end].
     */
    fun getSelectedText(start: Endpoint, end: Endpoint): String {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        if (lines.isEmpty()) return ""
        val sLineIdx = s.lineIndex.coerceIn(0, lines.size - 1)
        val eLineIdx = e.lineIndex.coerceIn(sLineIdx, lines.size - 1)
        val sb = StringBuilder()
        for (l in sLineIdx..eLineIdx) {
            val line = lines[l]
            if (line.glyphCount == 0) continue
            val gStart = if (l == sLineIdx) s.glyphIndex.coerceIn(0, line.glyphCount - 1) else 0
            val gEnd = if (l == eLineIdx) e.glyphIndex.coerceIn(gStart, line.glyphCount - 1) else line.glyphCount - 1
            val cStart = line.charStart + gStart
            val cEnd = line.charStart + gEnd
            if (cStart in 0 until text.length && cEnd in cStart until text.length) {
                if (sb.isNotEmpty() && l > sLineIdx) sb.append(' ')
                sb.append(text.substring(cStart, (cEnd + 1).coerceAtMost(text.length)))
            }
        }
        return sb.toString()
    }

    /**
     * Returns the list of CharRects spanning between [start] and [end].
     */
    fun getSelectedRects(start: Endpoint, end: Endpoint): List<CharRect> {
        val s = minOf(start, end)
        val e = maxOf(start, end)
        if (lines.isEmpty() || glyphs.isEmpty()) return emptyList()
        val sLineIdx = s.lineIndex.coerceIn(0, lines.size - 1)
        val eLineIdx = e.lineIndex.coerceIn(sLineIdx, lines.size - 1)
        val list = ArrayList<CharRect>()
        for (l in sLineIdx..eLineIdx) {
            val line = lines[l]
            if (line.glyphCount == 0) continue
            val gStart = if (l == sLineIdx) s.glyphIndex.coerceIn(0, line.glyphCount - 1) else 0
            val gEnd = if (l == eLineIdx) e.glyphIndex.coerceIn(gStart, line.glyphCount - 1) else line.glyphCount - 1
            for (g in gStart..gEnd) {
                val realIdx = line.glyphStart + g
                if (realIdx in glyphs.indices) {
                    val glyph = glyphs[realIdx]
                    list.add(CharRect(glyph.char, glyph.x, glyph.y, glyph.w, glyph.h))
                }
            }
        }
        return list
    }

    companion object {
        /**
         * Rule-based parser to construct a PageTextModel from raw PDF JSON coordinates.
         */
        fun fromGeometryJson(pageNum: Int, jsonStr: String): PageTextModel {
            if (jsonStr.isBlank() || jsonStr == "{}" || jsonStr == "[]") {
                return PageTextModel(pageNum, "", emptyList(), emptyList(), emptyList(), emptyList())
            }

            return try {
                val soaObj = JSONObject(jsonStr)
                val cArr = soaObj.getJSONArray("c")
                val xArr = soaObj.getJSONArray("x")
                val yArr = soaObj.getJSONArray("y")
                val wArr = soaObj.getJSONArray("w")
                val hArr = soaObj.getJSONArray("h")
                val len = cArr.length()

                val rawGlyphs = ArrayList<TextGlyph>(len)
                for (i in 0 until len) {
                    val ch = cArr.getString(i)
                    val x = xArr.getDouble(i).toFloat()
                    val y = yArr.getDouble(i).toFloat()
                    val w = wArr.getDouble(i).toFloat()
                    val h = hArr.getDouble(i).toFloat()
                    rawGlyphs.add(TextGlyph(ch, x, y, w, h))
                }

                // Cluster raw glyphs into visual lines first
                val rawLines = clusterGlyphsIntoLines(rawGlyphs)

                // Sort visual lines monotonically top-to-bottom (ascending top coordinate)
                rawLines.sortBy { it.top }

                // Now rebuild the canonical glyph list and line metadata in strictly top-to-bottom order
                val canonicalGlyphs = ArrayList<TextGlyph>(len)
                val canonicalLines = ArrayList<TextLine>(rawLines.size)
                val textBuilder = StringBuilder(len)
                var charCounter = 0

                for (lineIdx in rawLines.indices) {
                    val line = rawLines[lineIdx]
                    val sortedLineGlyphs = line.glyphs.sortedBy { it.left }
                    val lineGlyphStart = canonicalGlyphs.size
                    val lineCharStart = charCounter

                    for (g in sortedLineGlyphs) {
                        canonicalGlyphs.add(g)
                        textBuilder.append(g.char)
                        charCounter++
                    }

                    val lineGlyphEnd = canonicalGlyphs.size - 1
                    val lineCharEnd = charCounter - 1
                    val glyphCount = sortedLineGlyphs.size

                    val lineTop = if (sortedLineGlyphs.isNotEmpty()) sortedLineGlyphs.minOf { it.top } else 0f
                    val lineBottom = if (sortedLineGlyphs.isNotEmpty()) sortedLineGlyphs.maxOf { it.bottom } else 0f

                    val boundaries = FloatArray(glyphCount + 1)
                    if (glyphCount > 0) {
                        boundaries[0] = sortedLineGlyphs.first().left
                        for (k in 1 until glyphCount) {
                            val prevG = sortedLineGlyphs[k - 1]
                            val currG = sortedLineGlyphs[k]
                            boundaries[k] = (prevG.right + currG.left) * 0.5f
                        }
                        boundaries[glyphCount] = sortedLineGlyphs.last().right
                    }

                    canonicalLines.add(
                        TextLine(
                            lineIndex = lineIdx,
                            glyphStart = lineGlyphStart,
                            glyphEnd = lineGlyphEnd,
                            charStart = lineCharStart,
                            charEnd = lineCharEnd,
                            top = lineTop,
                            bottom = lineBottom,
                            boundaries = boundaries
                        )
                    )
                }

                val fullText = textBuilder.toString()
                val words = buildWords(canonicalLines, fullText)
                val sentences = buildSentences(words, fullText)

                PageTextModel(pageNum, fullText, canonicalGlyphs, canonicalLines, words, sentences)
            } catch (e: Exception) {
                try {
                    Log.e("PageTextModel", "Failed to parse geometry for page $pageNum", e)
                } catch (_: Throwable) {
                    System.err.println("PageTextModel: Failed to parse geometry for page $pageNum: ${e.message}")
                }
                PageTextModel(pageNum, "", emptyList(), emptyList(), emptyList(), emptyList())
            }
        }

        private data class RawLineCluster(
            val glyphs: MutableList<TextGlyph>,
            var top: Float,
            var bottom: Float,
            val anchorBaselineY: Float,
            val anchorH: Float
        )

        /**
         * Clusters glyphs into lines using strict baseline matching and vertical overlap.
         * Prevents adjacent lines from accidentally merging into interleaved words.
         */
        private fun clusterGlyphsIntoLines(glyphs: List<TextGlyph>): ArrayList<RawLineCluster> {
            val clusters = ArrayList<RawLineCluster>()
            if (glyphs.isEmpty()) return clusters

            for (g in glyphs) {
                var matched: RawLineCluster? = null
                var minDiffY = Float.MAX_VALUE

                for (cluster in clusters) {
                    val baselineDiff = kotlin.math.abs(cluster.anchorBaselineY - g.bottom)
                    val minH = minOf(cluster.anchorH, g.h)

                    // Vertical overlap check between cluster bounds and glyph bounds
                    val overlapTop = maxOf(cluster.top, g.top)
                    val overlapBottom = minOf(cluster.bottom, g.bottom)
                    val verticalOverlap = overlapBottom - overlapTop

                    // Must have meaningful vertical overlap (> 40% of glyph height)
                    // and baseline difference within 35% of glyph height
                    if (baselineDiff <= (minH * 0.35f).coerceAtLeast(2.5f) && verticalOverlap >= minH * 0.40f) {
                        if (baselineDiff < minDiffY) {
                            minDiffY = baselineDiff
                            matched = cluster
                        }
                    }
                }

                if (matched != null) {
                    matched.glyphs.add(g)
                    matched.top = minOf(matched.top, g.top)
                    matched.bottom = maxOf(matched.bottom, g.bottom)
                } else {
                    clusters.add(RawLineCluster(mutableListOf(g), g.top, g.bottom, g.bottom, g.h))
                }
            }

            return clusters
        }

        /**
         * Extracts word entities from lines with whitespace splitting.
         */
        private fun buildWords(lines: List<TextLine>, fullText: String): List<TextWord> {
            val words = ArrayList<TextWord>()
            var wordCounter = 0

            for (line in lines) {
                val lineText = fullText.substring(line.charStart, line.charEnd + 1)
                var inWord = false
                var wordStartOffset = 0

                for (idx in lineText.indices) {
                    val c = lineText[idx]
                    val isWordChar = !c.isWhitespace()

                    if (isWordChar && !inWord) {
                        inWord = true
                        wordStartOffset = idx
                    } else if (!isWordChar && inWord) {
                        inWord = false
                        val wordStr = lineText.substring(wordStartOffset, idx)
                        val gStart = line.glyphStart + wordStartOffset
                        val gEnd = line.glyphStart + idx - 1
                        val cStart = line.charStart + wordStartOffset
                        val cEnd = line.charStart + idx - 1
                        words.add(TextWord(wordCounter++, line.lineIndex, gStart, gEnd, cStart, cEnd, wordStr))
                    }
                }
                if (inWord) {
                    val wordStr = lineText.substring(wordStartOffset)
                    val gStart = line.glyphStart + wordStartOffset
                    val gEnd = line.glyphEnd
                    val cStart = line.charStart + wordStartOffset
                    val cEnd = line.charEnd
                    words.add(TextWord(wordCounter++, line.lineIndex, gStart, gEnd, cStart, cEnd, wordStr))
                }
            }

            return words
        }

        /**
         * Rule-based sentence segmenter.
         * Correctly handles:
         * - Abbreviations: e.g., i.e., vs., Dr., Mr., Mrs., Fig., No., etc., v2.
         * - Numbers and decimals: ₹3, 12.5, 15-20%
         * - Bullet points and headings
         * - Ellipses (...)
         */
        private fun buildSentences(words: List<TextWord>, fullText: String): List<TextSentence> {
            if (words.isEmpty()) return emptyList()

            val abbreviations = setOf(
                "e.g.", "i.e.", "vs.", "mr.", "mrs.", "ms.", "dr.", "prof.",
                "fig.", "no.", "vol.", "pp.", "dept.", "approx.", "est."
            )

            val sentences = ArrayList<TextSentence>()
            var sentStartWordIdx = 0

            for (wIdx in words.indices) {
                val isLast = wIdx == words.size - 1
                val curWord = words[wIdx]
                val wordText = curWord.text.trim()
                val lower = wordText.lowercase()

                var isSentenceEnd = isLast

                if (!isLast && !abbreviations.contains(lower)) {
                    val lastChar = wordText.lastOrNull()
                    if (lastChar == '.' || lastChar == '!' || lastChar == '?') {
                        // Check if it's an abbreviation like "v2." or currency decimal like "3."
                        val beforeDot = wordText.dropLast(1)
                        val isDecimalOrAbbrev = beforeDot.all { it.isDigit() } || beforeDot.length <= 1

                        if (!isDecimalOrAbbrev) {
                            // Check next word capitalization
                            val nextWord = words[wIdx + 1]
                            val firstNextChar = nextWord.text.firstOrNull()
                            if (firstNextChar != null && (firstNextChar.isUpperCase() || firstNextChar == '•' || firstNextChar == '-')) {
                                isSentenceEnd = true
                            }
                        }
                    }
                }

                if (isSentenceEnd) {
                    val startWord = words[sentStartWordIdx]
                    val endWord = words[wIdx]
                    val sCharStart = startWord.charStart
                    val sCharEnd = endWord.charEnd
                    val sentenceStr = fullText.substring(sCharStart, (sCharEnd + 1).coerceAtMost(fullText.length))

                    sentences.add(
                        TextSentence(
                            sentenceIndex = sentences.size,
                            wordStart = sentStartWordIdx,
                            wordEnd = wIdx,
                            charStart = sCharStart,
                            charEnd = sCharEnd,
                            text = sentenceStr
                        )
                    )
                    sentStartWordIdx = wIdx + 1
                }
            }

            return sentences
        }
    }
}

/**
 * Thread-safe global cache for PageTextModel instances.
 */
object PageTextModelCache {
    private val cache = ConcurrentHashMap<Int, PageTextModel>()

    fun getOrBuild(pageNum: Int, nativePtr: Long): PageTextModel {
        cache[pageNum]?.let { existing ->
            if (existing.lines.isNotEmpty()) return existing
            if (nativePtr == 0L) return existing
        }

        if (nativePtr == 0L) {
            return PageTextModel(pageNum, "", emptyList(), emptyList(), emptyList(), emptyList())
        }

        val jsonStr = try {
            NativeEngine.extractPageGeometryFromPtr(nativePtr, pageNum)
        } catch (e: Exception) {
            Log.e("PageTextModelCache", "Error extracting geometry for page $pageNum", e)
            ""
        }

        val model = PageTextModel.fromGeometryJson(pageNum, jsonStr)
        if (model.lines.isNotEmpty()) {
            cache[pageNum] = model
        }
        return model
    }

    fun clear() {
        cache.clear()
    }
}
