package com.swiffle.app.conversion.proof.pdf

import com.swiffle.app.conversion.MarkdownIpPrivacy
import com.swiffle.app.conversion.PdfVectorRulingExtractor
import com.swiffle.app.conversion.LatticeTableRegion
import com.swiffle.app.conversion.proof.core.*
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.Writer

internal data class Position(val id: String,val text: String,val x: Float,val y: Float,
    val width: Float,val height: Float,val upright: Boolean = true) {
    val box get() = listOf(x,y,width,height).joinToString(",")
}
private data class Slot(val table: Int,val row: Int,val column: Int)
internal fun layoutPage(page: Int, positions: List<Position>, tables: List<LatticeTableRegion>): List<List<Cell>> {
    val digest = ProvenanceDigest()
    val slots = mutableMapOf<Slot,MutableList<Position>>()
    val outside = mutableListOf<Position>()
    val safeText = mutableMapOf<String,String>()
    for (line in positions.groupBy { it.y }.values) {
        val sorted = line.sortedBy { it.x }
        val pieces = MarkdownIpPrivacy.redactPieces(sorted.map { it.text })
        sorted.forEachIndexed { i, glyph -> if (glyph.text != pieces[i]) safeText[glyph.id] = pieces[i] }
    }
    for (original in positions) {
        val pos = safeText[original.id]?.let { original.copy(text = it) } ?: original
        val cx = pos.x + pos.width / 2
        val cy = pos.y - pos.height / 2
        var match: Slot? = null
        if (pos.upright) for ((t, table) in tables.withIndex()) {
            if (cx < table.left || cx >= table.right || cy < table.top || cy >= table.bottom) continue
            val r = table.rows.indexOfFirst { cy >= it.yTop && cy < it.yBottom }
            if (r < 0) continue
            val col = table.rows[r].cells.indexOfFirst { cx >= it.x1 && cx < it.x2 }
            if (col < 0) continue
            require(match == null) { "Ambiguous table assignment on page " + page + " at " + pos.box }
            match = Slot(t,r,col)
        }
        if (match == null) outside += pos else slots.getOrPut(match) { mutableListOf() }.add(pos)
    }
    val ordered = mutableListOf<Pair<Float,List<Cell>>>()
    fun cell(id: String,box: String,region: String,label: String,col: String,glyphs: List<Position>): Cell {
        val tokens = glyphs.sortedWith(compareBy<Position> { it.y }.thenBy { it.x }).map { SourceToken(it.id,it.text,it.box) }
        val raw = if (tokens.isEmpty()) null else tokens.joinToString("") { it.text }
        return Cell(id,"page " + page,box,region,label,col,raw,raw,raw ?: "∅",
            if(raw == null) CellType.EMPTY else CellType.TEXT,digest.hash(id + "/" + box + "/" + tokens),tokens = tokens)
    }
    for ((t,table) in tables.withIndex()) for ((r,band) in table.rows.withIndex()) {
        val row = band.cells.mapIndexed { c, slot ->
            val id = "p$page/t$t/r$r/c$c"
            val col = "column " + (slot.colStart + 1) + " (span " + slot.colSpan + ")"
            cell(id,listOf(slot.x1,band.yTop,slot.x2-slot.x1,band.yBottom-band.yTop).joinToString(","),
                "ruled table " + (t+1),"row " + (r+1),col,slots[Slot(t,r,c)].orEmpty())
        }
        if(row.isNotEmpty()) ordered += band.yTop to row
    }
    for(pos in outside) ordered += pos.y to listOf(cell(pos.id,pos.box,"text layer","","",listOf(pos)))
    val result = ordered.sortedBy { it.first }.map { it.second }
    val seen = HashSet<String>(positions.size)
    for(row in result) for(c in row) for(token in c.tokens) require(seen.add(token.id)) { "Duplicated " + token.id }
    require(seen.size == positions.size) { "Unassigned text position on page " + page }
    return result
}
internal fun pdfRows(source: File): Sequence<List<Cell>> = sequence {
    PDDocument.load(source,MemoryUsageSetting.setupTempFileOnly()).use { doc ->
        for (page in 1..doc.numberOfPages) {
            val glyphs = mutableListOf<Position>()
            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(pos: TextPosition) {
                    val value = pos.unicode ?: error("Undecodable glyph on page " + page)
                    glyphs += Position("p$page/g" + glyphs.size,value,pos.xDirAdj,pos.yDirAdj,pos.widthDirAdj,pos.heightDir,pos.dir == 0f)
                }
            }
            stripper.startPage = page
            stripper.endPage = page
            stripper.writeText(doc,object: Writer() {
                override fun write(cbuf: CharArray, off: Int, len: Int) {}
                override fun flush() {}
                override fun close() {}
            })
            require(glyphs.isNotEmpty()) { "Page " + page + " has no text layer. OCR evidence unavailable." }
            val geometry = PdfVectorRulingExtractor.extractPageGeometry(doc.getPage(page-1))
            yieldAll(layoutPage(page,glyphs,geometry.latticeTables.filterNot { it.isSemiLattice }))
        }
    }
}
