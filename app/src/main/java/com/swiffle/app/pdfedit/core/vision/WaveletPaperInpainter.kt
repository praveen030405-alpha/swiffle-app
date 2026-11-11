package com.swiffle.app.pdfedit.core.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class InpaintedPaperResult(
    val patchBitmap: Bitmap,
    val dominantColorRgb: Int,
    val isCleanWhite: Boolean
)

/**
 * Two-Scale Spatial Frequency Separation Inpainter:
 * Reconstructs authentic paper backgrounds without flat boxes or artificial static:
 * 1. Low-Frequency Pass: Bilinear surface interpolation across boundary paper luminance/gradients.
 * 2. High-Frequency Pass: Seamless transfer of microscopic paper fiber residual from clean adjacent regions.
 */
object WaveletPaperInpainter {

    suspend fun inpaintBackground(
        sourceBitmap: Bitmap?,
        targetRect: Rect,
        paddingPx: Int = 3
    ): InpaintedPaperResult = withContext(Dispatchers.Default) {
        if (sourceBitmap == null || sourceBitmap.isRecycled || targetRect.width() <= 0 || targetRect.height() <= 0) {
            val fallback = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                eraseColor(AndroidColor.WHITE)
            }
            return@withContext InpaintedPaperResult(fallback, AndroidColor.WHITE, true)
        }

        val bmpW = sourceBitmap.width
        val bmpH = sourceBitmap.height

        val left = max(0, targetRect.left - paddingPx)
        val top = max(0, targetRect.top - paddingPx)
        val right = min(bmpW, targetRect.right + paddingPx)
        val bottom = min(bmpH, targetRect.bottom + paddingPx)

        val patchW = max(1, right - left)
        val patchH = max(1, bottom - top)

        // Sample perimeter ring outside the text bounds
        val ringThickness = 4
        val topRingY = max(0, top - ringThickness)
        val btmRingY = min(bmpH, bottom + ringThickness)
        val leftRingX = max(0, left - ringThickness)
        val rightRingX = min(bmpW, right + ringThickness)

        var bgRSum = 0L
        var bgGSum = 0L
        var bgBSum = 0L
        var bgCount = 0

        val boundaryTopColors = mutableListOf<Int>()
        val boundaryBtmColors = mutableListOf<Int>()
        val boundaryLeftColors = mutableListOf<Int>()
        val boundaryRightColors = mutableListOf<Int>()

