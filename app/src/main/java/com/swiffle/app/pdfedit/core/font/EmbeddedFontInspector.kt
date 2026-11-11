package com.swiffle.app.pdfedit.core.font

import android.content.Context
import android.graphics.Typeface
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDFontDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracted Font DNA capturing metric attributes down to 0.01pt precision.
 */
data class FontDna(
    val fontName: String,
    val isTrueType: Boolean,
    val isOpenType: Boolean,
    val isType1: Boolean,
    val isSerif: Boolean,
    val isMonospace: Boolean,
    val isItalic: Boolean,
    val isBold: Boolean,
    val ascent: Float,
    val descent: Float,
    val capHeight: Float,
    val xHeight: Float,
    val italicAngle: Float,
    val stemV: Float,
    val stemH: Float,
    val estimatedWeight: Float, // 100f..900f
    val estimatedWidthPercent: Float, // 75f..125f
    val extractedTypefaceFile: File?
)

data class MissingCharactersReport(
    val isAllEncodable: Boolean,
    val missingChars: Set<Char>,
    val presentChars: Set<Char>
)

/**
 * Deep Binary Font Inspector and Metric Extractor.
 */
object EmbeddedFontInspector {

    private val fontFileCache = ConcurrentHashMap<String, File>()

    suspend fun inspectFont(
        context: Context,
        font: PDFont?
    ): FontDna = withContext(Dispatchers.IO) {
        if (font == null) {
            return@withContext createFallbackDna("Helvetica")
        }

        val descriptor: PDFontDescriptor? = font.fontDescriptor
        val rawName = font.name ?: "UnknownFont"
        val cleanName = cleanFontSubsetPrefix(rawName)

        val flags = descriptor?.flags ?: 0
        val isFixedPitch = (flags and 1) != 0
        val isSerif = (flags and 2) != 0
        val isItalicFlag = (flags and 64) != 0
        val isForceBold = (flags and 262144) != 0

        val ascent = descriptor?.ascent ?: 750f
        val descent = descriptor?.descent ?: -250f
        val capHeight = descriptor?.capHeight ?: 700f
        val xHeight = descriptor?.xHeight ?: 500f
        val italicAngle = descriptor?.italicAngle ?: 0f
        val stemV = descriptor?.stemV ?: 80f
        val stemH = descriptor?.stemH ?: 0f

        // Continuous weight estimation from StemV (Standard normal stem ~ 70-85, Bold ~ 130-160, Heavy ~ 180+)
        val calculatedWeight = when {
            descriptor?.fontWeight != null && descriptor.fontWeight > 0 -> descriptor.fontWeight
            stemV > 0f -> {
                val weightScore = 100f + ((stemV - 40f) / (180f - 40f)) * 800f
                weightScore.coerceIn(100f, 900f)
            }
            isForceBold || cleanName.contains("Bold", ignoreCase = true) -> 700f
            cleanName.contains("Light", ignoreCase = true) -> 300f
            cleanName.contains("Thin", ignoreCase = true) -> 100f
            cleanName.contains("Black", ignoreCase = true) || cleanName.contains("Heavy", ignoreCase = true) -> 900f
            else -> 400f
        }

        // Extract raw embedded font binary stream to internal app cache
        var extractedFile: File? = fontFileCache[rawName]
        if (extractedFile == null || !extractedFile.exists()) {
            extractedFile = extractBinaryStream(context, rawName, descriptor)
            if (extractedFile != null) {
                fontFileCache[rawName] = extractedFile
            }
        }

        val isTTF = descriptor?.fontFile2 != null
        val isOTF = descriptor?.fontFile3 != null
        val isType1 = descriptor?.fontFile != null

        FontDna(
            fontName = cleanName,
            isTrueType = isTTF,
            isOpenType = isOTF,
            isType1 = isType1,
            isSerif = isSerif || cleanName.contains("Times", ignoreCase = true) || cleanName.contains("Serif", ignoreCase = true),
            isMonospace = isFixedPitch || cleanName.contains("Mono", ignoreCase = true) || cleanName.contains("Courier", ignoreCase = true),
            isItalic = isItalicFlag || italicAngle != 0f || cleanName.contains("Italic", ignoreCase = true) || cleanName.contains("Oblique", ignoreCase = true),
            isBold = isForceBold || calculatedWeight >= 650f,
            ascent = ascent,
            descent = descent,
            capHeight = capHeight,
            xHeight = xHeight,
            italicAngle = italicAngle,
            stemV = stemV,
            stemH = stemH,
            estimatedWeight = calculatedWeight,
            estimatedWidthPercent = 100f,
            extractedTypefaceFile = extractedFile
        )
    }

    /**
     * Strips 6-character random subset tags (e.g., 'ABCDEF+TimesNewRoman' -> 'TimesNewRoman').
     */
    fun cleanFontSubsetPrefix(fontName: String): String {
        val plusIdx = fontName.indexOf('+')
        return if (plusIdx in 1..7) {
            fontName.substring(plusIdx + 1)
        } else {
            fontName
        }
    }

    /**
     * Checks if all characters in the candidate string can be natively encoded by the font.
     */
    fun checkCharacterEncoding(font: PDFont?, text: String): MissingCharactersReport {
        if (font == null) {
            return MissingCharactersReport(false, text.toSet(), emptySet())
        }

        val missing = mutableSetOf<Char>()
        val present = mutableSetOf<Char>()

        for (ch in text) {
            try {
                val encoded = font.encode(ch.toString())
                if (encoded != null && encoded.isNotEmpty()) {
                    present.add(ch)
                } else {
                    missing.add(ch)
                }
            } catch (_: Exception) {
                missing.add(ch)
            }
        }

        return MissingCharactersReport(
            isAllEncodable = missing.isEmpty(),
            missingChars = missing,
            presentChars = present
        )
    }

    private fun extractBinaryStream(
        context: Context,
        rawName: String,
        descriptor: PDFontDescriptor?
    ): File? {
        if (descriptor == null) return null

        val pds = descriptor.fontFile2 ?: descriptor.fontFile3 ?: descriptor.fontFile ?: return null
        return try {
            val extension = when {
                descriptor.fontFile2 != null -> ".ttf"
                descriptor.fontFile3 != null -> ".otf"
                else -> ".pfb"
            }

            val hash = md5(rawName)
            val cacheDir = File(context.cacheDir, "pdf_extracted_fonts")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val targetFile = File(cacheDir, "font_${hash}$extension")
            if (targetFile.exists() && targetFile.length() > 0) {
                return targetFile
            }

            pds.createInputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.length() > 0) targetFile else null
        } catch (_: Exception) {
            null
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun createFallbackDna(name: String): FontDna {
        return FontDna(
            fontName = name,
            isTrueType = false,
            isOpenType = false,
            isType1 = false,
            isSerif = false,
            isMonospace = false,
            isItalic = false,
            isBold = false,
            ascent = 750f,
            descent = -250f,
            capHeight = 700f,
            xHeight = 500f,
            italicAngle = 0f,
            stemV = 80f,
            stemH = 0f,
            estimatedWeight = 400f,
            estimatedWidthPercent = 100f,
            extractedTypefaceFile = null
        )
    }
}

