package com.swiffle.app.evidence

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceModelsTest {
    private val source = EvidenceSource("content://test/one.pdf", "Invoice.pdf", "abc")
    private val region = EvidenceBounds(0.2f, 0.3f, 0.5f, 0.7f)

    @Test
    fun reverseDragIsNormalisedAndClampedToThePage() {
        assertEquals(
            EvidenceBounds(0f, 0.2f, 1f, 0.8f),
            EvidenceBounds.between(1.2f, 0.8f, -0.1f, 0.2f)
        )
    }

    @Test
    fun reorderingKeepsOtherGroupsAndGroupOrderInPlace() {
        val first = EvidenceItem("1", source, 3, listOf(region), "Total", group = "Invoices")
        val second = EvidenceItem("2", source, 1, listOf(region), group = "Statements")
        val third = EvidenceItem("3", source, 4, listOf(region), "GST", group = "Invoices")
        val pack = EvidencePack("pack", "Review", listOf(first, second, third))

        assertEquals(listOf(third, second, first), pack.moveWithinGroup("3", -1).items)
        assertEquals(pack, pack.moveWithinGroup("2", 1))
        assertEquals(pack, pack.moveWithinGroup("missing", -1))
    }

    @Test
    fun roundTripPreservesSourceRegionsAndReviewState() {
        val text = EvidenceItem("1", source, 3, listOf(region), "₹12,450", note = "Check total", reviewed = true)
        val visual = EvidenceItem("2", source.copy(retainedCopy = true), 2, listOf(region), group = "Payment")
        val original = EvidenceLibrary("pack", listOf(EvidencePack("pack", "Review", listOf(text, visual))))
        val json = Json.encodeToString(EvidenceLibrary.serializer(), original)
        val restored = Json.decodeFromString(EvidenceLibrary.serializer(), json)

        assertEquals(original, restored)
        assertNull(restored.activePack?.items?.get(1)?.excerpt)
    }
}
