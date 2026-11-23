package com.swiffle.app.ai.client

import com.swiffle.app.ai.AiChatMessage
import com.swiffle.app.ai.AiChatRequest
import com.swiffle.app.ai.SseThinkTagSplitter
import com.swiffle.app.ai.StreamChunkResponse
import com.swiffle.app.ai.SwiffleAiException
import com.swiffle.app.ai.SwiffleAiStreamEvent
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
 * Free Enterprise-grade Streaming LLM Client powered by Pollinations AI.
 * Delivers unauthenticated, full-capacity streaming inference for Swiffle AI.
 * Provides high-reasoning document analysis, quizzes, structured tables, and natural conversation.
 */
class PollinationsApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val endpoint = "https://text.pollinations.ai/"

    /**
     * Executes real-time SSE streaming chat inference.
     */
    fun streamChat(
        messages: List<AiChatMessage>,
        model: String = "openai-fast",
        temperature: Float = 0.3f
    ): Flow<SwiffleAiStreamEvent> = flow {
        val startTimeMs = System.currentTimeMillis()
        val requestBody = AiChatRequest(
            model = model,
            messages = messages,
            stream = true,
            temperature = temperature
        )

        val jsonString = json.encodeToString(AiChatRequest.serializer(), requestBody)

        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("User-Agent", "Swiffle-PDF-Intelligence/2.0 (Android)")
            .post(jsonString.toRequestBody(jsonMediaType))
            .build()

        var response: Response? = null
        try {
            response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorCode = response.code
                val errorBody = response.body?.string() ?: ""
                throw SwiffleAiException.Unknown("Pollinations API error ($errorCode): $errorBody")
            }

            val body = response.body ?: throw SwiffleAiException.StreamInterrupted("Empty response body")
            val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
            emit(SwiffleAiStreamEvent.StatusUpdate("Stream connected. Waiting for the model to begin responding."))

            val fullAccumulatedText = StringBuilder()
            var tokenCount = 0
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
                            val choice = chunk.choices.firstOrNull()
                            val delta = choice?.delta

                            // Reasoning / Thinking tokens from API delta reasoning field
                            val reasoning = delta?.reasoningContent?.ifBlank { null } ?: delta?.reasoning?.ifBlank { null }
                            if (!reasoning.isNullOrEmpty()) {
                                emit(SwiffleAiStreamEvent.Thinking(reasoning))
                            }

                            // Standard content tokens - cleanly separate <think>...</think> across chunk boundaries
                            val content = delta?.content
                            if (!content.isNullOrEmpty()) {
                                pendingThinking.clear()
                                pendingTokens.clear()
                                splitter.processChunk(
                                    chunk = content,
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
                            // Non-fatal parse issue on raw ping chunk
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
            emit(
                SwiffleAiStreamEvent.Completed(
                    fullText = fullAccumulatedText.toString(),
                    engineUsed = "Swiffle Live Cloud LLM ($model)",
                    latencyMs = elapsedMs,
                    totalTokens = tokenCount
                )
            )

        } catch (ex: Exception) {
            emit(SwiffleAiStreamEvent.Error(SwiffleAiException.Unknown(ex.localizedMessage ?: "Pollinations error")))
            throw ex
        } finally {
            response?.close()
        }
    }.flowOn(Dispatchers.IO)
}
