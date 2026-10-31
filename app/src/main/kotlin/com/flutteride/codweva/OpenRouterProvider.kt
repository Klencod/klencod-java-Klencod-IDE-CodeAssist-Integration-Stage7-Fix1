package com.flutteride.codweva

import org.json.JSONObject

/**
 * مزود OpenRouter — chat/completions API.
 *
 * يستخدم:
 *   - Authorization: Bearer <key>
 *   - POST https://openrouter.ai/api/v1/chat/completions
 *   - متوافق مع OpenAI API (يدعم tools)
 *   - يستفيد من OpenAiProvider.toMessages / toMessagesWithTools
 */
class OpenRouterProvider @JvmOverloads constructor(
    private val apiKey: String?,
    private val endpoint: String = DEFAULT_ENDPOINT
) : AiProvider {

    override fun getId(): String = AiSettings.OPENROUTER

    override fun getDisplayName(): String = "OpenRouter"

    // ============================================================
    // الإرسال العادي
    // ============================================================
    @Throws(Exception::class)
    override fun send(
        messages: List<AiMessage>?,
        model: String?,
        temperature: Double,
        maxTokens: Int
    ): AiResponse {
        if (apiKey.isNullOrBlank()) {
            throw IllegalArgumentException("أدخل مفتاح OpenRouter أولًا")
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", OpenAiProvider.toMessages(messages))
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }

        val response = AiHttpClient.postJson(
            endpoint, apiKey, body, keyInQuery = false
        )

        val choices = response.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw Exception("لم يُرجع OpenRouter أي استجابة")
        }

        val message = choices.getJSONObject(0).optJSONObject("message")
        val text = message?.optString("content", "") ?: ""
        return AiResponse(text, getId(), model)
    }

    // ============================================================
    // الإرسال مع أدوات
    // ============================================================
    @Throws(Exception::class)
    override fun sendWithTools(
        messages: List<AiMessage>?,
        model: String?,
        temperature: Double,
        maxTokens: Int,
        previousToolResults: List<AgentTool.ToolCall>?
    ): AiResponse {
        if (apiKey.isNullOrBlank()) {
            throw IllegalArgumentException("أدخل مفتاح OpenRouter أولًا")
        }

        val body = JSONObject().apply {
            put("model", model)
            // نستخدم النسخة الموسّعة من OpenAiProvider
            put("messages", OpenAiProvider.toMessagesWithTools(messages))
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }

        val tools = AgentTool.buildOpenAiTools()
        if (tools.length() > 0) {
            body.put("tools", tools)
            body.put("tool_choice", "auto")
        }

        val response = AiHttpClient.postJson(
            endpoint, apiKey, body, keyInQuery = false,
            connectTimeoutMs = 30000, readTimeoutMs = 180000
        )

        val choices = response.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw Exception("لم يُرجع OpenRouter أي استجابة")
        }

        val choice = choices.getJSONObject(0)
        val message = choice.optJSONObject("message")
            ?: throw Exception("رد OpenRouter لا يحتوي message")

        val text = message.optString("content", "").orEmpty()
        val calls = AgentTool.parseOpenAiToolCalls(message)

        if (calls.isEmpty()) {
            return AiResponse(text, getId(), model)
        }

        return AiResponse(
            text = "",
            toolCalls = calls,
            providerId = getId(),
            model = model,
            rawAssistantMessage = message
        )
    }

    // ============================================================
    // ثوابت
    // ============================================================
    companion object {
        private const val DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}