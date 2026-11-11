package com.swiffle.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.AlertDialog
import com.swiffle.app.R
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.swiffle.app.auth.AuthState
import com.swiffle.app.auth.OrigamiAuth
import com.swiffle.app.theme.GoogleSans
import com.swiffle.app.theme.SwiffleNavyPrimary
import com.swiffle.app.ui.components.GoogleBrandIcon
import com.swiffle.app.ui.components.GoogleSignInButton
import com.swiffle.app.ui.components.StockMaterialCheckmark
import com.swiffle.app.ui.components.StockMaterialLogoLoadingScreen
import com.swiffle.app.ui.components.StockMaterialWavyLoadingIndicator
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Consolidated 3-Stage Stock Material 4 Pro Onboarding Flow:
 * - Stage 0: Auth Gateway with Terms Agreement Checkbox, Live Spinner Pill, Guest Hyperlink, and Play Protect Footer
 * - Stage 1: The Unified "1 to 5" Power Hub (Scrollable, Animated Down Arrow, Mandatory Storage Setup Gate)
 * - Stage 2: Celebratory Setup Complete with Authentic Stock Material Checkmark Badge
 */
@Composable
fun OnboardingScreen(
    onAllSetReached: () -> Unit = {},
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentStage by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val authState by OrigamiAuth.authState.collectAsState()

    // Seamlessly notify parent container when reaching All Set celebration stage
    LaunchedEffect(currentStage) {
        if (currentStage == 2) {
            onAllSetReached()
        }
    }

    var isAgreedToTerms by remember { mutableStateOf(false) }
    var isSpinningAuth by remember { mutableStateOf(false) }

    // Play-compliant Scoped Storage & SAF readiness (zero broad storage permissions required)
    var isStorageGranted by remember { mutableStateOf(true) }

    fun checkPermissions() {
        isStorageGranted = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        checkPermissions()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isExiting by remember { mutableStateOf(false) }

    fun triggerExit() {
        coroutineScope.launch {
            isExiting = true
            delay(350)
            onOnboardingComplete()
        }
    }

    var isAuthSuccess by remember { mutableStateOf(false) }
    var isPostLoginLoading by remember { mutableStateOf(false) }
    var isPostSetupLoading by remember { mutableStateOf(false) }

    // Auto-advance with intentional 2-second native loading screen after sign-in success
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Authenticated -> {
                isSpinningAuth = false
                isAuthSuccess = true
                isPostLoginLoading = true
                delay(1200)
                // Advance directly to next page UNDER the loading screen before fading out
                currentStage = 1
                delay(60)
                isPostLoginLoading = false
                isAuthSuccess = false
            }
            is AuthState.Idle -> {
                isSpinningAuth = false
                isAuthSuccess = false
            }
            is AuthState.Error -> {
                isSpinningAuth = false
                isAuthSuccess = false
                isPostLoginLoading = false
                if (!OrigamiAuth.isUserCancellation(Exception(state.message))) {
                    snackbarHostState.showSnackbar(state.message)
                }
            }
            else -> {}
        }
    }

    val surfaceBg = MaterialTheme.colorScheme.background

    // Once proceeded, strictly NO going back to any earlier page
    BackHandler(enabled = currentStage > 0) {
        // Intercept and swallow back press: forward progress only
    }

    val exitAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isExiting) 0f else 1f,
        animationSpec = tween(320, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "exit_alpha"
    )
    val exitScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isExiting) 0.95f else 1f,
        animationSpec = tween(320, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "exit_scale"
    )

    val isAnyLoading = isPostLoginLoading || isPostSetupLoading

    // Authentic Fullscreen Backdrop Blur: smoothly blurs underlying screens while loading overlay is active
    val backdropBlurRadius by animateDpAsState(
        targetValue = if (isAnyLoading) 24.dp else 0.dp,
        animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "onboarding_backdrop_blur"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = exitAlpha
                    scaleX = exitScale
                    scaleY = exitScale
                }
        ) {
            // Stage Content with smooth backdrop blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdropBlurRadius > 0.dp) {
                            Modifier.blur(backdropBlurRadius)
                        } else {
                            Modifier
                        }
                    )
            ) {
                // Fluent Horizontal Slide & Fade Across Onboarding Stages
                AnimatedContent(
                    targetState = currentStage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            com.swiffle.app.theme.SwiffleMotion.horizontalForward()
                        } else {
                            com.swiffle.app.theme.SwiffleMotion.horizontalBackward()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "fluent_onboarding_stage_transition"
                ) { stage ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (stage) {
                        0 -> StageZeroAuthGate(
                            authState = authState,
                            isSpinningAuth = isSpinningAuth,
                            isAuthSuccess = isAuthSuccess,
                            onGoogleSignInClick = {
                                coroutineScope.launch {
                                    isSpinningAuth = true
                                    try {
                                        val result = OrigamiAuth.signInWithGoogle(context)
                                        result.onFailure { error ->
                                            if (!OrigamiAuth.isUserCancellation(error)) {
                                                snackbarHostState.showSnackbar(
                                                    error.localizedMessage ?: "Google Sign-In could not be completed"
                                                )
                                            }
                                        }
                                    } finally {
                                        // Enterprise Grade: Instant frame-0 reset when dismissed!
                                        isSpinningAuth = false
                                    }
                                }
                            },
                            onEmailSignInClick = { email, pass, rememberMe ->
                                isSpinningAuth = true
                                coroutineScope.launch {
                                    val res = OrigamiAuth.signInWithEmail(email, pass)
                                    if (res.isFailure) {
                                        isSpinningAuth = false
                                        snackbarHostState.showSnackbar(res.exceptionOrNull()?.localizedMessage ?: "Invalid email or password")
                                    } else {
                                        context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("is_user_logged_in", true)
                                            .putBoolean("hasCompletedOnboarding", true)
                                            .putString("google_user_email", email)
                                            .apply()
                                        isSpinningAuth = false
                                        isPostLoginLoading = true
                                        delay(2000)
                                        currentStage = 1
                                        delay(60)
                                        isPostLoginLoading = false
                                    }
                                }
                            },
                            onEmailSignUpClick = { name, email, pass ->
                                isSpinningAuth = true
                                coroutineScope.launch {
                                    val res = OrigamiAuth.signUpWithEmail(email, pass)
                                    if (res.isFailure) {
                                        isSpinningAuth = false
                                        snackbarHostState.showSnackbar(res.exceptionOrNull()?.localizedMessage ?: "Failed to create account")
                                    } else {
                                        if (name.isNotBlank()) {
                                            context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                                                .edit()
                                                .putString("google_user_name", name)
                                                .apply()
                                        }
                                        context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("is_user_logged_in", true)
                                            .putBoolean("hasCompletedOnboarding", true)
                                            .putString("google_user_email", email)
                                            .apply()
                                        isSpinningAuth = false
                                        isPostLoginLoading = true
                                        delay(2000)
                                        currentStage = 1
                                        delay(60)
                                        isPostLoginLoading = false
                                    }
                                }
                            },
                            onGuestClick = {
                                context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_user_logged_in", true)
                                    .putString("user_mode", "guest")
                                    .remove("google_user_email")
                                    .remove("google_user_name")
                                    .remove("google_user_avatar")
                                    .remove("google_user_avatar_url")
                                    .apply()
                                try {
                                    val oldGuestAvatar = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
                                    if (oldGuestAvatar.exists()) oldGuestAvatar.delete()
                                } catch (_: Exception) {}
                                coroutineScope.launch {
                                    isPostLoginLoading = true
                                    delay(2000)
                                    currentStage = 1
                                    delay(60)
                                    isPostLoginLoading = false
                                }
                            }
                        )
                        1 -> StageOneFeatureHub(
                            isStorageGranted = isStorageGranted,
                            onGrantStorage = {
                                isStorageGranted = true
                            },
                            onCompleteSetup = {
                                coroutineScope.launch {
                                    isPostSetupLoading = true
                                    delay(2000)
                                    currentStage = 2
                                    delay(60)
                                    isPostSetupLoading = false
                                }
                            }
                        )
                        2 -> StageTwoCelebration(
                            isVisible = currentStage == 2,
                            onLaunch = { triggerExit() }
                        )
                    }
                }
            }
        }

        // Fullscreen Backdrop-Blurred Loading Screen with Shimmering Origami Logo
        StockMaterialLogoLoadingScreen(
            visible = isPostLoginLoading || isPostSetupLoading,
            title = if (isPostLoginLoading) "Syncing Preferences..." else "Finalizing Setup...",
            subtitle = if (isPostLoginLoading)
                "Connecting cloud profile & preparing your PDF workspace"
            else
                "Indexing storage permissions & configuring high-resolution display"
        )
        }
    }
}

