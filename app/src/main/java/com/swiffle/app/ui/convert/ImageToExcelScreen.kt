package com.swiffle.app.ui.convert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.swiffle.app.R
import com.swiffle.app.theme.*
import com.swiffle.app.ui.components.SwiffleAnim
import com.swiffle.app.ui.components.SwiffleLottieOneShot
import kotlin.math.roundToInt

// ── Windows 11 Fluent Colors & Excel Brand Palette ────────────────────────────
private val FluentExcelGreen      = Color(0xFF107C41) // Microsoft Excel Primary Green
private val FluentExcelDarkGreen  = Color(0xFF0B5A2F) // Deep Green
private val FluentExcelLightGreen = Color(0xFF57D38C) // Accent Soft Mint
private val FluentExcelContainer  = Color(0xFF107C41).copy(alpha = 0.12f)

// ── Navigation inside Excel Studio ─────────────────────────────────────────────
private enum class ExcelStudioScreen {
    LANDING,
    REVIEW_STAGING,
    CAMERA_SCAN
}

/**
 * Windows 11 Fluent Design System Master Screen for Image or PDF to Excel.
 * Mimics ImageToPdfScreen with Green Excel brand language, official MS Excel icon,
 * and high-fidelity table extraction capabilities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToExcelScreen(
    onBack: () -> Unit,
    vm: ConversionViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val isDark  = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()

    var currentScreen by remember { mutableStateOf(ExcelStudioScreen.LANDING) }
    var scannerSourceScreen by remember { mutableStateOf(ExcelStudioScreen.LANDING) }
    var viewingExcelUri by remember { mutableStateOf<Uri?>(null) }
    var viewingExcelName by remember { mutableStateOf<String?>(null) }

    // ── Fluent Material Tokens ─────────────────────────────────────────────────
    val micaBackdrop   = if (isDark) Color(0xFF000000) else Color(0xFFF3F3F3)
    val cardLayerFill  = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val cardNestedFill = if (isDark) Color(0xFF161616) else Color(0xFFF8F9FA)
    val specularStroke = if (isDark) Color(0x2EFFFFFF) else Color(0x14000000)
    val textPrimary    = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val textSecondary  = if (isDark) Color(0xFFB5B5B5) else Color(0xFF5D5D5D)
    val textTertiary   = if (isDark) Color(0xFF7A7A7A) else Color(0xFF8A8A8A)

    val conversionState by vm.conversionState.collectAsState()

    // Camera permission launcher
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            currentScreen = ExcelStudioScreen.CAMERA_SCAN
        } else {
            Toast.makeText(context, "Camera permission required for table scanning", Toast.LENGTH_SHORT).show()
        }
    }

    // PDF to Excel direct picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.convertPdfToExcel(context, uri)
        }
    }

    // Android back gesture
    BackHandler(enabled = true) {
        when (currentScreen) {
            ExcelStudioScreen.REVIEW_STAGING -> currentScreen = ExcelStudioScreen.LANDING
            ExcelStudioScreen.CAMERA_SCAN -> {
                currentScreen = if (vm.images.value.isNotEmpty()) {
                    ExcelStudioScreen.REVIEW_STAGING
                } else scannerSourceScreen
            }
            ExcelStudioScreen.LANDING -> onBack()
        }
    }

    // In-App Universal Excel Viewer with closed-loop RLHF learning dialog
    if (viewingExcelUri != null) {
        com.swiffle.app.ui.viewer.UniversalDocumentViewerScreen(
            uri = viewingExcelUri!!,
            fileName = viewingExcelName ?: "Extracted_Workbook.xlsx",
            onBack = { viewingExcelUri = null },
            isNewlyConvertedExcel = true
        )
        return
    }

    // Animated Screen Navigation
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == ExcelStudioScreen.CAMERA_SCAN) {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(250, easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f))
                ) + fadeIn(animationSpec = tween(167)) togetherWith fadeOut(animationSpec = tween(167))
            } else if (initialState == ExcelStudioScreen.CAMERA_SCAN) {
                fadeIn(animationSpec = tween(167)) togetherWith slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(250, easing = CubicBezierEasing(1.0f, 0.0f, 1.0f, 1.0f))
                ) + fadeOut(animationSpec = tween(167))
            } else if (initialState == ExcelStudioScreen.REVIEW_STAGING && targetState == ExcelStudioScreen.LANDING) {
                com.swiffle.app.theme.SwiffleMotion.horizontalBackward()
            } else {
                com.swiffle.app.theme.SwiffleMotion.horizontalForward()
            }
        },
        label = "excelStudioTransition",
        modifier = Modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            ExcelStudioScreen.LANDING -> {
                FluentExcelStudioLanding(
                    onBack = onBack,
                    onStartCameraScan = {
                        scannerSourceScreen = ExcelStudioScreen.LANDING
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            currentScreen = ExcelStudioScreen.CAMERA_SCAN
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onImagesPicked = { uris ->
                        vm.addImages(uris)
                        currentScreen = ExcelStudioScreen.REVIEW_STAGING
                    },
                    onPickPdf = {
                        pdfPickerLauncher.launch("application/pdf")
                    },
                    onNavigateToReview = {
                        currentScreen = ExcelStudioScreen.REVIEW_STAGING
                    },
                    onOpenExcelFile = { uri ->
                        openExcelWorkbook(context, uri)
                    },
                    vm = vm,
                    micaBackdrop = micaBackdrop,
                    cardLayerFill = cardLayerFill,
                    cardNestedFill = cardNestedFill,
                    specularStroke = specularStroke,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    textTertiary = textTertiary
                )
            }

            ExcelStudioScreen.REVIEW_STAGING -> {
                FluentExcelReviewSubScreen(
                    onBack = { currentScreen = ExcelStudioScreen.LANDING },
                    onAddMoreFromCamera = {
                        scannerSourceScreen = ExcelStudioScreen.REVIEW_STAGING
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            currentScreen = ExcelStudioScreen.CAMERA_SCAN
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onFinishedConversion = {
                        currentScreen = ExcelStudioScreen.LANDING
                    },
                    vm = vm,
                    micaBackdrop = micaBackdrop,
                    cardLayerFill = cardLayerFill,
                    specularStroke = specularStroke,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    textTertiary = textTertiary
                )
            }

            ExcelStudioScreen.CAMERA_SCAN -> {
                FluentCameraScannerSheet(
                    initialMode = ScannerMode.DOCS,
                    onCapture = { uri ->
                        vm.addImage(uri)
                    },
                    onClose = {
                        currentScreen = if (vm.images.value.isNotEmpty()) ExcelStudioScreen.REVIEW_STAGING else scannerSourceScreen
                    }
                )
            }
        }
    }

    // ── Global Conversion Progress & Success Modal ─────────────────────────────
    when (val state = conversionState) {
        is ConversionState.Converting -> {
            Dialog(
                onDismissRequest = { /* Modal during active processing */ },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardLayerFill,
                    border = BorderStroke(1.dp, specularStroke),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = FluentExcelGreen,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Extracting Excel Workbook",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            fontFamily = GoogleSans
                        )
                        Text(
                            text = state.progress,
                            color = textSecondary,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = GoogleSans
                        )
                    }
                }
            }
        }

        is ConversionState.Success -> {
            Dialog(
                onDismissRequest = { vm.resetState() },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardLayerFill,
                    border = BorderStroke(1.dp, specularStroke),
                    shadowElevation = 24.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SwiffleLottieOneShot(SwiffleAnim.SUCCESS_CHECK, size = 80.dp)

                        Text(
                            text = "Excel Workbook Created!",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = GoogleSans
                        )

                        Text(
                            text = state.message,
                            color = textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = GoogleSans
                        )

                        Spacer(Modifier.height(4.dp))

                        // Open in Excel Button (Built-in Fluent Excel Viewer with Closed-Loop RLHF Feedback)
                        Button(
                            onClick = {
                                viewingExcelUri = state.contentUri
                                viewingExcelName = state.outputFile.name
                                vm.resetState()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FluentExcelGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_ms_excel),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Open in Excel",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = GoogleSans
                                )
                            }
                        }

                        // Share Workbook Button
                        OutlinedButton(
                            onClick = {
                                shareExcelFile(context, state.contentUri)
                            },
                            border = BorderStroke(1.dp, specularStroke),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Share, null, tint = textPrimary, modifier = Modifier.size(16.dp))
                                Text(
                                    "Share Workbook",
                                    color = textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    fontFamily = GoogleSans
                                )
                            }
                        }

                        TextButton(
                            onClick = { vm.resetState() }
                        ) {
                            Text(
                                "Dismiss",
                                color = textSecondary,
                                fontSize = 13.sp,
                                fontFamily = GoogleSans
                            )
                        }
                    }
                }
            }
        }

        is ConversionState.Error -> {
            Dialog(
                onDismissRequest = { vm.resetState() }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardLayerFill,
                    border = BorderStroke(1.dp, specularStroke),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFD13438).copy(alpha = 0.12f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Warning, null, tint = Color(0xFFD13438), modifier = Modifier.size(26.dp))
                            }
                        }

                        Text(
                            "Extraction Notice",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            fontFamily = GoogleSans
                        )

                        Text(
                            state.message,
                            color = textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = GoogleSans
                        )

                        Button(
                            onClick = { vm.resetState() },
                            colors = ButtonDefaults.buttonColors(containerColor = FluentExcelGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("OK", color = Color.White, fontWeight = FontWeight.SemiBold, fontFamily = GoogleSans)
                        }
                    }
                }
            }
        }

        else -> { /* Idle */ }
    }
}

