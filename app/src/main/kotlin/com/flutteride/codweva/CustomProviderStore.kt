package com.flutteride.codweva

import android.content.Context
import android.content.SharedPreferences

/**
 * CustomProviderStore — تخزين إعدادات المزود المخصص.
 *
 * مبسّط:
 *   - 3 حقول فقط
 *   - بدون تصدير/استيراد
 *   - API Key في AiKeyStore
 */
class CustomProviderStore(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val keyStore: AiKeyStore = AiKeyStore(appContext)

    // ============================================================
    // الحفظ
    // ============================================================

    /**
     * يحفظ التكوين + API Key.
     */
    fun save(config: CustomProviderConfig?, apiKey: String?): Boolean {
        if (config == null || !config.isValid) return false

        return try {
            prefs.edit()
                .putString(KEY_BASE_URL, config.baseUrl)
                .putString(KEY_MODEL, config.model)
                .putLong(KEY_TEMP, java.lang.Double.doubleToLongBits(config.temperature))
                .putInt(KEY_MAX_TOKENS, config.maxTokens)
                .putBoolean(KEY_ENABLED, true)
                .apply()

            if (!apiKey.isNullOrBlank()) {
                keyStore.save(AiSettings.CUSTOM, apiKey)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * حفظ API Key فقط.
     */
    fun saveApiKey(apiKey: String?): Boolean {
        if (apiKey.isNullOrBlank()) return false
        return try {
            keyStore.save(AiSettings.CUSTOM, apiKey)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ============================================================
    // القراءة
    // ============================================================

    /**
     * يقرأ التكوين.
     */
    fun getConfig(): CustomProviderConfig? {
        try {
            val baseUrl = prefs.getString(KEY_BASE_URL, "") ?: ""
            val model = prefs.getString(KEY_MODEL, "") ?: ""

            if (baseUrl.isEmpty() && model.isEmpty()) return null

            val temp = java.lang.Double.longBitsToDouble(
                prefs.getLong(KEY_TEMP, java.lang.Double.doubleToLongBits(0.2))
            )
            val maxTokens = prefs.getInt(KEY_MAX_TOKENS, 4096)

            return CustomProviderConfig(
                baseUrl = baseUrl,
                model = model,
                temperature = temp,
                maxTokens = maxTokens
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * يقرأ API Key.
     */
    fun getApiKey(): String? {
        val key = keyStore.get(AiSettings.CUSTOM)
        return if (key.isNullOrBlank()) null else key
    }

    /**
     * هل المزود جاهز للاستخدام؟
     */
    val isReady: Boolean
        get() {
            if (!prefs.getBoolean(KEY_ENABLED, false)) return false
            val config = getConfig() ?: return false
            if (!config.isValid) return false
            return !getApiKey().isNullOrBlank()
        }

    /**
     * هل المزود مُفعّل؟
     */
    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    // ============================================================
    // ⭐ اسم المزود للعرض
    // ============================================================

    /**
     * اسم المزود للعرض.
     * (ثابت — لأن المزود المخصص ليس له اسم)
     */
    fun getDisplayName(): String = "مخصص"

    // ============================================================
    // الحذف
    // ============================================================

    /**
     * يحذف كل الإعدادات.
     */
    fun clear() {
        try {
            prefs.edit()
                .remove(KEY_BASE_URL)
                .remove(KEY_MODEL)
                .remove(KEY_TEMP)
                .remove(KEY_MAX_TOKENS)
                .remove(KEY_ENABLED)
                .apply()
            keyStore.clear(AiSettings.CUSTOM)
        } catch (_: Exception) {
        }
    }

    /**
     * يعطّل المزود.
     */
    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
    }

    // ============================================================
    // ثوابت
    // ============================================================
    companion object {
        private const val PREFS = "custom_provider_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_TEMP = "temperature"
        private const val KEY_MAX_TOKENS = "max_tokens"
        private const val KEY_ENABLED = "enabled"

        /** القيمة الافتراضية لـ Base URL */
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
    }
}