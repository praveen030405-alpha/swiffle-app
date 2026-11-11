package com.swiffle.app.ui.canvas.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Windows 11 Fluent Interactive Transform Gizmo:
 * Renders an 8-handle transform box with rotation stem, drag hit-boxes,
 * and high-contrast Cobalt borders (#0078D4) with ambient drop shadows.
 * 
 * Non-blocking: Allows multi-touch pinch-to-zoom and external pans to pass through cleanly!
 */
@Composable
fun TransformGizmo(
    selectedObject: EditableObject,
    viewportZoom: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    pageOriginScreenX: Float,
    pageOriginScreenY: Float,
    onTransformUpdated: (EditableObject) -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert PDF point rect to Screen Pixel coordinates
    val screenRect = remember(
        selectedObject.currentPdfRect,
        viewportZoom,
        panOffsetX,
        panOffsetY,
        pageOriginScreenX,
        pageOriginScreenY
    ) {
        val left = pageOriginScreenX + (selectedObject.currentPdfRect.left * viewportZoom)
        val top = pageOriginScreenY + (selectedObject.currentPdfRect.top * viewportZoom)
        val right = pageOriginScreenX + (selectedObject.currentPdfRect.right * viewportZoom)
        val bottom = pageOriginScreenY + (selectedObject.currentPdfRect.bottom * viewportZoom)
        Rect(left, top, right, bottom)
    }

    val handleRadius = 16f // Touch radius in px
    val rotationStemLength = 36f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(selectedObject.id, viewportZoom, screenRect) {
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent(PointerEventPass.Main)
                        // If multi-touch (pinch-to-zoom), do NOT consume! Let zoom/pan happen freely!
                        if (downEvent.changes.size > 1) {
                            continue
                        }

                        val downChange = downEvent.changes.firstOrNull() ?: continue
                        if (!downChange.pressed) continue

                        val hitHandle = hitTestGizmo(
                            touch = downChange.position,
                            rect = screenRect,
                            handleRadius = handleRadius * 1.6f,
                            rotationStemLength = rotationStemLength,
                            rotationDegrees = selectedObject.rotationDegrees
                        )

                        // If touch didn't hit gizmo handle or body, do not consume!
                        if (hitHandle == null) {
                            continue
                        }

                        // Consume touch because it hit the gizmo!
                        downChange.consume()
                        val initialCenterScreen = Offset(screenRect.center.x, screenRect.center.y)

                        while (true) {
                            val dragEvent = awaitPointerEvent(PointerEventPass.Main)
                            // If user adds second finger during drag, release gizmo to let zoom take over
                            if (dragEvent.changes.size > 1) {
                                break
                            }

                            val dragChange = dragEvent.changes.firstOrNull() ?: break
                            if (!dragChange.pressed) {
                                break
                            }

                            val dragAmount = dragChange.positionChange()
                            if (dragAmount != Offset.Zero) {
                                dragChange.consume()

                                val deltaPdfX = dragAmount.x / viewportZoom
                                val deltaPdfY = dragAmount.y / viewportZoom

                                when (hitHandle) {
                                    GizmoHandle.BODY -> {
                                        selectedObject.currentPdfRect.offset(deltaPdfX, deltaPdfY)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.TOP_LEFT -> {
                                        selectedObject.currentPdfRect.left += deltaPdfX
                                        selectedObject.currentPdfRect.top += deltaPdfY
                                        normalizeRect(selectedObject.currentPdfRect)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.TOP_RIGHT -> {
                                        selectedObject.currentPdfRect.right += deltaPdfX
                                        selectedObject.currentPdfRect.top += deltaPdfY
                                        normalizeRect(selectedObject.currentPdfRect)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.BOTTOM_LEFT -> {
                                        selectedObject.currentPdfRect.left += deltaPdfX
                                        selectedObject.currentPdfRect.bottom += deltaPdfY
                                        normalizeRect(selectedObject.currentPdfRect)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.BOTTOM_RIGHT -> {
                                        selectedObject.currentPdfRect.right += deltaPdfX
                                        selectedObject.currentPdfRect.bottom += deltaPdfY
                                        normalizeRect(selectedObject.currentPdfRect)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.TOP_CENTER -> {
                                        selectedObject.currentPdfRect.top += deltaPdfY
                                        normalizeRect(selectedObject.currentPdfRect)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.BOTTOM_CENTER -> {
                                        selectedObject.currentPdfRect.bottom += deltaPdfY
                                        normalizeRect(selectedObject.currentPdfRect)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.MIDDLE_LEFT -> {
                                        selectedObject.currentPdfRect.left += deltaPdfX
                                        normalizeRect(selectedObject.currentPdfRect)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.MIDDLE_RIGHT -> {
                                        selectedObject.currentPdfRect.right += deltaPdfX
                                        normalizeRect(selectedObject.currentPdfRect)
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                    GizmoHandle.ROTATE -> {
                                        val currentTouch = dragChange.position
                                        val angleRad = atan2(
                                            currentTouch.y - initialCenterScreen.y,
                                            currentTouch.x - initialCenterScreen.x
                                        )
                                        var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat() + 90f
                                        if (angleDeg < 0) angleDeg += 360f
                                        if (angleDeg >= 360f) angleDeg -= 360f

                                        val snapTolerance = 3.5f
                                        for (cardinal in listOf(0f, 90f, 180f, 270f, 360f)) {
                                            if (kotlin.math.abs(angleDeg - cardinal) < snapTolerance) {
                                                angleDeg = if (cardinal == 360f) 0f else cardinal
                                                break
                                            }
                                        }
                                        selectedObject.rotationDegrees = angleDeg
                                        selectedObject.isMovedOrModified = true
                                        onTransformUpdated(selectedObject)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val center = screenRect.center

        rotate(degrees = selectedObject.rotationDegrees, pivot = center) {
            // 1. Ambient drop shadow outline
            drawRect(
                color = Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(screenRect.left - 1.5f, screenRect.top - 1.5f),
                size = Size(screenRect.width + 3f, screenRect.height + 3f),
                style = Stroke(width = 3.5f)
            )

            // 2. Windows 11 Fluent Cobalt primary border
            drawRect(
                color = Color(0xFF0078D4),
                topLeft = screenRect.topLeft,
                size = screenRect.size,
                style = Stroke(
                    width = 2.0f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                )
            )

            // 3. Rotation stem & top handle
            val stemStart = Offset(screenRect.center.x, screenRect.top)
            val stemEnd = Offset(screenRect.center.x, screenRect.top - rotationStemLength)

            drawLine(
                color = Color(0xFF0078D4),
                start = stemStart,
                end = stemEnd,
                strokeWidth = 2.0f
            )

            drawHandleCircle(stemEnd, handleRadius * 0.9f, Color(0xFF0078D4), isRotation = true)

            // 4. Corner Handles (Squares)
            val handles = listOf(
                Offset(screenRect.left, screenRect.top),
                Offset(screenRect.right, screenRect.top),
                Offset(screenRect.left, screenRect.bottom),
                Offset(screenRect.right, screenRect.bottom)
            )
            for (h in handles) {
                drawHandleSquare(h, handleRadius)
            }

            // 5. Edge Middle Handles (Circles)
            val edgeMiddles = listOf(
                Offset(screenRect.center.x, screenRect.top),
                Offset(screenRect.center.x, screenRect.bottom),
                Offset(screenRect.left, screenRect.center.y),
                Offset(screenRect.right, screenRect.center.y)
            )
            for (m in edgeMiddles) {
                drawHandleCircle(m, handleRadius * 0.75f, Color(0xFF0078D4), isRotation = false)
            }
        }
    }
}

private fun DrawScope.drawHandleSquare(center: Offset, radius: Float) {
    drawRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 2f)
    )
    drawRect(
        color = Color.White,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f)
    )
    drawRect(
        color = Color(0xFF0078D4),
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 2.5f)
    )
}

private fun DrawScope.drawHandleCircle(
    center: Offset,
    radius: Float,
    accentColor: Color,
    isRotation: Boolean
) {
    drawCircle(
        color = Color.Black.copy(alpha = 0.35f),
        radius = radius + 1f,
        center = center,
        style = Stroke(width = 1.5f)
    )
    drawCircle(
        color = Color.White,
        radius = radius,
        center = center
    )
    drawCircle(
        color = accentColor,
        radius = radius,
        center = center,
        style = Stroke(width = 2.5f)
    )

    if (isRotation) {
        drawCircle(
            color = accentColor,
            radius = radius * 0.45f,
            center = center
        )
    }
}

private fun hitTestGizmo(
    touch: Offset,
    rect: Rect,
    handleRadius: Float,
    rotationStemLength: Float,
    rotationDegrees: Float
): GizmoHandle? {
    val center = rect.center
    val angleRad = Math.toRadians(-rotationDegrees.toDouble())
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()

    val dx = touch.x - center.x
    val dy = touch.y - center.y
    val unrotatedX = center.x + (dx * cosA - dy * sinA)
    val unrotatedY = center.y + (dx * sinA + dy * cosA)
    val localTouch = Offset(unrotatedX, unrotatedY)

    val rotateKnobCenter = Offset(rect.center.x, rect.top - rotationStemLength)
    if ((localTouch - rotateKnobCenter).getDistance() <= handleRadius * 1.5f) {
        return GizmoHandle.ROTATE
    }

    if ((localTouch - rect.topLeft).getDistance() <= handleRadius) return GizmoHandle.TOP_LEFT
    if ((localTouch - rect.topRight).getDistance() <= handleRadius) return GizmoHandle.TOP_RIGHT
    if ((localTouch - rect.bottomLeft).getDistance() <= handleRadius) return GizmoHandle.BOTTOM_LEFT
    if ((localTouch - rect.bottomRight).getDistance() <= handleRadius) return GizmoHandle.BOTTOM_RIGHT

    val topCenter = Offset(rect.center.x, rect.top)
    val bottomCenter = Offset(rect.center.x, rect.bottom)
    val middleLeft = Offset(rect.left, rect.center.y)
    val middleRight = Offset(rect.right, rect.center.y)

    if ((localTouch - topCenter).getDistance() <= handleRadius) return GizmoHandle.TOP_CENTER
    if ((localTouch - bottomCenter).getDistance() <= handleRadius) return GizmoHandle.BOTTOM_CENTER
    if ((localTouch - middleLeft).getDistance() <= handleRadius) return GizmoHandle.MIDDLE_LEFT
    if ((localTouch - middleRight).getDistance() <= handleRadius) return GizmoHandle.MIDDLE_RIGHT

    if (rect.contains(localTouch)) {
        return GizmoHandle.BODY
    }

    return null
}

private fun normalizeRect(rect: android.graphics.RectF) {
    if (rect.left > rect.right) {
        val temp = rect.left
        rect.left = rect.right
        rect.right = temp
    }
    if (rect.top > rect.bottom) {
        val temp = rect.top
        rect.top = rect.bottom
        rect.bottom = temp
    }
    if (rect.width() < 10f) rect.right = rect.left + 10f
    if (rect.height() < 8f) rect.bottom = rect.top + 8f
}
