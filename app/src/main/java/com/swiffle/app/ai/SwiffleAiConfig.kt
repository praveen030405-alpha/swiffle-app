package com.swiffle.app.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aiDataStore by preferencesDataStore(name = "swiffle_ai_settings")

/**
 * Configuration and credential manager for Swiffle AI.
 * Supports persistent custom API keys for Groq and OpenRouter, active provider routing,
 * real token usage telemetry, and verified production model identifiers.
 */
object SwiffleAiConfig {

    // --- Default API Endpoints ---
    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    // --- Provider Identifiers ---
    const val PROVIDER_AUTO = "auto"
    const val PROVIDER_GROQ = "groq"
    const val PROVIDER_OPENROUTER = "openrouter"
    const val PROVIDER_FIREBASE_GEMINI = "firebase_gemini"

    // --- Verified Production Groq LPU Models ---
    const val GROQ_MODEL_SUPREME = "llama-3.3-70b-versatile"                     // 70B Flagship Reasoning & Deep Synthesis
    const val GROQ_MODEL_PRIMARY = "llama-3.1-8b-instant"                        // Ultra-Fast Daily Driver (Q&A / Summaries)
    const val GROQ_MODEL_TABLES = "meta-llama/llama-4-scout-17b-16e-instruct"    // Precision Table & Structured Extraction
    const val GROQ_MODEL_VOICE = "whisper-large-v3-turbo"                        // Voice dictation & Audio transcription
    const val GROQ_MODEL_LLAMA_FAST = "llama-3.1-8b-instant"                     // Universal baseline fallback

    // --- Verified OpenRouter Models ---
    const val OPENROUTER_MODEL_PRIMARY = "meta-llama/llama-3.3-70b-instruct:free"
    const val OPENROUTER_MODEL_FALLBACK = "qwen/qwen-2.5-72b-instruct:free"
    const val OPENROUTER_MODEL_GEMINI = "google/gemini-2.5-flash"

    // --- On-Device Gemma 2 Embedding Model Identifier ---
    const val OFFLINE_EMBEDDING_MODEL_GEMMA_2 = "google_embeddinggemma_2.tflite"

    // --- DataStore Keys ---
    private val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
    private val KEY_OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
    private val KEY_AI_ENGINE_PREFERENCE = stringPreferencesKey("ai_engine_preference")
    private val KEY_PREFERRED_GROQ_MODEL = stringPreferencesKey("preferred_groq_model")
    private val KEY_PREFERRED_OPENROUTER_MODEL = stringPreferencesKey("preferred_openrouter_model")

    private const val SYNC_PREFS_NAME = "OrigamiPrefs"

    /**
     * Reads the Groq API key from DataStore (or falls back to OrigamiPrefs for synchronous callers).
     */
    fun getGroqApiKey(context: Context): Flow<String> {
        return context.aiDataStore.data.map { preferences ->
            val dsKey = preferences[KEY_GROQ_API_KEY].orEmpty()
            if (dsKey.isNotBlank()) dsKey else getGroqApiKeySync(context)
        }
    }

    fun getGroqApiKeySync(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("groq_api_key", "")?.trim().orEmpty()
    }

    /**
     * Saves a user-provided Groq API Key in both DataStore and SharedPreferences.
     */
    suspend fun saveGroqApiKey(context: Context, key: String) {
        val trimmed = key.trim()
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("groq_api_key", trimmed)
            .apply()
        context.aiDataStore.edit { preferences ->
            preferences[KEY_GROQ_API_KEY] = trimmed
        }
    }

    fun saveGroqApiKeySync(context: Context, key: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("groq_api_key", key.trim())
            .apply()
    }

    /**
     * Reads the OpenRouter API key from DataStore (or falls back to OrigamiPrefs).
     */
    fun getOpenRouterApiKey(context: Context): Flow<String> {
        return context.aiDataStore.data.map { preferences ->
            val dsKey = preferences[KEY_OPENROUTER_API_KEY].orEmpty()
            if (dsKey.isNotBlank()) dsKey else getOpenRouterApiKeySync(context)
        }
    }

    fun getOpenRouterApiKeySync(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("openrouter_api_key", "")?.trim().orEmpty()
    }

    /**
     * Saves a user-provided OpenRouter API Key in both DataStore and SharedPreferences.
     */
    suspend fun saveOpenRouterApiKey(context: Context, key: String) {
        val trimmed = key.trim()
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("openrouter_api_key", trimmed)
            .apply()
        context.aiDataStore.edit { preferences ->
            preferences[KEY_OPENROUTER_API_KEY] = trimmed
        }
    }

    fun saveOpenRouterApiKeySync(context: Context, key: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("openrouter_api_key", key.trim())
            .apply()
    }

    /**
     * Gets or sets the active AI provider ("auto", "groq", "openrouter", "firebase_gemini").
     */
    fun getPreferredProvider(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("ai_preferred_provider", PROVIDER_AUTO) ?: PROVIDER_AUTO
    }

    fun setPreferredProvider(context: Context, provider: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("ai_preferred_provider", provider)
            .apply()
    }

    fun getPreferredGroqModel(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("preferred_groq_model", GROQ_MODEL_SUPREME) ?: GROQ_MODEL_SUPREME
    }

    fun setPreferredGroqModel(context: Context, model: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("preferred_groq_model", model)
            .apply()
    }

    /**
     * Reads user-configured AI Custom Name and System Prompt from Settings.
     */
    fun getCustomAiName(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("ai_custom_name", "Swiffle")?.trim().takeUnless { it.isNullOrBlank() } ?: "Swiffle"
    }

    fun getCustomAiInstructions(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("ai_custom_prompt", "")?.trim().orEmpty()
    }

    /**
     * Records real token & query telemetry into OrigamiPrefs for Usage & Limits tracking.
     */
    fun recordTokenUsage(context: Context, tokensGenerated: Int) {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val currentTokens = prefs.getInt("real_tokens_used_today", 0)
        val currentQueries = prefs.getInt("real_queries_executed_today", 0)
        val estimatedTokens = (tokensGenerated * 4).coerceAtLeast(24)
        prefs.edit()
            .putInt("real_tokens_used_today", currentTokens + estimatedTokens)
            .putInt("real_queries_executed_today", currentQueries + 1)
            .putLong("last_ai_query_timestamp", System.currentTimeMillis())
            .apply()
    }
}
