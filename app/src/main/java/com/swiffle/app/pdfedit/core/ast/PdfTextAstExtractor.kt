package com.swiffle.app.pdfedit.core.ast

import android.graphics.Color as AndroidColor
import android.graphics.RectF
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Hierarchical DOM representation of a PDF Page for lossless typographic inspection and modification.
 */
data class PdfPageDom(
    val pageIndex: Int,
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val rotationDegrees: Int,
    val paragraphs: List<PdfParagraphBlock>,
    val allLines: List<PdfTextLineNode>,
    val allWords: List<PdfWordNode>,
    val rawTokens: List<Any>
)

data class PdfParagraphBlock(
    val id: String,
    val bounds: RectF,
    val lines: List<PdfTextLineNode>,
    val averageLineSpacingPt: Float
)

data class PdfTextLineNode(
    val id: String,
    val lineText: String,
    val bounds: RectF,
    val baselineY: Float,
    val fontName: String,
    val font: PDFont?,
    val fontResourceKey: String,
    val fontSizePt: Float,
    val horizontalScale: Float, // Tz (100 = 100%)
    val charSpacing: Float,     // Tc
    val wordSpacing: Float,     // Tw
    val colorRgb: Int,
    val words: List<PdfWordNode>,
    val tokenStartIndex: Int,
    val tokenEndIndex: Int
)

data class PdfWordNode(
    val id: String,
    val text: String,
    val bounds: RectF,
    val baselineY: Float,
    val fontName: String,
    val font: PDFont?,
    val fontResourceKey: String,
    val fontSizePt: Float,
    val textMatrix: Matrix?,
    val horizontalScale: Float,
    val charSpacing: Float,
    val wordSpacing: Float,
    val colorRgb: Int,
    val tokenStartIndex: Int,
    val tokenEndIndex: Int,
    val glyphs: List<PdfGlyphRun>
)

data class PdfGlyphRun(
    val character: String,
    val bounds: RectF,
    val font: PDFont?,
    val fontSizePt: Float,
    val unscaledWidthPt: Float
)

/**
 * Advanced PDF Content Stream AST Extractor & DOM Builder.
 * Combines low-level PDFStreamParser tokenization with PDFTextStripper typographic geometry.
 */
class PdfTextAstExtractor : PDFTextStripper() {

    private val collectedGlyphs = mutableListOf<TextPosition>()

    init {
        sortByPosition = true
    }

    override fun processTextPosition(text: TextPosition) {
        collectedGlyphs.add(text)
        super.processTextPosition(text)
    }

