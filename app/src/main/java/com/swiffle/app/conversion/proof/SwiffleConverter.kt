package com.swiffle.app.conversion.proof

import android.content.Context
import com.swiffle.app.conversion.proof.core.*
import com.swiffle.app.conversion.proof.excel.csvRows
import com.swiffle.app.conversion.proof.excel.workbookRows
import com.swiffle.app.conversion.proof.pdf.pdfRows
import com.swiffle.app.conversion.proof.verify.VerifyBundle
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

internal object SwiffleConverter {
    fun convert(context: Context, source: File, workDir: File, displayName: String?, mime: String?,
                onRow: (Long) -> Unit = {}): Evidence {
        val header = source.inputStream().use { input -> ByteArray(8).also { input.read(it) } }
        val rows = when {
            header.copyOf(5).contentEquals("%PDF-".toByteArray()) -> {
                PDFBoxResourceLoader.init(context.applicationContext)
                pdfRows(source)
            }
            header[0] == 80.toByte() && header[1] == 75.toByte() -> workbookRows(source,workDir)
            header[0] == 0xd0.toByte() && header[1] == 0xcf.toByte() ->
                error("Legacy binary XLS is unsupported by this evidence preview.")
            mime in setOf("text/csv","text/comma-separated-values") || displayName?.endsWith(".csv",true) == true -> csvRows(source)
            else -> error("Select a text-layer PDF, XLSX workbook or UTF-8 CSV.")
        }
        var count = 0L
        val heapMiB = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val shardRows = (heapMiB * 4).coerceIn(500L,2000L).toInt()
        val target = File(workDir,"bundle")
        val cert = ProofBundle.write(source,target,rows.onEach { onRow(++count) },shardRows)
        try {
            VerifyBundle.checkBundle(target,source)
        } catch (e: Exception) {
            target.deleteRecursively()
            throw e
        }
        return cert
    }
}
