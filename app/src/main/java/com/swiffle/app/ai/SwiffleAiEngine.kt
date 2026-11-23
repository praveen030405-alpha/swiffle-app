package com.swiffle.app.ai

import android.content.Context
import com.swiffle.app.ai.client.GeminiApiClient
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
 * 1. Google Gemini Free-Tier Models (`gemini-2.5-flash`, `gemini-2.5-flash-lite`, `gemini-2.5-pro`)
 * 2. Custom Groq Cloud LPUs (`llama-3.3-70b-versatile`, `llama-3.1-8b-instant`, `llama-4-scout-17b`)
 * 3. Custom OpenRouter API (`meta-llama/llama-3.3-70b-instruct:free`, `qwen/qwen-2.5-72b-instruct:free`)
 * 4. Pollinations Streaming Fallback & On-Device Gemma 2 TFLite Embedding RAG Search
 */
class SwiffleAiEngine(
    private val context: Context,
    val networkMonitor: SwiffleNetworkMonitor = SwiffleNetworkMonitor(context),
    val offlineModelManager: SwiffleOfflineModels = SwiffleOfflineModels(context),
    private val geminiClient: GeminiApiClient = GeminiApiClient(),
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
        task: SwiffleAiTask = SwiffleAiTask.AskQuestion,
        modelTier: SwiffleModelTier? = null
    ): Flow<SwiffleAiStreamEvent> = flow {
        emit(
            SwiffleAiStreamEvent.StatusUpdate(
                if (!documentContext.isNullOrBlank()) {
                    "Preparing your attached document context for analysis."
                } else {
                    "Connecting to the Swiffle AI streaming service."
                }
            )
        )

        val messages = mutableListOf<AiChatMessage>()

        val customAiName = SwiffleAiConfig.getCustomAiName(context)
        val customInstructions = SwiffleAiConfig.getCustomAiInstructions(context)

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
        val geminiApiKey = SwiffleAiConfig.getGeminiApiKey(context).first()
        val groqApiKey = SwiffleAiConfig.getGroqApiKey(context).first()
        val openRouterApiKey = SwiffleAiConfig.getOpenRouterApiKey(context).first()

        val isOnline = networkMonitor.isCurrentlyOnline()

        if (!isOnline) {
            emit(
                SwiffleAiStreamEvent.Error(
                    SwiffleAiException.Unknown(
                        "You are currently offline. Connect to the internet to use Cloud AI (Gemini / Groq / OpenRouter), or use on-device Gemma 2 Embedding search for semantic document lookup."
                    )
                )
            )
            return@flow
        }

        var customSuccess = false

        when (preferredProvider) {
            SwiffleAiConfig.PROVIDER_GROQ -> {
                if (groqApiKey.isNotBlank()) {
                    customSuccess = executeGroqFreePool(groqApiKey, messages, task, modelTier)
                }
                if (!customSuccess && openRouterApiKey.isNotBlank()) {
                    customSuccess = executeOpenRouterFreePool(openRouterApiKey, messages, task, modelTier)
                }
                if (!customSuccess && geminiApiKey.isNotBlank()) {
                    customSuccess = executeGeminiFreePool(geminiApiKey, messages, task, modelTier)
                }
            }

            SwiffleAiConfig.PROVIDER_OPENROUTER -> {
                if (openRouterApiKey.isNotBlank()) {
                    customSuccess = executeOpenRouterFreePool(openRouterApiKey, messages, task, modelTier)
                }
                if (!customSuccess && geminiApiKey.isNotBlank()) {
                    customSuccess = executeGeminiFreePool(geminiApiKey, messages, task, modelTier)
                }
            }

            SwiffleAiConfig.PROVIDER_GEMINI, SwiffleAiConfig.PROVIDER_FIREBASE_GEMINI -> {
                if (geminiApiKey.isNotBlank()) {
                    customSuccess = executeGeminiFreePool(geminiApiKey, messages, task, modelTier)
                    if (!customSuccess) {
                        emit(
                            SwiffleAiStreamEvent.Error(
                                SwiffleAiException.Unknown(
                                    "Gemini could not complete this request right now. Check your GEMINI_API_KEY in AI Studio Secrets or Settings → AI Providers & Cloud Integrations, or inspect Usage & Limits for active free-tier cooldowns."
                                )
                            )
                        )
                        return@flow
                    }
                } else {
                    emit(
                        SwiffleAiStreamEvent.Error(
                            SwiffleAiException.InvalidApiKey(
                                "Google Gemini API key not configured. Add GEMINI_API_KEY in AI Studio Secrets or Settings → AI Providers & Cloud Integrations."
                            )
                        )
                    )
                    return@flow
                }
            }

            else -> {
                // PROVIDER_AUTO:
                // 1. Exhaust Cloud Groq Free-Tier pool completely
                // 2. Exhaust OpenRouter :free model pool completely
                // 3. Fall back to Google Gemini Free-Tier pool
                if (groqApiKey.isNotBlank()) {
                    customSuccess = executeGroqFreePool(groqApiKey, messages, task, modelTier)
                }
                if (!customSuccess && openRouterApiKey.isNotBlank()) {
                    if (groqApiKey.isNotBlank()) {
                        emit(
                            SwiffleAiStreamEvent.EngineSwitched(
                                fromEngine = "Groq Free Tier",
                                toEngine = "OpenRouter Free Tier",
                                reason = "Groq free-tier quota exhausted. Routing to OpenRouter free pool."
                            )
                        )
                    }
                    customSuccess = executeOpenRouterFreePool(openRouterApiKey, messages, task, modelTier)
                }
                if (!customSuccess && geminiApiKey.isNotBlank()) {
                    if (groqApiKey.isNotBlank() || openRouterApiKey.isNotBlank()) {
                        emit(
                            SwiffleAiStreamEvent.EngineSwitched(
                                fromEngine = "Groq / OpenRouter Free Tier",
                                toEngine = "Google Gemini Free Tier",
                                reason = "Groq and OpenRouter free tiers exhausted. Switching to Gemini free tier."
                            )
                        )
                    }
                    customSuccess = executeGeminiFreePool(geminiApiKey, messages, task, modelTier)
                }
            }
        }

        if (!customSuccess) {
            emit(SwiffleAiStreamEvent.StatusUpdate("Routing your request to the Swiffle streaming service."))
            try {
                val fallbackModel = modelTier?.pollinationsModelId ?: "openai-fast"
                emitAll(
                    pollinationsClient.streamChat(
                        messages = messages,
                        model = fallbackModel,
                        temperature = modelTier?.defaultTemperature ?: 0.3f
                    ).onEach { event ->
                        if (event is SwiffleAiStreamEvent.Completed) {
                            SwiffleAiConfig.recordDetailedUsage(
                                context = context,
                                modelId = event.modelId.ifBlank { fallbackModel },
                                provider = "Pollinations Cloud",
                                promptTokens = event.promptTokens,
                                outputTokens = event.outputTokens.takeIf { it > 0 } ?: (event.fullText.length / 4),
                                thoughtTokens = event.thoughtTokens,
                                totalTokens = event.totalTokens,
                                latencyMs = event.latencyMs
                            )
                        }
                    }
                )
            } catch (_: Exception) {
                emit(
                    SwiffleAiStreamEvent.Error(
                        SwiffleAiException.Unknown(
                            "The cloud AI service could not be reached right now. Please check your connection or verify your API keys in Settings."
                        )
                    )
                )
            }
        }
    }

    /**
     * Queries Swiffle AI using on-device Gemma 2 semantic chunk retrieval combined with current page context.
     */
    fun queryWithRag(
        docId: String,
        userPrompt: String,
        currentPageContext: String? = null,
        task: SwiffleAiTask = SwiffleAiTask.AskQuestion,
        modelTier: SwiffleModelTier? = null
    ): Flow<SwiffleAiStreamEvent> {
        val retrievedChunks = gemmaEmbeddingEngine.searchSimilarChunks(
            docId = docId,
            query = userPrompt,
            topK = 3
        )
        val combinedContext = buildString {
            if (!currentPageContext.isNullOrBlank()) {
                append(currentPageContext.trim())
                append("\n\n")
            }
            if (retrievedChunks.isNotEmpty()) {
                append("Retrieved Document Context (Semantic Match):\n")
                retrievedChunks.forEach { (chunk, _) ->
                    append("- [Page ${chunk.pageNum}] ${chunk.text}\n")
                }
            }
        }.ifBlank { null }

        return query(
            userPrompt = userPrompt,
            documentContext = combinedContext,
            task = task,
            modelTier = modelTier
        )
    }

    /**
     * Stage 1: Exhausts the entire Cloud Groq Free-Tier model pool before handing off to OpenRouter.
     * Automatically skips any model currently in an active 429 cooldown or at its daily free RPD cap.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<SwiffleAiStreamEvent>.executeGroqFreePool(
        apiKey: String,
        messages: List<AiChatMessage>,
        task: SwiffleAiTask,
        modelTier: SwiffleModelTier?
    ): Boolean {
        if (apiKey.isBlank()) return false

        val preferredGroqModel = SwiffleAiConfig.getPreferredGroqModel(context)
        val primaryModel = modelTier?.groqModelId
            ?: preferredGroqModel.ifBlank { groqClient.resolveModelForTask(task) }

        val candidateModels = linkedSetOf(primaryModel).apply {
            SwiffleAiConfig.GROQ_FREE_MODELS.forEach { add(it.modelId) }
        }.toList()

        for ((idx, modelId) in candidateModels.withIndex()) {
            if (SwiffleAiConfig.isModelInCooldown(context, modelId) ||
                SwiffleAiConfig.isModelDailyQuotaExhausted(context, modelId)
            ) {
                continue
            }

            try {
                emit(
                    SwiffleAiStreamEvent.StatusUpdate(
                        if (idx == 0) {
                            "Opening a high-speed Groq streaming session."
                        } else {
                            "Switching to another Groq free-tier model to continue streaming."
                        }
                    )
                )
                emitAll(
                    groqClient.streamChat(
                        apiKey = apiKey,
                        model = modelId,
                        messages = messages,
                        temperature = modelTier?.defaultTemperature ?: 0.2f
                    ).onEach { event ->
                        if (event is SwiffleAiStreamEvent.Completed) {
                            SwiffleAiConfig.recordDetailedUsage(
                                context = context,
                                modelId = event.modelId.ifBlank { modelId },
                                provider = "Groq Cloud",
                                promptTokens = event.promptTokens,
                                outputTokens = event.outputTokens.takeIf { it > 0 } ?: (event.fullText.length / 4),
                                thoughtTokens = event.thoughtTokens,
                                totalTokens = event.totalTokens,
                                latencyMs = event.latencyMs
                            )
                        }
                    }
                )
                return true
            } catch (rateLimitEx: SwiffleAiException.RateLimitExhausted) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "Groq Cloud",
                    retryAfterSec = rateLimitEx.retryAfterSec,
                    detailMessage = "Groq free-tier rate limit hit on $modelId (retry in ${rateLimitEx.retryAfterSec}s)"
                )
                if (idx < candidateModels.lastIndex) {
                    emit(
                        SwiffleAiStreamEvent.EngineSwitched(
                            fromEngine = "Groq ($modelId)",
                            toEngine = "Groq (${candidateModels[idx + 1]})",
                            reason = "Free-tier rate limit reached on $modelId."
                        )
                    )
                }
            } catch (_: SwiffleAiException.InvalidApiKey) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "Groq Cloud",
                    retryAfterSec = 10L,
                    detailMessage = "Groq API key rejected (HTTP 401/403). Check GROQ_API_KEY."
                )
                break
            } catch (_: SwiffleAiException.ModelUnavailable) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "Groq Cloud",
                    retryAfterSec = 30L,
                    detailMessage = "Groq model $modelId temporarily unavailable."
                )
            } catch (_: Exception) {
                // Try next model in Groq free pool before leaving Groq
                continue
            }
        }
        return false
    }

    /**
     * Stage 2: Exhausts the entire OpenRouter `:free` model pool before handing off to Google Gemini.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<SwiffleAiStreamEvent>.executeOpenRouterFreePool(
        openRouterApiKey: String,
        messages: List<AiChatMessage>,
        task: SwiffleAiTask,
        modelTier: SwiffleModelTier?
    ): Boolean {
        if (openRouterApiKey.isBlank()) return false

        val primaryModel = modelTier?.openRouterModelId ?: openRouterClient.resolveFreeModelForTask(task)
        val candidateModels = linkedSetOf(primaryModel).apply {
            SwiffleAiConfig.OPENROUTER_FREE_MODELS.forEach { add(it.modelId) }
        }.toList()

        for ((idx, modelId) in candidateModels.withIndex()) {
            if (SwiffleAiConfig.isModelInCooldown(context, modelId) ||
                SwiffleAiConfig.isModelDailyQuotaExhausted(context, modelId)
            ) {
                continue
            }

            try {
                emit(
                    SwiffleAiStreamEvent.StatusUpdate(
                        if (idx == 0) {
                            "Opening an OpenRouter free-tier streaming session."
                        } else {
                            "Switching to another OpenRouter free-tier model to maintain speed."
                        }
                    )
                )
                emitAll(
                    openRouterClient.streamChat(
                        apiKey = openRouterApiKey,
                        model = modelId,
                        messages = messages,
                        temperature = modelTier?.defaultTemperature ?: 0.2f
                    ).onEach { event ->
                        if (event is SwiffleAiStreamEvent.Completed) {
                            SwiffleAiConfig.recordDetailedUsage(
                                context = context,
                                modelId = event.modelId.ifBlank { modelId },
                                provider = "OpenRouter",
                                promptTokens = event.promptTokens,
                                outputTokens = event.outputTokens.takeIf { it > 0 } ?: (event.fullText.length / 4),
                                thoughtTokens = event.thoughtTokens,
                                totalTokens = event.totalTokens,
                                latencyMs = event.latencyMs
                            )
                        }
                    }
                )
                return true
            } catch (rateLimitEx: SwiffleAiException.RateLimitExhausted) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "OpenRouter",
                    retryAfterSec = rateLimitEx.retryAfterSec,
                    detailMessage = "OpenRouter free-tier limit hit on $modelId (retry in ${rateLimitEx.retryAfterSec}s)"
                )
                if (idx < candidateModels.lastIndex) {
                    emit(
                        SwiffleAiStreamEvent.EngineSwitched(
                            fromEngine = "OpenRouter ($modelId)",
                            toEngine = "OpenRouter (${candidateModels[idx + 1]})",
                            reason = "Free-tier rate limit reached on $modelId."
                        )
                    )
                }
            } catch (_: SwiffleAiException.InvalidApiKey) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "OpenRouter",
                    retryAfterSec = 10L,
                    detailMessage = "OpenRouter API key rejected (HTTP 401/403). Check OPENROUTER_API_KEY."
                )
                break
            } catch (_: SwiffleAiException.ModelUnavailable) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "OpenRouter",
                    retryAfterSec = 30L,
                    detailMessage = "OpenRouter model $modelId temporarily unavailable."
                )
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }

    /**
     * Stage 3: Executes Google Gemini Free-Tier streaming with automatic intra-tier fallback
     * across all Gemini free-tier models after Groq and OpenRouter free tiers are exhausted.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<SwiffleAiStreamEvent>.executeGeminiFreePool(
        geminiApiKey: String,
        messages: List<AiChatMessage>,
        task: SwiffleAiTask,
        modelTier: SwiffleModelTier?
    ): Boolean {
        if (geminiApiKey.isBlank()) return false

        val primaryModel = modelTier?.geminiModelId
            ?: SwiffleAiConfig.getPreferredGeminiModel(context).ifBlank {
                geminiClient.resolveFreeModelForTask(task)
            }

        val candidateModels = linkedSetOf(
            primaryModel,
            SwiffleAiConfig.GEMINI_MODEL_FLASH,
            SwiffleAiConfig.GEMINI_MODEL_FLASH_LITE,
            SwiffleAiConfig.GEMINI_MODEL_PRO
        ).toList()

        for ((idx, modelId) in candidateModels.withIndex()) {
            if (SwiffleAiConfig.isModelInCooldown(context, modelId) ||
                SwiffleAiConfig.isModelDailyQuotaExhausted(context, modelId)
            ) {
                continue
            }

            try {
                emit(
                    SwiffleAiStreamEvent.StatusUpdate(
                        if (idx == 0) {
                            "Opening a Google Gemini free-tier streaming session."
                        } else {
                            "Switching smoothly to $modelId to maintain your free-tier stream."
                        }
                    )
                )

                emitAll(
                    geminiClient.streamChat(
                        apiKey = geminiApiKey,
                        model = modelId,
                        messages = messages,
                        temperature = modelTier?.defaultTemperature ?: 0.25f
                    ).onEach { event ->
                        if (event is SwiffleAiStreamEvent.Completed) {
                            SwiffleAiConfig.recordDetailedUsage(
                                context = context,
                                modelId = event.modelId.ifBlank { modelId },
                                provider = "Google Gemini",
                                promptTokens = event.promptTokens,
                                outputTokens = event.outputTokens,
                                thoughtTokens = event.thoughtTokens,
                                totalTokens = event.totalTokens,
                                latencyMs = event.latencyMs
                            )
                        }
                    }
                )
                return true
            } catch (rateLimitEx: SwiffleAiException.RateLimitExhausted) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "Google Gemini",
                    retryAfterSec = rateLimitEx.retryAfterSec,
                    detailMessage = "Gemini free-tier rate limit hit on $modelId (retry in ${rateLimitEx.retryAfterSec}s)"
                )
                if (idx < candidateModels.lastIndex) {
                    emit(
                        SwiffleAiStreamEvent.EngineSwitched(
                            fromEngine = "Gemini ($modelId)",
                            toEngine = "Gemini (${candidateModels[idx + 1]})",
                            reason = "Free-tier rate limit reached on $modelId."
                        )
                    )
                }
            } catch (_: SwiffleAiException.InvalidApiKey) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "Google Gemini",
                    retryAfterSec = 10L,
                    detailMessage = "Gemini API key rejected (HTTP 401/403). Check GEMINI_API_KEY."
                )
                break
            } catch (_: SwiffleAiException.ModelUnavailable) {
                SwiffleAiConfig.recordRateLimitHit(
                    context = context,
                    modelId = modelId,
                    provider = "Google Gemini",
                    retryAfterSec = 30L,
                    detailMessage = "$modelId temporarily overloaded."
                )
                if (idx < candidateModels.lastIndex) {
                    emit(
                        SwiffleAiStreamEvent.EngineSwitched(
                            fromEngine = "Gemini ($modelId)",
                            toEngine = "Gemini (${candidateModels[idx + 1]})",
                            reason = "$modelId temporarily overloaded."
                        )
                    )
                }
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }
}
