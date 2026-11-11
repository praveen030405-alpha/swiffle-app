package com.swiffle.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swiffle.app.R
import com.swiffle.app.ai.SwiffleAiEngine
import com.swiffle.app.ai.SwiffleAiStreamEvent
import com.swiffle.app.ai.SwiffleAiTask
import com.swiffle.app.theme.GoogleSans
import com.swiffle.app.theme.SwiffleMotion
import com.swiffle.app.theme.SwiffleMotion.FluentNormalDuration
import com.swiffle.app.theme.SwiffleMotion.FluentFastDuration
import com.swiffle.app.theme.SwiffleMotion.FluentDecelerate
import com.swiffle.app.theme.SwiffleMotion.FluentAccelerate
import com.swiffle.app.ui.canvas.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Windows 11 Fluent Design Swiffle AI Chat Screen (WinUI 3 Architecture):
 * - Windows 11 Mica Surface Layering & Specular Hairline Contours.
 * - Windows 11 Integrated TitleBar with Copilot Starlight Emblem.
 * - Authentic Windows Copilot Thinking Animation (Iridescent 4-Point Starlight & Harmonic Micro-Nodes).
 * - WinUI 3 Fluent Cards (8dp Corner Radii, Elevation 1-2, Cobalt Accent).
 * - WinUI 3 Fluent CommandBar Input Dock with solid Cobalt Send & Stop buttons.
 * - Windows 11 Expander for Collapsible Step-by-Step Reasoning with Fluent 250ms Transitions.
 */
