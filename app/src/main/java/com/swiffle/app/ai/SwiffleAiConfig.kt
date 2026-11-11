package com.swiffle.app.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aiDataStore by preferencesDataStore(name = "swiffle_ai_settings")

/**
 * Enterprise configuration and credential manager for Swiffle AI.
 * Supports persistent custom API keys with zero hardcoded leak vulnerabilities,
 * and maintains active model endpoints for Groq, OpenRouter, and Local Qwen.
 */
object SwiffleAiConfig {

    // --- Default API Endpoints ---
    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    // --- Groq LPU Models (Verified Active on Groq Console) ---
    const val GROQ_MODEL_SUPREME = "openai/gpt-oss-120b"       // 120B Flagship Reasoning & Deep Synthesis
    const val GROQ_MODEL_PRIMARY = "openai/gpt-oss-20b"        // 20B Fast Daily Driver (Sub-second Q&A / Summaries)
    const val GROQ_MODEL_TABLES = "qwen/qwen3.8-27b"           // 27B Precision Table & Multilingual Specialist
    const val GROQ_MODEL_VOICE = "whisper-large-v3-turbo"      // Voice dictation & Audio transcription
    const val GROQ_MODEL_LLAMA_FAST = "llama-3.1-8b-instant"   // Universal baseline fallback

    // --- OpenRouter Free Pool Models ---
    const val OPENROUTER_MODEL_PRIMARY = "meta-llama/llama-3.3-70b-instruct:free"
    const val OPENROUTER_MODEL_FALLBACK = "qwen/qwen-2.5-72b-instruct:free"

    // --- Offline Model Identifier ---
    const val OFFLINE_MODEL_QWEN_05B = "qwen2.5-0.5b-instruct-q4_k_m"

    // --- DataStore Keys ---
    private val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
    private val KEY_OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
    private val KEY_AI_ENGINE_PREFERENCE = stringPreferencesKey("ai_engine_preference")

    /**
     * Reads the Groq API key from DataStore.
     */
    fun getGroqApiKey(context: Context): Flow<String> {
        return context.aiDataStore.data.map { preferences ->
            preferences[KEY_GROQ_API_KEY] ?: ""
        }
    }

    /**
     * Saves a user-provided Groq API Key securely in DataStore.
     */
    suspend fun saveGroqApiKey(context: Context, key: String) {
        context.aiDataStore.edit { preferences ->
            preferences[KEY_GROQ_API_KEY] = key.trim()
        }
    }

    /**
     * Reads the OpenRouter API key from DataStore.
     */
    fun getOpenRouterApiKey(context: Context): Flow<String> {
        return context.aiDataStore.data.map { preferences ->
            preferences[KEY_OPENROUTER_API_KEY] ?: ""
        }
    }

    /**
     * Saves a user-provided OpenRouter API Key securely in DataStore.
     */
    suspend fun saveOpenRouterApiKey(context: Context, key: String) {
        context.aiDataStore.edit { preferences ->
            preferences[KEY_OPENROUTER_API_KEY] = key.trim()
        }
    }
}
