package com.swiffle.app.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swiffle.app.R
import com.swiffle.app.auth.OrigamiAuth
import com.swiffle.app.theme.ClaudeSans
import com.swiffle.app.theme.ClaudeSerif
import com.swiffle.app.theme.SwiffleMotion
import com.swiffle.app.theme.ThemeManager
import com.swiffle.app.ui.canvas.PdfCanvasScreen
import com.swiffle.app.ui.canvas.PdfPreloader
import com.swiffle.app.ui.components.DarkVoidAmbientLightPulse
import com.swiffle.app.ui.viewer.UniversalDocumentViewerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val MAX_RECENT_FILES = 15

@Immutable
data class RecentFile(
    val uri: Uri,
    val name: String,
    val timestamp: Long,
    val size: Long
)

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
    var size = 0L
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
            size = File(uri.path!!).length()
        } catch (_: Exception) {}
    }
    return size
}

fun formatRecentTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffSec = ((now - timestamp) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 15 -> "Just now"
        diffSec < 60 -> "${diffSec}s ago"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        diffSec < 86400 -> "${diffSec / 3600}h ago"
        diffSec < 7 * 86400 -> "${diffSec / 86400}d ago"
        else -> DateUtils.getRelativeTimeSpanString(
            timestamp,
            now,
            DateUtils.DAY_IN_MILLIS
        ).toString()
    }
}

private val SUPPORTED_DOC_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "text/markdown",
    "text/html",
    "*/*"
)

/**
 * Swiffle Home Screen & Navigation Drawer
 * Unified Dark Void + Obsidian Glassmorphic Editorial Design System matching Onboarding.
 */
