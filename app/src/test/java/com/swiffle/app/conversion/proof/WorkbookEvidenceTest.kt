package com.swiffle.app.conversion.proof

import com.swiffle.app.conversion.proof.excel.workbookRows
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.kxml2.io.KXmlParser
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorkbookEvidenceTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun sparseAndTypedCells() {
        val source = temp.newFile()
        val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        ZipOutputStream(source.outputStream()).use { out ->
            fun part(name: String, xml: String) {
                out.putNextEntry(ZipEntry(name))
                out.write(xml.toByteArray()); out.closeEntry()
            }
            part("xl/_rels/workbook.xml.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$rel/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            part("xl/workbook.xml","""<workbook xmlns="$main" xmlns:r="$rel"><sheets><sheet name="FY" sheetId="1" state="veryHidden" r:id="rId1"/></sheets></workbook>""")
            part("xl/sharedStrings.xml","""<sst xmlns="$main"><si><r><t>0000</t></r><r><t>123</t></r></si></sst>""")
            part("xl/worksheets/sheet1.xml","""<worksheet xmlns="$main"><sheetData><row r="1048576"><c r="A1048576" t="s"><v>0</v></c><c r="B1048576"/><c r="C1048576" t="inlineStr"><is><t></t></is></c><c r="D1048576"><v>12345678901234567890</v></c><c r="E1048576" t="e"><f>1/0</f><v>#DIV/0!</v></c><c r="XFD1048576"><v>0</v></c></row></sheetData></worksheet>""")
        }
        val row = workbookRows(source,temp.newFolder()) { KXmlParser() }.single()
        assertEquals(listOf("0000123",null,"","12345678901234567890","#DIV/0!","0"),row.map { it.valueTyped })
        assertEquals("1/0",row[4].formula)
        assertEquals("#DIV/0!",row[4].cachedValue)
        assertEquals("XFD",row.last().columnKey)
        assertEquals("XFD1048576",row.last().location)
    }
}