        // Sample top border via bulk getPixels
        val topBandH = max(0, top - topRingY)
        val ringW = max(0, right - left)
        if (topBandH > 0 && ringW > 0) {
            val topPixels = IntArray(ringW * topBandH)
            sourceBitmap.getPixels(topPixels, 0, ringW, left, topRingY, ringW, topBandH)
            for (p in topPixels) {
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum > 185f) {
                    bgRSum += r; bgGSum += g; bgBSum += b; bgCount++
                    boundaryTopColors.add(p)
                }
            }
        }

        // Sample bottom border via bulk getPixels
        val btmBandH = max(0, btmRingY - bottom)
        if (btmBandH > 0 && ringW > 0) {
            val btmPixels = IntArray(ringW * btmBandH)
            sourceBitmap.getPixels(btmPixels, 0, ringW, left, bottom, ringW, btmBandH)
            for (p in btmPixels) {
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum > 185f) {
                    bgRSum += r; bgGSum += g; bgBSum += b; bgCount++
                    boundaryBtmColors.add(p)
                }
            }
        }

        // Sample left border via bulk getPixels
        val leftBandW = max(0, left - leftRingX)
        val ringH = max(0, bottom - top)
        if (leftBandW > 0 && ringH > 0) {
            val leftPixels = IntArray(leftBandW * ringH)
            sourceBitmap.getPixels(leftPixels, 0, leftBandW, leftRingX, top, leftBandW, ringH)
            for (p in leftPixels) {
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum > 185f) {
                    bgRSum += r; bgGSum += g; bgBSum += b; bgCount++
                    boundaryLeftColors.add(p)
                }
            }
        }

        // Sample right border via bulk getPixels
        val rightBandW = max(0, rightRingX - right)
        if (rightBandW > 0 && ringH > 0) {
            val rightPixels = IntArray(rightBandW * ringH)
            sourceBitmap.getPixels(rightPixels, 0, rightBandW, right, top, rightBandW, ringH)
            for (p in rightPixels) {
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum > 185f) {
                    bgRSum += r; bgGSum += g; bgBSum += b; bgCount++
                    boundaryRightColors.add(p)
                }
            }
        }

        val avgR = if (bgCount > 0) (bgRSum / bgCount).toInt().coerceIn(0, 255) else 255
        val avgG = if (bgCount > 0) (bgGSum / bgCount).toInt().coerceIn(0, 255) else 255
        val avgB = if (bgCount > 0) (bgBSum / bgCount).toInt().coerceIn(0, 255) else 255
        val dominantColor = AndroidColor.rgb(avgR, avgG, avgB)

        val patchBitmap = Bitmap.createBitmap(patchW, patchH, Bitmap.Config.ARGB_8888)

        // Case A: Pure or near-pure digital white paper (> 245 luminance) -> 100% clean solid tone (Zero TV static)
        val isCleanWhite = avgR > 245 && avgG > 245 && avgB > 245
        if (isCleanWhite || boundaryTopColors.isEmpty() || boundaryBtmColors.isEmpty()) {
            val canvas = Canvas(patchBitmap)
            canvas.drawColor(dominantColor)
            return@withContext InpaintedPaperResult(patchBitmap, dominantColor, isCleanWhite)
        }

        // Case B: Textured/Scanned Document: Bilinear Low-Frequency Illumination Interpolation
        val topAvg = averageColor(boundaryTopColors, dominantColor)
        val btmAvg = averageColor(boundaryBtmColors, dominantColor)
        val leftAvg = averageColor(boundaryLeftColors, dominantColor)
        val rightAvg = averageColor(boundaryRightColors, dominantColor)

        // Pre-sample high-frequency paper grain texture patch via bulk array to eliminate per-pixel JNI overhead
        val noiseTileW = max(1, patchW.coerceAtMost(32))
        val noiseTileH = max(1, ringThickness.coerceAtLeast(1))
        val noiseBandPixels = IntArray(noiseTileW * noiseTileH)
        val sampleStartX = left.coerceIn(0, max(0, bmpW - noiseTileW))
        val sampleStartY = topRingY.coerceIn(0, max(0, bmpH - noiseTileH))
        sourceBitmap.getPixels(noiseBandPixels, 0, noiseTileW, sampleStartX, sampleStartY, noiseTileW, noiseTileH)

        val outPixels = IntArray(patchW * patchH)

        for (py in 0 until patchH) {
            val vWeight = py.toFloat() / max(1f, (patchH - 1).toFloat())
            val interVerR = (1f - vWeight) * ((topAvg shr 16) and 0xFF) + vWeight * ((btmAvg shr 16) and 0xFF)
            val interVerG = (1f - vWeight) * ((topAvg shr 8) and 0xFF) + vWeight * ((btmAvg shr 8) and 0xFF)
            val interVerB = (1f - vWeight) * (topAvg and 0xFF) + vWeight * (btmAvg and 0xFF)

            for (px in 0 until patchW) {
                val hWeight = px.toFloat() / max(1f, (patchW - 1).toFloat())
                val interHorR = (1f - hWeight) * ((leftAvg shr 16) and 0xFF) + hWeight * ((rightAvg shr 16) and 0xFF)
                val interHorG = (1f - hWeight) * ((leftAvg shr 8) and 0xFF) + hWeight * ((rightAvg shr 8) and 0xFF)
                val interHorB = (1f - hWeight) * (leftAvg and 0xFF) + hWeight * (rightAvg and 0xFF)

                // Two-Scale Wavelet Frequency Synthesis: Low-frequency illumination + high-frequency paper grain residual
                val noisePixel = noiseBandPixels[(py % noiseTileH) * noiseTileW + (px % noiseTileW)]
                val noiseL = ((noisePixel shr 16) and 0xFF) * 0.299f + ((noisePixel shr 8) and 0xFF) * 0.587f + (noisePixel and 0xFF) * 0.114f
                val deltaL = (noiseL - 250f) * 0.25f // Soft paper fiber variation

                val finalR = ((interVerR + interHorR) * 0.5f + deltaL).toInt().coerceIn(0, 255)
                val finalG = ((interVerG + interHorG) * 0.5f + deltaL).toInt().coerceIn(0, 255)
                val finalB = ((interVerB + interHorB) * 0.5f + deltaL).toInt().coerceIn(0, 255)

                outPixels[py * patchW + px] = AndroidColor.rgb(finalR, finalG, finalB)
            }
        }

        patchBitmap.setPixels(outPixels, 0, patchW, 0, 0, patchW, patchH)
        InpaintedPaperResult(patchBitmap, dominantColor, false)
    }

    private fun averageColor(colors: List<Int>, fallback: Int): Int {
        if (colors.isEmpty()) return fallback
        var r = 0L; var g = 0L; var b = 0L
        for (c in colors) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val count = colors.size
        return AndroidColor.rgb((r / count).toInt().coerceIn(0, 255), (g / count).toInt().coerceIn(0, 255), (b / count).toInt().coerceIn(0, 255))
    }
}

