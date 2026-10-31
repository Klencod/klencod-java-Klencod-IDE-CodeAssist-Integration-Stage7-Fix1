package com.flutteride.codweva

import org.json.JSONArray
import org.json.JSONObject

/**
 * مزود Google Gemini — generateContent API.
 *
 * يستخدم:
 *   - API key في Query (?key=...)
 *   - POST {endpoint}/models/{model}:generateContent
 *   - يدعم function declarations
 *   - يستخدم "model" بدلاً من "assistant" في الأدوار
 */
class GeminiProvider @JvmOverloads constructor(
    private val apiKey: String?,
    private val endpointBase: String = DEFAULT_ENDPOINT
) : AiProvider {

    override fun getId(): String = AiSettings.GEMINI

    override fun getDisplayName(): String = "Gemini"

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
            throw IllegalArgumentException("أدخل مفتاح Gemini أولًا")
        }

        val cleanModel = cleanModelName(model)
        val body = JSONObject()
        val contents = JSONArray()
        var systemText: String? = null

        messages?.forEach { message ->
            if (message.role == "system") {
                systemText = if (systemText == null) {
                    message.content
                } else {
                    "$systemText\n${message.content}"
                }
                return@forEach
            }
            contents.put(buildContent(message))
        }

        body.put("contents", contents)

        if (!systemText.isNullOrEmpty()) {
            body.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemText))
                )
            )
        }

        val generationConfig = JSONObject().apply {
            put("temperature", temperature)
            put("maxOutputTokens", maxTokens)
        }
        body.put("generationConfig", generationConfig)

        val fullEndpoint = "$endpointBase/models/$cleanModel:generateContent"
        val response = AiHttpClient.postJson(
            fullEndpoint, apiKey, body, keyInQuery = true
        )
        return parseStandardResponse(response, cleanModel)
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
            throw IllegalArgumentException("أدخل مفتاح Gemini أولًا")
        }

        val cleanModel = cleanModelName(model)
        val body = JSONObject()
        val contents = JSONArray()
        var systemText: String? = null

        messages?.forEach { message ->
            if (message.role == "system") {
                systemText = if (systemText == null) {
                    message.content
                } else {
                    "$systemText\n${message.content}"
                }
                return@forEach
            }

            if (message.role == "tool") {
                // رسالة tool في Gemini: parts: [{functionResponse: {name, response}}]
                // نغلّفها داخل رسالة user (لأن Gemini لا يعرف دور "tool")
                val part = JSONObject()
                val fnResp = JSONObject()
                val resp = JSONObject()

                resp.put("content", message.content)
                fnResp.put("name", message.toolCallId ?: "")
                fnResp.put("response", resp)
                part.put("functionResponse", fnResp)

                val content = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(part))
                }
                contents.put(content)
                return@forEach
            }

            contents.put(buildContent(message))
        }

        body.put("contents", contents)

        if (!systemText.isNullOrEmpty()) {
            body.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemText))
                )
            )
        }

        val generationConfig = JSONObject().apply {
            put("temperature", temperature)
            put("maxOutputTokens", maxTokens)
        }
        body.put("generationConfig", generationConfig)

        // إضافة تعريف الأدوات بصيغة Gemini
        val declarations = AgentTool.buildGeminiFunctionDeclarations()
        if (declarations.length() > 0) {
            val toolWrapper = JSONObject().apply {
                put("functionDeclarations", declarations)
            }
            body.put("tools", JSONArray().put(toolWrapper))
        }

        val fullEndpoint = "$endpointBase/models/$cleanModel:generateContent"
        val response = AiHttpClient.postJson(
            fullEndpoint, apiKey, body, keyInQuery = true,
            connectTimeoutMs = 30000, readTimeoutMs = 180000
        )

        val candidates = response.optJSONArray("candidates")
            ?: throw Exception("لم يُرجع Gemini أي استجابة")
        if (candidates.length() == 0) {
            throw Exception("لم يُرجع Gemini أي استجابة")
        }

        val candidate = candidates.optJSONObject(0)
            ?: throw Exception("رد Gemini فارغ")
        val content = candidate.optJSONObject("content")
            ?: throw Exception("رد Gemini لا يحتوي content")
        val parts = content.optJSONArray("parts") ?: JSONArray()

        // جمع النص و function calls
        val text = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.has("text")) {
                text.append(part.optString("text", ""))
            }
        }

        val calls = AgentTool.parseGeminiFunctionCalls(parts)

        if (calls.isEmpty()) {
            return AiResponse(text.toString(), getId(), cleanModel)
        }

        // نُخزّن الرسالة الأصلية
        val rawAssistant = JSONObject().apply {
            put("role", "model")
            val rawParts = JSONArray()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i)
                if (p != null) rawParts.put(p)
            }
            put("parts", rawParts)
        }

        return AiResponse(
            text = text.toString(),
            toolCalls = calls,
            providerId = getId(),
            model = cleanModel,
            rawAssistantMessage = rawAssistant
        )
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================

    private fun cleanModelName(model: String?): String {
        var clean = model?.trim() ?: DEFAULT_MODEL
        if (clean.startsWith("models/")) {
            clean = clean.substring("models/".length)
        }
        return clean
    }

    /**
     * بناء content لرسالة عادية (user / assistant).
     * Gemini يستخدم "model" بدلاً من "assistant".
     */
    @Throws(Exception::class)
    private fun buildContent(message: AiMessage): JSONObject {
        val content = JSONObject()
        content.put("role", if (message.role == "assistant") "model" else "user")

        val textPart = JSONObject().apply {
            put("text", message.content)
        }
        val parts = JSONArray().apply { put(textPart) }
        content.put("parts", parts)
        return content
    }

    /**
     * تحليل رد Gemini عادي (بدون أدوات).
     */
    @Throws(Exception::class)
    private fun parseStandardResponse(response: JSONObject, model: String): AiResponse {
        val candidates = response.optJSONArray("candidates")
            ?: throw Exception("لم يُرجع Gemini أي استجابة")
        if (candidates.length() == 0) {
            throw Exception("لم يُرجع Gemini أي استجابة")
        }

        val content = candidates.getJSONObject(0).optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val text = StringBuilder()

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i)
                if (part != null) text.append(part.optString("text", ""))
            }
        }

        return AiResponse(text.toString(), getId(), model)
    }

    // ============================================================
    // ثوابت
    // ============================================================
    companion object {
        private const val DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta"
        private const val DEFAULT_MODEL = "gemini-2.0-flash"
    }
}