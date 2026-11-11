package com.swiffle.app.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticTableStitcherTest {

    @Test
    fun testMultiLineHeaderSynthesis() {
        val columns = listOf(
            LatticeColumn(0, 0f, 100f, 100f),
            LatticeColumn(1, 100f, 300f, 200f),
            LatticeColumn(2, 300f, 450f, 150f)
        )

        val headerRow1 = LatticeRow(
            rowIndex = 0,
            cells = listOf(
                LatticeCell(0, 0, "Item", CellDataType.TEXT, isHeader = true, bounds = LatticeRect(10f, 10f, 80f, 30f)),
                LatticeCell(0, 1, "Description", CellDataType.TEXT, isHeader = true, bounds = LatticeRect(110f, 10f, 280f, 30f)),
                LatticeCell(0, 2, "Scope", CellDataType.TEXT, isHeader = true, bounds = LatticeRect(310f, 10f, 400f, 30f))
            ),
            baselineY = 20f,
            topY = 10f,
            bottomY = 30f,
            isHeaderCandidate = true
        )

        val headerRow2 = LatticeRow(
            rowIndex = 1,
            cells = listOf(
                LatticeCell(1, 0, "No.", CellDataType.TEXT, isHeader = true, bounds = LatticeRect(10f, 35f, 50f, 55f)),
                LatticeCell(1, 1, "of Items", CellDataType.TEXT, isHeader = true, bounds = LatticeRect(110f, 35f, 220f, 55f)),
                LatticeCell(1, 2, "Qty", CellDataType.TEXT, isHeader = true, bounds = LatticeRect(310f, 35f, 380f, 55f))
            ),
            baselineY = 45f,
            topY = 35f,
            bottomY = 55f,
            isHeaderCandidate = true
        )

        val synthesized = SemanticTableStitcher.synthesizeHeader(listOf(headerRow1, headerRow2), columns)

        assertEquals("Item No.", synthesized[0])
        assertEquals("Description of Items", synthesized[1])
        assertEquals("Scope Qty", synthesized[2])
    }

    @Test
    fun testMultiPageContinuousStitching() {
        val columns = listOf(
            LatticeColumn(0, 0f, 100f, 100f),
            LatticeColumn(1, 100f, 300f, 200f)
        )

        // Page 1 Grid with Header + 2 data rows
        val p1Header = LatticeRow(
            0,
            listOf(
                LatticeCell(0, 0, "Item No.", CellDataType.TEXT, isHeader = true, bounds = LatticeRect(0f, 0f, 50f, 20f)),
                LatticeCell(0, 1, "Description", CellDataType.TEXT, isHeader = true, bounds = LatticeRect(100f, 0f, 250f, 20f))
            ),
            10f, 0f, 20f, true
        )
        val p1Row1 = LatticeRow(
            1,
            listOf(
                LatticeCell(1, 0, "1.1", CellDataType.TEXT, bounds = LatticeRect(0f, 25f, 40f, 45f)),
                LatticeCell(1, 1, "Excavation", CellDataType.TEXT, bounds = LatticeRect(100f, 25f, 200f, 45f))
            ),
            35f, 25f, 45f, false
        )
        val gridPage1 = ReconstructedTableGrid(
            rows = listOf(p1Header, p1Row1),
            columns = columns,
            pageIndex = 0,
            pageWidth = 500f,
            pageHeight = 800f
        )

        // Page 2 Grid: continuation page with 1 data row
        val p2Row1 = LatticeRow(
            0,
            listOf(
                LatticeCell(0, 0, "1.2", CellDataType.TEXT, bounds = LatticeRect(0f, 25f, 40f, 45f)),
                LatticeCell(0, 1, "Concreting", CellDataType.TEXT, bounds = LatticeRect(100f, 25f, 200f, 45f))
            ),
            35f, 25f, 45f, false
        )
        val gridPage2 = ReconstructedTableGrid(
            rows = listOf(p2Row1),
            columns = columns,
            pageIndex = 1,
            pageWidth = 500f,
            pageHeight = 800f
        )

        val stitched = SemanticTableStitcher.stitchTables(listOf(gridPage1, gridPage2))

        // Must fuse both pages into 1 continuous table!
        assertEquals(1, stitched.size)
        val masterTable = stitched.first()
        assertEquals(listOf(0, 1), masterTable.sourcePageIndices)
        assertEquals(2, masterTable.rows.size)
        assertEquals("1.1", masterTable.rows[0][0].text)
        assertEquals("1.2", masterTable.rows[1][0].text)
    }
}

