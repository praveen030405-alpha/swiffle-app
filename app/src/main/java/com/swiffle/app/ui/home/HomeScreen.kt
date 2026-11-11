package com.swiffle.app.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swiffle.app.R
import com.swiffle.app.auth.OrigamiAuth
import com.swiffle.app.theme.*
import com.swiffle.app.ui.canvas.PdfCanvasScreen
import com.swiffle.app.ui.canvas.PdfPreloader
import com.swiffle.app.ui.components.GoogleBrandIcon
import com.swiffle.app.ui.components.M3SkeletonDocumentRow
import com.swiffle.app.ui.components.OrigamiAnim
import com.swiffle.app.ui.components.OrigamiLottie
import com.swiffle.app.theme.GoogleSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Document.pdf"
}

fun getFileSize(context: Context, uri: Uri): Long {
    var size: Long = 0
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) {
                    size = cursor.getLong(index)
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
    } else if (uri.scheme == "file" && uri.path != null) {
        try {
            size = java.io.File(uri.path!!).length()
        } catch (_: Exception) {}
    }
    return size
}

const val MAX_RECENT_FILES = 15

fun formatRecentTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffSec = diffMs / 1000
    return when {
        diffSec < 15 -> "Just now"
        diffSec < 60 -> "${diffSec}s ago"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        diffSec < 7 * 86400 -> "${diffSec / 86400}d ago"
        else -> android.text.format.DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            android.text.format.DateUtils.DAY_IN_MILLIS
        ).toString()
    }
}

@androidx.compose.runtime.Immutable
data class RecentFile(val uri: Uri, val name: String, val timestamp: Long, val size: Long)

private val GoogleAvatarBorderBrush = Brush.sweepGradient(
    listOf(
        Color(0xFF4285F4),
        Color(0xFF9B51E0),
        Color(0xFFEA4335),
        Color(0xFFFBBC05),
        Color(0xFF34A853),
        Color(0xFF4285F4)
    )
)

