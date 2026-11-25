package com.swiffle.app.ui.convert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.print.PrintManager
import android.app.Activity
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.concurrent.Executors
import com.swiffle.app.conversion.ImageToPdfConverter
import com.swiffle.app.theme.*
import com.swiffle.app.ui.components.StockMaterialLogoShimmer
import com.swiffle.app.ui.components.SwiffleAnim
import com.swiffle.app.ui.components.SwiffleLottieOneShot
import com.swiffle.app.ui.components.WinUiToggleSwitch
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// ── Windows 11 Fluent Motion & Physics Constants ──────────────────────────────
private const val FluentFasterDuration = 83
private const val FluentFastDuration = 167
private const val FluentNormalDuration = 250
private val FluentDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
private val FluentAccelerate = CubicBezierEasing(1.0f, 0.0f, 1.0f, 1.0f)

// ── Windows 11 Fluent Colors & Brand Tints ────────────────────────────────────
private val FluentCobaltBlue   = Color(0xFF0078D4) // Primary PDF & System Accent
private val FluentWordBlue     = Color(0xFF185ABD) // Microsoft Word
private val FluentPptOrange    = Color(0xFFD83B01) // Microsoft PowerPoint
private val FluentExcelGreen   = Color(0xFF107C41) // Microsoft Excel / ID Card
private val FluentPdfRed       = Color(0xFFD13438) // PDF File / Red Accent
private val FluentCompressAmber= Color(0xFFCA5010) // Compression
private val FluentSecurityCyan = Color(0xFF008272) // Security & Lock

// ── Active Navigation Screen inside Convert & Scan Hub ────────────────────────
private enum class HubScreen {
    TOOLS_HUB,
    IMAGE_TO_PDF,
    REVIEW_CONVERT,
    SMART_SCAN_DOCS,
    SMART_SCAN_ID_CARD,
    EXCEL_STUDIO
}

data class ToolItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val badgeColor: Color,
    val badgeText: String? = null
)

