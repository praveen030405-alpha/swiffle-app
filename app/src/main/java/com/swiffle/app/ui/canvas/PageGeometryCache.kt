package com.swiffle.app.ui.canvas

import android.util.Log
import com.swiffle.app.NativeEngine
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot

/**
 * Flat, high-performance primitive geometry cache for PDF text characters.
 * 
 * Replaces expensive runtime JSONObject parsing with contiguous FloatArray memory:
 * coords[i * 4 + 0] = x
 * coords[i * 4 + 1] = y
 * coords[i * 4 + 2] = width
 * coords[i * 4 + 3] = height
 */
class FlatPageGeometry(
    val pageNum: Int,
    val text: String,
    val coords: FloatArray,
    val count: Int
) {
    fun getChar(index: Int): String = if (index in 0 until count) text.substring(index, index + 1) else ""
    fun getX(index: Int): Float = coords[index * 4]
    fun getY(index: Int): Float = coords[index * 4 + 1]
    fun getW(index: Int): Float = coords[index * 4 + 2]
    fun getH(index: Int): Float = coords[index * 4 + 3]

    /**
     * Enterprise line-aware hit-test for fluid multi-line text selection dragging.
     * Snaps to line ends/starts when dragging into margins or across ragged-right lines.
     */
    /**
     * Enterprise line-aware hit-test for fluid multi-line text selection dragging.
     * Snaps to line ends/starts when dragging into margins or across ragged-right lines.
     * Prioritizes exact bounding boxes and closest glyph centers.
     */
    fun findClosestCharIndex(pdfX: Float, pdfY: Float, maxDistance: Float = 500f): Int {
        if (count == 0) return -1

        // Phase 1: Direct glyph bounding-box hit test (select glyph whose center is closest)
        var exactHitIdx = -1
        var minExactDistSq = Float.MAX_VALUE

        for (i in 0 until count) {
            val base = i * 4
            val x = coords[base]
            val y = coords[base + 1]
            val w = coords[base + 2]
            val h = coords[base + 3]
            val top = y - h
            val bottom = y
            // True bounding box check with minimal 1.5pt finger padding
            if (pdfX >= x - 1.5f && pdfX <= x + w + 1.5f && pdfY >= top - 2f && pdfY <= bottom + 2f) {
                val cx = x + w * 0.5f
                val cy = y - h * 0.5f
                val dx = pdfX - cx
                val dy = pdfY - cy
                val distSq = dx * dx + dy * dy
                if (distSq < minExactDistSq) {
                    minExactDistSq = distSq
                    exactHitIdx = i
                }
            }
        }
        if (exactHitIdx != -1) {
            return exactHitIdx
        }

        // Phase 2: Line-aware hit-test (enables smooth multi-line dragging up & down across lines)
        var bestLineDistY = Float.MAX_VALUE
        var bestLineIdx = -1

        for (i in 0 until count) {
            val base = i * 4
            val cy = coords[base + 1] - coords[base + 3] * 0.5f
            val dy = kotlin.math.abs(pdfY - cy)
            if (dy < bestLineDistY) {
                bestLineDistY = dy
                bestLineIdx = i
            }
        }

        if (bestLineIdx != -1) {
            val targetBase = bestLineIdx * 4
            val targetCy = coords[targetBase + 1] - coords[targetBase + 3] * 0.5f
            val lineH = coords[targetBase + 3].coerceAtLeast(8f)

            var closestOnLine = -1
            var minLineDistX = Float.MAX_VALUE
            var lineFirstIdx = -1
            var lineLastIdx = -1
            var lineMinX = Float.MAX_VALUE
            var lineMaxX = -Float.MAX_VALUE

            for (i in 0 until count) {
                val base = i * 4
                val cy = coords[base + 1] - coords[base + 3] * 0.5f
                if (kotlin.math.abs(cy - targetCy) <= lineH * 0.75f) {
                    val cx = coords[base] + coords[base + 2] * 0.5f
                    val x = coords[base]
                    val rightX = x + coords[base + 2]
                    if (x < lineMinX) {
                        lineMinX = x
                        lineFirstIdx = i
                    }
                    if (rightX > lineMaxX) {
                        lineMaxX = rightX
                        lineLastIdx = i
                    }
                    val dx = kotlin.math.abs(pdfX - cx)
                    if (dx < minLineDistX) {
                        minLineDistX = dx
                        closestOnLine = i
                    }
                }
            }

            // If dragged into ragged right margin, snap to the end of that line!
            if (pdfX >= lineMaxX && lineLastIdx != -1) {
                return lineLastIdx
            }
            // If dragged into left margin, snap to the start of that line!
            if (pdfX <= lineMinX && lineFirstIdx != -1) {
                return lineFirstIdx
            }
            if (closestOnLine != -1) {
                return closestOnLine
            }
        }

        // Phase 3: Global fallback with anisotropic distance metric (vertical distance weighted 2.5x)
        var closestIdx = -1
        var minScore = Float.MAX_VALUE

        for (i in 0 until count) {
            val base = i * 4
            val cx = coords[base] + coords[base + 2] * 0.5f
            val cy = coords[base + 1] - coords[base + 3] * 0.5f
            val dx = pdfX - cx
            val dy = (pdfY - cy) * 2.5f
            val score = dx * dx + dy * dy
            if (score < minScore) {
                minScore = score
                closestIdx = i
            }
        }
        return closestIdx
    }

    /**
     * Converts to legacy CharRect list for backwards compatibility where required.
     */
    fun toCharRectList(): List<CharRect> {
        val list = ArrayList<CharRect>(count)
        for (i in 0 until count) {
            val base = i * 4
            list.add(
                CharRect(
                    char = getChar(i),
                    x = coords[base],
                    y = coords[base + 1],
                    w = coords[base + 2],
                    h = coords[base + 3]
                )
            )
        }
        return list
    }
}

