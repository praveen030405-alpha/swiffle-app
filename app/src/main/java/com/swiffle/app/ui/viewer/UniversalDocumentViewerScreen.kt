package com.swiffle.app.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.swiffle.app.ui.convert.FluentConversionFeedbackDialog

/**
 * Universal In-App Document Viewer Screen Router.
 * Automatically inspects the file extension / mime type and renders the high-performance
 * Fluent viewer for Excel (.xlsx, .xls), Word (.docx, .doc), Markdown (.md), and HTML (.html).
 */
@Composable
fun UniversalDocumentViewerScreen(
    uri: Uri,
    fileName: String,
    onBack: () -> Unit,
    isNewlyConvertedExcel: Boolean = false
) {
    val context = LocalContext.current
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val extension = fileName.substringAfterLast('.', "").lowercase()

    val handleBack = {
        if (isNewlyConvertedExcel) {
            showFeedbackDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    val shareFile = {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when (extension) {
                    "xlsx", "xls" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "csv" -> "text/csv"
                    "docx", "doc" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "md", "markdown" -> "text/markdown"
                    "html", "htm" -> "text/html"
                    else -> "*/*"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } catch (_: Exception) {}
    }

    when (extension) {
        "xlsx", "xls", "csv" -> {
            FluentExcelViewer(
                uri = uri,
                fileName = fileName,
                onBack = handleBack,
                onShare = shareFile,
                onRequestFeedback = { showFeedbackDialog = true }
            )
        }
        "docx", "doc" -> {
            FluentWordViewer(
                uri = uri,
                fileName = fileName,
                onBack = onBack,
                onShare = shareFile
            )
        }
        "md", "markdown" -> {
            FluentMarkdownViewer(
                uri = uri,
                fileName = fileName,
                onBack = onBack,
                onShare = shareFile
            )
        }
        "html", "htm" -> {
            FluentHtmlViewer(
                uri = uri,
                fileName = fileName,
                onBack = onBack,
                onShare = shareFile
            )
        }
        else -> {
            // Default to Word or Markdown fallback
            FluentWordViewer(
                uri = uri,
                fileName = fileName,
                onBack = onBack,
                onShare = shareFile
            )
        }
    }

    if (showFeedbackDialog) {
        FluentConversionFeedbackDialog(
            onDismiss = {
                showFeedbackDialog = false
                onBack()
            },
            onSubmitFeedback = { _, _ ->
                showFeedbackDialog = false
                onBack()
            }
        )
    }
}

