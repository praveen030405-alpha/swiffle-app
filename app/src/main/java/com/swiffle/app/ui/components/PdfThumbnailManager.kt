package com.swiffle.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiffle.app.theme.GoogleSans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfThumbnailManager {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun clearMemoryCache() {
        try {
            memoryCache.evictAll()
        } catch (_: Throwable) {}
    }

    fun trimToHalf() {
        try {
            memoryCache.trimToSize(memoryCache.size() / 2)
        } catch (_: Throwable) {}
    }

    suspend fun getOrRenderThumbnail(
        context: Context,
        uri: Uri,
        targetWidth: Int = 180
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "thumb_${uri.hashCode().toUInt()}"

        // 1. Check in-memory LRU Cache
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // 2. Check persistent disk cache
        val thumbDir = File(context.cacheDir, "pdf_thumbnails")
        if (!thumbDir.exists()) thumbDir.mkdirs()
        val thumbFile = File(thumbDir, "$cacheKey.jpg")

        if (thumbFile.exists() && thumbFile.length() > 0) {
            try {
                val diskBmp = BitmapFactory.decodeFile(thumbFile.absolutePath)
                if (diskBmp != null) {
                    memoryCache.put(cacheKey, diskBmp)
                    return@withContext diskBmp
                }
            } catch (_: Throwable) {}
        }

        // 3. Render Page 0 from PDF
        try {
            var pfd: ParcelFileDescriptor? = null
            val vaultDir = File(context.filesDir, "recent_vault")
            val vaultFile = File(vaultDir, "vault_${uri.hashCode().toUInt()}.pdf")
            if (vaultFile.exists() && vaultFile.length() > 0) {
                pfd = ParcelFileDescriptor.open(vaultFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            if (pfd == null) {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
            }

            if (pfd == null) return@withContext null

            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) {
                renderer.close()
                pfd.close()
                return@withContext null
            }

            val page = renderer.openPage(0)
            val pWidth = page.width
            val pHeight = page.height
            val aspectRatio = if (pWidth > 0) pHeight.toFloat() / pWidth.toFloat() else 1.414f
            val targetHeight = (targetWidth * aspectRatio).toInt().coerceIn(60, 400)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            renderer.close()
            pfd.close()

            try {
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
            } catch (_: Throwable) {}

            memoryCache.put(cacheKey, bitmap)
            return@withContext bitmap
        } catch (_: Throwable) {
            return@withContext null
        }
    }
}

/**
 * High-performance Windows 11 Fluent Thumbnail View
 * Automatically renders and displays authentic cover page of any PDF document!
 */
@Composable
fun PdfThumbnailImage(
    uri: Uri,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = PdfThumbnailManager.getOrRenderThumbnail(context, uri)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Document Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fluent Encrypted / Protected Document Badge: Clean card with small Swiffle Icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0078D4).copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.swiffle.app.R.drawable.ic_swiffle_logo),
                    contentDescription = "Encrypted Document",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

