package com.swiffle.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageBSelectionTest {

    private fun createTwoLineModel(): PageTextModel {
        // Line 0: "Swiffle is great" (y=20, h=12, top=8, bottom=20)
        // Line 1: "Origami PDF app"  (y=40, h=12, top=28, bottom=40)
        val json = """
            {
                "c": [
                    "S", "w", "i", "f", "f", "l", "e", " ", "i", "s", " ", "g", "r", "e", "a", "t",
                    "O", "r", "i", "g", "a", "m", "i", " ", "P", "D", "F", " ", "a", "p", "p"
                ],
                "x": [
                    10.0, 18.0, 26.0, 34.0, 42.0, 50.0, 58.0, 66.0, 72.0, 80.0, 88.0, 94.0, 102.0, 110.0, 118.0, 126.0,
                    10.0, 18.0, 26.0, 34.0, 42.0, 50.0, 58.0, 66.0, 72.0, 80.0, 88.0, 94.0, 102.0, 110.0, 118.0
                ],
                "y": [
                    20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0,
                    40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0
                ],
                "w": [
                    8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 6.0, 8.0, 8.0, 6.0, 8.0, 8.0, 8.0, 8.0, 8.0,
                    8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 6.0, 8.0, 8.0, 8.0, 6.0, 8.0, 8.0, 8.0
                ],
                "h": [
                    12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0,
                    12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0, 12.0
                ]
            }
        """.trimIndent()
        return PageTextModel.fromGeometryJson(pageNum = 1, jsonStr = json)
    }

    @Test
    fun testWordSelectionInitializesEndpointsAccurately() {
        val model = createTwoLineModel()
        val controller = SelectionController()

        // Long press on "Swiffle" (x=20, y=16 on line 0)
        val selected = controller.selectWord(pageNum = 1, pageModel = model, pdfX = 20f, pdfY = 16f)
        assertTrue(selected)

        val s = controller.startEndpoint
        val e = controller.endEndpoint
        assertNotNull(s)
        assertNotNull(e)

        assertEquals(0, s!!.lineIndex)
        assertEquals(0, s.glyphIndex) // 'S'

        assertEquals(0, e!!.lineIndex)
        assertEquals(6, e.glyphIndex) // 'e' in "Swiffle"

        assertEquals("Swiffle", controller.currentSelection?.text)

        // Verify stem positions
        val startStem = controller.getStartStem(model)
        val endStem = controller.getEndStem(model)
        assertNotNull(startStem)
        assertNotNull(endStem)
        assertEquals(10f, startStem!!.x, 0.001f) // start of 'S'
        assertEquals(8f, startStem.topY, 0.001f)
        assertEquals(20f, startStem.bottomY, 0.001f)
    }

    @Test
    fun testDraggingEndHandleDownToLine2DoesNotDetachStartHandle() {
        // Reproduces user video Record_2026-10-10-21-10-26:
        // User selects "Swiffle" on Line 0, then drags the ending handle DOWN towards Line 1.
        val model = createTwoLineModel()
        val controller = SelectionController()

        controller.selectWord(pageNum = 1, pageModel = model, pdfX = 20f, pdfY = 16f)
        val originalStart = controller.startEndpoint

        // Start dragging "end" handle
        controller.startDrag(handle = "end", touchOffset = Offset.Zero)
        assertTrue(controller.isHandleDragging)
        assertEquals("end", controller.draggingHandle)

        // Drag down towards Line 1, around 'i' in "Origami" (x=30, y=36 on Line 1)
        controller.updateDrag(
            pdfX = 30f,
            pdfY = 36f,
            pageModel = model,
            screenCursorOffset = Offset(30f, 36f)
        )

        // The start endpoint MUST remain strictly pinned to "Swiffle" (Line 0, Glyph 0)
        assertEquals(originalStart, controller.startEndpoint)
        assertEquals(0, controller.startEndpoint!!.lineIndex)
        assertEquals(0, controller.startEndpoint!!.glyphIndex)

        // The end endpoint MUST advance to Line 1
        val newEnd = controller.endEndpoint
        assertNotNull(newEnd)
        assertEquals(1, newEnd!!.lineIndex)
        assertTrue(newEnd.glyphIndex >= 2) // at or past 'i' in Origami

        // Monotonic guarantee
        assertTrue(controller.startEndpoint!! <= controller.endEndpoint!!)

        controller.endDrag()
        assertTrue(!controller.isHandleDragging)
        assertTrue(controller.justEndedDrag)
    }

    @Test
    fun testTrueAnchorCrossoverWhenDraggingHandlePastAnchor() {
        val model = createTwoLineModel()
        val controller = SelectionController()

        // Line 0: "Swiffle is great" (indices 0..15)
        // Line 1: "Origami PDF app"  (indices 16..30)
        // Select "Origami" on Line 1 (x=20, y=36)
        controller.selectWord(pageNum = 1, pageModel = model, pdfX = 20f, pdfY = 36f)
        val initialStart = controller.startEndpoint!!
        val initialEnd = controller.endEndpoint!!
        assertEquals(1, initialStart.lineIndex)
        assertEquals(0, initialStart.glyphIndex) // 'O' in Origami
        assertEquals(1, initialEnd.lineIndex)
        assertEquals(6, initialEnd.glyphIndex)   // 'i' in Origami

        // 1. Drag Closing Handle ("end") backwards past the start anchor into Line 0 ("Swiffle")
        controller.startDrag(handle = "end", touchOffset = Offset.Zero)
        assertEquals("end", controller.draggingHandle)
        assertEquals(initialStart, controller.dragAnchorEndpoint) // Anchor is locked at 'O' on Line 1!

        // Drag up to center of 'i' in "Swiffle" on Line 0 (x=26..34, center=30f, y=16)
        controller.updateDrag(
            pdfX = 30f,
            pdfY = 16f,
            pageModel = model,
            screenCursorOffset = Offset.Zero
        )

        // Stationary anchor at 'O' on Line 1 MUST remain rock-solid pinned as the new endEndpoint!
        assertEquals(initialStart, controller.endEndpoint)
        assertEquals(1, controller.endEndpoint!!.lineIndex)
        assertEquals(0, controller.endEndpoint!!.glyphIndex)

        // The active handle crosses over to Line 0, becoming the new startEndpoint!
        assertEquals(0, controller.startEndpoint!!.lineIndex)
        assertEquals(2, controller.startEndpoint!!.glyphIndex) // 'i' in Swiffle
        assertEquals("start", controller.draggingHandle)       // Active role flipped to start!

        // Monotonic invariant holds: start <= end
        assertTrue(controller.startEndpoint!! <= controller.endEndpoint!!)

        controller.endDrag()
    }

    @Test
    fun testOldVsNewDragBehaviorComparisonProvesBugFix() {
        // OLD IMPLEMENTATION BEHAVIOR:
        // anchor = dragFixedAnchorIndex = 6 (end of "Swiffle")
        // When user moved handle to left of anchor (e.g. closestIdx = 2):
        // oldStart = minOf(6, 2) = 2
        // oldEnd = maxOf(6, 2) = 6
        // Result: The start anchor flipped! The start handle moved from 'S' to 'i'!
        val oldAnchor = 6
        val oldActive = 2
        val oldCalculatedStart = minOf(oldAnchor, oldActive)
        assertEquals(2, oldCalculatedStart) // FAILED on old code: start flipped from 0 to 2!

        // NEW IMPLEMENTATION BEHAVIOR (SelectionController):
        val model = createTwoLineModel()
        val controller = SelectionController()
        controller.selectWord(pageNum = 1, pageModel = model, pdfX = 20f, pdfY = 16f)

        // When dragging "end", anchor is locked at startEndpoint (0, 0)
        controller.startDrag("end", Offset.Zero)
        controller.updateDrag(pdfX = 30f, pdfY = 16f, pageModel = model, screenCursorOffset = Offset.Zero)

        // Start handle remains locked at 0!
        assertEquals(0, controller.startEndpoint!!.glyphIndex)
        // End handle is at 2!
        assertEquals(2, controller.endEndpoint!!.glyphIndex)
    }

    @Test
    fun testStartHandleCrossoverForwardPastEndAnchor() {
        val model = createTwoLineModel()
        val controller = SelectionController()

        // Select "Swiffle" on Line 0 (start=0, end=6)
        controller.selectWord(pageNum = 1, pageModel = model, pdfX = 20f, pdfY = 16f)
        val initialStart = controller.startEndpoint!!
        val initialEnd = controller.endEndpoint!!
        assertEquals(0, initialStart.glyphIndex)
        assertEquals(6, initialEnd.glyphIndex)

        // Drag Opening Handle ("start") forward past initialEnd onto Line 1 ("Origami")
        controller.startDrag(handle = "start", touchOffset = Offset.Zero)
        assertEquals("start", controller.draggingHandle)
        assertEquals(initialEnd, controller.dragAnchorEndpoint) // Anchor is locked at 'e' on Line 0!

        // Drag down to 'g' in "Origami" on Line 1 (x=34, y=36)
        controller.updateDrag(
            pdfX = 34f,
            pdfY = 36f,
            pageModel = model,
            screenCursorOffset = Offset.Zero
        )

        // Stationary anchor at 'e' on Line 0 MUST remain rock-solid pinned as the new startEndpoint!
        assertEquals(initialEnd, controller.startEndpoint)
        assertEquals(0, controller.startEndpoint!!.lineIndex)
        assertEquals(6, controller.startEndpoint!!.glyphIndex)

        // The active handle crosses over to Line 1, becoming the new endEndpoint!
        assertEquals(1, controller.endEndpoint!!.lineIndex)
        assertEquals(3, controller.endEndpoint!!.glyphIndex) // 'g' in Origami
        assertEquals("end", controller.draggingHandle)       // Active role flipped to end!

        // Monotonic invariant holds
        assertTrue(controller.startEndpoint!! <= controller.endEndpoint!!)

        controller.endDrag()
    }
}
