package com.flutteride.codweva

/**
 * استجابة موحّدة من أي مزود.
 * تحمل إما نصاً عادياً (text) أو استدعاءات أدوات (toolCalls).
 */
class AiResponse(
    /** النص النهائي (فارغ إذا كان الرد يحتوي فقط على tool calls). */
    val text: String?,

    /** استدعاءات الأدوات إن وُجدت (عادة فارغة). */
    val toolCalls: List<AgentTool.ToolCall>?,

    /** معرّف المزود. */
    val providerId: String?,

    /** اسم النموذج المستخدم. */
    val model: String?,

    /**
     * الرسالة الأصلية بصيغة المزود (تُستخدم لتمرير tool_calls في الجولة التالية لـ OpenAI).
     * مثلاً: JSONObject يحمل {"role":"assistant","content":null,"tool_calls":[...]}
     * أو null إذا لم يكن هناك tool calls.
     */
    val rawAssistantMessage: Any?
) {

    // ============================================================
    // Constructors مساعدة
    // ============================================================

    /** للاستخدام العام: رد نصي بدون أدوات. */
    constructor(text: String?, providerId: String?, model: String?) : this(
        text = text,
        toolCalls = emptyList(),
        providerId = providerId,
        model = model,
        rawAssistantMessage = null
    )

    // ============================================================
    // قيم آمنة (نفس سلوك Java)
    // ============================================================

    val safeText: String get() = text ?: ""
    val safeToolCalls: List<AgentTool.ToolCall> get() = toolCalls ?: emptyList()
    val safeProviderId: String get() = providerId ?: ""
    val safeModel: String get() = model ?: ""

    /** هل الرد يحتوي على tool calls؟ */
    val hasToolCalls: Boolean
        get() = !safeToolCalls.isEmpty()

    override fun toString(): String {
        return "AiResponse(provider=$safeProviderId, model=$safeModel, " +
                "text=${safeText.take(50)}, toolCalls=${safeToolCalls.size})"
    }
}