@Composable
fun HomeScreen(
    externalPdfUri: Uri? = null,
    onConsumeExternalPdf: () -> Unit = {},
    onConvert: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAiChat: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val isDark = ThemeManager.isDarkThemeActive()
    val tokens = rememberHomeTokens(isDark)

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val swiffleLogoPainter = painterResource(id = R.drawable.ic_swiffle_logo)
    val sharedPref = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val tabs = remember { listOf("All Documents", "Starred", "Annotated") }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDrawerItem by remember { mutableStateOf("all_docs") }

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
        selectedDrawerItem = when (pagerState.currentPage) {
            0 -> "all_docs"
            1 -> "starred"
            else -> "annotated"
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedPdfData by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    fun getEffectiveAvatar(): String? {
        val email = sharedPref.getString("google_user_email", null) ?: OrigamiAuth.getCurrentUserEmail(context)
        val isGoogleUser = email != null && sharedPref.getString("user_mode", "") != "guest"
        return if (isGoogleUser) {
            sharedPref.getString("google_user_avatar_url", null)
                ?: sharedPref.getString("google_user_avatar", null)
                ?: OrigamiAuth.getCurrentUserAvatarUrl(context)
        } else {
            val f = File(context.filesDir, "custom_profile_avatar.jpg")
            if (f.exists()) f.absolutePath
            else sharedPref.getString("google_user_avatar", null)
                ?: sharedPref.getString("google_user_avatar_url", null)
        }
    }

    var currentUserEmail by remember {
        mutableStateOf(sharedPref.getString("google_user_email", null) ?: OrigamiAuth.getCurrentUserEmail(context))
    }
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
                val size = if (obj.has("size")) {
                    when (val s = obj.opt("size")) {
                        is Number -> s.toLong()
                        is String -> s.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                } else 0L
                list.add(RecentFile(Uri.parse(obj.getString("uri")), obj.getString("name"), timestamp, size))
            }
            list.sortedByDescending { it.timestamp }.take(MAX_RECENT_FILES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    var showRecentDocs by remember {
        mutableStateOf(sharedPref.getBoolean("pref_show_recent_documents", true))
    }
    var askBeforeDelete by remember {
        mutableStateOf(sharedPref.getBoolean("pref_confirm_file_delete", true))
    }
    var filePendingDelete by remember { mutableStateOf<RecentFile?>(null) }
    var recentFiles by remember { mutableStateOf(loadRecentFiles()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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
                (sharedPref.getStringSet("starred_files", emptySet()) ?: emptySet()).toSet()
            } catch (_: Exception) {
                emptySet()
            }
        )
    }

    fun toggleStar(uri: Uri) {
        val s = uri.toString()
        val next = if (starredUris.contains(s)) starredUris - s else starredUris + s
        starredUris = next
        sharedPref.edit().putStringSet("starred_files", next).apply()
    }

    fun persistRecentList(list: List<RecentFile>) {
        val array = JSONArray()
        list.take(MAX_RECENT_FILES).forEach {
            array.put(
                JSONObject().apply {
                    put("uri", it.uri.toString())
                    put("name", it.name)
                    put("timestamp", it.timestamp)
                    put("size", it.size)
                }
            )
        }
        sharedPref.edit().putString("recentFiles", array.toString()).apply()
    }

    fun deleteRecentFile(file: RecentFile) {
        val newList = recentFiles.filter { it.uri != file.uri }
        recentFiles = newList
        persistRecentList(newList)
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
        persistRecentList(newList)

        Thread {
            try {
                val vaultDir = File(context.filesDir, "recent_vault")
                if (!vaultDir.exists()) vaultDir.mkdirs()
                val vaultFile = File(vaultDir, "vault_${uri.hashCode().toUInt()}.pdf")
                if (!vaultFile.exists() || vaultFile.length() == 0L) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val tmp = File(vaultDir, "${vaultFile.name}.tmp")
                        tmp.outputStream().use { output -> input.copyTo(output) }
                        if (tmp.exists() && tmp.length() > 0) {
                            tmp.renameTo(vaultFile)
                        }
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

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
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
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

    val allDocsListState = rememberLazyListState()
    val starredListState = rememberLazyListState()
    val annotatedListState = rememberLazyListState()

    var isPrewarming by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(220)
        isPrewarming = false
    }

    val isScrollingFast by remember {
        derivedStateOf {
            allDocsListState.isScrollInProgress ||
                starredListState.isScrollInProgress ||
                annotatedListState.isScrollInProgress
        }
    }

    LaunchedEffect(isScrollingFast) {
        if (isScrollingFast) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val drawerWidth = minOf(304.dp, (screenWidthDp - 72).coerceAtLeast(264).dp)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    AnimatedContent(
        targetState = selectedPdfData,
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bgVoid),
        transitionSpec = {
            if (targetState != null) {
                SwiffleMotion.horizontalForward()
            } else {
                SwiffleMotion.horizontalBackward()
            }
        },
        label = "pdf_canvas_transition"
    ) { activePdf ->
        if (activePdf != null) {
            val ext = activePdf.second.substringAfterLast('.', "").lowercase()
            if (ext in listOf("xlsx", "xls", "csv", "docx", "doc", "md", "markdown", "html", "htm")) {
                UniversalDocumentViewerScreen(
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
            val starredCount = remember(recentFiles, starredUris) {
                recentFiles.count { starredUris.contains(it.uri.toString()) }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                scrimColor = Color.Black.copy(alpha = if (tokens.isDark) 0.65f else 0.36f),
                drawerContent = {
                    ObsidianNavigationDrawerContent(
                        drawerWidth = drawerWidth,
                        swiffleLogoPainter = swiffleLogoPainter,
                        tokens = tokens,
                        selectedDrawerItem = selectedDrawerItem,
                        documentCount = recentFiles.size,
                        starredCount = starredCount,
                        onSelectTab = { itemKey, tabIndex ->
                            selectedDrawerItem = itemKey
                            coroutineScope.launch {
                                drawerState.close()
                                pagerState.animateScrollToPage(
                                    page = tabIndex,
                                    animationSpec = SwiffleMotion.SpringSpatial
                                )
                            }
                        },
                        onConvert = {
                            coroutineScope.launch { drawerState.close() }
                            onConvert()
                        },
                        onOpenAiChat = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenAiChat()
                        },
                        onOpenSettings = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenSettings()
                        },
                        onLogout = {
                            coroutineScope.launch { drawerState.close() }
                            onLogout()
                        }
                    )
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Ambient radial light pulse matching Onboarding's Dark Void depth
                    if (tokens.isDark) {
                        val depthProgress = ((pagerState.currentPage + pagerState.currentPageOffsetFraction) /
                            (tabs.size - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        DarkVoidAmbientLightPulse(
                            activeStageIndex = selectedTab,
                            activeSubStepIndex = selectedTab,
                            transitionDepthProgress = depthProgress
                        )
                    }

                    val density = LocalDensity.current
                    val imeInsets = WindowInsets.ime
                    val navigationBarInsets = WindowInsets.navigationBars
                    val imeBottomPx by remember(imeInsets, density) {
                        derivedStateOf { imeInsets.getBottom(density) }
                    }
                    val navBarBottomPx by remember(navigationBarInsets, density) {
                        derivedStateOf { navigationBarInsets.getBottom(density) }
                    }
                    val isImeVisible by remember {
                        derivedStateOf { imeBottomPx > navBarBottomPx }
                    }
                    val imeHeightDp = with(density) { imeBottomPx.toDp() }
                    val navBarHeightDp = with(density) { navBarBottomPx.toDp() }
                    var isKeyboardOpen by remember { mutableStateOf(false) }

                    LaunchedEffect(imeInsets, navigationBarInsets, density) {
                        snapshotFlow {
                            imeInsets.getBottom(density) > navigationBarInsets.getBottom(density)
                        }
                            .distinctUntilChanged()
                            .collect { open ->
                                isKeyboardOpen = open
                            }
                    }

                    LaunchedEffect(drawerState.targetValue) {
                        if (drawerState.targetValue == DrawerValue.Open) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }

                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                        ),
                        modifier = Modifier
                            .graphicsLayer { clip = true }
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                })
                            },
                        floatingActionButton = {
                            Box(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .imePadding()
                            ) {
                                AnimatedVisibility(
                                    visible = !isImeVisible,
                                    enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.88f),
                                    exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.88f)
                                ) {
                                    ObsidianMorphicFab(
                                        isCompact = isScrollingFast,
                                        tokens = tokens,
                                        forceCollapse = isKeyboardOpen,
                                        onAddPdf = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            pdfPickerLauncher.launch(SUPPORTED_DOC_MIME_TYPES)
                                        },
                                        onConvert = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            onConvert()
                                        }
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .consumeWindowInsets(paddingValues),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 600.dp)
                            ) {
                                Spacer(modifier = Modifier.height(14.dp))

                                // 1. Top Glassmorphic Search & Profile Bar
                                ObsidianSearchIsland(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    tokens = tokens,
                                    currentUserEmail = currentUserEmail,
                                    currentUserAvatarUrl = currentUserAvatarUrl,
                                    currentUserName = currentUserName,
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

                                Spacer(modifier = Modifier.height(10.dp))

                                // 2. Top-left "View all" + Swipeable Horizontal Tool Rectangle Cards
                                ObsidianToolsCarouselSection(
                                    tokens = tokens,
                                    onOpenTools = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        onConvert()
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 3. Glassmorphic Connected Segmented Tab Bar
                                ObsidianSegmentedTabBar(
                                    tabs = tabs,
                                    pagerState = pagerState,
                                    tokens = tokens,
                                    onSelectTab = { index ->
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                page = index,
                                                animationSpec = SwiffleMotion.SpringSpatial
                                            )
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // 4. Horizontal Pager across All Documents / Starred / Annotated
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
                                            val base = if (searchQuery.isBlank()) {
                                                recentFiles
                                            } else {
                                                recentFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
                                            }
                                            when (pageIndex) {
                                                0 -> base
                                                1 -> base.filter { starredUris.contains(it.uri.toString()) }
                                                else -> emptyList()
                                            }
                                        }
                                    }

                                    val listBottomPadding = if (isImeVisible) {
                                        maxOf(imeHeightDp, navBarHeightDp) + 16.dp
                                    } else {
                                        navBarHeightDp + 104.dp
                                    }

                                    LazyColumn(
                                        state = pageListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            top = 6.dp,
                                            bottom = listBottomPadding
                                        )
                                    ) {
                                        item(key = "section_header_$pageIndex") {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = when {
                                                        searchQuery.isNotBlank() -> "SEARCH RESULTS"
                                                        pageIndex == 0 -> "ALL DOCUMENTS"
                                                        pageIndex == 1 -> "STARRED"
                                                        else -> "ANNOTATED"
                                                    },
                                                    color = tokens.accentRose,
                                                    fontFamily = ClaudeSans,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp,
                                                    letterSpacing = 1.5.sp
                                                )
                                                Text(
                                                    text = "${pageFiles.size} ${if (pageFiles.size == 1) "file" else "files"}",
                                                    color = tokens.textMuted,
                                                    fontFamily = ClaudeSans,
                                                    fontSize = 12.sp,
                                                    style = TextStyle(fontFeatureSettings = "tnum")
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }

                                        if (isPrewarming && recentFiles.isEmpty()) {
                                            items(4) {
                                                ObsidianSkeletonDocumentRow(tokens = tokens)
                                                Spacer(modifier = Modifier.height(10.dp))
                                            }
                                        } else if (pageFiles.isEmpty()) {
                                            item(key = "empty_state_$pageIndex") {
                                                when (pageIndex) {
                                                    0 -> ObsidianEmptyStateCard(
                                                        icon = if (searchQuery.isNotBlank()) Icons.Rounded.Search else Icons.Rounded.FolderOpen,
                                                        iconTint = tokens.accentRose,
                                                        title = if (searchQuery.isNotBlank()) {
                                                            "No matches for \"$searchQuery\""
                                                        } else {
                                                            "Your library is clear"
                                                        },
                                                        description = if (searchQuery.isNotBlank()) {
                                                            "Try a different filename or clear the search filter."
                                                        } else {
                                                            "Open a PDF, Word, or spreadsheet file to start reading and annotating."
                                                        },
                                                        tokens = tokens,
                                                        actionLabel = if (searchQuery.isNotBlank()) null else "Browse documents",
                                                        onAction = if (searchQuery.isNotBlank()) {
                                                            null
                                                        } else {
                                                            { pdfPickerLauncher.launch(SUPPORTED_DOC_MIME_TYPES) }
                                                        }
                                                    )
                                                    1 -> ObsidianEmptyStateCard(
                                                        icon = Icons.Filled.Star,
                                                        iconTint = tokens.starAmber,
                                                        title = "No starred documents",
                                                        description = "Tap the star icon on any file in your library to keep it close at hand.",
                                                        tokens = tokens
                                                    )
                                                    else -> ObsidianEmptyStateCard(
                                                        icon = Icons.Outlined.EditNote,
                                                        iconTint = tokens.accentRose,
                                                        title = "No annotated documents",
                                                        description = "Notes, highlights, and bookmarks added while reading will collect here.",
                                                        tokens = tokens
                                                    )
                                                }
                                            }
                                        } else {
                                            items(pageFiles, key = { it.uri.toString() }) { file ->
                                                val isStarred = starredUris.contains(file.uri.toString())
                                                ObsidianDocumentRow(
                                                    file = file,
                                                    tokens = tokens,
                                                    isStarred = isStarred,
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
                                                    onToggleStar = { toggleStar(file.uri) }
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Obsidian Glass Delete Confirmation Dialog
                        filePendingDelete?.let { target ->
                            val dialogShape = RoundedCornerShape(24.dp)
                            AlertDialog(
                                onDismissRequest = { filePendingDelete = null },
                                shape = dialogShape,
                                containerColor = if (tokens.isDark) Color(0xFF0E0E13) else tokens.cardTop,
                                titleContentColor = tokens.textPrimary,
                                textContentColor = tokens.textSecondary,
                                title = {
                                    Text(
                                        text = "Remove document?",
                                        fontFamily = ClaudeSerif,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 20.sp,
                                        color = tokens.textPrimary
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Remove \"${target.name}\" from your recent library? The original file stays on your device.",
                                        fontFamily = ClaudeSans,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = tokens.textSecondary
                                    )
                                },
                                confirmButton = {
                                    val btnShape = RoundedCornerShape(999.dp)
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(btnShape)
                                            .background(tokens.dangerRose.copy(alpha = 0.16f))
                                            .border(0.8.dp, tokens.dangerRose.copy(alpha = 0.45f), btnShape)
                                            .clickable {
                                                deleteRecentFile(target)
                                                filePendingDelete = null
                                            }
                                            .padding(horizontal = 18.dp)
                                            .testTag("confirm_delete_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Remove",
                                            fontFamily = ClaudeSans,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = tokens.dangerRose
                                        )
                                    }
                                },
                                dismissButton = {
                                    val btnShape = RoundedCornerShape(999.dp)
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(btnShape)
                                            .border(0.8.dp, tokens.borderSubtle, btnShape)
                                            .clickable { filePendingDelete = null }
                                            .padding(horizontal = 18.dp)
                                            .testTag("cancel_delete_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            fontFamily = ClaudeSans,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = tokens.textPrimary
                                        )
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
