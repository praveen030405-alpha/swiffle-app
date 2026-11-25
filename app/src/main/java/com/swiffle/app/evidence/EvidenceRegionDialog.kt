package com.swiffle.app.evidence

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swiffle.app.theme.Maroon600
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EvidenceRegionDialog(
    uri: Uri,
    page: Int,
    saving: Boolean,
    onDismiss: () -> Unit,
    onClip: (EvidenceCapture) -> Unit
) {
    val context = LocalContext.current
    val previewHeight = (LocalConfiguration.current.screenHeightDp * 0.52f).coerceAtMost(440f).dp
    val rendered by produceState<Result<Bitmap>?>(null, uri, page) {
        value = evidenceResult {
            withContext(Dispatchers.IO) { renderEvidencePage(context, uri, page, 1300).bitmap }
        }
    }
    var bounds by remember(uri, page) { mutableStateOf<EvidenceBounds?>(null) }
    var isSelecting by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Clip page $page", style = MaterialTheme.typography.titleLarge)
                Text("Drag over the part you want to keep, or capture the whole page.")
                val image = rendered?.getOrNull()
                when {
                    image != null -> BoxWithConstraints(
                        Modifier.fillMaxWidth().heightIn(max = previewHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        val ratio = image.width.toFloat() / image.height
                        val width = minOf(maxWidth, maxHeight * ratio)
                        val height = width / ratio
                        EvidencePageImage(
                            image, listOfNotNull(bounds),
                            Modifier.size(width, height).pointerInput(image, saving) {
                                if (saving) return@pointerInput
                                var start = Offset.Zero
                                var end = Offset.Zero
                                detectDragGestures(
                                    onDragStart = {
                                        isSelecting = true
                                        start = it
                                        end = it
                                        bounds = null
                                    },
                                    onDragEnd = {
                                        isSelecting = false
                                        if (bounds?.let { it.width < 0.005f || it.height < 0.005f } == true) bounds = null
                                    },
                                    onDragCancel = { isSelecting = false; bounds = null }
                                ) { change, amount ->
                                    change.consume()
                                    end += amount
                                    bounds = EvidenceBounds.between(
                                        start.x / size.width,
                                        start.y / size.height,
                                        end.x / size.width,
                                        end.y / size.height
                                    )
                                }
                            }
                        )
                    }
                    rendered?.isFailure == true -> Text(
                        rendered?.exceptionOrNull()?.message ?: "Unable to display the PDF page.",
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> CircularProgressIndicator(Modifier.size(28.dp))
                }
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
                    Button(
                        enabled = image != null && !saving && !isSelecting,
                        onClick = { onClip(EvidenceCapture(page, listOf(bounds ?: EvidenceBounds.FullPage))) }
                    ) {
                        Text(if (saving) "Saving…" else if (bounds == null) "Add whole page" else "Add clip")
                    }
                }
            }
        }
    }
}

@Composable
internal fun EvidencePageImage(bitmap: Bitmap, regions: List<EvidenceBounds>, modifier: Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Box(modifier) {
        Image(image, contentDescription = "PDF page", modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        if (regions.isNotEmpty()) {
            Canvas(Modifier.matchParentSize()) {
                regions.forEach { bounds ->
                    val anchor = Offset(size.width * bounds.left, size.height * bounds.top)
                    val extent = Size(size.width * bounds.width, size.height * bounds.height)
                    drawRect(Maroon600.copy(alpha = 0.18f), topLeft = anchor, size = extent)
                    drawRect(Maroon600, topLeft = anchor, size = extent, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
