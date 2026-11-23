package com.swiffle.app.ui.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.swiffle.app.R
import com.swiffle.app.auth.AuthState
import com.swiffle.app.auth.OrigamiAuth
import com.swiffle.app.theme.*
import com.swiffle.app.ui.components.DarkVoidAmbientLightPulse
import com.swiffle.app.ui.components.GoogleSignInButton
import com.swiffle.app.ui.components.M3ConnectedLoadingToSuccessTick
import com.swiffle.app.ui.components.M3ExpressiveMorphingShapeLoader
import com.swiffle.app.ui.components.MaroonAccentRose
import com.swiffle.app.ui.components.MaroonBlushSecondary
import com.swiffle.app.ui.components.MaroonIvoryText
import com.swiffle.app.ui.components.OnboardingHaptics
import com.swiffle.app.ui.components.OnboardingImageMemoryManager
import com.swiffle.app.ui.components.OnboardingPagerMemoryEffect
import com.swiffle.app.ui.components.OnboardingStageMemoryEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  SWIFFLE PRODUCTION-GRADE FLUID VERTICAL ONBOARDING & IN-PLACE TRANSFORMATION
 * ══════════════════════════════════════════════════════════════════════════════
 * - Vertical Seamless Pager (`VerticalPager`): Advancing between onboarding steps
 *   via "Next" or touch swipe flows vertically with spring physics and vertical
 *   depth parallax.
 * - In-Place Loading -> Success Transformation: A single unified layout where the
 *   morphing shape loader, headline, and status pill smoothly transform in-place
 *   into the animated checkmark seal, readiness cards, and primary workspace CTA.
 * - Clean Professional Wording: Zero font names, zero coding/technical jargon,
 *   and zero external tool names anywhere in copy or canvas visuals.
 */
private enum class OnboardingStage(val index: Int) {
    GATEWAY(0),
    CREDENTIALS(1),
    FEATURE_TOUR(2),
    WORKSPACE_READY(3)
}

@Composable
fun OnboardingScreen(
    onStageChanged: (Int) -> Unit = {},
    onAllSetReached: () -> Unit = {},
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val authState by OrigamiAuth.authState.collectAsState()

    var currentStage by remember { mutableStateOf(OnboardingStage.GATEWAY) }
    var previousStageBeforeTour by remember { mutableStateOf(OnboardingStage.GATEWAY) }
    var isSpinningAuth by remember { mutableStateOf(false) }
    var isAuthSuccess by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }
    var activeTourSubStep by remember { mutableIntStateOf(0) }
    var tourDepthProgress by remember { mutableFloatStateOf(0f) }

    // Automatically clear inactive screen bitmap caches on every onboarding stage transition
    OnboardingStageMemoryEffect(currentStageIndex = currentStage.index)

    LaunchedEffect(currentStage) {
        onStageChanged(currentStage.index)
        if (currentStage != OnboardingStage.FEATURE_TOUR) {
            activeTourSubStep = 0
            tourDepthProgress = if (currentStage == OnboardingStage.WORKSPACE_READY) 1f else 0f
        }
    }

    fun finishIntoWorkspace() {
        if (isExiting) return
        coroutineScope.launch {
            isExiting = true
            OnboardingImageMemoryManager.clearAllOnboardingBitmapCaches(context)
            onAllSetReached()
            delay(180)
            onOnboardingComplete()
        }
    }

    fun advanceToFeatureTour(fromStage: OnboardingStage) {
        previousStageBeforeTour = fromStage
        currentStage = OnboardingStage.FEATURE_TOUR
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Authenticated -> {
                isSpinningAuth = false
                isAuthSuccess = true
                delay(220)
                if (currentStage == OnboardingStage.GATEWAY || currentStage == OnboardingStage.CREDENTIALS) {
                    advanceToFeatureTour(currentStage)
                }
            }
            is AuthState.Idle -> {
                isSpinningAuth = false
                isAuthSuccess = false
            }
            is AuthState.Error -> {
                isSpinningAuth = false
                isAuthSuccess = false
                if (!OrigamiAuth.isUserCancellation(Exception(state.message))) {
                    snackbarHostState.showSnackbar(state.message)
                }
            }
            else -> {}
        }
    }

    val exitAlpha by animateFloatAsState(
        targetValue = if (isExiting) 0f else 1f,
        animationSpec = tween(180),
        label = "exit_alpha"
    )

    val exitScale by animateFloatAsState(
        targetValue = if (isExiting) 1.03f else 1f,
        animationSpec = SwiffleMotion.SpringSpatial,
        label = "exit_scale"
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
            // Slow-moving radial gradient ambient pulse creating layered depth over the #000000 Dark Void
            DarkVoidAmbientLightPulse(
                activeStageIndex = currentStage.index,
                activeSubStepIndex = activeTourSubStep,
                transitionDepthProgress = tourDepthProgress
            )

            StageZeroAuthGate(
                currentStage = currentStage,
                onStageChange = { currentStage = it },
                previousStageBeforeTour = previousStageBeforeTour,
                authState = authState,
                isSpinningAuth = isSpinningAuth,
                isAuthSuccess = isAuthSuccess,
                onShowMessage = { msg ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                },
                onGoogleSignInClick = {
                    coroutineScope.launch {
                        isSpinningAuth = true
                        try {
                            val result = OrigamiAuth.signInWithGoogle(context)
                            result.onFailure { error ->
                                if (!OrigamiAuth.isUserCancellation(error)) {
                                    snackbarHostState.showSnackbar(
                                        error.localizedMessage ?: "Sign-in could not be completed. Check your connection and try again."
                                    )
                                }
                            }
                        } finally {
                            isSpinningAuth = false
                        }
                    }
                },
                onEmailSignInClick = { email, pass, _ ->
                    isSpinningAuth = true
                    coroutineScope.launch {
                        val res = OrigamiAuth.signInWithEmail(email, pass)
                        if (res.isFailure) {
                            isSpinningAuth = false
                            snackbarHostState.showSnackbar(
                                res.exceptionOrNull()?.localizedMessage ?: "Incorrect email or password. Please check your credentials and try again."
                            )
                        } else {
                            context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("google_user_email", email)
                                .putString("user_mode", "authenticated")
                                .apply()
                            isSpinningAuth = false
                            advanceToFeatureTour(OnboardingStage.CREDENTIALS)
                        }
                    }
                },
                onEmailSignUpClick = { name, email, pass ->
                    isSpinningAuth = true
                    coroutineScope.launch {
                        val res = OrigamiAuth.signUpWithEmail(email, pass)
                        if (res.isFailure) {
                            isSpinningAuth = false
                            snackbarHostState.showSnackbar(
                                res.exceptionOrNull()?.localizedMessage ?: "Could not create account. Please verify your email and try again."
                            )
                        } else {
                            val prefs = context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE).edit()
                            if (name.isNotBlank()) {
                                prefs.putString("google_user_name", name)
                            }
                            prefs.putString("google_user_email", email)
                                .putString("user_mode", "authenticated")
                                .apply()
                            isSpinningAuth = false
                            advanceToFeatureTour(OnboardingStage.CREDENTIALS)
                        }
                    }
                },
                onGuestClick = {
                    context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
                        .edit()
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
                    advanceToFeatureTour(currentStage)
                },
                onEnterWorkspace = {
                    finishIntoWorkspace()
                },
                onTourScrollProgressChanged = { stepIdx, depthProgress ->
                    activeTourSubStep = stepIdx
                    tourDepthProgress = depthProgress
                }
            )
        }
    }
}

