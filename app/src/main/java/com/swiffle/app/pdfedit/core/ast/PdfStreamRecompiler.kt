package com.swiffle.app.pdfedit.core.ast

import android.graphics.Color as AndroidColor
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Low-level In-place PDF Content Stream Rewriter & AST Recompiler.
 * Surgically replaces or injects text operators (Tj, TJ, Tz, Tc, Tw, Tf, rg) within BT...ET blocks
 * and re-serializes the page contents into a Flate-compressed stream without document corruption.
 */
object PdfStreamRecompiler {

    data class TextStreamEdit(
        val tokenIndex: Int,
        val targetOperator: String,
        val newText: String,
        val font: PDFont?,
        val fontResourceKey: String,
        val fontSizePt: Float,
        val horizontalScale: Float = 100f,
        val charSpacing: Float = 0f,
        val wordSpacing: Float = 0f,
        val colorRgb: Int = AndroidColor.BLACK,
        val kerningShifts: List<Float> = emptyList()
    )

    /**
     * Applies surgical in-place edits to a page's content stream.
     */
    suspend fun recompilePageStream(
        document: PDDocument,
        pageIndex: Int,
        edits: List<TextStreamEdit>
    ): Boolean = withContext(Dispatchers.Default) {
        if (edits.isEmpty()) return@withContext true

        val page = document.getPage(pageIndex)
        val parser = try {
            PDFStreamParser(page)
        } catch (e: Exception) {
            return@withContext false
        }

        parser.parse()
        val tokens = parser.tokens.toMutableList()

        // Sort edits in descending order of tokenIndex so replacements don't invalidate lower indices
        val sortedEdits = edits.sortedByDescending { it.tokenIndex }

        for (edit in sortedEdits) {
            if (edit.tokenIndex < 0 || edit.tokenIndex >= tokens.size) {
                // If token index is invalid, append an isolated BT...ET block
                appendVectorTextBlock(tokens, edit)
                continue
            }

            val currentToken = tokens[edit.tokenIndex]
            if (currentToken is Operator && (currentToken.name == "Tj" || currentToken.name == "TJ" || currentToken.name == "'" || currentToken.name == "\"")) {
                replaceTextOperator(tokens, edit.tokenIndex, edit)
            } else {
                appendVectorTextBlock(tokens, edit)
            }
        }

        // Write tokens back into a compressed PDStream
        return@withContext try {
            val newStream = PDStream(document)
            val outputStream: OutputStream = newStream.createOutputStream(COSName.FLATE_DECODE)
            val writer = ContentStreamWriter(outputStream)
            writer.writeTokens(tokens)
            outputStream.close()

            page.setContents(newStream)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun replaceTextOperator(
        tokens: MutableList<Any>,
        opIndex: Int,
        edit: TextStreamEdit
    ) {
        val font = edit.font
        // Robust Multi-Encoding Handler (CID Type0 Identity-H, WinAnsi, UTF-16BE, ISO-8859-1)
        val encodedBytes = try {
            font?.encode(edit.newText) ?: run {
                // If standard single-byte fails, check for multi-byte unicode stream
                if (edit.newText.any { it.code > 127 }) {
                    edit.newText.toByteArray(Charsets.UTF_16BE)
                } else {
                    edit.newText.toByteArray(Charsets.ISO_8859_1)
                }
            }
        } catch (_: Exception) {
            if (edit.newText.any { it.code > 127 }) {
                edit.newText.toByteArray(Charsets.UTF_16BE)
            } else {
                edit.newText.toByteArray(Charsets.ISO_8859_1)
            }
        }

        val r = AndroidColor.red(edit.colorRgb) / 255f
        val g = AndroidColor.green(edit.colorRgb) / 255f
        val b = AndroidColor.blue(edit.colorRgb) / 255f

        // Build injected prefix operators for micro-typography: rg, Tz, Tc, Tw
        val prefixTokens = mutableListOf<Any>()
        prefixTokens.add(COSFloat(r))
        prefixTokens.add(COSFloat(g))
        prefixTokens.add(COSFloat(b))
        prefixTokens.add(Operator.getOperator("rg"))

        prefixTokens.add(COSFloat(edit.horizontalScale))
        prefixTokens.add(Operator.getOperator("Tz"))

        prefixTokens.add(COSFloat(edit.charSpacing))
        prefixTokens.add(Operator.getOperator("Tc"))

        prefixTokens.add(COSFloat(edit.wordSpacing))
        prefixTokens.add(Operator.getOperator("Tw"))

        // Build the replacement operand and operator
        val newOperands = mutableListOf<Any>()
        if (edit.kerningShifts.isNotEmpty()) {
            val cosArray = COSArray()
            val text = edit.newText
            for (i in text.indices) {
                val singleCharBytes = try {
                    font?.encode(text.substring(i, i + 1)) ?: text.substring(i, i + 1).toByteArray(Charsets.ISO_8859_1)
                } catch (_: Exception) {
                    text.substring(i, i + 1).toByteArray(Charsets.ISO_8859_1)
                }
                cosArray.add(COSString(singleCharBytes))
                if (i < edit.kerningShifts.size) {
                    val shift = edit.kerningShifts[i]
                    if (shift != 0f) {
                        cosArray.add(COSFloat(shift))
                    }
                }
            }
            newOperands.add(cosArray)
            newOperands.add(Operator.getOperator("TJ"))
        } else {
            newOperands.add(COSString(encodedBytes))
            newOperands.add(Operator.getOperator("Tj"))
        }

        // Determine operand count based on target operator syntax (" takes 3 operands: [aw, ac, string], others take 1)
        val opToken = tokens.getOrNull(opIndex) as? Operator
        val opName = opToken?.name ?: edit.targetOperator
        val operandCount = when (opName) {
            "\"" -> 3
            else -> 1
        }
        val operandStart = (opIndex - operandCount).coerceAtLeast(0)

        // Remove old operator and all its associated operands safely
        val itemsToRemove = (opIndex - operandStart) + 1
        repeat(itemsToRemove) {
            if (operandStart < tokens.size) {
                tokens.removeAt(operandStart)
            }
        }

        // Insert prefix tokens + new operands at operandStart
        val allInjected = prefixTokens + newOperands
        tokens.addAll(operandStart, allInjected)
    }

    private fun appendVectorTextBlock(
        tokens: MutableList<Any>,
        edit: TextStreamEdit
    ) {
        val r = AndroidColor.red(edit.colorRgb) / 255f
        val g = AndroidColor.green(edit.colorRgb) / 255f
        val b = AndroidColor.blue(edit.colorRgb) / 255f

        val font = edit.font
        val encodedBytes = try {
            font?.encode(edit.newText) ?: edit.newText.toByteArray(Charsets.ISO_8859_1)
        } catch (_: Exception) {
            edit.newText.toByteArray(Charsets.ISO_8859_1)
        }

        val block = mutableListOf<Any>()
        block.add(Operator.getOperator("q")) // Save graphics state
        block.add(Operator.getOperator("BT")) // Begin Text

        // Color
        block.add(COSFloat(r))
        block.add(COSFloat(g))
        block.add(COSFloat(b))
        block.add(Operator.getOperator("rg"))

        // Font and size
        val rawResKey = edit.fontResourceKey.removePrefix("/")
        block.add(COSName.getPDFName(rawResKey))
        block.add(COSFloat(edit.fontSizePt))
        block.add(Operator.getOperator("Tf"))

        // Micro-typography
        block.add(COSFloat(edit.horizontalScale))
        block.add(Operator.getOperator("Tz"))
        block.add(COSFloat(edit.charSpacing))
        block.add(Operator.getOperator("Tc"))
        block.add(COSFloat(edit.wordSpacing))
        block.add(Operator.getOperator("Tw"))

        // String
        block.add(COSString(encodedBytes))
        block.add(Operator.getOperator("Tj"))

        block.add(Operator.getOperator("ET")) // End Text
        block.add(Operator.getOperator("Q")) // Restore graphics state

        tokens.addAll(block)
    }
}

