package com.swiffle.app.ui.chat

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.swiffle.app.ai.SwiffleAiConfig
import com.swiffle.app.ai.SwiffleAiEngine
import com.swiffle.app.ai.SwiffleAiStreamEvent
import com.swiffle.app.ai.SwiffleAiTask
import com.swiffle.app.ai.SwiffleModelTier
import com.swiffle.app.theme.ThemeManager
import com.swiffle.app.ui.canvas.SwiffleChatMessage
import com.swiffle.app.ui.components.DarkVoidAmbientLightPulse
import com.swiffle.app.ui.home.rememberHomeTokens
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Obsidian Glassmorphic Editorial Swiffle AI Chat Screen.
 */
@Composable
fun SwiffleAiChatScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = ThemeManager.isDarkThemeActive()
    val tokens = rememberHomeTokens(isDark = isDark)

    val aiEngine = remember { SwiffleAiEngine(context) }

    var currentSessionId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var inputQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf<SwiffleChatMessage>() }
    var activeStreamingJob by remember { mutableStateOf<Job?>(null) }
    var pendingResponseMsgId by remember { mutableStateOf<String?>(null) }
    var pendingBufferedResponse by remember { mutableStateOf("") }
    var autoScrollPinned by remember { mutableStateOf(true) }
    var isUserDraggingList by remember { mutableStateOf(false) }

    var showHistorySheet by remember { mutableStateOf(false) }
    var showUsageLimitsSheet by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var selectedModelTier by remember {
        mutableStateOf(SwiffleModelTier.fromId(SwiffleAiConfig.getSelectedModelTierId(context)))
    }
    var savedSessions by remember { mutableStateOf(loadStoredChatSessions(context)) }
    var usageSnapshot by remember { mutableStateOf(loadChatUsageSnapshot(context)) }

    // Active Attachments State (Document & Image OCR grounding)
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachedDocumentText by remember { mutableStateOf<String?>(null) }
    var attachedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isExtractingAttachment by remember { mutableStateOf(false) }

    fun persistCurrentConversation() {
        val settled = messages.filter { !it.isStreaming && !it.isThinking && it.text.isNotBlank() }
        if (settled.isNotEmpty()) {
            savedSessions = saveChatSession(context, currentSessionId, settled)
        }
    }

    // TTS Read Aloud Engine
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        tts = ttsInstance
        onDispose {
            persistCurrentConversation()
            activeStreamingJob?.cancel()
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    BackHandler {
        when {
            modelMenuExpanded -> modelMenuExpanded = false
            overflowMenuExpanded -> overflowMenuExpanded = false
            else -> {
                persistCurrentConversation()
                onBack()
            }
        }
    }

    fun readAloud(text: String) {
        val clean = text.replace(Regex("[#*`|\\[\\]()]"), "").trim()
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "swiffle_tts")
    }

    fun startNewChatSession() {
        persistCurrentConversation()
        activeStreamingJob?.cancel()
        activeStreamingJob = null
        messages.clear()
        currentSessionId = UUID.randomUUID().toString()
        inputQuery = ""
        attachedFileName = null
        attachedPhotoUri = null
        attachedDocumentText = null
    }

    fun streamAiResponse(
        assistantMsgId: String,
        userPrompt: String,
        attachmentContext: String? = null
    ) {
        activeStreamingJob?.cancel()
        pendingResponseMsgId = assistantMsgId
        pendingBufferedResponse = ""
        autoScrollPinned = true
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
                        if (msg.id != assistantMsgId && msg.text.isNotBlank() && !msg.isErrorNotice) {
                            append(if (msg.isUser) "User: " else "Assistant: ")
                            append(msg.text.take(200))
                            append("\n")
                        }
                    }
                    append("\n")
                }
            }

            var accumulatedText = ""
            var accumulatedThinking = ""
            var tokenCount = 0

            suspend fun revealBufferedResponse(finalText: String) {
                val idxStart = messages.indexOfFirst { it.id == assistantMsgId }
                if (idxStart == -1) return

                val elapsedSec = messages[idxStart].thinkingDurationSec
                    ?: ((System.currentTimeMillis() - thinkingStartMs) / 1000L).coerceAtLeast(1L).toInt()
                val finalReasoning = formatStructuredReasoning(accumulatedThinking).ifBlank {
                    messages[idxStart].thinkingText?.ifBlank { null }
                }

                if (finalText.isBlank()) {
                    messages[idxStart] = messages[idxStart].copy(
                        isThinking = false,
                        isStreaming = false,
                        activePhaseText = null,
                        thinkingDurationSec = elapsedSec,
                        thinkingText = finalReasoning,
                        isErrorNotice = false,
                        text = ""
                    )
                    pendingResponseMsgId = null
                    pendingBufferedResponse = ""
                    return
                }

                // Transition from background generation indicator into progressive display
                messages[idxStart] = messages[idxStart].copy(
                    isThinking = false,
                    isStreaming = true,
                    activePhaseText = null,
                    thinkingDurationSec = elapsedSec,
                    thinkingText = finalReasoning,
                    isErrorNotice = false,
                    text = ""
                )

                val targetFrames = when {
                    finalText.length <= 80 -> 6
                    finalText.length <= 360 -> 18
                    finalText.length <= 1200 -> 30
                    else -> 42
                }
                val baseStep = (finalText.length / targetFrames).coerceAtLeast(10)
                var cursor = 0

                while (cursor < finalText.length) {
                    var nextCursor = (cursor + baseStep).coerceAtMost(finalText.length)
                    if (nextCursor < finalText.length) {
                        val lookAheadLimit = (nextCursor + 18).coerceAtMost(finalText.length)
                        var wordEnd = nextCursor
                        while (wordEnd < lookAheadLimit && !finalText[wordEnd].isWhitespace()) {
                            wordEnd++
                        }
                        if (wordEnd < lookAheadLimit) {
                            nextCursor = wordEnd + 1
                        }
                    }
                    cursor = nextCursor
                    val idx = messages.indexOfFirst { it.id == assistantMsgId }
                    if (idx == -1) break
                    messages[idx] = messages[idx].copy(text = finalText.substring(0, cursor))
                    if (cursor < finalText.length) {
                        delay(18L)
                    }
                }

                val idxEnd = messages.indexOfFirst { it.id == assistantMsgId }
                if (idxEnd != -1) {
                    messages[idxEnd] = messages[idxEnd].copy(
                        isStreaming = false,
                        isThinking = false,
                        activePhaseText = null,
                        thinkingText = finalReasoning,
                        thinkingDurationSec = elapsedSec,
                        isErrorNotice = false,
                        text = finalText
                    )
                }
                pendingResponseMsgId = null
                pendingBufferedResponse = ""
            }

            aiEngine.query(
                userPrompt = userPrompt,
                documentContext = sessionMemoryContext.ifBlank { null },
                task = SwiffleAiTask.AskQuestion,
                modelTier = selectedModelTier
            ).catch { e ->
                val idx = messages.indexOfFirst { it.id == assistantMsgId }
                if (idx != -1) {
                    if (accumulatedText.isNotBlank()) {
                        revealBufferedResponse(accumulatedText)
                    } else {
                        messages[idx] = messages[idx].copy(
                            isThinking = false,
                            isStreaming = false,
                            activePhaseText = null,
                            isErrorNotice = true,
                            text = formatFriendlyAiError(e.localizedMessage)
                        )
                    }
                }
                pendingResponseMsgId = null
                pendingBufferedResponse = ""
            }.collect { event ->
                when (event) {
                    is SwiffleAiStreamEvent.StatusUpdate -> {
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1 && messages[idx].isThinking) {
                            messages[idx] = messages[idx].copy(
                                activePhaseText = ensureCleanEnglishSentence(event.message)
                            )
                        }
                    }

                    is SwiffleAiStreamEvent.EngineSwitched -> {
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1 && messages[idx].isThinking) {
                            messages[idx] = messages[idx].copy(
                                activePhaseText = "Switching smoothly to ${event.toEngine} to keep your stream active."
                            )
                        }
                    }

                    is SwiffleAiStreamEvent.Thinking -> {
                        accumulatedThinking += event.text
                        val cleanSentence = extractCleanReasoningSentence(accumulatedThinking)
                        val structuredTranscript = formatStructuredReasoning(accumulatedThinking)
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            val currentPhase = messages[idx].activePhaseText
                            val nextPhase = cleanSentence
                                ?: currentPhase?.takeIf { it.isNotBlank() }
                                ?: "Analyzing your request step by step."
                            messages[idx] = messages[idx].copy(
                                thinkingText = structuredTranscript,
                                activePhaseText = nextPhase
                            )
                        }
                    }

                    is SwiffleAiStreamEvent.Token -> {
                        // Generate and buffer the response in the background until ~95%+ completion
                        tokenCount++
                        accumulatedText += event.text
                        pendingBufferedResponse = accumulatedText
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            val phaseText = when {
                                accumulatedText.length < 100 -> "Drafting your response in the background."
                                accumulatedText.length < 360 -> "Structuring key points and details."
                                accumulatedText.length < 850 -> "Refining response formatting and clarity."
                                else -> "Finalizing response output before display."
                            }
                            messages[idx] = messages[idx].copy(
                                isThinking = true,
                                isStreaming = false,
                                activePhaseText = phaseText,
                                thinkingText = formatStructuredReasoning(accumulatedThinking).ifBlank {
                                    messages[idx].thinkingText
                                }
                            )
                        }
                    }

                    is SwiffleAiStreamEvent.Completed -> {
                        val finalText = accumulatedText.ifBlank { event.fullText }
                        revealBufferedResponse(finalText)
                        usageSnapshot = loadChatUsageSnapshot(context)
                        persistCurrentConversation()
                    }

                    is SwiffleAiStreamEvent.Error -> {
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            if (accumulatedText.isNotBlank()) {
                                revealBufferedResponse(accumulatedText)
                            } else {
                                messages[idx] = messages[idx].copy(
                                    isThinking = false,
                                    isStreaming = false,
                                    activePhaseText = null,
                                    isErrorNotice = true,
                                    text = formatFriendlyAiError(event.error.localizedMessage)
                                )
                                pendingResponseMsgId = null
                                pendingBufferedResponse = ""
                            }
                        }
                        persistCurrentConversation()
                    }

                    is SwiffleAiStreamEvent.OfflineModelRequired -> {
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            messages[idx] = messages[idx].copy(
                                isThinking = false,
                                isStreaming = false,
                                activePhaseText = null,
                                isErrorNotice = true,
                                text = "You are currently offline. Please reconnect to the internet to stream responses, or download the on-device neural model in Settings."
                            )
                        }
                        pendingResponseMsgId = null
                        pendingBufferedResponse = ""
                    }
                }
            }
        }
    }

    suspend fun scrollToChatBottom(smooth: Boolean) {
        if (messages.isEmpty()) return
        val lastIdx = messages.lastIndex
        val layoutInfo = listState.layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
        if (lastVisible == null || lastVisible.index < lastIdx) {
            if (smooth) {
                listState.animateScrollToItem(index = lastIdx, scrollOffset = 0)
            } else {
                listState.scrollToItem(index = lastIdx, scrollOffset = 0)
            }
        }
        val refreshedInfo = listState.layoutInfo
        val refreshedLast = refreshedInfo.visibleItemsInfo.lastOrNull()
        if (refreshedLast != null && refreshedLast.index == lastIdx) {
            val viewportBottom = refreshedInfo.viewportEndOffset - refreshedInfo.afterContentPadding
            val overshoot = (refreshedLast.offset + refreshedLast.size) - viewportBottom
            if (overshoot > 2) {
                if (!smooth || overshoot <= 72) {
                    listState.scrollBy(overshoot.toFloat())
                } else {
                    listState.animateScrollBy(
                        overshoot.toFloat(),
                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                    )
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

        focusManager.clearFocus()
        keyboardController?.hide()

        val displayPrompt = when {
            trimmed.isNotEmpty() -> trimmed
            currentFileName != null -> "Please analyze this attached document '$currentFileName', outline key findings, and summarize core insights."
            currentPhotoUri != null -> "Please inspect this attached photo, identify key visual text/elements, and provide a structured breakdown."
            else -> "Hello"
        }

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
                thinkingText = null,
                activePhaseText = "Connecting to the Swiffle AI streaming service.",
                isThinking = true,
                isStreaming = false
            )
        )

        autoScrollPinned = true
        coroutineScope.launch {
            delay(40)
            scrollToChatBottom(smooth = true)
        }

        streamAiResponse(
            assistantMsgId = assistantMsgId,
            userPrompt = displayPrompt,
            attachmentContext = currentDocText
        )
    }

    fun handleRegenerate(assistantMsgId: String) {
        val msgIdx = messages.indexOfFirst { it.id == assistantMsgId }
        if (msgIdx <= 0) return
        val precedingUserMsg = messages.subList(0, msgIdx).lastOrNull { it.isUser } ?: return

        autoScrollPinned = true
        messages[msgIdx] = messages[msgIdx].copy(
            text = "",
            thinkingText = null,
            activePhaseText = "Connecting to the Swiffle AI streaming service.",
            thinkingDurationSec = null,
            isThinkingExpanded = false,
            isThinking = true,
            isStreaming = false,
            isErrorNotice = false
        )

        streamAiResponse(
            assistantMsgId = assistantMsgId,
            userPrompt = precedingUserMsg.text
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
        contract = ActivityResultContracts.PickVisualMedia()
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
                    Toast.makeText(context, "Photo attached ($words words detected)", Toast.LENGTH_SHORT).show()
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

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim()
            if (!spokenText.isNullOrBlank()) {
                inputQuery = if (inputQuery.isBlank()) spokenText else "$inputQuery $spokenText"
            }
        }
    }

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val bottomSystemInsetPx = maxOf(imeBottomPx, navBottomPx)
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var composerDockHeightPx by remember { mutableIntStateOf(0) }

    val isAiGenerating = messages.any { it.isStreaming || it.isThinking }
    val lastMessage = messages.lastOrNull()
    val lastMessageTextLength = lastMessage?.text?.length ?: 0
    val lastMessageIsActive = (lastMessage?.isStreaming == true) || (lastMessage?.isThinking == true)

    // Track manual user drag gestures so autoscroll yields if the user scrolls up to read
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isUserDraggingList = true
                    autoScrollPinned = false
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    isUserDraggingList = false
                    if (!listState.canScrollForward) {
                        autoScrollPinned = true
                    }
                }
            }
        }
    }

    // Re-arm autoscroll automatically if user manually scrolls back down to the bottom
    LaunchedEffect(listState, isUserDraggingList) {
        snapshotFlow { listState.canScrollForward }.collect { canScrollForward ->
            if (!isUserDraggingList && !canScrollForward) {
                autoScrollPinned = true
            }
        }
    }

    // Continuous autoscroll while AI is thinking, displaying response chunks, or settling final action bar
    LaunchedEffect(
        messages.size,
        lastMessageTextLength,
        lastMessageIsActive,
        lastMessage?.activePhaseText,
        autoScrollPinned,
        isUserDraggingList,
        composerDockHeightPx
    ) {
        if (messages.isEmpty() || !autoScrollPinned || isUserDraggingList) return@LaunchedEffect
        if (isAiGenerating || !lastMessageIsActive) {
            delay(16)
            scrollToChatBottom(smooth = !lastMessageIsActive)
        }
    }

    LaunchedEffect(imeBottomPx) {
        if (imeBottomPx > 0 && messages.isNotEmpty() && autoScrollPinned) {
            delay(80)
            scrollToChatBottom(smooth = true)
        }
    }

    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val measuredHeaderBottomDp = with(density) { headerHeightPx.toDp() }
    val effectiveHeaderBottomDp = maxOf(measuredHeaderBottomDp, statusBarTopDp + 68.dp)
    val topContentPaddingDp = effectiveHeaderBottomDp + 20.dp
    val bottomContentPaddingDp = with(density) { (bottomSystemInsetPx + composerDockHeightPx).toDp() }.coerceAtLeast(102.dp) + 16.dp
    val topVignetteHeightDp = effectiveHeaderBottomDp + 22.dp
    val bottomVignetteHeightDp = with(density) { (bottomSystemInsetPx + composerDockHeightPx).toDp() }.coerceAtLeast(102.dp) + 42.dp

    val lastCompletedAssistantId = messages.lastOrNull {
        !it.isUser && !it.isThinking && !it.isStreaming && !it.isErrorNotice && it.text.isNotBlank()
    }?.id
    val rootBgColor = if (isDark) Color(0xFF000000) else tokens.bgVoid

    val isAnyHeaderMenuOpen = modelMenuExpanded || overflowMenuExpanded
    val liquidBackdropBlurDp by animateDpAsState(
        targetValue = if (isAnyHeaderMenuOpen) 16.dp else 0.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "liquid_glass_backdrop_blur"
    )
    val liquidScrimAlpha by animateFloatAsState(
        targetValue = if (isAnyHeaderMenuOpen) (if (isDark) 0.22f else 0.10f) else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "liquid_glass_scrim_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBgColor)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    modelMenuExpanded = false
                    overflowMenuExpanded = false
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
    ) {
        // Dark Void Ambient Radial Light Pulse matching Onboarding & Home
        if (isDark) {
            DarkVoidAmbientLightPulse(
                activeStageIndex = if (messages.isEmpty()) 0 else 1,
                activeSubStepIndex = if (isAiGenerating) 1 else 0,
                transitionDepthProgress = if (isAiGenerating) 0.7f else 0.25f
            )
        }

        // 1. Full-Bleed Scrollable Chat Stream / Welcome Hero (glides beneath transparent header & floating dock;
        //    real GPU-blurred on Android 12+ when Liquid Glass dropdowns open)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (liquidBackdropBlurDp > 0.5.dp) {
                        Modifier.blur(
                            radius = liquidBackdropBlurDp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                        )
                    } else {
                        Modifier
                    }
                )
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .widthIn(max = 640.dp)
                .align(Alignment.Center)
        ) {
            if (messages.isEmpty()) {
                ObsidianChatHeroView(
                    tokens = tokens,
                    contentPadding = PaddingValues(
                        top = topContentPaddingDp,
                        bottom = bottomContentPaddingDp
                    ),
                    onPromptClick = { prompt -> handleSend(prompt) }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(
                        top = topContentPaddingDp,
                        bottom = bottomContentPaddingDp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.isUser) {
                            ObsidianUserMessageBubble(
                                message = msg,
                                tokens = tokens
                            )
                        } else {
                            ObsidianAssistantMessageBubble(
                                message = msg,
                                tokens = tokens,
                                showInlineActions = (msg.id == lastCompletedAssistantId),
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
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Swiffle AI", msg.text))
                                    Toast.makeText(context, "Copied to clipboard.", Toast.LENGTH_SHORT).show()
                                },
                                onReadAloud = { readAloud(msg.text) },
                                onRegenerate = { handleRegenerate(msg.id) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Top Screen Edge Vignette Scrim (smoothly fades scrolled conversations behind transparent header)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topVignetteHeightDp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to rootBgColor.copy(alpha = 0.96f),
                        0.52f to rootBgColor.copy(alpha = 0.78f),
                        0.82f to rootBgColor.copy(alpha = 0.28f),
                        1.0f to Color.Transparent
                    )
                )
        )

        // 3. Bottom Screen Edge Vignette Scrim (smoothly dissolves scrolled messages behind floating glass dock)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomVignetteHeightDp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.28f to rootBgColor.copy(alpha = 0.30f),
                        0.62f to rootBgColor.copy(alpha = 0.78f),
                        1.0f to rootBgColor.copy(alpha = 0.96f)
                    )
                )
        )

        // 4. Tap-outside optical scrim to close open Liquid Glass dropdowns smoothly
        if (modelMenuExpanded || overflowMenuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = liquidScrimAlpha)
                        else Color.White.copy(alpha = liquidScrimAlpha)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            modelMenuExpanded = false
                            overflowMenuExpanded = false
                        }
                    )
            )
        }

        // 5. Transparent Floating Glassy Header (Back Pill + "Swiffle Omni" Model Pill + 3-Dot Pill)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .widthIn(max = 640.dp)
                .onSizeChanged { headerHeightPx = it.height }
                .statusBarsPadding()
        ) {
            ObsidianChatTopBar(
                tokens = tokens,
                selectedModel = selectedModelTier,
                modelMenuExpanded = modelMenuExpanded,
                overflowMenuExpanded = overflowMenuExpanded,
                onBack = {
                    persistCurrentConversation()
                    onBack()
                },
                onToggleModelMenu = {
                    overflowMenuExpanded = false
                    modelMenuExpanded = !modelMenuExpanded
                },
                onToggleOverflowMenu = {
                    modelMenuExpanded = false
                    overflowMenuExpanded = !overflowMenuExpanded
                }
            )
        }

        // 6. Fluent Glassmorphism Dropdown Overlays anchored cleanly below header pills
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .widthIn(max = 640.dp)
                .padding(
                    top = effectiveHeaderBottomDp + 2.dp,
                    start = 16.dp,
                    end = 16.dp
                )
        ) {
            ObsidianModelSelectorDropdown(
                visible = modelMenuExpanded,
                selectedModel = selectedModelTier,
                tokens = tokens,
                onSelectModel = { tier ->
                    selectedModelTier = tier
                    SwiffleAiConfig.setSelectedModelTierId(context, tier.id)
                    modelMenuExpanded = false
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 58.dp)
            )

            ObsidianOverflowMenuDropdown(
                visible = overflowMenuExpanded,
                hasMessages = messages.isNotEmpty(),
                tokens = tokens,
                onNewChat = {
                    overflowMenuExpanded = false
                    startNewChatSession()
                },
                onOpenHistory = {
                    overflowMenuExpanded = false
                    persistCurrentConversation()
                    savedSessions = loadStoredChatSessions(context)
                    showHistorySheet = true
                },
                onOpenUsageLimits = {
                    overflowMenuExpanded = false
                    usageSnapshot = loadChatUsageSnapshot(context)
                    showUsageLimitsSheet = true
                },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // 7. Transparent Floating Glass Composer Dock (IME + Navigation Bar safe)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .widthIn(max = 640.dp)
                .navigationBarsPadding()
                .imePadding()
                .onSizeChanged { composerDockHeightPx = it.height }
        ) {
            ObsidianChatInputDock(
                query = inputQuery,
                tokens = tokens,
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
                        val targetMsg = messages[sIdx]
                        val buffered = if (pendingResponseMsgId == targetMsg.id) {
                            pendingBufferedResponse
                        } else {
                            ""
                        }
                        messages[sIdx] = targetMsg.copy(
                            isStreaming = false,
                            isThinking = false,
                            activePhaseText = null,
                            text = targetMsg.text.ifBlank { buffered }
                        )
                    }
                    pendingResponseMsgId = null
                    pendingBufferedResponse = ""
                    persistCurrentConversation()
                },
                onMicClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Swiffle AI...")
                        }
                        speechRecognizerLauncher.launch(intent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            "Voice dictation is not available on this device.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onUploadFiles = {
                    try {
                        filePickerLauncher.launch(arrayOf("application/pdf", "text/*", "*/*"))
                    } catch (_: Exception) {}
                },
                onPhotos = {
                    try {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } catch (_: Exception) {}
                },
                onCamera = {
                    try {
                        val cameraDir = File(context.cacheDir, "ai_photos").apply { mkdirs() }
                        val photoFile = File(cameraDir, "camera_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        cameraPhotoUri = uri
                        cameraLauncher.launch(uri)
                    } catch (_: Exception) {}
                }
            )
        }

        if (showUsageLimitsSheet) {
            LaunchedEffect(Unit) {
                while (true) {
                    usageSnapshot = loadChatUsageSnapshot(context)
                    delay(1000L)
                }
            }
            ObsidianUsageLimitsSheet(
                snapshot = usageSnapshot,
                tokens = tokens,
                onDismiss = { showUsageLimitsSheet = false }
            )
        }

        if (showHistorySheet) {
            val sessionItems = remember(savedSessions, currentSessionId) {
                savedSessions.map { session ->
                    val firstAssistant = session.messages.firstOrNull { !it.isUser }?.text.orEmpty()
                        .replace(Regex("[#*`>\\n]+"), " ")
                        .trim()
                    ChatHistorySessionItem(
                        id = session.id,
                        title = session.title,
                        preview = firstAssistant,
                        updatedAtMs = session.updatedAtMs,
                        messageCount = session.messages.size,
                        isCurrent = (session.id == currentSessionId)
                    )
                }
            }
            ObsidianChatHistorySheet(
                sessions = sessionItems,
                tokens = tokens,
                onSelectSession = { selectedId ->
                    persistCurrentConversation()
                    val target = savedSessions.firstOrNull { it.id == selectedId }
                    if (target != null) {
                        activeStreamingJob?.cancel()
                        activeStreamingJob = null
                        currentSessionId = target.id
                        messages.clear()
                        messages.addAll(target.messages)
                        inputQuery = ""
                        attachedFileName = null
                        attachedPhotoUri = null
                        attachedDocumentText = null
                    }
                },
                onDeleteSession = { deleteId ->
                    savedSessions = deleteStoredChatSession(context, deleteId)
                    if (deleteId == currentSessionId) {
                        activeStreamingJob?.cancel()
                        activeStreamingJob = null
                        messages.clear()
                        currentSessionId = UUID.randomUUID().toString()
                    }
                },
                onStartNewChat = { startNewChatSession() },
                onDismiss = { showHistorySheet = false }
            )
        }
    }
}