object PageGeometryCache {
    private val cache = ConcurrentHashMap<Int, FlatPageGeometry>()

    fun get(pageNum: Int): FlatPageGeometry? = cache[pageNum]

    /**
     * Loads and flattens page geometry into contiguous primitive FloatArray.
     * Thread-safe and parses JSON only ONCE per page.
     * Never permanently caches empty geometry when native pointer is 0L.
     */
    fun getOrLoad(pageNum: Int, nativePtr: Long): FlatPageGeometry {
        cache[pageNum]?.let { existing ->
            if (existing.count > 0) return existing
            // If cached version was empty but we now have a valid native pointer, re-extract!
            if (nativePtr == 0L) return existing
        }

        if (nativePtr == 0L) {
            // DO NOT store into cache so subsequent calls with valid pointer can load!
            return FlatPageGeometry(pageNum, "", FloatArray(0), 0)
        }

        val jsonStr = try {
            NativeEngine.extractPageGeometryFromPtr(nativePtr, pageNum)
        } catch (e: Exception) {
            Log.e("PageGeometryCache", "Error extracting geometry for page $pageNum", e)
            ""
        }

        if (jsonStr.isBlank() || jsonStr == "{}" || jsonStr == "[]") {
            val empty = FlatPageGeometry(pageNum, "", FloatArray(0), 0)
            cache[pageNum] = empty
            return empty
        }

        return try {
            val soaObj = JSONObject(jsonStr)
            val cArr = soaObj.getJSONArray("c")
            val xArr = soaObj.getJSONArray("x")
            val yArr = soaObj.getJSONArray("y")
            val wArr = soaObj.getJSONArray("w")
            val hArr = soaObj.getJSONArray("h")
            val len = cArr.length()

            val textBuilder = StringBuilder(len)
            val coords = FloatArray(len * 4)

            for (i in 0 until len) {
                textBuilder.append(cArr.getString(i))
                val base = i * 4
                coords[base + 0] = xArr.getDouble(i).toFloat()
                coords[base + 1] = yArr.getDouble(i).toFloat()
                coords[base + 2] = wArr.getDouble(i).toFloat()
                coords[base + 3] = hArr.getDouble(i).toFloat()
            }

            val flat = FlatPageGeometry(
                pageNum = pageNum,
                text = textBuilder.toString(),
                coords = coords,
                count = len
            )
            cache[pageNum] = flat
            flat
        } catch (e: Exception) {
            Log.e("PageGeometryCache", "Error parsing geometry JSON for page $pageNum", e)
            val empty = FlatPageGeometry(pageNum, "", FloatArray(0), 0)
            cache[pageNum] = empty
            empty
        }
    }

    fun clear() {
        cache.clear()
    }
}
