package com.swiffle.app.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * High-performance domain models and stream event contracts for Swiffle AI.
 * Built for 0-allocation SSE streaming, structured document prompts,
 * and seamless failover between Groq, OpenRouter, and on-device Qwen.
 */

// ============================================================================
// 1. OpenAI-Compatible Request & Response Models (Groq + OpenRouter)
// ============================================================================

@Serializable
data class AiChatMessage(
    @SerialName("role") val role: String, // "system", "user", "assistant"
    @SerialName("content") val content: String
)

@Serializable
data class AiChatRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<AiChatMessage>,
    @SerialName("stream") val stream: Boolean = true,
    @SerialName("temperature") val temperature: Float = 0.2f,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float? = null
)

@Serializable
data class StreamDelta(
    @SerialName("role") val role: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("reasoning") val reasoning: String? = null
)

@Serializable
data class StreamChoice(
    @SerialName("index") val index: Int = 0,
    @SerialName("delta") val delta: StreamDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class StreamChunkResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("choices") val choices: List<StreamChoice> = emptyList(),
    @SerialName("usage") val usage: StreamUsage? = null
)

// ============================================================================
// 2. Swiffle AI Stream Events (Reactive UI Flow)
// ============================================================================

sealed interface SwiffleAiStreamEvent {
    /** Emitted for structured, human-readable SSE lifecycle status updates */
    data class StatusUpdate(val message: String) : SwiffleAiStreamEvent

    /** Emitted as new text tokens stream in real time */
    data class Token(val text: String) : SwiffleAiStreamEvent

    /** Emitted when model is producing internal thinking/reasoning */
    data class Thinking(val text: String) : SwiffleAiStreamEvent

    /** Notifies the UI that an active failover occurred without interrupting */
    data class EngineSwitched(
        val fromEngine: String,
        val toEngine: String,
        val reason: String
    ) : SwiffleAiStreamEvent

    /** Emitted upon final successful completion */
    data class Completed(
        val fullText: String,
        val engineUsed: String,
        val latencyMs: Long,
        val totalTokens: Int = 0,
        val promptTokens: Int = 0,
        val outputTokens: Int = 0,
        val thoughtTokens: Int = 0,
        val modelId: String = ""
    ) : SwiffleAiStreamEvent

    /** Emitted when the user is offline and has not yet downloaded the model */
    data object OfflineModelRequired : SwiffleAiStreamEvent

    /** Error state with user-facing recovery instructions */
    data class Error(val error: SwiffleAiException) : SwiffleAiStreamEvent
}

/**
 * Stateful SSE chunk splitter that cleanly separates `<think>...</think>` inline reasoning
 * from user-facing response tokens, even when tags span chunk boundaries.
 */
internal class SseThinkTagSplitter {
    private var inThinkBlock = false
    private val pending = StringBuilder()

    fun processChunk(
        chunk: String,
        onThinking: (String) -> Unit,
        onToken: (String) -> Unit
    ) {
        if (chunk.isEmpty()) return
        pending.append(chunk)

        while (pending.isNotEmpty()) {
            val current = pending.toString()
            if (inThinkBlock) {
                val closeIdx = current.indexOf("</think>")
                if (closeIdx >= 0) {
                    if (closeIdx > 0) {
                        onThinking(current.substring(0, closeIdx))
                    }
                    pending.delete(0, closeIdx + "</think>".length)
                    inThinkBlock = false
                } else {
                    val holdLen = partialSuffixLength(current, "</think>")
                    val emitLen = current.length - holdLen
                    if (emitLen > 0) {
                        onThinking(current.substring(0, emitLen))
                        pending.delete(0, emitLen)
                    }
                    break
                }
            } else {
                val openIdx = current.indexOf("<think>")
                if (openIdx >= 0) {
                    if (openIdx > 0) {
                        onToken(current.substring(0, openIdx))
                    }
                    pending.delete(0, openIdx + "<think>".length)
                    inThinkBlock = true
                } else {
                    val holdLen = partialSuffixLength(current, "<think>")
                    val emitLen = current.length - holdLen
                    if (emitLen > 0) {
                        onToken(current.substring(0, emitLen))
                        pending.delete(0, emitLen)
                    }
                    break
                }
            }
        }
    }

