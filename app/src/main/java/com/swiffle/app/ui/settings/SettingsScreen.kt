package com.swiffle.app.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.swiffle.app.theme.GoogleSans
import com.swiffle.app.theme.SwiffleMotion
import com.swiffle.app.theme.ThemeManager
import com.swiffle.app.theme.ThemeMode
import com.swiffle.app.ui.components.GoogleBrandIcon
import com.swiffle.app.ui.components.WinUiToggleSwitch
import com.swiffle.app.ui.canvas.PdfPreloader
import com.swiffle.app.auth.OrigamiAuth

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  SWIFFLE SETTINGS & INTELLIGENCE — FULL SCREEN GOOGLE ACCOUNT CENTER ARCHITECTURE
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * Implements a standalone, full-page Material 3 Expressive settings system inspired
 * by the official Google Gemini Settings / Google Account Center.
 *
 * - Zero bottom sheet constraints: Rich, spacious, multi-page canvas
 * - Fluid spatial depth via SwiffleMotion (Shared Axis Z Scale + Fade)
 * - Strict per-user sandbox isolation for files, settings, and AI telemetry
 * - Edge-to-edge support with Large Top App Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }

    fun getEffectiveAvatar(): String? {
        val email = sharedPrefs.getString("google_user_email", null) ?: com.swiffle.app.auth.OrigamiAuth.getCurrentUserEmail(context)
        val isGoogle = email != null && sharedPrefs.getString("user_mode", "") != "guest"
        return if (isGoogle) {
            sharedPrefs.getString("google_user_avatar_url", null)
                ?: sharedPrefs.getString("google_user_avatar", null)
                ?: com.swiffle.app.auth.OrigamiAuth.getCurrentUserAvatarUrl(context)
        } else {
            val f = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
            if (f.exists()) {
                f.absolutePath
            } else {
                sharedPrefs.getString("google_user_avatar", null)
                    ?: sharedPrefs.getString("google_user_avatar_url", null)
            }
        }
    }

    // Active User Profile State
    val currentUserEmail = remember { sharedPrefs.getString("google_user_email", null) ?: com.swiffle.app.auth.OrigamiAuth.getCurrentUserEmail(context) }
    val isGoogleUser = currentUserEmail != null && sharedPrefs.getString("user_mode", "") != "guest"
    var currentUserName by remember {
        mutableStateOf(
            if (isGoogleUser) {
                sharedPrefs.getString("google_user_name", null)
                    ?: com.swiffle.app.auth.OrigamiAuth.getCurrentUserName(context)
                    ?: "User"
            } else {
                sharedPrefs.getString("google_user_name", "Guest User") ?: "Guest User"
            }
        )
    }
    var currentUserAvatarUrl by remember { mutableStateOf(getEffectiveAvatar()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentUserAvatarUrl = getEffectiveAvatar()
                if (isGoogleUser) {
                    currentUserName = sharedPrefs.getString("google_user_name", null)
                        ?: com.swiffle.app.auth.OrigamiAuth.getCurrentUserName(context)
                        ?: "User"
                } else {
                    currentUserName = sharedPrefs.getString("google_user_name", "Guest User") ?: "Guest User"
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Subpage Navigation Router
    var currentRoute by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Main) }

    // Dialog & Popover States
    var showProfileEditModal by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // Handle physical back gesture: Return to Main Settings first, then pop to Home
    BackHandler {
        if (currentRoute != SettingsRoute.Main) {
            currentRoute = SettingsRoute.Main
        } else {
            onBack()
        }
    }

    // Master Scale + Fade Page Orchestrator (Material 3 Expressive Shared Axis Z)
    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            if (targetState != SettingsRoute.Main && initialState == SettingsRoute.Main) {
                SwiffleMotion.horizontalForward()
            } else {
                SwiffleMotion.horizontalBackward()
            }
        },
        label = "settings_page_transition",
        modifier = Modifier.fillMaxSize()
    ) { route ->
        when (route) {
            SettingsRoute.Main -> {
                MainSettingsHub(
                    isGoogleUser = isGoogleUser,
                    userEmail = currentUserEmail,
                    userName = currentUserName,
                    avatarUrl = currentUserAvatarUrl,
                    onBack = onBack,
                    onNavigate = { target -> currentRoute = target },
                    onAvatarClick = {
                        if (!isGoogleUser) {
                            showProfileEditModal = true
                        }
                    },
                    onNameClick = {
                        if (!isGoogleUser) {
                            showProfileEditModal = true
                        }
                    },
                    onLogoutClick = { showLogoutConfirmDialog = true }
                )
            }
            SettingsRoute.General -> {
                GeneralSubpage(
                    onBack = { currentRoute = SettingsRoute.Main },
                    onLogout = onLogout
                )
            }
            SettingsRoute.Appearance -> {
                AppearanceSubpage(
                    onBack = { currentRoute = SettingsRoute.Main }
                )
            }
            SettingsRoute.Notifications -> {
                NotificationsSubpage(
                    onBack = { currentRoute = SettingsRoute.Main }
                )
            }
            SettingsRoute.Intelligence -> {
                IntelligenceSubpage(
                    onBack = { currentRoute = SettingsRoute.Main }
                )
            }
            SettingsRoute.Plugins -> com.swiffle.app.plugins.PluginSettings { currentRoute = SettingsRoute.Main }
            SettingsRoute.GoogleCloud -> {
                GoogleCloudAndAiProvidersSubpage(
                    onBack = { currentRoute = SettingsRoute.Main }
                )
            }
            SettingsRoute.Subscriptions -> {
                SubscriptionsSubpage(
                    onBack = { currentRoute = SettingsRoute.Main }
                )
            }
            SettingsRoute.UsageLimits -> {
                UsageLimitsSubpage(
                    onBack = { currentRoute = SettingsRoute.Main }
                )
            }
            SettingsRoute.About -> {
                AboutSwiffleSubpage(
                    onBack = { currentRoute = SettingsRoute.Main }
                )
            }
            SettingsRoute.HelpFeedback -> {
                HelpAndFeedbackSubpage(
                    onBack = { currentRoute = SettingsRoute.Main }
                )
            }
        }
    }

    // Profile Photo & Name Update Modal
    if (showProfileEditModal) {
        ProfileEditModal(
            currentName = currentUserName,
            currentAvatarUrl = currentUserAvatarUrl,
            onDismiss = { showProfileEditModal = false },
            onSave = { newName, newAvatarUrl ->
                currentUserName = newName
                currentUserAvatarUrl = newAvatarUrl
                sharedPrefs.edit()
                    .putString("google_user_name", newName)
                    .apply()
                if (newAvatarUrl != null) {
                    sharedPrefs.edit()
                        .putString("google_user_avatar", newAvatarUrl)
                        .putString("google_user_avatar_url", newAvatarUrl)
                        .apply()
                } else {
                    sharedPrefs.edit()
                        .remove("google_user_avatar")
                        .remove("google_user_avatar_url")
                        .apply()
                    try {
                        val avatarFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
                        if (avatarFile.exists()) avatarFile.delete()
                    } catch (_: Exception) {}
                }
                showProfileEditModal = false
            }
        )
    }

    // Modal Confirmation Dialog: Logout
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Log out of Swiffle?",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Your on-device workspace will be locked until you sign back in. Any active document edits have already been safely preserved.",
                    fontFamily = GoogleSans,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Log Out", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = false },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (ThemeManager.isDarkThemeActive()) Color(0x33FFFFFF) else Color(0x1F000000))
                ) {
                    Text("Cancel", fontFamily = GoogleSans)
                }
            },
            shape = RoundedCornerShape(8.dp)
        )
    }
}