/**
 * Stage 0: 2-Screen Pure Material 3 Expressive Auth Studio:
 * - Sub-screen 0: The Sanctuary / Welcome Gateway (Gemini Multi-Color Aurora Mesh, Launch Ignition Emblem, Monolithic "Get Started", Guest link)
 * - Sub-screen 1: The Studio / Enterprise Credentials (Option C: "Welcome to Your Studio", Material 3 Inputs, Single Google Pill, Mode Switcher)
 */
@Composable
private fun StageZeroAuthGate(
    authState: AuthState,
    onGoogleSignInClick: () -> Unit,
    onEmailSignInClick: (String, String, Boolean) -> Unit,
    onEmailSignUpClick: (String, String, String) -> Unit,
    onGuestClick: () -> Unit,
    isSpinningAuth: Boolean,
    isAuthSuccess: Boolean = false
) {
    var subScreen by remember { mutableStateOf(0) }
    var isSignUpMode by remember { mutableStateOf(false) }

    // Intercept back button when inside Credential Studio to glide smoothly back to Gateway
    BackHandler(enabled = subScreen == 1) {
        subScreen = 0
    }

    AnimatedContent(
        targetState = subScreen,
        transitionSpec = {
            if (targetState > initialState) {
                com.swiffle.app.theme.SwiffleMotion.horizontalForward()
            } else {
                com.swiffle.app.theme.SwiffleMotion.horizontalBackward()
            }
        },
        modifier = Modifier.fillMaxSize(),
        label = "auth_subscreen_transition"
    ) { screen ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (screen) {
                0 -> AuthSanctuaryGateway(
                    onGetStarted = {
                        isSignUpMode = false
                        subScreen = 1
                    },
                    onGuestClick = onGuestClick
                )
                1 -> AuthStudioCredentialScreen(
                    isSignUpMode = isSignUpMode,
                    onToggleMode = { isSignUpMode = !isSignUpMode },
                    onBack = {
                        subScreen = 0
                    },
                    authState = authState,
                    isSpinningAuth = isSpinningAuth,
                    isAuthSuccess = isAuthSuccess,
                    onGoogleSignInClick = onGoogleSignInClick,
                    onEmailSignInClick = onEmailSignInClick,
                    onEmailSignUpClick = onEmailSignUpClick,
                    onGuestClick = onGuestClick
                )
            }
        }
    }
}

