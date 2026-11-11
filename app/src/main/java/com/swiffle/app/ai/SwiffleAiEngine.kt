package com.swiffle.app.ai

import android.content.Context
import com.swiffle.app.ai.client.GroqApiClient
import com.swiffle.app.ai.client.OpenRouterApiClient
import com.swiffle.app.ai.client.PollinationsApiClient
import com.swiffle.app.ai.offline.GemmaEmbeddingEngine
import com.swiffle.app.ai.offline.LocalQwenEngine
import com.swiffle.app.ai.offline.SwiffleOfflineModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * The Master Intelligence Orchestrator for Swiffle AI.
 *
 * Implements the 4-Tier Multi-Engine Architecture:
 * 1. Tier 1 (Online Custom LPU): Groq Cloud LPUs (sub-second TTFT via openai/gpt-oss-120b, 20b)
 * 2. Tier 2 (Online Custom Failover): OpenRouter (:free model pool) on 429 rate limit or network glitch
 * 3. Tier 3 (Online Zero-Config Flagship): Free streaming LLM via Pollinations AI (openai/gpt-oss-20b/mistral)
 * 4. Tier 4 (Air-Gapped On-Device): Google Gemma & Local Offline Model (Gemma 2 / Qwen 2.5)
 *
 * Safe, non-blocking, zero heap leak coroutine flow.
 */