@Composable
private fun StageZeroAuthGate(
    currentStage: OnboardingStage,
    onStageChange: (OnboardingStage) -> Unit,
    previousStageBeforeTour: OnboardingStage,
    authState: AuthState,
    onShowMessage: (String) -> Unit,
    onGoogleSignInClick: () -> Unit,
    onEmailSignInClick: (String, String, Boolean) -> Unit,
    onEmailSignUpClick: (String, String, String) -> Unit,
    onGuestClick: () -> Unit,
    onEnterWorkspace: () -> Unit,
    onTourScrollProgressChanged: (Int, Float) -> Unit = { _, _ -> },
    isSpinningAuth: Boolean,
    isAuthSuccess: Boolean = false
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var isSignUpMode by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = currentStage,
        transitionSpec = {
            if (targetState.index > initialState.index) {
                SwiffleMotion.sharedAxisZForward()
            } else {
                SwiffleMotion.sharedAxisZBackward()
            }
        },
        modifier = Modifier.fillMaxSize(),
        label = "onboarding_shared_axis_z_transition"
    ) { stage ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (stage) {
                OnboardingStage.GATEWAY -> AuthSanctuaryGateway(
                    onGetStarted = {
                        isSignUpMode = false
                        onStageChange(OnboardingStage.CREDENTIALS)
                    },
                    onGuestClick = onGuestClick
                )
                OnboardingStage.CREDENTIALS -> {
                    BackHandler {
                        onStageChange(OnboardingStage.GATEWAY)
                    }
                    AuthStudioCredentialScreen(
                        isSignUpMode = isSignUpMode,
                        onToggleMode = {
                            OnboardingHaptics.performPageStepHaptic(context, view)
                            isSignUpMode = !isSignUpMode
                        },
                        onBack = { onStageChange(OnboardingStage.GATEWAY) },
                        authState = authState,
                        isSpinningAuth = isSpinningAuth,
                        isAuthSuccess = isAuthSuccess,
                        onShowMessage = onShowMessage,
                        onGoogleSignInClick = onGoogleSignInClick,
                        onEmailSignInClick = onEmailSignInClick,
                        onEmailSignUpClick = onEmailSignUpClick,
                        onGuestClick = onGuestClick
                    )
                }
                OnboardingStage.FEATURE_TOUR -> {
                    InteractiveFeatureTourScreen(
                        onBackToPreviousStage = {
                            onStageChange(previousStageBeforeTour)
                        },
                        onTourFinished = {
                            onStageChange(OnboardingStage.WORKSPACE_READY)
                        },
                        onScrollProgressChanged = onTourScrollProgressChanged
                    )
                }
                OnboardingStage.WORKSPACE_READY -> {
                    // Terminal stage: system Back enters workspace
                    BackHandler {
                        onEnterWorkspace()
                    }
                    WorkspaceLoadingAndAllSetScreen(
                        onEnterWorkspace = onEnterWorkspace
                    )
                }
            }
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  REUSABLE MILD-WHITE GLASSMORPHIC PILL BUTTONS
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun GlassWhitePrimaryPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = SwiffleMotion.SpringSpatialFast,
        label = "glass_primary_btn_scale"
    )

    val pillShape = RoundedCornerShape(999.dp)

    val glassFillBrush = remember(isPressed) {
        Brush.linearGradient(
            colors = if (isPressed) {
                listOf(
                    Color.White.copy(alpha = 0.34f),
                    Color(0xFFFFF5F2).copy(alpha = 0.24f),
                    Color.White.copy(alpha = 0.17f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.26f),
                    Color(0xFFFFF5F2).copy(alpha = 0.17f),
                    Color.White.copy(alpha = 0.12f)
                )
            },
            start = Offset(0f, 0f),
            end = Offset(700f, 200f)
        )
    }

    val glassBorderBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.58f),
                Color(0xFFF7DAD9).copy(alpha = 0.32f),
                Color.White.copy(alpha = 0.16f)
            ),
            start = Offset(0f, 0f),
            end = Offset(700f, 200f)
        )
    }

    Box(
        modifier = modifier
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.50f
            }
            .clip(pillShape)
            .background(glassFillBrush)
            .border(width = 1.2.dp, brush = glassBorderBrush, shape = pillShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.30f)),
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.11f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
private fun GlassWhiteSecondaryPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = SwiffleMotion.SpringSpatialFast,
        label = "glass_secondary_btn_scale"
    )

    val pillShape = RoundedCornerShape(999.dp)

    val glassFillBrush = remember(isPressed) {
        Brush.linearGradient(
            colors = if (isPressed) {
                listOf(
                    Color.White.copy(alpha = 0.20f),
                    Color(0xFFFFF5F2).copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.08f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color(0xFFFFF5F2).copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.05f)
                )
            },
            start = Offset(0f, 0f),
            end = Offset(700f, 200f)
        )
    }

    val glassBorderBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.36f),
                Color(0xFFF7DAD9).copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.10f)
            ),
            start = Offset(0f, 0f),
            end = Offset(700f, 200f)
        )
    }

    Box(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.50f
            }
            .clip(pillShape)
            .background(glassFillBrush)
            .border(width = 1.dp, brush = glassBorderBrush, shape = pillShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.22f)),
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  STAGE 0: GET STARTED PAGE (DARK VOID GLASSMORPHISM + ANCHORED HERO LOGO)
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun AuthSanctuaryGateway(
    onGetStarted: () -> Unit,
    onGuestClick: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf("English (US)") }
    var showLanguageMenu by remember { mutableStateOf(false) }
    val availableLanguages = listOf("English (US)", "Español", "Français", "Deutsch", "日本語", "हिन्दी")

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Top Glassmorphic Language Selector Pill + Wide Corner-Clean Overflow Menu
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val langPillShape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(langPillShape)
                    .clickable { showLanguageMenu = true }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(langPillShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.White.copy(alpha = 0.06f)
                                )
                            )
                        )
                        .border(
                            width = 0.8.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.10f)
                                )
                            ),
                            shape = langPillShape
                        )
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_internet_globe),
                        contentDescription = "Select language",
                        tint = MaroonIvoryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedLanguage,
                        fontFamily = ClaudeSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaroonIvoryText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaroonBlushSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            }

            val menuShape = RoundedCornerShape(20.dp)
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    surface = Color(0xFF0E0E12),
                    surfaceContainer = Color(0xFF0E0E12)
                ),
                shapes = MaterialTheme.shapes.copy(
                    extraSmall = menuShape,
                    small = menuShape,
                    medium = menuShape
                )
            ) {
                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { showLanguageMenu = false },
                    offset = DpOffset(x = 0.dp, y = 8.dp),
                    shape = menuShape,
                    containerColor = Color(0xFF0E0E12),
                    tonalElevation = 0.dp,
                    shadowElevation = 16.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.36f),
                                Color(0xFFF7DAD9).copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        )
                    ),
                    modifier = Modifier
                        .width(220.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF16161C),
                                    Color(0xFF0A0A0E)
                                )
                            ),
                            shape = menuShape
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    availableLanguages.forEach { lang ->
                        val isSelected = lang == selectedLanguage
                        val itemShape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(itemShape)
                                .background(
                                    if (isSelected) {
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.20f),
                                                Color.White.copy(alpha = 0.10f)
                                            )
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, Color.Transparent)
                                        )
                                    }
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 0.8.dp,
                                            color = Color.White.copy(alpha = 0.30f),
                                            shape = itemShape
                                        )
                                    } else Modifier
                                )
                                .clickable {
                                    selectedLanguage = lang
                                    showLanguageMenu = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = lang,
                                    fontFamily = ClaudeSans,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaroonIvoryText else MaroonBlushSecondary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaroonIvoryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Center Hero (Exact Coordinate Match with SplashScreen — Pure Logo, No Rings/Holes)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_swiffle_logo),
                contentDescription = "Swiffle Logo",
                modifier = Modifier.size(112.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Swiffle",
                fontFamily = ClaudeSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 42.sp,
                lineHeight = 50.sp,
                color = MaroonIvoryText,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.4).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Speed, clarity, and intelligence in every page.",
                fontFamily = ClaudeSans,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = MaroonBlushSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // 3. Bottom Mild-White Glassmorphic Action Stack
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassWhitePrimaryPillButton(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("get_started_button")
            ) {
                Text(
                    text = "Get started",
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = MaroonIvoryText
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaroonIvoryText,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassWhiteSecondaryPillButton(
                onClick = onGuestClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("continue_guest_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaroonIvoryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continue as guest",
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = MaroonIvoryText
                )
            }
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  STAGE 1: LOGIN & SIGNUP PAGE (DARK VOID GLASSMORPHISM + CLAUDE SERIF & SANS)
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun AuthStudioCredentialScreen(
    isSignUpMode: Boolean,
    onToggleMode: () -> Unit,
    onBack: () -> Unit,
    authState: AuthState,
    isSpinningAuth: Boolean,
    isAuthSuccess: Boolean,
    onShowMessage: (String) -> Unit,
    onGoogleSignInClick: () -> Unit,
    onEmailSignInClick: (String, String, Boolean) -> Unit,
    onEmailSignUpClick: (String, String, String) -> Unit,
    onGuestClick: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val sharedPref = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(sharedPref.getString("saved_login_email", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(sharedPref.getBoolean("remember_me_enabled", true)) }

    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    var isNameTouched by remember { mutableStateOf(false) }
    var isEmailTouched by remember { mutableStateOf(false) }
    var isPasswordTouched by remember { mutableStateOf(false) }

    val emailTrimmed = email.trim()
    val isEmailFormatValid = android.util.Patterns.EMAIL_ADDRESS.matcher(emailTrimmed).matches()
    val isEmailValid = emailTrimmed.isNotEmpty() && isEmailFormatValid
    val emailError: String? = when {
        !hasAttemptedSubmit && !isEmailTouched -> null
        emailTrimmed.isEmpty() -> if (hasAttemptedSubmit) "Enter your email address to continue." else null
        !isEmailFormatValid -> "Enter a valid email address (for example, name@domain.com)."
        else -> null
    }

    val isPasswordLengthValid = password.length >= 6
    val passwordError: String? = when {
        !hasAttemptedSubmit && !isPasswordTouched -> null
        password.isEmpty() -> if (hasAttemptedSubmit) "Enter your password to continue." else null
        password.length < 6 -> "Use at least 6 characters for your password."
        else -> null
    }

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
            nameTrimmed.isEmpty() -> if (hasAttemptedSubmit) "Enter your full name to create an account." else null
            nameTrimmed.length < 2 -> "Enter at least 2 characters for your name."
            else -> null
        }
    } else null

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
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Navigation Row (48dp Glassmorphic Back Button + Centered Swiffle Logo)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("auth_back_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                width = 0.8.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.24f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Go back",
                            tint = MaroonIvoryText,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.ic_swiffle_logo),
                    contentDescription = "Swiffle Logo",
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Editorial Page Headline in Claude Serif + Subtitle in Claude Sans
            AnimatedContent(
                targetState = isSignUpMode,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically { it / 5 }).togetherWith(
                        fadeOut(tween(150)) + slideOutVertically { -it / 5 }
                    )
                },
                label = "auth_header_anim"
            ) { signUp ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (signUp) "Create your account" else "Welcome back",
                        fontFamily = ClaudeSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        lineHeight = 39.sp,
                        color = MaroonIvoryText,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (signUp)
                            "Sync your documents, annotations, and workspace across devices."
                        else
                            "Sign in to access your documents and workspace.",
                        fontFamily = ClaudeSans,
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp,
                        color = MaroonBlushSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Obsidian Dark-Void Hairline Glass Card Surface
            val cardShape = RoundedCornerShape(26.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .clip(cardShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF121216).copy(alpha = 0.94f),
                                Color(0xFF08080B).copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 0.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color(0xFFF7DAD9).copy(alpha = 0.10f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = cardShape
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    // A. Mild-White Glassmorphic Pill Segmented Slider (Sign in | Create account)
                    val sliderTrackShape = RoundedCornerShape(999.dp)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(sliderTrackShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.26f),
                                        Color.White.copy(alpha = 0.10f)
                                    )
                                ),
                                shape = sliderTrackShape
                            )
                            .padding(4.dp)
                    ) {
                        val tabWidth = maxWidth / 2
                        val animatedPillOffset by animateDpAsState(
                            targetValue = if (isSignUpMode) tabWidth else 0.dp,
                            animationSpec = spring(
                                dampingRatio = 0.68f,
                                stiffness = 750f
                            ),
                            label = "auth_segmented_thumb_offset"
                        )

                        val activeThumbShape = RoundedCornerShape(999.dp)
                        Box(
                            modifier = Modifier
                                .offset(x = animatedPillOffset)
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(activeThumbShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.28f),
                                            Color(0xFFFFF5F2).copy(alpha = 0.18f),
                                            Color.White.copy(alpha = 0.12f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.56f),
                                            Color(0xFFF7DAD9).copy(alpha = 0.28f),
                                            Color.White.copy(alpha = 0.16f)
                                        )
                                    ),
                                    shape = activeThumbShape
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.09f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
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
                                Text(
                                    text = "Sign in",
                                    fontFamily = ClaudeSans,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = if (!isSignUpMode) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (!isSignUpMode) MaroonIvoryText else MaroonBlushSecondary.copy(alpha = 0.78f)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(999.dp))
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
                                Text(
                                    text = "Create account",
                                    fontFamily = ClaudeSans,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = if (isSignUpMode) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSignUpMode) MaroonIvoryText else MaroonBlushSecondary.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // B. Full Name Input (Create account mode)
                    AnimatedVisibility(
                        visible = isSignUpMode,
                        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(180))
                    ) {
                        Column {
                            M3MaroonLabeledTextField(
                                label = "Full name",
                                value = name,
                                onValueChange = {
                                    name = it
                                    isNameTouched = true
                                },
                                placeholder = "Enter your full name",
                                errorText = nameError,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = if (nameError != null) Color(0xFFFF6B6B) else MaroonBlushSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                )
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // C. Email Address Input
                    M3MaroonLabeledTextField(
                        label = "Email address",
                        value = email,
                        onValueChange = {
                            email = it
                            isEmailTouched = true
                        },
                        placeholder = "name@domain.com",
                        errorText = emailError,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_material_email),
                                contentDescription = null,
                                tint = if (emailError != null) Color(0xFFFF6B6B) else MaroonBlushSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = if (isEmailValid) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Valid email",
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // D. Password Input
                    M3MaroonLabeledTextField(
                        label = "Password",
                        value = password,
                        onValueChange = {
                            password = it
                            isPasswordTouched = true
                        },
                        placeholder = if (isSignUpMode) "At least 6 characters" else "Enter your password",
                        errorText = passwordError,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_material_lock),
                                contentDescription = null,
                                tint = if (passwordError != null) Color(0xFFFF6B6B) else MaroonBlushSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = if (passwordError != null) Color(0xFFFF6B6B) else MaroonBlushSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { validateAndSubmit() }
                        )
                    )

                    if (isSignUpMode && password.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (bar in 1..4) {
                                    val barColor = when {
                                        passwordStrength >= bar -> when (passwordStrength) {
                                            1 -> Color(0xFFFF6B6B)
                                            2 -> Color(0xFFFFB74D)
                                            3 -> MaroonAccentRose
                                            else -> Color(0xFF81C784)
                                        }
                                        else -> Color.White.copy(alpha = 0.12f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val strengthLabel = when (passwordStrength) {
                                1 -> "Weak password"
                                2 -> "Fair password"
                                3 -> "Good password"
                                4 -> "Strong password"
                                else -> "Too short"
                            }
                            Text(
                                text = strengthLabel,
                                fontFamily = ClaudeSans,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaroonBlushSecondary,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }

                    // Remember Me & Reset Password Row
                    if (!isSignUpMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        rememberMe = !rememberMe
                                        sharedPref.edit().putBoolean("remember_me_enabled", rememberMe).apply()
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                val checkShape = RoundedCornerShape(6.dp)
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(checkShape)
                                        .background(
                                            if (rememberMe) {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.28f),
                                                        Color.White.copy(alpha = 0.14f)
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.06f),
                                                        Color.White.copy(alpha = 0.03f)
                                                    )
                                                )
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (rememberMe) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.28f),
                                            shape = checkShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (rememberMe) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Checked",
                                            tint = MaroonIvoryText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Remember me",
                                    fontFamily = ClaudeSans,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaroonBlushSecondary
                                )
                            }

                            Text(
                                text = "Reset password",
                                fontFamily = ClaudeSans,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaroonIvoryText,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isEmailValid) {
                                            onShowMessage("Password reset instructions sent to $emailTrimmed.")
                                        } else {
                                            isEmailTouched = true
                                            onShowMessage("Enter your email address above to receive a reset link.")
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // E. Primary Mild-White Glassmorphic Pill Button ("Sign in" / "Create account")
                    GlassWhitePrimaryPillButton(
                        onClick = { if (!isLoading) validateAndSubmit() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            M3ExpressiveMorphingShapeLoader(
                                size = 24.dp,
                                showCenterEmblem = false
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = if (isSignUpMode) "Create account" else "Sign in",
                            fontFamily = ClaudeSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = MaroonIvoryText
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // F. Clean Glass Divider ("or")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.14f))
                        )
                        Text(
                            text = "  or  ",
                            fontFamily = ClaudeSans,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaroonBlushSecondary
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.14f))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // G. Mild-White Glassmorphic "Continue with Google" Pill Button
                    GoogleSignInButton(
                        onClick = {
                            focusManager.clearFocus()
                            onGoogleSignInClick()
                        },
                        isLoading = isLoading,
                        isSpinning = isSpinningAuth,
                        isSuccess = isAuthSuccess,
                        enabled = !isLoading,
                        text = "Continue with Google",
                        spinnerText = "Connecting...",
                        successText = "Signed in",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // H. Direct Guest Access Pill Button
                    GlassWhiteSecondaryPillButton(
                        onClick = {
                            focusManager.clearFocus()
                            onGuestClick()
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaroonIvoryText,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Continue as guest",
                            fontFamily = ClaudeSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp,
                            color = MaroonIvoryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  STAGE 2: EDITORIAL VERTICAL ONBOARDING TOUR (`VerticalPager`)
 * ══════════════════════════════════════════════════════════════════════════════
 * Architectural restraint, strict 24dp keyline alignment, single showcase card per
 * step with zero overlapping floating badges or concentric ring clutter, and clean
 * two-line editorial copy.
 */
private data class FeatureSpecItem(
    val icon: ImageVector,
    val label: String
)

private data class FeatureTourStepData(
    val stepIndex: Int,
    @RawRes val videoResId: Int,
    val overlineCategory: String,
    val headline: String,
    val description: String,
    val specLeft: FeatureSpecItem,
    val specRight: FeatureSpecItem
)

@Composable
private fun InteractiveFeatureTourScreen(
    onBackToPreviousStage: () -> Unit,
    onTourFinished: () -> Unit,
    onScrollProgressChanged: (Int, Float) -> Unit = { _, _ -> }
) {
    val steps = remember {
        listOf(
            FeatureTourStepData(
                stepIndex = 0,
                videoResId = R.raw.workflow_pdf_edit,
                overlineCategory = "EFFORTLESS EDITING",
                headline = "Edit documents like a note.",
                description = "Update paragraphs and refine layouts while keeping original styling intact.",
                specLeft = FeatureSpecItem(
                    icon = Icons.Rounded.Draw,
                    label = "Natural flow"
                ),
                specRight = FeatureSpecItem(
                    icon = Icons.Rounded.Verified,
                    label = "Layout intact"
                )
            ),
            FeatureTourStepData(
                stepIndex = 1,
                videoResId = R.raw.workflow_ai_insight,
                overlineCategory = "INSTANT CLARITY",
                headline = "Key takeaways in seconds.",
                description = "Review long agreements faster with concise summaries and direct page links.",
                specLeft = FeatureSpecItem(
                    icon = Icons.Rounded.Description,
                    label = "Page links"
                ),
                specRight = FeatureSpecItem(
                    icon = Icons.Rounded.Lock,
                    label = "On-device privacy"
                )
            ),
            FeatureTourStepData(
                stepIndex = 2,
                videoResId = R.raw.workflow_table_extract,
                overlineCategory = "ORGANIZED TABLES",
                headline = "Turn pages into clean tables.",
                description = "Capture schedules and financial summaries with structured rows and totals.",
                specLeft = FeatureSpecItem(
                    icon = Icons.Rounded.GridOn,
                    label = "Clean structure"
                ),
                specRight = FeatureSpecItem(
                    icon = Icons.Rounded.TableChart,
                    label = "Verified totals"
                )
            )
        )
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { steps.size })
    val coroutineScope = rememberCoroutineScope()

    // Clear off-screen tour page bitmap caches whenever the vertical pager settles on a new step
    OnboardingPagerMemoryEffect(pagerState = pagerState)

    LaunchedEffect(pagerState) {
        val lastIdx = (steps.size - 1).coerceAtLeast(1).toFloat()
        snapshotFlow {
            val normalized = ((pagerState.currentPage + pagerState.currentPageOffsetFraction) / lastIdx)
                .coerceIn(0f, 1f)
            pagerState.currentPage to normalized
        }.collect { (page, normalized) ->
            onScrollProgressChanged(page, normalized)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vertical_tour_master_clock")
    val showcaseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "showcase_cycle_progress"
    )

    BackHandler {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage - 1,
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 290f)
                )
            }
        } else {
            onBackToPreviousStage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Minimalist Top Bar (Back Button · Quiet Step Counter · Text Skip Action)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = pagerState.currentPage - 1,
                                        animationSpec = spring(dampingRatio = 0.88f, stiffness = 290f)
                                    )
                                }
                            } else {
                                onBackToPreviousStage()
                            }
                        }
                        .testTag("tour_back_button"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Previous step",
                            tint = MaroonIvoryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "0${pagerState.currentPage + 1}  /  0${steps.size}",
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = 1.4.sp,
                    color = MaroonBlushSecondary.copy(alpha = 0.78f),
                    style = TextStyle(fontFeatureSettings = "tnum")
                )

                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onTourFinished)
                        .padding(horizontal = 12.dp)
                        .testTag("tour_skip_button"),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Skip",
                        fontFamily = ClaudeSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaroonBlushSecondary.copy(alpha = 0.85f)
                    )
                }
            }

            // 2. Seamless VerticalPager + Subtle Finger-Following Circular Progress Indicator
            val continuousProgress = (
                (pagerState.currentPage + pagerState.currentPageOffsetFraction + 1f) / steps.size.toFloat()
            ).coerceIn(0.08f, 1f)

            var isSwipingWithFinger by remember { mutableStateOf(false) }
            var activePointerOffset by remember { mutableStateOf(Offset.Zero) }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            val downPos = down.position
                            activePointerOffset = downPos
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break
                                    if (!change.pressed) break
                                    activePointerOffset = change.position
                                    if (!isSwipingWithFinger &&
                                        (change.position - downPos).getDistance() > viewConfiguration.touchSlop
                                    ) {
                                        isSwipingWithFinger = true
                                    }
                                }
                            } finally {
                                isSwipingWithFinger = false
                            }
                        }
                    }
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { pageIndex ->
                    val stepData = steps[pageIndex]
                    val signedOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                    val absOffset = signedOffset.absoluteValue.coerceIn(0f, 1f)

                    val pageScale = lerp(0.95f, 1f, 1f - absOffset)
                    val pageAlpha = lerp(0.30f, 1f, 1f - absOffset)

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                scaleX = pageScale
                                scaleY = pageScale
                                alpha = pageAlpha
                                translationY = signedOffset * 20f
                            }
                    ) {
                        val isCompactViewport = maxHeight < 490.dp
                        val copyToVisualGap = if (isCompactViewport) 16.dp else 22.dp

                        val contentColumnModifier = if (isCompactViewport) {
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        } else {
                            Modifier.fillMaxSize()
                        }

                        Column(
                            modifier = contentColumnModifier,
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (isCompactViewport) {
                                Spacer(modifier = Modifier.height(8.dp))
                            } else {
                                Spacer(modifier = Modifier.weight(0.32f))
                            }

                            // 1. TOP EDITORIAL TITLE & DESCRIPTION
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = stepData.overlineCategory,
                                    fontFamily = ClaudeSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    letterSpacing = 1.6.sp,
                                    color = MaroonAccentRose
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = stepData.headline,
                                    fontFamily = ClaudeSerif,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 29.sp,
                                    lineHeight = 35.sp,
                                    color = MaroonIvoryText,
                                    letterSpacing = (-0.4).sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = stepData.description,
                                    fontFamily = ClaudeSans,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = MaroonBlushSecondary.copy(alpha = 0.86f)
                                )
                            }

                            Spacer(modifier = Modifier.height(copyToVisualGap))

                            // 2. VISUAL REPRESENTATION BELOW DESCRIPTION
                            EditorialShowcaseStage(
                                stepIndex = stepData.stepIndex,
                                progress = showcaseProgress,
                                verticalParallaxPx = signedOffset * 16f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3. QUIET INLINE CAPABILITY HIGHLIGHTS ANCHORED BENEATH VISUAL
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                InlineCapabilityHighlight(
                                    spec = stepData.specLeft,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(0.8.dp)
                                        .height(16.dp)
                                        .background(Color.White.copy(alpha = 0.14f))
                                )
                                InlineCapabilityHighlight(
                                    spec = stepData.specRight,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (isCompactViewport) {
                                Spacer(modifier = Modifier.height(12.dp))
                            } else {
                                Spacer(modifier = Modifier.weight(0.68f))
                            }
                        }
                    }
                }

                // Subtle, non-intrusive floating circular progress indicator tracking the finger swipe
                val density = LocalDensity.current
                val ringSize = 42.dp
                val (ringSizePx, edgeMarginPx, fingerLiftPx) = with(density) {
                    Triple(ringSize.toPx(), 16.dp.toPx(), 54.dp.toPx())
                }
                val maxTrackerX = (constraints.maxWidth.toFloat() - ringSizePx - edgeMarginPx)
                    .coerceAtLeast(edgeMarginPx)
                val maxTrackerY = (constraints.maxHeight.toFloat() - ringSizePx - edgeMarginPx)
                    .coerceAtLeast(edgeMarginPx)
                val targetTrackerOffset = Offset(
                    x = (activePointerOffset.x - ringSizePx / 2f).coerceIn(edgeMarginPx, maxTrackerX),
                    y = (activePointerOffset.y - ringSizePx / 2f - fingerLiftPx).coerceIn(edgeMarginPx, maxTrackerY)
                )
                val animatedTrackerOffset by animateOffsetAsState(
                    targetValue = targetTrackerOffset,
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 620f),
                    label = "swipe_finger_tracker_offset"
                )

                Box(
                    modifier = Modifier.offset {
                        IntOffset(
                            x = animatedTrackerOffset.x.roundToInt(),
                            y = animatedTrackerOffset.y.roundToInt()
                        )
                    }
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSwipingWithFinger,
                        enter = fadeIn(tween(130)) + scaleIn(
                            animationSpec = tween(150, easing = FastOutSlowInEasing),
                            initialScale = 0.82f
                        ),
                        exit = fadeOut(tween(220)) + scaleOut(
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            targetScale = 0.88f
                        )
                    ) {
                        SwipeCircularProgressOrb(
                            progress = continuousProgress,
                            stepNumber = pagerState.currentPage + 1,
                            modifier = Modifier
                                .size(ringSize)
                                .testTag("swipe_circular_progress")
                        )
                    }
                }
            }

            // 3. Bottom Navigation Dock (Left Circular Swipe Progress Ring + Right Primary Glass CTA)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CircularSwipeProgressBadge(
                    progress = continuousProgress,
                    currentStep = pagerState.currentPage + 1,
                    totalSteps = steps.size,
                    onClick = {
                        val nextPage = (pagerState.currentPage + 1) % steps.size
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = nextPage,
                                animationSpec = spring(dampingRatio = 0.88f, stiffness = 290f)
                            )
                        }
                    }
                )

                val isLastStep = pagerState.currentPage == steps.lastIndex
                GlassWhitePrimaryPillButton(
                    onClick = {
                        if (!isLastStep) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = spring(dampingRatio = 0.88f, stiffness = 290f)
                                )
                            }
                        } else {
                            onTourFinished()
                        }
                    },
                    modifier = Modifier
                        .width(188.dp)
                        .testTag("tour_next_button")
                ) {
                    AnimatedContent(
                        targetState = isLastStep,
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInVertically { it / 3 }).togetherWith(
                                fadeOut(tween(130)) + slideOutVertically { -it / 3 }
                            )
                        },
                        label = "bottom_cta_text"
                    ) { last ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (last) "Finish setup" else "Next",
                                fontFamily = ClaudeSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp,
                                color = MaroonIvoryText
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaroonIvoryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineCapabilityHighlight(
    spec: FeatureSpecItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = null,
            tint = MaroonAccentRose,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = spec.label,
            fontFamily = ClaudeSans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = MaroonIvoryText.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Subtle Floating Finger-Following Circular Progress Ring shown only during active swipe gestures.
 */
@Composable
private fun SwipeCircularProgressOrb(
    progress: Float,
    stepNumber: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF0E0E13).copy(alpha = 0.84f))
            .border(0.7.dp, Color.White.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val strokePx = 2.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)

            drawArc(
                color = Color.White.copy(alpha = 0.12f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx)
            )

            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(MaroonAccentRose, MaroonIvoryText)
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Text(
            text = stepNumber.toString(),
            fontFamily = ClaudeSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = MaroonIvoryText.copy(alpha = 0.92f),
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

/**
 * Quiet Bottom-Dock Circular Progress Ring synced 1:1 with the `VerticalPager` swipe progression.
 */
@Composable
private fun CircularSwipeProgressBadge(
    progress: Float,
    currentStep: Int,
    totalSteps: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Step $currentStep of $totalSteps" }
            .clickable(onClick = onClick)
            .testTag("dock_circular_progress_indicator"),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
            ) {
                val strokePx = 1.9.dp.toPx()
                val inset = strokePx / 2f
                val arcSize = Size(size.width - strokePx, size.height - strokePx)

                drawArc(
                    color = Color.White.copy(alpha = 0.12f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx)
                )

                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(MaroonAccentRose, MaroonIvoryText)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            Text(
                text = "$currentStep",
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = MaroonIvoryText.copy(alpha = 0.88f),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  ARCHITECTURAL SHOWCASE CARD (SINGLE CLEAN SURFACE · ZERO CLUTTER)
 * ══════════════════════════════════════════════════════════════════════════════
 */
@Composable
private fun EditorialShowcaseStage(
    stepIndex: Int,
    progress: Float,
    verticalParallaxPx: Float,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 260.dp)
            .graphicsLayer {
                translationY = -verticalParallaxPx * 0.35f
            }
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF121216),
                        Color(0xFF09090C)
                    )
                )
            )
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color(0xFFF7DAD9).copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = cardShape
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        when (stepIndex) {
            0 -> EditorialCardDocumentEditing(progress = progress)
            1 -> EditorialCardPrivateClarity(progress = progress)
            else -> EditorialCardOrganizedTables(progress = progress)
        }
    }
}

