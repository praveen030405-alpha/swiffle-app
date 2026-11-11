package com.swiffle.app.pdfedit.core.typography

import android.graphics.Paint
import android.graphics.RectF
import com.swiffle.app.pdfedit.core.ast.PdfParagraphBlock
import com.swiffle.app.pdfedit.core.ast.PdfTextLineNode
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class MicroTypographyResult(
    val fitsInSingleLine: Boolean,
    val primaryLineTz: Float, // Horizontal scaling (e.g. 98.5f)
    val primaryLineTc: Float, // Character spacing in pt
    val primaryLineTw: Float, // Word spacing in pt
    val kerningShifts: List<Float>,
    val reflowedLines: List<ReflowedLine>
)

data class ReflowedLine(
    val lineText: String,
    val bounds: RectF,
    val baselineY: Float,
    val horizontalScale: Float,
    val charSpacing: Float,
    val wordSpacing: Float,
    val kerningShifts: List<Float>
)

/**
 * Hermann Zapf `Hz` Micro-Typography Solver & Knuth-Plass Line Reflow Engine.
 * 
 * Provides imperceptible character and word expansion/compression (Tz, Tc, Tw) to keep edits
 * contained within the original line boundary without disturbing surrounding text.
 * When text exceeds micro-justification thresholds, executes dynamic programming paragraph reflow.
 */
object HzMicroTypographySolver {

    // Hermann Zapf imperceptible limits
    const val MIN_TZ = 96.5f
    const val MAX_TZ = 103.5f
    const val MIN_TC = -0.25f // points
    const val MAX_TC = 0.45f  // points
    const val MIN_TW = -0.80f // points
    const val MAX_TW = 1.50f  // points

    suspend fun solve(
        originalLine: PdfTextLineNode,
        newText: String,
        paragraph: PdfParagraphBlock? = null,
        targetWidthPt: Float = originalLine.bounds.width()
    ): MicroTypographyResult = withContext(Dispatchers.Default) {
        val font = originalLine.font
        val fontSize = originalLine.fontSizePt
        val naturalWidth = measureNaturalWidth(newText, font, fontSize)

        val kerningShifts = buildOpticalKerningShifts(newText)

        // Stage 1: Check if line fits within Zapf Hz micro-scaling bounds
        val charCount = max(1, newText.length)
        val spaceCount = newText.count { it == ' ' }

        val widthDiff = targetWidthPt - naturalWidth

        // Test if Tz alone can absorb the difference
        val candidateTz = (targetWidthPt / max(1f, naturalWidth)) * 100f

        if (candidateTz in MIN_TZ..MAX_TZ) {
            val reflow = ReflowedLine(
                lineText = newText,
                bounds = RectF(originalLine.bounds.left, originalLine.bounds.top, originalLine.bounds.left + targetWidthPt, originalLine.bounds.bottom),
                baselineY = originalLine.baselineY,
                horizontalScale = candidateTz,
                charSpacing = 0f,
                wordSpacing = 0f,
                kerningShifts = kerningShifts
            )
            return@withContext MicroTypographyResult(
                fitsInSingleLine = true,
                primaryLineTz = candidateTz,
                primaryLineTc = 0f,
                primaryLineTw = 0f,
                kerningShifts = kerningShifts,
                reflowedLines = listOf(reflow)
            )
        }

        // Test combined Tz + Tc + Tw distribution
        val clampedTz = candidateTz.coerceIn(MIN_TZ, MAX_TZ)
        val scaledWidth = naturalWidth * (clampedTz / 100f)
        val remainingDiff = targetWidthPt - scaledWidth

        var distributedTc = 0f
        var distributedTw = 0f

        if (spaceCount > 0) {
            // Allocate 60% of remaining delta to word spacing, 40% to tracking
            val targetTw = (remainingDiff * 0.6f) / spaceCount
            val targetTc = (remainingDiff * 0.4f) / charCount

            if (targetTw in MIN_TW..MAX_TW && targetTc in MIN_TC..MAX_TC) {
                distributedTw = targetTw
                distributedTc = targetTc

                val reflow = ReflowedLine(
                    lineText = newText,
                    bounds = RectF(originalLine.bounds.left, originalLine.bounds.top, originalLine.bounds.left + targetWidthPt, originalLine.bounds.bottom),
                    baselineY = originalLine.baselineY,
                    horizontalScale = clampedTz,
                    charSpacing = distributedTc,
                    wordSpacing = distributedTw,
                    kerningShifts = kerningShifts
                )
                return@withContext MicroTypographyResult(
                    fitsInSingleLine = true,
                    primaryLineTz = clampedTz,
                    primaryLineTc = distributedTc,
                    primaryLineTw = distributedTw,
                    kerningShifts = kerningShifts,
                    reflowedLines = listOf(reflow)
                )
            }
        }

        // Stage 2: Exceeds micro-justification -> Knuth-Plass Paragraph Reflow
        val reflowed = knuthPlassReflow(
            fullText = newText,
            columnWidthPt = max(targetWidthPt, paragraph?.bounds?.width() ?: targetWidthPt),
            font = font,
            fontSizePt = fontSize,
            startBaselineY = originalLine.baselineY,
            lineSpacingPt = paragraph?.averageLineSpacingPt ?: (fontSize * 1.35f),
            originX = originalLine.bounds.left
        )

        val firstLine = reflowed.firstOrNull()

        MicroTypographyResult(
            fitsInSingleLine = reflowed.size <= 1,
            primaryLineTz = firstLine?.horizontalScale ?: 100f,
            primaryLineTc = firstLine?.charSpacing ?: 0f,
            primaryLineTw = firstLine?.wordSpacing ?: 0f,
            kerningShifts = firstLine?.kerningShifts ?: kerningShifts,
            reflowedLines = reflowed
        )
    }

