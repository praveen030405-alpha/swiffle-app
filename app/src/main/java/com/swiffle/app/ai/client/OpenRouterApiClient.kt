package com.swiffle.app.ai.client

import com.swiffle.app.ai.AiChatMessage
import com.swiffle.app.ai.AiChatRequest
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
 * Enterprise Sentinel Failover Client for OpenRouter.
 * Secondary resilience engine for Swiffle AI.
 *
 * Automatically steps in when Groq hits a rate limit or service interruption.
 * Targets OpenRouter's high-capacity `:free` model pool, providing continuous,
 * uninterrupted document synthesis for $0.00.
 */
class OpenRouterApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
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
     * Resolves the optimal free model on OpenRouter based on task type.
     */
    fun resolveFreeModelForTask(task: SwiffleAiTask): String {
        return when (task) {
            SwiffleAiTask.Summarize -> SwiffleAiConfig.OPENROUTER_MODEL_PRIMARY  // meta-llama/llama-3.3-70b-instruct:free
            SwiffleAiTask.ExtractTables -> SwiffleAiConfig.OPENROUTER_MODEL_FALLBACK // qwen/qwen-2.5-72b-instruct:free
            SwiffleAiTask.Explain -> SwiffleAiConfig.OPENROUTER_MODEL_PRIMARY
            SwiffleAiTask.Translate -> SwiffleAiConfig.OPENROUTER_MODEL_FALLBACK
            SwiffleAiTask.AskQuestion -> SwiffleAiConfig.OPENROUTER_MODEL_PRIMARY
        }
    }

    /**
     * Executes a streaming inference request against the OpenRouter API.
     *
     * @param apiKey User or system OpenRouter API key (starts with "sk-or-")
     * @param model OpenRouter model identifier (default: meta-llama/llama-3.3-70b-instruct:free)
     * @param messages Formatted conversation history with system prompt & document context
     * @param temperature Sampling temperature (0.0 to 1.0)
     */
    fun streamChat(
        apiKey: String,
        model: String = SwiffleAiConfig.OPENROUTER_MODEL_PRIMARY,
        messages: List<AiChatMessage>,
        temperature: Float = 0.2f
    ): Flow<SwiffleAiStreamEvent> = flow {
        if (apiKey.isBlank()) {
            throw SwiffleAiException.InvalidApiKey("OpenRouter")
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
            .url(SwiffleAiConfig.OPENROUTER_ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            // OpenRouter best practices for attribution and rate limit prioritization
            .addHeader("HTTP-Referer", "https://swiffle.app")
            .addHeader("X-Title", "Swiffle PDF Android")
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
                    throw SwiffleAiException.RateLimitExhausted("OpenRouter", retryAfterSec)
                } else if (errorCode == 401 || errorCode == 403) {
                    throw SwiffleAiException.InvalidApiKey("OpenRouter")
                } else if (errorCode == 503 || errorCode == 500) {
                    throw SwiffleAiException.ModelUnavailable(model)
                } else {
                    throw SwiffleAiException.Unknown("OpenRouter API error ($errorCode): $errorBody")
                }
            }

            val body = response.body ?: throw SwiffleAiException.StreamInterrupted("Empty response body from OpenRouter")
            val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
            val fullAccumulatedText = StringBuilder()
            var tokenCount = 0

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
                            val choice = chunk.choices.firstOrNull()
                            val delta = choice?.delta

                            // Check for reasoning content
                            val reasoning = delta?.reasoningContent
                            if (!reasoning.isNullOrEmpty()) {
                                emit(SwiffleAiStreamEvent.Thinking(reasoning))
                            }

                            // Check for standard text content
                            val token = delta?.content
                            if (!token.isNullOrEmpty()) {
                                fullAccumulatedText.append(token)
                                tokenCount++
                                emit(SwiffleAiStreamEvent.Token(token))
                            }
                        } catch (_: Exception) {
                            // Non-fatal: bypass malformed SSE frames
                        }
                    }
                }
                line = reader.readLine()
            }

            val elapsedMs = System.currentTimeMillis() - startTimeMs
            emit(
                SwiffleAiStreamEvent.Completed(
                    fullText = fullAccumulatedText.toString(),
                    engineUsed = "OpenRouter ($model)",
                    latencyMs = elapsedMs,
                    totalTokens = tokenCount
                )
            )

        } catch (e: SwiffleAiException) {
            throw e
        } catch (ioe: java.io.IOException) {
            throw SwiffleAiException.StreamInterrupted("OpenRouter connection dropped: ${ioe.localizedMessage}")
        } catch (general: Exception) {
            throw SwiffleAiException.Unknown("Unexpected OpenRouter streaming error: ${general.localizedMessage}", general)
        } finally {
            response?.close()
        }
    }.flowOn(Dispatchers.IO)
}
