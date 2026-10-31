package com.flutteride.codweva

/**
 * واجهة موحدة لمزودي الذكاء الاصطناعي.
 *
 * كل مزود (OpenAI، Gemini، OpenRouter) يُنفّذ هذه الواجهة.
 */
interface AiProvider {

    /** معرّف المزود (openai/gemini/openrouter). */
    fun getId(): String

    /** الاسم المعروض للمستخدم. */
    fun getDisplayName(): String

    /**
     * إرسال عادي بدون أدوات (يُستخدم في الوضع الذكي).
     */
    @Throws(Exception::class)
    fun send(
        messages: List<AiMessage>?,
        model: String?,
        temperature: Double,
        maxTokens: Int
    ): AiResponse

    /**
     * إرسال مع أدوات (يُستخدم في الوضع الكامل).
     *
     * @return AiResponse يحمل إما نصاً (text) أو استدعاءات أدوات (toolCalls).
     */
    @Throws(Exception::class)
    fun sendWithTools(
        messages: List<AiMessage>?,
        model: String?,
        temperature: Double,
        maxTokens: Int,
        previousToolResults: List<AgentTool.ToolCall>?
    ): AiResponse
}