    companion object {

        suspend fun extractPageDom(
            document: PDDocument,
            pageIndex: Int
        ): PdfPageDom = withContext(Dispatchers.Default) {
            val page = document.getPage(pageIndex)
            val cropBox = page.cropBox ?: page.mediaBox
            val pageWidth = cropBox.width
            val pageHeight = cropBox.height
            val rotation = page.rotation

            // Step 1: Parse raw binary content tokens
            val rawTokens = try {
                val parser = PDFStreamParser(page)
                parser.parse()
                parser.tokens
            } catch (e: Throwable) {
                emptyList<Any>()
            }

            // Step 2: Strip text positions with exact typographic geometry
            val extractor = PdfTextAstExtractor()
            extractor.startPage = pageIndex + 1
            extractor.endPage = pageIndex + 1
            extractor.output = OutputStreamWriter(ByteArrayOutputStream())
            extractor.getText(document)

            val textPositions = extractor.collectedGlyphs

            // Step 3: Correlate stream tokens and locate Tf, Tz, Tc, Tw, rg/RG operators
            val operatorMap = parseStreamOperatorMap(rawTokens)

            // Step 4: Group glyphs into Words and Lines
            val lines = groupGlyphsIntoLines(textPositions, pageHeight, operatorMap)
            val allWords = lines.flatMap { it.words }

            // Step 5: Group Lines into Paragraph Blocks
            val paragraphs = groupLinesIntoParagraphs(lines)

            PdfPageDom(
                pageIndex = pageIndex,
                pageWidthPt = pageWidth,
                pageHeightPt = pageHeight,
                rotationDegrees = rotation,
                paragraphs = paragraphs,
                allLines = lines,
                allWords = allWords,
                rawTokens = rawTokens
            )
        }

        private fun parseStreamOperatorMap(tokens: List<Any>): List<StreamTextOp> {
            val ops = mutableListOf<StreamTextOp>()
            var curFontResKey = "/F1"
            var curFontSize = 12f
            var curTz = 100f
            var curTc = 0f
            var curTw = 0f
            var curRgb = AndroidColor.BLACK

            for (i in tokens.indices) {
                val token = tokens[i]
                if (token is Operator) {
                    val opName = token.name
                    when (opName) {
                        "Tf" -> {
                            // Operands: [fontName, fontSize]
                            if (i >= 2) {
                                val fontNameToken = tokens[i - 2]
                                val fontSizeToken = tokens[i - 1]
                                if (fontNameToken is COSName) {
                                    curFontResKey = "/" + fontNameToken.name
                                }
                                if (fontSizeToken is COSNumber) {
                                    curFontSize = fontSizeToken.floatValue()
                                }
                            }
                        }
                        "Tz" -> {
                            if (i >= 1 && tokens[i - 1] is COSNumber) {
                                curTz = (tokens[i - 1] as COSNumber).floatValue()
                            }
                        }
                        "Tc" -> {
                            if (i >= 1 && tokens[i - 1] is COSNumber) {
                                curTc = (tokens[i - 1] as COSNumber).floatValue()
                            }
                        }
                        "Tw" -> {
                            if (i >= 1 && tokens[i - 1] is COSNumber) {
                                curTw = (tokens[i - 1] as COSNumber).floatValue()
                            }
                        }
                        "rg", "g", "k" -> {
                            curRgb = parseColorRgb(tokens, i, opName)
                        }
                        "Tj", "TJ", "'", "\"" -> {
                            val textHint = if (i >= 1) {
                                when (val prev = tokens[i - 1]) {
                                    is COSString -> prev.string ?: ""
                                    is COSArray -> prev.filterIsInstance<COSString>().joinToString("") { it.string ?: "" }
                                    else -> ""
                                }
                            } else ""

                            ops.add(
                                StreamTextOp(
                                    tokenIndex = i,
                                    operatorName = opName,
                                    fontResourceKey = curFontResKey,
                                    fontSizePt = curFontSize,
                                    horizontalScale = curTz,
                                    charSpacing = curTc,
                                    wordSpacing = curTw,
                                    colorRgb = curRgb,
                                    textHint = textHint
                                )
                            )
                        }
                    }
                }
            }
            return ops
        }

        private fun parseColorRgb(tokens: List<Any>, opIndex: Int, opName: String): Int {
            try {
                when (opName) {
                    "rg" -> {
                        if (opIndex >= 3) {
                            val r = ((tokens[opIndex - 3] as? COSNumber)?.floatValue() ?: 0f) * 255f
                            val g = ((tokens[opIndex - 2] as? COSNumber)?.floatValue() ?: 0f) * 255f
                            val b = ((tokens[opIndex - 1] as? COSNumber)?.floatValue() ?: 0f) * 255f
                            return AndroidColor.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
                        }
                    }
                    "g" -> {
                        if (opIndex >= 1) {
                            val v = ((tokens[opIndex - 1] as? COSNumber)?.floatValue() ?: 0f) * 255f
                            val gray = v.toInt().coerceIn(0, 255)
                            return AndroidColor.rgb(gray, gray, gray)
                        }
                    }
                    "k" -> {
                        if (opIndex >= 4) {
                            val c = (tokens[opIndex - 4] as? COSNumber)?.floatValue() ?: 0f
                            val m = (tokens[opIndex - 3] as? COSNumber)?.floatValue() ?: 0f
                            val y = (tokens[opIndex - 2] as? COSNumber)?.floatValue() ?: 0f
                            val k = (tokens[opIndex - 1] as? COSNumber)?.floatValue() ?: 0f
                            val r = (255f * (1f - c) * (1f - k)).toInt().coerceIn(0, 255)
                            val g = (255f * (1f - m) * (1f - k)).toInt().coerceIn(0, 255)
                            val b = (255f * (1f - y) * (1f - k)).toInt().coerceIn(0, 255)
                            return AndroidColor.rgb(r, g, b)
                        }
                    }
                }
            } catch (_: Exception) {}
            return AndroidColor.BLACK
        }

        private fun groupGlyphsIntoLines(
            glyphs: List<TextPosition>,
            pageHeightPt: Float,
            operatorMap: List<StreamTextOp>
        ): List<PdfTextLineNode> {
            if (glyphs.isEmpty()) return emptyList()

            val lines = mutableListOf<PdfTextLineNode>()
            var currentLineGlyphs = mutableListOf<TextPosition>()
            var lastY = glyphs.first().yDirAdj
            var lastHeight = glyphs.first().heightDir
            val usedTokenIndices = mutableSetOf<Int>()

            for (glyph in glyphs) {
                val currentY = glyph.yDirAdj
                val currentHeight = glyph.heightDir

                // Glyphs on approximately the same baseline belong to the same line
                val deltaY = abs(currentY - lastY)
                val tolerance = max(currentHeight, lastHeight) * 0.45f

                if (deltaY > tolerance && currentLineGlyphs.isNotEmpty()) {
                    lines.add(buildLineNode(currentLineGlyphs, pageHeightPt, operatorMap, lines.size, usedTokenIndices))
                    currentLineGlyphs = mutableListOf()
                    lastY = currentY
                    lastHeight = currentHeight
                }
                currentLineGlyphs.add(glyph)
            }

            if (currentLineGlyphs.isNotEmpty()) {
                lines.add(buildLineNode(currentLineGlyphs, pageHeightPt, operatorMap, lines.size, usedTokenIndices))
            }

            return lines
        }

        private fun buildLineNode(
            lineGlyphs: List<TextPosition>,
            pageHeightPt: Float,
            operatorMap: List<StreamTextOp>,
            lineIndex: Int,
            usedTokenIndices: MutableSet<Int>
        ): PdfTextLineNode {
            val words = mutableListOf<PdfWordNode>()
            var currentWordGlyphs = mutableListOf<TextPosition>()

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            val lineSb = StringBuilder()

            for (i in lineGlyphs.indices) {
                val g = lineGlyphs[i]
                lineSb.append(g.unicode)

                val gx = g.xDirAdj
                val gyTop = g.yDirAdj
                val gw = g.widthDirAdj
                val gh = g.heightDir
                val gyBottom = gyTop + gh

                // Convert to PDF coordinates (bottom-left origin)
                val pdfYBottom = pageHeightPt - gyBottom
                val pdfYTop = pageHeightPt - gyTop

                minX = min(minX, gx)
                maxX = max(maxX, gx + gw)
                minY = min(minY, pdfYBottom)
                maxY = max(maxY, pdfYTop)

                if (g.unicode == " " || g.unicode == "\t") {
                    if (currentWordGlyphs.isNotEmpty()) {
                        words.add(buildWordNode(currentWordGlyphs, pageHeightPt, operatorMap, "${lineIndex}_${words.size}"))
                        currentWordGlyphs = mutableListOf()
                    }
                } else {
                    currentWordGlyphs.add(g)
                }
            }

            if (currentWordGlyphs.isNotEmpty()) {
                words.add(buildWordNode(currentWordGlyphs, pageHeightPt, operatorMap, "${lineIndex}_${words.size}"))
            }

            val primaryGlyph = lineGlyphs.firstOrNull()
            val font = primaryGlyph?.font
            val fontName = font?.name ?: "Helvetica"
            val fontSize = primaryGlyph?.fontSizeInPt ?: 12f
            val baselineY = pageHeightPt - (primaryGlyph?.yDirAdj ?: 0f)

            val lineTextTrimmed = lineSb.toString().trim()

            // Find closest content stream operator with token disambiguation
            val unusedOps = operatorMap.filter { it.tokenIndex !in usedTokenIndices }
            val op = unusedOps.firstOrNull { opItem ->
                opItem.textHint.isNotBlank() && (lineTextTrimmed.contains(opItem.textHint, ignoreCase = true) || opItem.textHint.contains(lineTextTrimmed.take(6), ignoreCase = true))
            } ?: unusedOps.firstOrNull { abs(it.fontSizePt - fontSize) < 0.5f }
              ?: unusedOps.firstOrNull()
              ?: operatorMap.getOrNull(lineIndex)
              ?: operatorMap.firstOrNull()

            if (op != null) {
                usedTokenIndices.add(op.tokenIndex)
            }

            return PdfTextLineNode(
                id = "line_$lineIndex",
                lineText = lineTextTrimmed,
                bounds = RectF(minX, minY, maxX, maxY),
                baselineY = baselineY,
                fontName = fontName,
                font = font,
                fontResourceKey = op?.fontResourceKey ?: "/F1",
                fontSizePt = fontSize,
                horizontalScale = op?.horizontalScale ?: 100f,
                charSpacing = op?.charSpacing ?: 0f,
                wordSpacing = op?.wordSpacing ?: 0f,
                colorRgb = op?.colorRgb ?: AndroidColor.BLACK,
                words = words,
                tokenStartIndex = op?.tokenIndex ?: -1,
                tokenEndIndex = op?.tokenIndex ?: -1
            )
        }

        private fun buildWordNode(
            wordGlyphs: List<TextPosition>,
            pageHeightPt: Float,
            operatorMap: List<StreamTextOp>,
            wordId: String
        ): PdfWordNode {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            val wordSb = StringBuilder()
            val glyphRuns = mutableListOf<PdfGlyphRun>()

            for (g in wordGlyphs) {
                wordSb.append(g.unicode)
                val gx = g.xDirAdj
                val gyTop = g.yDirAdj
                val gw = g.widthDirAdj
                val gh = g.heightDir
                val gyBottom = gyTop + gh

                val pdfYBottom = pageHeightPt - gyBottom
                val pdfYTop = pageHeightPt - gyTop

                minX = min(minX, gx)
                maxX = max(maxX, gx + gw)
                minY = min(minY, pdfYBottom)
                maxY = max(maxY, pdfYTop)

                glyphRuns.add(
                    PdfGlyphRun(
                        character = g.unicode,
                        bounds = RectF(gx, pdfYBottom, gx + gw, pdfYTop),
                        font = g.font,
                        fontSizePt = g.fontSizeInPt,
                        unscaledWidthPt = g.width
                    )
                )
            }

            val first = wordGlyphs.first()
            val font = first.font
            val fontName = font?.name ?: "Helvetica"
            val fontSize = first.fontSizeInPt
            val baselineY = pageHeightPt - first.yDirAdj
            val op = operatorMap.firstOrNull { it.fontSizePt == fontSize } ?: operatorMap.firstOrNull()

            return PdfWordNode(
                id = "word_$wordId",
                text = wordSb.toString(),
                bounds = RectF(minX, minY, maxX, maxY),
                baselineY = baselineY,
                fontName = fontName,
                font = font,
                fontResourceKey = op?.fontResourceKey ?: "/F1",
                fontSizePt = fontSize,
                textMatrix = first.textMatrix,
                horizontalScale = op?.horizontalScale ?: 100f,
                charSpacing = op?.charSpacing ?: 0f,
                wordSpacing = op?.wordSpacing ?: 0f,
                colorRgb = op?.colorRgb ?: AndroidColor.BLACK,
                tokenStartIndex = op?.tokenIndex ?: -1,
                tokenEndIndex = op?.tokenIndex ?: -1,
                glyphs = glyphRuns
            )
        }

        private fun groupLinesIntoParagraphs(lines: List<PdfTextLineNode>): List<PdfParagraphBlock> {
            if (lines.isEmpty()) return emptyList()

            val paragraphs = mutableListOf<PdfParagraphBlock>()
            var currentLines = mutableListOf<PdfTextLineNode>()

            for (i in lines.indices) {
                val line = lines[i]
                if (currentLines.isEmpty()) {
                    currentLines.add(line)
                    continue
                }

                val prevLine = currentLines.last()
                val lineGap = abs(prevLine.bounds.bottom - line.bounds.top)
                val expectedLineHeight = prevLine.fontSizePt * 1.6f

                // If gap is more than 2x font size, it's a new paragraph block
                if (lineGap > expectedLineHeight) {
                    paragraphs.add(buildParagraphBlock(currentLines, paragraphs.size))
                    currentLines = mutableListOf(line)
                } else {
                    currentLines.add(line)
                }
            }

            if (currentLines.isNotEmpty()) {
                paragraphs.add(buildParagraphBlock(currentLines, paragraphs.size))
            }

            return paragraphs
        }

        private fun buildParagraphBlock(lines: List<PdfTextLineNode>, index: Int): PdfParagraphBlock {
            val minX = lines.minOf { it.bounds.left }
            val minY = lines.minOf { it.bounds.top }
            val maxX = lines.maxOf { it.bounds.right }
            val maxY = lines.maxOf { it.bounds.bottom }

            var totalGap = 0f
            var gapCount = 0
            for (i in 0 until lines.size - 1) {
                totalGap += abs(lines[i].bounds.bottom - lines[i + 1].bounds.top)
                gapCount++
            }
            val avgSpacing = if (gapCount > 0) totalGap / gapCount else lines.first().fontSizePt * 1.2f

            return PdfParagraphBlock(
                id = "paragraph_$index",
                bounds = RectF(minX, minY, maxX, maxY),
                lines = lines,
                averageLineSpacingPt = avgSpacing
            )
        }
    }
}

private data class StreamTextOp(
    val tokenIndex: Int,
    val operatorName: String,
    val fontResourceKey: String,
    val fontSizePt: Float,
    val horizontalScale: Float,
    val charSpacing: Float,
    val wordSpacing: Float,
    val colorRgb: Int,
    val textHint: String = ""
)

