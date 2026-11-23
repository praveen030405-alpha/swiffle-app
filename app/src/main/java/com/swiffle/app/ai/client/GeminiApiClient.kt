package com.swiffle.app.ai.client

import com.swiffle.app.ai.AiChatMessage
import com.swiffle.app.ai.SseThinkTagSplitter
import com.swiffle.app.ai.SwiffleAiConfig
import com.swiffle.app.ai.SwiffleAiException
import com.swiffle.app.ai.SwiffleAiStreamEvent
import com.swiffle.app.ai.SwiffleAiTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Streaming SSE Client for Google Gemini Free-Tier Models
 * (`gemini-2.5-flash`, `gemini-2.5-flash-lite`, `gemini-2.5-pro`).
 *
 * Automatically supports both:
 * - Vertex AI Express Mode keys (`AQ.*` -> `aiplatform.googleapis.com/v1/publishers/google/models/...:streamGenerateContent?alt=sse`)
 * - Google AI Studio keys (`AIza*` -> `generativelanguage.googleapis.com/v1beta/models/...:streamGenerateContent?alt=sse`)
 * with clean fallback across endpoints and exact `usageMetadata` extraction.
 */
class GeminiApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun resolveFreeModelForTask(task: SwiffleAiTask): String {
        return when (task) {
            SwiffleAiTask.Summarize -> SwiffleAiConfig.GEMINI_MODEL_FLASH
            SwiffleAiTask.ExtractTables -> SwiffleAiConfig.GEMINI_MODEL_FLASH
            SwiffleAiTask.Explain -> SwiffleAiConfig.GEMINI_MODEL_FLASH_LITE
            SwiffleAiTask.Translate -> SwiffleAiConfig.GEMINI_MODEL_FLASH_LITE
            SwiffleAiTask.AskQuestion -> SwiffleAiConfig.GEMINI_MODEL_FLASH
        }
    }

    fun streamChat(
        apiKey: String,
        model: String = SwiffleAiConfig.GEMINI_MODEL_FLASH,
        messages: List<AiChatMessage>,
        temperature: Float = 0.25f
    ): Flow<SwiffleAiStreamEvent> = flow {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            throw SwiffleAiException.InvalidApiKey("Google Gemini")
        }

        val startTimeMs = System.currentTimeMillis()
        val payloadJson = buildGeminiRequestJson(messages, temperature)

        val candidateEndpoints = if (trimmedKey.startsWith("AQ.")) {
            listOf(
                "${SwiffleAiConfig.GEMINI_VERTEX_EXPRESS_BASE_URL}/$model:streamGenerateContent?alt=sse",
                "${SwiffleAiConfig.GEMINI_STUDIO_BASE_URL}/$model:streamGenerateContent?alt=sse"
            )
        } else {
            listOf(
                "${SwiffleAiConfig.GEMINI_STUDIO_BASE_URL}/$model:streamGenerateContent?alt=sse",
                "${SwiffleAiConfig.GEMINI_VERTEX_EXPRESS_BASE_URL}/$model:streamGenerateContent?alt=sse"
            )
        }

        var activeResponse: Response? = null
        var lastErrorCode = 0
        var lastErrorBody = ""

        for ((index, url) in candidateEndpoints.withIndex()) {
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", trimmedKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(payloadJson.toRequestBody(jsonMediaType))
                .build()

            val resp = client.newCall(request).execute()
            if (resp.isSuccessful) {
                activeResponse = resp
                break
            }

            lastErrorCode = resp.code
            val retryAfterHeader = resp.header("retry-after")?.toLongOrNull()
            lastErrorBody = resp.body?.string().orEmpty()
            resp.close()

            if (lastErrorCode == 429) {
                val retrySec = retryAfterHeader ?: extractRetryDelaySeconds(lastErrorBody) ?: 60L
                throw SwiffleAiException.RateLimitExhausted("Google Gemini ($model)", retrySec)
            }

            // If key/endpoint mismatch (401/403/404) and another endpoint remains, try fallback endpoint
            val shouldTryNext = index == 0 && (lastErrorCode == 400 || lastErrorCode == 401 || lastErrorCode == 403 || lastErrorCode == 404)
            if (!shouldTryNext) {
                break
            }
        }

        val response = activeResponse
        if (response == null) {
            when (lastErrorCode) {
                401, 403 -> throw SwiffleAiException.InvalidApiKey("Google Gemini")
                404, 500, 503 -> throw SwiffleAiException.ModelUnavailable(model)
                else -> throw SwiffleAiException.Unknown(
                    parseGeminiErrorMessage(lastErrorBody).ifBlank {
                        "Gemini API error ($lastErrorCode). Please verify your API key."
                    }
                )
            }
        }

        try {
            val body = response.body ?: throw SwiffleAiException.StreamInterrupted("Empty response body from Gemini")
            val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
            emit(SwiffleAiStreamEvent.StatusUpdate("Connected to $model. Generating response."))

            val fullAccumulatedText = StringBuilder()
            var streamedTokenEvents = 0
            var reportedPromptTokens = 0
            var reportedOutputTokens = 0
            var reportedThoughtTokens = 0
            var reportedTotalTokens = 0

            val splitter = SseThinkTagSplitter()
            val pendingThinking = mutableListOf<String>()
            val pendingTokens = mutableListOf<String>()
            val sseDataBuffer = StringBuilder()

            fun processSseEventData(rawJson: String) {
                if (rawJson.isBlank() || rawJson == "[DONE]") return
                val obj = try {
                    JSONObject(rawJson)
                } catch (_: Exception) {
                    return
                }

                // Extract real token usageMetadata from Gemini SSE stream
                obj.optJSONObject("usageMetadata")?.let { usage ->
                    val p = usage.optInt("promptTokenCount", 0)
                    val c = usage.optInt("candidatesTokenCount", 0)
                    val th = usage.optInt("thoughtsTokenCount", 0)
                    val tot = usage.optInt("totalTokenCount", 0)
                    if (p > 0) reportedPromptTokens = p
                    if (c > 0) reportedOutputTokens = c
                    if (th > 0) reportedThoughtTokens = th
                    if (tot > 0) reportedTotalTokens = tot
                }

                val candidates = obj.optJSONArray("candidates") ?: return
                val firstCandidate = candidates.optJSONObject(0) ?: return
                val contentObj = firstCandidate.optJSONObject("content") ?: return
                val parts = contentObj.optJSONArray("parts") ?: return

                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val partText = part.optString("text", "")
                    if (partText.isEmpty()) continue

                    if (part.optBoolean("thought", false)) {
                        pendingThinking.add(partText)
                    } else {
                        splitter.processChunk(
                            chunk = partText,
                            onThinking = { pendingThinking.add(it) },
                            onToken = { pendingTokens.add(it) }
                        )
                    }
                }
            }

            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trimEnd()
                when {
                    trimmed.startsWith("data:") -> {
                        val slice = trimmed.substring(5).trimStart()
                        if (sseDataBuffer.isNotEmpty()) sseDataBuffer.append('\n')
                        sseDataBuffer.append(slice)
                    }
                    trimmed.isEmpty() -> {
                        if (sseDataBuffer.isNotEmpty()) {
                            pendingThinking.clear()
                            pendingTokens.clear()
                            processSseEventData(sseDataBuffer.toString())
                            sseDataBuffer.clear()

                            for (thought in pendingThinking) {
                                emit(SwiffleAiStreamEvent.Thinking(thought))
                            }
                            for (tok in pendingTokens) {
                                fullAccumulatedText.append(tok)
                                streamedTokenEvents++
                                emit(SwiffleAiStreamEvent.Token(tok))
                            }
                        }
                    }
                }
                line = reader.readLine()
            }

            if (sseDataBuffer.isNotEmpty()) {
                pendingThinking.clear()
                pendingTokens.clear()
                processSseEventData(sseDataBuffer.toString())
                sseDataBuffer.clear()
                for (thought in pendingThinking) {
                    emit(SwiffleAiStreamEvent.Thinking(thought))
                }
                for (tok in pendingTokens) {
                    fullAccumulatedText.append(tok)
                    streamedTokenEvents++
                    emit(SwiffleAiStreamEvent.Token(tok))
                }
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
                streamedTokenEvents++
                emit(SwiffleAiStreamEvent.Token(tok))
            }

            val finalText = fullAccumulatedText.toString()
            val fallbackOutTokens = if (reportedOutputTokens > 0) {
                reportedOutputTokens
            } else {
                (finalText.length / 4).coerceAtLeast(streamedTokenEvents)
            }
            val finalTotalTokens = if (reportedTotalTokens > 0) {
                reportedTotalTokens
            } else {
                reportedPromptTokens + fallbackOutTokens + reportedThoughtTokens
            }

            val elapsedMs = System.currentTimeMillis() - startTimeMs
            emit(
                SwiffleAiStreamEvent.Completed(
                    fullText = finalText,
                    engineUsed = "Google Gemini ($model)",
                    latencyMs = elapsedMs,
                    totalTokens = finalTotalTokens,
                    promptTokens = reportedPromptTokens,
                    outputTokens = fallbackOutTokens,
                    thoughtTokens = reportedThoughtTokens,
                    modelId = model
                )
            )
        } finally {
            try {
                response.close()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildGeminiRequestJson(
        messages: List<AiChatMessage>,
        temperature: Float
    ): String {
        val systemText = messages
            .filter { it.role == "system" && it.content.isNotBlank() }
            .joinToString("\n\n") { it.content }

        val contentsArray = JSONArray()
        messages.filter { it.role != "system" && it.content.isNotBlank() }.forEach { msg ->
            val geminiRole = if (msg.role == "assistant" || msg.role == "model") "model" else "user"
            val parts = JSONArray().put(JSONObject().put("text", msg.content))
            contentsArray.put(
                JSONObject()
                    .put("role", geminiRole)
                    .put("parts", parts)
            )
        }

        if (contentsArray.length() == 0) {
            contentsArray.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", "Hello")))
            )
        }

        val root = JSONObject()
        if (systemText.isNotBlank()) {
            root.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText)))
            )
        }
        root.put("contents", contentsArray)
        root.put(
            "generationConfig",
            JSONObject().put("temperature", temperature.toDouble())
        )
        return root.toString()
    }

    private fun extractRetryDelaySeconds(errorBody: String): Long? {
        val match = Regex("retryDelay\"?\\s*[:=]\\s*\"?(\\d+)").find(errorBody)
            ?: Regex("retry in ([0-9]+)", RegexOption.IGNORE_CASE).find(errorBody)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun parseGeminiErrorMessage(errorBody: String): String {
        return try {
            val root = JSONObject(errorBody)
            root.optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