/**
 * Android 16/17 Material 4 Pro Home Screen Architecture:
 * - Fluid Floating Search Island with integrated Brand Crane + Google Account Chip
 * - Living Shelf: Horizontal Carousel of Recent Documents with Dynamic Visual Aspect Cards
 * - Adaptive Morphic FAB Capsule: Shrinks into pebble on fast scroll, expands into action pill when idle
 * - Volumetric Glass Tonal Shelves & Specular Rim Lighting
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    externalPdfUri: Uri? = null,
    onConsumeExternalPdf: () -> Unit = {},
    onConvert: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAiChat: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
    val colorScheme = MaterialTheme.colorScheme

    // Windows 11 Fluent WinUI 3 Design System Tokens (Void Black #000000)
    val bgPrimary = if (isDark) Color(0xFF000000) else Color(0xFFF3F3F3)
    val surfaceIsland = if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF)
    val surfaceCard = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val borderSpecular = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
    val textPrimary = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val textMuted = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)
    val fluentCobalt = Color(0xFF0078D4)

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val tabs = remember { listOf("All Documents", "Starred", "Annotated") }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedDrawerItem by remember { mutableStateOf("all_docs") }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var selectedPdfData by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var isParsing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val swiffleLogoPainter = painterResource(id = R.drawable.ic_swiffle_logo)
    val sharedPref = context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    fun getEffectiveAvatar(): String? {
        val email = sharedPref.getString("google_user_email", null) ?: OrigamiAuth.getCurrentUserEmail(context)
        val isGoogleUser = email != null && sharedPref.getString("user_mode", "") != "guest"

        return if (isGoogleUser) {
            sharedPref.getString("google_user_avatar_url", null)
                ?: sharedPref.getString("google_user_avatar", null)
                ?: OrigamiAuth.getCurrentUserAvatarUrl(context)
        } else {
            val f = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
            if (f.exists()) {
                f.absolutePath
            } else {
                sharedPref.getString("google_user_avatar", null)
                    ?: sharedPref.getString("google_user_avatar_url", null)
            }
        }
    }

    var currentUserEmail by remember { mutableStateOf(sharedPref.getString("google_user_email", null) ?: OrigamiAuth.getCurrentUserEmail(context)) }
    var currentUserAvatarUrl by remember { mutableStateOf(getEffectiveAvatar()) }
    var currentUserName by remember { 
        val email = sharedPref.getString("google_user_email", null) ?: OrigamiAuth.getCurrentUserEmail(context)
        val isGoogleUser = email != null && sharedPref.getString("user_mode", "") != "guest"
        mutableStateOf(
            if (isGoogleUser) {
                sharedPref.getString("google_user_name", null) 
                    ?: OrigamiAuth.getCurrentUserName(context) 
                    ?: "User"
            } else {
                sharedPref.getString("google_user_name", "Guest User") ?: "Guest User"
            }
        ) 
    }

    fun refreshProfile() {
        currentUserAvatarUrl = getEffectiveAvatar()
        currentUserEmail = sharedPref.getString("google_user_email", null) ?: OrigamiAuth.getCurrentUserEmail(context)
        val isGoogleUser = currentUserEmail != null && sharedPref.getString("user_mode", "") != "guest"
        currentUserName = if (isGoogleUser) {
            sharedPref.getString("google_user_name", null)
                ?: OrigamiAuth.getCurrentUserName(context)
                ?: "User"
        } else {
            sharedPref.getString("google_user_name", "Guest User") ?: "Guest User"
        }
    }

    val authState by OrigamiAuth.authState.collectAsState()
    LaunchedEffect(Unit, authState) {
        refreshProfile()
    }

    fun loadRecentFiles(): List<RecentFile> {
        return try {
            val jsonStr = sharedPref.getString("recentFiles", "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            val list = mutableListOf<RecentFile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val timestamp = if (obj.has("timestamp")) obj.optLong("timestamp", System.currentTimeMillis()) else System.currentTimeMillis()
                val size = when {
                    obj.has("size") -> {
                        val s = obj.opt("size")
                        when (s) {
                            is Number -> s.toLong()
                            is String -> s.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                    }
                    else -> 0L
                }
                list.add(RecentFile(Uri.parse(obj.getString("uri")), obj.getString("name"), timestamp, size))
            }
            list.sortedByDescending { it.timestamp }.take(MAX_RECENT_FILES)
        } catch (_: Exception) { emptyList() }
    }

    var showRecentDocs by remember {
        mutableStateOf(sharedPref.getBoolean("pref_show_recent_documents", true))
    }
    var askBeforeDelete by remember {
        mutableStateOf(sharedPref.getBoolean("pref_confirm_file_delete", true))
    }
    var filePendingDelete by remember { mutableStateOf<RecentFile?>(null) }

    var recentFiles by remember {
        mutableStateOf<List<RecentFile>>(loadRecentFiles())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshProfile()
                showRecentDocs = sharedPref.getBoolean("pref_show_recent_documents", true)
                askBeforeDelete = sharedPref.getBoolean("pref_confirm_file_delete", true)
                recentFiles = loadRecentFiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var starredUris by remember {
        mutableStateOf(
            try {
                val set = sharedPref.getStringSet("starred_files", emptySet()) ?: emptySet()
                set.toSet()
            } catch (_: Exception) { emptySet() }
        )
    }

    fun toggleStar(uri: Uri) {
        val s = uri.toString()
        val next = if (starredUris.contains(s)) starredUris - s else starredUris + s
        starredUris = next
        sharedPref.edit().putStringSet("starred_files", next).apply()
    }

    fun deleteRecentFile(file: RecentFile) {
        val newList = recentFiles.filter { it.uri != file.uri }
        recentFiles = newList
        val array = JSONArray()
        newList.take(MAX_RECENT_FILES).forEach {
            val obj = JSONObject()
            obj.put("uri", it.uri.toString())
            obj.put("name", it.name)
            obj.put("timestamp", it.timestamp)
            obj.put("size", it.size)
            array.put(obj)
        }
        sharedPref.edit().putString("recentFiles", array.toString()).apply()
    }

    fun addRecentFile(uri: Uri, name: String) {
        val now = System.currentTimeMillis()
        val existingFile = recentFiles.firstOrNull { it.uri == uri }
        val size = if (existingFile != null && existingFile.size > 0) {
            existingFile.size
        } else {
            val queried = getFileSize(context, uri)
            if (queried > 0) queried else (existingFile?.size ?: 0L)
        }
        val filtered = recentFiles.filter { it.uri != uri }
        val newList = (listOf(RecentFile(uri, name, now, size)) + filtered).take(MAX_RECENT_FILES)
        recentFiles = newList
        val array = JSONArray()
        newList.forEach {
            val obj = JSONObject()
            obj.put("uri", it.uri.toString())
            obj.put("name", it.name)
            obj.put("timestamp", it.timestamp)
            obj.put("size", it.size)
            array.put(obj)
        }
        sharedPref.edit().putString("recentFiles", array.toString()).apply()

        // Mirror to permanent internal vault immediately while URI read permission is active
        Thread {
            try {
                val vaultDir = java.io.File(context.filesDir, "recent_vault")
                if (!vaultDir.exists()) vaultDir.mkdirs()
                val vaultFile = java.io.File(vaultDir, "vault_${uri.hashCode().toUInt()}.pdf")
                if (!vaultFile.exists() || vaultFile.length() == 0L) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val tmp = java.io.File(vaultDir, "${vaultFile.name}.tmp")
                        tmp.outputStream().use { output -> input.copyTo(output) }
                        if (tmp.exists() && tmp.length() > 0) {
                            tmp.renameTo(vaultFile)
                        }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    val coroutineScope = rememberCoroutineScope()

    // Preload up to 10 pages of the top recent document in background even before user taps!
    LaunchedEffect(recentFiles) {
        if (recentFiles.isNotEmpty()) {
            val topDoc = recentFiles.first()
            PdfPreloader.preload(context, topDoc.uri, topDoc.name, 10)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val fileName = getFileName(context, uri)
            PdfPreloader.preload(context, uri, fileName, 10)
            addRecentFile(uri, fileName)
            selectedPdfData = uri to fileName
        }
    }

    LaunchedEffect(externalPdfUri) {
        if (externalPdfUri != null) {
            val fileName = getFileName(context, externalPdfUri)
            PdfPreloader.preload(context, externalPdfUri, fileName, 10)
            addRecentFile(externalPdfUri, fileName)
            selectedPdfData = externalPdfUri to fileName
            onConsumeExternalPdf()
        }
    }

    // Independent scroll states for each tab to guarantee 0 scroll contention & 0 relayout jank
    val allDocsListState = rememberLazyListState()
    val starredListState = rememberLazyListState()
    val annotatedListState = rememberLazyListState()
    val drawerScrollState = rememberScrollState()

    var isPrewarming by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(260)
        isPrewarming = false
    }

    val isScrollingFast by remember {
        derivedStateOf {
            allDocsListState.isScrollInProgress ||
            starredListState.isScrollInProgress ||
            annotatedListState.isScrollInProgress
        }
    }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Android Predictive Back / Back button dismiss contract for navigation drawer
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    // Clear search focus and dismiss keyboard whenever user scrolls or swipes
    LaunchedEffect(isScrollingFast) {
        if (isScrollingFast) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    AnimatedContent(
        targetState = selectedPdfData,
        modifier = Modifier
            .fillMaxSize()
            .background(bgPrimary),
        transitionSpec = {
            if (targetState != null) {
                com.swiffle.app.theme.SwiffleMotion.horizontalForward()
            } else {
                com.swiffle.app.theme.SwiffleMotion.horizontalBackward()
            }
        },
        label = "pdf_canvas_transition"
    ) { activePdf ->
        if (activePdf != null) {
            val ext = activePdf.second.substringAfterLast('.', "").lowercase()
            if (ext in listOf("xlsx", "xls", "csv", "docx", "doc", "md", "markdown", "html", "htm")) {
                com.swiffle.app.ui.viewer.UniversalDocumentViewerScreen(
                    uri = activePdf.first,
                    fileName = activePdf.second,
                    onBack = {
                        selectedPdfData = null
                        onConsumeExternalPdf()
                    }
                )
            } else {
                PdfCanvasScreen(
                    pdfUri = activePdf.first,
                    pdfTitle = activePdf.second,
                    onBack = {
                        selectedPdfData = null
                        onConsumeExternalPdf()
                    }
                )
            }
        } else {
            val drawerWidth = minOf(295.dp, (screenWidthDp - 80).coerceAtLeast(260).dp)

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                scrimColor = Color.Black.copy(alpha = 0.38f),
                drawerContent = {
                    ModalDrawerSheet(
                        drawerShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        drawerContainerColor = if (isDark) Color(0xFF000000) else Color(0xFFF9F9F9),
                        drawerTonalElevation = 0.dp,
                        modifier = Modifier
                            .width(drawerWidth)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        ) {
                            // WinUI 3 Navigation View Header: Monoline Emblem + Title Ramp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = swiffleLogoPainter,
                                    contentDescription = "Swiffle Emblem",
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Swiffle",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Document Studio",
                                        fontFamily = GoogleSans,
                                        fontSize = 11.5.sp,
                                        color = textMuted
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = if (isDark) Color(0x1FFFFFFF) else Color(0x0F000000),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 1.dp
                            )

                            // WinUI 3 Navigation View Scrollable Pane Items
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(drawerScrollState)
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Hub",
                                    fontFamily = GoogleSans,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textMuted,
                                    modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp)
                                )

                                WinUiDrawerItem(
                                    label = "All Documents",
                                    icon = Icons.Outlined.Folder,
                                    isSelected = selectedDrawerItem == "all_docs",
                                    onClick = {
                                        selectedDrawerItem = "all_docs"
                                        selectedTab = 0
                                        coroutineScope.launch { drawerState.close() }
                                    },
                                    badgeText = "${recentFiles.size}",
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    isDark = isDark
                                )

                                WinUiDrawerItem(
                                    label = "Starred",
                                    icon = Icons.Outlined.StarBorder,
                                    isSelected = selectedDrawerItem == "starred",
                                    onClick = {
                                        selectedDrawerItem = "starred"
                                        selectedTab = 1
                                        coroutineScope.launch { drawerState.close() }
                                    },
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    isDark = isDark
                                )

                                WinUiDrawerItem(
                                    label = "Annotated",
                                    icon = Icons.Outlined.EditNote,
                                    isSelected = selectedDrawerItem == "annotated",
                                    onClick = {
                                        selectedDrawerItem = "annotated"
                                        selectedTab = 2
                                        coroutineScope.launch { drawerState.close() }
                                    },
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    isDark = isDark
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = if (isDark) Color(0x1FFFFFFF) else Color(0x0F000000),
                                    thickness = 1.dp
                                )

                                Text(
                                    text = "Tools & Intelligence",
                                    fontFamily = GoogleSans,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textMuted,
                                    modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 4.dp)
                                )

                                WinUiDrawerItem(
                                    label = "Convert & PDF Tools",
                                    icon = Icons.Filled.SwapHoriz,
                                    isSelected = false,
                                    onClick = {
                                        coroutineScope.launch { drawerState.close() }
                                        onConvert()
                                    },
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    isDark = isDark
                                )

                                WinUiDrawerItem(
                                    label = "Swiffle AI Chat",
                                    icon = Icons.Outlined.AutoAwesome,
                                    isSelected = false,
                                    onClick = {
                                        coroutineScope.launch { drawerState.close() }
                                        onOpenAiChat()
                                    },
                                    badgeText = "AI",
                                    badgeColor = if (isDark) Color(0xFF2C273B) else Color(0xFFECE6F6),
                                    badgeTextColor = Color(0xFF9B72CB),
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    isDark = isDark
                                )
                            }

                            // Footer: WinUI 3 Settings Anchor
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = if (isDark) Color(0x1FFFFFFF) else Color(0x0F000000),
                                thickness = 1.dp
                            )

                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                WinUiDrawerItem(
                                    label = "Settings",
                                    icon = Icons.Outlined.Settings,
                                    isSelected = false,
                                    onClick = {
                                        coroutineScope.launch { drawerState.close() }
                                        onOpenSettings()
                                    },
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    isDark = isDark
                                )
                            }
                        }
                    }
                }
            ) {
                Scaffold(
                    containerColor = bgPrimary,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    modifier = Modifier
                        .graphicsLayer { clip = true }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            })
                        },
                    floatingActionButton = {
                        Material4MorphicFab(
                            isCompact = isScrollingFast,
                            onAddPdf = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                pdfPickerLauncher.launch(arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "text/markdown",
                                    "text/html",
                                    "*/*"
                                ))
                            },
                            onConvert = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onConvert()
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 600.dp)
                        ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        M4FloatingSearchIsland(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            isExpanded = isSearchExpanded,
                            onToggleExpand = { isSearchExpanded = !isSearchExpanded },
                            surfaceColor = surfaceIsland,
                            borderColor = borderSpecular,
                            textColor = textPrimary,
                            textMuted = textMuted,
                            currentUserEmail = currentUserEmail,
                            currentUserAvatarUrl = currentUserAvatarUrl,
                            currentUserName = currentUserName,
                            onOpenMenu = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                showOverflowMenu = true
                            },
                            onOpenSettings = onOpenSettings,
                            onLogout = onLogout,
                            onOpenDrawer = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                coroutineScope.launch {
                                    if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        M3ConnectedButtonGroup(
                            tabs = tabs,
                            pagerState = pagerState,
                            onSelectTab = { index ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = androidx.compose.animation.core.spring(
                                            dampingRatio = 0.85f,
                                            stiffness = 500f
                                        )
                                    )
                                }
                            }
                        )

                        // HorizontalPager for seamless swiping transitions between All Documents <-> Starred <-> Annotated
                        // Pre-warms All Docs, Starred, Annotated tabs in memory for instant 120 FPS swiping with 0 cold composition spikes
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { pageIndex ->
                            val pageListState = when (pageIndex) {
                                0 -> allDocsListState
                                1 -> starredListState
                                else -> annotatedListState
                            }
                            val pageFiles = remember(pageIndex, searchQuery, recentFiles, starredUris, showRecentDocs) {
                                if (!showRecentDocs && pageIndex == 0 && searchQuery.isBlank()) {
                                    emptyList()
                                } else {
                                    val base = if (searchQuery.isBlank()) recentFiles else recentFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
                                    when (pageIndex) {
                                        0 -> base
                                        1 -> base.filter { starredUris.contains(it.uri.toString()) }
                                        2 -> emptyList()
                                        else -> base
                                    }
                                }
                            }

                            LazyColumn(
                                state = pageListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 96.dp)
                            ) {

                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when {
                                                searchQuery.isNotBlank() -> "Search Results"
                                                pageIndex == 0 -> "Library Files"
                                                pageIndex == 1 -> "Starred Files"
                                                else -> "Annotated Files"
                                            },
                                            color = textPrimary,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = GoogleSans,
                                            letterSpacing = (-0.3).sp
                                        )
                                        Text(
                                            text = "${pageFiles.size} ${if (pageFiles.size == 1) "file" else "files"}",
                                            color = textMuted,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = GoogleSans
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                }

                                if (isParsing || (isPrewarming && recentFiles.isEmpty())) {
                                    items(4) {
                                        M3SkeletonDocumentRow()
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                } else if (pageFiles.isEmpty()) {
                                    item {
                                        when (pageIndex) {
                                            0 -> M4EmptyLibraryState(
                                                isSearch = searchQuery.isNotBlank(),
                                                query = searchQuery,
                                                onBrowseFiles = {
                                        pdfPickerLauncher.launch(arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/msword",
                                            "text/markdown",
                                            "text/html",
                                            "*/*"
                                        ))
                                    },
                                                textPrimary = textPrimary,
                                                textMuted = textMuted,
                                                borderColor = borderSpecular
                                            )
                                            1 -> M4EmptyTabState(
                                                icon = Icons.Filled.Star,
                                                iconTint = Color(0xFFFBBC05),
                                                title = "No Starred Documents",
                                                description = "Tap the star icon on any document in your library to access it quickly here.",
                                                textPrimary = textPrimary,
                                                textMuted = textMuted,
                                                borderColor = borderSpecular
                                            )
                                            else -> M4EmptyTabState(
                                                icon = Icons.Outlined.EditNote,
                                                iconTint = GoogleBlue,
                                                title = "No Annotated Documents",
                                                description = "Highlights, pen strokes, and bookmarks added inside the PDF reader will appear here.",
                                                textPrimary = textPrimary,
                                                textMuted = textMuted,
                                                borderColor = borderSpecular
                                            )
                                        }
                                    }
                                } else {
                                    items(pageFiles, key = { it.uri.toString() }) { file ->
                                        val isStarred = starredUris.contains(file.uri.toString())
                                        M4DocumentRow(
                                            file = file,
                                            onClick = {
                                                addRecentFile(file.uri, file.name)
                                                PdfPreloader.preload(context, file.uri, file.name, 10)
                                                selectedPdfData = file.uri to file.name
                                            },
                                            onDelete = {
                                                if (askBeforeDelete) {
                                                    filePendingDelete = file
                                                } else {
                                                    deleteRecentFile(file)
                                                }
                                            },
                                            cardBg = surfaceCard,
                                            borderColor = borderSpecular,
                                            textPrimary = textPrimary,
                                            textMuted = textMuted,
                                            isStarred = isStarred,
                                            onToggleStar = { toggleStar(file.uri) }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Windows 11 Fluent ContentDialog for File Deletion
                    if (filePendingDelete != null) {
                        val target = filePendingDelete!!
                        AlertDialog(
                            onDismissRequest = { filePendingDelete = null },
                            shape = RoundedCornerShape(8.dp),
                            containerColor = if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF),
                            titleContentColor = textPrimary,
                            textContentColor = textMuted,
                            title = {
                                Text(
                                    text = "Delete Document?",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = textPrimary
                                )
                            },
                            text = {
                                Text(
                                    text = "Are you sure you want to remove \"${target.name}\" from your library?",
                                    fontFamily = GoogleSans,
                                    fontSize = 14.sp,
                                    color = textMuted
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        deleteRecentFile(target)
                                        filePendingDelete = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("Delete", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { filePendingDelete = null },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x1F000000))
                                ) {
                                    Text("Cancel", fontFamily = GoogleSans, color = textPrimary)
                                }
                            }
                        )
                    }
                }
            }

            }
        }
    }
}