/**
 * SCENE 01: Clean Editorial Document Sheet with Smooth Highlight Reveal
 */
@Composable
private fun EditorialCardDocumentEditing(progress: Float) {
    // Smooth breathing highlight wave (0f -> 1f -> 1f -> 0f) without erratic cursor blinking
    val highlightWidth = when {
        progress < 0.25f -> (progress / 0.25f).coerceIn(0f, 1f)
        progress < 0.80f -> 1f
        else -> (1f - (progress - 0.80f) / 0.20f).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Card Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Executive Overview",
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                color = MaroonBlushSecondary.copy(alpha = 0.82f)
            )
            Text(
                text = "Page 04",
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaroonBlushSecondary.copy(alpha = 0.55f),
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Editorial Content Body
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Milestones & Deliverables",
                fontFamily = ClaudeSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = MaroonIvoryText
            )

            Text(
                text = "All quarterly goals, partner agreements, and key annotations remain cleanly aligned across every page.",
                fontFamily = ClaudeSans,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = MaroonBlushSecondary.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Smooth highlighted annotation bar integrated cleanly inside the document flow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .drawBehind {
                        val insetH = 6.dp.toPx()
                        val insetV = 4.dp.toPx()
                        val barWidth = ((size.width - insetH * 2f) * highlightWidth).coerceAtLeast(0f)
                        if (barWidth > 0f) {
                            drawRoundRect(
                                color = MaroonAccentRose.copy(alpha = 0.18f * highlightWidth),
                                topLeft = Offset(insetH, insetV),
                                size = Size(barWidth, (size.height - insetV * 2f).coerceAtLeast(0f)),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "Original layout and typography preserved.",
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = MaroonIvoryText
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quiet Aligned Card Footer (No floating pills)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF81C784))
                )
                Text(
                    text = "Formatting locked",
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = MaroonBlushSecondary.copy(alpha = 0.78f)
                )
            }
            Text(
                text = "Saved locally",
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaroonBlushSecondary.copy(alpha = 0.55f)
            )
        }
    }
}

