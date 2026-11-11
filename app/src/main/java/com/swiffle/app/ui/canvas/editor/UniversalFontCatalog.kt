package com.swiffle.app.ui.canvas.editor

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.swiffle.app.R

/**
 * Universal Production-Grade Font Catalog:
 * 
 * Provides global coverage across all primary typographic classifications:
 * - Sans-Serif (Modern, Corporate, Neutral, Clean)
 * - Serif (Legal, Publications, Academic, Formal Contracts)
 * - Monospace (Technical, Coding, Forms, Tabular Data)
 * - Handwriting / Script (Legal Signatures, Sign-offs, Cursive Annotations)
 * 
 * Supports dynamic font thickness scaling from 100 (Thin) to 900 (Black)
 * and intelligent "Auto-Match Source Font" heuristics.
 */
object UniversalFontCatalog {

    val GoogleFontsProvider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )

    data class FontEntry(
        val displayName: String,
        val technicalName: String,
        val category: FontCategory,
        val composeFontFamily: FontFamily
    )

    enum class FontCategory {
        SERIF,
        SANS_SERIF,
        MONOSPACE,
        HANDWRITING,
        SYSTEM
    }

    private fun createGoogleFontFamily(name: String): FontFamily {
        val googleFont = GoogleFont(name)
        return FontFamily(
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W100),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W200),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W300),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W400),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W500),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W600),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W700),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W800),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W900),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W400, style = FontStyle.Italic),
            Font(googleFont = googleFont, fontProvider = GoogleFontsProvider, weight = FontWeight.W700, style = FontStyle.Italic)
        )
    }

    // Comprehensive catalog across all document typographies
    val allFonts: List<FontEntry> by lazy {
        listOf(
            // --- SERIF (Contracts, Books, Formal Letters) ---
            FontEntry("Times New Roman", "Times New Roman", FontCategory.SERIF, FontFamily.Serif),
            FontEntry("Georgia", "Georgia", FontCategory.SERIF, createGoogleFontFamily("Merriweather")),
            FontEntry("Merriweather", "Merriweather", FontCategory.SERIF, createGoogleFontFamily("Merriweather")),
            FontEntry("Garamond (EB Garamond)", "EB Garamond", FontCategory.SERIF, createGoogleFontFamily("EB Garamond")),
            FontEntry("Libre Baskerville", "Libre Baskerville", FontCategory.SERIF, createGoogleFontFamily("Libre Baskerville")),
            FontEntry("Playfair Display", "Playfair Display", FontCategory.SERIF, createGoogleFontFamily("Playfair Display")),
            FontEntry("Lora", "Lora", FontCategory.SERIF, createGoogleFontFamily("Lora")),
            FontEntry("PT Serif", "PT Serif", FontCategory.SERIF, createGoogleFontFamily("PT Serif")),
            FontEntry("Cinzel", "Cinzel", FontCategory.SERIF, createGoogleFontFamily("Cinzel")),

            // --- SANS-SERIF (Modern, Invoices, Receipts, Statements) ---
            FontEntry("Roboto", "Roboto", FontCategory.SANS_SERIF, createGoogleFontFamily("Roboto")),
            FontEntry("Arial / Helvetica", "Arial", FontCategory.SANS_SERIF, FontFamily.SansSerif),
            FontEntry("Inter", "Inter", FontCategory.SANS_SERIF, createGoogleFontFamily("Inter")),
            FontEntry("Open Sans", "Open Sans", FontCategory.SANS_SERIF, createGoogleFontFamily("Open Sans")),
            FontEntry("Montserrat", "Montserrat", FontCategory.SANS_SERIF, createGoogleFontFamily("Montserrat")),
            FontEntry("Lato", "Lato", FontCategory.SANS_SERIF, createGoogleFontFamily("Lato")),
            FontEntry("Poppins", "Poppins", FontCategory.SANS_SERIF, createGoogleFontFamily("Poppins")),
            FontEntry("Raleway", "Raleway", FontCategory.SANS_SERIF, createGoogleFontFamily("Raleway")),
            FontEntry("Nunito", "Nunito", FontCategory.SANS_SERIF, createGoogleFontFamily("Nunito")),
            FontEntry("Ubuntu", "Ubuntu", FontCategory.SANS_SERIF, createGoogleFontFamily("Ubuntu")),

            // --- MONOSPACE (Technical, Terminal, Data Sheets, Invoices) ---
            FontEntry("Courier New", "Courier New", FontCategory.MONOSPACE, FontFamily.Monospace),
            FontEntry("Roboto Mono", "Roboto Mono", FontCategory.MONOSPACE, createGoogleFontFamily("Roboto Mono")),
            FontEntry("Fira Code", "Fira Code", FontCategory.MONOSPACE, createGoogleFontFamily("Fira Code")),
            FontEntry("Source Code Pro", "Source Code Pro", FontCategory.MONOSPACE, createGoogleFontFamily("Source Code Pro")),
            FontEntry("Space Mono", "Space Mono", FontCategory.MONOSPACE, createGoogleFontFamily("Space Mono")),
            FontEntry("Inconsolata", "Inconsolata", FontCategory.MONOSPACE, createGoogleFontFamily("Inconsolata")),

            // --- HANDWRITING & SIGNATURES ---
            FontEntry("Caveat (Signature)", "Caveat", FontCategory.HANDWRITING, createGoogleFontFamily("Caveat")),
            FontEntry("Dancing Script", "Dancing Script", FontCategory.HANDWRITING, createGoogleFontFamily("Dancing Script")),
            FontEntry("Pacifico", "Pacifico", FontCategory.HANDWRITING, createGoogleFontFamily("Pacifico")),
            FontEntry("Satisfy", "Satisfy", FontCategory.HANDWRITING, createGoogleFontFamily("Satisfy")),
            FontEntry("Great Vibes", "Great Vibes", FontCategory.HANDWRITING, createGoogleFontFamily("Great Vibes")),

            // --- SYSTEM DEFAULTS ---
            FontEntry("System Sans-Serif", "sans-serif", FontCategory.SYSTEM, FontFamily.SansSerif),
            FontEntry("System Serif", "serif", FontCategory.SYSTEM, FontFamily.Serif),
            FontEntry("System Monospace", "monospace", FontCategory.SYSTEM, FontFamily.Monospace),
            FontEntry("System Cursive", "cursive", FontCategory.SYSTEM, FontFamily.Cursive)
        )
    }

    /**
     * Resolves a numeric thickness (100 to 900) to standard Compose FontWeight.
     */
    fun resolveFontWeight(weightValue: Int): FontWeight {
        return when {
            weightValue <= 150 -> FontWeight.W100
            weightValue <= 250 -> FontWeight.W200
            weightValue <= 350 -> FontWeight.W300
            weightValue <= 450 -> FontWeight.W400
            weightValue <= 550 -> FontWeight.W500
            weightValue <= 650 -> FontWeight.W600
            weightValue <= 750 -> FontWeight.W700
            weightValue <= 850 -> FontWeight.W800
            else -> FontWeight.W900
        }
    }

    /**
     * Intelligent Auto-Match Source Font Heuristic:
     * 
     * Inspects the PDF's font name and bounding box to match the source document font 1:1.
     * 
     * @param sourceFontDescriptor e.g. "TimesNewRomanPSMT", "Helvetica-Bold", "Calibri", "BAAAAA+Roboto-Medium"
     * @param boundingBoxHeightPt The height of the text bounding box in PDF point space.
     * @param isBoldHint Hint whether the source character stream used bold matrices.
     * @param isItalicHint Hint whether oblique/italic transforms were used.
     * @return TypographyConfig calibrated to mirror the source document.
     */
    fun matchSourceFont(
        sourceFontDescriptor: String?,
        boundingBoxHeightPt: Float,
        isBoldHint: Boolean = false,
        isItalicHint: Boolean = false
    ): TypographyConfig {
        val raw = (sourceFontDescriptor ?: "").lowercase()

        // 1. Determine Weight (100 to 900)
        val weightValue = when {
            raw.contains("black") || raw.contains("heavy") || raw.contains("ultra") -> 900
            raw.contains("extrabold") || raw.contains("semibold") -> 700
            raw.contains("bold") || isBoldHint -> 700
            raw.contains("medium") -> 500
            raw.contains("light") || raw.contains("thin") -> 300
            else -> 400
        }

        // 2. Determine Italic
        val isItalic = isItalicHint || raw.contains("italic") || raw.contains("oblique")

        // 3. Match Font Family
        val matchedEntry = when {
            // Serif signatures
            raw.contains("times") || raw.contains("roman") || raw.contains("minion") -> {
                allFonts.firstOrNull { it.technicalName == "Times New Roman" }
            }
            raw.contains("georgia") -> allFonts.firstOrNull { it.technicalName == "Georgia" }
            raw.contains("garamond") -> allFonts.firstOrNull { it.technicalName == "EB Garamond" }
            raw.contains("baskerville") -> allFonts.firstOrNull { it.technicalName == "Libre Baskerville" }
            raw.contains("playfair") -> allFonts.firstOrNull { it.technicalName == "Playfair Display" }
            raw.contains("lora") -> allFonts.firstOrNull { it.technicalName == "Lora" }
            raw.contains("serif") -> allFonts.firstOrNull { it.category == FontCategory.SERIF }

            // Monospace signatures
            raw.contains("courier") || raw.contains("consolas") || raw.contains("mono") -> {
                allFonts.firstOrNull { it.technicalName == "Courier New" } ?: allFonts.firstOrNull { it.technicalName == "Roboto Mono" }
            }

            // Sans-Serif signatures
            raw.contains("helvetica") || raw.contains("arial") -> {
                allFonts.firstOrNull { it.technicalName == "Arial" }
            }
            raw.contains("inter") -> allFonts.firstOrNull { it.technicalName == "Inter" }
            raw.contains("montserrat") -> allFonts.firstOrNull { it.technicalName == "Montserrat" }
            raw.contains("lato") -> allFonts.firstOrNull { it.technicalName == "Lato" }
            raw.contains("poppins") -> allFonts.firstOrNull { it.technicalName == "Poppins" }
            raw.contains("opensans") || raw.contains("open-sans") -> allFonts.firstOrNull { it.technicalName == "Open Sans" }
            raw.contains("roboto") -> allFonts.firstOrNull { it.technicalName == "Roboto" }

            else -> allFonts.firstOrNull { it.technicalName == "Roboto" } ?: allFonts.first()
        } ?: allFonts.first()

        // 4. Calculate accurate typographic point size
        // Typographic em-size is typically ~85-95% of line bounding height
        val calculatedSizePt = (boundingBoxHeightPt * 0.90f).coerceIn(5.0f, 144.0f)

        return TypographyConfig(
            fontName = matchedEntry.displayName,
            fontFamily = matchedEntry.composeFontFamily,
            fontSizePt = calculatedSizePt,
            fontWeight = resolveFontWeight(weightValue),
            fontWeightValue = weightValue,
            isItalic = isItalic,
            isSourceMatched = true
        )
    }
}

