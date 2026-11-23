package com.swiffle.app.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swiffle.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.aiDataStore by preferencesDataStore(name = "swiffle_ai_settings")

data class GeminiModelQuotaSpec(
    val modelId: String,
    val displayName: String,
    val tierLabel: String,
    val rpmLimit: Int,
    val tpmLimit: Int,
    val rpdLimit: Int
)

data class ModelUsageStats(
    val modelId: String,
    val displayName: String,
    val tierLabel: String,
    val requestsLastMinute: Int,
    val tokensLastMinute: Int,
    val requestsToday: Int,
    val tokensToday: Int,
    val rpmLimit: Int,
    val tpmLimit: Int,
    val rpdLimit: Int,
    val cooldownRemainingSec: Int
)

data class UsageRateSnapshot(
    val activeProviderLabel: String,
    val activeModelId: String,
    val activeModelDisplayName: String,
    val hasGeminiKey: Boolean,
    val keySourceLabel: String,
    val currentRpm: Int,
    val rpmLimit: Int,
    val currentTpm: Int,
    val tpmLimit: Int,
    val currentRpd: Int,
    val rpdLimit: Int,
    val promptTokensToday: Int,
    val outputTokensToday: Int,
    val thoughtTokensToday: Int,
    val totalTokensToday: Int,
    val dailyTokenQuota: Int,
    val queriesToday: Int,
    val lastLatencyMs: Long,
    val lastQueryTimestampMs: Long,
    val lastRateLimitMessage: String?,
    val cooldownRemainingSec: Int,
    val perModelStats: List<ModelUsageStats>
)

/**
 * Configuration, credential manager, and live rate/usage/limit telemetry for Swiffle AI.
 * Supports Gemini API Free Tier models via BuildConfig.GEMINI_API_KEY (AI Studio Secrets panel)
 * or optional Settings credentials, plus Groq and OpenRouter failover.
 */
object SwiffleAiConfig {

    // --- Default API Endpoints ---
    const val GEMINI_STUDIO_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    const val GEMINI_VERTEX_EXPRESS_BASE_URL = "https://aiplatform.googleapis.com/v1/publishers/google/models"
    const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    // --- Provider Identifiers ---
    const val PROVIDER_AUTO = "auto"
    const val PROVIDER_GEMINI = "gemini"
    const val PROVIDER_GROQ = "groq"
    const val PROVIDER_OPENROUTER = "openrouter"
    const val PROVIDER_FIREBASE_GEMINI = "firebase_gemini"

    // --- Verified Google Gemini Free Tier Models ---
    const val GEMINI_MODEL_FLASH = "gemini-2.5-flash"
    const val GEMINI_MODEL_FLASH_LITE = "gemini-2.5-flash-lite"
    const val GEMINI_MODEL_PRO = "gemini-2.5-pro"

    // --- Verified Production Groq LPU Models ---
    const val GROQ_MODEL_SUPREME = "llama-3.3-70b-versatile"                     // 70B Flagship Reasoning & Deep Synthesis
    const val GROQ_MODEL_PRIMARY = "llama-3.1-8b-instant"                        // Ultra-Fast Daily Driver (Q&A / Summaries)
    const val GROQ_MODEL_TABLES = "meta-llama/llama-4-scout-17b-16e-instruct"    // Precision Table & Structured Extraction
    const val GROQ_MODEL_QWEN = "qwen/qwen3-32b"                                 // High-RPM Reasoning Fallback
    const val GROQ_MODEL_VOICE = "whisper-large-v3-turbo"                        // Voice dictation & Audio transcription
    const val GROQ_MODEL_LLAMA_FAST = "llama-3.1-8b-instant"                     // Universal baseline fallback

    // --- Verified OpenRouter Free-Tier Models ---
    const val OPENROUTER_MODEL_PRIMARY = "meta-llama/llama-3.3-70b-instruct:free"
    const val OPENROUTER_MODEL_FALLBACK = "qwen/qwen-2.5-72b-instruct:free"
    const val OPENROUTER_MODEL_GEMMA = "google/gemma-3-27b-it:free"
    const val OPENROUTER_MODEL_MISTRAL = "mistralai/mistral-small-3.1-24b-instruct:free"
    const val OPENROUTER_MODEL_AUTO_FREE = "openrouter/free"
    const val OPENROUTER_MODEL_GEMINI = "google/gemini-2.5-flash"

