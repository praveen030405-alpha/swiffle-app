package com.swiffle.app

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.swiffle.app.theme.OrigamiPDFTheme
import com.swiffle.app.ui.components.MaroonClothPrimary
import com.swiffle.app.ui.components.SwiffleAuroraBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * High-Precision Multi-Stage State Machine for the Splash-to-App Transition:
 * 1. `MAROON_SPLASH_HOLD`: Renders only the #440E03 Maroon Splash Screen on Frame 0
 *    for instant startup with zero black flash.
 * 2. `INITIALIZE_BLACK_VOID_UNDERLAY`: Initializes the #000000 Black Void background
 *    and pre-composes the target App screen (Onboarding or Home) underneath the opaque
 *    Splash overlay so layout and GPU layer allocation finish with zero visible frame drop.
 * 3. `CONTROLLED_FADE_TO_BLACK`: Executes a controlled two-stage Aurora background
 *    fade-to-black (0f -> 0.58f Aurora wave -> 1.0f pure #000000 Black Void) so the
 *    Black Void background is fully established before the Splash Screen dismisses.
 * 4. `DISMISS_SPLASH_OVERLAY`: Triggers `AnimatedVisibility(visible = false)` on the
 *    Splash overlay, revealing the already-initialized #000000 Black Void App Layer.
 * 5. `APP_VOID_ACTIVE`: Unmounts the Splash overlay and keeps the app locked in #000000.
 */
private enum class SplashTransitionPhase(val step: Int) {
    MAROON_SPLASH_HOLD(0),
    INITIALIZE_BLACK_VOID_UNDERLAY(1),
    CONTROLLED_FADE_TO_BLACK(2),
    DISMISS_SPLASH_OVERLAY(3),
    APP_VOID_ACTIVE(4)
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setBackgroundDrawableResource(R.drawable.bg_window_maroon)

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        try {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        } catch (_: Exception) {}
    }

    enableEdgeToEdge()
    com.swiffle.app.theme.ThemeManager.init(this)

    // Forward any external document open intents to dedicated document viewers
    if (intent?.action == android.content.Intent.ACTION_VIEW && intent?.data != null) {
        val uri = intent.data!!
        val fileName = try { com.swiffle.app.ui.home.getFileName(this, uri).lowercase() } catch (_: Exception) { "" }
        val mime = intent.type?.lowercase() ?: ""

        val isOfficeOrDoc = fileName.endsWith(".xlsx") || fileName.endsWith(".xls") || fileName.endsWith(".csv") ||
                fileName.endsWith(".docx") || fileName.endsWith(".doc") || fileName.endsWith(".md") ||
                fileName.endsWith(".markdown") || fileName.endsWith(".html") || fileName.endsWith(".htm") ||
                mime.contains("spreadsheet") || mime.contains("excel") || mime.contains("csv") ||
                mime.contains("word") || mime.contains("document") || mime.contains("markdown") || mime.contains("html")

        val targetClass = if (isOfficeOrDoc) {
            com.swiffle.app.ui.viewer.UniversalViewerActivity::class.java
        } else {
            com.swiffle.app.ui.canvas.PdfViewerActivity::class.java
        }

        val viewerIntent = android.content.Intent(this, targetClass).apply {
            data = intent.data
            action = android.content.Intent.ACTION_VIEW
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    (intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = intent.clipData
        }
        startActivity(viewerIntent)
        finish()
        return
    }

    // Offload disk I/O and Rust engine bootstrap to background thread so Compose renders immediately on frame 0
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val origamiDir = java.io.File(documentsDir, "ORIGAMI PDF")
            if (!origamiDir.exists()) {
                origamiDir.mkdirs()
            }
        } catch (_: Exception) {}

        try {
            if (NativeEngine.load()) {
                val greeting = NativeEngine.initEngine("Android")
                android.util.Log.d("OrigamiCore", greeting)
            } else {
                android.util.Log.e("OrigamiCore", "Failed to load native engine")
            }
        } catch (e: Throwable) {
            android.util.Log.e("OrigamiCore", "NativeEngine init exception", e)
        }
    }

    val sharedPref = getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
    val hasCompletedOnboarding = sharedPref.getBoolean("hasCompletedOnboarding_v9_statemachine", false)
    val isUserPermanentlyLoggedIn = hasCompletedOnboarding
    val isDirectViewIntent = intent?.action == android.content.Intent.ACTION_VIEW

    setContent {
      var splashPhase by remember {
          mutableStateOf(
              if (isDirectViewIntent) SplashTransitionPhase.APP_VOID_ACTIVE
              else SplashTransitionPhase.MAROON_SPLASH_HOLD
          )
      }
      var showOnboarding by remember {
          mutableStateOf(!isUserPermanentlyLoggedIn && !isDirectViewIntent)
      }
      var onboardingStageIndex by remember { mutableIntStateOf(0) }
      var isOnboardingAllSet by remember { mutableStateOf(false) }

      val darkVoidAnim = remember {
          Animatable(if (isDirectViewIntent) 1f else 0f)
      }

      // Deterministic Multi-Stage State Machine Orchestrator
      LaunchedEffect(Unit) {
          com.swiffle.app.ui.splash.SplashScreenController.resetForNewLaunch()
          if (!isDirectViewIntent) {
              // Stage 0: Hold pure #440E03 Maroon Splash Screen (zero background composition overhead)
              delay(550)

              // Stage 1: Initialize the #000000 Black Void underlay and pre-compose the target App
              // screen while the Splash overlay is still 100% opaque on top (eliminates frame drop)
              splashPhase = SplashTransitionPhase.INITIALIZE_BLACK_VOID_UNDERLAY
              delay(220)
              window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))

              // Stage 2: Execute controlled multi-stage Aurora background fade-to-black
              splashPhase = SplashTransitionPhase.CONTROLLED_FADE_TO_BLACK
              // Sub-stage 2A: Organic Crimson-Rose & Obsidian Aurora wave (0.0 -> 0.58)
              darkVoidAnim.animateTo(
                  targetValue = 0.58f,
                  animationSpec = tween(
                      durationMillis = 720,
                      easing = CubicBezierEasing(0.32f, 0.0f, 0.25f, 1.0f)
                  )
              )
              // Sub-stage 2B: Complete controlled fade into pure #000000 Black Void (0.58 -> 1.0)
              // ensuring the Black Void background is 100% established before Splash dismisses
              darkVoidAnim.animateTo(
                  targetValue = 1.0f,
                  animationSpec = tween(
                      durationMillis = 560,
                      easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                  )
              )

              // Immediately clear heavy Lottie animations and Camera resources upon completion of fade-to-black
              com.swiffle.app.ui.splash.SplashScreenController.disposeHeavyLottieAndCameraResources(this@MainActivity)

              // Stage 3: Dismiss the Splash Screen via AnimatedVisibility now that the
              // Black Void background and App Layer are both 100% initialized and synchronized
              splashPhase = SplashTransitionPhase.DISMISS_SPLASH_OVERLAY
              delay(580)

              // Stage 4: Lock into pure #000000 Black Void App state
              splashPhase = SplashTransitionPhase.APP_VOID_ACTIVE
          }
      }

      val isDarkThemeActive = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()

      OrigamiPDFTheme(darkTheme = isDarkThemeActive) {
          val externalPdfUri = if (isDirectViewIntent) intent?.data else null
          val animatedDarkVoidProgress = darkVoidAnim.value

          val isBlackVoidUnderlayInitialized =
              splashPhase.step >= SplashTransitionPhase.INITIALIZE_BLACK_VOID_UNDERLAY.step
          val isFadeToBlackCompleted =
              splashPhase.step >= SplashTransitionPhase.DISMISS_SPLASH_OVERLAY.step ||
                  animatedDarkVoidProgress >= 0.999f
          val isSplashOverlayVisible =
              splashPhase.step < SplashTransitionPhase.DISMISS_SPLASH_OVERLAY.step

          // Root surface starts at #440E03 Maroon on Frame 0, then locks to #000000 Black Void
          // as soon as the underlay is initialized behind the opaque Splash overlay
          val rootSurfaceColor = if (isBlackVoidUnderlayInitialized) {
              Color(0xFF000000)
          } else {
              lerp(MaroonClothPrimary, Color(0xFF000000), animatedDarkVoidProgress)
          }

          val appLayerRevealAlpha by animateFloatAsState(
              targetValue = if (splashPhase.step >= SplashTransitionPhase.DISMISS_SPLASH_OVERLAY.step) 1f else 0.99f,
              animationSpec = tween(durationMillis = 480, easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)),
              label = "app_layer_reveal_alpha"
          )

          Surface(
              modifier = Modifier.fillMaxSize(),
              color = rootSurfaceColor
          ) {
              Box(modifier = Modifier.fillMaxSize()) {
                  // ── LAYER 1: Pre-Initialized #000000 Black Void + Target App Screen ──
                  if (isBlackVoidUnderlayInitialized) {
                      Box(
                          modifier = Modifier
                              .fillMaxSize()
                              .background(Color(0xFF000000))
                              .graphicsLayer {
                                  alpha = appLayerRevealAlpha
                              }
                      ) {
                          androidx.compose.animation.AnimatedContent(
                              targetState = if (showOnboarding) "onboarding" else "main",
                              transitionSpec = {
                                  com.swiffle.app.theme.SwiffleMotion.sharedAxisZForward()
                              },
                              label = "app_content_transition"
                          ) { screen ->
                              when (screen) {
                                  "onboarding" -> {
                                      com.swiffle.app.ui.onboarding.OnboardingScreen(
                                          onStageChanged = { stageIdx -> onboardingStageIndex = stageIdx },
                                          onAllSetReached = { isOnboardingAllSet = true },
                                          onOnboardingComplete = {
                                              sharedPref.edit()
                                                  .putBoolean("hasCompletedOnboarding", true)
                                                  .putBoolean("hasCompletedOnboarding_v9_statemachine", true)
                                                  .putBoolean("is_user_logged_in", true)
                                                  .apply()
                                              showOnboarding = false
                                          }
                                      )
                                  }
                                  else -> {
                                      var showConvert by remember { mutableStateOf(false) }
                                      var showSettings by remember { mutableStateOf(false) }
                                      var showAiChat by remember { mutableStateOf(false) }
                                      var activePdfUri by remember { mutableStateOf<android.net.Uri?>(null) }

                                      androidx.activity.compose.BackHandler(enabled = showConvert || showAiChat || showSettings) {
                                          when {
                                              showAiChat -> showAiChat = false
                                              showSettings -> showSettings = false
                                              showConvert -> showConvert = false
                                          }
                                      }

                                      val activeMainDestination = when {
                                          showAiChat -> "aichat"
                                          showSettings -> "settings"
                                          showConvert -> "convert"
                                          else -> "home"
                                      }

                                      androidx.compose.animation.AnimatedContent(
                                          targetState = activeMainDestination,
                                          transitionSpec = {
                                              if (targetState != "home" && initialState == "home") {
                                                  com.swiffle.app.theme.SwiffleMotion.sharedAxisZForward()
                                              } else {
                                                  com.swiffle.app.theme.SwiffleMotion.sharedAxisZBackward()
                                              }
                                          },
                                          label = "main_nav"
                                      ) { destination ->
                                          when (destination) {
                                              "aichat" -> {
                                                  com.swiffle.app.ui.chat.SwiffleAiChatScreen(
                                                      onBack = { showAiChat = false }
                                                  )
                                              }
                                              "convert" -> {
                                                  com.swiffle.app.ui.convert.ImageToPdfScreen(
                                                      onBack = { showConvert = false },
                                                      onOpenPdf = { uri, _ ->
                                                          activePdfUri = uri
                                                          showConvert = false
                                                      }
                                                  )
                                              }
                                              "settings" -> {
                                                  com.swiffle.app.ui.settings.SettingsScreen(
                                                      onBack = { showSettings = false },
                                                      onLogout = {
                                                          showSettings = false
                                                          kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                              com.swiffle.app.auth.OrigamiAuth.signOut(this@MainActivity)
                                                          }
                                                          sharedPref.edit()
                                                              .putBoolean("hasCompletedOnboarding", false)
                                                              .putBoolean("hasCompletedOnboarding_v9_statemachine", false)
                                                              .putBoolean("is_user_logged_in", false)
                                                              .remove("google_user_email")
                                                              .remove("google_user_name")
                                                              .remove("google_user_avatar")
                                                              .remove("google_user_avatar_url")
                                                              .remove("user_mode")
                                                              .apply()
                                                          try {
                                                              val avatarFile = java.io.File(filesDir, "custom_profile_avatar.jpg")
                                                              if (avatarFile.exists()) avatarFile.delete()
                                                          } catch (_: Exception) {}
                                                          onboardingStageIndex = 0
                                                          isOnboardingAllSet = false
                                                          showOnboarding = true
                                                      }
                                                  )
                                              }
                                              else -> {
                                                  com.swiffle.app.ui.home.HomeScreen(
                                                      externalPdfUri = activePdfUri ?: externalPdfUri,
                                                      onConsumeExternalPdf = { activePdfUri = null },
                                                      onConvert = { showConvert = true },
                                                      onOpenSettings = { showSettings = true },
                                                      onOpenAiChat = { showAiChat = true },
                                                      onLogout = {
                                                          kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                              com.swiffle.app.auth.OrigamiAuth.signOut(this@MainActivity)
                                                          }
                                                          sharedPref.edit()
                                                              .putBoolean("hasCompletedOnboarding", false)
                                                              .putBoolean("hasCompletedOnboarding_v9_statemachine", false)
                                                              .putBoolean("is_user_logged_in", false)
                                                              .remove("google_user_email")
                                                              .remove("google_user_name")
                                                              .remove("google_user_avatar")
                                                              .remove("google_user_avatar_url")
                                                              .remove("user_mode")
                                                              .apply()
                                                          try {
                                                              val avatarFile = java.io.File(filesDir, "custom_profile_avatar.jpg")
                                                              if (avatarFile.exists()) avatarFile.delete()
                                                          } catch (_: Exception) {}
                                                          onboardingStageIndex = 0
                                                          isOnboardingAllSet = false
                                                          showOnboarding = true
                                                      }
                                                  )
                                              }
                                          }
                                      }
                                  }
                              }
                          }
                      }
                  }

                  // ── LAYER 2: High-Precision AnimatedVisibility Splash Screen Overlay ──
                  AnimatedVisibility(
                      visible = isSplashOverlayVisible,
                      enter = EnterTransition.None,
                      exit = fadeOut(
                          animationSpec = tween(
                              durationMillis = 580,
                              easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                          )
                      ) + scaleOut(
                          targetScale = 1.02f,
                          animationSpec = tween(
                              durationMillis = 580,
                              easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                          )
                      ),
                      modifier = Modifier.fillMaxSize(),
                      label = "splash_to_app_state_machine"
                  ) {
                      Box(modifier = Modifier.fillMaxSize()) {
                          SwiffleAuroraBackground(
                              modifier = Modifier.fillMaxSize(),
                              enabled = !isFadeToBlackCompleted,
                              darkVoidProgress = animatedDarkVoidProgress
                          )
                          com.swiffle.app.ui.splash.SplashScreen(
                              fadeProgress = animatedDarkVoidProgress,
                              isFadeToBlackCompleted = isFadeToBlackCompleted
                          )
                      }
                  }
              }
          }
      }
    }
  }
}
