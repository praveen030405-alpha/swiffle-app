package com.swiffle.app.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableReinforcementAgentTest {

    @Test
    fun testExtractStateZeroTokensReturnsSafeDefaults() {
        val state = TableReinforcementAgent.extractState(emptyList(), 1000f, 1500f)
        assertEquals(0f, state.tokenDensity, 0.001f)
        assertEquals(0f, state.numericRatio, 0.001f)
    }

    @Test
    fun testExtractStateCalculatesAccurateFeatures() {
        val tokens = listOf(
            OcrToken("Item", LatticeRect(50f, 100f, 100f, 120f)),
            OcrToken("Price", LatticeRect(200f, 100f, 250f, 120f)),
            OcrToken("Bolt", LatticeRect(50f, 140f, 90f, 160f)),
            OcrToken("$45.00", LatticeRect(200f, 140f, 260f, 160f))
        )

        val state = TableReinforcementAgent.extractState(tokens, 500f, 500f)
        assertTrue(state.tokenDensity > 0f)
        assertTrue(state.numericRatio >= 0.25f) // At least 1 out of 4 tokens is currency/numeric
        assertTrue(state.medianAspectRatio > 0f)
    }

    @Test
    fun testPredictActionWithoutContextReturnsDefaultAction() {
        val state = DocumentLayoutState(0.2f, 2.5f, 0.4f, 0.5f, 0.1f, 0.5f)
        val action = TableReinforcementAgent.predictAction(null, state)

        assertEquals(TableReinforcementAgent.DEFAULT_ACTION.sigmaFactor, action.sigmaFactor, 0.001f)
        assertEquals(TableReinforcementAgent.DEFAULT_ACTION.valleyThreshold, action.valleyThreshold, 0.001f)
        assertEquals(TableReinforcementAgent.DEFAULT_ACTION.rowToleranceFactor, action.rowToleranceFactor, 0.001f)
        assertEquals(TableReinforcementAgent.DEFAULT_ACTION.minCellOverlap, action.minCellOverlap, 0.001f)
    }

    @Test
    fun testEvaluateRewardHighForStructuredGrid() {
        // Build a clean 2x2 grid
        val row0 = LatticeRow(
            rowIndex = 0,
            cells = listOf(
                LatticeCell(0, 0, "Product", CellDataType.TEXT, isHeader = true),
                LatticeCell(0, 1, "Amount", CellDataType.TEXT, isHeader = true)
            ),
            baselineY = 100f,
            topY = 90f,
            bottomY = 110f,
            isHeaderCandidate = true
        )

        val row1 = LatticeRow(
            rowIndex = 1,
            cells = listOf(
                LatticeCell(1, 0, "Steel Rod", CellDataType.TEXT),
                LatticeCell(1, 1, "$85.00", CellDataType.CURRENCY, numericValue = 85.0)
            ),
            baselineY = 140f,
            topY = 130f,
            bottomY = 150f
        )

        val grid = ReconstructedTableGrid(
            rows = listOf(row0, row1),
            columns = listOf(
                LatticeColumn(0, 40f, 180f, 140f),
                LatticeColumn(1, 190f, 350f, 160f)
            ),
            pageWidth = 500f,
            pageHeight = 500f
        )

        val reward = TableReinforcementAgent.evaluateReward(grid)
        assertTrue("Expected reward to be high for structured grid, got $reward", reward >= 0.70f)
    }
}