    // --- On-Device Gemma 2 Embedding Model Identifier ---
    const val OFFLINE_EMBEDDING_MODEL_GEMMA_2 = "google_embeddinggemma_2.tflite"

    private const val DEFAULT_PLACEHOLDER_GEMINI_KEY = "GEMINI_API_KEY_DEFAULT_VALUE"
    private const val DEFAULT_PLACEHOLDER_GROQ_KEY = "GROQ_API_KEY_DEFAULT_VALUE"
    private const val DEFAULT_PLACEHOLDER_OPENROUTER_KEY = "OPENROUTER_API_KEY_DEFAULT_VALUE"
    const val DAILY_TOKEN_QUOTA_DEFAULT = 250_000

    val GROQ_FREE_MODELS: List<GeminiModelQuotaSpec> = listOf(
        GeminiModelQuotaSpec(
            modelId = GROQ_MODEL_TABLES,
            displayName = "Groq Llama 4 Scout 17B",
            tierLabel = "Groq Free · 30 RPM",
            rpmLimit = 30,
            tpmLimit = 30_000,
            rpdLimit = 1_000
        ),
        GeminiModelQuotaSpec(
            modelId = GROQ_MODEL_PRIMARY,
            displayName = "Groq Llama 3.1 8B Instant",
            tierLabel = "Groq Free · 14.4K RPD",
            rpmLimit = 30,
            tpmLimit = 6_000,
            rpdLimit = 14_400
        ),
        GeminiModelQuotaSpec(
            modelId = GROQ_MODEL_SUPREME,
            displayName = "Groq Llama 3.3 70B",
            tierLabel = "Groq Free · Flagship",
            rpmLimit = 30,
            tpmLimit = 12_000,
            rpdLimit = 1_000
        ),
        GeminiModelQuotaSpec(
            modelId = GROQ_MODEL_QWEN,
            displayName = "Groq Qwen 3 32B",
            tierLabel = "Groq Free · 60 RPM",
            rpmLimit = 60,
            tpmLimit = 6_000,
            rpdLimit = 1_000
        )
    )

    val OPENROUTER_FREE_MODELS: List<GeminiModelQuotaSpec> = listOf(
        GeminiModelQuotaSpec(
            modelId = OPENROUTER_MODEL_PRIMARY,
            displayName = "OpenRouter Llama 3.3 70B",
            tierLabel = "OpenRouter Free",
            rpmLimit = 20,
            tpmLimit = 100_000,
            rpdLimit = 50
        ),
        GeminiModelQuotaSpec(
            modelId = OPENROUTER_MODEL_FALLBACK,
            displayName = "OpenRouter Qwen 2.5 72B",
            tierLabel = "OpenRouter Free",
            rpmLimit = 20,
            tpmLimit = 100_000,
            rpdLimit = 50
        ),
        GeminiModelQuotaSpec(
            modelId = OPENROUTER_MODEL_GEMMA,
            displayName = "OpenRouter Gemma 3 27B",
            tierLabel = "OpenRouter Free",
            rpmLimit = 20,
            tpmLimit = 100_000,
            rpdLimit = 50
        ),
        GeminiModelQuotaSpec(
            modelId = OPENROUTER_MODEL_MISTRAL,
            displayName = "OpenRouter Mistral Small 24B",
            tierLabel = "OpenRouter Free",
            rpmLimit = 20,
            tpmLimit = 100_000,
            rpdLimit = 50
        ),
        GeminiModelQuotaSpec(
            modelId = OPENROUTER_MODEL_AUTO_FREE,
            displayName = "OpenRouter Free Router",
            tierLabel = "OpenRouter Free Pool",
            rpmLimit = 20,
            tpmLimit = 100_000,
            rpdLimit = 50
        )
    )