@Composable
fun SwiffleAiChatScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = com.swiffle.app.theme.ThemeManager.isDarkThemeActive()

    BackHandler {
        onBack()
    }

    // Windows 11 Fluent Theme Palette
    val micaBg = if (isDark) Color(0xFF202020) else Color(0xFFF3F3F3)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1F1F1F)
    val textSecondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF5D5D5D)
    val borderColor = if (isDark) Color(0xFF383838) else Color(0xFFE5E5E5)

    val aiEngine = remember { SwiffleAiEngine(context) }
    val sharedPrefs = remember { context.getSharedPreferences("swiffle_usage_prefs", Context.MODE_PRIVATE) }

    var inputQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf<SwiffleChatMessage>() }

    var activeStreamingJob by remember { mutableStateOf<Job?>(null) }
    var activeQuizCorrectOption by remember { mutableStateOf<String?>(null) }
    var activeQuizExplanation by remember { mutableStateOf<String?>(null) }

    // Active Attachments State (Real Document & Image Analysis)
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachedDocumentText by remember { mutableStateOf<String?>(null) }
    var attachedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isExtractingAttachment by remember { mutableStateOf(false) }

    // TTS Read Aloud engine
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        tts = ttsInstance
        onDispose {
            activeStreamingJob?.cancel()
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    fun readAloud(text: String) {
        val clean = text.replace(Regex("[#*`|\\[\\]()]"), "").trim()
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "swiffle_tts")
    }

    fun recordTokenUsage(tokenCount: Int) {
        com.swiffle.app.ai.SwiffleAiConfig.recordTokenUsage(context, tokenCount)
    }

    fun streamAiResponse(
        assistantMsgId: String,
        userPrompt: String,
        attachmentContext: String? = null
    ) {
        activeStreamingJob?.cancel()
        activeStreamingJob = coroutineScope.launch {
            val thinkingStartMs = System.currentTimeMillis()

            val sessionMemoryContext = buildString {
                if (!attachmentContext.isNullOrBlank()) {
                    append("Attached Document / Image Content:\n\"\"\"\n")
                    append(attachmentContext.trim())
                    append("\n\"\"\"\n\n")
                }
                if (messages.size > 2) {
                    append("Prior Conversation History:\n")
                    messages.takeLast(8).forEach { msg ->
                        if (msg.id != assistantMsgId && msg.text.isNotBlank()) {
                            append(if (msg.isUser) "User: " else "Assistant: ")
                            append(msg.text.take(200))
                            append("\n")
                        }
                    }
                    append("\n")
                }
            }

            var hasReceivedTokens = false
            var accumulatedText = ""
            var accumulatedThinking = ""
            var tokenCount = 0

            aiEngine.query(
                userPrompt = userPrompt,
                documentContext = sessionMemoryContext.ifBlank { null },
                task = SwiffleAiTask.AskQuestion
            ).catch { e ->
                val idx = messages.indexOfFirst { it.id == assistantMsgId }
                if (idx != -1) {
                    messages[idx] = messages[idx].copy(
                        isThinking = false,
                        isStreaming = false,
                        text = "⚠️ **Connection Notice**\n\n${e.localizedMessage ?: "Unable to stream response from Swiffle AI. Please check your internet connection."}"
                    )
                }
            }.collect { event ->
                when (event) {
                    is SwiffleAiStreamEvent.Thinking -> {
                        accumulatedThinking += event.text
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            messages[idx] = messages[idx].copy(
                                thinkingText = accumulatedThinking,
                                activePhaseText = "Thinking..."
                            )
                        }
                    }
                    is SwiffleAiStreamEvent.Token -> {
                        tokenCount++
                        if (!hasReceivedTokens) {
                            hasReceivedTokens = true
                            val idx = messages.indexOfFirst { it.id == assistantMsgId }
                            val wasThinking = if (idx != -1) (messages[idx].isThinking || !messages[idx].thinkingText.isNullOrBlank()) else false
                            val elapsedSec = if (wasThinking) {
                                ((System.currentTimeMillis() - thinkingStartMs) / 1000L).coerceAtLeast(1L).toInt()
                            } else null
                            if (idx != -1) {
                                messages[idx] = messages[idx].copy(
                                    isThinking = false,
                                    isStreaming = true,
                                    thinkingDurationSec = elapsedSec,
                                    thinkingText = if (wasThinking) accumulatedThinking.ifBlank { messages[idx].thinkingText } else null,
                                    text = ""
                                )
                            }
                        }
                        accumulatedText += event.text
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            messages[idx] = messages[idx].copy(text = accumulatedText)
                        }
                    }
                    is SwiffleAiStreamEvent.Completed -> {
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            val elapsedSec = if (messages[idx].thinkingDurationSec != null) {
                                messages[idx].thinkingDurationSec
                            } else {
                                ((System.currentTimeMillis() - thinkingStartMs) / 1000L).coerceAtLeast(1L).toInt()
                            }
                            messages[idx] = messages[idx].copy(
                                isStreaming = false,
                                isThinking = false,
                                thinkingDurationSec = elapsedSec,
                                text = accumulatedText.ifBlank { event.fullText }
                            )
                        }
                        recordTokenUsage(tokenCount)
                    }
                    is SwiffleAiStreamEvent.Error -> {
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            messages[idx] = messages[idx].copy(
                                isThinking = false,
                                isStreaming = false,
                                text = if (accumulatedText.isNotBlank()) accumulatedText else "⚠️ **AI Engine Notice**\n\n${event.error.localizedMessage ?: "Stream encountered an error. Please try again."}"
                            )
                        }
                    }
                    is SwiffleAiStreamEvent.OfflineModelRequired -> {
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            messages[idx] = messages[idx].copy(
                                isThinking = false,
                                isStreaming = false,
                                text = "⚠️ **Offline Mode Active**\n\nYou are currently offline with zero internet connection.\n\nTo run AI reasoning 100% locally on your smartphone, download the **On-Device Neural Model (171 MB)** in **Settings > AI Settings**."
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun handleSend(query: String) {
        val trimmed = query.trim()
        val currentFileName = attachedFileName
        val currentPhotoUri = attachedPhotoUri
        val currentDocText = attachedDocumentText

        if (trimmed.isEmpty() && currentFileName == null && currentPhotoUri == null) return

        val displayPrompt = when {
            trimmed.isNotEmpty() -> trimmed
            currentFileName != null -> "Please analyze this attached document '$currentFileName', outline key findings, and summarize core insights."
            currentPhotoUri != null -> "Please inspect this attached photo, identify key visual text/elements, and provide a structured breakdown."
            else -> "Hello"
        }

        // Reset attachment state for next message
        attachedFileName = null
        attachedPhotoUri = null
        attachedDocumentText = null

        val runningIdx = messages.indexOfLast { it.isStreaming || it.isThinking }
        if (runningIdx != -1) {
            messages[runningIdx] = messages[runningIdx].copy(isStreaming = false, isThinking = false)
        }

        inputQuery = ""
        val userMsg = SwiffleChatMessage(
            isUser = true,
            text = displayPrompt,
            attachedFileName = currentFileName,
            attachedPhotoUri = currentPhotoUri
        )
        messages.add(userMsg)

        val assistantMsgId = UUID.randomUUID().toString()
        messages.add(
            SwiffleChatMessage(
                id = assistantMsgId,
                isUser = false,
                text = "",
                thinkingText = "",
                activePhaseText = "Thinking...",
                isThinking = true,
                isStreaming = false
            )
        )

        val userMsgIdx = (messages.size - 2).coerceAtLeast(0)
        coroutineScope.launch {
            delay(50)
            listState.animateScrollToItem(index = userMsgIdx, scrollOffset = 0)
        }

        streamAiResponse(
            assistantMsgId = assistantMsgId,
            userPrompt = displayPrompt,
            attachmentContext = currentDocText
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = getFileName(context, uri) ?: "document.pdf"
            attachedFileName = name
            attachedPhotoUri = null
            isExtractingAttachment = true
            coroutineScope.launch(Dispatchers.IO) {
                val extracted = extractTextFromDocUri(context, uri)
                withContext(Dispatchers.Main) {
                    attachedDocumentText = extracted
                    isExtractingAttachment = false
                    val words = extracted.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    Toast.makeText(context, "Attached '$name' ($words words extracted)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedPhotoUri = uri
            attachedFileName = null
            isExtractingAttachment = true
            coroutineScope.launch(Dispatchers.IO) {
                val extracted = extractOcrFromImageUri(context, uri)
                withContext(Dispatchers.Main) {
                    attachedDocumentText = extracted
                    isExtractingAttachment = false
                    val words = extracted.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    Toast.makeText(context, "Photo attached & scanned ($words words detected)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraPhotoUri
        if (success && uri != null) {
            attachedPhotoUri = uri
            attachedFileName = null
            isExtractingAttachment = true
            coroutineScope.launch(Dispatchers.IO) {
                val extracted = extractOcrFromImageUri(context, uri)
                withContext(Dispatchers.Main) {
                    attachedDocumentText = extracted
                    isExtractingAttachment = false
                    val words = extracted.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    Toast.makeText(context, "Camera snapshot attached ($words words detected)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            delay(80)
            val userIdx = (messages.size - 2).coerceAtLeast(0)
            listState.animateScrollToItem(userIdx, scrollOffset = 0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(micaBg)
            .statusBarsPadding()
    ) {
        // Windows 11 Fluent Integrated TitleBar & CommandBar Header
        FluentChatTitleBar(
            isDark = isDark,
            onBack = onBack,
            onNewChat = {
                activeStreamingJob?.cancel()
                activeStreamingJob = null
                messages.clear()
                inputQuery = ""
            }
        )

        // Chat Message Stream
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                FluentChatHeroView(
                    isDark = isDark,
                    onPromptClick = { prompt -> handleSend(prompt) }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.isUser) {
                            FluentUserMessageBubble(
                                message = msg,
                                isDark = isDark
                            )
                        } else {
                            FluentAssistantMessageBubble(
                                message = msg,
                                isDark = isDark,
                                onToggleThinking = {
                                    val idx = messages.indexOfFirst { it.id == msg.id }
                                    if (idx != -1) {
                                        messages[idx] = messages[idx].copy(
                                            isThinkingExpanded = !messages[idx].isThinkingExpanded
                                        )
                                    }
                                },
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Swiffle Copilot", msg.text))
                                },
                                onReadAloud = { readAloud(msg.text) }
                            )
                        }
                    }
                }
            }

            // Top Header Specular Vignette
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                micaBg.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Windows 11 Fluent CommandBar Bottom Input Dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            val isAiGenerating = messages.any { it.isStreaming || it.isThinking }
            val speechRecognizerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    val spokenText = matches?.firstOrNull()?.trim()
                    if (!spokenText.isNullOrBlank()) {
                        inputQuery = if (inputQuery.isBlank()) spokenText else "$inputQuery $spokenText"
                    }
                }
            }
            FluentChatInputDock(
                query = inputQuery,
                isDark = isDark,
                isGenerating = isAiGenerating,
                attachedFileName = attachedFileName,
                attachedPhotoUri = attachedPhotoUri,
                isExtractingAttachment = isExtractingAttachment,
                onRemoveAttachment = {
                    attachedFileName = null
                    attachedPhotoUri = null
                    attachedDocumentText = null
                },
                onQueryChange = { inputQuery = it },
                onSend = { handleSend(inputQuery) },
                onStop = {
                    activeStreamingJob?.cancel()
                    activeStreamingJob = null
                    val sIdx = messages.indexOfLast { it.isStreaming || it.isThinking }
                    if (sIdx != -1) {
                        messages[sIdx] = messages[sIdx].copy(isStreaming = false, isThinking = false)
                    }
                },
                onMicClick = {
                    try {
                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to Swiffle AI...")
                        }
                        speechRecognizerLauncher.launch(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Voice dictation is not available on this device.", Toast.LENGTH_SHORT).show()
                    }
                },
                onUploadFiles = {
                    try {
                        filePickerLauncher.launch(arrayOf("application/pdf", "text/*", "*/*"))
                    } catch (_: Exception) {}
                },
                onPhotos = {
                    try {
                        photoPickerLauncher.launch("image/*")
                    } catch (_: Exception) {}
                },
                onCamera = {
                    try {
                        val cameraDir = File(context.cacheDir, "ai_photos").apply { mkdirs() }
                        val photoFile = File(cameraDir, "camera_${System.currentTimeMillis()}.jpg")
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                        cameraPhotoUri = uri
                        cameraLauncher.launch(uri)
                    } catch (_: Exception) {}
                }
            )
        }
    }
}

/**
 * Windows 11 Fluent TitleBar / CommandBar Header:
 * - 48dp height, Segoe/GoogleSans typography ramp.
 * - Copilot 4-pointed iridescent star emblem.
 * - Specular bottom hairline divider.
 */
@Composable
private fun FluentChatTitleBar(
    isDark: Boolean,
    onBack: () -> Unit,
    onNewChat: () -> Unit
) {
    val barBg = if (isDark) Color(0xFF202020) else Color(0xFFF3F3F3)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1F1F1F)
    val textSecondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF5D5D5D)
    val dividerColor = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E5E5)

    Column(modifier = Modifier.fillMaxWidth().background(barBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fluent Square Back Button (4dp corner radius, 36x36dp)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onBack() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Swiffle AI Emblem (Gemini AI Studio Halo + Small Swiffle SVG)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF4285F4).copy(alpha = 0.16f),
                                Color(0xFF9B72CB).copy(alpha = 0.18f),
                                Color(0xFFD96570).copy(alpha = 0.14f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_swiffle_logo),
                    contentDescription = "Swiffle Copilot",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Swiffle Copilot",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF107C41)) // Fluent Green
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Windows 11 Fluent Intelligence • Active",
                        fontFamily = GoogleSans,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary
                    )
                }
            }

            // New Chat / Clear Action Button (WinUI 3 4dp button)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isDark) Color(0xFF2B2B2B) else Color(0xFFEBEBEB),
                border = BorderStroke(1.dp, dividerColor),
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onNewChat() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "New Chat",
                        tint = textPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "New Chat",
                        fontFamily = GoogleSans,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                }
            }
        }

        // Specular bottom hairline divider
        HorizontalDivider(thickness = 1.dp, color = dividerColor)
    }
}

