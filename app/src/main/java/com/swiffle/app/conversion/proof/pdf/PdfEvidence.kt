package com.swiffle.app.conversion.proof.pdf

import com.swiffle.app.conversion.proof.core.*
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.Writer
import java.security.MessageDigest

// Raw text-layer evidence. Logical row and header assignment requires a separate consensus gate.
internal fun pdfRows(source: File): Sequence<List<Cell>> = sequence {
    PDDocument.load(source,MemoryUsageSetting.setupTempFileOnly()).use { doc ->
        for (page in 1..doc.numberOfPages) {
            val cells = mutableListOf<Cell>()
            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(pos: TextPosition) {
                    val value = pos.unicode ?: error("Undecodable glyph on page " + page)
                    val box = listOf(pos.xDirAdj,pos.yDirAdj,pos.widthDirAdj,pos.heightDir).joinToString(",")
                    val id = "p" + page + "/" + cells.size
                    val sha = MessageDigest.getInstance("SHA-256").digest((id + "/" + box + "/" + value).toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    cells += Cell(id,"page " + page,box,"text layer","","",value,value,value,CellType.TEXT,sha)
                }
            }
            stripper.startPage = page
            stripper.endPage = page
            stripper.writeText(doc,object: Writer() {
                override fun write(cbuf: CharArray, off: Int, len: Int) {}
                override fun flush() {}
                override fun close() {}
            })
            require(cells.isNotEmpty()) { "Page " + page + " has no text layer. OCR evidence unavailable." }
            cells.sortWith(compareBy<Cell> { it.location.substringAfter(',').substringBefore(',').toFloat() }
                .thenBy { it.location.substringBefore(',').toFloat() })
            for (cell in cells) yield(listOf(cell))
        }
    }
}
