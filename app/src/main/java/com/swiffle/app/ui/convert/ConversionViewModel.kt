package com.swiffle.app.ui.convert

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiffle.app.conversion.ImageToExcelConverter
import com.swiffle.app.conversion.ImageToPdfConverter
import com.swiffle.app.conversion.PdfToExcelConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── State ───────────────────────────────────────────────────────────────────

sealed class ConversionState {
    data object Idle : ConversionState()
    data class Converting(val progress: String) : ConversionState()
    data class Success(
        val outputFile: File,
        val contentUri: Uri,
        val message: String,
        val formattedSize: String,
        val isPdf: Boolean
    ) : ConversionState()
    data class Error(val message: String) : ConversionState()
}

data class ImageItem(
    val uri: Uri,
    val id: Long = System.nanoTime(),
    val rotationDegrees: Float = 0f
)

data class RecentConversionItem(
    val uri: Uri,
    val file: File,
    val name: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val formattedSize: String,
    val formattedDate: String
)

// ── ViewModel ───────────────────────────────────────────────────────────────

class ConversionViewModel : ViewModel() {

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private val _quality = MutableStateFlow(ImageToPdfConverter.Quality.ULTRA)
    val quality: StateFlow<ImageToPdfConverter.Quality> = _quality.asStateFlow()

    private val _recentConversions = MutableStateFlow<List<RecentConversionItem>>(emptyList())
    val recentConversions: StateFlow<List<RecentConversionItem>> = _recentConversions.asStateFlow()

    private val _recentExcelConversions = MutableStateFlow<List<RecentConversionItem>>(emptyList())
    val recentExcelConversions: StateFlow<List<RecentConversionItem>> = _recentExcelConversions.asStateFlow()

    // ── Image list management ───────────────────────────────────────────────

    fun addImages(uris: List<Uri>) {
        _images.value = _images.value + uris.map { ImageItem(it) }
    }

    fun addImage(uri: Uri) {
        _images.value = _images.value + ImageItem(uri)
    }

    fun removeImage(id: Long) {
        _images.value = _images.value.filter { it.id != id }
    }