    val GEMINI_FREE_MODELS: List<GeminiModelQuotaSpec> = listOf(
        GeminiModelQuotaSpec(
            modelId = GEMINI_MODEL_FLASH,
            displayName = "Gemini 2.5 Flash",
            tierLabel = "Gemini Free · Balanced",
            rpmLimit = 10,
            tpmLimit = 250_000,
            rpdLimit = 250
        ),
        GeminiModelQuotaSpec(
            modelId = GEMINI_MODEL_FLASH_LITE,
            displayName = "Gemini 2.5 Flash-Lite",
            tierLabel = "Gemini Free · High Throughput",
            rpmLimit = 15,
            tpmLimit = 250_000,
            rpdLimit = 1_000
        ),
        GeminiModelQuotaSpec(
            modelId = GEMINI_MODEL_PRO,
            displayName = "Gemini 2.5 Pro",
            tierLabel = "Gemini Free · Deep Reasoning",
            rpmLimit = 5,
            tpmLimit = 250_000,
            rpdLimit = 25
        )
    )

    val ALL_FREE_MODELS: List<GeminiModelQuotaSpec> =
        GROQ_FREE_MODELS + OPENROUTER_FREE_MODELS + GEMINI_FREE_MODELS

    // --- DataStore Keys ---
    private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    private val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
    private val KEY_OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")

    const val SYNC_PREFS_NAME = "OrigamiPrefs"
    private const val KEY_MINUTE_WINDOW_EVENTS = "ai_minute_window_events_v1"
    private const val KEY_USAGE_DATE = "ai_usage_date_v1"
    private const val KEY_RATE_LIMIT_UNTIL_MS = "ai_rate_limit_until_ms"
    private const val KEY_RATE_LIMIT_MSG = "ai_rate_limit_msg"
    private const val KEY_LAST_LATENCY_MS = "ai_last_latency_ms"
    private const val KEY_LAST_ACTIVE_MODEL = "ai_last_active_model"
    private const val KEY_LAST_ACTIVE_PROVIDER = "ai_last_active_provider"

    private fun isValidApiKey(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.isNotBlank() &&
            trimmed != DEFAULT_PLACEHOLDER_GEMINI_KEY &&
            trimmed != DEFAULT_PLACEHOLDER_GROQ_KEY &&
            trimmed != DEFAULT_PLACEHOLDER_OPENROUTER_KEY &&
            trimmed != "null"
    }

    fun hasBuildConfigGeminiKey(): Boolean = isValidApiKey(BuildConfig.GEMINI_API_KEY)
    fun hasBuildConfigGroqKey(): Boolean = isValidApiKey(BuildConfig.GROQ_API_KEY)
    fun hasBuildConfigOpenRouterKey(): Boolean = isValidApiKey(BuildConfig.OPENROUTER_API_KEY)

    /**
     * Reads the Gemini API key: user-saved key first, then AI Studio Secrets (BuildConfig.GEMINI_API_KEY).
     */
    fun getGeminiApiKey(context: Context): Flow<String> {
        return context.aiDataStore.data.map { preferences ->
            val dsKey = preferences[KEY_GEMINI_API_KEY].orEmpty()
            when {
                isValidApiKey(dsKey) -> dsKey.trim()
                else -> getGeminiApiKeySync(context)
            }
        }
    }

    fun getGeminiApiKeySync(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString("gemini_api_key", "")?.trim().orEmpty()
        if (isValidApiKey(saved)) return saved
        val buildCfg = BuildConfig.GEMINI_API_KEY.trim()
        return if (isValidApiKey(buildCfg)) buildCfg else ""
    }

    suspend fun saveGeminiApiKey(context: Context, key: String) {
        val trimmed = key.trim()
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("gemini_api_key", trimmed)
            .apply()
        context.aiDataStore.edit { preferences ->
            preferences[KEY_GEMINI_API_KEY] = trimmed
        }
    }

    fun saveGeminiApiKeySync(context: Context, key: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("gemini_api_key", key.trim())
            .apply()
    }

    /**
     * Reads the Groq API key: user-saved key first, then AI Studio Secrets (BuildConfig.GROQ_API_KEY).
     */
    fun getGroqApiKey(context: Context): Flow<String> {
        return context.aiDataStore.data.map { preferences ->
            val dsKey = preferences[KEY_GROQ_API_KEY].orEmpty()
            if (isValidApiKey(dsKey)) dsKey.trim() else getGroqApiKeySync(context)
        }
    }