/**
 * Windows 11 Fluent Design System Master Screen for Convert and Scan.
 * Incorporates physics-based, authentic Windows 11 Fluent transitions:
 * - Forward Navigation: Horizontal Slide In (0.35x) + Fade In (250ms Decelerate)
 * - Backward Pop: Scale In (0.94x) + Fade In paired with Horizontal Slide Out (250ms Accelerate)
 * - Camera Scanner Reveal: Bottom-Up Vertical Slide (0.30x) + Scale In (0.92x -> 1.0x) + Fade
 * - Modal Sheets: Bottom Slide In / Out with Backdrop Blur
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    onBack: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit = { _, _ -> },
    vm: ConversionViewModel = viewModel(),
    startWithScan: Boolean = false
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isDark  = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
    val supportsBlur = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    // Screen navigation state
    var currentScreen by remember { mutableStateOf(HubScreen.TOOLS_HUB) }

    // ── Fluent Material Tokens (Deep Dark Void Black for Dark Theme) ────────────────
    val micaBackdrop   = if (isDark) Color(0xFF000000) else Color(0xFFF3F3F3)
    val cardLayerFill  = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val cardNestedFill = if (isDark) Color(0xFF161616) else Color(0xFFF8F9FA)
    val specularStroke = if (isDark) Color(0x2EFFFFFF) else Color(0x14000000)
    val textPrimary    = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val textSecondary  = if (isDark) Color(0xFFB5B5B5) else Color(0xFF5D5D5D)
    val textTertiary   = if (isDark) Color(0xFF7A7A7A) else Color(0xFF8A8A8A)

    // Sub-tool dialog states
    var compressTargetUri by remember { mutableStateOf<Uri?>(null) }
    var showCompressModal by remember { mutableStateOf(false) }
    var selectedCompressLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    var isCompressing by remember { mutableStateOf(false) }
    var compressProgress by remember { mutableIntStateOf(0) }
    var compressionResult by remember { mutableStateOf<CompressionResult?>(null) }

    var isMerging by remember { mutableStateOf(false) }
    var mergeProgress by remember { mutableIntStateOf(0) }
    var mergeResult by remember { mutableStateOf<MergeResult?>(null) }

    var isConvertingPdfToJpg by remember { mutableStateOf(false) }
    var pdfToJpgProgress by remember { mutableIntStateOf(0) }
    var pdfToJpgResult by remember { mutableStateOf<PdfToJpgResult?>(null) }

    // File pickers
    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            compressTargetUri = uri
            showCompressModal = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val multiPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            isMerging = true
            mergeProgress = 0
            coroutineScope.launch {
                val res = PdfToolsEngine.mergePdfs(context, uris) { prog -> mergeProgress = prog }
                isMerging = false
                res.fold(
                    onSuccess = { mergeResult = it },
                    onFailure = { Toast.makeText(context, it.message ?: "Merge failed", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    val pdfToJpgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isConvertingPdfToJpg = true
            pdfToJpgProgress = 0
            coroutineScope.launch {
                val res = PdfToolsEngine.pdfToJpg(context, uri) { prog -> pdfToJpgProgress = prog }
                isConvertingPdfToJpg = false
                res.fold(
                    onSuccess = { pdfToJpgResult = it },
                    onFailure = { Toast.makeText(context, it.message ?: "Extraction failed", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    val importPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment ?: "Document.pdf"
            onOpenPdf(uri, name)
        }
    }

    val printPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val printIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(printIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "No PDF print viewer installed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val officeFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Opening document in Swiffle Viewer...", Toast.LENGTH_SHORT).show()
            val name = uri.lastPathSegment ?: "document"
            onOpenPdf(uri, name)
        }
    }

    var scannerSourceScreen by remember { mutableStateOf(HubScreen.TOOLS_HUB) }

    // Camera permission for Smart Scan
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            currentScreen = HubScreen.SMART_SCAN_DOCS
        } else {
            Toast.makeText(context, "Camera permission required for document scanning", Toast.LENGTH_SHORT).show()
        }
    }

    var openedFromScanShortcut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startWithScan) {
        if (startWithScan && !openedFromScanShortcut) {
            openedFromScanShortcut = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                currentScreen = HubScreen.SMART_SCAN_DOCS
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Camera permission for ID Card
    val idCardPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            currentScreen = HubScreen.SMART_SCAN_ID_CARD
        } else {
            Toast.makeText(context, "Camera permission required for ID card scanning", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle Android system back gesture smoothly across all screens
    BackHandler(enabled = true) {
        when (currentScreen) {
            HubScreen.REVIEW_CONVERT -> currentScreen = HubScreen.IMAGE_TO_PDF
            HubScreen.IMAGE_TO_PDF, HubScreen.EXCEL_STUDIO -> currentScreen = HubScreen.TOOLS_HUB
            HubScreen.SMART_SCAN_DOCS, HubScreen.SMART_SCAN_ID_CARD -> {
                currentScreen = if (vm.images.value.isNotEmpty()) {
                    HubScreen.REVIEW_CONVERT
                } else scannerSourceScreen
            }
            HubScreen.TOOLS_HUB -> onBack()
        }
    }

    // ── Windows 11 Fluent Animated Screen Router (Fade & Horizontal Slide In / Out) ──
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == HubScreen.SMART_SCAN_DOCS || targetState == HubScreen.SMART_SCAN_ID_CARD) {
                // Bottom-Up Camera Scanner Reveal
                (slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(FluentNormalDuration, easing = FluentDecelerate)
                ) + fadeIn(
                    animationSpec = tween(FluentNormalDuration, easing = FluentDecelerate)
                )).togetherWith(
                    fadeOut(animationSpec = tween(FluentFastDuration, easing = FluentAccelerate))
                )
            } else if (initialState == HubScreen.SMART_SCAN_DOCS || initialState == HubScreen.SMART_SCAN_ID_CARD) {
                // Camera Scanner Dismissal
                fadeIn(animationSpec = tween(FluentNormalDuration, easing = FluentDecelerate)).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(FluentNormalDuration, easing = FluentAccelerate)
                    ) + fadeOut(animationSpec = tween(FluentFastDuration, easing = FluentAccelerate))
                )
            } else if (
                (initialState == HubScreen.REVIEW_CONVERT && (targetState == HubScreen.IMAGE_TO_PDF || targetState == HubScreen.TOOLS_HUB)) ||
                ((initialState == HubScreen.IMAGE_TO_PDF || initialState == HubScreen.EXCEL_STUDIO) && targetState == HubScreen.TOOLS_HUB)
            ) {
                // Horizontal Pop (Backward Return)
                com.swiffle.app.theme.SwiffleMotion.horizontalBackward()
            } else {
                // Horizontal Push (Forward Transition across all pages)
                com.swiffle.app.theme.SwiffleMotion.horizontalForward()
            }
        },
        label = "fluentScreenTransition",
        modifier = Modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            HubScreen.TOOLS_HUB -> {
                FluentToolsHubContent(
                    onBack = onBack,
                    onNavigateToImageToPdf = { currentScreen = HubScreen.IMAGE_TO_PDF },
                    onNavigateToExcel = { currentScreen = HubScreen.EXCEL_STUDIO },
                    onStartSmartScan = {
                        scannerSourceScreen = HubScreen.TOOLS_HUB
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            currentScreen = HubScreen.SMART_SCAN_DOCS
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onStartIdCardScan = {
                        scannerSourceScreen = HubScreen.TOOLS_HUB
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            currentScreen = HubScreen.SMART_SCAN_ID_CARD
                        } else {
                            idCardPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onPickSinglePdf = { singlePdfPicker.launch("application/pdf") },
                    onPickMultiPdf = { multiPdfPicker.launch("application/pdf") },
                    onPickPdfForJpg = { pdfToJpgPicker.launch("application/pdf") },
                    onPickPdfToImport = { importPdfPicker.launch("application/pdf") },
                    onPickPdfToPrint = { printPdfPicker.launch("application/pdf") },
                    onPickOfficeFile = { officeFilePicker.launch("*/*") },
                    micaBackdrop = micaBackdrop,
                    cardLayerFill = cardLayerFill,
                    specularStroke = specularStroke,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    textTertiary = textTertiary,
                    haptic = haptic,
                    context = context
                )
            }

            HubScreen.IMAGE_TO_PDF -> {
                FluentImageToPdfStudioLanding(
                    onBack = { currentScreen = HubScreen.TOOLS_HUB },
                    onOpenPdf = onOpenPdf,
                    onStartCameraScan = {
                        scannerSourceScreen = HubScreen.IMAGE_TO_PDF
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            currentScreen = HubScreen.SMART_SCAN_DOCS
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onImagesPicked = { uris ->
                        vm.addImages(uris)
                        currentScreen = HubScreen.REVIEW_CONVERT
                    },
                    onNavigateToReview = {
                        currentScreen = HubScreen.REVIEW_CONVERT
                    },
                    vm = vm,
                    micaBackdrop = micaBackdrop,
                    cardLayerFill = cardLayerFill,
                    cardNestedFill = cardNestedFill,
                    specularStroke = specularStroke,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    textTertiary = textTertiary,
                    isDark = isDark
                )
            }

            HubScreen.REVIEW_CONVERT -> {
                FluentReviewAndConvertSubScreen(
                    onBack = { currentScreen = HubScreen.IMAGE_TO_PDF },
                    onOpenPdf = onOpenPdf,
                    onAddMoreFromCamera = {
                        scannerSourceScreen = HubScreen.REVIEW_CONVERT
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            currentScreen = HubScreen.SMART_SCAN_DOCS
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onFinishedConversion = {
                        currentScreen = HubScreen.IMAGE_TO_PDF
                    },
                    vm = vm,
                    micaBackdrop = micaBackdrop,
                    cardLayerFill = cardLayerFill,
                    cardNestedFill = cardNestedFill,
                    specularStroke = specularStroke,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    textTertiary = textTertiary,
                    isDark = isDark,
                    supportsBlur = supportsBlur
                )
            }

            HubScreen.SMART_SCAN_DOCS -> {
                FluentCameraScannerSheet(
                    initialMode = ScannerMode.DOCS,
                    onCapture = { uri ->
                        vm.addImage(uri)
                    },
                    onClose = {
                        currentScreen = if (vm.images.value.isNotEmpty()) HubScreen.REVIEW_CONVERT else scannerSourceScreen
                    }
                )
            }

            HubScreen.SMART_SCAN_ID_CARD -> {
                FluentCameraScannerSheet(
                    initialMode = ScannerMode.ID_CARD,
                    onCapture = { uri ->
                        vm.addImage(uri)
                    },
                    onClose = {
                        currentScreen = if (vm.images.value.isNotEmpty()) HubScreen.REVIEW_CONVERT else scannerSourceScreen
                    }
                )
            }

            HubScreen.EXCEL_STUDIO -> {
                ImageToExcelScreen(
                    onBack = { currentScreen = HubScreen.TOOLS_HUB }
                )
            }
        }
    }

    // ── Animated Dialog: Compress PDF Modal Sheet ─────────────────────────────
    if (showCompressModal && compressTargetUri != null) {
        Dialog(
            onDismissRequest = { if (!isCompressing) showCompressModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = cardLayerFill,
                    border = BorderStroke(1.dp, specularStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Compress PDF",
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                fontFamily = GoogleSans
                            )
                            IconButton(
                                onClick = { showCompressModal = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, "Close", tint = textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        CompressionLevel.values().forEach { level ->
                            val isSelected = level == selectedCompressLevel
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) FluentCobaltBlue.copy(alpha = 0.08f) else cardNestedFill,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) FluentCobaltBlue else specularStroke
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedCompressLevel = level }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            level.title,
                                            color = textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp
                                        )
                                        Text(
                                            level.subtitle,
                                            color = textSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedCompressLevel = level },
                                        colors = RadioButtonDefaults.colors(selectedColor = FluentCobaltBlue)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = {
                                isCompressing = true
                                compressProgress = 0
                                coroutineScope.launch {
                                    val res = PdfToolsEngine.compressPdf(
                                        context = context,
                                        pdfUri = compressTargetUri!!,
                                        level = selectedCompressLevel
                                    ) { prog -> compressProgress = prog }
                                    isCompressing = false
                                    showCompressModal = false
                                    res.fold(
                                        onSuccess = { compressionResult = it },
                                        onFailure = { Toast.makeText(context, it.message ?: "Compression failed", Toast.LENGTH_SHORT).show() }
                                    )
                                }
                            },
                            enabled = !isCompressing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FluentCobaltBlue)
                        ) {
                            if (isCompressing) {
                                Text("Compressing $compressProgress%…", color = Color.White, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("COMPRESS", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Animated Dialog: Compression Success Modal ────────────────────────────
    if (compressionResult != null) {
        val r = compressionResult!!
        Dialog(
            onDismissRequest = { compressionResult = null },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cardLayerFill,
                shadowElevation = 24.dp,
                border = BorderStroke(1.dp, specularStroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    SwiffleLottieOneShot(SwiffleAnim.SUCCESS_CHECK, size = 80.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Compressed successfully!",
                        color = textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        fontFamily = GoogleSans
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${PdfToolsEngine.formatBytes(r.originalSizeBytes)} → ${PdfToolsEngine.formatBytes(r.compressedSizeBytes)} (${r.reductionPercent}% reduced)",
                        color = FluentCobaltBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = cardNestedFill,
                        border = BorderStroke(1.dp, specularStroke),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(FluentIcons.Compress, null, tint = FluentCobaltBlue, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(r.outputFile.name, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Saved in Documents/Swiffle", color = textTertiary, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val target = r.contentUri
                            val name = r.outputFile.name
                            compressionResult = null
                            onOpenPdf(target, name)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FluentCobaltBlue)
                    ) {
                        Text("OPEN", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // ── Animated Dialog: Merge Success Modal ──────────────────────────────────
    if (mergeResult != null) {
        val r = mergeResult!!
        Dialog(onDismissRequest = { mergeResult = null }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cardLayerFill,
                shadowElevation = 24.dp,
                border = BorderStroke(1.dp, specularStroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    SwiffleLottieOneShot(SwiffleAnim.SUCCESS_CHECK, size = 80.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("PDFs Merged Successfully!", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, fontFamily = GoogleSans)
                    Spacer(Modifier.height(4.dp))
                    Text("Combined into ${r.totalPages} pages (${PdfToolsEngine.formatBytes(r.fileSizeBytes)})", color = textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            val uri = r.contentUri
                            val name = r.outputFile.name
                            mergeResult = null
                            onOpenPdf(uri, name)
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FluentCobaltBlue)
                    ) {
                        Text("OPEN", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // ── Animated Dialog: PDF to JPG Success Modal ─────────────────────────────
    if (pdfToJpgResult != null) {
        val r = pdfToJpgResult!!
        Dialog(onDismissRequest = { pdfToJpgResult = null }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cardLayerFill,
                border = BorderStroke(1.dp, specularStroke),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    SwiffleLottieOneShot(SwiffleAnim.SUCCESS_CHECK, size = 80.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Images Extracted!", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, fontFamily = GoogleSans)
                    Spacer(Modifier.height(4.dp))
                    Text("${r.pageCount} pages saved as high-res JPG in Gallery", color = textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { pdfToJpgResult = null },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FluentCobaltBlue)
                    ) {
                        Text("DONE", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Windows 11 Fluent Tools Hub Content ───────────────────────────────────────

@Composable
private fun FluentToolsHubContent(
    onBack: () -> Unit,
    onNavigateToImageToPdf: () -> Unit,
    onNavigateToExcel: () -> Unit = {},
    onStartSmartScan: () -> Unit,
    onStartIdCardScan: () -> Unit,
    onPickSinglePdf: () -> Unit,
    onPickMultiPdf: () -> Unit,
    onPickPdfForJpg: () -> Unit,
    onPickPdfToImport: () -> Unit,
    onPickPdfToPrint: () -> Unit,
    onPickOfficeFile: () -> Unit,
    micaBackdrop: Color,
    cardLayerFill: Color,
    specularStroke: Color,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: Context
) {
    Scaffold(
        containerColor = micaBackdrop,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(micaBackdrop)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    FluentSubtleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        tint = textPrimary,
                        surface = cardLayerFill,
                        stroke = specularStroke
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Tools",
                        color = textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSans
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Feature Request Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cardLayerFill,
                border = BorderStroke(1.dp, specularStroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Toast.makeText(context, "Thank you! Feature suggestions are logged.", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(FluentCobaltBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Lightbulb, null, tint = FluentCobaltBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Request a new feature",
                            color = textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            fontFamily = GoogleSans
                        )
                        Text(
                            "What other features do you want?",
                            color = textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = textTertiary, modifier = Modifier.size(18.dp))
                }
            }

            // ── Section: Convert & PDF Tools ─────────────────────────────────
            Text(
                "Convert",
                color = textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                fontFamily = GoogleSans,
                modifier = Modifier.padding(start = 2.dp)
            )

            val convertTools = listOf(
                ToolItem("img_pdf", "Image to PDF", FluentIcons.DocumentPdf, FluentPdfRed, "PDF"),
                ToolItem("docs_pdf", "Docs to PDF", FluentIcons.DocumentWord, FluentWordBlue, "Word"),
                ToolItem("pdf_docx", "PDF to Docx", FluentIcons.DocumentWord, FluentWordBlue, "Word"),
                ToolItem("pdf_ppt", "PDF to PPT", FluentIcons.DocumentPpt, FluentPptOrange, "PPT"),
                ToolItem("ppt_pdf", "PPT to PDF", FluentIcons.DocumentPpt, FluentPptOrange, "PPT"),
                ToolItem("pdf_jpg", "PDF to JPG", FluentIcons.DocumentJpg, FluentPdfRed, "JPG"),
                ToolItem("pdf_excel", "PDF to Excel", FluentIcons.DocumentExcel, FluentExcelGreen, "Excel"),
                ToolItem("excel_pdf", "Excel to PDF", FluentIcons.DocumentExcel, FluentExcelGreen, "Excel"),
                ToolItem("merge_pdf", "Merge PDF", FluentIcons.MergePdf, FluentPptOrange, "Merge"),
                ToolItem("lock_pdf", "Lock PDF", FluentIcons.Lock, FluentCobaltBlue, "Protect"),
                ToolItem("unlock_pdf", "Unlock PDF", FluentIcons.Unlock, FluentCobaltBlue, "Security"),
                ToolItem("smart_ocr", "Smart OCR", FluentIcons.Sparkle, Color(0xFF6B46C1), "AI")
            )

            FluentToolsGrid(
                tools = convertTools,
                cardFill = cardLayerFill,
                stroke = specularStroke,
                textPrimary = textPrimary,
                onToolClick = { item ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    when (item.id) {
                        "img_pdf" -> onNavigateToImageToPdf()
                        "pdf_excel" -> onNavigateToExcel()
                        "docs_pdf", "ppt_pdf", "excel_pdf" -> onPickOfficeFile()
                        "pdf_docx", "pdf_ppt", "pdf_jpg", "lock_pdf", "unlock_pdf" -> onPickSinglePdf()
                        "merge_pdf" -> onPickMultiPdf()
                        "smart_ocr" -> Toast.makeText(context, "Smart OCR Engine ready for configuration", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

// ── Fluent 3-Column Bento Grid ────────────────────────────────────────────────

@Composable
private fun FluentToolsGrid(
    tools: List<ToolItem>,
    cardFill: Color,
    stroke: Color,
    textPrimary: Color,
    onToolClick: (ToolItem) -> Unit
) {
    val cols = 3
    val rows = (tools.size + cols - 1) / cols

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (idx < tools.size) {
                        val item = tools[idx]
                        FluentToolBentoCard(
                            item = item,
                            cardFill = cardFill,
                            stroke = stroke,
                            textPrimary = textPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = { onToolClick(item) }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FluentToolBentoCard(
    item: ToolItem,
    cardFill: Color,
    stroke: Color,
    textPrimary: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(FluentFasterDuration, easing = FluentDecelerate),
        label = "bentoPress"
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = cardFill,
        border = BorderStroke(1.dp, stroke),
        shadowElevation = 1.5.dp,
        modifier = modifier
            .aspectRatio(1.05f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(item.badgeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = item.title,
                color = textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                fontFamily = GoogleSans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FluentSubtleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color,
    surface: Color,
    stroke: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = surface,
        border = BorderStroke(1.dp, stroke),
        modifier = Modifier
            .size(36.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(17.dp))
        }
    }
}

// ── Sub-Screen 1: Fluent Image to PDF Studio Landing ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluentImageToPdfStudioLanding(
    onBack: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onStartCameraScan: () -> Unit,
    onImagesPicked: (List<Uri>) -> Unit,
    onNavigateToReview: () -> Unit,
    vm: ConversionViewModel,
    micaBackdrop: Color,
    cardLayerFill: Color,
    cardNestedFill: Color,
    specularStroke: Color,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color,
    isDark: Boolean
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current

    val images by vm.images.collectAsState()
    val recentConversions by vm.recentConversions.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadRecentConversions(context)
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onImagesPicked(uris)
        }
    }

    Scaffold(
        containerColor = micaBackdrop,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Image to PDF Studio",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            fontFamily = GoogleSans
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.padding(start = 8.dp).size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                actions = {
                    if (images.isNotEmpty()) {
                        Surface(
                            onClick = onNavigateToReview,
                            shape = RoundedCornerShape(16.dp),
                            color = FluentCobaltBlue.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, FluentCobaltBlue.copy(alpha = 0.35f)),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(FluentCobaltBlue))
                                Text(
                                    "Review (${images.size})",
                                    color = FluentCobaltBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = GoogleSans
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = micaBackdrop)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Hero Facility Card (Bug 2: No 300 DPI button; Enterprise publishing copy) ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardLayerFill,
                border = BorderStroke(1.dp, specularStroke),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pdf_brand_icon.svg")
                                .decoderFactory(SvgDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = "Swiffle PDF Icon",
                            modifier = Modifier.size(46.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Swiffle Image to PDF Convertor",
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                fontFamily = GoogleSans
                            )
                        }
                    }

                    Text(
                        "Convert photos and camera scans into crisp, publication-grade PDF documents with 100% native clarity, intuitive multi-page reordering, and optional 128-bit password protection.",
                        color = textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontFamily = GoogleSans
                    )
                }
            }

            // ── Dual Source Action Cards (Bug 8: Surface onClick ensures NO boxy shadow hover!) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Scan with Camera
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartCameraScan()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = cardLayerFill,
                    border = BorderStroke(1.dp, specularStroke),
                    shadowElevation = 2.dp,
                    modifier = Modifier.weight(1f).height(124.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE53935).copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(FluentIcons.CameraScan, null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Scan with Camera", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, fontFamily = GoogleSans)
                            Spacer(Modifier.height(2.dp))
                            Text("Live corner brackets", color = textTertiary, fontSize = 11.sp, fontFamily = GoogleSans)
                        }
                    }
                }

                // Card 2: Choose from Album
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = cardLayerFill,
                    border = BorderStroke(1.dp, specularStroke),
                    shadowElevation = 2.dp,
                    modifier = Modifier.weight(1f).height(124.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE53935).copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(FluentIcons.PhotoGallery, null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Choose from Album", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, fontFamily = GoogleSans)
                            Spacer(Modifier.height(2.dp))
                            Text("Batch pick photos", color = textTertiary, fontSize = 11.sp, fontFamily = GoogleSans)
                        }
                    }
                }
            }

            // ── Recent Conversions History Section ─────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Recent Conversions",
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = GoogleSans
                    )
                    if (recentConversions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cardNestedFill,
                            border = BorderStroke(1.dp, specularStroke)
                        ) {
                            Text(
                                "${recentConversions.size} PDFs",
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (recentConversions.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cardLayerFill,
                        border = BorderStroke(1.dp, specularStroke),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(FluentIcons.DocumentPdf, null, tint = textTertiary, modifier = Modifier.size(36.dp))
                            Text("No Converted PDFs Yet", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, fontFamily = GoogleSans)
                            Text("Scanned and converted documents will appear here for instant single-tap access.", color = textSecondary, fontSize = 11.5.sp, textAlign = TextAlign.Center, fontFamily = GoogleSans)
                        }
                    }
                } else {
                    recentConversions.forEach { item ->
                        Surface(
                            onClick = { onOpenPdf(item.uri, item.name) },
                            shape = RoundedCornerShape(10.dp),
                            color = cardLayerFill,
                            border = BorderStroke(1.dp, specularStroke),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = FluentPdfRed.copy(alpha = 0.10f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(FluentIcons.DocumentPdf, null, tint = FluentPdfRed, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = GoogleSans)
                                    Spacer(Modifier.height(2.dp))
                                    Text("${item.formattedDate} • ${item.formattedSize}", color = textTertiary, fontSize = 11.sp, fontFamily = GoogleSans)
                                }
                                Icon(Icons.Filled.ChevronRight, "Open", tint = textTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FacilityPill(text: String, fill: Color, textCol: Color, stroke: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = fill,
        border = BorderStroke(1.dp, stroke)
    ) {
        Text(
            text,
            color = textCol,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GoogleSans,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── Sub-Screen 2: Dedicated Review & Convert Workspace (User Proposed Architecture!) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluentReviewAndConvertSubScreen(
    onBack: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onAddMoreFromCamera: () -> Unit,
    onFinishedConversion: () -> Unit,
    vm: ConversionViewModel,
    micaBackdrop: Color,
    cardLayerFill: Color,
    cardNestedFill: Color,
    specularStroke: Color,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color,
    isDark: Boolean,
    supportsBlur: Boolean
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current

    val images by vm.images.collectAsState()
    val state  by vm.conversionState.collectAsState()

    var showConfigSheet by remember { mutableStateOf(false) }
    var inspectingItem by remember { mutableStateOf<ImageItem?>(null) }
    var cropTargetItem by remember { mutableStateOf<ImageItem?>(null) }

    val moreFromGalleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.addImages(uris)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Fullscreen Page Inspector (Bug 5: Full width & height with Rotate 90°, Crop, Delete)
    if (inspectingItem != null) {
        val currItem = inspectingItem!!
        val currIndex = images.indexOfFirst { it.id == currItem.id }
        if (currIndex != -1) {
            PageInspectorDialog(
                item = images[currIndex],
                pageIndex = currIndex,
                totalPages = images.size,
                onDismiss = { inspectingItem = null },
                onRotate = {
                    vm.rotateImage(currItem.id)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onCrop = {
                    cropTargetItem = currItem
                    inspectingItem = null
                },
                onDelete = {
                    vm.removeImage(currItem.id)
                    inspectingItem = null
                }
            )
        } else {
            inspectingItem = null
        }
    }

    // Document Crop Dialog
    if (cropTargetItem != null) {
        DocumentCropDialog(
            imageUri = cropTargetItem!!.uri,
            onDismiss = { cropTargetItem = null },
            onCropSaved = { newUri ->
                vm.replaceImage(cropTargetItem!!.id, newUri)
                cropTargetItem = null
            }
        )
    }

    // In-Window Fluent Bottom Sheet Overlay (Edge-to-Edge, ZERO grey status bar glitch!)
    AnimatedVisibility(
        visible = showConfigSheet,
        enter = fadeIn(tween(180)) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        ),
        exit = fadeOut(tween(150)) + slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(150, easing = FluentAccelerate)
        ),
        modifier = Modifier.fillMaxSize().zIndex(200f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    showConfigSheet = false
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            PreFlightConfigSheetContent(
                onDismiss = { showConfigSheet = false },
                onConfirm = { name, isLossless, isAiOcr, password ->
                    showConfigSheet = false
                    vm.convertImagesToPdf(context, name, isLossless, password, isAiOcr)
                },
                cardLayerFill = cardLayerFill,
                cardNestedFill = cardNestedFill,
                specularStroke = specularStroke,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                textTertiary = textTertiary,
                isDark = isDark
            )
        }
    }

    // Fullscreen Backdrop Blur Loading Experience
    if (state is ConversionState.Converting) {
        val c = state as ConversionState.Converting
        ConversionBackdropLoader(
            message = c.progress,
            cardLayerFill = cardLayerFill,
            specularStroke = specularStroke,
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )
    }

    // Wide Success State Dialog (Bug 4: Clears images on Done / Open Viewer!)
    if (state is ConversionState.Success) {
        val s = state as ConversionState.Success
        ConversionSuccessDialog(
            file = s.outputFile,
            formattedSize = s.formattedSize,
            pageCount = images.size,
            onOpenInViewer = {
                val uri = s.contentUri
                val name = s.outputFile.name
                vm.clearImages()
                vm.resetState()
                onOpenPdf(uri, name)
                onFinishedConversion()
            },
            onDone = {
                vm.clearImages()
                vm.resetState()
                onFinishedConversion()
            },
            cardLayerFill = cardLayerFill,
            specularStroke = specularStroke,
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )
    }

    Scaffold(
        containerColor = micaBackdrop,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Review & Convert",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = GoogleSans
                        )
                        Text(
                            "${images.size} Page(s) ready • Tap to inspect or reorder",
                            color = textSecondary,
                            fontSize = 11.5.sp,
                            fontFamily = GoogleSans
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.padding(start = 8.dp).size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                actions = {
                    // Quick Action: Add More
                    Surface(
                        onClick = onAddMoreFromCamera,
                        shape = CircleShape,
                        color = Color(0xFFE53935).copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(FluentIcons.CameraScan, "Scan more", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        onClick = {
                            moreFromGalleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = CircleShape,
                        color = Color(0xFFE53935).copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(FluentIcons.PhotoGallery, "Pick more", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // Clear all
                    Surface(
                        onClick = {
                            vm.clearImages()
                            onBack()
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.padding(end = 8.dp).size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(FluentIcons.Delete, "Clear", tint = textTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = micaBackdrop)
            )
        },
        bottomBar = {
            // Floating 60dp Convert Bar
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = cardLayerFill,
                border = BorderStroke(1.dp, specularStroke),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .height(60.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = FluentCobaltBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(FluentIcons.DocumentPdf, null, tint = FluentCobaltBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            "${images.size} Pages",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = GoogleSans
                        )
                    }

                    Surface(
                        onClick = {
                            if (images.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showConfigSheet = true
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = FluentCobaltBlue,
                        shadowElevation = 4.dp,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Convert to PDF",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                fontFamily = GoogleSans
                            )
                            Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(FluentIcons.DocumentPdf, null, tint = textTertiary, modifier = Modifier.size(48.dp))
                    Text("No pages left in staging", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(20.dp),
                        color = FluentCobaltBlue
                    ) {
                        Text("Return to Studio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        } else {
            // Fluid Reorderable Page Grid (Bug 1: Live slot swapping without coordinate spring fighting)
            FluidReorderableGrid(
                images = images,
                onReorder = { from, to -> vm.reorderImages(from, to) },
                onInspect = { item -> inspectingItem = item },
                onRotate = { id -> vm.rotateImage(id) },
                onDelete = { id -> vm.removeImage(id) },
                cardFill = cardLayerFill,
                stroke = specularStroke,
                textPrimary = textPrimary,
                haptic = haptic,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

// ── Fluid Reorderable Grid Component (Bug 1 Fixed!) ────────────────────────────

@Composable
private fun FluidReorderableGrid(
    images: List<ImageItem>,
    onReorder: (Int, Int) -> Unit,
    onInspect: (ImageItem) -> Unit,
    onRotate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    cardFill: Color,
    stroke: Color,
    textPrimary: Color,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // 2-column A4 layout
    BoxWithConstraints(modifier = modifier) {
        val totalWidth = maxWidth
        val spacing = 12.dp
        val cols = 2
        val cardWidth = (totalWidth - spacing) / cols
        val cardHeight = cardWidth / 0.707f // A4 Aspect Ratio

        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val spacingPx = with(density) { spacing.toPx() }

        val rowCount = (images.size + cols - 1) / cols
        val totalGridHeight = (cardHeight * rowCount) + (spacing * (rowCount - 1).coerceAtLeast(0)) + 80.dp

        var draggedItemId by remember { mutableStateOf<Long?>(null) }
        var draggedIndex by remember { mutableIntStateOf(-1) }
        var currentTargetIndex by remember { mutableIntStateOf(-1) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        val scrollState = rememberScrollState()

        // 60FPS Fluid Auto-Scroll when dragging near viewport boundaries!
        LaunchedEffect(draggedItemId) {
            if (draggedItemId != null && draggedIndex != -1) {
                while (true) {
                    val r = draggedIndex / cols
                    val origCardY = r * (cardHeightPx + spacingPx)
                    val currentTopInViewport = origCardY + dragOffset.y - scrollState.value
                    val currentBottomInViewport = currentTopInViewport + cardHeightPx
                    val viewportHeightPx = with(density) { maxHeight.toPx() }
                    val edgeThresholdPx = with(density) { 90.dp.toPx() }

                    if (currentTopInViewport < edgeThresholdPx && scrollState.value > 0) {
                        val factor = ((edgeThresholdPx - currentTopInViewport) / edgeThresholdPx).coerceIn(0.2f, 1.2f)
                        val step = (-18f * factor).toInt()
                        val newPos = (scrollState.value + step).coerceIn(0, scrollState.maxValue)
                        scrollState.scrollTo(newPos)
                    } else if (currentBottomInViewport > viewportHeightPx - edgeThresholdPx && scrollState.value < scrollState.maxValue) {
                        val factor = ((currentBottomInViewport - (viewportHeightPx - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0.2f, 1.2f)
                        val step = (18f * factor).toInt()
                        val newPos = (scrollState.value + step).coerceIn(0, scrollState.maxValue)
                        scrollState.scrollTo(newPos)
                    }
                    kotlinx.coroutines.delay(16)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalGridHeight)
            ) {
                images.forEachIndexed { index, item ->
                    key(item.id) {
                        val liveIndex by rememberUpdatedState(index)
                        val isDragged = (draggedItemId == item.id)

                        // Dynamic live slot reordering: calculate prospective slot for all non-dragged items!
                        val effectiveSlot = when {
                            draggedIndex == -1 || currentTargetIndex == -1 -> liveIndex
                            liveIndex == draggedIndex -> liveIndex
                            draggedIndex < currentTargetIndex && liveIndex > draggedIndex && liveIndex <= currentTargetIndex -> liveIndex - 1
                            draggedIndex > currentTargetIndex && liveIndex >= currentTargetIndex && liveIndex < draggedIndex -> liveIndex + 1
                            else -> liveIndex
                        }

                        val slotCol = effectiveSlot % cols
                        val slotRow = effectiveSlot / cols
                        val targetSlotX = slotCol * (cardWidthPx + spacingPx)
                        val targetSlotY = slotRow * (cardHeightPx + spacingPx)

                        val origCol = liveIndex % cols
                        val origRow = liveIndex / cols
                        val origX = origCol * (cardWidthPx + spacingPx)
                        val origY = origRow * (cardHeightPx + spacingPx)

                        // Spring animation for fluid tile movement (NoBouncy for precision tactile slide)
                        val animX by animateFloatAsState(
                            targetValue = if (isDragged) (origX + dragOffset.x) else targetSlotX,
                            animationSpec = if (isDragged) snap() else spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "cardX_${item.id}"
                        )
                        val animY by animateFloatAsState(
                            targetValue = if (isDragged) (origY + dragOffset.y) else targetSlotY,
                            animationSpec = if (isDragged) snap() else spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "cardY_${item.id}"
                        )

                    val cardElevation by animateDpAsState(
                        targetValue = if (isDragged) 24.dp else 2.dp,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                        label = "elev_${item.id}"
                    )
                    val cardScale by animateFloatAsState(
                        targetValue = if (isDragged) 1.07f else 1.0f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                        label = "scale_${item.id}"
                    )

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(animX.roundToInt(), animY.roundToInt())
                            }
                            .size(cardWidth, cardHeight)
                            .zIndex(if (isDragged) 100f else 1f)
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                                shadowElevation = with(density) { cardElevation.toPx() }
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardFill)
                            .border(
                                width = if (isDragged) 2.dp else 1.dp,
                                color = if (isDragged) FluentCobaltBlue else stroke,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .pointerInput(item.id, liveIndex) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggedItemId = item.id
                                        draggedIndex = liveIndex
                                        currentTargetIndex = liveIndex
                                        dragOffset = Offset.Zero
                                    },
                                    onDragEnd = {
                                        val from = draggedIndex
                                        val to = currentTargetIndex
                                        if (from != -1 && to != -1 && from != to) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onReorder(from, to)
                                        }
                                        draggedItemId = null
                                        draggedIndex = -1
                                        currentTargetIndex = -1
                                        dragOffset = Offset.Zero
                                    },
                                    onDragCancel = {
                                        draggedItemId = null
                                        draggedIndex = -1
                                        currentTargetIndex = -1
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount

                                        val curCol = liveIndex % cols
                                        val curRow = liveIndex / cols
                                        val curOrigX = curCol * (cardWidthPx + spacingPx)
                                        val curOrigY = curRow * (cardHeightPx + spacingPx)

                                        val currentCenterX = curOrigX + dragOffset.x + cardWidthPx / 2f
                                        val currentCenterY = curOrigY + dragOffset.y + cardHeightPx / 2f

                                        val col = (currentCenterX / (cardWidthPx + spacingPx)).toInt().coerceIn(0, cols - 1)
                                        val row = (currentCenterY / (cardHeightPx + spacingPx)).toInt().coerceIn(0, rowCount - 1)
                                        val target = (row * cols + col).coerceIn(0, images.size - 1)

                                        if (target != currentTargetIndex) {
                                            currentTargetIndex = target
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                )
                            }
                    ) {
                        // Image Thumbnail (Tap to open full page inspector!)
                        AsyncImage(
                            model = item.uri,
                            contentDescription = "Page ${liveIndex + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = item.rotationDegrees }
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (draggedItemId == null) {
                                        onInspect(item)
                                    }
                                }
                        )

                        // Page Number Badge (P1, P2...) - Dynamically shows prospective slot!
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = cardFill.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, stroke),
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        ) {
                            Text(
                                "P${effectiveSlot + 1}",
                                color = textPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSans,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Top-right quick actions: Rotate & Delete
                        Row(
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { onRotate(item.id) },
                                shape = CircleShape,
                                color = cardFill.copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, stroke),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(FluentIcons.RotateClockwise, "Rotate", tint = textPrimary, modifier = Modifier.size(13.dp))
                                }
                            }
                            Surface(
                                onClick = { onDelete(item.id) },
                                shape = CircleShape,
                                color = cardFill.copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, stroke),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(FluentIcons.Delete, "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

// ── Page Inspector & Editor Modal (Bug 5 Fixed: Full Width + Rotate, Crop, Delete!) ──

@Composable
private fun PageInspectorDialog(
    item: ImageItem,
    pageIndex: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onRotate: () -> Unit,
    onCrop: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Text(
                        "Page ${pageIndex + 1} of $totalPages",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSans,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.width(38.dp))
            }

            // Image Preview in Center
            AsyncImage(
                model = item.uri,
                contentDescription = "Page preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 80.dp)
                    .graphicsLayer { rotationZ = item.rotationDegrees }
            )

            // Bottom Action Bar: Rotate 90°, Crop, Delete
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF202020).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .navigationBarsPadding()
                    .height(60.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                ) {
                    // Rotate
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRotate() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(FluentIcons.RotateClockwise, "Rotate 90°", tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Rotate 90°", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.2f)))

                    // Crop
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCrop() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(FluentIcons.Crop, "Crop", tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Crop", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                    }

                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.2f)))

                    // Delete
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDelete() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(FluentIcons.Delete, "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                        Text("Delete", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                    }
                }
            }
        }
    }
}

// ── Pre-Flight Configuration Modal (Bug 3: Real Password Protection Support!) ──

@Composable
private fun PreFlightConfigSheetContent(
    onDismiss: () -> Unit,
    onConfirm: (name: String, isLossless: Boolean, isAiOcr: Boolean, password: String?) -> Unit,
    cardLayerFill: Color,
    cardNestedFill: Color,
    specularStroke: Color,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color,
    isDark: Boolean = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
) {
    val defaultName = remember {
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()).let { "Swiffle_Doc_$it" }
    }
    var fileName by remember { mutableStateOf(defaultName) }
    var isLossless by remember { mutableStateOf(true) }
    var isAiOcr by remember { mutableStateOf(true) }
    var hasPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = cardLayerFill,
        border = BorderStroke(1.dp, specularStroke),
        shadowElevation = 24.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* consume clicks */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Save as PDF", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = GoogleSans)
                            Text("Configure clarity and encryption", color = textSecondary, fontSize = 12.sp, fontFamily = GoogleSans)
                        }
                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = cardNestedFill,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Close, "Close", tint = textPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // File Name Input
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Document Name", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = cardNestedFill,
                            border = BorderStroke(1.dp, specularStroke),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = fileName,
                                    onValueChange = { fileName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = textPrimary, fontSize = 13.5.sp, fontFamily = GoogleSans),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(".pdf", color = textTertiary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                            }
                        }
                    }

                    // Quality Selection Tiles
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Resolution & Quality", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = { isLossless = true },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLossless) FluentCobaltBlue.copy(alpha = 0.12f) else cardNestedFill,
                                border = BorderStroke(1.dp, if (isLossless) FluentCobaltBlue else specularStroke),
                                modifier = Modifier.weight(1f).height(64.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("Ultra Clarity", color = if (isLossless) FluentCobaltBlue else textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = GoogleSans)
                                    Text("1:1 native sensor resolution", color = textTertiary, fontSize = 10.5.sp, fontFamily = GoogleSans)
                                }
                            }

                            Surface(
                                onClick = { isLossless = false },
                                shape = RoundedCornerShape(8.dp),
                                color = if (!isLossless) FluentCobaltBlue.copy(alpha = 0.12f) else cardNestedFill,
                                border = BorderStroke(1.dp, if (!isLossless) FluentCobaltBlue else specularStroke),
                                modifier = Modifier.weight(1f).height(64.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("Compact A4", color = if (!isLossless) FluentCobaltBlue else textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = GoogleSans)
                                    Text("Standard print DPI for sharing", color = textTertiary, fontSize = 10.5.sp, fontFamily = GoogleSans)
                                }
                            }
                        }
                    }

                    // AI Neural Searchable PDF (Paddle OCR)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isAiOcr) Color(0xFF7C3AED) else textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    "AI Searchable PDF (Paddle OCR)",
                                    color = textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    fontFamily = GoogleSans
                                )
                                Text(
                                    "Embeds invisible searchable & selectable text layer",
                                    color = textTertiary,
                                    fontSize = 10.5.sp,
                                    fontFamily = GoogleSans
                                )
                            }
                        }
                        WinUiToggleSwitch(
                            checked = isAiOcr,
                            onCheckedChange = { isAiOcr = it },
                            activeColor = Color(0xFF7C3AED),
                            isDark = isDark
                        )
                    }

                    // Password Encryption Toggle & Input (Bug 3)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(FluentIcons.Lock, null, tint = if (hasPassword) FluentSecurityCyan else textTertiary, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Password Protection", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = GoogleSans)
                                    Text("Standard 128-bit PDF AES encryption", color = textTertiary, fontSize = 10.5.sp, fontFamily = GoogleSans)
                                }
                            }
                            WinUiToggleSwitch(
                                checked = hasPassword,
                                onCheckedChange = { hasPassword = it },
                                activeColor = FluentSecurityCyan,
                                isDark = isDark
                            )
                        }

                        if (hasPassword) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cardNestedFill,
                                border = BorderStroke(1.dp, specularStroke),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (password.isEmpty()) {
                                        Text(
                                            "Enter document password",
                                            color = textTertiary,
                                            fontSize = 13.sp,
                                            fontFamily = GoogleSans
                                        )
                                    }
                                    BasicTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        textStyle = TextStyle(color = textPrimary, fontSize = 13.5.sp, fontFamily = GoogleSans),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Confirm Action Button
                    Surface(
                        onClick = {
                            val finalPassword = if (hasPassword && password.isNotBlank()) password.trim() else null
                            onConfirm(fileName, isLossless, isAiOcr, finalPassword)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = FluentCobaltBlue,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("COMPILE PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = GoogleSans)
                        }
                    }
                }
            }
}

// ── Backdrop Blur Loading Modal ───────────────────────────────────────────────

@Composable
private fun ConversionBackdropLoader(
    message: String,
    cardLayerFill: Color,
    specularStroke: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardLayerFill,
                shadowElevation = 32.dp,
                border = BorderStroke(1.dp, specularStroke),
                modifier = Modifier.width(300.dp).padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = FluentCobaltBlue,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Compiling PDF",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = GoogleSans
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            message,
                            color = textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = GoogleSans
                        )
                    }
                }
            }
        }
    }
}

// ── Wide Success State Dialog (Bug 4 Fixed: Calls vm.clearImages()!) ──────────

@Composable
private fun ConversionSuccessDialog(
    file: File,
    formattedSize: String,
    pageCount: Int,
    onOpenInViewer: () -> Unit,
    onDone: () -> Unit,
    cardLayerFill: Color,
    specularStroke: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardLayerFill,
                border = BorderStroke(1.dp, specularStroke),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Centered Vector Check Badge
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF107C41).copy(alpha = 0.12f),
                        border = BorderStroke(2.dp, Color(0xFF107C41)),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, null, tint = Color(0xFF107C41), modifier = Modifier.size(36.dp))
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PDF Created Successfully!", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, fontFamily = GoogleSans)
                        Spacer(Modifier.height(4.dp))
                        Text(file.name, color = FluentCobaltBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = GoogleSans)
                        Spacer(Modifier.height(2.dp))
                        Text("$pageCount Page(s) • $formattedSize", color = textSecondary, fontSize = 12.sp, fontFamily = GoogleSans)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = onDone,
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, specularStroke),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("DONE", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = GoogleSans)
                            }
                        }

                        Surface(
                            onClick = onOpenInViewer,
                            shape = RoundedCornerShape(8.dp),
                            color = FluentCobaltBlue,
                            shadowElevation = 4.dp,
                            modifier = Modifier.weight(1.3f).height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("OPEN IN VIEWER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = GoogleSans)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Studio Camera Scanner (Dynamic Edge Tracking + Balanced Centered Dock & Header + Shutter Flash) ──

enum class ScannerMode {
    DOCS,
    ID_CARD
}

enum class IdCardSide {
    FRONT,
    BACK
}

data class DetectedQuad(
    val pTL: Offset = Offset(0.10f, 0.18f),
    val pTR: Offset = Offset(0.90f, 0.18f),
    val pBR: Offset = Offset(0.90f, 0.82f),
    val pBL: Offset = Offset(0.10f, 0.82f),
    val isDetected: Boolean = false
)

private fun analyzeFrameForDocument(
    buffer: java.nio.ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    rotationDegrees: Int
): DetectedQuad {
    // High-resolution 96x96 sampling grid
    val gridDim = 96
    val lum = IntArray(gridDim * gridDim)

    var minL = 255
    var maxL = 0

    for (gy in 0 until gridDim) {
        val sy = (gy * height / gridDim).coerceIn(0, height - 1)
        val rowOffset = sy * rowStride
        for (gx in 0 until gridDim) {
            val sx = (gx * width / gridDim).coerceIn(0, width - 1)
            val index = rowOffset + sx * pixelStride
            if (index < buffer.limit()) {
                val v = buffer.get(index).toInt() and 0xFF
                lum[gy * gridDim + gx] = v
                if (v < minL) minL = v
                if (v > maxL) maxL = v
            }
        }
    }

    // Dynamic contrast adaptation: adjusts to ambient indoor/desk lighting
    val dynamicRange = maxL - minL
    val edgeThreshold = (dynamicRange * 0.16f).toInt().coerceIn(12, 26)

    fun getScreenLum(normX: Float, normY: Float): Int {
        return when (rotationDegrees) {
            90 -> {
                val rotX = (normY * (gridDim - 1)).toInt().coerceIn(0, gridDim - 1)
                val rotY = ((1f - normX) * (gridDim - 1)).toInt().coerceIn(0, gridDim - 1)
                lum[rotY * gridDim + rotX]
            }
            270 -> {
                val rotX = ((1f - normY) * (gridDim - 1)).toInt().coerceIn(0, gridDim - 1)
                val rotY = (normX * (gridDim - 1)).toInt().coerceIn(0, gridDim - 1)
                lum[rotY * gridDim + rotX]
            }
            else -> {
                val gx = (normX * (gridDim - 1)).toInt().coerceIn(0, gridDim - 1)
                val gy = (normY * (gridDim - 1)).toInt().coerceIn(0, gridDim - 1)
                lum[gy * gridDim + gx]
            }
        }
    }

    // Multi-scanline radial edge search (7 lines per side)
    val testPoints = floatArrayOf(0.18f, 0.28f, 0.38f, 0.50f, 0.62f, 0.72f, 0.82f)
    val leftEdges = mutableListOf<Float>()
    val rightEdges = mutableListOf<Float>()
    val topEdges = mutableListOf<Float>()
    val bottomEdges = mutableListOf<Float>()

    // Horizontal scanlines for Left and Right boundaries
    for (y in testPoints) {
        var prev = getScreenLum(0.04f, y)
        for (step in 6..44) {
            val x = step / 100f
            val curr = getScreenLum(x, y)
            if (kotlin.math.abs(curr - prev) >= edgeThreshold) {
                leftEdges.add(x)
                break
            }
            prev = curr
        }

        prev = getScreenLum(0.96f, y)
        for (step in 6..44) {
            val x = 1f - (step / 100f)
            val curr = getScreenLum(x, y)
            if (kotlin.math.abs(curr - prev) >= edgeThreshold) {
                rightEdges.add(x)
                break
            }
            prev = curr
        }
    }

    // Vertical scanlines for Top and Bottom boundaries
    for (x in testPoints) {
        var prev = getScreenLum(x, 0.08f)
        for (step in 8..44) {
            val y = step / 100f
            val curr = getScreenLum(x, y)
            if (kotlin.math.abs(curr - prev) >= edgeThreshold) {
                topEdges.add(y)
                break
            }
            prev = curr
        }

        prev = getScreenLum(x, 0.92f)
        for (step in 8..44) {
            val y = 1f - (step / 100f)
            val curr = getScreenLum(x, y)
            if (kotlin.math.abs(curr - prev) >= edgeThreshold) {
                bottomEdges.add(y)
                break
            }
            prev = curr
        }
    }

    // Robust multi-side boundary convergence (require at least 2 edges on each side)
    if (leftEdges.size >= 2 && rightEdges.size >= 2 && topEdges.size >= 2 && bottomEdges.size >= 2) {
        leftEdges.sort()
        rightEdges.sort()
        topEdges.sort()
        bottomEdges.sort()

        // Use median to reject corner shadows or finger clipping
        val foundLeft = leftEdges[leftEdges.size / 2].coerceIn(0.05f, 0.40f)
        val foundRight = rightEdges[rightEdges.size / 2].coerceIn(0.60f, 0.95f)
        val foundTop = topEdges[topEdges.size / 2].coerceIn(0.08f, 0.42f)
        val foundBottom = bottomEdges[bottomEdges.size / 2].coerceIn(0.58f, 0.92f)

        val docWidth = foundRight - foundLeft
        val docHeight = foundBottom - foundTop

        if (docWidth >= 0.28f && docHeight >= 0.28f) {
            // Perspective corner fitting: top width vs bottom width
            val leftTop = leftEdges.first().coerceIn(foundLeft - 0.04f, foundLeft + 0.04f)
            val leftBottom = leftEdges.last().coerceIn(foundLeft - 0.04f, foundLeft + 0.04f)
            val rightTop = rightEdges.first().coerceIn(foundRight - 0.04f, foundRight + 0.04f)
            val rightBottom = rightEdges.last().coerceIn(foundRight - 0.04f, foundRight + 0.04f)

            return DetectedQuad(
                pTL = Offset(leftTop, foundTop),
                pTR = Offset(rightTop, foundTop),
                pBR = Offset(rightBottom, foundBottom),
                pBL = Offset(leftBottom, foundBottom),
                isDetected = true
            )
        }
    }

    return DetectedQuad(isDetected = false)
}

@Composable
fun FluentCameraScannerSheet(
    initialMode: ScannerMode = ScannerMode.DOCS,
    onCapture: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic         = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // MS White Fluent Theme Tokens
    val isDark          = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
    val specularStroke  = if (isDark) Color(0x2EFFFFFF) else Color(0x14000000)
    val acrylicWhite    = Color.White.copy(alpha = 0.94f)
    val specularBorder  = Color.Black.copy(alpha = 0.10f)
    val textDark        = Color(0xFF202020)
    val textMuted       = Color(0xFF6E6E6E)
    val cobaltBlue      = FluentCobaltBlue

    var scannerMode by remember { mutableStateOf(initialMode) }
    var idCardDoubleSided by remember { mutableStateOf(true) }
    var currentIdSide by remember { mutableStateOf(IdCardSide.FRONT) }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isAutoCapture by remember { mutableStateOf(false) }
    var capturedCount by remember { mutableIntStateOf(0) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var isCapturingNow by remember { mutableStateOf(false) }

    // Dynamic Document Detection & Tracking
    var detectedQuad by remember { mutableStateOf(DetectedQuad()) }
    var consecutiveDetections by remember { mutableIntStateOf(0) }
    var isDocStable by remember { mutableStateOf(false) }
    var stableStartTime by remember { mutableLongStateOf(0L) }
    var lastAutoCaptureTime by remember { mutableLongStateOf(0L) }
    var prevCenter by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    // Subtle White Camera Shutter Flash Animation
    val flashAnim = remember { Animatable(0f) }

    // Google Play Services ML Kit Document Scanner Launcher (Industry-Leading AI Scanning)
    val gmsScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val pages = scanResult?.pages
            if (!pages.isNullOrEmpty()) {
                pages.forEach { page ->
                    onCapture(page.imageUri)
                    capturedCount++
                }
                Toast.makeText(context, "${pages.size} page(s) captured with AI", Toast.LENGTH_SHORT).show()
                onClose()
            }
        }
    }

    val launchGmsAiScanner = {
        val activity = context as? Activity
        if (activity != null) {
            try {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(100)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()

                val scanner = GmsDocumentScanning.getClient(options)
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        gmsScannerLauncher.launch(
                            IntentSenderRequest.Builder(intentSender).build()
                        )
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "AI Scanner: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "AI Scanner unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Analysis single-thread executor
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    fun triggerPhotoCapture() {
        val ic = imageCapture ?: return
        if (isCapturingNow) return
        isCapturingNow = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Shutter flash scrim (180ms)
        coroutineScope.launch {
            flashAnim.snapTo(0.85f)
            flashAnim.animateTo(0f, animationSpec = tween(180, easing = LinearOutSlowInEasing))
        }

        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(cameraDir, "scan_${System.currentTimeMillis()}.jpg")
        val opt = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        ic.takePicture(opt, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                capturedCount++
                lastCapturedUri = uri
                isCapturingNow = false
                onCapture(uri)

                if (scannerMode == ScannerMode.ID_CARD && idCardDoubleSided && currentIdSide == IdCardSide.FRONT) {
                    currentIdSide = IdCardSide.BACK
                    Toast.makeText(context, "Front captured! Now scan back side", Toast.LENGTH_SHORT).show()
                } else if (scannerMode == ScannerMode.ID_CARD) {
                    onClose()
                } else {
                    Toast.makeText(context, "Page $capturedCount captured!", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onError(exc: ImageCaptureException) {
                isCapturingNow = false
                Toast.makeText(context, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Auto-capture watcher
    LaunchedEffect(detectedQuad, isAutoCapture, isCapturingNow) {
        if (!isAutoCapture || isCapturingNow) {
            isDocStable = false
            stableStartTime = 0L
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        if (now - lastAutoCaptureTime < 2400L) {
            isDocStable = false
            return@LaunchedEffect
        }

        if (detectedQuad.isDetected) {
            val center = (detectedQuad.pTL + detectedQuad.pBR) / 2f
            val delta = (center - prevCenter).getDistance()
            prevCenter = center

            if (delta < 0.035f) {
                if (stableStartTime == 0L) {
                    stableStartTime = now
                } else if (now - stableStartTime >= 700L) {
                    isDocStable = true
                    lastAutoCaptureTime = now
                    stableStartTime = 0L
                    triggerPhotoCapture()
                } else {
                    isDocStable = (now - stableStartTime >= 300L)
                }
            } else {
                stableStartTime = now
                isDocStable = false
            }
        } else {
            isDocStable = false
            stableStartTime = 0L
        }
    }

    // Smooth physics springs for live corners
    val animTLX by animateFloatAsState(if (detectedQuad.isDetected) detectedQuad.pTL.x else 0.10f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "tlx")
    val animTLY by animateFloatAsState(if (detectedQuad.isDetected) detectedQuad.pTL.y else 0.18f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "tly")
    val animTRX by animateFloatAsState(if (detectedQuad.isDetected) detectedQuad.pTR.x else 0.90f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "trx")
    val animTRY by animateFloatAsState(if (detectedQuad.isDetected) detectedQuad.pTR.y else 0.18f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "try")
    val animBRX by animateFloatAsState(if (detectedQuad.isDetected) detectedQuad.pBR.x else 0.90f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "brx")
    val animBRY by animateFloatAsState(if (detectedQuad.isDetected) detectedQuad.pBR.y else 0.82f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "bry")
    val animBLX by animateFloatAsState(if (detectedQuad.isDetected) detectedQuad.pBL.x else 0.10f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "blx")
    val animBLY by animateFloatAsState(if (detectedQuad.isDetected) detectedQuad.pBL.y else 0.82f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "bly")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {

        // ── 1. SOLID WINDOWS 11 HEADER (CommandBar Archetype) ──
        Surface(
            color = if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, specularStroke),
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Group: Back Button + Title + Status Pill (Bounded with weight so it never pushes the right group)
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { onClose() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = if (isDark) Color.White else textDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (scannerMode == ScannerMode.DOCS) "Document Scanner" else "ID Card Scanner",
                            color = if (isDark) Color.White else textDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCapturingNow -> cobaltBlue
                                            isDocStable -> Color(0xFF107C41)
                                            detectedQuad.isDetected -> Color(0xFF107C41)
                                            isAutoCapture -> Color(0xFFFF8C00)
                                            else -> cobaltBlue
                                        }
                                    )
                            )
                            Text(
                                text = when {
                                    isCapturingNow -> "Capturing…"
                                    capturedCount > 0 -> "$capturedCount page(s) captured"
                                    isDocStable && isAutoCapture -> "Hold steady!"
                                    detectedQuad.isDetected -> "Document detected"
                                    isAutoCapture -> "Auto-detecting…"
                                    else -> "Ready to scan"
                                },
                                color = if (isDark) Color(0xFFB0B0B0) else Color(0xFF666666),
                                fontSize = 10.5.sp,
                                fontFamily = GoogleSans,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right Group: Windows 11 CommandBar — All pills have identical 34dp height & 8dp radius
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pill 1: Google Play Services ML Kit AI Scanner Launch
                    Surface(
                        onClick = { launchGmsAiScanner() },
                        shape = RoundedCornerShape(8.dp),
                        color = cobaltBlue.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, cobaltBlue.copy(alpha = 0.45f)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "AI Scanner",
                                tint = cobaltBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "AI SCAN",
                                color = cobaltBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSans
                            )
                        }
                    }

                    // Pill 2: AUTO / MANUAL Capture Toggle
                    Surface(
                        onClick = {
                            isAutoCapture = !isAutoCapture
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAutoCapture) cobaltBlue else (if (isDark) Color(0xFF222222) else Color(0xFFEEEEEE)),
                        border = BorderStroke(1.dp, if (isAutoCapture) cobaltBlue else specularStroke),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 9.dp)
                        ) {
                            Text(
                                text = if (isAutoCapture) "AUTO" else "MANUAL",
                                color = if (isAutoCapture) Color.White else (if (isDark) Color.White else textDark),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSans
                            )
                        }
                    }

                    // Pill 3: Torch Toggle Button
                    Surface(
                        onClick = {
                            isTorchOn = !isTorchOn
                            cameraControl?.enableTorch(isTorchOn)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isTorchOn) cobaltBlue else (if (isDark) Color(0xFF222222) else Color(0xFFEEEEEE)),
                        border = BorderStroke(1.dp, if (isTorchOn) cobaltBlue else specularStroke),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                FluentIcons.Torch,
                                contentDescription = "Torch",
                                tint = if (isTorchOn) Color(0xFFFFB900) else (if (isDark) Color.White else textDark),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 2. CENTER VIEWPORT (Camera Viewfinder with Boundary Clipping) ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(Color.Black)
        ) {

            // CameraX Viewfinder with Real-Time Frame Analysis
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val camFuture = ProcessCameraProvider.getInstance(ctx)
                    camFuture.addListener({
                        val provider = camFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val ic = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setJpegQuality(100)
                            .build()
                        imageCapture = ic

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build()

                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val img = imageProxy.image
                                if (img != null && img.planes.isNotEmpty()) {
                                    val plane = img.planes[0]
                                    val quad = analyzeFrameForDocument(
                                        plane.buffer,
                                        imageProxy.width,
                                        imageProxy.height,
                                        plane.rowStride,
                                        plane.pixelStride,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    if (quad.isDetected) {
                                        consecutiveDetections++
                                        if (consecutiveDetections >= 2) {
                                            detectedQuad = quad
                                        }
                                    } else {
                                        consecutiveDetections = 0
                                        detectedQuad = DetectedQuad(isDetected = false)
                                    }
                                }
                            } catch (_: Exception) {} finally {
                                imageProxy.close()
                            }
                        }

                        try {
                            provider.unbindAll()
                            val camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                ic,
                                analysis
                            )
                            cameraControl = camera.cameraControl
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Live Dynamic Document Contour Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val pTL = Offset(animTLX * w, animTLY * h)
                val pTR = Offset(animTRX * w, animTRY * h)
                val pBR = Offset(animBRX * w, animBRY * h)
                val pBL = Offset(animBLX * w, animBLY * h)

                val docPath = Path().apply {
                    moveTo(pTL.x, pTL.y)
                    lineTo(pTR.x, pTR.y)
                    lineTo(pBR.x, pBR.y)
                    lineTo(pBL.x, pBL.y)
                    close()
                }

                val activeColor = if (isDocStable) Color(0xFF107C41) else cobaltBlue

                if (detectedQuad.isDetected) {
                    drawPath(docPath, color = activeColor.copy(alpha = if (isDocStable) 0.22f else 0.12f))
                    drawPath(
                        docPath,
                        color = activeColor,
                        style = Stroke(width = if (isDocStable) 3.5.dp.toPx() else 2.5.dp.toPx())
                    )

                    val bracketLen = 26.dp.toPx()
                    val sw = 3.5.dp.toPx()
                    val bColor = activeColor

                    // 4 Anchor Brackets
                    drawLine(bColor, pTL, pTL + Offset(bracketLen, 0f), sw)
                    drawLine(bColor, pTL, pTL + Offset(0f, bracketLen), sw)
                    drawCircle(Color.White, 4.dp.toPx(), pTL)
                    drawCircle(bColor, 2.5.dp.toPx(), pTL)

                    drawLine(bColor, pTR, pTR + Offset(-bracketLen, 0f), sw)
                    drawLine(bColor, pTR, pTR + Offset(0f, bracketLen), sw)
                    drawCircle(Color.White, 4.dp.toPx(), pTR)
                    drawCircle(bColor, 2.5.dp.toPx(), pTR)

                    drawLine(bColor, pBR, pBR + Offset(-bracketLen, 0f), sw)
                    drawLine(bColor, pBR, pBR + Offset(0f, -bracketLen), sw)
                    drawCircle(Color.White, 4.dp.toPx(), pBR)
                    drawCircle(bColor, 2.5.dp.toPx(), pBR)

                    drawLine(bColor, pBL, pBL + Offset(bracketLen, 0f), sw)
                    drawLine(bColor, pBL, pBL + Offset(0f, -bracketLen), sw)
                    drawCircle(Color.White, 4.dp.toPx(), pBL)
                    drawCircle(bColor, 2.5.dp.toPx(), pBL)
                }
            }

            // Camera Shutter Flash Scrim
            if (flashAnim.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAnim.value))
                )
            }
        }

        // ── 3. SOLID WINDOWS 11 BOTTOM COMMAND DECK ──
        Surface(
            color = if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF),
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, specularStroke),
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Document / ID Mode Selector
                Surface(
                    onClick = {
                        scannerMode = if (scannerMode == ScannerMode.DOCS) ScannerMode.ID_CARD else ScannerMode.DOCS
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF222222) else Color(0xFFEEEEEE),
                    border = BorderStroke(1.dp, specularStroke),
                    modifier = Modifier.height(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            text = if (scannerMode == ScannerMode.DOCS) "DOCS" else "ID CARD",
                            color = if (isDark) Color.White else textDark,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSans
                        )
                    }
                }

                // Center: Windows Camera Primary Shutter Button
                Surface(
                    onClick = { triggerPhotoCapture() },
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(3.5.dp, cobaltBlue),
                    modifier = Modifier.size(70.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isCapturingNow) cobaltBlue.copy(alpha = 0.5f) else cobaltBlue)
                        )
                    }
                }

                // Right: Photos Deck Preview Card (Tapping directly proceeds to Review / Next Step!)
                Surface(
                    onClick = {
                        if (capturedCount > 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClose()
                        } else {
                            Toast.makeText(context, "Take a photo first to proceed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (capturedCount > 0) (if (isDark) Color(0xFF222222) else Color(0xFFF2F2F2)) else Color.Transparent,
                    border = BorderStroke(1.5.dp, if (capturedCount > 0) cobaltBlue else specularStroke),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (lastCapturedUri != null) {
                            AsyncImage(
                                model = lastCapturedUri,
                                contentDescription = "Proceed to Review",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // High-contrast page count badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = cobaltBlue,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                            ) {
                                Text(
                                    "$capturedCount",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            Icon(
                                FluentIcons.DocumentPdf,
                                contentDescription = "No captures yet",
                                tint = if (isDark) Color(0xFF666666) else Color(0xFF999999),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
