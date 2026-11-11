package com.swiffle.app.ui.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageATextModelTest {

    @Test
    fun testEndpointPackingAndMonotonicComparison() {
        val ep1 = Endpoint(pageIndex = 0, lineIndex = 0, glyphIndex = 0)
        val ep2 = Endpoint(pageIndex = 0, lineIndex = 0, glyphIndex = 5)
        val ep3 = Endpoint(pageIndex = 0, lineIndex = 1, glyphIndex = 2)
        val ep4 = Endpoint(pageIndex = 1, lineIndex = 0, glyphIndex = 0)

        // Verify component extraction
        assertEquals(0, ep1.pageIndex)
        assertEquals(0, ep1.lineIndex)
        assertEquals(0, ep1.glyphIndex)

        assertEquals(0, ep2.pageIndex)
        assertEquals(0, ep2.lineIndex)
        assertEquals(5, ep2.glyphIndex)

        assertEquals(0, ep3.pageIndex)
        assertEquals(1, ep3.lineIndex)
        assertEquals(2, ep3.glyphIndex)

        assertEquals(1, ep4.pageIndex)
        assertEquals(0, ep4.lineIndex)
        assertEquals(0, ep4.glyphIndex)

        // Monotonic reading order ordering
        assertTrue(ep1 < ep2) // Same line, earlier glyph comes first
        assertTrue(ep2 < ep3) // Earlier line comes before later line
        assertTrue(ep3 < ep4) // Earlier page comes before later page

        // Inversion normalization
        val invertedStart = ep3
        val invertedEnd = ep1
        val normalizedStart = minOf(invertedStart, invertedEnd)
        val normalizedEnd = maxOf(invertedStart, invertedEnd)

        assertEquals(ep1, normalizedStart)
        assertEquals(ep3, normalizedEnd)
        assertTrue(normalizedStart <= normalizedEnd)
    }

    @Test
    fun testContinuousLineBoundariesAndSpaceBridging() {
        // Construct a line with 3 glyphs: 'A' (x=10..18), 'B' (x=20..28), 'C' (x=30..38)
        val json = """
            {
                "c": ["A", "B", "C"],
                "x": [10.0, 20.0, 30.0],
                "y": [20.0, 20.0, 20.0],
                "w": [8.0, 8.0, 8.0],
                "h": [12.0, 12.0, 12.0]
            }
        """.trimIndent()

        val model = PageTextModel.fromGeometryJson(pageNum = 1, jsonStr = json)
        assertEquals(1, model.lines.size)

        val line = model.lines[0]
        assertEquals(3, line.glyphCount)
        // For 3 glyphs, there must be 3 + 1 = 4 boundary markers
        assertEquals(4, line.boundaries.size)

        // boundary[0] = first glyph left edge
        assertEquals(10f, line.boundaries[0], 0.001f)
        // boundary[1] = bridge between A and B: (18 + 20)/2 = 19.0
        assertEquals(19f, line.boundaries[1], 0.001f)
        // boundary[2] = bridge between B and C: (28 + 30)/2 = 29.0
        assertEquals(29f, line.boundaries[2], 0.001f)
        // boundary[3] = last glyph right edge: 30 + 8 = 38.0
        assertEquals(38f, line.boundaries[3], 0.001f)

        // Highlight full line
        val bands = model.getHighlightBands(
            start = Endpoint(0, 0, 0),
            end = Endpoint(0, 0, 2)
        )
        assertEquals(1, bands.size)
        val band = bands[0]
        // Highlight band must span continuously from 10.0 to 38.0 with NO internal gaps
        assertEquals(10f, band.left, 0.001f)
        assertEquals(38f, band.right, 0.001f)
        assertEquals(28f, band.width, 0.001f)
    }

    @Test
    fun testSentenceSegmentationEdgeCases() {
        // Test text with abbreviations, numbers, decimals, and multiple sentences
        val sample = """
            {
                "c": ["D","r","."," ","S","m","i","t","h"," ","s","a","i","d"," ","e",".","g","."," ","t","h","a","t"," ","₹","3"," ","i","s"," ","1","5","-","2","0","%"," ","o","f"," ","v","2","."," ","T","h","i","s"," ","i","s"," ","n","e","w","."],
                "x": [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56],
                "y": [10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10],
                "w": [1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],
                "h": [10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10]
            }
        """.trimIndent()

        val model = PageTextModel.fromGeometryJson(pageNum = 1, jsonStr = sample)
        val sentences = model.sentences

        // "Dr. Smith said e.g. that ₹3 is 15-20% of v2." should NOT split on Dr. or e.g. or ₹3 or 15-20%
        // It must cleanly split into 2 sentences:
        // 1: "Dr. Smith said e.g. that ₹3 is 15-20% of v2."
        // 2: "This is new."
        assertEquals(2, sentences.size)
        assertTrue(sentences[0].text.startsWith("Dr. Smith"))
        assertTrue(sentences[0].text.endsWith("v2."))
        assertTrue(sentences[1].text.startsWith("This is new."))
    }

    @Test
    fun testComparisonOldDisjointGapsVsNewContinuousBands() {
        // Reproduce Bug 6:
        // Two adjacent characters in a word: 'P' at x=10..18 and 'D' at x=20..28
        // Advance width creates a 2.0pt gap between glyphs (x=18 to x=20)
        val char1Left = 10f
        val char1Right = 18f
        val char2Left = 20f
        val char2Right = 28f

        // OLD CODE BEHAVIOR (PdfCanvasScreen lines 5188-5214):
        // Each character was drawn as its own disjoint drawRoundRect(rx, ry, rw, rh)
        val oldBox1Right = char1Right
        val oldBox2Left = char2Left
        val oldGap = oldBox2Left - oldBox1Right
        // Old code leaves an unhighlighted gap between the glyphs:
        assertTrue("Old code leaves a gap > 0 between adjacent characters", oldGap > 0f)
        assertEquals(2.0f, oldGap, 0.001f)

        // NEW CODE BEHAVIOR (HighlightRenderer + PageTextModel):
        // Continuous line boundaries bridge the space between char1 and char2
        val json = """
            {
                "c": ["P", "D"],
                "x": [$char1Left, $char2Left],
                "y": [20.0, 20.0],
                "w": [8.0, 8.0],
                "h": [12.0, 12.0]
            }
        """.trimIndent()

        val model = PageTextModel.fromGeometryJson(pageNum = 1, jsonStr = json)
        val bands = model.getHighlightBands(
            start = Endpoint(0, 0, 0),
            end = Endpoint(0, 0, 1)
        )

        assertEquals(1, bands.size)
        val band = bands[0]

        // The new HighlightBand is ONE continuous polygon from left edge of 'P' to right edge of 'D'
        assertEquals(char1Left, band.left, 0.001f)
        assertEquals(char2Right, band.right, 0.001f)
        // Gap inside the continuous highlight band is exactly ZERO
        val newGap = (band.right - band.left) - ((char1Right - char1Left) + (char2Right - char2Left) + oldGap)
        assertEquals(0.0f, newGap, 0.001f)
    }
}
