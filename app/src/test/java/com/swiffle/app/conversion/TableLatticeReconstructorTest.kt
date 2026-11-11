package com.swiffle.app.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableLatticeReconstructorTest {

    @Test
    fun testReconstructEmptyTokensReturnsEmptyGrid() {
        val grid = TableLatticeReconstructor.reconstructGrid(
            tokens = emptyList(),
            pageWidth = 1000f,
            pageHeight = 1500f
        )
        assertEquals(0, grid.rowCount)
        assertEquals(0, grid.columnCount)
    }

    @Test
    fun testMultiWordCellPreservationAndColumnAlignment() {
        // Mock a 3-column invoice table:
        // Col 0: Description (multi-word phrases!) [x: 50 to 350]
        // Col 1: Quantity (single numeric)        [x: 450 to 520]
        // Col 2: Total Price (currency)          [x: 650 to 750]

        val tokens = listOf(
            // Row 0 (Header, Y ~ 100)
            OcrToken("Item", LatticeRect(50f, 95f, 100f, 115f)),
            OcrToken("Description", LatticeRect(110f, 95f, 230f, 115f)),
            OcrToken("Qty", LatticeRect(450f, 95f, 490f, 115f)),
            OcrToken("Total", LatticeRect(650f, 95f, 710f, 115f)),

            // Row 1 (Item 1, Y ~ 140)
            OcrToken("Heavy", LatticeRect(50f, 135f, 110f, 155f)),
            OcrToken("Duty", LatticeRect(115f, 135f, 160f, 155f)),
            OcrToken("Hex", LatticeRect(165f, 135f, 205f, 155f)),
            OcrToken("Bolts", LatticeRect(210f, 135f, 260f, 155f)),
            OcrToken("25", LatticeRect(460f, 135f, 485f, 155f)),
            OcrToken("$125.00", LatticeRect(650f, 135f, 730f, 155f)),

            // Row 2 (Item 2, Y ~ 180)
            OcrToken("Titanium", LatticeRect(50f, 175f, 140f, 195f)),
            OcrToken("Washer", LatticeRect(145f, 175f, 220f, 195f)),
            OcrToken("100", LatticeRect(455f, 175f, 490f, 195f)),
            OcrToken("$450.50", LatticeRect(650f, 175f, 730f, 195f))
        )

        val grid = TableLatticeReconstructor.reconstructGrid(
            tokens = tokens,
            pageWidth = 800f,
            pageHeight = 1200f
        )

        // Verify rows count: 3 rows (Header + 2 items)
        assertEquals(3, grid.rowCount)
        assertTrue(grid.columnCount >= 3)

        // Verify Row 0 (Header)
        val headerRow = grid.rows[0]
        assertTrue(headerRow.isHeaderCandidate)
        val headerDescCell = headerRow.cells.first()
        assertEquals("Item Description", headerDescCell.rawText)
        assertEquals(CellDataType.TEXT, headerDescCell.dataType)

        // Verify Row 1: "Heavy Duty Hex Bolts" must be kept in ONE cell, not 4 separate columns!
        val item1Row = grid.rows[1]
        val descCell = item1Row.cells[0]
        assertEquals("Heavy Duty Hex Bolts", descCell.rawText)
        assertEquals(CellDataType.TEXT, descCell.dataType)

        // Verify Qty is typed as NUMERIC
        val qtyCell = item1Row.cells[1]
        assertEquals("25", qtyCell.rawText)
        assertEquals(CellDataType.NUMERIC, qtyCell.dataType)
        assertEquals(25.0, qtyCell.numericValue!!, 0.001)

        // Verify Price is typed as CURRENCY with numeric extraction
        val priceCell = item1Row.cells[2]
        assertEquals("$125.00", priceCell.rawText)
        assertEquals(CellDataType.CURRENCY, priceCell.dataType)
        assertEquals("$", priceCell.currencySymbol)
        assertEquals(125.0, priceCell.numericValue!!, 0.001)

        // Verify Row 2
        val item2Row = grid.rows[2]
        assertEquals("Titanium Washer", item2Row.cells[0].rawText)
        assertEquals(100.0, item2Row.cells[1].numericValue!!, 0.001)
        assertEquals(450.50, item2Row.cells[2].numericValue!!, 0.001)
    }

    @Test
    fun testPercentageAndFloatInference() {
        val tokens = listOf(
            // Header
            OcrToken("Rate", LatticeRect(50f, 100f, 100f, 120f)),
            OcrToken("Discount", LatticeRect(300f, 100f, 400f, 120f)),

            // Row 1
            OcrToken("15.75", LatticeRect(50f, 140f, 110f, 160f)),
            OcrToken("12.5%", LatticeRect(300f, 140f, 360f, 160f))
        )

        val grid = TableLatticeReconstructor.reconstructGrid(tokens, 600f, 800f)
        assertEquals(2, grid.rowCount)

        val dataRow = grid.rows[1]
        val rateCell = dataRow.cells[0]
        assertEquals(CellDataType.NUMERIC, rateCell.dataType)
        assertEquals(15.75, rateCell.numericValue!!, 0.001)

        val discountCell = dataRow.cells[1]
        assertEquals(CellDataType.PERCENTAGE, discountCell.dataType)
        assertEquals(0.125, discountCell.numericValue!!, 0.0001)
    }
}

