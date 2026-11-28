package com.swiffle.app.conversion.proof.excel

import android.util.Xml
import com.swiffle.app.conversion.proof.core.*
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.RandomAccessFile
import com.swiffle.app.conversion.proof.core.ProvenanceDigest
import java.util.zip.ZipFile

private fun XmlPullParser.attr(name: String) = getAttributeValue(null,name)
private fun column(address: String) = address.takeWhile { it in 'A'..'Z' }

// Shared-string offsets and UTF-8 text stay on disk, including for sparse/wide workbooks.
private class StringStore(dir: File) : java.io.Closeable {
    private val index = RandomAccessFile(File(dir,"offsets"),"rw")
    private val data = RandomAccessFile(File(dir,"strings"),"rw")
    fun add(value: String) {
        val bytes = value.toByteArray()
        index.writeLong(data.filePointer)
        data.writeInt(bytes.size)
        data.write(bytes)
    }
    fun get(id: Long): String {
        require(id >= 0 && id < index.length()/8)
        index.seek(id*8)
        data.seek(index.readLong())
        val bytes = ByteArray(data.readInt())
        data.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
    override fun close() { try { index.close() } finally { data.close() } }
}

internal fun workbookRows(source: File, scratch: File,
    newParser: () -> XmlPullParser = { Xml.newPullParser() }): Sequence<List<Cell>> = sequence {
    val digest = ProvenanceDigest()
    ZipFile(source).use { zip ->
        fun parser(input: java.io.InputStream) = newParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true); setInput(input,null) }
        fun entry(path: String) = requireNotNull(zip.getEntry(path)) { "Missing workbook part: " + path }
        val rels = mutableMapOf<String,String>()
        zip.getInputStream(entry("xl/_rels/workbook.xml.rels")).use { input ->
            val p = parser(input)
            while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                if (p.attr("TargetMode") != "External") {
                    val target = requireNotNull(p.attr("Target"))
                    val path = java.net.URI("/xl/workbook.xml").resolve(target).normalize().path.trimStart('/')
                    rels[requireNotNull(p.attr("Id"))] = path
                }
            }
        }
        val sheets = mutableListOf<Pair<String,String>>()
        zip.getInputStream(entry("xl/workbook.xml")).use { input ->
            val p = parser(input)
            while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                val id = (0 until p.attributeCount).first { p.getAttributeName(it) == "id" }
                sheets += requireNotNull(p.attr("name")) to rels.getValue(p.getAttributeValue(id))
            }
        }
        StringStore(scratch).use { strings ->
            zip.getEntry("xl/sharedStrings.xml")?.let { part -> zip.getInputStream(part).use { input ->
                val p = parser(input)
                val text = StringBuilder()
                var phonetic = false
                while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "si" -> text.setLength(0)
                        "rPh" -> phonetic = true
                        "t" -> if (!phonetic) text.append(p.nextText())
                    }
                    XmlPullParser.END_TAG -> when (p.name) {
                        "si" -> strings.add(text.toString())
                        "rPh" -> phonetic = false
                    }
                }
            } }
            for ((sheet,path) in sheets) zip.getInputStream(entry(path)).use { input ->
                val p = parser(input)
                var row = mutableListOf<Cell>()
                var address = ""
                var kind: String? = null
                var raw: String? = null
                var formula: String? = null
                var phonetic = false
                val inline = StringBuilder()
                while (p.next() != XmlPullParser.END_DOCUMENT) when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "row" -> row = mutableListOf()
                        "c" -> {
                            address = requireNotNull(p.attr("r")) { "Missing address in " + sheet }
                            kind = p.attr("t"); raw = null; formula = null; inline.setLength(0)
                        }
                        "v" -> raw = p.nextText()
                        "f" -> formula = p.nextText()
                        "rPh" -> phonetic = true
                        "t" -> if (kind == "inlineStr" && !phonetic) inline.append(p.nextText())
                    }
                    XmlPullParser.END_TAG -> when (p.name) {
                        "rPh" -> phonetic = false
                        "c" -> {
                            val value = when (kind) {
                                "s" -> strings.get(requireNotNull(raw).toLong())
                                "inlineStr" -> inline.toString()
                                "b" -> when(raw) { "0" -> "false"; "1" -> "true"; else -> error(sheet + "!" + address) }
                                else -> raw
                            }
                            val type = when {
                                value == null -> CellType.EMPTY
                                kind == "e" -> CellType.ERROR
                                kind == "b" -> CellType.BOOLEAN
                                kind == null || kind == "n" -> CellType.DECIMAL
                                else -> CellType.TEXT
                            }
                            val id = sheet + "!" + address
                            val evidence = listOf(path,address,kind,raw,formula,value).joinToString("") { v -> v?.let { it.length.toString() + ":" + it } ?: "-" }
                            val hash = digest.hash(evidence)
                            row += Cell(id,sheet,address,"worksheet","",column(address),
                                if(kind == "inlineStr") value else raw, value,value ?: "∅",type,hash,formula,if(formula != null) raw else null)
                        }
                        "row" -> if (row.isNotEmpty()) yield(row)
                    }
                }
            }
        }
    }
}
