package com.swiffle.app.ui.canvas

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.swiffle.app.evidence.EvidenceBounds
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PdfReaderHighlight(val page: Int, val start: Int, val end: Int, val regions: List<EvidenceBounds>)

internal class PdfReaderHighlights(context: Context) {
    private val preferences = context.getSharedPreferences("SwiffleReaderHighlights", Context.MODE_PRIVATE)

    fun hasHighlights(uri: Uri) = preferences.contains(uri.toString())

    fun read(uri: Uri): List<PdfReaderHighlight> =
        preferences.getString(uri.toString(), null)?.let {
            runCatching { Json.decodeFromString<List<PdfReaderHighlight>>(it) }.getOrNull()
        }.orEmpty()

    fun write(uri: Uri, highlights: List<PdfReaderHighlight>) {
        val name = uri.toString()
        preferences.edit().apply {
            if (highlights.isEmpty()) remove(name) else putString(name, Json.encodeToString(highlights))
        }.apply()
    }
}

internal fun DrawScope.drawReaderHighlights(
    highlights: List<PdfReaderHighlight>,
    page: Int,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    highlights.asSequence().filter { it.page == page }.forEach { highlight ->
        highlight.regions.forEach { region ->
            drawRect(
                color = Color(0x66FFCB35),
                topLeft = Offset(left + region.left * width, top + region.top * height),
                size = Size(region.width * width, region.height * height)
            )
        }
    }
}
