package com.swiffle.app.pdfedit.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swiffle.app.pdfedit.pipeline.PdfEditTool
import com.swiffle.app.pdfedit.pipeline.PdfEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Top-level Chameleon PDF Edit Screen.
 * Integrates high-res page rendering, AST DOM extraction, and the 120Hz Dual-Layer Viewport.
 */
@Composable
fun SwifflePdfEditScreen(
    pdfUri: Uri,
    onBack: () -> Unit,
    onSaved: (File) -> Unit = {},
    viewModel: PdfEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    // Initialize PDF Renderer & load document
    DisposableEffect(pdfUri) {
        try {
            val cacheDocName = "swiffle_doc_${pdfUri.hashCode().toUInt()}.pdf"
            val cachedFile = File(context.cacheDir, cacheDocName)
            val vaultFile = File(File(context.filesDir, "recent_vault"), "vault_${pdfUri.hashCode().toUInt()}.pdf")
            val diskFile = if (vaultFile.exists() && vaultFile.length() > 0) vaultFile
                else if (cachedFile.exists() && cachedFile.length() > 0) cachedFile
                else if (pdfUri.scheme == "file") File(pdfUri.path ?: "")
                else null

            val fileDescriptor = if (diskFile != null && diskFile.exists() && diskFile.length() > 0) {
                ParcelFileDescriptor.open(diskFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(pdfUri, "r")
            }

            if (fileDescriptor != null) {
                pfd = fileDescriptor
                val renderer = PdfRenderer(fileDescriptor)
                pdfRenderer = renderer
                totalPages = renderer.pageCount
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        viewModel.loadDocument(context, pdfUri, currentPageIndex)

        onDispose {
            try {
                pdfRenderer?.close()
                pfd?.close()
            } catch (_: Exception) {}
        }
    }

    // Render page bitmap at 2.5x high resolution
    LaunchedEffect(currentPageIndex, pfd) {
        val descriptor = pfd ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val renderer = pdfRenderer ?: PdfRenderer(descriptor).also { pdfRenderer = it }
                totalPages = renderer.pageCount
                val page = renderer.openPage(currentPageIndex)
                val renderScale = 2.5f
                val w = (page.width * renderScale).toInt().coerceAtLeast(1)
                val h = (page.height * renderScale).toInt().coerceAtLeast(1)

                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                withContext(Dispatchers.Main) {
                    pageBitmap = bmp
                    viewModel.runOcrIfNeeded(bmp)
                }
            } catch (e: Exception) {
                android.util.Log.e("PdfEdit", "Failed to render page", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .statusBarsPadding()
    ) {
        // Futuristic Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit PDF",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Professional PDF Document Editor",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Page Navigation Stepper
                if (totalPages > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (currentPageIndex > 0) {
                                    currentPageIndex--
                                    viewModel.loadDocument(context, pdfUri, currentPageIndex)
                                }
                            },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Page")
                        }

                        Text(
                            text = "${currentPageIndex + 1} / $totalPages",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                if (currentPageIndex < totalPages - 1) {
                                    currentPageIndex++
                                    viewModel.loadDocument(context, pdfUri, currentPageIndex)
                                }
                            },
                            enabled = currentPageIndex < totalPages - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                        }
                    }
                }

                // Prominent Top Bar Save Action
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0078D4),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable(enabled = !uiState.isSaving) {
                            val outDir = context.getExternalFilesDir(null) ?: context.filesDir
                            val outFile = File(outDir, "Edited_${System.currentTimeMillis()}.pdf")
                            viewModel.commitAndSavePdf(
                                context = context,
                                destinationFile = outFile,
                                onSuccess = { savedFile ->
                                    Toast.makeText(
                                        context,
                                        "Successfully saved to: ${savedFile.name}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onSaved(savedFile)
                                },
                                onError = { error ->
                                    Toast.makeText(context, "Error saving: $error", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save PDF",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // Main Editor Canvas Viewport (Clipped to prevent zoom bleed over header)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            SwifflePdfEditCanvas(
                pageBitmap = pageBitmap,
                state = uiState,
                viewModel = viewModel,
                onSaveRequested = {
                    val outDir = context.getExternalFilesDir(null) ?: context.filesDir
                    val outFile = File(outDir, "Edited_${System.currentTimeMillis()}.pdf")
                    viewModel.commitAndSavePdf(
                        context = context,
                        destinationFile = outFile,
                        onSuccess = { savedFile ->
                            Toast.makeText(
                                context,
                                "Successfully saved to: ${savedFile.name}",
                                Toast.LENGTH_LONG
                            ).show()
                            onSaved(savedFile)
                        },
                        onError = { error ->
                            Toast.makeText(context, "Error saving: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )

            // Gemini / Samsung Galaxy AI Full-Screen Light Overlay Animation during OCR
            AiOcrScanningOverlay(
                isScanning = uiState.isOcrScanning,
                modifier = Modifier.fillMaxSize()
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0078D4))
                }
            }
        }

        // Super Advanced Edit Toolkit Toolbar Dock
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. AI OCR Action (Wand / Sparkle with iridescent gradient)
                Surface(
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                            } else {
                                @Suppress("DEPRECATION")
                                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                                vib?.vibrate(40L)
                            }
                        } catch (_: Exception) {}

                        viewModel.performAiOcr(pageBitmap) { count, errorMsg ->
                            if (count > 0) {
                                Toast.makeText(context, "AI OCR Complete! $count text lines detected.", Toast.LENGTH_SHORT).show()
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                                        vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                                    }
                                } catch (_: Exception) {}
                            } else {
                                val msg = if (errorMsg != null) "OCR Notice: $errorMsg" else "No text detected on this page."
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !uiState.isOcrScanning && pageBitmap != null,
                    shape = RoundedCornerShape(10.dp),
                    color = if (uiState.isOcrScanning) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF), Color(0xFF9D50BB)))
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Perform AI OCR",
                            tint = if (uiState.isOcrScanning) Color.White else Color(0xFF0078D4),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isOcrScanning) "Scanning..." else "Perform AI OCR",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (uiState.isOcrScanning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Edit Text Tool
                AdvancedToolChip(
                    icon = Icons.Default.Edit,
                    label = "Edit Text",
                    isSelected = uiState.activeTool == PdfEditTool.TEXT_EDIT,
                    onClick = {
                        viewModel.setActiveTool(PdfEditTool.TEXT_EDIT)
                        Toast.makeText(context, "Tap or Long-Press any text line to edit", Toast.LENGTH_SHORT).show()
                    }
                )

                // 3. Add Text Tool
                AdvancedToolChip(
                    icon = Icons.Default.FormatColorText,
                    label = "Add Text",
                    isSelected = uiState.activeTool == PdfEditTool.ADD_TEXT,
                    onClick = {
                        viewModel.setActiveTool(PdfEditTool.ADD_TEXT)
                        Toast.makeText(context, "Tap anywhere on page to place new text", Toast.LENGTH_SHORT).show()
                    }
                )

                // 4. Inpaint Eraser
                AdvancedToolChip(
                    icon = Icons.Default.InvertColors,
                    label = "Inpaint Eraser",
                    isSelected = uiState.activeTool == PdfEditTool.INPAINT_ERASER,
                    onClick = {
                        viewModel.setActiveTool(PdfEditTool.INPAINT_ERASER)
                        Toast.makeText(context, "Wavelet paper background inpainter active", Toast.LENGTH_SHORT).show()
                    }
                )

                // 5. Highlight
                AdvancedToolChip(
                    icon = Icons.Default.Highlight,
                    label = "Highlight",
                    isSelected = uiState.activeTool == PdfEditTool.HIGHLIGHT,
                    onClick = {
                        viewModel.setActiveTool(PdfEditTool.HIGHLIGHT)
                    }
                )

                // 6. Fit Screen
                AdvancedToolChip(
                    icon = Icons.Default.CropFree,
                    label = "Fit Screen",
                    isSelected = false,
                    onClick = {
                        viewModel.setActiveTool(PdfEditTool.FIT_SCREEN)
                        Toast.makeText(context, "Double-tap page to snap fit to screen", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun AdvancedToolChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