    fun rotateImage(id: Long) {
        _images.value = _images.value.map {
            if (it.id == id) it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f)
            else it
        }
    }

    fun replaceImage(id: Long, newUri: Uri) {
        _images.value = _images.value.map {
            if (it.id == id) it.copy(uri = newUri, rotationDegrees = 0f)
            else it
        }
    }

    /** Drag-reorder: swap item at [from] with item at [to] */
    fun reorderImages(from: Int, to: Int) {
        val list = _images.value.toMutableList()
        if (from < 0 || to < 0 || from >= list.size || to >= list.size) return
        val item = list.removeAt(from)
        list.add(to, item)
        _images.value = list
    }

    fun clearImages() {
        _images.value = emptyList()
    }

    fun setQuality(q: ImageToPdfConverter.Quality) {
        _quality.value = q
    }

    fun resetState() {
        _conversionState.value = ConversionState.Idle
    }

    // ── Helper: Output Directory ─────────────────────────────────────────────

    private fun getPublicOutputDir(context: Context): File {
        return try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val swiffleDir = File(docsDir, "Swiffle")
            if (!swiffleDir.exists()) {
                swiffleDir.mkdirs()
            }
            val testFile = File(swiffleDir, ".write_test_${System.currentTimeMillis()}")
            if (testFile.createNewFile()) {
                testFile.delete()
                swiffleDir
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Swiffle").apply { mkdirs() }
            }
        } catch (_: Exception) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Swiffle").apply { mkdirs() }
        }
    }

    private fun recordRecentFile(context: Context, uri: Uri, fileName: String, sizeBytes: Long) {
        try {
            val sharedPref = context.getSharedPreferences("SwifflePrefs", Context.MODE_PRIVATE)
            val existingJson = sharedPref.getString("recentFiles", "[]") ?: "[]"
            val currentArray = JSONArray(existingJson)

            val newArray = JSONArray()
            val newObj = JSONObject().apply {
                put("uri", uri.toString())
                put("name", fileName)
                put("timestamp", System.currentTimeMillis())
                put("size", sizeBytes)
            }
            newArray.put(newObj)

            for (i in 0 until currentArray.length()) {
                val item = currentArray.getJSONObject(i)
                if (item.optString("name") != fileName && newArray.length() < 15) {
                    newArray.put(item)
                }
            }
            sharedPref.edit().putString("recentFiles", newArray.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024        -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / 1024)
            else                 -> "$bytes B"
        }
    }

    fun loadRecentConversions(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val outDir = getPublicOutputDir(context)
            val files = outDir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.take(15) ?: emptyList()

            val sdf = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
            val items = files.mapNotNull { file ->
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    RecentConversionItem(
                        uri = uri,
                        file = file,
                        name = file.name,
                        timestamp = file.lastModified(),
                        sizeBytes = file.length(),
                        formattedSize = formatBytes(file.length()),
                        formattedDate = sdf.format(Date(file.lastModified()))
                    )
                } catch (_: Exception) {
                    null
                }
            }
            _recentConversions.value = items
        }
    }

    fun loadRecentExcelConversions(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val outDir = getPublicOutputDir(context)
            val files = outDir.listFiles { f -> f.extension.equals("xlsx", ignoreCase = true) || f.extension.equals("xls", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.take(15) ?: emptyList()

            val sdf = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
            val items = files.mapNotNull { file ->
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    RecentConversionItem(
                        uri = uri,
                        file = file,
                        name = file.name,
                        timestamp = file.lastModified(),
                        sizeBytes = file.length(),
                        formattedSize = formatBytes(file.length()),
                        formattedDate = sdf.format(Date(file.lastModified()))
                    )
                } catch (_: Exception) {
                    null
                }
            }
            _recentExcelConversions.value = items
        }
    }

    // ── Conversion actions ──────────────────────────────────────────────────

    fun convertImagesToPdf(
        context: Context,
        customName: String? = null,
        isLossless: Boolean = true,
        password: String? = null,
        enableAiOcr: Boolean = true
    ) {
        val currentImages = _images.value
        if (currentImages.isEmpty()) {
            _conversionState.value = ConversionState.Error("Add at least one photo or scan to convert.")
            return
        }
        val pages = currentImages.map { ImageToPdfConverter.PageInput(it.uri, it.rotationDegrees) }
        val quality = if (isLossless) ImageToPdfConverter.Quality.ULTRA else ImageToPdfConverter.Quality.STANDARD

        viewModelScope.launch {
            _conversionState.value = ConversionState.Converting("Initializing AI Neural OCR & PDF Stream…")
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val baseName = if (!customName.isNullOrBlank()) {
                customName.trim().removeSuffix(".pdf").removeSuffix(".PDF")
            } else {
                "Swiffle_Scan_${ts}"
            }
            val outDir = getPublicOutputDir(context)
            val outFile = File(outDir, "${baseName}.pdf")

            val result = ImageToPdfConverter.convertPages(
                pages = pages,
                context = context,
                outputFile = outFile,
                quality = quality,
                password = password,
                enableAiOcr = enableAiOcr,
                onProgress = { cur, total, stage ->
                    _conversionState.value = ConversionState.Converting("Page $cur of $total: $stage")
                }
            )
            result.fold(
                onSuccess = { r ->
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        r.file
                    )
                    val sizeFormatted = formatBytes(r.fileSizeBytes)
                    recordRecentFile(context, contentUri, r.file.name, r.fileSizeBytes)
                    loadRecentConversions(context)

                    _conversionState.value = ConversionState.Success(
                        outputFile = r.file,
                        contentUri = contentUri,
                        message = "PDF created successfully with ${r.pageCount} page(s).",
                        formattedSize = sizeFormatted,
                        isPdf = true
                    )
                },
                onFailure = { e ->
                    _conversionState.value = ConversionState.Error(e.message ?: "PDF generation failed. Please try again.")
                }
            )
        }
    }

    fun convertImagesToExcel(context: Context) {
        val uris = _images.value.map { it.uri }
        if (uris.isEmpty()) {
            _conversionState.value = ConversionState.Error("Select at least one document photo for OCR.")
            return
        }
        viewModelScope.launch {
            _conversionState.value = ConversionState.Converting("Extracting text and tables via ML OCR…")
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outDir = getPublicOutputDir(context)
            val outFile = File(outDir, "Swiffle_Table_${ts}.xlsx")

            val result = ImageToExcelConverter.convert(
                uris = uris,
                context = context,
                outputFile = outFile,
                onProgress = { cur, total ->
                    _conversionState.value = ConversionState.Converting("OCR: processing page $cur of $total…")
                }
            )
            result.fold(
                onSuccess = { r ->
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        r.file
                    )
                    val sizeFormatted = formatBytes(r.file.length())
                    recordRecentFile(context, contentUri, r.file.name, r.file.length())
                    loadRecentExcelConversions(context)

                    _conversionState.value = ConversionState.Success(
                        outputFile = r.file,
                        contentUri = contentUri,
                        message = "Excel table ready — ${r.rowCount} rows across ${r.sheetCount} sheet(s).",
                        formattedSize = sizeFormatted,
                        isPdf = false
                    )
                },
                onFailure = { e ->
                    _conversionState.value = ConversionState.Error(e.message ?: "Excel extraction failed")
                }
            )
        }
    }

    fun convertPdfToExcel(context: Context, pdfUri: Uri) {
        viewModelScope.launch {
            _conversionState.value = ConversionState.Converting("Rendering PDF vector pages…")
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outDir = getPublicOutputDir(context)
            val outFile = File(outDir, "Swiffle_Extracted_${ts}.xlsx")

            val result = PdfToExcelConverter.convert(
                pdfUri = pdfUri,
                context = context,
                outputFile = outFile,
                onProgress = { cur, total ->
                    _conversionState.value = ConversionState.Converting("OCR extraction: page $cur of $total…")
                }
            )
            result.fold(
                onSuccess = { r ->
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        r.file
                    )
                    val sizeFormatted = formatBytes(r.file.length())
                    recordRecentFile(context, contentUri, r.file.name, r.file.length())
                    loadRecentExcelConversions(context)

                    _conversionState.value = ConversionState.Success(
                        outputFile = r.file,
                        contentUri = contentUri,
                        message = "Excel created — ${r.rowCount} rows across ${r.sheetCount} sheet(s).",
                        formattedSize = sizeFormatted,
                        isPdf = false
                    )
                },
                onFailure = { e ->
                    _conversionState.value = ConversionState.Error(e.message ?: "PDF to Excel conversion failed")
                }
            )
        }
    }
}