/**
 * Android 16/17 Floating Dynamic Search Island
 * Pill-shaped glass container holding the Origami Crest, responsive search input, and Google user chip
 */
@Composable
fun M4FloatingSearchIsland(
    query: String,
    onQueryChange: (String) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    textMuted: Color,
    currentUserEmail: String?,
    currentUserAvatarUrl: String?,
    currentUserName: String? = null,
    onOpenMenu: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchFocused by remember { mutableStateOf(false) }
    var isProfileMenuExpanded by remember { mutableStateOf(false) }

    // Gemini-style thin radiant multi-color circle border
    val geminiGradient = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFF4285F4), // Google Blue
                Color(0xFF9B51E0), // Gemini Violet
                Color(0xFFEA4335), // Google Red
                Color(0xFFFBBC05), // Google Yellow
                Color(0xFF34A853), // Google Green
                Color(0xFF4285F4)  // Close sweep
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = if (isSearchFocused) 4.dp else 2.dp,
                shape = RoundedCornerShape(23.dp),
                spotColor = if (isSearchFocused) Color(0xFF0078D4).copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(23.dp))
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = if (isSearchFocused) Color(0xFF0078D4) else borderColor,
                shape = RoundedCornerShape(23.dp)
            )
            .height(46.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // M3 Navigation Drawer Hamburger Icon (48x48dp accessible touch target)
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onOpenDrawer()
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Rounded.Menu,
                    contentDescription = "Open Navigation Drawer",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Expandable or inline search input with zero attention when unfocused
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isSearchFocused = it.isFocused },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    fontFamily = GoogleSans,
                    fontSize = 15.sp
                ),
                singleLine = true,
                cursorBrush = if (isSearchFocused) SolidColor(MaterialTheme.colorScheme.primary) else SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search PDF documents...",
                                color = textMuted.copy(alpha = 0.75f),
                                fontSize = 15.sp,
                                fontFamily = GoogleSans,
                                letterSpacing = 0.1.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.Clear,
                        contentDescription = "Clear",
                        tint = textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Google / Profile Avatar with Gemini-Style Radiant Aurora Circle Border + M3 Overflow Menu
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(
                            width = 1.8.dp,
                            brush = geminiGradient,
                            shape = CircleShape
                        )
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            isProfileMenuExpanded = true
                            onOpenMenu()
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentUserAvatarUrl.isNullOrBlank()) {
                        val avatarModel: Any = if (!currentUserAvatarUrl.startsWith("http")) {
                            java.io.File(currentUserAvatarUrl)
                        } else {
                            currentUserAvatarUrl
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile Photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val initial = currentUserName?.firstOrNull()?.uppercase()
                            ?: currentUserEmail?.firstOrNull()?.uppercase()
                            ?: "U"
                        Text(
                            text = initial,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSans
                        )
                    }
                }

                // Windows 11 Fluent Profile MenuFlyout
                DropdownMenu(
                    expanded = isProfileMenuExpanded,
                    onDismissRequest = { isProfileMenuExpanded = false },
                    shape = RoundedCornerShape(8.dp),
                    containerColor = surfaceColor,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
                ) {
                    // User Info Header Item
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = currentUserName ?: currentUserEmail ?: "Guest User",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentUserEmail ?: "Local Workspace",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        enabled = false,
                        colors = MenuDefaults.itemColors(
                            disabledTextColor = textColor,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        onClick = {}
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Settings",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                color = textColor
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        onClick = {
                            isProfileMenuExpanded = false
                            onOpenSettings()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sign Out",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        onClick = {
                            isProfileMenuExpanded = false
                            onLogout()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Stock Google Material 3 / Material 4 Pro Filter Pills
 */
/**
 * Official Material 3 Expressive Connected Button Group
 * Directly implements Sites 22-26 & Hero Video Frame 07s:
 * - Fused single-container squircle architecture (20dp outer radius, M3 Expressive Large Corner)
 * - Shared 1dp interior dividers between unselected segments
 * - Active pill segment morphs smoothly with secondaryContainer & onSecondaryContainer
 * - Standardized 48dp height (Medium size tier) with 48x48dp accessible hit targets
 */
@Composable
fun M3ConnectedButtonGroup(
    tabs: List<String>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
    val containerBg = if (isDark) Color(0xFF0C0C0C) else Color(0xFFEBEBEB)
    val selectedBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val selectedText = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0078D4)
    val unselectedText = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)
    val outerBorder = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(42.dp),
        shape = RoundedCornerShape(21.dp), // Windows 11 Fluent Segmented Pill
        color = containerBg,
        border = BorderStroke(1.dp, outerBorder)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        ) {
            val tabCount = tabs.size
            val tabWidth = maxWidth / tabCount
            val tabHeight = maxHeight
            val density = LocalDensity.current
            val tabWidthPx = with(density) { tabWidth.toPx() }

            // Seamless Moving Pill: Physically glides across tabs in real-time on GPU render layer
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .height(tabHeight)
                    .graphicsLayer {
                        val offset = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (tabCount - 1).toFloat())
                        translationX = tabWidthPx * offset
                    }
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.15f))
                    .clip(RoundedCornerShape(18.dp))
                    .background(selectedBg)
                    .border(1.dp, if (isDark) Color(0x28FFFFFF) else Color(0x10000000), RoundedCornerShape(18.dp))
            )

            // Tab Text Items
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = pagerState.currentPage == index

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(21.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectTab(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) selectedText else unselectedText,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.5.sp,
                            letterSpacing = (-0.2).sp,
                            fontFamily = GoogleSans,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Android 16/17 "Living Shelf" — Horizon Carousel with Dynamic Page Aspect Cards
 */
@Composable
fun M4LivingShelf(
    recentFiles: List<RecentFile>,
    onOpenPdf: (RecentFile) -> Unit,
    textColor: Color,
    textMuted: Color,
    cardBg: Color,
    borderColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Recent Shelf",
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSans,
                    letterSpacing = (-0.3).sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(GoogleGreen)
                )
            }
            Text(
                text = "Swipe to explore",
                color = textMuted,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = GoogleSans
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(recentFiles, key = { "shelf_${it.uri}" }) { file ->
                M4AspectDocumentCard(
                    file = file,
                    onClick = {
                        onOpenPdf(file)
                    },
                    cardBg = cardBg,
                    borderColor = borderColor,
                    textColor = textColor,
                    textMuted = textMuted
                )
            }
        }
    }
}

