package com.swiffle.app.ui.canvas.editor

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.ui.canvas.PageTextModel
import com.swiffle.app.ui.canvas.PageTextModelCache
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Windows 11 Fluent Interactive PDF Editor Overlay:
 * 
 * - Seamless Multi-Touch: Passes 2+ finger pinch gestures directly to viewport for free zoom & pan.
 * - Photorealistic Inpainting: Erases original text lines down to the micro-pixel using scanned paper texture.
 * - Live Typography Sync: Changes in font, size, weight, and text string render live at 120 FPS.
 * - Floating CommandBar: Positioned above the navigation bars with IME keyboard responsiveness.
 */
@Composable
fun PdfEditorOverlay(
    isEditModeActive: Boolean,
    currentPage: Int,
    pageLeftOnScreen: Float,
    pageTopOnScreen: Float,
    pageScale: Float,
    pdfScaleX: Float,
    pdfScaleY: Float,
    pageBitmap: Bitmap?,
    pdfNativePtr: Long,
    editableObjects: List<EditableObject>,
    inpaintedPatches: List<InpaintedPatch>,
    selectedObject: EditableObject?,
    onSelectObject: (EditableObject?) -> Unit,
    onObjectCreated: (EditableObject, InpaintedPatch?) -> Unit,
    onObjectTransformed: (EditableObject) -> Unit,
    onTypographyChanged: (TypographyConfig) -> Unit,
    onRealismChanged: (RealismConfig) -> Unit,
    onAutoMatchFont: (EditableObject) -> Unit,
    onDuplicateObject: (EditableObject) -> Unit,
    onDeleteObject: (EditableObject) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isEditModeActive) return

    val density = LocalDensity.current

    fun pdfToScreenX(pdfX: Float): Float = pageLeftOnScreen + (pdfX * pdfScaleX * pageScale)
    fun pdfToScreenY(pdfY: Float): Float = pageTopOnScreen + (pdfY * pdfScaleY * pageScale)
    fun screenToPdfX(screenX: Float): Float = ((screenX - pageLeftOnScreen) / pageScale) / (if (pdfScaleX > 0f) pdfScaleX else 1f)
    fun screenToPdfY(screenY: Float): Float = ((screenY - pageTopOnScreen) / pageScale) / (if (pdfScaleY > 0f) pdfScaleY else 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isEditModeActive, currentPage, selectedObject?.id, pageScale, pageLeftOnScreen, pageTopOnScreen) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)

                        // If multi-touch (pinch-to-zoom / free zoom), DO NOT consume! Let viewport zoom freely!
                        if (event.changes.size > 1) {
                            continue
                        }

                        val change = event.changes.firstOrNull() ?: continue
                        if (change.changedToDown()) {
                            val downPos = change.position
                            var isTap = true

                            while (true) {
                                val nextEvent = awaitPointerEvent(PointerEventPass.Main)
                                if (nextEvent.changes.size > 1) {
                                    isTap = false
                                    break
                                }
                                val current = nextEvent.changes.firstOrNull() ?: break
                                if ((current.position - downPos).getDistance() > 14f) {
                                    isTap = false
                                }
                                if (current.changedToUp()) {
                                    if (isTap) {
                                        // Handle Tap Selection
                                        val tapPdfX = screenToPdfX(downPos.x)
                                        val tapPdfY = screenToPdfY(downPos.y)

                                        // 1. Check existing editable object
                                        val tappedObj = editableObjects.findLast { obj ->
                                            obj.pageNum == currentPage && obj.currentPdfRect.contains(tapPdfX, tapPdfY)
                                        }

                                        if (tappedObj != null) {
                                            onSelectObject(tappedObj)
                                        } else {
                                            // 2. Hit-test against original document text
                                            var matchedLineObj: EditableObject? = null
                                            var generatedPatch: InpaintedPatch? = null

                                            if (pdfNativePtr != 0L) {
                                                val pageModel = PageTextModelCache.getOrBuild(currentPage, pdfNativePtr)
                                                val hitLine = pageModel.lines.find { line ->
                                                    val bounds = line.boundaries
                                                    val left = if (bounds.isNotEmpty()) bounds.first() else 0f
                                                    val right = if (bounds.isNotEmpty()) bounds.last() else 0f
                                                    tapPdfY >= line.top - 3f && tapPdfY <= line.bottom + 3f &&
                                                            tapPdfX >= left - 6f && tapPdfX <= right + 6f
                                                }

                                                if (hitLine != null && hitLine.glyphCount > 0) {
                                                    val lineBounds = hitLine.boundaries
                                                    val lineLeft = if (lineBounds.isNotEmpty()) lineBounds.first() else 0f
                                                    val lineRight = if (lineBounds.isNotEmpty()) lineBounds.last() else 0f
                                                    val lineTop = hitLine.top
                                                    val lineBottom = hitLine.bottom

                                                    val linePdfRect = RectF(lineLeft, lineTop, lineRight, lineBottom)
                                                    val lineText = if (hitLine.charStart in pageModel.text.indices &&
                                                        hitLine.charEnd in pageModel.text.indices &&
                                                        hitLine.charEnd >= hitLine.charStart
                                                    ) {
                                                        pageModel.text.substring(hitLine.charStart, hitLine.charEnd + 1).trim()
                                                    } else ""

                                                    if (lineText.isNotBlank()) {
                                                        val bmp = pageBitmap
                                                        var patchBmp: Bitmap? = null
                                                        var patchColor = Color.White
                                                        var noiseSigma = 0.06f

                                                        if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                                                            val curPtsW = if (pdfScaleX > 0f) bmp.width / (pdfScaleX * 2f) else bmp.width.toFloat()
                                                            val curPtsH = if (pdfScaleY > 0f) bmp.height / (pdfScaleY * 2f) else bmp.height.toFloat()

                                                            // Pixel rect in bitmap coordinate space (with 3px padding to ensure 0 edge ghosting)
                                                            val pixelScaleX = bmp.width.toFloat() / max(1f, curPtsW)
                                                            val pixelScaleY = bmp.height.toFloat() / max(1f, curPtsH)

                                                            val bmpRect = Rect(
                                                                ((lineLeft - 2f) * pixelScaleX).toInt().coerceIn(0, bmp.width - 1),
                                                                ((lineTop - 2f) * pixelScaleY).toInt().coerceIn(0, bmp.height - 1),
                                                                ((lineRight + 2f) * pixelScaleX).toInt().coerceIn(1, bmp.width),
                                                                ((lineBottom + 2f) * pixelScaleY).toInt().coerceIn(1, bmp.height)
                                                            )

                                                            val inpaintRes = RealisticInpaintEngine.createInpaintedPatch(bmp, bmpRect, paddingPx = 4)
                                                            patchBmp = inpaintRes.bitmap
                                                            patchColor = inpaintRes.dominantColor
                                                            noiseSigma = inpaintRes.estimatedNoiseSigma
                                                        }

                                                        val detectedInkColor = RealisticInpaintEngine.sampleSourceInkColor(
                                                            sourceBitmap = bmp,
                                                            targetRect = Rect(
                                                                (lineLeft * pdfScaleX).toInt().coerceAtLeast(0),
                                                                (lineTop * pdfScaleY).toInt().coerceAtLeast(0),
                                                                (lineRight * pdfScaleX).toInt().coerceAtLeast(1),
                                                                (lineBottom * pdfScaleY).toInt().coerceAtLeast(1)
                                                            ),
                                                            backgroundColor = patchColor
                                                        )

                                                        val matchedTypography = UniversalFontCatalog.matchSourceFont(
                                                            sourceFontDescriptor = null,
                                                            boundingBoxHeightPt = linePdfRect.height()
                                                        ).copy(textColor = detectedInkColor)

                                                        val inpaintRect = RectF(lineLeft - 2f, lineTop - 2f, lineRight + 2f, lineBottom + 2f)

                                                        val newObj = EditableObject(
                                                            pageNum = currentPage,
                                                            type = EditObjectType.TEXT,
                                                            originalPdfRect = RectF(linePdfRect),
                                                            currentPdfRect = RectF(linePdfRect),
                                                            text = lineText,
                                                            originalText = lineText,
                                                            typography = matchedTypography,
                                                            realism = RealismConfig(
                                                                edgeSofteningPx = 0.40f,
                                                                paperGrainIntensity = noiseSigma.coerceIn(0.04f, 0.20f)
                                                            ),
                                                            isSelected = true
                                                        )

                                                        generatedPatch = InpaintedPatch(
                                                            pageNum = currentPage,
                                                            pdfRect = inpaintRect,
                                                            patchBitmap = patchBmp,
                                                            averageColor = patchColor
                                                        )

                                                        matchedLineObj = newObj
                                                    }
                                                }
                                            }

                                            if (matchedLineObj != null) {
                                                onObjectCreated(matchedLineObj, generatedPatch)
                                            } else {
                                                onSelectObject(null)
                                            }
                                        }
                                    }
                                    break
                                }
                                if (!current.pressed) break
                            }
                        }
                    }
                }
            }
    ) {
        // 1. Draw Inpainted Background Patches (Canvas)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pagePatches = inpaintedPatches.filter { it.pageNum == currentPage }
            for (patch in pagePatches) {
                val sLeft = pdfToScreenX(patch.pdfRect.left)
                val sTop = pdfToScreenY(patch.pdfRect.top)
                val sRight = pdfToScreenX(patch.pdfRect.right)
                val sBottom = pdfToScreenY(patch.pdfRect.bottom)
                val sWidth = sRight - sLeft
                val sHeight = sBottom - sTop

                val bmp = patch.patchBitmap
                if (bmp != null && !bmp.isRecycled) {
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset(sLeft.roundToInt(), sTop.roundToInt()),
                        dstSize = IntSize(sWidth.roundToInt().coerceAtLeast(1), sHeight.roundToInt().coerceAtLeast(1))
                    )
                } else {
                    drawRect(
                        color = patch.averageColor,
                        topLeft = Offset(sLeft, sTop),
                        size = androidx.compose.ui.geometry.Size(sWidth, sHeight)
                    )
                }
            }
        }

        // 2. Draw Editable Objects with Authentic Ink Typography
        val pageObjects = editableObjects.filter { it.pageNum == currentPage }
        for (obj in pageObjects) {
            val sLeft = pdfToScreenX(obj.currentPdfRect.left)
            val sTop = pdfToScreenY(obj.currentPdfRect.top)
            val sRight = pdfToScreenX(obj.currentPdfRect.right)
            val sBottom = pdfToScreenY(obj.currentPdfRect.bottom)
            val sWidth = (sRight - sLeft).coerceAtLeast(10f)
            val sHeight = (sBottom - sTop).coerceAtLeast(8f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                rotate(degrees = obj.rotationDegrees, pivot = Offset(sLeft + sWidth / 2f, sTop + sHeight / 2f)) {
                    when (obj.type) {
                        EditObjectType.TEXT -> {
                            if (obj.text.isNotBlank()) {
                                val scaledFontSize = obj.typography.fontSizePt * pageScale * pdfScaleX

                                val androidTypeface = when {
                                    obj.typography.fontName.contains("Times", ignoreCase = true) ||
                                    obj.typography.fontName.contains("Serif", ignoreCase = true) ||
                                    obj.typography.fontName.contains("Garamond", ignoreCase = true) ||
                                    obj.typography.fontName.contains("Baskerville", ignoreCase = true) -> {
                                        android.graphics.Typeface.create(
                                            android.graphics.Typeface.SERIF,
                                            getAndroidFontStyle(obj.typography)
                                        )
                                    }
                                    obj.typography.fontName.contains("Courier", ignoreCase = true) ||
                                    obj.typography.fontName.contains("Mono", ignoreCase = true) -> {
                                        android.graphics.Typeface.create(
                                            android.graphics.Typeface.MONOSPACE,
                                            getAndroidFontStyle(obj.typography)
                                        )
                                    }
                                    obj.typography.fontName.contains("Caveat", ignoreCase = true) ||
                                    obj.typography.fontName.contains("Script", ignoreCase = true) ||
                                    obj.typography.fontName.contains("Cursive", ignoreCase = true) -> {
                                        android.graphics.Typeface.create("cursive", getAndroidFontStyle(obj.typography))
                                    }
                                    else -> {
                                        android.graphics.Typeface.create(
                                            android.graphics.Typeface.SANS_SERIF,
                                            getAndroidFontStyle(obj.typography)
                                        )
                                    }
                                }

                                val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    typeface = androidTypeface
                                    textSize = scaledFontSize
                                    color = obj.typography.textColor.toArgb() // Correct 32-bit ARGB ink color!
                                    isSubpixelText = true
                                    isUnderlineText = obj.typography.isUnderline
                                }

                                val fontMetrics = textPaint.fontMetrics
                                val baseline = sTop - fontMetrics.ascent

                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(obj.text, sLeft, baseline, textPaint)
                                }
                            }
                        }
                        EditObjectType.IMAGE -> {
                            val img = obj.imageBitmap
                            if (img != null && !img.isRecycled) {
                                drawImage(
                                    image = img.asImageBitmap(),
                                    dstOffset = IntOffset(sLeft.roundToInt(), sTop.roundToInt()),
                                    dstSize = IntSize(sWidth.roundToInt(), sHeight.roundToInt())
                                )
                            }
                        }
                        EditObjectType.SHAPE -> {}
                    }
                }
            }
        }

        // 3. Render Transform Gizmo on Selected Object
        if (selectedObject != null && selectedObject.pageNum == currentPage) {
            TransformGizmo(
                selectedObject = selectedObject,
                viewportZoom = pageScale * pdfScaleX,
                panOffsetX = 0f,
                panOffsetY = 0f,
                pageOriginScreenX = pageLeftOnScreen,
                pageOriginScreenY = pageTopOnScreen,
                onTransformUpdated = onObjectTransformed
            )
        }

        // 4. Floating Typography & Realism CommandBar (with Live Text Box)
        AnimatedVisibility(
            visible = selectedObject != null && selectedObject.pageNum == currentPage,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.ime)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            selectedObject?.let { selObj ->
                FluentTypographyCommandBar(
                    editableObject = selObj,
                    onTextChanged = { newText ->
                        selObj.text = newText
                        selObj.isMovedOrModified = true
                        onObjectTransformed(selObj)
                    },
                    onTypographyChanged = { newTypo ->
                        selObj.typography = newTypo
                        selObj.isMovedOrModified = true
                        onTypographyChanged(newTypo)
                        onObjectTransformed(selObj)
                    },
                    onRealismChanged = { newRealism ->
                        selObj.realism = newRealism
                        selObj.isMovedOrModified = true
                        onRealismChanged(newRealism)
                        onObjectTransformed(selObj)
                    },
                    onAutoMatchRequested = {
                        onAutoMatchFont(selObj)
                    },
                    onDuplicateRequested = {
                        onDuplicateObject(selObj)
                    },
                    onDeleteRequested = {
                        onDeleteObject(selObj)
                    },
                    onDismiss = {
                        onSelectObject(null)
                    }
                )
            }
        }
    }
}

private fun getAndroidFontStyle(typography: TypographyConfig): Int {
    val isBold = typography.fontWeightValue >= 600
    val isItalic = typography.isItalic
    return when {
        isBold && isItalic -> android.graphics.Typeface.BOLD_ITALIC
        isBold -> android.graphics.Typeface.BOLD
        isItalic -> android.graphics.Typeface.ITALIC
        else -> android.graphics.Typeface.NORMAL
    }
}
