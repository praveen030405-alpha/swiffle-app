package com.swiffle.app.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * High-Precision Image → Excel Converter.
 *
 * Pipeline:
 *  1. EXIF-corrected bitmap loading & adaptive contrast normalization for camera table captures
 *  2. ML Kit TextRecognition token & bounding box extraction
 *  3. Contextual RL policy prediction + Interval-Span 2D Table Lattice Reconstruction
 *  4. Multi-Image Continuous Table Stitching with empty-column pruning
 *  5. Native Apache POI `.xlsx` synthesis via shared workbook builder
 */
object ImageToExcelConverter {

    data class Result(
        val file: File,
        val sheetCount: Int,
        val rowCount: Int,
        val averageReward: Float = 0.0f
    )

    suspend fun convert(
        uris: List<Uri>,
        context: Context,
        outputFile: File,
        onProgress: (image: Int, total: Int) -> Unit = { _, _ -> }
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val pageGrids = mutableListOf<ReconstructedTableGrid>()
            var totalReward = 0f
            var lastLayoutState: DocumentLayoutState? = null
            var lastPolicyAction: TableExtractionPolicyAction? = null

            uris.forEachIndexed { imgIdx, uri ->
                onProgress(imgIdx + 1, uris.size)

                val rawBitmap = loadOrientedBitmap(context, uri) ?: return@forEachIndexed
                val enhancedBitmap = enhanceContrastForOcr(rawBitmap)
                if (enhancedBitmap !== rawBitmap) {
                    rawBitmap.recycle()
                }

                val bmpW = enhancedBitmap.width.toFloat()
                val bmpH = enhancedBitmap.height.toFloat()

                val inputImage = InputImage.fromBitmap(enhancedBitmap, 0)
                val visionText = withContext(Dispatchers.Main) {
                    recognizer.process(inputImage).await()
                }
                enhancedBitmap.recycle()

                val tokens = mutableListOf<OcrToken>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            val box = element.boundingBox ?: continue
                            tokens.add(
                                OcrToken(
                                    text = element.text,
                                    bounds = LatticeRect(
                                        left = box.left.toFloat(),
                                        top = box.top.toFloat(),
                                        right = box.right.toFloat(),
                                        bottom = box.bottom.toFloat()
                                    ),
                                    confidence = 0.95f
                                )
                            )
                        }
                    }
                }

                if (tokens.isEmpty()) {
                    return@forEachIndexed
                }

                val layoutState = TableReinforcementAgent.extractState(tokens, bmpW, bmpH)
                val policyAction = TableReinforcementAgent.predictAction(context, layoutState)
                lastLayoutState = layoutState
                lastPolicyAction = policyAction

                val grid = TableLatticeReconstructor.reconstructGrid(
                    tokens = tokens,
                    pageWidth = bmpW,
                    pageHeight = bmpH,
                    pageIndex = imgIdx,
                    policyAction = policyAction
                )

                val reward = TableReinforcementAgent.evaluateReward(grid)
                TableReinforcementAgent.recordExperienceAndUpdate(context, layoutState, policyAction, reward)
                totalReward += reward
                pageGrids.add(grid)
            }

            if (lastLayoutState != null && lastPolicyAction != null) {
                TableReinforcementAgent.setLastConversionContext(lastLayoutState, lastPolicyAction)
            }

            val semanticTables = SemanticTableStitcher.stitchTables(pageGrids)
            val totalSynthesizedRows = PdfToExcelConverter.writeTablesToWorkbook(semanticTables, outputFile)

            val avgReward = if (pageGrids.isNotEmpty()) totalReward / pageGrids.size else 0.90f
            Result(
                file = outputFile,
                sheetCount = semanticTables.size.coerceAtLeast(1),
                rowCount = totalSynthesizedRows,
                averageReward = avgReward
            )
        }
    }

    /**
     * Gently boosts luminance contrast on photographed documents so faint table text is recognized cleanly.
     */
    private fun enhanceContrastForOcr(source: Bitmap): Bitmap {
        return try {
            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val contrast = 1.18f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            val cm = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(source, 0f, 0f, paint)
            out
        } catch (_: Exception) {
            source
        }
    }

    private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
        var sampleSize = 1
        val targetMax = 2560
        while (maxDim / sampleSize > targetMax) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null

        val rotation = try {
            val exif = ExifInterface(bytes.inputStream())
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }

        return if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            rawBitmap.recycle()
            rotated
        } else {
            rawBitmap
        }
    }
}
