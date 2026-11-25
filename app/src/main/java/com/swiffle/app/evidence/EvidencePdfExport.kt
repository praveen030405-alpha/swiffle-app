package com.swiffle.app.evidence

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.min

internal suspend fun exportEvidencePack(context: Context, store: EvidenceStore, pack: EvidencePack, target: Uri) =
    withContext(Dispatchers.IO) {
        val pending = File.createTempFile("evidence-pack-", ".pdf", context.cacheDir)
        try {
            val document = PdfDocument()
            try {
                val writer = EvidencePdfWriter(document)
                try {
                    writer.paragraph("SWIFFLE  /  EVIDENCE PACK", 10f, true, MAROON)
                    writer.paragraph(pack.title, 25f, true)
                    writer.paragraph("${pack.items.size} excerpts · ${pack.items.count { it.reviewed }} reviewed", 11f)
                    pack.items.groupBy { it.group }.forEach { (group, items) ->
                        writer.space(12f)
                        writer.paragraph(group.ifBlank { "Ungrouped" }, 17f, true, MAROON, keepWithNext = 42f)
                        items.forEach { item ->
                            writer.space(8f)
                            writer.paragraph("${item.source.filename}  ·  Page ${item.page}", 12f, true, keepWithNext = 28f)
                            writer.paragraph(if (item.reviewed) "Reviewed" else "Unresolved", 10f, color = Color.DKGRAY)
                            if (item.excerpt != null) {
                                writer.paragraph(item.excerpt, 12f)
                            } else {
                                val image = BitmapFactory.decodeFile(store.imageFile(item.id).path)
                                    ?: throw IOException("A page clip is missing. Remove or recapture it before exporting.")
                                try {
                                    writer.image(image)
                                } finally {
                                    image.recycle()
                                }
                            }
                            if (item.note.isNotBlank()) writer.paragraph("Note  ${item.note}", 11f)
                        }
                    }
                } finally {
                    writer.finish()
                }
                pending.outputStream().use { document.writeTo(it) }
            } finally {
                document.close()
            }
            val output = context.contentResolver.openOutputStream(target, "wt")
                ?: throw IOException("Unable to write to the selected location.")
            output.use { out -> pending.inputStream().use { it.copyTo(out) } }
        } finally {
            pending.delete()
        }
    }

private val MAROON = Color.rgb(128, 0, 0)

private class EvidencePdfWriter(private val document: PdfDocument) {
    private val width = 595
    private val height = 842
    private val margin = 46
    private val bottom = height - 58f
    private var number = 1
    private var page = newPage()
    private var y = margin.toFloat()

    private fun newPage() = document.startPage(PdfDocument.PageInfo.Builder(width, height, number).create())

    private fun footer() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.DKGRAY }
        page.canvas.drawText("Swiffle · Evidence Pack", margin.toFloat(), height - 25f, paint)
        paint.textAlign = Paint.Align.RIGHT
        page.canvas.drawText(number.toString(), width - margin.toFloat(), height - 25f, paint)
        document.finishPage(page)
    }

    private fun advance() {
        footer()
        number++
        page = newPage()
        y = margin.toFloat()
    }

    fun space(points: Float) {
        y += points
    }

    fun paragraph(
        text: String,
        size: Float,
        bold: Boolean = false,
        color: Int = Color.BLACK,
        keepWithNext: Float = 0f
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width - 2 * margin)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(2f, 1f)
            .build()
        if (keepWithNext > 0f && y + layout.height + keepWithNext > bottom && y > margin) advance()
        var first = 0
        while (first < layout.lineCount) {
            val firstTop = layout.getLineTop(first)
            var end = first
            while (end < layout.lineCount && y + layout.getLineTop(end + 1) - firstTop <= bottom) end++
            if (end == first) {
                advance()
                continue
            }
            val drawnHeight = (layout.getLineTop(end) - firstTop).toFloat()
            page.canvas.save()
            page.canvas.clipRect(margin.toFloat(), y, width - margin.toFloat(), y + drawnHeight)
            page.canvas.translate(margin.toFloat(), y - firstTop)
            layout.draw(page.canvas)
            page.canvas.restore()
            y += drawnHeight
            first = end
            if (first < layout.lineCount) advance()
        }
        y += 5f
    }

    fun image(bitmap: android.graphics.Bitmap) {
        val scale = min((width - 2f * margin) / bitmap.width, 400f / bitmap.height)
        val drawHeight = bitmap.height * scale
        if (y + drawHeight > bottom) advance()
        val target = RectF(margin.toFloat(), y, margin + bitmap.width * scale, y + drawHeight)
        page.canvas.drawBitmap(bitmap, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        y += drawHeight + 6f
    }

    fun finish() = footer()
}