/**
 * SCENE 02: Clean Key Takeaways Index with Sequential Soft Spotlight
 */
@Composable
private fun EditorialCardPrivateClarity(progress: Float) {
    val activeRow = when {
        progress < 0.33f -> 0
        progress < 0.66f -> 1
        else -> 2
    }

    val rows = remember {
        listOf(
            Triple("Review Schedule", "Completed ahead of target date", "p. 04"),
            Triple("Coverage Terms", "Full protection for all members", "p. 09"),
            Triple("Renewal Window", "60-day advance notice period", "p. 14")
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Key Takeaways",
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                color = MaroonBlushSecondary.copy(alpha = 0.82f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "On-device",
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = MaroonBlushSecondary.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3 Balanced Rows (Zero Truncation, Zero Floating Overlays)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEachIndexed { idx, (title, summary, pageTag) ->
                val isSelected = idx == activeRow
                val rowBgAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 0.09f else 0.02f,
                    animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    label = "clarity_row_bg_$idx"
                )
                val rowBorderAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 0.24f else 0.07f,
                    animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    label = "clarity_row_border_$idx"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = rowBgAlpha))
                        .border(0.8.dp, Color.White.copy(alpha = rowBorderAlpha), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontFamily = ClaudeSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = if (isSelected) MaroonIvoryText else MaroonIvoryText.copy(alpha = 0.78f),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary,
                            fontFamily = ClaudeSans,
                            fontSize = 11.5.sp,
                            color = MaroonBlushSecondary.copy(alpha = if (isSelected) 0.85f else 0.58f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isSelected) MaroonAccentRose.copy(alpha = 0.26f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = pageTag,
                            fontFamily = ClaudeSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (isSelected) MaroonIvoryText else MaroonBlushSecondary.copy(alpha = 0.75f),
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }
            }
        }
    }
}