/**
 * Windows 11 Fluent User Message Bubble:
 * - Windows 11 Cobalt Blue Surface (#0078D4 / #005FB8).
 * - Standard 10dp corner radius.
 * - Supports attached photo thumbnails & attached document pills.
 */
@Composable
private fun FluentUserMessageBubble(
    message: SwiffleChatMessage,
    isDark: Boolean
) {
    val accentBg = if (isDark) Color(0xFF005FB8) else Color(0xFF0078D4)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accentBg,
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            shadowElevation = 1.5.dp,
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Attached Photo Preview
                if (message.attachedPhotoUri != null) {
                    AsyncImage(
                        model = message.attachedPhotoUri,
                        contentDescription = "Attached Photo",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Attached Document File Pill
                if (!message.attachedFileName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message.attachedFileName,
                                fontFamily = GoogleSans,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = message.text,
                    fontFamily = GoogleSans,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Windows 11 Fluent Assistant Message (Open, Unboxed Presentation):
 * - Direct, elegant typography rendered on the background (NO enclosing card/box).
 * - Swiffle SVG Emblem & live stream tag.
 * - Gemini AI Studio real-time thinking animation with small Swiffle SVG inside.
 * - Clean minimal action bar at bottom.
 */
@Composable
private fun FluentAssistantMessageBubble(
    message: SwiffleChatMessage,
    isDark: Boolean,
    onToggleThinking: () -> Unit,
    onCopy: () -> Unit,
    onReadAloud: () -> Unit
) {
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1F1F1F)
    val textSecondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF5D5D5D)
    var isCopied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Real Reasoning & Thinking Section (Left-aligned, no AI logo/Swiffle Copilot header)
        if (message.isThinking) {
            FluentCopilotThinkingIndicator(
                thoughtText = message.thinkingText?.ifBlank { "Thinking..." } ?: "Thinking...",
                isDark = isDark
            )
        } else if (!message.thinkingText.isNullOrBlank()) {
            FluentReasoningExpander(
                durationSec = message.thinkingDurationSec ?: 1,
                thinkingText = message.thinkingText,
                isExpanded = message.isThinkingExpanded,
                onToggle = onToggleThinking,
                isDark = isDark
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Answer Body - Rendered directly onto mica background with NO enclosing box!
        if (message.text.isNotEmpty()) {
            RichMarkdownBody(
                text = message.text,
                isDark = isDark,
                textPrimary = textPrimary
            )
        }

        // Subtle Icon Action Row at bottom
        if (!message.isStreaming && !message.isThinking && message.text.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onCopy()
                        isCopied = true
                        coroutineScope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (isCopied) Color(0xFF107C41) else textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onReadAloud,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Read aloud",
                        tint = textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact Gemini AI Studio Thinking Animation with Small Swiffle SVG Inside:
 * - Features a continuous 360° rotating monochrome orbital ring with dynamic expanding/contracting
 *   arc sweep (zero color shifting).
 * - Centered inside a compact 20.dp orb: the small Swiffle SVG (`R.drawable.ic_swiffle_logo` at 11.dp)
 *   with a smooth breathing pulse.
 * - Accompanied by a clean, monochrome luminance shimmer across the status text ("Thinking...").
 */
@Composable
fun FluentCopilotThinkingIndicator(
    thoughtText: String = "Thinking...",
    isDark: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GeminiAiStudioThinking")

    // Primary 360° continuous orbital rotation for the monochrome ring
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GeminiOrbitRotation"
    )

    // Dynamic arc sweep expansion/contraction (authentic Google AI Studio spinner feel)
    val arcSweep by infiniteTransition.animateFloat(
        initialValue = 75f,
        targetValue = 265f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GeminiArcSweep"
    )

    // Gentle breathing pulse for the inner small Swiffle SVG
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SwiffleLogoPulse"
    )

    // Monochrome luminance shimmer sweep across the "Thinking..." label (no color changing)
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -140f,
        targetValue = 460f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MonochromeTextShimmer"
    )

    val ringTone = if (isDark) Color(0xFFE4E4E7) else Color(0xFF3F3F46)
    val ringTrackColor = ringTone.copy(alpha = if (isDark) 0.14f else 0.12f)
    val monochromeRingColors = remember(isDark) {
        listOf(
            ringTone.copy(alpha = 0.08f),
            ringTone.copy(alpha = 0.45f),
            ringTone.copy(alpha = 0.92f),
            ringTone.copy(alpha = 0.08f)
        )
    }

    val baseTextColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF71717A)
    val highlightTextColor = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B)

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            baseTextColor,
            highlightTextColor,
            baseTextColor
        ),
        start = Offset(shimmerTranslate - 120f, 0f),
        end = Offset(shimmerTranslate, 0f)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Compact 20.dp Orbital Ring with Small 11.dp Swiffle SVG Inside
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. Subtle track + rotating monochrome arc (no color changing)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 1.5.dp.toPx()
                val radius = (size.minDimension - strokeWidthPx) / 2f

                // Subtle background track ring
                drawCircle(
                    color = ringTrackColor,
                    radius = radius,
                    style = Stroke(width = 1.0.dp.toPx())
                )

                // Rotating monochrome arc
                rotate(degrees = orbitRotation) {
                    drawArc(
                        brush = Brush.sweepGradient(monochromeRingColors),
                        startAngle = 0f,
                        sweepAngle = arcSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }

            // 2. Small Swiffle SVG centered inside the compact ring
            Image(
                painter = painterResource(id = R.drawable.ic_swiffle_logo),
                contentDescription = "Swiffle AI Thinking",
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Monochrome Luminance Shimmering Thought Label (no color changing)
        Text(
            text = if (thoughtText.isNotBlank()) thoughtText else "Thinking...",
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(brush = shimmerBrush),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Clean Left-Aligned Reasoning Expander:
 * - Strictly left-aligned "Thought for ${duration}s" with rotating chevron (no AI logo, no "Reasoning Complete" pill).
 * - Expands to reveal step-by-step reasoning transcript.
 */
@Composable
private fun FluentReasoningExpander(
    durationSec: Int,
    thinkingText: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isDark: Boolean
) {
    val textSecondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF5D5D5D)

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(FluentNormalDuration, easing = FluentDecelerate),
        label = "FluentChevronRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggle() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "Thought for ${durationSec}s",
                fontFamily = GoogleSans,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(FluentNormalDuration, easing = FluentDecelerate)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(FluentFastDuration, easing = FluentAccelerate)) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, top = 4.dp, bottom = 6.dp)
            ) {
                // Left vertical accent guide
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .defaultMinSize(minHeight = 20.dp)
                        .background(Color(0xFF9B72CB).copy(alpha = 0.55f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = thinkingText,
                    fontFamily = GoogleSans,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = textSecondary
                )
            }
        }
    }
}

