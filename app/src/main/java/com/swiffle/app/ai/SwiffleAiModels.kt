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
data class StreamChunkResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("choices") val choices: List<StreamChoice> = emptyList()
)

// ============================================================================
// 2. Swiffle AI Stream Events (Reactive UI Flow)
// ============================================================================

sealed interface SwiffleAiStreamEvent {
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
        val totalTokens: Int = 0
    ) : SwiffleAiStreamEvent

    /** Emitted when the user is offline and has not yet downloaded the model */
    data object OfflineModelRequired : SwiffleAiStreamEvent

    /** Error state with user-facing recovery instructions */
    data class Error(val error: SwiffleAiException) : SwiffleAiStreamEvent
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