    fun getGroqApiKeySync(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString("groq_api_key", "")?.trim().orEmpty()
        if (isValidApiKey(saved)) return saved
        val buildCfg = BuildConfig.GROQ_API_KEY.trim()
        return if (isValidApiKey(buildCfg)) buildCfg else ""
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
     * Reads the OpenRouter API key: user-saved key first, then AI Studio Secrets (BuildConfig.OPENROUTER_API_KEY).
     */
    fun getOpenRouterApiKey(context: Context): Flow<String> {
        return context.aiDataStore.data.map { preferences ->
            val dsKey = preferences[KEY_OPENROUTER_API_KEY].orEmpty()
            if (isValidApiKey(dsKey)) dsKey.trim() else getOpenRouterApiKeySync(context)
        }
    }

    fun getOpenRouterApiKeySync(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString("openrouter_api_key", "")?.trim().orEmpty()
        if (isValidApiKey(saved)) return saved
        val buildCfg = BuildConfig.OPENROUTER_API_KEY.trim()
        return if (isValidApiKey(buildCfg)) buildCfg else ""
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
     * Gets or sets the active AI provider ("auto", "gemini", "groq", "openrouter").
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

    fun getPreferredGeminiModel(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("preferred_gemini_model", GEMINI_MODEL_FLASH) ?: GEMINI_MODEL_FLASH
    }

    fun setPreferredGeminiModel(context: Context, model: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("preferred_gemini_model", model)
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

    fun getSelectedModelTierId(context: Context): String {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("selected_swiffle_model_tier", SwiffleModelTier.OMNI.id)
            ?: SwiffleModelTier.OMNI.id
    }

    fun setSelectedModelTierId(context: Context, tierId: String) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("selected_swiffle_model_tier", tierId)
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

    // =========================================================================
    // Real Usage, Rate (RPM / TPM / RPD) & Limit Telemetry
    // =========================================================================

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureDailyRollover(prefs: SharedPreferences) {
        val today = todayDateKey()
        val storedDate = prefs.getString(KEY_USAGE_DATE, null)
        if (storedDate != null && storedDate != today) {
            val editor = prefs.edit()
                .putString(KEY_USAGE_DATE, today)
                .putInt("real_tokens_used_today", 0)
                .putInt("real_prompt_tokens_today", 0)
                .putInt("real_output_tokens_today", 0)
                .putInt("real_thought_tokens_today", 0)
                .putInt("real_queries_executed_today", 0)
            ALL_FREE_MODELS.forEach { spec ->
                editor.putInt(modelDailyRequestsKey(spec.modelId), 0)
                editor.putInt(modelDailyTokensKey(spec.modelId), 0)
            }
            editor.apply()
        } else if (storedDate == null) {
            prefs.edit().putString(KEY_USAGE_DATE, today).apply()
        }
    }

    private fun modelDailyRequestsKey(modelId: String): String =
        "rpd_count_${modelId.replace(Regex("[^a-zA-Z0-9_]"), "_")}"

    private fun modelDailyTokensKey(modelId: String): String =
        "tok_count_${modelId.replace(Regex("[^a-zA-Z0-9_]"), "_")}"

    private fun modelCooldownUntilKey(modelId: String): String =
        "cooldown_until_${modelId.replace(Regex("[^a-zA-Z0-9_]"), "_")}"

    fun isModelInCooldown(context: Context, modelId: String): Boolean {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val cooldownUntil = prefs.getLong(modelCooldownUntilKey(modelId), 0L)
        return cooldownUntil > System.currentTimeMillis()
    }

    fun isModelDailyQuotaExhausted(context: Context, modelId: String): Boolean {
        val spec = ALL_FREE_MODELS.firstOrNull { it.modelId == modelId } ?: return false
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        ensureDailyRollover(prefs)
        val usedToday = prefs.getInt(modelDailyRequestsKey(modelId), 0)
        return usedToday >= spec.rpdLimit
    }

    /**
     * Records backwards-compatible token usage (e.g. from local simulated stream fallback).
     */
    fun recordTokenUsage(context: Context, tokensGenerated: Int) {
        val estimatedOutput = (tokensGenerated * 4).coerceAtLeast(24)
        recordDetailedUsage(
            context = context,
            modelId = getPreferredGeminiModel(context),
            provider = getPreferredProvider(context),
            promptTokens = 0,
            outputTokens = estimatedOutput,
            thoughtTokens = 0,
            totalTokens = estimatedOutput,
            latencyMs = 0L
        )
    }

    /**
     * Records exact server-reported or measured request & token telemetry into OrigamiPrefs,
     * including a 60-second sliding window for real RPM and TPM calculations.
     */
    fun recordDetailedUsage(
        context: Context,
        modelId: String,
        provider: String,
        promptTokens: Int,
        outputTokens: Int,
        thoughtTokens: Int,
        totalTokens: Int,
        latencyMs: Long
    ) {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        ensureDailyRollover(prefs)

        val now = System.currentTimeMillis()
        val effectiveTotal = totalTokens.coerceAtLeast(promptTokens + outputTokens + thoughtTokens).coerceAtLeast(1)
        val effectiveOutput = if (outputTokens > 0) outputTokens else effectiveTotal

        val currentTokens = prefs.getInt("real_tokens_used_today", 0)
        val currentPrompt = prefs.getInt("real_prompt_tokens_today", 0)
        val currentOutput = prefs.getInt("real_output_tokens_today", 0)
        val currentThought = prefs.getInt("real_thought_tokens_today", 0)
        val currentQueries = prefs.getInt("real_queries_executed_today", 0)

        val resolvedModel = modelId.ifBlank { GEMINI_MODEL_FLASH }
        val rpdKey = modelDailyRequestsKey(resolvedModel)
        val tokKey = modelDailyTokensKey(resolvedModel)
        val currentModelRpd = prefs.getInt(rpdKey, 0)
        val currentModelTok = prefs.getInt(tokKey, 0)

        val updatedWindow = pruneWindowEvents(prefs.getString(KEY_MINUTE_WINDOW_EVENTS, null), now)
        updatedWindow.put(
            JSONObject().apply {
                put("ts", now)
                put("model", resolvedModel)
                put("promptTokens", promptTokens.coerceAtLeast(0))
                put("outputTokens", effectiveOutput)
                put("totalTokens", effectiveTotal)
            }
        )

        prefs.edit()
            .putString(KEY_USAGE_DATE, todayDateKey())
            .putInt("real_tokens_used_today", currentTokens + effectiveTotal)
            .putInt("real_prompt_tokens_today", currentPrompt + promptTokens.coerceAtLeast(0))
            .putInt("real_output_tokens_today", currentOutput + effectiveOutput)
            .putInt("real_thought_tokens_today", currentThought + thoughtTokens.coerceAtLeast(0))
            .putInt("real_queries_executed_today", currentQueries + 1)
            .putInt(rpdKey, currentModelRpd + 1)
            .putInt(tokKey, currentModelTok + effectiveTotal)
            .putLong("last_ai_query_timestamp", now)
            .putLong(KEY_LAST_LATENCY_MS, latencyMs.coerceAtLeast(0L))
            .putString(KEY_LAST_ACTIVE_MODEL, resolvedModel)
            .putString(KEY_LAST_ACTIVE_PROVIDER, provider)
            .putString(KEY_MINUTE_WINDOW_EVENTS, updatedWindow.toString())
            .apply()
    }

    fun recordRateLimitHit(
        context: Context,
        modelId: String,
        provider: String,
        retryAfterSec: Long,
        detailMessage: String? = null
    ) {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val safeSec = retryAfterSec.coerceIn(5L, 3600L)
        val untilMs = now + (safeSec * 1000L)
        val resolvedModel = modelId.ifBlank { GEMINI_MODEL_FLASH }
        val message = detailMessage?.takeIf { it.isNotBlank() }
            ?: "$provider rate limit reached for $resolvedModel (retry in ${safeSec}s)"

        prefs.edit()
            .putLong(KEY_RATE_LIMIT_UNTIL_MS, untilMs)
            .putLong(modelCooldownUntilKey(resolvedModel), untilMs)
            .putString(KEY_RATE_LIMIT_MSG, message)
            .apply()
    }

    fun resetDailyUsageTelemetry(context: Context) {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_USAGE_DATE, todayDateKey())
            .putInt("real_tokens_used_today", 0)
            .putInt("real_prompt_tokens_today", 0)
            .putInt("real_output_tokens_today", 0)
            .putInt("real_thought_tokens_today", 0)
            .putInt("real_queries_executed_today", 0)
            .putInt("tokens_used_today", 0)
            .putInt("queries_executed_today", 0)
            .putString(KEY_MINUTE_WINDOW_EVENTS, "[]")
            .putLong(KEY_RATE_LIMIT_UNTIL_MS, 0L)
            .remove(KEY_RATE_LIMIT_MSG)
        ALL_FREE_MODELS.forEach { spec ->
            editor.putInt(modelDailyRequestsKey(spec.modelId), 0)
            editor.putInt(modelDailyTokensKey(spec.modelId), 0)
            editor.putLong(modelCooldownUntilKey(spec.modelId), 0L)
        }
        editor.apply()
    }

    fun getUsageRateSnapshot(context: Context): UsageRateSnapshot {
        val prefs = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        ensureDailyRollover(prefs)

        val now = System.currentTimeMillis()
        val windowArray = pruneWindowEvents(prefs.getString(KEY_MINUTE_WINDOW_EVENTS, null), now)
        prefs.edit().putString(KEY_MINUTE_WINDOW_EVENTS, windowArray.toString()).apply()

        val selectedTier = SwiffleModelTier.fromId(getSelectedModelTierId(context))
        val preferredProvider = getPreferredProvider(context)
        val groqKey = getGroqApiKeySync(context)
        val openRouterKey = getOpenRouterApiKeySync(context)
        val geminiKey = getGeminiApiKeySync(context)
        val hasGroqKey = groqKey.isNotBlank()
        val hasOpenRouterKey = openRouterKey.isNotBlank()
        val hasGeminiKey = geminiKey.isNotBlank()
        val hasAnyCloudKey = hasGroqKey || hasOpenRouterKey || hasGeminiKey

        val lastRecordedModel = prefs.getString(KEY_LAST_ACTIVE_MODEL, null)?.ifBlank { null }
        val activeModelId = when {
            preferredProvider == PROVIDER_GROQ && hasGroqKey -> selectedTier.groqModelId
            preferredProvider == PROVIDER_OPENROUTER && hasOpenRouterKey -> selectedTier.openRouterModelId
            (preferredProvider == PROVIDER_GEMINI || preferredProvider == PROVIDER_FIREBASE_GEMINI) && hasGeminiKey ->
                selectedTier.geminiModelId
            lastRecordedModel != null && ALL_FREE_MODELS.any { it.modelId == lastRecordedModel } -> lastRecordedModel
            hasGroqKey -> selectedTier.groqModelId
            hasOpenRouterKey -> selectedTier.openRouterModelId
            hasGeminiKey -> selectedTier.geminiModelId
            else -> selectedTier.geminiModelId
        }

        val activeSpec = ALL_FREE_MODELS.firstOrNull { it.modelId == activeModelId }
            ?: GEMINI_FREE_MODELS.first()

        val activeProviderLabel = when {
            preferredProvider == PROVIDER_GROQ ->
                "Groq Cloud Free Tier"
            preferredProvider == PROVIDER_OPENROUTER ->
                "OpenRouter Free Tier"
            preferredProvider == PROVIDER_GEMINI || preferredProvider == PROVIDER_FIREBASE_GEMINI ->
                "Google Gemini Free Tier"
            hasGroqKey || hasOpenRouterKey ->
                "Auto (Groq → OpenRouter → Gemini)"
            hasGeminiKey ->
                "Google Gemini Free Tier (Auto)"
            else ->
                "Auto (Groq → OpenRouter → Gemini)"
        }

        val configuredSources = buildList {
            if (hasGroqKey) add("Groq")
            if (hasOpenRouterKey) add("OpenRouter")
            if (hasGeminiKey) add("Gemini")
        }
        val keySourceLabel = when {
            configuredSources.isNotEmpty() -> configuredSources.joinToString(" · ")
            else -> "No keys configured"
        }

        var activeModelRpm = 0
        var activeModelTpm = 0
        val perModelMinuteRpm = mutableMapOf<String, Int>()
        val perModelMinuteTpm = mutableMapOf<String, Int>()

        for (i in 0 until windowArray.length()) {
            val item = windowArray.optJSONObject(i) ?: continue
            val m = item.optString("model", GEMINI_MODEL_FLASH)
            val tok = item.optInt("totalTokens", 0)
            perModelMinuteRpm[m] = (perModelMinuteRpm[m] ?: 0) + 1
            perModelMinuteTpm[m] = (perModelMinuteTpm[m] ?: 0) + tok
            if (m == activeModelId) {
                activeModelRpm++
                activeModelTpm += tok
            }
        }

        val totalMinuteRpm = windowArray.length()
        val totalMinuteTpm = (0 until windowArray.length()).sumOf { idx ->
            windowArray.optJSONObject(idx)?.optInt("totalTokens", 0) ?: 0
        }

        // Show active configured provider pools (and always include Gemini Free Tier)
        val trackedSpecs = buildList {
            if (hasGroqKey || preferredProvider == PROVIDER_GROQ) addAll(GROQ_FREE_MODELS)
            if (hasOpenRouterKey || preferredProvider == PROVIDER_OPENROUTER) addAll(OPENROUTER_FREE_MODELS)
            addAll(GEMINI_FREE_MODELS)
        }.distinctBy { it.modelId }

        val perModelStats = trackedSpecs.map { spec ->
            val cooldownUntil = prefs.getLong(modelCooldownUntilKey(spec.modelId), 0L)
            val cooldownSec = if (cooldownUntil > now) ((cooldownUntil - now) / 1000L).toInt().coerceAtLeast(1) else 0
            ModelUsageStats(
                modelId = spec.modelId,
                displayName = spec.displayName,
                tierLabel = spec.tierLabel,
                requestsLastMinute = perModelMinuteRpm[spec.modelId] ?: 0,
                tokensLastMinute = perModelMinuteTpm[spec.modelId] ?: 0,
                requestsToday = prefs.getInt(modelDailyRequestsKey(spec.modelId), 0),
                tokensToday = prefs.getInt(modelDailyTokensKey(spec.modelId), 0),
                rpmLimit = spec.rpmLimit,
                tpmLimit = spec.tpmLimit,
                rpdLimit = spec.rpdLimit,
                cooldownRemainingSec = cooldownSec
            )
        }

        val globalCooldownUntil = prefs.getLong(KEY_RATE_LIMIT_UNTIL_MS, 0L)
        val cooldownSec = if (globalCooldownUntil > now) {
            ((globalCooldownUntil - now) / 1000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
        val rateLimitMsg = if (cooldownSec > 0) prefs.getString(KEY_RATE_LIMIT_MSG, null) else null

        val activeModelRpd = prefs.getInt(modelDailyRequestsKey(activeSpec.modelId), 0)
        val totalQueriesToday = prefs.getInt("real_queries_executed_today", 0)

        return UsageRateSnapshot(
            activeProviderLabel = activeProviderLabel,
            activeModelId = activeSpec.modelId,
            activeModelDisplayName = activeSpec.displayName,
            hasGeminiKey = hasAnyCloudKey,
            keySourceLabel = keySourceLabel,
            currentRpm = if (activeModelRpm > 0) activeModelRpm else totalMinuteRpm,
            rpmLimit = activeSpec.rpmLimit,
            currentTpm = if (activeModelTpm > 0) activeModelTpm else totalMinuteTpm,
            tpmLimit = activeSpec.tpmLimit,
            currentRpd = if (activeModelRpd > 0 || !hasAnyCloudKey) activeModelRpd else totalQueriesToday,
            rpdLimit = activeSpec.rpdLimit,
            promptTokensToday = prefs.getInt("real_prompt_tokens_today", 0),
            outputTokensToday = prefs.getInt("real_output_tokens_today", 0),
            thoughtTokensToday = prefs.getInt("real_thought_tokens_today", 0),
            totalTokensToday = prefs.getInt("real_tokens_used_today", 0),
            dailyTokenQuota = DAILY_TOKEN_QUOTA_DEFAULT,
            queriesToday = totalQueriesToday,
            lastLatencyMs = prefs.getLong(KEY_LAST_LATENCY_MS, 0L),
            lastQueryTimestampMs = prefs.getLong("last_ai_query_timestamp", 0L),
            lastRateLimitMessage = rateLimitMsg,
            cooldownRemainingSec = cooldownSec,
            perModelStats = perModelStats
        )
    }

    private fun pruneWindowEvents(rawJson: String?, nowMs: Long): JSONArray {
        val cutoff = nowMs - 60_000L
        val pruned = JSONArray()
        if (rawJson.isNullOrBlank()) return pruned
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optLong("ts", 0L) >= cutoff) {
                    pruned.put(obj)
                }
            }
        } catch (_: Exception) {
        }
        return pruned
    }
}
