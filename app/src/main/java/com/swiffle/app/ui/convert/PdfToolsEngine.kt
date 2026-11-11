package com.swiffle.app.ui.convert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class CompressionLevel(val title: String, val subtitle: String, val scale: Float, val jpegQuality: Int) {
    MAX("Max Compression", "Small size, low quality", 0.50f, 40),
    MEDIUM("Medium Compression", "Medium size, medium quality", 0.72f, 65),
    LOW("Low Compression", "Max size, original quality", 1.0f, 85)
}

data class CompressionResult(
    val outputFile: File,
    val contentUri: Uri,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val reductionBytes: Long,
    val reductionPercent: Int,
    val pageCount: Int
)

data class MergeResult(
    val outputFile: File,
    val contentUri: Uri,
    val totalPages: Int,
    val fileSizeBytes: Long
)

data class PdfToJpgResult(
    val outputDir: File,
    val generatedImages: List<File>,
    val pageCount: Int
)

object PdfToolsEngine {

    private fun getSwiffleDocsDir(context: Context): File {
        return try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docs, "Swiffle")
            if (!dir.exists()) dir.mkdirs()
            dir
        } catch (_: Exception) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Swiffle").apply { mkdirs() }
        }
    }

    private fun getSwifflePicturesDir(context: Context): File {
        return try {
            val pics = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = File(pics, "Swiffle")
            if (!dir.exists()) dir.mkdirs()
            dir
        } catch (_: Exception) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Swiffle").apply { mkdirs() }
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024        -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / 1024)
            else                 -> "$bytes B"
        }
    }

    /**
     * Compress an existing PDF by re-encoding pages at target resolution and JPEG compression.
     */
    suspend fun compressPdf(
        context: Context,
        pdfUri: Uri,
        level: CompressionLevel,
        onProgress: (Int) -> Unit
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outputPdf = PdfDocument()

        try {
            pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext Result.failure(Exception("Cannot open PDF file descriptor"))

            val originalSize = pfd.statSize
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            if (pageCount == 0) {
                return@withContext Result.failure(Exception("Document has no pages"))
            }

            for (i in 0 until pageCount) {
                val progress = ((i.toFloat() / pageCount) * 90f).roundToInt()
                onProgress(progress)

                val page = renderer.openPage(i)
                val originalW = page.width
                val originalH = page.height

                // Render at scaled size
                val renderW = (originalW * level.scale).roundToInt().coerceAtLeast(200)
                val renderH = (originalH * level.scale).roundToInt().coerceAtLeast(200)

                val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                // Re-compress via JPEG stream
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, stream)
                val compressedBytes = stream.toByteArray()
                bitmap.recycle()

                val compressedBitmap = android.graphics.BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                // Add to output PdfDocument preserving original page dimensions
                val pageInfo = PdfDocument.PageInfo.Builder(originalW, originalH, i + 1).create()
                val docPage = outputPdf.startPage(pageInfo)
                val canvas = docPage.canvas

                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                canvas.drawBitmap(compressedBitmap, null, Rect(0, 0, originalW, originalH), paint)
                outputPdf.finishPage(docPage)
                compressedBitmap.recycle()
            }

            onProgress(95)
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outDir = getSwiffleDocsDir(context)
            val outFile = File(outDir, "Swiffle_Compressed_${ts}.pdf")

            FileOutputStream(outFile).use { fos ->
                outputPdf.writeTo(fos)
            }
            outputPdf.close()

            val compressedSize = outFile.length()
            val reduction = (originalSize - compressedSize).coerceAtLeast(0)
            val reductionPercent = if (originalSize > 0) ((reduction.toDouble() / originalSize) * 100).roundToInt() else 0

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )

            onProgress(100)

            Result.success(
                CompressionResult(
                    outputFile = outFile,
                    contentUri = contentUri,
                    originalSizeBytes = originalSize,
                    compressedSizeBytes = compressedSize,
                    reductionBytes = reduction,
                    reductionPercent = reductionPercent,
                    pageCount = pageCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { outputPdf.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Merge multiple PDF documents into a single document.
     */
    suspend fun mergePdfs(
        context: Context,
        pdfUris: List<Uri>,
        onProgress: (Int) -> Unit
    ): Result<MergeResult> = withContext(Dispatchers.IO) {
        if (pdfUris.isEmpty()) {
            return@withContext Result.failure(Exception("Select at least one PDF to merge"))
        }

        val outputPdf = PdfDocument()
        var totalPages = 0
        var globalIndex = 1

        try {
            for ((docIdx, uri) in pdfUris.withIndex()) {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: continue
                val renderer = try { PdfRenderer(pfd) } catch (_: Exception) { pfd.close(); continue }

                for (pageIdx in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIdx)
                    val w = page.width
                    val h = page.height

                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(w, h, globalIndex++).create()
                    val docPage = outputPdf.startPage(pageInfo)
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    outputPdf.finishPage(docPage)
                    bitmap.recycle()
                    totalPages++
                }

                renderer.close()
                pfd.close()

                val prog = (((docIdx + 1).toFloat() / pdfUris.size) * 90f).roundToInt()
                onProgress(prog)
            }

            if (totalPages == 0) {
                return@withContext Result.failure(Exception("No readable pages found in selected documents"))
            }

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outDir = getSwiffleDocsDir(context)
            val outFile = File(outDir, "Swiffle_Merged_${ts}.pdf")

            FileOutputStream(outFile).use { fos ->
                outputPdf.writeTo(fos)
            }
            outputPdf.close()

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )

            onProgress(100)

            Result.success(
                MergeResult(
                    outputFile = outFile,
                    contentUri = contentUri,
                    totalPages = totalPages,
                    fileSizeBytes = outFile.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { outputPdf.close() } catch (_: Exception) {}
        }
    }

    /**
     * Extract each PDF page as a high-resolution JPG image in Gallery.
     */
    suspend fun pdfToJpg(
        context: Context,
        pdfUri: Uri,
        onProgress: (Int) -> Unit
    ): Result<PdfToJpgResult> = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val generatedFiles = mutableListOf<File>()

        try {
            pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext Result.failure(Exception("Cannot open PDF file descriptor"))

            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outDir = File(getSwifflePicturesDir(context), "Swiffle_Export_${ts}").apply { mkdirs() }

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                // 1.5x scale for crisp reading sharpness
                val w = (page.width * 1.5f).roundToInt()
                val h = (page.height * 1.5f).roundToInt()

                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // Fill white background for pages with transparency
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val imgFile = File(outDir, "Page_${i + 1}.jpg")
                FileOutputStream(imgFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                }
                bitmap.recycle()
                generatedFiles.add(imgFile)

                val prog = (((i + 1).toFloat() / pageCount) * 100f).roundToInt()
                onProgress(prog)
            }

            Result.success(
                PdfToJpgResult(
                    outputDir = outDir,
                    generatedImages = generatedFiles,
                    pageCount = pageCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }
}