class SwiffleAiEngine(
    private val context: Context,
    val networkMonitor: SwiffleNetworkMonitor = SwiffleNetworkMonitor(context),
    val offlineModelManager: SwiffleOfflineModels = SwiffleOfflineModels(context),
    private val groqClient: GroqApiClient = GroqApiClient(),
    private val openRouterClient: OpenRouterApiClient = OpenRouterApiClient(),
    private val pollinationsClient: PollinationsApiClient = PollinationsApiClient(),
    val localEngine: LocalQwenEngine = LocalQwenEngine(context, offlineModelManager),
    val gemmaEmbeddingEngine: GemmaEmbeddingEngine = GemmaEmbeddingEngine()
) {

    /**
     * Public entry-point: Queries Swiffle AI with contextual document grounding.
     *
     * @param userPrompt The user's query or instruction
     * @param documentContext Extracted text/tables from the currently active PDF page
     * @param task Document intelligence task (Summarize, ExtractTables, Explain, Translate, AskQuestion)
     */
    fun query(
        userPrompt: String,
        documentContext: String? = null,
        task: SwiffleAiTask = SwiffleAiTask.AskQuestion
    ): Flow<SwiffleAiStreamEvent> = flow {
        // Construct standard prompt messages
        val messages = mutableListOf<AiChatMessage>()

        // 1. System Prompt with intelligent adaptive formatting instructions
        val enhancedSystemPrompt = buildString {
            append(task.systemPrompt)
            append("\n\nCRITICAL CONVERSATIONAL & FORMATTING INSTRUCTIONS:\n")
            append("1. Dynamic Intent Adaptation: Structure your response wisely based on the user's intent. If the user asks a simple question or greets you, reply naturally, concisely, and conversationally in 1-2 sentences. Do NOT force unwanted headings, bullet points, or conclusion sections.\n")
            append("2. In-Depth & Technical Queries: When asked for technical explanations, code, or deep analysis, organize thoughtfully using standard Markdown (e.g. ## Overview, ## Implementation) without repetitive or artificial template titles.\n")
            append("3. NO FORCED CONCLUSIONS: NEVER append an artificial '### Conclusion', 'Summary', or 'Takeaway' section at the end of your response unless the user explicitly requested an executive summary, formal recommendation, or comprehensive audit report.\n")
            append("4. Markdown Dividers & Cleanliness: When using horizontal separators, use standard '---'. Never output stray hyphens, raw asterisks, or repetitive labels.\n")
            append("5. Tables & Data: Generate Markdown tables ONLY when comparing multiple items, metrics, or parameters where tabular clarity is genuinely valuable.\n")
            append("6. Tone & Personality: Speak with clarity, depth, and intelligence. Be direct and helpful.")
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

        val isOnline = networkMonitor.isCurrentlyOnline()

        if (isOnline) {
            val groqApiKey = SwiffleAiConfig.getGroqApiKey(context).first()
            val openRouterApiKey = SwiffleAiConfig.getOpenRouterApiKey(context).first()

            // 1. Attempt user-provided Groq or OpenRouter keys if configured
            var customSuccess = false
            if (groqApiKey.isNotBlank() || openRouterApiKey.isNotBlank()) {
                customSuccess = executeGroqWithFallback(
                    apiKey = groqApiKey,
                    openRouterApiKey = openRouterApiKey,
                    messages = messages,
                    task = task
                )
            }

            // 2. Default Zero-Config Online Tier: High-speed streaming LLM
            if (!customSuccess) {
                try {
                    emitAll(pollinationsClient.streamChat(messages = messages))
                    return@flow
                } catch (_: Exception) {
                    // Fall through to on-device engine
                }
            }

            // 3. Fall back to on-device engine if cloud attempts failed
            if (localEngine.isAvailable()) {
                emit(
                    SwiffleAiStreamEvent.EngineSwitched(
                        fromEngine = "Cloud AI",
                        toEngine = "Google Gemma / On-Device Engine",
                        reason = "Network issue, using on-device model"
                    )
                )
                emitAll(localEngine.streamChat(messages, task))
            } else {
                emit(
                    SwiffleAiStreamEvent.Error(
                        SwiffleAiException.Unknown("Unable to connect to Swiffle AI. Please check your internet or download the Google Gemma model in Settings.")
                    )
                )
            }
        } else {
            // Pure Air-Gapped Offline Mode
            if (localEngine.isAvailable()) {
                emitAll(localEngine.streamChat(messages, task))
            } else {
                emit(SwiffleAiStreamEvent.OfflineModelRequired)
            }
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
        val targetGroqModel = groqClient.resolveModelForTask(task)

        if (apiKey.isNotBlank()) {
            try {
                emitAll(groqClient.streamChat(apiKey = apiKey, model = targetGroqModel, messages = messages))
                return true
            } catch (rateLimitEx: SwiffleAiException.RateLimitExhausted) {
                emit(
                    SwiffleAiStreamEvent.EngineSwitched(
                        fromEngine = "Groq ($targetGroqModel)",
                        toEngine = "OpenRouter",
                        reason = "Rate limit reached. Auto-switching to OpenRouter free pool."
                    )
                )
                return executeOpenRouterFallback(openRouterApiKey, messages, task)
            } catch (otherEx: Exception) {
                emit(
                    SwiffleAiStreamEvent.EngineSwitched(
                        fromEngine = "Groq",
                        toEngine = "OpenRouter",
                        reason = "Connection issue: ${otherEx.localizedMessage}. Auto-switching to OpenRouter."
                    )
                )
                return executeOpenRouterFallback(openRouterApiKey, messages, task)
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
                emitAll(openRouterClient.streamChat(apiKey = openRouterApiKey, model = targetOpenRouterModel, messages = messages))
                return true
            } catch (_: Exception) {
                return false
            }
        }
        return false
    }

    /**
     * Executes RAG-grounded query using Gemma 2 embeddings to retrieve the most semantically relevant
     * document chunks before feeding into the inference pipeline.
     */
    fun queryWithRag(
        docId: String,
        userPrompt: String,
        currentPageContext: String? = null,
        task: SwiffleAiTask = SwiffleAiTask.AskQuestion
    ): Flow<SwiffleAiStreamEvent> {
        val topChunks = gemmaEmbeddingEngine.searchSimilarChunks(docId, userPrompt, topK = 3)
        val combinedContext = buildString {
            if (!currentPageContext.isNullOrBlank()) {
                append("Current Page Excerpt:\n")
                append(currentPageContext.trim())
                append("\n\n")
            }
            if (topChunks.isNotEmpty()) {
                append("Relevant Document Excerpts (Retrieved via Gemma Embeddings):\n")
                topChunks.forEachIndexed { i, (chunk, score) ->
                    append("[Page ${chunk.pageNum} - Relevance ${(score * 100).toInt()}%]:\n\"")
                    append(chunk.text)
                    append("\"\n\n")
                }
            }
        }
        return query(userPrompt, combinedContext.ifBlank { null }, task)
    }
}
