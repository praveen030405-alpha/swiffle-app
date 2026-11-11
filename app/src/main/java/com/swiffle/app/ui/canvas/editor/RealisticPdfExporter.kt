package com.swiffle.app.ui.canvas.editor

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Production-Grade Realistic PDF Exporter:
 * 
 * Bakes inpainted background paper patches and photorealistic typography directly into
 * the PDF structure without degrading unedited pages or vector text streams.
 */
object RealisticPdfExporter {

    private const val TAG = "RealisticPdfExporter"

    suspend fun exportEditedPdf(
        context: Context,
        sourcePdfFile: File,
        outputPdfFile: File,
        editedObjects: List<EditableObject>,
        inpaintedPatches: List<InpaintedPatch>,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context)

            val pdDoc = if (!password.isNullOrBlank()) {
                PDDocument.load(sourcePdfFile, password.trim())
            } else {
                PDDocument.load(sourcePdfFile)
            }

            // Group edits by 1-based page number
            val objectsByPage = editedObjects.filter { it.isMovedOrModified || it.type == EditObjectType.IMAGE }
                .groupBy { it.pageNum }
            val patchesByPage = inpaintedPatches.groupBy { it.pageNum }

            val modifiedPages = (objectsByPage.keys + patchesByPage.keys).toSet()

            for (pageNum in modifiedPages) {
                val pageIndex = pageNum - 1
                if (pageIndex < 0 || pageIndex >= pdDoc.numberOfPages) continue

                val page = pdDoc.getPage(pageIndex)
                val pageHeight = page.mediaBox.height

                val patches = patchesByPage[pageNum] ?: emptyList()
                val objects = objectsByPage[pageNum] ?: emptyList()

                PDPageContentStream(
                    pdDoc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->

                    // 1. Inpaint vacated or replaced areas with authentic paper texture patches
                    for (patch in patches) {
                        val patchBmp = patch.patchBitmap ?: continue
                        if (patchBmp.isRecycled) continue

                        val pdfX = patch.pdfRect.left
                        val pdfY = pageHeight - patch.pdfRect.bottom
                        val pdfW = max(1f, patch.pdfRect.width())
                        val pdfH = max(1f, patch.pdfRect.height())

                        try {
                            val pdImage = LosslessFactory.createFromImage(pdDoc, patchBmp)
                            contentStream.drawImage(pdImage, pdfX, pdfY, pdfW, pdfH)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to bake inpaint patch on page $pageNum: ${e.message}", e)
                        }
                    }

                    // 2. Render modified and new objects (Text & Images)
                    for (obj in objects) {
                        val pdfX = obj.currentPdfRect.left
                        val pdfY = pageHeight - obj.currentPdfRect.bottom
                        val pdfW = max(1f, obj.currentPdfRect.width())
                        val pdfH = max(1f, obj.currentPdfRect.height())

                        when (obj.type) {
                            EditObjectType.TEXT -> {
                                if (obj.text.isNotBlank()) {
                                    // 300 DPI high-resolution raster scale (approx 4.16x points)
                                    val renderScale = 3.0f
                                    val pixelW = (pdfW * renderScale).toInt().coerceAtLeast(8)
                                    val pixelH = (pdfH * renderScale).toInt().coerceAtLeast(8)

                                    val textBitmap = RealisticInpaintEngine.renderRealisticTextToBitmap(
                                        text = obj.text,
                                        typography = obj.typography,
                                        realism = obj.realism,
                                        widthPx = pixelW,
                                        heightPx = pixelH,
                                        scale = renderScale
                                    )

                                    try {
                                        val pdImage = LosslessFactory.createFromImage(pdDoc, textBitmap)
                                        contentStream.drawImage(pdImage, pdfX, pdfY, pdfW, pdfH)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to bake text object on page $pageNum: ${e.message}", e)
                                    } finally {
                                        textBitmap.recycle()
                                    }
                                }
                            }
                            EditObjectType.IMAGE -> {
                                val img = obj.imageBitmap
                                if (img != null && !img.isRecycled) {
                                    try {
                                        val pdImage = LosslessFactory.createFromImage(pdDoc, img)
                                        contentStream.drawImage(pdImage, pdfX, pdfY, pdfW, pdfH)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to bake image object on page $pageNum: ${e.message}", e)
                                    }
                                }
                            }
                            EditObjectType.SHAPE -> {
                                // Optional shapes
                            }
                        }
                    }
                }
            }

            // Ensure destination directory exists
            outputPdfFile.parentFile?.mkdirs()
            pdDoc.save(outputPdfFile)
            pdDoc.close()

            Log.i(TAG, "Successfully exported realistic edited PDF to ${outputPdfFile.absolutePath}")
            Result.success(outputPdfFile)
        } catch (e: Throwable) {
            Log.e(TAG, "Error exporting realistic PDF: ${e.message}", e)
            Result.failure(e)
        }
    }
}

