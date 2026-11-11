package com.swiffle.app.ui.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableExtractorTest {

    @Test
    fun testTableExtractionBasic() {
        // Construct a 2x2 grid of character rects:
        // Row 1 (y=100): "Name" at x=10, "Score" at x=80
        // Row 2 (y=80):  "Alice" at x=10, "95" at x=80
        val rects = listOf(
            CharRect("N", 10f, 100f, 8f, 12f),
            CharRect("a", 18f, 100f, 8f, 12f),
            CharRect("m", 26f, 100f, 8f, 12f),
            CharRect("e", 34f, 100f, 8f, 12f),

            CharRect("S", 80f, 100f, 8f, 12f),
            CharRect("c", 88f, 100f, 8f, 12f),
            CharRect("o", 96f, 100f, 8f, 12f),
            CharRect("r", 104f, 100f, 8f, 12f),
            CharRect("e", 112f, 100f, 8f, 12f),

            CharRect("A", 10f, 80f, 8f, 12f),
            CharRect("l", 18f, 80f, 8f, 12f),
            CharRect("i", 26f, 80f, 8f, 12f),
            CharRect("c", 34f, 80f, 8f, 12f),
            CharRect("e", 42f, 80f, 8f, 12f),

            CharRect("9", 80f, 80f, 8f, 12f),
            CharRect("5", 88f, 80f, 8f, 12f)
        )

        val markdown = TableExtractor.extractMarkdownTable(rects)
        assertTrue(markdown.isNotBlank())
        assertTrue(markdown.contains("Name"))
        assertTrue(markdown.contains("Score"))
        assertTrue(markdown.contains("Alice"))
        assertTrue(markdown.contains("95"))
        assertTrue(markdown.contains("---"))
    }

    @Test
    fun testTableExtractionEmpty() {
        val emptyMarkdown = TableExtractor.extractMarkdownTable(emptyList())
        assertEquals("", emptyMarkdown)
    }
}
