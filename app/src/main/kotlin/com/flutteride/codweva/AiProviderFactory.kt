package com.flutteride.codweva

import android.content.Context

/**
 * مصنع المزودين — ينشئ AiProvider حسب المعرّف.
 *
 * المزودون المدعومون:
 *   - OpenAI
 *   - Gemini
 *   - OpenRouter
 *   - Custom (مزود مخصص — OpenAI-compatible)
 *
 * المسؤوليات:
 *   - قراءة المفتاح من AiKeyStore
 *   - قراءة التكوين من CustomProviderStore (للمزود المخصص)
 *   - إنشاء المزود المناسب
 */
object AiProviderFactory {

    /**
     * ينشئ مزود AI حسب المعرّف.
     */
    @JvmStatic
    fun create(context: Context, providerId: String?): AiProvider {
        val keyStore = AiKeyStore(context)
        val key = keyStore.get(providerId)

        return when (providerId) {
            AiSettings.OPENAI -> OpenAiProvider(key)
            AiSettings.GEMINI -> GeminiProvider(key)
            AiSettings.OPENROUTER -> OpenRouterProvider(key)

            AiSettings.CUSTOM -> {
                val customStore = CustomProviderStore(context)
                val config = customStore.getConfig()

                if (config == null) {
                    throw IllegalArgumentException(
                        "لم يتم إعداد المزود المخصص بعد"
                    )
                }

                if (!config.isValid) {
                    throw IllegalArgumentException(
                        "إعدادات المزود المخصص غير مكتملة"
                    )
                }

                if (key.isNullOrBlank()) {
                    throw IllegalArgumentException(
                        "أدخل مفتاح API للمزود المخصص"
                    )
                }

                CustomProvider(config, key)
            }

            else -> throw IllegalArgumentException("مزود غير معروف: $providerId")
        }
    }

    /**
     * يتحقق إذا كان المزود جاهزاً للاستخدام.
     */
    @JvmStatic
    fun isReady(context: Context, providerId: String?): Boolean {
        return try {
            val keyStore = AiKeyStore(context)

            when (providerId) {
                AiSettings.OPENAI,
                AiSettings.GEMINI,
                AiSettings.OPENROUTER -> {
                    !keyStore.get(providerId).isNullOrBlank()
                }

                AiSettings.CUSTOM -> {
                    val customStore = CustomProviderStore(context)
                    customStore.isReady
                }

                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * يُرجع رسالة خطأ واضحة إذا لم يكن المزود جاهزاً.
     *
     * @return null إذا كان جاهزاً، أو رسالة الخطأ
     */
    @JvmStatic
    fun getReadinessMessage(context: Context, providerId: String?): String? {
        return try {
            val keyStore = AiKeyStore(context)

            when (providerId) {
                AiSettings.OPENAI,
                AiSettings.GEMINI,
                AiSettings.OPENROUTER -> {
                    if (keyStore.get(providerId).isNullOrBlank()) {
                        "أدخل مفتاح ${displayNameFor(providerId)} من الإعدادات أولاً"
                    } else null
                }

                AiSettings.CUSTOM -> {
                    val customStore = CustomProviderStore(context)

                    if (!customStore.isEnabled) {
                        "المزود المخصص غير مُفعّل"
                    } else if (customStore.getConfig() == null) {
                        "لم يتم إعداد المزود المخصص بعد"
                    } else if (!customStore.getConfig()!!.isValid) {
                        "إعدادات المزود المخصص غير مكتملة"
                    } else if (customStore.getApiKey().isNullOrBlank()) {
                        "أدخل مفتاح API للمزود المخصص"
                    } else null
                }

                else -> "مزود غير معروف: $providerId"
            }
        } catch (e: Exception) {
            "خطأ: ${e.message}"
        }
    }

    /**
     * الاسم المعروض للمزود.
     */
    @JvmStatic
    private fun displayNameFor(providerId: String?): String {
        return when (providerId) {
            AiSettings.OPENAI -> "OpenAI"
            AiSettings.GEMINI -> "Gemini"
            AiSettings.OPENROUTER -> "OpenRouter"
            AiSettings.CUSTOM -> "مخصص"
            else -> "غير معروف"
        }
    }
}