/**
 * Screen 1: The Sanctuary / Welcome Gateway
 * Features animated Launch Ignition for the Swiffle logo, breathing Gemini Aurora,
 * and a single, monolithic "Get Started" pill button.
 */
@Composable
private fun AuthSanctuaryGateway(
    onGetStarted: () -> Unit,
    onGuestClick: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    // Gentle rhythmic floating motion for logo starting at 0f (exact splash handoff)
    val infiniteTransition = rememberInfiniteTransition(label = "logo_motion")
    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating_offset"
    )

    // Language selection state
    var selectedLanguage by remember { mutableStateOf("English (US)") }
    var showLanguageMenu by remember { mutableStateOf(false) }
    val availableLanguages = listOf("English (US)", "Español", "Français", "Deutsch", "日本語", "हिन्दी")

    // Subtle minimal celestial star burst (Single gentle cadence every 3200ms)
    val starBurstProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "minimal_star_burst"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- TOP-MIDDLE LANGUAGE SELECTOR PILL (Pure White Floating Capsule) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = { showLanguageMenu = true },
                shape = RoundedCornerShape(4.dp),
                color = Color.White.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0x18000000)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_internet_globe),
                        contentDescription = "Language",
                        tint = Color(0xFF0078D4),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = selectedLanguage,
                        fontFamily = GoogleSans,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Windows 11 WinUI 3 MenuFlyout (8px corner radius, 1px contour border)
            androidx.compose.material3.DropdownMenu(
                expanded = showLanguageMenu,
                onDismissRequest = { showLanguageMenu = false },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x18000000), RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp)
            ) {
                availableLanguages.forEach { lang ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = lang,
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                fontWeight = if (lang == selectedLanguage) FontWeight.Bold else FontWeight.Normal,
                                color = if (lang == selectedLanguage) primaryColor else Color(0xFF0F172A)
                            )
                        },
                        leadingIcon = {
                            if (lang == selectedLanguage) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = {
                            selectedLanguage = lang
                            showLanguageMenu = false
                        }
                    )
                }
            }
        }

        // --- CENTER HERO CONTENT (Pixel-Perfect Parity with SplashScreen for Zero-Cut Handoff) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emblem with Rhythmic Celestial Diamond Star Burst
            Box(
                modifier = Modifier.size(130.dp),
                contentAlignment = Alignment.Center
            ) {
                // Classy Minimal Celestial Star Burst Particles radiating softly from Emblem
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .size(260.dp)
                        .graphicsLayer {
                            clip = false
                            translationY = floatingOffset
                        }
                ) {
                    drawClassyStarBurst(progress = starBurstProgress, center = center)
                }

                Image(
                    painter = painterResource(id = R.drawable.ic_swiffle_logo),
                    contentDescription = "Swiffle Logo",
                    modifier = Modifier
                        .size(130.dp)
                        .graphicsLayer {
                            translationY = floatingOffset
                        }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Brand Wordmark
            Text(
                text = "Swiffle",
                fontFamily = GoogleSans,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Speed, clarity, and intelligence in every page.",
                fontFamily = GoogleSans,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // --- BOTTOM ACTION STACK ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // WinUI 3 Fluent Cobalt Blue "Get Started" Primary Button
            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0078D4),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 1.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Get Started",
                        fontFamily = GoogleSans,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.25.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // WinUI 3 Subtle Secondary Guest Button
            Surface(
                onClick = onGuestClick,
                shape = RoundedCornerShape(4.dp),
                color = Color.White.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, Color(0x18000000)),
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Continue as a Guest",
                        fontFamily = GoogleSans,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = Color(0xFF334155)
                    )
                }
            }
        }
    }
}