/**
 * SCENE 03: Clean Financial Summary Table (Tabular Numerals, Zero Colliding Cards)
 */
@Composable
private fun EditorialCardOrganizedTables(progress: Float) {
    val highlightTotal = progress > 0.35f
    val totalRowAlpha by animateFloatAsState(
        targetValue = if (highlightTotal) 0.16f else 0.04f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "table_total_highlight"
    )

    val rows = remember {
        listOf(
            Triple("Department", "Q1", "Annual"),
            Triple("Operations", "$24,200", "$52,600"),
            Triple("Research", "$19,800", "$51,000"),
            Triple("Total", "$44,000", "$103,600")
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quarterly Summary",
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                color = MaroonBlushSecondary.copy(alpha = 0.82f)
            )
            Text(
                text = "Verified Figures",
                fontFamily = ClaudeSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color(0xFF81C784).copy(alpha = 0.88f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Clean Aligned Ledger Table
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(0.8.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
        ) {
            rows.forEachIndexed { idx, (label, q1, annual) ->
                val isHeader = idx == 0
                val isTotal = idx == rows.lastIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                isHeader -> Color.White.copy(alpha = 0.05f)
                                isTotal -> MaroonAccentRose.copy(alpha = totalRowAlpha)
                                else -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontFamily = ClaudeSans,
                        fontWeight = if (isHeader || isTotal) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (isHeader) 11.5.sp else 13.sp,
                        color = if (isHeader) MaroonBlushSecondary.copy(alpha = 0.72f) else MaroonIvoryText,
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = q1,
                        fontFamily = ClaudeSans,
                        fontWeight = if (isHeader || isTotal) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (isHeader) 11.5.sp else 13.sp,
                        color = if (isHeader) MaroonBlushSecondary.copy(alpha = 0.72f) else MaroonBlushSecondary,
                        textAlign = TextAlign.End,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.weight(0.9f)
                    )
                    Text(
                        text = annual,
                        fontFamily = ClaudeSans,
                        fontWeight = if (isHeader || isTotal) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = if (isHeader) 11.5.sp else 13.sp,
                        color = if (isTotal && highlightTotal) MaroonIvoryText else if (isHeader) MaroonBlushSecondary.copy(alpha = 0.72f) else MaroonIvoryText.copy(alpha = 0.90f),
                        textAlign = TextAlign.End,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (idx < rows.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.8.dp)
                            .background(Color.White.copy(alpha = 0.07f))
                    )
                }
            }
        }
    }
}

