package com.swiffle.app.ai.client

import com.swiffle.app.ai.AiChatMessage
import com.swiffle.app.ai.AiChatRequest
import com.swiffle.app.ai.SseThinkTagSplitter
import com.swiffle.app.ai.StreamChunkResponse
import com.swiffle.app.ai.SwiffleAiConfig
import com.swiffle.app.ai.SwiffleAiException
import com.swiffle.app.ai.SwiffleAiStreamEvent
import com.swiffle.app.ai.SwiffleAiTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Enterprise SSE streaming client for Groq Cloud LPUs.
 * Primary Lightning Client for Swiffle AI.
 * Delivers up to 500 tokens/second with sub-second time-to-first-token (TTFT).
 * Accurately parses Server-Sent Events (SSE), detects 429 rate limits,
 * handles task-specific model routing (120B for deep synthesis, 27B for tables, 20B for fast Q&A),
 * and emits real-time SwiffleAiStreamEvents without UI thread blocking.
 */
class GroqApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Resolves the optimal Groq model according to the requested Swiffle AI task.
     */
    fun resolveModelForTask(task: SwiffleAiTask): String {
        return when (task) {
            SwiffleAiTask.Summarize -> SwiffleAiConfig.GROQ_MODEL_SUPREME // openai/gpt-oss-120b for master synthesis
            SwiffleAiTask.ExtractTables -> SwiffleAiConfig.GROQ_MODEL_TABLES // qwen/qwen3.8-27b for precision markdown tables
            SwiffleAiTask.Explain -> SwiffleAiConfig.GROQ_MODEL_PRIMARY  // openai/gpt-oss-20b for instant ELI5
            SwiffleAiTask.Translate -> SwiffleAiConfig.GROQ_MODEL_TABLES  // qwen/qwen3.8-27b for multilingual fidelity
            SwiffleAiTask.AskQuestion -> SwiffleAiConfig.GROQ_MODEL_PRIMARY // openai/gpt-oss-20b for rapid grounded Q&A
        }
    }

    /**
     * Executes a streaming inference request against the Groq Cloud API.
     *
     * @param apiKey User or system Groq API key (starts with "gsk_")
     * @param model Groq model ID (default: resolves to optimal model based on task)
     * @param messages Formatted conversation history with system prompt & document context
     * @param temperature Sampling temperature (0.0 to 1.0)
     */
    fun streamChat(
        apiKey: String,
        model: String = SwiffleAiConfig.GROQ_MODEL_PRIMARY,
        messages: List<AiChatMessage>,
        temperature: Float = 0.2f
    ): Flow<SwiffleAiStreamEvent> = flow {
        if (apiKey.isBlank()) {
            throw SwiffleAiException.InvalidApiKey("Groq")
        }

        val startTimeMs = System.currentTimeMillis()
        val requestBody = AiChatRequest(
            model = model,
            messages = messages,
            stream = true,
            temperature = temperature
        )

        val jsonString = json.encodeToString(AiChatRequest.serializer(), requestBody)

        val httpRequest = Request.Builder()
            .url(SwiffleAiConfig.GROQ_ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(jsonString.toRequestBody(jsonMediaType))
            .build()

        var response: Response? = null
        try {
            response = client.newCall(httpRequest).execute()

            // Handle HTTP error codes
            if (!response.isSuccessful) {
                val errorCode = response.code
                val errorBody = response.body?.string() ?: ""

                if (errorCode == 429) {
                    val retryAfterSec = response.header("retry-after")?.toLongOrNull() ?: 60L
                    throw SwiffleAiException.RateLimitExhausted("Groq", retryAfterSec)
                } else if (errorCode == 401 || errorCode == 403) {
                    throw SwiffleAiException.InvalidApiKey("Groq")
                } else if (errorCode == 503 || errorCode == 500) {
                    throw SwiffleAiException.ModelUnavailable(model)
                } else {
                    throw SwiffleAiException.Unknown("Groq API error ($errorCode): $errorBody")
                }
            }

            val body = response.body ?: throw SwiffleAiException.StreamInterrupted("Empty response body from Groq")
            val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
            emit(SwiffleAiStreamEvent.StatusUpdate("Stream connected. Waiting for the model to begin responding."))

            val fullAccumulatedText = StringBuilder()
            var tokenCount = 0
            var reportedPromptTokens = 0
            var reportedCompletionTokens = 0
            var reportedTotalTokens = 0
            val splitter = SseThinkTagSplitter()
            val pendingThinking = mutableListOf<String>()
            val pendingTokens = mutableListOf<String>()

            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("data:")) {
                    val dataContent = trimmed.substring(5).trim()

                    if (dataContent == "[DONE]") {
                        break
                    }

                    if (dataContent.isNotEmpty()) {
                        try {
                            val chunk = json.decodeFromString(StreamChunkResponse.serializer(), dataContent)
                            chunk.usage?.let { u ->
                                if (u.promptTokens > 0) reportedPromptTokens = u.promptTokens
                                if (u.completionTokens > 0) reportedCompletionTokens = u.completionTokens
                                if (u.totalTokens > 0) reportedTotalTokens = u.totalTokens
                            }
                            val choice = chunk.choices.firstOrNull()
                            val delta = choice?.delta

                            // Check for reasoning content (thinking models)
                            val reasoning = delta?.reasoningContent?.ifBlank { null } ?: delta?.reasoning?.ifBlank { null }
                            if (!reasoning.isNullOrEmpty()) {
                                emit(SwiffleAiStreamEvent.Thinking(reasoning))
                            }

                            // Check for standard text content (with <think> tag separation)
                            val tokenChunk = delta?.content
                            if (!tokenChunk.isNullOrEmpty()) {
                                pendingThinking.clear()
                                pendingTokens.clear()
                                splitter.processChunk(
                                    chunk = tokenChunk,
                                    onThinking = { pendingThinking.add(it) },
                                    onToken = { pendingTokens.add(it) }
                                )
                                for (thought in pendingThinking) {
                                    emit(SwiffleAiStreamEvent.Thinking(thought))
                                }
                                for (tok in pendingTokens) {
                                    fullAccumulatedText.append(tok)
                                    tokenCount++
                                    emit(SwiffleAiStreamEvent.Token(tok))
                                }
                            }
                        } catch (_: Exception) {
                            // Non-fatal: bypass malformed SSE frames
                        }
                    }
                }
                line = reader.readLine()
            }

            pendingThinking.clear()
            pendingTokens.clear()
            splitter.flush(
                onThinking = { pendingThinking.add(it) },
                onToken = { pendingTokens.add(it) }
            )
            for (thought in pendingThinking) {
                emit(SwiffleAiStreamEvent.Thinking(thought))
            }
            for (tok in pendingTokens) {
                fullAccumulatedText.append(tok)
                tokenCount++
                emit(SwiffleAiStreamEvent.Token(tok))
            }

            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val estimatedPrompt = (messages.sumOf { it.content.length } / 4).coerceAtLeast(1)
            val estimatedOutput = (fullAccumulatedText.length / 4).coerceAtLeast(tokenCount)
            val finalPrompt = if (reportedPromptTokens > 0) reportedPromptTokens else estimatedPrompt
            val finalOutput = if (reportedCompletionTokens > 0) reportedCompletionTokens else estimatedOutput
            val finalTotal = if (reportedTotalTokens > 0) reportedTotalTokens else (finalPrompt + finalOutput)
            emit(
                SwiffleAiStreamEvent.Completed(
                    fullText = fullAccumulatedText.toString(),
                    engineUsed = "Groq ($model)",
                    latencyMs = elapsedMs,
                    totalTokens = finalTotal,
                    promptTokens = finalPrompt,
                    outputTokens = finalOutput,
                    modelId = model
                )
            )

        } catch (e: SwiffleAiException) {
            throw e
        } catch (ioe: java.io.IOException) {
            throw SwiffleAiException.StreamInterrupted("Groq network connection dropped: ${ioe.localizedMessage}")
        } catch (general: Exception) {
            throw SwiffleAiException.Unknown("Unexpected Groq streaming error: ${general.localizedMessage}", general)
        } finally {
            response?.close()
        }
    }.flowOn(Dispatchers.IO)
}
