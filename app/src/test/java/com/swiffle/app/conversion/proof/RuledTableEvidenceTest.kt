package com.swiffle.app.conversion.proof

import com.swiffle.app.conversion.*
import com.swiffle.app.conversion.proof.pdf.*
import com.swiffle.app.conversion.proof.core.CellType
import org.junit.Assert.*
import org.junit.Test

class RuledTableEvidenceTest {
    private val table = LatticeTableRegion(0f,0f,40f,10f,listOf(0f,10f,20f,40f),
        listOf(LatticeRowBand(0f,10f,listOf(LatticeCellSlot(0f,10f,0,1),
        LatticeCellSlot(10f,20f,1,1),LatticeCellSlot(20f,40f,2,1)))))
    @Test fun positionAssignsValueAndConservesFooter() {
        val positions = listOf(Position("b","0",12f,8f,2f,4f),Position("a","Total",1f,8f,5f,4f),
            Position("z","Verifcation",3f,15f,12f,4f))
        val rows = layoutPage(1,positions,listOf(table))
        assertEquals(listOf("Total","0",null),rows.first().map { it.valueRaw })
        assertEquals(CellType.EMPTY,rows.first().last().type)
        assertEquals("Verifcation",rows.last().single().valueRaw)
        assertEquals(listOf("a","b","z"),rows.flatten().flatMap { it.tokens }.map { it.id })
    }
    @Test fun rejectsOverlappingOwnership() {
        assertTrue(runCatching { layoutPage(1,listOf(Position("a","1",1f,8f,2f,4f)),listOf(table,table)) }.isFailure)
    }
}
