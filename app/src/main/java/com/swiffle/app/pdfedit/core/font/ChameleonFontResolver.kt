package com.swiffle.app.pdfedit.core.font

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class ResolvedChameleonFont(
    val fontFamily: FontFamily,
    val androidTypeface: Typeface,
    val fontWeight: FontWeight,
    val fontStyle: FontStyle,
    val fontVariationSettings: String,
    val isExactEmbeddedMatch: Boolean,
    val fontDna: FontDna
)

/**
 * Universal Chameleon Font Resolver:
 * Resolves identical typography using the embedded font binary when possible,
 * or dynamically synthesizes parametric variable font metrics.
 */
object ChameleonFontResolver {

    suspend fun resolveFont(
        context: Context,
        pdfFont: PDFont?,
        targetText: String = ""
    ): ResolvedChameleonFont = withContext(Dispatchers.IO) {
        val dna = EmbeddedFontInspector.inspectFont(context, pdfFont)
        val encodingReport = if (targetText.isNotEmpty()) {
            EmbeddedFontInspector.checkCharacterEncoding(pdfFont, targetText)
        } else {
            MissingCharactersReport(true, emptySet(), emptySet())
        }

        // Case 1: Exact embedded font file exists and can encode all requested characters
        if (dna.extractedTypefaceFile != null && encodingReport.isAllEncodable) {
            try {
                val tf = Typeface.createFromFile(dna.extractedTypefaceFile)
                if (tf != null) {
                    val composeFamily = FontFamily(tf)
                    val style = if (dna.isItalic) FontStyle.Italic else FontStyle.Normal
                    val weight = FontWeight(dna.estimatedWeight.roundToInt().coerceIn(100, 900))

                    return@withContext ResolvedChameleonFont(
                        fontFamily = composeFamily,
                        androidTypeface = tf,
                        fontWeight = weight,
                        fontStyle = style,
                        fontVariationSettings = "'wght' ${dna.estimatedWeight}",
                        isExactEmbeddedMatch = true,
                        fontDna = dna
                    )
                }
            } catch (_: Exception) {}
        }

        // Case 2: Subset font is missing characters or no raw binary stream could be extracted.
        // Fall back to parametric variable typeface synthesis.
        val baseFamilyName = when {
            dna.isMonospace -> Typeface.MONOSPACE
            dna.isSerif -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }

        val weightInt = dna.estimatedWeight.roundToInt().coerceIn(100, 900)
        val isItalic = dna.isItalic || dna.italicAngle != 0f

        val androidTf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(baseFamilyName, weightInt, isItalic)
        } else {
            val styleFlag = when {
                weightInt >= 700 && isItalic -> Typeface.BOLD_ITALIC
                weightInt >= 700 -> Typeface.BOLD
                isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(baseFamilyName, styleFlag)
        }

        val composeFamily = when {
            dna.isMonospace -> FontFamily.Monospace
            dna.isSerif -> FontFamily.Serif
            else -> FontFamily.SansSerif
        }

        val variationSettings = "'wght' ${dna.estimatedWeight}, 'wdth' ${dna.estimatedWidthPercent}, 'slnt' ${dna.italicAngle}"

        ResolvedChameleonFont(
            fontFamily = composeFamily,
            androidTypeface = androidTf,
            fontWeight = FontWeight(weightInt),
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
            fontVariationSettings = variationSettings,
            isExactEmbeddedMatch = false,
            fontDna = dna
        )
    }
}

