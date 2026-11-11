package com.swiffle.app.ui.viewer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.swiffle.app.theme.OrigamiPDFTheme
import com.swiffle.app.ui.home.getFileName

/**
 * Dedicated Document-Centric Activity for opening Excel (.xlsx, .xls, .csv),
 * Word (.docx, .doc), Markdown (.md), and HTML (.html) files from third-party apps
 * (WhatsApp, Files, Gmail, Chrome, Downloads).
 */
class UniversalViewerActivity : ComponentActivity() {

    private val currentUriState = mutableStateOf<Uri?>(null)
    private val currentTitleState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uri = intent?.data
        if (uri == null) {
            finishAndRemoveTask()
            return
        }

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        val fileName = getFileName(this, uri)
        currentUriState.value = uri
        currentTitleState.value = fileName

        setContent {
            OrigamiPDFTheme {
                val activeUri = currentUriState.value
                val activeTitle = currentTitleState.value

                if (activeUri != null) {
                    UniversalDocumentViewerScreen(
                        uri = activeUri,
                        fileName = activeTitle,
                        onBack = { finishAndRemoveTask() }
                    )
                }
            }
        }
    }
}

