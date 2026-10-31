package com.flutteride.codweva

/**
 * CustomProviderConfig — إعدادات مزود AI مخصص.
 *
 * مبسّط:
 *   - 3 حقول فقط: Base URL + Model + API Key (في AiKeyStore)
 *   - نوع API: OpenAI-compatible دائماً
 *   - لا Tools / لا تصدير / لا Format
 */
class CustomProviderConfig(
    /** Base URL (مثل https://api.groq.com/openai/v1) */
    val baseUrl: String,

    /** اسم النموذج */
    val model: String,

    /** درجة الحرارة */
    val temperature: Double = 0.2,

    /** الحد الأقصى للتوكنات */
    val maxTokens: Int = 4096
) {

    /** هل التكوين صالح؟ */
    val isValid: Boolean
        get() = baseUrl.isNotBlank() &&
                baseUrl.startsWith("http") &&
                model.isNotBlank()

    companion object {
        /** قيمة افتراضية */
        @JvmStatic
        fun default(): CustomProviderConfig {
            return CustomProviderConfig(
                baseUrl = "",
                model = ""
            )
        }
    }
}