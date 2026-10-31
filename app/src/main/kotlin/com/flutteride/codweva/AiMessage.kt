package com.flutteride.codweva

import org.json.JSONObject
import java.io.Serializable

/**
 * رسالة موحدة تستخدمها كل مزودات الذكاء الاصطناعي.
 *
 * الحقول:
 *   - role         : user / assistant / system / tool
 *   - content      : نص الرسالة
 *   - toolCallId   : (اختياري) معرّف الاستدعاء — يُستخدم في رسائل "tool"
 *   - rawMessage   : (اختياري) الرسالة الأصلية بصيغة JSON من المزود.
 */
class AiMessage(
    role: String?,
    content: String?
) : Serializable {

    /** الدور بعد التطبيع. */
    val role: String = role ?: "user"

    /** النص بعد التطبيع. */
    val content: String = content ?: ""

    /** معرّف الاستدعاء — يُستخدم في رسائل "tool" فقط. */
    @Transient
    var toolCallId: String? = null

    /** الرسالة الأصلية من المزود (JSONObject) — للردود المركّبة. */
    @Transient
    var rawMessage: JSONObject? = null

    override fun toString(): String {
        return "AiMessage($role: ${content.take(50)})"
    }

    // ============================================================
    // مُنشئات مساعدة (Companion)
    // ============================================================
    companion object {

        @JvmStatic
        fun system(text: String?): AiMessage =
            AiMessage("system", text ?: "")

        @JvmStatic
        fun user(text: String?): AiMessage =
            AiMessage("user", text ?: "")

        @JvmStatic
        fun assistant(text: String?): AiMessage =
            AiMessage("assistant", text ?: "")

        @JvmStatic
        fun tool(toolCallId: String?, content: String?): AiMessage {
            val msg = AiMessage("tool", content ?: "")
            msg.toolCallId = toolCallId
            return msg
        }

        @JvmStatic
        fun assistantWithToolCalls(rawMessage: JSONObject?): AiMessage {
            val msg = AiMessage("assistant", "")
            msg.rawMessage = rawMessage
            return msg
        }
    }
}