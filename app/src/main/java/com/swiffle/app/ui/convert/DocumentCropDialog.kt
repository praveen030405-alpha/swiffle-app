package com.swiffle.app.ui.convert

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.swiffle.app.theme.GoogleSans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private enum class CropDragHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT
}

/**
 * Enterprise Document Cropper Studio.
 *
 * Provides full-bleed canvas utilization, 8-point handles, intelligent
 * edge auto-detection, and a precision 2.5x Loupe Magnifier that floats above
 * the finger for pixel-perfect document boundary adjustments.
 */
@Composable
fun DocumentCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropSaved: (Uri) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    // Normalized crop box (0f..1f relative to image width/height)
    var cropLeft   by remember { mutableFloatStateOf(0.04f) }
    var cropTop    by remember { mutableFloatStateOf(0.04f) }
    var cropRight  by remember { mutableFloatStateOf(0.96f) }
    var cropBottom by remember { mutableFloatStateOf(0.96f) }

    var isProcessing by remember { mutableStateOf(false) }

    // Interactive Loupe (Zoom Magnifier) state
    var activeHandle by remember { mutableStateOf(CropDragHandle.NONE) }
    var touchPosPx by remember { mutableStateOf(Offset.Zero) }

    val cobaltBlue = Color(0xFF0078D4)

    // Load Bitmap and Auto-Detect Initial Edges
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    bitmap = bmp
                    if (bmp != null) {
                        val detected = autoDetectBitmapBounds(bmp)
                        cropLeft = detected.left
                        cropTop = detected.top
                        cropRight = detected.right
                        cropBottom = detected.bottom
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF141619) // Studio Graphite Workbench
        ) {
            val currentBitmap = bitmap
            if (currentBitmap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.swiffle.app.ui.components.StockMaterialLoadingIndicator(
                        size = 36.dp,
                        strokeWidth = 3.5.dp,
                        useGoogleColors = true
                    )
                }
                return@Surface
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ── Top Bar: Studio Header (Close + Dimensions + Reset) ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Cancel", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Crop Document",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSans
                            )
                            val w = if (rotationDegrees % 180f == 0f) currentBitmap.width else currentBitmap.height
                            val h = if (rotationDegrees % 180f == 0f) currentBitmap.height else currentBitmap.width
                            Text(
                                "• ${w}×${h}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.5.sp,
                                fontFamily = GoogleSans
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            cropLeft = 0f
                            cropTop = 0f
                            cropRight = 1f
                            cropBottom = 1f
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.RestartAlt, "Reset", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                }

                // ── Main Full-Bleed Studio Canvas Area (Zero Wasted Space!) ──
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val availableW = maxWidth.value
                        val availableH = maxHeight.value

                        val bmpW = if (rotationDegrees % 180f == 0f) currentBitmap.width else currentBitmap.height
                        val bmpH = if (rotationDegrees % 180f == 0f) currentBitmap.height else currentBitmap.width

                        // Maximize scale to fully occupy canvas
                        val fitScale = minOf(availableW / bmpW.toFloat(), availableH / bmpH.toFloat())
                        val displayW = bmpW * fitScale
                        val displayH = bmpH * fitScale

                        val displayWPx = with(density) { displayW.dp.toPx() }
                        val displayHPx = with(density) { displayH.dp.toPx() }

                        Box(
                            modifier = Modifier
                                .size(displayW.dp, displayH.dp)
                                .shadow(elevation = 16.dp, shape = RoundedCornerShape(3.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                .clip(RoundedCornerShape(3.dp))
                        ) {
                            // 1. Render Document Image
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawImage(
                                    image = currentBitmap.asImageBitmap(),
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                )

                                val rL = cropLeft * size.width
                                val rT = cropTop * size.height
                                val rR = cropRight * size.width
                                val rB = cropBottom * size.height

                                // Frosted translucent vignette outside crop box
                                val darkScrim = Color.Black.copy(alpha = 0.65f)
                                drawRect(darkScrim, Offset.Zero, Size(size.width, rT))
                                drawRect(darkScrim, Offset(0f, rB), Size(size.width, size.height - rB))
                                drawRect(darkScrim, Offset(0f, rT), Size(rL, rB - rT))
                                drawRect(darkScrim, Offset(rR, rT), Size(size.width - rR, rB - rT))

                                // Precision Boundary Contour
                                drawRect(
                                    color = cobaltBlue,
                                    topLeft = Offset(rL, rT),
                                    size = Size(rR - rL, rB - rT),
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                // Rule of Thirds Grid Lines (Subtle guide)
                                val wThird = (rR - rL) / 3f
                                val hThird = (rB - rT) / 3f
                                val gridCol = Color.White.copy(alpha = 0.35f)
                                drawLine(gridCol, Offset(rL + wThird, rT), Offset(rL + wThird, rB), 1.dp.toPx())
                                drawLine(gridCol, Offset(rL + wThird * 2, rT), Offset(rL + wThird * 2, rB), 1.dp.toPx())
                                drawLine(gridCol, Offset(rL, rT + hThird), Offset(rR, rT + hThird), 1.dp.toPx())
                                drawLine(gridCol, Offset(rL, rT + hThird * 2), Offset(rR, rT + hThird * 2), 1.dp.toPx())

                                // 4 Corner Anchor Spheres
                                fun drawCornerNode(center: Offset) {
                                    drawCircle(Color.White, 7.dp.toPx(), center)
                                    drawCircle(cobaltBlue, 5.dp.toPx(), center)
                                    drawCircle(Color.White, 2.dp.toPx(), center)
                                }
                                drawCornerNode(Offset(rL, rT))
                                drawCornerNode(Offset(rR, rT))
                                drawCornerNode(Offset(rR, rB))
                                drawCornerNode(Offset(rL, rB))

                                // 4 Edge Midpoint Handles (Pills)
                                val edgeHandleLen = 16.dp.toPx()
                                val edgeHandleThick = 4.dp.toPx()
                                // Top
                                drawLine(Color.White, Offset((rL + rR) / 2f - edgeHandleLen / 2, rT), Offset((rL + rR) / 2f + edgeHandleLen / 2, rT), edgeHandleThick)
                                // Bottom
                                drawLine(Color.White, Offset((rL + rR) / 2f - edgeHandleLen / 2, rB), Offset((rL + rR) / 2f + edgeHandleLen / 2, rB), edgeHandleThick)
                                // Left
                                drawLine(Color.White, Offset(rL, (rT + rB) / 2f - edgeHandleLen / 2), Offset(rL, (rT + rB) / 2f + edgeHandleLen / 2), edgeHandleThick)
                                // Right
                                drawLine(Color.White, Offset(rR, (rT + rB) / 2f - edgeHandleLen / 2), Offset(rR, (rT + rB) / 2f + edgeHandleLen / 2), edgeHandleThick)
                            }

                            // 2. Touch Gesture Processing
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                touchPosPx = offset
                                                val tx = offset.x / size.width
                                                val ty = offset.y / size.height

                                                val threshold = 0.12f

                                                val distTL = kotlin.math.hypot(tx - cropLeft, ty - cropTop)
                                                val distTR = kotlin.math.hypot(tx - cropRight, ty - cropTop)
                                                val distBR = kotlin.math.hypot(tx - cropRight, ty - cropBottom)
                                                val distBL = kotlin.math.hypot(tx - cropLeft, ty - cropBottom)

                                                activeHandle = when {
                                                    distTL < threshold -> CropDragHandle.TOP_LEFT
                                                    distTR < threshold -> CropDragHandle.TOP_RIGHT
                                                    distBR < threshold -> CropDragHandle.BOTTOM_RIGHT
                                                    distBL < threshold -> CropDragHandle.BOTTOM_LEFT
                                                    kotlin.math.abs(ty - cropTop) < 0.08f -> CropDragHandle.EDGE_TOP
                                                    kotlin.math.abs(ty - cropBottom) < 0.08f -> CropDragHandle.EDGE_BOTTOM
                                                    kotlin.math.abs(tx - cropLeft) < 0.08f -> CropDragHandle.EDGE_LEFT
                                                    kotlin.math.abs(tx - cropRight) < 0.08f -> CropDragHandle.EDGE_RIGHT
                                                    else -> CropDragHandle.NONE
                                                }
                                            },
                                            onDragEnd = {
                                                activeHandle = CropDragHandle.NONE
                                            },
                                            onDragCancel = {
                                                activeHandle = CropDragHandle.NONE
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                touchPosPx = change.position
                                                val dx = dragAmount.x / size.width
                                                val dy = dragAmount.y / size.height

                                                when (activeHandle) {
                                                    CropDragHandle.TOP_LEFT -> {
                                                        cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.12f)
                                                        cropTop  = (cropTop + dy).coerceIn(0f, cropBottom - 0.12f)
                                                    }
                                                    CropDragHandle.TOP_RIGHT -> {
                                                        cropRight = (cropRight + dx).coerceIn(cropLeft + 0.12f, 1f)
                                                        cropTop   = (cropTop + dy).coerceIn(0f, cropBottom - 0.12f)
                                                    }
                                                    CropDragHandle.BOTTOM_RIGHT -> {
                                                        cropRight  = (cropRight + dx).coerceIn(cropLeft + 0.12f, 1f)
                                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.12f, 1f)
                                                    }
                                                    CropDragHandle.BOTTOM_LEFT -> {
                                                        cropLeft   = (cropLeft + dx).coerceIn(0f, cropRight - 0.12f)
                                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.12f, 1f)
                                                    }
                                                    CropDragHandle.EDGE_TOP -> {
                                                        cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.12f)
                                                    }
                                                    CropDragHandle.EDGE_BOTTOM -> {
                                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.12f, 1f)
                                                    }
                                                    CropDragHandle.EDGE_LEFT -> {
                                                        cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.12f)
                                                    }
                                                    CropDragHandle.EDGE_RIGHT -> {
                                                        cropRight = (cropRight + dx).coerceIn(cropLeft + 0.12f, 1f)
                                                    }
                                                    CropDragHandle.NONE -> {}
                                                }
                                            }
                                        )
                                    }
                            )

                            // 3. Precision 2.5x Loupe Magnifier (Floats above finger during drag!)
                            if (activeHandle != CropDragHandle.NONE) {
                                val loupeDiameter = 88.dp
                                val loupeDiameterPx = with(density) { loupeDiameter.toPx() }

                                // Target coordinate in image space (0f..1f)
                                val targetNorm = when (activeHandle) {
                                    CropDragHandle.TOP_LEFT -> Offset(cropLeft, cropTop)
                                    CropDragHandle.TOP_RIGHT -> Offset(cropRight, cropTop)
                                    CropDragHandle.BOTTOM_RIGHT -> Offset(cropRight, cropBottom)
                                    CropDragHandle.BOTTOM_LEFT -> Offset(cropLeft, cropBottom)
                                    CropDragHandle.EDGE_TOP -> Offset((cropLeft + cropRight) / 2f, cropTop)
                                    CropDragHandle.EDGE_BOTTOM -> Offset((cropLeft + cropRight) / 2f, cropBottom)
                                    CropDragHandle.EDGE_LEFT -> Offset(cropLeft, (cropTop + cropBottom) / 2f)
                                    CropDragHandle.EDGE_RIGHT -> Offset(cropRight, (cropTop + cropBottom) / 2f)
                                    CropDragHandle.NONE -> Offset(0.5f, 0.5f)
                                }

                                // Position loupe 70dp above finger
                                val loupeLeftPx = (touchPosPx.x - loupeDiameterPx / 2f).coerceIn(8f, displayWPx - loupeDiameterPx - 8f)
                                val loupeTopPx = (touchPosPx.y - loupeDiameterPx - with(density) { 60.dp.toPx() }).coerceIn(8f, displayHPx - loupeDiameterPx - 8f)

                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black,
                                    border = androidx.compose.foundation.BorderStroke(2.5.dp, Color.White),
                                    shadowElevation = 20.dp,
                                    modifier = Modifier
                                        .offset { IntOffset(loupeLeftPx.roundToInt(), loupeTopPx.roundToInt()) }
                                        .size(loupeDiameter)
                                        .zIndex(100f)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val zoom = 2.4f
                                        val centerCanvas = Offset(size.width / 2f, size.height / 2f)

                                        val imgX = targetNorm.x * currentBitmap.width
                                        val imgY = targetNorm.y * currentBitmap.height

                                        // Draw magnified bitmap
                                        drawContext.canvas.save()
                                        val clipPath = Path().apply {
                                            addOval(Rect(Offset.Zero, size))
                                        }
                                        clipPath(clipPath) {
                                            // Scale matrix around center
                                            val srcW = size.width / zoom * (currentBitmap.width.toFloat() / displayWPx)
                                            val srcH = size.height / zoom * (currentBitmap.height.toFloat() / displayHPx)

                                            val srcLeft = (imgX - srcW / 2f).toInt().coerceIn(0, currentBitmap.width - 1)
                                            val srcTop  = (imgY - srcH / 2f).toInt().coerceIn(0, currentBitmap.height - 1)
                                            val srcRight = (srcLeft + srcW).toInt().coerceIn(srcLeft + 1, currentBitmap.width)
                                            val srcBottom = (srcTop + srcH).toInt().coerceIn(srcTop + 1, currentBitmap.height)

                                            drawImage(
                                                image = currentBitmap.asImageBitmap(),
                                                srcOffset = IntOffset(srcLeft, srcTop),
                                                srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
                                                dstOffset = IntOffset.Zero,
                                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                            )

                                            // Precision Crosshair
                                            val crossCol = cobaltBlue
                                            val crosshairLen = 14.dp.toPx()
                                            drawLine(crossCol, Offset(centerCanvas.x - crosshairLen, centerCanvas.y), Offset(centerCanvas.x + crosshairLen, centerCanvas.y), 2.dp.toPx())
                                            drawLine(crossCol, Offset(centerCanvas.x, centerCanvas.y - crosshairLen), Offset(centerCanvas.x, centerCanvas.y + crosshairLen), 2.dp.toPx())
                                            drawCircle(Color.White, 3.dp.toPx(), centerCanvas)
                                        }
                                        drawContext.canvas.restore()
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Bottom Fluent Studio Bar (Auto Detect + Full Page + Rotate + Apply) ──
                Surface(
                    color = Color(0xFF1E2024),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Action Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 1. Auto-Detect Corners
                            Surface(
                                onClick = {
                                    val detected = autoDetectBitmapBounds(currentBitmap)
                                    cropLeft = detected.left
                                    cropTop = detected.top
                                    cropRight = detected.right
                                    cropBottom = detected.bottom
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.10f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.AutoFixHigh, "Auto", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Auto", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                                }
                            }

                            // 2. Full Page Reset
                            Surface(
                                onClick = {
                                    cropLeft = 0f
                                    cropTop = 0f
                                    cropRight = 1f
                                    cropBottom = 1f
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.10f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.CropFree, "Full", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Full", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                                }
                            }

                            // 3. Rotate 90°
                            Surface(
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90f) % 360f
                                },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.10f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.RotateRight, "Rotate", tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                            }
                        }

                        // Apply Crop Button
                        Button(
                            onClick = {
                                if (isProcessing) return@Button
                                isProcessing = true
                                val b = currentBitmap
                                val cropX = (cropLeft * b.width).toInt().coerceIn(0, b.width - 1)
                                val cropY = (cropTop * b.height).toInt().coerceIn(0, b.height - 1)
                                val cropW = ((cropRight - cropLeft) * b.width).toInt().coerceIn(1, b.width - cropX)
                                val cropH = ((cropBottom - cropTop) * b.height).toInt().coerceIn(1, b.height - cropY)

                                val matrix = Matrix().apply {
                                    if (rotationDegrees != 0f) postRotate(rotationDegrees)
                                }
                                val cropped = Bitmap.createBitmap(b, cropX, cropY, cropW, cropH, matrix, true)

                                val croppedFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(croppedFile).use { out ->
                                    cropped.compress(Bitmap.CompressFormat.JPEG, 98, out)
                                }
                                val resultUri = Uri.fromFile(croppedFile)
                                isProcessing = false
                                onCropSaved(resultUri)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cobaltBlue),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Apply", fontWeight = FontWeight.Bold, fontFamily = GoogleSans, color = Color.White, fontSize = 13.5.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fast downsampled edge detector to automatically find document margins.
 */
private fun autoDetectBitmapBounds(bitmap: Bitmap): Rect {
    return try {
        val sampleW = 100
        val sampleH = 80
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        fun getLum(x: Int, y: Int): Int {
            val c = pixels[y * sampleW + x]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }

        var minX = 0.04f
        var maxX = 0.96f
        var minY = 0.04f
        var maxY = 0.96f

        val testRows = listOf(20, 30, 40, 50, 60)
        var leftSum = 0
        var leftCount = 0
        var rightSum = 0
        var rightCount = 0

        for (y in testRows) {
            val baseL = getLum(2, y)
            for (x in 4..38) {
                if (kotlin.math.abs(getLum(x, y) - baseL) > 20) {
                    leftSum += x
                    leftCount++
                    break
                }
            }
            val baseR = getLum(sampleW - 3, y)
            for (step in 4..38) {
                val x = sampleW - 1 - step
                if (kotlin.math.abs(getLum(x, y) - baseR) > 20) {
                    rightSum += x
                    rightCount++
                    break
                }
            }
        }

        val testCols = listOf(25, 40, 50, 60, 75)
        var topSum = 0
        var topCount = 0
        var bottomSum = 0
        var bottomCount = 0

        for (x in testCols) {
            val baseT = getLum(x, 2)
            for (y in 4..30) {
                if (kotlin.math.abs(getLum(x, y) - baseT) > 20) {
                    topSum += y
                    topCount++
                    break
                }
            }
            val baseB = getLum(x, sampleH - 3)
            for (step in 4..30) {
                val y = sampleH - 1 - step
                if (kotlin.math.abs(getLum(x, y) - baseB) > 20) {
                    bottomSum += y
                    bottomCount++
                    break
                }
            }
        }

        if (leftCount >= 2 && rightCount >= 2 && topCount >= 2 && bottomCount >= 2) {
            val l = (leftSum.toFloat() / leftCount / sampleW).coerceIn(0.03f, 0.35f)
            val r = (rightSum.toFloat() / rightCount / sampleW).coerceIn(0.65f, 0.97f)
            val t = (topSum.toFloat() / topCount / sampleH).coerceIn(0.03f, 0.35f)
            val b = (bottomSum.toFloat() / bottomCount / sampleH).coerceIn(0.65f, 0.97f)
            if (r - l > 0.30f && b - t > 0.30f) {
                minX = l
                maxX = r
                minY = t
                maxY = b
            }
        }

        if (scaled != bitmap) scaled.recycle()
        Rect(minX, minY, maxX, maxY)
    } catch (_: Exception) {
        Rect(0.04f, 0.04f, 0.96f, 0.96f)
    }
}