private data class SettingsSearchTarget(
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val route: SettingsRoute? = null,
    val isLogout: Boolean = false
)

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  THE MASTER SETTINGS HUB (MAIN SCREEN)
 * ══════════════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettingsHub(
    isGoogleUser: Boolean,
    userEmail: String?,
    userName: String,
    avatarUrl: String?,
    onBack: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val currentThemeMode by ThemeManager.themeMode.collectAsState()
    val numberFmt = remember { java.text.NumberFormat.getIntegerInstance() }
    var liveUsageSnapshot by remember {
        mutableStateOf(com.swiffle.app.ai.SwiffleAiConfig.getUsageRateSnapshot(context))
    }

    LaunchedEffect(Unit) {
        while (true) {
            liveUsageSnapshot = com.swiffle.app.ai.SwiffleAiConfig.getUsageRateSnapshot(context)
            delay(1500L)
        }
    }

    // Interactive tooltip state for Google-authenticated users attempting to edit profile
    var showGoogleAuthTooltip by remember { mutableStateOf(false) }

    LaunchedEffect(showGoogleAuthTooltip) {
        if (showGoogleAuthTooltip) {
            delay(3200)
            showGoogleAuthTooltip = false
        }
    }

    // 4-Color Gemini Aurora Gradient Brush for Avatar Halo
    val geminiAuroraBrush = remember {
        Brush.sweepGradient(
            colors = listOf(
                Color(0xFF4285F4), // Google Blue
                Color(0xFF9B51E0), // Gemini Violet
                Color(0xFFEA4335), // Google Red
                Color(0xFFFBBC05), // Google Yellow
                Color(0xFF34A853), // Google Green
                Color(0xFF4285F4)  // Close loop
            )
        )
    }

    // Constantly Flowing Multi-colored Gemini Aurora Animated Brush for Upgrade to Plus Border
    val infiniteTransition = rememberInfiniteTransition(label = "GeminiAuroraBorderTransition")
    val borderRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GeminiBorderRotation"
    )
    val flowingGeminiBorderBrush = remember(borderRotationAngle) {
        val angleRad = Math.toRadians(borderRotationAngle.toDouble())
        val cos = kotlin.math.cos(angleRad).toFloat()
        val sin = kotlin.math.sin(angleRad).toFloat()
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF4285F4), // Blue
                Color(0xFF9B51E0), // Violet
                Color(0xFFEA4335), // Red
                Color(0xFFFBBC05), // Yellow
                Color(0xFF34A853), // Green
                Color(0xFF4285F4)  // Loop
            ),
            start = androidx.compose.ui.geometry.Offset(500f * (1f - cos), 500f * (1f - sin)),
            end = androidx.compose.ui.geometry.Offset(500f * (1f + cos), 500f * (1f + sin))
        )
    }

    val isDark = ThemeManager.isDarkThemeActive()
    val winUiCanvas = if (isDark) Color(0xFF000000) else Color(0xFFF3F3F3)
    val winUiCard = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF)
    val winUiBorder = if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)
    val textPrimary = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val textMuted = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)
    val fluentCobalt = Color(0xFF0078D4)

    var searchQuery by remember { mutableStateOf("") }

    val allSettings = remember {
        listOf(
            SettingsSearchTarget("General", "Default app behavior, file management, cache", "SYSTEM & PREFERENCES", Icons.Outlined.Tune, SettingsRoute.General),
            SettingsSearchTarget("Appearance", "Theme, Dark mode, Light mode, System default", "SYSTEM & PREFERENCES", Icons.Outlined.Palette, SettingsRoute.Appearance),
            SettingsSearchTarget("Dark Mode & Theme", "Pure pitch Void Black, OLED optimization", "SYSTEM & PREFERENCES", Icons.Outlined.Palette, SettingsRoute.Appearance),
            SettingsSearchTarget("Notifications", "Document processing alerts, conversion notifications", "SYSTEM & PREFERENCES", Icons.Outlined.Notifications, SettingsRoute.Notifications),
            SettingsSearchTarget("Swiffle Intelligence", "Local AI models, smart OCR, document summarization", "INTELLIGENCE & SERVICES", Icons.Outlined.AutoAwesome, SettingsRoute.Intelligence),
            SettingsSearchTarget("Plugins", "ChatGPT connection and plugin access", "INTELLIGENCE & SERVICES", Icons.Outlined.Extension, SettingsRoute.Plugins),
            SettingsSearchTarget("Integrations & Cloud Services", "Google Cloud, Google Drive backup, connected storage", "INTELLIGENCE & SERVICES", Icons.Outlined.CloudQueue, SettingsRoute.GoogleCloud),
            SettingsSearchTarget("Manage Subscriptions", "Swiffle Plus, plan details, billing, upgrade", "INTELLIGENCE & SERVICES", Icons.Outlined.WorkspacePremium, SettingsRoute.Subscriptions),
            SettingsSearchTarget("Usage and limits", "Token balance, daily quota, usage breakdown", "INTELLIGENCE & SERVICES", Icons.Outlined.DataUsage, SettingsRoute.UsageLimits),
            SettingsSearchTarget("About Swiffle", "App version, open source licenses, terms", "ABOUT & SUPPORT", Icons.Outlined.Info, SettingsRoute.About),
            SettingsSearchTarget("Help & Feedback", "FAQs, report an issue, contact support", "ABOUT & SUPPORT", Icons.Outlined.HelpOutline, SettingsRoute.HelpFeedback),
            SettingsSearchTarget("Log Out", "Sign out of your account and lock workspace", "SESSION", Icons.AutoMirrored.Rounded.Logout, null, isLogout = true)
        )
    }

    val filteredSettings = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            allSettings.filter {
                it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(winUiCanvas)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Transparent,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Settings",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Windows 11 Fluent Floating Search Pill
                Surface(
                    shape = CircleShape,
                    color = if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF),
                    border = BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x18000000)),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotEmpty()) fluentCobalt else textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Find a setting",
                                    fontFamily = GoogleSans,
                                    fontSize = 14.sp,
                                    color = textMuted
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = GoogleSans,
                                    fontSize = 14.sp,
                                    color = textPrimary
                                ),
                                cursorBrush = SolidColor(fluentCobalt),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Surface(
                                onClick = { searchQuery = "" },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear",
                                        tint = textMuted,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = winUiCanvas,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (searchQuery.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "SEARCH RESULTS (${filteredSettings.size})",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.8.sp,
                        color = fluentCobalt,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    if (filteredSettings.isNotEmpty()) {
                        SettingsGroupCard {
                            filteredSettings.forEachIndexed { idx, item ->
                                if (idx > 0) SettingsItemDivider()
                                SettingsItemRow(
                                    icon = item.icon,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    onClick = {
                                        if (item.isLogout) {
                                            onLogoutClick()
                                        } else if (item.route != null) {
                                            onNavigate(item.route)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = winUiCard,
                            border = BorderStroke(1.dp, winUiBorder),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = textMuted,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No settings found for \"$searchQuery\"",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Try searching for a different keyword or category.",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.5.sp,
                                    color = textMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {

            // ── HERO ACCOUNT IDENTITY CARD (WinUI 3 Account Banner) ──────────
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = winUiCard,
                border = BorderStroke(1.dp, winUiBorder),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // WinUI 3 "Free Tier" Pill in Top-Right Corner
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = fluentCobalt.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, fluentCobalt.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 14.dp, end = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(fluentCobalt)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Free Tier",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = fluentCobalt
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Centered Avatar with Gemini Multi-Color Aurora Border and floating pencil badge
                        Box(
                            modifier = Modifier.size(86.dp),
                            contentAlignment = Alignment.Center
                        ) {
                        // The Circular Avatar with Aurora halo
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .border(width = 2.8.dp, brush = geminiAuroraBrush, shape = CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    if (isGoogleUser) {
                                        showGoogleAuthTooltip = true
                                    } else {
                                        onAvatarClick()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                val avatarModel: Any = if (!avatarUrl.startsWith("http")) {
                                    java.io.File(avatarUrl)
                                } else {
                                    avatarUrl
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(avatarModel)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initial = if (isGoogleUser) {
                                    (userName.firstOrNull() ?: userEmail?.firstOrNull() ?: 'U').uppercase()
                                } else {
                                    (userName.firstOrNull() ?: 'G').uppercase()
                                }
                                Text(
                                    text = initial,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Floating Edit Pencil badge for guest users (positioned outside avatar clip)
                        if (!isGoogleUser) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 3.dp,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                                    .clickable(onClick = onAvatarClick)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Profile",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // User Display Name in Google Sans 20sp Bold (No pencil icon)
                    Text(
                        text = userName,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            if (isGoogleUser) {
                                showGoogleAuthTooltip = true
                            } else {
                                onNameClick()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Email Subtitle in Muted Typography
                    Text(
                        text = userEmail ?: "Local Workspace (Guest)",
                        fontFamily = GoogleSans,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Expressive Tooltip for Google account users
                    AnimatedVisibility(
                        visible = showGoogleAuthTooltip,
                        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.inverseSurface,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Profile is managed by your Google Account and cannot be edited here.",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "Upgrade to Plus" WinUI 3 Tonal Button with Flowing Multi-colored Gemini Border
                    Surface(
                        onClick = { onNavigate(SettingsRoute.Subscriptions) },
                        shape = RoundedCornerShape(4.dp),
                        color = if (isDark) Color(0xFF141414) else Color(0xFFF9F9F9),
                        border = BorderStroke(1.2.dp, flowingGeminiBorderBrush),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Swiffle's Official SVG Logo
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.swiffle.app.R.drawable.ic_swiffle_logo),
                                contentDescription = "Swiffle Logo",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Upgrade to Plus",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = textPrimary
                            )
                        }
                    }
                }
            }
        }

            Spacer(modifier = Modifier.height(20.dp))

            // ── GROUP 1: SYSTEM & GENERAL ──────────────────────────────────────
            Text(
                text = "SYSTEM & PREFERENCES",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 0.8.sp,
                color = fluentCobalt,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            SettingsGroupCard {
                SettingsItemRow(
                    icon = Icons.Outlined.Tune,
                    title = "General",
                    onClick = { onNavigate(SettingsRoute.General) }
                )
                SettingsItemDivider()
                SettingsItemRow(
                    icon = Icons.Outlined.Palette,
                    title = "Appearance",
                    subtitle = currentThemeMode.displayName,
                    onClick = { onNavigate(SettingsRoute.Appearance) }
                )
                SettingsItemDivider()
                SettingsItemRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    onClick = { onNavigate(SettingsRoute.Notifications) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── GROUP 2: INTELLIGENCE & CLOUD ECOSYSTEM ────────────────────────
            Text(
                text = "INTELLIGENCE & SERVICES",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 0.8.sp,
                color = fluentCobalt,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            // Live AI Rate, Usage & Limits Summary Card
            val tokenProgress = (liveUsageSnapshot.totalTokensToday.toFloat() /
                liveUsageSnapshot.dailyTokenQuota.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            Surface(
                onClick = { onNavigate(SettingsRoute.UsageLimits) },
                shape = RoundedCornerShape(8.dp),
                color = winUiCard,
                border = BorderStroke(1.dp, winUiBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = liveUsageSnapshot.activeModelDisplayName,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = textPrimary
                            )
                            Text(
                                text = liveUsageSnapshot.activeProviderLabel,
                                fontFamily = GoogleSans,
                                fontSize = 12.sp,
                                color = textMuted
                            )
                        }
                        Text(
                            text = if (liveUsageSnapshot.cooldownRemainingSec > 0) {
                                "Cooldown ${liveUsageSnapshot.cooldownRemainingSec}s"
                            } else {
                                "${liveUsageSnapshot.currentRpm}/${liveUsageSnapshot.rpmLimit} RPM"
                            },
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = if (liveUsageSnapshot.cooldownRemainingSec > 0) {
                                Color(0xFFD97706)
                            } else {
                                fluentCobalt
                            }
                        )
                    }

                    LinearProgressIndicator(
                        progress = { tokenProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                        color = fluentCobalt,
                        trackColor = textMuted.copy(alpha = 0.16f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${numberFmt.format(liveUsageSnapshot.totalTokensToday)} / ${numberFmt.format(liveUsageSnapshot.dailyTokenQuota)} tok",
                            fontFamily = GoogleSans,
                            fontSize = 11.5.sp,
                            color = textMuted
                        )
                        Text(
                            text = "${liveUsageSnapshot.currentRpd}/${liveUsageSnapshot.rpdLimit} RPD • ${numberFmt.format(liveUsageSnapshot.currentTpm)}/${numberFmt.format(liveUsageSnapshot.tpmLimit)} TPM",
                            fontFamily = GoogleSans,
                            fontSize = 11.5.sp,
                            color = textMuted
                        )
                    }
                }
            }
            SettingsGroupCard {
                SettingsItemRow(
                    iconPainter = androidx.compose.ui.res.painterResource(id = com.swiffle.app.R.drawable.ic_swiffle_logo_monochrome),
                    title = "Swiffle Intelligence",
                    onClick = { onNavigate(SettingsRoute.Intelligence) }
                )
                SettingsItemDivider()
                SettingsItemRow(
                    icon = Icons.Outlined.Extension,
                    title = "Plugins",
                    subtitle = "ChatGPT connection and plugin access",
                    onClick = { onNavigate(SettingsRoute.Plugins) }
                )
                SettingsItemDivider()
                SettingsItemRow(
                    icon = Icons.Outlined.CloudQueue,
                    title = "Integrations & Cloud Services",
                    subtitle = if (liveUsageSnapshot.hasGeminiKey) {
                        "Gemini Free Tier (${liveUsageSnapshot.keySourceLabel})"
                    } else {
                        "Configure Gemini, Groq & OpenRouter keys"
                    },
                    onClick = { onNavigate(SettingsRoute.GoogleCloud) }
                )
                SettingsItemDivider()
                SettingsItemRow(
                    icon = Icons.Outlined.WorkspacePremium,
                    title = "Manage Subscriptions",
                    onClick = { onNavigate(SettingsRoute.Subscriptions) }
                )
                SettingsItemDivider()
                SettingsItemRow(
                    icon = Icons.Outlined.DataUsage,
                    title = "Usage and limits",
                    subtitle = "${liveUsageSnapshot.currentRpm}/${liveUsageSnapshot.rpmLimit} RPM • ${liveUsageSnapshot.currentRpd}/${liveUsageSnapshot.rpdLimit} RPD • ${numberFmt.format(liveUsageSnapshot.totalTokensToday)} tok",
                    onClick = { onNavigate(SettingsRoute.UsageLimits) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── GROUP 3: SUPPORT & INFORMATION ─────────────────────────────────
            Text(
                text = "ABOUT & SUPPORT",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 0.8.sp,
                color = fluentCobalt,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            SettingsGroupCard {
                SettingsItemRow(
                    icon = Icons.Outlined.Info,
                    title = "About Swiffle",
                    onClick = { onNavigate(SettingsRoute.About) }
                )
                SettingsItemDivider()
                SettingsItemRow(
                    icon = Icons.Outlined.HelpOutline,
                    title = "Help & Feedback",
                    onClick = { onNavigate(SettingsRoute.HelpFeedback) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── GROUP 4: SESSION CONTROL (RED LOG OUT) ─────────────────────────
            Text(
                text = "SESSION",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            SettingsGroupCard {
                Surface(
                    onClick = onLogoutClick,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Log Out",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Log Out",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            }
            }
        }
    }
}

/**
 * Windows 11 WinUI 3 Group Container (Progressive 8px Corner Radius)
 */
@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    val isDark = ThemeManager.isDarkThemeActive()
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF0C0C0C) else Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, if (isDark) Color(0x22FFFFFF) else Color(0x0F000000)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Windows 11 WinUI 3 Settings Item Row (4px Monoline Icon Badge, Chevron, Hover)
 */
@Composable
private fun SettingsItemRow(
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String? = null,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    subtitleOverflow: TextOverflow = TextOverflow.Clip,
    onClick: () -> Unit
) {
    val isDark = ThemeManager.isDarkThemeActive()
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDark) Color(0xFF161616) else Color(0xFFF2F2F2)),
                contentAlignment = Alignment.Center
            ) {
                if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFD4D4D4) else Color(0xFF4C4C4C),
                        modifier = Modifier.size(18.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFD4D4D4) else Color(0xFF4C4C4C),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontFamily = GoogleSans,
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E),
                        maxLines = subtitleMaxLines,
                        overflow = subtitleOverflow
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (isDark) Color(0xFF7A7A7A) else Color(0xFF9E9E9E),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SettingsItemDivider() {
    val isDark = ThemeManager.isDarkThemeActive()
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
        color = if (isDark) Color(0x18FFFFFF) else Color(0x0A000000),
        thickness = 1.dp
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isDark = ThemeManager.isDarkThemeActive()
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDark) Color(0xFF161616) else Color(0xFFF2F2F2)),
                contentAlignment = Alignment.Center
            ) {
                if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFD4D4D4) else Color(0xFF4C4C4C),
                        modifier = Modifier.size(18.dp)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFD4D4D4) else Color(0xFF4C4C4C),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
                )
                Text(
                    text = subtitle,
                    fontFamily = GoogleSans,
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            WinUiToggleSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                activeColor = Color(0xFF0078D4),
                isDark = isDark
            )
        }
    }
}

/**
 * Windows 11 Fluent WinUI 3 Breadcrumb Subpage Shell
 */
@Composable
fun SettingsSubpageShell(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = ThemeManager.isDarkThemeActive()
    val winUiCanvas = if (isDark) Color(0xFF000000) else Color(0xFFF3F3F3)
    val textPrimary = if (isDark) Color(0xFFF5F5F5) else Color(0xFF1A1A1A)
    val textMuted = if (isDark) Color(0xFF8A8A8A) else Color(0xFF6E6E6E)
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(winUiCanvas)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Transparent,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = textPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = textPrimary
                    )
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = if (isDark) Color(0x1AFFFFFF) else Color(0x0E000000)
                )
            }
        },
        containerColor = winUiCanvas,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                content()
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  GENERAL SUBPAGE — ENTERPRISE BEHAVIOR, READING, FILE HANDLING & DANGER ZONE
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun GeneralSubpage(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // ── 1. DEFAULT PDF VIEWER STATUS ───────────────────────────────────────────
    fun checkDefaultPdfViewer(): Boolean {
        return try {
            val testIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://dummy/test.pdf"), "application/pdf")
            }
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    testIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    var isDefaultPdf by remember { mutableStateOf(checkDefaultPdfViewer()) }

    // Re-evaluate default app status when resuming from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultPdf = checkDefaultPdfViewer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val currentStatus = checkDefaultPdfViewer()
            if (currentStatus != isDefaultPdf) {
                isDefaultPdf = currentStatus
            }
            delay(800)
        }
    }

    fun launchDefaultAppsChooser() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                context.startActivity(intent)
            } else {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Please set Swiffle as default in Android Settings -> Apps", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 2. PREFERENCE STATES ──────────────────────────────────────────────────
    var rememberReadingPosition by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_remember_reading_position", false))
    }
    var pageTurnAnimation by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_page_turn_animation", false))
    }
    var keepScreenAwake by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_keep_screen_awake", true))
    }
    var smartCropEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_smart_crop", true))
    }
    var confirmFileDelete by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_confirm_file_delete", true))
    }
    var showRecentDocuments by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_show_recent_documents", true))
    }
    var autoDetectPdfs by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_auto_detect_pdfs", true))
    }

    // ── 3. PHYSICAL STORAGE DIRECTORY & REAL SAF FOLDER PICKER ───────────────
    val defaultFallbackPath = remember {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val appDir = java.io.File(documentsDir, "ORIGAMI PDF")
        if (!appDir.exists()) {
            try { appDir.mkdirs() } catch (_: Exception) {}
        }
        appDir.absolutePath
    }

    var customFolderUriString by remember {
        mutableStateOf(sharedPrefs.getString("pref_custom_folder_uri", null))
    }
    var customFolderDisplayName by remember {
        mutableStateOf(sharedPrefs.getString("pref_custom_folder_name", null))
    }

    // Android Storage Access Framework (SAF) Directory Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                // Persist read & write permissions across app restarts and reboots
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)

                // Derive human-readable directory name from DocumentTree URI
                val docId = try {
                    android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                } catch (_: Exception) {
                    treeUri.lastPathSegment ?: "Selected Folder"
                }
                val readableName = if (docId != null && docId.contains(":")) {
                    val parts = docId.split(":")
                    if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else parts[0]
                } else {
                    docId ?: "Selected Folder"
                }

                customFolderUriString = treeUri.toString()
                customFolderDisplayName = readableName

                sharedPrefs.edit()
                    .putString("pref_custom_folder_uri", treeUri.toString())
                    .putString("pref_custom_folder_name", readableName)
                    .apply()

                Toast.makeText(context, "Default folder updated to: $readableName", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // Fallback if takePersistableUriPermission encounters edge cases
                val fallbackName = treeUri.lastPathSegment ?: "Selected Folder"
                customFolderUriString = treeUri.toString()
                customFolderDisplayName = fallbackName
                sharedPrefs.edit()
                    .putString("pref_custom_folder_uri", treeUri.toString())
                    .putString("pref_custom_folder_name", fallbackName)
                    .apply()
                Toast.makeText(context, "Folder selected: $fallbackName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── 4. DIALOG STATES ──────────────────────────────────────────────────────
    var showResetFolderConfirmDialog by remember { mutableStateOf(false) }
    var showClearRecentDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    SettingsSubpageShell(
        title = "General",
        onBack = onBack
    ) {
        // ── TOP PILL: SET AS DEFAULT PDF VIEWER (Dismisses once set) ───────────
        AnimatedVisibility(
            visible = !isDefaultPdf,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { launchDefaultAppsChooser() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Set as Default PDF Viewer",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Open documents instantly without app chooser dialogs.",
                                fontFamily = GoogleSans,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { launchDefaultAppsChooser() },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Set",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // ── SECTION 1: PDF BEHAVIOR ────────────────────────────────────────────
        var alwaysOpenFromBeginning by remember {
            mutableStateOf(sharedPrefs.getBoolean("pref_always_open_beginning", true))
        }
        Text(
            text = "PDF BEHAVIOR",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            SettingsSwitchRow(
                icon = Icons.Outlined.RestartAlt,
                title = "Always Open From Beginning",
                subtitle = "Documents cleanly open from Page 1 without jump locks.",
                checked = alwaysOpenFromBeginning,
                onCheckedChange = { isChecked ->
                    alwaysOpenFromBeginning = isChecked
                    sharedPrefs.edit().putBoolean("pref_always_open_beginning", isChecked).apply()
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── SECTION 2: READING & NAVIGATION ────────────────────────────────────
        Text(
            text = "READING & NAVIGATION",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Page Turn Animation",
                subtitle = "Curl and flip transitions between pages (Coming Soon)",
                checked = pageTurnAnimation,
                onCheckedChange = { isChecked ->
                    pageTurnAnimation = isChecked
                    sharedPrefs.edit().putBoolean("pref_page_turn_animation", isChecked).apply()
                }
            )
            SettingsItemDivider()
            SettingsSwitchRow(
                icon = Icons.Outlined.LightMode,
                title = "Keep Screen Awake While Reading",
                subtitle = "Prevent your screen from turning off while reading documents.",
                checked = keepScreenAwake,
                onCheckedChange = { isChecked ->
                    keepScreenAwake = isChecked
                    sharedPrefs.edit().putBoolean("pref_keep_screen_awake", isChecked).apply()
                }
            )
            SettingsItemDivider()
            SettingsSwitchRow(
                icon = Icons.Outlined.Crop,
                title = "Smart Margin Auto-Crop",
                subtitle = "Automatically trim empty white borders and scanner margins for edge-to-edge reading.",
                checked = smartCropEnabled,
                onCheckedChange = { isChecked ->
                    smartCropEnabled = isChecked
                    sharedPrefs.edit().putBoolean("pref_smart_crop", isChecked).apply()
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── SECTION 3: FILE HANDLING ───────────────────────────────────────────
        Text(
            text = "FILE HANDLING",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            // Default File Location Row (Interactive Storage Access Framework Directory Picker)
            Surface(
                onClick = {
                    try {
                        folderPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open system folder picker: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = if (customFolderDisplayName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Default File Location",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (customFolderDisplayName != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "Custom",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = customFolderDisplayName ?: "Internal Storage/Documents/ORIGAMI PDF",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = if (customFolderDisplayName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (customFolderUriString != null) "Tap to change folder in system picker" else defaultFallbackPath,
                            fontFamily = GoogleSans,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (customFolderDisplayName != null) {
                        IconButton(
                            onClick = { showResetFolderConfirmDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = "Reset to Default Folder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            SettingsItemDivider()

            SettingsSwitchRow(
                icon = Icons.Outlined.HelpOutline,
                title = "Ask Before Deleting Files",
                subtitle = "Prompt for confirmation before removing files from your library.",
                checked = confirmFileDelete,
                onCheckedChange = { isChecked ->
                    confirmFileDelete = isChecked
                    sharedPrefs.edit().putBoolean("pref_confirm_file_delete", isChecked).apply()
                }
            )

            SettingsItemDivider()

            SettingsSwitchRow(
                icon = Icons.Outlined.History,
                title = "Show Recent Documents",
                subtitle = "Display recent document shelf and history on the home library.",
                checked = showRecentDocuments,
                onCheckedChange = { isChecked ->
                    showRecentDocuments = isChecked
                    sharedPrefs.edit().putBoolean("pref_show_recent_documents", isChecked).apply()
                }
            )

            SettingsItemDivider()

            // Clear Recent Documents
            Surface(
                onClick = { showClearRecentDialog = true },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clear Recent Documents",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Remove recent viewing records from the home screen.",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            SettingsItemDivider()

            SettingsSwitchRow(
                icon = Icons.Outlined.FindInPage,
                title = "Automatically Detect PDF Files",
                subtitle = "Scan device storage periodically for newly downloaded or created PDFs.",
                checked = autoDetectPdfs,
                onCheckedChange = { isChecked ->
                    autoDetectPdfs = isChecked
                    sharedPrefs.edit().putBoolean("pref_auto_detect_pdfs", isChecked).apply()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── SECTION 4: DATA MANAGEMENT ──────────────────────────────────────────
        Text(
            text = "DATA MANAGEMENT",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            // Clear Document Cache
            Surface(
                onClick = { showClearCacheDialog = true },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cached,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clear Document Cache",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Free up storage by clearing pre-rendered PDF page and thumbnail cache.",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsItemDivider()

            // Reset All Settings
            Surface(
                onClick = { showResetSettingsDialog = true },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reset All Settings",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Revert all settings back to default values.",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsItemDivider()

            // Delete Swiffle Account
            Surface(
                onClick = { showDeleteAccountDialog = true },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Delete Swiffle Account",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Permanently delete account session, profile data, and local cache.",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // ── DIALOG 1: CLEAR RECENT DOCUMENTS ──────────────────────────────────────
    if (showClearRecentDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecentDialog = false },
            title = {
                Text(
                    text = "Clear Recent Documents?",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will remove all documents from your Recent library history. Your physical files on the device will not be deleted.",
                    fontFamily = GoogleSans,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sharedPrefs.edit().putString("recentFiles", "[]").apply()
                        showClearRecentDialog = false
                        Toast.makeText(context, "Recent documents cleared", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Recent", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecentDialog = false }) {
                    Text("Cancel", fontFamily = GoogleSans)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── DIALOG 2: CLEAR DOCUMENT CACHE ────────────────────────────────────────
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    text = "Clear Document Cache?",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This will purge all preloaded bitmaps and temporary rendering buffers. Documents will re-render fresh upon opening.",
                    fontFamily = GoogleSans,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        PdfPreloader.clearAllCache()
                        try {
                            context.cacheDir.deleteRecursively()
                        } catch (_: Exception) {}
                        showClearCacheDialog = false
                        Toast.makeText(context, "Document cache cleared", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Cache", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", fontFamily = GoogleSans)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── DIALOG 3: RESET ALL SETTINGS ──────────────────────────────────────────
    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = {
                Text(
                    text = "Reset All Settings?",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "All preferences will revert back to factory defaults. Your PDF documents and bookmarks will remain unaffected.",
                    fontFamily = GoogleSans,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        rememberReadingPosition = false
                        pageTurnAnimation = false
                        keepScreenAwake = true
                        smartCropEnabled = true
                        confirmFileDelete = true
                        showRecentDocuments = true
                        autoDetectPdfs = true

                        sharedPrefs.edit().apply {
                            putBoolean("pref_remember_reading_position", false)
                            putBoolean("pref_page_turn_animation", false)
                            putBoolean("pref_keep_screen_awake", true)
                            putBoolean("pref_smart_crop", true)
                            putBoolean("pref_confirm_file_delete", true)
                            putBoolean("pref_show_recent_documents", true)
                            putBoolean("pref_auto_detect_pdfs", true)
                            apply()
                        }
                        ThemeManager.setThemeMode(context, ThemeMode.SYSTEM)
                        showResetSettingsDialog = false
                        Toast.makeText(context, "All settings restored to defaults", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) {
                    Text("Cancel", fontFamily = GoogleSans)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── DIALOG 4: DELETE SWIFFLE ACCOUNT ──────────────────────────────────────
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text(
                    text = "Delete Swiffle Account?",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete your account session and local profile data? This will log you out, wipe cached credentials, and reset preferences. This action cannot be undone.",
                    fontFamily = GoogleSans,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        coroutineScope.launch {
                            OrigamiAuth.signOut(context)
                            sharedPrefs.edit().clear().apply()
                            try {
                                context.cacheDir.deleteRecursively()
                                val avatarFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
                                if (avatarFile.exists()) avatarFile.delete()
                            } catch (_: Exception) {}
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Account", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", fontFamily = GoogleSans)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── DIALOG 5: RESET DEFAULT FOLDER ───────────────────────────────────────
    if (showResetFolderConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetFolderConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Reset Default Folder?",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Switch back to the standard system storage directory (Documents/ORIGAMI PDF)? Newly created or exported PDFs will be stored there.",
                    fontFamily = GoogleSans,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sharedPrefs.edit()
                            .remove("pref_custom_folder_uri")
                            .remove("pref_custom_folder_name")
                            .apply()
                        customFolderUriString = null
                        customFolderDisplayName = null
                        showResetFolderConfirmDialog = false
                        Toast.makeText(context, "Storage folder reset to standard Documents directory", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset Folder", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetFolderConfirmDialog = false }) {
                    Text("Cancel", fontFamily = GoogleSans)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  APPEARANCE SUBPAGE — 3-TIER MATERIAL 3 THEME SWITCHER
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun AppearanceSubpage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentMode by ThemeManager.themeMode.collectAsState()

    SettingsSubpageShell(
        title = "Appearance",
        onBack = onBack
    ) {
        Text(
            text = "Choose how Swiffle PDF appears on your device. When set to System default, the app automatically syncs with your phone's dark mode schedule.",
            fontFamily = GoogleSans,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        SettingsGroupCard {
            ThemeOptionRow(
                icon = Icons.Outlined.SettingsBrightness,
                title = ThemeMode.SYSTEM.displayName,
                subtitle = ThemeMode.SYSTEM.subtitle,
                selected = currentMode == ThemeMode.SYSTEM,
                onClick = { ThemeManager.setThemeMode(context, ThemeMode.SYSTEM) }
            )
            SettingsItemDivider()
            ThemeOptionRow(
                icon = Icons.Outlined.LightMode,
                title = ThemeMode.LIGHT.displayName,
                subtitle = ThemeMode.LIGHT.subtitle,
                selected = currentMode == ThemeMode.LIGHT,
                onClick = { ThemeManager.setThemeMode(context, ThemeMode.LIGHT) }
            )
            SettingsItemDivider()
            ThemeOptionRow(
                icon = Icons.Outlined.DarkMode,
                title = ThemeMode.DARK.displayName,
                subtitle = ThemeMode.DARK.subtitle,
                selected = currentMode == ThemeMode.DARK,
                onClick = { ThemeManager.setThemeMode(context, ThemeMode.DARK) }
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = GoogleSans,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontFamily = GoogleSans,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  NOTIFICATIONS SUBPAGE (STEP 3.A)
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun NotificationsSubpage(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }

    var areSystemNotificationsEnabled by remember {
        mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    var conversionAlerts by remember {
        mutableStateOf(sharedPrefs.getBoolean("notify_conversion_alerts", true))
    }
    var ocrAlerts by remember {
        mutableStateOf(sharedPrefs.getBoolean("notify_ocr_alerts", true))
    }
    var aiInsightsAlerts by remember {
        mutableStateOf(sharedPrefs.getBoolean("notify_ai_insights", true))
    }
    var quietMode by remember {
        mutableStateOf(sharedPrefs.getBoolean("notify_quiet_mode", false))
    }

    // Automatically detect permission grant the second user returns from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                areSystemNotificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Active sub-second ticker to update instantly while in settings
    LaunchedEffect(Unit) {
        while (true) {
            val enabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (enabled != areSystemNotificationsEnabled) {
                areSystemNotificationsEnabled = enabled
            }
            delay(500)
        }
    }

    SettingsSubpageShell(
        title = "Notifications",
        onBack = onBack
    ) {
        // System Permission Status Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (areSystemNotificationsEnabled) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            border = BorderStroke(
                1.dp,
                if (areSystemNotificationsEnabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (areSystemNotificationsEnabled) Color(0xFF1B5E20).copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (areSystemNotificationsEnabled) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = if (areSystemNotificationsEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (areSystemNotificationsEnabled) "Notifications are active" else "System permissions required",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (areSystemNotificationsEnabled) "Swiffle can alert you when exports and AI scans finish."
                               else "Enable system notifications to receive background alerts.",
                        fontFamily = GoogleSans,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!areSystemNotificationsEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Open Settings",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Group 1: Document Processing Activity
        Text(
            text = "DOCUMENT ACTIVITY",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            NotificationToggleRow(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Conversion & Export Alerts",
                subtitle = "Notify when large PDF generation completes",
                checked = conversionAlerts,
                onCheckedChange = { checked ->
                    conversionAlerts = checked
                    sharedPrefs.edit().putBoolean("notify_conversion_alerts", checked).apply()
                }
            )
            SettingsItemDivider()
            NotificationToggleRow(
                icon = Icons.Outlined.DocumentScanner,
                title = "OCR & Page Scanning",
                subtitle = "Alert when optical character recognition finishes",
                checked = ocrAlerts,
                onCheckedChange = { checked ->
                    ocrAlerts = checked
                    sharedPrefs.edit().putBoolean("notify_ocr_alerts", checked).apply()
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Group 2: Intelligence & Preferences
        Text(
            text = "INTELLIGENCE & PREFERENCES",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            NotificationToggleRow(
                iconPainter = androidx.compose.ui.res.painterResource(id = com.swiffle.app.R.drawable.ic_swiffle_logo_monochrome),
                title = "AI Document Insights",
                subtitle = "Smart summaries and multi-page review suggestions",
                checked = aiInsightsAlerts,
                onCheckedChange = { checked ->
                    aiInsightsAlerts = checked
                    sharedPrefs.edit().putBoolean("notify_ai_insights", checked).apply()
                }
            )
            SettingsItemDivider()
            NotificationToggleRow(
                icon = Icons.Outlined.Bedtime,
                title = "Quiet Mode",
                subtitle = "Mute notification vibrations and sounds after 10 PM",
                checked = quietMode,
                onCheckedChange = { checked ->
                    quietMode = checked
                    sharedPrefs.edit().putBoolean("notify_quiet_mode", checked).apply()
                }
            )
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  SWIFFLE INTELLIGENCE SUBPAGE (STEP 3.B)
 * ══════════════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntelligenceSubpage(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }

    var aiCustomName by remember {
        mutableStateOf(sharedPrefs.getString("ai_custom_name", "Swiffle Intelligence") ?: "Swiffle Intelligence")
    }
    var aiCustomPrompt by remember {
        mutableStateOf(sharedPrefs.getString("ai_custom_prompt", "") ?: "")
    }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditPromptDialog by remember { mutableStateOf(false) }

    SettingsSubpageShell(
        title = "Swiffle Intelligence",
        onBack = onBack
    ) {
        // Hero Aurora Header Card with Monochromatic Swiffle Logo
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.swiffle.app.R.drawable.ic_swiffle_logo),
                            contentDescription = "Swiffle Intelligence",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = aiCustomName,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Private Document Assistant",
                            fontFamily = GoogleSans,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Swiffle Intelligence works seamlessly alongside you to analyze, summarize, and translate documents. All file contents stay private on your device and are never shared.",
                    fontFamily = GoogleSans,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Personalization & Prompts
        Text(
            text = "PERSONALIZATION",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            SettingsItemRow(
                icon = Icons.Outlined.SmartToy,
                title = "Assistant Name",
                subtitle = aiCustomName,
                onClick = { showEditNameDialog = true }
            )
            SettingsItemDivider()
            SettingsItemRow(
                icon = Icons.Outlined.Tune,
                title = "Custom User Instructions",
                subtitle = if (aiCustomPrompt.isBlank()) "None set (Uses standard document persona)" else aiCustomPrompt,
                subtitleMaxLines = 1,
                subtitleOverflow = TextOverflow.Ellipsis,
                onClick = { showEditPromptDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // System Prompt Hierarchy Guarantee Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Your custom instructions are prioritized for tone and formatting, while Swiffle's document accuracy safeguards always preserve exact factual fidelity.",
                    fontFamily = GoogleSans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: AI Capabilities
        Text(
            text = "AI CAPABILITIES",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            CapabilityRow(
                icon = Icons.Outlined.Summarize,
                title = "Instant Page Summaries",
                description = "Get the key bullet points from multi-page chapters in seconds."
            )
            SettingsItemDivider()
            CapabilityRow(
                icon = Icons.Outlined.QuestionAnswer,
                title = "Document Q&A",
                description = "Ask questions directly about figures, terms, and contracts."
            )
            SettingsItemDivider()
            CapabilityRow(
                icon = Icons.Outlined.TableChart,
                title = "Table & Data Extraction",
                description = "Convert printed tables into structured, copyable spreadsheets."
            )
            SettingsItemDivider()
            CapabilityRow(
                icon = Icons.Outlined.Translate,
                title = "Multilingual Translation",
                description = "Translate clauses and paragraphs smoothly across 100+ languages."
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: On-Device Google AI & Offline Intelligence
        Text(
            text = "ON-DEVICE AI MODEL",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        val offlineManager = remember { com.swiffle.app.ai.offline.SwiffleOfflineModels(context) }
        val offlineState by offlineManager.modelState.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        var downloadErrorMsg by remember { mutableStateOf<String?>(null) }

        SettingsGroupCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DownloadForOffline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Gemma 2 & On-Device Engine",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (offlineState) {
                                is com.swiffle.app.ai.offline.OfflineModelState.Ready -> "Google Gemma installed & active (171 MB)"
                                is com.swiffle.app.ai.offline.OfflineModelState.Downloading -> "Downloading Google Gemma weights..."
                                is com.swiffle.app.ai.offline.OfflineModelState.Error -> "Download interrupted"
                                com.swiffle.app.ai.offline.OfflineModelState.NotDownloaded -> "Hugging Face · google/embeddinggemma-2 (171 MB)"
                            },
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Runs 100% locally on your smartphone with zero internet connection required. Powers offline vector search, semantic embeddings, and instant document Q&A.",
                    fontFamily = GoogleSans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                when (val state = offlineState) {
                    is com.swiffle.app.ai.offline.OfflineModelState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading from Hugging Face...",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(state.progressPercent * 100).toInt()}% (${state.bytesDownloaded / (1024 * 1024)} MB / ${if (state.totalBytes > 0) state.totalBytes / (1024 * 1024) else 171} MB)",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { state.progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                    is com.swiffle.app.ai.offline.OfflineModelState.Ready -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E8E3E).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF1E8E3E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Google Gemma Ready On-Device",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E8E3E)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { offlineManager.deleteModel() }
                            ) {
                                Text(
                                    text = "Uninstall",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                downloadErrorMsg = null
                                offlineManager.startBackgroundDownload()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download Google Gemma Model (171 MB)",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }
                        if (downloadErrorMsg != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = downloadErrorMsg ?: "",
                                fontFamily = GoogleSans,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: AI Privacy
        Text(
            text = "AI PRIVACY",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        SettingsGroupCard {
            CapabilityRow(
                icon = Icons.Outlined.Lock,
                title = "Zero Data Sharing",
                description = "Files are never uploaded for model training or public indexing."
            )
            SettingsItemDivider()
            CapabilityRow(
                icon = Icons.Outlined.Security,
                title = "Encrypted Local Processing",
                description = "Document parsing executes strictly within your private device sandbox."
            )
            SettingsItemDivider()
            CapabilityRow(
                icon = Icons.Outlined.VpnKey,
                title = "Zero-Knowledge Telemetry",
                description = "Prompts and responses are never logged to third-party ad networks."
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Reset to Default button
        TextButton(
            onClick = {
                aiCustomName = "Swiffle Intelligence"
                aiCustomPrompt = ""
                sharedPrefs.edit()
                    .putString("ai_custom_name", "Swiffle Intelligence")
                    .putString("ai_custom_prompt", "")
                    .apply()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Reset to Default Persona",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Bottom Modal: Edit AI Name (Compact Bottom Sheet, Not Full Screen)
    if (showEditNameDialog) {
        val nameSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditNameDialog = false },
            sheetState = nameSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            var tempName by remember { mutableStateOf(aiCustomName) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Name Your Assistant",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose a friendly name for your Swiffle AI companion.",
                    fontFamily = GoogleSans,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Assistant Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                // Cancel and Save buttons in bottom right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showEditNameDialog = false },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("Cancel", fontFamily = GoogleSans, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val cleaned = tempName.trim().ifBlank { "Swiffle Intelligence" }
                            aiCustomName = cleaned
                            sharedPrefs.edit().putString("ai_custom_name", cleaned).apply()
                            showEditNameDialog = false
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Save", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Bottom Modal: Edit Custom User Instructions (Full Screen Bottom Modal)
    if (showEditPromptDialog) {
        val promptSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditPromptDialog = false },
            sheetState = promptSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.fillMaxHeight().statusBarsPadding()
        ) {
            var tempPrompt by remember { mutableStateOf(aiCustomPrompt) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Custom User Instructions",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Provide personalized instructions on how Swiffle should format answers or speak to you (e.g. \"Keep answers under 3 sentences\", \"Focus on technical details\").",
                    fontFamily = GoogleSans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tempPrompt,
                    onValueChange = { tempPrompt = it },
                    placeholder = { Text("Enter your custom system instructions and document persona preferences...") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Cancel and Save buttons in bottom right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showEditPromptDialog = false },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("Cancel", fontFamily = GoogleSans, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val cleaned = tempPrompt.trim()
                            aiCustomPrompt = cleaned
                            sharedPrefs.edit().putString("ai_custom_prompt", cleaned).apply()
                            showEditPromptDialog = false
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Save", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchRow(
        icon = icon,
        iconPainter = iconPainter,
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontFamily = GoogleSans,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  PROFILE PHOTO & NAME UPDATE MODAL
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun ProfileEditModal(
    currentName: String,
    currentAvatarUrl: String?,
    onDismiss: () -> Unit,
    onSave: (newName: String, newAvatarUrl: String?) -> Unit
) {
    val context = LocalContext.current
    var editedName by remember { mutableStateOf(currentName) }
    var selectedAvatarPath by remember { mutableStateOf(currentAvatarUrl) }
    var wasPhotoRemoved by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val avatarFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
                avatarFile.outputStream().use { output ->
                    inputStream?.copyTo(output)
                }
                selectedAvatarPath = avatarFile.absolutePath
                wasPhotoRemoved = false
            } catch (e: Exception) {
                selectedAvatarPath = uri.toString()
                wasPhotoRemoved = false
            }
        }
    }

    // 4-Color Gemini Aurora Gradient Brush for Avatar Halo
    val geminiAuroraBrush = remember {
        Brush.sweepGradient(
            colors = listOf(
                Color(0xFF4285F4), // Google Blue
                Color(0xFF9B51E0), // Gemini Violet
                Color(0xFFEA4335), // Google Red
                Color(0xFFFBBC05), // Google Yellow
                Color(0xFF34A853), // Google Green
                Color(0xFF4285F4)  // Close loop
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Update Profile",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Avatar Preview (Clickable to launch PhotoPicker)
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .border(width = 3.dp, brush = geminiAuroraBrush, shape = CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!selectedAvatarPath.isNullOrBlank() && !wasPhotoRemoved) {
                        val previewModel: Any = if (!selectedAvatarPath!!.startsWith("http")) {
                            java.io.File(selectedAvatarPath!!)
                        } else {
                            selectedAvatarPath!!
                        }
                        AsyncImage(
                            model = previewModel,
                            contentDescription = "Avatar Preview",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = (editedName.firstOrNull() ?: 'U').uppercase(),
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons for Avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedAvatarPath.isNullOrBlank() || wasPhotoRemoved) "Choose Photo" else "Change Photo",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        )
                    }

                    if (!selectedAvatarPath.isNullOrBlank() && !wasPhotoRemoved) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                wasPhotoRemoved = true
                                selectedAvatarPath = null
                            }
                        ) {
                            Text(
                                text = "Remove",
                                fontFamily = GoogleSans,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Name Input Field
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Display Name", fontFamily = GoogleSans) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = editedName.trim().ifBlank { currentName }
                    val finalAvatar = if (wasPhotoRemoved) null else selectedAvatarPath
                    onSave(finalName, finalAvatar)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", fontFamily = GoogleSans, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = GoogleSans)
            }
        },
        shape = RoundedCornerShape(26.dp)
    )
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  SUBSCRIPTIONS SUBPAGE (PLACEHOLDER ARCHITECTURE)
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
fun SubscriptionsSubpage(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }
    var activeTierId by remember {
        mutableStateOf(sharedPrefs.getString("pref_active_subscription_tier", "free") ?: "free")
    }

    var isAnnualBilling by remember { mutableStateOf(false) }
    var selectedProTierOption by remember { mutableIntStateOf(1) } // 0: Standard, 1: More Usage / Best Value, 2: Max Usage

    // Silky smooth flowing aurora border brush for whichever subscription card is currently active
    val infiniteTransition = rememberInfiniteTransition(label = "ActiveCardAuroraTransition")
    val borderRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ActiveCardBorderRotation"
    )
    val flowingAuroraCardBrush = remember(borderRotationAngle) {
        val angleRad = Math.toRadians(borderRotationAngle.toDouble())
        val cos = kotlin.math.cos(angleRad).toFloat()
        val sin = kotlin.math.sin(angleRad).toFloat()
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF4285F4), // Blue
                Color(0xFF9B51E0), // Violet
                Color(0xFFEA4335), // Red
                Color(0xFFFBBC05), // Yellow
                Color(0xFF34A853), // Green
                Color(0xFF4285F4)  // Loop
            ),
            start = androidx.compose.ui.geometry.Offset(500f * (1f - cos), 500f * (1f - sin)),
            end = androidx.compose.ui.geometry.Offset(500f * (1f + cos), 500f * (1f + sin))
        )
    }

    SettingsSubpageShell(
        title = "Manage Subscriptions",
        onBack = onBack
    ) {

        // ── WINUI 3 FLUENT SEGMENTED BILLING CYCLE TOGGLE ──
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                val fullWidth = maxWidth
                val halfWidth = fullWidth / 2
                val slideFraction by animateFloatAsState(
                    targetValue = if (isAnnualBilling) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "BillingCycleSlide"
                )

                // Seamless Moving Slider Pill (WinUI 3 4dp radius)
                Box(
                    modifier = Modifier
                        .width(halfWidth)
                        .height(40.dp)
                        .graphicsLayer {
                            translationX = (halfWidth.toPx()) * slideFraction
                        }
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(4.dp), spotColor = Color.Black.copy(alpha = 0.15f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                )

                // Tab Clickable Targets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Monthly Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isAnnualBilling = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Monthly",
                            fontFamily = GoogleSans,
                            fontWeight = if (!isAnnualBilling) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = if (!isAnnualBilling) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Annual Tab with SAVE 25% Badge
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { isAnnualBilling = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Annual",
                                fontFamily = GoogleSans,
                                fontWeight = if (isAnnualBilling) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.5.sp,
                                color = if (isAnnualBilling) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "SAVE 25%",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═════════════════════════════════════════════════════════════════════════
        // TIER 1: SWIFFLE PLUS (GEMINI 3.8 FLASH)
        // ═════════════════════════════════════════════════════════════════════════
        val isPlusActive = activeTierId == "plus"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isPlusActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
            border = if (isPlusActive) BorderStroke(1.dp, flowingAuroraCardBrush)
                     else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Plus",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isPlusActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "Your current plan",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "More access to advanced intelligence & 1M context",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AnimatedContent(
                            targetState = isAnnualBilling,
                            transitionSpec = {
                                if (targetState) {
                                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> height } + fadeOut())
                                } else {
                                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> -height } + fadeOut())
                                }
                            },
                            label = "PlusPriceSlide"
                        ) { annual ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (annual) "₹7,999" else "₹999",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (annual) "per year (₹666/mo)" else "per month",
                                    fontFamily = GoogleSans,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Pill (Outline pill for Manage Subscription, Solid for Switch)
                if (isPlusActive) {
                    OutlinedButton(
                        onClick = {
                            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                            context.startActivity(playIntent)
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Manage subscription",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                            context.startActivity(playIntent)
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0078D4),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Switch to Plus",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feature Checklist
                SubscriptionCheckItem("Everything in Free, plus:")
                SubscriptionCheckItem("5x more usage & access to Swiffle 1.0 Plus (powered by Google GenAI)")
                SubscriptionCheckItem("1,000,000 token context window (Book-length documents)")
                SubscriptionCheckItem("Advanced PDF summarization with structured executive abstracts")
                SubscriptionCheckItem("Multimodal vision: explain embedded charts, photos & diagrams")
                SubscriptionCheckItem("Intelligent handwriting recognition (Clear scans & notes)")
                SubscriptionCheckItem("Multilingual comprehension across 100+ languages")
                SubscriptionCheckItem("Semantic document analysis & contractual risk insights")
                SubscriptionCheckItem("Document comparison: side-by-side clause & revision detection")

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = isAnnualBilling,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "PlusRenewalSlide"
                ) { annual ->
                    Text(
                        text = if (annual) "Renews at ₹7,999/year. Cancel anytime." else "Renews at ₹999/month. Cancel anytime.",
                        fontFamily = GoogleSans,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═════════════════════════════════════════════════════════════════════════
        // TIER 2: SWIFFLE PRO (GEMINI 4 ARGON — DEEP REASONING & MATH)
        // ═════════════════════════════════════════════════════════════════════════
        val isProActive = activeTierId == "pro"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isProActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
            border = if (isProActive) BorderStroke(1.dp, flowingAuroraCardBrush)
                     else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                // Header Row with Professional Tier Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Pro",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0078D4)
                            ) {
                                Text(
                                    text = "EXPERT REASONING",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (isProActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "Your current plan",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Advanced mathematical analysis & complex document synthesis, powered by Google GenAI",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Interactive 3-Option Segmented Selector with Sliding Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 0: Standard
                    ProPricingTierCard(
                        priceText = if (isAnnualBilling) "₹34,999" else "₹3,999",
                        label = "Standard",
                        isSelected = selectedProTierOption == 0,
                        onClick = { selectedProTierOption = 0 },
                        modifier = Modifier.weight(1f)
                    )

                    // Option 1: More Usage / Best Value (Default)
                    ProPricingTierCard(
                        priceText = if (isAnnualBilling) "₹39,999" else "₹4,499",
                        label = "Best Value",
                        isSelected = selectedProTierOption == 1,
                        onClick = { selectedProTierOption = 1 },
                        modifier = Modifier.weight(1f)
                    )

                    // Option 2: Max Usage / Unlimited
                    ProPricingTierCard(
                        priceText = if (isAnnualBilling) "₹49,999" else "₹5,499",
                        label = "Max Usage",
                        isSelected = selectedProTierOption == 2,
                        onClick = { selectedProTierOption = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Pill (Outline pill for Manage Subscription, Solid for Switch)
                if (isProActive) {
                    OutlinedButton(
                        onClick = {
                            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                            context.startActivity(playIntent)
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Manage subscription",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                            context.startActivity(playIntent)
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0078D4),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Get Pro",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pro Feature Checklist
                SubscriptionCheckItem("Everything in Plus, plus:")
                SubscriptionCheckItem("10x more usage & access to Swiffle 1.0 Pro (powered by Google GenAI)")
                SubscriptionCheckItem("Advanced mathematical & actuarial reasoning with rendered LaTeX")
                SubscriptionCheckItem("Complex document synthesis & deep logical chain-of-thought")
                SubscriptionCheckItem("Multi-Document Workspace: cross-analyze up to 10 PDFs simultaneously")
                SubscriptionCheckItem("Detailed report generation: export executive briefs to PDF")
                SubscriptionCheckItem("Advanced table & data analysis: direct export to structured Excel")
                SubscriptionCheckItem("Priority processing pipeline (Sub-second dedicated cloud queue)")
                SubscriptionCheckItem("Uncapped standalone AI chat interface with full native app access")

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = isAnnualBilling,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ProRenewalSlide"
                ) { annual ->
                    val proRenewalPrice = when (selectedProTierOption) {
                        0 -> if (annual) "₹34,999/year" else "₹3,999/month"
                        1 -> if (annual) "₹39,999/year" else "₹4,499/month"
                        else -> if (annual) "₹49,999/year" else "₹5,499/month"
                    }
                    Text(
                        text = "Auto-renews at $proRenewalPrice. Cancel anytime.",
                        fontFamily = GoogleSans,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═════════════════════════════════════════════════════════════════════════
        // TIER 3: BUSINESS (TEAM WORKSPACES & POOLED QUOTAS)
        // ═════════════════════════════════════════════════════════════════════════
        val isBusinessActive = activeTierId == "business"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isBusinessActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
            border = if (isBusinessActive) BorderStroke(1.dp, flowingAuroraCardBrush)
                     else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Business",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "TEAMS",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (isBusinessActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "Your current plan",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Centralized intelligence & shared vaults for teams",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AnimatedContent(
                            targetState = isAnnualBilling,
                            transitionSpec = {
                                if (targetState) {
                                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> height } + fadeOut())
                                } else {
                                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> -height } + fadeOut())
                                }
                            },
                            label = "BusinessPriceSlide"
                        ) { annual ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (annual) "₹99,999" else "₹9,999",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (annual) "per team/yr" else "per team/mo",
                                    fontFamily = GoogleSans,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Pill (Outline pill for Manage Subscription, Solid for Switch)
                if (isBusinessActive) {
                    OutlinedButton(
                        onClick = {
                            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                            context.startActivity(playIntent)
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Manage subscription",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                            context.startActivity(playIntent)
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0078D4),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Upgrade to Business",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                SubscriptionCheckItem("Up to 10 team seats with shared workspace vaults")
                SubscriptionCheckItem("5,000+ monthly team pooled intelligence queries")
                SubscriptionCheckItem("Frontier enterprise AI routing, powered by Google GenAI")
                SubscriptionCheckItem("Centralized billing & administrator audit dashboard")
                SubscriptionCheckItem("Team document collaboration & synchronized annotations")

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = isAnnualBilling,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "BusinessRenewalSlide"
                ) { annual ->
                    Text(
                        text = if (annual) "Renews at ₹99,999/year. Cancel anytime." else "Renews at ₹9,999/month. Cancel anytime.",
                        fontFamily = GoogleSans,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ═════════════════════════════════════════════════════════════════════════
        // TIER 4: FREE PLAN (USER'S ACTIVE TIER — CHATGPT STYLE)
        // ═════════════════════════════════════════════════════════════════════════
        val isFreeActive = activeTierId == "free"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isFreeActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
            border = if (isFreeActive) BorderStroke(1.dp, flowingAuroraCardBrush)
                     else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                // Free Tier Title & Your current plan aligned on the same horizontal line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Free Tier",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isFreeActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Your current plan",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle uses horizontal space freely
                Text(
                    text = "Essential on-device & lightweight cloud AI",
                    fontFamily = GoogleSans,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Manage Subscription Outline Rounded Corner Pill
                OutlinedButton(
                    onClick = {
                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
                        context.startActivity(playIntent)
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Manage subscription",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SubscriptionCheckItem("Swiffle 1.0 Nano: 100% private, on-device intelligence")
                SubscriptionCheckItem("Lightning-fast responses with low-latency processing")
                SubscriptionCheckItem("Ask questions about PDF text & concepts (Daily limits apply)")
                SubscriptionCheckItem("Explain & simplify points instantly")
                SubscriptionCheckItem("Fast, lightweight document summaries")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── BOTTOM UTILITIES: RESTORE SUBSCRIPTION & ENTERPRISE ──────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "Querying active Google Play purchases...", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Restore subscription",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Looking for custom VPC or enterprise governance? Contact enterprise@swiffle.app",
                fontFamily = GoogleSans,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Reusable Material 3 Expressive Feature Checklist Item
 */
@Composable
private fun SubscriptionCheckItem(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Interactive 3-Option Segmented Price Card for Pro Plan (ChatGPT style)
 */
@Composable
private fun ProPricingTierCard(
    priceText: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) Color(0xFF0078D4) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            AnimatedContent(
                targetState = priceText,
                transitionSpec = {
                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> height } + fadeOut())
                },
                label = "ProSegmentPriceSlide"
            ) { targetPrice ->
                Text(
                    text = targetPrice,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) Color(0xFF0078D4) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
