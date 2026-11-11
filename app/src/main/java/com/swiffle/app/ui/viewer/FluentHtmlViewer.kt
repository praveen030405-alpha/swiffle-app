package com.swiffle.app.ui.viewer

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Windows 11 Fluent In-App HTML Viewer (.html, .htm).
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FluentHtmlViewer(
    uri: Uri,
    fileName: String,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()

    var htmlContent by remember { mutableStateOf<String?>(null) }

    val bgPrimary = if (isDark) Color(0xFF0F0F0F) else Color(0xFFF9F9F9)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val htmlOrange = Color(0xFFE44D26)

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                withContext(Dispatchers.Main) {
                    htmlContent = text
                }
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgPrimary)) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = fileName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textPrimary
                    )
                    Text(
                        text = "HTML Web Document",
                        fontSize = 11.sp,
                        color = htmlOrange,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = textPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (isDark) Color(0xFF181818) else Color(0xFFFFFFFF)
            )
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (htmlContent != null) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_NO_CACHE
                            }
                            setBackgroundColor(if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)

                            val styledHtml = if (isDark) {
                                """
                                <html>
                                <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                  body { background-color: #121212 !important; color: #E0E0E0 !important; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                                  table, th, td { border-color: #333333 !important; }
                                  a { color: #58A6FF !important; }
                                </style>
                                </head>
                                <body>
                                $htmlContent
                                </body>
                                </html>
                                """.trimIndent()
                            } else {
                                htmlContent!!
                            }

                            loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

