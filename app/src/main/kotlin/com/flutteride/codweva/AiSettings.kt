package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences

/**
 * إعدادات مزودي AI.
 *
 * المزودون المدعومون:
 *   - OpenAI
 *   - Gemini
 *   - OpenRouter
 *   - Custom (مزود مخصص)
 *
 * v2.0 — إضافة CUSTOM:
 *   - ثابت CUSTOM = "custom"
 *   - دوال مساعدة للتحقق من النوع
 */
class AiSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ============================================================
    // المزود الافتراضي
    // ============================================================
    val defaultProvider: String
        get() = prefs.getString(DEFAULT_PROVIDER, OPENAI) ?: OPENAI

    fun setDefaultProvider(providerId: String?) {
        val safe = providerId ?: OPENAI
        prefs.edit().putString(DEFAULT_PROVIDER, safe).apply()
    }

    // ============================================================
    // النموذج (Model) لكل مزود
    // ============================================================
    fun getModel(providerId: String?): String {
        val defaultModel = when (providerId) {
            OPENAI -> "gpt-4o-mini"
            GEMINI -> "gemini-2.0-flash"
            OPENROUTER -> "openai/gpt-4o-mini"
            CUSTOM -> "" // يُقرأ من CustomProviderStore
            else -> "gpt-4o-mini"
        }
        return prefs.getString(MODEL_PREFIX + providerId, defaultModel) ?: defaultModel
    }

    fun setModel(providerId: String?, model: String?) {
        prefs.edit().putString(
            MODEL_PREFIX + providerId,
            model?.trim() ?: ""
        ).apply()
    }

    // ============================================================
    // درجة الحرارة (Temperature)
    // ============================================================
    val temperature: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(TEMPERATURE, java.lang.Double.doubleToLongBits(0.2))
        )

    fun setTemperature(value: Double) {
        val clamped = value.coerceIn(0.0, 2.0)
        prefs.edit().putLong(
            TEMPERATURE,
            java.lang.Double.doubleToLongBits(clamped)
        ).apply()
    }

    // ============================================================
    // الحد الأقصى للتوكنات
    // ============================================================
    val maxTokens: Int
        get() = prefs.getInt(MAX_TOKENS, 4096)

    fun setMaxTokens(value: Int) {
        prefs.edit().putInt(MAX_TOKENS, value.coerceIn(256, 32768)).apply()
    }

    // ============================================================
    // دوال مساعدة للتحقق من نوع المزود
    // ============================================================

    /**
     * هل المزود مخصص؟
     */
    fun isCustomProvider(providerId: String?): Boolean {
        return providerId == CUSTOM
    }

    /**
     * هل المزود مدمج (أحد الثلاثة)؟
     */
    fun isBuiltInProvider(providerId: String?): Boolean {
        return providerId == OPENAI ||
                providerId == GEMINI ||
                providerId == OPENROUTER
    }

    /**
     * هل المزود معروف (مدمج أو مخصص)؟
     */
    fun isValidProvider(providerId: String?): Boolean {
        return isBuiltInProvider(providerId) || isCustomProvider(providerId)
    }

    /**
     * قائمة كل المزودين المدمجين.
     */
    val builtInProviders: List<String>
        get() = listOf(OPENAI, GEMINI, OPENROUTER)

    /**
     * قائمة كل المزودين (مدمج + مخصص).
     */
    val allProviders: List<String>
        get() = listOf(OPENAI, GEMINI, OPENROUTER, CUSTOM)

    /**
     * الاسم المعروض للمزود.
     */
    fun displayNameFor(providerId: String?, customName: String? = null): String {
        return when (providerId) {
            OPENAI -> "OpenAI"
            GEMINI -> "Gemini"
            OPENROUTER -> "OpenRouter"
            CUSTOM -> customName?.ifBlank { "مخصص" } ?: "مخصص"
            else -> "غير معروف"
        }
    }

    // ============================================================
    // ثوابت
    // ============================================================
    companion object {
        // معرّفات المزودين
        const val OPENAI = "openai"
        const val GEMINI = "gemini"
        const val OPENROUTER = "openrouter"
        const val CUSTOM = "custom"

        // مفاتيح SharedPreferences
        private const val PREFS = "ai_settings"
        private const val DEFAULT_PROVIDER = "default_provider"
        private const val MODEL_PREFIX = "model_"
        private const val TEMPERATURE = "temperature"
        private const val MAX_TOKENS = "max_tokens"

        // قيم افتراضية
        const val DEFAULT_TEMPERATURE = 0.2
        const val DEFAULT_MAX_TOKENS = 4096
    }
}