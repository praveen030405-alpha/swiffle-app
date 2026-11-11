package com.swiffle.app.ui.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryCacheTest {

    @Test
    fun testFlatPageGeometryLookup() {
        // Construct 3 sample character boxes:
        // Char 0: 'A' at [10, 20, 8, 12] (center cx=14, cy=14)
        // Char 1: 'B' at [30, 20, 8, 12] (center cx=34, cy=14)
        // Char 2: 'C' at [50, 20, 8, 12] (center cx=54, cy=14)
        val coords = floatArrayOf(
            10f, 20f, 8f, 12f,
            30f, 20f, 8f, 12f,
            50f, 20f, 8f, 12f
        )
        val text = "ABC"
        val geo = FlatPageGeometry(
            pageNum = 0,
            text = text,
            coords = coords,
            count = 3
        )

        assertEquals(3, geo.count)
        assertEquals("B", geo.getChar(1))
        assertEquals(30f, geo.getX(1), 0.001f)
        assertEquals(20f, geo.getY(1), 0.001f)
        assertEquals(8f, geo.getW(1), 0.001f)
        assertEquals(12f, geo.getH(1), 0.001f)

        // Point near Char 1 center (34, 14)
        val idx = geo.findClosestCharIndex(34f, 14f, maxDistance = 20f)
        assertEquals(1, idx)

        // Point near Char 2 center (54, 14)
        val idx2 = geo.findClosestCharIndex(53f, 15f, maxDistance = 20f)
        assertEquals(2, idx2)

        // Test toCharRectList
        val rects = geo.toCharRectList()
        assertEquals(3, rects.size)
        assertEquals("A", rects[0].char)
        assertEquals(10f, rects[0].x, 0.001f)
        assertEquals(20f, rects[0].y, 0.001f)
        assertEquals(8f, rects[0].w, 0.001f)
        assertEquals(12f, rects[0].h, 0.001f)
    }

    @Test
    fun testEmptyGeometry() {
        val empty = FlatPageGeometry(1, "", FloatArray(0), 0)
        assertEquals(0, empty.count)
        assertEquals(-1, empty.findClosestCharIndex(10f, 10f))
        assertEquals(0, empty.toCharRectList().size)
    }
}
