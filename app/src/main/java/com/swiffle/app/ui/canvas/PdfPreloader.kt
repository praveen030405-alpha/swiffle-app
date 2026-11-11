package com.swiffle.app.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * High-performance enterprise preloader for Origami PDF.
 * Preloads and renders initial pages in the background before or during PDF launch
 * so that when the document opens, the first pages are already rendered with 0 blank frames.
 * Relies on modern Android NativeAllocationRegistry and ART GC for leak-free, zero-recycle stability.
 */
object PdfPreloader {
    data class PreloadedPdf(
        val uri: Uri,
        val pageCount: Int,
        val dimensions: List<Pair<Float, Float>>,
        val initialRatio: Float,
        val initialPWidth: Float,
        val initialPHeight: Float,
        val bitmaps: Map<Int, Bitmap>,
        val crops: Map<Int, PageCrop> = emptyMap()
    )

    private val preloadedCache = ConcurrentHashMap<Uri, PreloadedPdf>()
    private val activeJobs = ConcurrentHashMap<Uri, Job>()
    private val preloadScope = CoroutineScope(Dispatchers.IO)

    fun get(uri: Uri): PreloadedPdf? {
        val cached = preloadedCache[uri] ?: return null
        // Ensure no bitmap in cache is recycled; if any is recycled, invalidate immediately
        if (cached.bitmaps.values.any { it.isRecycled }) {
            preloadedCache.remove(uri)
            return null
        }
        return cached
    }

    fun remove(uri: Uri) {
        activeJobs.remove(uri)?.cancel()
        preloadedCache.remove(uri)
    }

    fun put(uri: Uri, data: PreloadedPdf) {
        // Never cache already-recycled bitmaps
        if (data.bitmaps.values.any { it.isRecycled }) return
        // Evict older caches cleanly without calling destructive .recycle()
        preloadedCache.keys.filter { it != uri }.forEach { oldUri ->
            preloadedCache.remove(oldUri)
        }
        preloadedCache[uri] = data
    }

    fun clearAllCache() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        preloadedCache.clear()
    }

    fun preload(context: Context, uri: Uri, title: String = "", maxPages: Int = 2) {
        val existing = preloadedCache[uri]
        if (existing != null && existing.bitmaps.values.none { it.isRecycled }) {
            return
        }
        if (activeJobs[uri]?.isActive == true) return

        // Clear previous preloads to strictly conserve RAM without manual recycle
        activeJobs.keys.filter { it != uri }.forEach { oldUri ->
            activeJobs.remove(oldUri)?.cancel()
        }
        preloadedCache.keys.filter { it != uri }.forEach { oldUri ->
            preloadedCache.remove(oldUri)
        }

        val job = preloadScope.launch {
            try {
                val opened = openPdfDescriptorSafely(context, uri, title) ?: return@launch
                val (fd, _) = opened
                val renderer = try {
                    PdfRenderer(fd)
                } catch (_: Throwable) {
                    fd.close()
                    return@launch
                }

                val pCount = renderer.pageCount
                if (pCount <= 0) {
                    renderer.close()
                    fd.close()
                    return@launch
                }

                val dimsList = ArrayList<Pair<Float, Float>>(pCount)
                var initWidth = 0f
                var initHeight = 0f
                var initRatio = 1f / 1.414f

                val scanLimit = minOf(pCount, 40)
                for (i in 0 until scanLimit) {
                    try {
                        val p = renderer.openPage(i)
                        val pw = p.width.toFloat()
                        val ph = p.height.toFloat()
                        dimsList.add(Pair(pw, ph))
                        if (i == 0) {
                            initWidth = pw
                            initHeight = ph
                            initRatio = if (ph > 0) pw / ph else initRatio
                        }
                        p.close()
                    } catch (_: Throwable) {
                        dimsList.add(Pair(initWidth, initHeight))
                    }
                }
                for (i in scanLimit until pCount) {
                    dimsList.add(Pair(initWidth, initHeight))
                }

                // Preload at most 2 pages to prevent heap pressure
                val preloadCount = minOf(maxPages, pCount).coerceAtMost(2)
                val bitmaps = ConcurrentHashMap<Int, Bitmap>()
                val crops = ConcurrentHashMap<Int, PageCrop>()

                // Eagerly pre-render up to 2 pages in background for instant 0ms reveal
                for (i in 0 until preloadCount) {
                    try {
                        val page = renderer.openPage(i)
                        val pw = page.width.toFloat()
                        val ph = page.height.toFloat()
                        val ratio = if (ph > 0) pw / ph else initRatio

                        val baseTargetWidth = 2000.coerceAtMost(max(1400, (pw * 2.2f).toInt()))
                        val rawTargetHeight = (baseTargetWidth / ratio).toInt().coerceAtLeast(1)
                        val maxGpuDim = 4096
                        val (targetWidth, targetHeight) = if (rawTargetHeight > maxGpuDim) {
                            val downscale = maxGpuDim.toFloat() / rawTargetHeight
                            Pair((baseTargetWidth * downscale).toInt().coerceAtLeast(1080), maxGpuDim)
                        } else {
                            Pair(baseTargetWidth, rawTargetHeight)
                        }

                        val bmp = try {
                            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        } catch (_: Throwable) { null }

                        if (bmp != null) {
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            val m = android.graphics.Matrix()
                            m.postScale(bmp.width.toFloat() / pw, bmp.height.toFloat() / ph)
                            page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps[i] = bmp
                            val detectedCrop = detectPageCrop(bmp)
                            crops[i] = detectedCrop
                        }
                        page.close()
                    } catch (_: Throwable) {}
                }

                renderer.close()
                fd.close()

                if (bitmaps.isNotEmpty() && bitmaps.values.none { it.isRecycled }) {
                    preloadedCache[uri] = PreloadedPdf(
                        uri = uri,
                        pageCount = pCount,
                        dimensions = dimsList,
                        initialRatio = initRatio,
                        initialPWidth = initWidth,
                        initialPHeight = initHeight,
                        bitmaps = bitmaps,
                        crops = crops
                    )
                }
            } catch (_: Throwable) {} finally {
                activeJobs.remove(uri)
            }
        }
        activeJobs[uri] = job
    }
}
