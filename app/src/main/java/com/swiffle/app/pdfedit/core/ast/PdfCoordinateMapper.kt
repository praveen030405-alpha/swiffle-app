package com.swiffle.app.pdfedit.core.ast

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import kotlin.math.abs

/**
 * Production-Grade Bi-directional Affine Coordinate Mapper between PDF User Space (72 DPI, bottom-left origin)
 * and Jetpack Compose Viewport Canvas Space (pixels, top-left origin).
 * 
 * Supports arbitrary Page Rotations (0Â°, 90Â°, 180Â°, 270Â°), CropBox/MediaBox origin offsets,
 * dynamic Viewport Zoom, Pan Offsets, and Multi-page vertical/horizontal layout offsets.
 */
class PdfCoordinateMapper(
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val rotationDegrees: Int = 0,
    val cropBox: RectF = RectF(0f, 0f, pageWidthPt, pageHeightPt)
) {
    // Effective rendered width & height on screen at 1x (72 DPI basis or scaled viewport basis)
    val effectiveWidthPt: Float = if (rotationDegrees == 90 || rotationDegrees == 270) pageHeightPt else pageWidthPt
    val effectiveHeightPt: Float = if (rotationDegrees == 90 || rotationDegrees == 270) pageWidthPt else pageHeightPt

    /**
     * Converts a Screen Canvas Coordinate (pixels) to PDF User Space Point (points, bottom-left origin).
     */
    fun screenToPdfPoint(
        screenX: Float,
        screenY: Float,
        zoomScale: Float,
        panOffsetX: Float,
        panOffsetY: Float,
        pageOriginScreenX: Float = 0f,
        pageOriginScreenY: Float = 0f,
        scaleFactorPtToPx: Float = 1.0f
    ): PointF {
        // Step 1: Remove viewport pan and zoom
        val unzoomedX = (screenX - panOffsetX - pageOriginScreenX) / zoomScale
        val unzoomedY = (screenY - panOffsetY - pageOriginScreenY) / zoomScale

        // Step 2: Convert screen pixels to PDF points
        val pointX = unzoomedX / scaleFactorPtToPx
        val pointY = unzoomedY / scaleFactorPtToPx

        // Step 3: Invert page rotation and bottom-left origin flip
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val pdfX: Float
        val pdfY: Float

        when (normalizedRotation) {
            0 -> {
                pdfX = pointX + cropBox.left
                pdfY = (pageHeightPt - pointY) + cropBox.bottom
            }
            90 -> {
                pdfX = pointY + cropBox.left
                pdfY = pointX + cropBox.bottom
            }
            180 -> {
                pdfX = (pageWidthPt - pointX) + cropBox.left
                pdfY = pointY + cropBox.bottom
            }
            270 -> {
                pdfX = (pageWidthPt - pointY) + cropBox.left
                pdfY = (pageHeightPt - pointX) + cropBox.bottom
            }
            else -> {
                pdfX = pointX + cropBox.left
                pdfY = (pageHeightPt - pointY) + cropBox.bottom
            }
        }

        return PointF(pdfX, pdfY)
    }

    /**
     * Converts a PDF Bounding Box (in PDF points, bottom-left origin) to Compose Viewport Rect (pixels).
     */
    fun pdfBoxToScreenRect(
        pdfRect: RectF,
        zoomScale: Float,
        panOffsetX: Float,
        panOffsetY: Float,
        pageOriginScreenX: Float = 0f,
        pageOriginScreenY: Float = 0f,
        scaleFactorPtToPx: Float = 1.0f
    ): ComposeRect {
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360

        // Calculate unrotated, unscaled screen point positions (PDF origin bottom-left -> Screen origin top-left)
        val relPdfLeft = pdfRect.left - cropBox.left
        val relPdfBottom = pdfRect.bottom - cropBox.bottom
        val relPdfRight = pdfRect.right - cropBox.left
        val relPdfTop = pdfRect.top - cropBox.bottom

        val screenL: Float
        val screenT: Float
        val screenR: Float
        val screenB: Float

        when (normalizedRotation) {
            0 -> {
                screenL = relPdfLeft
                screenT = pageHeightPt - relPdfTop
                screenR = relPdfRight
                screenB = pageHeightPt - relPdfBottom
            }
            90 -> {
                screenL = relPdfBottom
                screenT = relPdfLeft
                screenR = relPdfTop
                screenB = relPdfRight
            }
            180 -> {
                screenL = pageWidthPt - relPdfRight
                screenT = relPdfBottom
                screenR = pageWidthPt - relPdfLeft
                screenB = relPdfTop
            }
            270 -> {
                screenL = pageHeightPt - relPdfTop
                screenT = pageWidthPt - relPdfRight
                screenR = pageHeightPt - relPdfBottom
                screenB = pageWidthPt - relPdfLeft
            }
            else -> {
                screenL = relPdfLeft
                screenT = pageHeightPt - relPdfTop
                screenR = relPdfRight
                screenB = pageHeightPt - relPdfBottom
            }
        }

        // Apply scale factor (points to screen pixels), zoom, and pan offset
        val finalLeft = (minOf(screenL, screenR) * scaleFactorPtToPx * zoomScale) + panOffsetX + pageOriginScreenX
        val finalTop = (minOf(screenT, screenB) * scaleFactorPtToPx * zoomScale) + panOffsetY + pageOriginScreenY
        val finalRight = (maxOf(screenL, screenR) * scaleFactorPtToPx * zoomScale) + panOffsetX + pageOriginScreenX
        val finalBottom = (maxOf(screenT, screenB) * scaleFactorPtToPx * zoomScale) + panOffsetY + pageOriginScreenY

        return ComposeRect(
            left = finalLeft,
            top = finalTop,
            right = finalRight,
            bottom = finalBottom
        )
    }

    /**
     * Production-Grade Affine Transformation supporting 6-element PDF Text Matrix (Tm: [a b c d e f])
     * allowing perfect mapping for skewed, sheared, or rotated text watermarks and headings.
     */
    fun transformTextMatrixPoint(
        point: PointF,
        textMatrix: Matrix?
    ): PointF {
        if (textMatrix == null) return point
        val pts = floatArrayOf(point.x, point.y)
        textMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    /**
     * Converts a single PDF point to Screen coordinate.
     */
    fun pdfPointToScreen(
        pdfPoint: PointF,
        zoomScale: Float,
        panOffsetX: Float,
        panOffsetY: Float,
        pageOriginScreenX: Float = 0f,
        pageOriginScreenY: Float = 0f,
        scaleFactorPtToPx: Float = 1.0f
    ): Offset {
        val rect = pdfBoxToScreenRect(
            pdfRect = RectF(pdfPoint.x, pdfPoint.y, pdfPoint.x, pdfPoint.y),
            zoomScale = zoomScale,
            panOffsetX = panOffsetX,
            panOffsetY = panOffsetY,
            pageOriginScreenX = pageOriginScreenX,
            pageOriginScreenY = pageOriginScreenY,
            scaleFactorPtToPx = scaleFactorPtToPx
        )
        return Offset(rect.left, rect.top)
    }
}

