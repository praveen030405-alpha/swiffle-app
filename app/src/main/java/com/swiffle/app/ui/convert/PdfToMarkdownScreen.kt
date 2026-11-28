package com.swiffle.app.ui.convert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.swiffle.app.conversion.MarkdownPreset
import com.swiffle.app.conversion.PdfDocumentInspectInfo
import com.swiffle.app.conversion.PdfToMarkdownConfig
import com.swiffle.app.conversion.PdfToMarkdownEngine
import com.swiffle.app.conversion.PdfToMarkdownProgress
import com.swiffle.app.conversion.PdfToMarkdownResult
import com.swiffle.app.theme.ClaudeSans
import com.swiffle.app.theme.ClaudeSerif
import com.swiffle.app.theme.ThemeManager
import com.swiffle.app.ui.canvas.RichMarkdownBody
import com.swiffle.app.ui.components.DarkVoidAmbientLightPulse
import com.swiffle.app.ui.home.HomeThemeTokens
import com.swiffle.app.ui.home.rememberHomeTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfToMarkdownScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isDark = ThemeManager.isDarkThemeActive()
    val tokens = rememberHomeTokens(isDark = isDark)

    // Pipeline state
    var inspectedDoc by remember { mutableStateOf<PdfDocumentInspectInfo?>(null) }
    var isInspecting by remember { mutableStateOf(false) }
    var inspectError by remember { mutableStateOf<String?>(null) }

    var config by remember { mutableStateOf(PdfToMarkdownConfig.forPreset(MarkdownPreset.ACADEMIC_BOOK)) }
    var showAdvancedOptions by remember { mutableStateOf(false) }

    var isConverting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<PdfToMarkdownProgress?>(null) }
    var conversionJob by remember { mutableStateOf<Job?>(null) }
    var conversionError by remember { mutableStateOf<String?>(null) }

    var result by remember { mutableStateOf<PdfToMarkdownResult?>(null) }
    var savedDownloadPath by remember { mutableStateOf<String?>(null) }
    var isSavingDownload by remember { mutableStateOf(false) }

    // 0 = Rendered Markdown, 1 = Raw .md Source
    var previewTab by remember { mutableIntStateOf(0) }
    var showTocOutline by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            conversionJob?.cancel()
        }
    }

    BackHandler(enabled = true) {
        if (isConverting) {
            conversionJob?.cancel()
            isConverting = false
        } else {
            onBack()
        }
    }

    // Zero-permission PDF Document Picker (SAF OpenDocument)
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            inspectError = null
            conversionError = null
            result = null
            savedDownloadPath = null
            isInspecting = true
            scope.launch {
                val res = PdfToMarkdownEngine.inspectPdf(context, uri)
                isInspecting = false
                res.fold(
                    onSuccess = { info ->
                        inspectedDoc = info
                        config = config.copy(pageRangeExpr = "")
                    },
                    onFailure = { err ->
                        inspectError = err.message ?: "Unable to open this PDF."
                    }
                )
            }
        }
    }

    // Zero-permission SAF "Save As…" exporter for arbitrary .md location
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { targetUri: Uri? ->
        val activeResult = result ?: return@rememberLauncherForActivityResult
        if (targetUri != null) {
            scope.launch {
                val writeRes = PdfToMarkdownEngine.writeMarkdownToUri(
                    context = context,
                    sourceFile = activeResult.outputFile,
                    targetUri = targetUri
                )
                writeRes.fold(
                    onSuccess = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(context, "Saved ${activeResult.fileName}", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { err ->
                        Toast.makeText(context, err.message ?: "Save failed", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    fun startConversion() {
        val doc = inspectedDoc ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        conversionJob?.cancel()
        conversionError = null
        result = null
        savedDownloadPath = null
        isConverting = true
        progress = PdfToMarkdownProgress(
            currentPage = 0,
            totalSelectedPages = doc.pageCount,
            phase = "Initializing streaming engine…",
            pagesPerSecond = 0f,
            etaSeconds = 0,
            wordsExtracted = 0,
            headingsFound = 0,
            tablesFound = 0,
            codeBlocksFound = 0,
            ocrPagesProcessed = 0,
            headersStripped = 0
        )

        conversionJob = scope.launch {
            try {
                val res = PdfToMarkdownEngine.convertPdfToMarkdown(
                    context = context,
                    uri = doc.uri,
                    config = config,
                    onProgress = { p -> progress = p }
                )
                isConverting = false
                res.fold(
                    onSuccess = { mdResult ->
                        result = mdResult
                        previewTab = 0
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onFailure = { err ->
                        if (err !is CancellationException) {
                            conversionError = err.message ?: "Conversion failed."
                        }
                    }
                )
            } catch (_: CancellationException) {
                isConverting = false
            } catch (e: Exception) {
                isConverting = false
                conversionError = e.message ?: "Unexpected conversion error."
            }
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (tokens.isDark) Color(0xFF000000) else tokens.bgVoid)
    ) {
        if (tokens.isDark) {
            DarkVoidAmbientLightPulse(
                activeStageIndex = 1,
                activeSubStepIndex = 1,
                transitionDepthProgress = 0.35f,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ── Floating Glass Top Bar ────────────────────────────────────────
            PdfToMarkdownTopBar(
                tokens = tokens,
                onBack = {
                    if (isConverting) {
                        conversionJob?.cancel()
                        isConverting = false
                    } else {
                        onBack()
                    }
                }
            )

            // ── Scrollable Centered Adaptive Studio Body ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isConverting) ConversionEvidenceCard()

                    // 1. Source PDF Picker / Inspector Card
                    PdfSourceCard(
                        doc = inspectedDoc,
                        isInspecting = isInspecting,
                        inspectError = inspectError,
                        isConverting = isConverting,
                        tokens = tokens,
                        onPickPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                    )

                    // 2. Live Streaming Telemetry HUD (when converting)
                    AnimatedVisibility(
                        visible = isConverting && progress != null,
                        enter = fadeIn(tween(220)) + expandVertically(),
                        exit = fadeOut(tween(180)) + shrinkVertically()
                    ) {
                        progress?.let { liveProgress ->
                            StreamingProgressCard(
                                progress = liveProgress,
                                tokens = tokens,
                                onCancel = {
                                    conversionJob?.cancel()
                                    isConverting = false
                                    Toast.makeText(context, "Conversion cancelled", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // Error Banner
                    AnimatedVisibility(visible = conversionError != null) {
                        conversionError?.let { errText ->
                            ObsidianGlassPanel(
                                tokens = tokens,
                                accentTint = tokens.dangerRose
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Error",
                                        tint = tokens.dangerRose,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = errText,
                                        color = tokens.textPrimary,
                                        fontFamily = ClaudeSans,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Result Dashboard + Export Bar + Preview Studio
                    AnimatedVisibility(
                        visible = result != null && !isConverting,
                        enter = fadeIn(tween(260)) + expandVertically(),
                        exit = fadeOut(tween(180)) + shrinkVertically()
                    ) {
                        result?.let { mdResult ->
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ConversionResultCard(
                                    result = mdResult,
                                    savedDownloadPath = savedDownloadPath,
                                    isSavingDownload = isSavingDownload,
                                    tokens = tokens,
                                    onDownload = {
                                        if (isSavingDownload) return@ConversionResultCard
                                        isSavingDownload = true
                                        scope.launch {
                                            val saveRes = PdfToMarkdownEngine.saveMarkdownToDownloads(
                                                context = context,
                                                sourceFile = mdResult.outputFile,
                                                desiredFileName = mdResult.fileName
                                            )
                                            isSavingDownload = false
                                            saveRes.fold(
                                                onSuccess = { savedPath ->
                                                    savedDownloadPath = savedPath
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    Toast.makeText(
                                                        context,
                                                        "Downloaded to $savedPath",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                },
                                                onFailure = { err ->
                                                    Toast.makeText(
                                                        context,
                                                        err.message ?: "Download failed",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            )
                                        }
                                    },
                                    onSaveAs = {
                                        saveAsLauncher.launch(mdResult.fileName)
                                    },
                                    onShare = {
                                        shareMarkdownFile(context, mdResult)
                                    },
                                    onCopy = {
                                        copyMarkdownPreview(context, mdResult)
                                    },
                                    onConvertAnother = {
                                        result = null
                                        savedDownloadPath = null
                                        inspectedDoc = null
                                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                    }
                                )

                                // Detected Document Outline (TOC)
                                if (mdResult.tocEntries.isNotEmpty()) {
                                    TocOutlineCard(
                                        entries = mdResult.tocEntries,
                                        expanded = showTocOutline,
                                        tokens = tokens,
                                        onToggle = { showTocOutline = !showTocOutline }
                                    )
                                }

                                // Rendered vs Raw .md Preview
                                MarkdownPreviewCard(
                                    result = mdResult,
                                    selectedTab = previewTab,
                                    tokens = tokens,
                                    onSelectTab = { previewTab = it }
                                )
                            }
                        }
                    }

                    // 4. Configuration Studio (Presets, Page Range, Advanced Pipeline Toggles, Convert CTA)
                    AnimatedVisibility(
                        visible = inspectedDoc != null && !isConverting,
                        enter = fadeIn(tween(240)) + expandVertically(),
                        exit = fadeOut(tween(180)) + shrinkVertically()
                    ) {
                        inspectedDoc?.let { doc ->
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (result != null) {
                                    Text(
                                        text = "Re-run with Custom Pipeline",
                                        fontFamily = ClaudeSerif,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.textPrimary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                // Presets 2x2 Grid
                                PresetSelectorSection(
                                    selectedPreset = config.preset,
                                    tokens = tokens,
                                    onSelectPreset = { preset ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        config = PdfToMarkdownConfig.forPreset(
                                            preset = preset,
                                            currentRange = config.pageRangeExpr
                                        )
                                    }
                                )

                                // Page Range Selector
                                PageRangeCard(
                                    totalPages = doc.pageCount,
                                    rangeExpr = config.pageRangeExpr,
                                    tokens = tokens,
                                    onRangeChange = { newExpr ->
                                        config = config.copy(pageRangeExpr = newExpr)
                                    }
                                )

                                // Expandable Advanced Engine Pipeline Toggles
                                AdvancedPipelineOptionsCard(
                                    config = config,
                                    expanded = showAdvancedOptions,
                                    tokens = tokens,
                                    onToggleExpanded = { showAdvancedOptions = !showAdvancedOptions },
                                    onUpdateConfig = { config = it }
                                )

                                // Primary Convert Button
                                val resolvedPages = remember(config.pageRangeExpr, doc.pageCount) {
                                    PdfToMarkdownEngine.parsePageRange(config.pageRangeExpr, doc.pageCount).size
                                }
                                val ctaLabel = if (result != null) {
                                    "Re-Convert $resolvedPages ${if (resolvedPages == 1) "Page" else "Pages"} to .md"
                                } else {
                                    "Convert $resolvedPages ${if (resolvedPages == 1) "Page" else "Pages"} to Markdown"
                                }

                                ObsidianPrimaryActionButton(
                                    text = ctaLabel,
                                    icon = Icons.Rounded.AutoAwesome,
                                    tokens = tokens,
                                    testTag = "pdf_md_convert_btn",
                                    onClick = { startConversion() }
                                )
                            }
                        }
                    }

                    // 5. Architecture Highlights Footer (when idle with no PDF picked yet)
                    if (inspectedDoc == null && !isInspecting) {
                        EngineCapabilitiesGrid(tokens = tokens)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfToMarkdownTopBar(
    tokens: HomeThemeTokens,
    onBack: () -> Unit
) {
    val pillFill = remember(tokens.isDark) {
        if (tokens.isDark) {
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f))
            )
        } else {
            Brush.verticalGradient(listOf(Color.White, Color(0xFFF6F1F1)))
        }
    }
    val pillBorder = remember(tokens.isDark) {
        Brush.linearGradient(
            listOf(
                if (tokens.isDark) Color.White.copy(alpha = 0.28f) else tokens.borderStrong,
                tokens.accentRose.copy(alpha = 0.28f),
                tokens.borderSubtle
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(pillFill)
                .border(0.9.dp, pillBorder, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = tokens.textPrimary.copy(alpha = 0.16f)),
                    onClick = onBack
                )
                .testTag("pdf_md_back_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = tokens.textPrimary,
                modifier = Modifier.size(21.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PDF to Markdown",
                fontFamily = ClaudeSerif,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Streaming layout, tables & OCR engine",
                fontFamily = ClaudeSans,
                fontSize = 12.sp,
                color = tokens.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(tokens.accentRose.copy(alpha = if (tokens.isDark) 0.16f else 0.10f))
                .border(
                    width = 0.8.dp,
                    color = tokens.accentRose.copy(alpha = 0.36f),
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "∞ PAGES",
                fontFamily = ClaudeSans,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = tokens.accentRose
            )
        }
    }
}

@Composable
private fun PdfSourceCard(
    doc: PdfDocumentInspectInfo?,
    isInspecting: Boolean,
    inspectError: String?,
    isConverting: Boolean,
    tokens: HomeThemeTokens,
    onPickPdf: () -> Unit
) {
    val accentViolet = if (tokens.isDark) Color(0xFFB89BFF) else Color(0xFF6B46C1)

    ObsidianGlassPanel(
        tokens = tokens,
        accentTint = if (doc != null) tokens.accentRose else accentViolet
    ) {
        if (isInspecting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = tokens.accentRose,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Inspecting PDF structure & page count…",
                    fontFamily = ClaudeSans,
                    fontSize = 13.5.sp,
                    color = tokens.textSecondary
                )
            }
        } else if (doc == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentViolet.copy(alpha = if (tokens.isDark) 0.16f else 0.10f))
                        .border(0.9.dp, accentViolet.copy(alpha = 0.38f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = "Select PDF",
                        tint = accentViolet,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Convert Any PDF to Clean .md",
                    fontFamily = ClaudeSerif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Built for massive 500+ page books, research papers, manuals & scans. Streams directly to disk with zero memory limits.",
                    fontFamily = ClaudeSans,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                if (inspectError != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = inspectError,
                        fontFamily = ClaudeSans,
                        fontSize = 12.5.sp,
                        color = tokens.dangerRose
                    )
                }

                Spacer(Modifier.height(18.dp))

                ObsidianPrimaryActionButton(
                    text = "Select PDF Document",
                    icon = Icons.Rounded.FolderOpen,
                    tokens = tokens,
                    testTag = "pdf_md_pick_btn",
                    onClick = onPickPdf
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.accentRose.copy(alpha = if (tokens.isDark) 0.16f else 0.10f))
                        .border(0.9.dp, tokens.accentRose.copy(alpha = 0.36f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = tokens.accentRose,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.fileName,
                        fontFamily = ClaudeSerif,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    val sizeLabel = if (doc.fileSizeBytes > 0) {
                        PdfToolsEngine.formatBytes(doc.fileSizeBytes)
                    } else {
                        "Streamed PDF"
                    }
                    val metaLine = buildString {
                        append("${doc.pageCount} ${if (doc.pageCount == 1) "Page" else "Pages"}")
                        append("  ·  ")
                        append(sizeLabel)
                        if (doc.outlineCount > 0) {
                            append("  ·  ${doc.outlineCount} Bookmarks")
                        }
                    }
                    Text(
                        text = metaLine,
                        fontFamily = ClaudeSans,
                        fontSize = 12.sp,
                        color = tokens.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!doc.author.isNullOrBlank()) {
                        Text(
                            text = "Author: ${doc.author}",
                            fontFamily = ClaudeSans,
                            fontSize = 11.5.sp,
                            color = tokens.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!isConverting) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (tokens.isDark) Color.White.copy(alpha = 0.08f)
                                else Color.Black.copy(alpha = 0.05f)
                            )
                            .border(0.8.dp, tokens.borderSubtle, RoundedCornerShape(12.dp))
                            .clickable(onClick = onPickPdf)
                            .padding(horizontal = 12.dp)
                            .testTag("pdf_md_change_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Change",
                            fontFamily = ClaudeSans,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingProgressCard(
    progress: PdfToMarkdownProgress,
    tokens: HomeThemeTokens,
    onCancel: () -> Unit
) {
    val fraction = if (progress.totalSelectedPages > 0) {
        (progress.currentPage.toFloat() / progress.totalSelectedPages.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(220),
        label = "md_progress"
    )
    val percentInt = (animatedFraction * 100f).toInt()
    val mintTint = if (tokens.isDark) Color(0xFF56C288) else Color(0xFF107C41)

    ObsidianGlassPanel(
        tokens = tokens,
        accentTint = tokens.accentRose
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Streaming Page ${progress.currentPage} of ${progress.totalSelectedPages}",
                    fontFamily = ClaudeSerif,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = progress.phase,
                    fontFamily = ClaudeSans,
                    fontSize = 12.sp,
                    color = tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$percentInt%",
                fontFamily = ClaudeSerif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.accentRose
            )
        }

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = tokens.accentRose,
            trackColor = if (tokens.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val speedText = if (progress.pagesPerSecond > 0.05f) {
                String.format(Locale.US, "%.1f pages/sec", progress.pagesPerSecond)
            } else {
                "Measuring throughput…"
            }
            val etaText = if (progress.etaSeconds > 0) {
                "ETA ${formatEta(progress.etaSeconds)}"
            } else {
                "Finalizing…"
            }
            Text(
                text = speedText,
                fontFamily = ClaudeSans,
                fontSize = 11.5.sp,
                color = mintTint,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = etaText,
                fontFamily = ClaudeSans,
                fontSize = 11.5.sp,
                color = tokens.textMuted
            )
        }

        Spacer(Modifier.height(14.dp))

        // Live extraction counters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactStatChip("Words", formatCompactNumber(progress.wordsExtracted), tokens, Modifier.weight(1f))
            CompactStatChip("Headings", progress.headingsFound.toString(), tokens, Modifier.weight(1f))
            CompactStatChip("Tables", progress.tablesFound.toString(), tokens, Modifier.weight(1f))
            CompactStatChip("OCR", progress.ocrPagesProcessed.toString(), tokens, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tokens.dangerRose.copy(alpha = if (tokens.isDark) 0.14f else 0.09f))
                .border(0.85.dp, tokens.dangerRose.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .clickable(onClick = onCancel)
                .testTag("pdf_md_cancel_btn"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Cancel Conversion",
                fontFamily = ClaudeSans,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.dangerRose
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversionResultCard(
    result: PdfToMarkdownResult,
    savedDownloadPath: String?,
    isSavingDownload: Boolean,
    tokens: HomeThemeTokens,
    onDownload: () -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onConvertAnother: () -> Unit
) {
    val mintTint = if (tokens.isDark) Color(0xFF56C288) else Color(0xFF107C41)

    ObsidianGlassPanel(
        tokens = tokens,
        accentTint = mintTint
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(mintTint.copy(alpha = if (tokens.isDark) 0.18f else 0.12f))
                    .border(0.9.dp, mintTint.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Complete",
                    tint = mintTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.fileName,
                    fontFamily = ClaudeSerif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val secText = String.format(Locale.US, "%.1fs", result.elapsedMs / 1000f)
                Text(
                    text = "${result.totalPagesConverted} pages · ${PdfToolsEngine.formatBytes(result.fileSizeBytes)} · $secText",
                    fontFamily = ClaudeSans,
                    fontSize = 12.sp,
                    color = mintTint,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Comprehensive Quality Metrics
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricPill("${formatCompactNumber(result.wordCount)} words", tokens)
            MetricPill("${result.headingCount} headings", tokens)
            MetricPill("${result.tableCount} tables", tokens)
            if (result.codeBlockCount > 0) {
                MetricPill("${result.codeBlockCount} code blocks", tokens)
            }
            if (result.ocrPageCount > 0) {
                MetricPill("${result.ocrPageCount} OCR pages", tokens)
            }
            if (result.headersFootersStripped > 0) {
                MetricPill("${result.headersFootersStripped} chrome lines stripped", tokens)
            }
        }

        if (savedDownloadPath != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(mintTint.copy(alpha = if (tokens.isDark) 0.14f else 0.09f))
                    .border(0.8.dp, mintTint.copy(alpha = 0.34f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = mintTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Saved to $savedDownloadPath",
                    fontFamily = ClaudeSans,
                    fontSize = 12.sp,
                    color = tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Primary Download .md CTA
        ObsidianPrimaryActionButton(
            text = if (isSavingDownload) "Saving to Downloads…" else "Download .md File",
            icon = Icons.Rounded.Download,
            tokens = tokens,
            enabled = !isSavingDownload,
            testTag = "pdf_md_download_btn",
            onClick = onDownload
        )

        Spacer(Modifier.height(10.dp))

        // Secondary Action Row: Save As / Share / Copy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassActionButton(
                label = "Save As",
                icon = Icons.Rounded.SaveAs,
                tokens = tokens,
                testTag = "pdf_md_save_as_btn",
                modifier = Modifier.weight(1f),
                onClick = onSaveAs
            )
            GlassActionButton(
                label = "Share",
                icon = Icons.Rounded.Share,
                tokens = tokens,
                testTag = "pdf_md_share_btn",
                modifier = Modifier.weight(1f),
                onClick = onShare
            )
            GlassActionButton(
                label = "Copy",
                icon = Icons.Rounded.ContentCopy,
                tokens = tokens,
                testTag = "pdf_md_copy_btn",
                modifier = Modifier.weight(1f),
                onClick = onCopy
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onConvertAnother)
                .testTag("pdf_md_reset_btn"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Convert Another PDF",
                    fontFamily = ClaudeSans,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.textSecondary
                )
            }
        }
    }
}

@Composable
private fun TocOutlineCard(
    entries: List<com.swiffle.app.conversion.MarkdownTocEntry>,
    expanded: Boolean,
    tokens: HomeThemeTokens,
    onToggle: () -> Unit
) {
    ObsidianGlassPanel(tokens = tokens, accentTint = null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle)
                .testTag("pdf_md_toc_toggle"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = tokens.accentRose,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Detected Document Outline",
                    fontFamily = ClaudeSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
                Text(
                    text = "${entries.size} structured headings auto-linked",
                    fontFamily = ClaudeSans,
                    fontSize = 11.5.sp,
                    color = tokens.textMuted
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = tokens.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HorizontalDivider(color = tokens.borderSubtle, thickness = 0.8.dp)
                Spacer(Modifier.height(4.dp))
                entries.take(40).forEach { entry ->
                    val indent = ((entry.level - 1).coerceIn(0, 3) * 14).dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indent),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "H${entry.level}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.accentRose
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.title,
                            fontFamily = ClaudeSans,
                            fontSize = 12.5.sp,
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "p.${entry.pageNumber}",
                            fontFamily = ClaudeSans,
                            fontSize = 11.sp,
                            color = tokens.textMuted
                        )
                    }
                }
                if (entries.size > 40) {
                    Text(
                        text = "+ ${entries.size - 40} more headings included in exported .md file",
                        fontFamily = ClaudeSans,
                        fontSize = 11.5.sp,
                        color = tokens.textMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreviewCard(
    result: PdfToMarkdownResult,
    selectedTab: Int,
    tokens: HomeThemeTokens,
    onSelectTab: (Int) -> Unit
) {
    ObsidianGlassPanel(tokens = tokens, accentTint = null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Markdown Preview",
                fontFamily = ClaudeSerif,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (tokens.isDark) Color.White.copy(alpha = 0.06f)
                        else Color.Black.copy(alpha = 0.05f)
                    )
                    .border(0.8.dp, tokens.borderSubtle, RoundedCornerShape(999.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PreviewTabChip(
                    label = "Rendered",
                    selected = selectedTab == 0,
                    tokens = tokens,
                    testTag = "pdf_md_tab_rendered",
                    onClick = { onSelectTab(0) }
                )
                PreviewTabChip(
                    label = "Raw .md",
                    selected = selectedTab == 1,
                    tokens = tokens,
                    testTag = "pdf_md_tab_raw",
                    onClick = { onSelectTab(1) }
                )
            }
        }

        if (result.isPreviewTruncated) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.accentRose.copy(alpha = if (tokens.isDark) 0.13f else 0.08f))
                    .border(0.8.dp, tokens.accentRose.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Large document preview capped for 60fps UI performance. Download or Save As exports all ${result.totalPagesConverted} pages (${PdfToolsEngine.formatBytes(result.fileSizeBytes)}).",
                    fontFamily = ClaudeSans,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = tokens.textSecondary
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = tokens.borderSubtle, thickness = 0.8.dp)
        Spacer(Modifier.height(12.dp))

        if (selectedTab == 0) {
            RichMarkdownBody(
                text = result.previewMarkdown,
                isDark = tokens.isDark,
                textPrimary = tokens.textPrimary
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (tokens.isDark) Color(0xFF06060A)
                        else Color(0xFFF3EEEE)
                    )
                    .border(0.8.dp, tokens.borderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = result.previewMarkdown,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = tokens.textPrimary
                )
            }
        }
    }
}

@Composable
private fun PreviewTabChip(
    label: String,
    selected: Boolean,
    tokens: HomeThemeTokens,
    testTag: String,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) {
            if (tokens.isDark) Color.White.copy(alpha = 0.16f) else Color.White
        } else Color.Transparent,
        animationSpec = tween(160),
        label = "preview_tab_bg"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = ClaudeSans,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) tokens.textPrimary else tokens.textMuted
        )
    }
}

@Composable
private fun PresetSelectorSection(
    selectedPreset: MarkdownPreset,
    tokens: HomeThemeTokens,
    onSelectPreset: (MarkdownPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Conversion Profile",
            fontFamily = ClaudeSerif,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary,
            modifier = Modifier.padding(start = 2.dp)
        )

        val presets = MarkdownPreset.entries
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (rowIndex in 0 until presets.size step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (colOffset in 0..1) {
                        val idx = rowIndex + colOffset
                        if (idx < presets.size) {
                            val preset = presets[idx]
                            val isSelected = preset == selectedPreset
                            PresetGlassCard(
                                preset = preset,
                                isSelected = isSelected,
                                tokens = tokens,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectPreset(preset) }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetGlassCard(
    preset: MarkdownPreset,
    isSelected: Boolean,
    tokens: HomeThemeTokens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val borderBrush = if (isSelected) {
        Brush.linearGradient(
            listOf(
                tokens.accentRose.copy(alpha = 0.85f),
                if (tokens.isDark) Color.White.copy(alpha = 0.35f) else tokens.accentRose.copy(alpha = 0.45f)
            )
        )
    } else {
        Brush.linearGradient(listOf(tokens.borderStrong.copy(alpha = 0.5f), tokens.borderSubtle))
    }

    val fillBrush = if (tokens.isDark) {
        Brush.verticalGradient(
            listOf(
                if (isSelected) tokens.accentRose.copy(alpha = 0.16f) else tokens.cardTop,
                tokens.cardBottom
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                if (isSelected) tokens.accentRose.copy(alpha = 0.09f) else tokens.cardTop,
                tokens.cardBottom
            )
        )
    }

    Column(
        modifier = modifier
            .heightIn(min = 82.dp)
            .clip(shape)
            .background(fillBrush)
            .border(if (isSelected) 1.2.dp else 0.85.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("pdf_md_preset_${preset.name.lowercase()}"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = preset.title,
                fontFamily = ClaudeSerif,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) tokens.textPrimary else tokens.textPrimary.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tokens.accentRose)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = preset.subtitle,
            fontFamily = ClaudeSans,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = tokens.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PageRangeCard(
    totalPages: Int,
    rangeExpr: String,
    tokens: HomeThemeTokens,
    onRangeChange: (String) -> Unit
) {
    val selectedCount = remember(rangeExpr, totalPages) {
        PdfToMarkdownEngine.parsePageRange(rangeExpr, totalPages).size
    }

    ObsidianGlassPanel(tokens = tokens, accentTint = null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Page Range",
                fontFamily = ClaudeSerif,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )
            Text(
                text = "$selectedCount / $totalPages selected",
                fontFamily = ClaudeSans,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.accentRose
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (tokens.isDark) Color.White.copy(alpha = 0.06f)
                    else Color.Black.copy(alpha = 0.04f)
                )
                .border(0.85.dp, tokens.borderSubtle, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = rangeExpr,
                onValueChange = onRangeChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = ClaudeSans,
                    fontSize = 14.sp,
                    color = tokens.textPrimary
                ),
                cursorBrush = SolidColor(tokens.accentRose),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_md_page_range_input"),
                decorationBox = { innerTextField ->
                    if (rangeExpr.isBlank()) {
                        Text(
                            text = "All $totalPages pages (or e.g. 1-50, 120-$totalPages)",
                            fontFamily = ClaudeSans,
                            fontSize = 13.5.sp,
                            color = tokens.textMuted
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun AdvancedPipelineOptionsCard(
    config: PdfToMarkdownConfig,
    expanded: Boolean,
    tokens: HomeThemeTokens,
    onToggleExpanded: () -> Unit,
    onUpdateConfig: (PdfToMarkdownConfig) -> Unit
) {
    ObsidianGlassPanel(tokens = tokens, accentTint = null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggleExpanded)
                .testTag("pdf_md_advanced_toggle"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = tokens.accentRose,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Engine Pipeline Options",
                    fontFamily = ClaudeSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary
                )
                Text(
                    text = "Tables, multi-column, OCR fallback, frontmatter & TOC",
                    fontFamily = ClaudeSans,
                    fontSize = 11.5.sp,
                    color = tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = tokens.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                HorizontalDivider(color = tokens.borderSubtle, thickness = 0.8.dp)
                Spacer(Modifier.height(4.dp))

                PipelineToggleRow(
                    title = "Strip Repeating Headers & Footers",
                    subtitle = "Auto-detect & remove running titles and page numbers",
                    checked = config.stripHeadersAndFooters,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(stripHeadersAndFooters = it)) }
                )
                PipelineToggleRow(
                    title = "GFM Table Reconstruction",
                    subtitle = "Rebuild aligned multi-column tables as Markdown pipes",
                    checked = config.detectTablesAsGfm,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(detectTablesAsGfm = it)) }
                )
                PipelineToggleRow(
                    title = "Multi-Column Reading Order",
                    subtitle = "Re-flow 2-column academic papers left-to-right cleanly",
                    checked = config.reorderMultiColumn,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(reorderMultiColumn = it)) }
                )
                PipelineToggleRow(
                    title = "Rich Inline Styles & Fenced Code",
                    subtitle = "Preserve **bold**, *italic*, `code`, and language blocks",
                    checked = config.preserveRichTypography,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(preserveRichTypography = it)) }
                )
                PipelineToggleRow(
                    title = "Smart De-Hyphenation & Unwrap",
                    subtitle = "Merge hard line breaks & rejoin hyphenated words",
                    checked = config.dehyphenateAndUnwrap,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(dehyphenateAndUnwrap = it)) }
                )
                PipelineToggleRow(
                    title = "YAML Frontmatter Metadata",
                    subtitle = "Prepend title, author, pages & stats for Obsidian / RAG",
                    checked = config.includeYamlFrontmatter,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(includeYamlFrontmatter = it)) }
                )
                PipelineToggleRow(
                    title = "Auto-Linked Table of Contents",
                    subtitle = "Generate clickable Markdown anchor outline at top",
                    checked = config.generateTableOfContents,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(generateTableOfContents = it)) }
                )
                PipelineToggleRow(
                    title = "Page Anchor Comments",
                    subtitle = "Insert <!-- Page N --> markers for LLM chunking",
                    checked = config.insertPageMarkers,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(insertPageMarkers = it)) }
                )
                PipelineToggleRow(
                    title = "Automatic ML Kit OCR Fallback",
                    subtitle = "OCR scanned image pages seamlessly during streaming",
                    checked = config.ocrFallbackForScannedPages,
                    tokens = tokens,
                    onCheckedChange = { onUpdateConfig(config.copy(ocrFallbackForScannedPages = it)) }
                )
            }
        }
    }
}

@Composable
private fun PipelineToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tokens: HomeThemeTokens,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = ClaudeSans,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = ClaudeSans,
                fontSize = 11.5.sp,
                color = tokens.textMuted
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = tokens.accentRose,
                uncheckedThumbColor = tokens.textSecondary,
                uncheckedTrackColor = if (tokens.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f),
                uncheckedBorderColor = tokens.borderSubtle
            )
        )
    }
}

@Composable
private fun EngineCapabilitiesGrid(tokens: HomeThemeTokens) {
    val items = listOf(
        Triple("500–1,000+ Pages", "Disk-backed streaming prevents crashes & OOM", tokens.accentRose),
        Triple("Layout Intelligence", "2-column reordering, GFM tables & headings", if (tokens.isDark) Color(0xFF6B9EFF) else Color(0xFF185ABD)),
        Triple("Smart OCR Fallback", "Scanned image pages auto-OCR via ML Kit", if (tokens.isDark) Color(0xFFB89BFF) else Color(0xFF6B46C1)),
        Triple("Obsidian & RAG Ready", "YAML frontmatter, TOC links & page anchors", if (tokens.isDark) Color(0xFF56C288) else Color(0xFF107C41))
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Why Swiffle MD Engine",
            fontFamily = ClaudeSerif,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp)
        )
        for (i in items.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (j in 0..1) {
                    val item = items[i + j]
                    ObsidianGlassPanel(
                        tokens = tokens,
                        accentTint = item.third,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(item.third)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.first,
                            fontFamily = ClaudeSerif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.textPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.second,
                            fontFamily = ClaudeSans,
                            fontSize = 11.5.sp,
                            lineHeight = 15.5.sp,
                            color = tokens.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ObsidianGlassPanel(
    tokens: HomeThemeTokens,
    accentTint: Color?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val fillBrush = remember(tokens.isDark) {
        Brush.verticalGradient(listOf(tokens.cardTop, tokens.cardBottom))
    }
    val borderBrush = remember(tokens.isDark, accentTint) {
        Brush.linearGradient(
            listOf(
                if (tokens.isDark) Color.White.copy(alpha = 0.22f) else tokens.borderStrong,
                (accentTint ?: tokens.accentRose).copy(alpha = if (tokens.isDark) 0.28f else 0.16f),
                tokens.borderSubtle
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fillBrush)
            .drawBehind {
                if (accentTint != null && tokens.isDark) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentTint.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(size.width * 0.14f, size.height * 0.12f),
                            radius = size.width * 0.65f
                        ),
                        center = Offset(size.width * 0.14f, size.height * 0.12f),
                        radius = size.width * 0.65f
                    )
                }
            }
            .border(0.9.dp, borderBrush, shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
private fun ObsidianPrimaryActionButton(
    text: String,
    icon: ImageVector,
    tokens: HomeThemeTokens,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(90),
        label = "primary_btn_scale"
    )
    val shape = RoundedCornerShape(16.dp)
    val bgBrush = remember(tokens.isDark, enabled) {
        if (tokens.isDark) {
            Brush.linearGradient(
                listOf(
                    Color(0xFF9E1F2A).copy(alpha = if (enabled) 1f else 0.45f),
                    Color(0xFF6B0F1A).copy(alpha = if (enabled) 1f else 0.45f)
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    Color(0xFF8B1420).copy(alpha = if (enabled) 1f else 0.45f),
                    Color(0xFF5D0A14).copy(alpha = if (enabled) 1f else 0.45f)
                )
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgBrush)
            .border(
                width = 0.9.dp,
                color = Color.White.copy(alpha = if (tokens.isDark) 0.28f else 0.22f),
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontFamily = ClaudeSans,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassActionButton(
    label: String,
    icon: ImageVector,
    tokens: HomeThemeTokens,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (tokens.isDark) Color.White.copy(alpha = 0.07f)
                else Color.Black.copy(alpha = 0.045f)
            )
            .border(0.85.dp, tokens.borderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tokens.textPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = ClaudeSans,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactStatChip(
    label: String,
    value: String,
    tokens: HomeThemeTokens,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (tokens.isDark) Color.White.copy(alpha = 0.05f)
                else Color.Black.copy(alpha = 0.035f)
            )
            .border(0.75.dp, tokens.borderSubtle, shape)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontFamily = ClaudeSerif,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary,
            maxLines = 1
        )
        Text(
            text = label,
            fontFamily = ClaudeSans,
            fontSize = 10.5.sp,
            color = tokens.textMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun MetricPill(
    text: String,
    tokens: HomeThemeTokens
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (tokens.isDark) Color.White.copy(alpha = 0.06f)
                else Color.Black.copy(alpha = 0.045f)
            )
            .border(0.75.dp, tokens.borderSubtle, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontFamily = ClaudeSans,
            fontSize = 11.5.sp,
            color = tokens.textSecondary
        )
    }
}

private fun shareMarkdownFile(context: Context, result: PdfToMarkdownResult) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            result.outputFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, result.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${result.fileName}"))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "Unable to share Markdown file", Toast.LENGTH_SHORT).show()
    }
}

private fun copyMarkdownPreview(context: Context, result: PdfToMarkdownResult) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = if (!result.isPreviewTruncated && result.outputFile.length() <= 180_000L) {
            result.outputFile.readText(Charsets.UTF_8)
        } else {
            result.previewMarkdown
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(result.fileName, textToCopy))
        val msg = if (result.isPreviewTruncated) {
            "Copied preview chunk (use Download for full ${result.totalPagesConverted}-page file)"
        } else {
            "Copied complete Markdown to clipboard"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to copy Markdown", Toast.LENGTH_SHORT).show()
    }
}

private fun formatEta(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val mins = seconds / 60
    val remSec = seconds % 60
    return if (mins < 60) "${mins}m ${remSec}s" else "${mins / 60}h ${mins % 60}m"
}

private fun formatCompactNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000f)
        n >= 10_000 -> String.format(Locale.US, "%.1fk", n / 1_000f)
        else -> String.format(Locale.US, "%,d", n)
    }
}