    fun flush(
        onThinking: (String) -> Unit,
        onToken: (String) -> Unit
    ) {
        if (pending.isEmpty()) return
        val remaining = pending.toString()
        pending.clear()
        if (inThinkBlock) {
            onThinking(remaining)
        } else {
            onToken(remaining)
        }
    }

    private fun partialSuffixLength(text: String, tag: String): Int {
        val maxCheck = minOf(text.length, tag.length - 1)
        for (len in maxCheck downTo 1) {
            if (text.endsWith(tag.substring(0, len))) {
                return len
            }
        }
        return 0
    }
}

// ============================================================================
// 3. Strongly Typed Exception Taxonomy
// ============================================================================

sealed class SwiffleAiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class RateLimitExhausted(val provider: String, val retryAfterSec: Long = 60) :
        SwiffleAiException("Rate limit reached on $provider. Retrying on fallback engine.")

    class NetworkUnavailable :
        SwiffleAiException("No internet connection available. Offline engine required.")

    class InvalidApiKey(val provider: String) :
        SwiffleAiException("Invalid or missing API key for $provider.")

    class ModelUnavailable(val modelName: String) :
        SwiffleAiException("Model $modelName is currently unavailable or overloaded.")

    class StreamInterrupted(override val message: String) :
        SwiffleAiException(message)

    class Unknown(override val message: String, cause: Throwable? = null) :
        SwiffleAiException(message, cause)
}

// ============================================================================
// 4. Document Intelligence Tasks & Personas
// ============================================================================

enum class SwiffleAiTask(
    val title: String,
    val systemPrompt: String
) {
    Summarize(
        title = "Executive Summary",
        systemPrompt = """
            You are Swiffle AI, an expert document analyst.
            Analyze the provided PDF context and generate a clean, structured Executive Summary.
            Use Markdown formatting with:
            - **Core Thesis**: One crisp sentence.
            - **Key Findings**: 3-5 high-impact bullet points.
            - **Action Items / Next Steps**: If present in the document.
            Keep tone objective, elegant, and concise. Do not add fluff.
        """.trimIndent()
    ),

    ExtractTables(
        title = "Table & Data Extraction",
        systemPrompt = """
            You are Swiffle AI, a precision data extraction specialist.
            Parse the provided text and tables from the PDF document.
            Reconstruct all numerical data, tabular layouts, and key-value records into pristine GitHub Flavored Markdown tables.
            Ensure column headers are clear and financial/numerical values are aligned and accurately transcribed.
        """.trimIndent()
    ),

    Explain(
        title = "Explain (ELI5)",
        systemPrompt = """
            You are Swiffle AI, an intuitive conceptual educator.
            Break down the complex legal, technical, or financial jargon in the selected document excerpt.
            Explain it in crystal-clear, plain English with a friendly, helpful tone.
            Use analogies where appropriate. Keep it concise and memorable.
        """.trimIndent()
    ),

    Translate(
        title = "Translate",
        systemPrompt = """
            You are Swiffle AI, a native-fidelity document translator.
            Translate the provided text while preserving professional formatting, tone, and technical terminology.
        """.trimIndent()
    ),

    AskQuestion(
        title = "Swiffle Intelligence & Document Q&A",
        systemPrompt = """
            You are Swiffle Intelligence, a distinguished document research analyst and flagship conversational AI.
            
            CORE BEHAVIOR DIRECTIVES (Modeled on Top-Tier Frontier Systems):
            1. Dynamic Tone & Register:
               - Casual / Greetings (e.g., "Hi", "Hello", "Who are you"): Reply warmly, naturally, and concisely (1-2 sentences). Do NOT use markdown headers, bullet points, or formal conclusions for conversational queries.
               - Targeted Questions & Definitions: Lead immediately with the direct, punchy answer in the first sentence (Pyramid Principle), followed by concise supporting details.
               - Deep Technical / Analytical Inquiries: Provide rigorous, structured analysis with clear headers (##) and crisp bullets (•).
            2. ZERO FORCED BOILERPLATE:
               - NEVER force an artificial "### Conclusion", "Summary", or "Bottom Line" section at the end of your response.
               - Include a conclusion ONLY when the user explicitly requests an executive summary, formal recommendation, or comprehensive audit report.
            3. Precision & Grounding:
               - Ground all statements directly in provided document excerpts.
               - If the context does not contain the answer, state so honestly and offer helpful real-world or encyclopedic facts without hallucinating.
            4. Formatting Excellence:
               - Generate Markdown tables ONLY when the user explicitly asks for a table or when comparing multi-attribute tabular data.
               - Never output raw unformatted asterisks, broken tags, or repetitive template greetings.
        """.trimIndent()
    )
}