/**
 * Draws a celestial diamond star burst wave radiating at equal timed intervals
 * from the perimeter of the Swiffle SVG emblem into the ambient aurora mesh.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClassyStarBurst(
    progress: Float,
    center: androidx.compose.ui.geometry.Offset
) {
    if (progress <= 0.02f || progress >= 0.98f) return

    val startRadius = 66.dp.toPx()
    val maxRadius = 118.dp.toPx()
    val currentRadius = startRadius + (progress * (maxRadius - startRadius))

    // Soft, minimal bell-curve alpha: subtle starlight shimmer
    val alpha = (kotlin.math.sin(progress * Math.PI.toFloat()) * 0.45f).coerceIn(0f, 1f)
    val starCount = 5
    val baseAngleOffset = progress * 0.22f

    for (i in 0 until starCount) {
        val angle = (i * (2 * Math.PI / starCount) + baseAngleOffset).toFloat()
        val px = center.x + kotlin.math.cos(angle) * currentRadius
        val py = center.y + kotlin.math.sin(angle) * currentRadius

        val isPrimary = i % 2 == 0
        val starColor = when (i % 4) {
            0 -> Color(0xFF4285F4) // Google Blue
            1 -> Color(0xFF9B72CB) // Gemini Violet
            2 -> Color(0xFFF4B400) // Amber Gold
            else -> Color.White   // Pure Celestial Diamond
        }

        // Minimal, crisp micro-sparkle sizes
        val starRadius = if (isPrimary) {
            (2.2.dp.toPx() * (1f - progress * 0.35f))
        } else {
            (1.5.dp.toPx() * (1f - progress * 0.25f))
        }

        // Crisp minimal 4-pointed micro diamond star
        val starRotationRad = angle + (progress * 1.25f)
        drawDiamondStar(
            center = androidx.compose.ui.geometry.Offset(px, py),
            radius = starRadius,
            color = starColor.copy(alpha = alpha),
            rotationAngleRad = starRotationRad
        )
    }
}

/**
 * Draws a mathematical 4-pointed curved diamond star (astroid sparkle).
 * Matches official Google Gemini and Material 3 Pro celestial sparkle design.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiamondStar(
    center: androidx.compose.ui.geometry.Offset,
    radius: Float,
    color: Color,
    rotationAngleRad: Float
) {
    val cosA = kotlin.math.cos(rotationAngleRad)
    val sinA = kotlin.math.sin(rotationAngleRad)

    fun rotX(dx: Float, dy: Float): Float = center.x + (dx * cosA - dy * sinA)
    fun rotY(dx: Float, dy: Float): Float = center.y + (dx * sinA + dy * cosA)

    val r = radius
    val p = r * 0.20f

    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(rotX(0f, -r), rotY(0f, -r))
        quadraticBezierTo(rotX(p, -p), rotY(p, -p), rotX(r, 0f), rotY(r, 0f))
        quadraticBezierTo(rotX(p, p), rotY(p, p), rotX(0f, r), rotY(0f, r))
        quadraticBezierTo(rotX(-p, p), rotY(-p, p), rotX(-r, 0f), rotY(-r, 0f))
        quadraticBezierTo(rotX(-p, -p), rotY(-p, -p), rotX(0f, -r), rotY(0f, -r))
        close()
    }
    drawPath(path, color)
}

/**
 * Screen 2: The Studio / Enterprise Credentials
 * Features Option C Greeting ("Welcome to Your Studio."), Material 3 TextFields,
 * single Lean Google Pill, and smooth Log In <-> Create Account mode toggling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuthStudioCredentialScreen(
    isSignUpMode: Boolean,
    onToggleMode: () -> Unit,
    onBack: () -> Unit,
    authState: AuthState,
    isSpinningAuth: Boolean,
    isAuthSuccess: Boolean,
    onGoogleSignInClick: () -> Unit,
    onEmailSignInClick: (String, String, Boolean) -> Unit,
    onEmailSignUpClick: (String, String, String) -> Unit,
    onGuestClick: () -> Unit
) {
    val context = LocalContext.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val sharedPref = remember { context.getSharedPreferences("OrigamiPrefs", android.content.Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(sharedPref.getString("saved_login_email", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(sharedPref.getBoolean("remember_me_enabled", true)) }

    var showGuestWarningDialog by remember { mutableStateOf(false) }

    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    var isNameTouched by remember { mutableStateOf(false) }
    var isEmailTouched by remember { mutableStateOf(false) }
    var isPasswordTouched by remember { mutableStateOf(false) }

    val emailTrimmed = email.trim()
    val isEmailFormatValid = android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrimmed).matches()
    val isEmailValid = emailTrimmed.isNotEmpty() && isEmailFormatValid
    val emailError: String? = when {
        !hasAttemptedSubmit && !isEmailTouched -> null
        emailTrimmed.isEmpty() -> if (hasAttemptedSubmit) "Email address is required." else null
        !isEmailFormatValid -> "Please enter a valid email address (e.g. name@domain.com)."
        else -> null
    }

    val isPasswordLengthValid = password.length >= 6
    val passwordError: String? = when {
        !hasAttemptedSubmit && !isPasswordTouched -> null
        password.isEmpty() -> if (hasAttemptedSubmit) "Password is required." else null
        password.length < 6 -> "Password must be at least 6 characters long."
        else -> null
    }

    // Password strength score (0 to 4)
    val passwordStrength: Int = remember(password) {
        if (password.isEmpty()) 0
        else {
            var score = 0
            if (password.length >= 6) score++
            if (password.length >= 10) score++
            if (password.any { it.isDigit() }) score++
            if (password.any { !it.isLetterOrDigit() } || password.any { it.isUpperCase() }) score++
            score
        }
    }

    val nameTrimmed = name.trim()
    val isNameValid = !isSignUpMode || nameTrimmed.length >= 2
    val nameError: String? = if (isSignUpMode) {
        when {
            !hasAttemptedSubmit && !isNameTouched -> null
            nameTrimmed.isEmpty() -> if (hasAttemptedSubmit) "Full name is required." else null
            nameTrimmed.length < 2 -> "Name must be at least 2 characters."
            else -> null
        }
    } else null

    val focusManager = LocalFocusManager.current

    fun validateAndSubmit() {
        hasAttemptedSubmit = true
        isEmailTouched = true
        isPasswordTouched = true
        if (isSignUpMode) isNameTouched = true

        if (isEmailValid && isPasswordLengthValid && isNameValid) {
            focusManager.clearFocus()
            if (rememberMe) {
                sharedPref.edit()
                    .putString("saved_login_email", emailTrimmed)
                    .putBoolean("remember_me_enabled", true)
                    .apply()
            } else {
                sharedPref.edit()
                    .remove("saved_login_email")
                    .putBoolean("remember_me_enabled", false)
                    .apply()
            }
            if (isSignUpMode) {
                onEmailSignUpClick(nameTrimmed, emailTrimmed, password)
            } else {
                onEmailSignInClick(emailTrimmed, password, rememberMe)
            }
        }
    }

    val isLoading = authState is AuthState.Loading || isSpinningAuth
    val scrollState = rememberScrollState()

    // Subtle ethereal floating levitation for Studio SVG emblem
    val infiniteTransition = rememberInfiniteTransition(label = "studio_logo_float")
    val studioFloatingOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "studio_floating_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Centered Floating Swiffle Emblem
            Image(
                painter = painterResource(id = R.drawable.ic_swiffle_logo),
                contentDescription = "Swiffle Logo",
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        translationY = studioFloatingOffset
                    }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Enterprise Greeting with Smooth Crossfade Animation
            AnimatedContent(
                targetState = isSignUpMode,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically { it / 4 }).togetherWith(
                        fadeOut(tween(160)) + slideOutVertically { -it / 4 }
                    )
                },
                label = "auth_greeting_anim"
            ) { signUp ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (signUp) "Create Your Account." else "Welcome to Your Studio.",
                        fontFamily = GoogleSans,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (signUp)
                            "Join Swiffle to sync your document workspace, unlock AI insights, and access high-speed PDF tools."
                        else
                            "Continuous, encrypted access to your high-performance document suite across devices.",
                        fontFamily = GoogleSans,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.5.sp,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // WinUI 3 Fluent Segmented Switcher (Sign In <-> Create Account)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.65f))
                    .border(
                        1.dp,
                        Color(0x18000000),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(3.dp)
            ) {
                val tabWidth = maxWidth / 2
                val animatedPillOffset by animateDpAsState(
                    targetValue = if (isSignUpMode) tabWidth else 0.dp,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "auth_segmented_thumb_offset"
                )

                // WinUI 3 Elevated Solid Layer Sliding Thumb
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, Color(0x18000000)),
                    modifier = Modifier
                        .offset(x = animatedPillOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                ) {}

                // Foreground Interactive Clickable Labels
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sign In Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isSignUpMode) {
                                    hasAttemptedSubmit = false
                                    isNameTouched = false
                                    isEmailTouched = false
                                    isPasswordTouched = false
                                    onToggleMode()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val signInTextColor by animateColorAsState(
                            targetValue = if (!isSignUpMode) Color(0xFF0078D4) else Color(0xFF64748B),
                            animationSpec = tween(220),
                            label = "sign_in_text_color"
                        )
                        Text(
                            text = "Sign In",
                            fontFamily = GoogleSans,
                            fontSize = 14.sp,
                            fontWeight = if (!isSignUpMode) FontWeight.Bold else FontWeight.Medium,
                            color = signInTextColor
                        )
                    }

                    // Create Account Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSignUpMode) {
                                    hasAttemptedSubmit = false
                                    isNameTouched = false
                                    isEmailTouched = false
                                    isPasswordTouched = false
                                    onToggleMode()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val signUpTextColor by animateColorAsState(
                            targetValue = if (isSignUpMode) Color(0xFF0078D4) else Color(0xFF64748B),
                            animationSpec = tween(220),
                            label = "sign_up_text_color"
                        )
                        Text(
                            text = "Create Account",
                            fontFamily = GoogleSans,
                            fontSize = 14.sp,
                            fontWeight = if (isSignUpMode) FontWeight.Bold else FontWeight.Medium,
                            color = signUpTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // --- WINUI 3 FLUENT LAYER 1 CREDENTIAL CARD ---
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.85f)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp
                ),
                border = BorderStroke(1.dp, Color(0x18000000)),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = Color(0x12000000),
                        spotColor = Color(0x12000000)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    // Full Name (Only on Sign Up)
                    AnimatedVisibility(
                        visible = isSignUpMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { 
                                    name = it 
                                    isNameTouched = true
                                },
                                label = { Text("Full Name", fontFamily = GoogleSans) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = if (nameError != null) MaterialTheme.colorScheme.error else Color(0xFF64748B)
                                    )
                                },
                                isError = nameError != null,
                                supportingText = nameError?.let { msg ->
                                    {
                                        Text(
                                            text = msg,
                                            fontFamily = GoogleSans,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                shape = RoundedCornerShape(4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.60f),
                                    focusedBorderColor = Color(0xFF0078D4),
                                    unfocusedBorderColor = Color(0xFFD0D7E3).copy(alpha = 0.80f),
                                    focusedLabelColor = Color(0xFF0078D4),
                                    unfocusedLabelColor = Color(0xFF64748B),
                                    focusedTextColor = Color(0xFF0F172A),
                                    unfocusedTextColor = Color(0xFF0F172A),
                                    cursorColor = Color(0xFF0078D4)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // Email Field with Strict Validation & Live Validation Indicator
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it 
                            isEmailTouched = true
                        },
                        label = { Text("Email Address", fontFamily = GoogleSans) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_material_email),
                                contentDescription = null,
                                tint = if (emailError != null) MaterialTheme.colorScheme.error else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (isEmailValid) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Valid email",
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        isError = emailError != null,
                        supportingText = emailError?.let { msg ->
                            {
                                Text(
                                    text = msg,
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White.copy(alpha = 0.60f),
                            focusedBorderColor = Color(0xFF0078D4),
                            unfocusedBorderColor = Color(0xFFD0D7E3).copy(alpha = 0.80f),
                            focusedLabelColor = Color(0xFF0078D4),
                            unfocusedLabelColor = Color(0xFF64748B),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            cursorColor = Color(0xFF0078D4)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it 
                            isPasswordTouched = true
                        },
                        label = { Text("Password", fontFamily = GoogleSans) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_material_lock),
                                contentDescription = null,
                                tint = if (passwordError != null) MaterialTheme.colorScheme.error else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = if (passwordError != null) MaterialTheme.colorScheme.error else Color(0xFF64748B)
                                )
                            }
                        },
                        isError = passwordError != null,
                        supportingText = passwordError?.let { msg ->
                            {
                                Text(
                                    text = msg,
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { validateAndSubmit() }
                        ),
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White.copy(alpha = 0.60f),
                            focusedBorderColor = Color(0xFF0078D4),
                            unfocusedBorderColor = Color(0xFFD0D7E3).copy(alpha = 0.80f),
                            focusedLabelColor = Color(0xFF0078D4),
                            unfocusedLabelColor = Color(0xFF64748B),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            cursorColor = Color(0xFF0078D4)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Interactive Password Strength Meter (Shown on Sign Up when user types)
                    if (isSignUpMode && password.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (bar in 1..4) {
                                    val barColor = when {
                                        passwordStrength >= bar -> when (passwordStrength) {
                                            1 -> Color(0xFFEF4444) // Weak
                                            2 -> Color(0xFFF59E0B) // Fair
                                            3 -> Color(0xFF3B82F6) // Good
                                            else -> Color(0xFF16A34A) // Strong
                                        }
                                        else -> Color(0xFFE2E5EB)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val strengthLabel = when (passwordStrength) {
                                1 -> "Weak password"
                                2 -> "Moderate password"
                                3 -> "Good password"
                                4 -> "Strong & resilient password"
                                else -> "Too short"
                            }
                            val labelColor = when (passwordStrength) {
                                1 -> Color(0xFFEF4444)
                                2 -> Color(0xFFF59E0B)
                                3 -> Color(0xFF3B82F6)
                                4 -> Color(0xFF16A34A)
                                else -> Color(0xFF94A3B8)
                            }
                            Text(
                                text = strengthLabel,
                                fontFamily = GoogleSans,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = labelColor,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }

                    // Remember Me / Forgot Password Row (On Sign In Mode)
                    if (!isSignUpMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        rememberMe = !rememberMe
                                        sharedPref.edit().putBoolean("remember_me_enabled", rememberMe).apply()
                                    }
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = {
                                        rememberMe = it
                                        sharedPref.edit().putBoolean("remember_me_enabled", it).apply()
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SwiffleNavyPrimary,
                                        checkmarkColor = Color.White
                                    )
                                )
                                Text(
                                    text = "Remember me",
                                    fontFamily = GoogleSans,
                                    fontSize = 13.sp,
                                    color = Color(0xFF475569)
                                )
                            }

                            Text(
                                text = "Forgot Password?",
                                fontFamily = GoogleSans,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://supabase.com"))
                                        )
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // WinUI 3 Fluent Cobalt Blue Primary Action Button
                    Button(
                        onClick = { validateAndSubmit() },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0078D4),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF0078D4).copy(alpha = 0.40f),
                            disabledContentColor = Color.White.copy(alpha = 0.65f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 1.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        AnimatedContent(
                            targetState = isSignUpMode,
                            transitionSpec = {
                                (fadeIn(tween(200)) + slideInVertically { it / 2 }).togetherWith(
                                    fadeOut(tween(150)) + slideOutVertically { -it / 2 }
                                )
                            },
                            label = "btn_auth_text_anim"
                        ) { isSignUp ->
                            Text(
                                text = if (isSignUp) "Create Account" else "Sign In",
                                fontFamily = GoogleSans,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 0.25.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Minimalist Divider: "─── Or continue with ───"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color(0xFFE2E5EB))
                        )
                        Text(
                            text = "  Or continue with  ",
                            fontFamily = GoogleSans,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color(0xFFE2E5EB))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Single Google Pill Button with Official G Logo
                    GoogleSignInButton(
                        onClick = {
                            focusManager.clearFocus()
                            onGoogleSignInClick()
                        },
                        isLoading = isLoading,
                        isSpinning = isSpinningAuth,
                        isSuccess = isAuthSuccess,
                        enabled = !isLoading,
                        text = "Sign in with Google",
                        spinnerText = "Connecting to Google...",
                        successText = "Sign in Successful",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // WinUI 3 ContentDialog - Guest Warning Dialog
    if (showGuestWarningDialog) {
        AlertDialog(
            onDismissRequest = { showGuestWarningDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFD3E3FD)),
                    contentAlignment = Alignment.Center
                ) {
                    GoogleBrandIcon(modifier = Modifier.size(24.dp))
                }
            },
            title = {
                Text(
                    text = "Google Drive Integration will not be available",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Documents stay on this device. Cloud sync, multi-device access, and Google Drive backup will be disabled unless you sign in with Google.",
                    fontFamily = GoogleSans,
                    fontSize = 14.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuestWarningDialog = false
                        onGoogleSignInClick()
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0078D4)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GoogleBrandIcon(modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue using Google",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGuestWarningDialog = false
                        onGuestClick()
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Proceed Anyways",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

/**
 * Stage 1: The Unified "1 to 5" Power Hub (Scrollable, Animated Down Arrow, Mandatory Complete Setup Gate)
 */
