package com.swiffle.app.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExcelFormulaEvaluatorTest {

    @Test
    fun testSumFormula() {
        val sheet = MutableExcelSheet(
            name = "Test",
            headers = mutableListOf("Col A", "Col B"),
            rows = mutableListOf(
                mutableListOf("10", "5"),
                mutableListOf("20", "15"),
                mutableListOf("30", "25")
            )
        )

        val result = ExcelFormulaEvaluator.evaluateCell("=SUM(A1:A3)", sheet)
        assertEquals("60", result)
    }

    @Test
    fun testAverageFormula() {
        val sheet = MutableExcelSheet(
            name = "Test",
            headers = mutableListOf("Col A"),
            rows = mutableListOf(
                mutableListOf("10"),
                mutableListOf("20"),
                mutableListOf("30")
            )
        )

        val result = ExcelFormulaEvaluator.evaluateCell("=AVERAGE(A1:A3)", sheet)
        assertEquals("20", result)
    }

    @Test
    fun testMinMaxFormula() {
        val sheet = MutableExcelSheet(
            name = "Test",
            headers = mutableListOf("Col A"),
            rows = mutableListOf(
                mutableListOf("15"),
                mutableListOf("3"),
                mutableListOf("42")
            )
        )

        assertEquals("3", ExcelFormulaEvaluator.evaluateCell("=MIN(A1:A3)", sheet))
        assertEquals("42", ExcelFormulaEvaluator.evaluateCell("=MAX(A1:A3)", sheet))
        assertEquals("3", ExcelFormulaEvaluator.evaluateCell("=COUNT(A1:A3)", sheet))
    }

    @Test
    fun testArithmeticExpression() {
        val sheet = MutableExcelSheet(
            name = "Test",
            headers = mutableListOf("A", "B"),
            rows = mutableListOf(
                mutableListOf("100", "25")
            )
        )

        assertEquals("125", ExcelFormulaEvaluator.evaluateCell("=A1+B1", sheet))
        assertEquals("4", ExcelFormulaEvaluator.evaluateCell("=A1/B1", sheet))
    }
}

