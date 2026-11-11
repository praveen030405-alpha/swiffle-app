package com.swiffle.app.ui.canvas.links

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * Encapsulates an active In-App Web Browser Tab Session.
 *
 * Persists the live hardware-accelerated WebView in memory across minimizes/expands,
 * preserving DOM tree, form fields, playback, scroll position, and history back-stack.
 */
class BrowserTabSession(
    val id: String = UUID.randomUUID().toString(),
    val initialUrl: String,
    context: Context
) {
    var currentUrl by mutableStateOf(initialUrl)
    var pageTitle by mutableStateOf("Loading...")
    var loadingProgress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(true)
    var favicon by mutableStateOf<Bitmap?>(null)

    val hostName: String
        get() {
            return try {
                val uri = Uri.parse(currentUrl)
                uri.host?.removePrefix("www.") ?: currentUrl
            } catch (_: Exception) {
                currentUrl
            }
        }

    val webView: WebView = WebView(context.applicationContext).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadingProgress = newProgress
                isLoading = newProgress < 100
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    pageTitle = title
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                if (icon != null) {
                    this@BrowserTabSession.favicon = icon
                }
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, fav: Bitmap?) {
                isLoading = true
                if (!url.isNullOrBlank()) currentUrl = url
                if (fav != null) this@BrowserTabSession.favicon = fav
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isLoading = false
                if (!url.isNullOrBlank()) currentUrl = url
                if (!view?.title.isNullOrBlank()) pageTitle = view?.title ?: ""
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val reqUri = request?.url ?: return false
                val scheme = reqUri.scheme?.lowercase() ?: ""

                if (scheme != "http" && scheme != "https") {
                    return try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, reqUri).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                return false
            }
        }

        loadUrl(initialUrl)
    }

    fun destroy() {
        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) {}
    }
}

/**
 * Controller managing multi-tab side sheet state and docked edge tags.
 */
class BrowserTabManager {
    val tabs = mutableStateListOf<BrowserTabSession>()
    var activeTabId by mutableStateOf<String?>(null)

    val activeTab: BrowserTabSession?
        get() = tabs.firstOrNull { it.id == activeTabId }

    fun openUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        // If this URL is already open in an existing tab, simply bring it to front
        val existing = tabs.firstOrNull { it.currentUrl == trimmed || it.initialUrl == trimmed }
        if (existing != null) {
            activeTabId = existing.id
            return
        }

        // Otherwise open a new tab session
        val newSession = BrowserTabSession(
            initialUrl = trimmed,
            context = context
        )
        tabs.add(newSession)
        activeTabId = newSession.id
    }

    fun minimizeActiveTab() {
        activeTabId = null
    }

    fun activateTab(id: String) {
        activeTabId = id
    }

    fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index != -1) {
            val session = tabs.removeAt(index)
            session.destroy()
            if (activeTabId == id) {
                // If there are other tabs, activate the latest one, else none
                activeTabId = tabs.lastOrNull()?.id
            }
        }
    }

    fun closeAll() {
        tabs.forEach { it.destroy() }
        tabs.clear()
        activeTabId = null
    }
}