@Composable
private fun StageOneFeatureHub(
    isStorageGranted: Boolean,
    onGrantStorage: () -> Unit,
    onCompleteSetup: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val successColor = Color(0xFF198754)
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Bouncing Down Arrow Animation
    val infiniteTransition = rememberInfiniteTransition(label = "arrow_bounce")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_y"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Everything in One Place",
                fontFamily = GoogleSans,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Next-generation PDF architecture with Google Cloud & on-device privacy.",
                fontFamily = GoogleSans,
                fontSize = 13.sp,
                color = onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Point 1: Image to PDF
            StepHubCard(
                stepNumber = "1",
                title = "Image & Scan to PDF Converter",
                description = "Batch compile camera scans or gallery photos into searchable, high-resolution multi-page PDF documents.",
                icon = Icons.Outlined.PictureAsPdf,
                surfaceContainer = surfaceContainer,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Point 2: Google Drive Sync
            StepHubCard(
                stepNumber = "2",
                title = "Google Drive Cloud Integration",
                description = "Directly open and stream PDFs from Google Drive with synchronized document reading history across sessions.",
                icon = Icons.Outlined.CloudDone,
                surfaceContainer = surfaceContainer,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Point 3: Table Extraction to Excel
            StepHubCard(
                stepNumber = "3",
                title = "Smart Table Extraction to Excel",
                description = "Intelligently extracts table structures and data cells inside PDF pages and exports them into editable native .xlsx spreadsheets.",
                icon = Icons.Outlined.TableChart,
                surfaceContainer = surfaceContainer,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Point 4: GPU Acceleration
            StepHubCard(
                stepNumber = "4",
                title = "High-Capacity GPU Acceleration",
                description = "Fluid pinch-to-zoom and frictionless high-framerate rendering across heavy 5,000+ page technical documents with zero lag.",
                icon = Icons.Outlined.Speed,
                surfaceContainer = surfaceContainer,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Point 5: Google Integrity & Security
            StepHubCard(
                stepNumber = "5",
                title = "Google Play Protect & Sandbox",
                description = "Runs in strict Android application sandboxing with total on-device document isolation.",
                icon = Icons.Outlined.Security,
                surfaceContainer = surfaceContainer,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Point 6: On-Device AI Summarizer
            StepHubCard(
                stepNumber = "6",
                title = "On-Device Intelligence & OCR",
                description = "Extract text from unselectable scanned contracts with on-device OCR and generate instant document insights without cloud latency.",
                icon = Icons.Outlined.AutoAwesome,
                surfaceContainer = surfaceContainer,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Point 7: Google Play Integrity & KeyStore (Clean Single Line Heading)
            StepHubCard(
                stepNumber = "7",
                title = "Play Integrity & KeyStore Vault",
                description = "Binary tamper attestation via Google Play Services with biometric Android KeyStore cryptographic document isolation.",
                icon = Icons.Outlined.VerifiedUser,
                surfaceContainer = surfaceContainer,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Point 8: Lossless Scanned Compression & De-skew
            StepHubCard(
                stepNumber = "8",
                title = "Lossless Scanned PDF Compression",
                description = "Shrink multi-megabyte scanned manuals into compact, email-ready documents with perspective auto-flatten and de-skew.",
                icon = Icons.Outlined.Compress,
                surfaceContainer = surfaceContainer,
                primaryColor = primaryColor,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Point 9: Device Storage Access (Interactive Permission)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E5EB)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isStorageGranted) successColor.copy(alpha = 0.12f) else primaryColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isStorageGranted) Icons.Outlined.Check else Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = if (isStorageGranted) successColor else primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = "Device Storage Access",
                            fontFamily = GoogleSans,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        if (isStorageGranted) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(successColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = successColor, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Granted",
                                        fontFamily = GoogleSans,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = successColor
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onGrantStorage,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(text = "Allow", fontFamily = GoogleSans, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Required by Android to discover and open PDF documents locally. Your documents remain completely private.",
                        fontFamily = GoogleSans,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = onSurfaceVariant,
                        textAlign = TextAlign.Justify
                    )
                }
            }

            Spacer(modifier = Modifier.height(110.dp))
        }

        // Persistent Floating Bottom Action Bar (Stock Material 3 Pro Design)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // More Features Toast (Fades in clearly on the left while scroll is available)
            AnimatedVisibility(
                visible = scrollState.canScrollForward,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .graphicsLayer { translationY = arrowOffset }
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(scrollState.value + 450)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "More features below",
                            fontFamily = GoogleSans,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Scroll down",
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Floating "Complete Setup" Button on the Right Bottom Corner (Native Material 3 Adaptive Extended FAB)
            val hasScrolledFeatures = !scrollState.canScrollForward || scrollState.value > 150
            val isSetupActive = isStorageGranted && hasScrolledFeatures
            val isScrolling = scrollState.isScrollInProgress

            val buttonBgColor by animateColorAsState(
                targetValue = if (isSetupActive) primaryColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                animationSpec = tween(300),
                label = "setup_btn_bg"
            )
            val buttonContentColor by animateColorAsState(
                targetValue = if (isSetupActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(300),
                label = "setup_btn_content"
            )
            val buttonElevation by animateDpAsState(
                targetValue = if (isSetupActive) 6.dp else 1.dp,
                animationSpec = tween(300),
                label = "setup_btn_elev"
            )
            val buttonRadius by animateDpAsState(
                targetValue = 4.dp,
                animationSpec = tween(250),
                label = "setup_btn_radius"
            )

            Surface(
                onClick = {
                    if (isSetupActive) {
                        onCompleteSetup()
                    } else if (!hasScrolledFeatures) {
                        coroutineScope.launch {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    } else if (!isStorageGranted) {
                        onGrantStorage()
                    }
                },
                shape = RoundedCornerShape(4.dp),
                color = buttonBgColor,
                contentColor = buttonContentColor,
                shadowElevation = buttonElevation,
                border = if (isSetupActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = if (isScrolling) 13.dp else 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSetupActive) Icons.Rounded.ChevronRight else Icons.Rounded.Lock,
                        contentDescription = "Complete Setup",
                        tint = if (isSetupActive) MaterialTheme.colorScheme.onPrimary else onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(17.dp)
                    )
                    AnimatedVisibility(
                        visible = !isScrolling,
                        enter = expandHorizontally(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
                        exit = shrinkHorizontally(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Complete Setup",
                                fontFamily = GoogleSans,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = buttonContentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHubCard(
    stepNumber: String = "",
    title: String,
    description: String,
    icon: ImageVector,
    surfaceContainer: Color,
    primaryColor: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x18000000)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0078D4).copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFF0078D4),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = GoogleSans,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    fontFamily = GoogleSans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Justify
                )
            }
        }
    }
}

/**
 * Stage 2: Celebratory Setup Complete with Authentic Stock Material Checkmark Badge
 * No back navigation allowed: The only action is entering Origami!
 */
@Composable
private fun StageTwoCelebration(
    isVisible: Boolean,
    onLaunch: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val currentUserEmail = OrigamiAuth.getCurrentUserEmail()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Authentic Stock Google Material Checkmark Badge
        StockMaterialCheckmark(
            size = 96.dp,
            triggerAnimation = isVisible
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "You're All Set!",
            fontFamily = GoogleSans,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        val desc = if (currentUserEmail != null) {
            "Signed in as $currentUserEmail. Your documents and preferences are fully synchronized."
        } else {
            "Swiffle is primed and configured. Enjoy high-speed, private document reading."
        }

        Text(
            text = desc,
            fontFamily = GoogleSans,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = Color(0xFF475569),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(44.dp))

        Button(
            onClick = onLaunch,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0078D4),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 1.dp
            ),
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(0.70f)
        ) {
            Text(
                text = "Enter Swiffle",
                fontFamily = GoogleSans,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
    }
}
