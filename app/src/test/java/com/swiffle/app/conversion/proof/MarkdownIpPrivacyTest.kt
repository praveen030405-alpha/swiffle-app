package com.swiffle.app.conversion.proof

import com.swiffle.app.conversion.MarkdownIpPrivacy
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownIpPrivacyTest {
    @Test fun addressesAreHiddenInBodyUrlsAndIpv6Footers() {
        for (ip in listOf("192.0.2.24","2001:db8::1","fe80::1%wlan0","::ffff:192.0.2.4","::1","2001:db8:1:2:3:4:5:6")) {
            assertEquals("FINAL [IP removed].",MarkdownIpPrivacy.redact("FINAL " + ip + "."))
        }
        assertEquals("http://[IP removed]:8080/path",MarkdownIpPrivacy.redact("http://192.0.2.1:8080/path"))
        assertEquals("Total 1,50,000.00 · 2025-10-31",MarkdownIpPrivacy.redact("Total 1,50,000.00 · 2025-10-31"))
    }
    @Test fun fragmentedGlyphsAreHidden() {
        assertEquals("IP [IP removed] FINAL",MarkdownIpPrivacy.redactPieces(listOf("IP 192.","0",".2.1 FINAL")).joinToString(""))
    }
}