/**
 * Aspect Ratio Document Card with authentic paper crease & tactile spring press
 */
@Composable
fun M4AspectDocumentCard(
    file: RecentFile,
    onClick: () -> Unit,
    cardBg: Color,
    borderColor: Color,
    textColor: Color,
    textMuted: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "card_scale"
    )

    Column(
        modifier = Modifier
            .width(136.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Windows 11 Fluent Document Aspect Card (8px Corner)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(8.dp),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                )
                .clip(RoundedCornerShape(8.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Real PDF Document Cover Thumbnail
            com.swiffle.app.ui.components.PdfThumbnailImage(
                uri = file.uri,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 8.dp
            )

            // Top-right corner page-curl badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "READY",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontFamily = GoogleSans
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title & metadata
        Text(
            text = file.name,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = GoogleSans
        )
        Text(
            text = formatRecentTime(file.timestamp),
            color = textMuted,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = GoogleSans
        )
    }
}

/**
 * Material 4 Pro Document Feed Row
 */
@Composable
fun M4DocumentRow(
    file: RecentFile,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textMuted: Color,
    isStarred: Boolean = false,
    onToggleStar: () -> Unit = {}
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val dateStr = remember(file.timestamp) {
        formatRecentTime(file.timestamp)
    }
    val sizeStr = remember(file.size) {
        android.text.format.Formatter.formatShortFileSize(context, file.size)
    }

    val isDarkRow = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Real PDF Cover Thumbnail (Windows 11 Fluent 4px progressive corner)
        Box(
            modifier = Modifier
                .size(38.dp, 50.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            com.swiffle.app.ui.components.PdfThumbnailImage(
                uri = file.uri,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 4.dp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = GoogleSans
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (file.size > 0) "$dateStr • $sizeStr" else dateStr,
                color = textMuted,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSans
            )
        }

        // Quick Star Action
        IconButton(onClick = onToggleStar) {
            Icon(
                imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isStarred) "Starred" else "Star",
                tint = if (isStarred) Color(0xFFFBBC05) else textMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        // More Options Dropdown
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Document Options",
                    tint = textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(8.dp),
                containerColor = if (isDarkRow) Color(0xFF141414) else Color(0xFFFFFFFF),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, if (isDarkRow) Color(0x33FFFFFF) else Color(0x18000000)),
                modifier = Modifier.widthIn(min = 180.dp, max = 280.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Open Document", fontFamily = GoogleSans, color = textPrimary, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { Icon(Icons.Filled.OpenInNew, null, tint = GoogleBlue, modifier = Modifier.size(24.dp)) },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share PDF", fontFamily = GoogleSans, color = textPrimary, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { Icon(Icons.Filled.Share, null, tint = GoogleGreen, modifier = Modifier.size(24.dp)) },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = {
                        showMenu = false
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, file.uri)
                            clipData = android.content.ClipData.newRawUri("PDF", file.uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF"))
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
                DropdownMenuItem(
                    text = { Text("Remove from History", fontFamily = GoogleSans, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp)) },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Official Material 3 Expressive Speed-Dial FAB Cascade
 * Directly implements Hero Expressive Video Frame 07s:
 * - Asymmetrical vertical squircle cascade (Select, Scan, Table Matrix, Import)
 * - Kinetic spring rotation on primary anchor (+ morphing into X)
 * - Adaptive Extended FAB squircle collapse during scroll
 * - M3 tonal color mapping with 48x48dp interactive hit areas
 */
@Composable
fun Material4MorphicFab(
    isCompact: Boolean,
    onAddPdf: () -> Unit,
    onConvert: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Spring rotation for anchor button (+ to X)
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 700f),
        label = "speed_dial_rotation"
    )

    val fabInteraction = remember { MutableInteractionSource() }
    val isPressed by fabInteraction.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "fab_scale"
    )

    Column(
        modifier = Modifier.padding(bottom = 24.dp, end = 16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Vertical Cascading Squircle Action Pills (Frame 07s)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f),
                targetOffsetY = { it / 2 }
            )
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isDarkFab = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
                val fabItemBg = if (isDarkFab) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                val fabItemBorder = if (isDarkFab) Color(0x33FFFFFF) else Color(0x18000000)
                val fabItemText = if (isDarkFab) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)

                // Item 1: On-Device Camera Scanner
                Surface(
                    onClick = {
                        isExpanded = false
                        onConvert()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = fabItemBg,
                    border = BorderStroke(1.dp, fabItemBorder),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Scan Document",
                            tint = Color(0xFF0078D4),
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scan Document",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            color = fabItemText
                        )
                    }
                }

                // Item 2: Table & Excel Extractor
                Surface(
                    onClick = {
                        isExpanded = false
                        onConvert()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = fabItemBg,
                    border = BorderStroke(1.dp, fabItemBorder),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.TableChart,
                            contentDescription = "Extract Tables",
                            tint = Color(0xFF107C41),
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Extract Tables",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            color = fabItemText
                        )
                    }
                }

                // Item 3: File System Storage
                Surface(
                    onClick = {
                        isExpanded = false
                        onAddPdf()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = fabItemBg,
                    border = BorderStroke(1.dp, fabItemBorder),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = "Browse Storage",
                            tint = Color(0xFF0078D4),
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Browse Storage",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            color = fabItemText
                        )
                    }
                }
            }
        }

        // Windows 11 Fluent Anchor FAB - New Document Pill (LightBlue background)
        val isDarkFab = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
        val pillBgColor = if (isDarkFab) {
            if (isExpanded) Color(0xFF1E3A5F) else Color(0xFF163254)
        } else {
            if (isExpanded) Color(0xFFBCE0FD) else Color(0xFFD3E3FD)
        }
        val pillContentColor = if (isDarkFab) Color(0xFFD3E3FD) else Color(0xFF004A77)
        val pillBorder = if (isDarkFab) Color(0x33FFFFFF) else Color(0xFFBFD8F8)

        Surface(
            onClick = {
                if (isCompact && !isExpanded) {
                    onAddPdf()
                } else {
                    isExpanded = !isExpanded
                }
            },
            interactionSource = fabInteraction,
            shape = RoundedCornerShape(24.dp),
            color = pillBgColor,
            contentColor = pillContentColor,
            shadowElevation = if (isPressed) 6.dp else 3.dp,
            border = BorderStroke(1.dp, pillBorder),
            modifier = Modifier
                .height(48.dp)
                .graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = if (isCompact && !isExpanded) 14.dp else 18.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (isExpanded) "Close Actions" else "Action Menu",
                    tint = pillContentColor,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = rotationAngle }
                )
                AnimatedVisibility(
                    visible = !isCompact && !isExpanded,
                    enter = expandHorizontally(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkHorizontally(animationSpec = tween(180)) + fadeOut(animationSpec = tween(150))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "New Document",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSans,
                            color = pillContentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty Library State with interactive Lottie and dashed rim
 */
@Composable
fun M4EmptyLibraryState(
    isSearch: Boolean,
    query: String,
    onBrowseFiles: () -> Unit,
    textPrimary: Color,
    textMuted: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (com.swiffle.app.theme.ThemeManager.isDarkThemeActive()) Color(0xFF0C0C0C) else Color(0xFFFFFFFF))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // WinUI 3 36dp Monoline Icon Badge
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0078D4).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearch) Icons.Rounded.Search else Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = Color(0xFF0078D4),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isSearch) "No documents matching \"$query\"" else "Your Library is Clear",
                color = textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSans,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSearch) "Try searching for a different keyword or file name." else "Import a PDF document to begin reading with native acceleration.",
                color = textMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                fontFamily = GoogleSans
            )
            if (!isSearch) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onBrowseFiles,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0078D4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Browse Documents",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSans
                    )
                }
            }
        }
    }
}