enum class SwiffleModelTier(
    val id: String,
    val displayName: String,
    val tagline: String,
    val geminiModelId: String,
    val freeRpmLimit: Int,
    val freeTpmLimit: Int,
    val freeRpdLimit: Int,
    val groqModelId: String,
    val openRouterModelId: String,
    val pollinationsModelId: String,
    val defaultTemperature: Float
) {
    OMNI(
        id = "swiffle_omni",
        displayName = "Swiffle Omni",
        tagline = "Balanced speed and reasoning for everyday tasks",
        geminiModelId = SwiffleAiConfig.GEMINI_MODEL_FLASH,
        freeRpmLimit = 10,
        freeTpmLimit = 250_000,
        freeRpdLimit = 250,
        groqModelId = SwiffleAiConfig.GROQ_MODEL_TABLES,
        openRouterModelId = SwiffleAiConfig.OPENROUTER_MODEL_PRIMARY,
        pollinationsModelId = "openai-fast",
        defaultTemperature = 0.25f
    ),
    FLASH(
        id = "swiffle_flash",
        displayName = "Swiffle Flash",
        tagline = "Instant, lightweight replies at maximum speed",
        geminiModelId = SwiffleAiConfig.GEMINI_MODEL_FLASH_LITE,
        freeRpmLimit = 15,
        freeTpmLimit = 250_000,
        freeRpdLimit = 1_000,
        groqModelId = SwiffleAiConfig.GROQ_MODEL_PRIMARY,
        openRouterModelId = SwiffleAiConfig.OPENROUTER_MODEL_GEMMA,
        pollinationsModelId = "openai-fast",
        defaultTemperature = 0.20f
    ),
    ULTRA(
        id = "swiffle_ultra",
        displayName = "Swiffle Ultra",
        tagline = "Deep reasoning for complex analysis and synthesis",
        geminiModelId = SwiffleAiConfig.GEMINI_MODEL_PRO,
        freeRpmLimit = 5,
        freeTpmLimit = 250_000,
        freeRpdLimit = 25,
        groqModelId = SwiffleAiConfig.GROQ_MODEL_SUPREME,
        openRouterModelId = SwiffleAiConfig.OPENROUTER_MODEL_FALLBACK,
        pollinationsModelId = "openai-large",
        defaultTemperature = 0.15f
    );

    companion object {
        fun fromId(id: String?): SwiffleModelTier {
            return when (id) {
                "swiffle_1_omni" -> OMNI
                "swiffle_1_flash" -> FLASH
                "swiffle_1_pro" -> ULTRA
                else -> entries.firstOrNull { it.id == id } ?: OMNI
            }
        }

        fun fromGeminiModelId(modelId: String?): SwiffleModelTier? {
            return entries.firstOrNull { it.geminiModelId == modelId }
        }
    }
}
