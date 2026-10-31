package com.flutteride.codweva

import org.json.JSONArray
import org.json.JSONObject

/**
 * مزود OpenAI — chat/completions API.
 *
 * يستخدم:
 *   - Authorization: Bearer <key>
 *   - POST https://api.openai.com/v1/chat/completions
 *   - يدعم Tools (function calling)
 */
class OpenAiProvider @JvmOverloads constructor(
    private val apiKey: String?,
    private val endpoint: String = DEFAULT_ENDPOINT
) : AiProvider {

    override fun getId(): String = AiSettings.OPENAI

    override fun getDisplayName(): String = "OpenAI"

    // ============================================================
    // الإرسال العادي (بدون أدوات)
    // ============================================================
    @Throws(Exception::class)
    override fun send(
        messages: List<AiMessage>?,
        model: String?,
        temperature: Double,
        maxTokens: Int
    ): AiResponse {
        if (apiKey.isNullOrBlank()) {
            throw IllegalArgumentException("أدخل مفتاح OpenAI أولًا")
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", toMessages(messages))
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }

        val response = AiHttpClient.postJson(
            endpoint, apiKey, body, keyInQuery = false
        )

        val choices = response.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw Exception("لم يُرجع OpenAI أي استجابة")
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
            throw IllegalArgumentException("أدخل مفتاح OpenAI أولًا")
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", toMessagesWithTools(messages))
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
            throw Exception("لم يُرجع OpenAI أي استجابة")
        }

        val choice = choices.getJSONObject(0)
        val message = choice.optJSONObject("message")
            ?: throw Exception("رد OpenAI لا يحتوي message")

        val text = message.optString("content", "").orEmpty()
        val calls = AgentTool.parseOpenAiToolCalls(message)

        if (calls.isEmpty()) {
            return AiResponse(text, getId(), model)
        }

        // رد يحتوي tool calls — نحتفظ بالرسالة الأصلية
        return AiResponse(
            text = "",
            toolCalls = calls,
            providerId = getId(),
            model = model,
            rawAssistantMessage = message
        )
    }

    // ============================================================
    // تحويلات الرسائل
    // ============================================================
    companion object {
        private const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"

        /**
         * للاستخدام العام (OpenRouter تعتمد عليها).
         */
        @JvmStatic
        @Throws(Exception::class)
        fun toMessages(messages: List<AiMessage>?): JSONArray {
            val array = JSONArray()
            messages?.forEach { message ->
                val item = JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                }
                array.put(item)
            }
            return array
        }

        /**
         * تحويل موسّع يدعم:
         *   - رسائل assistant عادية
         *   - رسائل assistant التي تحتوي tool_calls (rawMessage)
         *   - رسائل tool (toolCallId)
         */
        @JvmStatic
        @Throws(Exception::class)
        fun toMessagesWithTools(messages: List<AiMessage>?): JSONArray {
            val array = JSONArray()
            if (messages == null) return array

            for (message in messages) {
                // 1) رسالة assistant تحتوي tool_calls — استخدم rawMessage الأصلية
                if (message.role == "assistant" && message.rawMessage != null) {
                    array.put(message.rawMessage)
                    continue
                }

                // 2) رسالة tool — تحتاج tool_call_id
                if (message.role == "tool") {
                    val item = JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", message.toolCallId ?: "")
                        put("content", message.content)
                    }
                    array.put(item)
                    continue
                }

                // 3) رسالة عادية (system / user / assistant)
                val item = JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                }
                array.put(item)
            }
            return array
        }
    }
}