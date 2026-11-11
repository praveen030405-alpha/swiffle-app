package com.swiffle.app.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.hypot

data class TextPos(val page: Int, val index: Int) : Comparable<TextPos> {
    override fun compareTo(other: TextPos): Int {
        if (page != other.page) return page.compareTo(other.page)
        return index.compareTo(other.index)
    }
}

data class Selection(val start: TextPos, val end: TextPos)

/**
 * Enterprise Native Android Text Selection Engine.
 * Powered by ICU BreakIterator for semantic word boundaries and line clustering.
 */
object NativeSelectionEngine {
    /**
     * Resolves the exact semantic word boundary around [charIndex] using Android's
     * native ICU BreakIterator engine. Handles contractions ("don't"), decimal numbers ("12.5%"),
     * hyphens, and non-Latin unicode scripts.
     */
    fun findWordRange(text: String, charIndex: Int): IntRange {
        if (text.isEmpty() || charIndex < 0 || charIndex >= text.length) return IntRange.EMPTY
        val iterator = java.text.BreakIterator.getWordInstance(java.util.Locale.getDefault())
        iterator.setText(text)

        var end = iterator.following(charIndex)
        var start = iterator.previous()

        if (start == java.text.BreakIterator.DONE) start = 0
        if (end == java.text.BreakIterator.DONE) end = text.length

        // If the selection hit whitespace, skip to adjacent non-whitespace if possible
        while (start < end && text[start].isWhitespace()) {
            start++
        }
        while (end > start && text[end - 1].isWhitespace()) {
            end--
        }

        if (start >= end) {
            return charIndex..charIndex
        }

        return start until end
    }

    /**
     * Resolves smart entity selection using Android's native TextClassifier service
     * (recognizing URLs, email addresses, phone numbers, addresses, and flight numbers)
     * with graceful fallback to ICU BreakIterator.
     */
    fun suggestSmartSelection(context: android.content.Context, text: String, charIndex: Int): IntRange {
        if (text.isEmpty() || charIndex < 0 || charIndex >= text.length) return IntRange.EMPTY
        
        // 1. Try Android TextClassifier on API 26+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val classifier = (context.getSystemService(android.content.Context.TEXT_CLASSIFICATION_SERVICE) as? android.view.textclassifier.TextClassificationManager)?.textClassifier
                if (classifier != null && !classifier.isDestroyed) {
                    val wordRange = findWordRange(text, charIndex)
                    val start = wordRange.first.coerceIn(0, text.length)
                    val end = (wordRange.last + 1).coerceIn(start, text.length)
                    
                    val request = android.view.textclassifier.TextSelection.Request.Builder(text, start, end)
                        .setDefaultLocales(android.os.LocaleList.getDefault())
                        .build()
                    val suggested = classifier.suggestSelection(request)
                    val s = suggested.selectionStartIndex.coerceIn(0, text.length)
                    val e = suggested.selectionEndIndex.coerceIn(s, text.length)
                    if (s < e) {
                        return s until e
                    }
                }
            } catch (_: Throwable) {
                // Ignore and fall back to ICU
            }
        }
        
        // 2. ICU BreakIterator fallback
        return findWordRange(text, charIndex)
    }

    /**
     * Merges a series of character bounding boxes into visual line segments.
     * Prevents rendering thousands of individual char rectangles by clustering contiguous glyphs
     * on the same baseline into unified line highlight rectangles.
     */
    fun clusterToLineRects(rects: List<CharRect>): List<android.graphics.RectF> {
        if (rects.isEmpty()) return emptyList()
        val result = mutableListOf<android.graphics.RectF>()
        var lineStartIdx = 0

        for (i in rects.indices) {
            val isLast = i == rects.size - 1
            val cur = rects[i]
            val next = if (isLast) null else rects[i + 1]

            // Line break if vertical baseline jumps or last character
            val isLineBreak = next == null || kotlin.math.abs(cur.y - next.y) > cur.h * 0.45f
            if (isLineBreak) {
                val lineChars = rects.subList(lineStartIdx, i + 1)
                val minX = lineChars.minOf { it.x }
                val minY = lineChars.minOf { it.y - it.h }
                val maxX = lineChars.maxOf { it.x + it.w }
                val maxY = lineChars.maxOf { it.y }

                result.add(android.graphics.RectF(minX, minY, maxX, maxY))
                lineStartIdx = i + 1
            }
        }
        return result
    }
}