    private fun measureNaturalWidth(text: String, font: PDFont?, fontSizePt: Float): Float {
        if (text.isEmpty()) return 0f
        var total = 0f
        if (font != null) {
            for (ch in text) {
                try {
                    val code = ch.code
                    val w = font.getWidth(code)
                    total += (w / 1000f) * fontSizePt
                } catch (_: Exception) {
                    total += fontSizePt * 0.55f
                }
            }
        } else {
            val paint = Paint().apply { textSize = fontSizePt }
            total = paint.measureText(text)
        }
        return total
    }

    private fun buildOpticalKerningShifts(text: String): List<Float> {
        if (text.length < 2) return emptyList()
        val shifts = mutableListOf<Float>()

        val kerningPairs = mapOf(
            "AV" to 20f, "AW" to 18f, "VA" to 20f, "WA" to 18f,
            "To" to 15f, "Ta" to 15f, "Te" to 12f, "Tr" to 10f, "Tu" to 10f,
            "Yo" to 22f, "Ya" to 20f, "Ye" to 18f, "We" to 10f, "Wo" to 12f,
            "LT" to 15f, "LV" to 15f, "FA" to 18f, "PA" to 12f,
            "r." to 16f, "r," to 16f, "v," to 14f, "w," to 14f, "y," to 14f,
            "P." to 18f, "T." to 18f, "Y." to 20f, "V." to 20f, "W." to 18f,
            "“A" to 12f, "“T" to 14f, "“Y" to 16f, "”." to 15f, "”," to 15f
        )

        for (i in 0 until text.length - 1) {
            val pair = text.substring(i, i + 2)
            val shift = kerningPairs[pair] ?: 0f
            shifts.add(shift)
        }
        return shifts
    }

    private fun knuthPlassReflow(
        fullText: String,
        columnWidthPt: Float,
        font: PDFont?,
        fontSizePt: Float,
        startBaselineY: Float,
        lineSpacingPt: Float,
        originX: Float
    ): List<ReflowedLine> {
        val words = fullText.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val n = words.size
        val wordWidths = FloatArray(n) { measureNaturalWidth(words[it], font, fontSizePt) }
        val spaceWidth = measureNaturalWidth(" ", font, fontSizePt)

        // Prefix widths for O(1) line width queries
        val prefixWidths = FloatArray(n + 1)
        for (i in 0 until n) {
            prefixWidths[i + 1] = prefixWidths[i] + wordWidths[i]
        }

        // DP state: dp[j] = minimum total demerits to format words 0 until j
        val dp = FloatArray(n + 1) { Float.MAX_VALUE }
        val parent = IntArray(n + 1) { -1 }
        dp[0] = 0f

        for (i in 0 until n) {
            if (dp[i] == Float.MAX_VALUE) continue
            for (j in i + 1..n) {
                val wordsW = prefixWidths[j] - prefixWidths[i]
                val spacesW = (j - 1 - i) * spaceWidth
                val lineNaturalW = wordsW + spacesW
                val diff = columnWidthPt - lineNaturalW

                // Allowable horizontal stretch/shrink ratio (95% to 105%)
                val maxShrink = columnWidthPt * 0.05f
                if (diff < -maxShrink) {
                    // Line is too long; further j will only be even longer
                    break
                }

                // Compute demerits
                val isLastLine = (j == n)
                val demerits: Float = if (isLastLine && diff >= 0) {
                    0f // Last line has zero stretch penalty
                } else {
                    val ratio = (diff / columnWidthPt).coerceIn(-0.05f, 0.50f)
                    val badness = 100f * abs(ratio * ratio * ratio)
                    (10f + badness) * (10f + badness)
                }

                val totalDemerits = dp[i] + demerits
                if (totalDemerits < dp[j]) {
                    dp[j] = totalDemerits
                    parent[j] = i
                }
            }
        }

        // Backtrack to recover globally optimal line breaks
        val lineBreakRanges = mutableListOf<Pair<Int, Int>>()
        var curr = n
        while (curr > 0) {
            val prev = parent[curr]
            if (prev == -1) {
                // Fallback to greedy if no valid DP path was found for extreme token length
                lineBreakRanges.clear()
                var start = 0
                while (start < n) {
                    var end = start + 1
                    var w = wordWidths[start]
                    while (end < n && w + spaceWidth + wordWidths[end] <= columnWidthPt) {
                        w += spaceWidth + wordWidths[end]
                        end++
                    }
                    lineBreakRanges.add(start to end)
                    start = end
                }
                break
            }
            lineBreakRanges.add(0, prev to curr)
            curr = prev
        }

        val lines = mutableListOf<ReflowedLine>()
        var currentBaselineY = startBaselineY

        for ((startIdx, endIdx) in lineBreakRanges) {
            val lineWords = words.subList(startIdx, endIdx)
            val lineText = lineWords.joinToString(" ")
            val naturalW = measureNaturalWidth(lineText, font, fontSizePt)
            val tz = ((columnWidthPt / max(1f, naturalW)) * 100f).coerceIn(95f, 105f)

            lines.add(
                ReflowedLine(
                    lineText = lineText,
                    bounds = RectF(originX, currentBaselineY - fontSizePt, originX + min(columnWidthPt, naturalW), currentBaselineY + (fontSizePt * 0.3f)),
                    baselineY = currentBaselineY,
                    horizontalScale = tz,
                    charSpacing = 0f,
                    wordSpacing = 0f,
                    kerningShifts = buildOpticalKerningShifts(lineText)
                )
            )
            currentBaselineY -= lineSpacingPt
        }

        return lines
    }
}

