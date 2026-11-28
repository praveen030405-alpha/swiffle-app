package com.swiffle.app.ui.convert

import android.net.Uri
import android.provider.OpenableColumns
import com.swiffle.app.conversion.proof.excel.csvRows
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.swiffle.app.conversion.proof.core.ProofBundle
import com.swiffle.app.conversion.proof.excel.workbookRows
import com.swiffle.app.conversion.proof.pdf.pdfRows
import com.swiffle.app.conversion.proof.verify.VerifyBundle
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
internal fun ConversionEvidenceCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("Inspect PDF text positions, stored XLSX values or CSV fields offline. This preview checks bundle integrity. Source fidelity and AI readability are unmeasured.") }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { result?.deleteRecursively() }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val dir = result
        if (uri != null && dir != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val bundle = File(dir,"bundle")
                    VerifyBundle.checkBundle(bundle,File(dir,"source"))
                    ZipOutputStream(requireNotNull(ctx.contentResolver.openOutputStream(uri,"wt"))).use { out ->
                        bundle.walkTopDown().filter { it.isFile }.forEach { file ->
                            out.putNextEntry(ZipEntry(file.relativeTo(bundle).invariantSeparatorsPath))
                            file.inputStream().use { it.copyTo(out) }
                            out.closeEntry()
                        }
                    }
                }
                status = "Evidence ZIP saved."
            } catch (e: Exception) { status = e.message ?: "Export failed" }
            finally { busy = false }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            result?.deleteRecursively()
            result = null
            status = "Reading local document…"
            var stage: File? = null
            try {
                val message = withContext(Dispatchers.IO) {
                    val dir = Files.createTempDirectory(ctx.cacheDir.toPath(),"conversion-").toFile()
                    stage = dir
                    val source = File(dir,"source")
                    requireNotNull(ctx.contentResolver.openInputStream(uri)).use { input ->
                        source.outputStream().use { input.copyTo(it) }
                    }
                    val header = source.inputStream().use { val b = ByteArray(5); it.read(b); b }
                    val rows = if (header.contentEquals("%PDF-".toByteArray())) {
                        PDFBoxResourceLoader.init(ctx.applicationContext)
                        pdfRows(source)
                    } else if (ctx.contentResolver.getType(uri) == "text/csv" || ctx.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { it.moveToFirst() && it.getString(0).endsWith(".csv",true) } == true) {
                        csvRows(source)
                    } else {
                        require(header[0] == 80.toByte() && header[1] == 75.toByte()) { "Select a PDF or XLSX file" }
                        workbookRows(source,dir)
                    }
                    val job = currentCoroutineContext()
                    val memory = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                    val shardSize = (memory * 4).coerceIn(500L,2000L).toInt()
                    val cert = ProofBundle.write(source,File(dir,"bundle"),rows.onEach { job.ensureActive() },shardSize)
                    VerifyBundle.checkBundle(File(dir,"bundle"),source)
                    "Round trip passed for " + cert.shards.sumOf { it.cells } + " extracted records. Source fidelity unverified. Merkle root " + cert.merkleRoot
                }
                result?.deleteRecursively()
                result = stage
                stage = null
                status = message
            } catch (e: Exception) { status = e.message ?: "Conversion failed" }
            finally { stage?.deleteRecursively(); busy = false }
        }
    }
    Text("Conversion evidence preview",style = MaterialTheme.typography.titleMedium)
    Text(status)
    OutlinedButton(enabled = !busy,onClick = { pick.launch(arrayOf("text/csv","text/comma-separated-values","application/pdf","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) {
        Text("Inspect PDF, Excel or CSV")
    }
    if (result != null) OutlinedButton(enabled = !busy,onClick = { save.launch("Swiffle-evidence.zip") }) {
        Text("Verify and export evidence")
    }
}
