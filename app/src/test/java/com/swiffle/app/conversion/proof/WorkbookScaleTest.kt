package com.swiffle.app.conversion.proof

import com.swiffle.app.conversion.proof.core.ProofBundle
import com.swiffle.app.conversion.proof.excel.workbookRows
import com.swiffle.app.conversion.proof.verify.VerifyBundle
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.kxml2.io.KXmlParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorkbookScaleTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun measureStreamingWorksheet() {
        val count = System.getenv("SWIFFLE_BENCH_ROWS")?.toIntOrNull()
        assumeTrue(count == 100000 || count == 1000000)
        val n = requireNotNull(count)
        val source = tmp.newFile("scale.xlsx")
        ZipOutputStream(source.outputStream().buffered()).use { zip ->
            fun part(name: String, data: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry()
            }
            val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
            val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            val pkg = "http://schemas.openxmlformats.org/package/2006/relationships"
            part("[Content_Types].xml","""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/rows.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            part("_rels/.rels","""<Relationships xmlns="$pkg"><Relationship Id="workbook" Type="$rel/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            part("xl/workbook.xml","""<workbook xmlns="$ns" xmlns:r="$rel"><sheets><sheet name="Ledger" sheetId="1" r:id="rows"/></sheets></workbook>""")
            part("xl/_rels/workbook.xml.rels","""<Relationships xmlns="$pkg"><Relationship Id="rows" Type="$rel/worksheet" Target="worksheets/rows.xml"/></Relationships>""")
            zip.putNextEntry(ZipEntry("xl/worksheets/rows.xml"))
            val writer = zip.bufferedWriter()
            writer.append("""<worksheet xmlns="$ns"><sheetData>""")
            for (i in 1..n) writer.append("""<row r="$i"><c r="A$i"><v>$i</v></c></row>""")
            writer.append("</sheetData></worksheet>"); writer.flush(); zip.closeEntry()
        }
        val begin = System.nanoTime()
        val target = File(tmp.root,"bundle")
        val cert = ProofBundle.write(source,target,workbookRows(source,tmp.root) { KXmlParser() },1000)
        assertEquals(n.toLong(),cert.shards.sumOf { it.cells })
        assertEquals(cert.merkleRoot,VerifyBundle.checkBundle(target,source))
        val elapsed = (System.nanoTime()-begin)/1000000
        val peak = File("/proc/self/status").useLines { lines -> lines.first { it.startsWith("VmHWM:") }.substringAfter(':').trim() }
        val report = File(requireNotNull(System.getenv("SWIFFLE_BENCH_REPORT")))
        report.writeText("Rows: $n\nColumns: 1 numeric\nElapsed conversion and verification: $elapsed ms\nPeak JVM process RSS: $peak\nHeap limit: " + Runtime.getRuntime().maxMemory() + " bytes\nSource file bytes: " + source.length() + "\n")
    }
}
