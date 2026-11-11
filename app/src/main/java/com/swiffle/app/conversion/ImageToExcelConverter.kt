package com.swiffle.app.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Production-Grade Image → Excel Converter (Semantic Stitching + RL-QW-TRE).
 *
 * Pipeline:
 *  1. EXIF-corrected bitmap loading & normalization
 *  2. High-precision ML OCR token extraction
 *  3. Contextual Reinforcement Learning policy prediction
 *  4. Quantum-wave continuous Gaussian column & baseline lattice binning
 *  5. Semantic multi-image continuous table stitching
 *  6. Professional Microsoft Excel workbook synthesis with typed cells & auto-formatting
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

                val bitmap = loadOrientedBitmap(context, uri) ?: return@forEachIndexed
                val bmpW = bitmap.width.toFloat()
                val bmpH = bitmap.height.toFloat()

                // 1. OCR Token Extraction
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = withContext(Dispatchers.Main) {
                    recognizer.process(inputImage).await()
                }
                bitmap.recycle()

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
                                    confidence = 1.0f
                                )
                            )
                        }
                    }
                }

                if (tokens.isEmpty()) {
                    return@forEachIndexed
                }

                // 2. RL Agent State Vectorization & Policy Action Prediction
                val layoutState = TableReinforcementAgent.extractState(tokens, bmpW, bmpH)
                val policyAction = TableReinforcementAgent.predictAction(context, layoutState)
                lastLayoutState = layoutState
                lastPolicyAction = policyAction

                // 3. Quantum-Wave Lattice Grid Reconstruction
                val grid = TableLatticeReconstructor.reconstructGrid(
                    tokens = tokens,
                    pageWidth = bmpW,
                    pageHeight = bmpH,
                    pageIndex = imgIdx,
                    policyAction = policyAction
                )

                // 4. Intrinsic Reward Evaluation & Lifelong Experience Learning
                val reward = TableReinforcementAgent.evaluateReward(grid)
                TableReinforcementAgent.recordExperienceAndUpdate(context, layoutState, policyAction, reward)
                totalReward += reward
                pageGrids.add(grid)
            }

            if (lastLayoutState != null && lastPolicyAction != null) {
                TableReinforcementAgent.setLastConversionContext(lastLayoutState, lastPolicyAction)
            }

            // 5. Semantic Multi-Image Table Stitching
            val semanticTables = SemanticTableStitcher.stitchTables(pageGrids)

            // ── Excel Styling Definitions ──────────────────────────────────────────
            val workbook = XSSFWorkbook()
            val dataFormat = workbook.createDataFormat()

            val headerFont = workbook.createFont().apply {
                bold = true
                fontName = "Segoe UI"
                fontHeightInPoints = 11.toShort()
                color = IndexedColors.WHITE.index
            }

            val metaFont = workbook.createFont().apply {
                bold = true
                fontName = "Segoe UI"
                fontHeightInPoints = 11.toShort()
                color = IndexedColors.BLACK.index
            }

            val regularFont = workbook.createFont().apply {
                fontName = "Segoe UI"
                fontHeightInPoints = 10.toShort()
            }

            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.DARK_GREEN.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                setBorders(this, BorderStyle.THIN)
            }

            val metaStyle = workbook.createCellStyle().apply {
                setFont(metaFont)
                verticalAlignment = VerticalAlignment.CENTER
            }

            val textStyle = workbook.createCellStyle().apply {
                setFont(regularFont)
                verticalAlignment = VerticalAlignment.CENTER
                setBorders(this, BorderStyle.THIN)
            }

            val numericStyle = workbook.createCellStyle().apply {
                setFont(regularFont)
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
                setDataFormat(dataFormat.getFormat("#,##0.00"))
                setBorders(this, BorderStyle.THIN)
            }

            val currencyStyle = workbook.createCellStyle().apply {
                setFont(regularFont)
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
                setDataFormat(dataFormat.getFormat("$#,##0.00"))
                setBorders(this, BorderStyle.THIN)
            }

            val percentageStyle = workbook.createCellStyle().apply {
                setFont(regularFont)
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.CENTER
                setDataFormat(dataFormat.getFormat("0.00%"))
                setBorders(this, BorderStyle.THIN)
            }

            val zebraTextStyle = workbook.createCellStyle().apply {
                setFont(regularFont)
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.NO_FILL
                verticalAlignment = VerticalAlignment.CENTER
                setBorders(this, BorderStyle.THIN)
            }

            var totalSynthesizedRows = 0
            val tablesToRender = if (semanticTables.isNotEmpty()) semanticTables else listOf(
                SemanticDocumentTable(
                    title = "Table 1",
                    columnHeaders = listOf("Column 1"),
                    rows = emptyList(),
                    sourcePageIndices = listOf(0)
                )
            )

            val usedSheetNames = mutableSetOf<String>()

            for ((tIdx, table) in tablesToRender.withIndex()) {
                var safeTitle = table.title.replace(Regex("[\\\\/*?:\\[\\]]"), " ").take(28).trim()
                if (safeTitle.isBlank()) safeTitle = "Table ${tIdx + 1}"
                var candidateName = safeTitle
                var suffix = 2
                while (usedSheetNames.contains(candidateName)) {
                    candidateName = "${safeTitle.take(24)} ($suffix)"
                    suffix++
                }
                usedSheetNames.add(candidateName)

                val sheet = workbook.createSheet(candidateName)
                sheet.setDisplayGridlines(true)

                var currentRowIdx = 0

                // Metadata Banner
                for (metaLine in table.metadataHeader) {
                    val metaRow = sheet.createRow(currentRowIdx++)
                    val cell = metaRow.createCell(0)
                    cell.setCellValue(metaLine)
                    cell.cellStyle = metaStyle
                    totalSynthesizedRows++
                }

                if (table.metadataHeader.isNotEmpty()) {
                    currentRowIdx++
                }

                val headerRowIndex = currentRowIdx

                // Column Headers
                val headerRow = sheet.createRow(currentRowIdx++)
                table.columnHeaders.forEachIndexed { colIdx, headerText ->
                    val cell = headerRow.createCell(colIdx)
                    cell.setCellValue(headerText)
                    cell.cellStyle = headerStyle
                }
                totalSynthesizedRows++

                // Freeze Header Pane
                sheet.createFreezePane(0, headerRowIndex + 1)

                // Continuous Data Rows
                val colCount = table.columnHeaders.size
                table.rows.forEachIndexed { rIdx, rowCells ->
                    val dataRow = sheet.createRow(currentRowIdx++)
                    totalSynthesizedRows++

                    rowCells.forEachIndexed { cIdx, cell ->
                        if (cIdx < colCount) {
                            val excelCell = dataRow.createCell(cIdx)
                            when (cell.dataType) {
                                CellDataType.NUMERIC -> {
                                    if (cell.numericValue != null) {
                                        excelCell.setCellValue(cell.numericValue)
                                        excelCell.cellStyle = numericStyle
                                    } else {
                                        excelCell.setCellValue(cell.text)
                                        excelCell.cellStyle = textStyle
                                    }
                                }
                                CellDataType.CURRENCY -> {
                                    if (cell.numericValue != null) {
                                        excelCell.setCellValue(cell.numericValue)
                                        excelCell.cellStyle = currencyStyle
                                    } else {
                                        excelCell.setCellValue(cell.text)
                                        excelCell.cellStyle = textStyle
                                    }
                                }
                                CellDataType.PERCENTAGE -> {
                                    if (cell.numericValue != null) {
                                        excelCell.setCellValue(cell.numericValue)
                                        excelCell.cellStyle = percentageStyle
                                    } else {
                                        excelCell.setCellValue(cell.text)
                                        excelCell.cellStyle = textStyle
                                    }
                                }
                                else -> {
                                    excelCell.setCellValue(cell.text)
                                    excelCell.cellStyle = if (rIdx % 2 == 1) zebraTextStyle else textStyle
                                }
                            }
                        }
                    }
                }

                // Proportional column widths
                for (c in 0 until colCount) {
                    val headerLen = table.columnHeaders.getOrNull(c)?.length ?: 10
                    val maxDataLen = table.rows.mapNotNull { r ->
                        r.getOrNull(c)?.text?.length
                    }.maxOrNull() ?: 10

                    val targetCharWidth = (max(headerLen, maxDataLen) + 4).coerceIn(12, 55)
                    sheet.setColumnWidth(c, targetCharWidth * 256)
                }
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { workbook.write(it) }
            workbook.close()

            val avgReward = if (pageGrids.isNotEmpty()) totalReward / pageGrids.size else 0.90f
            Result(
                file = outputFile,
                sheetCount = tablesToRender.size,
                rowCount = totalSynthesizedRows,
                averageReward = avgReward
            )
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

    private fun setBorders(style: CellStyle, borderStyle: BorderStyle) {
        style.borderTop = borderStyle
        style.borderBottom = borderStyle
        style.borderLeft = borderStyle
        style.borderRight = borderStyle
    }
}
