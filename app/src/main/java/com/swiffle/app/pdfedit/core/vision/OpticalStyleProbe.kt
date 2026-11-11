package com.swiffle.app.pdfedit.core.vision

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class OpticalStyleDna(
    val estimatedWeight: Float, // 100f..900f
    val subPixelDilation: Float, // -0.5f..+0.5f
    val trueInkRgb: Int,
    val scannerBlurRadiusPx: Float, // 0.1f..2.5f
    val paperColorRgb: Int,
    val isCleanDigitalVector: Boolean
)

/**
 * Optical Style Probe:
 * Analyzes rasterized text patches using computer-vision algorithms:
 * - Otsu's Adaptive Binarization
 * - Stroke Width Transform (SWT) Approximation
 * - Core Ink Chromaticity Extraction (ignoring edge anti-aliasing)
 * - Point Spread Function (PSF) edge softness estimation
 */
object OpticalStyleProbe {

    suspend fun probeStyle(
        pageBitmap: Bitmap?,
        targetRect: Rect
    ): OpticalStyleDna = withContext(Dispatchers.Default) {
        if (pageBitmap == null || pageBitmap.isRecycled || targetRect.width() <= 2 || targetRect.height() <= 2) {
            return@withContext OpticalStyleDna(
                estimatedWeight = 400f,
                subPixelDilation = 0f,
                trueInkRgb = AndroidColor.rgb(30, 30, 30),
                scannerBlurRadiusPx = 0.5f,
                paperColorRgb = AndroidColor.WHITE,
                isCleanDigitalVector = true
            )
        }

        val bmpW = pageBitmap.width
        val bmpH = pageBitmap.height

        val left = max(0, targetRect.left)
        val top = max(0, targetRect.top)
        val right = min(bmpW, targetRect.right)
        val bottom = min(bmpH, targetRect.bottom)

        val w = right - left
        val h = bottom - top
        if (w <= 2 || h <= 2) {
            return@withContext OpticalStyleDna(
                estimatedWeight = 400f,
                subPixelDilation = 0f,
                trueInkRgb = AndroidColor.rgb(30, 30, 30),
                scannerBlurRadiusPx = 0.5f,
                paperColorRgb = AndroidColor.WHITE,
                isCleanDigitalVector = true
            )
        }

        val pixels = IntArray(w * h)
        pageBitmap.getPixels(pixels, 0, w, left, top, w, h)

        // Step 1: Compute Grayscale Luminance and Histogram
        val lum = FloatArray(w * h)
        val histogram = IntArray(256)
        var totalLumSum = 0L

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            lum[i] = l.toFloat()
            histogram[l]++
            totalLumSum += l
        }

        // Step 2: Otsu's Adaptive Thresholding
        val totalPixels = w * h
        var sumB = 0L
        var wB = 0L
        var maxVariance = 0.0
        var otsuThreshold = 128

        for (t in 0 until 256) {
            wB += histogram[t]
            if (wB == 0L) continue
            val wF = totalPixels - wB
            if (wF == 0L) break

            sumB += (t * histogram[t])
            val mB = sumB.toDouble() / wB
            val mF = (totalLumSum - sumB).toDouble() / wF
            val varBetween = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)

            if (varBetween > maxVariance) {
                maxVariance = varBetween
                otsuThreshold = t
            }
        }

        // Step 3: Ink Mask and Foreground / Background Segmentation
        val isInk = BooleanArray(w * h)
        var fgArea = 0
        var bgRSum = 0L
        var bgGSum = 0L
        var bgBSum = 0L
        var bgCount = 0

        for (i in pixels.indices) {
            if (lum[i] <= otsuThreshold) {
                isInk[i] = true
                fgArea++
            } else {
                val c = pixels[i]
                bgRSum += (c shr 16) and 0xFF
                bgGSum += (c shr 8) and 0xFF
                bgBSum += c and 0xFF
                bgCount++
            }
        }

        val paperColor = if (bgCount > 0) {
            AndroidColor.rgb(
                (bgRSum / bgCount).toInt().coerceIn(0, 255),
                (bgGSum / bgCount).toInt().coerceIn(0, 255),
                (bgBSum / bgCount).toInt().coerceIn(0, 255)
            )
        } else {
            AndroidColor.WHITE
        }

        // Step 4: Stroke Width Transform (SWT) Approximation
        var edgePerimeter = 0
        val coreInkPixels = mutableListOf<Int>()
        var gradientSum = 0f
        var gradientSamples = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (isInk[idx]) {
                    // Check 4-connected neighbors
                    val up = isInk[(y - 1) * w + x]
                    val down = isInk[(y + 1) * w + x]
                    val leftN = isInk[y * w + (x - 1)]
                    val rightN = isInk[y * w + (x + 1)]

                    if (!up || !down || !leftN || !rightN) {
                        edgePerimeter++
                        // Measure edge luminance gradient for scanner PSF
                        val maxNeighborLum = max(
                            max(lum[(y - 1) * w + x], lum[(y + 1) * w + x]),
                            max(lum[y * w + (x - 1)], lum[y * w + (x + 1)])
                        )
                        gradientSum += abs(maxNeighborLum - lum[idx])
                        gradientSamples++
                    } else {
                        // Deep core ink pixel (uncontaminated by anti-aliasing)
                        coreInkPixels.add(pixels[idx])
                    }
                }
            }
        }

        // Step 5: True Ink Chromaticity from core pixels
        val trueInkColor = if (coreInkPixels.size >= 4) {
            val sortedR = coreInkPixels.map { (it shr 16) and 0xFF }.sorted()
            val sortedG = coreInkPixels.map { (it shr 8) and 0xFF }.sorted()
            val sortedB = coreInkPixels.map { it and 0xFF }.sorted()
            val mid = coreInkPixels.size / 2
            AndroidColor.rgb(sortedR[mid], sortedG[mid], sortedB[mid])
        } else {
            AndroidColor.rgb(30, 30, 30)
        }

        // Step 6: Stroke Width & Continuous Weight Estimation
        val strokeWidthPx = if (edgePerimeter > 0) {
            (2.0f * fgArea) / edgePerimeter.toFloat()
        } else {
            2.0f
        }

        val glyphHeightPx = max(4, h).toFloat()
        val strokeRatio = (strokeWidthPx / glyphHeightPx).coerceIn(0.03f, 0.35f)

        // Continuous mapping to Font Weight (100f..900f)
        val continuousWeight = 100f + ((strokeRatio - 0.04f) / (0.24f - 0.04f)) * 800f
        val clampedWeight = continuousWeight.coerceIn(100f, 900f)

        // Sub-pixel dilation (-0.5f to +0.5f)
        val dilation = ((strokeRatio - 0.10f) * 2.5f).coerceIn(-0.5f, 0.5f)

        // Scanner PSF blur estimate
        val avgGradient = if (gradientSamples > 0) gradientSum / gradientSamples else 80f
        // High gradient = razor-sharp vector (0.2px). Low gradient = soft scanned document (1.8px).
        val blurRadius = (2.2f - (avgGradient / 128f) * 1.5f).coerceIn(0.1f, 2.5f)

        val isDigital = avgGradient > 90f && (AndroidColor.red(paperColor) > 240 && AndroidColor.green(paperColor) > 240)

        OpticalStyleDna(
            estimatedWeight = clampedWeight,
            subPixelDilation = dilation,
            trueInkRgb = trueInkColor,
            scannerBlurRadiusPx = blurRadius,
            paperColorRgb = paperColor,
            isCleanDigitalVector = isDigital
        )
    }
}

