package com.swiffle.app.ui.viewer

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val lang: String = "") : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

/**
 * Windows 11 Fluent In-App Markdown Viewer (.md, .markdown).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluentMarkdownViewer(
    uri: Uri,
    fileName: String,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()

    var blocks by remember { mutableStateOf<List<MarkdownBlock>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val bgPrimary = if (isDark) Color(0xFF0F0F0F) else Color(0xFFF9F9F9)
    val cardBg = if (isDark) Color(0xFF181818) else Color(0xFFFFFFFF)
    val strokeColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E1E1E)
    val textMuted = if (isDark) Color(0xFF8A8886) else Color(0xFF605E5C)
    val accentPurple = Color(0xFF744DA9)
    val codeBg = if (isDark) Color(0xFF121212) else Color(0xFFEFEFEF)

    LaunchedEffect(uri) {
        isLoading = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Cannot open markdown stream")
                val text = inputStream.bufferedReader().use { it.readText() }
                val parsed = parseMarkdown(text)
                withContext(Dispatchers.Main) {
                    blocks = parsed
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg = e.localizedMessage ?: "Failed to read Markdown file"
                    isLoading = false
                }
            }
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
                        text = "Markdown Document",
                        fontSize = 11.sp,
                        color = accentPurple,
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

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accentPurple, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Parsing Markdown…", fontSize = 13.sp, color = textMuted)
                }
            }
        } else if (errorMsg != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Error: $errorMsg",
                    fontSize = 14.sp,
                    color = Color.Red,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, strokeColor, RoundedCornerShape(8.dp)),
                        color = cardBg,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            blocks.forEach { block ->
                                when (block) {
                                    is MarkdownBlock.Header -> {
                                        val fontSize = when (block.level) {
                                            1 -> 22.sp
                                            2 -> 19.sp
                                            3 -> 17.sp
                                            else -> 15.sp
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = block.text,
                                            fontSize = fontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    is MarkdownBlock.Paragraph -> {
                                        Text(
                                            text = block.text,
                                            fontSize = 14.sp,
                                            color = textPrimary,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    is MarkdownBlock.BulletItem -> {
                                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                            Text(text = "•", color = accentPurple, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = block.text, fontSize = 14.sp, color = textPrimary)
                                        }
                                    }
                                    is MarkdownBlock.CodeBlock -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(codeBg)
                                                .border(1.dp, strokeColor, RoundedCornerShape(6.dp))
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = block.code,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = textPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    is MarkdownBlock.Table -> {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, strokeColor, RoundedCornerShape(4.dp))
                                        ) {
                                            // Header
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(accentPurple.copy(alpha = 0.12f))
                                                    .border(0.5.dp, strokeColor)
                                            ) {
                                                block.headers.forEach { h ->
                                                    Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                                                        Text(text = h, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                                    }
                                                }
                                            }
                                            // Rows
                                            block.rows.forEach { row ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .border(0.5.dp, strokeColor)
                                                ) {
                                                    row.forEach { c ->
                                                        Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                                                            Text(text = c, fontSize = 11.sp, color = textPrimary)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    MarkdownBlock.Divider -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(strokeColor)
                                                .padding(vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun parseMarkdown(rawText: String): List<MarkdownBlock> {
    val lines = rawText.lines()
    val blocks = mutableListOf<MarkdownBlock>()

    var inCodeBlock = false
    var codeBuffer = StringBuilder()
    var codeLang = ""

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(code = codeBuffer.toString().trimEnd(), lang = codeLang))
                codeBuffer = StringBuilder()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeBuffer.append(line).append("\n")
            continue
        }

        when {
            trimmed.startsWith("### ") -> blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ").trim()))
            trimmed.startsWith("## ") -> blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ").trim()))
            trimmed.startsWith("# ") -> blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ").trim()))
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.substring(2).trim()))
            }
            trimmed == "---" || trimmed == "***" -> blocks.add(MarkdownBlock.Divider)
            trimmed.isNotBlank() -> blocks.add(MarkdownBlock.Paragraph(line))
        }
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(code = codeBuffer.toString().trimEnd(), lang = codeLang))
    }

    return blocks
}
