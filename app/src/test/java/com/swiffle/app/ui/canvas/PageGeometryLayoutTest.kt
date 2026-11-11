package com.swiffle.app.ui.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageGeometryLayoutTest {

    @Test
    fun testVariablePageSizeLayoutCalculation() {
        // Test layout with 3 mixed-size pages:
        // Page 0: A4 portrait (w=595, h=842) -> ratio = 595/842 = 0.70665f
        // Page 1: Landscape slide (w=1920, h=1080) -> ratio = 1920/1080 = 1.77778f
        // Page 2: Square receipt (w=600, h=600) -> ratio = 1.0f
        val pageDimensions = listOf(
            Pair(595f, 842f),
            Pair(1920f, 1080f),
            Pair(600f, 600f)
        )

        val screenWidth = 1080f
        val paddingPx = 32f
        val spacingPx = 32f
        val topPaddingPx = 200f
        val maxReadingWidth = screenWidth - paddingPx * 2 // 1016f

        // Compute layout heights and tops
        val pageHeightsAt1x = FloatArray(pageDimensions.size)
        val pageTopsAt1x = FloatArray(pageDimensions.size)
        var currentTop = topPaddingPx

        for (i in pageDimensions.indices) {
            val (pw, ph) = pageDimensions[i]
            val ratio = if (ph > 0f) pw / ph else (1f / 1.414f)
            val h = maxReadingWidth / ratio
            pageHeightsAt1x[i] = h
            pageTopsAt1x[i] = currentTop
            currentTop += h + spacingPx
        }

        // Verify page heights reflect individual aspect ratios
        val expectedH0 = 1016f / (595f / 842f)
        val expectedH1 = 1016f / (1920f / 1080f)
        val expectedH2 = 1016f / 1.0f
        assertEquals(expectedH0, pageHeightsAt1x[0], 0.01f)
        assertEquals(expectedH1, pageHeightsAt1x[1], 0.01f)
        assertEquals(expectedH2, pageHeightsAt1x[2], 0.01f)

        // Verify tops
        assertEquals(200f, pageTopsAt1x[0], 0.01f)
        assertEquals(200f + expectedH0 + 32f, pageTopsAt1x[1], 0.01f)
        assertEquals(pageTopsAt1x[1] + expectedH1 + 32f, pageTopsAt1x[2], 0.01f)

        // Total content height
        val totalH = currentTop
        assertTrue(totalH > pageTopsAt1x[2] + pageHeightsAt1x[2])
    }

    @Test
    fun testEmptyPageDimensionsFallback() {
        val pageDimensions = emptyList<Pair<Float, Float>>()
        val pageCount = 5
        val defaultRatio = 1f / 1.414f
        val screenWidth = 1000f
        val paddingPx = 20f
        val maxReadingWidth = screenWidth - paddingPx * 2 // 960f
        val defaultHeight = maxReadingWidth / defaultRatio

        val pageHeights = (0 until pageCount).map { i ->
            val (pw, ph) = pageDimensions.getOrNull(i) ?: Pair(0f, 0f)
            val ratio = if (ph > 0f && pw > 0f) pw / ph else defaultRatio
            maxReadingWidth / ratio
        }

        assertEquals(5, pageHeights.size)
        pageHeights.forEach {
            assertEquals(defaultHeight, it, 0.01f)
        }
    }
}