/**
 * ══════════════════════════════════════════════════════════════════════════════
 *  STAGE 3: SEAMLESS IN-PLACE TRANSFORMATION FROM LOADING -> SUCCESS SCREEN
 * ══════════════════════════════════════════════════════════════════════════════
 * - ONE SINGLE UNIFIED LAYOUT that smoothly transforms in-place from the
 *   Loading state into the Success ("You're all set") state without swapping
 *   screens or displaying a static page.
 * - The `M3ExpressiveMorphingShapeLoader` stays continuously mounted, gliding
 *   upward as its center Swiffle emblem morphs into a progressive animated
 *   checkmark seal while the readiness cards and primary CTA unfold in-place.
 */
@Composable
private fun WorkspaceLoadingAndAllSetScreen(
    onEnterWorkspace: () -> Unit
) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE) }
    val userName = remember {
        sharedPref.getString("google_user_name", null)?.takeIf { it.isNotBlank() }
    }

    var isReady by remember { mutableStateOf(false) }
    var hasTriggeredEnter by remember { mutableStateOf(false) }
    var loadingStepIndex by remember { mutableIntStateOf(0) }
    val loadingSteps = remember {
        listOf(
            "Preparing your reading and editing tools...",
            "Applying your privacy preferences...",
            "Finalizing your workspace..."
        )
    }

    fun triggerEnterWorkspaceOnce() {
        if (!hasTriggeredEnter) {
            hasTriggeredEnter = true
            OnboardingHaptics.performStageTransitionHaptic(context)
            onEnterWorkspace()
        }
    }

    LaunchedEffect(Unit) {
        delay(450)
        loadingStepIndex = 1
        delay(450)
        loadingStepIndex = 2
        delay(450)
        isReady = true
        // Deliver signature two-pulse celebratory haptic confirmation as the loader coalesces into the Tick
        delay(220)
        OnboardingHaptics.performSuccessTickHaptic(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Single-Canvas Connected Morphing Loader -> Success Tick
            //    The 120-vertex M3 Expressive scalloped polygon and inner sweeping arc
            //    physically coalesce into a closed circular seal and draw the tick in one unbroken stroke.
            M3ConnectedLoadingToSuccessTick(
                isCompleted = isReady,
                size = 148.dp
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 2. In-Place Morphing Editorial Headline
            AnimatedContent(
                targetState = isReady,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 }).togetherWith(
                        fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 }
                    )
                },
                label = "in_place_headline_morph"
            ) { ready ->
                Text(
                    text = if (ready) {
                        if (userName != null) "You're all set, ${userName.substringBefore(" ")}" else "You're all set"
                    } else {
                        "Setting up your workspace"
                    },
                    fontFamily = ClaudeSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    color = MaroonIvoryText,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.3).sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Constant-Height Status Pill (zero layout jump during Loading -> Tick transition)
            val statusLabel = if (isReady) {
                "Your workspace is ready."
            } else {
                loadingSteps[loadingStepIndex]
            }

            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.03f)
                            )
                        )
                    )
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = statusLabel,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 3 }).togetherWith(
                            fadeOut(tween(150)) + slideOutVertically { -it / 3 }
                        )
                    },
                    label = "connected_status_pill_text"
                ) { stepText ->
                    Text(
                        text = stepText,
                        fontFamily = ClaudeSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        color = if (isReady) MaroonIvoryText else MaroonBlushSecondary
                    )
                }
            }
        }

        // 4. Bottom Primary CTA Pill Button (Rises smoothly in-place once the tick locks in)
        AnimatedVisibility(
            visible = isReady,
            enter = fadeIn(tween(320, delayMillis = 180)) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            GlassWhitePrimaryPillButton(
                onClick = { triggerEnterWorkspaceOnce() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("enter_workspace_button")
            ) {
                Text(
                    text = "Enter workspace",
                    fontFamily = ClaudeSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = MaroonIvoryText
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaroonIvoryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Clean Material 3 OutlinedTextField with Always-Visible Label in Claude Sans:
 */
@Composable
private fun M3MaroonLabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    errorText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val isError = errorText != null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = ClaudeSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = if (isError) Color(0xFFFF6B6B) else MaroonIvoryText
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    fontFamily = ClaudeSans,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaroonBlushSecondary.copy(alpha = 0.55f)
                )
            },
            textStyle = TextStyle(
                fontFamily = ClaudeSans,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaroonIvoryText
            ),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = isError,
            visualTransformation = visualTransformation,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Black.copy(alpha = 0.50f),
                unfocusedContainerColor = Color.Black.copy(alpha = 0.36f),
                errorContainerColor = Color.Black.copy(alpha = 0.50f),
                focusedBorderColor = Color.White.copy(alpha = 0.55f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                errorBorderColor = Color(0xFFFF6B6B),
                focusedTextColor = MaroonIvoryText,
                unfocusedTextColor = MaroonIvoryText,
                errorTextColor = MaroonIvoryText,
                cursorColor = MaroonIvoryText,
                errorCursorColor = Color(0xFFFF6B6B)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (errorText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Error",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = errorText,
                    fontFamily = ClaudeSans,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFFF6B6B)
                )
            }
        }
    }
}
