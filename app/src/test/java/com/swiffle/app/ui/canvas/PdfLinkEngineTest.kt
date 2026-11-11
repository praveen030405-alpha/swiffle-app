package com.swiffle.app.ui.canvas.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PdfLinkEngineTest {

    @Test
    fun testExtractFirstUrl_standardHttpAndHttps() {
        val text1 = "Visit our site at https://swiffle.app for more details."
        assertEquals("https://swiffle.app", PdfLinkEngine.extractFirstUrl(text1))

        val text2 = "Check http://example.com/docs/intro now!"
        assertEquals("http://example.com/docs/intro", PdfLinkEngine.extractFirstUrl(text2))
    }

    @Test
    fun testExtractFirstUrl_wwwAndDomainOnly() {
        val text1 = "Go to www.google.com to search"
        assertEquals("https://www.google.com", PdfLinkEngine.extractFirstUrl(text1))

        val text2 = "github.com/swiffle/origami"
        assertEquals("https://github.com/swiffle/origami", PdfLinkEngine.extractFirstUrl(text2))
    }

    @Test
    fun testExtractFirstUrl_email() {
        val text = "Contact support@swiffle.com today"
        assertEquals("mailto:support@swiffle.com", PdfLinkEngine.extractFirstUrl(text))
    }

    @Test
    fun testExtractFirstUrl_plainTextNoUrl() {
        val text = "This is simply normal body text with no hyperlinks."
        assertNull(PdfLinkEngine.extractFirstUrl(text))
    }
}