// ── Attachment & OCR Processing Helpers ──

private suspend fun extractTextFromDocUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val doc = PDDocument.load(stream)
            val stripper = PDFTextStripper()
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
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, _, _ -> decoder.isMutableRequired = true }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(inputImage).await()
        result.text
    } catch (e: Exception) {
        Log.e("SwiffleAi", "OCR on photo failed: ${e.message}", e)
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

// ── Clean English SSE Framing, Usage Telemetry & Chat History Storage ──

private const val CHAT_HISTORY_PREFS_KEY = "swiffle_ai_chat_history_v1"
private const val CHAT_HISTORY_MAX_SESSIONS = 25

private data class StoredChatSession(
    val id: String,
    val title: String,
    val updatedAtMs: Long,
    val messages: List<SwiffleChatMessage>
)

private fun loadChatUsageSnapshot(context: Context): com.swiffle.app.ai.UsageRateSnapshot {
    return SwiffleAiConfig.getUsageRateSnapshot(context)
}

private fun loadStoredChatSessions(context: Context): List<StoredChatSession> {
    val prefs = context.getSharedPreferences(SwiffleAiConfig.SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    val rawJson = prefs.getString(CHAT_HISTORY_PREFS_KEY, null).orEmpty()
    if (rawJson.isBlank()) return emptyList()
    return try {
        val array = JSONArray(rawJson)
        val out = mutableListOf<StoredChatSession>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").ifBlank { continue }
            val title = obj.optString("title").ifBlank { "Untitled conversation" }
            val updatedAt = obj.optLong("updatedAtMs", 0L)
            val msgArray = obj.optJSONArray("messages") ?: JSONArray()
            val parsedMessages = mutableListOf<SwiffleChatMessage>()
            for (j in 0 until msgArray.length()) {
                val mObj = msgArray.optJSONObject(j) ?: continue
                parsedMessages.add(
                    SwiffleChatMessage(
                        id = mObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        isUser = mObj.optBoolean("isUser", false),
                        text = mObj.optString("text", ""),
                        thinkingText = mObj.optString("thinkingText", "").ifBlank { null },
                        thinkingDurationSec = if (mObj.has("thinkingDurationSec") && !mObj.isNull("thinkingDurationSec")) {
                            mObj.optInt("thinkingDurationSec")
                        } else {
                            null
                        },
                        attachedFileName = mObj.optString("attachedFileName", "").ifBlank { null },
                        isErrorNotice = mObj.optBoolean("isErrorNotice", false)
                    )
                )
            }
            if (parsedMessages.isNotEmpty()) {
                out.add(
                    StoredChatSession(
                        id = id,
                        title = title,
                        updatedAtMs = updatedAt,
                        messages = parsedMessages
                    )
                )
            }
        }
        out.sortedByDescending { it.updatedAtMs }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun writeStoredChatSessions(context: Context, sessions: List<StoredChatSession>) {
    val array = JSONArray()
    sessions.take(CHAT_HISTORY_MAX_SESSIONS).forEach { session ->
        val obj = JSONObject()
        obj.put("id", session.id)
        obj.put("title", session.title)
        obj.put("updatedAtMs", session.updatedAtMs)
        val msgArray = JSONArray()
        session.messages.forEach { msg ->
            val mObj = JSONObject()
            mObj.put("id", msg.id)
            mObj.put("isUser", msg.isUser)
            mObj.put("text", msg.text)
            if (!msg.thinkingText.isNullOrBlank()) {
                mObj.put("thinkingText", msg.thinkingText)
            }
            if (msg.thinkingDurationSec != null) {
                mObj.put("thinkingDurationSec", msg.thinkingDurationSec)
            }
            if (!msg.attachedFileName.isNullOrBlank()) {
                mObj.put("attachedFileName", msg.attachedFileName)
            }
            mObj.put("isErrorNotice", msg.isErrorNotice)
            msgArray.put(mObj)
        }
        obj.put("messages", msgArray)
        array.put(obj)
    }
    context.getSharedPreferences(SwiffleAiConfig.SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(CHAT_HISTORY_PREFS_KEY, array.toString())
        .apply()
}

private fun saveChatSession(
    context: Context,
    sessionId: String,
    messages: List<SwiffleChatMessage>
): List<StoredChatSession> {
    val firstUserPrompt = messages.firstOrNull { it.isUser }?.text?.trim().orEmpty()
    val title = firstUserPrompt
        .replace(Regex("\\s+"), " ")
        .take(54)
        .ifBlank { "Swiffle AI Conversation" }

    val updatedSession = StoredChatSession(
        id = sessionId,
        title = title,
        updatedAtMs = System.currentTimeMillis(),
        messages = messages.toList()
    )
    val existing = loadStoredChatSessions(context).filterNot { it.id == sessionId }
    val merged = (listOf(updatedSession) + existing).take(CHAT_HISTORY_MAX_SESSIONS)
    writeStoredChatSessions(context, merged)
    return merged
}

private fun deleteStoredChatSession(context: Context, sessionId: String): List<StoredChatSession> {
    val remaining = loadStoredChatSessions(context).filterNot { it.id == sessionId }
    writeStoredChatSessions(context, remaining)
    return remaining
}

private fun ensureCleanEnglishSentence(raw: String): String {
    val cleaned = raw
        .replace(Regex("</?think>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (cleaned.isEmpty()) return "Connecting to the Swiffle AI streaming service."
    val capitalized = cleaned.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
    }
    return if (capitalized.endsWith(".") || capitalized.endsWith("!") || capitalized.endsWith("?")) {
        capitalized
    } else {
        "$capitalized."
    }
}

private fun extractCleanReasoningSentence(rawReasoning: String): String? {
    val stripped = rawReasoning
        .replace(Regex("</?think>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("[*_`#>]+"), "")
        .replace(Regex("^\\s*[-•]\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (stripped.length < 20) return null

    val sentenceRegex = Regex("[^.!?]+[.!?]")
    val completed = sentenceRegex.findAll(stripped)
        .map { it.value.trim() }
        .filter { it.length >= 20 && it.any(Char::isLetter) }
        .toList()

    val latest = completed.lastOrNull() ?: return null
    val normalized = latest.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
    }
    return if (normalized.length <= 108) {
        normalized
    } else {
        val cut = normalized.take(105).substringBeforeLast(' ', normalized.take(105))
        "$cut..."
    }
}

private fun formatStructuredReasoning(rawReasoning: String): String {
    val cleaned = rawReasoning
        .replace(Regex("</?think>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    if (cleaned.isBlank()) return ""
    return cleaned.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
    }
}

private fun formatFriendlyAiError(rawMessage: String?): String {
    val detail = rawMessage
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
    if (detail.isBlank()) {
        return "We could not complete your request right now. Please check your internet connection and try again."
    }
    return ensureCleanEnglishSentence(detail)
}
