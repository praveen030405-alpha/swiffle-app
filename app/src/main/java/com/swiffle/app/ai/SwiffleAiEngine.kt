package com.swiffle.app.ai

import android.content.Context
import com.swiffle.app.ai.client.GroqApiClient
import com.swiffle.app.ai.client.OpenRouterApiClient
import com.swiffle.app.ai.client.PollinationsApiClient
import com.swiffle.app.ai.offline.GemmaEmbeddingEngine
import com.swiffle.app.ai.offline.SwiffleOfflineModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * The Master Intelligence Orchestrator for Swiffle AI.
 *
 * Implements the Multi-Provider Architecture:
 * 1. Custom Groq Cloud LPUs (`llama-3.3-70b-versatile`, `llama-3.1-8b-instant`, `llama-4-scout-17b`)
 * 2. Custom OpenRouter API (`meta-llama/llama-3.3-70b-instruct:free`, `qwen/qwen-2.5-72b-instruct:free`)
 * 3. Firebase AI Logic (Gemini) placeholder notice when Firebase setup is not enabled
 * 4. Pollinations Streaming Fallback & Real On-Device Gemma 2 TFLite Embedding RAG Search
 */
class SwiffleAiEngine(
    private val context: Context,
    val networkMonitor: SwiffleNetworkMonitor = SwiffleNetworkMonitor(context),
    val offlineModelManager: SwiffleOfflineModels = SwiffleOfflineModels(context),
    private val groqClient: GroqApiClient = GroqApiClient(),
    private val openRouterClient: OpenRouterApiClient = OpenRouterApiClient(),
    private val pollinationsClient: PollinationsApiClient = PollinationsApiClient(),
    val gemmaEmbeddingEngine: GemmaEmbeddingEngine = GemmaEmbeddingEngine(context)
) {

    /**
     * Public entry-point: Queries Swiffle AI with contextual document grounding.
     */
    fun query(
        userPrompt: String,
        documentContext: String? = null,
        task: SwiffleAiTask = SwiffleAiTask.AskQuestion
    ): Flow<SwiffleAiStreamEvent> = flow {
        val messages = mutableListOf<AiChatMessage>()

        val customAiName = SwiffleAiConfig.getCustomAiName(context)
        val customInstructions = SwiffleAiConfig.getCustomAiInstructions(context)

        // 1. System Prompt with user personalization & adaptive formatting instructions
        val enhancedSystemPrompt = buildString {
            append("You are $customAiName, an advanced document intelligence assistant.\n")
            append(task.systemPrompt)
            if (customInstructions.isNotBlank()) {
                append("\n\nUSER CUSTOM SYSTEM INSTRUCTIONS:\n")
                append(customInstructions)
            }
            append("\n\nCRITICAL CONVERSATIONAL & FORMATTING INSTRUCTIONS:\n")
            append("1. Dynamic Intent Adaptation: Structure your response wisely based on the user's intent. If the user asks a simple question or greets you, reply naturally, concisely, and conversationally in 1-2 sentences.\n")
            append("2. In-Depth & Technical Queries: When asked for technical explanations, code, or deep analysis, organize thoughtfully using standard Markdown.\n")
            append("3. NO FORCED CONCLUSIONS: NEVER append an artificial '### Conclusion' or 'Takeaway' section unless explicitly requested.\n")
            append("4. Tables & Data: Generate Markdown tables when comparing multiple items, metrics, or tabular document excerpts.\n")
            append("5. Tone & Personality: Speak with clarity, precision, and intelligence.")
        }
        messages.add(AiChatMessage(role = "system", content = enhancedSystemPrompt))

        // 2. User Message with injected document context
        val fullUserContent = buildString {
            if (!documentContext.isNullOrBlank()) {
                append("Document Context:\n\"\"\"\n")
                append(documentContext.trim())
                append("\n\"\"\"\n\n")
            }
            append("Instruction / Question:\n")
            append(userPrompt.trim())
        }
        messages.add(AiChatMessage(role = "user", content = fullUserContent))

        val preferredProvider = SwiffleAiConfig.getPreferredProvider(context)
        val groqApiKey = SwiffleAiConfig.getGroqApiKey(context).first()
        val openRouterApiKey = SwiffleAiConfig.getOpenRouterApiKey(context).first()

        // If user explicitly chose Firebase Gemini while Firebase is unprovisioned, return labeled sample response + notice
        if (preferredProvider == SwiffleAiConfig.PROVIDER_FIREBASE_GEMINI) {
            val notice = buildFirebaseGeminiNotice(userPrompt, documentContext)
            emit(SwiffleAiStreamEvent.Token(notice))
            SwiffleAiConfig.recordTokenUsage(context, 45)
            emit(
                SwiffleAiStreamEvent.Completed(
                    fullText = notice,
                    engineUsed = "Firebase Gemini (Requires Setup)",
                    latencyMs = 12L,
                    totalTokens = 45
                )
            )
            return@flow
        }

        val isOnline = networkMonitor.isCurrentlyOnline()

        if (isOnline) {
            var customSuccess = false

            when (preferredProvider) {
                SwiffleAiConfig.PROVIDER_GROQ -> {
                    if (groqApiKey.isNotBlank()) {
                        customSuccess = executeGroqWithFallback(groqApiKey, openRouterApiKey, messages, task)
                    }
                }
                SwiffleAiConfig.PROVIDER_OPENROUTER -> {
                    if (openRouterApiKey.isNotBlank()) {
                        customSuccess = executeOpenRouterFallback(openRouterApiKey, messages, task)
                    }
                }
                else -> {
                    if (groqApiKey.isNotBlank() || openRouterApiKey.isNotBlank()) {
                        customSuccess = executeGroqWithFallback(groqApiKey, openRouterApiKey, messages, task)
                    }
                }
            }

            if (!customSuccess) {
                try {
                    emitAll(
                        pollinationsClient.streamChat(messages = messages).onEach { event ->
                            if (event is SwiffleAiStreamEvent.Completed) {
                                SwiffleAiConfig.recordTokenUsage(context, event.totalTokens)
                            }
                        }
                    )
                    return@flow
                } catch (e: Exception) {
                    emit(
                        SwiffleAiStreamEvent.Error(
                            SwiffleAiException.Unknown(
                                "Cloud AI connection failed (${e.localizedMessage ?: "network error"}). Configure your Groq or OpenRouter API Key in Settings → AI & Cloud Services, or enable Firebase AI Logic."
                            )
                        )
                    )
                }
            }
        } else {
            emit(
                SwiffleAiStreamEvent.Error(
                    SwiffleAiException.Unknown(
                        "You are currently offline. Connect to the internet to use Cloud AI (Groq / OpenRouter / Gemini), or use on-device Gemma 2 Embedding search for semantic document lookup."
                    )
                )
            )
        }
    }

    private fun buildFirebaseGeminiNotice(userPrompt: String, documentContext: String?): String {
        val contextPreview = documentContext?.trim()?.take(280)?.let {
            "\n\n> **Grounded Document Excerpt:**\n> ${it.replace("\n", " ")}…"
        }.orEmpty()
        return buildString {
            append("**[Sample Placeholder Response — Firebase AI Logic Required]**\n\n")
            append("Gemini in Android apps built in AI Studio requires **Firebase AI Logic** to be enabled, or you can enter a **Groq** (`gsk_...`) or **OpenRouter** (`sk-or-...`) API key in **Settings → Integrations & Cloud Services**.\n")
            append(contextPreview)
            append("\n\n**Sample Analysis for:** *\"${userPrompt.take(100)}\"*\n")
            append("- Enable Firebase AI Logic in AI Studio to connect directly to `gemini-3.8-flash`.\n")
            append("- Or switch your active AI provider to **Auto / Groq / OpenRouter** in Settings.")
        }
    }

    /**
     * Executes Groq with automatic failover to OpenRouter.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<SwiffleAiStreamEvent>.executeGroqWithFallback(
        apiKey: String,
        openRouterApiKey: String,
        messages: List<AiChatMessage>,
        task: SwiffleAiTask
    ): Boolean {
        val preferredGroqModel = SwiffleAiConfig.getPreferredGroqModel(context)
        val targetGroqModel = if (preferredGroqModel.isNotBlank()) preferredGroqModel else groqClient.resolveModelForTask(task)

        if (apiKey.isNotBlank()) {
            try {
                emitAll(
                    groqClient.streamChat(apiKey = apiKey, model = targetGroqModel, messages = messages).onEach { event ->
                        if (event is SwiffleAiStreamEvent.Completed) {
                            SwiffleAiConfig.recordTokenUsage(context, event.totalTokens)
                        }
                    }
                )
                return true
            } catch (rateLimitEx: SwiffleAiException.RateLimitExhausted) {
                emit(
                    SwiffleAiStreamEvent.EngineSwitched(
                        fromEngine = "Groq ($targetGroqModel)",
                        toEngine = "OpenRouter",
                        reason = "Rate limit reached. Auto-switching to OpenRouter."
                    )
                )
                return executeOpenRouterFallback(openRouterApiKey, messages, task)
            } catch (otherEx: Exception) {
                if (openRouterApiKey.isNotBlank()) {
                    emit(
                        SwiffleAiStreamEvent.EngineSwitched(
                            fromEngine = "Groq",
                            toEngine = "OpenRouter",
                            reason = "Connection issue: ${otherEx.localizedMessage}. Auto-switching to OpenRouter."
                        )
                    )
                    return executeOpenRouterFallback(openRouterApiKey, messages, task)
                }
                return false
            }
        } else {
            return executeOpenRouterFallback(openRouterApiKey, messages, task)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<SwiffleAiStreamEvent>.executeOpenRouterFallback(
        openRouterApiKey: String,
        messages: List<AiChatMessage>,
        task: SwiffleAiTask
    ): Boolean {
        if (openRouterApiKey.isNotBlank()) {
            val targetOpenRouterModel = openRouterClient.resolveFreeModelForTask(task)
            try {
                emitAll(
                    openRouterClient.streamChat(apiKey = openRouterApiKey, model = targetOpenRouterModel, messages = messages).onEach { event ->
                        if (event is SwiffleAiStreamEvent.Completed) {
                            SwiffleAiConfig.recordTokenUsage(context, event.totalTokens)
                        }
                    }
                )
                return true
            } catch (_: Exception) {
                return false
            }
        }
        return false
    }

    /**
     * Executes RAG-grounded query using Gemma 2 TFLite embeddings to retrieve the most semantically relevant
     * document chunks before feeding into the inference pipeline.
     */
    fun queryWithRag(
        docId: String,
        userPrompt: String,
        currentPageContext: String? = null,
        task: SwiffleAiTask = SwiffleAiTask.AskQuestion
    ): Flow<SwiffleAiStreamEvent> {
        val topChunks = gemmaEmbeddingEngine.searchSimilarChunks(docId, userPrompt, topK = 3)
        val engineLabel = if (gemmaEmbeddingEngine.isNeuralInterpreterLoaded()) {
            "Gemma 2 TFLite Neural Embeddings"
        } else {
            "BM25 + TF-IDF Hybrid Index"
        }
        val combinedContext = buildString {
            if (!currentPageContext.isNullOrBlank()) {
                append("Current Page Excerpt:\n")
                append(currentPageContext.trim())
                append("\n\n")
            }
            if (topChunks.isNotEmpty()) {
                append("Relevant Document Excerpts (Retrieved via $engineLabel):\n")
                topChunks.forEach { (chunk, score) ->
                    append("[Page ${chunk.pageNum} - Relevance ${(score * 100).toInt()}%]:\n\"")
                    append(chunk.text)
                    append("\"\n\n")
                }
            }
        }
        return query(userPrompt, combinedContext.ifBlank { null }, task)
    }
}
