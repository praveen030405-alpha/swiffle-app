package com.swiffle.app.conversion.proof

import com.swiffle.app.conversion.proof.excel.csvRows
import com.swiffle.app.conversion.proof.core.CellType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CsvEvidenceTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun quotedBlanksNewlinesAndIds() {
        val file = temp.newFile().apply { writeText("\uFEFF,\"\",00000000000000000001,\"x\r\ny\",\"he said \"\"hi\"\"\"\r\na,") }
        val rows = csvRows(file).toList()
        assertEquals(2,rows.size)
        assertEquals(CellType.EMPTY,rows[0][0].type)
        assertEquals("",rows[0][1].valueTyped)
        assertEquals("00000000000000000001",rows[0][2].valueTyped)
        assertEquals("x\r\ny",rows[0][3].valueTyped)
        assertEquals("he said \"hi\"",rows[0][4].valueTyped)
        assertEquals("B2",rows[1].last().location)
        assertEquals(CellType.EMPTY,rows[1].last().type)
    }
    @Test fun rejectsUnclosedQuote() {
        val file = temp.newFile().apply { writeText("x,\"unterminated") }
        assertTrue(runCatching { csvRows(file).toList() }.isFailure)
    }
}