/**
 * Empty tab placeholder for Starred and Annotated tabs
 */
@Composable
fun M4EmptyTabState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    textPrimary: Color,
    textMuted: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (com.swiffle.app.theme.ThemeManager.isDarkThemeActive()) Color(0xFF0C0C0C) else Color(0xFFFFFFFF))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSans,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = textMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                fontFamily = GoogleSans
            )
        }
    }
}

// ── Windows 11 WinUI 3 Navigation View Item ──────────────────────────────────
@Composable
private fun WinUiDrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    badgeText: String? = null,
    badgeColor: Color? = null,
    badgeTextColor: Color? = null,
    textPrimary: Color,
    textMuted: Color,
    isDark: Boolean
) {
    val cobaltBlue = Color(0xFF0078D4)
    val containerBg = when {
        isSelected -> if (isDark) Color(0x1FFFFFFF) else Color(0x0E000000)
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp), // WinUI 3 4px progressive corner rounding
        color = containerBg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .height(40.dp) // WinUI 3 standard 40epx item height
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // WinUI 3 Selection Indicator (Left 3dp accent bar)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isSelected) cobaltBlue else Color.Transparent)
            )

            Spacer(Modifier.width(10.dp))

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) cobaltBlue else textMuted,
                modifier = Modifier.size(19.dp)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = GoogleSans,
                color = if (isSelected) textPrimary else textMuted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor ?: (if (isDark) Color(0x22FFFFFF) else Color(0x0C000000)),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSans,
                        color = badgeTextColor ?: (if (isDark) Color(0xFFDDDDDD) else Color(0xFF555555)),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
