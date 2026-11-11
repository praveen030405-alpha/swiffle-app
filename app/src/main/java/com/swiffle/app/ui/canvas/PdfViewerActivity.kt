package com.swiffle.app.ui.canvas

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.swiffle.app.NativeEngine
import com.swiffle.app.theme.OrigamiPDFTheme
import com.swiffle.app.ui.home.getFileName
import com.swiffle.app.ui.home.getFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dedicated Document-Centric Activity for opening PDFs from Third-Party Applications:
 * - Optimized for blazing-fast cold launch with zero UI-thread contention.
 * - Automatically purges from Android's Recent Apps when closed.
 * - Shows smooth Material 3 Expressive theme-adaptive Opening and Closing pills.
 */
class PdfViewerActivity : ComponentActivity() {

    private val currentUriState = mutableStateOf<Uri?>(null)
    private val currentTitleState = mutableStateOf("")
    private val activityScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Asynchronously ensure NativeEngine is loaded without stalling frame 0
        NativeEngine.load()

        val uri = intent?.data
        if (uri == null) {
            finishAndRemoveTask()
            return
        }

        handleIncomingUri(uri)

        setContent {
            OrigamiPDFTheme {
                val activeUri = currentUriState.value
                val activeTitle = currentTitleState.value

                if (activeUri != null) {
                    PdfCanvasScreen(
                        pdfUri = activeUri,
                        pdfTitle = activeTitle,
                        onBack = { finishAndRemoveTask() },
                        isExternalWindow = true
                    )
                }
            }
        }
    }

    override fun finish() {
        // Bug 1: Ensure closing PDF purges the document entry from Recent Apps
        finishAndRemoveTask()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { handleIncomingUri(it) }
    }

    private fun handleIncomingUri(uri: Uri) {
        // Take persistable permission if granted by caller
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        // Instant Frame-0 Title (derived without IPC blocking)
        val initialName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Document.pdf"
        currentUriState.value = uri
        currentTitleState.value = initialName

        // Background IPC: Query true display name and save to recent documents asynchronously
        activityScope.launch {
            val trueName = getFileName(this@PdfViewerActivity, uri).takeIf { it.isNotBlank() } ?: initialName
            currentTitleState.value = trueName

            // Set document task title in Android's Recent Apps switcher
            try {
                @Suppress("DEPRECATION")
                setTaskDescription(ActivityManager.TaskDescription(trueName))
            } catch (_: Exception) {}

            saveToRecentFiles(uri, trueName)
        }
    }

    private fun saveToRecentFiles(uri: Uri, name: String) {
        try {
            val sharedPref = getSharedPreferences("OrigamiPrefs", Context.MODE_PRIVATE)
            val jsonStr = sharedPref.getString("recentFiles", "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            val list = mutableListOf<JSONObject>()
            var existingSize = 0L
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("uri") == uri.toString()) {
                    existingSize = obj.optLong("size", 0L)
                } else {
                    list.add(obj)
                }
            }

            val finalSize = if (existingSize > 0) {
                existingSize
            } else {
                val queried = getFileSize(this@PdfViewerActivity, uri)
                if (queried > 0) queried else existingSize
            }

            val newObj = JSONObject().apply {
                put("uri", uri.toString())
                put("name", name)
                put("timestamp", System.currentTimeMillis())
                put("size", finalSize)
            }
            list.add(0, newObj)

            val newArray = JSONArray()
            list.take(15).forEach { newArray.put(it) }
            sharedPref.edit().putString("recentFiles", newArray.toString()).apply()
        } catch (_: Exception) {}
    }
}
