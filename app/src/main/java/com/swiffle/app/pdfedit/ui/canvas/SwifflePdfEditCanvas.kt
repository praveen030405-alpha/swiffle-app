package com.swiffle.app.pdfedit.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import android.graphics.RectF as AndroidRectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.pdfedit.core.ast.PdfTextLineNode
import com.swiffle.app.pdfedit.pipeline.PdfEditUiState
import com.swiffle.app.pdfedit.pipeline.PdfEditViewModel
import com.swiffle.app.pdfedit.ui.shader.ChameleonInkShader
import kotlin.math.max
import kotlin.math.min

/**
 * 120Hz Interactive Jetpack Compose Dual-Layer Viewport:
 * - Fluid Multi-touch Pinch Zoom and Pan
 * - Wavelet Frequency-Separation Inpainted Patch Compositing
 * - AGSL Physical Ink Shader Live Typographic Rendering
 * - Seamless Baseline In-Place and Floating Edit Synchronizer
 */
@Composable
fun SwifflePdfEditCanvas(
    pageBitmap: Bitmap?,
    state: PdfEditUiState,
    viewModel: PdfEditViewModel,
    onSaveRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val coroutineScope = rememberCoroutineScope()
    val zoomAnimatable = remember { Animatable(1.0f) }
    val panXAnimatable = remember { Animatable(0f) }
    val panYAnimatable = remember { Animatable(0f) }

    val zoomScale = zoomAnimatable.value
    val panOffsetX = panXAnimatable.value
    val panOffsetY = panYAnimatable.value

    LaunchedEffect(state.selectedLine) {
        if (state.selectedLine != null) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val inkRenderEffect = remember(
        state.subPixelThickness,
        state.scannerSoftness,
        state.customInkColor
    ) {
        ChameleonInkShader.createInkRenderEffect(
            subPixelThickness = state.subPixelThickness,
            scannerSoftness = state.scannerSoftness,
            inkColor = state.customInkColor
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF121212))
            .pointerInput(pageBitmap) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val activePointers = changes.filter { it.pressed }

                        if (activePointers.size > 1) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            coroutineScope.launch {
                                val currentZ = zoomAnimatable.value
                                val targetZ = (currentZ * zoomChange).coerceIn(0.65f, 6.0f)
                                zoomAnimatable.snapTo(targetZ)
                                panXAnimatable.snapTo(panXAnimatable.value + panChange.x)
                                panYAnimatable.snapTo(panYAnimatable.value + panChange.y)
                            }
                            changes.forEach { it.consume() }
                        } else if (activePointers.size == 1 && zoomAnimatable.value > 1.05f) {
                            val panChange = event.calculatePan()
                            coroutineScope.launch {
                                panXAnimatable.snapTo(panXAnimatable.value + panChange.x)
                                panYAnimatable.snapTo(panYAnimatable.value + panChange.y)
                            }
                            // Do NOT consume single pointer events so detectTapGestures can receive down/up/long-press!
                        }

                        // FIT TO SCREEN SPRING BACK: Detect when all fingers are released
                        if (activePointers.isEmpty()) {
                            val curZoom = zoomAnimatable.value
                            val bmp = pageBitmap
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()

                            coroutineScope.launch {
                                if (curZoom < 1.0f) {
                                    // Smoothly spring back to 1.0x Fit to Screen
                                    launch {
                                        zoomAnimatable.animateTo(
                                            1.0f,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                                        )
                                    }
                                    launch {
                                        panXAnimatable.animateTo(
                                            0f,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                                        )
                                    }
                                    launch {
                                        panYAnimatable.animateTo(
                                            0f,
                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                                        )
                                    }
                                } else if (bmp != null) {
                                    val bmpW = bmp.width.toFloat()
                                    val bmpH = bmp.height.toFloat()
                                    val baseScale = min(canvasW / bmpW, canvasH / bmpH)
                                    val fittedW = bmpW * baseScale
                                    val fittedH = bmpH * baseScale

                                    val maxPanX = max(0f, (fittedW * curZoom - canvasW) / 2f + 40f)
                                    val maxPanY = max(0f, (fittedH * curZoom - canvasH) / 2f + 60f)

                                    val targetPanX = panXAnimatable.value.coerceIn(-maxPanX, maxPanX)
                                    val targetPanY = panYAnimatable.value.coerceIn(-maxPanY, maxPanY)

                                    if (targetPanX != panXAnimatable.value) {
                                        launch {
                                            panXAnimatable.animateTo(
                                                targetPanX,
                                                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                                            )
                                        }
                                    }
                                    if (targetPanY != panYAnimatable.value) {
                                        launch {
                                            panYAnimatable.animateTo(
                                                targetPanY,
                                                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(state.pageDom, pageBitmap) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            val curZ = zoomAnimatable.value
                            if (curZ > 1.2f) {
                                // Double-tap zoom out -> Spring Fit to Screen
                                launch { zoomAnimatable.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                                launch { panXAnimatable.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                                launch { panYAnimatable.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                            } else {
                                // Double-tap zoom in -> 2.5x comfortable reading zoom centered
                                val canvasW = size.width.toFloat()
                                val canvasH = size.height.toFloat()
                                val targetPanX = ((canvasW / 2f) - tapOffset.x) * 1.5f
                                val targetPanY = ((canvasH / 2f) - tapOffset.y) * 1.5f
                                launch { zoomAnimatable.animateTo(2.5f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                                launch { panXAnimatable.animateTo(targetPanX, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                                launch { panYAnimatable.animateTo(targetPanY, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                            }
                        }
                    },
                    onTap = { tapOffset ->
                        val dom = state.pageDom ?: return@detectTapGestures
                        val mapper = state.coordinateMapper ?: return@detectTapGestures
                        val bmp = pageBitmap ?: return@detectTapGestures

                        val curZoom = zoomAnimatable.value
                        val curPanX = panXAnimatable.value
                        val curPanY = panYAnimatable.value

                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val bmpW = bmp.width.toFloat()
                        val bmpH = bmp.height.toFloat()
                        val baseScale = min(canvasW / bmpW, canvasH / bmpH)
                        val fittedW = bmpW * baseScale
                        val fittedH = bmpH * baseScale

                        val originX = (canvasW - fittedW * curZoom) / 2f + curPanX
                        val originY = (canvasH - fittedH * curZoom) / 2f + curPanY
                        val scaleFactorPtToPx = fittedW / max(1f, mapper.effectiveWidthPt)

                        // 1. Direct on-screen hit testing with generous touch slop and proximity ranking
                        val closestLine = dom.allLines.mapNotNull { line ->
                            val screenRect = mapper.pdfBoxToScreenRect(
                                pdfRect = line.bounds,
                                zoomScale = curZoom,
                                panOffsetX = originX,
                                panOffsetY = originY,
                                scaleFactorPtToPx = scaleFactorPtToPx
                            )
                            val slopX = 36f * curZoom
                            val slopY = 24f * curZoom
                            val expL = screenRect.left - slopX
                            val expR = screenRect.right + slopX
                            val expT = screenRect.top - slopY
                            val expB = screenRect.bottom + slopY

                            if (tapOffset.x in expL..expR && tapOffset.y in expT..expB) {
                                val dx = if (tapOffset.x < screenRect.left) screenRect.left - tapOffset.x
                                         else if (tapOffset.x > screenRect.right) tapOffset.x - screenRect.right
                                         else 0f
                                val dy = if (tapOffset.y < screenRect.top) screenRect.top - tapOffset.y
                                         else if (tapOffset.y > screenRect.bottom) tapOffset.y - screenRect.bottom
                                         else 0f
                                Pair(line, dx * dx + dy * dy)
                            } else null
                        }.minByOrNull { it.second }?.first

                        // 2. Secondary fallback via PDF user space coordinates
                        val pdfPoint = mapper.screenToPdfPoint(
                            screenX = tapOffset.x,
                            screenY = tapOffset.y,
                            zoomScale = curZoom,
                            panOffsetX = originX,
                            panOffsetY = originY,
                            scaleFactorPtToPx = scaleFactorPtToPx
                        )

                        val hitLine = closestLine ?: dom.allLines.firstOrNull { line ->
                            val minPdfX = min(line.bounds.left, line.bounds.right) - 30f
                            val maxPdfX = max(line.bounds.left, line.bounds.right) + 30f
                            val minPdfY = min(line.bounds.top, line.bounds.bottom) - 24f
                            val maxPdfY = max(line.bounds.top, line.bounds.bottom) + 24f

                            pdfPoint.x in minPdfX..maxPdfX && pdfPoint.y in minPdfY..maxPdfY
                        }

                        when (state.activeTool) {
                            com.swiffle.app.pdfedit.pipeline.PdfEditTool.INPAINT_ERASER -> {
                                if (hitLine != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.eraseLineWithInpainting(context, hitLine, pageBitmap)
                                }
                            }
                            com.swiffle.app.pdfedit.pipeline.PdfEditTool.HIGHLIGHT -> {
                                if (hitLine != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleHighlight(hitLine.id)
                                }
                            }
                            com.swiffle.app.pdfedit.pipeline.PdfEditTool.ADD_TEXT -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.addNewTextAt(context, android.graphics.PointF(pdfPoint.x, pdfPoint.y), pageBitmap)
                            }
                            else -> {
                                if (hitLine != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.selectLineForEdit(context, hitLine, pageBitmap)
                                } else {
                                    viewModel.clearSelection()
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    },
                    onLongPress = { longPressOffset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                            } else {
                                @Suppress("DEPRECATION")
                                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                vib?.vibrate(50L)
                            }
                        } catch (_: Exception) {}

                        val dom = state.pageDom ?: return@detectTapGestures
                        val mapper = state.coordinateMapper ?: return@detectTapGestures
                        val bmp = pageBitmap ?: return@detectTapGestures

                        val curZoom = zoomAnimatable.value
                        val curPanX = panXAnimatable.value
                        val curPanY = panYAnimatable.value

                        val canvasW = size.width.toFloat()
                        val canvasH = size.height.toFloat()
                        val bmpW = bmp.width.toFloat()
                        val bmpH = bmp.height.toFloat()
                        val baseScale = min(canvasW / bmpW, canvasH / bmpH)
                        val fittedW = bmpW * baseScale
                        val fittedH = bmpH * baseScale

                        val originX = (canvasW - fittedW * curZoom) / 2f + curPanX
                        val originY = (canvasH - fittedH * curZoom) / 2f + curPanY
                        val scaleFactorPtToPx = fittedW / max(1f, mapper.effectiveWidthPt)

                        val closestLongPressLine = dom.allLines.mapNotNull { line ->
                            val screenRect = mapper.pdfBoxToScreenRect(
                                pdfRect = line.bounds,
                                zoomScale = curZoom,
                                panOffsetX = originX,
                                panOffsetY = originY,
                                scaleFactorPtToPx = scaleFactorPtToPx
                            )
                            val slopX = 36f * curZoom
                            val slopY = 24f * curZoom
                            val expL = screenRect.left - slopX
                            val expR = screenRect.right + slopX
                            val expT = screenRect.top - slopY
                            val expB = screenRect.bottom + slopY

                            if (longPressOffset.x in expL..expR && longPressOffset.y in expT..expB) {
                                val dx = if (longPressOffset.x < screenRect.left) screenRect.left - longPressOffset.x
                                         else if (longPressOffset.x > screenRect.right) longPressOffset.x - screenRect.right
                                         else 0f
                                val dy = if (longPressOffset.y < screenRect.top) screenRect.top - longPressOffset.y
                                         else if (longPressOffset.y > screenRect.bottom) longPressOffset.y - screenRect.bottom
                                         else 0f
                                Pair(line, dx * dx + dy * dy)
                            } else null
                        }.minByOrNull { it.second }?.first

                        val hitLine = closestLongPressLine ?: run {
                            val pdfPoint = mapper.screenToPdfPoint(
                                screenX = longPressOffset.x,
                                screenY = longPressOffset.y,
                                zoomScale = curZoom,
                                panOffsetX = originX,
                                panOffsetY = originY,
                                scaleFactorPtToPx = scaleFactorPtToPx
                            )
                            dom.allLines.firstOrNull { line ->
                                val minPdfX = min(line.bounds.left, line.bounds.right) - 30f
                                val maxPdfX = max(line.bounds.left, line.bounds.right) + 30f
                                val minPdfY = min(line.bounds.top, line.bounds.bottom) - 24f
                                val maxPdfY = max(line.bounds.top, line.bounds.bottom) + 24f

                                pdfPoint.x in minPdfX..maxPdfX && pdfPoint.y in minPdfY..maxPdfY
                            }
                        }

                        if (hitLine != null) {
                            viewModel.selectLineForEdit(context, hitLine, pageBitmap)
                        }
                    }
                )
            }
    ) {
        // Dual-Layer Skia Canvas
        if (pageBitmap == null) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF0078D4)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val canvasW = size.width
            val canvasH = size.height

            if (pageBitmap != null && !pageBitmap.isRecycled) {
                val mapper = state.coordinateMapper
                val bmpW = pageBitmap.width.toFloat()
                val bmpH = pageBitmap.height.toFloat()

                // Calculate base center layout
                val baseScale = min(canvasW / bmpW, canvasH / bmpH)
                val fittedW = bmpW * baseScale
                val fittedH = bmpH * baseScale

                val originX = (canvasW - fittedW * zoomScale) / 2f + panOffsetX
                val originY = (canvasH - fittedH * zoomScale) / 2f + panOffsetY

                val scaleFactorPtToPx = if (mapper != null) {
                    fittedW / max(1f, mapper.effectiveWidthPt)
                } else 1.0f

                drawIntoCanvas { composeCanvas ->
                    val nativeCanvas = composeCanvas.nativeCanvas

                    // Layer 1: Base PDF Surface
                    val dstRect = AndroidRectF(
                        originX,
                        originY,
                        originX + fittedW * zoomScale,
                        originY + fittedH * zoomScale
                    )
                    nativeCanvas.drawBitmap(pageBitmap, null, dstRect, null)

                    // Layer 1.5: Highlighting Underlay
                    state.highlightedLines.forEach { lineId ->
                        val hlLine = state.pageDom?.allLines?.firstOrNull { it.id == lineId }
                        if (hlLine != null && mapper != null) {
                            val screenRect = mapper.pdfBoxToScreenRect(
                                pdfRect = hlLine.bounds,
                                zoomScale = zoomScale,
                                panOffsetX = originX,
                                panOffsetY = originY,
                                scaleFactorPtToPx = scaleFactorPtToPx
                            )
                            val hlPaint = AndroidPaint().apply {
                                color = android.graphics.Color.argb(130, 255, 235, 59)
                                style = AndroidPaint.Style.FILL
                            }
                            nativeCanvas.drawRoundRect(
                                screenRect.left - 2f,
                                screenRect.top - 1f,
                                screenRect.right + 2f,
                                screenRect.bottom + 1f,
                                4f,
                                4f,
                                hlPaint
                            )
                        }
                    }

                    // Layer 2: Wavelet Inpainted Background Patches (erasing old text seamlessly)
                    state.inpaintedPatches.forEach { (lineId, patch) ->
                        val targetLine = state.pageDom?.allLines?.firstOrNull { it.id == lineId }
                        if (targetLine != null && mapper != null) {
                            val screenRect = mapper.pdfBoxToScreenRect(
                                pdfRect = targetLine.bounds,
                                zoomScale = zoomScale,
                                panOffsetX = originX,
                                panOffsetY = originY,
                                scaleFactorPtToPx = scaleFactorPtToPx
                            )

                            if (patch.isCleanWhite) {
                                val paint = AndroidPaint().apply {
                                    color = patch.dominantColorRgb
                                    style = AndroidPaint.Style.FILL
                                }
                                nativeCanvas.drawRect(
                                    screenRect.left - 2f,
                                    screenRect.top - 2f,
                                    screenRect.right + 2f,
                                    screenRect.bottom + 2f,
                                    paint
                                )
                            } else if (!patch.patchBitmap.isRecycled) {
                                val patchDst = AndroidRectF(
                                    screenRect.left - 2f,
                                    screenRect.top - 2f,
                                    screenRect.right + 2f,
                                    screenRect.bottom + 2f
                                )
                                nativeCanvas.drawBitmap(patch.patchBitmap, null, patchDst, null)
                            }
                        }
                    }

                    // Layer 3: Modified Chameleon Typography Rendered Directly onto Skia Surface
                    state.modifiedRecords.forEach { (lineId, record) ->
                        // Don't draw the line if it is currently being actively typed in the overlay
                        if (state.selectedLine?.id != lineId && mapper != null) {
                            val targetLine = record.originalLine
                            val screenRect = mapper.pdfBoxToScreenRect(
                                pdfRect = targetLine.bounds,
                                zoomScale = zoomScale,
                                panOffsetX = originX,
                                panOffsetY = originY,
                                scaleFactorPtToPx = scaleFactorPtToPx
                            )

                            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = state.customInkColor.toArgb()
                                textSize = targetLine.fontSizePt * scaleFactorPtToPx * zoomScale
                                typeface = record.resolvedFont.androidTypeface
                                textScaleX = record.typographyResult.primaryLineTz / 100f
                                letterSpacing = (record.typographyResult.primaryLineTc / max(1f, targetLine.fontSizePt))
                            }

                            // Calculate baseline
                            val baselineOffsetFromTop = targetLine.fontSizePt * 0.82f * scaleFactorPtToPx * zoomScale
                            val baselineScreenY = screenRect.top + baselineOffsetFromTop

                            nativeCanvas.drawText(
                                record.newText,
                                screenRect.left,
                                baselineScreenY,
                                paint
                            )
                        }
                    }

                    // Layer 4: Subtle Text Detection Outlines (Visual Proof that OCR is Live & Interactive)
                    if (state.activeTool == com.swiffle.app.pdfedit.pipeline.PdfEditTool.TEXT_EDIT && mapper != null) {
                        val detectedOutlinePaint = AndroidPaint().apply {
                            color = android.graphics.Color.argb(55, 0, 120, 212)
                            style = AndroidPaint.Style.STROKE
                            strokeWidth = 1.2f
                        }
                        val detectedFillPaint = AndroidPaint().apply {
                            color = android.graphics.Color.argb(12, 0, 120, 212)
                            style = AndroidPaint.Style.FILL
                        }
                        state.pageDom?.allLines?.forEach { line ->
                            if (line.id != state.selectedLine?.id) {
                                val lRect = mapper.pdfBoxToScreenRect(
                                    pdfRect = line.bounds,
                                    zoomScale = zoomScale,
                                    panOffsetX = originX,
                                    panOffsetY = originY,
                                    scaleFactorPtToPx = scaleFactorPtToPx
                                )
                                nativeCanvas.drawRoundRect(
                                    lRect.left - 2f,
                                    lRect.top - 1f,
                                    lRect.right + 2f,
                                    lRect.bottom + 1f,
                                    4f,
                                    4f,
                                    detectedFillPaint
                                )
                                nativeCanvas.drawRoundRect(
                                    lRect.left - 2f,
                                    lRect.top - 1f,
                                    lRect.right + 2f,
                                    lRect.bottom + 1f,
                                    4f,
                                    4f,
                                    detectedOutlinePaint
                                )
                            }
                        }
                    }

                    // Draw Glassmorphic Selection Bounding Box around currently selected line
                    val currentSelected = state.selectedLine
                    if (currentSelected != null && mapper != null) {
                        val selRect = mapper.pdfBoxToScreenRect(
                            pdfRect = currentSelected.bounds,
                            zoomScale = zoomScale,
                            panOffsetX = originX,
                            panOffsetY = originY,
                            scaleFactorPtToPx = scaleFactorPtToPx
                        )

                        val highlightPaint = AndroidPaint().apply {
                            color = android.graphics.Color.argb(35, 0, 120, 212)
                            style = AndroidPaint.Style.FILL
                        }
                        nativeCanvas.drawRoundRect(
                            selRect.left - 4f,
                            selRect.top - 2f,
                            selRect.right + 4f,
                            selRect.bottom + 2f,
                            6f,
                            6f,
                            highlightPaint
                        )

                        val borderPaint = AndroidPaint().apply {
                            color = android.graphics.Color.argb(180, 0, 120, 212)
                            style = AndroidPaint.Style.STROKE
                            strokeWidth = 2f
                        }
                        nativeCanvas.drawRoundRect(
                            selRect.left - 4f,
                            selRect.top - 2f,
                            selRect.right + 4f,
                            selRect.bottom + 2f,
                            6f,
                            6f,
                            borderPaint
                        )
                    }
                }
            }
        }

        // Live Floating Editor Card + Chameleon Inspector HUD (Lifts automatically with IME keyboard)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            // Live Quick-Input Field when a line is selected
            AnimatedVisibility(
                visible = state.selectedLine != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        BasicTextField(
                            value = state.currentEditText,
                            onValueChange = { viewModel.updateEditText(it) },
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = state.resolvedFont?.fontWeight ?: FontWeight.Normal,
                                fontStyle = state.resolvedFont?.fontStyle ?: FontStyle.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )

                        if (state.currentEditText.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateEditText("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Chameleon Inspector HUD Controls
            ChameleonInspectorHud(
                state = state,
                onThicknessChange = { viewModel.updateSubPixelThickness(it) },
                onSoftnessChange = { viewModel.updateScannerSoftness(it) },
                onColorChange = { viewModel.updateCustomInkColor(it) },
                onAutoMatch = { viewModel.autoMatchDna() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onSave = onSaveRequested,
                onDismissSelection = {
                    viewModel.clearSelection()
                    focusManager.clearFocus()
                }
            )
        }
    }
}