/**
 * Windows 11 Fluent Hero View (Empty State):
 * - Large Swiffle SVG emblem with Gemini multi-tone gradient aura.
 * - Title & Subtitle Segoe ramp.
 * - 4 WinUI 3 styled Prompt Cards with 8dp rounded corners & hover lift.
 */
@Composable
private fun FluentChatHeroView(
    isDark: Boolean,
    onPromptClick: (String) -> Unit
) {
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1F1F1F)
    val textSecondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF5D5D5D)
    val cardBg = if (isDark) Color(0xFF2B2B2B) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF383838) else Color(0xFFE5E5E5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 80.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Large Swiffle SVG Emblem inside Gemini Aurora Container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF2B2B2B) else Color(0xFFFFFFFF),
            border = BorderStroke(1.dp, cardBorder),
            shadowElevation = 3.dp,
            modifier = Modifier.size(64.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF4285F4).copy(alpha = 0.16f),
                                Color(0xFF9B72CB).copy(alpha = 0.16f),
                                Color(0xFFD96570).copy(alpha = 0.12f)
                            )
                        )
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_swiffle_logo),
                    contentDescription = "Swiffle Copilot",
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "How can Copilot assist you today?",
            fontFamily = GoogleSans,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Experience deep neural reasoning with live document grounding\nand Windows 11 Fluent intelligence.",
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            lineHeight = 18.5.sp,
            color = textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        val promptSuggestions = listOf(
            "Summarize active document with structured takeaways" to "📄",
            "Extract and format document tables into clean data" to "📊",
            "Deep dive into technical concepts or complex clauses" to "🔍",
            "Generate production-ready code snippets and architecture" to "⚡"
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            promptSuggestions.forEach { (title, emoji) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPromptClick(title) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = emoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = title,
                            fontFamily = GoogleSans,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Windows 11 Fluent CommandBar Bottom Input Dock:
 * - 8dp corner radius, specular hairline border.
 * - Solid Fluent Blue (#0078D4) Send Button with white send arrow.
 * - Stop button (#C42B1C) with white square glyph during active generation.
 * - Clean attachment and mic controls with 4dp hover surfaces.
 */
@Composable
private fun FluentChatInputDock(
    query: String,
    isDark: Boolean,
    isGenerating: Boolean,
    attachedFileName: String?,
    attachedPhotoUri: Uri?,
    isExtractingAttachment: Boolean,
    onRemoveAttachment: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit,
    onUploadFiles: () -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit
) {
    val dockBg = if (isDark) Color(0xFF272727) else Color(0xFFFFFFFF)
    val dockBorder = if (isDark) Color(0xFF383838) else Color(0xFFE0E0E0)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1F1F1F)
    val textSecondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF6E6E6E)
    var showAttachmentMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = dockBg,
        border = BorderStroke(1.dp, dockBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            // Attachment flyout bar
            AnimatedVisibility(
                visible = showAttachmentMenu,
                enter = expandVertically(animationSpec = tween(FluentFastDuration)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(FluentFastDuration)) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FluentAttachmentChip(
                        icon = Icons.Default.Description,
                        label = "Document",
                        isDark = isDark,
                        onClick = {
                            showAttachmentMenu = false
                            onUploadFiles()
                        }
                    )
                    FluentAttachmentChip(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Gallery",
                        isDark = isDark,
                        onClick = {
                            showAttachmentMenu = false
                            onPhotos()
                        }
                    )
                    FluentAttachmentChip(
                        icon = Icons.Default.PhotoCamera,
                        label = "Camera",
                        isDark = isDark,
                        onClick = {
                            showAttachmentMenu = false
                            onCamera()
                        }
                    )
                }
            }

            // Active Attachment Extraction Indicator
            if (isExtractingAttachment) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF0078D4)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Extracting text from attachment via OCR...",
                        fontFamily = GoogleSans,
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
            } else if (attachedFileName != null || attachedPhotoUri != null) {
                // Active Attached Item Pill with Detach Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF333333) else Color(0xFFF0F0F0),
                        border = BorderStroke(0.8.dp, dockBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            if (attachedPhotoUri != null) {
                                AsyncImage(
                                    model = attachedPhotoUri,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Photo attached",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = textPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF0078D4),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = attachedFileName ?: "Document",
                                    fontFamily = GoogleSans,
                                    fontSize = 12.sp,
                                    color = textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp)
                                )
                            }

                            IconButton(
                                onClick = onRemoveAttachment,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach button
                IconButton(
                    onClick = { showAttachmentMenu = !showAttachmentMenu },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (showAttachmentMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Query text field
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textStyle = TextStyle(
                        fontFamily = GoogleSans,
                        fontSize = 14.sp,
                        color = textPrimary
                    ),
                    cursorBrush = SolidColor(Color(0xFF0078D4)),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = if (attachedFileName != null || attachedPhotoUri != null) "Ask about this attachment..." else "Ask Copilot anything...",
                                fontFamily = GoogleSans,
                                fontSize = 14.sp,
                                color = textSecondary
                            )
                        }
                        innerTextField()
                    }
                )

                // Mic button
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Prompt",
                        tint = textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Send or Stop Button
                if (isGenerating) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFC42B1C), // Windows 11 Error/Stop Red
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onStop() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.White)
                            )
                        }
                    }
                } else {
                    val isEnabled = query.trim().isNotEmpty() || attachedFileName != null || attachedPhotoUri != null
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isEnabled) Color(0xFF0078D4) else if (isDark) Color(0xFF333333) else Color(0xFFE5E5E5),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .then(if (isEnabled) Modifier.clickable { onSend() } else Modifier)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (isEnabled) Color.White else textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FluentAttachmentChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isDark) Color(0xFF333333) else Color(0xFFF0F0F0),
        border = BorderStroke(0.8.dp, if (isDark) Color(0xFF444444) else Color(0xFFDDDDDD)),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF0078D4),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontFamily = GoogleSans,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1F1F1F)
            )
        }
    }
}

// ── Attachment & OCR Processing Helpers ──

private suspend fun extractTextFromDocUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(doc)
            doc.close()
            text.take(20000)
        } ?: ""
    } catch (_: Exception) {
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }?.take(20000) ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}

private suspend fun extractOcrFromImageUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, _, _ -> decoder.isMutableRequired = true }
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )
        val result = recognizer.process(inputImage).await()
        result.text
    } catch (e: Exception) {
        android.util.Log.e("SwiffleAi", "OCR on photo failed: ${e.message}", e)
        ""
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
