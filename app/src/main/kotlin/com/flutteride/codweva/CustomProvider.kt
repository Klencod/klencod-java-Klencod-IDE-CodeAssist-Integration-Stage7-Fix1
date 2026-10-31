package com.flutteride.codweva

import org.json.JSONObject

/**
 * CustomProvider — مزود AI مخصص.
 *
 * مبسّط:
 *   - OpenAI-compatible فقط
 *   - يستخدم AiHttpClient + AgentTool الموجودين
 *   - يدعم Tools (لأن OpenAI-compatible يدعمها)
 */
class CustomProvider(
    private val config: CustomProviderConfig?,
    private val apiKey: String?
) : AiProvider {

    override fun getId(): String = AiSettings.CUSTOM

    override fun getDisplayName(): String = "مخصص"

    // ============================================================
    // التحقق
    // ============================================================
    private fun requireReady() {
        if (config == null) {
            throw IllegalArgumentException("لم يتم إعداد المزود المخصص بعد")
        }
        if (!config.isValid) {
            throw IllegalArgumentException("إعدادات المزود المخصص غير مكتملة")
        }
        if (apiKey.isNullOrBlank()) {
            throw IllegalArgumentException("أدخل مفتاح API للمزود المخصص")
        }
    }

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
        requireReady()
        val cfg = config!!

        val effectiveModel = model?.takeIf { it.isNotBlank() } ?: cfg.model
        val effectiveTemp = if (temperature > 0.0) temperature else cfg.temperature
        val effectiveMaxTokens = if (maxTokens > 0) maxTokens else cfg.maxTokens

        val endpoint = buildEndpoint(cfg.baseUrl)

        val body = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", OpenAiProvider.toMessages(messages))
            put("temperature", effectiveTemp)
            put("max_tokens", effectiveMaxTokens)
        }

        val response = AiHttpClient.postJson(
            endpoint = endpoint,
            apiKey = apiKey,
            body = body,
            keyInQuery = false,
            connectTimeoutMs = 30000,
            readTimeoutMs = 180000
        )

        val choices = response.optJSONArray("choices")
            ?: throw Exception("لم يُرجع المزود أي استجابة")

        if (choices.length() == 0) {
            throw Exception("لم يُرجع المزود أي استجابة")
        }

        val message = choices.getJSONObject(0).optJSONObject("message")
        val text = message?.optString("content", "") ?: ""

        return AiResponse(text, getId(), effectiveModel)
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
        requireReady()
        val cfg = config!!

        val effectiveModel = model?.takeIf { it.isNotBlank() } ?: cfg.model
        val effectiveTemp = if (temperature > 0.0) temperature else cfg.temperature
        val effectiveMaxTokens = if (maxTokens > 0) maxTokens else cfg.maxTokens

        val endpoint = buildEndpoint(cfg.baseUrl)

        val body = JSONObject().apply {
            put("model", effectiveModel)
            put("messages", OpenAiProvider.toMessagesWithTools(messages))
            put("temperature", effectiveTemp)
            put("max_tokens", effectiveMaxTokens)
        }

        val tools = AgentTool.buildOpenAiTools()
        if (tools.length() > 0) {
            body.put("tools", tools)
            body.put("tool_choice", "auto")
        }

        val response = AiHttpClient.postJson(
            endpoint = endpoint,
            apiKey = apiKey,
            body = body,
            keyInQuery = false,
            connectTimeoutMs = 30000,
            readTimeoutMs = 180000
        )

        val choices = response.optJSONArray("choices")
            ?: throw Exception("لم يُرجع المزود أي استجابة")

        if (choices.length() == 0) {
            throw Exception("لم يُرجع المزود أي استجابة")
        }

        val choice = choices.getJSONObject(0)
        val message = choice.optJSONObject("message")
            ?: throw Exception("رد المزود لا يحتوي message")

        val text = message.optString("content", "").orEmpty()
        val calls = AgentTool.parseOpenAiToolCalls(message)

        if (calls.isEmpty()) {
            return AiResponse(text, getId(), effectiveModel)
        }

        return AiResponse(
            text = "",
            toolCalls = calls,
            providerId = getId(),
            model = effectiveModel,
            rawAssistantMessage = message
        )
    }

    // ============================================================
    // بناء endpoint
    // ============================================================
    private fun buildEndpoint(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return "$base/chat/completions"
    }
}