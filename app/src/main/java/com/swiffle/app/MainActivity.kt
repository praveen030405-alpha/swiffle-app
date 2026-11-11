package com.swiffle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import kotlinx.coroutines.launch
import com.swiffle.app.theme.OrigamiPDFTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
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
    val hasCompletedOnboarding = sharedPref.getBoolean("hasCompletedOnboarding", false)
    val isUserLoggedIn = sharedPref.getBoolean("is_user_logged_in", false)
    val isUserPermanentlyLoggedIn = hasCompletedOnboarding || isUserLoggedIn

    setContent {
      var showSplash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(intent?.action != android.content.Intent.ACTION_VIEW) }
      var showOnboarding by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!isUserPermanentlyLoggedIn && intent?.action != android.content.Intent.ACTION_VIEW) }
      val isDarkThemeActive = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()
      val effectiveDarkTheme = if (showSplash || showOnboarding) false else isDarkThemeActive

      OrigamiPDFTheme(darkTheme = effectiveDarkTheme) { 
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
              
              val externalPdfUri = if (intent?.action == android.content.Intent.ACTION_VIEW) intent?.data else null
              
              var isOnboardingAllSet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

              val currentScreen = when {
                  showSplash -> "splash"
                  showOnboarding -> "onboarding"
                  else -> "main"
              }

              val isAuroraVisible = when {
                  showSplash -> true
                  showOnboarding && !isOnboardingAllSet -> true
                  else -> false
              }

              // Material 3 Expressive seamless Aurora dissolve via spring physics
              val auroraAlpha by androidx.compose.animation.core.animateFloatAsState(
                  targetValue = if (isAuroraVisible) 1f else 0f,
                  animationSpec = androidx.compose.animation.core.spring(
                      dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                      stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                  ),
                  label = "continuous_aurora_alpha"
              )

              Box(modifier = Modifier.fillMaxSize()) {
                  // Google Native Pattern: Continuous Shared Ambient Aurora Canvas!
                  // Persists smoothly across Splash and Onboarding without tearing or stutter,
                  // then gently dissolves as the user enters the Home Workspace.
                  if (auroraAlpha > 0.002f) {
                      com.swiffle.app.ui.components.SwiffleAuroraBackground(
                          modifier = Modifier
                              .fillMaxSize()
                              .graphicsLayer { 
                                  alpha = auroraAlpha 
                              },
                          enabled = isAuroraVisible
                      )
                  }

                  androidx.compose.animation.AnimatedContent(
                      targetState = currentScreen,
                      transitionSpec = {
                          if (initialState == "splash" && targetState == "onboarding") {
                              // Seamless shared-position crossfade: logo stays anchored while Get Started controls glide in
                              (androidx.compose.animation.fadeIn(
                                  animationSpec = androidx.compose.animation.core.tween(350, easing = com.swiffle.app.theme.SwiffleMotion.EmphasizedDecelerate)
                              )).togetherWith(
                                  androidx.compose.animation.fadeOut(
                                      animationSpec = androidx.compose.animation.core.tween(180, easing = com.swiffle.app.theme.SwiffleMotion.EmphasizedAccelerate)
                                  )
                              )
                          } else {
                              com.swiffle.app.theme.SwiffleMotion.horizontalForward()
                          }
                      },
                      label = "root_screen_transition"
                  ) { screen ->
                  when (screen) {
                      "splash" -> {
                          com.swiffle.app.ui.splash.SplashScreen(onSplashComplete = { showSplash = false })
                      }
                      "onboarding" -> {
                          com.swiffle.app.ui.onboarding.OnboardingScreen(
                              onAllSetReached = { isOnboardingAllSet = true },
                              onOnboardingComplete = { 
                                  sharedPref.edit()
                                      .putBoolean("hasCompletedOnboarding", true)
                                      .putBoolean("is_user_logged_in", true)
                                      .apply()
                                  showOnboarding = false 
                              }
                          )
                      }
                      "main" -> {
                          var showConvert by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                          var showSettings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                          var showAiChat by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                          var activePdfUri by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }

                          // Handle system back button smoothly without exiting the app
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
                                      com.swiffle.app.theme.SwiffleMotion.horizontalForward()
                                  } else {
                                      com.swiffle.app.theme.SwiffleMotion.horizontalBackward()
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
      }
    }
  }
}
