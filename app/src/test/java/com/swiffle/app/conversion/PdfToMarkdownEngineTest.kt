package com.swiffle.app.conversion

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfToMarkdownEngineTest {

    @Test
    fun gstr1ComplexTabularPdf_preservesGridWatermarkFooterLigaturesAndRowAlignment() {
        val doc = PDDocument()
        try {
            // ── Real PDPage with exact PDF vector table rulings (A4: 595 x 842) ──
            val page1 = PDPage(PDRectangle.A4)
            doc.addPage(page1)

            val pageH = 842f
            val leftCols = floatArrayOf(30f, 175f, 385f)
            val metaYsPdf = floatArrayOf(780f, 758f, 736f, 714f)

            val rightCols = floatArrayOf(400f, 495f, 565f)
            val rightYsPdf = floatArrayOf(780f, 758f, 736f)

            val mainCols = floatArrayOf(30f, 210f, 260f, 325f, 390f, 450f, 510f, 565f)
            val mainYsPdf = floatArrayOf(690f, 662f, 634f, 606f, 578f, 550f, 522f, 494f, 466f)

            PDPageContentStream(doc, page1).use { cs ->
                drawGrid(cs, leftCols, metaYsPdf)
                drawGrid(cs, rightCols, rightYsPdf)
                drawGrid(cs, mainCols, mainYsPdf)
            }

            // 1. Run REAL PDF content stream vector ruling extractor on the PDPage!
            val geometry = PdfVectorRulingExtractor.extractPageGeometry(page1)
            assertTrue(
                "Expected >= 3 lattice tables extracted from PDF vector stream, got ${geometry.latticeTables.size}",
                geometry.latticeTables.size >= 3
            )

            // 2. Build exact visual-space RawGlyphs matching the PDF layout
            val bodyGlyphs = mutableListOf<RawGlyph>()
            val rotatedWatermarkGlyphs = mutableListOf<RawGlyph>()

            fun addText(x: Float, pdfY: Float, text: String, size: Float = 8.5f, bold: Boolean = false): Float {
                if (text.isEmpty()) return x
                val visualY = pageH - pdfY
                var curX = x
                val charW = size * 0.48f
                for (ch in text) {
                    val w = if (ch == ' ') size * 0.28f else charW
                    bodyGlyphs.add(
                        RawGlyph(
                            unicode = ch.toString(),
                            x = curX,
                            y = visualY,
                            width = w,
                            height = size * 0.8f,
                            yTop = visualY - size * 0.7f,
                            yBottom = visualY + size * 0.15f,
                            fontSizePt = size,
                            widthOfSpace = size * 0.28f,
                            isBold = bold,
                            isItalic = false,
                            isMonospace = false,
                            isObliqueRotated = false
                        )
                    )
                    curX += w
                }
                return curX
            }

            // Diagonal 45-degree "FINAL" watermark glyphs across page center
            "FINAL".forEachIndexed { idx, ch ->
                val wx = 170f + idx * 55f
                val wy = 480f - idx * 55f
                rotatedWatermarkGlyphs.add(
                    RawGlyph(
                        unicode = ch.toString(),
                        x = wx,
                        y = wy,
                        width = 48f,
                        height = 72f,
                        yTop = wy - 60f,
                        yBottom = wy + 12f,
                        fontSizePt = 84f,
                        widthOfSpace = 22f,
                        isBold = true,
                        isItalic = false,
                        isMonospace = false,
                        isObliqueRotated = true
                    )
                )
            }

            // Title
            addText(220f, 800f, "FORM GSTR-1", size = 14f, bold = true)

            // Top Left Metadata Box
            addText(35f, 766f, "1. GSTIN", bold = true)
            addText(180f, 766f, "33AABCS1234F1Z5")
            addText(35f, 744f, "2(a). Legal name", bold = true)
            addText(180f, 744f, "SHREE MITHAI PRIVATE LIMITED")
            addText(35f, 722f, "2(b). Trade name", bold = true)
            addText(180f, 722f, "SHREE MITHAI PRIVATE LIMITED")

            // Top Right Metadata Box (side-by-side!)
            addText(405f, 766f, "Financial year", bold = true)
            addText(500f, 766f, "2025-26")
            addText(405f, 744f, "Tax period", bold = true)
            addText(500f, 744f, "January")

            // Main 7-Column GSTR-1 Table
            addText(35f, 678f, "Description", size = 8f, bold = true)
            addText(214f, 678f, "No. of", size = 8f, bold = true)
            addText(214f, 668f, "records", size = 8f, bold = true)
            addText(264f, 678f, "Type", size = 8f, bold = true)
            addText(329f, 678f, "Value", size = 8f, bold = true)
            addText(394f, 678f, "IGST", size = 8f, bold = true)
            addText(454f, 678f, "CGST", size = 8f, bold = true)
            addText(514f, 678f, "SGST", size = 8f, bold = true)

            // Row 1: 6C Exports (blank IGST cell!)
            addText(35f, 646f, "6C - Deemed Exports", size = 8f)
            addText(220f, 646f, "0", size = 8f)
            addText(266f, 646f, "Invoice", size = 8f)
            addText(330f, 646f, "0.00", size = 8f)
            // IGST intentionally omitted (blank cell!)
            addText(455f, 646f, "0.00", size = 8f)
            addText(515f, 646f, "0.00", size = 8f)

            // Row 2: Table 7 B2CS
            addText(35f, 618f, "7 - B2C (Others)", size = 8f)
            addText(220f, 618f, "12", size = 8f)
            addText(266f, 618f, "Total", size = 8f)
            addText(328f, 618f, "13,81,53,353.26", size = 8f)
            addText(395f, 618f, "0.00", size = 8f)
            addText(455f, 618f, "34,53,833.83", size = 8f)
            addText(515f, 618f, "34,53,833.83", size = 8f)

            // Row 3: 9B CDNR (all zeros)
            addText(35f, 590f, "9B - Credit/Debit Notes (Registered) - CDNR", size = 8f)
            addText(220f, 590f, "0", size = 8f)
            addText(266f, 590f, "Total", size = 8f)
            addText(330f, 590f, "0.00", size = 8f)
            addText(395f, 590f, "0.00", size = 8f)
            addText(455f, 590f, "0.00", size = 8f)
            addText(515f, 590f, "0.00", size = 8f)

            // Row 4 & 5: Table 14 (a) and (b)
            addText(35f, 562f, "(a) Liable to collect tax u/s 52", size = 8f)
            addText(220f, 562f, "0", size = 8f)
            addText(266f, 562f, "Total", size = 8f)
            addText(330f, 562f, "0.00", size = 8f)
            addText(395f, 562f, "0.00", size = 8f)
            addText(455f, 562f, "0.00", size = 8f)
            addText(515f, 562f, "0.00", size = 8f)

            addText(35f, 534f, "(b) Liable to pay tax u/s 9(5)", size = 8f)
            addText(220f, 534f, "0", size = 8f)
            addText(266f, 534f, "Total", size = 8f)
            addText(330f, 534f, "0.00", size = 8f)
            addText(395f, 534f, "0.00", size = 8f)
            addText(455f, 534f, "0.00", size = 8f)
            addText(515f, 534f, "0.00", size = 8f)

            // Row 6: Total Liability
            addText(35f, 506f, "Total Liability", size = 8f, bold = true)
            addText(220f, 506f, "12", size = 8f, bold = true)
            addText(266f, 506f, "Total", size = 8f, bold = true)
            addText(328f, 506f, "13,81,53,353.26", size = 8f, bold = true)
            addText(395f, 506f, "0.00", size = 8f, bold = true)
            addText(455f, 506f, "34,53,833.83", size = 8f, bold = true)
            addText(515f, 506f, "34,53,833.83", size = 8f, bold = true)

            // Verification declaration with split chunks & dropped "fi" ligatures
            addText(30f, 420f, "Verifcation:", size = 9.5f, bold = true)
            addText(30f, 404f, "I hereby solemnly affrm and declare that the information given herein above is true and correct", size = 9f)
            val xAfterInCase = addText(30f, 390f, "in case ", size = 9f)
            val xAfterLia = addText(xAfterInCase, 390f, "of any reduction in output tax lia", size = 9f)
            addText(xAfterLia + 0.2f, 390f, "bility the beneft thereof has been passed on.", size = 9f)

            addText(30f, 350f, "Date", size = 9f, bold = true)
            addText(420f, 350f, "Signature", size = 9f, bold = true)

            // Upright footer with FINAL & IP address
            addText(30f, 32f, "FINAL   IP Address: 49.207.181.99", size = 8f)

            val profile = TypographyProfile(
                modalBodyFontSize = 8.5f,
                modalLineSpacing = 13f,
                typicalLeftMargin = 30f,
                repeatingHeaders = emptySet(),
                repeatingFooters = setOf("final ip address: #.#.#.#"),
                hasPdfOutline = false
            )
            val config = PdfToMarkdownConfig.forPreset(MarkdownPreset.ACADEMIC_BOOK)
            val out = PdfSpatialLayoutAnalyzer.analyzeAndRenderPage(
                bodyGlyphsInput = bodyGlyphs,
                rotatedWatermarkGlyphs = rotatedWatermarkGlyphs,
                geometry = geometry,
                pageNum = 1,
                profile = profile,
                config = config,
                usedAnchors = mutableMapOf()
            )

            val md = out.markdown
            assertTrue("Expected >= 3 tables detected, got ${out.tableCount}", out.tableCount >= 3)
            assertTrue("Expected FINAL watermark recorded", out.detectedWatermarks.any { it.contains("FINAL") })
            assertFalse("Must NOT contain stray AL heading", md.contains("# AL"))
            assertFalse("Must NOT contain stray IN heading", md.contains("# IN"))
            assertFalse("Must NOT promote column headers or Date/Signature to headings", out.headings.any {
                it.title.equals("records", true) ||
                    it.title.equals("Type", true) ||
                    it.title.equals("Value", true) ||
                    it.title.equals("Date", true) ||
                    it.title.equals("Signature", true) ||
                    it.title.startsWith("Verification", true)
            })

            assertTrue(
                "Legal name row must contain SHREE MITHAI PRIVATE LIMITED",
                md.lines().any { it.contains("2(a). Legal name") && it.contains("SHREE MITHAI PRIVATE LIMITED") }
            )
            assertTrue(
                "Trade name row must contain SHREE MITHAI PRIVATE LIMITED",
                md.lines().any { it.contains("2(b). Trade name") && it.contains("SHREE MITHAI PRIVATE LIMITED") }
            )
            assertTrue(
                "Financial year 2025-26 must sit on its own row",
                md.lines().any { it.contains("Financial year") && it.contains("2025-26") && !it.contains("6C") }
            )
            assertTrue(
                "Tax period January must sit on its own row",
                md.lines().any { it.contains("Tax period") && it.contains("January") && !it.contains("6C") }
            )
            assertTrue(
                "Table 7 row must hold 13,81,53,353.26",
                md.lines().any { it.contains("7 - B2C (Others)") && it.contains("13,81,53,353.26") }
            )
            assertTrue(
                "9B CDNR row must NOT hold 13,81,53,353.26",
                md.lines().any { it.contains("9B - Credit/Debit Notes") && !it.contains("13,81,53,353.26") && it.contains("0.00") }
            )

            val row6C = md.lines().first { it.contains("6C - Deemed Exports") }
            assertTrue("6C row must preserve blank IGST cell slot: $row6C", row6C.contains("| 0.00 |  | 0.00 | 0.00 |"))

            assertTrue("Must preserve (a) label", md.contains("(a) Liable to collect tax u/s 52"))
            assertTrue("Must preserve (b) label", md.contains("(b) Liable to pay tax u/s 9(5)"))

            assertTrue("Must repair Verifcation -> Verification", md.contains("Verification:"))
            assertTrue("Must repair affrm -> affirm", md.contains("solemnly affirm and declare"))
            assertTrue(
                "Must keep 'in case of any reduction in output tax liability the benefit thereof' unbroken",
                md.contains("in case of any reduction in output tax liability the benefit thereof")
            )

            assertTrue("Must preserve IP address footer 49.207.181.99", md.contains("49.207.181.99"))
            assertTrue("Must preserve upright FINAL footer", md.contains("FINAL"))
        } finally {
            doc.close()
        }
    }

    @Test
    fun proseAlphaSubItems_preserveParenthesizedLabels() {
        val glyphs = mutableListOf<RawGlyph>()
        fun addLine(y: Float, text: String) {
            var curX = 40f
            for (ch in text) {
                val w = if (ch == ' ') 3f else 5f
                glyphs.add(
                    RawGlyph(
                        unicode = ch.toString(),
                        x = curX,
                        y = y,
                        width = w,
                        height = 9f,
                        yTop = y - 7f,
                        yBottom = y + 2f,
                        fontSizePt = 10f,
                        widthOfSpace = 3f,
                        isBold = false,
                        isItalic = false,
                        isMonospace = false,
                        isObliqueRotated = false
                    )
                )
                curX += w
            }
        }
        addLine(100f, "(a) Liable to collect tax u/s 52")
        addLine(118f, "(b) Liable to pay tax u/s 9(5)")

        val out = PdfSpatialLayoutAnalyzer.analyzeAndRenderPage(
            bodyGlyphsInput = glyphs,
            rotatedWatermarkGlyphs = emptyList(),
            geometry = PageVectorGeometry(emptyList(), emptyList(), emptyList(), 595f, 842f),
            pageNum = 1,
            profile = TypographyProfile(10f, 14f, 40f, emptySet(), emptySet()),
            config = PdfToMarkdownConfig.forPreset(MarkdownPreset.ACADEMIC_BOOK),
            usedAnchors = mutableMapOf()
        )
        assertTrue("Must preserve (a) label in prose list", out.markdown.contains("**(a)** Liable to collect tax u/s 52"))
        assertTrue("Must preserve (b) label in prose list", out.markdown.contains("**(b)** Liable to pay tax u/s 9(5)"))
    }

    private fun drawGrid(
        cs: PDPageContentStream,
        xs: FloatArray,
        ys: FloatArray
    ) {
        cs.setLineWidth(0.75f)
        val xMin = xs.first()
        val xMax = xs.last()
        val yTop = ys.first()
        val yBot = ys.last()
        for (y in ys) {
            cs.moveTo(xMin, y)
            cs.lineTo(xMax, y)
            cs.stroke()
        }
        for (x in xs) {
            cs.moveTo(x, yTop)
            cs.lineTo(x, yBot)
            cs.stroke()
        }
    }
}