// ── Landing Sub-Screen: Fluent Excel Studio Landing ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluentExcelStudioLanding(
    onBack: () -> Unit,
    onStartCameraScan: () -> Unit,
    onImagesPicked: (List<Uri>) -> Unit,
    onPickPdf: () -> Unit,
    onNavigateToReview: () -> Unit,
    onOpenExcelFile: (Uri) -> Unit,
    vm: ConversionViewModel,
    micaBackdrop: Color,
    cardLayerFill: Color,
    cardNestedFill: Color,
    specularStroke: Color,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current

    val images by vm.images.collectAsState()
    val recentExcelConversions by vm.recentExcelConversions.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadRecentExcelConversions(context)
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
                            "Image to Excel Studio",
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
                            color = FluentExcelGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, FluentExcelGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(FluentExcelGreen))
                                Text(
                                    "Review (${images.size})",
                                    color = FluentExcelGreen,
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
            // ── Hero Facility Card (Green Excel Branding with Official Desktop Icon & SVG) ──
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
                        // Official Green Excel SVG Graphic
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/excel_brand_icon.svg")
                                .decoderFactory(SvgDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = "Swiffle Excel Icon",
                            modifier = Modifier.size(46.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Swiffle Image / PDF to Excel",
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                fontFamily = GoogleSans
                            )
                        }
                    }

                    Text(
                        "Convert photos, camera scans, and PDF documents into clean, structured Microsoft Excel (.xlsx) workbooks with high-precision cell detection, row-column alignment, and multi-sheet preservation.",
                        color = textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontFamily = GoogleSans
                    )
                }
            }

            // ── Dual Source Action Cards (Green Accents) ───────────────────────
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
                            color = FluentExcelGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(FluentIcons.CameraScan, null, tint = FluentExcelGreen, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Scan with Camera", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, fontFamily = GoogleSans)
                            Spacer(Modifier.height(2.dp))
                            Text("Live table capture", color = textTertiary, fontSize = 11.sp, fontFamily = GoogleSans)
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
                            color = FluentExcelGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(FluentIcons.PhotoGallery, null, tint = FluentExcelGreen, modifier = Modifier.size(20.dp))
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

            // ── Card 3: Import PDF Document to Excel ───────────────────────────
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPickPdf()
                },
                shape = RoundedCornerShape(12.dp),
                color = cardLayerFill,
                border = BorderStroke(1.dp, specularStroke),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = FluentExcelGreen.copy(alpha = 0.12f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(FluentIcons.DocumentPdf, null, tint = FluentExcelGreen, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Import PDF Document",
                            color = textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            fontFamily = GoogleSans
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Extract embedded tables and text directly into .xlsx",
                            color = textTertiary,
                            fontSize = 11.5.sp,
                            fontFamily = GoogleSans
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        "Pick PDF",
                        tint = textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Recent Excel Conversions Section ───────────────────────────────
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
                        "Recent Workbooks",
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = GoogleSans
                    )
                    if (recentExcelConversions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cardNestedFill,
                            border = BorderStroke(1.dp, specularStroke)
                        ) {
                            Text(
                                "${recentExcelConversions.size} Workbooks",
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (recentExcelConversions.isEmpty()) {
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
                            Icon(FluentIcons.DocumentExcel, null, tint = textTertiary, modifier = Modifier.size(36.dp))
                            Text("No Converted Excel Workbooks Yet", color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, fontFamily = GoogleSans)
                            Text("Scanned and converted spreadsheets will appear here for instant single-tap access.", color = textSecondary, fontSize = 11.5.sp, textAlign = TextAlign.Center, fontFamily = GoogleSans)
                        }
                    }
                } else {
                    recentExcelConversions.forEach { item ->
                        Surface(
                            onClick = { onOpenExcelFile(item.uri) },
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
                                // Official MS Excel PNG Icon
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = FluentExcelGreen.copy(alpha = 0.10f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_ms_excel),
                                            contentDescription = "Excel",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        color = textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = GoogleSans
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${item.formattedDate} • ${item.formattedSize}",
                                        color = textTertiary,
                                        fontSize = 11.sp,
                                        fontFamily = GoogleSans
                                    )
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

// ── Sub-Screen 2: Dedicated Review & Convert Workspace for Excel ──────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluentExcelReviewSubScreen(
    onBack: () -> Unit,
    onAddMoreFromCamera: () -> Unit,
    onFinishedConversion: () -> Unit,
    vm: ConversionViewModel,
    micaBackdrop: Color,
    cardLayerFill: Color,
    specularStroke: Color,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current

    val images by vm.images.collectAsState()

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

    // Fullscreen Page Inspector
    if (inspectingItem != null) {
        val currItem = inspectingItem!!
        val currIndex = images.indexOfFirst { it.id == currItem.id }
        if (currIndex != -1) {
            ExcelPageInspectorDialog(
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
                            "${images.size} Sheet/Page(s) staged • Tap to inspect",
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
                    // Quick Action: Add More Camera
                    Surface(
                        onClick = onAddMoreFromCamera,
                        shape = CircleShape,
                        color = FluentExcelGreen.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(FluentIcons.CameraScan, "Scan more", tint = FluentExcelGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // Quick Action: Add More Gallery
                    Surface(
                        onClick = {
                            moreFromGalleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = CircleShape,
                        color = FluentExcelGreen.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(FluentIcons.PhotoGallery, "Pick more", tint = FluentExcelGreen, modifier = Modifier.size(18.dp))
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
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.DeleteOutline, "Clear all", tint = textTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = micaBackdrop)
            )
        },
        bottomBar = {
            // ── Sticky Floating Excel Convert Bar (60dp Green Elevation) ───────
            Surface(
                color = micaBackdrop,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.convertImagesToExcel(context)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = FluentExcelGreen,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_ms_excel),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Convert to Excel (.xlsx)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = GoogleSans
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
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
                    Icon(FluentIcons.DocumentExcel, null, tint = textTertiary, modifier = Modifier.size(48.dp))
                    Text("No pages left in staging", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(20.dp),
                        color = FluentExcelGreen
                    ) {
                        Text("Return to Studio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        } else {
            FluidExcelReorderableGrid(
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

// ── Fluid Reorderable Grid for Excel Studio ────────────────────────────────────

@Composable
private fun FluidExcelReorderableGrid(
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

    BoxWithConstraints(modifier = modifier) {
        val totalWidth = maxWidth
        val spacing = 12.dp
        val cols = 2
        val cardWidth = (totalWidth - spacing) / cols
        val cardHeight = cardWidth / 0.707f // A4 / Document Aspect Ratio

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

                        val animX by animateFloatAsState(
                            targetValue = if (isDragged) (origX + dragOffset.x) else targetSlotX,
                            animationSpec = if (isDragged) snap() else spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "excelCardX_${item.id}"
                        )
                        val animY by animateFloatAsState(
                            targetValue = if (isDragged) (origY + dragOffset.y) else targetSlotY,
                            animationSpec = if (isDragged) snap() else spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "excelCardY_${item.id}"
                        )

                        val cardElevation by animateDpAsState(
                            targetValue = if (isDragged) 24.dp else 2.dp,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                            label = "excelElev_${item.id}"
                        )
                        val cardScale by animateFloatAsState(
                            targetValue = if (isDragged) 1.07f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                            label = "excelScale_${item.id}"
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
                                    color = if (isDragged) FluentExcelGreen else stroke,
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
                            // Image Thumbnail
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

                            // Page / Sheet Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = cardFill.copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, stroke),
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            ) {
                                Text(
                                    "Sheet ${effectiveSlot + 1}",
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

// ── Fullscreen Page Inspector for Excel Studio ────────────────────────────────

@Composable
private fun ExcelPageInspectorDialog(
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
                        "Sheet ${pageIndex + 1} of $totalPages",
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
                contentDescription = "Sheet preview",
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

// ── Helpers: Open & Share Excel Files ──────────────────────────────────────────

private fun openExcelWorkbook(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
            Toast.makeText(context, "No spreadsheet viewer found on device", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareExcelFile(context: Context, uri: Uri) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Excel Workbook"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
